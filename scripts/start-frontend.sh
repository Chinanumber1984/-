#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../snowy-admin-web"
export VITE_API_BASEURL=http://127.0.0.1:8080
export VITE_PORT=5173
exec npm run dev -- --host 127.0.0.1 --strictPort
