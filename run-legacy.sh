#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if ! command -v javac >/dev/null || ! command -v gcc >/dev/null || ! pkg-config --exists freetype2; then
  printf -v task_run_command '%q ' bash "$PWD/run-legacy.sh" "$@"
  exec nix-shell shell.nix --run "$task_run_command"
fi
mkdir -p build/app build/native
javac -proc:none -cp lib/flatlaf-3.4.1.jar -d build/app src/*.java
gcc -std=c11 -O2 -fPIC -shared -ffp-contract=off \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  -o build/native/libtopspin_reconstructed.so native/kernel.c native/jni.c -lm
gcc -std=c11 -O2 -fPIC -shared $(pkg-config --cflags freetype2) \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  -o build/native/libtopspin_font.so native/font.c $(pkg-config --libs freetype2)
export _JAVA_AWT_WM_NONREPARENTING=1
exec java -Dsun.java2d.uiScale=2 -Djava.library.path=build/native -cp build/app:lib/flatlaf-3.4.1.jar Viewer "$@"
