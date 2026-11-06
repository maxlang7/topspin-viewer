# TopSpin appearance verification

## Result and scope

The viewer now has a persistent **Appearance → TopSpin** mode which switches live, including the ribbon, browser, command footer, fonts, palette, plot margins, spectrum title and axes. Gruvbox, Dark and Light remain available. This is a native Rust interface using the viewer's processing and project model.

**The requested exact visual match is not yet achieved.** The full empty-client comparison has 97.3887% identical pixels; the large blank workspace contributes substantially to that figure. The screenshot comparison is deliberately strict and reports `pixel_exact: false`. Similar background pixels are not evidence of equivalent controls or workflows. This appearance check does not establish numerical processing or export parity.

## Evidence

The files in [evidence/ui/rust-topspin-mode](evidence/ui/rust-topspin-mode) contain the actual installed TopSpin 5.0.0 window and native viewer captures, a side-by-side comparison, an unmasked pixel difference image and machine-readable results. The spectrum capture demonstrates the mode with MLN1P121 open. Scientific samples and integral values are not altered by an appearance switch.

Both applications were captured through their own X11 window IDs on the same laptop at 2× scale. The reference capture includes Wine's 32 physical pixel title bar; only that title bar is removed. The client comparison is 2876 × 1822 pixels. Images are not resized and content is not masked. The empty view is used for pixel comparison because it avoids different dataset contents being counted as layout differences. Loaded-spectrum styling is reviewed separately against the existing TopSpin integration/phase captures in `evidence/ui`.

Reproduce the strict comparison with:

```sh
nix-shell -p python3Packages.pillow --run 'python tests/compare_topspin_appearance.py evidence/ui/rust-topspin-mode/topspin-live.png evidence/ui/rust-topspin-mode/viewer-final.png artifacts/topspin-appearance-recheck'
```

A nonzero exit status means the images differ. `--allow-differences` is available for investigation; it does not change the `pixel_exact` result.

## Remaining differences

- Font rasterization, some widget borders and spacing, tree controls and scrollbar styling still differ. The browser search strip, pane sizes, split position, lower placeholder and export strip now use measured reference dimensions.
- Browser roots and status text reflect the native application's actual contents.
- Unsupported TopSpin features remain absent, following the existing requirement to hide unimplemented controls. Their ribbon space is therefore different.
- Editing uses the native docked tools. Integral labels and fixed-baseline behavior retain the requested native interaction design.
- The reference logo and icon assets are retained for this private prototype; these assets require a licensing review before public distribution.

## Functional checks

The release application was exercised through real menu clicks: TopSpin → Gruvbox → TopSpin in the same process, with saved settings read back after each change. The isolated configuration also restored the saved mode on launch. A reference dataset was opened through Ctrl+O and Enter and captured. Search text entry, clearing the filter and the natural/A–Z sort menu were also exercised in the actual window. Test configuration was isolated from the user's appearance settings.

`cargo test`: 51 passed, 1 ignored (opt-in local laboratory export test). New tests verify reference panel geometry, toolbar actions, appearance preference round trips and bit-preservation of processing settings, spectrum samples and integral values across live appearance switches. `cargo fmt --check`, `cargo clippy --all-targets -- -D warnings`, and the optimized release build passed.

## Browser refinement

The second screenshot pass improved the full-client identical-pixel count from 95.3784% to 97.3887%, and the browser region from 83.3512% to 93.2402%. Both comparisons use the same reference, size and unmasked regions. The large blank workspace still makes the overall percentage more favorable than the individual controls. Unsupported controls remain hidden.

The search strip now has the measured full width, magnifier, clear control and filter menu. The reference pane split and export strip have fixed measured spacing. The command input has an explicit border and padding. Ctrl+O now opens the folder field in TopSpin mode too, and Escape cancels it.
