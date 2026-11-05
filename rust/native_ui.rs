use super::*;
impl Trace {
    pub(super) fn refresh(&mut self) -> Result<(), String> {
        let mut key = vec![self.settings.p0.to_bits(), self.settings.p1.to_bits()];
        key.extend(self.settings.baseline.map(f64::to_bits));
        if key == self.processing_key {
            return Ok(());
        }
        let phased = processing::phase(
            &self.data.real,
            &self.data.imag,
            self.settings.p0,
            self.settings.p1,
            self.data.ft_size,
        );
        let values = processing::polynomial(&phased, self.settings.baseline, 0, phased.len())?;
        self.processed_max = values.iter().fold(f64::MIN_POSITIVE, |m, v| m.max(v.abs()));
        self.processed = Arc::new(values);
        self.processing_key = key;
        self.revision += 1;
        Ok(())
    }
    pub(super) fn pick_peaks(&mut self, threshold: f64) -> Vec<(f64, f64)> {
        if self.refresh().is_err() {
            return vec![];
        }
        let mut peaks = Vec::new();
        for i in 1..self.processed.len().saturating_sub(1) {
            let v = self.processed[i];
            if v > threshold * self.processed_max
                && v > self.processed[i - 1]
                && v >= self.processed[i + 1]
            {
                peaks.push((self.data.ppm(i), v));
            }
        }
        peaks.sort_by(|a, b| b.1.total_cmp(&a.1));
        peaks.truncate(128);
        peaks.sort_by(|a, b| b.0.total_cmp(&a.0));
        peaks
    }
}
impl App {
    pub(super) fn refresh_processing(&mut self) {
        for t in &mut self.traces {
            if let Err(e) = t.refresh() {
                self.status = e;
            }
        }
        for r in &mut self.regions {
            if let Some(t) = self.traces.get(r.trace) {
                let key = vec![
                    t.revision,
                    r.left.to_bits(),
                    r.right.to_bits(),
                    r.bias.to_bits(),
                    r.slope.to_bits(),
                    r.baseline as u64,
                    r.points as u64,
                ];
                if r.calculation_key == key {
                    continue;
                }
                match processing::region(
                    &t.processed,
                    [t.data.left, t.data.right],
                    [r.left, r.right],
                    r.bias,
                    r.slope,
                    r.baseline,
                    r.points,
                ) {
                    Ok(a) => {
                        if ![a.value(), a.bias, a.slope].iter().all(|v| v.is_finite()) {
                            r.error = Some("Integral overflow".into());
                            r.curve.clear();
                            r.value = 0.;
                            continue;
                        }
                        r.value = a.value();
                        r.curve = a.curve;
                        r.error = None;
                    }
                    Err(e) => {
                        r.error = Some(e.clone());
                        r.curve.clear();
                        r.value = 0.;
                        self.status = e;
                    }
                }
                r.calculation_key = key;
            }
        }
    }
    pub(super) fn measure_region(&mut self, a: f64, b: f64) {
        self.refresh_processing();
        let first_value = self
            .regions
            .iter()
            .find(|r| r.trace == self.selected)
            .map(|r| r.value);
        let Some(t) = self.traces.get_mut(self.selected) else {
            return;
        };
        if let Err(e) = t.refresh() {
            self.status = e;
            return;
        }
        match processing::region(
            &t.processed,
            [t.data.left, t.data.right],
            [a, b],
            self.region_bias,
            self.region_slope,
            self.region_baseline,
            self.region_points,
        ) {
            Ok(v) => {
                if !v.value().is_finite() {
                    self.status = "Integral overflow".into();
                    return;
                }
                if first_value.is_none() {
                    t.settings.normalization_frozen = false;
                }
                if !t.settings.normalization_frozen {
                    t.settings.integral_scale = processing::normalization(
                        &[first_value.unwrap_or(v.value())],
                        0,
                        1.,
                        0.,
                        0,
                    );
                }
                self.regions.push(Region {
                    trace: self.selected,
                    left: a.max(b).min(t.data.left),
                    right: a.min(b).max(t.data.right),
                    bias: self.region_bias,
                    slope: self.region_slope,
                    baseline: self.region_baseline,
                    points: self.region_points,
                    value: v.value(),
                    calculation_key: vec![],
                    curve: vec![],
                    error: None,
                });
                self.status="Integral added. First region normalized to 1; corrections stay local to each region.".into();
            }
            Err(e) => self.status = e,
        }
    }
    pub(super) fn integral_controls(&mut self, ui: &mut egui::Ui) {
        let error_color = self.palette().error;
        ui.collapsing("Integrate", |ui| {
            ui.horizontal(|ui| {
                ui.label("From / to ppm");
                ui.add(egui::DragValue::new(&mut self.region_a).speed(0.01));
                ui.add(egui::DragValue::new(&mut self.region_b).speed(0.01));
            });
            ui.checkbox(
                &mut self.region_baseline,
                "Correct integral baseline from endpoints",
            );
            ui.add(
                egui::DragValue::new(&mut self.region_points)
                    .range(1..=65536)
                    .prefix("Endpoint points "),
            );
            ui.add(
                egui::DragValue::new(&mut self.region_bias)
                    .speed(1.)
                    .prefix("Bias "),
            );
            ui.add(
                egui::DragValue::new(&mut self.region_slope)
                    .speed(0.01)
                    .prefix("Slope / point "),
            );
            if ui.button("Integrate ppm range").clicked() {
                self.measure_region(self.region_a, self.region_b);
            }
            ui.separator();
            ui.add(
                egui::DragValue::new(&mut self.calibration_value)
                    .speed(0.1)
                    .prefix("Reference integral "),
            );
            let mut normalize = None;
            let mut delete = None;
            for (i, r) in self
                .regions
                .iter_mut()
                .enumerate()
                .filter(|(_, r)| r.trace == self.selected)
            {
                ui.collapsing(
                    format!("#{} {:.3} – {:.3}", i + 1, r.left, r.right),
                    |ui| {
                        ui.add(
                            egui::DragValue::new(&mut r.left)
                                .speed(0.01)
                                .prefix("From "),
                        );
                        ui.add(egui::DragValue::new(&mut r.right).speed(0.01).prefix("To "));
                        ui.checkbox(&mut r.baseline, "Endpoint baseline correction");
                        ui.add(
                            egui::DragValue::new(&mut r.points)
                                .range(1..=65536)
                                .prefix("Endpoint points "),
                        );
                        ui.add(egui::DragValue::new(&mut r.bias).speed(1.).prefix("Bias "));
                        ui.add(
                            egui::DragValue::new(&mut r.slope)
                                .speed(0.01)
                                .prefix("Slope / point "),
                        );
                        let scale = self
                            .traces
                            .get(r.trace)
                            .map(|t| t.settings.integral_scale)
                            .unwrap_or(1.);
                        ui.label(format!(
                            "Raw {:.8} · normalized {:.8}",
                            r.value,
                            r.value * scale
                        ));
                        if let Some(e) = &r.error {
                            ui.colored_label(error_color, e);
                        }
                        if ui.button("Set as reference").clicked() {
                            normalize = Some(i);
                        }
                        if ui.button("Remove region").clicked() {
                            delete = Some(i);
                        }
                    },
                );
            }
            if let Some(i) = delete {
                self.delete_integral(i);
            }
            if let Some(i) = normalize
                && let Err(e) = self.calibrate_integral(i, self.calibration_value)
            {
                self.status = e;
            }
            if ui.button("Clear selected spectrum's regions").clicked() {
                self.regions.retain(|r| r.trace != self.selected);
                self.integral_edit = None;
                self.selected_integral = None;
                self.hovered_integral = None;
                if let Some(t) = self.traces.get_mut(self.selected) {
                    t.settings.normalization_frozen = false;
                }
            }
        });
    }
    pub(super) fn integral_table(&mut self, ui: &mut egui::Ui) {
        ui.heading("INTEGRALS");
        egui::ScrollArea::both().show(ui, |ui| {
            egui::Grid::new("integral-table")
                .striped(true)
                .show(ui, |ui| {
                    for h in [
                        "Spectrum",
                        "From / ppm",
                        "To / ppm",
                        "Raw integral",
                        "Normalized",
                    ] {
                        ui.strong(h);
                    }
                    ui.end_row();
                    for r in &self.regions {
                        let scale = self
                            .traces
                            .get(r.trace)
                            .map(|t| t.settings.integral_scale)
                            .unwrap_or(1.);
                        ui.label(format!("{}", r.trace + 1));
                        ui.label(format!("{:.5}", r.left));
                        ui.label(format!("{:.5}", r.right));
                        ui.label(format!("{:.8}", r.value));
                        ui.label(if let Some(e) = &r.error {
                            e.clone()
                        } else {
                            format!("{:.8}", r.value * scale)
                        });
                        ui.end_row();
                    }
                });
        });
    }
    pub fn begin_baseline(&mut self) {
        if self.baseline_open {
            return;
        }
        if let Some(t) = self.traces.get(self.selected) {
            self.baseline_session = Some((t.data.path.clone(), t.settings.baseline));
            self.baseline_open = true;
        } else {
            self.status = "Open and select a spectrum first".into();
        }
    }
    pub(super) fn baseline_controls(&mut self, ctx: &egui::Context) {
        let error_color = self.palette().error;
        if !self.baseline_open {
            return;
        }
        let Some((path, _before)) = self.baseline_session.clone() else {
            return;
        };
        let Some(t) = self.traces.iter_mut().find(|t| t.data.path == path) else {
            self.baseline_open = false;
            self.baseline_session = None;
            return;
        };
        let mut open = true;
        let mut action = 0;
        egui::Window::new("Manual baseline correction").open(&mut open).default_width(450.).show(ctx,|ui|{
            ui.label(data::dataset_name(&t.data.path));
            ui.label("Polynomial baseline in sample coordinates: a0 + a1·i + a2·i² + a3·i³ + a4·i⁴");
            ui.label("Live preview over the full spectrum. Source files are unchanged.");
            for i in 0..5{let speed=t.data.max_amplitude.max(1.)*0.001/(t.data.real.len()as f64).powi(i as i32);ui.horizontal(|ui|{ui.label(format!("a{i}"));ui.add(egui::DragValue::new(&mut t.settings.baseline[i]).speed(speed).custom_formatter(|v,_|format!("{v:.10e}")));});}
            ui.separator();
            ui.add(egui::DragValue::new(&mut self.baseline_points).range(1..=t.data.real.len().div_ceil(2)).prefix("Points per endpoint "));
            if ui.button("Estimate linear coefficients from endpoints").on_hover_text("Starting estimate for the manual polynomial; not the automatic TopSpin abs command").clicked(){
                let samples=processing::phase(&t.data.real,&t.data.imag,t.settings.p0,t.settings.p1,t.data.ft_size);let n=samples.len();let points=self.baseline_points.max(1).min(n.div_ceil(2));let mut first=0.;let mut last=0.;for i in 0..points{first+=samples[i];last+=samples[n-1-i];}first/=points as f64;last/=points as f64;let slope=if n>points{(last-first)/(n-points)as f64}else{0.};t.settings.baseline=[first-slope*(points-1)as f64/2.,slope,0.,0.,0.];
            }
            if ui.button("Reset coefficients").clicked(){t.settings.baseline=[0.;5];}
            if let Some(previous)=t.baseline_undo&&ui.button("Undo last applied correction").clicked(){t.settings.baseline=previous;}
            let valid=t.refresh();if let Err(e)=&valid{ui.colored_label(error_color,e);}
            ui.horizontal(|ui|{if ui.add_enabled(valid.is_ok(),egui::Button::new("Apply")).clicked(){action=1;}
            if ui.button("Cancel").clicked(){action=2;}});
        });
        if action == 1 {
            self.finish_baseline(true);
        } else if action == 2 || !open {
            self.finish_baseline(false);
        }
    }
    pub(super) fn finish_baseline(&mut self, apply: bool) {
        if let Some((path, before)) = self.baseline_session.take()
            && let Some(t) = self.traces.iter_mut().find(|t| t.data.path == path)
        {
            if apply {
                t.baseline_undo = Some(before);
            } else {
                t.settings.baseline = before;
            }
        }
        self.baseline_open = false;
        self.status = if apply {
            "Manual polynomial baseline applied. Edits autosave to the working project."
        } else {
            "Baseline preview cancelled"
        }
        .into();
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn app() -> App {
        let mut app = App::state(Instant::now(), false);
        let data = Spectrum {
            nc: 0,
            phc0: 0.,
            phc1: 0.,
            path: PathBuf::from("/synthetic/pdata/1"),
            title: String::new(),
            real: vec![10., 11., 15., 13., 10.],
            imag: vec![0.; 5],
            max_amplitude: 15.,
            left: 5.,
            right: 0.,
            ft_size: 5,
            integral_scale: 1.,
            saved_regions: vec![],
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
        };
        app.traces.push(Trace {
            data: Arc::new(data),
            settings,
            cache_key: String::new(),
            cache: vec![],
            processed: Arc::new(vec![]),
            processing_key: vec![],
            revision: 0,
            processed_max: 1.,
            baseline_undo: None,
        });
        app.refresh_processing();
        app
    }
    #[test]
    fn baseline_preview_cancel_apply_and_undo_preserve_source() {
        let mut a = app();
        let source = a.traces[0].data.real.clone();
        a.measure_region(5., 1.);
        let before = a.regions[0].value;
        a.begin_baseline();
        a.traces[0].settings.baseline = [10., 0., 0., 0., 0.];
        a.refresh_processing();
        assert_ne!(a.regions[0].value, before);
        assert_eq!(*a.traces[0].processed, vec![0., 1., 5., 3., 0.]);
        a.finish_baseline(false);
        a.refresh_processing();
        assert_eq!(a.regions[0].value.to_bits(), before.to_bits());
        a.begin_baseline();
        a.traces[0].settings.baseline = [10., 0., 0., 0., 0.];
        a.finish_baseline(true);
        a.refresh_processing();
        assert_eq!(a.regions[0].value, 9.);
        assert_eq!(a.traces[0].baseline_undo, Some([0.; 5]));
        assert_eq!(a.traces[0].data.real, source);
    }
    #[test]
    fn session_roundtrip_keeps_corrected_values_and_normalization_bits() {
        let mut a = app();
        a.traces[0].settings.baseline = [
            1.2345678901234567,
            -0.00000000000000003456789012345678,
            0.,
            0.,
            0.,
        ];
        a.measure_region(5., 1.);
        a.refresh_processing();
        let trace = a.traces[0].settings.clone();
        let s = Session {
            version: 2,
            traces: vec![trace.clone()],
            stacked: false,
            normalize: true,
            range: None,
            regions: vec![],
            integration_regions: a.regions.clone(),
        };
        let encoded = serde_json::to_vec(&s).unwrap();
        let decoded: Session = serde_json::from_slice(&encoded).unwrap();
        assert!(decoded.integration_regions[0].curve.is_empty());
        for (x, y) in trace.baseline.iter().zip(decoded.traces[0].baseline) {
            assert_eq!(x.to_bits(), y.to_bits());
        }
        let mut b = app();
        b.traces[0].settings = decoded.traces[0].clone();
        b.regions = decoded.integration_regions;
        b.refresh_processing();
        assert_eq!(a.regions[0].value.to_bits(), b.regions[0].value.to_bits());
        assert_eq!(
            a.traces[0].settings.integral_scale.to_bits(),
            b.traces[0].settings.integral_scale.to_bits()
        );
        assert_eq!(
            a.traces[0]
                .processed
                .iter()
                .map(|v| v.to_bits())
                .collect::<Vec<_>>(),
            b.traces[0]
                .processed
                .iter()
                .map(|v| v.to_bits())
                .collect::<Vec<_>>()
        );
    }
    #[test]
    fn invalid_baseline_preview_cannot_replace_processed_samples() {
        let mut a = app();
        let before = a.traces[0].processed.clone();
        a.traces[0].settings.baseline = [f64::MAX; 5];
        assert!(a.traces[0].refresh().is_err());
        assert_eq!(*before, *a.traces[0].processed);
    }
    #[test]
    fn corrected_export_and_session_reload_use_production_pipeline() {
        use std::fs;
        let root = std::env::temp_dir().join(format!("nmr-native-workflow-{}", std::process::id()));
        let pdata = root.join("sample/10/pdata/1");
        fs::create_dir_all(&pdata).unwrap();
        fs::write(pdata.join("procs"),"##$SI= 5\n##$BYTORDP= 0\n##$NC_proc= 0\n##$SF= 1\n##$SW_p= 5\n##$OFFSET= 5\n##$INTSCL= 1\n").unwrap();
        let bytes: Vec<u8> = [10i32, 11, 15, 13, 10]
            .into_iter()
            .flat_map(i32::to_le_bytes)
            .collect();
        fs::write(pdata.join("1r"), &bytes).unwrap();
        let mut a = app();
        a.traces[0].data = Arc::new(data::load(&pdata).unwrap());
        a.traces[0].settings.path = pdata.clone();
        a.traces[0].settings.baseline = [10., 0., 0., 0., 0.];
        a.measure_region(5., 1.);
        a.refresh_processing();
        let expected = a.traces[0].processed.clone();
        let integral = a.regions[0].value;
        let scale = a.traces[0].settings.integral_scale;
        a.session_path = root.join("comparison.json").display().to_string();
        a.save_session().unwrap();
        assert!(a.save_session().is_err());
        a.export_path = root.join("csv-export").display().to_string();
        a.export(egui::Context::default());
        let message =
            a.rx.recv_timeout(std::time::Duration::from_secs(3))
                .unwrap();
        assert!(matches!(message, Message::Exported(Ok(_))));
        let csv = fs::read_to_string(root.join("csv-export/spectrum.csv")).unwrap();
        for (line, expected) in csv.lines().skip(1).zip(expected.iter()) {
            let got = line.split(',').nth(4).unwrap().parse::<f64>().unwrap();
            assert_eq!(got.to_bits(), expected.to_bits());
        }
        let mut b = App::state(Instant::now(), false);
        b.session_path = a.session_path.clone();
        b.restore(egui::Context::default()).unwrap();
        let message =
            b.rx.recv_timeout(std::time::Duration::from_secs(3))
                .unwrap();
        b.handle_message(message, &egui::Context::default());
        b.refresh_processing();
        assert_eq!(b.regions[0].value.to_bits(), integral.to_bits());
        assert_eq!(
            b.traces[0].settings.integral_scale.to_bits(),
            scale.to_bits()
        );
        assert_eq!(
            b.traces[0]
                .processed
                .iter()
                .map(|v| v.to_bits())
                .collect::<Vec<_>>(),
            expected.iter().map(|v| v.to_bits()).collect::<Vec<_>>()
        );
        let invalid = Session {
            version: 2,
            traces: vec![],
            stacked: true,
            normalize: false,
            range: None,
            regions: vec![],
            integration_regions: b.regions.clone(),
        };
        let invalid_path = root.join("invalid.json");
        fs::write(&invalid_path, serde_json::to_vec(&invalid).unwrap()).unwrap();
        b.session_path = invalid_path.display().to_string();
        assert!(b.restore(egui::Context::default()).is_err());
        assert_eq!(b.traces.len(), 1);
        assert!(!b.stacked);
        assert_eq!(fs::read(pdata.join("1r")).unwrap(), bytes);
    }
    #[test]
    fn native_baseline_window_renders_without_legacy_application() {
        let mut a = app();
        a.begin_baseline();
        let ctx = egui::Context::default();
        let input = egui::RawInput {
            screen_rect: Some(egui::Rect::from_min_size(
                egui::Pos2::ZERO,
                egui::vec2(1000., 700.),
            )),
            ..Default::default()
        };
        let output = ctx.run(input, |ctx| a.baseline_controls(ctx));
        assert!(!output.shapes.is_empty());
        assert!(a.baseline_open);
        assert_eq!(a.pending, 0);
    }
}
