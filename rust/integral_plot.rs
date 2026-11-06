//! Display-only integral layout. Numerical curves and normalization are never modified here.
use super::*;

#[derive(Default)]
pub(super) struct IntegralOverlay {
    pub labels: Vec<(usize, egui::Rect)>,
    pub hidden: Vec<usize>,
    pub overflow_rect: Option<egui::Rect>,
}
pub(super) struct Lane {
    pub baseline: f32,
    pub amplitude: f32,
    pub band: egui::Rect,
    pub curve_band: egui::Rect,
}
pub(super) fn visual_offset(theme: appearance::Theme, stacked: bool, offset: f64) -> f64 {
    offset
        + if theme == appearance::Theme::TopSpin && !stacked {
            0.03 / 0.7
        } else {
            0.
        }
}
pub(super) fn lane_layout(
    rect: egui::Rect,
    stacked: bool,
    count: usize,
    lane: usize,
    offset: f64,
    _integrals: bool,
) -> Lane {
    let h = if stacked {
        rect.height() / count.max(1) as f32
    } else {
        rect.height()
    };
    let top = rect.top() + if stacked { h * lane as f32 } else { 0. };
    let bottom = top + h;
    // Spectrum geometry never depends on integral visibility.
    let fraction = if stacked { 0.85 } else { 0.88 };
    let amplitude = h * 0.7;
    let baseline = top + h * fraction - offset as f32 * amplitude;
    let band = egui::Rect::from_min_max(
        Pos2::new(rect.left(), (baseline + 3.).clamp(top, bottom - 1.)),
        Pos2::new(rect.right(), bottom - 1.),
    );
    let curve_bottom = (baseline - (h * 0.025).min(8.)).clamp(top, bottom);
    let curve_band = egui::Rect::from_min_max(
        Pos2::new(
            rect.left(),
            (curve_bottom - (h * 0.22).min(90.))
                .max(top + h * 0.01)
                .min(curve_bottom),
        ),
        Pos2::new(rect.right(), curve_bottom),
    );
    Lane {
        curve_band,
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
    pub(super) fn integral_overflow_menu(&mut self, ui: &mut egui::Ui, overlay: &IntegralOverlay) {
        let Some(rect) = overlay.overflow_rect else {
            return;
        };
        ui.scope_builder(egui::UiBuilder::new().max_rect(rect), |ui| {
            ui.menu_button(format!("+{} integrals", overlay.hidden.len()), |ui| {
                ui.label("Additional integral values");
                egui::ScrollArea::vertical()
                    .max_height(240.)
                    .show(ui, |ui| {
                        for &index in &overlay.hidden {
                            if let Some(r) = self.regions.get(index)
                                && let Some(t) = self.traces.get(r.trace)
                            {
                                ui.monospace(format!(
                                    "#{}  {:.4}–{:.4} ppm  {:.5}",
                                    index + 1,
                                    r.left + t.settings.reference_shift,
                                    r.right + t.settings.reference_shift,
                                    r.value * t.settings.integral_scale
                                ));
                            }
                        }
                    });
                if ui.button("Open integration table").clicked() {
                    self.data_tab = 1;
                    ui.close();
                }
            });
        });
    }
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
    ) -> IntegralOverlay {
        if !self.stacked {
            return self.paint_trace_integrals(self.selected, rect, range, painter, response);
        }
        let mut overlay = IntegralOverlay::default();
        for (index, _) in self
            .traces
            .iter()
            .enumerate()
            .filter(|(_, t)| t.settings.visible)
        {
            let part = self.paint_trace_integrals(index, rect, range, painter, response);
            overlay.labels.extend(part.labels);
            overlay.hidden.extend(part.hidden);
            if index == self.selected {
                overlay.overflow_rect = part.overflow_rect;
            }
        }
        overlay
    }
    fn paint_trace_integrals(
        &self,
        index: usize,
        rect: egui::Rect,
        range: [f64; 2],
        painter: &egui::Painter,
        response: &egui::Response,
    ) -> IntegralOverlay {
        let mut overlay = IntegralOverlay::default();
        let Some(t) = self.traces.get(index).filter(|t| t.settings.visible) else {
            return overlay;
        };
        let count = self
            .traces
            .iter()
            .filter(|t| t.settings.visible)
            .count()
            .max(1);
        let lane = self
            .traces
            .iter()
            .take(index)
            .filter(|t| t.settings.visible)
            .count();
        let geometry = lane_layout(
            rect,
            self.stacked,
            count,
            lane,
            visual_offset(self.theme, self.stacked, t.settings.offset),
            true,
        );
        let band = geometry.band;
        let width = range[0] - range[1];
        let px = |ppm: f64| rect.left() + ((range[0] - ppm) / width) as f32 * rect.width();
        let font_size = if self.stacked {
            (band.height() - 5.).clamp(9., 14.)
        } else {
            14.
        };
        let font = egui::FontId::monospace(font_size);
        let line_h = if self.stacked { font_size + 3. } else { 21. };
        let mut regions: Vec<_> = self
            .regions
            .iter()
            .enumerate()
            .filter(|(_, r)| r.trace == index && r.error.is_none() && !r.curve.is_empty())
            .filter_map(|(id, r)| {
                let start = ((((r.left.max(r.right).min(t.data.left) - t.data.left)
                    / (t.data.right - t.data.left))
                    * t.data.real.len() as f64
                    + 0.5) as isize)
                    .clamp(0, t.data.real.len() as isize) as usize;
                let left = px(t.ppm(start));
                let right = px(t.ppm(start + r.curve.len() - 1));
                if right < rect.left() || left > rect.right() {
                    return None;
                }
                let value = r.value * t.settings.integral_scale;
                let text = if value.abs() >= 1e6 || (value != 0. && value.abs() < 1e-4) {
                    format!("{value:.3e}")
                } else {
                    format!("{value:.5}")
                };
                let text = painter.layout_no_wrap(text, font.clone(), self.palette().accent);
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
            return overlay;
        }
        // Overview markers stay pinned to the plot top while following ppm zoom/pan.
        // Separate overlapping intervals without moving their horizontal endpoints.
        let mut row_ends: Vec<f32> = Vec::new();
        let top = painter.with_clip_rect(response.rect);
        for (id, r, _, left, right, _) in &regions {
            let x0 = (*left).min(rect.right() - 2.).max(rect.left());
            let x1 = (*right).max(x0 + 2.).min(rect.right());
            let row = row_ends
                .iter()
                .position(|end| x0 > *end + 3.)
                .unwrap_or(row_ends.len());
            if row == row_ends.len() {
                row_ends.push(x1);
            } else {
                row_ends[row] = x1;
            }
            let y = if self.stacked {
                rect.top() + rect.height() / count as f32 * lane as f32 + 4.
            } else {
                response.rect.top()
                    + if self.theme == appearance::Theme::TopSpin {
                        27.
                    } else {
                        6.
                    }
            } + row as f32 * 9.;
            let marker = egui::Rect::from_min_max(Pos2::new(x0, y), Pos2::new(x1, y + 6.));
            top.rect_filled(marker, 1., self.palette().accent);
            if response
                .hover_pos()
                .is_some_and(|p| marker.expand(3.).contains(p))
            {
                response.clone().on_hover_text_at_pointer(format!(
                    "Integral #{} · {:.5}–{:.5} ppm\n{:.8}",
                    id + 1,
                    r.left + t.settings.reference_shift,
                    r.right + t.settings.reference_shift,
                    r.value * t.settings.integral_scale
                ));
            }
        }
        // Give the selected label first choice of a bounded row, without moving traces.
        let mut order = (0..regions.len()).collect::<Vec<_>>();
        order.sort_by_key(|&i| self.selected_integral != Some(regions[i].0));
        let desired = order
            .iter()
            .map(|&i| {
                (
                    (regions[i].3 + regions[i].4) / 2.,
                    regions[i].5.size().x + 8.,
                )
            })
            .collect::<Vec<_>>();
        let max_rows = ((band.height() - 2.) / line_h).floor().clamp(0., 4.) as usize;
        let mut placed = label_layout(&desired, rect.left() + 2., rect.right() - 2., 8.);
        let overflow_needed = placed.iter().any(|(_, row)| *row >= max_rows)
            || desired.iter().any(|(_, w)| *w > rect.width() - 4.);
        let label_right = if overflow_needed {
            (rect.right() - 148.).max(rect.left() + 2.)
        } else {
            rect.right() - 2.
        };
        if overflow_needed {
            placed = label_layout(&desired, rect.left() + 2., label_right, 8.);
        }
        let mut labels = vec![(0., 0); regions.len()];
        for (i, pos) in order.into_iter().zip(placed) {
            labels[i] = pos;
        }
        let curve_rect = geometry.curve_band;
        let curve_h = curve_rect.height();
        let plot = painter.with_clip_rect(band.intersect(rect));
        painter.with_clip_rect(rect).line_segment(
            [
                Pos2::new(rect.left(), geometry.baseline),
                Pos2::new(rect.right(), geometry.baseline),
            ],
            Stroke::new(1., self.palette().border),
        );

        for ((id, r, start, left, right, text), (label_x, row)) in regions.iter().zip(labels) {
            let (first, last) = visible_indices(
                r.curve.len(),
                *start,
                t.data.real.len(),
                [t.left(), t.right()],
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
                    px(t.ppm(start + i)),
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
                        curve_painter
                            .line_segment([before, p], Stroke::new(1.4, self.palette().accent));
                    }
                    previous = Some(p);
                }
            }
            let bracket_y = geometry.baseline - 3.;
            let bracket_painter = painter.with_clip_rect(rect);
            for x in [*left, *right] {
                bracket_painter.line_segment(
                    [Pos2::new(x, bracket_y - 3.), Pos2::new(x, bracket_y + 3.)],
                    Stroke::new(1., self.palette().accent),
                );
            }
            bracket_painter.line_segment(
                [Pos2::new(*left, bracket_y), Pos2::new(*right, bracket_y)],
                Stroke::new(1., self.palette().accent),
            );
            if row < max_rows && text.size().x + 8. <= label_right - rect.left() - 2. {
                let y = band.top() + 2. + row as f32 * line_h;
                let label_rect = egui::Rect::from_min_size(
                    Pos2::new(label_x, y),
                    egui::vec2(text.size().x + 8., line_h),
                );
                plot.line_segment(
                    [
                        Pos2::new((left + right) / 2., bracket_y + 3.),
                        Pos2::new(label_rect.center().x, y),
                    ],
                    Stroke::new(0.7, self.palette().muted),
                );
                overlay
                    .labels
                    .push((*id, label_rect.intersect(band).intersect(rect)));
                plot.rect_filled(label_rect, 2., self.palette().panel);
                if self.selected_integral == Some(*id) {
                    plot.rect_stroke(
                        label_rect,
                        2.,
                        Stroke::new(1., self.palette().accent),
                        egui::StrokeKind::Inside,
                    );
                }
                plot.galley(
                    Pos2::new(label_x + 4., y + 1.),
                    text.clone(),
                    self.palette().accent,
                );
            } else {
                overlay.hidden.push(*id);
            }
            if let Some(pos) = response.hover_pos()
                && band.contains(pos)
                && pos.x >= left - 4.
                && pos.x <= right + 4.
            {
                response.clone().on_hover_text_at_pointer(format!(
                    "Integral #{}  {:.5}–{:.5} ppm\nNormalized: {:.12}\nRaw: {:.12}",
                    id + 1,
                    r.left + t.settings.reference_shift,
                    r.right + t.settings.reference_shift,
                    r.value * t.settings.integral_scale,
                    r.value
                ));
            }
        }
        if !overlay.hidden.is_empty() {
            let bottom = band.bottom().max(rect.top() + 24.);
            overlay.overflow_rect = Some(egui::Rect::from_min_size(
                Pos2::new(rect.right() - 142., bottom - 23.),
                egui::vec2(140., 23.),
            ));
        }
        overlay
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn integral_visibility_never_changes_baseline_or_amplitude() {
        let rect = egui::Rect::from_min_size(Pos2::ZERO, egui::vec2(1000., 800.));
        for stacked in [false, true] {
            for count in [1, 3, 12] {
                for offset in [-2., -0.2, 0., 0.2, 2.] {
                    let a = lane_layout(rect, stacked, count, 0, offset, false);
                    let b = lane_layout(rect, stacked, count, 0, offset, true);
                    assert_eq!(a.baseline.to_bits(), b.baseline.to_bits());
                    assert_eq!(a.amplitude.to_bits(), b.amplitude.to_bits());
                    assert!(b.band.height() >= 0. && b.curve_band.height() >= 0.);
                    if b.band.height() >= 21. {
                        assert!(b.band.top() > b.baseline);
                    }
                    if b.curve_band.height() > 0. {
                        assert!(b.curve_band.bottom() < b.baseline);
                    }
                }
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
