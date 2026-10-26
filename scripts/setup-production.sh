#!/usr/bin/env bash
# Run once with a schema-owner MySQL login; the running backend uses a separate DML-only login.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${DB_URL:?Export the JDBC URL for your empty MySQL database}"
: "${DB_USERNAME:?Export the schema-owner username}"
: "${DB_PASSWORD:?Export the schema-owner password}"
if [[ ! -d target/clinic-manager/WEB-INF/lib ]]; then echo 'Run mvn package first.' >&2; exit 1; fi
java -cp 'target/classes:target/clinic-manager/WEB-INF/lib/*' com.clinic.config.SchemaSetup "$@"
java -cp 'target/classes:target/clinic-manager/WEB-INF/lib/*' com.clinic.config.SetupAdmin
