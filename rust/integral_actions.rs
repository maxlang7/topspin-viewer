//! Plot interactions change integral recipes and use the shared normalization arithmetic.
use super::*;
pub(super) struct IntegralEdit {
    pub index: usize,
    pub text: String,
    pub focus: bool,
    pub error: Option<String>,
}
pub(super) fn plot_scroll(
    shift: bool,
    raw: egui::Vec2,
    smooth: egui::Vec2,
    tail: &mut bool,
) -> egui::Vec2 {
    if shift {
        *tail |= raw != egui::Vec2::ZERO || smooth != egui::Vec2::ZERO;
        return egui::Vec2::ZERO;
    }
    if *tail {
        if raw != egui::Vec2::ZERO {
            *tail = false;
        } else {
            if smooth == egui::Vec2::ZERO {
                *tail = false;
            }
            return egui::Vec2::ZERO;
        }
    }
    smooth
}
impl App {
    #[allow(clippy::too_many_arguments)]
    pub(super) fn handle_plot_navigation(
        &mut self,
        ctx: &egui::Context,
        response: &egui::Response,
        rect: egui::Rect,
        range: [f64; 2],
        have_integrals: bool,
        label_blocked: bool,
        integrating: bool,
        painter: &egui::Painter,
    ) {
        let count = self
            .traces
            .iter()
            .filter(|t| t.settings.visible)
            .count()
            .max(1);
        let selected = self.selected;
        let width = range[0] - range[1];
        let ppm = |x: f32| range[0] - (x - rect.left()) as f64 / rect.width() as f64 * width;
        let plot_pointer = ctx.pointer_hover_pos().filter(|p| {
            response.rect.contains(*p) && ctx.layer_id_at(*p) == Some(response.layer_id)
        });
        if let Some(pos) = plot_pointer {
            let x = ppm(pos.x);
            painter.line_segment(
                [
                    Pos2::new(pos.x, rect.top()),
                    Pos2::new(pos.x, rect.bottom()),
                ],
                Stroke::new(1., self.palette().muted),
            );
            painter.text(
                rect.right_top(),
                egui::Align2::RIGHT_TOP,
                format!("{x:.5} ppm"),
                egui::FontId::monospace(12.),
                self.colors()[0],
            );
            let scroll = ctx.input(|i| {
                integral_actions::plot_scroll(
                    i.modifiers.shift,
                    i.raw_scroll_delta,
                    i.smooth_scroll_delta,
                    &mut self.suppress_scroll_tail,
                )
            });
            let lane_h = if self.stacked {
                rect.height() / count as f32
            } else {
                rect.height()
            };
            let lane = if self.stacked {
                ((pos.y - rect.top()) / lane_h)
                    .floor()
                    .clamp(0., count.saturating_sub(1) as f32) as usize
            } else {
                0
            };
            let hit = self.spectrum_under_pointer(rect, range, pos, have_integrals);
            let hovered = hit.and_then(|idx| self.traces.get(idx).map(|t| (idx, t)));
            let offset = hovered.map_or(0., |(_, t)| t.settings.offset);
            let integrals = have_integrals
                && (!self.stacked || hovered.is_some_and(|(idx, _)| idx == selected));
            let geometry =
                integral_plot::lane_layout(rect, self.stacked, count, lane, offset, integrals);
            let below = pos.y > geometry.baseline;
            let horizontal = scroll.x;
            if horizontal != 0. {
                let shift = horizontal as f64 / rect.width() as f64 * width;
                self.range = Some([range[0] + shift, range[1] + shift]);
            }
            if below && scroll.y != 0. {
                let r = self.range.unwrap_or(range);
                let anchor =
                    r[0] - (pos.x - rect.left()) as f64 / rect.width() as f64 * (r[0] - r[1]);
                let factor = (-scroll.y as f64 * 0.003).exp().clamp(0.5, 2.);
                let zoomed = [
                    anchor + (r[0] - anchor) * factor,
                    anchor + (r[1] - anchor) * factor,
                ];
                if zoomed[0] - zoomed[1] > 1e-8 && zoomed[0] - zoomed[1] < 1e6 {
                    self.range = Some(zoomed);
                }
            }
            if !below
                && scroll.y != 0.
                && let Some(idx) = hit
            {
                self.zoom_trace(idx, scroll.y);
            }
        }

        if response.double_clicked() && !label_blocked {
            self.range = None;
        }
        if response.drag_started() && !label_blocked && integrating {
            self.start = ctx.input(|i| i.pointer.press_origin()).map(|p| ppm(p.x));
            self.integration_drag_trace = plot_pointer
                .and_then(|pos| self.spectrum_under_pointer(rect, range, pos, have_integrals));
            if let Some(index) = self.integration_drag_trace {
                self.selected = index;
            }
        }
        if response.dragged() && self.start.is_none() && !integrating && !label_blocked {
            let dx = ctx.input(|i| i.pointer.delta().x) as f64 / rect.width() as f64 * width;
            self.range = Some([range[0] + dx, range[1] + dx]);
        }
        if let (Some(a), Some(p)) = (self.start, ctx.pointer_latest_pos()) {
            self.paint_integration_preview(rect, range, a, ppm(p.x), painter);
        }
        if response.drag_stopped()
            && let (Some(a), Some(p), Some(index)) = (
                self.start.take(),
                ctx.pointer_latest_pos(),
                self.integration_drag_trace.take(),
            )
            && index < self.traces.len()
        {
            self.selected = index;
            if (a - ppm(p.x)).abs() > 1e-9 {
                self.measure_region(a, ppm(p.x));
            }
        }
    }
    pub(super) fn calibrate_integral(&mut self, index: usize, target: f64) -> Result<(), String> {
        self.refresh_processing();
        let r = self.regions.get(index).ok_or("Integral no longer exists")?;
        if r.error.is_some() || r.value == 0. || !r.value.is_finite() {
            return Err("A nonzero valid integral is needed for calibration".into());
        }
        if !target.is_finite() || target <= 0. {
            return Err("Enter a positive finite proton count".into());
        }
        let scale = processing::normalization(&[r.value], 0, target, 0., 0);
        if !scale.is_finite() || scale == 0. {
            return Err("Normalization is outside the supported range".into());
        }
        let trace = r.trace;
        self.traces[trace].settings.integral_scale = scale;
        self.traces[trace].settings.normalization_frozen = true;
        self.selected_integral = Some(index);
        self.status = format!(
            "Integral calibrated to {target}. All integrals on this spectrum rescaled; changes autosave."
        );
        Ok(())
    }
    pub(super) fn delete_integral(&mut self, index: usize) {
        if index >= self.regions.len() {
            return;
        }
        let trace = self.regions.remove(index).trace;
        self.integral_edit = None;
        self.hovered_integral = None;
        self.selected_integral = self.selected_integral.and_then(|i| {
            if i == index {
                None
            } else {
                Some(if i > index { i - 1 } else { i })
            }
        });
        let first = self.regions.iter().find(|r| r.trace == trace);
        if let Some(t) = self.traces.get_mut(trace) {
            if let Some(r) = first {
                if !t.settings.normalization_frozen {
                    t.settings.integral_scale = processing::normalization(&[r.value], 0, 1., 0., 0);
                }
            } else {
                t.settings.normalization_frozen = false;
            }
        }
        self.status = "Integral deleted. Changes autosave.".into();
    }
    pub(super) fn handle_integral_delete_shortcut(&mut self, ctx: &egui::Context) {
        if self.integral_edit.is_some() {
            return;
        }
        if let Some((index, rect)) = self.hovered_integral
            && self
                .regions
                .get(index)
                .is_some_and(|r| r.trace == self.selected)
            && ctx.pointer_hover_pos().is_some_and(|p| rect.contains(p))
            && ctx.input(|i| {
                i.key_pressed(egui::Key::D)
                    && !i.modifiers.command
                    && !i.modifiers.ctrl
                    && !i.modifiers.alt
            })
        {
            ctx.input_mut(|i| {
                let modifiers = i.modifiers;
                i.consume_key(modifiers, egui::Key::D);
                i.events
                    .retain(|e| !matches!(e,egui::Event::Text(s) if s=="d"||s=="D"));
            });
            self.delete_integral(index);
        }
    }
    pub(super) fn integral_interactions(
        &mut self,
        ui: &mut egui::Ui,
        hits: &[(usize, egui::Rect)],
    ) -> bool {
        let ctx = ui.ctx().clone();
        self.hovered_integral = None;
        if self.integral_edit.as_ref().is_some_and(|e| {
            self.regions
                .get(e.index)
                .is_none_or(|r| r.trace != self.selected)
        }) {
            self.integral_edit = None;
        }
        let mut blocked = false;
        for &(index, rect) in hits {
            let response = ui.interact(
                rect,
                ui.id().with(("integral-number", index)),
                egui::Sense::click(),
            );
            if response.hovered() {
                self.hovered_integral = Some((index, rect));
                blocked = true;
            }
            if response.clicked() {
                self.selected_integral = Some(index);
            }
            if response.double_clicked() {
                let r = &self.regions[index];
                let value = r.value * self.traces[r.trace].settings.integral_scale;
                self.integral_edit = Some(IntegralEdit {
                    index,
                    text: value.to_string(),
                    focus: true,
                    error: None,
                });
                self.selected_integral = Some(index);
            }
            response.on_hover_text("Click to select · Hover + D to delete\nDouble-click, type a proton count and Enter to calibrate all integrals on this spectrum");
        }
        let mut commit = None;
        let mut cancel = false;
        if let Some(edit) = &mut self.integral_edit {
            if let Some((_, hit)) = hits.iter().find(|(i, _)| *i == edit.index) {
                let rect = egui::Rect::from_min_size(hit.min, egui::vec2(120., 24.));
                blocked |= ctx.pointer_hover_pos().is_some_and(|p| rect.contains(p));
                let id = ui.id().with("integral-value-editor");
                let response = ui.put(
                    rect,
                    egui::TextEdit::singleline(&mut edit.text)
                        .id(id)
                        .font(egui::TextStyle::Monospace),
                );
                if edit.focus {
                    response.request_focus();
                    if let Some(mut state) = egui::text_edit::TextEditState::load(&ctx, id) {
                        state
                            .cursor
                            .set_char_range(Some(egui::text::CCursorRange::two(
                                egui::text::CCursor::new(0),
                                egui::text::CCursor::new(edit.text.chars().count()),
                            )));
                        state.store(&ctx, id);
                    }
                    edit.focus = false;
                } else if ctx.input(|i| i.key_pressed(egui::Key::Escape)) {
                    cancel = true;
                } else if ctx.input(|i| i.key_pressed(egui::Key::Enter)) {
                    commit = Some((edit.index, edit.text.clone()));
                } else if response.lost_focus() {
                    cancel = true;
                }
                if let Some(error) = &edit.error {
                    response.on_hover_text(error);
                }
            } else {
                cancel = true;
            }
        }
        if cancel {
            self.integral_edit = None;
        }
        if let Some((index, text)) = commit {
            let result = text
                .trim()
                .parse::<f64>()
                .map_err(|_| "Enter a number, for example 3".to_string())
                .and_then(|v| self.calibrate_integral(index, v));
            match result {
                Ok(()) => self.integral_edit = None,
                Err(error) => {
                    self.status = error.clone();
                    if let Some(edit) = &mut self.integral_edit {
                        edit.error = Some(error);
                        edit.focus = true;
                    }
                }
            }
        }
        blocked
    }
    pub(super) fn integration_indicator(&self, ui: &egui::Ui, rect: egui::Rect, enabled: bool) {
        if !enabled && self.start.is_none() {
            return;
        }
        let text = if self.start.is_some() && !enabled {
            "INTEGRATION MODE · Release mouse to add region"
        } else if self.integrate {
            "INTEGRATION MODE · Drag to add · Toggle Integrate to leave"
        } else {
            "INTEGRATION MODE · Shift held · Drag to add"
        };
        let galley = ui.painter().layout(
            text.into(),
            egui::FontId::proportional(14.),
            self.palette().badge_text,
            (rect.width() - 24.).max(20.),
        );
        let badge = egui::Rect::from_center_size(
            Pos2::new(rect.center().x, rect.top() + galley.size().y / 2. + 8.),
            galley.size() + egui::vec2(20., 12.),
        );
        ui.painter().rect_filled(badge, 5., self.palette().accent);
        ui.painter().galley(
            badge.min + egui::vec2(10., 6.),
            galley,
            self.palette().badge_text,
        );
        ui.painter().text(Pos2::new(rect.left()+8.,rect.bottom()-8.),egui::Align2::LEFT_BOTTOM,"Drag a range to integrate · Shift+scroll disabled\nHover number + D deletes · Double-click number calibrates",egui::FontId::proportional(12.),self.palette().accent);
    }
    pub(super) fn paint_integration_preview(
        &self,
        rect: egui::Rect,
        range: [f64; 2],
        a: f64,
        b: f64,
        painter: &egui::Painter,
    ) {
        let Some(t) = self.integration_drag_trace.and_then(|i| self.traces.get(i)) else {
            return;
        };
        let left = a.max(b).min(t.data.left);
        let right = a.min(b).max(t.data.right);
        if left < right {
            return;
        }
        let x = |ppm: f64| {
            rect.left() + ((range[0] - ppm) / (range[0] - range[1])) as f32 * rect.width()
        };
        let x0 = x(left).clamp(rect.left(), rect.right());
        let x1 = x(right).clamp(rect.left(), rect.right());
        let bar = egui::Rect::from_min_max(
            Pos2::new(x0, rect.top() - 25.),
            Pos2::new(x1.max(x0 + 2.), rect.top() - 16.),
        );
        painter.rect_filled(bar, 2., self.palette().accent);
        let shade =
            egui::Rect::from_min_max(Pos2::new(x0, rect.top()), Pos2::new(x1, rect.bottom()));
        painter.with_clip_rect(rect).rect_filled(
            shade,
            0.,
            self.palette().accent.gamma_multiply(0.12),
        );
        painter.text(
            Pos2::new(bar.center().x, bar.bottom() + 2.),
            egui::Align2::CENTER_TOP,
            format!("{left:.4} → {right:.4} ppm"),
            egui::FontId::monospace(12.),
            self.palette().accent,
        );
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn app() -> App {
        let mut a = App::state(Instant::now(), false);
        let s = Spectrum {
            path: PathBuf::from("/test/10/pdata/1"),
            title: "test".into(),
            real: vec![1., 2., 3., 4., 5., 6., 7., 8.],
            imag: vec![0.; 8],
            max_amplitude: 8.,
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
            path: s.path.clone(),
            visible: true,
            gain: 1.,
            offset: 0.,
            p0: 0.,
            p1: 0.,
            baseline: [0.; 5],
            integral_scale: 1.,
            normalization_frozen: false,
        };
        a.traces.push(Trace {
            data: Arc::new(s),
            settings,
            cache_key: String::new(),
            cache: vec![],
            processed: Arc::new(vec![]),
            processing_key: vec![],
            revision: 0,
            processed_max: 1.,
            baseline_undo: None,
        });
        a.refresh_processing();
        a
    }
    fn key(k: egui::Key) -> egui::Event {
        egui::Event::Key {
            key: k,
            physical_key: None,
            pressed: true,
            repeat: false,
            modifiers: egui::Modifiers::NONE,
        }
    }
    fn button(pos: Pos2, pressed: bool, modifiers: egui::Modifiers) -> egui::Event {
        egui::Event::PointerButton {
            pos,
            button: egui::PointerButton::Primary,
            pressed,
            modifiers,
        }
    }
    fn frame(
        a: &mut App,
        ctx: &egui::Context,
        time: f64,
        events: Vec<egui::Event>,
        modifiers: egui::Modifiers,
    ) -> (Vec<(usize, egui::Rect)>, egui::FullOutput) {
        let mut hits = vec![];
        let output = ctx.run(
            egui::RawInput {
                screen_rect: Some(egui::Rect::from_min_size(
                    Pos2::ZERO,
                    egui::vec2(800., 600.),
                )),
                time: Some(time),
                events,
                modifiers,
                ..Default::default()
            },
            |ctx| {
                a.handle_integral_delete_shortcut(ctx);
                a.refresh_processing();
                egui::CentralPanel::default().show(ctx, |ui| {
                    let (response, painter) =
                        ui.allocate_painter(ui.available_size(), egui::Sense::click_and_drag());
                    let rect = response.rect.shrink2(egui::vec2(14., 28.));
                    hits = a.paint_integrals(rect, [8., 0.], &painter, &response);
                    let blocked = a.integral_interactions(ui, &hits);
                    let integrate = a.integrate
                        || ctx.input(|i| i.modifiers.shift) && a.integral_edit.is_none();
                    a.integration_indicator(ui, rect, integrate);
                    a.handle_plot_navigation(
                        ctx,
                        &response,
                        rect,
                        [8., 0.],
                        a.has_visible_integrals(),
                        blocked,
                        integrate,
                        &painter,
                    );
                });
            },
        );
        (hits, output)
    }
    #[test]
    fn number_double_click_enter_calibrates_and_hover_d_deletes() {
        let mut a = app();
        a.measure_region(7., 5.);
        a.measure_region(4., 2.);
        a.refresh_processing();
        a.range = Some([8., 0.]);
        let original = a.regions.iter().map(|r| r.value).collect::<Vec<_>>();
        let ctx = egui::Context::default();
        let (hits, _) = frame(&mut a, &ctx, 0., vec![], egui::Modifiers::NONE);
        let p = hits[0].1.center();
        frame(
            &mut a,
            &ctx,
            0.05,
            vec![egui::Event::PointerMoved(p)],
            egui::Modifiers::NONE,
        );
        frame(
            &mut a,
            &ctx,
            0.10,
            vec![button(p, true, egui::Modifiers::NONE)],
            egui::Modifiers::NONE,
        );
        frame(
            &mut a,
            &ctx,
            0.15,
            vec![button(p, false, egui::Modifiers::NONE)],
            egui::Modifiers::NONE,
        );
        assert_eq!(a.selected_integral, Some(0));
        frame(
            &mut a,
            &ctx,
            0.20,
            vec![button(p, true, egui::Modifiers::NONE)],
            egui::Modifiers::NONE,
        );
        frame(
            &mut a,
            &ctx,
            0.25,
            vec![button(p, false, egui::Modifiers::NONE)],
            egui::Modifiers::NONE,
        );
        assert!(a.integral_edit.is_some());
        frame(
            &mut a,
            &ctx,
            0.30,
            vec![egui::Event::Text("3".into())],
            egui::Modifiers::NONE,
        );
        frame(
            &mut a,
            &ctx,
            0.35,
            vec![key(egui::Key::Enter)],
            egui::Modifiers::NONE,
        );
        assert!(a.integral_edit.is_none(), "{:?}", a.status);
        let scale = a.traces[0].settings.integral_scale;
        assert_eq!((a.regions[0].value * scale).to_bits(), 3f64.to_bits());
        assert_eq!(scale.to_bits(), (3. / original[0]).to_bits());
        assert_eq!(
            (a.regions[1].value * scale).to_bits(),
            (original[1] * (3. / original[0])).to_bits()
        );
        assert!(a.traces[0].settings.normalization_frozen);
        assert_eq!(a.range, Some([8., 0.]));
        let (hits, _) = frame(&mut a, &ctx, 0.40, vec![], egui::Modifiers::NONE);
        let second = hits[1].1.center();
        frame(
            &mut a,
            &ctx,
            0.45,
            vec![egui::Event::PointerMoved(second)],
            egui::Modifiers::NONE,
        );
        frame(
            &mut a,
            &ctx,
            0.50,
            vec![key(egui::Key::D), egui::Event::Text("d".into())],
            egui::Modifiers::NONE,
        );
        assert_eq!(a.regions.len(), 1);
        assert_eq!(a.regions[0].value, original[0]);
        assert_eq!(a.traces[0].settings.integral_scale, scale);
    }
    #[test]
    fn shift_drag_previews_creates_on_hovered_spectrum_and_scroll_is_unchanged_in_button_mode() {
        let mut a = app();
        let original = &a.traces[0];
        let mut data = (*original.data).clone();
        data.path = PathBuf::from("/test-second/10/pdata/1");
        let mut settings = original.settings.clone();
        settings.path = data.path.clone();
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
        });
        a.stacked = true;
        let ctx = egui::Context::default();
        let shift = egui::Modifiers {
            shift: true,
            ..Default::default()
        };
        let p = Pos2::new(150., 400.);
        let q = Pos2::new(400., 430.);
        frame(&mut a, &ctx, 0., vec![egui::Event::PointerMoved(p)], shift);
        frame(&mut a, &ctx, 0.1, vec![button(p, true, shift)], shift);
        let (_, output) = frame(&mut a, &ctx, 0.2, vec![egui::Event::PointerMoved(q)], shift);
        assert!(a.start.is_some());
        assert!(output.shapes.iter().any(
            |s| matches!(&s.shape,egui::Shape::Rect(r) if r.fill==COLORS[1]&&r.rect.height()==9.)
        ));
        // The gesture stays an integral if Shift is released before the mouse.
        frame(
            &mut a,
            &ctx,
            0.3,
            vec![button(q, false, egui::Modifiers::NONE)],
            egui::Modifiers::NONE,
        );
        assert_eq!(a.regions.len(), 1);
        assert_eq!(a.regions[0].trace, 1);
        assert!(a.start.is_none());
        assert!(!a.integrate);
        let before = a.traces[1].settings.gain;
        let wheel = |m| egui::Event::MouseWheel {
            unit: egui::MouseWheelUnit::Point,
            delta: egui::vec2(0., 60.),
            modifiers: m,
        };
        frame(&mut a, &ctx, 0.4, vec![wheel(shift)], shift);
        assert_eq!(a.traces[1].settings.gain, before);
        assert!(a.range.is_none());
        frame(&mut a, &ctx, 0.41, vec![], egui::Modifiers::NONE);
        assert_eq!(a.traces[1].settings.gain, before);
        assert!(a.range.is_none());
        a.integrate = true;
        frame(
            &mut a,
            &ctx,
            0.5,
            vec![wheel(egui::Modifiers::NONE)],
            egui::Modifiers::NONE,
        );
        assert_ne!(a.traces[1].settings.gain, before);
        let below = Pos2::new(400., 550.);
        frame(
            &mut a,
            &ctx,
            0.6,
            vec![
                egui::Event::PointerMoved(below),
                wheel(egui::Modifiers::NONE),
            ],
            egui::Modifiers::NONE,
        );
        assert!(a.range.is_some());
    }
    #[test]
    fn invalid_reference_cannot_change_normalization() {
        let mut a = app();
        a.measure_region(7., 5.);
        let before = a.traces[0].settings.integral_scale;
        for v in [0., -1., f64::NAN, f64::INFINITY] {
            assert!(a.calibrate_integral(0, v).is_err());
            assert_eq!(a.traces[0].settings.integral_scale, before);
        }
    }
}
