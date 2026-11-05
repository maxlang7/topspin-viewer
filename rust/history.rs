//! Undo recipes only: source samples and display geometry never enter history.
use super::*;
use std::time::Duration;

#[derive(Clone, Serialize, Deserialize, PartialEq)]
pub(super) struct IntegralRecipe {
    left: f64,
    right: f64,
    bias: f64,
    slope: f64,
    baseline: bool,
    points: usize,
}
#[derive(Clone, Serialize, Deserialize, PartialEq)]
pub(super) struct EditState {
    #[serde(default)]
    reference_shift: f64,
    p0: f64,
    p1: f64,
    baseline: [f64; 5],
    scale: f64,
    frozen: bool,
    regions: Vec<IntegralRecipe>,
}
#[derive(Clone, Serialize, Deserialize)]
pub(super) struct Checkpoint {
    pub name: String,
    state: EditState,
}
impl Checkpoint {
    pub(super) fn valid(&self) -> bool {
        let s = &self.state;
        !self.name.trim().is_empty()
            && [s.p0, s.p1, s.scale, s.reference_shift]
                .iter()
                .chain(&s.baseline)
                .all(|v| v.is_finite())
            && s.regions.iter().all(|r| {
                r.points > 0
                    && [r.left, r.right, r.bias, r.slope]
                        .iter()
                        .all(|v| v.is_finite())
            })
    }
}
struct Step {
    before: EditState,
    after: EditState,
    label: String,
}
#[derive(Default)]
pub(super) struct History {
    current: Option<EditState>,
    pending: Option<EditState>,
    undo: Vec<Step>,
    redo: Vec<Step>,
}
impl History {
    fn observe(&mut self, state: EditState, commit: bool) {
        if let Some(current) = &self.current
            && *current != state
        {
            if self.pending.is_none() {
                self.pending = Some(current.clone());
            }
            self.redo.clear();
        }
        self.current = Some(state);
        if commit && let Some(before) = self.pending.take() {
            let after = self.current.as_ref().unwrap().clone();
            if before != after {
                let label = description(&before, &after).into();
                self.undo.push(Step {
                    before,
                    after,
                    label,
                });
                if self.undo.len() > 200 {
                    self.undo.remove(0);
                }
            }
        }
    }
}
fn description(before: &EditState, after: &EditState) -> &'static str {
    if before.reference_shift != after.reference_shift {
        "Reference spectrum"
    } else if before.p0 != after.p0 || before.p1 != after.p1 {
        "Phase adjustment"
    } else if before.baseline != after.baseline {
        "Baseline correction"
    } else if before.regions.len() < after.regions.len() {
        "Add integral"
    } else if before.regions.len() > after.regions.len() {
        "Delete integral"
    } else if before.scale != after.scale || before.frozen != after.frozen {
        "Calibrate integrals"
    } else {
        "Edit integral"
    }
}
impl App {
    fn edit_state(&self, index: usize) -> EditState {
        let t = &self.traces[index];
        let s = &t.settings;
        // Preview coefficients are not an accepted edit until Apply.
        let baseline = self
            .baseline_session
            .as_ref()
            .filter(|(path, _)| *path == t.data.path)
            .map_or(s.baseline, |(_, before)| *before);
        let (p0, p1, baseline) = self
            .preview_recipe(&t.data.path)
            .unwrap_or((s.p0, s.p1, baseline));
        EditState {
            reference_shift: s.reference_shift,
            p0,
            p1,
            baseline,
            scale: s.integral_scale,
            frozen: s.normalization_frozen,
            regions: self
                .regions
                .iter()
                .filter(|r| r.trace == index)
                .map(|r| IntegralRecipe {
                    left: r.left,
                    right: r.right,
                    bias: r.bias,
                    slope: r.slope,
                    baseline: r.baseline,
                    points: r.points,
                })
                .collect(),
        }
    }
    pub(super) fn sync_history(&mut self, commit: bool) {
        for i in 0..self.traces.len() {
            let state = self.edit_state(i);
            self.traces[i].history.observe(state, commit);
        }
    }
    pub(super) fn track_history(&mut self, ctx: &egui::Context) {
        let (wheel, pointer, typing) = ctx.input(|i| {
            (
                i.raw_scroll_delta != egui::Vec2::ZERO,
                i.pointer.primary_down(),
                !i.key_pressed(egui::Key::Enter),
            )
        });
        if wheel {
            self.history_wheel_at = Some(Instant::now());
        }
        let wheel_pending = self
            .history_wheel_at
            .is_some_and(|at| at.elapsed() < Duration::from_millis(250));
        let holding = pointer || wheel_pending || (typing && ctx.wants_keyboard_input());
        self.sync_history(!holding);
        if holding && self.traces.iter().any(|t| t.history.pending.is_some()) {
            ctx.request_repaint_after(Duration::from_millis(260));
        }
    }
    fn apply_edit_state(&mut self, index: usize, state: &EditState) {
        let s = &mut self.traces[index].settings;
        s.reference_shift = state.reference_shift;
        s.p0 = state.p0;
        s.p1 = state.p1;
        s.baseline = state.baseline;
        s.integral_scale = state.scale;
        s.normalization_frozen = state.frozen;
        self.traces[index].baseline_undo = None;
        self.regions.retain(|r| r.trace != index);
        self.regions.extend(state.regions.iter().map(|r| Region {
            trace: index,
            left: r.left,
            right: r.right,
            bias: r.bias,
            slope: r.slope,
            baseline: r.baseline,
            points: r.points,
            value: 0.,
            curve: vec![],
            calculation_key: vec![],
            error: None,
        }));
        self.selected_integral = None;
        self.integral_edit = None;
        self.hovered_integral = None;
        self.start = None;
        self.integration_drag_trace = None;
        self.peaks.clear();
        self.refresh_processing();
    }
    pub(super) fn undo_redo(&mut self, redo: bool) {
        if self.has_processing_preview() {
            self.status = "Apply or cancel the processing preview before undo/redo".into();
            return;
        }
        self.sync_history(true);
        let Some(t) = self.traces.get_mut(self.selected) else {
            return;
        };
        let history = &mut t.history;
        let step = if redo {
            history.redo.pop()
        } else {
            history.undo.pop()
        };
        let Some(step) = step else {
            return;
        };
        let state = if redo {
            step.after.clone()
        } else {
            step.before.clone()
        };
        self.status = format!(
            "{}: {} · autosaving",
            if redo { "Redo" } else { "Undo" },
            step.label
        );
        history.current = Some(state.clone());
        if redo {
            history.undo.push(step);
        } else {
            history.redo.push(step);
        }
        self.apply_edit_state(self.selected, &state);
    }
    pub(super) fn history_buttons(&mut self, ui: &mut egui::Ui) {
        let h = self.traces.get(self.selected).map(|t| &t.history);
        let undo = h.is_some_and(|h| !h.undo.is_empty() || h.pending.is_some())
            && !self.has_processing_preview();
        let redo = h.is_some_and(|h| !h.redo.is_empty()) && !self.has_processing_preview();
        if ui
            .add_enabled(undo, egui::Button::new("Undo"))
            .on_hover_text("Undo scientific edit · Ctrl+Z")
            .clicked()
        {
            self.undo_redo(false);
        }
        if ui
            .add_enabled(redo, egui::Button::new("Redo"))
            .on_hover_text("Redo scientific edit · Ctrl+Shift+Z or Ctrl+Y")
            .clicked()
        {
            self.undo_redo(true);
        }
    }
    pub(super) fn create_checkpoint(&mut self, name: &str) {
        if self.has_processing_preview() {
            self.status = "Apply or cancel processing preview before creating a checkpoint".into();
            return;
        }
        if self.selected >= self.traces.len() {
            return;
        }
        let name = name.trim();
        if name.is_empty() {
            self.status = "Give the checkpoint a name".into();
            return;
        }
        self.sync_history(true);
        let state = self.edit_state(self.selected);
        self.traces[self.selected]
            .settings
            .checkpoints
            .push(Checkpoint {
                name: name.into(),
                state,
            });
        self.status = format!("Checkpoint saved: {name} · autosaving");
    }
    pub(super) fn restore_checkpoint(&mut self, index: usize) {
        if self.has_processing_preview() {
            self.status = "Apply or cancel processing preview before restoring a checkpoint".into();
            return;
        }
        let Some(checkpoint) = self
            .traces
            .get(self.selected)
            .and_then(|t| t.settings.checkpoints.get(index))
            .cloned()
        else {
            return;
        };
        if !checkpoint.valid() {
            self.status = "Invalid checkpoint".into();
            return;
        }
        self.sync_history(true);
        if self.edit_state(self.selected) == checkpoint.state {
            self.status = format!("Already at checkpoint: {}", checkpoint.name);
            return;
        }
        self.apply_edit_state(self.selected, &checkpoint.state);
        self.sync_history(true);
        if let Some(step) = self.traces[self.selected].history.undo.last_mut() {
            step.label = format!("Restore checkpoint {}", checkpoint.name);
        }
        self.status = format!("Restored checkpoint: {} · autosaving", checkpoint.name);
    }
    pub(super) fn history_controls(&mut self, ui: &mut egui::Ui) {
        ui.horizontal(|ui| self.history_buttons(ui));
        ui.weak(
            "Undo/redo applies to the selected spectrum. Last 200 edits are kept for this session.",
        );
        ui.separator();
        ui.strong("Named checkpoints");
        ui.weak("Phase, baseline and integrals. Saved with this spectrum's project.");
        ui.text_edit_singleline(&mut self.checkpoint_name);
        if ui
            .add_enabled(
                !self.baseline_open && !self.traces.is_empty(),
                egui::Button::new("Save checkpoint"),
            )
            .clicked()
        {
            let name = self.checkpoint_name.clone();
            self.create_checkpoint(&name);
        }
        let mut restore = None;
        if let Some(t) = self.traces.get(self.selected) {
            for (i, c) in t.settings.checkpoints.iter().enumerate() {
                ui.horizontal(|ui| {
                    ui.label(&c.name);
                    if ui.button("Restore").clicked() {
                        restore = Some(i);
                    }
                });
            }
            ui.separator();
            ui.strong("Recent edits");
            for step in t.history.undo.iter().rev().take(30) {
                ui.label(&step.label);
            }
        }
        if let Some(i) = restore {
            self.restore_checkpoint(i);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn app() -> App {
        let mut a = App::state(Instant::now(), false);
        let data = Spectrum {
            path: "/synthetic/10/pdata/1".into(),
            title: "test".into(),
            real: vec![10., 11., 15., 13., 12., 10., 9., 8.],
            imag: vec![1., 2., -1., 0., 1., -1., 2., 0.],
            max_amplitude: 15.,
            nc: 0,
            phc0: 0.,
            phc1: 0.,
            ft_size: 8,
            integral_scale: 1.,
            saved_regions: vec![],
            left: 8.,
            right: 0.,
        };
        let settings = Settings {
            path: data.path.clone(),
            visible: true,
            gain: 1.,
            offset: 0.,
            p0: 0.,
            p1: 0.,
            baseline: [0.; 5],
            integral_scale: 1.,
            normalization_frozen: false,
            checkpoints: vec![],
            reference_shift: 0.,
        };
        a.traces.push(Trace {
            data: Arc::new(data),
            settings,
            cache_key: String::new(),
            cache: vec![],
            processed: Arc::new(vec![]),
            processing_key: vec![],
            revision: 0,
            processed_max: 1.,
            baseline_undo: None,
            history: History::default(),
        });
        a.refresh_processing();
        a.sync_history(true);
        a
    }
    fn bits(a: &mut App) -> (Vec<u8>, Vec<u64>, Vec<u64>) {
        a.refresh_processing();
        (
            serde_json::to_vec(&a.edit_state(a.selected)).unwrap(),
            a.traces[a.selected]
                .processed
                .iter()
                .map(|v| v.to_bits())
                .collect(),
            a.regions
                .iter()
                .filter(|r| r.trace == a.selected)
                .map(|r| (r.value * a.traces[a.selected].settings.integral_scale).to_bits())
                .collect(),
        )
    }
    #[test]
    fn scientific_edits_undo_and_redo_exactly_without_touching_source_or_display() {
        let mut a = app();
        let original = a.traces[0].data.real.clone();
        let mut states = vec![bits(&mut a)];
        a.traces[0].settings.p0 = 13.25;
        a.traces[0].settings.p1 = -7.5;
        a.sync_history(true);
        states.push(bits(&mut a));
        a.begin_baseline();
        a.traces[0].settings.baseline = [1., 0.01, 0., 0., 0.];
        a.sync_history(true);
        assert_eq!(a.traces[0].history.undo.len(), 1);
        a.finish_baseline(false);
        a.sync_history(true);
        assert_eq!(bits(&mut a), states[1]);
        a.begin_baseline();
        a.traces[0].settings.baseline = [1., 0.01, 0., 0., 0.];
        a.finish_baseline(true);
        a.sync_history(true);
        states.push(bits(&mut a));
        a.measure_region(8., 5.);
        a.sync_history(true);
        states.push(bits(&mut a));
        a.measure_region(4., 1.);
        a.sync_history(true);
        states.push(bits(&mut a));
        a.calibrate_integral(0, 3.).unwrap();
        a.sync_history(true);
        states.push(bits(&mut a));
        a.regions[1].left = 4.5;
        a.regions[1].bias = 0.125;
        a.regions[1].slope = -0.01;
        a.regions[1].baseline = true;
        a.regions[1].points = 2;
        a.sync_history(true);
        states.push(bits(&mut a));
        a.delete_integral(0);
        a.sync_history(true);
        states.push(bits(&mut a));
        a.traces[0].settings.gain = 2.;
        a.range = Some([5., 2.]);
        for state in states.iter().rev().skip(1) {
            a.undo_redo(false);
            assert_eq!(&bits(&mut a), state);
        }
        for state in states.iter().skip(1) {
            a.undo_redo(true);
            assert_eq!(&bits(&mut a), state);
        }
        assert_eq!(a.traces[0].settings.gain, 2.);
        assert_eq!(a.range, Some([5., 2.]));
        assert_eq!(a.traces[0].data.real, original);
        a.undo_redo(false);
        a.traces[0].settings.p0 = 22.;
        a.sync_history(true);
        assert!(a.traces[0].history.redo.is_empty());
    }
    #[test]
    fn coalesced_phase_gesture_and_checkpoint_restore_are_undoable() {
        let mut a = app();
        for phase in [1., 2., 3., 4.] {
            a.traces[0].settings.p0 = phase;
            a.sync_history(false);
        }
        a.sync_history(true);
        assert_eq!(a.traces[0].history.undo.len(), 1);
        a.create_checkpoint("Processed");
        let saved = bits(&mut a);
        a.traces[0].settings.p0 = 30.;
        a.measure_region(7., 2.);
        a.sync_history(true);
        let changed = bits(&mut a);
        a.restore_checkpoint(0);
        assert_eq!(bits(&mut a), saved);
        a.undo_redo(false);
        assert_eq!(bits(&mut a), changed);
        a.undo_redo(true);
        assert_eq!(bits(&mut a), saved);
        let settings = serde_json::to_vec(&a.traces[0].settings).unwrap();
        let reopened: Settings = serde_json::from_slice(&settings).unwrap();
        assert_eq!(reopened.checkpoints[0].name, "Processed");
        assert!(reopened.checkpoints[0].valid());
        assert!(reopened.checkpoints[0].state == a.edit_state(0));
    }
    #[test]
    fn history_stays_with_spectrum_when_stack_is_reordered() {
        let mut a = app();
        let mut second = app().traces.remove(0);
        Arc::make_mut(&mut second.data).path = "/synthetic/other/pdata/1".into();
        second.settings.path = second.data.path.clone();
        a.traces.push(second);
        a.sync_history(true);
        a.traces[0].settings.p0 = 12.;
        a.sync_history(true);
        a.traces[1].settings.p0 = 24.;
        a.sync_history(true);
        a.move_trace(0, 1);
        a.selected = 0;
        a.undo_redo(false);
        assert_eq!(a.traces[0].settings.p0, 0.);
        assert_eq!(a.traces[1].settings.p0, 12.);
        a.selected = 1;
        a.undo_redo(false);
        assert_eq!(a.traces[1].settings.p0, 0.);
    }
}
