package com.clinic.dao;
import java.sql.*;import java.util.*;
public interface IDoctorDAO {List<Map<String,Object>> search(Connection c,String name,String specialty,int limit,int offset)throws SQLException;void update(Connection c,long userId,String specialty,double fee,String description)throws SQLException;}
