@echo off
setlocal
powershell.exe -NoProfile -STA -ExecutionPolicy Bypass -File "%~dp0scripts\open-workbench.ps1" %*
set "task_exit=%ERRORLEVEL%"
if not "%task_exit%"=="0" pause
exit /b %task_exit%
