"""Open artificial processed spectra in copies to identify exact native min/max semantics."""
from pathlib import Path
import json
import sys
from bruker.api.topspin import Topspin

root=Path(sys.argv[1]).resolve();project=root.parent.parent
if root.name!='live-fid-reference-20261019' or project.name!='topspin-viewer':
    raise RuntimeError('Explicit copied reference root required')
top=Topspin();rows=[]
for case in json.loads((root/'spectrum-edge-cases.json').read_text()):
    experiment=(root/case['copy_relative']).resolve()
    if not experiment.is_relative_to(root):
        raise RuntimeError('Dataset is outside copied root')
    dataset=top.getDataProvider().getNMRData(str(experiment/'pdata/1'))
    top.getDisplay().show(dataset,newWindow=True)
    destination=project/'artifacts/live-fid-display-20261019'/case['id']
    if destination.exists():
        raise RuntimeError('Reference destination already exists')
    (project/'artifacts/live-fid-capture-request.json').write_text(json.dumps({'destination':str(destination).replace('\\','/'),'raw':False}))
    top.executeCommand('xpy '+str(project/'tests/capture_live_fid_display.py'),wait=True)
    if not (destination/'panel.png').is_file():
        raise RuntimeError((project/'artifacts/live-fid-display-error.txt').read_text())
    rows.append('\t'.join([case['id'],case['copy_relative'],destination.name]))
    print('CAPTURE EDGE',case['id'],flush=True)
(project/'artifacts/live-fid-display-20261019/spectrum-edges.tsv').write_text('\n'.join(rows)+'\n')
