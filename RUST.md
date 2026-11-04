# Native Rust viewer

The [integration parity audit](INTEGRATION-PARITY-AUDIT.md) fails for native Rust. Its integration measurements and baseline workflow are not certified TopSpin equivalents. The retained Java/C checks do not certify the Rust implementation.

Build once with `bash build-rust.sh`, then launch with `bash run.sh`.
The launcher executes the release binary directly, with library paths recorded during the build. It does not compile or evaluate Nix during startup. Rebuild explicitly after changing source. `Cargo.lock` pins dependencies. Build outputs are ignored and must be rebuilt after cloning. The desktop entry uses this launcher.

The Java/C viewer remains available with `bash run.sh --legacy` or `bash run-legacy.sh`. Its original source, scientific regression suite, reference captures and Git history are retained.

## TopSpin-style layout

The Rust UI follows the retained TopSpin reference layout: Process/Analyze/Manage navigation, a processing ribbon, a separate view toolbar, left data browser, open-spectrum tabs, optional right display/analysis controls through the Display button, and bottom SPECTRUM/INTEGRALS/FID tabs. The INTEGRALS tab shows measurements as a table. Commands marked ↗ open the retained application for validated processing; FID opens there too. The command bar supports `.all`, `.ph`, `int`, `.multi`, `.csv`, `efp`, and `.cal`. Gruvbox colors remain in use.

## Comparison workflow

Enter a Bruker folder in Data path, then Open / scan, or pass paths on the command line. A single processed dataset opens directly. A larger folder is scanned in a worker thread and listed in Dataset browser. With no paths supplied, the app scans `~/Documents/IGB Lab/NMRs` when that folder exists. The browser includes raw-only processing numbers marked FID, which open in the retained processing app. Its list fills the panel and scrolls through every result. The clickable sort menu above the list offers Alphabetical and Alphabetical (natural), where MLN1P85 precedes MLN1P115. Click rows to add spectra, or use Add matching spectra to load up to 32 filtered results. Drop folders onto the window as an alternative.

Overlay shares the ppm axis. Stacked separates visible spectra vertically while retaining the shared axis. Each spectrum has a color, visibility toggle, gain and vertical offset. Normalize scales each spectrum separately. Disable it to compare source amplitudes. Scroll above the spectrum baseline to zoom intensity for all visible spectra. Scroll below the baseline to zoom the ppm axis around the cursor; horizontal wheel gestures pan anywhere over the plot. The baseline hit area follows vertical offsets and stacked lanes. Use the + and − toolbar controls to zoom the ppm axis; drag to pan; double-click or Full range resets the axis. These operations apply to all displayed spectra.

Phase preview supports PH0 and PH1 where a `1i` component is present. It uses FTSIZE for first-order phase, with direct trigonometric evaluation. It has not been certified bitwise equivalent to TopSpin's recurrence arithmetic. It never writes processed components.

Enable Measure integral and drag over a ppm interval, or enter its endpoints in Integral measurements. Measurements are signed sample sums multiplied by the ppm step, with no baseline correction or TopSpin normalization. Results are snapshots at the phase settings used to measure them. Highlighted intervals belong to the selected trace. Peak picking lists up to 128 strongest positive local maxima above the selected relative threshold.

CSV export writes `spectrum.csv` inside the selected processing folder, in a worker thread, containing ppm, original real/imaginary values and the phase preview. It refuses to overwrite an existing file. Comparison files save paths, per-spectrum settings, layout, view range and measurement snapshots as JSON, also refusing overwrites. Load a comparison when other loads have finished. Data remains external to comparison files.

## Performance

Folder scans, file reads and CSV writes run off the UI thread. Traces use min/max envelopes per physical screen column to preserve narrow peaks; cached geometry is reused until the viewport or spectrum settings change. Idle operation uses egui's event-driven repainting. Opening very many spectra can still consume substantial memory, and initial envelope generation is on the UI thread.

`bash run.sh --smoke [dataset paths...]` logs time to the first UI update and time until requested datasets have loaded and generated plot geometry, then exits. These are application instrumentation points, not compositor presentation timestamps or cold disk-cache benchmarks.

## Parity status

| Capability | Rust | Retained Java/C |
|---|---|---|
| Processed int32 1D reader, endian and NC_proc scaling | Implemented, differential check available | Validated |
| Multiple spectra, overlay / stacked, linked navigation | Implemented | Single dataset |
| Gruvbox theme | Implemented | Implemented |
| Phase preview | Direct trig preview; not bitwise certified | Validated |
| Integration | Signed measurements, snapshot regions | Validated correction, normalization and saving |
| Peak picking | Positive local maxima | Not implemented |
| Comparison files, CSV | Implemented | Not implemented |
| Raw FID viewing / processing, ZIP opening | Pending | Supported within documented limits |
| Bruker phase / integration / calibration writes | Pending | Validated, recoverable saves |
| Browser filesystem watching and remembered roots | Pending | Implemented |
| Multidimensional spectra, acquisition control | Pending | Not implemented |

Full TopSpin parity is not achieved by either version. The historical README's numerical validation claims apply to the retained Java/C implementation only.

## Validation

`nix-shell rust-shell.nix --run 'cargo test && cargo clippy -- -D warnings'`

`nix-shell shell.nix --run 'python tests/verify_rust_reader.py "/path/to/NMRs"'`

The latter compares every decoded real/imaginary sample via ordered 64-bit rolling digests and compares exact axis endpoint bits against the independently compiled Java reader. It reads original acquisitions without modifying them. `--audit` provides machine-readable JSON for each processed dataset.

## Checked on November 4, 2026

Five native unit tests and Clippy with warnings denied pass. The lab-folder differential check matches 10 valid spectra, 655,360 real samples, all imaginary sample digests and both axis endpoint bits. One existing truncated `1r` in MLN1P93 is rejected by both implementations. This validates processed reading, not all processing operations.
