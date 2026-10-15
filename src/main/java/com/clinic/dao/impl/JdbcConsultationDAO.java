package com.clinic.dao.impl;
import com.clinic.dao.IConsultationDAO;
import java.sql.*;
import java.util.*;

public final class JdbcConsultationDAO implements IConsultationDAO {
    @Override public Map<String,Object> patient(Connection c,long patient)throws SQLException{
        try(PreparedStatement s=c.prepareStatement("SELECT p.*,u.name FROM patients p JOIN users u ON u.user_id=p.user_id WHERE p.patient_id=?")){s.setLong(1,patient);try(ResultSet r=s.executeQuery()){
            if(!r.next())return null;Map<String,Object> d=new LinkedHashMap<>();d.put("id",patient);d.put("name",r.getString("name"));d.put("dateOfBirth",r.getDate("date_of_birth")==null?null:r.getDate("date_of_birth").toString());d.put("bloodGroup",r.getString("blood_group"));d.put("allergies",r.getString("allergies"));d.put("medications",r.getString("medications"));d.put("emergencyContact",r.getString("emergency_contact"));return d;
        }}
    }
    @Override public List<Map<String,Object>> listByPatient(Connection c,long patient)throws SQLException{
        List<Map<String,Object>> out=new ArrayList<>();
        try(PreparedStatement s=c.prepareStatement("SELECT co.*,a.appointment_date,u.name doctor_name FROM consultations co JOIN appointments a ON a.appointment_id=co.appointment_id JOIN doctors d ON d.doctor_id=co.doctor_id JOIN users u ON u.user_id=d.user_id WHERE co.patient_id=? ORDER BY a.appointment_date DESC,co.consultation_id DESC LIMIT 200")){s.setLong(1,patient);try(ResultSet r=s.executeQuery()){
            while(r.next()){Map<String,Object> row=new LinkedHashMap<>();long id=r.getLong("consultation_id");row.put("id",id);row.put("appointmentId",r.getLong("appointment_id"));row.put("date",r.getDate("appointment_date").toString());row.put("doctorName",r.getString("doctor_name"));row.put("clinicalNotes",r.getString("clinical_notes"));row.put("diagnosis",r.getString("diagnosis"));row.put("followUpDate",r.getDate("follow_up_date")==null?null:r.getDate("follow_up_date").toString());row.put("prescription",prescription(c,id));out.add(row);}
        }}return out;
    }
    private static String prescription(Connection c,long consultation)throws SQLException{
        List<String> values=new ArrayList<>();try(PreparedStatement s=c.prepareStatement("SELECT medication_details FROM prescriptions WHERE consultation_id=? ORDER BY prescription_id")){s.setLong(1,consultation);try(ResultSet r=s.executeQuery()){while(r.next())values.add(r.getString(1));}}return String.join("\n",values);
    }
}
