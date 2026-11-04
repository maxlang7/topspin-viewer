mod data;
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
}
struct Trace {
    data: Arc<Spectrum>,
    settings: Settings,
    cache_key: String,
    cache: Vec<egui::Shape>,
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
}
enum Message {
    Exported(Result<String, String>),
    Loaded(Result<Spectrum, String>, Option<Settings>),
    Found(Result<Vec<PathBuf>, String>),
}
struct App {
    nav: String,
    data_tab: usize,
    command: String,
    traces: Vec<Trace>,
    found: Vec<PathBuf>,
    selected: usize,
    path: String,
    filter: String,
    status: String,
    stacked: bool,
    normalize: bool,
    range: Option<[f64; 2]>,
    tx: mpsc::Sender<Message>,
    rx: mpsc::Receiver<Message>,
    pending: usize,
    integrate: bool,
    start: Option<f64>,
    regions: Vec<(usize, f64, f64, f64)>,
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
        let (tx, rx) = mpsc::channel();
        let mut s = Self {
            nav: "Process".into(),
            data_tab: 0,
            command: String::new(),
            traces: vec![],
            found: vec![],
            selected: 0,
            path: String::new(),
            filter: String::new(),
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
            region_a: 0.,
            region_b: 1.,
            peaks: vec![],
            peak_threshold: 0.05,
            session_path: "comparison.nmr.json".into(),
            started,
            first_frame: true,
            smoke,
        };
        for p in paths {
            s.open(p, cc.egui_ctx.clone());
        }
        s
    }
    fn load(&mut self, p: PathBuf, settings: Option<Settings>, ctx: egui::Context) {
        if self.traces.iter().any(|t| t.data.path == p) {
            return;
        }
        self.pending += 1;
        let tx = self.tx.clone();
        std::thread::spawn(move || {
            let result = data::load(&p);
            let _ = tx.send(Message::Loaded(result, settings));
            ctx.request_repaint();
        });
    }
    fn open(&mut self, p: PathBuf, ctx: egui::Context) {
        self.pending += 1;
        let tx = self.tx.clone();
        std::thread::spawn(move || {
            let _ = tx.send(Message::Found(data::discover(&p)));
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
                let f = std::fs::OpenOptions::new()
                    .write(true)
                    .create_new(true)
                    .open(&p)
                    .map_err(|e| e.to_string())?;
                let mut w = std::io::BufWriter::new(f);
                writeln!(w, "ppm,real,imaginary,phase_preview").map_err(|e| e.to_string())?;
                for i in 0..data.real.len() {
                    writeln!(
                        w,
                        "{:.12},{:.12},{:.12},{:.12}",
                        data.ppm(i),
                        data.real[i],
                        data.imag.get(i).copied().unwrap_or(0.),
                        data.phased(i, settings.p0, settings.p1)
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
        let s = Session {
            version: 1,
            traces: self.traces.iter().map(|t| t.settings.clone()).collect(),
            stacked: self.stacked,
            normalize: self.normalize,
            range: self.range,
            regions: self.regions.clone(),
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
        if self.pending > 0 {
            return Err("Wait for pending loads and exports before restoring a comparison".into());
        }
        let bytes = std::fs::read(&self.session_path).map_err(|e| e.to_string())?;
        let s: Session = serde_json::from_slice(&bytes).map_err(|e| e.to_string())?;
        if s.version != 1
            || s.traces.len() > 128
            || s.range
                .is_some_and(|r| !r[0].is_finite() || !r[1].is_finite() || r[0] <= r[1])
            || s.traces
                .iter()
                .any(|t| ![t.gain, t.offset, t.p0, t.p1].iter().all(|n| n.is_finite()))
        {
            return Err("Invalid comparison session".into());
        }
        self.stacked = s.stacked;
        self.normalize = s.normalize;
        self.range = s.range;
        self.regions = s.regions;
        self.traces.clear();
        self.selected = 0;
        let tx = self.tx.clone();
        self.pending += s.traces.len();
        std::thread::spawn(move || {
            for t in s.traces {
                let _ = tx.send(Message::Loaded(data::load(&t.path), Some(t)));
                ctx.request_repaint();
            }
        });
        Ok("Loading comparison".into())
    }
}
impl eframe::App for App {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        while let Ok(m) = self.rx.try_recv() {
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
                        self.found = paths;
                    }
                }
                Message::Loaded(Ok(s), settings) => {
                    if !self.traces.iter().any(|t| t.data.path == s.path) {
                        let settings = settings.unwrap_or(Settings {
                            path: s.path.clone(),
                            visible: true,
                            gain: 1.,
                            offset: 0.,
                            p0: 0.,
                            p1: 0.,
                        });
                        self.traces.push(Trace {
                            data: Arc::new(s),
                            settings,
                            cache_key: String::new(),
                            cache: vec![],
                        });
                        self.selected = self.traces.len() - 1;
                        self.status = format!("{} spectra loaded", self.traces.len());
                    }
                }
                Message::Loaded(Err(e), _) | Message::Found(Err(e)) => {
                    self.status = e;
                    self.regions.clear();
                }
            }
        }
        for f in ctx.input(|i| i.raw.dropped_files.clone()) {
            if let Some(p) = f.path {
                self.open(p, ctx.clone());
            }
        }
        self.topspin_chrome(ctx);
        egui::SidePanel::left("browser")
            .default_width(245.)
            .min_width(180.)
            .resizable(true)
            .show(ctx, |ui| {
                ui.horizontal(|ui| {
                    ui.selectable_label(true, "☷  Data")
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
                ui.text_edit_singleline(&mut self.filter);
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
                        self.load(p, None, ctx.clone());
                    }
                }
                let filter = self.filter.to_lowercase();
                let mut add = None;
                egui::ScrollArea::vertical().show(ui, |ui| {
                    for p in &self.found {
                        let label = p.display().to_string();
                        let short = format!(
                            "▸ {} / {}",
                            p.ancestors()
                                .nth(3)
                                .and_then(|p| p.file_name())
                                .unwrap_or_default()
                                .to_string_lossy(),
                            p.file_name().unwrap_or_default().to_string_lossy()
                        );
                        if label.to_lowercase().contains(&filter)
                            && ui
                                .selectable_label(false, &short)
                                .on_hover_text(&label)
                                .clicked()
                        {
                            add = Some(p.clone());
                        }
                    }
                });
                if let Some(p) = add {
                    self.load(p, None, ctx.clone());
                }
            });
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
                        egui::Slider::new(&mut t.settings.gain, 0.01..=10.)
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
                ui.collapsing("Integral measurements", |ui| {
                    ui.horizontal(|ui| {
                        ui.add(egui::DragValue::new(&mut self.region_a).speed(0.01));
                        ui.add(egui::DragValue::new(&mut self.region_b).speed(0.01));
                    });
                    if ui.button("Measure ppm range").clicked()
                        && let Some(t) = self.traces.get(self.selected)
                    {
                        self.regions.push((
                            self.selected,
                            self.region_a,
                            self.region_b,
                            t.data.integral(
                                self.region_a,
                                self.region_b,
                                t.settings.p0,
                                t.settings.p1,
                            ),
                        ));
                    }
                    for (i, a, b, v) in &self.regions {
                        ui.label(format!("#{} {a:.3}–{b:.3}: {v:.5}", i + 1));
                    }
                    if ui.button("Clear measurements").clicked() {
                        self.regions.clear();
                    }
                    ui.label("Signed sample sum × ppm step; no baseline correction.");
                });
                ui.collapsing("Peak picking",|ui|{
                ui.add(egui::Slider::new(&mut self.peak_threshold,0.001..=1.).logarithmic(true).text("Threshold"));
                if ui.button("Pick selected spectrum").clicked() && let Some(t)=self.traces.get(self.selected) {self.peaks=t.data.peaks(self.peak_threshold,t.settings.p0,t.settings.p1);}
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
        self.spectrum_tabs(ctx);
        egui::CentralPanel::default().show(ctx,|ui|{
   if self.data_tab==1 {ui.heading("INTEGRALS");egui::Grid::new("integral-table").striped(true).show(ui,|ui|{for h in ["Spectrum","From / ppm","To / ppm","Measured area"]{ui.strong(h);}ui.end_row();for(i,a,b,v)in &self.regions{ui.label(format!("{}",i+1));ui.label(format!("{a:.5}"));ui.label(format!("{b:.5}"));ui.label(format!("{v:.6}"));ui.end_row();}});return;}

   let (response,painter)=ui.allocate_painter(ui.available_size(),egui::Sense::click_and_drag());let rect=response.rect.shrink2(egui::vec2(14.,28.));if rect.width()<10.||rect.height()<10.{return}
   painter.rect_filled(response.rect,0.,Color32::from_rgb(29,32,33));let range=self.range.unwrap_or_else(||self.full_range());let width=range[0]-range[1];let ppm=|x:f32|range[0]-(x-rect.left())as f64/rect.width()as f64*width;
   for j in 0..=10{let x=rect.left()+rect.width()*j as f32/10.;painter.line_segment([Pos2::new(x,rect.top()),Pos2::new(x,rect.bottom())],Stroke::new(1.,Color32::from_rgb(50,48,47)));painter.text(Pos2::new(x,rect.bottom()+8.),egui::Align2::CENTER_TOP,format!("{:.2}",ppm(x)),egui::FontId::monospace(12.),Color32::from_rgb(189,174,147));}
   let count=self.traces.iter().filter(|t|t.settings.visible).count().max(1);let common=self.traces.iter().filter(|t|t.settings.visible).fold(f64::MIN_POSITIVE,|m,t|m.max(t.data.max_amplitude));
   for (lane,(idx,t)) in self.traces.iter_mut().enumerate().filter(|(_,t)|t.settings.visible).enumerate(){let d=&t.data;let s=&t.settings;let scale=if self.normalize{d.max_amplitude}else{common};let lane_h=if self.stacked{rect.height()/count as f32}else{rect.height()};let base=if self.stacked{rect.top()+lane_h*(lane as f32+0.85)}else{rect.bottom()-lane_h*0.12};let amp=lane_h*0.7;
    let y=|v:f64|base-((v/scale*s.gain+s.offset)as f32*amp).clamp(-1e6,1e6);
    // Min/max per physical column retains narrow peaks at all zoom levels.
    let key=format!("{rect:?}/{range:?}/{:?}/{common}/{}",(s.gain,s.offset,s.p0,s.p1,self.normalize,self.stacked,lane,count,idx),ctx.pixels_per_point());
    if t.cache_key!=key {
    t.cache.clear();
    let columns=(rect.width()*ctx.pixels_per_point()).ceil()as usize;let mut previous=None;
    for column in 0..columns{let x0=rect.left()+column as f32/columns as f32*rect.width();let x1=rect.left()+(column+1)as f32/columns as f32*rect.width();let index=|p:f64|((d.left-p)/(d.left-d.right)*d.real.len()as f64).floor()as isize;let lo=index(ppm(x0)).max(0)as usize;let hi=index(ppm(x1)).max(0)as usize;if lo>=d.real.len()||ppm(x1)>d.left||ppm(x0)<d.right{previous=None;continue}let hi=hi.max(lo+1).min(d.real.len());let(mut low,mut high)=(f64::INFINITY,f64::NEG_INFINITY);for i in lo..hi{let v=d.phased(i,s.p0,s.p1);low=low.min(v);high=high.max(v);}let a=Pos2::new(x0,y(low));let b=Pos2::new(x0,y(high));let stroke=Stroke::new(1.,COLORS[idx%6]);t.cache.push(egui::Shape::line_segment([a,b],stroke));if let Some(p)=previous{t.cache.push(egui::Shape::line_segment([p,a],stroke));}previous=Some(b);}
    t.cache_key=key;
    }
    painter.with_clip_rect(rect).extend(t.cache.iter().cloned());
    painter.text(Pos2::new(rect.left()+8.,if self.stacked{rect.top()+lane_h*lane as f32+4.}else{rect.top()+idx as f32*18.}),egui::Align2::LEFT_TOP,format!("{} {}",idx+1,d.title),egui::FontId::proportional(12.),COLORS[idx%6]);
   }
   for (_,a,b,_) in self.regions.iter().filter(|(i,_,_,_)|*i==self.selected){let px=|p:f64|rect.left()+((range[0]-p)/width)as f32*rect.width();let r=egui::Rect::from_x_y_ranges(px(a.max(*b))..=px(a.min(*b)),rect.top()..=rect.bottom());painter.with_clip_rect(rect).rect_filled(r,0.,Color32::from_rgba_unmultiplied(250,189,47,22));}
   if let Some(pos)=response.hover_pos(){let x=ppm(pos.x);painter.line_segment([Pos2::new(pos.x,rect.top()),Pos2::new(pos.x,rect.bottom())],Stroke::new(1.,Color32::from_gray(130)));painter.text(rect.right_top(),egui::Align2::RIGHT_TOP,format!("{x:.5} ppm"),egui::FontId::monospace(12.),COLORS[0]);let scroll=ctx.input(|i|i.smooth_scroll_delta.y);if scroll!=0.{let f=(-scroll as f64*0.002).exp().clamp(0.5,2.);let r=[x+(range[0]-x)*f,x+(range[1]-x)*f];if r[0]-r[1]>1e-8 && r[0]-r[1]<1e6{self.range=Some(r);}}}
   if response.double_clicked(){self.range=None;}
   if response.drag_started()&&self.integrate{self.start=response.interact_pointer_pos().map(|p|ppm(p.x));}
   if response.dragged()&&!self.integrate{let dx=ctx.input(|i|i.pointer.delta().x)as f64/rect.width()as f64*width;self.range=Some([range[0]+dx,range[1]+dx]);}
   if response.drag_stopped()&&self.integrate&& let (Some(a),Some(p),Some(t))=(self.start.take(),response.interact_pointer_pos(),self.traces.get(self.selected)){let b=ppm(p.x);self.regions.push((self.selected,a,b,t.data.integral(a,b,t.settings.p0,t.settings.p1)));}
   if self.traces.is_empty(){painter.text(rect.center(),egui::Align2::CENTER_CENTER,"Open a Bruker dataset folder to begin\nAdd several spectra for overlay or stacked comparison",egui::FontId::proportional(20.),COLORS[0]);}
  });
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
