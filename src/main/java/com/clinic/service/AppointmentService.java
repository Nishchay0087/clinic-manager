package com.clinic.service;

import com.clinic.config.Database;
import com.clinic.exception.AppException;
import com.clinic.dao.IAppointmentDAO;
import com.clinic.dao.impl.JdbcAppointmentDAO;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Appointment writes serialize on the doctor and patient rows, including schedule edits. */
public class AppointmentService {
    private static final IAppointmentDAO DAO=new JdbcAppointmentDAO();
    private static final String SELECT = JdbcAppointmentDAO.SELECT;
    private static final String ACTIVE = "('BOOKED','CONFIRMED','CHECKED_IN','IN_PROGRESS')";

    public List<Map<String,Object>> list(long userId,String role,Map<String,String> filters) {
        return read(c -> {
            StringBuilder sql=new StringBuilder(SELECT+" WHERE 1=1"); List<Object> args=new ArrayList<>();
            if ("PATIENT".equals(role)) {sql.append(" AND p.user_id=?");args.add(userId);}
            else if ("DOCTOR".equals(role)) {sql.append(" AND d.user_id=?");args.add(userId);}
            else require("ADMIN".equals(role),403,"Access denied.");
            for (String key:List.of("date","status","doctorId","patientId")) {
                String v=filters.get(key); if(v==null||v.isBlank())continue;
                if("date".equals(key))date(v);else if(key.endsWith("Id"))id(v);else require(Set.of("BOOKED","CONFIRMED","CHECKED_IN","IN_PROGRESS","COMPLETED","CANCELLED","NO_SHOW").contains(v),400,"Invalid appointment status.");
                sql.append(" AND ").append(switch(key){case "date"->"a.appointment_date";case "status"->"a.status";case "doctorId"->"a.doctor_id";default->"a.patient_id";}).append("=?");args.add(v);
            }
            for(String key:List.of("from","to")){String v=filters.get(key);if(v!=null&&!v.isBlank()){LocalDate parsed=date(v);sql.append(" AND a.appointment_date").append(key.equals("from")?">= ?":"<= ?");args.add(parsed);}}
            String query=filters.getOrDefault("q","").strip();if(!query.isEmpty()){require(query.length()<=200,400,"Search is too long.");sql.append(" AND (pu.name LIKE ? OR du.name LIKE ?)");args.add("%"+query+"%");args.add("%"+query+"%");}
            String specialization=filters.get("specialization"); if(specialization!=null&&!specialization.isBlank()){sql.append(" AND d.specialization=?");args.add(specialization);}
            int page=positiveInt(filters.getOrDefault("page","1"),1,100000), size=positiveInt(filters.getOrDefault("pageSize","100"),1,100);
            sql.append(" ORDER BY a.appointment_date DESC,a.start_time DESC LIMIT ? OFFSET ?");args.add(size);args.add((page-1)*size);
            List<Map<String,Object>> out=new ArrayList<>();try(PreparedStatement s=prepare(c,sql.toString(),args.toArray());ResultSet r=s.executeQuery()){while(r.next())out.add(JdbcAppointmentDAO.mapRow(r));}return out;
        });
    }

    public List<Map<String,Object>> history(long userId,String role,long appointmentId) {
        return read(c->{Map<String,Object>a=find(c,appointmentId);authorize(c,userId,role,((Number)a.get("patientId")).longValue(),((Number)a.get("doctorId")).longValue());return DAO.history(c,appointmentId);});
    }

    public List<String> slots(long doctorId,LocalDate date) {
        return read(c->{
            require(date!=null,400,"Choose a date."); int days=setting(c,"booking_days",90);
            if(date.isBefore(LocalDate.now())||date.isAfter(LocalDate.now().plusDays(days)))return List.of();
            int duration=doctorDuration(c,doctorId,false);List<String> out=new ArrayList<>();
            for(Window w:windows(c,doctorId,date.getDayOfWeek().getValue())) {
                for(LocalTime t=w.start();!t.plusMinutes(duration).isAfter(w.end())&&t.plusMinutes(duration).isAfter(t);t=t.plusMinutes(duration)){
                    if(!date.atTime(t).isAfter(LocalDateTime.now()))continue;
                    if(!overlap(c,doctorId,0,date,t,t.plusMinutes(duration),0)&&!blocked(c,doctorId,date.atTime(t),date.atTime(t.plusMinutes(duration))))out.add(t.toString());
                }
            }return out;
        });
    }

