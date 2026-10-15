-- DEVELOPMENT ONLY. All names and medical text below are fictional.
-- Import once into a fresh schema. Every demo password: ClinicDemo!2026
START TRANSACTION;
INSERT INTO users(user_id,name,email,password_hash,role) VALUES
(1,'Alex Morgan','admin@clinic.demo','$2a$12$S5tWcPFIm8c2JEkqJOTHAuRxoBZ3lttsCTxI56fomjQoNTb.eaGt2','ADMIN'),
(2,'Dr. Ananya Sharma','doctor@clinic.demo','$2a$12$S5tWcPFIm8c2JEkqJOTHAuRxoBZ3lttsCTxI56fomjQoNTb.eaGt2','DOCTOR'),
(3,'Dr. Rohan Mehta','cardio@clinic.demo','$2a$12$S5tWcPFIm8c2JEkqJOTHAuRxoBZ3lttsCTxI56fomjQoNTb.eaGt2','DOCTOR'),
(4,'Dr. Maya Rao','skin@clinic.demo','$2a$12$S5tWcPFIm8c2JEkqJOTHAuRxoBZ3lttsCTxI56fomjQoNTb.eaGt2','DOCTOR'),
(5,'Aarav Patel','patient@clinic.demo','$2a$12$S5tWcPFIm8c2JEkqJOTHAuRxoBZ3lttsCTxI56fomjQoNTb.eaGt2','PATIENT'),
(6,'Priya Kapoor','priya@clinic.demo','$2a$12$S5tWcPFIm8c2JEkqJOTHAuRxoBZ3lttsCTxI56fomjQoNTb.eaGt2','PATIENT'),
(7,'Kabir Shah','kabir@clinic.demo','$2a$12$S5tWcPFIm8c2JEkqJOTHAuRxoBZ3lttsCTxI56fomjQoNTb.eaGt2','PATIENT'),
(8,'Isha Verma','isha@clinic.demo','$2a$12$S5tWcPFIm8c2JEkqJOTHAuRxoBZ3lttsCTxI56fomjQoNTb.eaGt2','PATIENT');
INSERT INTO doctors(doctor_id,user_id,specialization,consultation_fee,appointment_duration,profile_description) VALUES
(1,2,'General Medicine',650,30,'Patient-centered primary care, preventive health, and follow-up consultations.'),
(2,3,'Cardiology',1200,30,'Cardiology consultations and ongoing cardiovascular care.'),
(3,4,'Dermatology',850,30,'Personalized consultations for skin, hair, and nail concerns.');
INSERT INTO patients(patient_id,user_id,date_of_birth,blood_group,emergency_contact,allergies,medications) VALUES
(1,5,'1998-05-14','O+','Demo contact — not a real phone','None reported (fictional demo)','None reported (fictional demo)'),
(2,6,'1992-08-21','A+',NULL,'Demo allergy entry',NULL),(3,7,'1985-02-03','B+',NULL,NULL,NULL),(4,8,'2001-11-10','AB+',NULL,NULL,NULL);
INSERT INTO doctor_availability(doctor_id,day_of_week,start_time,end_time)
SELECT d.doctor_id,days.n,'09:00:00','12:00:00' FROM doctors d CROSS JOIN (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7) days;
INSERT INTO doctor_availability(doctor_id,day_of_week,start_time,end_time)
SELECT d.doctor_id,days.n,'13:00:00','18:00:00' FROM doctors d CROSS JOIN (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7) days;
INSERT INTO appointments(appointment_id,patient_id,doctor_id,appointment_date,start_time,end_time,reason_for_visit,status) VALUES
(1,1,1,CURRENT_DATE,'09:00','09:30','Routine follow-up (fictional)','CHECKED_IN'),
(2,2,1,CURRENT_DATE,'09:30','10:00','General consultation (fictional)','CHECKED_IN'),
(3,3,1,CURRENT_DATE,'10:00','10:30','Clinical review (fictional)','CHECKED_IN'),
(4,4,2,CURRENT_DATE,'11:00','11:30','Scheduled review (fictional)','CONFIRMED'),
(5,1,3,DATE_ADD(CURRENT_DATE,INTERVAL 2 DAY),'14:00','14:30','Skin consultation (fictional)','BOOKED'),
(6,2,2,DATE_ADD(CURRENT_DATE,INTERVAL 1 DAY),'10:00','10:30','Follow-up (fictional)','CONFIRMED'),
(7,1,1,DATE_SUB(CURRENT_DATE,INTERVAL 3 DAY),'09:00','09:30','Previous check-up (fictional)','COMPLETED'),
(8,3,3,DATE_SUB(CURRENT_DATE,INTERVAL 2 DAY),'14:00','14:30','Previous consultation (fictional)','COMPLETED'),
(9,4,1,DATE_SUB(CURRENT_DATE,INTERVAL 1 DAY),'10:00','10:30','Cancelled review (fictional)','CANCELLED'),
(10,2,3,DATE_SUB(CURRENT_DATE,INTERVAL 1 DAY),'11:00','11:30','Missed visit (fictional)','NO_SHOW');
INSERT INTO appointment_history(appointment_id,actor_user_id,old_status,new_status,change_reason) SELECT appointment_id,1,NULL,status,'Development seed appointment' FROM appointments;
INSERT INTO consultations(consultation_id,appointment_id,patient_id,doctor_id,clinical_notes,diagnosis,follow_up_date) VALUES
(1,7,1,1,'FICTIONAL DEMO: Routine review recorded by the clinician. No real patient data.','Demonstration record only',DATE_ADD(CURRENT_DATE,INTERVAL 14 DAY)),
(2,8,3,3,'FICTIONAL DEMO: Follow-up discussion recorded.','Demonstration record only',NULL);
INSERT INTO prescriptions(consultation_id,medication_details,dosage_instructions,duration) VALUES (1,'FICTIONAL: No medication prescribed in this demo.','','');
INSERT INTO triage_entries(patient_id,appointment_id,severity_level,arrival_time,assessed_by,priority_timestamp) VALUES
(1,1,'ROUTINE',DATE_SUB(NOW(),INTERVAL 20 MINUTE),2,NOW()),(2,2,'URGENT',DATE_SUB(NOW(),INTERVAL 10 MINUTE),2,NOW()),(3,3,'EMERGENCY',DATE_SUB(NOW(),INTERVAL 5 MINUTE),2,NOW());
INSERT INTO notifications(user_id,title,message,notification_type) VALUES
(5,'Your care, in one place','Welcome to your patient portal. View appointments and keep your medical documents together.','WELCOME'),
(2,'Your clinic day is ready','Review today’s schedule and the patient queue to begin.','WELCOME'),
(1,'Development demo loaded','All accounts and clinical records in this setup are fictional.','SYSTEM');
INSERT INTO audit_logs(actor_user_id,action,entity_type,entity_id,metadata) VALUES(1,'DEMO_IMPORTED','SYSTEM',0,'Fictional development data only');
COMMIT;
