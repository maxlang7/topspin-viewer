# TopSpin viewer replacement

Work in progress for opening Bruker 1D data, spectrum viewing, manual integration and manual phase correction. The target is the installed Windows TopSpin Processing 5.0.0. Exact numerical comparisons are required; tolerance matches do not pass.

Run `bash run.sh /path/to/experiment` or `bash run.sh /path/to/pdata/1`. The launcher enters the project Nix shell, compiles Java and the independent C processing library, and opens the viewer. It currently uses the laptop's 2x display scale.

The application does not load Bruker classes, executables, processing libraries, Python or the TopSpin network API. It uses a pinned FlatLaf 3.4.1 dependency, captured control graphics and pinned fonts. These local reference assets have not been published.

## Verified numerical behavior

- Processed int32 reader, including byte order, `NC_proc` amplitude units and ppm axis, against captured TopSpin API data.
- Phase preview and integration curves against original Java routines, including 84 cases captured inside TopSpin's actual Windows Java 11.0.28 process.
- Saved phase against actual native Windows TopSpin output on all 85 lab acquisitions. Phase-only saves preserve the existing `NC_proc` exponent.
- Combined raw-FID `efp`, including exponential windowing centered on group delay, native FFT cache splitting, accumulated phase, scaling and truncation, for integer and floating-point FIDs. All 85 acquisitions in the user's current lab folder and five additional parameter variants pass bitwise comparisons, including their ppm axes.
- Integration regions and saved `intrng` and `integrals.txt` text against original Java routines, including regions touching the complete spectral endpoint. Three regions captured from the actual interactive module match every curve value, default normalization and saved scaling parameter.
- Display compression, forward and inverse coordinate mapping and complex-magnitude phase pivots against original Java routines.
- Spectrum rasterization against the original renderer in a controlled JVM: 303 cases and 135,167,445 exact pixel comparisons, covering sparse and compressed spectra, zoomed ranges, gain, baseline, flat data and 1x/2x scaling. This does not establish whole-window visual equivalence.
- A spectrum captured through the original renderer inside actual Windows TopSpin Java 11 matches the standalone renderer on all 2,654,720 pixels.
- The initial INTEGRALS table matches the captured Windows table on all 2,844,088 pixels at the laptop's 2x scale. Its 15 captured cell values, numeric formatting and column widths also match exactly. Sorting and selection interactions remain under reconstruction.
- Phase-drag arithmetic matches 540 original-controller transitions. Manual bias, slope and region splitting match 15,171 values captured from the actual interactive integration controller, including the second segment's shifted bias.
- Saving and reopening preserves every stored real and imaginary spectrum byte in the tested captures.
- A standalone saved phase result has been reopened in actual TopSpin: all 131,072 complex-component amplitudes and the integral table match exactly. TopSpin's integration module also reconstructs all three saved regions with 3,773 identical curve values, parameters and normalization.
- Loose acquisitions, nested dataset folders, raw filenames and ZIP archives open without changing numerical data. The UI workflow test covers phase preset/cancel/save/reopen and manual-region drag/undo/cancel.
- Returning and saving updates the current processing number, with complete before/after versions retained under `pdata/.topspin-viewer-history`. Failure tests cover invalid staged data and rollback after components have been replaced and a new table created. Preparing a phase save uses a separate model so a failed save cannot apply the phase twice.

Run `bash verify.sh` for the saved-reference and vendor Java comparisons. Reference data is local under ignored `artifacts/`; reference-generation scripts require the licensed TopSpin installation and operate only on isolated dataset copies. Original source inventories are checked separately by SHA256.

## Remaining work

The app is not yet an equivalent replacement. UI layout, spectrum drawing, interaction defaults and remaining integration controls are still being reconstructed and tested. Unsupported raw modes, fractional group delay, other transform sizes and second-level FFT cache splitting are rejected until validated. Every acquisition in the current lab inventory uses the validated raw formats and transform size.

Saving updates processed files in the current processing number and retains recoverable versions. Raw acquisition files are unchanged. Development and reference tests operate only on isolated dataset copies.
