//! Portable float64 spectra and immutable Bruker source snapshots. No GUI dependencies.
use crate::data::Spectrum;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{
    collections::BTreeMap,
    fs,
    io::{Read, Write},
    path::{Component, Path, PathBuf},
};
pub type Result<T> = std::result::Result<T, String>;
#[derive(Serialize, Deserialize)]
pub struct SpectrumMeta {
    pub version: u32,
    pub name: String,
    pub source_origin: PathBuf,
    pub source_relative: PathBuf,
    pub n: usize,
    pub imaginary: bool,
    pub left: f64,
    pub right: f64,
    pub ft_size: usize,
    pub nc: i32,
    pub phc0: f64,
    pub phc1: f64,
    pub integral_scale: f64,
    pub regions: Vec<[f64; 4]>,
    pub arrays: BTreeMap<String, String>,
    pub source_files: BTreeMap<PathBuf, String>,
}
pub fn fresh_directory(path: &Path) -> Result<()> {
    fs::create_dir_all(path.parent().ok_or("Destination has no parent")?)
        .map_err(|e| e.to_string())?;
    fs::create_dir(path).map_err(|e| format!("{}: {e}. Choose a new destination.", path.display()))
}
pub fn write_new(path: &Path, bytes: &[u8]) -> Result<()> {
    let mut f = fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(path)
        .map_err(|e| e.to_string())?;
    f.write_all(bytes)
        .and_then(|_| f.sync_all())
        .map_err(|e| e.to_string())
}
pub fn digest(path: &Path) -> Result<String> {
    let mut f = fs::File::open(path).map_err(|e| e.to_string())?;
    let mut hash = Sha256::new();
    let mut buffer = [0u8; 65536];
    loop {
        let n = f.read(&mut buffer).map_err(|e| e.to_string())?;
        if n == 0 {
            break;
        }
        hash.update(&buffer[..n]);
    }
    Ok(format!("{:x}", hash.finalize()))
}
pub fn relative(root: &Path, path: &Path) -> Result<PathBuf> {
    if path.as_os_str().is_empty()
        || path
            .components()
            .any(|p| !matches!(p, Component::Normal(_)))
    {
        return Err("Invalid project-relative path".into());
    }
    let p = root.join(path);
    let canonical = fs::canonicalize(&p).map_err(|e| e.to_string())?;
    let base = fs::canonicalize(root).map_err(|e| e.to_string())?;
    if !canonical.starts_with(base) {
        return Err("Project path escapes its directory".into());
    }
    Ok(canonical)
}
fn copy_tree(source: &Path, target: &Path) -> Result<()> {
    fs::create_dir(target).map_err(|e| e.to_string())?;
    for entry in fs::read_dir(source).map_err(|e| e.to_string())? {
        let entry = entry.map_err(|e| e.to_string())?;
        let kind = entry.file_type().map_err(|e| e.to_string())?;
        if kind.is_symlink() {
            return Err(format!(
                "Refusing symlink in source snapshot: {}",
                entry.path().display()
            ));
        }
        if kind.is_dir() {
            copy_tree(&entry.path(), &target.join(entry.file_name()))?;
        } else if kind.is_file() {
            fs::copy(entry.path(), target.join(entry.file_name())).map_err(|e| e.to_string())?;
        } else {
            return Err("Unsupported file in source dataset".into());
        }
    }
    Ok(())
}
pub fn copy_experiment(processing: &Path, target: &Path) -> Result<PathBuf> {
    let experiment = processing
        .parent()
        .and_then(Path::parent)
        .ok_or("Bruker processing folder must be below pdata")?;
    if processing
        .parent()
        .and_then(Path::file_name)
        .is_none_or(|v| v != "pdata")
    {
        return Err("Source is not a Bruker processing number".into());
    }
    fs::create_dir_all(target).map_err(|e| e.to_string())?;
    for entry in fs::read_dir(experiment).map_err(|e| e.to_string())? {
        let entry = entry.map_err(|e| e.to_string())?;
        if entry.file_name() == "pdata" {
            continue;
        }
        let kind = entry.file_type().map_err(|e| e.to_string())?;
        if kind.is_symlink() {
            return Err("Source acquisition contains a symlink".into());
        }
        if kind.is_dir() {
            copy_tree(&entry.path(), &target.join(entry.file_name()))?;
        } else if kind.is_file() {
            fs::copy(entry.path(), target.join(entry.file_name())).map_err(|e| e.to_string())?;
        }
    }
    fs::create_dir(target.join("pdata")).map_err(|e| e.to_string())?;
    let out = target
        .join("pdata")
        .join(processing.file_name().ok_or("Missing processing number")?);
    copy_tree(processing, &out)?;
    Ok(out)
}
fn inventory(root: &Path, dir: &Path, out: &mut BTreeMap<PathBuf, String>) -> Result<()> {
    for e in fs::read_dir(dir).map_err(|e| e.to_string())? {
        let e = e.map_err(|e| e.to_string())?;
        if e.file_type().map_err(|e| e.to_string())?.is_dir() {
            inventory(root, &e.path(), out)?;
        } else {
            out.insert(
                e.path()
                    .strip_prefix(root)
                    .map_err(|e| e.to_string())?
                    .to_owned(),
                digest(&e.path())?,
            );
        }
    }
    Ok(())
}
pub fn source_processing(path: &Path) -> Result<PathBuf> {
    if !path.join("spectrum.json").is_file() {
        return fs::canonicalize(path).map_err(|e| e.to_string());
    }
    let meta: SpectrumMeta =
        serde_json::from_slice(&fs::read(path.join("spectrum.json")).map_err(|e| e.to_string())?)
            .map_err(|e| e.to_string())?;
    for (p, hash) in &meta.source_files {
        if digest(&relative(path, p)?)? != *hash {
            return Err(format!("Source snapshot was changed: {}", p.display()));
        }
    }
    relative(path, &meta.source_relative)
}
pub fn write_array(path: &Path, values: &[f64]) -> Result<()> {
    let mut f = fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(path)
        .map_err(|e| e.to_string())?;
    let mut bytes = Vec::with_capacity(values.len() * 8);
    for v in values {
        bytes.extend(v.to_le_bytes());
    }
    f.write_all(&bytes)
        .and_then(|_| f.sync_all())
        .map_err(|e| e.to_string())
}
fn read_array(path: &Path, n: usize, hash: &str) -> Result<Vec<f64>> {
    if digest(path)? != hash {
        return Err(format!("Spectrum checksum mismatch: {}", path.display()));
    }
    let b = fs::read(path).map_err(|e| e.to_string())?;
    if b.len() != n * 8 {
        return Err("Invalid spectrum array length".into());
    }
    let data: Vec<_> = b
        .chunks_exact(8)
        .map(|v| f64::from_le_bytes(v.try_into().unwrap()))
        .collect();
    if data.iter().any(|v| !v.is_finite()) {
        return Err("Non-finite native spectrum sample".into());
    }
    Ok(data)
}
pub fn write_spectrum(path: &Path, s: &Spectrum) -> Result<()> {
    outside_source(path, s)?;
    fresh_directory(path)?;
    let source = source_processing(&s.path)?;
    let name = crate::data::dataset_name(&source);
    let expno = source
        .parent()
        .and_then(Path::parent)
        .and_then(Path::file_name)
        .ok_or("Missing experiment number")?;
    let relative_exp = PathBuf::from("source").join(&name).join(expno);
    let processing = copy_experiment(&source, &path.join(&relative_exp))?;
    let mut source_files = BTreeMap::new();
    inventory(path, &path.join("source"), &mut source_files)?;
    write_array(&path.join("source_real.f64"), &s.real)?;
    if !s.imag.is_empty() {
        write_array(&path.join("source_imaginary.f64"), &s.imag)?;
    }
    let mut arrays = BTreeMap::new();
    for name in ["source_real.f64", "source_imaginary.f64"] {
        if path.join(name).exists() {
            arrays.insert(name.into(), digest(&path.join(name))?);
        }
    }
    let origin = if s.path.join("spectrum.json").is_file() {
        let previous: SpectrumMeta = serde_json::from_slice(
            &fs::read(s.path.join("spectrum.json")).map_err(|e| e.to_string())?,
        )
        .map_err(|e| e.to_string())?;
        previous.source_origin
    } else {
        source.clone()
    };
    let meta = SpectrumMeta {
        version: 1,
        name: name.clone(),
        source_origin: origin,
        source_relative: processing
            .strip_prefix(path)
            .map_err(|e| e.to_string())?
            .to_owned(),
        n: s.real.len(),
        imaginary: !s.imag.is_empty(),
        left: s.left,
        right: s.right,
        ft_size: s.ft_size,
        nc: s.nc,
        phc0: s.phc0,
        phc1: s.phc1,
        integral_scale: s.integral_scale,
        regions: s.saved_regions.clone(),
        arrays,
        source_files,
    };
    write_new(
        &path.join("spectrum.json"),
        &serde_json::to_vec_pretty(&meta).map_err(|e| e.to_string())?,
    )?;
    write_new(&path.join("label.txt"), name.as_bytes())
}
pub fn load_spectrum(path: &Path) -> Result<Spectrum> {
    let meta: SpectrumMeta =
        serde_json::from_slice(&fs::read(path.join("spectrum.json")).map_err(|e| e.to_string())?)
            .map_err(|e| e.to_string())?;
    if meta.version != 1
        || meta.n == 0
        || meta.n > 16_777_216
        || meta.ft_size == 0
        || meta.ft_size > 16_777_216
        || meta
            .regions
            .iter()
            .any(|r| r[0] < r[1] || r.iter().any(|v| !v.is_finite()))
        || meta.left <= meta.right
        || ![
            meta.left,
            meta.right,
            meta.phc0,
            meta.phc1,
            meta.integral_scale,
        ]
        .iter()
        .all(|v| v.is_finite())
        || !(-1022..=1023).contains(&meta.nc)
    {
        return Err("Invalid native spectrum metadata".into());
    }
    let real = read_array(
        &relative(path, Path::new("source_real.f64"))?,
        meta.n,
        meta.arrays
            .get("source_real.f64")
            .ok_or("Missing real array hash")?,
    )?;
    let imag = if meta.imaginary {
        read_array(
            &relative(path, Path::new("source_imaginary.f64"))?,
            meta.n,
            meta.arrays
                .get("source_imaginary.f64")
                .ok_or("Missing imaginary hash")?,
        )?
    } else {
        vec![]
    };
    let max_amplitude = real
        .iter()
        .chain(&imag)
        .fold(f64::MIN_POSITIVE, |m, v| m.max(v.abs()));
    let title = fs::read_to_string(path.join("label.txt")).unwrap_or(meta.name);
    Ok(Spectrum {
        path: path.to_owned(),
        title,
        real,
        imag,
        left: meta.left,
        right: meta.right,
        ft_size: meta.ft_size,
        nc: meta.nc,
        phc0: meta.phc0,
        phc1: meta.phc1,
        integral_scale: meta.integral_scale,
        saved_regions: meta.regions,
        max_amplitude,
    })
}

