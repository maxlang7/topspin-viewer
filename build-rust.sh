#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
exec nix-shell rust-shell.nix --run 'cargo build --release && printf "%s\n" "$LD_LIBRARY_PATH" > target/release/runtime-library-path'
