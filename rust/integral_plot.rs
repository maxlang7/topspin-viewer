//! Display-only integral layout. Numerical curves and normalization are never modified here.
use super::*;

pub(super) struct Lane {
    pub baseline: f32,
    pub amplitude: f32,
    pub band: egui::Rect,
}
pub(super) fn lane_layout(
    rect: egui::Rect,
    stacked: bool,
    count: usize,
    lane: usize,
    offset: f64,
    integrals: bool,
) -> Lane {
    let h = if stacked {
        rect.height() / count.max(1) as f32
    } else {
        rect.height()
    };
    let top = rect.top() + if stacked { h * lane as f32 } else { 0. };
    let bottom = top + h;
    let fraction = if integrals {
        0.72
    } else if stacked {
        0.85
    } else {
        0.88
    };
    let amplitude = h * if integrals { 0.62 } else { 0.7 };
    let mut baseline = top + h * fraction - offset as f32 * amplitude;
    if integrals {
        baseline = baseline.clamp(top + h * 0.12, bottom - h * 0.24);
    }
    let band = egui::Rect::from_min_max(
        Pos2::new(rect.left(), baseline + 6.),
        Pos2::new(rect.right(), bottom - 6.),
    );
    Lane {
        baseline,
        amplitude,
        band,
    }
}

