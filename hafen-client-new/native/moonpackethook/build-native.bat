@echo off
setlocal
cd /d "%~dp0"
if not defined JAVA_HOME (
  echo [moonpackethook] ERROR: Set JAVA_HOME to a JDK ^(need include/jni.h^).
  exit /b 1
)
if not exist "%JAVA_HOME%\include\jni.h" (
  echo [moonpackethook] ERROR: jni.h not found under %%JAVA_HOME%%\include
  exit /b 1
)
if not exist "out" mkdir out
cmake -S . -B build -G "Visual Studio 17 2022" -A x64
if errorlevel 1 (
  cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
  if errorlevel 1 exit /b 1
  cmake --build build
) else (
  cmake --build build --config Release
)
if errorlevel 1 exit /b 1
echo [moonpackethook] OK: out\moonpackethook.dll ^(copy next to hafen.jar or use -Dhaven.moonjni.lib=...^)
exit /b 0
