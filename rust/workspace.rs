//! A single docked workspace for scientific tools.
use super::*;
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(super) enum Tool {
    Process,
    Export,
    Display,
    Phase,
    Baseline,
    Integrals,
    Peaks,
    History,
}
impl App {
    pub(super) fn select_tool(&mut self, tool: Tool) {
        if tool != Tool::Process {
            self.finish_auto_process(false);
            self.reference.picking = false;
        }
        if self.baseline_open && tool != Tool::Baseline {
            self.finish_baseline(false);
        }
        self.active_tool = tool;
        self.show_inspector = true;
    }
    pub(super) fn tool_panel(&mut self, ctx: &egui::Context) {
        if self.baseline_session.as_ref().is_some_and(|(path, _)| {
            self.traces
                .get(self.selected)
                .is_none_or(|t| t.data.path != *path)
        }) {
            self.finish_baseline(false);
        }
        if !self.show_inspector {
            return;
        }
        egui::SidePanel::right("scientific-tools")
            .default_width(285.)
            .min_width(220.)
            .max_width(500.)
            .resizable(true)
            .show(ctx, |ui| {
                ui.horizontal(|ui| {
                    ui.heading("Tools");
                    if ui.small_button("Hide").clicked() {
                        self.finish_auto_process(false);
                        self.reference.picking = false;
                        if self.baseline_open {
                            self.finish_baseline(false);
                        }
                        self.show_inspector = false;
                    }
                });
                ui.horizontal_wrapped(|ui| {
                    for (tool, label) in [
                        (Tool::Process, "Process"),
                        (Tool::Export, "Export"),
                        (Tool::Display, "Display"),
                        (Tool::Phase, "Phase"),
                        (Tool::Baseline, "Baseline"),
                        (Tool::Integrals, "Integrals"),
                        (Tool::Peaks, "Peaks"),
                        (Tool::History, "History"),
                    ] {
                        if !self.advanced
                            && matches!(
                                tool,
                                Tool::Display | Tool::Phase | Tool::Baseline | Tool::Peaks
                            )
                        {
                            continue;
                        }
                        if ui
                            .selectable_label(self.active_tool == tool, label)
                            .clicked()
                        {
                            if tool == Tool::Baseline {
                                self.begin_baseline();
                            } else {
                                self.select_tool(tool);
                            }
                        }
                    }
                });
                ui.separator();
                ui.spacing_mut().slider_width = (ui.available_width() - 110.).clamp(50., 150.);
                if let Some(t) = self.traces.get(self.selected) {
                    ui.label(data::dataset_name(&t.data.path));
                }
                egui::ScrollArea::vertical()
                    .id_salt(("tool-scroll", self.active_tool as u8))
                    .auto_shrink([false, false])
                    .show(ui, |ui| match self.active_tool {
                        Tool::Process => self.process_controls(ui, ctx),
                        Tool::Export => {
                            self.export_controls(ui, ctx);
                        }
                        Tool::Display => self.display_controls(ui, ctx),
                        Tool::Phase => self.phase_controls(ui),
                        Tool::Baseline => self.baseline_editor(ui),
                        Tool::Integrals => self.integral_controls(ui),
                        Tool::Peaks => self.peak_controls(ui),
                        Tool::History => self.history_controls(ui),
                    });
            });
    }
    fn display_controls(&mut self, ui: &mut egui::Ui, ctx: &egui::Context) {
        ui.horizontal(|ui| {
            ui.selectable_value(&mut self.stacked, false, "Overlay");
            ui.selectable_value(&mut self.stacked, true, "Stacked");
        });
        ui.checkbox(&mut self.normalize, "Normalize spectra");
        if let Some(t) = self.traces.get_mut(self.selected) {
            ui.label(&t.data.title);
            ui.label(format!("{} samples", t.data.real.len()));
            ui.add(
                egui::Slider::new(&mut t.settings.gain, 0.001..=100.)
                    .logarithmic(true)
                    .text("Gain"),
            );
            ui.add(egui::Slider::new(&mut t.settings.offset, -2. ..=2.).text("Offset"));
        }
        ui.collapsing("Comparison files", |ui| {
            ui.text_edit_singleline(&mut self.session_path);
            ui.horizontal(|ui| {
                if ui.button("Save new").clicked() {
                    self.status = self.save_session().unwrap_or_else(|e| e);
                }
                if ui.button("Load").clicked() {
                    self.status = self.restore(ctx.clone()).unwrap_or_else(|e| e);
                }
            });
        });
    }
    fn peak_controls(&mut self, ui: &mut egui::Ui) {
        ui.add(
            egui::Slider::new(&mut self.peak_threshold, 0.001..=1.)
                .logarithmic(true)
                .text("Threshold"),
        );
        if ui.button("Pick selected spectrum").clicked()
            && let Some(t) = self.traces.get_mut(self.selected)
        {
            self.peaks = t.pick_peaks(self.peak_threshold);
        }
        ui.label(
            "Positive local maxima, up to 128 strongest. Threshold relative to source maximum.",
        );
        for (ppm, value) in &self.peaks {
            ui.monospace(format!("{ppm:.5} ppm   {value:.3}"));
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn all_tools_use_one_docked_panel_at_small_and_large_window_sizes() {
        for width in [700., 1200.] {
            for theme in [
                appearance::Theme::GruvboxDark,
                appearance::Theme::Dark,
                appearance::Theme::Light,
            ] {
                for tool in [
                    Tool::Process,
                    Tool::Export,
                    Tool::Display,
                    Tool::Phase,
                    Tool::Baseline,
                    Tool::Integrals,
                    Tool::Peaks,
                    Tool::History,
                ] {
                    let mut app = auto_process::tests::app();
                    app.theme = theme;
                    app.select_tool(tool);
                    if tool == Tool::Baseline {
                        app.begin_baseline();
                    }
                    let ctx = egui::Context::default();
                    theme.apply(&ctx);
                    let mut remaining = width;
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
                            app.tool_panel(ctx);
                            remaining = ctx.available_rect().right();
                        },
                    );
                    assert!(
                        remaining < width - 200.,
                        "{tool:?} must be docked in the main layout"
                    );
                    assert!(!out.shapes.is_empty());
                }
            }
        }
    }
}
