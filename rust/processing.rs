//! Scientific arithmetic shared by the production viewer and original-TopSpin differential tests.
#[derive(Clone, Debug, Default)]
pub struct Integral {
    pub curve: Vec<f64>,
    pub bias: f64,
    pub slope: f64,
}
impl Integral {
    pub fn value(&self) -> f64 {
        self.curve.last().copied().unwrap_or(0.)
    }
}
pub fn integrate(
    samples: &[f64],
    mut bias: f64,
    mut slope: f64,
    baseline: bool,
    points: usize,
) -> Result<Integral, String> {
    if !bias.is_finite() || !slope.is_finite() {
        return Err("Integral corrections must be finite".into());
    }
    let n = samples.len();
    let mut curve = vec![0.; n];
    if n <= 1 {
        return Ok(Integral { curve, bias, slope });
    }
    if baseline && points > 1 {
        if points > n {
            return Err("Baseline points exceed region size".into());
        }
        let (mut first, mut last) = (0., 0.);
        for i in 0..points {
            first += samples[i];
            last += samples[n - (i + 1)];
        }
        first /= points as f64;
        last /= points as f64;
        bias = -first;
        slope += -(last - first) / n as f64;
    }
    let saved_bias = bias;
    curve[0] = samples[0] + bias;
    bias += slope;
    for i in 1..n - 1 {
        curve[i] = curve[i - 1] + samples[i] + bias;
        bias += slope;
    }
    curve[n - 1] = curve[n - 2];
    Ok(Integral {
        curve,
        bias: saved_bias,
        slope,
    })
}
pub fn region(
    samples: &[f64],
    axis: [f64; 2],
    bounds: [f64; 2],
    bias: f64,
    slope: f64,
    baseline: bool,
    points: usize,
) -> Result<Integral, String> {
    if samples.is_empty()
        || !axis.iter().chain(bounds.iter()).all(|v| v.is_finite())
        || axis[0] <= axis[1]
    {
        return Err("Invalid integral axis or bounds".into());
    }
    let a = axis[0].min(bounds[0].max(bounds[1]));
    let b = axis[1].max(bounds[0].min(bounds[1]));
    if a < b {
        return Err("Integral is outside spectrum".into());
    }
    let index = |x: f64| {
        ((((x - axis[0]) / (axis[1] - axis[0])) * samples.len() as f64 + 0.5) as isize)
            .clamp(0, samples.len() as isize) as usize
    };
    let start = index(a);
    let end = index(b).max(start);
    // Java Arrays.copyOfRange pads the full-right endpoint with zero.
    let mut selected = vec![0.; end - start + 1];
    if start < samples.len() {
        let count = (samples.len() - start).min(selected.len());
        selected[..count].copy_from_slice(&samples[start..start + count]);
    }
    integrate(&selected, bias, slope, baseline, points)
}
pub fn normalization(
    values: &[f64],
    reference: i32,
    calibration: f64,
    stored: f64,
    nc: i32,
) -> f64 {
    let mut denominator = 0.;
    for (i, value) in values.iter().enumerate() {
        if reference == -1 {
            denominator += value;
        } else if reference == -2 {
            denominator = stored / 2f64.powi(nc);
        } else if i as i32 == reference {
            denominator = *value;
        }
    }
    if denominator == 0. {
        denominator = 1.;
    }
    if reference == -2 {
        denominator
    } else {
        calibration / denominator
    }
}
/// Original manual polynomial model, local point coordinates and operation order.
pub fn polynomial(
    samples: &[f64],
    coefficients: [f64; 5],
    start: usize,
    count: usize,
) -> Result<Vec<f64>, String> {
    if !coefficients.iter().all(|c| c.is_finite())
        || start > samples.len()
        || count > samples.len() - start
    {
        return Err("Invalid polynomial baseline range or coefficients".into());
    }
    let mut output = samples.to_vec();
    for (i, value) in output.iter_mut().enumerate().skip(start).take(count) {
        let x = (i - start) as f64;
        let baseline = coefficients[0]
            + (coefficients[1]
                + (coefficients[2] + (coefficients[3] + coefficients[4] * x) * x) * x)
                * x;
        *value = samples[i] / 1.0 - baseline;
    }
    if output.iter().any(|v| !v.is_finite()) {
        return Err("Baseline coefficients overflowed the spectrum".into());
    }
    Ok(output)
}
pub fn phase(real: &[f64], imag: &[f64], p0: f64, p1: f64, ft_size: usize) -> Vec<f64> {
    phase_range(real, imag, p0, p1, ft_size, 0, real.len())
}
pub fn phase_range(
    real: &[f64],
    imag: &[f64],
    p0: f64,
    p1: f64,
    ft_size: usize,
    start: usize,
    count: usize,
) -> Vec<f64> {
    let degrees = 0.017_453_292_519_943_295;
    let step = (p1 / ft_size.max(1) as f64) * degrees;
    let angle = p0 * degrees + start as f64 * step;
    let (mut c, mut s) = (angle.cos(), angle.sin());
    let (dc, ds) = (step.cos(), step.sin());
    let mut output = Vec::with_capacity(count);
    for (i, value) in real.iter().enumerate().skip(start).take(count) {
        output.push(value * c - imag.get(i).copied().unwrap_or(0.) * s);
        let nc = c * dc - s * ds;
        let ns = s * dc + c * ds;
        c = nc;
        s = ns;
    }
    output
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn endpoint_and_padding() {
        assert_eq!(
            integrate(&[1., 2., 3., 4.], 0., 0., false, 1)
                .unwrap()
                .value(),
            6.
        );
        assert_eq!(
            region(&[1., 2., 3., 4.], [4., 0.], [4., 0.], 0., 0., false, 1)
                .unwrap()
                .value(),
            10.
        );
    }
    #[test]
    fn correction_and_polynomial() {
        let a = integrate(&[2., 3., 4., 5.], 0., 0., true, 2).unwrap();
        assert_eq!(a.bias, -2.5);
        assert_eq!(a.slope, -0.5);
        assert_eq!(
            polynomial(&[2., 3., 4., 5.], [2., 1., 0., 0., 0.], 0, 4).unwrap(),
            vec![0.; 4]
        );
        assert!(integrate(&[1., 2.], 0., 0., true, 3).is_err());
    }
}
