use super::*;
impl App {
    fn legacy(&mut self) {
        let mut c = std::process::Command::new("bash");
        c.arg(concat!(env!("CARGO_MANIFEST_DIR"), "/run-legacy.sh"));
        if let Some(t) = self.traces.get(self.selected) {
            c.arg(&t.data.path);
        }
        self.status = match c.spawn() {
            Ok(_) => "Opening advanced processing in the retained viewer".into(),
            Err(e) => format!("Could not launch retained viewer: {e}"),
        };
    }
    pub fn topspin_chrome(&mut self, ctx: &egui::Context) {
        egui::TopBottomPanel::top("navigation")
            .frame(
                egui::Frame::new()
                    .fill(Color32::from_rgb(50, 48, 47))
                    .inner_margin(8.),
            )
            .show(ctx, |ui| {
                ui.horizontal(|ui| {
                    ui.menu_button("☰", |ui| {
                        if ui.button("Export selected CSV").clicked() {
                            self.export(ctx.clone());
                            ui.close();
                        }
                        if ui.button("Advanced processing").clicked() {
                            self.legacy();
                            ui.close();
                        }
                    });
                    for nav in ["Process", "Analyze", "Manage"] {
                        ui.selectable_value(&mut self.nav, nav.into(), nav);
                    }
                    ui.separator();
                    ui.label(egui::RichText::new("NMR Viewer").strong());
                    if self.pending > 0 {
                        ui.spinner();
                        ui.label(format!("{} pending", self.pending));
                    }
                });
            });
        egui::TopBottomPanel::top("processing-ribbon")
            .frame(
                egui::Frame::new()
                    .fill(Color32::from_rgb(60, 56, 54))
                    .inner_margin(10.),
            )
            .show(ctx, |ui| {
                ui.horizontal_wrapped(|ui| {
                    ui.spacing_mut().button_padding = egui::vec2(12., 9.);
                    match self.nav.as_str() {
                        "Analyze" => {
                            if ui
                                .selectable_label(self.integrate, "∫  Integrate")
                                .clicked()
                            {
                                self.integrate = !self.integrate;
                                self.data_tab = 0;
                            }
                            ui.separator();
                            if ui.button("Peak picking").clicked()
                                && let Some(t) = self.traces.get(self.selected)
                            {
                                self.peaks =
                                    t.data
                                        .peaks(self.peak_threshold, t.settings.p0, t.settings.p1);
                            }
                            ui.separator();
                            if ui
                                .button("Calib. Axis ↗")
                                .on_hover_text(
                                    "Open validated axis calibration in the retained viewer",
                                )
                                .clicked()
                            {
                                self.legacy();
                            }
                        }
                        "Manage" => {
                            if ui.button("Save comparison").clicked() {
                                self.status = self.save_session().unwrap_or_else(|e| e);
                            }
                            if ui.button("Load comparison").clicked() {
                                self.status = self.restore(ctx.clone()).unwrap_or_else(|e| e);
                            }
                            ui.separator();
                            if ui.button("Export CSV").clicked() {
                                self.export(ctx.clone());
                            }
                        }
                        _ => {
                            if ui
                                .button("∿  Proc. Spectrum ↗")
                                .on_hover_text("Raw processing in the retained viewer")
                                .clicked()
                            {
                                self.legacy();
                            }
                            ui.separator();
                            ui.menu_button("Adjust Phase ▾", |ui| {
                                if let Some(t) = self.traces.get_mut(self.selected) {
                                    if t.data.imag.is_empty() {
                                        ui.label("No imaginary component");
                                    } else {
                                        ui.add(
                                            egui::Slider::new(&mut t.settings.p0, -180. ..=180.)
                                                .text("PH0"),
                                        );
                                        ui.add(
                                            egui::Slider::new(&mut t.settings.p1, -720. ..=720.)
                                                .text("PH1"),
                                        );
                                        if ui.button("Reset").clicked() {
                                            t.settings.p0 = 0.;
                                            t.settings.p1 = 0.;
                                        }
                                    }
                                }
                                ui.label("Preview only");
                            });
                            ui.separator();
                            if ui
                                .button("Baseline ↗")
                                .on_hover_text("Baseline corrections in the retained viewer")
                                .clicked()
                            {
                                self.legacy();
                            }
                            ui.separator();
                            if ui.button("Calib. Axis ↗").clicked() {
                                self.legacy();
                            }
                            ui.separator();
                            if ui.button("Advanced ↗").clicked() {
                                self.legacy();
                            }
                        }
                    }
                });
            });
        egui::TopBottomPanel::top("view-tools").show(ctx, |ui| {
            ui.horizontal_wrapped(|ui| {
                if ui
                    .button("×2")
                    .on_hover_text("Double selected spectrum intensity")
                    .clicked()
                    && let Some(t) = self.traces.get_mut(self.selected)
                {
                    t.settings.gain = (t.settings.gain * 2.).min(100.);
                }
                if ui
                    .button("÷2")
                    .on_hover_text("Halve selected spectrum intensity")
                    .clicked()
                    && let Some(t) = self.traces.get_mut(self.selected)
                {
                    t.settings.gain = (t.settings.gain / 2.).max(0.001);
                }
                ui.separator();
                if ui.button("＋").on_hover_text("Zoom in").clicked() {
                    let r = self.range.unwrap_or_else(|| self.full_range());
                    let m = (r[0] + r[1]) / 2.;
                    self.range = Some([m + (r[0] - m) * 0.5, m + (r[1] - m) * 0.5]);
                }
                if ui.button("−").on_hover_text("Zoom out").clicked() {
                    let r = self.range.unwrap_or_else(|| self.full_range());
                    let m = (r[0] + r[1]) / 2.;
                    self.range = Some([m + (r[0] - m) * 2., m + (r[1] - m) * 2.]);
                }
                if ui
                    .button("↔  Full")
                    .on_hover_text("Reset ppm range")
                    .clicked()
                {
                    self.range = None;
                }
                ui.separator();
                ui.selectable_value(&mut self.integrate, false, "Navigate");
                ui.selectable_value(&mut self.integrate, true, "∫  Select region");
            });
        });
        egui::TopBottomPanel::bottom("command-status").show(ctx, |ui| {
            ui.horizontal(|ui| {
                ui.label("Command");
                let r = ui.add(
                    egui::TextEdit::singleline(&mut self.command)
                        .hint_text(".all · .ph · int · .multi · .csv · efp"),
                );
                if r.lost_focus() && ui.input(|i| i.key_pressed(egui::Key::Enter)) {
                    match self.command.trim() {
                        ".all" => self.range = None,
                        ".ph" => self.nav = "Process".into(),
                        "int" => {
                            self.nav = "Analyze".into();
                            self.integrate = true;
                        }
                        ".multi" => self.stacked = !self.stacked,
                        ".csv" => self.export(ctx.clone()),
                        "efp" | ".cal" => self.legacy(),
                        _ => {
                            self.status = "Commands: .all, .ph, int, .multi, .csv, efp, .cal".into()
                        }
                    };
                    self.command.clear();
                    r.request_focus();
                }
            });
            ui.label(&self.status);
        });
    }
    pub fn spectrum_tabs(&mut self, ctx: &egui::Context) {
        egui::TopBottomPanel::top("open-spectra").show(ctx, |ui| {
            let mut close = None;
            egui::ScrollArea::horizontal().show(ui, |ui| {
                ui.horizontal(|ui| {
                    for (i, t) in self.traces.iter_mut().enumerate() {
                        ui.checkbox(&mut t.settings.visible, "");
                        let name = t
                            .data
                            .path
                            .ancestors()
                            .nth(3)
                            .and_then(|p| p.file_name())
                            .unwrap_or_default()
                            .to_string_lossy();
                        if ui
                            .selectable_label(
                                self.selected == i,
                                egui::RichText::new(format!("{}  {}", i + 1, name))
                                    .color(COLORS[i % 6]),
                            )
                            .on_hover_text(t.data.path.display().to_string())
                            .clicked()
                        {
                            self.selected = i;
                            self.peaks.clear();
                        }
                        if ui.small_button("×").clicked() {
                            close = Some(i);
                        }
                        ui.separator();
                    }
                });
            });
            if let Some(i) = close {
                self.traces.remove(i);
                self.regions.clear();
                self.peaks.clear();
                self.selected = self.selected.min(self.traces.len().saturating_sub(1));
            }
        });
        egui::TopBottomPanel::bottom("data-tabs").show(ctx, |ui| {
            ui.horizontal(|ui| {
                ui.selectable_value(&mut self.data_tab, 0, "SPECTRUM");
                ui.selectable_value(&mut self.data_tab, 1, "INTEGRALS");
                if ui
                    .button("FID ↗")
                    .on_hover_text("Open FID in the retained viewer")
                    .clicked()
                {
                    self.legacy();
                }
                ui.separator();
                ui.label("ppm");
            });
        });
    }
}
