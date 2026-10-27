#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source ./scripts/ensure-jdk.sh
command -v node >/dev/null
mkdir -p /workspace/.tools
if ! command -v mvn >/dev/null && [[ ! -x /workspace/.tools/apache-maven-3.9.11/bin/mvn ]]; then
  work=$(mktemp -d /tmp/srm-maven.XXXXXX)
  trap 'rm -rf "$work"' EXIT
  base=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11
  curl -fsSL --retry 2 "$base/apache-maven-3.9.11-bin.tar.gz" -o "$work/maven.tar.gz"
  curl -fsSL --retry 2 "$base/apache-maven-3.9.11-bin.tar.gz.sha512" -o "$work/maven.sha512"
  python3 - "$work" <<'PY'
import hashlib,pathlib,sys,tarfile
p=pathlib.Path(sys.argv[1]); archive=p/'maven.tar.gz'
if hashlib.sha512(archive.read_bytes()).hexdigest() != (p/'maven.sha512').read_text().strip():
 raise SystemExit('Maven checksum mismatch')
with tarfile.open(archive) as t:t.extractall('/workspace/.tools',filter='data')
PY
fi
npm --prefix snowy-admin-web ci --cache /workspace/.tools/npm-cache --no-audit --no-fund
./scripts/mvn.sh -T 2 package
npm --prefix snowy-admin-web run build

npm --prefix snowy-admin-web run demo:test
npm --prefix snowy-admin-web run demo:build

npm --prefix snowy-admin-web run workspace:build
./scripts/install-minio.sh
