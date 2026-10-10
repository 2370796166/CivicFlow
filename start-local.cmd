@echo off
cd /d "%~dp0"
powershell.exe -NoProfile -File "%~dp0deploy\local\dev.ps1" up
if errorlevel 1 pause
