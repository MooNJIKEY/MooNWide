#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK (need include/jni.h)}"
if [[ ! -f "$JAVA_HOME/include/jni.h" ]]; then
  echo "[havenbot] ERROR: jni.h not found under \$JAVA_HOME/include" >&2
  exit 1
fi
mkdir -p out
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --parallel
echo "[havenbot] OK: out/libhavenbot.so (or havenbot.dylib on macOS)"
