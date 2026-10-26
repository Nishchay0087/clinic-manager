#!/bin/sh
set -eu
: "${DB_URL:?Configure the hosted MySQL JDBC URL}"
: "${DB_USERNAME:?Configure the runtime MySQL username}"
: "${DB_PASSWORD:?Configure the runtime MySQL password}"
: "${APP_BASE_URL:?Configure the public HTTPS URL including /clinic-manager}"
case "${PORT:-8080}" in ''|*[!0-9]*) echo 'PORT must be numeric' >&2; exit 1;; esac
sed -i "s/port=\"8080\"/port=\"${PORT:-8080}\"/" /usr/local/tomcat/conf/server.xml
exec catalina.sh run
