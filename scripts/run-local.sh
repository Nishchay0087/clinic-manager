#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f .env ]]; then set -a; source .env; set +a; fi
: "${JAVA_HOME:?Set JAVA_HOME to a Java 25+ JDK}"
: "${CATALINA_HOME:?Set CATALINA_HOME to Tomcat 10.1}"
: "${DB_USERNAME:?Set DB_USERNAME}"
: "${DB_PASSWORD:?Set DB_PASSWORD}"
if [[ "${DOCUMENT_STORAGE_MODE:-filesystem}" != mysql ]]; then
  : "${DOCUMENT_STORAGE_DIR:?Set an absolute DOCUMENT_STORAGE_DIR outside Tomcat}"
fi
MVN_BIN="${MVN_BIN:-mvn}"
"$MVN_BIN" clean package
cp target/clinic-manager.war "$CATALINA_HOME/webapps/clinic-manager.war"
exec "$CATALINA_HOME/bin/catalina.sh" run
