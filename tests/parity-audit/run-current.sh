#!/usr/bin/env bash
# Exits 1 if the production Rust integrator differs from TopSpin.
set -euo pipefail
cd "$(dirname "$0")/../.."
if ! command -v javac >/dev/null; then exec nix-shell shell.nix --run 'bash tests/parity-audit/run-current.sh'; fi
vendor="${TOPSPIN_REFERENCE_ROOT:-/home/maxlang/.wine-topspin-5/drive_c/Bruker/TopSpinProcessing5.0.0}"
mkdir -p build/parity-audit
python3 - <<'PY'
from pathlib import Path
import struct
p=Path('build/parity-audit/fixtures/synthetic/10/pdata/1');p.mkdir(parents=True,exist_ok=True)
(p/'procs').write_text('##$SI= 4\n##$BYTORDP= 0\n##$NC_proc= 0\n##$SF= 1\n##$SW_p= 4\n##$OFFSET= 4\n')
(p/'1r').write_bytes(struct.pack('<4i',1,2,3,4))
PY
nix-shell rust-shell.nix --run 'rustc --edition 2024 -O tests/parity-audit/current_rust.rs -o build/parity-audit/current-rust && build/parity-audit/current-rust build/parity-audit/fixtures "$HOME/Documents/IGB Lab/NMRs" > build/parity-audit/current-rust.tsv'
javac -proc:none -cp "lib/flatlaf-3.4.1.jar:$vendor/classes/lib/*" -d build/parity-audit src/*.java tests/VendorDifferential.java tests/parity-audit/CurrentRustParity.java
java -Djava.awt.headless=true -cp "build/parity-audit:$vendor/classes/lib/*" CurrentRustParity build/parity-audit/current-rust.tsv
