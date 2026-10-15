package com.clinic.dao;
import java.sql.*;
public interface IPatientDAO {void update(Connection c,long userId,String birth,String blood,String contact,String allergies,String medications)throws SQLException;}
