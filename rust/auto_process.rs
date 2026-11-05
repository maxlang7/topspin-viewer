//! Background proposals with explicit preview/accept/cancel. No preview is autosaved.
use super::*;
#[derive(Clone, Copy, Debug, PartialEq)]
pub(super) struct Recipe {
    p0: f64,
    p1: f64,
    baseline: [f64; 5],
}
impl Recipe {
    fn read(s: &Settings) -> Self {
        Self {
            p0: s.p0,
            p1: s.p1,
            baseline: s.baseline,
        }
    }
    fn write(self, s: &mut Settings) {
        s.p0 = self.p0;
        s.p1 = self.p1;
        s.baseline = self.baseline;
    }
}
#[derive(Clone, Copy)]
pub(super) enum Kind {
    Phase,
    Baseline,
    Both,
}
pub(super) struct Preview {
    path: PathBuf,
    pub before: Recipe,
    after: Recipe,
    warnings: Vec<String>,
    showing_after: bool,
}
pub(super) struct Job {
    path: PathBuf,
    before: Recipe,
    worker: std::thread::JoinHandle<Result<(Recipe, Vec<String>), String>>,
}
impl App {
    pub(super) fn has_processing_preview(&self) -> bool {
        self.baseline_open || self.auto_preview.is_some()
    }
    pub(super) fn preview_recipe(&self, path: &std::path::Path) -> Option<(f64, f64, [f64; 5])> {
        self.auto_preview
            .as_ref()
            .filter(|p| p.path == path)
            .map(|p| (p.before.p0, p.before.p1, p.before.baseline))
    }
    pub(super) fn start_auto_process(&mut self, kind: Kind, ctx: &egui::Context) {
        if self.auto_job.is_some() || self.has_processing_preview() {
            self.status = "Finish the current processing operation first".into();
            return;
        }
        let Some(t) = self.traces.get(self.selected) else {
            self.status = "Open a spectrum first".into();
            return;
        };
        let before = Recipe::read(&t.settings);
        let path = t.data.path.clone();
        let data = t.data.clone();
        let ctx = ctx.clone();
        self.sync_history(true);
        let worker = std::thread::spawn(move || {
            let result = (|| {
                let mut after = before;
                let mut warnings = vec![];
                if matches!(kind, Kind::Phase | Kind::Both) {
                    let (p, w) = auto_algorithms::phase(
                        &data.real,
                        &data.imag,
                        data.ft_size,
                        [before.p0, before.p1],
                    )?;
                    after.p0 = p[0];
                    after.p1 = p[1];
                    warnings.extend(w);
                }
                if matches!(kind, Kind::Baseline | Kind::Both) {
                    let samples =
                        processing::phase(&data.real, &data.imag, after.p0, after.p1, data.ft_size);
                    let (b, w) = auto_algorithms::baseline(&samples)?;
                    after.baseline = b;
                    warnings.extend(w);
                }
                let samples =
                    processing::phase(&data.real, &data.imag, after.p0, after.p1, data.ft_size);
                processing::polynomial(&samples, after.baseline, 0, samples.len())?;
                Ok((after, warnings))
            })();
            ctx.request_repaint();
            result
        });
        self.auto_job = Some(Job {
            path,
            before,
            worker,
        });
        self.status = "Calculating automatic processing proposal…".into();
    }
    pub(super) fn poll_auto_process(&mut self, ctx: &egui::Context) {
        if self.auto_preview.as_ref().is_some_and(|p| {
            self.traces
                .get(self.selected)
                .is_none_or(|t| t.data.path != p.path)
        }) {
            self.finish_auto_process(false);
        }
        if !self
            .auto_job
            .as_ref()
            .is_some_and(|j| j.worker.is_finished())
        {
            return;
        }
        let job = self.auto_job.take().unwrap();
        let Some(t) = self
            .traces
            .get_mut(self.selected)
            .filter(|t| t.data.path == job.path && Recipe::read(&t.settings) == job.before)
        else {
            self.status =
                "Automatic proposal discarded because the selected spectrum or processing changed"
                    .into();
            return;
        };
        match job.worker.join() {
            Ok(Ok((after, warnings))) => {
                after.write(&mut t.settings);
                self.auto_preview = Some(Preview {
                    path: job.path,
                    before: job.before,
                    after,
                    warnings,
                    showing_after: true,
                });
                self.active_tool = workspace::Tool::Process;
                self.show_inspector = true;
                self.status =
                    "Processing preview: compare Before/After, then Accept or Cancel".into();
            }
            Ok(Err(e)) => self.status = e,
            Err(_) => {
                self.status = "Automatic processing worker failed; current settings kept".into()
            }
        }
        ctx.request_repaint();
    }
    pub(super) fn finish_auto_process(&mut self, apply: bool) {
        if let Some(preview) = self.auto_preview.take() {
            if let Some(t) = self.traces.iter_mut().find(|t| t.data.path == preview.path) {
                (if apply { preview.after } else { preview.before }).write(&mut t.settings);
            }
            self.sync_history(true);
            self.refresh_processing();
            self.status = if apply {
                "Automatic processing accepted · autosaving"
            } else {
                "Automatic processing preview cancelled"
            }
            .into();
        }
    }
    pub(super) fn process_controls(&mut self, ui: &mut egui::Ui, ctx: &egui::Context) {
        ui.strong("Process spectrum");
        if self.auto_job.is_some() {
            ui.spinner();
            ui.label("Calculating in the background…");
            return;
        }
        if self.auto_preview.is_some() {
            ui.colored_label(
                self.palette().accent,
                "Preview only · not saved · Enter accepts, Escape cancels",
            );
            let mut apply = None;
            let error_color = self.palette().error;
            let p = self.auto_preview.as_mut().unwrap();
            let before_after = p.showing_after;
            ui.horizontal(|ui| {
                ui.selectable_value(&mut p.showing_after, false, "Before");
                ui.selectable_value(&mut p.showing_after, true, "After");
            });
            if before_after != p.showing_after
                && let Some(t) = self.traces.iter_mut().find(|t| t.data.path == p.path)
            {
                (if p.showing_after { p.after } else { p.before }).write(&mut t.settings);
            }
            ui.label(format!("Phase: {:.2}° / {:.2}°", p.after.p0, p.after.p1));
            ui.weak("Review peak shapes, both spectrum edges and integration regions. Automatic estimates can be wrong.");
            for warning in &p.warnings {
                ui.colored_label(error_color, warning);
            }
            ui.horizontal(|ui| {
                if ui.button("Accept processing").clicked() {
                    apply = Some(true);
                }
                if ui.button("Cancel").clicked() {
                    apply = Some(false);
                }
            });
            if let Some(apply) = apply {
                self.finish_auto_process(apply);
            }
            return;
        }
        let ready = !self.traces.is_empty() && !self.baseline_open;
        let phase_available = self
            .traces
            .get(self.selected)
            .is_some_and(|t| t.data.imag.len() == t.data.real.len());
        ui.add_enabled_ui(ready, |ui| {
            if ui
                .add_enabled(
                    phase_available,
                    egui::Button::new("Preview automatic phase + baseline · Ctrl+Alt+P"),
                )
                .clicked()
            {
                self.start_auto_process(Kind::Both, ctx);
            }
            ui.horizontal_wrapped(|ui| {
                if ui
                    .add_enabled(phase_available, egui::Button::new("Auto phase only"))
                    .clicked()
                {
                    self.start_auto_process(Kind::Phase, ctx);
                }
                if ui.button("Auto baseline only").clicked() {
                    self.start_auto_process(Kind::Baseline, ctx);
                }
            });
        });
        if ready && !phase_available {
            ui.label("No imaginary component: automatic baseline remains available.");
        }
        ui.weak("Independent estimates for routine positive 1D spectra. Review broad, inverted or overlapping signals manually.");
        ui.separator();
        self.reference_controls(ui);
    }
}

