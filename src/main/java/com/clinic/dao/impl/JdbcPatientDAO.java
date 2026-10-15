package com.clinic.dao.impl;
import com.clinic.dao.IPatientDAO;import com.clinic.util.Sql;import java.sql.*;
public final class JdbcPatientDAO implements IPatientDAO {public void update(Connection c,long userId,String birth,String blood,String contact,String allergies,String medications)throws SQLException{Sql.update(c,"UPDATE patients SET date_of_birth=?,blood_group=?,emergency_contact=?,allergies=?,medications=? WHERE user_id=?",birth==null||birth.isBlank()?null:birth,blood,contact,allergies,medications,userId);}}
