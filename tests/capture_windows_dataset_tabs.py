"""Capture the standalone original dataset tabs; no acquisition files or licensing services are used."""
import argparse
import glob
import json
import os
from pathlib import Path
import shutil
import subprocess
import time

project = Path.cwd()
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('output', type=Path, help='Fresh reference directory; existing directories are never overwritten')
output = parser.parse_args().output.resolve()
if output.exists():
    raise SystemExit('Choose a fresh output directory before regenerating the reference')
installation = Path.home() / '.wine-topspin-5/drive_c/Bruker/TopSpinProcessing5.0.0'
wine = Path.home() / '.local/share/topspin-wine-runtime/wine-x11/bin/wine'
def windows(path):
    return 'Z:' + str(path).replace('/', '\\')
process = subprocess.Popen([
    str(wine), str(installation / 'jre/bin/java.exe'), '-Dsun.java2d.uiScale=2', '--add-exports=java.desktop/sun.font=ALL-UNNAMED',
    '-cp', windows(project / 'build/test') + ';' + windows(installation / 'classes/lib/*'),
    'oracle.WindowsDatasetTabsCapture', windows(output),
], env=dict(os.environ, WINEPREFIX=str(Path.home() / '.wine-topspin-5'), WINEDEBUG='-all', WAYLAND_DISPLAY=''),
    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
try:
    for attempt in range(100):
        marker = output / 'window.txt'
        if marker.exists():
            title = marker.read_text().splitlines()[0]
            clients = json.loads(subprocess.check_output(['hyprctl', 'clients', '-j']))
            matches = [client for client in clients if client.get('title') == title]
            if len(matches) == 1:
                client = matches[0]
                address = client['address']
                # Hyprland 0.55 dispatcher properties, scoped to this temporary reference window.
                for property_name, value in [('opacity', 1), ('opacity_inactive', 1), ('rounding', 0), ('no_shadow', 1)]:
                    result = subprocess.check_output(['hyprctl', 'dispatch', 'setprop', 'address:' + address + f' {property_name} {value} lock'], text=True).strip()
                    if result != 'ok':
                        raise RuntimeError(f'Reference window property could not be fixed: {result}')
                result = subprocess.check_output(['hyprctl', 'dispatch', 'movewindowpixel', f'exact 100 100,address:{address}'], text=True).strip()
                if result != 'ok':
                    raise RuntimeError(f'Reference window could not be moved clear of the desktop bar: {result}')
                time.sleep(.5)
                clients = json.loads(subprocess.check_output(['hyprctl', 'clients', '-j']))
                client = next(client for client in clients if client.get('address') == address)
                (output / 'client.json').write_text(json.dumps({key: client.get(key) for key in ['address','at','size','class','title','focusHistoryID']}, indent=2))
                x, y = client['at']; width, height = client['size']
                grim = shutil.which('grim') or glob.glob('/nix/store/*grim-*/bin/grim')[0]
                subprocess.run([grim, '-g', f'{x},{y} {width}x{height}', str(output / 'screen.png')], check=True)
                (output / 'screen.png').rename(output / 'screen-early.png')
                time.sleep(2)
                subprocess.run([grim, '-g', f'{x},{y} {width}x{height}', str(output / 'screen.png')], check=True)
                print(f'Captured {output / "screen.png"}', flush=True)
                break
        if process.poll() is not None:
            break
        time.sleep(.1)
    else:
        raise RuntimeError('Original dataset tabs did not become available')
finally:
    try:
        log, _ = process.communicate(timeout=20)
    except subprocess.TimeoutExpired:
        process.terminate()
        log, _ = process.communicate(timeout=10)
    print(log)
if process.returncode:
    raise SystemExit(process.returncode)
if not (output / 'screen.png').exists():
    raise SystemExit('No on-screen reference was captured')
