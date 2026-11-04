#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
if ! command -v javac >/dev/null; then exec nix-shell shell.nix --run 'bash tests/parity-audit/run-native.sh'; fi
vendor="${TOPSPIN_REFERENCE_ROOT:-/home/maxlang/.wine-topspin-5/drive_c/Bruker/TopSpinProcessing5.0.0}"
mkdir -p build/parity-audit
javac -proc:none -cp "lib/flatlaf-3.4.1.jar:$vendor/classes/lib/*" -d build/parity-audit src/*.java tests/VendorDifferential.java tests/IntegralScaleReference.java tests/parity-audit/NativeOracle.java tests/parity-audit/SavedStateOracle.java
java -Djava.awt.headless=true -cp "build/parity-audit:$vendor/classes/lib/*" IntegralScaleReference
java -Djava.awt.headless=true -cp "build/parity-audit:$vendor/classes/lib/*" NativeOracle build/parity-audit/native-oracle
java -Djava.awt.headless=true -cp "build/parity-audit:$vendor/classes/lib/*" SavedStateOracle build/parity-audit/saved-oracle artifacts/reference-datasets/captures/MLN1P121/pk-manual artifacts/viewer-workflow-16708493597640484641/sample/10/pdata/2
nix-shell rust-shell.nix --run 'rustc --edition 2024 -O tests/parity-audit/native_check.rs -o build/parity-audit/native-check && build/parity-audit/native-check build/parity-audit/native-oracle artifacts/live-jvm-reference && rustc --edition 2024 -O tests/parity-audit/saved_check.rs -o build/parity-audit/saved-check && build/parity-audit/saved-check build/parity-audit/saved-oracle'
