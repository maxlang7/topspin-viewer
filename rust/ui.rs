use super::*;
impl App {
    fn simple_toolbar(&mut self, ui: &mut egui::Ui) {
        if ui
            .button("Open")
            .on_hover_text("Enter a folder in the dataset browser, or drop it onto the window")
            .clicked()
        {
            self.focus_open = true;
        }
        if ui
            .selectable_label(
                self.show_inspector && self.active_tool == workspace::Tool::Process,
                "Process",
            )
            .clicked()
        {
            self.select_tool(workspace::Tool::Process);
        }
        if ui.selectable_label(self.integrate, "Integrate").clicked() {
            self.integrate = !self.integrate;
            self.data_tab = 0;
            self.select_tool(workspace::Tool::Integrals);
        }
        if ui.button("Export").clicked() {
            self.select_tool(workspace::Tool::Export);
        }
        ui.separator();
        self.history_buttons(ui);
        if ui.button("History").clicked() {
            self.select_tool(workspace::Tool::History);
        }
        if ui.button("Settings").clicked() {
            self.show_settings = true;
        }
        if let Some(t) = self.traces.get(self.selected)
            && let Some(entry) = self.automatic_projects.get(&t.data.path)
        {
            ui.label(entry.caption())
                .on_hover_text(entry.root.display().to_string());
        }
        if self.pending > 0 {
            ui.spinner();
        }
    }
    pub fn topspin_chrome(&mut self, ctx: &egui::Context) {
        if self.theme == appearance::Theme::TopSpin {
            self.topspin_reference_chrome(ctx);
            return;
        }
        egui::TopBottomPanel::top("command-row")
            .frame(
                egui::Frame::new()
                    .fill(self.palette().panel)
                    .inner_margin(4.),
            )
            .show(ctx, |ui| {
                egui::ScrollArea::horizontal().show(ui, |ui| {
                    ui.horizontal(|ui| {
                        ui.spacing_mut().button_padding = egui::vec2(5., 3.);
                        crate::branding::icon(ui, 24.);
                        ui.menu_button("Appearance", |ui| self.appearance_menu(ui));
                        if ui.checkbox(&mut self.advanced, "Advanced").changed() && !self.advanced {
                            self.select_tool(workspace::Tool::Process);
                        }
                        if !self.advanced {
                            self.simple_toolbar(ui);
                            return;
                        }
                        ui.menu_button("File", |ui| {
                            self.project_controls(ui, ctx);
                            ui.menu_button("Comparison file", |ui| {
                                ui.text_edit_singleline(&mut self.session_path);
                                if ui.button("Save new").clicked() {
                                    self.status = self.save_session().unwrap_or_else(|e| e);
                                }
                                if ui.button("Load").clicked() {
                                    self.status = self.restore(ctx.clone()).unwrap_or_else(|e| e);
                                }
                            });
                        });
                        ui.menu_button("View", |ui| {
                            if ui.button("Display").clicked() {
                                self.select_tool(workspace::Tool::Display);
                            }
                            ui.selectable_value(&mut self.stacked, false, "Overlay");
                            ui.selectable_value(&mut self.stacked, true, "Stacked");
                        });
                        ui.menu_button("Tools", |ui| {
                            if ui.button("History / checkpoints").clicked() {
                                self.select_tool(workspace::Tool::History);
                            }
                            if ui.button("Peak picking").clicked()
                                && let Some(t) = self.traces.get_mut(self.selected)
                            {
                                self.peaks = t.pick_peaks(self.peak_threshold);
                                self.select_tool(workspace::Tool::Peaks);
                            }
                        });
                        if ui
                            .selectable_label(
                                self.show_inspector && self.active_tool == workspace::Tool::Phase,
                                "Phase",
                            )
                            .clicked()
                        {
                            if self.show_inspector && self.active_tool == workspace::Tool::Phase {
                                self.show_inspector = false;
                            } else {
                                self.select_tool(workspace::Tool::Phase);
                            }
                        }
                        if ui
                            .button("Baseline")
                            .on_hover_text("Manual polynomial correction")
                            .clicked()
                        {
                            self.begin_baseline();
                        }
                        if ui.selectable_label(self.integrate, "∫ Integrate").clicked() {
                            self.integrate = !self.integrate;
                            self.data_tab = 0;
                            self.select_tool(workspace::Tool::Integrals);
                        }
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
                        self.history_buttons(ui);
                        if ui.button("+").on_hover_text("Zoom in").clicked() {
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
                        if ui.button("Settings").clicked() {
                            self.show_settings = true;
                        }
                        if let Some(t) = self.traces.get(self.selected)
                            && let Some(entry) = self.automatic_projects.get(&t.data.path)
                        {
                            ui.label(entry.caption())
                                .on_hover_text(entry.root.display().to_string());
                        }
                        if self.pending > 0 {
                            ui.spinner();
                            ui.label(format!("{} pending", self.pending));
                        }
                    });
                });
            });
        egui::TopBottomPanel::bottom("command-status").show(ctx, |ui| {
            ui.horizontal(|ui| {
                if self.advanced {
                    ui.label("Command");
                    let r = ui.add(
                        egui::TextEdit::singleline(&mut self.command)
                            .desired_width(205.)
                            .hint_text(".all · .ph · int · .multi · .csv · .basl"),
                    );
                    if r.lost_focus() && ui.input(|i| i.key_pressed(egui::Key::Enter)) {
                        match self.command.trim() {
                            ".all" => self.range = None,
                            ".ph" => {
                                self.select_tool(workspace::Tool::Phase);
                            }
                            "int" => {
                                self.integrate = true;
                                self.data_tab = 0;
                                self.select_tool(workspace::Tool::Integrals);
                            }
                            ".multi" => self.stacked = !self.stacked,
                            ".csv" => self.export(ctx.clone()),
                            ".basl" => self.begin_baseline(),
                            _ => {
                                self.status = "Commands: .all, .ph, int, .multi, .csv, .basl".into()
                            }
                        };
                        self.command.clear();
                        r.request_focus();
                    }
                }
                ui.add(egui::Label::new(&self.status).truncate())
                    .on_hover_text(&self.status);
            });
        });
    }
    pub(super) fn phase_controls(&mut self, ui: &mut egui::Ui) {
        if let Some(t) = self.traces.get_mut(self.selected) {
            ui.horizontal_wrapped(|ui| {
                ui.weak(format!(
                    "Saved phase: PH0 {:.3}° · PH1 {:.3}°",
                    t.data.phc0, t.data.phc1
                ));
            });
            if t.data.imag.is_empty() {
                ui.label("No imaginary component");
            } else {
                ui.horizontal_wrapped(|ui| {
                    ui.add(
                        egui::Slider::new(&mut t.settings.p0, -180. ..=180.).text("PH0 adjustment"),
                    );
                    ui.add(
                        egui::Slider::new(&mut t.settings.p1, -720. ..=720.).text("PH1 adjustment"),
                    );
                    if ui.button("Reset adjustments").clicked() {
                        t.settings.p0 = 0.;
                        t.settings.p1 = 0.;
                    }
                });
            }
        } else {
            ui.label("Open a spectrum to adjust its phase");
        }
        ui.weak("Edits autosave. Export Bruker to share with TopSpin.");
    }
    pub fn spectrum_tabs(&mut self, ctx: &egui::Context) {
        if self.theme == appearance::Theme::TopSpin {
            self.topspin_spectrum_tabs(ctx);
            return;
        }
        egui::TopBottomPanel::top("open-spectra").show(ctx, |ui| {
            let mut close = None;
            egui::ScrollArea::horizontal().show(ui, |ui| {
                ui.horizontal(|ui| {
                    let colors = self.colors();
                    for (i, t) in self.traces.iter_mut().enumerate() {
                        ui.checkbox(&mut t.settings.visible, "");
                        let name = data::dataset_name(&t.data.path);
                        if ui
                            .selectable_label(
                                self.selected == i,
                                egui::RichText::new(format!("{}  {}", i + 1, name))
                                    .color(colors[i % 6]),
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
                self.close_trace(i);
            }
        });
        egui::TopBottomPanel::bottom("data-tabs").show(ctx, |ui| {
            ui.horizontal(|ui| {
                ui.selectable_value(&mut self.data_tab, 0, "SPECTRUM");
                ui.selectable_value(&mut self.data_tab, 1, "INTEGRALS");
                ui.separator();
                ui.label("ppm");
            });
        });
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn compact_chrome_and_editors_render_in_all_themes_and_small_windows() {
        for width in [700., 1200.] {
            for theme in [
                appearance::Theme::GruvboxDark,
                appearance::Theme::Dark,
                appearance::Theme::Light,
            ] {
                let ctx = egui::Context::default();
                theme.apply(&ctx);
                let mut app = App::state(Instant::now(), false);
                app.theme = theme;
                app.show_settings = true;
                app.select_tool(workspace::Tool::Phase);
                let mut top = 0.;
                let out = ctx.run(
                    egui::RawInput {
                        screen_rect: Some(egui::Rect::from_min_size(
                            Pos2::ZERO,
                            egui::vec2(width, 600.),
                        )),
                        ..Default::default()
                    },
                    |ctx| {
                        app.topspin_chrome(ctx);
                        top = ctx.available_rect().top();
                        app.spectrum_tabs(ctx);
                        app.settings_window(ctx);
                        app.tool_panel(ctx);
                    },
                );
                assert!(
                    top <= 60.,
                    "Compact toolbar used {top} points at width {width}"
                );
                assert!(!out.shapes.is_empty());
                assert_eq!(
                    ctx.style().visuals.dark_mode,
                    theme != appearance::Theme::Light
                );
                assert!(
                    app.show_settings
                        && app.show_inspector
                        && app.active_tool == workspace::Tool::Phase
                );
            }
        }
    }
}
