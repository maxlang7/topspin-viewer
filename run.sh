#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
# Build explicitly with ./build-rust.sh. Normal launches never invoke Cargo or Nix.
if [[ ! -x target/release/nmr-viewer ]]; then
  echo 'Build the Rust viewer first: bash build-rust.sh' >&2
  exit 1
fi
if [[ ! -f target/release/runtime-library-path ]]; then
  echo 'Runtime paths missing. Run bash build-rust.sh.' >&2
  exit 1
fi
export LD_LIBRARY_PATH="$(cat target/release/runtime-library-path)${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
exec target/release/nmr-viewer "$@"
