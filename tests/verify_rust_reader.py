"""Compare every decoded sample and both axis endpoints against the retained Java reader.
Run in the existing shell.nix after bash build-rust.sh. No pip dependencies.
"""
import json
import pathlib
import subprocess
import sys

root = pathlib.Path(__file__).resolve().parents[1]
subprocess.run(["javac", "-proc:none", "-cp", "lib/flatlaf-3.4.1.jar", "-d", "build/rust-parity", *map(str, (root / "src").glob("*.java")), "tests/RustReaderParity.java"], cwd=root, check=True)
audit = subprocess.run(["bash", "run.sh", "--audit", sys.argv[1]], cwd=root, text=True, stdout=subprocess.PIPE, check=True)
rows = [json.loads(line) for line in audit.stdout.splitlines()]
java = subprocess.run(["java", "-cp", "build/rust-parity", "RustReaderParity", *[r["path"] for r in rows]], cwd=root, text=True, stdout=subprocess.PIPE, check=True)
expected = {parts[0]: parts[1:] for line in java.stdout.splitlines() if (parts := line.split("\t"))}
for row in rows:
    actual = [str(row["n"]), row["left_bits"], row["right_bits"], row["real_digest"], row["imag_digest"]]
    assert actual == expected[row["path"]], (row["path"], actual, expected[row["path"]])
print(f"PASS: {len(rows)} processed spectra, {sum(r['n'] for r in rows):,} real samples, all imaginary sample digests and both axis endpoint bits match Java")
