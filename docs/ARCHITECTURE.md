# Architecture

## Request path

`Browser → SecurityFilter → Servlet → Service → JDBC DAO / transactional SQL → DBCP2 → MySQL`

JSPs live under WEB-INF. PageServlet exposes public landing/directory and authenticated application shell; Tomcat's default servlet serves assets. AuthServlet handles form login/registration/logout. ApiServlet dispatches JSON and multipart requests without raw SQL. Services validate domain rules and own transaction boundaries; DAO interfaces isolate reusable persistence queries. Some multi-step transactional SQL remains within services, using the same connection throughout.

Prepared statements bind all user values. Dynamic SQL identifiers and sort choices come only from fixed code. Request sizes, string lengths, file size, enum values, dates and pagination are validated. Error responses omit SQL and stack traces. JDBC connections/statements/result sets are closed using try-with-resources. DBCP2 owns a bounded pool.

## Data model

- `users` contains immutable roles, BCrypt password hashes, status, and credential revision (`auth_version`). One-to-one `patients` and `doctors` hold role-specific fields.
- Doctors have weekly availability and explicit unavailable periods. Appointments join doctors/patients and retain `appointment_history`.
- `consultations` are one per appointment; `prescriptions` store clinician-entered text. Each save snapshots `consultation_versions`. Completed consultations are read-only.
- `medical_documents` stores ownership/metadata/text/hash; UUID files are outside the web root. Archive hides a record without deleting the original.
- `triage_entries` persist queue state and assessment provenance. `audit_logs` retain actor/action/entity/time; they do not contain document text, passwords, or reset tokens.
- `reminder_jobs` has a unique appointment/type constraint; notifications and delivery status commit atomically. `reservations` separately models expiring temporary holds.
- `clinic_settings`, `specialties` and `password_resets` hold operational configuration and hashed, expiring, one-use reset tokens.

See the schema for indexes, FK constraints, CHECKs and the generated unique active-slot column. There is no automatic schema import on app startup.

## Concurrency and scheduling

Appointment creation locks the doctor row then patient row under READ_COMMITTED, validates actual hours, clinic opening times, slot grid, breaks, and doctor/patient overlaps, then inserts and writes history in one transaction. Existing appointment changes take doctor → patient → appointment locks. The generated active slot constraint is a second safeguard against exact-start duplicates; overlap correctness depends on the common doctor locking protocol for every writer. Schedule edits use the same doctor lock. Database writers outside this application must follow that protocol.

Rescheduling preserves clinician and appointment identity, rechecks all rules, and resets persisted reminder jobs to the new lead time. Patient cancellations/reschedules obey `cancellation_hours` and allowed statuses. Unavailable periods and changed hours cannot invalidate existing bookings. Active bookings survive account deactivation and can still be cancelled by an authorized active actor.

Each queue read reconstructs Java's PriorityQueue from persisted rows, scoped to the clinician unless administrator. Comparator order is configured severity, arrival time, stable triage ID. Staff updates lock the same clinical profiles and appointment, then commit queue/audit state together. Patient requests cannot assign severity. Queue objects are request-local, avoiding shared mutable heap races. Checked-in visits initially enter ROUTINE until staff records an assessment.

A single ScheduledExecutorService starts with the webapp and shuts down with it. Reminder and cleanup services additionally guard against overlapping invocations. Reminder consumers use database row locks and SKIP LOCKED; in-app insertion and job acknowledgement are atomic. SMTP is at-least-once because a remote send cannot share the MySQL transaction. Cleanup only expires ACTIVE reservations, writes an audit entry, and never modifies confirmed/completed visits.

Clinic time is Asia/Kolkata by default. Java and MySQL sessions share that offset; appointment dates/times are clinic wall time. Runtime configuration changes require Tomcat restart except persisted clinic settings.

## OOP concepts

- **Inheritance:** abstract User with Patient, Doctor and Admin subclasses.
- **Encapsulation:** private identity fields, getters, and service validation instead of public mutable identity.
- **Abstraction:** IUserDAO, IDoctorDAO, IPatientDAO, IAppointmentDAO, IMedicalDocumentDAO and IConsultationDAO with JDBC implementations.
- **Polymorphism:** overridden role methods, DAO interfaces, an injectable ReminderService.EmailSender, and the triage Comparator used by PriorityQueue.

Map responses are deliberately simple JSON DTOs; authentication and authorization do not trust client roles or the illustrative User subclasses.

## Security boundaries

All nonpublic routes require an active server session. Every API operation enforces its own role/ownership checks. SecurityFilter reloads status/credential revision, verifies CSRF on writes and adds security headers. JSP uses c:out; JavaScript escapes dynamic HTML. CSP restricts scripts to self/approved CDN; style-only inline support is retained for Chart.js and dynamic layouts. HttpOnly, SameSite=Lax cookies and optional Secure flag apply; sessions last 30 minutes and login regenerates their ID.

Patient documents are own-only. Doctors require a care relationship. Administrators receive document metadata and cannot stream contents or read clinical consultation records. Files are extension/MIME/signature/parser validated, capped at 10 MB, UUID named, and streamed only after fresh authorization. No public file URL exists. Extracted text is search material, never an automatic diagnosis or prescription. Output contains no physical storage paths.

Reset tokens are random, SHA-256 hashed at rest, expire in 30 minutes, and consumed in a locking transaction. Password changes/reset invalidate old sessions using `auth_version`; role/status cannot be self-assigned. Login throttling is per-process/IP, appropriate to this single-node demo; multi-node deployments need a shared limiter and trusted proxy configuration.

## Reports

All report queries read persisted appointments, never temporary reservations. Date filters are inclusive clinic dates. Daily volume = count of appointments on each date. Status totals = count per persisted status. Cancellation/no-show rate = matching status count ÷ all appointments in the selected date range × 100 (displayed as unavailable when denominator is zero). Workload = all appointments per doctor with a separate completed count. Registration volume counts PATIENT users by creation date. Specialty volume groups appointment doctor specialties. CSV exports the same filtered groups and escapes quotes/spreadsheet formula prefixes.
