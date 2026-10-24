"""Capture licensed TopSpin calibration and an independent API read for each copied acquisition."""
from pathlib import Path
import json,time,hashlib
from bruker.api.topspin import Topspin
root=Path('Z:/home/maxlang/Projects/topspin-viewer');captures=root/'artifacts/calib-reference-20261024';top=Topspin()
rows=json.loads((captures/"complete.json").read_text()) if (captures/"complete.json").exists() else []
for case in json.loads((captures/'cases.json').read_text()):
    if case['name'] in rows:continue
    folder=captures/case['name'];processing=folder/'10/pdata/1'
    if (folder/'result.json').exists():raise RuntimeError('Fresh reference destination required')
    before={name:hashlib.sha256((processing/name).read_bytes()).hexdigest() for name in ['1r','1i']}
    data=top.getDataProvider().getNMRData(str(processing));top.getDisplay().show(data,newWindow=True)
    top.executeCommand('.cal',wait=True)
    request=dict(case,output='artifacts/calib-reference-20261024/'+case['name']+'/result.json')
    (root/'artifacts/calibration-request-20261024.json').write_text(json.dumps(request))
    top.executeCommand('xpy '+str(root/'tests/capture_live_calibration.py'),wait=True)
    result=folder/'result.json'
    if not result.exists():raise RuntimeError((root/'artifacts/calibration-capture-error-20261024.txt').read_text())
    time.sleep(.2)
    top.executeCommand('xpy '+str(root/'tests/capture_calibration_display.py'),wait=True)
    reopened=top.getDataProvider().getNMRData(str(processing));raw=reopened.getSpecDataPoints(component="1r")
    (folder/"api.json").write_text(json.dumps({"physicalRanges":raw["physicalRanges"],"parameters":reopened.getParameters(["status SF","status OFFSET","status SR","status SW_p"])},default=str,indent=2))
    # Persist source files and API physical endpoints independently of the Java display state.
    after={name:hashlib.sha256((processing/name).read_bytes()).hexdigest() for name in ['1r','1i']}
    if after!=before:raise RuntimeError('Calibration changed component bytes')
    rows.append(case['name'])
    print('CAPTURE',case['name'],flush=True)
(captures/'complete.json').write_text(json.dumps(rows))