/// Resolve a not-yet-created destination without permitting parent traversal or symlink escapes.
pub fn destination(path: &Path) -> Result<PathBuf> {
    let full = if path.is_absolute() {
        path.to_owned()
    } else {
        std::env::current_dir()
            .map_err(|e| e.to_string())?
            .join(path)
    };
    if full.components().any(|c| matches!(c, Component::ParentDir)) {
        return Err("Destination cannot contain '..'".into());
    }
    let mut ancestor = full.as_path();
    let mut tail = Vec::new();
    while !ancestor.exists() {
        tail.push(
            ancestor
                .file_name()
                .ok_or("Invalid destination")?
                .to_owned(),
        );
        ancestor = ancestor.parent().ok_or("Invalid destination")?;
    }
    let mut result = fs::canonicalize(ancestor).map_err(|e| e.to_string())?;
    for part in tail.into_iter().rev() {
        result.push(part);
    }
    Ok(result)
}
pub fn outside_source(path: &Path, spectrum: &Spectrum) -> Result<()> {
    let target = destination(path)?;
    if target.ancestors().any(|p| {
        p.join("acqus").is_file()
            || p.join("fid").is_file()
            || p.join("spectrum.json").is_file()
            || p.file_name().is_some_and(|n| n == "pdata")
    }) {
        return Err(
            "Destination is inside an acquisition or native spectrum; choose a separate folder"
                .into(),
        );
    }
    let source = source_processing(&spectrum.path)?;
    let mut protected = vec![
        source
            .parent()
            .and_then(Path::parent)
            .ok_or("Invalid source")?
            .to_owned(),
    ];
    if spectrum.path.join("spectrum.json").exists() {
        let meta: SpectrumMeta = serde_json::from_slice(
            &fs::read(spectrum.path.join("spectrum.json")).map_err(|e| e.to_string())?,
        )
        .map_err(|e| e.to_string())?;
        if let Some(root) = meta.source_origin.parent().and_then(Path::parent) {
            protected.push(destination(root)?);
        }
        protected.push(fs::canonicalize(&spectrum.path).map_err(|e| e.to_string())?);
    }
    if protected.iter().any(|root| target.starts_with(root)) {
        return Err(
            "Choose a destination outside the original acquisition and source snapshot".into(),
        );
    }
    Ok(())
}
