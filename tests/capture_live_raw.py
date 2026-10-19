"""Capture licensed Windows TopSpin raw samples on explicitly copied acquisitions only."""
from pathlib import Path
import hashlib
import json
import sys
import numpy as np
from bruker.api.topspin import Topspin

root = Path(sys.argv[1]).resolve()
if "topspin-viewer" not in str(root) or "artifacts" not in str(root):
    raise RuntimeError("An isolated project reference root is required")
manifest = json.loads((root / "reference-copy-manifest.json").read_text())
top = Topspin()
report = {"topspin_version": top.getVersion(), "cases": []}
rows = []
output = root / "captures"
output.mkdir(exist_ok=False)
for case in manifest["cases"]:
    folder = (root / case["copy_relative"]).resolve()
    if not folder.is_relative_to(root):
        raise RuntimeError("Acquisition is outside the copy root")
    dataset = top.getDataProvider().getNMRData(str(folder / "pdata/1"))
    if dataset is None:
        raise RuntimeError("Cannot open copied acquisition: " + str(folder))
    result = dataset.getRawDataPoints()
    values = np.asarray(result["dataPoints"], dtype="<f8")
    values.tofile(output / (case["id"] + ".f64"))
    metadata = {key: value for key, value in result.items() if key != "dataPoints"}
    metadata.update(id=case["id"], copy_relative=case["copy_relative"], samples=len(values))
    report["cases"].append(metadata)
    physical = result["physicalRanges"][0]
    index = result["indexRanges"][0]
    rows.append("\t".join(map(str, [case["id"], case["copy_relative"], len(values), index["firstPoint"],
        index["numberOfPoints"], float(physical["start"]).hex(), float(physical["end"]).hex(), physical["unit"]])))
    for name in ["fid", "acqus"]:
        actual = hashlib.sha256((folder / name).read_bytes()).hexdigest()
        if actual != case[name + "_sha256"]:
            raise AssertionError("Raw input changed: " + str(folder / name))
    print("CAPTURE", case["id"], len(values), flush=True)
(root / "capture-completion.json").write_text(json.dumps(report, indent=2, default=str) + "\n")
(root / "raw.tsv").write_text("\n".join(rows) + "\n")
print("RAW API CAPTURE COMPLETE", len(report["cases"]), flush=True)
