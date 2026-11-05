//! Native read-only opening of raw acquisitions, using the existing validated FFT kernel.
use crate::data::{Spectrum, parameters};
use std::{
    collections::BTreeMap,
    fs,
    path::{Path, PathBuf},
};
unsafe extern "C" {
    fn nmr_process_raw(
        fid: *const f64,
        count: i32,
        size: i32,
        delay: i32,
        lb: f64,
        sw: f64,
        p0: f64,
        p1: f64,
        nc: i32,
        cache: i32,
        output: *mut f64,
    ) -> i32;
}
fn number(p: &BTreeMap<String, String>, key: &str) -> Result<f64, String> {
    let n = p
        .get(key)
        .ok_or_else(|| format!("Missing {key}"))?
        .parse::<f64>()
        .map_err(|_| format!("Invalid {key}"))?;
    if n.is_finite() {
        Ok(n)
    } else {
        Err(format!("Invalid {key}"))
    }
}
fn integer(p: &BTreeMap<String, String>, key: &str) -> Result<i32, String> {
    let n = number(p, key)?;
    if n.fract() != 0. || n < i32::MIN as f64 || n > i32::MAX as f64 {
        Err(format!("Invalid integer {key}"))
    } else {
        Ok(n as i32)
    }
}
fn cache(index: &str) -> Result<i32, String> {
    let text = fs::read_to_string(
        PathBuf::from("/sys/devices/system/cpu/cpu0/cache")
            .join(index)
            .join("size"),
    )
    .map_err(|e| e.to_string())?;
    let text = text.trim();
    let scale = if text.ends_with('K') {
        1024
    } else if text.ends_with('M') {
        1024 * 1024
    } else {
        1
    };
    text.trim_end_matches(['K', 'M'])
        .parse::<i32>()
        .ok()
        .and_then(|v| v.checked_mul(scale))
        .ok_or_else(|| "Invalid FFT cache size".into())
}
pub fn load(path: &Path) -> Result<Spectrum, String> {
    let experiment = path
        .parent()
        .and_then(Path::parent)
        .ok_or("Choose a processing number below pdata")?;
    let acquisition = parameters(&experiment.join("acqus"))?;
    let processing = parameters(&path.join("proc"))?;
    if experiment.join("ser").exists() || integer(&acquisition, "AQ_mod")? != 3 {
        return Err("Unsupported acquisition mode; validated complex 1D FIDs only".into());
    }
    let ty = integer(&acquisition, "DTYPA")?;
    let order = integer(&acquisition, "BYTORDA")?;
    let count = integer(&acquisition, "TD")?;
    if ![0, 2].contains(&ty)
        || ![0, 1].contains(&order)
        || !(2..=33_554_432).contains(&count)
        || count % 2 != 0
    {
        return Err("Unsupported raw format or size".into());
    }
    let n = integer(&processing, "SI")?;
    if !(16..=16_777_216).contains(&n)
        || !(n as u32).is_power_of_two()
        || n.trailing_zeros() % 2 != 0
        || count > n * 2
    {
        return Err("Fourier size is outside validated raw processing modes".into());
    }
    let delay = number(&acquisition, "GRPDLY")?;
    let nc = integer(&acquisition, "NC")?;
    if delay.fract() != 0.
        || delay < 0.
        || delay >= count as f64 / 2.
        || !(-1022..=1023).contains(&nc)
    {
        return Err("Unsupported digital-filter delay or raw scaling".into());
    }
    if integer(&processing, "ME_mod")? != 0
        || integer(&processing, "WDW")? != 1
        || processing.get("PKNL").map(String::as_str) != Some("yes")
    {
        return Err("Raw processing requires the validated exponential window and digital-filter correction".into());
    }
    let lb = number(&processing, "LB")?;
    let sw = number(&acquisition, "SW_h")?;
    let p0 = number(&processing, "PHC0")?;
    let p1 = number(&processing, "PHC1")?;
    let sf = number(&processing, "SF")?;
    if lb < 0. || sw <= 0. || sf <= 0. {
        return Err("Invalid acquisition frequency, width, or broadening".into());
    }
    let first_cache = cache("index0")?;
    let second_cache = cache("index2")?;
    if !(0..=16_777_216).contains(&first_cache)
        || (second_cache > 128 && (second_cache as i64) < n as i64 * 8)
    {
        return Err("CPU FFT cache split needs an equivalence reference".into());
    }
    let raw = fs::read(experiment.join("fid")).map_err(|e| e.to_string())?;
    let stride = if ty == 2 { 8 } else { 4 };
    if raw.len() < (count as usize) * stride {
        return Err("Truncated raw acquisition".into());
    }
    let mut fid = Vec::with_capacity(count as usize);
    for b in raw.chunks_exact(stride).take(count as usize) {
        let v = if ty == 2 {
            let b = b.try_into().unwrap();
            if order == 0 {
                f64::from_le_bytes(b)
            } else {
                f64::from_be_bytes(b)
            }
        } else {
            let b = b.try_into().unwrap();
            if order == 0 {
                i32::from_le_bytes(b) as f64
            } else {
                i32::from_be_bytes(b) as f64
            }
        };
        if !v.is_finite() {
            return Err("Non-finite raw sample".into());
        }
        fid.push(v);
    }
    let mut result = vec![0.; n as usize * 2 + 1];
    // All lengths, formats and numerical domains are validated above. C only writes this owned output.
    let status = unsafe {
        nmr_process_raw(
            fid.as_ptr(),
            count / 2,
            n,
            delay as i32,
            lb,
            sw,
            p0,
            p1,
            nc,
            first_cache,
            result.as_mut_ptr(),
        )
    };
    if status != 0 {
        return Err("Unable to allocate native FFT buffers".into());
    }
    if result.iter().any(|v| !v.is_finite()) {
        return Err("Raw processing produced non-finite output".into());
    }
    let exponent = result[0] as i32;
    let scale = 2f64.powi(exponent);
    let n = n as usize;
    let carrier = number(&acquisition, "SFO1")?;
    let offset = (carrier - sf) * 1_000_000.0 / sf + sw / (2.0 * sf);
    let left = format!("{offset:.14e}")
        .parse::<f64>()
        .map_err(|e| e.to_string())?;
    let right = left - sw / sf;
    let real = result[1..n + 1].to_vec();
    let imag = result[n + 1..].to_vec();
    let max_amplitude = real
        .iter()
        .chain(&imag)
        .fold(f64::MIN_POSITIVE, |m, v| m.max(v.abs()));
    let integral_scale = processing
        .get("INTSCL")
        .map(|v| v.parse::<f64>().map_err(|e| e.to_string()))
        .transpose()?
        .unwrap_or(1.)
        / scale;
    let mut saved_regions = vec![];
    if path.join("intrng").exists() {
        let text = fs::read_to_string(path.join("intrng")).map_err(|e| e.to_string())?;
        if !text.lines().next().is_some_and(|l| l.starts_with("A 1.0")) {
            return Err("Unsupported integration region format".into());
        }
        for line in text.lines().skip(1) {
            let line = line.split('#').next().unwrap_or("").trim();
            if line.is_empty() {
                continue;
            }
            let v = line
                .split_whitespace()
                .take(4)
                .map(|s| s.parse::<f64>().map_err(|e| e.to_string()))
                .collect::<Result<Vec<_>, _>>()?;
            if v.len() != 4 || v[0] < v[1] {
                return Err("Invalid integration region".into());
            }
            saved_regions.push([v[0], v[1], -scale * v[2], -scale * v[3]]);
        }
    }
    if !left.is_finite()
        || !right.is_finite()
        || left <= right
        || !integral_scale.is_finite()
        || saved_regions.iter().flatten().any(|v| !v.is_finite())
    {
        return Err("Invalid raw processing axis or integration parameters".into());
    }
    let title = fs::read_to_string(path.join("title"))
        .unwrap_or_default()
        .lines()
        .next()
        .unwrap_or("")
        .to_owned();
    Ok(Spectrum {
        nc: exponent,
        phc0: p0,
        phc1: p1,
        path: path.to_owned(),
        title,
        real,
        imag,
        left,
        right,
        max_amplitude,
        ft_size: n,
        integral_scale,
        saved_regions,
    })
}
