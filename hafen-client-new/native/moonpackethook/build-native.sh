#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p out
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build
echo "[moonpackethook] OK: out/libmoonpackethook.so (or moonpackethook.dylib on macOS)"