    public Map<String,Object> book(long userId,String role,Map<String,Object> body) {
        require("PATIENT".equals(role)||"ADMIN".equals(role),403,"Only patients or administrators may book appointments.");
        long doctor=id(body.get("doctorId"));LocalDate date=date(body.get("date"));LocalTime start=time(body.get("startTime"));String reason=string(body,"reason",1000,true),notes=string(body,"notes",4000,false);
        return transaction(c->{
            int duration=doctorDuration(c,doctor,true);long patient="PATIENT".equals(role)?profile(c,"patients","patient_id",userId):id(body.get("patientId"));lockPatient(c,patient);
            LocalTime end=end(start,duration);validateSlot(c,doctor,patient,date,start,end,0);
            long appointmentId;
            try(PreparedStatement s=c.prepareStatement("INSERT INTO appointments(patient_id,doctor_id,appointment_date,start_time,end_time,reason_for_visit,notes,status) VALUES(?,?,?,?,?,?,?,'BOOKED')",Statement.RETURN_GENERATED_KEYS)){
                bind(s,patient,doctor,date,start,end,reason,notes);s.executeUpdate();try(ResultSet r=s.getGeneratedKeys()){r.next();appointmentId=r.getLong(1);}
            }
            history(c,appointmentId,userId,null,"BOOKED","Appointment booked");return find(c,appointmentId);
        });
    }

    public void cancel(long userId,String role,long appointmentId) {
        transaction(c->{Map<String,Object>a=locked(c,appointmentId,userId,role);modifiable(c,a);execute(c,"UPDATE appointments SET status='CANCELLED',updated_at=CURRENT_TIMESTAMP WHERE appointment_id=?",appointmentId);history(c,appointmentId,userId,(String)a.get("status"),"CANCELLED","Appointment cancelled");execute(c,"UPDATE triage_entries SET queue_status='CANCELLED',updated_at=CURRENT_TIMESTAMP WHERE appointment_id=? AND queue_status NOT IN ('COMPLETED','CANCELLED')",appointmentId);return null;});
    }

    /** Rescheduling preserves the clinician; choose a different clinician by cancelling and booking. */
    public void reschedule(long userId,String role,long appointmentId,Map<String,Object> body) {
        LocalDate date=date(body.get("date"));LocalTime start=time(body.get("startTime"));
        transaction(c->{Map<String,Object>a=locked(c,appointmentId,userId,role);modifiable(c,a);long doctor=((Number)a.get("doctorId")).longValue(),patient=((Number)a.get("patientId")).longValue();
            if(body.containsKey("doctorId"))require(id(body.get("doctorId"))==doctor,400,"Rescheduling keeps the same doctor. Cancel and book to change doctors.");
            LocalTime end=end(start,doctorDuration(c,doctor,false));validateSlot(c,doctor,patient,date,start,end,appointmentId);
            execute(c,"UPDATE appointments SET appointment_date=?,start_time=?,end_time=?,updated_at=CURRENT_TIMESTAMP WHERE appointment_id=?",date,start,end,appointmentId);
            execute(c,"UPDATE reminder_jobs SET status='PENDING',attempt_count=0,last_attempt_at=NULL,scheduled_at=? WHERE appointment_id=?",date.atTime(start).minusHours(setting(c,"reminder_hours",24)),appointmentId);
            history(c,appointmentId,userId,(String)a.get("status"),(String)a.get("status"),"Rescheduled from "+a.get("date")+" "+a.get("startTime")+" to "+date+" "+start);return null;
        });
    }

