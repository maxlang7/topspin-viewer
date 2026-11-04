{ pkgs ? import (builtins.getFlake "path:/etc/nixos").inputs.nixpkgs { system = builtins.currentSystem; } }:
pkgs.mkShell {
  packages = with pkgs; [ cargo rustc rustfmt clippy pkg-config gcc ];
  LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath (with pkgs; [ wayland libxkbcommon libGL libx11 libxcursor libxi libxrandr ]);
}
