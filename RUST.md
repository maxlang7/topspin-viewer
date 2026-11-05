# Native Rust viewer

The earlier integration regression is fixed. Native integration, phase preview, and the manual polynomial baseline now pass the scoped [native processing validation](NATIVE-PROCESSING-VALIDATION.md), including direct comparisons to installed TopSpin classes. The [original failing audit](INTEGRATION-PARITY-AUDIT.md) is retained as history. This validates the documented kernels and workflows, not every TopSpin command.

Build once with `bash build-rust.sh`, then launch with `bash run.sh`.
The launcher executes the release binary directly, with library paths recorded during the build. It does not compile or evaluate Nix during startup. Rebuild explicitly after changing source. `Cargo.lock` pins dependencies. Build outputs are ignored and must be rebuilt after cloning. The desktop entry uses this launcher.

The earlier Java/C source and scientific regression references remain in the repository. The native UI and default launcher do not launch that application.

## Compact layout and appearance

One compact command row contains the desktop spectrum logo, File/View/Tools menus, Phase, Baseline, Integrate, Undo/Redo, intensity and ppm zoom controls, Settings and autosave/loading status. It scrolls horizontally in smaller windows. Open-spectrum tabs remain below it. The bottom row holds command entry and a compact status message with the full text on hover; SPECTRUM and INTEGRALS remain accessible.

File contains working projects, Bruker/CSV export and path-based comparisons. View contains overlay/stack selection and Display. Tools contains peak picking, with thresholds and results in Display. The Phase button, Ctrl+P and `.ph` command select Phase in a single resizable right-hand tool panel. Display, Phase, Baseline, Integrals, Peaks and History share that panel; switching tools replaces its contents. Hide collapses the panel. Phase retains saved PHC0/PHC1, additional adjustments and Reset. Baseline previews cancel when switching tools, hiding the panel or changing the selected spectrum. Other existing commands remain `.all`, `int`, `.multi`, `.csv` and `.basl`.

Settings offers Gruvbox Dark by default, neutral Dark and conventional Light. Light uses a white plot, gray panels, dark text and blue selection accents. Plot/grid/axis/series/integral/error colors all follow the theme. Theme changes invalidate cached plot geometry and are remembered under `$XDG_CONFIG_HOME/nmr-viewer/settings.json`, falling back to `~/.config/nmr-viewer/settings.json`. Scientific settings stay in dataset projects; appearance is an application preference.

The fixed-baseline integral overlay keeps spectrum geometry identical when integrals are shown or hidden. Numerical labels occupy up to four rows below baseline, with the selected label given priority. Crowded values appear in a +N integrals menu with a link to the integration table. Curves stay above baseline and markers stay at the top. No numerical processing changes are involved.

Keyboard and wheel shortcuts defer to text entry:

| Shortcut | Action |
| --- | --- |
| Ctrl+Alt+scroll, or Ctrl+scroll | Adjust zero-order phase on the hovered spectrum |
| Home | Restore the full ppm range |
| Ctrl+Home | Reset selected spectrum gain and offset |
| Ctrl+, | Open Settings |
| Ctrl+E | Open export options; exporting still requires choosing an action |
| Ctrl+P | Open docked phase controls |
| Ctrl+B | Open docked Baseline |
| Ctrl+Z | Undo scientific edit on the selected spectrum |
| Ctrl+Shift+Z or Ctrl+Y | Redo scientific edit on the selected spectrum |
| Escape | Cancel the active interaction, then close the innermost popup or editor |

No first-order phase shortcut is assigned. Shift+scroll remains disabled. Ordinary scrolling retains its above/below-baseline behavior. The earlier design alternatives remain in `~/Documents/Codex-plans/nmr-ui-design-proposals.md`.

## Comparison workflow

Enter a Bruker folder in Data path, then Open / scan, or pass paths on the command line. A single processed dataset opens directly. A larger folder is scanned in a worker thread and listed in Dataset browser. With no paths supplied, the app scans `~/Documents/IGB Lab/NMRs` when that folder exists. The browser includes raw-only processing numbers marked FID. Supported complex 1D acquisitions are processed in memory by the native viewer using the existing validated C FFT kernel, with Rust handling parameter parsing and sample decoding. No Java process or source-file write is needed. Unsupported modes and corrupt components remain explicit errors. Its list fills the panel and scrolls through every result. The clickable sort menu above the list offers Alphabetical and Alphabetical (natural), where MLN1P85 precedes MLN1P115. Click a row to open that spectrum on its own. Hover over another row and press **S** to stack it with the visible spectra. This shortcut also works after searching, without inserting S into the filter. Existing hidden spectra retain their processing and integral state. Add matching spectra loads up to 32 filtered results into the stack. Drop folders onto the window as an alternative.

