#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v node >/dev/null
[[ -f workspace/build/workspace.html ]] || { echo 'Build the real workspace first: npm --prefix snowy-admin-web run workspace:build' >&2; exit 1; }
exec node scripts/serve-review.mjs
