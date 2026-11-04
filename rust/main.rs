mod data;
mod integral_plot;
mod native_ui;
mod processing;
#[cfg(feature = "native-raw")]
mod raw;
mod stack;
mod ui;
use data::Spectrum;
use eframe::egui::{self, Color32, Pos2, Stroke};
use serde::{Deserialize, Serialize};
use std::{
    path::PathBuf,
    sync::{Arc, mpsc},
    time::Instant,
};
const COLORS: [Color32; 6] = [
    Color32::from_rgb(142, 192, 124),
    Color32::from_rgb(250, 189, 47),
    Color32::from_rgb(131, 165, 152),
    Color32::from_rgb(211, 134, 155),
    Color32::from_rgb(254, 128, 25),
    Color32::from_rgb(184, 187, 38),
];
#[derive(Clone, Serialize, Deserialize)]
struct Settings {
    path: PathBuf,
    visible: bool,
    gain: f64,
    offset: f64,
    p0: f64,
    p1: f64,
    #[serde(default)]
    baseline: [f64; 5],
    #[serde(default = "one")]
    integral_scale: f64,
    #[serde(default = "frozen")]
    normalization_frozen: bool,
}
fn frozen() -> bool {
    true
}
fn one() -> f64 {
    1.
}
#[derive(Clone, Serialize, Deserialize)]
struct Region {
    trace: usize,
    left: f64,
    right: f64,
    bias: f64,
    slope: f64,
    baseline: bool,
    points: usize,
    value: f64,
    #[serde(skip)]
    calculation_key: Vec<u64>,
    #[serde(skip)]
    curve: Vec<f64>,
    #[serde(skip)]
    error: Option<String>,
}

