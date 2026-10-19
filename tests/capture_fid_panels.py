"""Drive copied FIDs through the licensed Windows GUI and capture each actual panel."""
from pathlib import Path
import json
import sys
from bruker.api.topspin import Topspin

root = Path(sys.argv[1]).resolve()
project = root.parent.parent
if root.name != "live-fid-reference-20261019" or project.name != "topspin-viewer":
    raise RuntimeError("Explicit copied FID reference root required")
output = project / "artifacts/live-fid-display-20261019"
fresh = len(sys.argv)>2 and sys.argv[2]=="fresh"
cases = json.loads((root / ("panel-cases-fresh.json" if fresh else "panel-cases.json")).read_text())
top = Topspin()
rows = []
for case in cases:
    experiment = (root / case["copy_relative"]).resolve()
    if not experiment.is_relative_to(root):
        raise RuntimeError("Dataset is outside the copy root")
    dataset = top.getDataProvider().getNMRData(str(experiment / "pdata/1"))
    if dataset is None:
        raise RuntimeError("Cannot open " + str(experiment))
    top.getDisplay().show(dataset, newWindow=fresh)
    top.executeCommand("xpy " + str(project / "tests/select_live_fid_tab.py"), wait=True)
    for state, commands in [("default", []), ("zoom", [".zi"]), ("scaled", ["*2"])]:
        for command in commands:
            top.executeCommand(command, wait=True)
        destination = output / (case["id"] + ("-fresh-" if fresh else "-") + state)
        if destination.exists():
            raise RuntimeError("Capture destination already exists: " + str(destination))
        request = {"destination": str(destination).replace("\\", "/")}
        (project / "artifacts/live-fid-capture-request.json").write_text(json.dumps(request))
        top.executeCommand("xpy " + str(project / "tests/capture_live_fid_display.py"), wait=True)
        if not (destination / "panel.png").is_file():
            error = project / "artifacts/live-fid-display-error.txt"
            raise RuntimeError(error.read_text() if error.is_file() else "No live FID panel was captured")
        rows.append("\t".join([case["id"], case["copy_relative"], state, destination.name]))
        print("CAPTURE PANEL", case["id"], state, flush=True)
(output / ("fresh-panels.tsv" if fresh else "panels.tsv")).write_text("\n".join(rows) + "\n")
print("FID PANEL CAPTURE COMPLETE", len(rows), flush=True)
