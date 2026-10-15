package com.clinic.servlet;
import com.clinic.service.*;import com.clinic.document.DocumentService;import com.clinic.triage.TriageService;import com.clinic.exception.AppException;
import com.fasterxml.jackson.databind.ObjectMapper;import jakarta.servlet.annotation.*;import jakarta.servlet.http.*;import java.io.*;import java.util.*;
@WebServlet("/api/*")
@MultipartConfig(maxFileSize=52428800,maxRequestSize=53477376,fileSizeThreshold=0)
public final class ApiServlet extends HttpServlet {
 private final ObjectMapper json=new ObjectMapper();private final PortalService portal=new PortalService();private final AppointmentService appointments=new AppointmentService();private final ConsultationService consultations=new ConsultationService();private final DocumentService documents=new DocumentService();private final TriageService triage=new TriageService();
 @Override protected void service(HttpServletRequest r,HttpServletResponse p)throws IOException{
  p.setContentType("application/json");try{
   @SuppressWarnings("unchecked") var user=(Map<String,Object>)r.getSession().getAttribute("user");long uid=user==null?0:((Number)user.get("id")).longValue();String role=user==null?"PUBLIC":(String)user.get("role");
   String route=Objects.toString(r.getPathInfo(),"/");String[] parts=route.split("/");String resource=parts.length>1?parts[1]:"";long id=parts.length>2&&parts[2].matches("[0-9]+")?Long.parseLong(parts[2]):0;String action=parts.length>3?parts[3]:"";
   Map<String,String> f=new HashMap<>();r.getParameterMap().forEach((k,v)->f.put(k,v[0]));boolean get=r.getMethod().equals("GET");if(!get&&!r.getMethod().equals("POST"))throw new AppException(405,"Unsupported method.");
   Map<String,Object>b=Map.of();if(!get&&!Objects.toString(r.getContentType(),"").startsWith("multipart/")){if(r.getContentLengthLong()>65536)throw new AppException(413,"Request is too large.");byte[] bytes=r.getInputStream().readNBytes(65537);if(bytes.length>65536)throw new AppException(413,"Request is too large.");if(bytes.length>0)b=json.readValue(bytes,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});}
   Object result=Map.of("success",true);
   switch(resource){
    case "me"->{if(!get)throw new AppException(405,"Read only.");result=user;}
    case "dashboard"->{requireGet(get);result=portal.dashboard(uid,role);}
    case "doctors"->{requireGet(get);result=portal.doctors(f);}
    case "slots"->{requireGet(get);result=appointments.slots(Long.parseLong(f.get("doctorId")),java.time.LocalDate.parse(f.get("date")));}
    case "appointments"->{if(get){result=id>0?portal.history(uid,role,id):appointments.list(uid,role,f);}else if(id==0){result=appointments.book(uid,role,b);p.setStatus(201);}else switch(action){case "cancel"->appointments.cancel(uid,role,id);case "reschedule"->appointments.reschedule(uid,role,id,b);case "status"->appointments.status(uid,role,id,Objects.toString(b.get("status"),""));default->throw new AppException(404,"Action not found.");}}
    case "records"->{requireGet(get);result=consultations.records(uid,role,nullable(f.get("patientId")));}
    case "consultations"->{if(get)result=consultations.records(uid,role,nullable(f.get("patientId")));else consultations.save(uid,role,b);}
    case "documents"->{if(get&&id>0){documents.stream(uid,role,id,"true".equals(f.get("download")),p);return;}else if(get)result=documents.list(uid,role,nullable(f.get("patientId")),f.get("q"),f);else if(id>0&&action.equals("archive"))documents.archive(uid,role,id);else if(id==0){result=documents.upload(uid,role,nullable(r.getParameter("patientId")),r.getParameter("category"),r.getParameter("title"),r.getParameter("description"),r.getPart("file"));p.setStatus(201);}else throw new AppException(404,"Action not found.");}
    case "queue"->{if(get)result=triage.list(uid,role);else triage.update(uid,role,number(b,"appointmentId"),Objects.toString(b.get("severity"),"ROUTINE"),Objects.toString(b.get("reason"),""));}
    case "availability"->{if(!role.equals("DOCTOR"))throw new AppException(403,"Doctor access required.");if(get)result=appointments.availability(uid);else appointments.saveAvailability(uid,b);}
    case "unavailability"->{if(get)throw new AppException(405,"Use availability to view breaks.");if(!role.equals("DOCTOR"))throw new AppException(403,"Doctor access required.");appointments.addUnavailability(uid,b);}
    case "users"->{PortalService.admin(role);if(get)result=portal.users(role,f);else if(id>0&&action.equals("doctor"))portal.doctor(uid,role,id,b);else if(id>0&&action.equals("reset"))result=new PasswordResetService().issue(uid,role,id);else if(id>0&&action.equals("status"))portal.userStatus(uid,role,id,Objects.toString(b.get("status"),""));else if(id==0){String newRole=Objects.toString(b.get("role"),"DOCTOR");if(!Set.of("DOCTOR","PATIENT").contains(newRole))throw new AppException(400,"Create a doctor or patient account.");String specialty=PortalService.str(b,"specialization",100,newRole.equals("DOCTOR"));double fee=Double.parseDouble(Objects.toString(b.get("consultationFee"),"0"));if(!Double.isFinite(fee)||fee<0||fee>1000000)throw new AppException(400,"Invalid consultation fee.");long created=new AuthService().create(PortalService.str(b,"name",120,true),PortalService.str(b,"email",190,true),PortalService.str(b,"password",100,true),newRole,specialty,fee,uid);result=Map.of("id",created);}else throw new AppException(404,"Action not found.");}
    case "settings"->{PortalService.admin(role);if(get)result=portal.settings();else portal.settings(uid,role,b);}
    case "reports"->{requireGet(get);result=portal.reports(role,f);if("csv".equals(f.get("format"))){p.setContentType("text/csv; charset=UTF-8");p.setHeader("Content-Disposition","attachment; filename=clinic-report.csv");writeCsv(p,result);return;}}
    case "audit"->{requireGet(get);result=portal.audit(role,f);}
    case "notifications"->{if(get)result=portal.notifications(uid);else portal.readNotifications(uid);}
    case "profile"->{if(get)result=portal.profile(uid);else portal.profile(uid,b);}
    case "password"->{if(get)throw new AppException(405,"POST required.");portal.password(uid,b);r.changeSessionId();}
    default->throw new AppException(404,"Resource not found.");
   }
   json.writeValue(p.getOutputStream(),result);
  }catch(AppException e){error(p,e.getStatus(),e.getMessage());}catch(IllegalStateException e){error(p,413,"The upload exceeds the server request size limit.");}catch(IllegalArgumentException|java.time.DateTimeException|com.fasterxml.jackson.core.JsonProcessingException e){error(p,400,"Please check the submitted values.");}catch(Exception e){getServletContext().log("API failure: "+e.getClass().getSimpleName());error(p,500,"The request could not be completed. Please try again.");}
 }
 private void error(HttpServletResponse p,int status,String message)throws IOException{if(!p.isCommitted()){p.resetBuffer();p.setContentType("application/json");p.setStatus(status);json.writeValue(p.getOutputStream(),Map.of("error",message));}}
 private static void requireGet(boolean get){if(!get)throw new AppException(405,"Read only.");}
 private static Long nullable(String v){return v==null||v.isBlank()?null:Long.valueOf(v);}
 private static long number(Map<String,Object>b,String key){return Long.parseLong(String.valueOf(b.get(key)));}
 @SuppressWarnings("unchecked") private void writeCsv(HttpServletResponse p,Object report)throws IOException{var w=p.getWriter();w.println("Report,Label,Count");for(var e:((Map<String,Object>)report).entrySet()){if(!(e.getValue() instanceof List<?> rows))continue;for(Object o:rows){var row=(Map<String,Object>)o;String label=Objects.toString(row.getOrDefault("date",row.getOrDefault("status",row.getOrDefault("doctor",row.get("specialty")))),"");w.println(csv(e.getKey())+","+csv(label)+","+csv(Objects.toString(row.get("count"),"0")));}}}
 private static String csv(String s){if(s.matches("^[=+@\\-].*"))s="'"+s;return "\""+s.replace("\"","\"\"")+"\"";}
}
