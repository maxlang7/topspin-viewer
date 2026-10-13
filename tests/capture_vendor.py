"""Generate reference outputs with licensed TopSpin, on explicitly copied datasets.

Run with TopSpin's bundled Python. This file is a test oracle, never a runtime dependency.
"""
import hashlib
import json
from pathlib import Path
import sys

from bruker.api.topspin import Topspin


def snapshot(dataset, folder, name):
    output = folder / name
    output.mkdir(exist_ok=False)
    report = {"parameters": dataset.getParameters([
        "status PHC0", "status PHC1", "status SI", "status NC_proc",
        "status SF", "status SW_p", "status OFFSET", "status FTSIZE",
        "status STSI", "status STSR", "status FT_mod", "PHC0", "PHC1",
    ])}
    for component in ("1r", "1i"):
        result = dataset.getSpecDataPoints(component=component)
        data = result["dataPoints"].astype("<f8")
        data.tofile(output / (component + ".api.f64"))
        report[component] = {
            "points": len(data), "physicalRanges": result["physicalRanges"],
            "indexRanges": result["indexRanges"],
        }
        original = Path(dataset.getIdentifier()) / component
        if original.exists():
            raw = original.read_bytes()
            (output / component).write_bytes(raw)
            report[component]["sha256"] = hashlib.sha256(raw).hexdigest()
    for filename in ("proc", "procs"):
        (output / filename).write_text(dataset.getProcFile(filename))
    (output / "snapshot.json").write_text(json.dumps(report, indent=2, default=str) + "\n")
    print("CAPTURE", name, report["1r"]["points"], flush=True)
    return report


def main():
    root = Path(sys.argv[1]).resolve()
    marker = root / "reference-copy-manifest.json"
    if not marker.is_file():
        raise RuntimeError("Reference-copy manifest required before any processing")
    manifest = json.loads(marker.read_text())
    top = Topspin()
    manifest["topspin_version"] = top.getVersion()
    for case in manifest["cases"]:
        relative = Path(case["copy_relative"])
        folder = (root / relative).resolve()
        if not folder.is_relative_to(root) or not (folder / "fid").is_file():
            raise RuntimeError("Refusing dataset outside reference-copy root")
        pdata = folder / "pdata" / "1"
        dataset = top.getDataProvider().getNMRData(str(pdata))
        if dataset is None:
            raise RuntimeError("TopSpin could not open " + str(pdata))
        case_output = root / "captures" / case["id"]
        case_output.mkdir(parents=True, exist_ok=False)
        if (pdata / "1r").exists() and (pdata / "1i").exists():
            snapshot(dataset, case_output, "original")
        dataset.launch("efp", wait=True)
        baseline = snapshot(dataset, case_output, "efp")
        ph0 = float(baseline["parameters"]["status PHC0"])
        ph1 = float(baseline["parameters"]["status PHC1"])
        dataset.setPar("PHC0", ph0 + 12.345)
        dataset.setPar("PHC1", ph1 - 23.456)
        dataset.launch("pk", wait=True)
        snapshot(dataset, case_output, "pk-manual")
    (root / "capture-completion.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print("REFERENCE CAPTURE COMPLETE", flush=True)


if __name__ == "__main__":
    main()
