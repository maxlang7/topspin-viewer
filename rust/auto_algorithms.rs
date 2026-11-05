//! Independent automatic-processing algorithms. These are not TopSpin algorithm replicas.
use super::processing;
fn median(v: &[f64]) -> f64 {
    let mut v = v.to_vec();
    v.sort_by(f64::total_cmp);
    v[v.len() / 2]
}
fn valid(v: &[f64]) -> Result<(), String> {
    if v.len() < 32 || v.iter().any(|v| !v.is_finite()) {
        Err("Automatic processing requires at least 32 finite samples".into())
    } else {
        Ok(())
    }
}
/// Peak-masked, iteratively reweighted quartic fit in normalized coordinates.
pub(super) fn baseline(samples: &[f64]) -> Result<([f64; 5], Vec<String>), String> {
    valid(samples)?;
    let n = samples.len();
    let stride = n.div_ceil(8192);
    let y = samples.iter().step_by(stride).copied().collect::<Vec<_>>();
    let x = (0..n)
        .step_by(stride)
        .map(|i| 2. * i as f64 / (n - 1) as f64 - 1.)
        .collect::<Vec<_>>();
    let scale = y.iter().fold(0f64, |s, v| s.max(v.abs()));
    if scale == 0. {
        return Ok((
            [0.; 5],
            vec!["Flat spectrum: no baseline correction needed".into()],
        ));
    }
    let y = y.iter().map(|v| v / scale).collect::<Vec<_>>();
    let mut weights = vec![1.; y.len()];
    let mut coeff = [0.; 5];
    let mut excluded = 0;
    for iteration in 0..18 {
        let mut matrix = [[0.; 6]; 5];
        for ((&x, &y), &w) in x.iter().zip(&y).zip(&weights) {
            let mut powers = [1.; 9];
            for j in 1..9 {
                powers[j] = powers[j - 1] * x;
            }
            for r in 0..5 {
                for c in 0..5 {
                    matrix[r][c] += w * powers[r + c];
                }
                matrix[r][5] += w * y * powers[r];
            }
        }
        for c in 0..5 {
            let pivot = (c..5)
                .max_by(|&a, &b| matrix[a][c].abs().total_cmp(&matrix[b][c].abs()))
                .unwrap();
            matrix.swap(c, pivot);
            if matrix[c][c].abs() < 1e-12 {
                return Err("Too little baseline support for a stable fit".into());
            }
            let d = matrix[c][c];
            for value in &mut matrix[c][c..] {
                *value /= d;
            }
            for r in 0..5 {
                if r == c {
                    continue;
                }
                let f = matrix[r][c];
                let pivot = matrix[c];
                for (value, pivot) in matrix[r][c..].iter_mut().zip(&pivot[c..]) {
                    *value -= f * pivot;
                }
            }
        }
        for j in 0..5 {
            coeff[j] = matrix[j][5];
        }
        let residual = x
            .iter()
            .zip(&y)
            .map(|(&x, &y)| y - evaluate(coeff, x))
            .collect::<Vec<_>>();
        let center = median(&residual);
        let mad = median(
            &residual
                .iter()
                .map(|v| (v - center).abs())
                .collect::<Vec<_>>(),
        )
        .max(1e-10)
            * 1.4826;
        excluded = 0;
        for (w, &r) in weights.iter_mut().zip(&residual) {
            let z = (r - center).abs() / (3. * mad);
            *w = if z >= 1. {
                excluded += 1;
                0.0001
            } else {
                (1. - z * z).powi(2)
            };
        }
        if iteration > 3 && excluded == 0 {
            break;
        }
    }
    // Convert sum c_k(2i/(n-1)-1)^k to the existing sample-coordinate recipe.
    let mut result = [0.; 5];
    let a = 2. / (n - 1) as f64;
    const BINOM: [[f64; 5]; 5] = [
        [1., 0., 0., 0., 0.],
        [1., 1., 0., 0., 0.],
        [1., 2., 1., 0., 0.],
        [1., 3., 3., 1., 0.],
        [1., 4., 6., 4., 1.],
    ];
    for k in 0..5 {
        for j in 0..=k {
            result[j] += scale
                * coeff[k]
                * BINOM[k][j]
                * a.powi(j as i32)
                * if (k - j) % 2 == 0 { 1. } else { -1. };
        }
    }
    processing::polynomial(samples, result, 0, n)?;
    let mut warnings = vec![];
    let residual_energy = x
        .iter()
        .zip(&y)
        .map(|(&x, &y)| (y - evaluate(coeff, x)).powi(2))
        .sum::<f64>();
    if residual_energy < y.iter().map(|v| v * v).sum::<f64>() * 0.10 {
        warnings.push("Most intensity is explained by the fitted baseline; broad signals may be removed. Review before accepting".into());
    }
    if excluded as f64 / y.len() as f64 > 0.35 {
        warnings.push(
            "Dense or broad signals leave limited baseline support; inspect peak areas carefully"
                .into(),
        );
    }
    if coeff.iter().skip(1).map(|v| v.abs()).sum::<f64>() > 1. {
        warnings.push(
            "Large baseline curvature: review the correction across the full spectrum".into(),
        );
    }
    Ok((result, warnings))
}
fn evaluate(c: [f64; 5], x: f64) -> f64 {
    c[0] + x * (c[1] + x * (c[2] + x * (c[3] + x * c[4])))
}
/// Derivative entropy plus a negative-intensity penalty, searched in PH0/PH1.
/// Sparse peak-preserving sampling bounds the search cost independently of data size.
pub(super) fn phase(
    real: &[f64],
    imag: &[f64],
    ft: usize,
    start: [f64; 2],
) -> Result<([f64; 2], Vec<String>), String> {
    valid(real)?;
    if imag.len() != real.len() || imag.iter().any(|v| !v.is_finite()) {
        return Err("Automatic phase needs a complete finite imaginary spectrum".into());
    }
    let peak = real
        .iter()
        .zip(imag)
        .map(|(r, i)| r.hypot(*i))
        .fold(0f64, f64::max);
    if !peak.is_finite() || peak == 0. {
        return Err("No signal found for automatic phase correction".into());
    }
    let magnitude = real
        .iter()
        .zip(imag)
        .step_by(real.len().div_ceil(4096))
        .map(|(r, i)| r.hypot(*i))
        .collect::<Vec<_>>();
    if (peak - median(&magnitude)) / peak < 0.02 {
        return Err(
            "Insufficient resolved structure for automatic phase; use manual controls".into(),
        );
    }
    let bucket = real.len().div_ceil(4096);
    let indices = (0..real.len())
        .step_by(bucket)
        .map(|lo| {
            let hi = (lo + bucket).min(real.len());
            (lo..hi)
                .max_by(|&a, &b| real[a].hypot(imag[a]).total_cmp(&real[b].hypot(imag[b])))
                .unwrap()
        })
        .collect::<Vec<_>>();
    let pairs = indices
        .iter()
        .map(|&i| (i as f64 / ft.max(1) as f64, real[i] / peak, imag[i] / peak))
        .collect::<Vec<_>>();
    let score = |p: [f64; 2]| {
        let mut values = Vec::with_capacity(pairs.len());
        let mut negative = 0.;
        let mut energy = 0.;
        for &(x, r, i) in &pairs {
            let angle = (p[0] + p[1] * x).to_radians();
            let v = r * angle.cos() - i * angle.sin();
            values.push(v);
            energy += v * v;
            if v < 0. {
                negative += v * v;
            }
        }
        let derivative = values
            .windows(2)
            .map(|v| (v[1] - v[0]).abs())
            .collect::<Vec<_>>();
        let sum = derivative.iter().sum::<f64>();
        if sum <= 1e-15 || energy <= 1e-15 {
            return f64::INFINITY;
        }
        let entropy = derivative
            .iter()
            .filter(|&&d| d > 0.)
            .map(|&d| {
                let p = d / sum;
                -p * p.ln()
            })
            .sum::<f64>();
        entropy + 20. * negative / energy
    };
    let mut best = start;
    let mut best_score = score(best);
    for p1 in (-360..=360).step_by(90) {
        for p0 in (-180..180).step_by(30) {
            let p = [p0 as f64, p1 as f64];
            let s = score(p);
            if s < best_score {
                best = p;
                best_score = s;
            }
        }
    }
    for step in [20., 8., 3., 1., 0.3, 0.1, 0.03] {
        for _ in 0..30 {
            let old = best_score;
            for d0 in [-step, 0., step] {
                for d1 in [-2. * step, 0., 2. * step] {
                    let candidate = [wrap(best[0] + d0), (best[1] + d1).clamp(-720., 720.)];
                    let s = score(candidate);
                    if s < best_score {
                        best = candidate;
                        best_score = s;
                    }
                }
            }
            if best_score == old {
                break;
            }
        }
    }
    let mut warnings = vec![];
    if best[1].abs() > 500. {
        warnings.push("Large first-order phase correction; review both spectrum edges".into());
    }
    let phased = processing::phase(real, imag, best[0], best[1], ft);
    let negative = phased
        .iter()
        .filter(|v| **v < 0.)
        .map(|v| v * v)
        .sum::<f64>();
    let energy = phased.iter().map(|v| v * v).sum::<f64>();
    if negative / energy.max(f64::MIN_POSITIVE) > 0.05 {
        warnings.push(
            "Substantial negative signal remains; inverted or overlapping peaks need manual review"
                .into(),
        );
    }
    Ok((best, warnings))
}
fn wrap(p: f64) -> f64 {
    (p + 180.).rem_euclid(360.) - 180.
}

