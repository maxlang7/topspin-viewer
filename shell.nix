{ pkgs ? import <nixpkgs> {} }:
pkgs.mkShell {
  packages = [ pkgs.jdk21 pkgs.binutils pkgs.python3 pkgs.gcc pkgs.freetype pkgs.pkg-config ];
  shellHook = ''
    if [ ! -d .venv ]; then
      python -m venv .venv
    fi
  '';
}
