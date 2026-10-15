#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -f .tools/demo.env ]]; then echo 'No project-local demo configuration.'; exit 1; fi
set -a; source .tools/demo.env; set +a
"$CATALINA_HOME/bin/catalina.sh" stop
"$PWD/.tools/mysql-8.4.11-macos15-arm64/bin/mysqladmin" --no-defaults --socket="$PWD/.tools/mysql.sock" -uroot shutdown
