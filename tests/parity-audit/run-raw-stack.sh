#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
if ! command -v javac >/dev/null; then exec nix-shell shell.nix --run 'bash tests/parity-audit/run-raw-stack.sh'; fi
mkdir -p build/parity-audit/raw-stack/native
python3 - <<'PY'
from pathlib import Path
root=Path.home()/'Documents/IGB Lab/NMRs'
paths={p.parent.resolve() for name in ('proc','procs') for p in root.rglob(name) if p.parent.parent.name=='pdata' and not any(s.startswith('.') for s in p.relative_to(root).parts)}
Path('build/parity-audit/raw-stack/paths.txt').write_text(''.join(str(p)+'\n' for p in sorted(paths)))
PY
javac -proc:none -cp lib/flatlaf-3.4.1.jar -d build/parity-audit src/*.java tests/parity-audit/RawStackOracle.java
gcc -std=c11 -O2 -fPIC -shared -ffp-contract=off -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" -o build/parity-audit/raw-stack/native/libtopspin_reconstructed.so native/kernel.c native/jni.c -lm
java -Djava.library.path=build/parity-audit/raw-stack/native -cp build/parity-audit RawStackOracle build/parity-audit/raw-stack build/parity-audit/raw-stack/paths.txt
nix-shell rust-shell.nix --run 'cargo run --release --features parity-tools --bin raw-parity-check -- build/parity-audit/raw-stack'
