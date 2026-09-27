@echo off
cd /d "%~dp0"
docker compose up -d --build --wait --wait-timeout 300
if errorlevel 1 (
  echo Startup failed. Run docker compose logs --tail 80 to inspect.
  pause
  exit /b 1
)
echo CivicFlow is ready at http://localhost:5173
pause
