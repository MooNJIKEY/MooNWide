@echo off
setlocal
cd /d "%~dp0"
if not exist "%~dp0Update-MooNWide.ps1" (
  echo [ERROR] Update-MooNWide.ps1 not found.
  pause
  exit /b 1
)
powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0Update-MooNWide.ps1"
set "UPDATER_EXIT=%ERRORLEVEL%"
if "%UPDATER_EXIT%"=="42" (
  echo [MooNWide] Update installed successfully.
) else if not "%UPDATER_EXIT%"=="0" (
  echo [MooNWide] Updater exited with code %UPDATER_EXIT%.
)
pause
exit /b %UPDATER_EXIT%
