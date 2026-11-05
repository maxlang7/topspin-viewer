#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
if ! command -v javac >/dev/null; then
    exec nix-shell shell.nix --run "bash tests/parity-audit/run-project-export.sh $(printf '%q' "$1")"
fi
vendor="${TOPSPIN_REFERENCE_ROOT:-/home/maxlang/.wine-topspin-5/drive_c/Bruker/TopSpinProcessing5.0.0}"
mapfile -t paths < "$1"
[[ ${#paths[@]} -gt 0 ]]
out="$(dirname "$1")/original-topspin-integrals"
mkdir -p build/parity-audit
javac -proc:none -cp "lib/flatlaf-3.4.1.jar:$vendor/classes/lib/*" -d build/parity-audit src/*.java tests/VendorDifferential.java tests/parity-audit/SavedStateOracle.java
java -Djava.awt.headless=true -cp "build/parity-audit:$vendor/classes/lib/*" SavedStateOracle "$out" "${paths[@]}"
nix-shell rust-shell.nix --run "rustc --edition 2024 -O tests/parity-audit/saved_check.rs -o build/parity-audit/saved-check && build/parity-audit/saved-check $(printf '%q' "$out")"
