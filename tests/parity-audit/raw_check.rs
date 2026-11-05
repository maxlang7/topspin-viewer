#![allow(dead_code)]
#[path = "../../rust/storage.rs"]
mod storage;

#[path = "../../rust/data.rs"]
mod data;
#[path = "../../rust/processing.rs"]
mod processing;
#[path = "../../rust/raw.rs"]
mod raw;
use std::{fs, path::Path};
fn main() {
    let root = std::env::args().nth(1).unwrap();
    let root = Path::new(&root);
    let mut samples = 0usize;
    let mut valid = 0;
    let mut rejected = 0;
    for line in fs::read_to_string(root.join("manifest.tsv"))
        .unwrap()
        .lines()
    {
        let c: Vec<_> = line.split('\t').collect();
        let result = data::load(Path::new(c[0]));
        if c[1] == "REJECT" {
            assert!(result.is_err(), "Rust accepted reference-rejected {}", c[0]);
            rejected += 1;
            continue;
        }
        let s = result.unwrap_or_else(|e| panic!("{}: {e}", c[0]));
        let bytes = fs::read(root.join(c[1])).unwrap();
        let n = u32::from_le_bytes(bytes[0..4].try_into().unwrap()) as usize;
        let m = u32::from_le_bytes(bytes[4..8].try_into().unwrap()) as usize;
        assert_eq!(s.real.len(), n);
        assert_eq!(s.imag.len(), m);
        let actual = std::iter::once(&s.left)
            .chain(std::iter::once(&s.right))
            .chain(&s.real)
            .chain(&s.imag);
        assert_eq!(bytes.len(), 8 + (n + m + 2) * 8);
        for (i, (value, expected)) in actual.zip(bytes[8..].chunks_exact(8)).enumerate() {
            assert_eq!(
                value.to_bits(),
                u64::from_le_bytes(expected.try_into().unwrap()),
                "{} sample {i}",
                c[0]
            );
            samples += 1;
        }
        valid += 1;
    }
    println!(
        "RAW_STACK_PARITY valid={valid} rejected={rejected} raw_bit_comparisons={samples} mismatches=0"
    );
    assert!(valid > 0);
}
