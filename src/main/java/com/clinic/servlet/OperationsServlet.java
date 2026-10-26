package com.clinic.servlet;

import com.clinic.config.Database;
import com.clinic.scheduler.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@WebServlet(urlPatterns={"/health","/internal/jobs"})
public final class OperationsServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest r,HttpServletResponse p)throws IOException{
        p.setContentType("application/json");
        if(r.getServletPath().equals("/internal/jobs")){
            String secret=Database.value("CRON_SECRET","");String actual=r.getHeader("Authorization");
            if(secret.length()<32||actual==null||!MessageDigest.isEqual(("Bearer "+secret).getBytes(StandardCharsets.UTF_8),actual.getBytes(StandardCharsets.UTF_8))){p.setStatus(403);p.getWriter().write("{\"error\":\"Forbidden\"}");return;}
            new ReminderService().run();new BookingCleanupService().run();
            p.getWriter().write("{\"dispatched\":true}");return;
        }
        try(var c=Database.getConnection();var s=c.prepareStatement("SELECT setting_value FROM clinic_settings WHERE setting_key='clinic_name'");var result=s.executeQuery()){
            if(!result.next())throw new IllegalStateException();p.getWriter().write("{\"status\":\"ok\"}");
        }catch(Exception e){p.setStatus(503);p.getWriter().write("{\"status\":\"database_unavailable\"}");}
    }
}
