# Working projects and Bruker export

A working project is a directory, normally ending in `.nmrproj`. Create or open it through Manage → Working project. Save first copies the data; applied edits then autosave into that project, with an additional flush on normal exit. Saving another new destination creates a separate portable copy. Existing path-only comparison JSON files remain supported.

## Storage

- `project.json` identifies format version 1.
- `HEAD` selects a JSON file in `revisions/`. Every save creates a new revision before atomically replacing HEAD. Earlier revisions remain available.
- `traces/<id>/source_real.f64` and optional `source_imaginary.f64` contain exact little-endian IEEE-754 float64 source samples. Edits do not repeatedly quantize these arrays.
- Each trace's `spectrum.json` stores axes, FT size, original NC_proc and phase, imported integration metadata, original location, relative snapshot location and SHA256 inventories.
- Each trace's `source/<dataset>/<experiment>/` contains the acquisition and the selected processing number. It includes the FID and original parameter/provenance files. The project can export Bruker data even when the external master is unavailable.
- Revision recipes retain phase adjustments, polynomial coefficients, integration bounds/corrections/normalization, stack order, visibility, gain, offset and view range. Reopening recomputes the scientific results from the native arrays and recipe.

Checksums validate native components when loaded and source snapshots before export/project reopening. Traversal and escaping symlink paths are rejected. Acquisition/snapshot directories cannot be destinations. Existing unrelated project/export destinations are never overwritten. Source masters are read only; the app does not change their filesystem permissions.

Project saves copy acquisitions synchronously. Large initial imports can briefly pause the UI. Later revisions reuse existing source snapshots. Normal data scans/loads and exports run on workers. Autosave serializes recipe fields without copying cumulative integral buffers each frame.

## CSV

`spectrum.csv` has `index,ppm,source_real,source_imaginary,corrected_real,corrected_imaginary`. Missing imaginary samples are blank. Corrected real includes phase then polynomial baseline. Corrected imaginary includes phase; the real baseline model does not alter it.

`integrals.csv` has `left_ppm,right_ppm,raw_area,normalized_area,bias,slope,normalization_factor`. Bias and slope are the effective corrections after optional endpoint estimation. Values use 17 digits after the decimal in scientific notation. CSV reports floating preview samples, not Bruker's quantized output.

## Bruker export

Export creates `<destination>/<dataset>/<experiment>/pdata/<processing-number>`. Raw acquisition files and genuine metadata/audit files are copied. It writes corrected little-endian int32 `1r` and optional `1i`, processing parameters, `intrng`, and a CRLF `integrals.txt` report. The output uses ordinary Bruker files without app-specific branding or fabricated TopSpin audit entries. Genuine source audit history is retained; it is not represented as a complete history of the new processing.

The phase save adapter uses the same independently reconstructed C kernel as the previously validated save implementation, preserving NC_proc and integer truncation. Polynomial correction follows phase and is quantized at that NC_proc. Out-of-range conversions are rejected rather than clipped. Status `procs` stores accumulated PHC0/PHC1; non-status `proc` stores the last applied increment when phase changes. Source spectral width and exact axis endpoints are preserved.

Integration ranges store effective bias/slope in NC_proc units. INTSCL uses the saved parameter precision. Integral values are recalculated from the saved integer samples and persisted normalization factor, so a floating preview may differ slightly after export. Export validates complete component readback, axis bits, range counts and normalized area bits before reporting success.

This covers supported 1D int32 datasets, validated raw-opening modes, phase adjustment, manual polynomial baseline results and integration. It does not certify every TopSpin operation, automatic baseline mode, or forensic indistinguishability.

## Validation on November 4, 2026

- Native tests pass for portable project reopening after the original master moves, unchanged master components, retained revisions, corrupted arrays/snapshots, source-path and symlink protections, normalization and phase/baseline exports.
- Three existing licensed TopSpin manual-phase captures match the Rust export save adapter on **393,216 raw-bit component comparisons**.
- MLN1P117, MLN1P121 and MLN1P55 were imported into isolated native projects, adjusted in phase and baseline, exported and reopened through the native Bruker reader.
- Bruker's installed original `Integral` class checked the resulting **9 saved regions on 99,672 raw-bit comparisons**, including entire curves and normalized values, with zero mismatches. Reproduce with `cargo test laboratory_exports -- --ignored --nocapture` inside the Rust shell, followed by `bash tests/parity-audit/run-project-export.sh <generated exports.txt>`.
- A fresh live GUI/API reopening was attempted, but the existing Wine TopSpin session had no visible window and its API refused the connection on port 3081. This release therefore does **not** claim a new successful live TopSpin reopen. `tests/reopen_standalone.py` remains available for that check once a working licensed session is open.

All generated laboratory projects/exports live under ignored `artifacts/`. No original acquisition files are written. The broader phase/integration/polynomial validation remains documented in NATIVE-PROCESSING-VALIDATION.md.

The optimized release smoke check reached the first UI update in 47.15 ms and loaded/rendered two raw-backed spectra in 71.03 ms. A portable project reached the first UI update in 50.24 ms and ready state in 73.61 ms. These are application instrumentation points on this laptop, not compositor presentation timestamps or a browser comparison. The regular suite passes 26 tests, with the laboratory export test run separately; Clippy and the release build also pass.