Overlay shares the ppm axis. Stacked separates visible spectra vertically while retaining the shared axis. Each spectrum has a color, visibility toggle, gain and vertical offset. Normalize scales each spectrum separately. Disable it to compare source amplitudes. Scroll above the spectrum baseline to zoom intensity only for the spectrum under the pointer. Stacked mode uses the hovered lane; overlay mode finds the nearest rendered trace at that ppm. Scroll below the baseline to zoom the ppm axis around the cursor; horizontal wheel gestures pan anywhere over the plot. The baseline hit area follows vertical offsets and stacked lanes. Use the + and − toolbar controls to zoom the ppm axis; drag to pan; double-click or Full range resets the axis. Horizontal navigation stays linked across the visible spectra.

Phase preview supports PH0 and PH1 where a `1i` component is present. It uses the original recurrence arithmetic and FTSIZE. Processed samples are cached and shared by the plot, peak picker, integrator, and CSV exporter. It never writes source components.

Choose **Integrate**, then drag over a ppm interval. **Display → Integrate** also accepts exact ppm bounds. Integrals use TopSpin's rounded inclusive bounds, final-point exclusion, full-right zero padding and accumulation order. The first region defaults to a normalized value of one. Existing Bruker `intrng` bias/slope and `INTSCL` normalization are imported. Reference calibration freezes the resulting normalization scale. Each region has editable bounds, bias, slope, optional endpoint-averaged baseline adjustment, and baseline point count. Small horizontal bars at the very top of the plot mark each visible integral range and track ppm zoom/pan. Hover over a bar for its bounds and value. Cumulative curves stay above the selected spectrum baseline. A separate band below the baseline holds normalized numerical labels. Curves automatically fit the visible ppm slice and retain extrema during screen-resolution compression. Labels keep a fixed readable font, spread into rows to avoid overlap, and stay inside the viewport when zooming. Hover over an integral for its complete value; crowded labels are also available in INTEGRALS. Spectrum intensity zoom does not alter the integral display height or numerical values. INTEGRALS shows raw and normalized values. Display gain and offset do not change measured values.

Choose **Baseline**, or enter `.basl`, for a native manual polynomial correction. Edit a0–a4, preview the corrected spectrum, then Apply or Cancel. Switching tools, hiding the panel or changing the selected spectrum cancels its preview. Undo last applied correction is available inside the editor. Coefficients use local sample coordinates across the full spectrum, matching the original polynomial model at unit scaling. An endpoint-line estimate provides starting coefficients; this helper is not the automatic TopSpin `abs` algorithm. Applied phase and polynomial settings form a non-destructive pipeline, phase first, baseline second.

Hold **Shift** while dragging over a spectrum for temporary integration mode, or toggle the **Integrate** button. A yellow mode badge and instructions appear in the plot. The drag shows a thick top range bar, ppm bounds and a shaded selection before the region is created. The initial mouse position defines the range; releasing Shift before the mouse still completes that gesture. In a stack, the spectrum beneath the pointer receives the region. Saved range markers are thicker too.

Click an integral number below baseline to select it. Hover its number and press **D** to delete it. Double-click a number, type its proton count, then press **Enter** to calibrate every integral on that spectrum to the reference. Escape cancels editing. Zero, negative, non-finite targets and zero-area references are rejected without changing normalization. Calibration and deletion use the same autosave as phase and baseline edits. Other stacked spectra keep their own normalization.

Normal scroll controls remain available while the Integrate button is active. **Shift+scroll does nothing**, including its smooth-scroll tail, so temporary Shift mode cannot accidentally change the view. The next ordinary wheel gesture resumes normal scrolling.

Spectrum baseline correction modifies the samples used by integrations. Per-region integral baseline correction adjusts the integral only. Both are distinct from the vertical display offset. Changes trigger recalculation of affected integral curves; they are no longer fixed measurement snapshots.

Opening a Bruker processing number automatically creates or reopens its working project. Normally this is `~/Documents/NMR Projects/<dataset>.nmrproj`, containing `<dataset>.csv`, `integrals.csv`, exact float64 source components, an acquisition snapshot, checksums and versioned JSON edit recipes. Names shared by different original folders or processing numbers get separate subdirectories so their edits cannot collide. Later opens of the original NMR reload the saved working copy, including its phase, baseline, integration and normalization settings. Each stacked spectrum saves independently.

Applied edits autosave on a background worker after one second without changes. Closing a spectrum or the app flushes outstanding edits. The top bar shows autosave status; hover it for the project location. Preview-only baseline edits are excluded until Apply. Original acquisitions remain untouched. Saved TopSpin phase is already present in the imported samples; the phase controls show the saved PHC0/PHC1 and apply additional adjustments. Saved `intrng` regions, bias/slope and `INTSCL` normalization are imported. Unsaved edits in another TopSpin process cannot be imported.

