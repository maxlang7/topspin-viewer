# Integration and baseline parity audit

Historical audit of `be0355f`. The native integration regression and missing manual polynomial model have since been addressed. See [native processing validation](NATIVE-PROCESSING-VALIDATION.md) for the current supported scope and passing checks. The original failed evidence below is retained.

Date: November 4, 2026. Production source audited at commit `be0355f`.

**Verdict: FAIL. The default Rust application does not produce bit-for-bit TopSpin-equivalent integrals. Native spectrum baseline correction is not implemented. The requested guarantee cannot be made.**

No production numerical code was changed during this audit. Tests read original lab data. Save/failure tests modify isolated dataset copies only.

## Direct check of the production Rust integrator

`tests/parity-audit/current_rust.rs` imports the actual `rust/data.rs`, loads datasets through the production reader, and calls `Spectrum::integral` without phase correction. It emits decimal values and exact IEEE-754 bits. `CurrentRustParity.java` compares these values against the original installed TopSpin `Integral` class on identical samples and physical ranges, with baseline correction off and bias/slope zero. It does not compare Rust against a second hand-written algorithm.

Results: **33 regions tested, zero exact matches, 33 mismatches**. These comprise three regions on each of ten valid current lab spectra plus a four-sample synthetic fixture. The existing truncated MLN1P93 component is skipped explicitly. These are three deterministic ranges per dataset, not exhaustive region enumeration.

The synthetic fixture contains `[1, 2, 3, 4]`, with a one-ppm sample step. Across the four sample positions, Rust returns `10.0`, raw bits `4024000000000000`; TopSpin returns `6.0`, raw bits `4018000000000000`. The original integral routine excludes the final selected sample. Rust also multiplies by the ppm sample step, whereas this TopSpin result is in sample-sum units. Different region-index mapping, saved normalization, and local bias/slope handling introduce further differences. Scaling Rust values afterward is insufficient to restore parity.

Full results are in [current-results.tsv](tests/parity-audit/results/current-results.tsv). The test intentionally returns a failing exit status when differences exist.

## Baseline correction is a separate missing capability

The Rust integrator has no baseline-correction inputs or correction algorithm. It calculates a phase-preview sample sum multiplied by the ppm step. It does not read or apply saved integration bias/slope and INTSCL normalization. Its measurements are snapshots, not a compatible TopSpin integration-region model.

The Rust ribbon's Baseline action merely starts the retained Java/C application. That action does not execute a spectrum correction. The retained app has manual **integral** bias/slope controls. Neither application implements the full family of TopSpin spectrum-baseline processing commands. Moving a displayed baseline vertically is also only a display operation; it does not correct spectrum samples.

The original TopSpin integral routine's optional endpoint-average baseline adjustment is covered by the retained numerical differential tests. That establishes equivalence of that particular Java routine on tested inputs, not equivalence of whole-spectrum baseline-processing commands or a native Rust workflow.

## Retained Java/C validation

All twelve selected test programs pass in a fresh run:

- VendorDifferential: 928 mixed phase/integral cases and 7,179,212 bitwise comparisons. Includes 288 integral parameter combinations across short/long buffers, baseline-correction flags, baseline-point counts, bias, slope and normalization. Of these, 63 execute endpoint baseline adjustment with more than one baseline point.
- LiveNumericsReference: 84 saved actual Windows TopSpin JVM cases, comprising 48 phase and 36 integral cases, 4,980,744 bitwise comparisons.
- LiveIntegrationReference: three captured interactive regions, 3,773 curve comparisons, normalization and saved-region text.
- IntegrationEditReference: 15,171 exact values for captured bias/slope/split operations.
- IntegrationReopenReference: three saved regions reopened in a prior actual TopSpin capture, 3,773 curve comparisons and normalization.
- IntegrationFileReference: four NC_proc variants, 2,019 exact serialized characters and 5,296 sample comparisons.
- IntegralScaleReference: 6,000 normalization transitions, 24,004 bitwise comparisons.
- IntegrationUndoReference: 608 controller transitions, 19,009 bitwise comparisons.
- IntegrationSelectionReference: 12,000 selection transitions and 1,516 selected bias/slope/reset and deletion/undo operations.
- IntegrationDisplayScaleReference: 6,000 operations, 70,200 bitwise comparisons.
- IntegrationDialogAddReference: 600 additions and 144,000 bitwise comparisons.
- SaveTransactionReference: staged-data rejection, rollback, original preservation and 131,072 component comparisons.

Logs: [legacy-results.txt](tests/parity-audit/results/legacy-results.txt).

These tests validate the retained Java/C paths. **They do not validate Rust integration.** Original-class arithmetic/controller checks execute installed vendor classes on in-memory test data. Live-JVM, interactive editing and reopen checks replay existing captured outputs, not newly generated licensed TopSpin UI sessions. No claim of all possible inputs or actions follows from these passing tests. The native Rust phase preview also uses different direct-trigonometry arithmetic, so phase-then-integrate needs separate validation even after an integration port.

## Reference identity and reproduction

Reference installation: `~/.wine-topspin-5/drive_c/Bruker/TopSpinProcessing5.0.0`.

Original class loaded from `classes/lib/top_base.jar`, SHA256:

`6c24fb17cf46c25c6260301a2f94d1d35658abe12b735971b86cac5ef4588b3b`

Run the retained Java/C checks with:

```
bash tests/parity-audit/run.sh
```

Run the failing direct production-Rust comparison with:

```
bash tests/parity-audit/run-current.sh
```

Both scripts use temporary Nix shells as needed and require the existing local reference installation. Generated fixtures and binaries stay in ignored build directories. Proprietary reference code is not copied into the repository.

## Work required before certification

The Rust app needs the exact region-index and endpoint rules, cumulative integration arithmetic, baseline bias/slope and endpoint correction, normalization, saved-region formats, and edit/save/reopen state transitions. The interactive Rust actions must then be tested against original TopSpin actions on isolated copies. Whole-spectrum baseline correction needs its own implementation and command-specific reference captures. Until those exist and pass, the requested guarantee remains false.
