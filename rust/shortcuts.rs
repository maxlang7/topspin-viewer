//! Global shortcuts defer to text editing; Escape cancels the innermost interaction.
use super::*;
impl App {
    pub(super) fn handle_shortcuts(&mut self, ctx: &egui::Context) {
        if ctx.input(|i| i.key_pressed(egui::Key::Escape)) {
            if egui::Popup::is_any_open(ctx) {
                egui::Popup::close_all(ctx);
            } else if self.integral_edit.is_some() {
                self.integral_edit = None;
            } else if self.start.is_some() || ctx.input(|i| i.pointer.primary_down()) {
                self.start = None;
                self.integration_drag_trace = None;
                self.cancel_plot_drag = true;
            } else if ctx.wants_keyboard_input() {
                return;
            } else if self.baseline_open {
                self.finish_baseline(false);
            } else if self.show_export {
                self.show_export = false;
            } else if self.show_settings {
                self.show_settings = false;
            } else if self.show_phase {
                self.show_phase = false;
            } else {
                self.integrate = false;
                self.selected_integral = None;
            }
            ctx.input_mut(|i| {
                i.consume_key(i.modifiers, egui::Key::Escape);
            });
            return;
        }
        if ctx.wants_keyboard_input() {
            return;
        }
        let modifiers = ctx.input(|i| i.modifiers);
        if modifiers.alt || modifiers.shift {
            return;
        }
        if ctx.input_mut(|i| i.consume_key(modifiers, egui::Key::Home)) {
            if modifiers.ctrl {
                if let Some(t) = self.traces.get_mut(self.selected) {
                    t.settings.gain = 1.;
                    t.settings.offset = 0.;
                }
            } else {
                self.range = None;
            }
            return;
        }
        if !modifiers.ctrl {
            return;
        }
        let key = ctx.input_mut(|i| {
            [egui::Key::Comma, egui::Key::E, egui::Key::P, egui::Key::B]
                .into_iter()
                .find(|key| i.consume_key(modifiers, *key))
        });
        match key {
            Some(egui::Key::Comma) => self.show_settings = true,
            Some(egui::Key::E) => self.show_export = true,
            Some(egui::Key::P) => self.show_phase = true,
            Some(egui::Key::B) => self.begin_baseline(),
            _ => {}
        }
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    fn key(ctx: &egui::Context, a: &mut App, key: egui::Key, modifiers: egui::Modifiers) {
        let _ = ctx.run(
            egui::RawInput {
                modifiers,
                events: vec![egui::Event::Key {
                    key,
                    physical_key: None,
                    pressed: true,
                    repeat: false,
                    modifiers,
                }],
                ..Default::default()
            },
            |ctx| a.handle_shortcuts(ctx),
        );
    }
    #[test]
    fn shortcuts_open_controls_without_exporting_and_escape_cancels_drag() {
        let ctx = egui::Context::default();
        let mut a = App::state(Instant::now(), false);
        a.range = Some([6., 1.]);
        key(&ctx, &mut a, egui::Key::Home, egui::Modifiers::NONE);
        assert!(a.range.is_none());
        key(&ctx, &mut a, egui::Key::Comma, egui::Modifiers::CTRL);
        assert!(a.show_settings);
        key(&ctx, &mut a, egui::Key::E, egui::Modifiers::CTRL);
        assert!(a.show_export);
        assert_eq!(a.pending, 0);
        key(&ctx, &mut a, egui::Key::P, egui::Modifiers::CTRL);
        assert!(a.show_phase);
        a.start = Some(3.);
        a.integration_drag_trace = Some(0);
        key(&ctx, &mut a, egui::Key::Escape, egui::Modifiers::NONE);
        assert!(a.start.is_none() && a.integration_drag_trace.is_none());
        assert!(a.cancel_plot_drag);
        assert!(a.show_export);
        key(&ctx, &mut a, egui::Key::Escape, egui::Modifiers::NONE);
        assert!(!a.show_export);
    }
    #[test]
    fn home_and_export_do_not_override_text_entry() {
        let ctx = egui::Context::default();
        let mut a = App::state(Instant::now(), false);
        a.range = Some([6., 1.]);
        let mut text = "abc".to_string();
        let _ = ctx.run(Default::default(), |ctx| {
            egui::CentralPanel::default().show(ctx, |ui| {
                ui.text_edit_singleline(&mut text).request_focus();
            });
        });
        key(&ctx, &mut a, egui::Key::Home, egui::Modifiers::NONE);
        assert_eq!(a.range, Some([6., 1.]));
        // Keep the text widget alive for the second key, as in the real application.
        let _ = ctx.run(Default::default(), |ctx| {
            egui::CentralPanel::default().show(ctx, |ui| {
                ui.text_edit_singleline(&mut text).request_focus();
            });
        });
        key(&ctx, &mut a, egui::Key::E, egui::Modifiers::CTRL);
        assert!(!a.show_export);
    }
}
