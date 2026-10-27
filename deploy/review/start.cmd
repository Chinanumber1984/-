@echo off
chcp 65001 >nul
cd /d "%~dp0"
docker info >nul 2>&1
if errorlevel 1 goto docker_error
docker run --rm -v "%cd%:/bundle" -w /bundle node:22-alpine@sha256:fe4f6d74e20370acb04ad889077d14d0f64a9c8ea6a42bc60cddf1e844699914 node bootstrap.mjs
if errorlevel 1 goto failed
docker compose --env-file runtime/compose.env up -d --wait --wait-timeout 360
if errorlevel 1 goto failed
docker compose --env-file runtime/compose.env exec -T mysql sh -c "MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql --default-character-set=utf8mb4 -uroot snowy" < runtime\99-accounts.sql
if errorlevel 1 goto failed
docker compose --env-file runtime/compose.env exec -T gateway node initialize-workflows.mjs
if errorlevel 1 goto failed
start "" "http://localhost:18080"
echo Ready. Login accounts are in runtime\登录账号.txt
pause
exit /b 0
:docker_error
echo Start Docker Desktop first, then run this file again.
:failed
echo Startup failed. Keep this window open and report the error. Do not send passwords.
pause
exit /b 1
