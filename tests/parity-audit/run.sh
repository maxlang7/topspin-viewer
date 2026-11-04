#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
if ! command -v javac >/dev/null; then exec nix-shell shell.nix --run 'bash tests/parity-audit/run.sh'; fi
vendor="${TOPSPIN_REFERENCE_ROOT:-/home/maxlang/.wine-topspin-5/drive_c/Bruker/TopSpinProcessing5.0.0}"
mkdir -p build/parity-audit
javac -proc:none -cp "lib/flatlaf-3.4.1.jar:$vendor/classes/lib/*" -d build/parity-audit src/*.java \
 tests/VendorDifferential.java tests/LiveNumericsReference.java tests/LiveIntegrationReference.java \
 tests/IntegrationEditReference.java tests/IntegrationReopenReference.java tests/IntegrationFileReference.java \
 tests/IntegralScaleReference.java tests/IntegrationUndoReference.java tests/IntegrationSelectionReference.java \
 tests/IntegrationDisplayScaleReference.java tests/IntegrationDialogAddReference.java tests/SaveTransactionReference.java
mkdir -p build/parity-audit/native
gcc -std=c11 -O2 -fPIC -shared -ffp-contract=off -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" -o build/parity-audit/native/libtopspin_reconstructed.so native/kernel.c native/jni.c -lm
# Every assertion compares raw IEEE-754 bits or exact text, not tolerances.
failed=0
check(){ echo "CHECK $1"; if java -Djava.library.path=build/parity-audit/native -Djava.awt.headless=true -cp "build/parity-audit:lib/flatlaf-3.4.1.jar:$vendor/classes/lib/*" "$@"; then echo "RESULT PASS $1"; else echo "RESULT FAIL $1"; failed=$((failed+1)); fi; }
check VendorDifferential
check LiveNumericsReference artifacts/live-jvm-reference
check LiveIntegrationReference artifacts/live-integration-reference artifacts/reference-datasets/captures/MLN1P121/pk-manual
edits="$(cat artifacts/latest-integration-edits-root.txt)"; edits="${edits#Z:}"; edits="${edits//\\//}"
check IntegrationEditReference "$edits"
reopen="$(cat artifacts/latest-integration-reopen-root.txt)"; reopen="${reopen#Z:}"; reopen="${reopen//\\//}"
check IntegrationReopenReference "$reopen" artifacts/viewer-workflow-16708493597640484641/sample/10/pdata/2
for name in IntegrationFileReference IntegralScaleReference IntegrationUndoReference IntegrationSelectionReference IntegrationDisplayScaleReference IntegrationDialogAddReference; do check "$name"; done
# Transaction test reads the source and mutates isolated copies only.
check SaveTransactionReference artifacts/reference-datasets/datasets/MLN1P121/10
printf 'LEGACY_AUDIT_FAILED=%s\n' "$failed"
test "$failed" -eq 0
