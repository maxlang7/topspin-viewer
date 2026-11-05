//! One independently autosaved CSV/native project per opened Bruker processing number.
use super::*;
use sha2::{Digest, Sha256};
use std::{
    fs,
    path::Path,
    thread::JoinHandle,
    time::{Duration, SystemTime, UNIX_EPOCH},
};
static PROJECT_IO: std::sync::Mutex<()> = std::sync::Mutex::new(());
pub(super) struct Imported {
    pub data: Spectrum,
    pub settings: Settings,
    pub regions: Vec<Region>,
    pub root: PathBuf,
    pub source: PathBuf,
}
type SaveJob = (Vec<u8>, JoinHandle<Result<(), String>>);
pub(super) struct AutoState {
    pub root: PathBuf,
    saved: Vec<u8>,
    dirty: Vec<u8>,
    changed: Option<Instant>,
    job: Option<SaveJob>,
}
impl AutoState {
    pub fn new(root: PathBuf) -> Self {
        Self {
            root,
            saved: vec![],
            dirty: vec![],
            changed: None,
            job: None,
        }
    }
    pub fn caption(&self) -> &'static str {
        if self.job.is_some() {
            "Saving…"
        } else if self.changed.is_some() {
            "Autosave pending"
        } else if self.saved.is_empty() {
            "Autosave on"
        } else {
            "Saved"
        }
    }
    fn finish(&mut self) -> Result<(), String> {
        if let Some((bytes, job)) = self.job.take() {
            job.join()
                .map_err(|_| "Autosave worker panicked".to_string())??;
            self.saved = bytes;
        }
        Ok(())
    }
}
fn id() -> String {
    format!(
        "{}-{}",
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_nanos(),
        std::process::id()
    )
}
fn state(settings: Settings, regions: Vec<Region>) -> Session {
    Session {
        version: 2,
        traces: vec![settings],
        stacked: false,
        normalize: true,
        range: None,
        regions: vec![],
        integration_regions: regions,
    }
}
fn root_for(source: &Path, base: &Path) -> Result<PathBuf, String> {
    let hash = format!("{:x}", Sha256::digest(source.to_string_lossy().as_bytes()));
    let name = data::dataset_name(source);
    let exp = source
        .ancestors()
        .nth(2)
        .and_then(Path::file_name)
        .ok_or("Missing experiment number")?
        .to_string_lossy();
    let proc = source
        .file_name()
        .ok_or("Missing processing number")?
        .to_string_lossy();
    let preferred = base.join(format!("{name}.nmrproj"));
    let alternative = base
        .join(format!("{exp}-{proc}-{}", &hash[..16]))
        .join(format!("{name}.nmrproj"));
    if alternative.exists() {
        return Ok(alternative);
    }
    if preferred.exists() {
        let manifest = fs::read(preferred.join("project.json"))
            .ok()
            .and_then(|b| serde_json::from_slice::<serde_json::Value>(&b).ok());
        if manifest.is_some_and(|m| m["source"] != serde_json::json!(source)) {
            return Ok(alternative);
        }
    }
    Ok(preferred)
}
fn commit(
    root: &Path,
    data: &Spectrum,
    settings: &Settings,
    regions: &[Region],
) -> Result<(), String> {
    let revision = id();
    let mut stored_settings = settings.clone();
    stored_settings.path = data
        .path
        .strip_prefix(root)
        .map_err(|e| e.to_string())?
        .to_owned();
    let bytes = serde_json::to_vec_pretty(&state(
        stored_settings,
        regions.iter().map(Region::recipe).collect(),
    ))
    .map_err(|e| e.to_string())?;
    let suffix = format!(".{revision}.tmp");
    crate::bruker_export::csv_files(root, data, settings, regions, &suffix)?;
    let recipe = format!("revisions/{revision}.json");
    storage::write_new(&root.join(&recipe), &bytes)?;
    fs::rename(
        root.join(format!("spectrum.csv{suffix}")),
        root.join(format!("{}.csv", data::dataset_name(&data.path))),
    )
    .map_err(|e| e.to_string())?;
    fs::rename(
        root.join(format!("integrals.csv{suffix}")),
        root.join("integrals.csv"),
    )
    .map_err(|e| e.to_string())?;
    let head = root.join(format!("HEAD-{revision}.tmp"));
    storage::write_new(&head, recipe.as_bytes())?;
    fs::rename(head, root.join("HEAD")).map_err(|e| e.to_string())
}
pub(super) fn import(source: &Path, base: &Path) -> Result<Imported, String> {
    let _lock = PROJECT_IO.lock().map_err(|e| e.to_string())?;
    let source = fs::canonicalize(source).map_err(|e| e.to_string())?;
    let root = storage::destination(&root_for(&source, base)?)?;
    if !root.exists() {
        let original = data::load(&source)?;
        storage::outside_source(&root, &original)?;
        let staging = root.with_file_name(format!(
            "{}.import-{}",
            root.file_name().unwrap_or_default().to_string_lossy(),
            id()
        ));
        storage::outside_source(&staging, &original)?;
        storage::fresh_directory(&staging)?;
        storage::fresh_directory(&staging.join("traces"))?;
        storage::fresh_directory(&staging.join("revisions"))?;
        let path = staging.join("traces/spectrum");
        storage::write_spectrum(&path, &original)?;
        let data = data::load(&path)?;
        let settings = Settings {
            path,
            visible: true,
            gain: 1.,
            offset: 0.,
            p0: 0.,
            p1: 0.,
            baseline: [0.; 5],
            integral_scale: data.integral_scale,
            normalization_frozen: !data.saved_regions.is_empty(),
            checkpoints: vec![],
            reference_shift: 0.,
        };
        let regions = data
            .saved_regions
            .iter()
            .map(|v| Region {
                trace: 0,
                left: v[0],
                right: v[1],
                bias: v[2],
                slope: v[3],
                baseline: false,
                points: 1,
                value: 0.,
                calculation_key: vec![],
                curve: vec![],
                error: None,
            })
            .collect::<Vec<_>>();
        let manifest = serde_json::json!({"format":"nmr-project","version":1,"automatic":true,"source":source,"spectrum_csv":format!("{}.csv",data::dataset_name(&source))});
        storage::write_new(
            &staging.join("project.json"),
            &serde_json::to_vec_pretty(&manifest).map_err(|e| e.to_string())?,
        )?;
        commit(&staging, &data, &settings, &regions)?;
        fs::rename(&staging, &root).map_err(|e| e.to_string())?;
    }
    let manifest: serde_json::Value =
        serde_json::from_slice(&fs::read(root.join("project.json")).map_err(|e| e.to_string())?)
            .map_err(|e| e.to_string())?;
    if manifest["automatic"] != true || manifest["source"] != serde_json::json!(source) {
        return Err("Automatic project belongs to a different source".into());
    }
    let head = fs::read_to_string(root.join("HEAD")).map_err(|e| e.to_string())?;
    let session: Session = serde_json::from_slice(
        &fs::read(storage::relative(&root, Path::new(head.trim()))?).map_err(|e| e.to_string())?,
    )
    .map_err(|e| e.to_string())?;
    if session.version != 2
        || session.traces.len() != 1
        || session.integration_regions.iter().any(|r| {
            r.trace != 0
                || r.points == 0
                || ![r.left, r.right, r.bias, r.slope]
                    .iter()
                    .all(|v| v.is_finite())
        })
    {
        return Err("Invalid automatic project recipe".into());
    }
    let mut settings = session.traces.into_iter().next().unwrap();
    if settings.checkpoints.iter().any(|c| !c.valid())
        || ![
            settings.p0,
            settings.p1,
            settings.gain,
            settings.offset,
            settings.integral_scale,
            settings.reference_shift,
        ]
        .iter()
        .chain(&settings.baseline)
        .all(|v| v.is_finite())
    {
        return Err("Invalid automatic processing settings".into());
    }
    settings.path = storage::relative(&root, &settings.path)?;
    settings.visible = true;
    let data = data::load(&settings.path)?;
    Ok(Imported {
        data,
        settings,
        regions: session.integration_regions,
        root,
        source,
    })
}
impl App {
    fn auto_snapshot(&self, index: usize) -> (Settings, Vec<Region>, Vec<u8>) {
        let settings = self.traces[index].settings.clone();
        let regions = self
            .regions
            .iter()
            .filter(|r| r.trace == index)
            .map(|r| {
                let mut r = r.recipe();
                r.trace = 0;
                r
            })
            .collect::<Vec<_>>();
        let bytes = serde_json::to_vec(&state(
            settings.clone(),
            regions.iter().map(Region::recipe).collect(),
        ))
        .unwrap_or_default();
        (settings, regions, bytes)
    }
    pub(super) fn autosave_automatic(&mut self, ctx: &egui::Context) {
        if self.has_processing_preview() {
            return;
        }
        for index in 0..self.traces.len() {
            let path = self.traces[index].data.path.clone();
            if !self.automatic_projects.contains_key(&path) {
                continue;
            }
            let (settings, regions, bytes) = self.auto_snapshot(index);
            let entry = self.automatic_projects.get_mut(&path).unwrap();
            if entry.job.as_ref().is_some_and(|(_, j)| j.is_finished())
                && let Err(e) = entry.finish()
            {
                self.status = format!("Autosave failed: {e}");
                entry.changed = Some(Instant::now());
            }
            if bytes == entry.saved {
                entry.changed = None;
                continue;
            }
            if bytes != entry.dirty {
                entry.dirty = bytes.clone();
                entry.changed = Some(Instant::now());
            }
            if entry.job.is_some()
                || entry
                    .changed
                    .is_none_or(|v| v.elapsed() < Duration::from_secs(1))
            {
                ctx.request_repaint_after(Duration::from_secs(1));
                continue;
            }
            let root = entry.root.clone();
            let data = self.traces[index].data.clone();
            let ctx = ctx.clone();
            entry.job = Some((
                bytes,
                std::thread::spawn(move || {
                    let result = (|| {
                        let _lock = PROJECT_IO.lock().map_err(|e| e.to_string())?;
                        commit(&root, &data, &settings, &regions)
                    })();
                    ctx.request_repaint();
                    result
                }),
            ));
        }
    }
    pub(super) fn flush_automatic(&mut self) {
        if self.has_processing_preview() {
            return;
        }
        self.refresh_processing();
        for index in 0..self.traces.len() {
            let path = self.traces[index].data.path.clone();
            if !self.automatic_projects.contains_key(&path) {
                continue;
            }
            let (settings, regions, bytes) = self.auto_snapshot(index);
            let entry = self.automatic_projects.get_mut(&path).unwrap();
            let result = entry.finish().and_then(|_| {
                if bytes == entry.saved {
                    return Ok(());
                }
                let _lock = PROJECT_IO.lock().map_err(|e| e.to_string())?;
                commit(&entry.root, &self.traces[index].data, &settings, &regions)?;
                entry.saved = bytes;
                Ok(())
            });
            if let Err(e) = result {
                self.status = format!("Autosave failed: {e}");
                eprintln!("{}", self.status);
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn fixture(root: &Path, name: &str) -> PathBuf {
        let source = root.join(name).join("10/pdata/1");
        fs::create_dir_all(&source).unwrap();
        fs::write(source.join("procs"),"##$SI= 8\n##$FTSIZE= 8\n##$BYTORDP= 0\n##$DTYPP= 0\n##$NC_proc= -2\n##$SF= 400\n##$SW_p= 3200\n##$OFFSET= 8\n##$PHC0= 12.5\n##$PHC1= -2.5\n##$INTSCL= 2\n").unwrap();
        for (name, values) in [
            ("1r", [100i32, 120, 130, 150, 140, 110, 90, 85]),
            ("1i", [15i32, 10, 25, -15, -20, 15, 10, 5]),
        ] {
            fs::write(
                source.join(name),
                values
                    .into_iter()
                    .flat_map(i32::to_le_bytes)
                    .collect::<Vec<_>>(),
            )
            .unwrap();
        }
        fs::write(
            source.join("intrng"),
            "A 1.0 #regions in PPM\n 7 2 -0.5 0.25\n",
        )
        .unwrap();
        source
    }
    fn receive(a: &mut App) {
        let message = a.rx.recv_timeout(Duration::from_secs(5)).unwrap();
        a.handle_message(message, &egui::Context::default());
        a.refresh_processing();
    }
    fn app(root: &Path) -> App {
        let mut a = App::state(Instant::now(), false);
        a.automatic_root = Some(root.join("working"));
        a
    }
    #[test]
    fn opening_creates_named_csv_project_and_reopening_restores_all_scientific_edits() {
        let root = std::env::temp_dir().join(format!("nmr-auto-{}", id()));
        let source = fixture(&root, "sample");
        let hashes =
            ["1r", "1i", "procs", "intrng"].map(|f| storage::digest(&source.join(f)).unwrap());
        let mut a = app(&root);
        a.load(source.clone(), None, egui::Context::default());
        receive(&mut a);
        assert_eq!(a.traces.len(), 1, "{}", a.status);
        assert_eq!(a.regions.len(), 1);
        assert_eq!(a.traces[0].data.phc0, 12.5);
        assert_eq!(a.traces[0].data.phc1, -2.5);
        assert_eq!(a.traces[0].settings.integral_scale, 8.);
        assert_eq!(a.regions[0].bias, 0.125);
        assert_eq!(a.regions[0].slope, -0.0625);
        let project = a.automatic_projects.values().next().unwrap().root.clone();
        assert_eq!(project.file_name().unwrap(), "sample.nmrproj");
        assert!(project.join("sample.csv").is_file());
        assert!(project.join("integrals.csv").is_file());
        a.traces[0].settings.p0 = 17.25;
        a.traces[0].settings.p1 = -32.5;
        a.traces[0].settings.baseline = [0.125, 0.01, 0., 0., 0.];
        a.regions[0].baseline = true;
        a.regions[0].points = 2;
        a.measure_region(6., 3.);
        a.refresh_processing();
        let expected = a.traces[0].processed.clone();
        let areas = a
            .regions
            .iter()
            .map(|r| r.value.to_bits())
            .collect::<Vec<_>>();
        a.create_checkpoint("Processed");
        a.flush_automatic();
        let csv = fs::read_to_string(project.join("sample.csv")).unwrap();
        for (row, v) in csv.lines().skip(1).zip(expected.iter()) {
            assert_eq!(
                row.split(',')
                    .nth(4)
                    .unwrap()
                    .parse::<f64>()
                    .unwrap()
                    .to_bits(),
                v.to_bits()
            );
        }
        let mut b = app(&root);
        b.load(source.clone(), None, egui::Context::default());
        receive(&mut b);
        assert_eq!(b.traces[0].settings.p0, 17.25);
        assert_eq!(b.traces[0].settings.p1, -32.5);
        assert_eq!(b.traces[0].settings.checkpoints[0].name, "Processed");
        b.sync_history(true);
        b.traces[0].settings.p0 = 23.;
        b.sync_history(true);
        b.restore_checkpoint(0);
        assert_eq!(b.traces[0].settings.p0, 17.25);
        b.undo_redo(false);
        assert_eq!(b.traces[0].settings.p0, 23.);
        b.undo_redo(true);
        assert_eq!(b.traces[0].settings.p0, 17.25);
        assert_eq!(*b.traces[0].processed, *expected);
        assert_eq!(
            b.regions
                .iter()
                .map(|r| r.value.to_bits())
                .collect::<Vec<_>>(),
            areas
        );
        assert!(b.regions[0].baseline);
        assert_eq!(b.regions[0].points, 2);
        // The original sidebar path selects the native trace without duplicating it.
        b.sidebar_open(source.clone(), false, egui::Context::default());
        assert_eq!(b.pending, 0);
        assert_eq!(b.traces.len(), 1);
        b.close_trace(0);
        b.sidebar_open(source.clone(), false, egui::Context::default());
        receive(&mut b);
        assert_eq!(b.traces[0].settings.p0, 17.25);
        let output = root.join("bruker-export");
        crate::bruker_export::export(
            &output,
            &b.traces[0].data,
            &b.traces[0].settings,
            &b.regions,
        )
        .unwrap();
        let exported = data::load(&output.join("sample/10/pdata/1")).unwrap();
        assert_eq!(exported.phc0, 29.75);
        assert_eq!(exported.phc1, -35.);
        assert_eq!(exported.saved_regions.len(), 2);
        let applied = b.traces[0].settings.baseline;
        b.begin_baseline();
        b.traces[0].settings.baseline = [999., 0., 0., 0., 0.];
        b.traces[0].settings.p0 = 22.;
        b.close_trace(0);
        b.load(source.clone(), None, egui::Context::default());
        receive(&mut b);
        assert_eq!(b.traces[0].settings.baseline, applied);
        assert_eq!(b.traces[0].settings.p0, 22.);
        b.calibrate_integral(0, 3.).unwrap();
        b.delete_integral(1);
        b.flush_automatic();
        let mut restored = app(&root);
        restored.load(source.clone(), None, egui::Context::default());
        receive(&mut restored);
        assert_eq!(restored.regions.len(), 1);
        assert!(restored.traces[0].settings.normalization_frozen);
        assert_eq!(
            restored.traces[0].settings.integral_scale.to_bits(),
            b.traces[0].settings.integral_scale.to_bits()
        );
        assert!(
            (restored.regions[0].value * restored.traces[0].settings.integral_scale - 3.).abs()
                < 1e-12
        );
        let area = restored.regions[0].value.to_bits();
        let source_bounds = [restored.regions[0].left, restored.regions[0].right];
        restored.apply_reference(7., 7.26).unwrap();
        restored.refresh_processing();
        assert_eq!(restored.regions[0].value.to_bits(), area);
        assert_eq!(
            [restored.regions[0].left, restored.regions[0].right],
            source_bounds
        );
        let shift = restored.traces[0].settings.reference_shift;
        restored.flush_automatic();
        let mut referenced = app(&root);
        referenced.load(source.clone(), None, egui::Context::default());
        receive(&mut referenced);
        assert_eq!(
            referenced.traces[0].settings.reference_shift.to_bits(),
            shift.to_bits()
        );
        let export = root.join("referenced-export");
        crate::bruker_export::export(
            &export,
            &referenced.traces[0].data,
            &referenced.traces[0].settings,
            &referenced.regions,
        )
        .unwrap();
        let reopened = data::load(&export.join("sample/10/pdata/1")).unwrap();
        assert_eq!(reopened.left, referenced.traces[0].left());
        assert_eq!(reopened.saved_regions[0][0], source_bounds[0] + shift);
        let csv = root.join("referenced-csv");
        crate::bruker_export::csv(
            &csv,
            &referenced.traces[0].data,
            &referenced.traces[0].settings,
            &referenced.regions,
        )
        .unwrap();
        let csv = fs::read_to_string(csv.join("spectrum.csv")).unwrap();
        let ppm = csv
            .lines()
            .nth(1)
            .unwrap()
            .split(',')
            .nth(1)
            .unwrap()
            .parse::<f64>()
            .unwrap();
        assert_eq!(ppm.to_bits(), referenced.traces[0].left().to_bits());
        for (f, hash) in ["1r", "1i", "procs", "intrng"].into_iter().zip(hashes) {
            assert_eq!(storage::digest(&source.join(f)).unwrap(), hash);
        }
    }
    #[test]
    fn equal_dataset_names_from_different_sources_never_share_edits() {
        let root = std::env::temp_dir().join(format!("nmr-auto-collision-{}", id()));
        let first = fixture(&root.join("archive-a"), "sample");
        let second = fixture(&root.join("archive-b"), "sample");
        let base = root.join("working");
        let mut a = import(&first, &base).unwrap();
        a.settings.p0 = 11.;
        commit(&a.root, &a.data, &a.settings, &a.regions).unwrap();
        let mut b = import(&second, &base).unwrap();
        b.settings.p0 = -21.;
        commit(&b.root, &b.data, &b.settings, &b.regions).unwrap();
        assert_ne!(a.root, b.root);
        assert_eq!(a.root, base.join("sample.nmrproj"));
        assert_eq!(b.root.file_name().unwrap(), "sample.nmrproj");
        assert_eq!(import(&first, &base).unwrap().settings.p0, 11.);
        assert_eq!(import(&second, &base).unwrap().settings.p0, -21.);
    }
    #[test]
    fn background_autosave_keeps_stacked_spectra_independent() {
        let root = std::env::temp_dir().join(format!("nmr-auto-stack-{}", id()));
        let first = fixture(&root, "one");
        let second = fixture(&root, "two");
        let mut a = app(&root);
        a.load(first.clone(), None, egui::Context::default());
        receive(&mut a);
        a.sidebar_open(second.clone(), true, egui::Context::default());
        receive(&mut a);
        a.traces[0].settings.p0 = 11.;
        a.traces[1].settings.p0 = -21.;
        a.autosave_automatic(&egui::Context::default());
        for entry in a.automatic_projects.values_mut() {
            entry.changed = Some(Instant::now() - Duration::from_secs(2));
        }
        a.autosave_automatic(&egui::Context::default());
        assert!(a.automatic_projects.values().all(|s| s.job.is_some()));
        for entry in a.automatic_projects.values_mut() {
            entry.finish().unwrap();
        }
        a.move_trace(0, 1);
        a.flush_automatic();
        assert_eq!(
            import(&first, &root.join("working")).unwrap().settings.p0,
            11.
        );
        assert_eq!(
            import(&second, &root.join("working")).unwrap().settings.p0,
            -21.
        );
        let first_project =
            root_for(&fs::canonicalize(first).unwrap(), &root.join("working")).unwrap();
        let mut b = app(&root);
        b.project_path = first_project.display().to_string();
        b.open_project(egui::Context::default()).unwrap();
        receive(&mut b);
        assert!(b.active_project.is_none());
        assert_eq!(b.automatic_projects.len(), 1);
        b.traces[0].settings.p1 = 42.;
        b.flush_automatic();
        assert_eq!(
            import(&fixture(&root, "one"), &root.join("working"))
                .unwrap()
                .settings
                .p1,
            42.
        );
    }
}
