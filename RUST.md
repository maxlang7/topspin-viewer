# Native Rust viewer

The earlier integration regression is fixed. Native integration, phase preview, and the manual polynomial baseline now pass the scoped [native processing validation](NATIVE-PROCESSING-VALIDATION.md), including direct comparisons to installed TopSpin classes. The [original failing audit](INTEGRATION-PARITY-AUDIT.md) is retained as history. This validates the documented kernels and workflows, not every TopSpin command.

Build once with `bash build-rust.sh`, then launch with `bash run.sh`.
The launcher executes the release binary directly, with library paths recorded during the build. It does not compile or evaluate Nix during startup. Rebuild explicitly after changing source. `Cargo.lock` pins dependencies. Build outputs are ignored and must be rebuilt after cloning. The desktop entry uses this launcher.

The Java/C viewer remains available with `bash run.sh --legacy` or `bash run-legacy.sh`. Its original source, scientific regression suite, reference captures and Git history are retained.

## TopSpin-style layout

The Rust UI follows the retained TopSpin reference layout: Process/Analyze/Manage navigation, a processing ribbon, a separate view toolbar, left data browser, open-spectrum tabs, optional right display/analysis controls through the Display button, and bottom SPECTRUM/INTEGRALS/FID tabs. The INTEGRALS tab shows measurements as a table. Commands marked ↗ open the retained application for validated processing; FID opens there too. The command bar supports `.all`, `.ph`, `int`, `.multi`, `.csv`, `efp`, `.cal`, and `.basl`. Gruvbox colors remain in use.

## Comparison workflow

Enter a Bruker folder in Data path, then Open / scan, or pass paths on the command line. A single processed dataset opens directly. A larger folder is scanned in a worker thread and listed in Dataset browser. With no paths supplied, the app scans `~/Documents/IGB Lab/NMRs` when that folder exists. The browser includes raw-only processing numbers marked FID, which open in the retained processing app. Its list fills the panel and scrolls through every result. The clickable sort menu above the list offers Alphabetical and Alphabetical (natural), where MLN1P85 precedes MLN1P115. Click rows to add spectra, or use Add matching spectra to load up to 32 filtered results. Drop folders onto the window as an alternative.

Overlay shares the ppm axis. Stacked separates visible spectra vertically while retaining the shared axis. Each spectrum has a color, visibility toggle, gain and vertical offset. Normalize scales each spectrum separately. Disable it to compare source amplitudes. Scroll above the spectrum baseline to zoom intensity for all visible spectra. Scroll below the baseline to zoom the ppm axis around the cursor; horizontal wheel gestures pan anywhere over the plot. The baseline hit area follows vertical offsets and stacked lanes. Use the + and − toolbar controls to zoom the ppm axis; drag to pan; double-click or Full range resets the axis. These operations apply to all displayed spectra.

Phase preview supports PH0 and PH1 where a `1i` component is present. It uses the original recurrence arithmetic and FTSIZE. Processed samples are cached and shared by the plot, peak picker, integrator, and CSV exporter. It never writes source components.

Choose **Analyze → Integrate** or **Select region**, then drag over a ppm interval. **Display → Integrate** also accepts exact ppm bounds. Integrals use TopSpin's rounded inclusive bounds, final-point exclusion, full-right zero padding and accumulation order. The first region defaults to a normalized value of one. Existing Bruker `intrng` bias/slope and `INTSCL` normalization are imported. Reference calibration freezes the resulting normalization scale. Each region has editable bounds, bias, slope, optional endpoint-averaged baseline adjustment, and baseline point count. The plot shows cumulative curves and normalized labels; INTEGRALS shows raw and normalized values. Display gain and offset do not change measured values.

Choose **Process → Baseline**, or enter `.basl`, for a native manual polynomial correction. Edit a0–a4, preview the corrected spectrum, then Apply or Cancel. Closing the window cancels its preview. Undo last applied correction is available inside the editor. Coefficients use local sample coordinates across the full spectrum, matching the original polynomial model at unit scaling. An endpoint-line estimate provides starting coefficients; this helper is not the automatic TopSpin `abs` algorithm. Applied phase and polynomial settings form a non-destructive pipeline, phase first, baseline second.

Spectrum baseline correction modifies the samples used by integrations. Per-region integral baseline correction adjusts the integral only. Both are distinct from the vertical display offset. Changes trigger recalculation of affected integral curves; they are no longer fixed measurement snapshots.

CSV export writes `spectrum.csv` inside the selected processing folder, in a worker thread, containing ppm, original real/imaginary values and the corrected processed preview. Values use round-trip scientific precision. It refuses to overwrite an existing file. Comparison files save paths, phase/polynomial settings, region correction options, normalization, layout and view range as JSON, also refusing overwrites. Version 2 comparisons reload and recompute corrected values; old version 1 measurement snapshots are migrated and recalculated rather than trusted. Data remains external to comparison files. Original Bruker components and processing parameters remain unchanged; native Bruker write parity is not claimed.

## Performance

Folder scans, file reads and CSV writes run off the UI thread. Traces use min/max envelopes per physical screen column to preserve narrow peaks; cached geometry is reused until the viewport or spectrum settings change. Idle operation uses egui's event-driven repainting. Opening very many spectra can still consume substantial memory, and initial envelope generation is on the UI thread.

`bash run.sh --smoke [dataset paths...]` logs time to the first UI update and time until requested datasets have loaded and generated plot geometry, then exits. These are application instrumentation points, not compositor presentation timestamps or cold disk-cache benchmarks.

## Parity status

| Capability | Rust | Retained Java/C |
|---|---|---|
| Processed int32 1D reader, endian and NC_proc scaling | Implemented, differential check available | Validated |
| Multiple spectra, overlay / stacked, linked navigation | Implemented | Single dataset |
| Gruvbox theme | Implemented | Implemented |
| Phase preview | Original recurrence; differential and captured checks pass | Validated |
| Integration | Native curves, bias/slope, endpoint correction, normalization and saved-region import; exact checks pass | Validated correction, normalization and saving |
| Manual polynomial baseline | Native degree 0–4, preview/apply/cancel/undo; exact model checks pass | Not implemented |
| Automatic baseline commands | Pending | Not implemented |
| Peak picking | Positive local maxima of processed preview | Not implemented |
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

Native scientific validation is recorded in [NATIVE-PROCESSING-VALIDATION.md](NATIVE-PROCESSING-VALIDATION.md). Run `bash tests/parity-audit/run-native.sh` for original-class polynomial/integration/phase checks, saved-region import checks, and captured Windows JVM cases. Run `bash tests/parity-audit/run-current.sh` for the 33 region comparisons that previously failed. Run `nix-shell rust-shell.nix --run 'cargo test && cargo clippy -- -D warnings'` for application workflow checks.
