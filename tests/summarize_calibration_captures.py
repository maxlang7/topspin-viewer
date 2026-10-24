"""Flatten independent live-controller and API captures for the standalone Java acceptance test."""
import json
from pathlib import Path
root=Path('artifacts/calib-reference-20261024')
rows=[]
cases={case["name"]:case for case in json.loads((root/"cases.json").read_text())}
for name in json.loads((root/'complete.json').read_text()):
    result=json.loads((root/name/'result.json').read_text());api=json.loads((root/name/'api.json').read_text())
    before=result['before'];after=result['after'];axis=api['physicalRanges'][0]
    values=[name,str(result['clicked']),str(result['target']),before['SF'],before['OFFSET'],*before['range'],after['SF'],after['OFFSET'],*after['range'],float(axis['start']).hex(),float(axis['end']).hex()]
    values.append(cases[name]["source"])
    display=root/name/"display-after-return.json"
    values.extend(json.loads(display.read_text())["viewRange"] if display.is_file() else ["",""])
    rows.append('\t'.join(values))
(root/'cases.tsv').write_text('\n'.join(rows)+'\n')
