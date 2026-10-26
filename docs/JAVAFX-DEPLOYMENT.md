# JavaFX + JDBC + MySQL

The primary frontend is now a native JavaFX desktop application in `desktop/`. It uses JavaFX controls, tables, dialogs and charts, not a WebView. Java services on the server execute JDBC prepared statements and transactions against MySQL. The old web interface remains available for compatibility; it is not the JavaFX frontend.

```text
JavaFX app on Mac / Windows / Linux
       │ HTTPS (opaque login token)
       ▼
Java Servlet backend on Tomcat
       │ JDBC + MySQL Connector/J + DBCP2
       ▼
Shared MySQL database
```

Each client uses the same server URL. Database passwords stay on the backend. Deploying the backend does not make JavaFX run in a browser: users install/run the desktop client. For a viva: “JavaFX handles presentation, the Java backend handles validation and authorization, and JDBC persists data in MySQL.”

## First run on this prepared Mac

In one terminal, run `./scripts/start-clinic.sh`. It creates/reuses a separate `clinic_live` MySQL database, applies migrations and prompts you to choose the first administrator's credentials. It never copies demo accounts or visits. Leave that terminal open; the backend runs on port **8083**. Subsequent starts reuse your records and do not ask you to recreate the administrator.

In a second terminal, run `./scripts/run-desktop.sh`. The helper prefills the new backend URL from the ignored local configuration. Sign in using the administrator you created. Create a doctor, let that doctor configure availability, and register patients normally. Stop the backend with Ctrl+C in its terminal. Existing demo databases and the old port-8081 demo are preserved.

This helper uses the MySQL/JDK/Tomcat tools already installed in this workspace. It is separate from the portable production setup below.

## Run the JavaFX client

Install JDK 25 or newer and Maven 3.9+. On the prepared Mac, the existing `.tools` Maven is supported by the helper:

```sh
./scripts/run-desktop.sh
```

Windows PowerShell:

```powershell
./scripts/run-desktop.ps1
```

Alternatively: `mvn -f desktop/pom.xml javafx:run`. Enter the backend URL in the login screen, including the context path, for example `https://YOUR_PROJECT.vercel.app/clinic-manager`. You can prefill it using `CLINIC_SERVER_URL`. Only the URL is saved in desktop preferences; passwords and login tokens are not persisted on disk. Tokens expire after 24 hours; logout, password changes and account deactivation revoke access.

For two laptops on a trusted LAN, run one backend and one MySQL instance; enter `http://HOST_LAN_IP:8081/clinic-manager` on both clients. Use HTTPS for internet hosting. Each app process has its own login. Refresh a page to see changes made by another user.

## Create an empty real database

The earlier demo was already real MySQL, populated with fictional data. Production setup deliberately does **not** run `seed-demo.sql` or `prepare-presentation.sh`.

1. Provision MySQL 8.0+ with a database named `clinic_manager`. Create a schema-owner account for setup and a separate runtime account. Keep credentials out of Git.
2. Build the backend with `mvn clean package`.
3. Export the schema owner's `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. For a hosted database use its CA/certificate instructions and a URL such as:

   ```text
   jdbc:mysql://YOUR_MYSQL_HOST:3306/clinic_manager?sslMode=VERIFY_IDENTITY&serverTimezone=Asia/Kolkata
   ```

4. Run `./scripts/setup-production.sh`. It applies versioned schema migrations and prompts for the first administrator's name, email and password. It inserts configuration/specialty defaults, but **no patients, doctors, appointments or medical records**. Running it again preserves the existing administrator and password. In a noninteractive environment supply `CLINIC_ADMIN_NAME`, `CLINIC_ADMIN_EMAIL` and `CLINIC_ADMIN_PASSWORD` temporarily, then remove them.
5. Configure the backend runtime account with SELECT, INSERT, UPDATE and DELETE on this database; schema migration permissions are only needed by the setup account.
6. Start the backend and sign into JavaFX as your administrator. Create a doctor, sign in as that doctor and set availability. Register a patient and book a visit. Every row now comes from actual application use.

Windows equivalent after building (classpath separator is `;`):

```powershell
java -cp 'target/classes;target/clinic-manager/WEB-INF/lib/*' com.clinic.config.SchemaSetup
java -cp 'target/classes;target/clinic-manager/WEB-INF/lib/*' com.clinic.config.SetupAdmin
```

Migrations are recorded with checksums in `schema_migrations`. MySQL DDL is not fully transactional: a failed migration is marked incomplete and requires inspection/backup recovery rather than blind automatic retries. Never edit an applied migration. For a database created with the previous V001 schema, back it up, verify the schema, then run setup with `--baseline-existing` to record V001 and apply V002. This flag is **not** for databases already manually imported from the new combined `schema.sql`.

