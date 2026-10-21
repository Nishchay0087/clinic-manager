package com.clinic.service;
import com.clinic.dao.IUserDAO;import com.clinic.exception.AppException;import org.junit.jupiter.api.Test;import org.mindrot.jbcrypt.BCrypt;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class AuthServiceTest {
 static class FakeUsers implements IUserDAO {
  Map<String,Object> user;String role,hash;
  public Map<String,Object> byEmail(String e){return user==null?null:new HashMap<>(user);}
  public Map<String,Object> byId(long i){return byEmail("");}
  public long create(String n,String e,String h,String r,String s,double f){hash=h;role=r;return 1;}
 }
 @Test void registrationAlwaysPatientAndHashed()throws Exception{var dao=new FakeUsers();new AuthService(dao).register("Demo","patient@example.test","Long-password1");assertEquals("PATIENT",dao.role);assertNotEquals("Long-password1",dao.hash);assertTrue(BCrypt.checkpw("Long-password1",dao.hash));}
 @Test void loginRejectsInactiveAndRemovesHash()throws Exception{var dao=new FakeUsers();dao.user=new HashMap<>(Map.of("id",1L,"name","Demo","role","PATIENT","passwordHash",BCrypt.hashpw("Long-password1",BCrypt.gensalt(4)),"accountStatus","INACTIVE"));var service=new AuthService(dao);assertEquals(401,assertThrows(AppException.class,()->service.login("a@b.test","Long-password1","ip")).getStatus());dao.user.put("accountStatus","ACTIVE");assertFalse(service.login("a@b.test","Long-password1","ip").containsKey("passwordHash"));}
 @Test void throttlesAndValidatesPasswords(){var service=new AuthService(new FakeUsers());for(int i=0;i<12;i++)assertThrows(AppException.class,()->service.login("a@b.test","wrong","ip"));assertEquals(429,assertThrows(AppException.class,()->service.login("a@b.test","wrong","ip")).getStatus());assertThrows(AppException.class,()->AuthService.validatePassword("short"));assertThrows(AppException.class,()->AuthService.validatePassword("é".repeat(40)));}
 @Test void roleEnforcement(){assertThrows(AppException.class,()->PortalService.admin("PATIENT"));assertThrows(AppException.class,()->PortalService.admin("DOCTOR"));assertDoesNotThrow(()->PortalService.admin("ADMIN"));}
 @Test void registrationRequiresMatchingConfirmation(){assertThrows(AppException.class,()->AuthService.validateConfirmation("Long-password1","different"));assertThrows(AppException.class,()->AuthService.validateConfirmation("Long-password1",null));assertDoesNotThrow(()->AuthService.validateConfirmation("Long-password1","Long-password1"));}
}
