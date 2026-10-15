package com.clinic.servlet;
import jakarta.servlet.*;import jakarta.servlet.annotation.WebServlet;import jakarta.servlet.http.*;import java.io.*;
@WebServlet(urlPatterns={"","/app","/directory","/doctors"})
public final class PageServlet extends HttpServlet {
 @Override protected void doGet(HttpServletRequest r,HttpServletResponse p)throws ServletException,IOException{
  r.setAttribute("uploadLimitMb",com.clinic.document.PdfValidator.MAX_BYTES/1024/1024);String path=r.getServletPath();String view=switch(path){case "/login"->"login";case "/register"->"register";case "/app"->"app";case "/directory","/doctors"->"directory";default->"landing";};
  if((path.equals("/login")||path.equals("/register"))&&r.getSession().getAttribute("user")!=null){p.sendRedirect(r.getContextPath()+"/app");return;}
  r.getRequestDispatcher("/WEB-INF/views/"+view+".jsp").forward(r,p);
 }
}
