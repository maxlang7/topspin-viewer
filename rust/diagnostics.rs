//! Stage timings and a UI heartbeat distinguish application work from display stalls.
use super::*;
use std::sync::{
    OnceLock,
    atomic::{AtomicBool, AtomicU64, AtomicUsize, Ordering},
};
static MONITOR: OnceLock<Monitor> = OnceLock::new();
const STAGES: [&str; 6] = [
    "event loop / display",
    "frame",
    "processing",
    "plot",
    "autosave",
    "tool panels",
];
struct Monitor {
    epoch: Instant,
    pulse: AtomicU64,
    stage: AtomicUsize,
    stop: AtomicBool,
}
pub(super) fn start(ctx: egui::Context) {
    if MONITOR
        .set(Monitor {
            epoch: Instant::now(),
            pulse: AtomicU64::new(0),
            stage: AtomicUsize::new(0),
            stop: AtomicBool::new(false),
        })
        .is_err()
    {
        return;
    }
    std::thread::spawn(move || {
        let m = MONITOR.get().unwrap();
        let mut last_report = 0;
        while !m.stop.load(Ordering::Relaxed) {
            std::thread::sleep(std::time::Duration::from_secs(2));
            if m.stop.load(Ordering::Relaxed) {
                break;
            }
            let now = m.epoch.elapsed().as_millis() as u64;
            let age = now.saturating_sub(m.pulse.load(Ordering::Relaxed));
            if age > 6000 && now - last_report > 10000 {
                eprintln!(
                    "NMR_UI_HEARTBEAT_LATE age_ms={age} stage={}",
                    STAGES[m.stage.load(Ordering::Relaxed)]
                );
                last_report = now;
            }
            ctx.request_repaint();
        }
    });
}
pub(super) fn stop() {
    if let Some(m) = MONITOR.get() {
        m.stop.store(true, Ordering::Relaxed);
    }
}
pub(super) struct Stage {
    start: Instant,
    index: usize,
    previous: usize,
}
pub(super) fn stage(index: usize) -> Stage {
    let previous = MONITOR.get().map_or(0, |m| {
        m.pulse
            .store(m.epoch.elapsed().as_millis() as u64, Ordering::Relaxed);
        m.stage.swap(index, Ordering::Relaxed)
    });
    Stage {
        start: Instant::now(),
        index,
        previous,
    }
}
impl Drop for Stage {
    fn drop(&mut self) {
        if self.start.elapsed().as_millis() >= 250 {
            eprintln!(
                "NMR_SLOW stage={} duration_ms={}",
                STAGES[self.index],
                self.start.elapsed().as_millis()
            );
        }
        if let Some(m) = MONITOR.get() {
            m.stage.store(self.previous, Ordering::Relaxed);
            m.pulse
                .store(m.epoch.elapsed().as_millis() as u64, Ordering::Relaxed);
        }
    }
}
