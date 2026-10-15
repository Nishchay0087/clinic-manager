package com.clinic.servlet;
import com.clinic.service.PasswordResetService;import com.clinic.exception.AppException;import jakarta.servlet.*;import jakarta.servlet.annotation.WebServlet;import jakarta.servlet.http.*;import java.io.*;
@WebServlet("/reset") public final class ResetServlet extends HttpServlet {
 protected void doGet(HttpServletRequest r,HttpServletResponse p)throws IOException,ServletException{r.getRequestDispatcher("/WEB-INF/views/reset.jsp").forward(r,p);}
 protected void doPost(HttpServletRequest r,HttpServletResponse p)throws IOException,ServletException{try{new PasswordResetService().reset(r.getParameter("token"),r.getParameter("password"));r.getSession().invalidate();p.sendRedirect(r.getContextPath()+"/login");}catch(AppException e){p.setStatus(e.getStatus());r.setAttribute("error",e.getMessage());doGet(r,p);}catch(Exception e){p.setStatus(500);r.setAttribute("error","Could not reset your password. Please try again.");doGet(r,p);}}
}
