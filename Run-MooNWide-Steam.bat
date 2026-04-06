@echo off
setlocal
cd /d "%~dp0"
REM Same as Run-MooNWide.bat; ensure working directory is the game folder for steam_appid.txt and JAR classpath.
if not exist "hafen.jar" (
  echo [ERROR] hafen.jar not found. Download the full client package and keep all files together.
  pause
  exit /b 1
)
set "JAVA_EXE="
if exist "%~dp0jre\bin\javaw.exe" (
  set "JAVA_EXE=%~dp0jre\bin\javaw.exe"
) else if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" (
  set "JAVA_EXE=%JAVA_HOME%\bin\javaw.exe"
) else (
  where javaw >nul 2>&1
  if errorlevel 1 (
    echo [ERROR] Java not found. Bundle jre\ or set JAVA_HOME/PATH.
    pause
    exit /b 1
  )
  set "JAVA_EXE=javaw"
)
REM steam_appid.txt in this folder is read by Steam API when present.
REM Prefer bundled runtime; fall back to JAVA_HOME or PATH only if it was removed manually.
REM Retail Steam: force direct TCP/UDP (no SOCKS / integration proxy from saved prefs).
set "JAVA_HOSTS_FILE=%~dp0java-hosts-moonwide"
set "JAVA_RUN_FLAGS=-Xmx2048M -Djdk.net.hosts.file=%JAVA_HOSTS_FILE% -Dsun.java2d.uiScale.enabled=false -Djava.net.preferIPv6Addresses=system -Dhaven.prefspec=moonwide -Dhaven.net.game-chain=direct -Dhaven.net.auth-chain=direct --add-exports=java.base/java.lang=ALL-UNNAMED --add-exports=java.desktop/sun.awt=ALL-UNNAMED --add-exports=java.desktop/sun.java2d=ALL-UNNAMED --enable-native-access=ALL-UNNAMED"
"%JAVA_EXE%" %JAVA_RUN_FLAGS% -jar "%~dp0hafen.jar" %*
exit /b %ERRORLEVEL%
