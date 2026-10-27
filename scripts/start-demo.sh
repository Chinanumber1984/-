#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../snowy-admin-web"
exec npm run demo -- --host 127.0.0.1
