#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${CLINIC_SERVER_URL:-}" && -f .tools/clinic.env ]]; then
  export CLINIC_SERVER_URL="$(source .tools/clinic.env; printf '%s' "$APP_BASE_URL")"
fi
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
if command -v mvn >/dev/null 2>&1; then
  exec mvn -f desktop/pom.xml javafx:run "$@"
elif [[ -x .tools/apache-maven-3.9.9/bin/mvn ]]; then
  exec .tools/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local="$PWD/.tools/m2" -f desktop/pom.xml javafx:run "$@"
else
  echo 'Install JDK 25+ and Maven 3.9+, then run this script again.' >&2; exit 1
fi
