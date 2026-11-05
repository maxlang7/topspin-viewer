//! Portable projects keep original acquisitions and exact float64 samples separate from edits.
use super::*;
use std::{
    fs,
    path::Path,
    time::{Duration, SystemTime, UNIX_EPOCH},
};

pub fn default_path(folder: &str, name: &str) -> String {
    PathBuf::from(std::env::var_os("HOME").unwrap_or_default())
        .join("Documents")
        .join(folder)
        .join(name)
        .display()
        .to_string()
}
fn stamp() -> String {
    format!(
        "{}-{}",
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_nanos(),
        std::process::id()
    )
}
impl App {
    fn snapshot(&self) -> Session {
        Session {
            version: 2,
            traces: self.traces.iter().map(|t| t.settings.clone()).collect(),
            stacked: self.stacked,
            normalize: self.normalize,
            range: self.range,
            regions: vec![],
            integration_regions: self.regions.iter().map(Region::recipe).collect(),
        }
    }
    fn project_ready(&self) -> Result<(), String> {
        if self.traces.is_empty() {
            return Err("Open a spectrum first".into());
        }
        if self.pending > 0 || self.baseline_open {
            return Err("Finish pending operations and baseline preview first".into());
        }
        Ok(())
    }
    pub(super) fn save_project(&mut self) -> Result<String, String> {
        self.project_ready()?;
        let root = storage::destination(Path::new(&self.project_path))?;
        for t in &self.traces {
            storage::outside_source(&root, &t.data)?;
        }
        let continuing = self.active_project.as_ref() == Some(&root);
        if root.join("project.json").exists() && !continuing {
            return Err(
                "This project already exists. Choose a new folder for a comparison copy.".into(),
            );
        }
        if !continuing {
            storage::fresh_directory(&root)?;
            storage::fresh_directory(&root.join("traces"))?;
            storage::fresh_directory(&root.join("revisions"))?;
        }
        self.refresh_processing();
        let mut session = self.snapshot();
        let id = stamp();
        let mut data = Vec::new();
        for (i, t) in self.traces.iter().enumerate() {
            let p = if continuing && t.data.path.starts_with(root.join("traces")) {
                t.data.path.clone()
            } else {
                let p = root.join("traces").join(format!("{id}-{i}"));
                storage::write_spectrum(&p, &t.data)?;
                p
            };
            session.traces[i].path = p.strip_prefix(&root).map_err(|e| e.to_string())?.to_owned();
            data.push(Arc::new(data::load(&p)?));
        }
        let bytes = serde_json::to_vec_pretty(&session).map_err(|e| e.to_string())?;
        let revision = format!("revisions/{id}.json");
        storage::write_new(&root.join(&revision), &bytes)?;
        if !continuing {
            storage::write_new(&root.join("project.json"),br#"{"format":"nmr-project","version":1,"samples":"little-endian IEEE float64","edits":"revisions selected by HEAD"}"#)?;
        }
        // Commit the revision only after all source copies, checksums and recipe are durable.
        let head = root.join(format!("HEAD-{id}.tmp"));
        storage::write_new(&head, revision.as_bytes())?;
        fs::rename(head, root.join("HEAD")).map_err(|e| e.to_string())?;
        for (t, d) in self.traces.iter_mut().zip(data) {
            t.settings.path = d.path.clone();
            t.data = d;
        }
        self.active_project = Some(root);
        self.saved_project_state =
            serde_json::to_vec(&self.snapshot()).map_err(|e| e.to_string())?;
        self.project_changed_at = None;
        Ok("Project saved. Edits autosave here; original Bruker files remain untouched.".into())
    }
    pub(super) fn open_project(&mut self, ctx: egui::Context) -> Result<String, String> {
        if self.pending > 0 || self.baseline_open {
            return Err("Finish pending operations and baseline preview first".into());
        }
        let root = fs::canonicalize(&self.project_path).map_err(|e| e.to_string())?;
        let manifest: serde_json::Value = serde_json::from_slice(
            &fs::read(root.join("project.json")).map_err(|e| e.to_string())?,
        )
        .map_err(|e| e.to_string())?;
        if manifest["format"] != "nmr-project" || manifest["version"] != 1 {
            return Err("Unsupported project version".into());
        }
        let head = fs::read_to_string(root.join("HEAD")).map_err(|e| e.to_string())?;
        let revision = storage::relative(&root, Path::new(head.trim()))?;
        let mut session: Session =
            serde_json::from_slice(&fs::read(revision).map_err(|e| e.to_string())?)
                .map_err(|e| e.to_string())?;
        for t in &mut session.traces {
            t.path = storage::relative(&root, &t.path)?;
            // Preflight every source before replacing the current comparison.
            storage::source_processing(&t.path)?;
            data::load(&t.path)?;
        }
        let automatic = manifest["automatic"] == true;
        let native_paths = session
            .traces
            .iter()
            .map(|t| t.path.clone())
            .collect::<Vec<_>>();
        self.restore_session(session, ctx)?;
        if automatic {
            for path in native_paths {
                self.automatic_projects
                    .entry(path.clone())
                    .or_insert_with(|| crate::automatic::AutoState::new(root.clone()));
                if let Some(source) = manifest["source"].as_str() {
                    self.source_aliases.insert(PathBuf::from(source), path);
                }
            }
            self.active_project = None;
        } else {
            self.active_project = Some(root);
        }
        self.saved_project_state.clear();
        self.project_changed_at = None;
        Ok("Loading portable project".into())
    }
    pub(super) fn flush_project(&mut self) {
        if let Some(root) = self.active_project.clone()
            && self.pending == 0
            && !self.baseline_open
            && !self.traces.is_empty()
        {
            self.project_path = root.display().to_string();
            if let Err(e) = self.save_project() {
                eprintln!("Project save on exit failed: {e}");
            }
        }
    }
    pub(super) fn autosave_project(&mut self, ctx: &egui::Context) {
        if self.active_project.is_none() || self.pending > 0 || self.baseline_open {
            return;
        }
        let Ok(bytes) = serde_json::to_vec(&self.snapshot()) else {
            return;
        };
        if bytes == self.saved_project_state {
            self.project_changed_at = None;
            return;
        }
        let since = self.project_changed_at.get_or_insert_with(Instant::now);
        if since.elapsed() < Duration::from_secs(1) {
            ctx.request_repaint_after(Duration::from_secs(1));
            return;
        }
        let entered_path = self.project_path.clone();
        self.project_path = self.active_project.as_ref().unwrap().display().to_string();
        let result = self.save_project();
        self.project_path = entered_path;
        if let Err(e) = result {
            self.status = format!("Autosave failed: {e}");
            self.active_project = None;
        }
    }
    pub(super) fn project_controls(&mut self, ui: &mut egui::Ui, ctx: &egui::Context) {
        ui.menu_button("Working project", |ui| {
            ui.label("Opened NMRs autosave automatically.");
            if let Some(t) = self.traces.get(self.selected)
                && let Some(entry) = self.automatic_projects.get(&t.data.path)
            {
                ui.label(entry.root.display().to_string());
            }
            ui.separator();
            ui.label("Optional comparison project folder");
            ui.text_edit_singleline(&mut self.project_path);
            if ui.button("Save project").clicked() {
                self.status = self.save_project().unwrap_or_else(|e| e);
            }
            if ui.button("Open project").clicked() {
                self.status = self.open_project(ctx.clone()).unwrap_or_else(|e| e);
            }
            ui.label("Self-contained spectra, acquisition snapshots and edit history.");
        });
        ui.menu_button("Export", |ui| {
            ui.label("New destination folder");
            ui.text_edit_singleline(&mut self.export_path);
            if ui.button("Bruker / TopSpin").clicked() {
                self.export_selected(ctx.clone(), true);
                ui.close();
            }
            if ui.button("Spectrum and integrals CSV").clicked() {
                self.export(ctx.clone());
                ui.close();
            }
        });
    }
    pub(super) fn export_selected(&mut self, ctx: egui::Context, bruker: bool) {
        if self.baseline_open {
            self.status = "Apply or cancel the baseline preview before exporting".into();
            return;
        }
        let Some(t) = self.traces.get(self.selected) else {
            self.status = "Select a spectrum".into();
            return;
        };
        let data = t.data.clone();
        let settings = t.settings.clone();
        let regions = self
            .regions
            .iter()
            .filter(|r| r.trace == self.selected)
            .map(Region::recipe)
            .collect::<Vec<_>>();
        let path = PathBuf::from(&self.export_path);
        let tx = self.tx.clone();
        self.pending += 1;
        std::thread::spawn(move || {
            let result = if bruker {
                crate::bruker_export::export(&path, &data, &settings, &regions)
            } else {
                crate::bruker_export::csv(&path, &data, &settings, &regions)
            };
            let _ = tx.send(Message::Exported(result));
            ctx.request_repaint();
        });
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn fixture() -> (App, PathBuf, PathBuf) {
        let root = std::env::temp_dir().join(format!("nmr-project-test-{}", stamp()));
        let source = root.join("original/sample/10/pdata/1");
        fs::create_dir_all(&source).unwrap();
        fs::write(source.join("procs"),"##TITLE= Original acquisition\n##$SI= 8\n##$FTSIZE= 8\n##$BYTORDP= 0\n##$DTYPP= 0\n##$NC_proc= -2\n##$SF= 400\n##$SW_p= 3200\n##$OFFSET= 8\n##$PHC0= 12.5\n##$PHC1= -2.5\n##$INTSCL= 1\n##END=\n").unwrap();
        for (name, values) in [
            ("1r", vec![100i32, 110, 135, 160, 125, 100, 90, 85]),
            ("1i", vec![50i32, 35, 20, 10, -20, -30, -25, -10]),
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
            source.ancestors().nth(2).unwrap().join("fid"),
            b"raw acquisition unchanged",
        )
        .unwrap();
        fs::write(source.join("auditp.txt"), b"original provenance\r\n").unwrap();
        let mut app = App::state(Instant::now(), false);
        app.load(source.clone(), None, egui::Context::default());
        let m = app.rx.recv_timeout(Duration::from_secs(3)).unwrap();
        app.handle_message(m, &egui::Context::default());
        app.traces[0].settings.baseline = [1.1, 0.02, 0., 0., 0.];
        app.measure_region(7., 2.);
        app.refresh_processing();
        (app, root, source)
    }
    #[test]
    fn portable_project_survives_missing_master_and_keeps_revision_history() {
        let (mut a, root, source) = fixture();
        let original = storage::digest(&source.join("1r")).unwrap();
        a.project_path = root.join("work.nmrproj").display().to_string();
        a.save_project().unwrap();
        let before = a.traces[0].processed.clone();
        let first = fs::read_to_string(Path::new(&a.project_path).join("HEAD")).unwrap();
        a.traces[0].settings.gain = 3.;
        a.save_project().unwrap();
        assert!(Path::new(&a.project_path).join(first).exists());
        assert_eq!(storage::digest(&source.join("1r")).unwrap(), original);
        fs::rename(root.join("original"), root.join("master-moved")).unwrap();
        let mut b = App::state(Instant::now(), false);
        b.project_path = a.project_path.clone();
        b.open_project(egui::Context::default()).unwrap();
        let m = b.rx.recv_timeout(Duration::from_secs(3)).unwrap();
        b.handle_message(m, &egui::Context::default());
        b.refresh_processing();
        assert_eq!(*before, *b.traces[0].processed);
        assert_eq!(b.traces[0].settings.gain, 3.);
        assert_eq!(a.regions[0].value.to_bits(), b.regions[0].value.to_bits());
        let out = root.join("portable-export");
        crate::bruker_export::export(&out, &b.traces[0].data, &b.traces[0].settings, &b.regions)
            .unwrap();
        assert_eq!(
            fs::read(out.join("sample/10/fid")).unwrap(),
            b"raw acquisition unchanged"
        );
        fs::write(b.traces[0].data.path.join("source_real.f64"), b"bad array").unwrap();
        assert!(data::load(&b.traces[0].data.path).is_err());
    }
    #[test]
    fn bruker_readback_keeps_saved_integrals_phase_and_provenance() {
        let (mut a, root, source) = fixture();
        a.traces[0].settings.p0 = 27.25;
        a.traces[0].settings.p1 = -45.;
        a.regions[0].baseline = true;
        a.regions[0].points = 2;
        a.refresh_processing();
        let hashes =
            ["1r", "1i", "procs", "auditp.txt"].map(|f| storage::digest(&source.join(f)).unwrap());
        let dest = root.join("bruker");
        crate::bruker_export::export(&dest, &a.traces[0].data, &a.traces[0].settings, &a.regions)
            .unwrap();
        let out = dest.join("sample/10/pdata/1");
        let saved = data::load(&out).unwrap();
        assert_eq!(saved.phc0, 39.75);
        assert_eq!(saved.phc1, -47.5);
        assert_eq!(saved.saved_regions.len(), 1);
        assert_eq!(
            fs::read(out.join("auditp.txt")).unwrap(),
            b"original provenance\r\n"
        );
        for (f, h) in ["1r", "1i", "procs", "auditp.txt"].into_iter().zip(hashes) {
            assert_eq!(storage::digest(&source.join(f)).unwrap(), h);
        }
        assert!(
            crate::bruker_export::export(
                &dest,
                &a.traces[0].data,
                &a.traces[0].settings,
                &a.regions
            )
            .is_err()
        );
        assert!(
            crate::bruker_export::csv(
                &source.join("forbidden"),
                &a.traces[0].data,
                &a.traces[0].settings,
                &a.regions
            )
            .is_err()
        );
        assert!(!source.join("forbidden").exists());
        #[cfg(unix)]
        {
            std::os::unix::fs::symlink(&source, root.join("linked-source")).unwrap();
            assert!(
                storage::outside_source(&root.join("linked-source/escape"), &a.traces[0].data)
                    .is_err()
            );
        }
    }
    #[test]
    fn autosave_stays_bound_to_open_project_and_exit_flushes_latest_edits() {
        let (mut a, root, _) = fixture();
        a.project_path = root.join("active.nmrproj").display().to_string();
        a.save_project().unwrap();
        let active = a.active_project.clone().unwrap();
        a.project_path = root
            .join("typed-but-not-saved.nmrproj")
            .display()
            .to_string();
        a.traces[0].settings.p0 = 1.25;
        a.project_changed_at = Some(Instant::now() - Duration::from_secs(2));
        a.autosave_project(&egui::Context::default());
        assert_eq!(a.active_project.as_ref(), Some(&active));
        assert!(!root.join("typed-but-not-saved.nmrproj").exists());
        a.traces[0].settings.p1 = -3.5;
        a.flush_project();
        let head = fs::read_to_string(active.join("HEAD")).unwrap();
        let session: Session =
            serde_json::from_slice(&fs::read(active.join(head)).unwrap()).unwrap();
        assert_eq!(session.traces[0].p0, 1.25);
        assert_eq!(session.traces[0].p1, -3.5);
    }
    #[test]
    fn corrupt_source_snapshot_is_rejected_before_export_or_project_restore() {
        let (mut a, root, _) = fixture();
        a.project_path = root.join("project").display().to_string();
        a.save_project().unwrap();
        let source = storage::source_processing(&a.traces[0].data.path).unwrap();
        fs::write(source.join("1r"), b"changed source").unwrap();
        assert!(storage::source_processing(&a.traces[0].data.path).is_err());
        let mut b = App::state(Instant::now(), false);
        b.project_path = a.project_path.clone();
        assert!(b.open_project(egui::Context::default()).is_err());
        assert!(!root.join("export").exists());
    }

    #[test]
    #[ignore = "reads local laboratory data and creates isolated export artifacts"]
    fn laboratory_exports_roundtrip_and_generate_live_reopen_targets() {
        let base = PathBuf::from(std::env::var_os("HOME").unwrap()).join("Documents/IGB Lab/NMRs");
        let output = Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("artifacts")
            .join(format!("native-project-export-{}", stamp()));
        let mut paths = Vec::new();
        for name in ["MLN1P117", "MLN1P121", "MLN1P55"] {
            let source = base
                .join(if name == "MLN1P55" {
                    "MLN1P55_10/MLN1P55"
                } else {
                    name
                })
                .join("10/pdata/1");
            let mut a = App::state(Instant::now(), false);
            a.load(source.clone(), None, egui::Context::default());
            let m = a.rx.recv_timeout(Duration::from_secs(15)).unwrap();
            a.handle_message(m, &egui::Context::default());
            assert_eq!(a.traces.len(), 1, "{}: {}", source.display(), a.status);
            a.traces[0].settings.p0 = 17.25;
            a.traces[0].settings.p1 = -32.5;
            a.traces[0].settings.baseline = [0.125, 0.00001, 0., 0., 0.];
            let axis = [a.traces[0].data.left, a.traces[0].data.right];
            let width = axis[0] - axis[1];
            a.measure_region(axis[0] - width * 0.25, axis[0] - width * 0.75);
            a.project_path = output.join(format!("{name}.nmrproj")).display().to_string();
            a.save_project().unwrap();
            let target = output.join(format!("{name}-export"));
            let message = crate::bruker_export::export(
                &target,
                &a.traces[0].data,
                &a.traces[0].settings,
                &a.regions,
            )
            .unwrap();
            println!("{message}");
            paths.push(target.join(name).join("10/pdata/1"));
        }
        storage::write_new(
            &output.join("exports.txt"),
            paths
                .iter()
                .map(|p| format!("{}\n", p.display()))
                .collect::<String>()
                .as_bytes(),
        )
        .unwrap();
        println!("EXPORT_MANIFEST {}", output.join("exports.txt").display());
    }
    #[test]
    fn captured_topspin_saved_phase_matches_rust_export_buffers() {
        let root =
            Path::new(env!("CARGO_MANIFEST_DIR")).join("artifacts/reference-datasets/captures");
        if !root.is_dir() {
            return;
        }
        let mut compared = 0;
        for entry in fs::read_dir(root).unwrap() {
            let path = entry.unwrap().path();
            let input = path.join("efp");
            let expected = path.join("pk-manual");
            if !input.join("1r").is_file() || !expected.join("1r").is_file() {
                continue;
            }
            let spectrum = data::load(&input).unwrap();
            let reference = data::load(&expected).unwrap();
            let phase_parameters = fs::read_to_string(expected.join("proc")).unwrap();
            let phase_value = |key: &str| {
                phase_parameters
                    .lines()
                    .find_map(|l| {
                        l.strip_prefix(&format!("##${key}="))
                            .and_then(|v| v.trim().parse::<f64>().ok())
                    })
                    .unwrap()
            };
            let settings = Settings {
                path: input,
                visible: true,
                gain: 1.,
                offset: 0.,
                p0: phase_value("PHC0"),
                p1: phase_value("PHC1"),
                baseline: [0.; 5],
                integral_scale: 1.,
                normalization_frozen: true,
            };
            let (re, im) = crate::bruker_export::saved(&spectrum, &settings).unwrap();
            for (a, b) in re
                .iter()
                .zip(&reference.real)
                .chain(im.iter().zip(&reference.imag))
            {
                assert_eq!(
                    a.to_bits(),
                    b.to_bits(),
                    "{} sample {compared}",
                    path.display()
                );
                compared += 1;
            }
        }
        assert!(compared > 0);
        println!("CAPTURED_SAVED_PHASE bit comparisons={compared}");
    }
}
