package com.clinic.servlet;
import com.clinic.service.AuthService;import com.clinic.exception.AppException;import com.clinic.filter.SecurityFilter;import jakarta.servlet.*;import jakarta.servlet.annotation.WebServlet;import jakarta.servlet.http.*;import java.io.*;
@WebServlet(urlPatterns={"/login","/register","/logout"})
public final class AuthServlet extends HttpServlet {
 private final AuthService auth=new AuthService();
 @Override protected void doGet(HttpServletRequest r,HttpServletResponse p)throws ServletException,IOException{if(r.getServletPath().equals("/logout")){p.sendError(405);return;}r.getRequestDispatcher("/WEB-INF/views"+r.getServletPath()+".jsp").forward(r,p);}
 @Override protected void doPost(HttpServletRequest r,HttpServletResponse p)throws IOException,ServletException{
  String route=r.getServletPath();if(route.equals("/logout")){r.getSession().invalidate();p.sendRedirect(r.getContextPath()+"/login");return;}
  try{if(route.equals("/register")){AuthService.validateConfirmation(r.getParameter("password"),r.getParameter("confirmPassword"));auth.register(r.getParameter("name"),r.getParameter("email"),r.getParameter("password"));}
   var u=auth.login(r.getParameter("email"),r.getParameter("password"),r.getRemoteAddr());r.changeSessionId();r.getSession().setAttribute("user",u);r.getSession().setAttribute("csrf",SecurityFilter.token());p.sendRedirect(r.getContextPath()+"/app");
  }catch(AppException e){p.setStatus(e.getStatus());r.setAttribute("error",e.getMessage());r.getRequestDispatcher("/WEB-INF/views"+route+".jsp").forward(r,p);}catch(Exception e){getServletContext().log("Authentication unavailable: "+e.getClass().getSimpleName());p.setStatus(503);r.setAttribute("error","The database is unavailable. Please try again later.");r.getRequestDispatcher("/WEB-INF/views"+route+".jsp").forward(r,p);}
 }
}
