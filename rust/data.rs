use std::{
    collections::BTreeMap,
    fs,
    path::{Path, PathBuf},
};

#[derive(Clone)]
pub struct Spectrum {
    pub path: PathBuf,
    pub title: String,
    pub real: Vec<f64>,
    pub imag: Vec<f64>,
    pub max_amplitude: f64,
    pub ft_size: usize,
    pub integral_scale: f64,
    pub saved_regions: Vec<[f64; 4]>,
    pub left: f64,
    pub right: f64,
}
impl Spectrum {
    pub fn ppm(&self, i: usize) -> f64 {
        self.left + (self.right - self.left) / self.real.len() as f64 * i as f64
    }
}

pub fn parameters(path: &Path) -> Result<BTreeMap<String, String>, String> {
    let raw = fs::read(path).map_err(|e| format!("{}: {e}", path.display()))?;
    let text: String = raw.iter().map(|&b| b as char).collect();
    Ok(text
        .lines()
        .filter_map(|l| {
            l.strip_prefix("##$")?
                .split_once('=')
                .map(|(k, v)| (k.to_owned(), v.trim().to_owned()))
        })
        .collect())
}
pub fn load(path: &Path) -> Result<Spectrum, String> {
    let path = if path.is_file() {
        path.parent().ok_or("No parent folder")?
    } else {
        path
    };
    let path = fs::canonicalize(path).map_err(|e| e.to_string())?;
    let p = parameters(&path.join("procs"))?;
    let number = |key: &str, default: Option<f64>| -> Result<f64, String> {
        let n = match p.get(key) {
            Some(v) => v.parse::<f64>().map_err(|_| format!("Invalid {key}"))?,
            None => default.ok_or_else(|| format!("Missing {key}"))?,
        };
        if n.is_finite() {
            Ok(n)
        } else {
            Err(format!("Nonfinite {key}"))
        }
    };
    let integer = |key: &str, default: Option<f64>| -> Result<i64, String> {
        let n = number(key, default)?;
        if n.fract() != 0. || n.abs() > i32::MAX as f64 {
            Err(format!("Invalid integer {key}"))
        } else {
            Ok(n as i64)
        }
    };
    if path.join("proc2s").exists() || integer("PPARMOD", Some(0.))? != 0 {
        return Err("Only 1D processing is supported".into());
    }
    if integer("DTYPP", Some(0.))? != 0 {
        return Err("Only validated int32 processed spectra are supported".into());
    }
    let n = integer("SI", None)?;
    if !(1..=16_777_216).contains(&n) {
        return Err("Invalid SI".into());
    }
    let order = integer("BYTORDP", None)?;
    if order != 0 && order != 1 {
        return Err("Invalid BYTORDP".into());
    }
    let exponent = integer("NC_proc", None)?;
    if !(-1022..=1023).contains(&exponent) {
        return Err("Invalid NC_proc".into());
    }
    if number("AXLEFT", Some(0.))? != number("AXRIGHT", Some(0.))?
        || integer("STSR", Some(0.))? != 0
        || ![0, n].contains(&integer("STSI", Some(0.))?)
    {
        return Err("Custom or stripped axes are not validated".into());
    }
    let read = |name: &str| -> Result<Vec<f64>, String> {
        let raw = fs::read(path.join(name)).map_err(|e| e.to_string())?;
        if raw.len() < (n as usize) * 4 {
            return Err(format!("Truncated {name}"));
        }
        Ok(raw
            .chunks_exact(4)
            .take(n as usize)
            .map(|b| {
                let b = b.try_into().unwrap();
                let v = if order == 0 {
                    i32::from_le_bytes(b)
                } else {
                    i32::from_be_bytes(b)
                };
                v as f64 * 2f64.powi(exponent as i32)
            })
            .collect())
    };
    let sf = number("SF", None)?;
    let sw = number("SW_p", None)?;
    if sf <= 0. || sw <= 0. {
        return Err("Invalid frequency or width".into());
    }
    let ft_size = integer("FTSIZE", Some(n as f64))?;
    let ft_size = if ft_size > 0 {
        ft_size as usize
    } else {
        n as usize
    };
    let left = number("OFFSET", None)?;
    let right = left - sw / sf;
    if !right.is_finite() || right >= left {
        return Err("Invalid axis".into());
    }
    let real = read("1r")?;
    let imag = if path.join("1i").exists() {
        read("1i")?
    } else {
        vec![]
    };
    let title = fs::read_to_string(path.join("title"))
        .unwrap_or_default()
        .lines()
        .next()
        .unwrap_or("")
        .to_owned();
    let max_amplitude = real
        .iter()
        .chain(imag.iter())
        .fold(0f64, |m, v| m.max(v.abs()))
        .max(f64::MIN_POSITIVE);
    let integral_scale = number("INTSCL", Some(1.))? / 2f64.powi(exponent as i32);
    if !integral_scale.is_finite() {
        return Err("Invalid saved integral normalization".into());
    }
    let mut saved_regions = Vec::new();
    if path.join("intrng").is_file() {
        let text = fs::read_to_string(path.join("intrng")).map_err(|e| e.to_string())?;
        if !text.lines().next().is_some_and(|l| l.starts_with("A 1.0")) {
            return Err("Unsupported integral-region format".into());
        }
        for line in text.lines().skip(1) {
            let line = line.split('#').next().unwrap_or("").trim();
            if line.is_empty() {
                continue;
            }
            let v: Vec<f64> = line
                .split_whitespace()
                .take(4)
                .map(|s| {
                    s.parse::<f64>()
                        .map_err(|_| "Invalid integral region".to_owned())
                })
                .collect::<Result<_, _>>()?;
            if v.len() != 4 || v.iter().any(|v| !v.is_finite()) || v[0] < v[1] {
                return Err("Invalid integral region".into());
            }
            saved_regions.push([
                v[0],
                v[1],
                -2f64.powi(exponent as i32) * v[2],
                -2f64.powi(exponent as i32) * v[3],
            ]);
        }
    }
    if saved_regions.iter().flatten().any(|v| !v.is_finite()) {
        return Err("Integral corrections overflowed saved units".into());
    }
    Ok(Spectrum {
        path,
        title,
        real,
        imag,
        left,
        right,
        max_amplitude,
        ft_size,
        integral_scale,
        saved_regions,
    })
}
// Iterative scan, no symlink traversal, bounded to avoid runaway filesystem walks.
pub fn discover(root: &Path) -> Result<Vec<PathBuf>, String> {
    discover_impl(root, false)
}
pub fn discover_browser(root: &Path) -> Result<Vec<PathBuf>, String> {
    discover_impl(root, true)
}
pub fn dataset_name(path: &Path) -> String {
    path.ancestors()
        .nth(3)
        .and_then(|p| p.file_name())
        .unwrap_or_default()
        .to_string_lossy()
        .into_owned()
}
pub fn sort_datasets(paths: &mut [PathBuf], natural: bool) {
    paths.sort_by_cached_key(|p| (dataset_name(p).to_lowercase(), p.clone()));
    if natural {
        paths.sort_by(|a, b| {
            natural_cmp(&dataset_name(a), &dataset_name(b))
                .then_with(|| natural_cmp(&a.to_string_lossy(), &b.to_string_lossy()))
        });
    }
}
fn discover_impl(root: &Path, include_raw: bool) -> Result<Vec<PathBuf>, String> {
    let is_dataset = |p: &Path| {
        (p.join("procs").is_file() && p.join("1r").is_file())
            || (include_raw
                && p.parent()
                    .is_some_and(|parent| parent.file_name().is_some_and(|n| n == "pdata"))
                && (p.join("procs").is_file() || p.join("proc").is_file()))
    };
    let root = if root.is_file() {
        root.parent().ok_or("No parent")?
    } else {
        root
    };
    if is_dataset(root) {
        return Ok(vec![root.to_owned()]);
    }
    let mut stack = vec![(root.to_owned(), 0)];
    let mut found = Vec::new();
    let mut visited = 0;
    while let Some((dir, depth)) = stack.pop() {
        visited += 1;
        if visited > 100_000 {
            return Err("Search exceeded 100,000 folders; choose a smaller folder".into());
        }
        let entries = fs::read_dir(&dir).map_err(|e| format!("{}: {e}", dir.display()))?;
        for entry in entries {
            let e = entry.map_err(|e| e.to_string())?;
            let ty = e.file_type().map_err(|e| e.to_string())?;
            if ty.is_dir() && !e.file_name().to_string_lossy().starts_with('.') {
                let p = e.path();
                if is_dataset(&p) {
                    found.push(p)
                } else if depth < 12 {
                    stack.push((p, depth + 1))
                }
            }
        }
    }
    found.sort_by(|a, b| natural_cmp(&a.to_string_lossy(), &b.to_string_lossy()));
    Ok(found)
}

