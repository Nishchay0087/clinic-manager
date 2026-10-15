package com.clinic.triage;

import com.clinic.config.Database;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class TriageConcurrencyTest {
 private String url,oldUrl,oldUser,oldPassword;
 @BeforeEach void setup()throws Exception{
  url="jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000";
  try(Connection c=DriverManager.getConnection(url);Statement s=c.createStatement()){
   s.execute("CREATE TABLE doctors(doctor_id BIGINT PRIMARY KEY,user_id BIGINT)");s.execute("CREATE TABLE patients(patient_id BIGINT PRIMARY KEY,user_id BIGINT)");
   s.execute("CREATE TABLE appointments(appointment_id BIGINT PRIMARY KEY,doctor_id BIGINT,patient_id BIGINT,status VARCHAR(30),severity_level VARCHAR(30))");
   s.execute("CREATE TABLE triage_entries(triage_id BIGINT AUTO_INCREMENT PRIMARY KEY,patient_id BIGINT,appointment_id BIGINT UNIQUE,severity_level VARCHAR(30),arrival_time TIMESTAMP,queue_status VARCHAR(30),assessed_by BIGINT,priority_timestamp TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
   s.execute("CREATE TABLE audit_logs(actor_user_id BIGINT,action VARCHAR(100),entity_type VARCHAR(80),entity_id BIGINT,metadata VARCHAR(2000))");
   s.execute("INSERT INTO doctors VALUES(1,21)");s.execute("INSERT INTO patients VALUES(1,11)");s.execute("INSERT INTO appointments VALUES(1,1,1,'CHECKED_IN','ROUTINE')");
  }
  Database.close();oldUrl=System.getProperty("DB_URL");oldUser=System.getProperty("DB_USERNAME");oldPassword=System.getProperty("DB_PASSWORD");System.setProperty("DB_URL",url);System.setProperty("DB_USERNAME","");System.setProperty("DB_PASSWORD","");
 }
 @AfterEach void cleanup()throws Exception{Database.close();restore("DB_URL",oldUrl);restore("DB_USERNAME",oldUser);restore("DB_PASSWORD",oldPassword);}
 private void restore(String k,String v){if(v==null)System.clearProperty(k);else System.setProperty(k,v);}
 @Test void concurrentAssessmentsPreserveOneQueueEntryAndBothAudits()throws Exception{
  CountDownLatch start=new CountDownLatch(1);try(ExecutorService executor=Executors.newFixedThreadPool(2)){
   Future<?> a=executor.submit(()->{await(start);new TriageService().update(21,"DOCTOR",1,"URGENT","Fictional assessment one");});
   Future<?> b=executor.submit(()->{await(start);new TriageService().update(30,"ADMIN",1,"EMERGENCY","Fictional assessment two");});
   start.countDown();a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
  }
  try(Connection c=DriverManager.getConnection(url);Statement s=c.createStatement()){
   try(ResultSet r=s.executeQuery("SELECT COUNT(*) FROM triage_entries")){r.next();assertEquals(1,r.getInt(1));}
   try(ResultSet r=s.executeQuery("SELECT COUNT(*) FROM audit_logs")){r.next();assertEquals(2,r.getInt(1));}
   try(ResultSet r=s.executeQuery("SELECT a.severity_level,t.severity_level FROM appointments a JOIN triage_entries t ON t.appointment_id=a.appointment_id")){r.next();assertEquals(r.getString(1),r.getString(2));}
  }
 }
 @Test void unassignedDoctorCannotModifyQueue(){assertThrows(com.clinic.exception.AppException.class,()->new TriageService().update(99,"DOCTOR",1,"URGENT","Fictional unauthorized assessment"));}
 private void await(CountDownLatch latch){try{latch.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}}
}
