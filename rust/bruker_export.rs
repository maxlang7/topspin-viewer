//! Standard Bruker export. Originals are templates only; all mutations target a fresh copy.
use super::*;
use std::{collections::BTreeMap, fs, io::Write, path::Path};
unsafe extern "C" {
    fn nmr_save_phase(
        re: *const f64,
        im: *const f64,
        n: i32,
        p0: f64,
        p1: f64,
        nc: i32,
        out: *mut f64,
    ) -> i32;
}
fn phase_complex(s: &Spectrum, settings: &Settings) -> (Vec<f64>, Vec<f64>) {
    let re = processing::phase(&s.real, &s.imag, settings.p0, settings.p1, s.ft_size);
    let im = if s.imag.is_empty() {
        vec![]
    } else {
        let negative: Vec<_> = s.real.iter().map(|v| -v).collect();
        processing::phase(&s.imag, &negative, settings.p0, settings.p1, s.ft_size)
    };
    (re, im)
}
fn integers(values: &[f64], nc: i32) -> Result<Vec<u8>, String> {
    let inverse = 2f64.powi(-nc);
    let mut out = Vec::with_capacity(values.len() * 4);
    for v in values {
        let raw = v * inverse;
        if !raw.is_finite() || !(-2147483648.0..2147483648.0).contains(&raw) {
            return Err(
                "Correction exceeds Bruker int32 range at this NC_proc; export cancelled".into(),
            );
        }
        out.extend((raw.trunc() as i32).to_le_bytes());
    }
    Ok(out)
}
pub(super) fn saved(s: &Spectrum, settings: &Settings) -> Result<(Vec<f64>, Vec<f64>), String> {
    let n = s.real.len();
    let (mut re, im) = if settings.p0 == 0. && settings.p1 == 0. {
        (s.real.clone(), s.imag.clone())
    } else {
        if s.imag.len() != n || n < 2 || n > i32::MAX as usize {
            return Err("Phase export needs a complete imaginary spectrum".into());
        }
        let mut output = vec![0.; n * 2];
        // Inputs and output have validated lengths. The C adapter checks conversion overflow.
        let code = unsafe {
            nmr_save_phase(
                s.real.as_ptr(),
                s.imag.as_ptr(),
                n as i32,
                settings.p0,
                settings.p1,
                s.nc,
                output.as_mut_ptr(),
            )
        };
        if code != 0 {
            return Err(format!(
                "Saved phase correction failed ({code}); no export written"
            ));
        }
        (output[..n].to_vec(), output[n..].to_vec())
    };
    re = processing::polynomial(&re, settings.baseline, 0, n)?;
    // Disk quantization is part of the save operation, before measuring saved integrals.
    let bytes = integers(&re, s.nc)?;
    let scale = 2f64.powi(s.nc);
    let re = bytes
        .chunks_exact(4)
        .map(|b| i32::from_le_bytes(b.try_into().unwrap()) as f64 * scale)
        .collect();
    Ok((re, im))
}
fn scalar(v: f64) -> String {
    format!("{v:.14e}")
}
fn parameters(path: &Path, updates: &BTreeMap<&str, String>) -> Result<(), String> {
    let bytes = fs::read(path).map_err(|e| e.to_string())?;
    let text: String = bytes.iter().map(|b| char::from(*b)).collect();
    let mut seen = std::collections::HashSet::new();
    let mut out = String::new();
    for line in text.lines() {
        if line.starts_with("##END=") {
            continue;
        }
        if let Some((name, _)) = line.strip_prefix("##$").and_then(|s| s.split_once('='))
            && let Some(value) = updates.get(name)
        {
            out.push_str(&format!("##${name}= {value}\n"));
            seen.insert(name.to_owned());
        } else {
            out.push_str(line);
            out.push('\n');
        }
    }
    for (key, value) in updates {
        if !seen.contains(*key) {
            out.push_str(&format!("##${key}= {value}\n"));
        }
    }
    out.push_str("##END=\n");
    let encoded: Vec<u8> = out.chars().map(|c| c as u8).collect();
    fs::write(path, encoded).map_err(|e| e.to_string())
}
fn integral_rows(
    s: &Spectrum,
    re: &[f64],
    regions: &[Region],
) -> Result<Vec<(Region, processing::Integral)>, String> {
    let mut rows = Vec::new();
    for r in regions {
        rows.push((
            r.clone(),
            processing::region(
                re,
                [s.left, s.right],
                [r.left, r.right],
                r.bias,
                r.slope,
                r.baseline,
                r.points,
            )?,
        ));
    }
    rows.sort_by(|a, b| b.0.left.total_cmp(&a.0.left));
    Ok(rows)
}
pub fn export(
    path: &Path,
    s: &Spectrum,
    settings: &Settings,
    regions: &[Region],
) -> Result<String, String> {
    storage::outside_source(path, s)?;
    let (re, im) = saved(s, settings)?;
    let rows = integral_rows(s, &re, regions)?;
    let source = storage::source_processing(&s.path)?;
    let template = if source.join("procs").is_file() {
        source.join("procs")
    } else {
        source.join("proc")
    };
    let text = fs::read(&template).map_err(|e| e.to_string())?;
    let text: String = text.iter().map(|b| char::from(*b)).collect();
    let get = |key: &str| -> Result<f64, String> {
        text.lines()
            .find_map(|l| {
                l.strip_prefix(&format!("##${key}="))
                    .and_then(|v| v.trim().parse::<f64>().ok())
            })
            .ok_or_else(|| format!("Missing Bruker {key}"))
    };
    let sf = get("SF")?;
    if !sf.is_finite() || sf <= 0. {
        return Err("Invalid spectrometer frequency".into());
    }
    let width = if source.join("1r").is_file() {
        get("SW_p")?
    } else {
        let acquisition = fs::read(
            source
                .ancestors()
                .nth(2)
                .ok_or("Missing acquisition")?
                .join("acqus"),
        )
        .map_err(|e| e.to_string())?;
        let acquisition: String = acquisition.iter().map(|b| char::from(*b)).collect();
        acquisition
            .lines()
            .find_map(|l| {
                l.strip_prefix("##$SW_h=")
                    .and_then(|v| v.trim().parse::<f64>().ok())
            })
            .ok_or("Missing acquisition SW_h")?
    };
    let scale = 2f64.powi(s.nc);
    let stored_scale = scalar(settings.integral_scale * scale);
    let factor = stored_scale.parse::<f64>().map_err(|e| e.to_string())? / scale;
    if !factor.is_finite()
        || rows.iter().any(|(_, a)| {
            ![a.value(), a.bias, a.slope, a.value() * factor]
                .iter()
                .all(|v| v.is_finite())
        })
    {
        return Err("Integral normalization overflow".into());
    }
    storage::fresh_directory(path)?;
    let name = data::dataset_name(&source);
    let expno = source
        .ancestors()
        .nth(2)
        .and_then(Path::file_name)
        .ok_or("Missing experiment")?;
    let out = storage::copy_experiment(&source, &path.join(&name).join(expno))?;
    let mut updates = BTreeMap::new();
    for (key, value) in [
        ("SI", re.len().to_string()),
        ("FTSIZE", s.ft_size.to_string()),
        ("STSR", "0".into()),
        ("STSI", re.len().to_string()),
        ("PPARMOD", "0".into()),
        ("NC_proc", s.nc.to_string()),
        ("BYTORDP", "0".into()),
        ("DTYPP", "0".into()),
        ("PHC0", scalar(s.phc0 + settings.p0)),
        ("PHC1", scalar(s.phc1 + settings.p1)),
        ("INTSCL", stored_scale),
        ("OFFSET", s.left.to_string()),
        ("SW_p", width.to_string()),
    ] {
        updates.insert(key, value);
    }
    // These paths belong exclusively to the newly created export, never to the master.
    fs::write(out.join("1r"), integers(&re, s.nc)?).map_err(|e| e.to_string())?;
    if !im.is_empty() {
        fs::write(out.join("1i"), integers(&im, s.nc)?).map_err(|e| e.to_string())?;
    }
    for file in ["proc", "procs"] {
        let p = out.join(file);
        if !p.exists() {
            fs::copy(&template, &p).map_err(|e| e.to_string())?;
        }
        let mut parameters_for_file = updates.clone();
        if file == "proc" {
            if settings.p0 != 0. || settings.p1 != 0. {
                parameters_for_file.insert("PHC0", scalar(settings.p0));
                parameters_for_file.insert("PHC1", scalar(settings.p1));
            } else {
                parameters_for_file.remove("PHC0");
                parameters_for_file.remove("PHC1");
            }
        }
        parameters(&p, &parameters_for_file)?;
    }
    let mut ranges =
        String::from("A 1.0 #regions in PPM\n# low field   high field  bias        slope\n");
    let mut table = format!(
        "   Current data set:\n   NAME =\t{name}\tEXPNO =\t{}\tPROCNO = {}\n   DU   =\t{}\n\n   Number   Integrated Region     Integral\n",
        expno.to_string_lossy(),
        out.file_name().unwrap_or_default().to_string_lossy(),
        storage::destination(path)?.display()
    );
    for (i, (r, a)) in rows.iter().enumerate() {
        ranges.push_str(&format!(
            "  {}  {}  {}  {}  # for region {}\n",
            r.left,
            r.right,
            -a.bias / scale,
            -a.slope / scale,
            i + 1
        ));
        let value = a.value() * factor;
        let integral = if value < 1_000_000. {
            format!("{value:.5}")
        } else {
            let raw = format!("{value:.5E}");
            let (mantissa, exponent) = raw.split_once('E').unwrap();
            format!("{mantissa}E{:02}", exponent.parse::<i32>().unwrap())
        };
        let pad = |text: String, width: usize| {
            let decimal = text.find('.').unwrap_or(0);
            format!("{}{text}", " ".repeat(width.saturating_sub(decimal)))
        };
        table.push_str(&format!(
            "{:>6}{}{}{}\n",
            i + 1,
            pad(format!("{:.3}", r.left), 7),
            pad(format!("{:.3}", r.right), 7),
            pad(integral, 10)
        ));
    }
    fs::write(out.join("intrng"), ranges).map_err(|e| e.to_string())?;
    fs::write(out.join("integrals.txt"), table.replace('\n', "\r\n")).map_err(|e| e.to_string())?;
    let reopened = data::load(&out)?;
    for (a, b) in re
        .iter()
        .zip(&reopened.real)
        .chain(im.iter().zip(&reopened.imag))
    {
        if a.to_bits() != b.to_bits() {
            return Err("Export verification failed: spectrum readback differs".into());
        }
    }
    if reopened.left.to_bits() != s.left.to_bits() || reopened.right.to_bits() != s.right.to_bits()
    {
        return Err("Export verification failed: axis differs".into());
    }
    for ((r, a), v) in rows.iter().zip(&reopened.saved_regions) {
        let actual = processing::region(
            &reopened.real,
            [reopened.left, reopened.right],
            [v[0], v[1]],
            v[2],
            v[3],
            false,
            1,
        )?;
        if actual.value().to_bits() != a.value().to_bits()
            || (actual.value() * reopened.integral_scale).to_bits()
                != (a.value() * factor).to_bits()
        {
            return Err(format!(
                "Export verification failed: integral {}..{} differs",
                r.left, r.right
            ));
        }
    }
    if reopened.real.len() != re.len() || reopened.saved_regions.len() != rows.len() {
        return Err("Export verification failed: lengths differ".into());
    }
    Ok(format!(
        "Exported Bruker dataset {}. Integrals use the saved integer spectrum.",
        out.display()
    ))
}
pub fn csv(
    path: &Path,
    s: &Spectrum,
    settings: &Settings,
    regions: &[Region],
) -> Result<String, String> {
    storage::outside_source(path, s)?;
    let (phased, im) = phase_complex(s, settings);
    let re = processing::polynomial(&phased, settings.baseline, 0, phased.len())?;
    let rows = integral_rows(s, &re, regions)?;
    storage::fresh_directory(path)?;
    let f = fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(path.join("spectrum.csv"))
        .map_err(|e| e.to_string())?;
    let mut w = std::io::BufWriter::new(f);
    writeln!(
        w,
        "index,ppm,source_real,source_imaginary,corrected_real,corrected_imaginary"
    )
    .map_err(|e| e.to_string())?;
    for (i, value) in re.iter().enumerate() {
        let original = s
            .imag
            .get(i)
            .map(|v| format!("{v:.17e}"))
            .unwrap_or_default();
        let corrected = im.get(i).map(|v| format!("{v:.17e}")).unwrap_or_default();
        writeln!(
            w,
            "{i},{:.17e},{:.17e},{original},{value:.17e},{corrected}",
            s.ppm(i),
            s.real[i]
        )
        .map_err(|e| e.to_string())?;
    }
    w.flush()
        .and_then(|_| w.get_ref().sync_all())
        .map_err(|e| e.to_string())?;
    let mut integrals = String::from(
        "left_ppm,right_ppm,raw_area,normalized_area,bias,slope,normalization_factor\n",
    );
    for (r, a) in rows {
        integrals.push_str(&format!(
            "{:.17e},{:.17e},{:.17e},{:.17e},{:.17e},{:.17e},{:.17e}\n",
            r.left,
            r.right,
            a.value(),
            a.value() * settings.integral_scale,
            a.bias,
            a.slope,
            settings.integral_scale
        ));
    }
    storage::write_new(&path.join("integrals.csv"), integrals.as_bytes())?;
    Ok(format!(
        "Exported spectrum and integrals CSV to {}",
        path.display()
    ))
}
