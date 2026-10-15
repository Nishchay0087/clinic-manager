package com.clinic.config;
import java.sql.*;
import org.apache.commons.dbcp2.BasicDataSource;
public final class Database {
 private static BasicDataSource pool;
 private Database(){}
 public static synchronized void initialize() {
  if(pool!=null)return;
  var p=new BasicDataSource();
  p.setUrl(value("DB_URL","jdbc:mysql://localhost:3306/clinic_manager?serverTimezone=Asia/Kolkata"));
  p.setDriverClassName(p.getUrl().startsWith("jdbc:mysql:")?"com.mysql.cj.jdbc.Driver":"org.h2.Driver");
  p.setUsername(value("DB_USERNAME","clinic_app"));p.setPassword(value("DB_PASSWORD",""));
  if(p.getUrl().startsWith("jdbc:mysql:")){String zone=java.time.ZoneId.of(value("CLINIC_TIMEZONE","Asia/Kolkata")).getRules().getOffset(java.time.Instant.now()).getId();if(zone.equals("Z"))zone="+00:00";p.setConnectionInitSqls(java.util.List.of("SET time_zone = '"+zone+"'"));}
  p.setInitialSize(0);p.setMaxTotal(16);p.setMaxIdle(8);p.setMaxWaitMillis(10000);p.setValidationQuery("SELECT 1");p.setTestOnBorrow(true);pool=p;
 }
 public static String value(String key,String fallback){return System.getProperty(key,System.getenv().getOrDefault(key,fallback));}
 public static Connection getConnection() throws SQLException {initialize();return pool.getConnection();}
 public static synchronized void close() throws SQLException{if(pool!=null){pool.close();pool=null;}}
}
