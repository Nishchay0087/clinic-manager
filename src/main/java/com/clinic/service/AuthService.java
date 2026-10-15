package com.clinic.service;
import com.clinic.dao.IUserDAO;import com.clinic.dao.impl.JdbcUserDAO;import com.clinic.exception.AppException;import org.mindrot.jbcrypt.BCrypt;import java.sql.*;import java.util.*;import java.util.concurrent.*;
public final class AuthService {
 private final IUserDAO users;private final ConcurrentHashMap<String,Attempt> attempts=new ConcurrentHashMap<>();
 private record Attempt(int count,long until){}
 public AuthService(){this(new JdbcUserDAO());} public AuthService(IUserDAO users){this.users=users;}
 public Map<String,Object> login(String email,String password,String ip)throws SQLException{
  String normalized=email==null?"":email.strip().toLowerCase(Locale.ROOT);String key=ip;
  long now=System.currentTimeMillis();attempts.entrySet().removeIf(e->e.getValue().until<now);
  Attempt a=attempts.compute(key,(k,old)->new Attempt(old==null?1:old.count+1,old==null?now+900000:old.until));
  if(a.count>12)throw new AppException(429,"Too many login attempts. Try again in 15 minutes.");
  var u=users.byEmail(normalized);String hash=u==null?"$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW":(String)u.get("passwordHash");
  boolean valid=password!=null&&password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=72&&BCrypt.checkpw(password,hash);
  if(u==null||!valid||!"ACTIVE".equals(u.get("accountStatus")))throw new AppException(401,"Email or password is incorrect, or the account is inactive.");
  attempts.remove(key);u.remove("passwordHash");return u;
 }
 public long register(String name,String email,String password)throws SQLException{return create(name,email,password,"PATIENT",null,0);}
 public long create(String name,String email,String password,String role,String specialty,double fee)throws SQLException{return create(name,email,password,role,specialty,fee,null);}
 public long create(String name,String email,String password,String role,String specialty,double fee,Long actor)throws SQLException{
  if(name==null||name.isBlank()||name.length()>120)throw new AppException(400,"Enter a name of 1–120 characters.");
  if(email==null||email.length()>190||!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw new AppException(400,"Enter a valid email address.");
  validatePassword(password);
  if(!Set.of("PATIENT","DOCTOR","ADMIN").contains(role))throw new AppException(400,"Invalid account role.");
  try{return users.create(name.strip(),email.strip().toLowerCase(Locale.ROOT),BCrypt.hashpw(password,BCrypt.gensalt(12)),role,specialty,fee,actor);}
  catch(SQLIntegrityConstraintViolationException e){throw new AppException(409,"An account with that email already exists.");}
 }
 public static void validatePassword(String password){if(password==null||password.length()<10||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new AppException(400,"Use at least 10 characters and at most 72 UTF-8 bytes for your password.");}
}
