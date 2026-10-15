package com.clinic.document;

import com.clinic.config.Database;
import com.clinic.exception.AppException;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;


class DocumentAuthorizationTest {
 @TempDir Path storage;
 private String url;
 private String oldUrl,oldUser,oldPassword;
 @BeforeEach void setup()throws Exception{
  url="jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1";
  try(Connection c=DriverManager.getConnection(url);Statement s=c.createStatement()){
   s.execute("CREATE TABLE patients(patient_id BIGINT PRIMARY KEY,user_id BIGINT)");
   s.execute("CREATE TABLE doctors(doctor_id BIGINT PRIMARY KEY,user_id BIGINT)");
   s.execute("CREATE TABLE appointments(patient_id BIGINT,doctor_id BIGINT,status VARCHAR(30))");
   s.execute("CREATE TABLE medical_documents(document_id BIGINT PRIMARY KEY,patient_id BIGINT,storage_filename VARCHAR(100),file_size BIGINT,archived BOOLEAN,title VARCHAR(200),description VARCHAR(1000),document_type VARCHAR(80),uploaded_by BIGINT,uploaded_at TIMESTAMP,extracted_text CLOB,sha256_hash VARCHAR(64))");
   s.execute("CREATE TABLE users(user_id BIGINT PRIMARY KEY,name VARCHAR(120))");s.execute("INSERT INTO users VALUES(11,'Fictional patient')");
   s.execute("INSERT INTO patients VALUES(1,11),(2,12)");s.execute("INSERT INTO doctors VALUES(1,21),(2,22)");s.execute("INSERT INTO appointments VALUES(1,1,'CONFIRMED'),(1,2,'CANCELLED')");
   s.execute("INSERT INTO medical_documents(document_id,patient_id,storage_filename,file_size,archived,title,document_type,uploaded_by,uploaded_at) VALUES(1,1,'00000000-0000-0000-0000-000000000000.pdf',50,FALSE,'Fictional report','LAB_REPORT',11,TIMESTAMP '2026-01-02 10:00:00')");
  }
  Database.close();oldUrl=System.getProperty("DB_URL");oldUser=System.getProperty("DB_USERNAME");oldPassword=System.getProperty("DB_PASSWORD");System.setProperty("DB_URL",url);System.setProperty("DB_USERNAME","");System.setProperty("DB_PASSWORD","");
 }
 @AfterEach void cleanup()throws Exception{Database.close();restore("DB_URL",oldUrl);restore("DB_USERNAME",oldUser);restore("DB_PASSWORD",oldPassword);}
 private void restore(String key,String value){if(value==null)System.clearProperty(key);else System.setProperty(key,value);}
 private HttpServletResponse response(){return (HttpServletResponse)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{HttpServletResponse.class},(proxy,method,args)->{throw new AssertionError("Unauthorized response must not be touched: "+method.getName());});}
 @Test void anotherPatientCannotReadPrivateFile(){HttpServletResponse response=response();assertThrows(AppException.class,()->new DocumentService(storage).stream(12,"PATIENT",1,false,response));}
 @Test void administratorCannotReadContent(){HttpServletResponse response=response();assertThrows(AppException.class,()->new DocumentService(storage).stream(30,"ADMIN",1,false,response));}
 @Test void cancelledRelationshipDoesNotAuthorizeDoctor(){HttpServletResponse response=response();assertThrows(AppException.class,()->new DocumentService(storage).stream(22,"DOCTOR",1,false,response));}
 @Test void documentFiltersAndPaginationAreScoped()throws Exception{
  var service=new DocumentService(storage);
  assertEquals(1,service.list(11,"PATIENT",null,"",java.util.Map.of("category","LAB_REPORT","from","2026-01-02","to","2026-01-02")).size());
  assertTrue(service.list(11,"PATIENT",null,"",java.util.Map.of("category","PRESCRIPTION")).isEmpty());
  assertTrue(service.list(11,"PATIENT",null,"",java.util.Map.of("page","2","pageSize","1")).isEmpty());
  assertTrue(service.list(12,"PATIENT",null,"",java.util.Map.of()).isEmpty());
 }
 @Test void rejectsUnboundedOrInvalidFilterValues(){var service=new DocumentService(storage);assertThrows(AppException.class,()->service.list(11,"PATIENT",null,"",java.util.Map.of("pageSize","100000")));assertThrows(AppException.class,()->service.list(11,"PATIENT",null,"",java.util.Map.of("from","invalid")));}

}
