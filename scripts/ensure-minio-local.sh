#!/usr/bin/env bash
set -euo pipefail
umask 077
cd "$(dirname "$0")/.."
./scripts/install-minio.sh
mkdir -p .runtime
python3 - <<'PY'
from pathlib import Path
import secrets,subprocess,os,urllib.request,time
root=Path.cwd();runtime=root/'.runtime';config=runtime/'storage.env'
if not config.exists():
 user='srm-local-'+secrets.token_hex(4);password=secrets.token_hex(24)
 config.write_text('SRM_STORAGE_ENGINE=MINIO\nSRM_MINIO_ENDPOINT=http://127.0.0.1:19000\nSRM_MINIO_BUCKET=srm-private\nSRM_MINIO_CREATE_BUCKET=true\nSRM_MINIO_ACCESS_KEY='+user+'\nSRM_MINIO_SECRET_KEY='+password+'\n')
config.chmod(0o600)
values=dict(line.split('=',1) for line in config.read_text().splitlines() if '=' in line and not line.startswith('#'))
if values.get('SRM_STORAGE_ENGINE')!='MINIO':raise SystemExit(0)
# This helper starts only the known loopback development service, never a remote production server.
if values.get('SRM_MINIO_ENDPOINT')!='http://127.0.0.1:19000':raise SystemExit(0)
pidfile=runtime/'minio.pid';binary='/workspace/.tools/minio';owned=False
if pidfile.exists():
 try:owned=binary.encode() in Path('/proc',pidfile.read_text().strip(),'cmdline').read_bytes().split(b'\0')
 except FileNotFoundError:pass
opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
def ready():
 try:
  with opener.open('http://127.0.0.1:19000/minio/health/live',timeout=2) as r:return r.status==200
 except Exception:return False
if ready() and not owned:raise SystemExit('Port 19000 is occupied by an unowned service; not adopting it')
if not owned:
 env=os.environ.copy();env['MINIO_ROOT_USER']=values['SRM_MINIO_ACCESS_KEY'];env['MINIO_ROOT_PASSWORD']=values['SRM_MINIO_SECRET_KEY'];env['MINIO_BROWSER']='off'
 data=runtime/'minio-data';data.mkdir(exist_ok=True)
 with (runtime/'minio.log').open('ab') as log:
  process=subprocess.Popen([binary,'server',str(data),'--address','127.0.0.1:19000'],env=env,stdout=log,stderr=log,start_new_session=True)
 pidfile.write_text(str(process.pid))
for _ in range(60):
 if ready():print('Private local MinIO is ready.');break
 time.sleep(.5)
else:raise SystemExit('MinIO failed to become ready; inspect private .runtime/minio.log')
PY
