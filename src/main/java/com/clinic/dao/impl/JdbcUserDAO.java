package com.clinic.dao.impl;
import com.clinic.dao.IUserDAO;import com.clinic.config.Database;import com.clinic.util.Sql;import java.sql.*;import java.util.*;
public final class JdbcUserDAO implements IUserDAO {
 public Map<String,Object> byEmail(String email)throws SQLException{try(var c=Database.getConnection()){return Sql.one(c,"SELECT user_id AS id,name,email,password_hash AS passwordHash,auth_version AS authVersion,role,account_status AS accountStatus FROM users WHERE email=?",email);}}
 public Map<String,Object> byId(long id)throws SQLException{try(var c=Database.getConnection()){return Sql.one(c,"SELECT user_id AS id,name,email,auth_version AS authVersion,role,account_status AS accountStatus FROM users WHERE user_id=?",id);}}
 public long create(String name,String email,String hash,String role,String specialty,double fee)throws SQLException{return create(name,email,hash,role,specialty,fee,null);}
 public long create(String name,String email,String hash,String role,String specialty,double fee,Long actor)throws SQLException{
  try(var c=Database.getConnection()){c.setAutoCommit(false);try{
   long id=Sql.insert(c,"INSERT INTO users(name,email,password_hash,role) VALUES(?,?,?,?)",name,email,hash,role);
   if(role.equals("PATIENT"))Sql.insert(c,"INSERT INTO patients(user_id) VALUES(?)",id);
   if(role.equals("DOCTOR"))Sql.insert(c,"INSERT INTO doctors(user_id,specialization,consultation_fee,profile_description,appointment_duration) VALUES(?,?,?,?,COALESCE((SELECT setting_value FROM clinic_settings WHERE setting_key='appointment_duration'),30))",id,specialty,fee,"");
   if(actor!=null)Sql.audit(c,actor,"ACCOUNT_CREATED","USER",id);c.commit();return id;
  }catch(SQLException e){c.rollback();throw e;}}
 }
}