#[cfg(test)]
pub(super) mod tests {
    use super::*;
    pub(crate) fn app() -> App {
        let mut a = App::state(Instant::now(), false);
        let n = 256;
        let real = (0..n)
            .map(|i| {
                let x = i as f64 / n as f64;
                0.2 + 0.1 * x + 0.001 / ((x - 0.4).powi(2) + 0.001)
            })
            .collect::<Vec<_>>();
        let data = Spectrum {
            path: "/synthetic/test/10/pdata/1".into(),
            title: "test".into(),
            real,
            imag: vec![0.; n],
            nc: 0,
            phc0: 0.,
            phc1: 0.,
            ft_size: n,
            integral_scale: 1.,
            saved_regions: vec![],
            left: 10.,
            right: 0.,
            max_amplitude: 2.,
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
            processed: Arc::new(vec![]),
            processed_max: 1.,
            processing_key: vec![],
            cache_key: String::new(),
            cache: vec![],
            revision: 0,
            baseline_undo: None,
            history: history::History::default(),
        });
        a.refresh_processing();
        a.sync_history(true);
        a
    }
    fn finish_job(a: &mut App, ctx: &egui::Context) {
        let deadline = Instant::now() + std::time::Duration::from_secs(5);
        while a.auto_job.is_some() {
            a.poll_auto_process(ctx);
            assert!(Instant::now() < deadline);
            std::thread::sleep(std::time::Duration::from_millis(2));
        }
        a.refresh_processing();
    }
    #[test]
    fn preview_cancels_exactly_and_accept_is_one_undoable_edit() {
        let mut a = app();
        let ctx = egui::Context::default();
        let before = a.traces[0].processed.clone();
        let source = a.traces[0].data.real.clone();
        a.start_auto_process(Kind::Baseline, &ctx);
        finish_job(&mut a, &ctx);
        assert!(a.auto_preview.is_some());
        assert_ne!(*before, *a.traces[0].processed);
        assert!(a.save_session().is_err());
        a.create_checkpoint("preview");
        assert!(a.traces[0].settings.checkpoints.is_empty());
        a.sync_history(true);
        a.finish_auto_process(false);
        assert_eq!(*before, *a.traces[0].processed);
        a.start_auto_process(Kind::Baseline, &ctx);
        finish_job(&mut a, &ctx);
        let after = a.traces[0].processed.clone();
        // Accept must use the candidate even if the user is viewing Before.
        a.auto_preview
            .as_ref()
            .unwrap()
            .before
            .write(&mut a.traces[0].settings);
        a.finish_auto_process(true);
        assert_eq!(*after, *a.traces[0].processed);
        a.undo_redo(false);
        assert_eq!(*before, *a.traces[0].processed);
        a.undo_redo(true);
        assert_eq!(*after, *a.traces[0].processed);
        assert_eq!(a.traces[0].data.real, source);
    }
    #[test]
    fn changed_processing_rejects_a_stale_background_proposal() {
        let mut a = app();
        let ctx = egui::Context::default();
        a.start_auto_process(Kind::Baseline, &ctx);
        a.traces[0].settings.p0 = 15.;
        finish_job(&mut a, &ctx);
        assert!(a.auto_preview.is_none());
        assert_eq!(a.traces[0].settings.p0, 15.);
        assert_eq!(a.traces[0].settings.baseline, [0.; 5]);
    }
}
