#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if ! command -v javac >/dev/null || ! command -v gcc >/dev/null; then
  exec nix-shell shell.nix --run 'bash verify.sh'
fi
mkdir -p build/app build/test build/native
javac -proc:none -cp lib/flatlaf-3.4.1.jar -d build/app src/*.java \
  tests/RawReference.java tests/SavedPhaseReference.java tests/ReaderReference.java \
  tests/WriterReference.java tests/LiveNumericsReference.java
gcc -std=c11 -O2 -fPIC -shared -ffp-contract=off \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  -o build/native/libtopspin_reconstructed.so native/kernel.c native/jni.c -lm
for task_root in artifacts/reference-datasets artifacts/held-out-references artifacts/parameter-references; do
  java -Djava.library.path=build/native -cp build/app RawReference "$task_root"
  java -Djava.library.path=build/native -cp build/app SavedPhaseReference "$task_root/captures"
  java -Djava.library.path=build/native -cp build/app WriterReference "$task_root/captures"
done
java -cp build/app ReaderReference artifacts/reference-datasets/captures
java -cp build/app LiveNumericsReference artifacts/live-jvm-reference
task_vendor_root="${TOPSPIN_REFERENCE_ROOT:-/home/maxlang/.wine-topspin-5/drive_c/Bruker/TopSpinProcessing5.0.0}"
javac -proc:none -cp "$task_vendor_root/classes/lib/*" -d build/test \
  src/Numerics.java src/IntegrationRegions.java tests/VendorDifferential.java tests/IntegrationFileReference.java
java -Djava.awt.headless=true -cp "build/test:$task_vendor_root/classes/lib/*" VendorDifferential
java -Djava.awt.headless=true -cp "build/test:$task_vendor_root/classes/lib/*" IntegrationFileReference
