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
# Keep diagnostics even when a desktop launcher sends stderr to /dev/null.
nmr_log_dir="${XDG_STATE_HOME:-$HOME/.local/state}/nmr-viewer"
mkdir -p "$nmr_log_dir"
nmr_log_file="$nmr_log_dir/viewer-$(date +%Y%m%d-%H%M%S)-$$.log"
exec 2> >(tee -a "$nmr_log_file" >&2)
# Native Wayland has produced intermittent idle ANR reports on this desktop.
# Use the per-application XWayland path when available; preserve an explicit opt-in.
nmr_backend="${NMR_VIEWER_BACKEND:-auto}"
case "$nmr_backend" in
  auto) if [[ -n "${DISPLAY:-}" ]]; then unset WAYLAND_DISPLAY WAYLAND_SOCKET; nmr_backend=x11; else nmr_backend=wayland; fi ;;
  x11) unset WAYLAND_DISPLAY WAYLAND_SOCKET ;;
  wayland) ;;
  *) echo 'NMR_VIEWER_BACKEND must be auto, x11, or wayland' >&2; exit 1 ;;
esac
printf 'NMR_LAUNCH backend=%s pid=%s log=%s\n' "$nmr_backend" "$$" "$nmr_log_file" >&2
exec target/release/nmr-viewer "$@"
