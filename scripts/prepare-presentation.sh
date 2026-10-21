#!/usr/bin/env bash
# Local fictional demo only. Creates a NEW database; never drops or clears existing data.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -f .tools/demo.env ]]; then echo 'This helper requires the provisioned local demo. See docs/SETUP.md for fresh installation.' >&2; exit 1; fi
set -a; source .tools/demo.env; set +a
DEMO_DATE="${1:-$(TZ=Asia/Kolkata date +%F)}"
python3 - "$DEMO_DATE" <<'PY'
import datetime,sys
assert datetime.date.fromisoformat(sys.argv[1]).isoformat()==sys.argv[1], 'Use YYYY-MM-DD'
PY
MYSQL_HOME="$PWD/.tools/mysql-8.4.11-macos15-arm64"
if ! "$MYSQL_HOME/bin/mysqladmin" --no-defaults --socket="$PWD/.tools/mysql.sock" -uroot ping >/dev/null 2>&1; then
 "$MYSQL_HOME/bin/mysqld" --no-defaults --basedir="$MYSQL_HOME" --datadir="$PWD/.tools/mysql-local" --bind-address=127.0.0.1 --port=3307 --socket="$PWD/.tools/mysql.sock" --mysqlx=OFF --pid-file="$PWD/.tools/mysql.pid" --log-error="$PWD/.tools/mysql.log" --default-time-zone='+05:30' --daemonize
fi
DEMO_DATABASE="clinic_demo_${DEMO_DATE//-/}_$(date +%H%M%S)"
MYSQL=("$MYSQL_HOME/bin/mysql" --no-defaults --socket="$PWD/.tools/mysql.sock" -uroot)
"${MYSQL[@]}" -e "CREATE DATABASE $DEMO_DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; GRANT SELECT,INSERT,UPDATE,DELETE ON $DEMO_DATABASE.* TO 'clinic_app'@'127.0.0.1';"
"${MYSQL[@]}" "$DEMO_DATABASE" < database/schema.sql
python3 - "$DEMO_DATE" <<'PY' > .tools/presentation-seed.sql
from pathlib import Path
import sys
s=Path('database/seed-demo.sql').read_text()
s=s.replace('CURRENT_DATE', "DATE '"+sys.argv[1]+"'")
s=s.replace('NOW()', "TIMESTAMP '"+sys.argv[1]+" 08:45:00'")
print(s)
PY
"${MYSQL[@]}" "$DEMO_DATABASE" < .tools/presentation-seed.sql
# Switch only the ignored local environment; retain previous config for recovery.
cp .tools/demo.env .tools/demo.env.previous
python3 - "$DEMO_DATABASE" <<'PY'
from pathlib import Path
import sys,re
p=Path('.tools/demo.env');s=p.read_text()
s=re.sub(r'^DB_URL=.*$', "DB_URL='jdbc:mysql://127.0.0.1:3307/"+sys.argv[1]+"?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Kolkata'",s,flags=re.M)
p.write_text(s)
PY
chmod 600 .tools/demo.env .tools/demo.env.previous
if curl --fail --silent "$APP_BASE_URL/login" >/dev/null; then
 "$CATALINA_HOME/bin/catalina.sh" stop
 for attempt in {1..30}; do
  if ! curl --fail --silent "$APP_BASE_URL/login" >/dev/null; then break; fi
  sleep 1
 done
fi
./scripts/start-demo.sh
printf 'Fresh fictional demo: %s (date %s)\nPrevious database preserved; configuration backup: .tools/demo.env.previous\n' "$DEMO_DATABASE" "$DEMO_DATE"
