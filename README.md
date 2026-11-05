# NMR Viewer

A native Rust desktop viewer for Bruker 1D NMR data, with a simple Open → Process → Integrate → Export workflow and selectable Gruvbox Dark, neutral Dark, and conventional Light themes.

Build with `bash build-rust.sh`, then launch with `bash run.sh [dataset or project folders...]`. Startup executes the optimized binary directly without compiling or evaluating Nix.

Click datasets to open them, or hover and press S to stack them. Reorder spectra in the top-right Stack window. Wheel above baseline changes the hovered spectrum's intensity; wheel below baseline zooms ppm. Integration curves remain above baseline, numbers below, and range markers at the top. Hold Shift and drag to integrate, or use the Integrate button. Hover a number and press D to delete it; double-click a number and enter its proton count to calibrate the spectrum. Shift+scroll does nothing.

The app supports phase adjustment, manual polynomial baseline correction, integration and normalization. Opening an NMR automatically creates or reopens a project with the same dataset name under `~/Documents/NMR Projects`. It keeps a named spectrum CSV, an integrations CSV, exact float64 source data, acquisition snapshots and versioned edit metadata. Phase, applied baseline, integration and normalization edits autosave in the background. No Save action is required. Original Bruker files remain untouched.

**File → Export** writes a fresh Bruker dataset for TopSpin or spectrum/integral CSVs to a separate directory. The UI contains only implemented native features; there are no launchers for the earlier viewer.

See [RUST.md](RUST.md) for controls and performance, [PROJECT-FORMAT.md](PROJECT-FORMAT.md) for storage/export details, and [NATIVE-PROCESSING-VALIDATION.md](NATIVE-PROCESSING-VALIDATION.md) for the numerical validation boundary. Full TopSpin parity and indistinguishable processing provenance are not claimed. Earlier source and reference tests remain in Git.

Run native tests with `nix-shell rust-shell.nix --run 'cargo test && cargo clippy -- -D warnings'`. The full source history is backed up to the private GitHub repository `maxlang7/topspin-viewer`. Generated files under `artifacts/`, build outputs and local environments remain ignored.

The default toolbar shows Open, Process, Integrate and Export. Advanced reveals manual phase, polynomial coefficients, peak picking, display controls and command entry. Process, Export, Phase, Baseline, Integrals, Peaks, Display and History share one collapsible right-hand tool panel. Switching tools replaces its contents; scientific editors no longer open floating windows. The single command row groups File/View/Tools menus beside Phase, Baseline, Integrate, Undo/Redo and zoom controls. The desktop spectrum logo sits at its upper left. Settings remembers the selected theme. Showing integrals keeps the spectrum baseline and intensity scale fixed. Labels use bounded rows below baseline; a +N integrals menu exposes crowded values without moving the spectrum.

Ctrl+Alt+scroll adjusts zero-order phase on the spectrum under the pointer; Ctrl+scroll also adjusts zero-order phase. There is no first-order phase shortcut. Home restores the full ppm range, Ctrl+Home resets the selected spectrum’s gain and offset, Ctrl+, opens Settings, Ctrl+E opens export options, Ctrl+P opens the docked phase controls, and Ctrl+B opens Baseline. Escape cancels the active interaction or closes a popup or editor. Shortcuts defer to text entry.

Undo scientific edits with Ctrl+Z; redo with Ctrl+Shift+Z or Ctrl+Y. History follows each spectrum through stack reordering and keeps the last 200 edits during the session. Open **Tools → History / checkpoints** to save named processing checkpoints. Checkpoints include phase, baseline, integral regions and calibration, survive project reopening, and restore as an undoable edit. All accepted edits and undo/redo results autosave.

Use **Process** to preview automatic phase, automatic baseline, or both. Calculations run in the background. Compare **Before / After**, then **Accept processing** or **Cancel**. Only accepted processing autosaves and enters undo history. These independent algorithms target routine positive 1D spectra; broad or inverted signals need review. Automatic baseline is a robust degree-four polynomial fit, not TopSpin's automatic `abs` algorithm.

**Process → Reference chemical shifts** lets you pick a peak, enter its known ppm, and apply the reference. Matching nucleus/solvent suggestions currently cover 1H and 13C in CDCl3 and DMSO-d6. Referencing is undoable and persists in projects, CSV and Bruker exports. It leaves source samples and existing integration sample intervals unchanged.