struct Trace {
    data: Arc<Spectrum>,
    settings: Settings,
    cache_key: String,
    cache: Vec<egui::Shape>,
    processed: Arc<Vec<f64>>,
    processing_key: Vec<u64>,
    revision: u64,
    processed_max: f64,
    baseline_undo: Option<[f64; 5]>,
}
#[derive(Serialize, Deserialize)]
struct Session {
    version: u32,
    traces: Vec<Settings>,
    stacked: bool,
    normalize: bool,
    range: Option<[f64; 2]>,
    #[serde(default)]
    regions: Vec<(usize, f64, f64, f64)>,
    #[serde(default)]
    integration_regions: Vec<Region>,
}
#[derive(Clone, Copy, Debug, PartialEq)]
enum ViewAction {
    Append,
    Solo,
    Stack,
    Restore,
}
#[derive(Clone, Copy)]
struct LoadView {
    action: ViewAction,
    epoch: u64,
}
enum Message {
    Exported(Result<String, String>),
    Loaded(Result<Box<Spectrum>, String>, Option<Settings>, LoadView),
    Found(Result<Vec<PathBuf>, String>),
}
struct App {
    view_epoch: u64,
    restoring: bool,
    hovered_sidebar: Option<(PathBuf, egui::Rect)>,
    show_inspector: bool,
    nav: String,
    data_tab: usize,
    command: String,
    traces: Vec<Trace>,
    found: Vec<PathBuf>,
    selected: usize,
    path: String,
    filter: String,
    natural_sort: bool,
    status: String,
    stacked: bool,
    normalize: bool,
    range: Option<[f64; 2]>,
    tx: mpsc::Sender<Message>,
    rx: mpsc::Receiver<Message>,
    pending: usize,
    integrate: bool,
    start: Option<f64>,
    regions: Vec<Region>,
    region_bias: f64,
    region_slope: f64,
    region_baseline: bool,
    region_points: usize,
    calibration_value: f64,
    baseline_open: bool,
    baseline_session: Option<(PathBuf, [f64; 5])>,
    baseline_points: usize,
    region_a: f64,
    region_b: f64,
    peaks: Vec<(f64, f64)>,
    peak_threshold: f64,
    session_path: String,
    started: Instant,
    first_frame: bool,
    smoke: bool,
}
impl App {
    fn new(
        cc: &eframe::CreationContext<'_>,
        started: Instant,
        paths: Vec<PathBuf>,
        smoke: bool,
    ) -> Self {
        let mut v = egui::Visuals::dark();
        v.panel_fill = Color32::from_rgb(40, 40, 40);
        v.window_fill = v.panel_fill;
        v.extreme_bg_color = Color32::from_rgb(29, 32, 33);
        v.override_text_color = Some(Color32::from_rgb(235, 219, 178));
        v.selection.bg_fill = Color32::from_rgb(80, 73, 69);
        cc.egui_ctx.set_visuals(v);
        let mut s = Self::state(started, smoke);
        if paths.is_empty()
            && let Some(home) = std::env::var_os("HOME")
        {
            let root = PathBuf::from(home).join("Documents/IGB Lab/NMRs");
            if root.is_dir() {
                s.path = root.display().to_string();
                s.open(root, cc.egui_ctx.clone());
            }
        }
        for p in paths {
            s.open(p, cc.egui_ctx.clone());
        }
        s
    }
    fn handle_message(&mut self, m: Message, ctx: &egui::Context) {
        self.pending = self.pending.saturating_sub(1);
        match m {
            Message::Exported(r) => self.status = r.unwrap_or_else(|e| e),
            Message::Found(Ok(paths)) => {
                if paths.is_empty() {
                    self.status =
                        "No processed 1D spectra found. Raw FIDs and ZIPs: use run-legacy.sh."
                            .into()
                } else {
                    self.status = format!(
                        "Found {} spectra. Select rows to add to comparison.",
                        paths.len()
                    );
                    if paths.len() == 1 {
                        self.load(paths[0].clone(), None, ctx.clone());
                    }
                    for p in paths {
                        if !self.found.contains(&p) {
                            self.found.push(p);
                        }
                    }
                    data::sort_datasets(&mut self.found, self.natural_sort);
                }
            }
            Message::Loaded(Ok(s), settings, view) => {
                let path = s.path.clone();
                if !self.traces.iter().any(|t| t.data.path == s.path) {
                    let import_saved = settings.is_none();
                    let settings = settings.unwrap_or(Settings {
                        path: s.path.clone(),
                        visible: true,
                        gain: 1.,
                        offset: 0.,
                        p0: 0.,
                        p1: 0.,
                        baseline: [0.; 5],
                        integral_scale: s.integral_scale,
                        normalization_frozen: !s.saved_regions.is_empty(),
                    });
                    if import_saved {
                        for row in &s.saved_regions {
                            self.regions.push(Region {
                                trace: self.traces.len(),
                                left: row[0],
                                right: row[1],
                                bias: row[2],
                                slope: row[3],
                                baseline: false,
                                points: 1,
                                value: 0.,
                                calculation_key: vec![],
                                curve: vec![],
                                error: None,
                            });
                        }
                    }
                    self.traces.push(Trace {
                        data: Arc::from(s),
                        settings,
                        cache_key: String::new(),
                        cache: vec![],
                        processed: Arc::new(vec![]),
                        processing_key: vec![],
                        revision: 0,
                        processed_max: 1.,
                        baseline_undo: None,
                    });
                    if view.epoch == self.view_epoch {
                        self.selected = self.traces.len() - 1;
                    }
                    if view.epoch != self.view_epoch {
                        self.traces.last_mut().unwrap().settings.visible = false;
                    }
                    self.status = format!("{} spectra loaded", self.traces.len());
                }
                self.apply_view(&path, view);
            }
            Message::Loaded(Err(e), _, _) | Message::Found(Err(e)) => {
                self.status = e;
                if self.restoring {
                    self.regions.clear();
                }
            }
        }
        if self.pending == 0 {
            self.restoring = false;
        }
    }
    fn state(started: Instant, smoke: bool) -> Self {
        let (tx, rx) = mpsc::channel();
        Self {
            view_epoch: 0,
            restoring: false,
            hovered_sidebar: None,
            show_inspector: false,
            nav: "Process".into(),
            data_tab: 0,
            command: String::new(),
            traces: vec![],
            found: vec![],
            selected: 0,
            path: String::new(),
            filter: String::new(),
            natural_sort: true,
            status: "Open a Bruker folder or drop folders here. Ctrl+O opens the path field."
                .into(),
            stacked: false,
            normalize: true,
            range: None,
            tx,
            rx,
            pending: 0,
            integrate: false,
            start: None,
            regions: vec![],
            region_bias: 0.,
            region_slope: 0.,
            region_baseline: false,
            region_points: 4,
            calibration_value: 1.,
            baseline_open: false,
            baseline_session: None,
            baseline_points: 32,
            region_a: 0.,
            region_b: 1.,
            peaks: vec![],
            peak_threshold: 0.05,
            session_path: "comparison.nmr.json".into(),
            started,
            first_frame: true,
            smoke,
        }
    }
    fn load(&mut self, p: PathBuf, settings: Option<Settings>, ctx: egui::Context) {
        self.load_view(
            p,
            settings,
            ctx,
            LoadView {
                action: ViewAction::Append,
                epoch: self.view_epoch,
            },
        );
    }
    fn load_view(
        &mut self,
        p: PathBuf,
        settings: Option<Settings>,
        ctx: egui::Context,
        view: LoadView,
    ) {
        let p = std::fs::canonicalize(&p).unwrap_or(p);
        let p = if p.is_file() {
            p.parent().unwrap_or(&p).to_owned()
        } else {
            p
        };
        if self.traces.iter().any(|t| t.data.path == p) {
            self.apply_view(&p, view);
            return;
        }
        self.pending += 1;
        let tx = self.tx.clone();
        std::thread::spawn(move || {
            let result = data::load(&p);
            let _ = tx.send(Message::Loaded(result.map(Box::new), settings, view));
            ctx.request_repaint();
        });
    }
    fn open(&mut self, p: PathBuf, ctx: egui::Context) {
        self.pending += 1;
        let tx = self.tx.clone();
        std::thread::spawn(move || {
            let _ = tx.send(Message::Found(data::discover_browser(&p)));
            ctx.request_repaint();
        });
    }
    fn full_range(&self) -> [f64; 2] {
        let mut r = [f64::NEG_INFINITY, f64::INFINITY];
        for t in &self.traces {
            if t.settings.visible {
                r[0] = r[0].max(t.data.left);
                r[1] = r[1].min(t.data.right);
            }
        }
        if r[0] > r[1] { r } else { [12., -1.] }
    }
    fn export(&mut self, ctx: egui::Context) {
        let Some(t) = self.traces.get(self.selected) else {
            self.status = "Select a spectrum".into();
            return;
        };
        let data = t.data.clone();
        let settings = t.settings.clone();
        let tx = self.tx.clone();
        self.pending += 1;
        std::thread::spawn(move || {
            let result = (|| -> Result<String, String> {
                let p = data.path.join("spectrum.csv"); // explicit export creates a new file only
                use std::io::Write;
                let phased = processing::phase(
                    &data.real,
                    &data.imag,
                    settings.p0,
                    settings.p1,
                    data.ft_size,
                );
                let processed =
                    processing::polynomial(&phased, settings.baseline, 0, phased.len())?;
                let f = std::fs::OpenOptions::new()
                    .write(true)
                    .create_new(true)
                    .open(&p)
                    .map_err(|e| e.to_string())?;
                let mut w = std::io::BufWriter::new(f);
                writeln!(w, "ppm,real,imaginary,processed_preview").map_err(|e| e.to_string())?;
                for (i, value) in processed.iter().enumerate() {
                    writeln!(
                        w,
                        "{:.17e},{:.17e},{:.17e},{:.17e}",
                        data.ppm(i),
                        data.real[i],
                        data.imag.get(i).copied().unwrap_or(0.),
                        value
                    )
                    .map_err(|e| e.to_string())?;
                }
                w.flush().map_err(|e| e.to_string())?;
                Ok(format!("Exported {}", p.display()))
            })();
            let _ = tx.send(Message::Exported(result));
            ctx.request_repaint();
        });
    }
    fn save_session(&self) -> Result<String, String> {
        use std::io::Write;
        if self.baseline_open {
            return Err("Apply or cancel the baseline preview before saving".into());
        }
        let s = Session {
            version: 2,
            traces: self.traces.iter().map(|t| t.settings.clone()).collect(),
            stacked: self.stacked,
            normalize: self.normalize,
            range: self.range,
            regions: vec![],
            integration_regions: self.regions.clone(),
        };
        let bytes = serde_json::to_vec_pretty(&s).map_err(|e| e.to_string())?;
        let mut f = std::fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&self.session_path)
            .map_err(|e| e.to_string())?;
        f.write_all(&bytes).map_err(|e| e.to_string())?;
        Ok("Saved comparison. Choose a new filename for subsequent saves.".into())
    }
    fn restore(&mut self, ctx: egui::Context) -> Result<String, String> {
        if self.pending > 0 || self.baseline_open {
            return Err(
                "Finish baseline preview, pending loads and exports before restoring a comparison"
                    .into(),
            );
        }
        let bytes = std::fs::read(&self.session_path).map_err(|e| e.to_string())?;
        let s: Session = serde_json::from_slice(&bytes).map_err(|e| e.to_string())?;
        if ![1, 2].contains(&s.version)
            || s.traces.len() > 128
            || s.traces
                .iter()
                .map(|t| &t.path)
                .collect::<std::collections::HashSet<_>>()
                .len()
                != s.traces.len()
            || s.range
                .is_some_and(|r| !r[0].is_finite() || !r[1].is_finite() || r[0] <= r[1])
            || s.traces.iter().any(|t| {
                ![t.gain, t.offset, t.p0, t.p1, t.integral_scale]
                    .iter()
                    .chain(t.baseline.iter())
                    .all(|n| n.is_finite())
            })
        {
            return Err("Invalid comparison session".into());
        }
        let restored_regions = if s.version == 1 {
            s.regions
                .iter()
                .map(|&(trace, left, right, _)| Region {
                    trace,
                    left,
                    right,
                    bias: 0.,
                    slope: 0.,
                    baseline: false,
                    points: 1,
                    value: 0.,
                    calculation_key: vec![],
                    curve: vec![],
                    error: None,
                })
                .collect()
        } else {
            s.integration_regions
        };
        if restored_regions.iter().any(|r| {
            r.trace >= s.traces.len()
                || ![r.left, r.right, r.bias, r.slope]
                    .iter()
                    .all(|v| v.is_finite())
                || r.points == 0
        }) {
            return Err("Invalid integration regions".into());
        }
        self.stacked = s.stacked;
        self.normalize = s.normalize;
        self.range = s.range;
        self.regions = restored_regions;
        self.traces.clear();
        self.selected = 0;
        self.view_epoch += 1;
        self.restoring = !s.traces.is_empty();
        let view = LoadView {
            action: ViewAction::Restore,
            epoch: self.view_epoch,
        };
        let tx = self.tx.clone();
        self.pending += s.traces.len();
        std::thread::spawn(move || {
            for t in s.traces {
                let _ = tx.send(Message::Loaded(
                    data::load(&t.path).map(Box::new),
                    Some(t),
                    view,
                ));
                ctx.request_repaint();
            }
        });
        Ok("Loading comparison".into())
    }
}
impl eframe::App for App {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        while let Ok(m) = self.rx.try_recv() {
            self.handle_message(m, ctx);
        }
        for f in ctx.input(|i| i.raw.dropped_files.clone()) {
            if let Some(p) = f.path {
                self.open(p, ctx.clone());
            }
        }
        self.handle_sidebar_shortcut(ctx);
        self.topspin_chrome(ctx);
        self.hovered_sidebar = None;
        egui::SidePanel::left("browser")
            .default_width(245.)
            .min_width(180.)
            .resizable(true)
            .show(ctx, |ui| {
                ui.horizontal(|ui| {
                    ui.selectable_label(true, "Data")
                        .on_hover_text("Bruker datasets");
                    ui.label("NMR Viewer");
                });
                ui.separator();
                ui.label("Open data directory");
                let response = ui.add(
                    egui::TextEdit::singleline(&mut self.path)
                        .hint_text("Folder or processing number"),
                );
                if ctx.input(|i| i.modifiers.command && i.key_pressed(egui::Key::O)) {
                    response.request_focus();
                }
                if ui.button("Open / refresh").clicked()
                    || (response.lost_focus() && ui.input(|i| i.key_pressed(egui::Key::Enter)))
                {
                    self.open(PathBuf::from(&self.path), ctx.clone());
                }
                ui.separator();
                ui.label("Dataset browser");
                ui.add(egui::TextEdit::singleline(&mut self.filter).hint_text("Filter datasets…"));
                let previous = self.natural_sort;
                egui::ComboBox::from_id_salt("dataset-sort")
                    .selected_text(if self.natural_sort {
                        "Alphabetical (natural)"
                    } else {
                        "Alphabetical (A-Z)"
                    })
                    .show_ui(ui, |ui| {
                        ui.selectable_value(&mut self.natural_sort, true, "Alphabetical (natural)")
                            .on_hover_text("Compare numbers numerically: MLN1P85 before MLN1P115");
                        ui.selectable_value(&mut self.natural_sort, false, "Alphabetical (A-Z)");
                    });
                if previous != self.natural_sort {
                    data::sort_datasets(&mut self.found, self.natural_sort);
                }
                if ui.button("Add matching spectra, up to 32").clicked() {
                    let matches: Vec<_> = self
                        .found
                        .iter()
                        .filter(|p| {
                            p.to_string_lossy()
                                .to_lowercase()
                                .contains(&self.filter.to_lowercase())
                        })
                        .take(32)
                        .cloned()
                        .collect();
                    for p in matches {
                        self.sidebar_open(p, true, ctx.clone());
                    }
                }
                let filter = self.filter.to_lowercase();
                let matches: Vec<_> = self
                    .found
                    .iter()
                    .filter(|p| p.to_string_lossy().to_lowercase().contains(&filter))
                    .collect();
                ui.label(format!("{} / {} datasets", matches.len(), self.found.len()));
                let mut add = None;
                let row_height = ui
                    .text_style_height(&egui::TextStyle::Body)
                    .max(ui.spacing().interact_size.y);
                egui::ScrollArea::vertical()
                    .id_salt("dataset-list")
                    .auto_shrink([false, false])
                    .scroll_bar_visibility(egui::scroll_area::ScrollBarVisibility::AlwaysVisible)
                    .show_rows(ui, row_height, matches.len(), |ui, rows| {
                        for row in rows {
                            let p = matches[row];
                            let raw = !p.join("1r").is_file();
                            let name = format!(
                                "{} / {}{}",
                                data::dataset_name(p),
                                p.file_name().unwrap_or_default().to_string_lossy(),
                                if raw { " (FID)" } else { "" }
                            );
                            let row_response = ui
                                .add_sized(
                                    [ui.available_width(), row_height],
                                    egui::Button::selectable(false, name)
                                        .wrap_mode(egui::TextWrapMode::Truncate),
                                )
                                .on_hover_text(format!(
                                    "{}\nClick: open · Hover + S: add to stack",
                                    p.display()
                                ));
                            if row_response.hovered() {
                                self.hovered_sidebar = Some((p.clone(), row_response.rect));
                            }
                            let stack = stack::stack_shortcut(&row_response, ctx);
                            if row_response.clicked() || stack {
                                add = Some((p.clone(), stack));
                            }
                        }
                    });
                if let Some((p, stack)) = add {
                    self.sidebar_open(p, stack, ctx.clone());
                }
            });
        if self.show_inspector {
            egui::SidePanel::right("inspector").default_width(235.).min_width(180.).resizable(true).show(ctx,|ui|{
            ui.heading("Display / analysis");
            ui.horizontal(|ui|{ui.selectable_value(&mut self.stacked,false,"Overlay");ui.selectable_value(&mut self.stacked,true,"Stacked");});
            ui.checkbox(&mut self.normalize,"Normalize spectra");
            egui::ScrollArea::vertical().id_salt("inspector-scroll").show(ui,|ui|{
                ui.separator();
                if let Some(t) = self.traces.get_mut(self.selected) {
                    ui.label(&t.data.title);
                    ui.label(format!("{} samples", t.data.real.len()));
                    ui.add(
                        egui::Slider::new(&mut t.settings.gain, 0.001..=100.)
                            .logarithmic(true)
                            .text("Gain"),
                    );
                    ui.add(egui::Slider::new(&mut t.settings.offset, -2. ..=2.).text("Offset"));
                    ui.collapsing("Phase preview", |ui| {
                        if t.data.imag.is_empty() {
                            ui.label("No 1i component. Phase unavailable.");
                        } else {
                            ui.add(
                                egui::Slider::new(&mut t.settings.p0, -180. ..=180.).text("PH0"),
                            );
                            ui.add(
                                egui::Slider::new(&mut t.settings.p1, -720. ..=720.).text("PH1"),
                            );
                            if ui.button("Reset phase").clicked() {
                                t.settings.p0 = 0.;
                                t.settings.p1 = 0.;
                            }
                            ui.label("Preview only. Source data stays unchanged.");
                        }
                    });
                }
                self.integral_controls(ui);
                ui.collapsing("Peak picking",|ui|{
                ui.add(egui::Slider::new(&mut self.peak_threshold,0.001..=1.).logarithmic(true).text("Threshold"));
                if ui.button("Pick selected spectrum").clicked() && let Some(t)=self.traces.get_mut(self.selected) {self.peaks=t.pick_peaks(self.peak_threshold);}
                ui.label("Positive local maxima, up to 128 strongest. Threshold relative to source maximum.");
                egui::ScrollArea::vertical().max_height(150.).show(ui,|ui|{for (ppm,value) in &self.peaks{ui.monospace(format!("{ppm:.5} ppm   {value:.3}"));}});
            });
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

            });
        });
        }
        self.baseline_controls(ctx);
        self.spectrum_tabs(ctx);
        self.refresh_processing();
        egui::CentralPanel::default().show(ctx,|ui|{
   if self.data_tab==1 {self.integral_table(ui);return;}

   let (response,painter)=ui.allocate_painter(ui.available_size(),egui::Sense::click_and_drag());let rect=response.rect.shrink2(egui::vec2(14.,28.));if rect.width()<10.||rect.height()<10.{return}
   painter.rect_filled(response.rect,0.,Color32::from_rgb(29,32,33));let range=self.range.unwrap_or_else(||self.full_range());let width=range[0]-range[1];let ppm=|x:f32|range[0]-(x-rect.left())as f64/rect.width()as f64*width;
   let ticks=(rect.width()/75.).floor().clamp(2.,10.)as usize;
   for j in 0..=ticks{let x=rect.left()+rect.width()*j as f32/ticks as f32;painter.line_segment([Pos2::new(x,rect.top()),Pos2::new(x,rect.bottom())],Stroke::new(1.,Color32::from_rgb(50,48,47)));painter.text(Pos2::new(x,rect.bottom()+8.),egui::Align2::CENTER_TOP,format!("{:.2}",ppm(x)),egui::FontId::monospace(12.),Color32::from_rgb(189,174,147));}
   let have_integrals=self.has_visible_integrals();
   let selected=self.selected;
   let count=self.traces.iter().filter(|t|t.settings.visible).count().max(1);let common=self.traces.iter().filter(|t|t.settings.visible).fold(f64::MIN_POSITIVE,|m,t|m.max(t.processed_max));
   for (lane,(idx,t)) in self.traces.iter_mut().enumerate().filter(|(_,t)|t.settings.visible).enumerate(){let d=&t.data;let s=&t.settings;if t.processed.is_empty(){continue;}let scale=if self.normalize{t.processed_max}else{common};let lane_h=if self.stacked{rect.height()/count as f32}else{rect.height()};let geometry=integral_plot::lane_layout(rect,self.stacked,count,lane,s.offset,have_integrals&&(!self.stacked||idx==selected));let amp=geometry.amplitude;
    let y=|v:f64|geometry.baseline-((v/scale*s.gain)as f32*amp).clamp(-1e6,1e6);
    // Min/max per physical column retains narrow peaks at all zoom levels.
    let key=format!("{rect:?}/{range:?}/{:?}/{common}/{}",(s.gain,s.offset,s.p0,s.p1,self.normalize,self.stacked,lane,count,idx,t.revision,have_integrals,selected),ctx.pixels_per_point());
    if t.cache_key!=key {
    t.cache.clear();
    let columns=(rect.width()*ctx.pixels_per_point()).ceil()as usize;let mut previous=None;
    for column in 0..columns{let x0=rect.left()+column as f32/columns as f32*rect.width();let x1=rect.left()+(column+1)as f32/columns as f32*rect.width();let index=|p:f64|((d.left-p)/(d.left-d.right)*d.real.len()as f64).floor()as isize;let lo=index(ppm(x0)).max(0)as usize;let hi=index(ppm(x1)).max(0)as usize;if lo>=d.real.len()||ppm(x1)>d.left||ppm(x0)<d.right{previous=None;continue}let hi=hi.max(lo+1).min(d.real.len());let(mut low,mut high)=(f64::INFINITY,f64::NEG_INFINITY);for i in lo..hi{let v=t.processed[i];low=low.min(v);high=high.max(v);}let a=Pos2::new(x0,y(low));let b=Pos2::new(x0,y(high));let stroke=Stroke::new(1.,COLORS[idx%6]);t.cache.push(egui::Shape::line_segment([a,b],stroke));if let Some(p)=previous{t.cache.push(egui::Shape::line_segment([p,a],stroke));}previous=Some(b);}
    t.cache_key=key;
    }
    painter.with_clip_rect(rect).extend(t.cache.iter().cloned());
    painter.text(Pos2::new(rect.left()+8.,if self.stacked{rect.top()+lane_h*lane as f32+4.}else{rect.top()+lane as f32*18.}),egui::Align2::LEFT_TOP,format!("{} {}",idx+1,d.title),egui::FontId::proportional(12.),COLORS[idx%6]);
   }
   self.paint_integrals(rect,range,&painter,&response);

   if let Some(pos)=response.hover_pos(){let x=ppm(pos.x);painter.line_segment([Pos2::new(pos.x,rect.top()),Pos2::new(pos.x,rect.bottom())],Stroke::new(1.,Color32::from_gray(130)));painter.text(rect.right_top(),egui::Align2::RIGHT_TOP,format!("{x:.5} ppm"),egui::FontId::monospace(12.),COLORS[0]);let scroll=ctx.input(|i|i.smooth_scroll_delta);
    let lane_h=if self.stacked{rect.height()/count as f32}else{rect.height()};
    let lane=if self.stacked{((pos.y-rect.top())/lane_h).floor().clamp(0.,count.saturating_sub(1)as f32)as usize}else{0};
    let hit=self.spectrum_under_pointer(rect,range,pos,have_integrals);
    let hovered=hit.and_then(|idx|self.traces.get(idx).map(|t|(idx,t)));
    let offset=hovered.map_or(0.,|(_,t)|t.settings.offset);
    let integrals=have_integrals&&(!self.stacked||hovered.is_some_and(|(idx,_)|idx==selected));
    let geometry=integral_plot::lane_layout(rect,self.stacked,count,lane,offset,integrals);
    let below=pos.y>geometry.baseline;
    let horizontal=scroll.x;
    if horizontal!=0.{let shift=horizontal as f64/rect.width()as f64*width;self.range=Some([range[0]+shift,range[1]+shift]);}
    if below && scroll.y!=0.{
        let r=self.range.unwrap_or(range);
        let anchor=r[0]-(pos.x-rect.left())as f64/rect.width()as f64*(r[0]-r[1]);
        let factor=(-scroll.y as f64*0.003).exp().clamp(0.5,2.);
        let zoomed=[anchor+(r[0]-anchor)*factor,anchor+(r[1]-anchor)*factor];
        if zoomed[0]-zoomed[1]>1e-8 && zoomed[0]-zoomed[1]<1e6 {self.range=Some(zoomed);}
    }
    if !below && scroll.y!=0. && let Some(idx)=hit{self.zoom_trace(idx,scroll.y);}
   }

   if response.double_clicked(){self.range=None;}
   if response.drag_started()&&self.integrate{self.start=response.interact_pointer_pos().map(|p|ppm(p.x));}
   if response.dragged()&&!self.integrate{let dx=ctx.input(|i|i.pointer.delta().x)as f64/rect.width()as f64*width;self.range=Some([range[0]+dx,range[1]+dx]);}
   if response.drag_stopped()&&self.integrate&& let (Some(a),Some(p))=(self.start.take(),response.interact_pointer_pos()){self.measure_region(a,ppm(p.x));}

   if self.traces.is_empty(){painter.text(rect.center(),egui::Align2::CENTER_CENTER,"Open a Bruker dataset folder to begin\nAdd several spectra for overlay or stacked comparison",egui::FontId::proportional(20.),COLORS[0]);}
  });
        self.stack_window(ctx);
        if self.first_frame {
            self.first_frame = false;
            eprintln!(
                "NMR_FIRST_FRAME_MS={:.2}",
                self.started.elapsed().as_secs_f64() * 1000.
            );
        }
        if self.smoke && self.pending == 0 {
            eprintln!(
                "NMR_READY_MS={:.2} SPECTRA={}",
                self.started.elapsed().as_secs_f64() * 1000.,
                self.traces.len()
            );
            ctx.send_viewport_cmd(egui::ViewportCommand::Close);
        }
    }
}
fn digest(values: &[f64]) -> u64 {
    values.iter().fold(0xcbf29ce484222325u64, |h, v| {
        (h ^ v.to_bits()).wrapping_mul(0x100000001b3)
    })
}
fn main() -> eframe::Result {
    let started = Instant::now();
    let args: Vec<_> = std::env::args().skip(1).collect();
    if args.first().is_some_and(|s| s == "--audit") {
        let root = args.get(1).expect("--audit requires a folder");
        let paths = data::discover(&PathBuf::from(root)).expect("scan failed");
        let mut failures = 0;
        for p in &paths {
            match data::load(p) {
                Ok(s) => println!(
                    "{}",
                    serde_json::json!({"path":p,"n":s.real.len(),"left":s.left,"right":s.right,"first":s.real[0],"last":s.real[s.real.len()-1],"sum":s.real.iter().sum::<f64>(),"left_bits":format!("{:016x}",s.left.to_bits()),"right_bits":format!("{:016x}",s.right.to_bits()),"real_digest":format!("{:016x}",digest(&s.real)),"imag_digest":format!("{:016x}",digest(&s.imag))})
                ),
                Err(e) => {
                    println!("{}", serde_json::json!({"path":p,"error":e}));
                    eprintln!("{}: {e}", p.display());
                    failures += 1;
                }
            }
        }
        eprintln!(
            "AUDIT spectra={} failures={} elapsed_ms={:.2}",
            paths.len(),
            failures,
            started.elapsed().as_secs_f64() * 1000.
        );
        std::process::exit(if failures == 0 { 0 } else { 1 });
    }
    let smoke = args.iter().any(|a| a == "--smoke");
    let paths = args
        .into_iter()
        .filter(|a| a != "--smoke")
        .map(PathBuf::from)
        .collect();
    let opts = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default()
            .with_inner_size([1200., 760.])
            .with_min_inner_size([700., 420.])
            .with_app_id("nmr-viewer"),
        ..Default::default()
    };
    eframe::run_native(
        "NMR Viewer",
        opts,
        Box::new(move |cc| Ok(Box::new(App::new(cc, started, paths, smoke)))),
    )
}
