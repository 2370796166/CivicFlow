@echo off
cd /d "%~dp0"
docker compose stop
echo Containers stopped. Data volumes and development keys are retained.
pause
