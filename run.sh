#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if ! command -v javac >/dev/null || ! command -v gcc >/dev/null; then
  printf -v task_run_command '%q ' bash "$PWD/run.sh" "$@"
  exec nix-shell shell.nix --run "$task_run_command"
fi
mkdir -p build/app build/native
javac -proc:none -cp lib/flatlaf-3.4.1.jar -d build/app src/*.java
gcc -std=c11 -O2 -fPIC -shared -ffp-contract=off \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  -o build/native/libtopspin_reconstructed.so native/kernel.c native/jni.c -lm
export _JAVA_AWT_WM_NONREPARENTING=1
exec java -Dsun.java2d.uiScale=2 -Djava.library.path=build/native -cp build/app:lib/flatlaf-3.4.1.jar Viewer "$@"