#[cfg(test)]
mod tests {
    use super::*;
    fn spectrum(n: usize) -> (Vec<f64>, Vec<f64>) {
        (0..n)
            .map(|i| {
                let x = i as f64 / n as f64;
                [
                    (0.16, 0.002, 1.),
                    (0.39, 0.003, 0.7),
                    (0.68, 0.0025, 0.8),
                    (0.87, 0.0018, 0.55),
                ]
                .iter()
                .fold((0., 0.), |(r, im), &(center, w, a)| {
                    let d = x - center;
                    let den = d * d + w * w;
                    (r + a * w * w / den, im + a * w * d / den)
                })
            })
            .unzip()
    }
    #[test]
    fn automatic_phase_recovers_known_complex_lorentzian_spectrum() {
        let n = 4096;
        let (truth, disp) = spectrum(n);
        let (real, imag): (Vec<_>, Vec<_>) = truth
            .iter()
            .zip(&disp)
            .enumerate()
            .map(|(i, (&r, &im))| {
                let angle = (37. - 65. * i as f64 / n as f64).to_radians();
                (
                    r * angle.cos() + im * angle.sin(),
                    -r * angle.sin() + im * angle.cos(),
                )
            })
            .unzip();
        let (p, _) = phase(&real, &imag, n, [0., 0.]).unwrap();
        let corrected = processing::phase(&real, &imag, p[0], p[1], n);
        let error = corrected
            .iter()
            .zip(&truth)
            .map(|(a, b)| (a - b).powi(2))
            .sum::<f64>()
            .sqrt()
            / truth.iter().map(|v| v * v).sum::<f64>().sqrt();
        assert!(error < 0.04, "phase {p:?}, relative error {error}");
        assert!((p[0] - 37.).abs() < 5. && (p[1] + 65.).abs() < 10., "{p:?}");
    }
    #[test]
    fn robust_baseline_removes_curvature_without_erasing_narrow_peak_areas() {
        let n = 8192;
        let (peaks, _) = spectrum(n);
        let baseline = (0..n)
            .map(|i| {
                let x = i as f64 / (n - 1) as f64;
                0.2 + 0.15 * x - 0.3 * x * x + 0.2 * x.powi(4)
            })
            .collect::<Vec<_>>();
        let samples = peaks
            .iter()
            .zip(&baseline)
            .enumerate()
            .map(|(i, (a, b))| a + b + 0.0002 * (i as f64 * 1.719).sin())
            .collect::<Vec<_>>();
        let (b, _) = super::baseline(&samples).unwrap();
        let corrected = processing::polynomial(&samples, b, 0, n).unwrap();
        let error = corrected
            .iter()
            .zip(&peaks)
            .map(|(a, b)| (a - b).powi(2))
            .sum::<f64>()
            / n as f64;
        assert!(error.sqrt() < 0.003, "baseline RMS {}", error.sqrt());
        let lo = (n as f64 * 0.14) as usize;
        let hi = (n as f64 * 0.18) as usize;
        let expected = peaks[lo..hi].iter().sum::<f64>();
        let actual = corrected[lo..hi].iter().sum::<f64>();
        assert!((actual - expected).abs() / expected < 0.04);
    }
    #[test]
    fn invalid_or_missing_inputs_are_rejected_without_candidates() {
        assert!(phase(&[0.; 64], &[0.; 64], 64, [0., 0.]).is_err());
        assert!(phase(&[1.; 64], &[], 64, [0., 0.]).is_err());
        assert!(super::baseline(&[f64::NAN; 64]).is_err());
        assert!(super::baseline(&[1.; 8]).is_err());
    }
}