## Vercel backend deployment

Current Vercel documentation describes OCI container Functions via a root `Dockerfile.vercel`. This repository includes one for the Java backend. JavaFX is distributed separately.

1. Provision a persistent hosted MySQL database and initialize it as above. Vercel does not run this MySQL server for you. Choose a provider reachable from the container deployment; check its network rules and TLS requirements.
2. Import the GitHub repository into Vercel using its container deployment path (`Dockerfile.vercel`). Keep the repository root as the project root, not `desktop/`.
3. Configure the variables listed in `deploy/production.env.example` in Vercel. Set `APP_BASE_URL` to your actual HTTPS URL ending in `/clinic-manager`. Set `PORT=8080`, `DOCUMENT_STORAGE_MODE=mysql`, `SCHEDULER_ENABLED=false`, `COOKIE_SECURE=true`, and start with `DB_POOL_SIZE=4`. Do not set first-admin credentials on the running service.
4. Deploy and verify `https://YOUR_PROJECT.vercel.app/clinic-manager/health` returns `{"status":"ok"}`.
5. Put that same base URL (without `/health`) into each JavaFX client.
6. Configure a supported external scheduler to call `GET /clinic-manager/internal/jobs` periodically with `Authorization: Bearer <CRON_SECRET>`. Use a random secret of at least 32 characters. Choose an interval supported by your hosting plan. This endpoint dispatches reminder/cleanup jobs; check server logs for job failures. Do not rely on an in-process timer on instances that can scale to zero.

Desktop tokens are hashed in MySQL. Uploaded PDFs use `document_content` with `DOCUMENT_STORAGE_MODE=mysql`, so they survive instance restarts and are available to all instances. Keep uploads at or below the hosting platform's request-body limits as well as the application limit (10 MB); use smaller PDFs if the host rejects a request before it reaches Java. Configure MySQL packet limits appropriately and include document content in backups. Existing filesystem uploads are **not** automatically migrated when changing storage mode; retain their old storage and migrate separately before switching an existing clinic.

The legacy browser interface uses Tomcat-local sessions and is not the supported horizontally scaled frontend. Use the JavaFX token-based client for this deployment. For password resets, choose “Use an administrator reset link” in JavaFX and paste the privately shared link; the native reset flow also works across instances.

The container configuration is prepared, not proof of a live cloud deployment. Cloud activation needs your Vercel account, container-feature availability, hosted MySQL credentials and a successful cloud smoke test. Docker images and native packages must be built for the target operating system/architecture.

Official references checked for this migration:
- https://openjfx.io/openjfx-docs/ — Maven and JavaFX runtime packaging
- https://vercel.com/kb/guide/docker — container deployment, ports and scale-to-zero
- https://vercel.com/kb/guide/does-vercel-support-docker-deployments — stateless container Functions

## Other hosts and local servers

`Dockerfile` is the equivalent backend image for a Docker-capable host. Build with `docker build -t clinic-backend .`, then run with a private environment file containing production values. MySQL stays external. Alternatively deploy `target/clinic-manager.war` to Tomcat 10.1 on JDK 25+. Configure `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `APP_BASE_URL` and `DOCUMENT_STORAGE_MODE=mysql`. A continuously running single backend may set `SCHEDULER_ENABLED=true` instead of using the jobs endpoint.

## Package the desktop application

On each target platform, run:

```sh
mvn -f desktop/pom.xml clean javafx:jlink
```

This creates `desktop/target/clinic-manager/` and a platform-specific ZIP containing Java and JavaFX. The launcher is `bin/clinic-manager` (`bin/clinic-manager.bat` on Windows). A Mac build is not a Windows build. Signing/notarization and native installers are separate release steps. Maven selects native JavaFX libraries for the machine doing the build.

## Workflow checklist

- Admin creates doctor → doctor sets weekly hours → patient registers → patient books.
- Doctor checks in today's visit → assesses severity → starts consultation → saves draft/completes.
- Patient uploads a PDF → treating doctor downloads it → unrelated users are denied.
- Admin views live reports and exports CSV; audit records persist.
- Restart the backend and confirm the same accounts, documents and records remain.

Tests that create fictional accounts must run only against an isolated development database, never the live clinic. `scripts/DesktopSmoke.java` exercises the actual desktop HTTP client against a fresh server; it requires temporary test-admin environment variables and must run during clinic hours for same-day consultation checks.
