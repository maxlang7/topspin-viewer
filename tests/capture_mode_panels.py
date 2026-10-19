"""Capture fresh spectrum, phase and manual-integration defaults on a copied acquisition."""
from pathlib import Path
import json
import sys
from bruker.api.topspin import Topspin

root=Path(sys.argv[1]).resolve()
project=root.parent.parent
experiment=root/'datasets/panel-modes-fresh/10'
if root.name!='live-fid-reference-20261019' or project.name!='topspin-viewer' or not experiment.is_relative_to(root):
    raise RuntimeError('Copied mode reference required')
top=Topspin()
dataset=top.getDataProvider().getNMRData(str(experiment/'pdata/1'))
top.getDisplay().show(dataset,newWindow=True)
rows=[]
for mode,command in [('spectrum',None),('phase','.ph'),('integration','.int')]:
    if command is not None:
        top.executeCommand(command,wait=True)
    destination=project/'artifacts/live-fid-display-20261019'/('modes-'+mode)
    if destination.exists():
        raise RuntimeError('Capture destination already exists')
    (project/'artifacts/live-fid-capture-request.json').write_text(json.dumps({'destination':str(destination).replace('\\','/'),'raw':False}))
    top.executeCommand('xpy '+str(project/'tests/capture_live_fid_display.py'),wait=True)
    if not (destination/'panel.png').is_file():
        raise RuntimeError((project/'artifacts/live-fid-display-error.txt').read_text())
    rows.append('\t'.join([mode,'datasets/panel-modes-fresh/10',destination.name]))
    print('CAPTURE MODE',mode,flush=True)
    if mode!='spectrum':
        top.executeCommand('.ret',wait=True)
(project/'artifacts/live-fid-display-20261019/modes.tsv').write_text('\n'.join(rows)+'\n')
