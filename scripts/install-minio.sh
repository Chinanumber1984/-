#!/usr/bin/env bash
# Development-only MinIO source build; no image-registry credentials are required.
set -euo pipefail
cd "$(dirname "$0")/.."
[[ -x /workspace/.tools/minio ]] && exit 0
[[ "$(uname -s)/$(uname -m)" == Linux/x86_64 ]] || { echo 'This bootstrap targets Linux x86_64'; exit 1; }
mkdir -p /workspace/.tools .runtime
if [[ ! -x /workspace/.tools/golang/go/bin/go ]]; then
  archive=$(mktemp /tmp/srm-go.XXXXXX.tar.gz)
  trap 'rm -f "$archive"' EXIT
  curl -fsSL --retry 2 https://dl.google.com/go/go1.23.12.linux-amd64.tar.gz -o "$archive"
  python3 - "$archive" <<'PY'
import hashlib,tarfile,sys
p=sys.argv[1]
with open(p,'rb') as f:assert hashlib.sha256(f.read()).hexdigest()=='d3847fef834e9db11bf64e3fb34db9c04db14e068eeb064f49af747010454f90','Go checksum mismatch'
with tarfile.open(p) as t:t.extractall('/workspace/.tools/golang',filter='data')
PY
fi
srm_source=/workspace/.tools/minio-source
if [[ ! -d "$srm_source/.git" ]]; then
  git clone --depth 1 --branch RELEASE.2024-12-18T13-15-44Z https://github.com/minio/minio.git "$srm_source"
fi
[[ "$(git -C "$srm_source" rev-parse HEAD)" == 16f8cf1c52f0a77eeb8f7565aaf7f7df12454583 ]] || { echo 'Unexpected MinIO source revision'; exit 1; }
[[ -z "$(git -C "$srm_source" status --porcelain)" ]] || { echo 'MinIO source has local changes; refusing build'; exit 1; }
export GOTOOLCHAIN=local GOPATH=/workspace/.tools/go-cache GOCACHE=/workspace/.tools/go-build CGO_ENABLED=0
export GOPROXY='https://proxy.golang.org|direct' GOSUMDB=sum.golang.org
build() { (cd "$srm_source"; /workspace/.tools/golang/go/bin/go build -mod=readonly -p 4 -trimpath -o /workspace/.tools/minio .); }
if ! build > .runtime/minio-build.log 2>&1; then
  # The platform HTTPS Git proxy accepts .git URLs; Go's dependency-cache remotes omit that suffix.
  python3 - <<'PY'
from pathlib import Path
import subprocess,urllib.parse
for p in Path('/workspace/.tools/go-cache/pkg/mod/cache/vcs').glob('*'):
 if not p.is_dir():continue
 r=subprocess.run(['git','-C',str(p),'remote','get-url','origin'],capture_output=True,text=True)
 if r.returncode:continue
 u=urllib.parse.urlsplit(r.stdout.strip())
 if u.scheme=='https' and u.hostname=='github.com' and not u.path.endswith('.git'):
  subprocess.run(['git','-C',str(p),'remote','set-url','origin','https://github.com'+u.path+'.git'],check=True)
PY
  build >> .runtime/minio-build.log 2>&1 || { echo 'MinIO build failed; inspect private .runtime/minio-build.log'; exit 1; }
fi
echo 'MinIO built from pinned official source with Go module checksum verification.'
