package com.clinic.dao.impl;
import com.clinic.dao.IAppointmentDAO;
import java.sql.*;
import java.util.*;

public final class JdbcAppointmentDAO implements IAppointmentDAO {
    public static final String SELECT="SELECT a.*,pu.name patient_name,du.name doctor_name,d.specialization FROM appointments a JOIN patients p ON p.patient_id=a.patient_id JOIN users pu ON pu.user_id=p.user_id JOIN doctors d ON d.doctor_id=a.doctor_id JOIN users du ON du.user_id=d.user_id";
    @Override public Map<String,Object> find(Connection c,long id)throws SQLException{
        try(PreparedStatement s=c.prepareStatement(SELECT+" WHERE a.appointment_id=?")){s.setLong(1,id);try(ResultSet r=s.executeQuery()){return r.next()?mapRow(r):null;}}
    }
    @Override public List<Map<String,Object>> history(Connection c,long id)throws SQLException{
        List<Map<String,Object>> out=new ArrayList<>();try(PreparedStatement s=c.prepareStatement("SELECT h.*,u.name actor_name FROM appointment_history h JOIN users u ON u.user_id=h.actor_user_id WHERE appointment_id=? ORDER BY created_at,history_id")){s.setLong(1,id);try(ResultSet r=s.executeQuery()){while(r.next()){Map<String,Object> row=new LinkedHashMap<>();row.put("id",r.getLong("history_id"));row.put("oldStatus",r.getString("old_status"));row.put("newStatus",r.getString("new_status"));row.put("reason",r.getString("change_reason"));row.put("actor",r.getString("actor_name"));row.put("createdAt",r.getTimestamp("created_at").toLocalDateTime().toString());out.add(row);}}}return out;
    }
    public static Map<String,Object> mapRow(ResultSet r)throws SQLException{
        Map<String,Object>a=new LinkedHashMap<>();a.put("id",r.getLong("appointment_id"));a.put("patientId",r.getLong("patient_id"));a.put("patientName",r.getString("patient_name"));a.put("doctorId",r.getLong("doctor_id"));a.put("doctorName",r.getString("doctor_name"));a.put("specialization",r.getString("specialization"));a.put("date",r.getDate("appointment_date").toString());a.put("startTime",r.getTime("start_time").toLocalTime().toString());a.put("endTime",r.getTime("end_time").toLocalTime().toString());a.put("status",r.getString("status"));a.put("reason",r.getString("reason_for_visit"));a.put("notes",r.getString("notes"));return a;
    }
}
