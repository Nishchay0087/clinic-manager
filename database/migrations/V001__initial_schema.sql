-- MySQL 8.0+; import into an empty clinic_manager database. All times use CLINIC_TIMEZONE.
CREATE TABLE users (
 user_id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(120) NOT NULL, email VARCHAR(190) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL, auth_version BIGINT NOT NULL DEFAULT 0, role VARCHAR(20) NOT NULL, account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 CHECK (role IN ('PATIENT','DOCTOR','ADMIN')), CHECK(account_status IN ('ACTIVE','INACTIVE'))
);
CREATE TABLE doctors (
 doctor_id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL UNIQUE, specialization VARCHAR(100) NOT NULL,
 consultation_fee DECIMAL(10,2) NOT NULL DEFAULT 0, appointment_duration INT NOT NULL DEFAULT 30, profile_description TEXT,
 FOREIGN KEY(user_id) REFERENCES users(user_id), CHECK(appointment_duration BETWEEN 5 AND 120), CHECK(consultation_fee >= 0)
);
CREATE TABLE patients (
 patient_id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL UNIQUE, date_of_birth DATE, blood_group VARCHAR(10),
 emergency_contact VARCHAR(100), allergies TEXT, medications TEXT, FOREIGN KEY(user_id) REFERENCES users(user_id)
);
CREATE TABLE specialties (specialty_id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(100) NOT NULL UNIQUE);
CREATE TABLE clinic_settings (setting_key VARCHAR(80) PRIMARY KEY, setting_value VARCHAR(500) NOT NULL);
INSERT INTO clinic_settings VALUES ('cancellation_hours','2'),('booking_days','90'),('reminder_hours','24'),('appointment_duration','30'),('clinic_name','E-Healthcare Clinic'),('working_start','09:00'),('working_end','18:00'),('triage_priority_order','EMERGENCY,URGENT,ROUTINE');
INSERT INTO specialties(name) VALUES ('General Medicine'),('Cardiology'),('Dermatology'),('Pediatrics'),('Orthopedics');
CREATE TABLE doctor_availability (
 availability_id BIGINT PRIMARY KEY AUTO_INCREMENT, doctor_id BIGINT NOT NULL, day_of_week INT NOT NULL, start_time TIME NOT NULL, end_time TIME NOT NULL,
 FOREIGN KEY(doctor_id) REFERENCES doctors(doctor_id), UNIQUE(doctor_id,day_of_week,start_time), CHECK(day_of_week BETWEEN 1 AND 7), CHECK(end_time>start_time)
);
CREATE TABLE doctor_unavailability (
 unavailability_id BIGINT PRIMARY KEY AUTO_INCREMENT, doctor_id BIGINT NOT NULL, start_datetime DATETIME NOT NULL, end_datetime DATETIME NOT NULL, reason VARCHAR(500),
 FOREIGN KEY(doctor_id) REFERENCES doctors(doctor_id), INDEX(doctor_id,start_datetime,end_datetime), CHECK(end_datetime>start_datetime)
);
CREATE TABLE appointments (
 appointment_id BIGINT PRIMARY KEY AUTO_INCREMENT, patient_id BIGINT NOT NULL, doctor_id BIGINT NOT NULL,
 appointment_date DATE NOT NULL, start_time TIME NOT NULL, end_time TIME NOT NULL, reason_for_visit VARCHAR(1000) NOT NULL, notes TEXT,
 status VARCHAR(20) NOT NULL DEFAULT 'BOOKED', severity_level VARCHAR(20) NOT NULL DEFAULT 'ROUTINE',
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 active_slot TIME GENERATED ALWAYS AS (CASE WHEN status IN ('BOOKED','CONFIRMED','CHECKED_IN','IN_PROGRESS') THEN start_time ELSE NULL END) STORED,
 UNIQUE(doctor_id,appointment_date,active_slot), FOREIGN KEY(patient_id) REFERENCES patients(patient_id), FOREIGN KEY(doctor_id) REFERENCES doctors(doctor_id),
 INDEX(patient_id,appointment_date,status), INDEX(doctor_id,appointment_date,status), INDEX(status,appointment_date), CHECK(end_time>start_time),
 CHECK(status IN ('BOOKED','CONFIRMED','CHECKED_IN','IN_PROGRESS','COMPLETED','CANCELLED','NO_SHOW'))
);
CREATE TABLE appointment_history (
 history_id BIGINT PRIMARY KEY AUTO_INCREMENT, appointment_id BIGINT NOT NULL, actor_user_id BIGINT NOT NULL,
 old_status VARCHAR(20), new_status VARCHAR(20) NOT NULL, change_reason VARCHAR(1000), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(appointment_id) REFERENCES appointments(appointment_id), FOREIGN KEY(actor_user_id) REFERENCES users(user_id)
);
CREATE TABLE consultations (
 consultation_id BIGINT PRIMARY KEY AUTO_INCREMENT, appointment_id BIGINT NOT NULL UNIQUE, patient_id BIGINT NOT NULL, doctor_id BIGINT NOT NULL,
 clinical_notes TEXT NOT NULL, diagnosis TEXT, follow_up_date DATE, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 FOREIGN KEY(appointment_id) REFERENCES appointments(appointment_id), FOREIGN KEY(patient_id) REFERENCES patients(patient_id), FOREIGN KEY(doctor_id) REFERENCES doctors(doctor_id), INDEX(patient_id,created_at)
);
CREATE TABLE prescriptions (
 prescription_id BIGINT PRIMARY KEY AUTO_INCREMENT, consultation_id BIGINT NOT NULL, medication_details TEXT NOT NULL, dosage_instructions TEXT, duration VARCHAR(100),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(consultation_id) REFERENCES consultations(consultation_id)
);
CREATE TABLE consultation_versions (
 version_id BIGINT PRIMARY KEY AUTO_INCREMENT, consultation_id BIGINT NOT NULL, actor_user_id BIGINT NOT NULL, clinical_notes TEXT, diagnosis TEXT, prescription TEXT, follow_up_date DATE,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(consultation_id) REFERENCES consultations(consultation_id), FOREIGN KEY(actor_user_id) REFERENCES users(user_id)
);
CREATE TABLE medical_documents (
 document_id BIGINT PRIMARY KEY AUTO_INCREMENT, patient_id BIGINT NOT NULL, uploaded_by BIGINT NOT NULL, title VARCHAR(200) NOT NULL, description VARCHAR(1000),
 document_type VARCHAR(80) NOT NULL, original_filename VARCHAR(255) NOT NULL, storage_filename VARCHAR(100) NOT NULL UNIQUE,
 mime_type VARCHAR(100) NOT NULL, file_size BIGINT NOT NULL, sha256_hash CHAR(64) NOT NULL, extracted_text MEDIUMTEXT,
 uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, archived BOOLEAN NOT NULL DEFAULT FALSE,
 FOREIGN KEY(patient_id) REFERENCES patients(patient_id), FOREIGN KEY(uploaded_by) REFERENCES users(user_id), INDEX(patient_id,archived,uploaded_at)
);
CREATE TABLE triage_entries (
 triage_id BIGINT PRIMARY KEY AUTO_INCREMENT, patient_id BIGINT NOT NULL, appointment_id BIGINT NOT NULL UNIQUE, severity_level VARCHAR(20) NOT NULL,
 arrival_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, queue_status VARCHAR(20) NOT NULL DEFAULT 'WAITING', assessed_by BIGINT NOT NULL,
 priority_timestamp DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 FOREIGN KEY(patient_id) REFERENCES patients(patient_id), FOREIGN KEY(appointment_id) REFERENCES appointments(appointment_id), FOREIGN KEY(assessed_by) REFERENCES users(user_id), INDEX(queue_status,arrival_time), CHECK(severity_level IN ('EMERGENCY','URGENT','ROUTINE'))
);
CREATE TABLE notifications (
 notification_id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, title VARCHAR(200) NOT NULL, message VARCHAR(1000) NOT NULL, notification_type VARCHAR(40) NOT NULL,
 read_status BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(user_id) REFERENCES users(user_id), INDEX(user_id,read_status,created_at)
);
CREATE TABLE reminder_jobs (
 reminder_id BIGINT PRIMARY KEY AUTO_INCREMENT, appointment_id BIGINT NOT NULL, reminder_type VARCHAR(40) NOT NULL, scheduled_at DATETIME NOT NULL,
 status VARCHAR(30) NOT NULL DEFAULT 'PENDING', attempt_count INT NOT NULL DEFAULT 0, last_attempt_at DATETIME,
 FOREIGN KEY(appointment_id) REFERENCES appointments(appointment_id), UNIQUE(appointment_id,reminder_type), INDEX(status,scheduled_at)
);
CREATE TABLE reservations (
 reservation_id BIGINT PRIMARY KEY AUTO_INCREMENT, doctor_id BIGINT NOT NULL, patient_id BIGINT NOT NULL, start_datetime DATETIME NOT NULL, end_datetime DATETIME NOT NULL,
 expires_at DATETIME NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', FOREIGN KEY(doctor_id) REFERENCES doctors(doctor_id), FOREIGN KEY(patient_id) REFERENCES patients(patient_id), INDEX(status,expires_at)
);
CREATE TABLE audit_logs (
 audit_id BIGINT PRIMARY KEY AUTO_INCREMENT, actor_user_id BIGINT, action VARCHAR(100) NOT NULL, entity_type VARCHAR(80) NOT NULL, entity_id BIGINT,
 metadata VARCHAR(2000), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(actor_user_id) REFERENCES users(user_id), INDEX(created_at)
);
CREATE TABLE password_resets (
 reset_id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, token_hash CHAR(64) NOT NULL UNIQUE, expires_at DATETIME NOT NULL, used BOOLEAN NOT NULL DEFAULT FALSE,
 FOREIGN KEY(user_id) REFERENCES users(user_id)
);
