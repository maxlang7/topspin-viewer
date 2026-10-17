"""Capture only the known text-test window; source datasets and license services are unused."""
import glob
import json
import os
from pathlib import Path
import subprocess
import time

destination = Path("artifacts/windows-native-text-reference-v2.png")
if destination.exists():
    raise SystemExit("Reference already exists; choose a new capture filename before regenerating it")
wine = Path.home() / ".local/share/topspin-wine-runtime/wine-x11/bin/wine"
installation = Path.home() / ".wine-topspin-5/drive_c/Bruker/TopSpinProcessing5.0.0"
process = subprocess.Popen([
    str(wine), str(installation / "jre/bin/java.exe"), "-Dsun.java2d.uiScale=2",
    "-cp", "Z:" + str(Path("build/test").resolve()).replace("/", "\\"), "WindowsTextReference",
], env=dict(os.environ, WINEPREFIX=str(Path.home()/".wine-topspin-5"), WINEDEBUG="-all", WAYLAND_DISPLAY=""))
try:
    for attempt in range(100):
        windows = json.loads(subprocess.check_output(["hyprctl", "clients", "-j"]))
        own = next((w for w in windows if w.get("title") == "TopSpin replacement text reference"), None)
        if own:
            if own["size"][0] < 600 or own["size"][1] < 180:
                raise RuntimeError("Text reference window is too small")
            time.sleep(.5)
            x, y = own["at"]
            grim = glob.glob("/nix/store/*grim-1.4.1/bin/grim")[0]
            subprocess.run([grim, "-g", f"{x},{y} 600x180", str(destination)], check=True)
            print(f"Captured {destination}", flush=True)
            break
        time.sleep(.1)
    else:
        raise RuntimeError("Text reference window did not appear")
finally:
    process.wait(timeout=25)