**File → Working project** shows the automatic location and retains optional comparison-project save/open controls. Projects use relative paths and can be opened directly even after the original acquisition has moved. Drop a project folder onto the app, enter its path, or use **Open project**. CSVs provide current corrected samples and integral values; JSON recipes and exact source arrays preserve the full editable state.

Use **File → Export** and choose a fresh destination outside acquisition directories. **Bruker / TopSpin** exports the selected spectrum as a normal dataset/experiment/pdata tree, including raw acquisition files, corrected `1r`/`1i`, `proc`/`procs`, `intrng` and `integrals.txt`. Phase uses the validated native save kernel; baseline results are quantized to the existing Bruker NC_proc. Saved integrals are recalculated from the final integer spectrum. Normalization and effective region bias/slope are preserved. Export keeps genuine source headers and audit files, without generating fake TopSpin processing history or adding application branding. No claim of forensic indistinguishability is made. See [PROJECT-FORMAT.md](PROJECT-FORMAT.md) for the format and validation scope.

**Spectrum and integrals CSV** writes two files in a new export directory. Spectrum columns are index, ppm, source real/imaginary and corrected real/imaginary. Missing imaginary components are empty fields. Integration columns include bounds, raw/normalized areas, effective bias/slope and normalization factor. Floating values use round-trip scientific precision. These CSVs describe the floating preview, before Bruker integer quantization. Neither CSV nor project/Bruker export writes into an original acquisition. Existing `.nmr.json` comparisons still work as lightweight path-based sessions; they do not include the underlying data.

The **Stack** window appears only when at least two spectra are visible and stays anchored at the top-right corner. Drag its rows to change order, click a row to select a spectrum, or uncheck it to hide it. The window can be resized and collapsed; its position stays anchored. Stack order is saved with comparison files. Reordering and closing individual tabs preserve the other spectra's integral ownership and processing settings.

## Performance

Folder scans, file reads and CSV writes run off the UI thread. Traces use min/max envelopes per physical screen column to preserve narrow peaks; cached geometry is reused until the viewport or spectrum settings change. Idle operation uses egui's event-driven repainting. Opening very many spectra can still consume substantial memory, and initial envelope generation is on the UI thread.

`bash run.sh --smoke [dataset paths...]` logs time to the first UI update and time until requested datasets have loaded and generated plot geometry, then exits. These are application instrumentation points, not compositor presentation timestamps or cold disk-cache benchmarks.

## Implemented native features

The app includes processed and supported raw 1D opening, natural-sort dataset browsing, overlay/stack comparisons, phase adjustment, manual degree 0–4 polynomial baseline correction, integration with region corrections and normalization, positive local-maximum peak picking, portable working projects, Bruker export and CSV export. The UI lists these native actions only. Full TopSpin feature parity remains outside the current implementation.

## Validation

`nix-shell rust-shell.nix --run 'cargo test && cargo clippy -- -D warnings'`

`nix-shell shell.nix --run 'python tests/verify_rust_reader.py "/path/to/NMRs"'`

The latter compares every decoded real/imaginary sample via ordered 64-bit rolling digests and compares exact axis endpoint bits against the independently compiled Java reader. It reads original acquisitions without modifying them. `--audit` provides machine-readable JSON for each processed dataset.

## Checked on November 4, 2026

Native scientific validation is recorded in [NATIVE-PROCESSING-VALIDATION.md](NATIVE-PROCESSING-VALIDATION.md). Run `bash tests/parity-audit/run-native.sh` for original-class polynomial/integration/phase checks, saved-region import checks, and captured Windows JVM cases. Run `bash tests/parity-audit/run-current.sh` for the 33 region comparisons that previously failed. Run `nix-shell rust-shell.nix --run 'cargo test && cargo clippy -- -D warnings'` for application workflow checks.

## Stack and raw-opening checks

`bash tests/parity-audit/run-raw-stack.sh` compares complete real/imaginary buffers and both axis endpoints with the retained validated Java/C processor. All 90 readable processing numbers in the current lab folder match on 11,796,660 raw-bit comparisons. The same existing truncated MLN1P93 component is rejected. No original files are modified. The UI tests also cover real egui drag/drop, hover-S with search focus, per-spectrum wheel zoom, late asynchronous loads and region ownership after reordering/removing tabs. This is validation of the native raw bridge against the retained processor, not new licensed-TopSpin captures for every acquisition.

Automatic project release checks on November 4, 2026 reached the first UI update in 46.13 ms. Initial conversion of two raw-backed spectra, including acquisition copies and named CSVs, reached ready state in 344.00 ms. Reopening the same two spectra reused their projects and reached ready state in 64.89 ms. These are application instrumentation points, not compositor presentation times.

## Undo, redo and checkpoints

