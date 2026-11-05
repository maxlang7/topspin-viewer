//! TopSpin 5 reference appearance, with native controls wired to the working viewer.
use super::*;
const TOP: Color32 = Color32::from_rgb(5, 65, 105);
const LOWER: Color32 = Color32::from_rgb(4, 48, 77);
const INK: Color32 = Color32::from_rgb(195, 208, 217);
const BG: Color32 = Color32::from_gray(245);
fn icon(ui: &egui::Ui, rect: egui::Rect, name: &'static str, bytes: &'static [u8]) {
    let key = egui::Id::new(("topspin-icon", name, bytes.as_ptr() as usize));
    let texture = ui
        .ctx()
        .data_mut(|d| d.get_temp::<egui::TextureHandle>(key))
        .unwrap_or_else(|| {
            let image = image::load_from_memory(bytes)
                .expect("bundled reference icon")
                .to_rgba8();
            let texture = ui.ctx().load_texture(
                name,
                egui::ColorImage::from_rgba_unmultiplied(
                    [image.width() as usize, image.height() as usize],
                    image.as_raw(),
                ),
                egui::TextureOptions::LINEAR,
            );
            ui.ctx().data_mut(|d| d.insert_temp(key, texture.clone()));
            texture
        });
    ui.painter().image(
        texture.id(),
        rect,
        egui::Rect::from_min_max(Pos2::ZERO, Pos2::new(1., 1.)),
        Color32::WHITE,
    );
}
fn button(
    ui: &mut egui::Ui,
    rect: egui::Rect,
    id: impl std::hash::Hash,
    text: &str,
    bg: Color32,
    fg: Color32,
    size: f32,
) -> egui::Response {
    let r = ui.interact(rect, ui.id().with(id), egui::Sense::click_and_drag());
    ui.painter().rect_filled(
        rect,
        0.,
        if r.hovered() {
            bg.gamma_multiply(1.15)
        } else {
            bg
        },
    );
    if !text.is_empty() {
        ui.painter().text(
            rect.center(),
            egui::Align2::CENTER_CENTER,
            text,
            egui::FontId::proportional(size),
            fg,
        );
    }
    r
}
fn rect(origin: Pos2, x: f32, y: f32, w: f32, h: f32) -> egui::Rect {
    egui::Rect::from_min_size(origin + egui::vec2(x, y), egui::vec2(w, h))
}
pub(super) fn plot_rect(response: egui::Rect) -> egui::Rect {
    egui::Rect::from_min_max(
        response.min + egui::vec2(5., 2.),
        response.max - egui::vec2(41., 24.),
    )
}
pub(super) fn plot_font() -> egui::FontId {
    egui::FontId::new(12., egui::FontFamily::Name("topspin-bold".into()))
}
impl App {
    pub(super) fn topspin_reference_chrome(&mut self, ctx: &egui::Context) {
        egui::TopBottomPanel::top("topspin-ribbon")
            .exact_height(104.)
            .frame(egui::Frame::NONE.fill(TOP))
            .show(ctx, |ui| {
                let area = ui.max_rect();
                let o = area.min;
                let w = area.width();
                ui.painter()
                    .rect_filled(rect(o, 0., 51., w, 53.), 0., LOWER);
                ui.painter().line_segment(
                    [o + egui::vec2(0., 51.), o + egui::vec2(w, 51.)],
                    Stroke::new(1., Color32::BLACK),
                );
                let menu = button(
                    ui,
                    rect(o, 0., 0., 65., 51.),
                    "main-menu",
                    "",
                    TOP,
                    INK,
                    23.,
                );
                for y in [20., 25.5, 31.] {
                    ui.painter().line_segment(
                        [o + egui::vec2(25., y), o + egui::vec2(42., y)],
                        Stroke::new(2., INK),
                    );
                }
                egui::Popup::menu(&menu).show(|ui| {
                    if ui.button("Open…").clicked() {
                        self.focus_open = true;
                        ui.close();
                    }
                    self.project_controls(ui, ctx);
                    if ui
                        .add_enabled(
                            !self.traces.is_empty(),
                            egui::Button::new("Close selected spectrum"),
                        )
                        .clicked()
                    {
                        self.close_trace(self.selected);
                        ui.close();
                    }
                    ui.separator();
                    ui.menu_button("Appearance", |ui| self.appearance_menu(ui));
                    if ui.button("Settings").clicked() {
                        self.show_settings = true;
                        ui.close();
                    }
                });
                let mut x = 65.;
                for (i, (text, width)) in [
                    ("Process", 75.),
                    ("Analyze", 74.),
                    ("Applications", 100.),
                    ("Manage", 75.),
                ]
                .into_iter()
                .enumerate()
                {
                    let r = rect(o, x, 0., width, 51.);
                    if button(
                        ui,
                        r,
                        ("topspin-tab", i),
                        text,
                        if self.topspin_tab == i { LOWER } else { TOP },
                        INK,
                        14.,
                    )
                    .clicked()
                    {
                        self.topspin_tab = i;
                    }
                    ui.painter().line_segment(
                        [r.right_top(), r.right_bottom()],
                        Stroke::new(1., Color32::BLACK),
                    );
                    x += width;
                }
                ui.painter().text(
                    o + egui::vec2((w + 171.5) / 2., 25.5),
                    egui::Align2::CENTER_CENTER,
                    "TopSpin Processing",
                    egui::FontId::proportional(14.),
                    Color32::WHITE,
                );
                if button(
                    ui,
                    rect(o, w - 228., 0., 45., 51.),
                    "settings",
                    "",
                    TOP,
                    INK,
                    24.,
                )
                .clicked()
                {
                    self.show_settings = true;
                }
                icon(
                    ui,
                    rect(o, w - 216., 15., 24., 24.),
                    "settings-reference",
                    include_bytes!("../assets/icons/37e706e6bcd59268@2x.png"),
                );
                let appearance = button(
                    ui,
                    rect(o, w - 183., 0., 52., 51.),
                    "appearance",
                    "",
                    TOP,
                    INK,
                    24.,
                )
                .on_hover_text("Appearance · switch live");
                icon(
                    ui,
                    rect(o, w - 170., 15., 24., 24.),
                    "appearance-reference",
                    include_bytes!("../assets/icons/a4c6ee0955933aaf@2x.png"),
                );
                ui.painter()
                    .rect_filled(rect(o, w - 130., 0., 130., 104.), 0., TOP);
                egui::Popup::menu(&appearance).show(|ui| self.appearance_menu(ui));
                ui.painter().line_segment(
                    [o + egui::vec2(w - 131., 0.), o + egui::vec2(w - 131., 104.)],
                    Stroke::new(1., Color32::BLACK),
                );
                icon(
                    ui,
                    rect(o, w - 130., 12., 130., 80.),
                    "bruker",
                    include_bytes!("../assets/icons/4d72af01e262c2de@2x.png"),
                );
                let commands: &[(&str, usize)] = match self.topspin_tab {
                    1 => &[("Integration", 4), ("Peak Picking", 5), ("Calib. Axis", 3)],
                    2 => &[("Auto Processing", 0), ("Export", 6)],
                    3 => &[("Working Project", 6), ("History", 7), ("Appearance", 8)],
                    _ => &[
                        ("Proc. Spectrum", 0),
                        ("Adjust Phase", 1),
                        ("Baseline", 2),
                        ("Calib. Axis", 3),
                        ("Advanced", 9),
                    ],
                };
                let mut x = 0.;
                for &(text, action) in commands {
                    let width = match action {
                        0 => 169.,
                        1 => 151.,
                        2 => 121.,
                        3 => 134.,
                        _ => 103.,
                    };
                    let r = rect(o, x, 52., width, 51.);
                    let response = button(ui, r, ("ribbon", action), "", LOWER, INK, 14.);
                    let image_rect = rect(o, x + 17., 67., 22., 22.);
                    match action {
                        0 => icon(
                            ui,
                            image_rect,
                            "process",
                            include_bytes!("../assets/icons/80b5a4a4743c8327@2x.png"),
                        ),
                        1 => icon(
                            ui,
                            image_rect,
                            "phase",
                            include_bytes!("../assets/icons/039e954349864a72@2x.png"),
                        ),
                        2 => icon(
                            ui,
                            image_rect,
                            "baseline",
                            include_bytes!("../assets/icons/8487e2dcb1a5f592@2x.png"),
                        ),
                        3 => icon(
                            ui,
                            image_rect,
                            "reference",
                            include_bytes!("../assets/icons/c8e25fef3c1106cd@2x.png"),
                        ),
                        _ => {}
                    }
                    ui.painter().text(
                        o + egui::vec2(x + if action <= 3 { 43. } else { 12. }, 77.5),
                        egui::Align2::LEFT_CENTER,
                        text,
                        egui::FontId::proportional(14.),
                        INK,
                    );
                    let c = o + egui::vec2(x + width - 13., 77.5);
                    ui.painter().add(egui::Shape::convex_polygon(
                        vec![
                            c + egui::vec2(-4., -2.),
                            c + egui::vec2(4., -2.),
                            c + egui::vec2(0., 3.),
                        ],
                        INK,
                        Stroke::NONE,
                    ));
                    ui.painter().line_segment(
                        [r.right_top(), r.right_bottom()],
                        Stroke::new(1., Color32::BLACK),
                    );
                    if response.clicked() {
                        match action {
                            0 => self.select_tool(workspace::Tool::Process),
                            1 => self.select_tool(workspace::Tool::Phase),
                            2 => self.begin_baseline(),
                            3 => {
                                self.select_tool(workspace::Tool::Process);
                                self.prepare_reference();
                                self.reference.picking = self.traces.get(self.selected).is_some();
                            }
                            4 => {
                                self.integrate = true;
                                self.data_tab = 0;
                            }
                            5 => self.select_tool(workspace::Tool::Peaks),
                            6 => self.select_tool(workspace::Tool::Export),
                            7 => self.select_tool(workspace::Tool::History),
                            8 => self.show_settings = true,
                            _ => {
                                self.advanced = !self.advanced;
                                self.select_tool(workspace::Tool::Display);
                            }
                        }
                    }
                    x += width;
                }
            });
        egui::TopBottomPanel::top("topspin-tools")
            .exact_height(39.)
            .frame(egui::Frame::NONE.fill(BG))
            .show(ctx, |ui| {
                let o = ui.max_rect().min;
                let mut x = 10.;
                let names = [
                    "Double intensity",
                    "Halve intensity",
                    "Intensity: drag",
                    "Horizontal zoom: drag",
                    "Vertical offset: drag",
                    "Pan: drag",
                    "Baseline to bottom",
                    "Zoom in",
                    "Zoom out",
                    "Previous zoom",
                    "Full spectrum",
                    "Synchronize visible regions",
                    "Retain expansion when switching datasets",
                    "Measure distance",
                    "Overlay / stack",
                    "More controls",
                ];
                let bytes: [&[u8]; 16] = [
                    include_bytes!("../assets/icons/7a682da9be2ab754@2x.png"),
                    include_bytes!("../assets/icons/2984f691487816fe@2x.png"),
                    include_bytes!("../assets/icons/f297e13743be2abf@2x.png"),
                    include_bytes!("../assets/icons/72f81d2d266f2a29@2x.png"),
                    include_bytes!("../assets/icons/82319bc80817dc38@2x.png"),
                    include_bytes!("../assets/icons/df74449286f2fef8@2x.png"),
                    include_bytes!("../assets/icons/d8b5a20232fed579@2x.png"),
                    include_bytes!("../assets/icons/a11a73bee2e742dd@2x.png"),
                    include_bytes!("../assets/icons/6fa07606bf510be7@2x.png"),
                    include_bytes!("../assets/icons/d6d623d37ec92722@2x.png"),
                    include_bytes!("../assets/icons/da5974157c4e9729@2x.png"),
                    include_bytes!("../assets/icons/d32596090ad1c25a@2x.png"),
                    include_bytes!("../assets/icons/4c6d91b4193e73e5@2x.png"),
                    include_bytes!("../assets/icons/631f85a368667253@2x.png"),
                    include_bytes!("../assets/icons/2dba9eebb099c8fb@2x.png"),
                    include_bytes!("../assets/icons/43559ff79fda4f31@2x.png"),
                ];
                for (i, name) in names.iter().enumerate() {
                    if [2, 4, 7, 11, 13, 15].contains(&i) {
                        ui.painter().line_segment(
                            [o + egui::vec2(x + 4., 7.), o + egui::vec2(x + 4., 31.)],
                            Stroke::new(1., Color32::GRAY),
                        );
                        x += 9.;
                    }
                    let r = rect(o, x, 2., 34., 34.);
                    let response = button(ui, r, ("tool", i), "", BG, Color32::DARK_GRAY, 16.)
                        .on_hover_text(*name);
                    icon(ui, r.shrink(5.), name, bytes[i]);
                    if response.dragged() {
                        let d = ctx.input(|i| i.pointer.delta());
                        match i {
                            2 => {
                                if let Some(t) = self.traces.get_mut(self.selected) {
                                    t.settings.gain = (t.settings.gain
                                        * (-d.y as f64 * 0.01).exp())
                                    .clamp(0.001, 100.);
                                }
                            }
                            3 => self.topspin_zoom((d.x as f64 * 0.01).exp()),
                            4 => {
                                if let Some(t) = self.traces.get_mut(self.selected) {
                                    t.settings.offset -= d.y as f64 / 400.;
                                }
                            }
                            5 => {
                                let mut r = self.range.unwrap_or_else(|| self.full_range());
                                let shift = d.x as f64 / 500. * (r[0] - r[1]);
                                r[0] += shift;
                                r[1] += shift;
                                self.range = Some(r);
                            }
                            _ => {}
                        }
                    }
                    if response.clicked() {
                        self.topspin_tool_action(i);
                    }
                    if i == 15 {
                        egui::Popup::menu(&response).show(|ui| {
                            if ui.button("Display").clicked() {
                                self.select_tool(workspace::Tool::Display);
                            }
                            if ui.button("History / checkpoints").clicked() {
                                self.select_tool(workspace::Tool::History);
                            }
                            self.history_buttons(ui);
                            ui.menu_button("Appearance", |ui| self.appearance_menu(ui));
                        });
                    }
                    x += 34.;
                }
            });
        egui::TopBottomPanel::bottom("topspin-command")
            .exact_height(47.)
            .frame(egui::Frame::NONE.fill(BG))
            .show(ctx, |ui| {
                let o = ui.max_rect().min;
                let response = ui.put(
                    rect(o, 9., 8., 360., 30.),
                    egui::TextEdit::singleline(&mut self.command)
                        .font(egui::FontId::proportional(12.))
                        .hint_text(
                            egui::RichText::new("Command...").color(Color32::from_gray(204)),
                        ),
                );
                if response.lost_focus() && ctx.input(|i| i.key_pressed(egui::Key::Enter)) {
                    match self.command.trim() {
                        ".all" => self.range = None,
                        ".ph" => self.select_tool(workspace::Tool::Phase),
                        "int" => self.integrate = true,
                        ".basl" => self.begin_baseline(),
                        _ => self.status = "Commands: .all, .ph, int, .basl".into(),
                    };
                    self.command.clear();
                }
                ui.painter().text(
                    o + egui::vec2(430., 23.),
                    egui::Align2::LEFT_CENTER,
                    &self.status,
                    egui::FontId::proportional(12.),
                    Color32::DARK_GRAY,
                );
            });
    }
    pub(super) fn topspin_spectrum_tabs(&mut self, ctx: &egui::Context) {
        egui::TopBottomPanel::top("topspin-spectrum-tabs")
            .exact_height(41.)
            .frame(
                egui::Frame::new()
                    .fill(Color32::from_gray(204))
                    .inner_margin(8.),
            )
            .show(ctx, |ui| {
                ui.horizontal(|ui| {
                    if self.integrate {
                        ui.add_sized(
                            [108., 32.],
                            egui::Button::selectable(true, "∫  Integration"),
                        );
                        if ui.button("Define region").clicked() {
                            self.integrate = true;
                        }
                        if ui.button("Reference integral").clicked() {
                            self.select_tool(workspace::Tool::Integrals);
                        }
                        self.history_buttons(ui);
                        if ui.button("Return").clicked() {
                            self.integrate = false;
                        }
                    } else {
                        ui.selectable_value(&mut self.data_tab, 0, "SPECTRUM");
                        ui.selectable_value(&mut self.data_tab, 1, "INTEGRALS");
                        if let Some(t) = self.traces.get(self.selected) {
                            ui.label(data::dataset_name(&t.data.path));
                        }
                        if self.traces.len() > 1 && ui.button("Stack").clicked() {
                            self.stacked = !self.stacked;
                        }
                    }
                });
            });
    }
    fn topspin_tool_action(&mut self, i: usize) {
        match i {
            0 | 1 => {
                if let Some(t) = self.traces.get_mut(self.selected) {
                    t.settings.gain =
                        (t.settings.gain * if i == 0 { 2. } else { 0.5 }).clamp(0.001, 100.);
                }
            }
            6 => {
                if let Some(t) = self.traces.get_mut(self.selected) {
                    t.settings.offset = 0.;
                }
            }
            7 => self.topspin_zoom(0.5),
            8 => self.topspin_zoom(2.),
            9 => {
                if let Some(previous) = self.topspin_previous_range.take() {
                    self.topspin_previous_range = self.range;
                    self.range = Some(previous);
                }
            }
            10 => {
                self.topspin_previous_range = self.range;
                self.range = None;
                if let Some(t) = self.traces.get_mut(self.selected) {
                    t.settings.gain = 1.;
                    t.settings.offset = 0.;
                }
            }
            11 => self.status = "Visible spectra share the ppm axis".into(),
            12 => self.retain_view = !self.retain_view,
            13 => {
                self.distance_mode = !self.distance_mode;
                self.distance_start = None;
                self.status = "Click two spectrum positions to measure their ppm separation".into();
            }
            14 => self.stacked = !self.stacked,
            _ => {}
        }
    }
    fn topspin_zoom(&mut self, factor: f64) {
        let r = self.range.unwrap_or_else(|| self.full_range());
        let m = (r[0] + r[1]) / 2.;
        self.topspin_previous_range = Some(r);
        self.range = Some([m + (r[0] - m) * factor, m + (r[1] - m) * factor]);
    }
    pub(super) fn topspin_browser(&mut self, ctx: &egui::Context) {
        egui::SidePanel::left("topspin-browser")
            .exact_width(340.)
            .resizable(false)
            .frame(egui::Frame::NONE.fill(BG).inner_margin(egui::Margin {
                left: 9,
                right: 10,
                top: 0,
                bottom: 0,
            }))
            .show(ctx, |ui| {
                let h = (ui.available_height() - 10.) / 2.;
                egui::Frame::new()
                    .fill(Color32::from_gray(204))
                    .stroke(Stroke::new(1., Color32::from_gray(142)))
                    .inner_margin(8.)
                    .show(ui, |ui| {
                        ui.set_width(303.);
                        ui.set_height(h - 18.);
                        let (tabs, _) =
                            ui.allocate_exact_size(egui::vec2(303., 32.), egui::Sense::hover());
                        let o = tabs.min;
                        let selected = Color32::from_rgb(174, 200, 217);
                        let r = rect(o, 0., 0., 72., 32.);
                        button(ui, r, "data", "", selected, Color32::DARK_GRAY, 12.);
                        ui.painter().rect_stroke(
                            r,
                            0.,
                            Stroke::new(1., Color32::from_gray(51)),
                            egui::StrokeKind::Inside,
                        );
                        icon(
                            ui,
                            rect(o, 12., 4., 24., 24.),
                            "data-reference",
                            include_bytes!("../assets/icons/browser-data@2x.png"),
                        );
                        ui.painter().text(
                            o + egui::vec2(40., 16.),
                            egui::Align2::LEFT_CENTER,
                            "Data",
                            egui::FontId::proportional(12.),
                            Color32::from_gray(51),
                        );
                        for (i, (name, bytes)) in [
                            (
                                "History",
                                include_bytes!("../assets/icons/topspin-browser-history@2x.png")
                                    .as_slice(),
                            ),
                            (
                                "Overlay / stack",
                                include_bytes!("../assets/icons/topspin-browser-stack@2x.png")
                                    .as_slice(),
                            ),
                            (
                                "Process",
                                include_bytes!("../assets/icons/topspin-browser-process@2x.png")
                                    .as_slice(),
                            ),
                        ]
                        .into_iter()
                        .enumerate()
                        {
                            let r = rect(
                                o,
                                77. + i as f32 * 52.,
                                0.,
                                if i == 2 { 54. } else { 48. },
                                32.,
                            );
                            let response = button(
                                ui,
                                r,
                                ("browser-tab", i),
                                "",
                                Color32::WHITE,
                                Color32::DARK_GRAY,
                                12.,
                            )
                            .on_hover_text(name);
                            icon(
                                ui,
                                egui::Rect::from_center_size(r.center(), egui::vec2(24., 24.)),
                                name,
                                bytes,
                            );
                            if response.clicked() {
                                match i {
                                    0 => self.select_tool(workspace::Tool::History),
                                    1 => self.stacked = !self.stacked,
                                    _ => self.select_tool(workspace::Tool::Process),
                                }
                            }
                        }
                        ui.add_space(9.);
                        egui::Frame::new()
                            .fill(Color32::WHITE)
                            .inner_margin(6.)
                            .show(ui, |ui| {
                                if self.focus_open {
                                    let r = ui.text_edit_singleline(&mut self.path);
                                    r.request_focus();
                                    if ctx.input(|i| i.key_pressed(egui::Key::Enter)) {
                                        self.focus_open = false;
                                        self.open(PathBuf::from(&self.path), ctx.clone());
                                    }
                                }
                                ui.horizontal(|ui| {
                                    ui.add_sized(
                                        [149., 30.],
                                        egui::TextEdit::singleline(&mut self.filter)
                                            .hint_text("Search..."),
                                    );
                                    ui.menu_button("Show Filters", |ui| {
                                        let old = self.natural_sort;
                                        ui.selectable_value(
                                            &mut self.natural_sort,
                                            true,
                                            "Alphabetical (natural)",
                                        );
                                        ui.selectable_value(
                                            &mut self.natural_sort,
                                            false,
                                            "Alphabetical (A–Z)",
                                        );
                                        if old != self.natural_sort {
                                            data::sort_datasets(&mut self.found, self.natural_sort);
                                        }
                                    });
                                });
                            });
                        egui::Frame::new().fill(Color32::WHITE).show(ui, |ui| {
                            ui.set_min_size(egui::vec2(303., h - 133.));
                            let paths = self
                                .found
                                .iter()
                                .filter(|p| {
                                    p.to_string_lossy()
                                        .to_lowercase()
                                        .contains(&self.filter.to_lowercase())
                                })
                                .cloned()
                                .collect::<Vec<_>>();
                            let mut open = None;
                            egui::ScrollArea::both()
                                .max_height(h - 135.)
                                .show(ui, |ui| {
                                    egui::CollapsingHeader::new(if self.path.is_empty() {
                                        "Datasets"
                                    } else {
                                        &self.path
                                    })
                                    .default_open(false)
                                    .show(ui, |ui| {
                                        for p in paths {
                                            let r = ui.selectable_label(
                                                false,
                                                format!(
                                                    "{} / {}",
                                                    data::dataset_name(&p),
                                                    p.file_name()
                                                        .unwrap_or_default()
                                                        .to_string_lossy()
                                                ),
                                            );
                                            if r.hovered() {
                                                self.hovered_sidebar = Some((p.clone(), r.rect));
                                            }
                                            let stack = stack::stack_shortcut(&r, ctx);
                                            if r.clicked() || stack {
                                                open = Some((p, stack));
                                            }
                                        }
                                    });
                                });
                            if let Some((p, stack)) = open {
                                self.sidebar_open(p, stack, ctx.clone());
                            }
                        });
                        ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                            if ui.link("Export").clicked() {
                                self.select_tool(workspace::Tool::Export);
                            }
                        });
                    });
                ui.add_space(10.);
                egui::Frame::new()
                    .fill(Color32::from_gray(204))
                    .stroke(Stroke::new(1., Color32::from_gray(142)))
                    .inner_margin(8.)
                    .show(ui, |ui| {
                        ui.set_width(303.);
                        ui.set_height(h - 18.);
                        let (tabs, _) =
                            ui.allocate_exact_size(egui::vec2(303., 32.), egui::Sense::hover());
                        let o = tabs.min;
                        let items: [(&str, &[u8]); 4] = [
                            (
                                "Display",
                                include_bytes!("../assets/icons/748f49a78d18d04f@2x.png"),
                            ),
                            (
                                "History",
                                include_bytes!("../assets/icons/9efe924beae2e07c@2x.png"),
                            ),
                            (
                                "Processing",
                                include_bytes!("../assets/icons/95998af089d85012@2x.png"),
                            ),
                            (
                                "Status",
                                include_bytes!("../assets/icons/b3aa7344d81b6aad@2x.png"),
                            ),
                        ];
                        for (i, (name, bytes)) in items.into_iter().enumerate() {
                            let r = rect(o, i as f32 * 48., 0., 42., 32.);
                            let response = button(
                                ui,
                                r,
                                ("details-tab", i),
                                "",
                                if i == 0 {
                                    Color32::from_rgb(174, 200, 217)
                                } else {
                                    Color32::WHITE
                                },
                                Color32::DARK_GRAY,
                                12.,
                            )
                            .on_hover_text(name);
                            icon(
                                ui,
                                egui::Rect::from_center_size(r.center(), egui::vec2(24., 24.)),
                                name,
                                bytes,
                            );
                            if i == 0 {
                                ui.painter().rect_stroke(
                                    r,
                                    0.,
                                    Stroke::new(1., Color32::from_gray(51)),
                                    egui::StrokeKind::Inside,
                                );
                            }
                            if response.clicked() {
                                self.select_tool(match i {
                                    0 => workspace::Tool::Display,
                                    2 => workspace::Tool::Process,
                                    _ => workspace::Tool::History,
                                });
                            }
                        }
                        ui.add_space(9.);
                        let (r, _) = ui.allocate_exact_size(
                            egui::vec2(303., (h - 68.).max(1.)),
                            egui::Sense::hover(),
                        );
                        ui.painter().rect_filled(r, 0., BG);
                        ui.painter().text(
                            r.center(),
                            egui::Align2::CENTER_CENTER,
                            if self.traces.is_empty() {
                                "No structure available."
                            } else {
                                ""
                            },
                            egui::FontId::proportional(12.),
                            Color32::DARK_GRAY,
                        );
                    });
            });
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn toolbar_actions_match_their_reference_icons() {
        let mut app = auto_process::tests::app();
        app.range = Some([7., 3.]);
        app.topspin_tool_action(7);
        assert_eq!(app.range, Some([6., 4.]));
        app.topspin_tool_action(9);
        assert_eq!(app.range, Some([7., 3.]));
        app.topspin_tool_action(12);
        assert!(app.retain_view);
        let path = app.traces[0].data.path.clone();
        app.apply_view(
            &path,
            LoadView {
                action: ViewAction::Solo,
                epoch: app.view_epoch,
            },
        );
        assert_eq!(app.range, Some([7., 3.]));
        app.topspin_tool_action(13);
        assert!(app.distance_mode);
        app.topspin_tool_action(14);
        assert!(app.stacked);
        app.traces[0].settings.gain = 8.;
        app.topspin_tool_action(10);
        assert!(app.range.is_none());
        assert_eq!(app.traces[0].settings.gain, 1.);
    }
    #[test]
    fn live_mode_switch_preserves_science_and_matches_reference_geometry() {
        let mut app = auto_process::tests::app();
        app.measure_region(7., 4.);
        app.refresh_processing();
        let recipe = serde_json::to_vec(&app.traces[0].settings).unwrap();
        let values = app.traces[0].processed.clone();
        let integral = app.regions[0].value.to_bits();
        let ctx = egui::Context::default();
        for theme in [
            appearance::Theme::TopSpin,
            appearance::Theme::GruvboxDark,
            appearance::Theme::TopSpin,
        ] {
            app.apply_theme(&ctx, theme);
            assert_eq!(serde_json::to_vec(&app.traces[0].settings).unwrap(), recipe);
            assert_eq!(*app.traces[0].processed, *values);
            assert_eq!(app.regions[0].value.to_bits(), integral);
            let _ = ctx.run(
                egui::RawInput {
                    screen_rect: Some(egui::Rect::from_min_size(
                        Pos2::ZERO,
                        egui::vec2(1438., 911.),
                    )),
                    ..Default::default()
                },
                |ctx| {
                    app.topspin_chrome(ctx);
                    if theme == appearance::Theme::TopSpin {
                        assert_eq!(ctx.available_rect().top(), 143.);
                        app.topspin_browser(ctx);
                        assert_eq!(ctx.available_rect().left(), 340.);
                        assert_eq!(ctx.available_rect().bottom(), 864.);
                    }
                },
            );
        }
        assert_eq!(
            appearance::Theme::TopSpin.palette().series[0],
            Color32::BLUE
        );
    }
}
