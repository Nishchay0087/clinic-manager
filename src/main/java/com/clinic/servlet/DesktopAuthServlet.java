package com.clinic.servlet;

import com.clinic.service.*;
import com.clinic.exception.AppException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;

/** JSON-only native client authentication, independent of instance-local servlet sessions. */
@WebServlet("/desktop-auth/*")
public final class DesktopAuthServlet extends HttpServlet {
    private final AuthService auth=new AuthService();
    private final ObjectMapper json=new ObjectMapper();
    @Override protected void doPost(HttpServletRequest r,HttpServletResponse p)throws IOException {
        p.setContentType("application/json");
        try {
            // Browser forms cannot set this header; cross-origin preflights are not allowed.
            if(!"javafx".equals(r.getHeader("X-Clinic-Client")) || r.getHeader("Origin")!=null || !Objects.toString(r.getContentType(),"").startsWith("application/json"))
                throw new AppException(403,"Native JSON client required.");
            byte[] bytes=r.getInputStream().readNBytes(8193);
            if(bytes.length>8192)throw new AppException(413,"Request too large.");
            var b=json.readTree(bytes);
            if(b==null||!b.isObject())throw new AppException(400,"A JSON object is required.");
            String route=Objects.toString(r.getPathInfo(),"");
            if(route.equals("/logout")) {
                String h=r.getHeader("Authorization");
                if(h!=null&&h.startsWith("Bearer "))DesktopSessions.revoke(h.substring(7));
                json.writeValue(p.getOutputStream(),Map.of("success",true));return;
            }
            if(route.equals("/reset")){
                AuthService.validateConfirmation(b.path("password").asText(),b.path("confirmPassword").asText());
                new PasswordResetService().reset(b.path("token").asText(),b.path("password").asText());
                json.writeValue(p.getOutputStream(),Map.of("success",true));return;
            }
            if(!Set.of("/login","/register").contains(route))throw new AppException(404,"Unknown authentication action.");
            String email=b.path("email").asText(),password=b.path("password").asText();
            if(route.equals("/register")) {
                AuthService.validateConfirmation(password,b.path("confirmPassword").asText());
                auth.register(b.path("name").asText(),email,password);
            }
            var user=auth.login(email,password,r.getRemoteAddr());
            json.writeValue(p.getOutputStream(),Map.of("token",DesktopSessions.issue(user),"user",user));
        } catch(AppException e){p.setStatus(e.getStatus());json.writeValue(p.getOutputStream(),Map.of("error",e.getMessage()));}
        catch(com.fasterxml.jackson.core.JsonProcessingException e){p.setStatus(400);json.writeValue(p.getOutputStream(),Map.of("error","Invalid JSON."));}
        catch(Exception e){getServletContext().log("Desktop authentication unavailable",e);p.setStatus(503);json.writeValue(p.getOutputStream(),Map.of("error","Authentication unavailable. Check the server database and migrations."));}
    }
}
