"""Read standalone output through actual TopSpin. No processing or dataset writes."""
import sys,json,struct,math
from pathlib import Path
import numpy as np
from bruker.api.topspin import Topspin

folder=Path(sys.argv[1]).resolve()
if 'topspin-viewer' not in str(folder) or 'artifacts' not in str(folder):raise RuntimeError('Standalone test output required')
top=Topspin();dataset=top.getDataProvider().getNMRData(str(folder))
if dataset is None:raise RuntimeError('TopSpin did not open standalone output')
params=dataset.getParameters(['status NC_proc','status SI','status PHC0','status PHC1','status INTSCL','status OFFSET','status SF','status SW_p'])
size=int(params['status SI']);nc=int(params['status NC_proc']);comparisons=0
for component in ['1r','1i']:
    result=dataset.getSpecDataPoints(component=component)
    expected=np.fromfile(folder/component,dtype='<i4',count=size).astype('<f8')*math.ldexp(1.0,nc)
    actual=np.asarray(result['dataPoints'],dtype='<f8')
    if not np.array_equal(expected.view('<u8'),actual.view('<u8')):raise AssertionError(component+' reopened amplitudes differ')
    comparisons+=size
table=dataset.getProcFile('integrals.txt')
local=(folder/'integrals.txt').read_text()
if table.replace('\r\n','\n')!=local:raise AssertionError('Reopened integral table differs')
top.getDisplay().show(dataset)
report={'path':str(folder),'topspin_version':top.getVersion(),'parameters':params,'bitwise_comparisons':comparisons,'integral_table_exact':True}
(folder.parent.parent.parent/'reopen-topspin.json').write_text(json.dumps(report,indent=2)+'\n')
print('PASS standalone save opened in actual TopSpin:',comparisons,'bitwise amplitudes and exact integral table',flush=True)
