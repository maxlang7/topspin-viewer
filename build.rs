fn main() {
    println!("cargo:rerun-if-changed=native/kernel.c");
    println!("cargo:rerun-if-changed=native/rust_raw.c");
    cc::Build::new()
        .files(["native/kernel.c", "native/rust_raw.c"])
        .opt_level(2)
        .flag("-ffp-contract=off")
        .warnings(false)
        .compile("nmr_fft");
    println!("cargo:rustc-link-lib=m");
}