/// Pack labels into rows, keeping text inside the viewport and avoiding collisions.
fn label_layout(desired: &[(f32, f32)], left: f32, right: f32, gap: f32) -> Vec<(f32, usize)> {
    let mut positions = vec![(0., 0); desired.len()];
    let mut first = 0;
    let mut row = 0;
    while first < desired.len() {
        let mut end = first;
        let mut occupied = 0.;
        while end < desired.len() {
            let extra = desired[end].1 + if end == first { 0. } else { gap };
            if end > first && occupied + extra > right - left {
                break;
            }
            occupied += extra;
            end += 1;
        }
        let mut cursor = left;
        for i in first..end {
            let x = (desired[i].0 - desired[i].1 / 2.).max(cursor);
            positions[i] = (x, row);
            cursor = x + desired[i].1 + gap;
        }
        let mut edge = right;
        for i in (first..end).rev() {
            positions[i].0 = positions[i].0.min(edge - desired[i].1).max(left);
            edge = positions[i].0 - gap;
        }
        first = end;
        row += 1;
    }
    positions
}
fn visible_indices(
    curve_len: usize,
    start: usize,
    n: usize,
    axis: [f64; 2],
    range: [f64; 2],
) -> (usize, usize) {
    let index = |ppm: f64| ((ppm - axis[0]) / (axis[1] - axis[0]) * n as f64) - start as f64;
    let end = curve_len.saturating_sub(1) as isize;
    (
        (index(range[0]).floor() as isize).clamp(0, end) as usize,
        (index(range[1]).ceil() as isize).clamp(0, end) as usize,
    )
}
fn curve_fraction(value: f64, low: f64, high: f64) -> f32 {
    if high <= low {
        return 0.5;
    }
    let span = high - low;
    let f = if span.is_finite() {
        (value - low) / span
    } else {
        let scale = high.abs().max(low.abs());
        (value / scale - low / scale) / (high / scale - low / scale)
    };
    f.clamp(0., 1.) as f32
}
impl App {
    pub(super) fn has_visible_integrals(&self) -> bool {
        self.traces
            .get(self.selected)
            .is_some_and(|t| t.settings.visible)
            && self
                .regions
                .iter()
                .any(|r| r.trace == self.selected && r.error.is_none() && !r.curve.is_empty())
    }
    pub(super) fn paint_integrals(
        &self,
        rect: egui::Rect,
        range: [f64; 2],
        painter: &egui::Painter,
        response: &egui::Response,
    ) {
        if !self.has_visible_integrals() {
            return;
        }
        let t = &self.traces[self.selected];
        let count = self
            .traces
            .iter()
            .filter(|t| t.settings.visible)
            .count()
            .max(1);
        let lane = self
            .traces
            .iter()
            .take(self.selected)
            .filter(|t| t.settings.visible)
            .count();
        let geometry = lane_layout(rect, self.stacked, count, lane, t.settings.offset, true);
        let band = geometry.band;
        if band.height() < 12. {
            return;
        }
        let width = range[0] - range[1];
        let px = |ppm: f64| rect.left() + ((range[0] - ppm) / width) as f32 * rect.width();
        let font = egui::FontId::monospace(12.);
        let line_h = 18.;
        let mut regions: Vec<_> = self
            .regions
            .iter()
            .enumerate()
            .filter(|(_, r)| r.trace == self.selected && r.error.is_none() && !r.curve.is_empty())
            .filter_map(|(id, r)| {
                let start = ((((r.left.max(r.right).min(t.data.left) - t.data.left)
                    / (t.data.right - t.data.left))
                    * t.data.real.len() as f64
                    + 0.5) as isize)
                    .clamp(0, t.data.real.len() as isize) as usize;
                let left = px(t.data.ppm(start));
                let right = px(t.data.ppm(start + r.curve.len() - 1));
                if right < rect.left() || left > rect.right() {
                    return None;
                }
                let value = r.value * t.settings.integral_scale;
                let text = if value.abs() >= 1e6 || (value != 0. && value.abs() < 1e-4) {
                    format!("{value:.3e}")
                } else {
                    format!("{value:.5}")
                };
                let text = painter.layout_no_wrap(text, font.clone(), COLORS[1]);
                Some((
                    id,
                    r,
                    start,
                    left.max(rect.left()),
                    right.min(rect.right()),
                    text,
                ))
            })
            .collect();
        regions.sort_by(|a, b| a.3.total_cmp(&b.3));
        if regions.is_empty() {
            return;
        }
        let desired: Vec<_> = regions
            .iter()
            .map(|r| ((r.3 + r.4) / 2., r.5.size().x + 8.))
            .collect();
        let labels = label_layout(&desired, rect.left() + 2., rect.right() - 2., 8.);
        let rows = labels.last().map_or(1, |p| p.1 + 1);
        let max_rows = ((band.height() * 0.5 / line_h).floor() as usize).clamp(1, 4);
        let shown_rows = rows.min(max_rows);
        let curve_h = (band.height() - shown_rows as f32 * line_h - 8.).clamp(4., 80.);
        let curve_rect = egui::Rect::from_min_max(
            band.left_top(),
            Pos2::new(band.right(), band.top() + curve_h),
        );
        let plot = painter.with_clip_rect(band.intersect(rect));
        painter.with_clip_rect(rect).line_segment(
            [
                Pos2::new(rect.left(), geometry.baseline),
                Pos2::new(rect.right(), geometry.baseline),
            ],
            Stroke::new(1., Color32::from_rgb(80, 73, 69)),
        );
        let mut hidden = 0;
        for ((id, r, start, left, right, text), (label_x, row)) in regions.iter().zip(labels) {
            let (first, last) = visible_indices(
                r.curve.len(),
                *start,
                t.data.real.len(),
                [t.data.left, t.data.right],
                range,
            );
            if first > last {
                continue;
            }
            let visible = &r.curve[first..=last];
            let low = visible.iter().fold(f64::INFINITY, |m, v| m.min(*v));
            let high = visible.iter().fold(f64::NEG_INFINITY, |m, v| m.max(*v));
            let point = |i: usize| {
                Pos2::new(
                    px(t.data.ppm(start + i)),
                    curve_rect.bottom() - curve_fraction(r.curve[i], low, high) * curve_h,
                )
            };
            let bucket = visible
                .len()
                .div_ceil((right - left).ceil().max(1.) as usize)
                .max(1);
            let curve_painter = painter.with_clip_rect(curve_rect);
            let mut previous = None;
            for base in (first..=last).step_by(bucket) {
                let end = (base + bucket - 1).min(last);
                let mut min = base;
                let mut max = base;
                for i in base..=end {
                    if r.curve[i] < r.curve[min] {
                        min = i
                    }
                    if r.curve[i] > r.curve[max] {
                        max = i
                    }
                }
                let mut indices = vec![base, min, max, end];
                indices.sort_unstable();
                indices.dedup();
                for i in indices {
                    let p = point(i);
                    if let Some(before) = previous {
                        curve_painter.line_segment([before, p], Stroke::new(1.4, COLORS[1]));
                    }
                    previous = Some(p);
                }
            }
            let bracket_y = curve_rect.bottom() + 3.;
            for x in [*left, *right] {
                plot.line_segment(
                    [Pos2::new(x, bracket_y - 3.), Pos2::new(x, bracket_y + 3.)],
                    Stroke::new(1., COLORS[1]),
                );
            }
            plot.line_segment(
                [Pos2::new(*left, bracket_y), Pos2::new(*right, bracket_y)],
                Stroke::new(1., COLORS[1]),
            );
            if row < max_rows {
                let y = curve_rect.bottom() + 8. + row as f32 * line_h;
                let label_rect = egui::Rect::from_min_size(
                    Pos2::new(label_x, y),
                    egui::vec2(text.size().x + 8., line_h),
                );
                plot.line_segment(
                    [
                        Pos2::new((left + right) / 2., bracket_y + 3.),
                        Pos2::new(label_rect.center().x, y),
                    ],
                    Stroke::new(0.7, Color32::from_rgb(122, 105, 55)),
                );
                plot.rect_filled(label_rect, 2., Color32::from_rgb(40, 40, 40));
                plot.galley(Pos2::new(label_x + 4., y + 1.), text.clone(), COLORS[1]);
            } else {
                hidden += 1;
            }
            if let Some(pos) = response.hover_pos()
                && band.contains(pos)
                && pos.x >= left - 4.
                && pos.x <= right + 4.
            {
                response.clone().on_hover_text_at_pointer(format!(
                    "Integral #{}  {:.5}–{:.5} ppm\nNormalized: {:.12}\nRaw: {:.12}",
                    id + 1,
                    r.left,
                    r.right,
                    r.value * t.settings.integral_scale,
                    r.value
                ));
            }
        }
        if hidden > 0 {
            plot.text(
                band.right_bottom(),
                egui::Align2::RIGHT_BOTTOM,
                format!("+{hidden} labels · INTEGRALS table"),
                font,
                COLORS[1],
            );
        }
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn band_stays_below_baseline_in_overlay_and_stacked_views() {
        let rect = egui::Rect::from_min_size(Pos2::ZERO, egui::vec2(1000., 800.));
        for stacked in [false, true] {
            for offset in [-2., 0., 2.] {
                let lane = lane_layout(rect, stacked, 3, 1, offset, true);
                assert!(lane.band.top() > lane.baseline);
                assert!(lane.band.bottom() <= if stacked { 800. * 2. / 3. } else { 800. });
                assert!(lane.band.height() > 0.);
            }
        }
    }
    #[test]
    fn crowded_labels_do_not_overlap_or_leave_viewport() {
        let desired = vec![(2., 70.), (3., 80.), (299., 60.), (299., 90.), (299., 60.)];
        let placed = label_layout(&desired, 0., 300., 8.);
        for (i, (x, row)) in placed.iter().enumerate() {
            assert!(*x >= 0. && x + desired[i].1 <= 300.);
            if i > 0 && *row == placed[i - 1].1 {
                assert!(*x >= placed[i - 1].0 + desired[i - 1].1 + 8.);
            }
        }
    }
    #[test]
    fn zoomed_negative_and_flat_curves_fit_display_band() {
        assert_eq!(curve_fraction(-3., -4., -2.), 0.5);
        assert_eq!(curve_fraction(0., 0., 0.), 0.5);
        assert!(curve_fraction(0., -1e308, 1e308).is_finite());
        assert_eq!(visible_indices(101, 0, 100, [10., 0.], [6., 4.]), (40, 60));
    }
}
