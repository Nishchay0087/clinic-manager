package com.clinic.service;

import com.clinic.config.Database;
import com.clinic.exception.AppException;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real JDBC transactions against an isolated H2 database in MySQL compatibility mode. */
class AppointmentIntegrationTest {
    private final AppointmentService service=new AppointmentService();
    private LocalDate tomorrow;
    @BeforeEach void setup()throws Exception{
        Database.close();System.setProperty("DB_URL","jdbc:h2:mem:appointments_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");System.setProperty("DB_USERNAME","sa");System.setProperty("DB_PASSWORD","");
        // H2 uses virtual generated columns; MySQL's stored-column storage modifier is removed only for this fixture.
        String schema=Files.readString(Path.of("database/schema.sql")).replace(" STORED","").replaceAll("(?m)^--.*$", "");
        try(Connection c=Database.getConnection();Statement s=c.createStatement()){
            for(String statement:schema.split(";")){if(!statement.isBlank())s.execute(statement);}
            s.execute("INSERT INTO users(user_id,name,email,password_hash,role) VALUES (1,'Patient One','one@example.test','unused','PATIENT'),(2,'Doctor One','two@example.test','unused','DOCTOR'),(3,'Patient Two','three@example.test','unused','PATIENT'),(4,'Doctor Two','four@example.test','unused','DOCTOR')");
            s.execute("INSERT INTO patients(patient_id,user_id) VALUES (1,1),(2,3)");
            s.execute("INSERT INTO doctors(doctor_id,user_id,specialization,appointment_duration) VALUES (1,2,'General Medicine',30),(2,4,'General Medicine',30)");
            for(int day=1;day<=7;day++)s.execute("INSERT INTO doctor_availability(doctor_id,day_of_week,start_time,end_time) VALUES (1,"+day+",'09:00','12:00'),(1,"+day+",'13:00','17:00'),(2,"+day+",'09:00','17:00')");
        }tomorrow=LocalDate.now().plusDays(1);
    }
    @AfterEach void cleanup()throws Exception{Database.close();System.clearProperty("DB_URL");System.clearProperty("DB_USERNAME");System.clearProperty("DB_PASSWORD");}
    private Map<String,Object> booking(long doctor,String time){return Map.of("doctorId",doctor,"date",tomorrow.toString(),"startTime",time,"reason","Fictional test visit");}
    private long book(long user,long doctor,String time){return ((Number)service.book(user,"PATIENT",booking(doctor,time)).get("id")).longValue();}
    private int count(String table)throws Exception{try(Connection c=Database.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM "+table)){r.next();return r.getInt(1);}}
    @Test void usesActualHoursAndRejectsBreaksAndOffGridTimes(){
        assertTrue(service.slots(1,tomorrow).contains("09:00"));assertFalse(service.slots(1,tomorrow).contains("12:00"));
        assertThrows(AppException.class,()->book(1,1,"12:00"));assertThrows(AppException.class,()->book(1,1,"09:15"));
        book(1,1,"09:00");assertFalse(service.slots(1,tomorrow).contains("09:00"));assertTrue(service.slots(1,tomorrow).contains("09:30"));
    }
    @Test void concurrentBookingsHaveExactlyOneWinner()throws Exception{
        ExecutorService executor=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);
        try{List<Future<Boolean>> calls=new ArrayList<>();for(long user:List.of(1L,3L))calls.add(executor.submit(()->{gate.await();try{book(user,1,"10:00");return true;}catch(AppException conflict){return false;}}));gate.countDown();int wins=0;for(Future<Boolean> f:calls)if(f.get(15,TimeUnit.SECONDS))wins++;assertEquals(1,wins);assertEquals(1,count("appointments"));assertEquals(1,count("appointment_history"));}finally{executor.shutdownNow();}
    }
    @Test void overlappingPatientAppointmentsAcrossDoctorsAreRejected()throws Exception{
        book(1,1,"10:00");assertThrows(AppException.class,()->book(1,2,"10:00"));assertEquals(1,count("appointments"));book(1,2,"10:30");assertEquals(2,count("appointments"));
    }
    @Test void concurrentPatientBookingsAcrossDoctorsSerialize()throws Exception{
        ExecutorService executor=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);
        try{List<Future<Boolean>> calls=new ArrayList<>();for(long doctor:List.of(1L,2L))calls.add(executor.submit(()->{gate.await();try{book(1,doctor,"11:00");return true;}catch(AppException conflict){return false;}}));gate.countDown();int wins=0;for(Future<Boolean> f:calls)if(f.get(15,TimeUnit.SECONDS))wins++;assertEquals(1,wins);assertEquals(1,count("appointments"));}finally{executor.shutdownNow();}
    }
    @Test void rescheduleConflictRollsBackAndCancellationReleasesSlot()throws Exception{
        long id=book(1,1,"09:00");book(3,1,"10:00");assertThrows(AppException.class,()->service.reschedule(1,"PATIENT",id,Map.of("date",tomorrow.toString(),"startTime","10:00")));
        assertEquals("09:00",service.list(1,"PATIENT",Map.of()).get(0).get("startTime"));assertEquals(2,count("appointment_history"));
        assertThrows(AppException.class,()->service.cancel(3,"PATIENT",id));service.cancel(1,"PATIENT",id);assertTrue(service.slots(1,tomorrow).contains("09:00"));
    }
    @Test void configurableCutoffAndScheduleEditsProtectBookings()throws Exception{
        long id=book(1,1,"09:00");assertThrows(AppException.class,()->service.saveAvailability(2,Map.of("dayOfWeek",tomorrow.getDayOfWeek().getValue(),"startTime","10:00","endTime","17:00")));
        try(Connection c=Database.getConnection();Statement s=c.createStatement()){s.execute("UPDATE clinic_settings SET setting_value='72' WHERE setting_key='cancellation_hours'");}
        assertThrows(AppException.class,()->service.cancel(1,"PATIENT",id));assertThrows(AppException.class,()->service.reschedule(1,"PATIENT",id,Map.of("date",tomorrow.toString(),"startTime","11:00")));
    }
    @Test void cancellationRemainsAvailableWhenDoctorAccountIsDeactivated()throws Exception{
        long id=book(1,1,"09:00");try(Connection c=Database.getConnection();Statement s=c.createStatement()){s.execute("UPDATE users SET account_status='INACTIVE' WHERE user_id=2");}
        assertDoesNotThrow(()->service.cancel(1,"PATIENT",id));assertThrows(AppException.class,()->book(3,1,"11:00"));
    }
    @Test void persistsConsultationVersionsAndEnforcesPatientScope()throws Exception{
        long id=book(1,1,"09:00");try(Connection c=Database.getConnection();PreparedStatement s=c.prepareStatement("UPDATE appointments SET status='IN_PROGRESS' WHERE appointment_id=?")){s.setLong(1,id);s.executeUpdate();}
        ConsultationService clinical=new ConsultationService();Map<String,Object>body=new HashMap<>(Map.of("appointmentId",id,"clinicalNotes","Fictional note","diagnosis","Fictional finding","prescription","Fictional medication instruction"));
        clinical.save(2,"DOCTOR",body);body.put("clinicalNotes","Fictional revised note");body.put("complete",true);clinical.save(2,"DOCTOR",body);
        assertEquals(1,count("consultations"));assertEquals(2,count("consultation_versions"));assertEquals(1,count("prescriptions"));assertEquals("COMPLETED",service.list(1,"PATIENT",Map.of()).get(0).get("status"));
        List<?> records=(List<?>)clinical.records(1,"PATIENT",null).get("consultations");assertEquals("Fictional revised note",((Map<?,?>)records.get(0)).get("clinicalNotes"));
        assertThrows(AppException.class,()->clinical.records(3,"PATIENT",1L));assertThrows(AppException.class,()->clinical.records(4,"DOCTOR",1L));assertThrows(AppException.class,()->clinical.save(2,"DOCTOR",body));
    }
}
