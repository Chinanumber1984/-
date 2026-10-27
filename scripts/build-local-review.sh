#!/usr/bin/env bash
# Build a runnable review package from Git source without installing Java/Maven/Node on the host.
set -euo pipefail
srm_repo=$(cd "$(dirname "$0")/.." && pwd)
cd "$srm_repo"
command -v python3 >/dev/null || { echo 'Python 3 is required for packaging.' >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo 'Docker access is required for this WSL user.' >&2; exit 1; }
srm_output="${SRM_REVIEW_OUTPUT:-$srm_repo/artifacts/SRM-local-review}"
[[ ! -e "$srm_output" ]] || { echo 'Existing review package retained. Use its start.sh; do not overwrite runtime or data.' >&2; exit 1; }
mkdir -p "$srm_repo/artifacts"
# Keep build files and Maven locks on the Linux filesystem under WSL.
# DrvFS (/mnt/c, /mnt/d) can fail Maven's concurrent file locking.
srm_build_dir=$(mktemp -d "${TMPDIR:-/tmp}/srm-review-build.XXXXXX")
trap 'rm -rf "$srm_build_dir"' EXIT
# Copy only source accepted by Git ignore rules; never copy local runtime credentials or caches.
python3 - "$srm_repo" "$srm_build_dir" <<'PY'
from pathlib import Path
import subprocess,shutil,sys,tarfile,io
source,target=map(Path,sys.argv[1:]);files=subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','-z'],cwd=source).decode().split('\0')
for name in set(filter(None,files)):
 if any(x in {'.runtime','artifacts','node_modules','target','.git','app-log'} for x in Path(name).parts):raise SystemExit('Unexpected private/build path in source list')
# Export committed files from Git's pack instead of thousands of DrvFS reads.
archive=subprocess.check_output(['git','archive','HEAD'],cwd=source)
with tarfile.open(fileobj=io.BytesIO(archive)) as tar:tar.extractall(target,filter='data')
changed=subprocess.check_output(['git','diff','--name-only','HEAD','-z'],cwd=source).decode().split('\0')
untracked=subprocess.check_output(['git','ls-files','--others','--exclude-standard','-z'],cwd=source).decode().split('\0')
for name in set(filter(None,changed+untracked)):
 p=source/name
 if not p.is_file():
  out=target/name
  if out.is_file():out.unlink()
  continue
 out=target/name;out.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,out)
PY
python3 - "$srm_build_dir" <<'PY_SETTINGS'
from pathlib import Path
from urllib.parse import urlsplit
from xml.sax.saxutils import escape
import os,sys
u=urlsplit(os.environ.get('HTTPS_PROXY',os.environ.get('https_proxy','')))
s='<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">'
if u.hostname:s+='<proxies><proxy><id>review</id><active>true</active><protocol>http</protocol><host>'+escape(u.hostname)+'</host><port>'+str(u.port or 80)+'</port><nonProxyHosts>localhost|127.0.0.1</nonProxyHosts></proxy></proxies>'
s+='</settings>';p=Path(sys.argv[1])/'.runtime';p.mkdir(exist_ok=True,mode=0o700);(p/'docker-maven-settings.xml').write_text(s)
PY_SETTINGS
srm_cache="${SRM_MAVEN_CACHE:-${TMPDIR:-/tmp}/srm-review-build-cache-$(id -u)/m2}"
srm_npm_cache="${SRM_NPM_CACHE:-${TMPDIR:-/tmp}/srm-review-build-cache-$(id -u)/npm}"
mkdir -p "$srm_cache" "$srm_npm_cache"
srm_network_args=()
for srm_var in HTTP_PROXY HTTPS_PROXY NO_PROXY http_proxy https_proxy no_proxy; do
 if [[ -n "${!srm_var:-}" ]]; then srm_network_args+=(-e "$srm_var"); fi
done
srm_java_ca=()
if [[ -r /etc/ssl/certs/java/cacerts ]]; then srm_java_ca+=(-v /etc/ssl/certs/java/cacerts:/etc/ssl/certs/java/cacerts:ro -e MAVEN_OPTS=-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts); fi
srm_node_ca=()
if [[ -r /etc/ssl/certs/ca-certificates.crt ]]; then srm_node_ca+=(-v /etc/ssl/certs/ca-certificates.crt:/tmp/srm-host-ca.pem:ro -e NODE_EXTRA_CA_CERTS=/tmp/srm-host-ca.pem); fi
srm_maven_args=(-B -ntp -T 2 -s /src/.runtime/docker-maven-settings.xml -Dmaven.repo.local=/cache/m2)
if [[ "${SRM_MAVEN_OFFLINE:-0}" == 1 ]]; then srm_maven_args+=(-o); fi
docker run --rm --user "$(id -u):$(id -g)" -e MAVEN_CONFIG=/tmp/maven-config -e SRM_TOOL_ROOT=/cache -e SRM_MAVEN_SETTINGS=/src/.runtime/docker-maven-settings.xml "${srm_network_args[@]}" "${srm_java_ca[@]}" \
 -v "$srm_build_dir:/src" -v "$srm_cache:/cache/m2" -w /src \
 maven:3.9.11-eclipse-temurin-17@sha256:e4a7ace3dc0d645ed97f8d9ad0b0d3f0b14fa8d150138f27f116d7105a639b82 bash scripts/mvn.sh "${srm_maven_args[@]}" package
docker run --rm --user "$(id -u):$(id -g)" -e NPM_CONFIG_CACHE=/npm-cache "${srm_network_args[@]}" "${srm_node_ca[@]}" \
 -v "$srm_build_dir:/src" -v "$srm_npm_cache:/npm-cache" -w /src/snowy-admin-web \
 node:22-alpine@sha256:fe4f6d74e20370acb04ad889077d14d0f64a9c8ea6a42bc60cddf1e844699914 \
 sh -c 'npm ci --no-audit --no-fund --maxsockets=5 && npm run workspace:build'
python3 "$srm_build_dir/scripts/package-review.py"
cp -a "$srm_build_dir/artifacts/SRM-local-review" "$srm_output"
echo 'Built real review package. Next: bash artifacts/SRM-local-review/start.sh'