    public void status(long userId,String role,long appointmentId,String next) {
        require("DOCTOR".equals(role)||"ADMIN".equals(role),403,"Only clinical staff may update visit status.");
        if("CANCELLED".equals(next)){cancel(userId,role,appointmentId);return;}
        transaction(c->{Map<String,Object>a=locked(c,appointmentId,userId,role);String old=(String)a.get("status");require(validTransition(old,next),409,"This appointment status change is not allowed.");
            LocalDate date=LocalDate.parse((String)a.get("date"));
            if(Set.of("CHECKED_IN","IN_PROGRESS","COMPLETED").contains(next))require(date.equals(LocalDate.now()),409,"This action is available on the appointment date.");
            if("NO_SHOW".equals(next))require(!date.atTime(LocalTime.parse((String)a.get("startTime"))).isAfter(LocalDateTime.now()),409,"A future visit cannot be marked as a no-show.");
            if("COMPLETED".equals(next))require(exists(c,"SELECT 1 FROM consultations WHERE appointment_id=?",appointmentId),409,"Save consultation notes before completing this visit.");
            execute(c,"UPDATE appointments SET status=?,updated_at=CURRENT_TIMESTAMP WHERE appointment_id=?",next,appointmentId);history(c,appointmentId,userId,old,next,"Visit status updated");
            if("CHECKED_IN".equals(next)&&!exists(c,"SELECT 1 FROM triage_entries WHERE appointment_id=?",appointmentId))execute(c,"INSERT INTO triage_entries(patient_id,appointment_id,severity_level,arrival_time,queue_status,assessed_by,priority_timestamp) VALUES(?,?,'ROUTINE',CURRENT_TIMESTAMP,'WAITING',?,CURRENT_TIMESTAMP)",a.get("patientId"),appointmentId,userId);
            if("IN_PROGRESS".equals(next)||"COMPLETED".equals(next)||"NO_SHOW".equals(next))execute(c,"UPDATE triage_entries SET queue_status=?,updated_at=CURRENT_TIMESTAMP WHERE appointment_id=?",next.equals("NO_SHOW")?"CANCELLED":next,appointmentId);return null;
        });
    }

    public Map<String,Object> availability(long userId) {
        return read(c->{long doctor=profile(c,"doctors","doctor_id",userId);Map<String,Object>out=new LinkedHashMap<>();out.put("doctorId",doctor);out.put("appointmentDuration",doctorDuration(c,doctor,false));List<Map<String,Object>>weekly=new ArrayList<>(),unavailable=new ArrayList<>();
            try(PreparedStatement s=prepare(c,"SELECT * FROM doctor_availability WHERE doctor_id=? ORDER BY day_of_week,start_time",doctor);ResultSet r=s.executeQuery()){while(r.next())weekly.add(Map.of("id",r.getLong("availability_id"),"dayOfWeek",r.getInt("day_of_week"),"startTime",r.getTime("start_time").toLocalTime().toString(),"endTime",r.getTime("end_time").toLocalTime().toString()));}
            try(PreparedStatement s=prepare(c,"SELECT * FROM doctor_unavailability WHERE doctor_id=? AND end_datetime>CURRENT_TIMESTAMP ORDER BY start_datetime LIMIT 100",doctor);ResultSet r=s.executeQuery()){while(r.next())unavailable.add(Map.of("id",r.getLong("unavailability_id"),"start",r.getTimestamp("start_datetime").toLocalDateTime().toString(),"end",r.getTimestamp("end_datetime").toLocalDateTime().toString(),"reason",Objects.toString(r.getString("reason"),"")));}
            out.put("weekly",weekly);out.put("unavailability",unavailable);return out;
        });
    }

    public void saveAvailability(long userId,Map<String,Object> body) {
        transaction(c->{long doctor=profile(c,"doctors","doctor_id",userId);int oldDuration=doctorDuration(c,doctor,true);int duration=body.containsKey("appointmentDuration")?positiveInt(body.get("appointmentDuration").toString(),5,120):oldDuration;
            List<Window> replacement=new ArrayList<>();boolean all=body.containsKey("weekly");
            if(all){require(body.get("weekly") instanceof List<?>,400,"Weekly schedule must be a list.");for(Object row:(List<?>)body.get("weekly")){require(row instanceof Map<?,?>,400,"Invalid schedule.");replacement.add(window((Map<?,?>)row));}}
            else replacement.add(window(body));
            validateWindows(replacement);LocalTime clinicStart=clinicTime(c,"working_start","09:00"),clinicEnd=clinicTime(c,"working_end","18:00");for(Window w:replacement)require(!w.start().isBefore(clinicStart)&&!w.end().isAfter(clinicEnd),400,"Working hours must fall within clinic opening hours.");List<Window> proposed=new ArrayList<>();
            if(!all){int day=replacement.get(0).day();for(int d=1;d<=7;d++)if(d!=day)proposed.addAll(windows(c,doctor,d));}proposed.addAll(replacement);
            try(PreparedStatement s=prepare(c,"SELECT appointment_date,start_time,end_time FROM appointments WHERE doctor_id=? AND status IN "+ACTIVE+" AND appointment_date>=CURRENT_DATE",doctor);ResultSet r=s.executeQuery()){
                while(r.next()){LocalDate d=r.getDate(1).toLocalDate();LocalTime st=r.getTime(2).toLocalTime(),en=r.getTime(3).toLocalTime();boolean covered=proposed.stream().anyMatch(w->w.day()==d.getDayOfWeek().getValue()&&!st.isBefore(w.start())&&!en.isAfter(w.end()));require(covered,409,"These hours conflict with an existing appointment. Reschedule or cancel it first.");}
            }
            if(all)execute(c,"DELETE FROM doctor_availability WHERE doctor_id=?",doctor);else execute(c,"DELETE FROM doctor_availability WHERE doctor_id=? AND day_of_week=?",doctor,replacement.get(0).day());
            for(Window w:replacement)execute(c,"INSERT INTO doctor_availability(doctor_id,day_of_week,start_time,end_time) VALUES(?,?,?,?)",doctor,w.day(),w.start(),w.end());
            execute(c,"UPDATE doctors SET appointment_duration=? WHERE doctor_id=?",duration,doctor);audit(c,userId,"SCHEDULE_UPDATED","doctor",doctor);return null;
        });
    }

