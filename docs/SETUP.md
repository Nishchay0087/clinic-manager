# Setup and operations

For the primary native frontend and a clean production database, follow [JavaFX deployment](JAVAFX-DEPLOYMENT.md). The instructions below retain the legacy web/demo setup.

## Requirements

Install JDK 25 LTS (or compatible newer JDK), Maven 3.9.9+, MySQL 8.4, and Apache Tomcat 10.1. The built WAR requires a Java 25+ runtime. No Node or frontend build tool is needed. Chart.js 4.4.7 is vendored locally. Maven pins the servlet, JSTL, JDBC driver, DBCP2, PDFBox, BCrypt, Jackson, Jakarta Mail and test versions in `pom.xml`.

Verify `java -version`, `mvn -version`, and `mysql --version`. Tomcat 9 is incompatible: the application uses `jakarta.servlet`, not `javax.servlet`. Use patched dependency and Tomcat releases when deploying beyond the demo, after testing compatibility.

## Database

Run the following as a database administrator. Replace the sample password; do not use a database root account for the application.

```sql
CREATE DATABASE clinic_manager CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'clinic_app'@'localhost' IDENTIFIED BY 'YOUR_UNIQUE_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE ON clinic_manager.* TO 'clinic_app'@'localhost';
```

Import as the administrator because the application user cannot create tables:

```sh
mysql -u root -p clinic_manager < database/schema.sql
# DEVELOPMENT ONLY, once on a fresh schema:
mysql -u root -p clinic_manager < database/seed-demo.sql
```

On PowerShell, avoid encoding-sensitive file pipelines. Open the mysql client and use its source command:

```powershell
mysql -u root -p clinic_manager
```

```sql
SOURCE C:/path/to/clinic-manager/database/schema.sql;
-- DEVELOPMENT ONLY:
SOURCE C:/path/to/clinic-manager/database/seed-demo.sql;
```

`database/schema.sql` is the combined V001 + V002 schema for manual imports. For tracked production migrations, use `SchemaSetup` as documented in the JavaFX deployment guide instead. Do not combine manual schema imports with the migration runner. Future schema changes require a new migration; the app does not auto-migrate or seed users on startup. Back up the DB and private file store together.

For a clean non-demo installation, generate a BCrypt hash with the provided Java tool after packaging:

```sh
java -cp 'target/clinic-manager/WEB-INF/lib/*' scripts/HashPassword.java
```

It reads the password from the console without echoing. In MySQL, create the initial admin with that hash:

```sql
INSERT INTO users(name,email,password_hash,role) VALUES ('Clinic administrator','you@example.com','PASTE_GENERATED_BCRYPT_HASH','ADMIN');
```

Create doctors/patients through the portal thereafter. Public registration always creates a PATIENT.

## Configuration and launch: macOS/Linux

Copy `.env.example` to `.env`; edit the placeholders. The shell helper sources this file (treat it as trusted shell configuration). For paths with spaces use shell quotes. Alternatively export variables directly:

```sh
export JAVA_HOME=/path/to/jdk-25
export CATALINA_HOME=/path/to/apache-tomcat-10.1
export DB_URL='jdbc:mysql://localhost:3306/clinic_manager?serverTimezone=Asia/Kolkata'
export DB_USERNAME=clinic_app
export DB_PASSWORD='your-unique-password'
export DOCUMENT_STORAGE_DIR='/absolute/private/clinic-documents'
export APP_BASE_URL='http://localhost:8080/clinic-manager'
export CLINIC_TIMEZONE=Asia/Kolkata
export COOKIE_SECURE=false
./scripts/run-local.sh
```

This runs all Maven tests, builds, copies the WAR, and starts Tomcat in the foreground. Ctrl-C stops Tomcat and its scheduler. Or build with `mvn clean package`, copy the WAR manually and use `catalina.sh run`. Setting `MVN_BIN` selects a non-PATH Maven executable.

## Configuration and launch: Windows PowerShell

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25'
$env:CATALINA_HOME='C:\tools\apache-tomcat-10.1'
$env:DB_URL='jdbc:mysql://localhost:3306/clinic_manager?serverTimezone=Asia/Kolkata'
$env:DB_USERNAME='clinic_app'
$env:DB_PASSWORD='your-unique-password'
$env:DOCUMENT_STORAGE_DIR='C:\clinic-private\documents'
$env:APP_BASE_URL='http://localhost:8080/clinic-manager'
$env:CLINIC_TIMEZONE='Asia/Kolkata'
$env:COOKIE_SECURE='false'
.\scripts\run-local.ps1
```

The PowerShell helper uses environment variables; it does not source `.env`. Grant the Tomcat OS account write access to private storage. Never put it in `webapps`, the WAR, or a public static directory. Set `DOCUMENT_MAX_MB` to an integer from 1–50 (default 10); restart Tomcat after changing it. The UI reads this server limit. The private storage default if unset is `~/.clinic-manager/documents`, but explicit deployment configuration is recommended.

## SMTP

Set nonempty `SMTP_HOST`, `SMTP_PORT` (default 587), `SMTP_USERNAME`, `SMTP_PASSWORD`, and `SMTP_FROM`. The sender requires authenticated STARTTLS. Restart Tomcat after configuration changes. With absent/blank credentials, only in-app reminders are generated. Jobs retry failed sends at controlled intervals, maximum three attempts. Review `reminder_jobs` for SENT, RETRY, FAILED, SKIPPED, or CANCELLED. Do not put credentials in Git. Email contains appointment date/time, not clinical notes.

## Cookies and timezone

Use HTTPS and `COOKIE_SECURE=true` for a real deployment. HTTP local development needs `false`. Session cookies are HttpOnly and SameSite=Lax, URL session rewriting is disabled, sessions expire after 30 minutes, and login regenerates the session ID. Password changes/reset increment a credential revision checked on every request. The JDBC pool sets each MySQL session to the clinic timezone offset. This project's default is Asia/Kolkata; DST-changing deployments should restart the pool across an offset transition or configure named MySQL timezone tables and adapt the connection initialization.

## Troubleshooting

- **Database unavailable / API error**: check MySQL process, host/port, user grants, password and schema. Environment variables must be in Tomcat's process environment, not only your IDE. Restart after changes.
- **Public-key retrieval error** on an isolated local MySQL setup: a development-only JDBC URL can add `allowPublicKeyRetrieval=true&useSSL=false`. For production, use TLS and a verified server certificate instead.
- **404**: use `/clinic-manager/`; check Tomcat logs and WAR deployment. JSP views under WEB-INF are deliberately not directly accessible.
- **No slots**: the doctor must set weekly hours, dates must be future and within the booking window, and existing bookings/breaks may occupy slots. Schedule changes must preserve existing visits.
- **403 upload**: use your own patient profile or an assigned doctor's patient. Administrators are metadata-only. Refresh for a fresh CSRF token if needed.
- **PDF fails**: maximum 10 MB; only readable, unencrypted, nonempty valid PDFs are accepted. Scanned image files need OCR for text search.
- **Secure cookie won't persist locally**: set `COOKIE_SECURE=false` for HTTP localhost.
- **MySQL binary crashes during initialization inside a sandbox**: the provided macOS binary requires normal CPU/system calls and should run outside a restrictive execution sandbox. No alternative database is silently substituted.
- **Port collision**: change the Tomcat connector or MySQL port and matching APP_BASE_URL/DB_URL. The workspace demo uses MySQL 3307 to avoid a standard 3306 installation.
