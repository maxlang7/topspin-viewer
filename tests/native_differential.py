"""Bitwise comparisons of independent C kernels with the installed ELF oracle.

These tests establish helper equivalence only. They do not certify Windows
TopSpin's full FID-to-file pipeline.
"""
import ctypes as c
from pathlib import Path
import random
import sys

ROOT = Path(__file__).resolve().parents[1]
VENDOR = Path("/home/maxlang/.local/share/topspin/opt/topspin5.0.0/prog/shlib/libArrayProcessor.so")
D = c.c_double
P = c.POINTER(D)
original = c.CDLL(str(VENDOR))
rebuilt = c.CDLL(str(ROOT / "build/native/libkernel.so"))
random_source = random.Random(0x544F505350494E)
comparisons = 0
cases = 0


def pair(name, arguments, result=None, local_offset=None):
    if local_offset is None:
        vendor = getattr(original, name)
        vendor.argtypes = arguments
        vendor.restype = result
    else:
        # Read-only use of a local, non-exported numerical function. No patching.
        base = c.cast(original.softproc, c.c_void_p).value - 0xBFB0
        vendor = c.CFUNCTYPE(result, *arguments)(base + local_offset)
    ours = getattr(rebuilt, "reconstructed_" + {"p1_pc": "phase"}.get(name, name))
    ours.argtypes = arguments
    ours.restype = result
    return vendor, ours


def equal(a, b, label):
    global comparisons
    raw_a = bytes(a)
    raw_b = bytes(b)
    if raw_a != raw_b:
        for i in range(len(raw_a) // 8):
            if raw_a[i*8:(i+1)*8] != raw_b[i*8:(i+1)*8]:
                raise AssertionError(f"{label} differs at double {i}: {a[i].hex()} vs {b[i].hex()}")
        raise AssertionError(label + " differs")
    comparisons += len(raw_a) // 8


def main():
    global cases
    phase = pair("p1_pc", [D, D, P, c.c_int, P, c.c_ulong], local_offset=0x28D0)
    scale = pair("getscale", [P, c.c_int], c.c_int)
    sine = pair("setsin4", [P, c.c_uint])
    fft = pair("radix4", [P, c.c_long, c.c_uint, c.c_int, c.c_ubyte])
    for n in [4, 16, 64, 256, 4096, 65536]:
        seed = [random_source.uniform(-1e9,1e9) for _ in range(n*2)]
        for p0, p1 in [(0,0), (12.345,-23.456), (-41.5206061238053,-11.9209746984819), (180,720)]:
            a, b = (D*(n*2))(*seed), (D*(n*2))(*seed)
            phase[0](p0,p1/n,a,n,None,0)
            phase[1](p0,p1/n,b,n,None,0)
            equal(a,b,"phase")
            if scale[0](a,n*2) != scale[1](b,n*2):
                raise AssertionError("scale exponent mismatch")
            cases += 1
        a, b = (D*n)(), (D*n)()
        sine[0](a,n)
        sine[1](b,n)
        equal(a,b,"sine table")
        x, y = (D*(n*2))(*seed), (D*(n*2))(*seed)
        fft[0](x,c.addressof(a),n,n,0)
        fft[1](y,c.addressof(b),n,n,0)
        equal(x,y,"radix-4 FFT")
        cases += 1
    print(f"PASS {cases} native helper cases, {comparisons} bit-for-bit double comparisons")


if __name__ == "__main__":
    main()
