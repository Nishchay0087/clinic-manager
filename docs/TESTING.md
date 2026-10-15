# Test and verification record

## Automated Java suite

Run `mvn test` or `mvn clean package`. Isolated H2 databases in MySQL compatibility mode exercise real JDBC transactions. Only H2's unsupported STORED generated-column keyword is removed in test fixtures. Test databases never use real records. Tests run sequentially across fixtures because Database is a shared static pool; explicit concurrency tests use separate connections and synchronized starts.

Verified in this workspace on October 16, 2026 (Asia/Kolkata): **46 tests, 0 failures, 0 errors, 0 skipped**, and WAR packaging succeeded. JDK 27 compiled with `--release 21`, Maven 3.9.9.

| Class | Tests | Main behavior |
|---|---:|---|
| AuthServiceTest | 4 | BCrypt, forced patient role, active-account login, throttle, roles |
| SecurityFilterTest | 7 | CSRF, anonymous denial, public assets, credential revision and inactive-session revocation |
| PasswordResetServiceTest | 5 | One-use/expiry/roles, hashed tokens, transaction rollback |
| AppointmentServiceTest | 4 | State transitions and domain validation |
| AppointmentIntegrationTest | 8 | Real JDBC booking races, cross-doctor patient overlap, cutoffs, availability, rollback, cancellation and consultation history |
| PdfValidatorTest | 5 | Signature/format/size/encryption validation, PDFBox text/hash |
| DocumentAuthorizationTest | 5 | Own versus other patient, doctor relationship, admin content denial, search/filter validation |
| PatientQueueEntryTest | 3 | Severity and deterministic tie ordering |
| TriageConcurrencyTest | 2 | Concurrent updates and persisted audit/priority consistency |
| ReminderServiceTest | 2 | Reminder eligibility and controlled retry boundaries |
| BookingCleanupServiceTest | 1 | Only expired ACTIVE holds change; audit dedup and record retention |

Reports are generated under `target/surefire-reports/`. JDK 27 emits dependency native-access/deprecation warnings; these are not test failures. Production recommends the requested JDK 21 LTS.

## Real deployment checks

WAR deployed successfully to **Tomcat 10.1.39** with **MySQL 8.4.11**, using an isolated localhost database on port 3307. No database emulation was used for the HTTP tests. Verified JSP compilation and CSS/JS static serving.

`python3 scripts/smoke-test.py` passed **12 workflow groups** against the running fictional demo:

1. Public JSP/assets/live directory and anonymous denial.
2. Three role logins, database dashboards, server authorization, CSRF rejection.
3. Registration ignores attempted ADMIN role escalation.
4. Concurrent real-MySQL double booking has one winner; ownership, reschedule/history and cancellation work.
5. PDF multipart upload, text extraction/search, doctor/patient preview and cross-patient/admin denial.
6. Soft archive prevents retrieval while preserving retention.
7. Staff triage update, start visit, completion and persisted consultation history.
8. Reports, CSV and audit queries.
9. Notification read state and profile persistence.
10. Password change verifies the current password and revokes the prior session.
11. Administrator doctor creation/edit, real availability, clinic settings validation and account deactivation.
12. One-time admin reset revokes an already authenticated session and rejects token reuse.

Run these tests only against fictional development data. The script creates test users/documents/bookings and consumes one checked-in seeded visit. It uses the clinic timezone; repeat after replenishing seed visits if all have been completed. `docs/fictional-report.pdf` is an intentionally synthetic upload fixture. The script requires Python 3.9+ with zoneinfo. If base URL differs, set `APP_BASE_URL`; override `DEMO_PASSWORD` if appropriate.

MySQL background-job smoke check passed: in-app reminder generated exactly once across repeated execution, absent SMTP sends no email, expired ACTIVE reservation becomes EXPIRED, and completed clinical records remain unchanged.

After packaging, run the job smoke with the same DB environment as Tomcat:

```sh
java -cp 'target/classes:target/clinic-manager/WEB-INF/lib/*' scripts/JobSmoke.java
```

Windows classpath uses `;` instead of `:`. This helper creates a fictional same-day 17:00 visit and expired reservation; use a fresh development database and run before 17:00 clinic time. Unit tests cover boundary cases independently of wall clock.

## Verification limits

H2 integration tests complement, rather than replace, the real MySQL smoke run. External SMTP delivery, automated scanned-image OCR, production reverse-proxy/TLS infrastructure, production backup restoration, and multi-node load/stress were not tested. No test used real patient data or sent real email.

Browser visual QA was attempted but unavailable: in-app browser connection failed and native Chrome reported missing Computer Use permissions. No rendered-layout screenshot or interactive browser click verification is claimed. JS syntax was checked separately; HTTP tests exercised the backend behind the UI.
