package com.clinic.service;

import com.clinic.config.*;
import com.clinic.util.Sql;
import com.clinic.document.DocumentService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;

class DesktopSessionsTest {
    @BeforeEach void setup()throws Exception{
        Database.close();System.setProperty("DB_URL","jdbc:h2:mem:desktop_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1");System.setProperty("DB_USERNAME","sa");System.setProperty("DB_PASSWORD","");
        String schema=Files.readString(Path.of("database/schema.sql")).replace(" STORED","").replaceAll("(?m)^--.*$","");
        try(var c=Database.getConnection();var s=c.createStatement()){for(String part:schema.split(";"))if(!part.isBlank())s.execute(part);}
    }
    @AfterEach void close()throws Exception{Database.close();for(String key:List.of("DB_URL","DB_USERNAME","DB_PASSWORD","DOCUMENT_STORAGE_MODE"))System.clearProperty(key);}
    private Map<String,Object> patient()throws Exception{var auth=new AuthService();auth.register("Test Patient","test@example.test","ValidPassword!234");return auth.login("test@example.test","ValidPassword!234","test");}
    @Test void freshSchemaHasNoDemoUsersAndBootstrapNeverOverwrites()throws Exception{
        try(var c=Database.getConnection()){assertEquals(0L,((Number)Sql.one(c,"SELECT COUNT(*) AS n FROM users").get("n")).longValue());}
        assertTrue(SetupAdmin.create("Owner","owner@example.test","ValidPassword!234"));
        assertFalse(SetupAdmin.create("Other","other@example.test","OtherPassword!234"));
        assertEquals("Owner",new AuthService().login("owner@example.test","ValidPassword!234","test").get("name"));
    }
    @Test void tokenSurvivesPoolRestartAndLogoutRevokesIt()throws Exception{
        String token=DesktopSessions.issue(patient());assertEquals(43,token.length());Database.close();assertNotNull(DesktopSessions.resolve(token));
        try(var c=Database.getConnection()){assertNotEquals(token,Sql.one(c,"SELECT token_hash FROM desktop_sessions").get("token_hash"));}
        DesktopSessions.revoke(token);assertNull(DesktopSessions.resolve(token));assertNull(DesktopSessions.resolve("invalid"));
    }
    @Test void passwordRevisionExpiryAndDeactivationRevokeTokens()throws Exception{
        var user=patient();String token=DesktopSessions.issue(user);
        try(var c=Database.getConnection()){Sql.update(c,"UPDATE users SET auth_version=auth_version+1");}assertNull(DesktopSessions.resolve(token));
        user=new AuthService().login("test@example.test","ValidPassword!234","test");token=DesktopSessions.issue(user);
        try(var c=Database.getConnection()){Sql.update(c,"UPDATE desktop_sessions SET expires_at=TIMESTAMP '2020-01-01 00:00:00'");}assertNull(DesktopSessions.resolve(token));
        token=DesktopSessions.issue(user);try(var c=Database.getConnection()){Sql.update(c,"UPDATE users SET account_status='INACTIVE'");}assertNull(DesktopSessions.resolve(token));
    }

    @Test void bearerApiUsesRequestIdentityWithoutCreatingServletSessions()throws Exception{
        String token=DesktopSessions.issue(patient());var attrs=new HashMap<String,Object>();var status=new java.util.concurrent.atomic.AtomicInteger(200);var calls=new java.util.concurrent.atomic.AtomicInteger();
        var request=(HttpServletRequest)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{HttpServletRequest.class},(p,m,a)->switch(m.getName()){
            case "getRequestURI"->"/clinic/api/profile";case "getContextPath"->"/clinic";case "getMethod"->"POST";
            case "getHeader"->a[0].equals("Authorization")?"Bearer "+token:null;
            case "setAttribute"->{attrs.put(a[0].toString(),a[1]);yield null;}case "getAttribute"->attrs.get(a[0]);
            case "getSession"->throw new AssertionError("Native requests must not create instance-local sessions");default->null;
        });
        var response=(HttpServletResponse)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{HttpServletResponse.class},(p,m,a)->switch(m.getName()){
            case "setStatus"->{status.set((Integer)a[0]);yield null;}case "getWriter"->new PrintWriter(new StringWriter());default->null;
        });
        var filter=new com.clinic.filter.SecurityFilter();filter.doFilter(request,response,(r,p)->{assertNotNull(r.getAttribute("authenticatedUser"));calls.incrementAndGet();});assertEquals(1,calls.get());
        DesktopSessions.revoke(token);filter.doFilter(request,response,(r,p)->calls.incrementAndGet());assertEquals(401,status.get());assertEquals(1,calls.get());
    }
    @Test void mysqlDocumentContentSurvivesServiceRecreationAndPreservesAuthorization()throws Exception{
        System.setProperty("DOCUMENT_STORAGE_MODE","mysql");long uid=((Number)patient().get("id")).longValue();byte[] bytes=Files.readAllBytes(Path.of("docs/fictional-report.pdf"));Part file=(Part)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Part.class},(p,m,a)->switch(m.getName()){case "getSize"->(long)bytes.length;case "getInputStream"->new ByteArrayInputStream(bytes);case "getSubmittedFileName"->"report.pdf";case "getContentType"->"application/pdf";default->null;});
        long id=((Number)new DocumentService().upload(uid,"PATIENT",null,"LAB_REPORT","Test report","",file).get("documentId")).longValue();Database.close();var output=new ByteArrayOutputStream();var stream=new ServletOutputStream(){public boolean isReady(){return true;}public void setWriteListener(WriteListener l){}public void write(int b){output.write(b);}};var response=(HttpServletResponse)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{HttpServletResponse.class},(p,m,a)->m.getName().equals("getOutputStream")?stream:null);
        new DocumentService().stream(uid,"PATIENT",id,true,response);assertArrayEquals(bytes,output.toByteArray());
        assertThrows(com.clinic.exception.AppException.class,()->new DocumentService().stream(uid,"ADMIN",id,true,response));
        new DocumentService().archive(uid,"PATIENT",id);assertThrows(com.clinic.exception.AppException.class,()->new DocumentService().stream(uid,"PATIENT",id,true,response));
    }
}
