package com.clinic.dao;
import java.sql.*;import java.util.*;
/** Abstraction: authentication does not depend on JDBC implementation details. */
public interface IUserDAO {
 Map<String,Object> byEmail(String email)throws SQLException;
 Map<String,Object> byId(long id)throws SQLException;
 long create(String name,String email,String hash,String role,String specialty,double fee)throws SQLException;
 default long create(String name,String email,String hash,String role,String specialty,double fee,Long actor)throws SQLException{return create(name,email,hash,role,specialty,fee);}
}
