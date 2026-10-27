@echo off
cd /d "%~dp0"
docker compose --env-file runtime/compose.env stop
pause
