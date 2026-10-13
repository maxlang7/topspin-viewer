#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
task_vendor_root="${TOPSPIN_REFERENCE_ROOT:-/home/maxlang/.wine-topspin-5/drive_c/Bruker/TopSpinProcessing5.0.0}"
if ! command -v javac >/dev/null; then
  exec nix-shell shell.nix --run 'bash test-vendor.sh'
fi
test -f "$task_vendor_root/classes/lib/top_base.jar"
mkdir -p build/test artifacts
javac -proc:none -cp "$task_vendor_root/classes/lib/*" -d build/test src/Numerics.java tests/VendorDifferential.java
java -Djava.awt.headless=true -cp "build/test:$task_vendor_root/classes/lib/*" VendorDifferential | tee artifacts/vendor-differential.txt
