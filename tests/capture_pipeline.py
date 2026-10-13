from pathlib import Path
import sys,json
from bruker.api.topspin import Topspin
from capture_vendor import snapshot
root=Path(sys.argv[1]).resolve();manifest=json.loads((root/'probe-manifest.json').read_text());top=Topspin()
for case in manifest['cases']:
 folder=(root/case['copy_relative']).resolve();assert folder.is_relative_to(root)
 ds=top.getDataProvider().getNMRData(str(folder/'pdata/1'));assert ds is not None
 output=root/'captures'/case['id'];output.mkdir(parents=True,exist_ok=False)
 for command in ['em','ft','pk']:
  ds.launch(command,wait=True)
  snapshot(ds,output,command)