pub fn natural_cmp(a: &str, b: &str) -> std::cmp::Ordering {
    use std::cmp::Ordering;
    let (mut a, mut b) = (a.as_bytes(), b.as_bytes());
    while !a.is_empty() && !b.is_empty() {
        if a[0].is_ascii_digit() && b[0].is_ascii_digit() {
            let an = a
                .iter()
                .position(|v| !v.is_ascii_digit())
                .unwrap_or(a.len());
            let bn = b
                .iter()
                .position(|v| !v.is_ascii_digit())
                .unwrap_or(b.len());
            let av = &a[..an];
            let bv = &b[..bn];
            let az = av.iter().position(|v| *v != b'0').unwrap_or(an);
            let bz = bv.iter().position(|v| *v != b'0').unwrap_or(bn);
            let order = (an - az)
                .cmp(&(bn - bz))
                .then_with(|| av[az..].cmp(&bv[bz..]));
            if order != Ordering::Equal {
                return order;
            }
            a = &a[an..];
            b = &b[bn..];
        } else {
            let order = a[0].to_ascii_lowercase().cmp(&b[0].to_ascii_lowercase());
            if order != Ordering::Equal {
                return order;
            }
            a = &a[1..];
            b = &b[1..];
        }
    }
    a.len().cmp(&b.len())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn natural_order_handles_long_numbers() {
        use std::cmp::Ordering::*;
        assert_eq!(natural_cmp("MLN1P9/10", "MLN1P111/2"), Less);
        assert_eq!(
            natural_cmp(
                "a99999999999999999999999999",
                "a100000000000000000000000000"
            ),
            Less
        );
        assert_eq!(natural_cmp("a002", "a2"), Equal);
        let mut paths = vec![
            PathBuf::from("/data/MLN1P115/10/pdata/1"),
            PathBuf::from("/data/MLN1P85/10/pdata/1"),
        ];
        sort_datasets(&mut paths, true);
        assert_eq!(dataset_name(&paths[0]), "MLN1P85");
        sort_datasets(&mut paths, false);
        assert_eq!(dataset_name(&paths[0]), "MLN1P115");
    }
    #[test]
    fn browser_includes_raw_only_processing_numbers() {
        let root = std::env::temp_dir().join(format!("nmr-browser-{}", std::process::id()));
        let raw = root.join("MLN1P85/10/pdata/1");
        fs::create_dir_all(&raw).unwrap();
        fs::write(raw.join("proc"), "##$SI= 65536").unwrap();
        assert_eq!(discover_browser(&root).unwrap(), vec![raw]);
        assert!(discover(&root).unwrap().is_empty());
    }
    #[test]
    fn phase_and_integral() {
        let s = Spectrum {
            path: PathBuf::new(),
            title: String::new(),
            max_amplitude: 4.,
            ft_size: 4,
            integral_scale: 1.,
            saved_regions: vec![],
            real: vec![1., 2., 3., 4.],
            imag: vec![2.; 4],
            left: 4.,
            right: 0.,
        };
        assert_eq!(s.ppm(3), 1.);
        assert_eq!(
            crate::processing::region(&s.real, [4., 0.], [4., 1.], 0., 0., false, 1)
                .unwrap()
                .value(),
            6.
        );
        assert!((crate::processing::phase(&s.real, &s.imag, 90., 0., 4)[0] + 2.).abs() < 1e-12);
    }
    #[test]
    fn endian_scaling_and_rejection() {
        for order in [0, 1] {
            let d = std::env::temp_dir().join(format!("nmr-reader-{}-{order}", std::process::id()));
            fs::create_dir_all(&d).unwrap();
            fs::write(d.join("procs"),format!("##$SI= 4\n##$BYTORDP= {order}\n##$NC_proc= -1\n##$SF= 400\n##$SW_p= 4000\n##$OFFSET= 12\n")).unwrap();
            let bytes: Vec<u8> = [-100i32, 0, 200, i32::MAX]
                .iter()
                .flat_map(|v| {
                    if order == 0 {
                        v.to_le_bytes()
                    } else {
                        v.to_be_bytes()
                    }
                })
                .collect();
            fs::write(d.join("1r"), bytes).unwrap();
            let s = load(&d).unwrap();
            assert_eq!(s.real, vec![-50., 0., 100., i32::MAX as f64 / 2.]);
            assert_eq!(s.right, 2.);
            assert_eq!(discover(&d).unwrap(), vec![d.clone()]);
            fs::write(d.join("1r"), [0u8; 3]).unwrap();
            assert!(load(&d).is_err()); /* retain tiny fixture for inspection */
        }
    }
}
