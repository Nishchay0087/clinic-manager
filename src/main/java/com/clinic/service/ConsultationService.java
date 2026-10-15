package com.clinic.service;

import com.clinic.dao.IConsultationDAO;
import com.clinic.dao.impl.JdbcConsultationDAO;
import java.sql.*;
import java.time.*;
import java.util.*;
import static com.clinic.service.AppointmentService.*;

/** Clinical records are visible to their patient and clinicians with a care relationship. */
public class ConsultationService {
    private final IConsultationDAO dao=new JdbcConsultationDAO();
    public Map<String,Object> records(long userId,String role,Long patientId) {
        require("PATIENT".equals(role)||"DOCTOR".equals(role),403,"Clinical records are restricted to patients and their treating clinicians.");
        return transaction(c->{
            long patient;
            if("PATIENT".equals(role)){patient=profile(c,"patients","patient_id",userId);require(patientId==null||patientId==patient,403,"You may only access your own medical records.");}
            else {require(patientId!=null&&patientId>0,400,"Choose a patient.");patient=patientId;
                require(exists(c,"SELECT 1 FROM appointments a JOIN doctors d ON d.doctor_id=a.doctor_id WHERE a.patient_id=? AND d.user_id=? AND a.status NOT IN ('CANCELLED','NO_SHOW')",patient,userId),403,"This patient is outside your care relationship.");
            }
            Map<String,Object> demographics=dao.patient(c,patient);require(demographics!=null,404,"Patient not found.");
            List<Map<String,Object>> records=dao.listByPatient(c,patient);
            audit(c,userId,"CLINICAL_RECORDS_VIEWED","patient",patient);Map<String,Object> result=new LinkedHashMap<>();result.put("patient",demographics);result.put("consultations",records);return result;
        });
    }

    public void save(long userId,String role,Map<String,Object> body) {
        require("DOCTOR".equals(role),403,"Only treating doctors may write clinical records.");
        long appointment=id(body.get("appointmentId"));String notes=string(body,"clinicalNotes",20000,true),diagnosis=string(body,"diagnosis",4000,false),prescription=string(body,"prescription",10000,false);
        LocalDate followUp=body.get("followUpDate")==null||body.get("followUpDate").toString().isBlank()?null:date(body.get("followUpDate"));
        require(followUp==null||!followUp.isBefore(LocalDate.now()),400,"A follow-up date cannot be in the past.");
        boolean complete=Boolean.TRUE.equals(body.get("complete"))||"true".equals(body.get("complete"));
        transaction(c->{Map<String,Object>a=locked(c,appointment,userId,role);require("IN_PROGRESS".equals(a.get("status")),409,"Start this consultation before saving; completed visits are read-only.");
            long patient=((Number)a.get("patientId")).longValue(),doctor=((Number)a.get("doctorId")).longValue();long consultation;
            try(PreparedStatement s=prepare(c,"SELECT consultation_id FROM consultations WHERE appointment_id=? FOR UPDATE",appointment);ResultSet r=s.executeQuery()){
                if(r.next()){consultation=r.getLong(1);execute(c,"UPDATE consultations SET clinical_notes=?,diagnosis=?,follow_up_date=?,updated_at=CURRENT_TIMESTAMP WHERE consultation_id=?",notes,diagnosis,followUp,consultation);}
                else {try(PreparedStatement insert=c.prepareStatement("INSERT INTO consultations(appointment_id,patient_id,doctor_id,clinical_notes,diagnosis,follow_up_date) VALUES(?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){bind(insert,appointment,patient,doctor,notes,diagnosis,followUp);insert.executeUpdate();try(ResultSet keys=insert.getGeneratedKeys()){keys.next();consultation=keys.getLong(1);}}}
            }
            execute(c,"DELETE FROM prescriptions WHERE consultation_id=?",consultation);
            if(!prescription.isBlank())execute(c,"INSERT INTO prescriptions(consultation_id,medication_details,dosage_instructions,duration) VALUES(?,?,?,?)",consultation,prescription,"As recorded by the treating clinician","");
            execute(c,"INSERT INTO consultation_versions(consultation_id,actor_user_id,clinical_notes,diagnosis,prescription,follow_up_date) VALUES(?,?,?,?,?,?)",consultation,userId,notes,diagnosis,prescription,followUp);
            if(complete){execute(c,"UPDATE appointments SET status='COMPLETED',updated_at=CURRENT_TIMESTAMP WHERE appointment_id=?",appointment);history(c,appointment,userId,"IN_PROGRESS","COMPLETED","Consultation completed");execute(c,"UPDATE triage_entries SET queue_status='COMPLETED',updated_at=CURRENT_TIMESTAMP WHERE appointment_id=?",appointment);}
            audit(c,userId,complete?"CONSULTATION_COMPLETED":"CONSULTATION_SAVED","consultation",consultation);return null;
        });
    }

}
