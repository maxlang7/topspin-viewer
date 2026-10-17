#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if ! command -v javac >/dev/null || ! command -v gcc >/dev/null || ! pkg-config --exists freetype2; then
  exec nix-shell shell.nix --run 'bash verify.sh'
fi
mkdir -p build/app build/test build/native
javac -proc:none -cp lib/flatlaf-3.4.1.jar -d build/app src/*.java \
  tests/RawReference.java tests/SavedPhaseReference.java tests/ReaderReference.java \
  tests/WriterReference.java tests/LiveNumericsReference.java tests/LiveIntegrationReference.java \
  tests/DatasetOpeningReference.java tests/ViewerWorkflowReference.java tests/IntegrationEditReference.java tests/IntegrationReopenReference.java tests/PlotRasterReference.java tests/IntegralTableReference.java tests/IntegralTableRasterProbe.java tests/SaveTransactionReference.java \
  tests/CapturedAxisReference.java tests/CapturedSpectrumReference.java tests/CapturedIntegralPanelReference.java tests/CursorMessageRasterReference.java tests/WindowsTextReference.java tests/NativeTextRasterReference.java tests/RibbonRasterReference.java tests/WindowsPhaseControllerReference.java
gcc -std=c11 -O2 -fPIC -shared -ffp-contract=off \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  -o build/native/libtopspin_reconstructed.so native/kernel.c native/jni.c -lm
gcc -std=c11 -O2 -fPIC -shared $(pkg-config --cflags freetype2) \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  -o build/native/libtopspin_font.so native/font.c $(pkg-config --libs freetype2)
for task_root in artifacts/reference-datasets artifacts/held-out-references artifacts/parameter-references artifacts/full-lab-references; do
  java -Djava.library.path=build/native -cp build/app RawReference "$task_root"
  java -Djava.library.path=build/native -cp build/app SavedPhaseReference "$task_root/captures"
  java -Djava.library.path=build/native -cp build/app WriterReference "$task_root/captures"
done
java -Djava.awt.headless=true -cp build/app CapturedAxisReference
java -Djava.awt.headless=true -cp build/app CapturedSpectrumReference
java -Djava.awt.headless=true -cp build/app CapturedIntegralPanelReference
java -Djava.awt.headless=true -cp build/app CursorMessageRasterReference
java -cp build/app ReaderReference artifacts/reference-datasets/captures
java -cp build/app LiveNumericsReference artifacts/live-jvm-reference
java -cp build/app WindowsPhaseControllerReference artifacts/windows-phase-controller.tsv
java -cp build/app LiveIntegrationReference artifacts/live-integration-reference artifacts/reference-datasets/captures/MLN1P121/pk-manual
task_edits_root="$(cat artifacts/latest-integration-edits-root.txt)"
task_edits_root="${task_edits_root#Z:}"
task_edits_root="${task_edits_root//\\//}"
java -cp build/app IntegrationEditReference "$task_edits_root"
task_reopen_root="$(cat artifacts/latest-integration-reopen-root.txt)"
task_reopen_root="${task_reopen_root#Z:}"
task_reopen_root="${task_reopen_root//\\//}"
java -cp build/app IntegrationReopenReference "$task_reopen_root" artifacts/viewer-workflow-16708493597640484641/sample/10/pdata/2
java -Djava.awt.headless=true -cp build/app PlotRasterReference artifacts/viewer-workflow-16708493597640484641/sample/10/pdata/2 artifacts/original-windows-spectrum-raster.png
java -Djava.awt.headless=true -cp build/app:lib/flatlaf-3.4.1.jar IntegralTableReference artifacts/viewer-workflow-16708493597640484641/sample/10/pdata/2 artifacts/integral-table-reference.tsv
java -Djava.library.path=build/native -cp build/app DatasetOpeningReference artifacts/reference-datasets/datasets/MLN1P121/10
java -Djava.library.path=build/native -cp build/app SaveTransactionReference artifacts/reference-datasets/datasets/MLN1P121/10
java -Djava.awt.headless=true -Djava.library.path=build/native -cp build/app:lib/flatlaf-3.4.1.jar NativeTextRasterReference
task_vendor_root="${TOPSPIN_REFERENCE_ROOT:-/home/maxlang/.wine-topspin-5/drive_c/Bruker/TopSpinProcessing5.0.0}"
javac -proc:none -cp "$task_vendor_root/classes/lib/*" -d build/test \
  src/Numerics.java src/IntegrationRegions.java src/IntegralScale.java src/VisualNumerics.java src/SpectrumPainter.java src/PointerGesture.java src/PlotSelection.java src/ViewRanges.java tests/VendorDifferential.java \
  src/CursorReadout.java tests/CursorReadoutReference.java tests/CursorBoundariesReference.java src/Assets.java src/PhaseCaption.java src/IntegralLabelPainter.java src/IntegralCurvePainter.java tests/IntegralCurveRasterReference.java tests/IntegralDisplayRangeReference.java tests/PhaseCaptionReference.java tests/IntegralLabelRasterReference.java src/AxisTicks.java src/AxisPainter.java tests/AxisTicksReference.java tests/AxisRasterReference.java tests/VerticalAxisRasterReference.java tests/IntegrationFileReference.java tests/IntegralScaleReference.java tests/IntegrationUndoReference.java tests/DisplayCompressionReference.java tests/DisplayMappingReference.java tests/PivotProbe.java tests/PhaseControlReference.java tests/PlotSelectionReference.java tests/SpectrumRasterDifferential.java tests/PointerGestureReference.java tests/ViewRangesReference.java
java -Djava.awt.headless=true -cp "build/test:$task_vendor_root/classes/lib/*" CursorReadoutReference
java -Djava.awt.headless=true -cp "build/test:$task_vendor_root/classes/lib/*" CursorBoundariesReference
java -Djava.awt.headless=true -cp "build/test:$task_vendor_root/classes/lib/*" IntegralCurveRasterReference
java -Djava.awt.headless=true -cp "build/test:$task_vendor_root/classes/lib/*" IntegralDisplayRangeReference
java -Djava.awt.headless=true -cp "build/test:$task_vendor_root/classes/lib/*" IntegralLabelRasterReference
java -Djava.awt.headless=true -cp "build/test:$task_vendor_root/classes/lib/*" PhaseCaptionReference
java -Djava.awt.headless=true -cp "build/test:$task_vendor_root/classes/lib/*" AxisTicksReference
java -Djava.awt.headless=true -cp "build/test:$task_vendor_root/classes/lib/*" AxisRasterReference
java -Djava.awt.headless=true -cp "build/test:$task_vendor_root/classes/lib/*" VerticalAxisRasterReference
java -Djava.awt.headless=true -cp "build/test:$task_vendor_root/classes/lib/*" VendorDifferential
java -Djava.awt.headless=true -cp "build/test:$task_vendor_root/classes/lib/*" IntegrationFileReference
java -Djava.awt.headless=true -cp "build/test:$task_vendor_root/classes/lib/*" IntegralScaleReference
java -Djava.awt.headless=true -cp "build/test:$task_vendor_root/classes/lib/*" IntegrationUndoReference
java -Djava.awt.headless=true -cp "build/test:$task_vendor_root/classes/lib/*" DisplayCompressionReference
java -Djava.awt.headless=true -cp "build/test:$task_vendor_root/classes/lib/*" DisplayMappingReference
java -Djava.awt.headless=true -cp "build/test:$task_vendor_root/classes/lib/*" SpectrumRasterDifferential
java -Djava.awt.headless=true -cp "build/test:$task_vendor_root/classes/lib/*" PivotProbe
java -Djava.awt.headless=true -cp "build/test:$task_vendor_root/classes/lib/*" PhaseControlReference
java -Djava.awt.headless=true -cp "build/test:$task_vendor_root/classes/lib/*" PlotSelectionReference
java -Djava.awt.headless=true -cp "build/test:$task_vendor_root/classes/lib/*" PointerGestureReference
java -Djava.awt.headless=true -cp "build/test:$task_vendor_root/classes/lib/*" ViewRangesReference
if [ -n "${DISPLAY:-}" ]; then
  _JAVA_AWT_WM_NONREPARENTING=1 java -Dsun.java2d.uiScale=2 -Djava.library.path=build/native \
    -cp build/app:lib/flatlaf-3.4.1.jar RibbonRasterReference
  _JAVA_AWT_WM_NONREPARENTING=1 java -Dsun.java2d.uiScale=2 \
    -cp build/app:lib/flatlaf-3.4.1.jar IntegralTableRasterProbe artifacts/viewer-workflow-16708493597640484641/sample/10/pdata/2
  _JAVA_AWT_WM_NONREPARENTING=1 java -Dsun.java2d.uiScale=2 -Djava.library.path=build/native \
    -cp build/app:lib/flatlaf-3.4.1.jar ViewerWorkflowReference artifacts/reference-datasets/datasets/MLN1P121/10
fi
