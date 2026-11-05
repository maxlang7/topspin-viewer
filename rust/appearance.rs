//! App appearance preferences are independent of scientific datasets and projects.
use super::*;
use std::{
    fs,
    path::{Path, PathBuf},
    time::{SystemTime, UNIX_EPOCH},
};
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub(super) enum Theme {
    #[default]
    GruvboxDark,
    Dark,
    Light,
    TopSpin,
}
#[derive(Clone, Copy)]
pub(super) struct Palette {
    pub panel: Color32,
    pub plot: Color32,
    pub grid: Color32,
    pub text: Color32,
    pub muted: Color32,
    pub border: Color32,
    pub accent: Color32,
    pub badge_text: Color32,
    pub series: [Color32; 6],
    pub error: Color32,
}
impl Theme {
    pub fn name(self) -> &'static str {
        match self {
            Self::GruvboxDark => "Gruvbox Dark",
            Self::Dark => "Dark",
            Self::Light => "Light",
            Self::TopSpin => "TopSpin",
        }
    }
    pub fn palette(self) -> Palette {
        let rgb = Color32::from_rgb;
        match self {
            Self::GruvboxDark => Palette {
                panel: rgb(40, 40, 40),
                plot: rgb(29, 32, 33),
                grid: rgb(50, 48, 47),
                text: rgb(235, 219, 178),
                muted: rgb(189, 174, 147),
                border: rgb(80, 73, 69),
                accent: COLORS[1],
                badge_text: rgb(29, 32, 33),
                series: COLORS,
                error: rgb(255, 128, 128),
            },
            Self::Dark => Palette {
                panel: rgb(35, 38, 43),
                plot: rgb(23, 25, 29),
                grid: rgb(52, 57, 64),
                text: rgb(230, 233, 238),
                muted: rgb(169, 178, 190),
                border: rgb(77, 85, 96),
                accent: rgb(246, 195, 76),
                badge_text: rgb(23, 25, 29),
                series: [
                    rgb(100, 181, 246),
                    rgb(246, 195, 76),
                    rgb(77, 208, 166),
                    rgb(206, 147, 216),
                    rgb(255, 155, 100),
                    rgb(186, 204, 109),
                ],
                error: rgb(255, 130, 130),
            },
            Self::TopSpin => Palette {
                panel: rgb(207, 207, 207),
                plot: Color32::WHITE,
                grid: rgb(245, 245, 245),
                text: rgb(51, 51, 51),
                muted: rgb(128, 128, 128),
                border: rgb(142, 142, 142),
                accent: rgb(5, 65, 105),
                badge_text: Color32::WHITE,
                series: [
                    Color32::BLUE,
                    rgb(255, 0, 0),
                    rgb(0, 128, 0),
                    rgb(128, 0, 128),
                    rgb(0, 160, 160),
                    rgb(180, 120, 0),
                ],
                error: rgb(180, 20, 20),
            },
            Self::Light => Palette {
                panel: rgb(245, 246, 248),
                plot: Color32::WHITE,
                grid: rgb(224, 228, 234),
                text: rgb(24, 28, 34),
                muted: rgb(83, 93, 106),
                border: rgb(186, 195, 206),
                accent: rgb(155, 94, 0),
                badge_text: Color32::WHITE,
                series: [
                    rgb(0, 86, 179),
                    rgb(155, 94, 0),
                    rgb(0, 115, 95),
                    rgb(153, 51, 102),
                    rgb(179, 60, 0),
                    rgb(85, 107, 0),
                ],
                error: rgb(185, 30, 40),
            },
        }
    }
    pub fn apply(self, ctx: &egui::Context) {
        let p = self.palette();
        let mut v = if matches!(self, Self::Light | Self::TopSpin) {
            egui::Visuals::light()
        } else {
            egui::Visuals::dark()
        };
        v.panel_fill = p.panel;
        v.window_fill = p.panel;
        v.extreme_bg_color = p.plot;
        v.override_text_color = Some(p.text);
        v.window_stroke = Stroke::new(1., p.border);
        if !matches!(self, Self::Light | Self::TopSpin) {
            v.selection.bg_fill = p.border;
            v.selection.stroke = Stroke::new(1., p.text);
        }
        if self == Self::TopSpin {
            v.selection.bg_fill = Color32::from_rgb(174, 200, 217);
            v.selection.stroke = Stroke::new(1., Color32::from_gray(51));
            v.widgets.inactive.corner_radius = egui::CornerRadius::ZERO;
            v.widgets.hovered.corner_radius = egui::CornerRadius::ZERO;
            v.widgets.active.corner_radius = egui::CornerRadius::ZERO;
            v.widgets.inactive.weak_bg_fill = Color32::WHITE;
        }
        ctx.set_visuals(v);
        ctx.style_mut(|style| {
            style.text_styles = egui::Style::default().text_styles;
            if self == Self::TopSpin {
                for text in [
                    egui::TextStyle::Body,
                    egui::TextStyle::Button,
                    egui::TextStyle::Small,
                ] {
                    style
                        .text_styles
                        .insert(text, egui::FontId::proportional(12.));
                }
            }
        });
        let mut fonts = egui::FontDefinitions::default();
        if self == Self::TopSpin {
            fonts.font_data.insert(
                "topspin-arial".into(),
                std::sync::Arc::new(egui::FontData::from_static(include_bytes!(
                    "../assets/fonts/arial.ttf"
                ))),
            );
            fonts.font_data.insert(
                "topspin-courier".into(),
                std::sync::Arc::new(egui::FontData::from_static(include_bytes!(
                    "../assets/fonts/cour.ttf"
                ))),
            );
            fonts.font_data.insert(
                "topspin-bold".into(),
                std::sync::Arc::new(egui::FontData::from_static(include_bytes!(
                    "../assets/fonts/courbd.ttf"
                ))),
            );
            fonts.families.insert(
                egui::FontFamily::Name("topspin-bold".into()),
                vec!["topspin-bold".into()],
            );
            fonts
                .families
                .get_mut(&egui::FontFamily::Proportional)
                .unwrap()
                .insert(0, "topspin-arial".into());
            fonts
                .families
                .get_mut(&egui::FontFamily::Monospace)
                .unwrap()
                .insert(0, "topspin-courier".into());
        }
        ctx.set_fonts(fonts);
    }
}
#[derive(Serialize, Deserialize)]
struct Preferences {
    version: u32,
    theme: Theme,
}
fn path() -> PathBuf {
    let root = std::env::var_os("XDG_CONFIG_HOME")
        .filter(|p| !p.is_empty())
        .map(PathBuf::from)
        .unwrap_or_else(|| {
            PathBuf::from(std::env::var_os("HOME").unwrap_or_default()).join(".config")
        });
    root.join("nmr-viewer/settings.json")
}
pub(super) fn load() -> Result<Theme, String> {
    load_from(&path())
}
fn load_from(path: &Path) -> Result<Theme, String> {
    let bytes = match fs::read(path) {
        Ok(b) => b,
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(Theme::default()),
        Err(e) => return Err(e.to_string()),
    };
    let p: Preferences = serde_json::from_slice(&bytes).map_err(|e| e.to_string())?;
    if p.version != 1 {
        return Err("Unsupported appearance settings version".into());
    }
    Ok(p.theme)
}
fn save_to(path: &Path, theme: Theme) -> Result<(), String> {
    fs::create_dir_all(path.parent().ok_or("Invalid preferences location")?)
        .map_err(|e| e.to_string())?;
    let temp = path.with_extension(format!(
        "{}.tmp",
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_nanos()
    ));
    storage::write_new(
        &temp,
        &serde_json::to_vec_pretty(&Preferences { version: 1, theme })
            .map_err(|e| e.to_string())?,
    )?;
    fs::rename(temp, path).map_err(|e| e.to_string())
}
impl App {
    pub(super) fn apply_theme(&mut self, ctx: &egui::Context, theme: Theme) {
        self.theme = theme;
        theme.apply(ctx);
        for t in &mut self.traces {
            t.cache_key.clear();
            t.cache.clear();
        }
    }
    pub(super) fn set_theme(&mut self, ctx: &egui::Context, theme: Theme) {
        if self.theme == theme {
            return;
        }
        self.apply_theme(ctx, theme);
        if let Err(e) = save_to(&path(), theme) {
            self.status = format!("Could not save appearance: {e}");
        }
    }
    pub(super) fn appearance_menu(&mut self, ui: &mut egui::Ui) {
        let ctx = ui.ctx().clone();
        for theme in [
            Theme::GruvboxDark,
            Theme::Dark,
            Theme::Light,
            Theme::TopSpin,
        ] {
            if ui
                .selectable_label(self.theme == theme, theme.name())
                .clicked()
            {
                self.set_theme(&ctx, theme);
                ui.close();
            }
        }
    }
    pub(super) fn palette(&self) -> Palette {
        self.theme.palette()
    }
    pub(super) fn colors(&self) -> [Color32; 6] {
        self.palette().series
    }
    pub(super) fn settings_window(&mut self, ctx: &egui::Context) {
        if !self.show_settings {
            return;
        }
        let mut open = true;
        let before = self.theme;
        egui::Window::new("Settings")
            .open(&mut open)
            .default_width(330.)
            .resizable(false)
            .show(ctx, |ui| {
                ui.heading("Appearance");
                for theme in [
                    Theme::GruvboxDark,
                    Theme::Dark,
                    Theme::Light,
                    Theme::TopSpin,
                ] {
                    ui.selectable_value(&mut self.theme, theme, theme.name());
                }
                ui.separator();
                ui.label("Theme changes apply immediately and are remembered.");
                ui.weak("Light uses a conventional white and gray interface with blue accents.");
            });
        self.show_settings = open;
        if self.theme != before {
            self.theme.apply(ctx);
            for trace in &mut self.traces {
                trace.cache_key.clear();
                trace.cache.clear();
            }
            if let Err(e) = save_to(&path(), self.theme) {
                self.status = format!("Could not save appearance preference: {e}");
            }
        }
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn preferences_roundtrip_and_light_uses_a_white_plot() {
        let path = std::env::temp_dir()
            .join(format!(
                "nmr-theme-{}",
                SystemTime::now()
                    .duration_since(UNIX_EPOCH)
                    .unwrap()
                    .as_nanos()
            ))
            .join("settings.json");
        assert_eq!(load_from(&path).unwrap(), Theme::GruvboxDark);
        for theme in [
            Theme::GruvboxDark,
            Theme::Dark,
            Theme::Light,
            Theme::TopSpin,
        ] {
            save_to(&path, theme).unwrap();
            assert_eq!(load_from(&path).unwrap(), theme);
        }
        let p = Theme::Light.palette();
        assert_eq!(p.plot, Color32::WHITE);
        for c in p.series.into_iter().chain([p.text, p.muted, p.accent]) {
            assert!(c.r().max(c.g()).max(c.b()) < 200);
        }
    }
}
