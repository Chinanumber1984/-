#!/usr/bin/env bash
# Shared test accounts use single-device login: run these checks sequentially.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f artifacts/SRM-local-review/runtime/account-profile.json ]]; then
 exec node scripts/test-four-account-review.mjs
fi
node scripts/test-sourcing-api.mjs
node scripts/test-sourcing-faults.mjs
node scripts/test-sourcing-awards.mjs
node scripts/test-sourcing-guards.mjs
node scripts/test-sourcing-socket-security.mjs
node scripts/test-sourcing-onboarding-regression.mjs
PLAYWRIGHT_BROWSERS_PATH="${PLAYWRIGHT_BROWSERS_PATH:-/tmp/srm-playwright}" "${SRM_BROWSER_PYTHON:-/tmp/srm-browser-venv/bin/python3}" scripts/test-sourcing-browser.py
if [[ -f .runtime/sourcing/load-actors.json ]]; then
 SRM_REUSE_LOAD_FIXTURE=1 node scripts/test-sourcing-load.mjs
else
 node scripts/test-sourcing-load.mjs
fi
python3 scripts/test-review-upgrade-preservation.py verify
