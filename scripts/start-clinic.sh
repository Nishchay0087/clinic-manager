#!/usr/bin/env bash
# First-run convenience for this already-provisioned Mac. Never imports demo data.
# Other machines/hosts: see docs/JAVAFX-DEPLOYMENT.md.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -f .tools/demo.env ]]; then echo 'Use docs/JAVAFX-DEPLOYMENT.md to install MySQL, JDK 25+, Maven and Tomcat first.' >&2; exit 1; fi
set -a; source .tools/demo.env; set +a
MYSQL_HOME="$PWD/.tools/mysql-8.4.11-macos15-arm64"
if ! "$MYSQL_HOME/bin/mysqladmin" --no-defaults --socket="$PWD/.tools/mysql.sock" -uroot ping >/dev/null 2>&1; then
 "$MYSQL_HOME/bin/mysqld" --no-defaults --basedir="$MYSQL_HOME" --datadir="$PWD/.tools/mysql-local" --bind-address=127.0.0.1 --port=3307 --socket="$PWD/.tools/mysql.sock" --mysqlx=OFF --pid-file="$PWD/.tools/mysql.pid" --log-error="$PWD/.tools/mysql.log" --default-time-zone='+05:30' --daemonize
fi
export PATH="$JAVA_HOME/bin:$PATH"
if [[ -f .tools/clinic.env ]]; then set -a; source .tools/clinic.env; set +a; fi
if curl --fail --silent http://localhost:8083/clinic-manager/health >/dev/null; then
 echo 'The clinic backend is already running on http://localhost:8083/clinic-manager. Open a second terminal and run ./scripts/run-desktop.sh'; exit 0
fi
.tools/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=.tools/m2 -q package
# Separate from every demo database. The same clinic_live database is reused on every start.
"$MYSQL_HOME/bin/mysql" --no-defaults --socket="$PWD/.tools/mysql.sock" -uroot -e "CREATE DATABASE IF NOT EXISTS clinic_live CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; GRANT ALL ON clinic_live.* TO 'clinic_app'@'127.0.0.1';"
export DB_URL='jdbc:mysql://127.0.0.1:3307/clinic_live?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Kolkata'
# Always restore least privilege, even if migration or interactive setup fails.
restore_grants() { "$MYSQL_HOME/bin/mysql" --no-defaults --socket="$PWD/.tools/mysql.sock" -uroot -e "REVOKE ALL ON clinic_live.* FROM 'clinic_app'@'127.0.0.1'; GRANT SELECT,INSERT,UPDATE,DELETE ON clinic_live.* TO 'clinic_app'@'127.0.0.1';"; }
trap restore_grants EXIT
java -cp 'target/classes:target/clinic-manager/WEB-INF/lib/*' com.clinic.config.SchemaSetup
java -cp 'target/classes:target/clinic-manager/WEB-INF/lib/*' com.clinic.config.SetupAdmin
restore_grants
trap - EXIT
export CATALINA_BASE="$PWD/.tools/tomcat-clinic"
export APP_BASE_URL=http://localhost:8083/clinic-manager DOCUMENT_STORAGE_MODE=mysql SCHEDULER_ENABLED=true COOKIE_SECURE=false
mkdir -p "$CATALINA_BASE"/{conf,webapps,logs,temp,work}
if [[ ! -f "$CATALINA_BASE/conf/server.xml" ]]; then cp -R "$CATALINA_HOME"/conf/* "$CATALINA_BASE/conf/"; fi
python3 - <<'PY'
from pathlib import Path
import os,shlex
p=Path(os.environ['CATALINA_BASE'])/'conf/server.xml';s=p.read_text().replace('port="8081"','port="8083"').replace('port="8080"','port="8083"').replace('port="18005"','port="18007"');p.write_text(s)
keys=['JAVA_HOME','CATALINA_HOME','CATALINA_BASE','DB_URL','DB_USERNAME','DB_PASSWORD','DOCUMENT_STORAGE_MODE','SCHEDULER_ENABLED','APP_BASE_URL','CLINIC_TIMEZONE','COOKIE_SECURE']
p=Path('.tools/clinic.env');p.write_text('\n'.join(k+'='+shlex.quote(os.environ[k]) for k in keys)+'\n');p.chmod(0o600)
PY
cp target/clinic-manager.war "$CATALINA_BASE/webapps/clinic-manager.war"
echo 'Starting your clinic at http://localhost:8083/clinic-manager. Leave this terminal open.'
echo 'In a second terminal run ./scripts/run-desktop.sh. Stop this backend with Ctrl+C.'
exec "$CATALINA_HOME/bin/catalina.sh" run
