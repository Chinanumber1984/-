#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
docker info >/dev/null 2>&1 || { echo 'Start Docker Desktop first.'; exit 1; }
docker run --rm --user "$(id -u):$(id -g)" -v "$PWD:/bundle" -w /bundle node:22-alpine@sha256:fe4f6d74e20370acb04ad889077d14d0f64a9c8ea6a42bc60cddf1e844699914 node bootstrap.mjs
docker compose --env-file runtime/compose.env up -d --wait --wait-timeout 360
docker compose --env-file runtime/compose.env exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot snowy' < runtime/99-accounts.sql
docker compose --env-file runtime/compose.env exec -T gateway node initialize-workflows.mjs
echo 'Ready. Open http://localhost:18080 on this computer. Accounts: runtime/登录账号.txt'
