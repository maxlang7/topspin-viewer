//! Folder groups mirror acquisition locations; leaves remain processing-number paths.
use super::*;
use std::collections::{BTreeMap, HashSet};
#[derive(Default)]
struct Folder {
    path: PathBuf,
    children: BTreeMap<PathBuf, Folder>,
    spectra: Vec<PathBuf>,
}
#[derive(Clone)]
struct Row {
    path: PathBuf,
    depth: usize,
    folder: bool,
    label: String,
}
#[derive(Default)]
pub(super) struct Browser {
    paths: Vec<PathBuf>,
    filter: String,
    natural: bool,
    tree: Folder,
    collapsed: HashSet<PathBuf>,
    rows: Vec<Row>,
}
impl Browser {
    fn prepare(&mut self, paths: &[PathBuf], filter: &str, natural: bool) {
        if self.paths == paths && self.filter == filter && self.natural == natural {
            return;
        }
        self.paths = paths.to_vec();
        self.filter = filter.into();
        self.natural = natural;
        let containers = paths
            .iter()
            .map(|p| p.ancestors().nth(4).unwrap_or(p))
            .collect::<Vec<_>>();
        let mut root = containers
            .first()
            .copied()
            .unwrap_or(std::path::Path::new(""));
        while containers.iter().any(|p| !p.starts_with(root)) {
            let Some(parent) = root.parent() else {
                break;
            };
            root = parent;
        }
        self.tree = Folder {
            path: root.to_owned(),
            ..Default::default()
        };
        let filter = filter.to_lowercase();
        for (path, container) in paths.iter().zip(containers) {
            if !path.to_string_lossy().to_lowercase().contains(&filter) {
                continue;
            }
            let mut node = &mut self.tree;
            let mut current = root.to_owned();
            for part in container
                .strip_prefix(root)
                .unwrap_or(container)
                .components()
            {
                current.push(part);
                node = node
                    .children
                    .entry(current.clone())
                    .or_insert_with(|| Folder {
                        path: current.clone(),
                        ..Default::default()
                    });
            }
            node.spectra.push(path.clone());
        }
        self.flatten();
    }
    fn flatten(&mut self) {
        fn visit(
            node: &Folder,
            depth: usize,
            collapsed: &HashSet<PathBuf>,
            searching: bool,
            natural: bool,
            rows: &mut Vec<Row>,
        ) {
            if node.children.is_empty() && node.spectra.is_empty() {
                return;
            }
            let name = node
                .path
                .file_name()
                .map(|n| n.to_string_lossy().into_owned())
                .unwrap_or_else(|| node.path.display().to_string());
            let name = if name.is_empty() {
                "Datasets".to_owned()
            } else {
                name
            };
            let closed = !searching && collapsed.contains(&node.path);
            rows.push(Row {
                path: node.path.clone(),
                depth,
                folder: true,
                label: format!("{} {}", if closed { "▸" } else { "▾" }, name),
            });
            if closed {
                return;
            }
            let mut children = node.children.values().collect::<Vec<_>>();
            children.sort_by(|a, b| {
                let a = a.path.file_name().unwrap_or_default().to_string_lossy();
                let b = b.path.file_name().unwrap_or_default().to_string_lossy();
                if natural {
                    data::natural_cmp(&a, &b)
                } else {
                    a.to_lowercase().cmp(&b.to_lowercase())
                }
            });
            for child in children {
                visit(child, depth + 1, collapsed, searching, natural, rows);
            }
            let mut spectra = node.spectra.clone();
            data::sort_datasets(&mut spectra, natural);
            for path in spectra {
                let exp = path
                    .ancestors()
                    .nth(2)
                    .and_then(|p| p.file_name())
                    .unwrap_or_default()
                    .to_string_lossy();
                let proc = path.file_name().unwrap_or_default().to_string_lossy();
                let label = format!(
                    "{} / {} / {}{}",
                    data::dataset_name(&path),
                    exp,
                    proc,
                    if path.join("1r").is_file() {
                        ""
                    } else {
                        " (FID)"
                    }
                );
                rows.push(Row {
                    path,
                    depth: depth + 1,
                    folder: false,
                    label,
                });
            }
        }
        self.rows.clear();
        visit(
            &self.tree,
            0,
            &self.collapsed,
            !self.filter.is_empty(),
            self.natural,
            &mut self.rows,
        );
    }
}
impl App {
    pub(super) fn dataset_tree(&mut self, ui: &mut egui::Ui, ctx: &egui::Context, max_height: f32) {
        self.browser
            .prepare(&self.found, &self.filter, self.natural_sort);
        let mut toggle = None;
        let mut open = None;
        let height = ui
            .text_style_height(&egui::TextStyle::Body)
            .max(ui.spacing().interact_size.y);
        egui::ScrollArea::vertical()
            .id_salt("folder-datasets")
            .max_height(max_height.max(1.))
            .auto_shrink([false, false])
            .scroll_bar_visibility(egui::scroll_area::ScrollBarVisibility::AlwaysVisible)
            .show_rows(ui, height, self.browser.rows.len(), |ui, range| {
                for index in range {
                    let row = &self.browser.rows[index];
                    ui.horizontal(|ui| {
                        ui.add_space((row.depth as f32 * 10.).min(50.));
                        let r = ui
                            .add_sized(
                                [ui.available_width(), height],
                                egui::Button::selectable(false, &row.label)
                                    .wrap_mode(egui::TextWrapMode::Truncate),
                            )
                            .on_hover_text(format!(
                                "{}\n{}",
                                row.path.display(),
                                if row.folder {
                                    "Click to expand or collapse"
                                } else {
                                    "Click: open · Hover + S: stack"
                                }
                            ));
                        if row.folder {
                            if r.clicked() {
                                toggle = Some(row.path.clone());
                            }
                        } else {
                            if r.hovered() {
                                self.hovered_sidebar = Some((row.path.clone(), r.rect));
                            }
                            let stack = stack::stack_shortcut(&r, ctx);
                            if r.clicked() || stack {
                                open = Some((row.path.clone(), stack));
                            }
                        }
                    });
                }
            });
        if let Some(path) = toggle {
            if !self.browser.collapsed.remove(&path) {
                self.browser.collapsed.insert(path);
            }
            self.browser.flatten();
        }
        if let Some((path, stack)) = open {
            self.sidebar_open(path, stack, ctx.clone());
        }
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn projects_nested_folders_sort_filter_and_collapse_without_losing_datasets() {
        let paths = [
            "/library/Project X/MLN1P115/10/pdata/1",
            "/library/Project X/MLN1P85/10/pdata/1",
            "/library/Project Y/Subfolder/MLN1P85/20/pdata/2",
        ]
        .map(PathBuf::from);
        let mut b = Browser::default();
        b.prepare(&paths, "", true);
        assert_eq!(
            b.rows
                .iter()
                .filter(|r| !r.folder)
                .map(|r| r.path.clone())
                .collect::<Vec<_>>(),
            vec![paths[1].clone(), paths[0].clone(), paths[2].clone()]
        );
        b.collapsed.insert("/library/Project X".into());
        b.flatten();
        assert_eq!(b.rows.iter().filter(|r| !r.folder).count(), 1);
        b.prepare(&paths, "MLN1P85", true);
        assert_eq!(b.rows.iter().filter(|r| !r.folder).count(), 2);
        assert!(b.rows.iter().any(|r| r.label.contains("Subfolder")));
        b.prepare(&paths, "", true);
        assert_eq!(b.rows.iter().filter(|r| !r.folder).count(), 1);
        b.collapsed.clear();
        b.prepare(&paths, "", false);
        b.flatten();
        assert_eq!(b.rows.iter().find(|r| !r.folder).unwrap().path, paths[0]);
    }
}
