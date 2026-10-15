package com.clinic.triage;

import com.clinic.config.Database;
import com.clinic.exception.AppException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.time.*;
import java.util.*;

public class TriageService {
    public List<Map<String,Object>> list(long userId,String role){
        staff(role);
        String sql="SELECT t.*,u.name patient_name,a.doctor_id FROM triage_entries t JOIN patients p ON p.patient_id=t.patient_id JOIN users u ON u.user_id=p.user_id JOIN appointments a ON a.appointment_id=t.appointment_id JOIN doctors d ON d.doctor_id=a.doctor_id WHERE t.queue_status IN ('WAITING','IN_PROGRESS') AND a.status IN ('CHECKED_IN','IN_PROGRESS')"+("DOCTOR".equals(role)?" AND d.user_id=?":"")+" ORDER BY t.arrival_time";
        try(Connection c=Database.getConnection();PreparedStatement s=c.prepareStatement(sql)){
            if("DOCTOR".equals(role))s.setLong(1,userId);
            PriorityQueue<PatientQueueEntry> queue=new PriorityQueue<>(PatientQueueEntry.ordering(priority(c)));
            try(ResultSet r=s.executeQuery()){while(r.next())queue.add(new PatientQueueEntry(r.getLong("triage_id"),r.getLong("patient_id"),r.getLong("appointment_id"),r.getString("patient_name"),r.getString("severity_level"),r.getTimestamp("arrival_time").toLocalDateTime(),r.getString("queue_status"),r.getTimestamp("priority_timestamp").toLocalDateTime(),r.getLong("doctor_id")));}
            List<Map<String,Object>> result=new ArrayList<>();while(!queue.isEmpty()){var e=queue.remove();Map<String,Object> row=new LinkedHashMap<>();row.put("triageId",e.triageId());row.put("patientId",e.patientId());row.put("appointmentId",e.appointmentId());row.put("patientName",e.patientName());row.put("severityLevel",e.severityLevel());row.put("arrivalTime",e.arrivalTime().toString());row.put("queueStatus",e.queueStatus());row.put("waitingMinutes",e.waitingMinutes(Clock.systemDefaultZone()));row.put("priorityTimestamp",e.priorityTimestamp().toString());row.put("doctorId",e.assignedClinician());result.add(row);}return result;
        }catch(SQLException e){throw new AppException(500,"Could not load the patient queue.");}
    }
    public void update(long actorId,String role,long appointmentId,String severity,String reason){
        staff(role);if(severity==null||!PatientQueueEntry.DEFAULT_PRIORITY.containsKey(severity))throw new AppException(400,"Select Emergency, Urgent or Routine.");
        if(reason==null||reason.isBlank()||reason.length()>500)throw new AppException(400,"Record a priority-change reason of 1–500 characters.");
        try(Connection c=Database.getConnection()){c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);c.setAutoCommit(false);try{
            long doctorId,patientId;
            try(PreparedStatement s=c.prepareStatement("SELECT doctor_id,patient_id FROM appointments WHERE appointment_id=?")){s.setLong(1,appointmentId);try(ResultSet r=s.executeQuery()){if(!r.next())throw new AppException(404,"Appointment not found.");doctorId=r.getLong(1);patientId=r.getLong(2);}}
            try(PreparedStatement s=c.prepareStatement("SELECT user_id FROM doctors WHERE doctor_id=? FOR UPDATE")){s.setLong(1,doctorId);try(ResultSet r=s.executeQuery()){if(!r.next())throw new AppException(404,"Doctor not found.");if("DOCTOR".equals(role)&&r.getLong(1)!=actorId)throw new AppException(403,"Only the assigned doctor can change this patient's priority.");}}
            try(PreparedStatement s=c.prepareStatement("SELECT patient_id FROM patients WHERE patient_id=? FOR UPDATE")){s.setLong(1,patientId);try(ResultSet r=s.executeQuery()){if(!r.next())throw new AppException(404,"Patient not found.");}}
            long patient;String status;String previousSeverity;
            try(PreparedStatement s=c.prepareStatement("SELECT a.patient_id,a.status,d.user_id,a.severity_level FROM appointments a JOIN doctors d ON d.doctor_id=a.doctor_id WHERE a.appointment_id=? FOR UPDATE")){s.setLong(1,appointmentId);try(ResultSet r=s.executeQuery()){if(!r.next())throw new AppException(404,"Appointment not found.");if("DOCTOR".equals(role)&&r.getLong(3)!=actorId)throw new AppException(403,"Only the assigned doctor can change this patient's priority.");patient=r.getLong(1);status=r.getString(2);previousSeverity=Objects.toString(r.getString(4),"ROUTINE");}}
            if(!Set.of("CHECKED_IN","IN_PROGRESS").contains(status))throw new AppException(409,"Check in the patient before assigning triage priority.");
            try(PreparedStatement s=c.prepareStatement("INSERT INTO triage_entries(patient_id,appointment_id,severity_level,arrival_time,queue_status,assessed_by,priority_timestamp) VALUES(?,?,?,CURRENT_TIMESTAMP,?,?,CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE severity_level=VALUES(severity_level),queue_status=VALUES(queue_status),assessed_by=VALUES(assessed_by),priority_timestamp=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP")){s.setLong(1,patient);s.setLong(2,appointmentId);s.setString(3,severity);s.setString(4,"IN_PROGRESS".equals(status)?"IN_PROGRESS":"WAITING");s.setLong(5,actorId);s.executeUpdate();}
            try(PreparedStatement s=c.prepareStatement("UPDATE appointments SET severity_level=? WHERE appointment_id=?")){s.setString(1,severity);s.setLong(2,appointmentId);s.executeUpdate();}
            try(PreparedStatement s=c.prepareStatement("INSERT INTO audit_logs(actor_user_id,action,entity_type,entity_id,metadata) VALUES(?,'TRIAGE_UPDATED','APPOINTMENT',?,?)")){s.setLong(1,actorId);s.setLong(2,appointmentId);s.setString(3,new ObjectMapper().writeValueAsString(Map.of("previousSeverity",previousSeverity,"severity",severity,"reason",reason)));s.executeUpdate();}c.commit();
        }catch(Exception e){c.rollback();if(e instanceof AppException a)throw a;throw new AppException(500,"Could not update triage priority.");}}
        catch(SQLException e){throw new AppException(500,"Could not update triage priority.");}
    }
    private Map<String,Integer> priority(Connection c)throws SQLException{
        try(PreparedStatement s=c.prepareStatement("SELECT setting_value FROM clinic_settings WHERE setting_key='triage_priority_order'");ResultSet r=s.executeQuery()){
            if(r.next()){String[] categories=r.getString(1).split(",");Map<String,Integer> values=new HashMap<>();for(int i=0;i<categories.length;i++)values.put(categories[i].trim().toUpperCase(Locale.ROOT),i);if(values.keySet().equals(PatientQueueEntry.DEFAULT_PRIORITY.keySet()))return values;}
        }return PatientQueueEntry.DEFAULT_PRIORITY;
    }
    private void staff(String role){if(!Set.of("ADMIN","DOCTOR").contains(role))throw new AppException(403,"Clinical staff access is required.");}
}
