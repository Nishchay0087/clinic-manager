package com.clinic.dao.impl;
import com.clinic.dao.IDoctorDAO;import com.clinic.util.Sql;import java.sql.*;import java.util.*;
public final class JdbcDoctorDAO implements IDoctorDAO {
 public List<Map<String,Object>> search(Connection c,String name,String specialty,int limit,int offset)throws SQLException{return Sql.query(c,"SELECT d.doctor_id AS id,u.name,d.specialization,d.consultation_fee AS consultationFee,d.appointment_duration AS appointmentDuration,d.profile_description AS profileDescription FROM doctors d JOIN users u ON u.user_id=d.user_id WHERE u.account_status='ACTIVE' AND u.name LIKE ? AND d.specialization LIKE ? ORDER BY u.name LIMIT ? OFFSET ?","%"+name+"%","%"+specialty+"%",limit,offset);}
 public void update(Connection c,long userId,String specialty,double fee,String description)throws SQLException{if(Sql.update(c,"UPDATE doctors SET specialization=?,consultation_fee=?,profile_description=? WHERE user_id=?",specialty,fee,description,userId)!=1)throw new com.clinic.exception.AppException(404,"Doctor not found.");}
}
