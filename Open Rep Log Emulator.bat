@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0open-replog-emulator.ps1"
if errorlevel 1 pause
endlocal
