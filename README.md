# E-Healthcare Clinic Manager

A Java Servlet/JSP clinic application with patient, doctor, and administrator portals. Uses JDBC and MySQL for persistent records, BCrypt sessions, private PDF storage, a persisted Java PriorityQueue, and scheduled reminders.

## Run the existing local demo

The development environment prepared in this workspace uses Tomcat at **http://localhost:8080/clinic-manager/** and an isolated MySQL instance on **127.0.0.1:3307**. These are local processes, not a public deployment. If they have stopped, use `scripts/start-demo.sh` (macOS Apple Silicon, downloaded `.tools` required). Use `scripts/stop-demo.sh` to stop only these project processes.

| Role | Email | Password (development only) |
|---|---|---|
| Administrator | admin@clinic.demo | ClinicDemo!2026 |
| Doctor | doctor@clinic.demo | ClinicDemo!2026 |
| Patient | patient@clinic.demo | ClinicDemo!2026 |

All seeded names and records are fictional. Never import the demo seed in production. Additional doctors: `cardio@clinic.demo`, `skin@clinic.demo`; additional patients: `priya@clinic.demo`, `kabir@clinic.demo`, `isha@clinic.demo`. They use the same demo password. HTTP smoke tests add clearly fictional accounts and change demo visit state.

## Fresh installation

Use **Java 21 LTS**, **Maven 3.9.9+**, **Tomcat 10.1**, and **MySQL 8.4 LTS**. The project compiles to Java 21 bytecode. This workspace was verified with installed JDK 27, Maven 3.9.9, Tomcat 10.1.39, and MySQL 8.4.11. Prefer a current patched Tomcat 10.1 build for deployment; it is installed separately from the WAR.

1. Create a MySQL database and restricted application user.
2. Import `database/schema.sql`. Optionally import `database/seed-demo.sql` for development.
3. Set the variables in `.env.example`, including private storage outside Tomcat.
4. Run `mvn clean package`.
5. Copy `target/clinic-manager.war` to Tomcat's `webapps/` and start Tomcat with those environment variables.
6. Open `http://localhost:8080/clinic-manager/`.

Detailed Unix and Windows commands, first-admin creation, SMTP setup, and troubleshooting: [docs/SETUP.md](docs/SETUP.md).

## What works

- Patient registration, login/logout, profile/password changes, one-time admin reset links, login throttling, CSRF checks, account deactivation and credential-revision session revocation.
- Searchable doctor directory, real availability, transactional booking/rescheduling/cancellation, appointment history, status transitions, and simultaneous double-booking prevention.
- Role dashboards, day/week/month schedules, working-hour and unavailable-period editing with conflict checks, patient queue, doctor consultations, prescriptions as clinician-authored records, and clinical revision history.
- Private PDF upload, PDFBox extraction, text/category/date searches, authenticated preview/download, SHA-256 metadata, and retention-preserving archive. Admins see metadata only.
- Admin account creation, doctor profiles/fees, clinic rules/specialties, audit logs, reports, Chart.js analytics, and filtered CSV exports.
- Persistent in-app reminders, optional SMTP with retries, and expired temporary reservation cleanup. Cleanup never deletes completed visits or cancels confirmed appointments just because time passed.

## Validation

`mvn test` runs the unit and isolated JDBC integration tests. `mvn package` produces the WAR. See [docs/TESTING.md](docs/TESTING.md) for actual results and optional MySQL/HTTP smoke checks.

Architecture and OOP: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Routes: [docs/API_AND_ROUTES.md](docs/API_AND_ROUTES.md).

## Deliberate limits

- Scanned PDFs are stored and previewable, but OCR is not implemented; the UI says text extraction needs OCR. An OCR adapter can run separately before indexing, with clinical review before any structured fact is recorded.
- SMTP is configurable but no external mail credentials were provided. In-app delivery was tested; real outbound email was not sent. SMTP delivery is at-least-once across a process crash immediately after a send.
- Rescheduling retains the doctor. To change doctors, cancel and book again within clinic rules.
- Completed clinical notes are immutable in the UI; in-progress drafts retain revision history. There is no electronic prescription signing, billing, insurance, or diagnostic automation.
- Reservations have a tested cleanup lifecycle but the booking UI confirms appointments directly; it does not create temporary payment holds.
- Document archival preserves stored files. A clinic-specific legal retention/deletion policy and production backup/restore operations must be configured by the deployment owner.
- Administrative reset links are shown once for direct, trusted handoff to the account holder; there is no unauthenticated email-based forgot-password delivery flow.
- This is a functional college project, not a certification of clinical, privacy, or regulatory compliance.
