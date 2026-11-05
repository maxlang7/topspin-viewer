//! Referencing changes the coordinate system, never the measured sample intervals.
use super::*;
#[derive(Default)]
pub(super) struct ReferenceUi {
    pub picking: bool,
    picked: Option<f64>,
    target: f64,
    metadata_path: Option<PathBuf>,
    nucleus: String,
    solvent: String,
}
fn solvent_target(nucleus: &str, solvent: &str) -> Option<f64> {
    let solvent = solvent.to_lowercase().replace([' ', '-', '_'], "");
    match (nucleus.trim_matches(['<', '>']), solvent.as_str()) {
        ("1H", "cdcl3" | "chloroform" | "chloroformd") => Some(7.26),
        ("13C", "cdcl3" | "chloroform" | "chloroformd") => Some(77.16),
        ("1H", "dmso" | "dmsod6") => Some(2.50),
        ("13C", "dmso" | "dmsod6") => Some(39.52),
        _ => None,
    }
}
impl App {
    pub(super) fn reference_controls(&mut self, ui: &mut egui::Ui) {
        ui.strong("Reference chemical shifts");
        let Some(t) = self.traces.get(self.selected) else {
            ui.label("Open a spectrum first");
            return;
        };
        if self.reference.metadata_path.as_ref() != Some(&t.data.path) {
            self.reference = ReferenceUi::default();
            self.reference.metadata_path = Some(t.data.path.clone());
            if let Ok(p) = storage::source_processing(&t.data.path) {
                let acq = p.ancestors().nth(2).map(|p| p.join("acqus"));
                if let Some(Ok(values)) = acq.map(|p| data::parameters(&p)) {
                    self.reference.nucleus = values
                        .get("NUC1")
                        .cloned()
                        .unwrap_or_default()
                        .trim_matches(['<', '>'])
                        .to_string();
                    self.reference.solvent = values
                        .get("SOLVENT")
                        .cloned()
                        .unwrap_or_default()
                        .trim_matches(['<', '>'])
                        .to_string();
                }
            }
        }
        ui.label(format!(
            "Current adjustment: {:+.5} ppm",
            t.settings.reference_shift
        ));
        ui.horizontal(|ui| {
            ui.label("Nucleus");
            ui.text_edit_singleline(&mut self.reference.nucleus);
        });
        ui.horizontal(|ui| {
            ui.label("Solvent");
            ui.text_edit_singleline(&mut self.reference.solvent);
        });
        if let Some(value) = solvent_target(&self.reference.nucleus, &self.reference.solvent) {
            if ui
                .button(format!("Use solvent suggestion: {value:.2} ppm"))
                .clicked()
            {
                self.reference.target = value;
            }
            ui.weak("Check the solvent assignment. Shifts vary with sample conditions. For a 13C multiplet, pick its center.");
        } else {
            ui.weak(
                "Enter a known reference shift. Suggestions support 1H/13C in CDCl3 and DMSO-d6.",
            );
        }
        ui.add(
            egui::DragValue::new(&mut self.reference.target)
                .speed(0.001)
                .prefix("Known shift ")
                .suffix(" ppm"),
        );
        if ui
            .selectable_label(self.reference.picking, "Pick reference peak in spectrum")
            .clicked()
        {
            self.reference.picking = !self.reference.picking;
        }
        if self.reference.picking {
            ui.colored_label(
                self.palette().accent,
                "Click the reference peak. Escape cancels picking.",
            );
        }
        if let Some(picked) = self.reference.picked {
            ui.label(format!("Picked: {picked:.6} ppm"));
            if ui.button("Apply reference").clicked()
                && let Err(e) = self.apply_reference(picked, self.reference.target)
            {
                self.status = e;
            }
        }
        if ui.button("Reset reference adjustment").clicked() {
            let delta = self.traces[self.selected].settings.reference_shift;
            if let Err(e) = self.apply_reference(delta, 0.) {
                self.status = e;
            }
        }
    }
    pub(super) fn pick_reference(&mut self, ppm: f64, pixels: f32, span: f64) {
        let Some(t) = self.traces.get(self.selected) else {
            return;
        };
        if self.reference.metadata_path.as_ref() != Some(&t.data.path) {
            self.reference.picking = false;
            return;
        }
        // Snap to a local absolute maximum within six screen pixels. Clicking a flat
        // region leaves the exact cursor coordinate available for multiplet centers.
        let n = t.data.real.len();
        let radius = (span.abs() / pixels.max(1.) as f64 * 6.).min((t.left() - t.right()) * 0.02);
        let index = |x: f64| {
            (((t.left() - x) / (t.left() - t.right()) * n as f64).round() as isize)
                .clamp(0, n.saturating_sub(1) as isize) as usize
        };
        let lo = index(ppm + radius);
        let hi = index(ppm - radius);
        let max = (lo..=hi)
            .filter(|&i| {
                i > 0
                    && i + 1 < t.processed.len()
                    && t.processed[i].abs() > t.processed[i - 1].abs()
                    && t.processed[i].abs() >= t.processed[i + 1].abs()
            })
            .min_by(|&a, &b| (t.ppm(a) - ppm).abs().total_cmp(&(t.ppm(b) - ppm).abs()));
        self.reference.picked = Some(max.map_or(ppm, |i| t.ppm(i)));
        self.reference.picking = false;
    }
    pub(super) fn apply_reference(&mut self, picked: f64, target: f64) -> Result<(), String> {
        if self.has_processing_preview() {
            return Err("Apply or cancel the processing preview before referencing".into());
        }
        let t = self
            .traces
            .get(self.selected)
            .ok_or("Open a spectrum first")?;
        let shift = t.settings.reference_shift + target - picked;
        if !picked.is_finite()
            || !target.is_finite()
            || !shift.is_finite()
            || !(t.data.left + shift).is_finite()
            || !(t.data.right + shift).is_finite()
        {
            return Err("Reference values must be finite".into());
        }
        if t.data.left + shift <= t.data.right + shift {
            return Err("Reference adjustment is too large for the spectrum axis".into());
        }
        self.sync_history(true);
        self.traces[self.selected].settings.reference_shift = shift;
        self.peaks.clear();
        self.range = None;
        self.reference.picked = None;
        self.reference.picking = false;
        self.sync_history(true);
        self.status = format!("Reference set to {target:.5} ppm · autosaving");
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn reference_preserves_samples_and_integrals_and_is_undoable() {
        let mut a = auto_process::tests::app();
        a.measure_region(7., 4.);
        a.refresh_processing();
        a.sync_history(true);
        let area = a.regions[0].value.to_bits();
        let bounds = [a.regions[0].left, a.regions[0].right];
        let source = a.traces[0].data.real.clone();
        let samples = a.traces[0].processed.clone();
        let peak = a.traces[0]
            .processed
            .iter()
            .enumerate()
            .max_by(|a, b| a.1.total_cmp(b.1))
            .unwrap()
            .0;
        let peak_ppm = a.traces[0].ppm(peak);
        a.reference.metadata_path = Some(a.traces[0].data.path.clone());
        a.reference.picking = true;
        a.pick_reference(peak_ppm + 0.001, 700., 10.);
        assert_eq!(a.reference.picked.unwrap().to_bits(), peak_ppm.to_bits());
        assert!(!a.reference.picking);
        let original = a.traces[0].left();
        a.apply_reference(7., 7.26).unwrap();
        a.refresh_processing();
        let shift = a.traces[0].settings.reference_shift;
        assert_eq!(a.traces[0].left(), original + shift);
        assert_eq!(a.traces[0].ppm(0), original + shift);
        assert_eq!(a.regions[0].value.to_bits(), area);
        assert_eq!([a.regions[0].left, a.regions[0].right], bounds);
        assert_eq!(*a.traces[0].processed, *samples);
        assert_eq!(a.traces[0].data.real, source);
        a.undo_redo(false);
        assert_eq!(a.traces[0].settings.reference_shift, 0.);
        a.undo_redo(true);
        assert_eq!(
            a.traces[0].settings.reference_shift.to_bits(),
            shift.to_bits()
        );
        assert!(a.apply_reference(f64::NAN, 7.26).is_err());
        assert!(a.apply_reference(7., 1e100).is_err());
        assert_eq!(solvent_target("1H", "CDCl3"), Some(7.26));
        assert_eq!(solvent_target("13C", "DMSO-d6"), Some(39.52));
        assert_eq!(solvent_target("19F", "CDCl3"), None);
    }
}
