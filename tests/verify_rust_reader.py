"""Compare every decoded sample and both axis endpoints against the retained Java reader.
Run in the existing shell.nix after bash build-rust.sh. No pip dependencies.
"""
import json
import pathlib
import subprocess
import sys

root = pathlib.Path(__file__).resolve().parents[1]
subprocess.run(["javac", "-proc:none", "-cp", "lib/flatlaf-3.4.1.jar", "-d", "build/rust-parity", *map(str, (root / "src").glob("*.java")), "tests/RustReaderParity.java"], cwd=root, check=True)
audit = subprocess.run(["bash", "run.sh", "--audit", sys.argv[1]], cwd=root, text=True, stdout=subprocess.PIPE, check=False)
assert audit.returncode in (0,1), audit.returncode
rows = [json.loads(line) for line in audit.stdout.splitlines()]
java = subprocess.run(["java", "-cp", "build/rust-parity", "RustReaderParity", *[r["path"] for r in rows]], cwd=root, text=True, stdout=subprocess.PIPE, check=True)
expected = {parts[0]: parts[1:] for line in java.stdout.splitlines() if (parts := line.split("\t"))}
for row in rows:
    actual = ["REJECT"] if "error" in row else [str(row["n"]), row["left_bits"], row["right_bits"], row["real_digest"], row["imag_digest"]]
    assert actual == expected[row["path"]], (row["path"], actual, expected[row["path"]])
valid = [r for r in rows if "error" not in r]
assert rows, "No processed spectra found"
print(f"PASS: {len(valid)} valid spectra, {sum(r['n'] for r in valid):,} real samples, all imaginary digests and axis bits match Java; {len(rows)-len(valid)} invalid spectra rejected by both readers")
