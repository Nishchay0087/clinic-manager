// Run only against an isolated empty development database: creates fictional accounts and visits.
import com.clinic.desktop.ClinicClient;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.time.*;
import java.util.*;

class DesktopSmoke {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static void denied(Throwing action,int status)throws Exception{try{action.run();throw new AssertionError("Expected HTTP "+status);}catch(ClinicClient.ApiException e){check(e.status==status,e.getMessage());}}
    interface Throwing {void run()throws Exception;}
    public static void main(String[] args)throws Exception {
        String base=System.getenv("APP_BASE_URL"),password="TestOnly!"+UUID.randomUUID(),suffix=UUID.randomUUID().toString().substring(0,8);
        try(var admin=new ClinicClient(base);var doctor=new ClinicClient(base);var patient=new ClinicClient(base);var other=new ClinicClient(base)){
            admin.login(System.getenv("CLINIC_ADMIN_EMAIL"),System.getenv("CLINIC_ADMIN_PASSWORD"));
            var first=admin.get("dashboard");check(first.path("appointments").isEmpty(),"Expected fresh database with no appointments");
            String email="doctor-"+suffix+"@example.test";
            admin.post("users",Map.of("name","Test Doctor","email",email,"password",password,"role","DOCTOR","specialization","General Medicine","consultationFee","500"));
            doctor.login(email,password);long doctorId=doctor.get("profile").path("doctorId").asLong();
            var weekly=new ArrayList<Map<String,Object>>();for(int day=1;day<=7;day++)weekly.add(Map.of("dayOfWeek",day,"startTime","09:00","endTime","18:00"));
            doctor.post("availability",Map.of("weekly",weekly,"appointmentDuration",15));
            patient.register("Test Patient","patient-"+suffix+"@example.test",password,password);
            other.register("Other Patient","other-"+suffix+"@example.test",password,password);
            check(patient.get("me").path("role").asText().equals("PATIENT"),"Wrong role");denied(()->patient.get("users"),403);
            LocalDate date=LocalDate.now(ZoneId.of("Asia/Kolkata"));var slots=patient.get("slots?doctorId="+doctorId+"&date="+date);
            if(slots.isEmpty())throw new AssertionError("Run during clinic hours, before 17:45 Asia/Kolkata, for same-day clinical workflow");
            JsonNode appointment=patient.post("appointments",Map.of("doctorId",doctorId,"date",date.toString(),"startTime",slots.get(0).asText(),"reason","Automated isolated test"));long id=appointment.path("id").asLong();
            check(doctor.get("appointments").get(0).path("id").asLong()==id,"Doctor cannot see patient booking");
            denied(()->other.post("appointments/"+id+"/cancel",Map.of()),403);
            denied(()->other.post("appointments",Map.of("doctorId",doctorId,"date",date.toString(),"startTime",slots.get(0).asText(),"reason","Conflicting slot")),409);
            doctor.post("appointments/"+id+"/status",Map.of("status","CHECKED_IN"));
            doctor.post("queue",Map.of("appointmentId",id,"severity","EMERGENCY","reason","Fictional test assessment"));
            check(doctor.get("queue").get(0).path("severityLevel").asText().equals("EMERGENCY"),"Priority not persisted");
            doctor.post("appointments/"+id+"/status",Map.of("status","IN_PROGRESS"));
            doctor.post("consultations",Map.of("appointmentId",id,"clinicalNotes","Fictional draft","diagnosis","Test only","complete",false));
            check(patient.get("records").path("consultations").get(0).path("clinicalNotes").asText().equals("Fictional draft"),"Draft missing");
            doctor.post("consultations",Map.of("appointmentId",id,"clinicalNotes","Fictional completed consultation","diagnosis","Test only","complete",true));
            check(doctor.get("queue").isEmpty(),"Completed visit still queued");
            byte[] pdf=Files.readAllBytes(Path.of("docs/fictional-report.pdf"));
            long document=patient.upload(Path.of("docs/fictional-report.pdf"),Map.of("title","Test PDF","category","LAB_REPORT")).path("documentId").asLong();
            check(Arrays.equals(pdf,patient.download("documents/"+document)),"PDF changed");
            check(Arrays.equals(pdf,doctor.download("documents/"+document)),"Treating doctor cannot read PDF");
            denied(()->other.download("documents/"+document),403);denied(()->admin.download("documents/"+document),403);
            patient.post("documents/"+document+"/archive",Map.of());denied(()->patient.download("documents/"+document),404);
            check(admin.download("reports?format=csv").length>0,"CSV missing");
            try(var second=new ClinicClient(base)){second.login("patient-"+suffix+"@example.test",password);patient.post("password",Map.of("currentPassword",password,"newPassword",password+"changed"));denied(()->second.get("profile"),401);}
            doctor.logout();denied(()->doctor.get("profile"),401);
            System.out.println("PASS native Java client: fresh database, admin-created doctor, patient registration, shared booking, ownership, slot conflict, severity, consultation draft/completion, MySQL PDF upload/download/archive, CSV, credential revision and logout.");
        }
    }
}
