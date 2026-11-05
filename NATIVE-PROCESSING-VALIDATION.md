# Native integration and manual baseline validation

Checked November 4, 2026. This supersedes the production Rust failures in the [historical audit](INTEGRATION-PARITY-AUDIT.md). The original failed results remain in the repository.

## Implemented behavior

The production Rust application now shares `rust/processing.rs` across integration, phase preview, polynomial baseline correction, plot processing and CSV export. Tests import that same module. No vendor code is loaded by the running viewer.

- Region-to-index conversion, endpoint inclusion, full-right zero padding and final selected-point exclusion follow the original integral routine.
- Cumulative integration preserves the original floating-point operation order; it no longer multiplies by the ppm sample interval.
- Manual integral bias and slope and optional endpoint-average baseline adjustment are implemented. Normalization and reference calibration use the original arithmetic. Existing `intrng` bias/slope and `INTSCL` values are imported with NC_proc scaling.
- Phase preview now uses the original cosine/sine recurrence, including FTSIZE and partial-range initial angles, replacing per-sample direct trigonometry.
- Manual polynomial baseline correction implements the original nested polynomial and subtraction order in sample coordinates. The native editor supports a0–a4, live preview, Apply, Cancel, window-close cancellation and undo. The source spectrum stays intact. The endpoint-line estimate is a convenience for selecting coefficients, not an implementation of `abs`.
- Processed samples and integral curves are cached and recalculated when scientific settings change. Vertical display scaling remains independent of numerical values. The graph shows cumulative integral curves and normalized labels.
- Version 2 comparison files retain correction settings and recompute scientific values on reload. JSON uses round-trip float parsing. CSV uses round-trip scientific precision and exports the same corrected samples as the plot and integrator. Existing version 1 measurement snapshots are recalculated with the corrected algorithm.

## Numerical evidence

`bash tests/parity-audit/run-current.sh` now reports **33 exact matches, zero mismatches** on the same three ranges per spectrum that previously failed: ten valid current lab spectra plus one synthetic four-point spectrum. The existing truncated MLN1P93 file is skipped explicitly and remains unchanged.

`bash tests/parity-audit/run-native.sh` reports:

- 631 native-kernel/captured-reference cases, **9,013,844 raw IEEE-754 comparisons, zero mismatches**.
- Three imported saved regions, **3,791 raw-bit comparisons, zero mismatches**, covering bounds, bias, slope, normalization, normalized values and every cumulative curve sample.
- 6,000 original normalization-controller transitions and 24,004 bitwise comparisons validating the normalization reference used by the native cases.

The 631 cases comprise 288 integral cases with bias/slope and endpoint-correction variants; 96 phase cases; 64 polynomial cases with full/partial ranges; 24 polynomial-then-integrate cases; 75 normalization cases; and 84 existing Windows TopSpin JVM captures, comprising 48 phase and 36 integral cases. Buffer lengths include 1, 2, 3, 4, 17, 256, 4,096 and 65,536. Polynomial coefficients include zero, constant, linear and fourth-degree models. The pipeline checks compare complete arrays, not rounded final values or tolerance thresholds.

Original integral, phase and polynomial expected outputs are generated from installed TopSpin classes. Normalization fixtures use the separately validated reconstructed normalization function, with the original controller differential check rerun in the same script. Saved normalization/region parsing is compared to the retained Java reader and original integral calculations; the earlier captured-TopSpin save/reopen tests independently validate that reader.

The phase/integral Windows captures are replayed existing reference files, not new licensed TopSpin UI captures. The manual polynomial reference is executed directly from the original installed class on in-memory spectra at scaling factor 1. It validates numerical coefficient semantics, not pixel-identical mouse gestures.

Reference class source: `~/.wine-topspin-5/drive_c/Bruker/TopSpinProcessing5.0.0/classes/lib/top_base.jar`.

SHA256: `6c24fb17cf46c25c6260301a2f94d1d35658abe12b735971b86cac5ef4588b3b`.

Results are stored under [tests/parity-audit/results](tests/parity-audit/results).

## Application behavior checked

The Rust test suite checks actual production paths for:

- Baseline preview, cancel, apply and undo state, with source samples preserved.
- Integral recalculation after baseline changes.
- Rejection of overflowing baseline settings without replacing valid processed samples.
- Native baseline-window construction without launching the retained application.
- Comparison-file float-bit preservation and scientific recalculation.
- Saved comparison reload through the real asynchronous message handler.
- Corrected CSV export and exact float readback.
- Refusal to overwrite existing comparison files.
- Invalid comparison rejection while preserving the active application state.

The native release is also launched with a saved integration dataset to exercise region import and curve rendering.

## Certification boundary

The original Rust integration failure is fixed for the tested supported inputs. These checks establish scoped numerical equivalence; they do not establish equivalence of every possible TopSpin action.

Automatic TopSpin `abs` modes, exponential/sinusoidal manual baseline functions, and complete original-controller interaction/save sequencing are outside this certification. The processing model is a non-destructive phase-then-polynomial pipeline. Native projects/comparisons/CSV preserve that model; source Bruker files are not overwritten. Native Bruker export has the separate scoped checks recorded in [PROJECT-FORMAT.md](PROJECT-FORMAT.md). An in-memory polynomial preview is not a claim of bit-identical persisted `1r` after TopSpin's quantization and save commands.

## Native raw opening and stack interactions

The viewer now opens supported raw acquisitions natively using a Rust decoder and the existing independently reconstructed C FFT kernel, statically linked at build time with floating-point contraction disabled. This is separate from the Rust polynomial/integration kernels validated above. The native raw bridge passes full-buffer comparisons against the retained Java/C adapter for 90 current readable datasets, 11,796,660 raw-bit comparisons, and both paths reject the existing truncated dataset. Existing acquisition, digital-filter, transform-size, window and CPU-cache validation limits are retained. See `tests/parity-audit/run-raw-stack.sh` for reproduction. This adds no vendor dependency to the running viewer.

The stack workflow is covered by tests for actual row drag/drop, sidebar single-open versus stacking, S while search has focus, per-lane/nearest-trace intensity zoom, stale asynchronous loads, and integral ownership during reordering/removal. Numerical sample buffers and saved corrections remain associated with their original paths when display order changes.
