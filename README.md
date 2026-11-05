# NMR Viewer

A native Rust desktop viewer for Bruker 1D NMR data, with a Gruvbox theme and Process/Analyze/Manage layout.

Build with `bash build-rust.sh`, then launch with `bash run.sh [dataset or project folders...]`. Startup executes the optimized binary directly without compiling or evaluating Nix.

Click datasets to open them, or hover and press S to stack them. Reorder spectra in the top-right Stack window. Wheel above baseline changes the hovered spectrum's intensity; wheel below baseline zooms ppm. Integration curves remain above baseline, numbers below, and range markers at the top.

The app supports phase adjustment, manual polynomial baseline correction, integration and normalization. Opening an NMR automatically creates or reopens a project with the same dataset name under `~/Documents/NMR Projects`. It keeps a named spectrum CSV, an integrations CSV, exact float64 source data, acquisition snapshots and versioned edit metadata. Phase, applied baseline, integration and normalization edits autosave in the background. No Save action is required. Original Bruker files remain untouched.

**Manage → Export** writes a fresh Bruker dataset for TopSpin or spectrum/integral CSVs to a separate directory. The UI contains only implemented native features; there are no launchers for the earlier viewer.

See [RUST.md](RUST.md) for controls and performance, [PROJECT-FORMAT.md](PROJECT-FORMAT.md) for storage/export details, and [NATIVE-PROCESSING-VALIDATION.md](NATIVE-PROCESSING-VALIDATION.md) for the numerical validation boundary. Full TopSpin parity and indistinguishable processing provenance are not claimed. Earlier source and reference tests remain in Git.

Run native tests with `nix-shell rust-shell.nix --run 'cargo test && cargo clippy -- -D warnings'`. The repository and laboratory captures remain local; no remote is configured.
