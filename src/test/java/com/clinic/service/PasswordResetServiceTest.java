package com.clinic.service;

import com.clinic.config.Database;
import com.clinic.exception.AppException;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mindrot.jbcrypt.BCrypt;
import static org.junit.jupiter.api.Assertions.*;

class PasswordResetServiceTest {
 private final PasswordResetService service=new PasswordResetService();
 private final Map<String,String> old=new HashMap<>();
 @BeforeEach void setup()throws Exception{
  Database.close();for(String key:List.of("DB_URL","DB_USERNAME","DB_PASSWORD"))old.put(key,System.getProperty(key));
  System.setProperty("DB_URL","jdbc:h2:mem:reset_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1");System.setProperty("DB_USERNAME","sa");System.setProperty("DB_PASSWORD","");
  try(var c=Database.getConnection();var s=c.createStatement()){
   s.execute("CREATE TABLE users(user_id BIGINT PRIMARY KEY,role VARCHAR(20),password_hash VARCHAR(100),auth_version BIGINT DEFAULT 0)");
   s.execute("INSERT INTO users(user_id,role,password_hash) VALUES(1,'ADMIN','original'),(2,'PATIENT','original'),(3,'DOCTOR','original')");
   s.execute("CREATE TABLE password_resets(reset_id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT,token_hash VARCHAR(64) UNIQUE,expires_at TIMESTAMP,used BOOLEAN DEFAULT FALSE)");
   s.execute("CREATE TABLE audit_logs(actor_user_id BIGINT,action VARCHAR(80),entity_type VARCHAR(20),entity_id BIGINT,metadata VARCHAR(100))");
  }
 }
 @AfterEach void cleanup()throws Exception{Database.close();old.forEach((key,value)->{if(value==null)System.clearProperty(key);else System.setProperty(key,value);});}
 private String issue()throws Exception{return service.issue(1,"ADMIN",2).get("resetUrl").toString().split("token=")[1];}
 private void execute(String sql)throws Exception{try(var c=Database.getConnection();var s=c.createStatement()){s.execute(sql);}}
 private Object scalar(String sql)throws Exception{try(var c=Database.getConnection();var s=c.createStatement();var r=s.executeQuery(sql)){assertTrue(r.next());return r.getObject(1);}}
 @Test void resetIsHashedOneTimeAndRevokesOldCredentials()throws Exception{
  String token=issue();assertNotEquals(token,scalar("SELECT token_hash FROM password_resets"));
  service.reset(token,"New-password123");
  assertTrue(BCrypt.checkpw("New-password123",scalar("SELECT password_hash FROM users WHERE user_id=2").toString()));
  assertEquals(1L,((Number)scalar("SELECT auth_version FROM users WHERE user_id=2")).longValue());
  assertEquals(400,assertThrows(AppException.class,()->service.reset(token,"Other-password123")).getStatus());
 }
 @Test void newLinkInvalidatesPreviousAndExpiredLinksCannotReset()throws Exception{
  String oldToken=issue(),newToken=issue();
  assertThrows(AppException.class,()->service.reset(oldToken,"New-password123"));
  execute("UPDATE password_resets SET expires_at=TIMESTAMP '2000-01-01 00:00:00'");
  assertThrows(AppException.class,()->service.reset(newToken,"New-password123"));
  assertEquals("original",scalar("SELECT password_hash FROM users WHERE user_id=2"));
 }
 @Test void roleAndTargetRestrictionsRejectBeforeCreatingLinks()throws Exception{
  for(String role:List.of("PATIENT","DOCTOR"))assertEquals(403,assertThrows(AppException.class,()->service.issue(2,role,3)).getStatus());
  assertThrows(AppException.class,()->service.issue(1,"ADMIN",1));assertThrows(AppException.class,()->service.issue(1,"ADMIN",99));
  assertEquals(0L,((Number)scalar("SELECT COUNT(*) FROM password_resets")).longValue());
 }
 @Test void auditFailureRollsBackPasswordTokenConsumptionAndSessionRevocation()throws Exception{
  String token=issue();execute("ALTER TABLE audit_logs ADD CONSTRAINT reject_reset CHECK(action <> 'PASSWORD_RESET')");
  assertThrows(SQLException.class,()->service.reset(token,"New-password123"));
  assertEquals("original",scalar("SELECT password_hash FROM users WHERE user_id=2"));assertEquals(false,scalar("SELECT used FROM password_resets"));
  assertEquals(0L,((Number)scalar("SELECT auth_version FROM users WHERE user_id=2")).longValue());
  execute("ALTER TABLE audit_logs DROP CONSTRAINT reject_reset");assertDoesNotThrow(()->service.reset(token,"New-password123"));
 }
 @Test void failedReplacementLinkPreservesPreviousUsableLink()throws Exception{
  String token=issue();execute("ALTER TABLE audit_logs ADD CONSTRAINT reject_new_link CHECK(action <> 'RESET_LINK_CREATED') NOCHECK");
  assertThrows(SQLException.class,()->issue());assertEquals(1L,((Number)scalar("SELECT COUNT(*) FROM password_resets")).longValue());
  assertEquals(false,scalar("SELECT used FROM password_resets"));assertDoesNotThrow(()->service.reset(token,"New-password123"));
 }
}