Toolbar Undo/Redo and Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y restore scientific recipes on the selected spectrum: phase, applied baseline coefficients, integral creation/deletion, boundaries, bias/slope, endpoint correction and normalization. Source samples and display gain, offset, visibility and zoom stay outside history. A slider drag or phase-wheel burst forms one step; text edits finish on Enter or focus loss. Baseline previews enter history only on Apply. Apply or cancel a preview before using global Undo/Redo.

History follows each spectrum when the stack is reordered. The last 200 accepted edits are retained while that spectrum remains open. A new scientific edit after Undo clears its redo branch. Reopening starts a new undo session.

Use **Tools → History / checkpoints** to name and save a processing checkpoint. Each checkpoint stores phase adjustments, baseline coefficients, integral recipes and normalization, without copying source samples. Checkpoints autosave inside that spectrum's project settings and survive reopening or portable project copies. Restore changes the selected spectrum only and is itself undoable. Checkpoint creation and restoration require finishing a baseline preview first. Restored edits follow ordinary autosave; original Bruker acquisitions remain untouched.

## Simple workspace and automatic processing

The default workspace exposes **Open → Process → Integrate → Export**. Open focuses the browser path field; folders can also be dropped onto the app. Process and Export use the existing docked tool panel. Advanced exposes the earlier detailed command row, command entry, manual phase and polynomial controls, display options and peak picking. Detailed integral bias/slope/endpoint controls are also under Advanced. The existing themes, scientific controls and shortcuts remain available.

Process offers separate automatic phase and baseline proposals or a combined phase-then-baseline proposal. They run on a background worker with bounded search/fitting samples. Results become a preview only if the selected source and original processing recipe still match. Before/After switches the rendered spectrum and recalculated integral curves. Accept commits the candidate even when Before is currently displayed. Cancel, changing tools, hiding the panel, closing the spectrum or exiting discards the preview. Preview phase/baseline settings are excluded from undo observation, checkpoint creation and saves/exports. Acceptance creates one scientific undo step and resumes normal autosave.

Automatic phase searches zero- and first-order adjustments using derivative entropy plus a negative-energy penalty. It requires a complete imaginary component and resolved spectral structure. The approach is related to the derivative-entropy family documented by [nmrglue](https://nmrglue.readthedocs.io/en/latest/reference/proc_autophase.html), but the sampling, score and search here are independent implementations. It does not reproduce TopSpin auto-phase commands. Negative residual energy and extreme first-order corrections produce review warnings. The supported routine workflow assumes positive absorption signals; inverted, broad, overlapping and weak signals require manual review.

Automatic baseline fits a degree-four polynomial in normalized sample coordinates with repeated robust residual masking and reweighting. It then converts the result into the existing manual polynomial recipe. Dense masking, large curvature and a fit explaining most signal energy produce warnings. This global polynomial cannot model arbitrary ripples or distinguish every broad peak from baseline. It is separate from the existing TopSpin manual-polynomial differential checks and from automatic `abs`.

Synthetic tests cover known complex Lorentzian phase distortions, a curved baseline with narrow signals, integrated-peak preservation, invalid inputs, preview cancellation, acceptance/undo/redo and stale-worker rejection. These checks are not a general accuracy certification across all NMR experiments.

## Chemical-shift referencing

In Process, enter the known shift, enable **Pick reference peak in spectrum**, click a peak, then **Apply reference**. Picking snaps to the nearest local absolute maximum within six screen pixels and at most two percent of the spectral width. Flat regions retain the exact cursor coordinate; for a 13C solvent multiplet, pick the center deliberately. Escape cancels picking. Reset reference adjustment restores the imported axis.

Referencing is an additive per-spectrum ppm shift. Source sample arrays and existing source-coordinate integral bounds remain unchanged, preserving existing measured areas exactly. New integrations and bound edits convert displayed ppm back to source coordinates. Plotting, peak picking, table values and tooltips show the referenced axis. Undo/redo and named checkpoints include the reference shift. Project JSON defaults missing shifts to zero for older projects.

CSV writes referenced ppm and referenced integral bounds. Bruker exports update processing OFFSET and intrng bounds while retaining source acquisition files. Export readback verifies the resulting arrays and integrals. An insignificant endpoint floating-point tolerance is used for the translated axis only; unreferenced axis checks remain bit-exact.

Nucleus and solvent are read from the saved acquisition parameters when available and remain editable for choosing a suggestion. Suggestions are intentionally limited to 1H/13C CDCl3 and DMSO-d6. The values used are 7.26/77.16 ppm and 2.50/39.52 ppm respectively, as reported in this [primary experimental source](https://pubs.acs.org/doi/pdf/10.1021/acs.organomet.5b00097). They are suggestions, not automatic signal assignments, and sample conditions can change shifts. Unsupported nucleus/solvent combinations require a manually entered reference.
