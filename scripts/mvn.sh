#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source ./scripts/ensure-jdk.sh
# Reuse the cloud machine's managed CA trust when the JDK comes from a container.
if [[ -r /etc/ssl/certs/java/cacerts ]]; then
  export MAVEN_OPTS="${MAVEN_OPTS:-} -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi
mkdir -p .runtime
python3 - <<'PY'
import os, pathlib, urllib.parse
from xml.sax.saxutils import escape
u=urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY',os.environ.get('https_proxy','')))
s='<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">'
if u.hostname:
 s+='<proxies><proxy><id>cloud</id><active>true</active><protocol>http</protocol><host>'+escape(u.hostname)+'</host><port>'+str(u.port or 80)+'</port><nonProxyHosts>localhost|127.0.0.1</nonProxyHosts></proxy></proxies>'
s+='</settings>'
pathlib.Path('.runtime/maven-settings.xml').write_text(s)
PY
if command -v mvn >/dev/null; then
  mvn_cmd="$(command -v mvn)"
elif [[ -x /workspace/.tools/apache-maven-3.9.11/bin/mvn ]]; then
  mvn_cmd=/workspace/.tools/apache-maven-3.9.11/bin/mvn
else
  echo 'Maven 3.9+ required; run scripts/install.sh.' >&2
  exit 1
fi
exec "$mvn_cmd" -B -ntp -Daether.connector.connectTimeout=10000 -Daether.connector.requestTimeout=20000 -s .runtime/maven-settings.xml -Dmaven.repo.local="${SRM_TOOL_ROOT:-/workspace/.tools}/m2" "$@"
