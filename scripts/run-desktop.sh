#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${CLINIC_SERVER_URL:-}" && -f .tools/clinic.env ]]; then
  export CLINIC_SERVER_URL="$(source .tools/clinic.env; printf '%s' "$APP_BASE_URL")"
fi
if [[ -z "${CLINIC_SERVER_URL:-}" && -f .tools/demo.env && ! -f .tools/clinic.env ]]; then
  export CLINIC_SERVER_URL=http://localhost:8083/clinic-manager
  echo 'First-time setup: run ./scripts/start-clinic.sh in another terminal and leave it open.'
fi
# macOS /usr/bin/java may be only Apple's placeholder, even with Homebrew Java installed.
use_jdk() {
  local candidate="$1" version major
  [[ -x "$candidate/bin/java" && -x "$candidate/bin/javac" ]] || return 1
  version="$("$candidate/bin/java" -version 2>&1)" || return 1
  major="$(printf '%s\n' "$version" | sed -nE 's/.*version "([0-9]+).*/\1/p' | head -n 1)"
  [[ "$major" =~ ^[0-9]+$ ]] && (( major >= 25 )) || return 1
  export JAVA_HOME="$candidate"
}
if ! use_jdk "${JAVA_HOME:-}"; then
  unset JAVA_HOME
  candidates=(/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home
    /opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
    /usr/local/opt/openjdk/libexec/openjdk.jdk/Contents/Home
    /usr/local/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home)
  if [[ -x /usr/libexec/java_home ]]; then
    detected="$(/usr/libexec/java_home -v '25+' 2>/dev/null || true)"
    [[ -z "$detected" ]] || candidates+=("$detected")
  fi
  for candidate in "${candidates[@]}"; do
    if use_jdk "$candidate"; then break; fi
  done
  if [[ -z "${JAVA_HOME:-}" ]]; then
    echo 'A JDK 25+ is required. Set JAVA_HOME to its installation directory and retry.' >&2
    echo 'Example on Apple Silicon: export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home' >&2
    exit 1
  fi
fi
export PATH="$JAVA_HOME/bin:$PATH"
echo "Using Java: $JAVA_HOME"
if command -v mvn >/dev/null 2>&1; then
  exec mvn -f desktop/pom.xml javafx:run "$@"
elif [[ -x .tools/apache-maven-3.9.9/bin/mvn ]]; then
  exec .tools/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local="$PWD/.tools/m2" -f desktop/pom.xml javafx:run "$@"
else
  echo 'Install JDK 25+ and Maven 3.9+, then run this script again.' >&2; exit 1
fi
