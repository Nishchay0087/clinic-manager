#!/usr/bin/env bash
# Restarts the already-provisioned, fictional macOS workspace demo; does not initialize/seed data.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -f .tools/demo.env ]]; then echo 'Local tools/config are unavailable. Follow docs/SETUP.md for a fresh installation.' >&2; exit 1; fi
set -a; source .tools/demo.env; set +a
MYSQL_HOME="$PWD/.tools/mysql-8.4.11-macos15-arm64"
if ! "$MYSQL_HOME/bin/mysqladmin" --no-defaults --socket="$PWD/.tools/mysql.sock" -uroot ping >/dev/null 2>&1; then
 "$MYSQL_HOME/bin/mysqld" --no-defaults --basedir="$MYSQL_HOME" --datadir="$PWD/.tools/mysql-local" --bind-address=127.0.0.1 --port=3307 --socket="$PWD/.tools/mysql.sock" --mysqlx=OFF --pid-file="$PWD/.tools/mysql.pid" --log-error="$PWD/.tools/mysql.log" --default-time-zone='+05:30' --daemonize
fi
if curl --fail --silent "$APP_BASE_URL/login" >/dev/null; then echo "Clinic is already running: $APP_BASE_URL/"; exit 0; fi
"$PWD/.tools/apache-maven-3.9.9/bin/mvn" -Dmaven.repo.local=.tools/m2 -q package
cp target/clinic-manager.war "$CATALINA_HOME/webapps/clinic-manager.war"
export CATALINA_PID="$PWD/.tools/tomcat.pid"
"$CATALINA_HOME/bin/catalina.sh" start
echo "Clinic starting at $APP_BASE_URL/"