    public void addUnavailability(long userId,Map<String,Object> body) {
        LocalDateTime start=dateTime(body.getOrDefault("startDatetime",body.get("start"))),end=dateTime(body.getOrDefault("endDatetime",body.get("end")));require(end.isAfter(start)&&end.isAfter(LocalDateTime.now()),400,"Choose an unavailable period with a future end after its start.");String reason=string(body,"reason",500,false);
        transaction(c->{long doctor=profile(c,"doctors","doctor_id",userId);doctorDuration(c,doctor,true);
            require(!exists(c,"SELECT 1 FROM appointments WHERE doctor_id=? AND status IN "+ACTIVE+" AND TIMESTAMP(appointment_date,start_time) < ? AND TIMESTAMP(appointment_date,end_time) > ?",doctor,end,start),409,"The unavailable period conflicts with an existing appointment.");
            execute(c,"INSERT INTO doctor_unavailability(doctor_id,start_datetime,end_datetime,reason) VALUES(?,?,?,?)",doctor,start,end,reason);audit(c,userId,"UNAVAILABILITY_ADDED","doctor",doctor);return null;
        });
    }

    static boolean validTransition(String old,String next) {
        if(old==null||next==null)return false;
        return switch(old){case "BOOKED"->Set.of("CONFIRMED","CHECKED_IN","CANCELLED","NO_SHOW").contains(next);case "CONFIRMED"->Set.of("CHECKED_IN","CANCELLED","NO_SHOW").contains(next);case "CHECKED_IN"->"IN_PROGRESS".equals(next);case "IN_PROGRESS"->"COMPLETED".equals(next);default->false;};
    }
    record Window(int day,LocalTime start,LocalTime end) {}
    static Window window(Map<?,?> row) {int day=positiveInt(Objects.toString(row.get("dayOfWeek"),""),1,7);LocalTime start=time(row.get("startTime")),end=time(row.get("endTime"));require(end.isAfter(start),400,"Working hours must end after they start.");return new Window(day,start,end);}
    static void validateWindows(List<Window> windows){for(int i=0;i<windows.size();i++)for(int j=i+1;j<windows.size();j++){Window a=windows.get(i),b=windows.get(j);require(a.day()!=b.day()||!a.start().isBefore(b.end())||!a.end().isAfter(b.start()),400,"Working-hour windows cannot overlap.");}}
    static LocalTime end(LocalTime start,int duration){LocalTime end=start.plusMinutes(duration);require(end.isAfter(start),400,"Appointments must finish on the same day.");return end;}
    private static void validateSlot(Connection c,long doctor,long patient,LocalDate date,LocalTime start,LocalTime end,long excluded)throws SQLException{
        require(date.atTime(start).isAfter(LocalDateTime.now()),400,"Appointments cannot be booked in the past.");require(!date.isAfter(LocalDate.now().plusDays(setting(c,"booking_days",90))),400,"The date is outside the clinic's booking window.");
        int duration=doctorDuration(c,doctor,false);require(windows(c,doctor,date.getDayOfWeek().getValue()).stream().anyMatch(w->!start.isBefore(w.start())&&!end.isAfter(w.end())&&Duration.between(w.start(),start).toMinutes()%duration==0),409,"This time is outside the doctor's available appointment slots.");
        require(!blocked(c,doctor,date.atTime(start),date.atTime(end)),409,"The doctor is unavailable at this time.");require(!overlap(c,doctor,patient,date,start,end,excluded),409,"This time conflicts with an existing doctor or patient appointment. Choose another slot.");
    }
    private static boolean overlap(Connection c,long doctor,long patient,LocalDate date,LocalTime start,LocalTime end,long excluded)throws SQLException{return exists(c,"SELECT 1 FROM appointments WHERE (doctor_id=? OR patient_id=?) AND appointment_date=? AND start_time<? AND end_time>? AND appointment_id<>? AND status IN "+ACTIVE,doctor,patient,date,end,start,excluded);}
    private static boolean blocked(Connection c,long doctor,LocalDateTime start,LocalDateTime end)throws SQLException{return exists(c,"SELECT 1 FROM doctor_unavailability WHERE doctor_id=? AND start_datetime<? AND end_datetime>?",doctor,end,start);}
    private static List<Window> windows(Connection c,long doctor,int day)throws SQLException{List<Window>out=new ArrayList<>();try(PreparedStatement s=prepare(c,"SELECT start_time,end_time FROM doctor_availability WHERE doctor_id=? AND day_of_week=? ORDER BY start_time",doctor,day);ResultSet r=s.executeQuery()){while(r.next()){LocalTime opening=clinicTime(c,"working_start","09:00"),closing=clinicTime(c,"working_end","18:00");LocalTime start=r.getTime(1).toLocalTime(),end=r.getTime(2).toLocalTime();if(start.isBefore(opening))start=opening;if(end.isAfter(closing))end=closing;if(start.isBefore(end))out.add(new Window(day,start,end));}}return out;}
    static int doctorDuration(Connection c,long doctor,boolean lock)throws SQLException{try(PreparedStatement s=prepare(c,"SELECT appointment_duration,user_id FROM doctors WHERE doctor_id=?"+(lock?" FOR UPDATE":""),doctor);ResultSet r=s.executeQuery()){require(r.next(),404,"Doctor not found.");require(exists(c,"SELECT 1 FROM users WHERE user_id=? AND account_status='ACTIVE'",r.getLong(2)),409,"This doctor is not accepting appointments.");int n=r.getInt(1);require(n>=5&&n<=120,409,"Doctor appointment duration is not configured.");return n;}}
    static void lockPatient(Connection c,long patient)throws SQLException{try(PreparedStatement s=prepare(c,"SELECT user_id FROM patients WHERE patient_id=? FOR UPDATE",patient);ResultSet r=s.executeQuery()){require(r.next(),404,"Patient not found.");require(exists(c,"SELECT 1 FROM users WHERE user_id=? AND account_status='ACTIVE'",r.getLong(1)),409,"This patient account is inactive.");}}
    static Map<String,Object> locked(Connection c,long id,long user,String role)throws SQLException{Map<String,Object>a=find(c,id);long doctor=((Number)a.get("doctorId")).longValue(),patient=((Number)a.get("patientId")).longValue();authorize(c,user,role,patient,doctor);lockExistingProfile(c,"doctors","doctor_id",doctor);lockExistingProfile(c,"patients","patient_id",patient);try(PreparedStatement s=prepare(c,"SELECT appointment_id FROM appointments WHERE appointment_id=? FOR UPDATE",id);ResultSet r=s.executeQuery()){require(r.next(),404,"Appointment not found.");}return find(c,id);}
    private static void lockExistingProfile(Connection c,String table,String column,long id)throws SQLException{try(PreparedStatement s=prepare(c,"SELECT "+column+" FROM "+table+" WHERE "+column+"=? FOR UPDATE",id);ResultSet r=s.executeQuery()){require(r.next(),404,"Clinical profile not found.");}}
    static void authorize(Connection c,long user,String role,long patient,long doctor)throws SQLException{if("ADMIN".equals(role))return;boolean permitted="PATIENT".equals(role)?exists(c,"SELECT 1 FROM patients WHERE patient_id=? AND user_id=?",patient,user):"DOCTOR".equals(role)&&exists(c,"SELECT 1 FROM doctors WHERE doctor_id=? AND user_id=?",doctor,user);require(permitted,403,"You do not have access to this appointment.");}
    private static void modifiable(Connection c,Map<String,Object>a)throws SQLException{require(Set.of("BOOKED","CONFIRMED").contains(a.get("status")),409,"Only booked or confirmed appointments can be changed.");LocalDateTime at=LocalDate.parse((String)a.get("date")).atTime(LocalTime.parse((String)a.get("startTime")));require(at.isAfter(LocalDateTime.now().plusHours(setting(c,"cancellation_hours",2))),409,"This appointment is inside the clinic's cancellation and rescheduling cutoff.");}
    static Map<String,Object> find(Connection c,long id)throws SQLException{Map<String,Object>a=DAO.find(c,id);require(a!=null,404,"Appointment not found.");return a;}
    static LocalTime clinicTime(Connection c,String key,String fallback)throws SQLException{try(PreparedStatement s=prepare(c,"SELECT setting_value FROM clinic_settings WHERE setting_key=?",key);ResultSet r=s.executeQuery()){return LocalTime.parse(r.next()?r.getString(1):fallback);}}
    static int setting(Connection c,String key,int fallback)throws SQLException{try(PreparedStatement s=prepare(c,"SELECT setting_value FROM clinic_settings WHERE setting_key=?",key);ResultSet r=s.executeQuery()){if(!r.next())return fallback;try{return Math.max(0,Integer.parseInt(r.getString(1)));}catch(NumberFormatException e){return fallback;}}}
    static long profile(Connection c,String table,String column,long user)throws SQLException{if(!Set.of("patients","doctors").contains(table))throw new IllegalArgumentException();try(PreparedStatement s=prepare(c,"SELECT "+column+" FROM "+table+" WHERE user_id=?",user);ResultSet r=s.executeQuery()){require(r.next(),403,"This account has no required clinical profile.");return r.getLong(1);}}
    static void history(Connection c,long id,long user,String old,String next,String reason)throws SQLException{execute(c,"INSERT INTO appointment_history(appointment_id,actor_user_id,old_status,new_status,change_reason) VALUES(?,?,?,?,?)",id,user,old,next,reason);}
    static void audit(Connection c,long user,String action,String entity,long id)throws SQLException{execute(c,"INSERT INTO audit_logs(actor_user_id,action,entity_type,entity_id,metadata) VALUES(?,?,?,?,?)",user,action,entity,id,"{}");}
    static boolean exists(Connection c,String sql,Object...args)throws SQLException{try(PreparedStatement s=prepare(c,sql,args);ResultSet r=s.executeQuery()){return r.next();}}
    static void execute(Connection c,String sql,Object...args)throws SQLException{try(PreparedStatement s=prepare(c,sql,args)){s.executeUpdate();}}
    static PreparedStatement prepare(Connection c,String sql,Object...args)throws SQLException{PreparedStatement s=c.prepareStatement(sql);try{bind(s,args);return s;}catch(SQLException e){s.close();throw e;}}
    static void bind(PreparedStatement s,Object...args)throws SQLException{for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);}
    @FunctionalInterface interface Work<T>{T run(Connection c)throws SQLException;}
    static <T>T read(Work<T>w){try(Connection c=Database.getConnection()){return w.run(c);}catch(SQLException e){throw new AppException(500,"The database could not complete this request.");}}
    static <T>T transaction(Work<T>w){return read(c->{c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);c.setAutoCommit(false);try{T result=w.run(c);c.commit();return result;}catch(SQLException|RuntimeException e){c.rollback();throw e;}finally{c.setAutoCommit(true);}});}
    static void require(boolean condition,int code,String message){if(!condition)throw new AppException(code,message);}
    static long id(Object value){try{long id=Long.parseLong(Objects.toString(value,""));require(id>0,400,"A valid identifier is required.");return id;}catch(NumberFormatException e){throw new AppException(400,"A valid identifier is required.");}}
    static int positiveInt(String value,int min,int max){try{int n=Integer.parseInt(value);require(n>=min&&n<=max,400,"Number must be between "+min+" and "+max+".");return n;}catch(NumberFormatException e){throw new AppException(400,"Enter a valid whole number.");}}
    static LocalDate date(Object value){try{return LocalDate.parse(Objects.toString(value,""));}catch(DateTimeException e){throw new AppException(400,"Enter a valid date.");}}
    static LocalTime time(Object value){try{LocalTime t=LocalTime.parse(Objects.toString(value,""));require(t.getSecond()==0&&t.getNano()==0,400,"Use whole-minute appointment times.");return t;}catch(DateTimeException e){throw new AppException(400,"Enter a valid time.");}}
    static LocalDateTime dateTime(Object value){try{return LocalDateTime.parse(Objects.toString(value,""));}catch(DateTimeException e){throw new AppException(400,"Enter a valid date and time.");}}
    static String string(Map<String,Object>body,String key,int max,boolean required){String value=Objects.toString(body.get(key),"").trim();require(!required||!value.isEmpty(),400,"Enter "+key+".");require(value.length()<=max,400,key+" is too long.");return value;}
}
