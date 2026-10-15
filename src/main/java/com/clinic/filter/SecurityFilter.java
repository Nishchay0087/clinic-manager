package com.clinic.filter;
import jakarta.servlet.*;import jakarta.servlet.annotation.WebFilter;import jakarta.servlet.http.*;import com.clinic.dao.impl.JdbcUserDAO;import java.io.*;import java.security.SecureRandom;import java.util.*;
@WebFilter("/*")
public final class SecurityFilter implements Filter {
 public static String token(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
 @Override public void doFilter(ServletRequest req,ServletResponse res,FilterChain chain)throws IOException,ServletException{
  var r=(HttpServletRequest)req;var p=(HttpServletResponse)res;r.setCharacterEncoding("UTF-8");p.setCharacterEncoding("UTF-8");
  p.setHeader("X-Content-Type-Options","nosniff");p.setHeader("X-Frame-Options","SAMEORIGIN");p.setHeader("Referrer-Policy","same-origin");p.setHeader("Permissions-Policy","camera=(), microphone=(), geolocation=()");
  p.setHeader("Content-Security-Policy","default-src 'self'; script-src 'self' https://cdn.jsdelivr.net; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdn.jsdelivr.net; font-src 'self' https://fonts.gstatic.com https://cdn.jsdelivr.net; img-src 'self' data:; frame-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'");
  String path=r.getRequestURI().substring(r.getContextPath().length());if(path.startsWith("/assets/")){chain.doFilter(r,p);return;}
  p.setHeader("Cache-Control","no-store");var session=r.getSession();if(session.getAttribute("csrf")==null)session.setAttribute("csrf",token());r.setAttribute("csrf",session.getAttribute("csrf"));
  boolean publicPath=Set.of("/","/login","/register","/reset","/directory","/doctors","/api/doctors").contains(path);
  @SuppressWarnings("unchecked") var user=(Map<String,Object>)session.getAttribute("user");
  if(user!=null){try{var fresh=new JdbcUserDAO().byId(((Number)user.get("id")).longValue());if(fresh==null||!"ACTIVE".equals(fresh.get("accountStatus"))||!Objects.equals(user.get("authVersion"),fresh.get("authVersion"))){session.removeAttribute("user");user=null;}else{user=fresh;session.setAttribute("user",fresh);}}catch(Exception e){p.sendError(503,"Database unavailable. Check server configuration.");return;}}
  if(!publicPath&&user==null){if(path.startsWith("/api/")){p.setStatus(401);p.setContentType("application/json");p.getWriter().write("{\"error\":\"Please sign in to continue.\"}");}else p.sendRedirect(r.getContextPath()+"/login");return;}
  if(!Set.of("GET","HEAD","OPTIONS").contains(r.getMethod())){String supplied=r.getHeader("X-CSRF-Token");if(supplied==null&&!Optional.ofNullable(r.getContentType()).orElse("").startsWith("multipart/"))supplied=r.getParameter("csrf");if(!Objects.equals(session.getAttribute("csrf"),supplied)){p.setStatus(403);p.setContentType("application/json");p.getWriter().write("{\"error\":\"Your security token expired. Refresh the page and try again.\"}");return;}}
  chain.doFilter(r,p);
 }
}
