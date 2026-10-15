package com.clinic.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.*;
import com.clinic.config.Database;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class SecurityFilterTest {
 private HttpServletRequest request;
 private HttpServletResponse response;
 private HttpSession session;
 private FilterChain chain;
 private Map<String,Object> attributes;
 private Map<String,String> headers;
 private StringWriter output;
 private String uri,method,contentType,headerToken,formToken,redirect;
 private int status,passed,parameterReads;
 @SuppressWarnings("unchecked") private <T>T proxy(Class<T> type,InvocationHandler handler){return (T)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{type},handler);}
 @BeforeEach void setup(){
  attributes=new HashMap<>();headers=new HashMap<>();output=new StringWriter();uri="/clinic/login";method="POST";status=200;passed=0;parameterReads=0;
  session=proxy(HttpSession.class,(p,m,a)->switch(m.getName()){
   case "getAttribute"->attributes.get(a[0]);case "setAttribute"->{attributes.put((String)a[0],a[1]);yield null;}case "removeAttribute"->{attributes.remove(a[0]);yield null;}default->null;
  });
  request=proxy(HttpServletRequest.class,(p,m,a)->switch(m.getName()){
   case "getContextPath"->"/clinic";case "getRequestURI"->uri;case "getMethod"->method;case "getSession"->session;
   case "getContentType"->contentType;case "getHeader"->headerToken;case "getParameter"->{parameterReads++;yield formToken;}default->null;
  });
  response=proxy(HttpServletResponse.class,(p,m,a)->switch(m.getName()){
   case "getWriter"->new PrintWriter(output);case "setHeader"->{headers.put((String)a[0],(String)a[1]);yield null;}
   case "setStatus","sendError"->{status=(Integer)a[0];yield null;}case "sendRedirect"->{redirect=(String)a[0];yield null;}default->null;
  });
  chain=(r,p)->passed++;
 }
 private void filter()throws Exception{new SecurityFilter().doFilter(request,response,chain);}
 @Test void missingCsrfRejectsEvenPublicLoginAndSeedsToken()throws Exception{
  filter();assertEquals(403,status);assertEquals(0,passed);assertTrue(attributes.get("csrf").toString().matches("[A-Za-z0-9_-]{43}"));
 }
 @Test void matchingFormTokenPermitsLoginAndSetsSecurityHeaders()throws Exception{
  attributes.put("csrf","known-token");formToken="known-token";filter();
  assertEquals(1,passed);assertEquals("no-store",headers.get("Cache-Control"));assertEquals("nosniff",headers.get("X-Content-Type-Options"));
 }
 @Test void incorrectHeaderCannotBeOverriddenByMatchingFormToken()throws Exception{
  attributes.put("csrf","known-token");headerToken="wrong-token";formToken="known-token";filter();assertEquals(403,status);assertEquals(0,passed);
 }
 @Test void multipartRequiresHeaderWithoutParsingBody()throws Exception{
  attributes.put("csrf","known-token");contentType="multipart/form-data; boundary=test";formToken="known-token";
  filter();assertEquals(403,status);assertEquals(0,parameterReads);assertEquals(0,passed);
  headerToken="known-token";filter();assertEquals(1,passed);
 }
 @Test void unauthenticatedApiReturns401WhilePageRedirects()throws Exception{
  method="GET";uri="/clinic/api/appointments";filter();
  assertEquals(401,status);assertTrue(output.toString().contains("Please sign in"));assertEquals(0,passed);
  uri="/clinic/app";filter();assertEquals("/clinic/login",redirect);assertEquals(0,passed);
 }
 @Test void publicReadsAndAssetsDoNotRequireAuthentication()throws Exception{
  method="GET";uri="/clinic/api/doctors";filter();assertEquals(1,passed);
  uri="/clinic/assets/js/app.js";filter();assertEquals(2,passed);
 }
 @Test void credentialRevisionAndDeactivationRevokeAuthenticatedSessions()throws Exception{
  var previous=new HashMap<String,String>();for(String key:List.of("DB_URL","DB_USERNAME","DB_PASSWORD"))previous.put(key,System.getProperty(key));
  Database.close();System.setProperty("DB_URL","jdbc:h2:mem:session_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1");System.setProperty("DB_USERNAME","sa");System.setProperty("DB_PASSWORD","");
  try{
   try(var c=Database.getConnection();var s=c.createStatement()){
    s.execute("CREATE TABLE users(user_id BIGINT PRIMARY KEY,name VARCHAR(120),email VARCHAR(190),role VARCHAR(20),account_status VARCHAR(20),auth_version BIGINT)");
    s.execute("INSERT INTO users VALUES(1,'Fictional patient','patient@example.test','PATIENT','ACTIVE',0)");
   }
   method="GET";uri="/clinic/api/appointments";attributes.put("user",Map.of("id",1L,"authVersion",0L));filter();assertEquals(1,passed);
   try(var c=Database.getConnection();var s=c.createStatement()){s.execute("UPDATE users SET auth_version=1 WHERE user_id=1");}
   filter();assertEquals(401,status);assertEquals(1,passed);assertNull(attributes.get("user"));
   attributes.put("user",Map.of("id",1L,"authVersion",1L));filter();assertEquals(2,passed);
   try(var c=Database.getConnection();var s=c.createStatement()){s.execute("UPDATE users SET account_status='INACTIVE' WHERE user_id=1");}
   filter();assertEquals(401,status);assertEquals(2,passed);assertNull(attributes.get("user"));
  }finally{Database.close();previous.forEach((key,value)->{if(value==null)System.clearProperty(key);else System.setProperty(key,value);});}
 }
}
