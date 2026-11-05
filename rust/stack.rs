use super::*;
pub(super) fn stack_shortcut(response: &egui::Response, ctx: &egui::Context) -> bool {
    response.hovered()
        && ctx.input(|i| {
            i.key_pressed(egui::Key::S)
                && !i.modifiers.command
                && !i.modifiers.ctrl
                && !i.modifiers.alt
        })
}

impl App {
    pub(super) fn handle_sidebar_shortcut(&mut self, ctx: &egui::Context) {
        let Some((path, rect)) = self.hovered_sidebar.clone() else {
            return;
        };
        if !ctx.pointer_hover_pos().is_some_and(|p| rect.contains(p))
            || !ctx.input(|i| {
                i.key_pressed(egui::Key::S)
                    && !i.modifiers.command
                    && !i.modifiers.ctrl
                    && !i.modifiers.alt
            })
        {
            return;
        }
        ctx.input_mut(|i| {
            let modifiers = i.modifiers;
            i.consume_key(modifiers, egui::Key::S);
            i.events
                .retain(|event| !matches!(event,egui::Event::Text(text) if text=="s"||text=="S"));
        });
        self.sidebar_open(path, true, ctx.clone());
    }

    pub(super) fn sidebar_open(&mut self, path: PathBuf, stack: bool, ctx: egui::Context) {
        if self.restoring {
            self.status = "Wait for the comparison to finish loading".into();
            return;
        }
        if !stack {
            self.view_epoch += 1;
        }
        let view = LoadView {
            action: if stack {
                ViewAction::Stack
            } else {
                ViewAction::Solo
            },
            epoch: self.view_epoch,
        };
        self.load_view(path, None, ctx, view);
    }
    pub(super) fn apply_view(&mut self, path: &std::path::Path, view: LoadView) {
        if view.epoch != self.view_epoch {
            return;
        }
        let Some(index) = self.traces.iter().position(|t| t.data.path == path) else {
            return;
        };
        match view.action {
            ViewAction::Solo => {
                for (i, t) in self.traces.iter_mut().enumerate() {
                    t.settings.visible = i == index;
                }
                self.selected = index;
                self.stacked = false;
                self.range = None;
                self.data_tab = 0;
                self.peaks.clear();
            }
            ViewAction::Stack => {
                self.traces[index].settings.visible = true;
                self.selected = index;
                self.stacked = true;
                self.data_tab = 0;
                self.peaks.clear();
            }
            ViewAction::Append | ViewAction::Restore => {}
        }
    }
    pub(super) fn move_trace(&mut self, from: usize, to: usize) {
        if from == to || from >= self.traces.len() || to >= self.traces.len() || self.restoring {
            return;
        }
        let remap = |i: usize| {
            if i == from {
                to
            } else if from < to && i > from && i <= to {
                i - 1
            } else if to < from && i >= to && i < from {
                i + 1
            } else {
                i
            }
        };
        let trace = self.traces.remove(from);
        self.traces.insert(to, trace);
        self.selected = remap(self.selected);
        for r in &mut self.regions {
            r.trace = remap(r.trace);
        }
    }
    pub(super) fn close_trace(&mut self, index: usize) {
        if index >= self.traces.len() || self.restoring {
            return;
        }
        if self.baseline_open {
            self.finish_baseline(false);
        }
        self.flush_automatic();
        self.integral_edit = None;
        self.selected_integral = None;
        self.hovered_integral = None;
        self.start = None;
        self.integration_drag_trace = None;
        self.traces.remove(index);
        self.regions.retain(|r| r.trace != index);
        for r in &mut self.regions {
            if r.trace > index {
                r.trace -= 1;
            }
        }
        if self.selected > index {
            self.selected -= 1;
        } else if self.selected == index {
            self.selected = index.min(self.traces.len().saturating_sub(1));
            self.peaks.clear();
        }
    }
    pub(super) fn zoom_trace(&mut self, index: usize, scroll: f32) {
        if let Some(t) = self.traces.get_mut(index)
            && t.settings.visible
        {
            let factor = (scroll as f64 * 0.003).exp().clamp(0.5, 2.);
            t.settings.gain = (t.settings.gain * factor).clamp(0.001, 100.);
        }
    }
    pub(super) fn spectrum_under_pointer(
        &self,
        rect: egui::Rect,
        range: [f64; 2],
        pos: Pos2,
        integrals: bool,
    ) -> Option<usize> {
        let visible: Vec<_> = self
            .traces
            .iter()
            .enumerate()
            .filter(|(_, t)| t.settings.visible)
            .collect();
        if visible.is_empty() {
            return None;
        }
        if self.stacked {
            let lane = ((pos.y - rect.top()) / (rect.height() / visible.len() as f32))
                .floor()
                .max(0.) as usize;
            return visible.get(lane).map(|(i, _)| *i);
        }
        // Overlay: hit the nearest rendered trace at the pointer's ppm, not the active tab.
        let ppm =
            range[0] - (pos.x - rect.left()) as f64 / rect.width() as f64 * (range[0] - range[1]);
        let common = visible
            .iter()
            .fold(f64::MIN_POSITIVE, |m, (_, t)| m.max(t.processed_max));
        visible
            .iter()
            .filter(|(_, t)| !t.processed.is_empty() && ppm <= t.data.left && ppm >= t.data.right)
            .map(|(i, t)| {
                let n = t.processed.len();
                let half = (range[0] - range[1]) / rect.width() as f64 / 2.;
                let index = |p: f64| {
                    (((t.data.left - p) / (t.data.left - t.data.right) * n as f64).floor() as isize)
                        .clamp(0, n as isize - 1) as usize
                };
                let lo = index(ppm + half);
                let hi = index(ppm - half).max(lo).min(n - 1);
                let (min, max) = t.processed[lo..=hi]
                    .iter()
                    .fold((f64::INFINITY, f64::NEG_INFINITY), |(lo, hi), v| {
                        (lo.min(*v), hi.max(*v))
                    });
                let geometry = integral_plot::lane_layout(
                    rect,
                    false,
                    visible.len(),
                    0,
                    t.settings.offset,
                    integrals,
                );
                let scale = if self.normalize {
                    t.processed_max
                } else {
                    common
                };
                let y = |v: f64| {
                    geometry.baseline - (v / scale * t.settings.gain) as f32 * geometry.amplitude
                };
                let top = y(max);
                let bottom = y(min);
                let distance = if pos.y < top {
                    top - pos.y
                } else if pos.y > bottom {
                    pos.y - bottom
                } else {
                    0.
                };
                (*i, distance)
            })
            .min_by(|a, b| {
                a.1.total_cmp(&b.1).then_with(|| {
                    if a.0 == self.selected {
                        std::cmp::Ordering::Less
                    } else if b.0 == self.selected {
                        std::cmp::Ordering::Greater
                    } else {
                        a.0.cmp(&b.0)
                    }
                })
            })
            .map(|(i, _)| i)
    }
    pub(super) fn stack_window(&mut self, ctx: &egui::Context) {
        let colors = self.colors();
        if self
            .traces
            .iter()
            .filter(|t| t.settings.visible)
            .take(2)
            .count()
            < 2
        {
            return;
        }
        let mut reorder = None;
        egui::Window::new("Stack")
            .id(egui::Id::new("stack-manager"))
            .anchor(egui::Align2::RIGHT_TOP, egui::vec2(-12., 12.))
            .movable(false)
            .default_width(250.)
            .min_width(170.)
            .resizable(true)
            .show(ctx, |ui| {
                ui.horizontal(|ui| {
                    ui.selectable_value(&mut self.stacked, true, "Stacked");
                    ui.selectable_value(&mut self.stacked, false, "Overlay");
                });
                ui.label("Drag rows to change order · hover a dataset + S to add");
                if self.restoring {
                    ui.spinner();
                    return;
                }
                egui::ScrollArea::vertical()
                    .id_salt("stack-rows")
                    .max_height(240.)
                    .show(ui, |ui| {
                        for (i, t) in self
                            .traces
                            .iter_mut()
                            .enumerate()
                            .filter(|(_, t)| t.settings.visible)
                        {
                            let path = t.data.path.clone();
                            let selected = self.selected == i;
                            let color = colors[i % 6];
                            let (row, payload) = ui.dnd_drop_zone::<PathBuf, _>(
                                egui::Frame::new().inner_margin(4.),
                                |ui| {
                                    ui.horizontal(|ui| {
                                        ui.checkbox(&mut t.settings.visible, "").on_hover_text(
                                            "Hide from stack; keep its processing and integrals",
                                        );
                                        ui.dnd_drag_source(
                                            egui::Id::new(("stack-row", &path)),
                                            path.clone(),
                                            |ui| {
                                                if ui
                                                    .selectable_label(
                                                        selected,
                                                        egui::RichText::new(format!(
                                                            "≡  {}",
                                                            data::dataset_name(&path)
                                                        ))
                                                        .color(color),
                                                    )
                                                    .on_hover_text(path.display().to_string())
                                                    .clicked()
                                                {
                                                    self.selected = i;
                                                    self.peaks.clear();
                                                }
                                            },
                                        );
                                    });
                                },
                            );
                            if let Some(source) = payload {
                                let below = ctx
                                    .pointer_interact_pos()
                                    .is_some_and(|p| p.y > row.response.rect.center().y);
                                reorder = Some(((*source).clone(), i + usize::from(below)));
                            }
                        }
                    });
            });
        if let Some((source, mut target)) = reorder
            && let Some(from) = self.traces.iter().position(|t| t.data.path == source)
        {
            if from < target {
                target = target.saturating_sub(1);
            }
            self.move_trace(from, target.min(self.traces.len() - 1));
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn app() -> App {
        let mut a = App::state(Instant::now(), false);
        for name in ["A", "B", "C"] {
            let data = Spectrum {
                nc: 0,
                phc0: 0.,
                phc1: 0.,
                path: PathBuf::from(format!("/{name}/10/pdata/1")),
                title: name.into(),
                real: vec![0.; 4],
                imag: vec![0.; 4],
                left: 4.,
                right: 0.,
                ft_size: 4,
                max_amplitude: f64::MIN_POSITIVE,
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
                checkpoints: vec![],
            };
            a.traces.push(Trace {
                data: Arc::new(data),
                settings,
                processed: Arc::new(vec![]),
                processing_key: vec![],
                revision: 0,
                processed_max: 1.,
                baseline_undo: None,
                history: history::History::default(),
                cache_key: String::new(),
                cache: vec![],
            });
        }
        a.selected = 2;
        a.refresh_processing();
        a
    }
    fn region(trace: usize, value: f64) -> Region {
        Region {
            trace,
            left: 4.,
            right: 1.,
            bias: 0.,
            slope: 0.,
            baseline: false,
            points: 1,
            value,
            calculation_key: vec![],
            curve: vec![],
            error: None,
        }
    }
    #[test]
    fn reorder_preserves_selection_processing_and_region_ownership() {
        let mut a = app();
        a.regions = vec![region(0, 100.), region(2, 300.)];
        a.traces[2].settings.baseline = [2.; 5];
        a.move_trace(2, 0);
        assert_eq!(a.traces[a.selected].data.title, "C");
        assert_eq!(
            a.regions
                .iter()
                .map(|r| a.traces[r.trace].data.title.clone())
                .collect::<Vec<_>>(),
            vec!["A", "C"]
        );
        assert_eq!(a.traces[0].settings.baseline, [2.; 5]);
        a.move_trace(0, 2);
        assert_eq!(a.selected, 2);
        assert_eq!(a.regions[0].trace, 0);
        assert_eq!(a.regions[1].trace, 2);
        a.close_trace(1);
        assert_eq!(a.regions.len(), 2);
        assert_eq!(a.regions[1].trace, 1);
        assert_eq!(a.selected, 1);
    }
    #[test]
    fn sidebar_click_opens_single_and_stack_preserves_current() {
        let mut a = app();
        let b = a.traces[1].data.path.clone();
        let c = a.traces[2].data.path.clone();
        a.apply_view(
            &b,
            LoadView {
                action: ViewAction::Solo,
                epoch: 0,
            },
        );
        assert_eq!(
            a.traces
                .iter()
                .map(|t| t.settings.visible)
                .collect::<Vec<_>>(),
            vec![false, true, false]
        );
        a.apply_view(
            &c,
            LoadView {
                action: ViewAction::Stack,
                epoch: 0,
            },
        );
        assert!(a.stacked);
        assert_eq!(
            a.traces
                .iter()
                .map(|t| t.settings.visible)
                .collect::<Vec<_>>(),
            vec![false, true, true]
        );
        assert_eq!(a.selected, 2);
    }
    #[test]
    fn late_load_cannot_replace_latest_sidebar_selection_or_clear_regions() {
        let mut a = app();
        a.view_epoch = 2;
        a.selected = 1;
        a.regions.push(region(1, 5.));
        let mut old = a.traces[0].data.as_ref().clone();
        old.path = PathBuf::from("/old/10/pdata/1");
        a.pending = 1;
        a.handle_message(
            Message::Loaded(
                Ok(Box::new(old)),
                None,
                LoadView {
                    action: ViewAction::Solo,
                    epoch: 1,
                },
            ),
            &egui::Context::default(),
        );
        assert_eq!(a.selected, 1);
        assert!(!a.traces[3].settings.visible);
        a.handle_message(
            Message::Loaded(
                Err("broken".into()),
                None,
                LoadView {
                    action: ViewAction::Solo,
                    epoch: 2,
                },
            ),
            &egui::Context::default(),
        );
        assert_eq!(a.regions.len(), 1);
    }
    #[test]
    fn vertical_zoom_targets_hovered_lane_or_overlay_trace_only() {
        let mut a = app();
        a.stacked = true;
        let rect = egui::Rect::from_min_size(Pos2::ZERO, egui::vec2(1000., 600.));
        let hit = a
            .spectrum_under_pointer(rect, [4., 0.], Pos2::new(200., 250.), false)
            .unwrap();
        assert_eq!(hit, 1);
        a.zoom_trace(hit, 100.);
        assert_eq!(a.traces[0].settings.gain, 1.);
        assert!(a.traces[1].settings.gain > 1.);
        assert_eq!(a.traces[2].settings.gain, 1.);
        a.stacked = false;
        a.traces[1].settings.offset = 0.3;
        a.traces[2].settings.visible = false;
        let hit = a
            .spectrum_under_pointer(rect, [4., 0.], Pos2::new(200., 402.), false)
            .unwrap();
        assert_eq!(hit, 1);
    }
    #[test]
    fn stack_window_drag_reorders_the_actual_trace_list() {
        let mut a = app();
        let ctx = egui::Context::default();
        let frame = |a: &mut App, events: Vec<egui::Event>| {
            let input = egui::RawInput {
                screen_rect: Some(egui::Rect::from_min_size(
                    Pos2::ZERO,
                    egui::vec2(1000., 800.),
                )),
                events,
                ..Default::default()
            };
            ctx.run(input, |ctx| a.stack_window(ctx))
        };
        // Anchored windows settle their measured size before pointer coordinates are read.
        for _ in 0..5 {
            let _ = frame(&mut a, vec![]);
        }
        let source_id = egui::Id::new(("stack-row", &a.traces[0].data.path));
        let target_id = egui::Id::new(("stack-row", &a.traces[2].data.path));
        let source = ctx.read_response(source_id).unwrap().rect.center();
        let target = ctx.read_response(target_id).unwrap().rect.center() + egui::vec2(0., 5.);
        let _ = frame(&mut a, vec![egui::Event::PointerMoved(source)]);
        let _ = frame(
            &mut a,
            vec![egui::Event::PointerButton {
                pos: source,
                button: egui::PointerButton::Primary,
                pressed: true,
                modifiers: Default::default(),
            }],
        );
        let _ = frame(&mut a, vec![egui::Event::PointerMoved(target)]);
        let _ = frame(&mut a, vec![egui::Event::PointerMoved(target)]);
        let _ = frame(
            &mut a,
            vec![egui::Event::PointerButton {
                pos: target,
                button: egui::PointerButton::Primary,
                pressed: false,
                modifiers: Default::default(),
            }],
        );
        assert_eq!(a.traces[2].data.title, "A");
    }
    #[test]
    fn hover_s_works_with_keyboard_focus_on_a_sidebar_button() {
        let ctx = egui::Context::default();
        let mut row = egui::Rect::NOTHING;
        let input = egui::RawInput {
            screen_rect: Some(egui::Rect::from_min_size(
                Pos2::ZERO,
                egui::vec2(500., 400.),
            )),
            ..Default::default()
        };
        let _ = ctx.run(input.clone(), |ctx| {
            egui::CentralPanel::default().show(ctx, |ui| {
                let r = ui.button("Folder");
                r.request_focus();
                row = r.rect;
            });
        });
        let mut requested = false;
        let mut next = input;
        next.events = vec![
            egui::Event::PointerMoved(row.center()),
            egui::Event::Key {
                key: egui::Key::S,
                physical_key: Some(egui::Key::S),
                pressed: true,
                repeat: false,
                modifiers: Default::default(),
            },
        ];
        let _ = ctx.run(next, |ctx| {
            egui::CentralPanel::default().show(ctx, |ui| {
                let r = ui.button("Folder");
                requested = stack_shortcut(&r, ctx);
            });
        });
        assert!(requested);
    }
    #[test]
    fn hover_s_stacks_without_typing_into_the_focused_search_field() {
        let mut a = app();
        a.filter = "MLN".into();
        let path = a.traces[1].data.path.clone();
        let rect = egui::Rect::from_min_max(Pos2::new(200., 100.), Pos2::new(300., 125.));
        a.hovered_sidebar = Some((path, rect));
        let ctx = egui::Context::default();
        let input = egui::RawInput {
            screen_rect: Some(egui::Rect::from_min_size(
                Pos2::ZERO,
                egui::vec2(500., 400.),
            )),
            ..Default::default()
        };
        let _ = ctx.run(input.clone(), |ctx| {
            egui::CentralPanel::default().show(ctx, |ui| {
                ui.text_edit_singleline(&mut a.filter).request_focus();
            });
        });
        let mut next = input;
        next.events = vec![
            egui::Event::PointerMoved(rect.center()),
            egui::Event::Key {
                key: egui::Key::S,
                physical_key: Some(egui::Key::S),
                pressed: true,
                repeat: false,
                modifiers: Default::default(),
            },
            egui::Event::Text("s".into()),
        ];
        let _ = ctx.run(next, |ctx| {
            a.handle_sidebar_shortcut(ctx);
            egui::CentralPanel::default().show(ctx, |ui| {
                ui.text_edit_singleline(&mut a.filter);
            });
        });
        assert!(a.stacked);
        assert_eq!(a.selected, 1);
        assert_eq!(a.filter, "MLN");
    }
}
