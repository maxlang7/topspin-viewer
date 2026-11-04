#![allow(dead_code)]
#[path="../../rust/processing.rs"] mod processing;
#[path="../../rust/data.rs"] mod data;
fn main(){
    for root in std::env::args().skip(1){
        for path in data::discover(std::path::Path::new(&root)).unwrap(){
            match data::load(&path){Ok(s)=>{
                let n=s.real.len();
                for (label,start,end) in [("full",0,n-1),("middle",n/4,n/2),("narrow",n/2,(n/2+3).min(n-1))]{
                    let a=s.ppm(start);let b=s.ppm(end);let phased=processing::phase(&s.real,&s.imag,0.,0.,s.ft_size);let value=processing::region(&phased,[s.left,s.right],[a,b],0.,0.,false,1).unwrap().value();
                    println!("{}\t{}\t{:.17e}\t{:.17e}\t{:.17e}\t{:016x}",path.display(),label,a,b,value,value.to_bits());
                }
            },Err(e)=>eprintln!("SKIP invalid {}: {e}",path.display())}
        }
    }
}
