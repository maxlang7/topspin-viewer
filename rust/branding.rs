use eframe::egui::{self, Color32, Pos2, Rect, Sense, Stroke, Vec2};

/// Paint the desktop icon in a square, using the coordinates from nmr-viewer.svg.
pub(super) fn paint_icon(painter: &egui::Painter, rect: Rect) {
    let scale = rect.width().min(rect.height()) / 64.0;
    let origin = rect.center() - Vec2::splat(32.0 * scale);
    let point = |x: f32, y: f32| Pos2::new(origin.x + x * scale, origin.y + y * scale);
    let square = Rect::from_min_size(origin, Vec2::splat(64.0 * scale));
    painter.rect_filled(square, 12.0 * scale, Color32::from_rgb(0x1d, 0x20, 0x21));
    let points = [
        (6.0, 46.0),
        (17.0, 46.0),
        (21.0, 40.0),
        (25.0, 46.0),
        (29.0, 46.0),
        (32.0, 13.0),
        (35.0, 46.0),
        (38.0, 46.0),
        (43.0, 32.0),
        (47.0, 46.0),
        (58.0, 46.0),
    ];
    painter.add(egui::Shape::line(
        points.into_iter().map(|(x, y)| point(x, y)).collect(),
        Stroke::new(3.0 * scale, Color32::from_rgb(0x8e, 0xc0, 0x7c)),
    ));
}

pub(super) fn icon(ui: &mut egui::Ui, size: f32) -> egui::Response {
    let (rect, response) = ui.allocate_exact_size(Vec2::splat(size), Sense::hover());
    if ui.is_rect_visible(rect) {
        paint_icon(ui.painter(), rect);
    }
    response.on_hover_text("NMR Viewer")
}
