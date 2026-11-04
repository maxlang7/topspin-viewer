#![allow(dead_code)]
#[path="../../rust/data.rs"]mod data;
#[path="../../rust/processing.rs"]mod processing;
use std::{path::Path,fs};
fn main(){let root=std::env::args().nth(1).unwrap();let root=Path::new(&root);let mut compared=0;let mut cases=0;
for line in fs::read_to_string(root.join("saved.tsv")).unwrap().lines(){let c:Vec<_>=line.split('\t').collect();let s=data::load(Path::new(c[0])).unwrap();let r=s.saved_regions[c[1].parse::<usize>().unwrap()];let integral=processing::region(&s.real,[s.left,s.right],[r[0],r[1]],r[2],r[3],false,1).unwrap();let mut values=vec![r[0],r[1],r[2],r[3],s.integral_scale,integral.value()*s.integral_scale];values.extend(integral.curve);let raw=fs::read(root.join(c[2])).unwrap();assert_eq!(values.len()*8,raw.len());for(a,b)in values.iter().zip(raw.chunks_exact(8)){compared+=1;assert_eq!(a.to_bits(),u64::from_le_bytes(b.try_into().unwrap()),"saved region {line}");}cases+=1;}
println!("SAVED_REGION_ORACLE cases={cases} raw_bit_comparisons={compared} mismatches=0");assert!(cases>0);}
