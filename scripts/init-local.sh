#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .runtime
if [[ ! -f .runtime/local.env ]]; then
  (umask 077; python3 - <<'PY'
import secrets,pathlib
pathlib.Path('.runtime/local.env').write_text('SRM_DB_PASSWORD='+secrets.token_hex(24)+'\n')
PY
  )
fi
docker compose --env-file .runtime/local.env -f deploy/compose.yml up -d --wait --wait-timeout 180

# Development keys are persistent, private local configuration; production must inject managed keys.
if [[ ! -f .runtime/crypto.env ]]; then
  (umask 077; python3 - <<'KEYS'
import secrets,base64,pathlib
p=pathlib.Path('.runtime/crypto.env')
p.write_text('SRM_DATA_KEY_ID=local-v1\n'+''.join(name+'='+base64.b64encode(secrets.token_bytes(size)).decode()+'\n' for name,size in [('SRM_ENCRYPTION_KEY',16),('SRM_MAC_KEY',32),('SRM_INDEX_KEY',32)]))
KEYS
  )
fi
./scripts/migrate-local.sh
./scripts/ensure-minio-local.sh
