package com.clinic.scheduler;

import com.clinic.config.Database;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;


class BookingCleanupServiceTest {
 @Test void expiresOnlyActiveExpiredReservationsAndAuditsOnce()throws Exception{
  String url="jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1";
  try(Connection c=DriverManager.getConnection(url);Statement s=c.createStatement()){
   s.execute("CREATE TABLE reservations(reservation_id BIGINT PRIMARY KEY,status VARCHAR(20),expires_at TIMESTAMP)");
   s.execute("CREATE TABLE audit_logs(actor_user_id BIGINT,action VARCHAR(100),entity_type VARCHAR(80),entity_id BIGINT,metadata VARCHAR(2000))");
   s.execute("INSERT INTO reservations VALUES(1,'ACTIVE',TIMESTAMP '2020-01-01 00:00:00'),(2,'ACTIVE',TIMESTAMP '2099-01-01 00:00:00'),(3,'CONFIRMED',TIMESTAMP '2020-01-01 00:00:00')");
  }
  String oldUrl=System.getProperty("DB_URL"),oldUser=System.getProperty("DB_USERNAME"),oldPassword=System.getProperty("DB_PASSWORD");
  Database.close();System.setProperty("DB_URL",url);System.setProperty("DB_USERNAME","");System.setProperty("DB_PASSWORD","");
  try{var cleanup=new BookingCleanupService();cleanup.run();cleanup.run();}
  finally{Database.close();restore("DB_URL",oldUrl);restore("DB_USERNAME",oldUser);restore("DB_PASSWORD",oldPassword);}
  try(Connection c=DriverManager.getConnection(url);Statement s=c.createStatement()){
   try(ResultSet r=s.executeQuery("SELECT status FROM reservations ORDER BY reservation_id")){assertTrue(r.next());assertEquals("EXPIRED",r.getString(1));assertTrue(r.next());assertEquals("ACTIVE",r.getString(1));assertTrue(r.next());assertEquals("CONFIRMED",r.getString(1));}
   try(ResultSet r=s.executeQuery("SELECT COUNT(*) FROM audit_logs")){r.next();assertEquals(1,r.getInt(1));}
  }
 }
 private void restore(String key,String value){if(value==null)System.clearProperty(key);else System.setProperty(key,value);}
}
