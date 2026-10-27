#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source ./scripts/ensure-jdk.sh
./scripts/init-local.sh
set -a
source .runtime/local.env
source .runtime/crypto.env
source .runtime/storage.env
set +a
jar=snowy-web-app/target/snowy-web-app-3.0.0.jar
[[ -f "$jar" ]] || { echo 'Build first: ./scripts/mvn.sh -T 2 package' >&2; exit 1; }
runtime_jar=".runtime/backend-$(date +%s).jar"
cp "$jar" "$runtime_jar"
chmod 600 "$runtime_jar"
exec java -Xms256m -Xmx1536m -jar "$runtime_jar" --spring.profiles.active=srm-local
