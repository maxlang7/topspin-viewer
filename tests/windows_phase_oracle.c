/* Test-only execution of the local Windows numerical routine.
 * The mapped copy never runs TopSpin's entry point. Only in-process IAT slots
 * for its two public CRT math imports are resolved. Installed files are untouched.
 */
#include <windows.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

typedef void (__cdecl *phase_fn)(double*, int, double*, double, double, unsigned);

int main(int argc, char** argv) {
    if (argc != 4 && argc != 5) return 2;
    HMODULE image = LoadLibraryExA(argv[1], NULL, DONT_RESOLVE_DLL_REFERENCES);
    if (!image) { fprintf(stderr,"LoadLibraryEx failed: %lu\n",GetLastError()); return 3; }
    fprintf(stderr,"Mapped numeric reference image at %p\n",(void*)image);
    unsigned char* base = (unsigned char*)image;
    // LoadLibrary maps EXEs as images without resolving their imports or
    // absolute code references. Relocate this private mapping if necessary.
    DWORD delta = (DWORD)(uintptr_t)base - 0x400000;
    if (*(DWORD*)(base+0x8c87) == 0x456878 && delta != 0) {
        IMAGE_DOS_HEADER* dos=(IMAGE_DOS_HEADER*)base;
        IMAGE_NT_HEADERS32* nt=(IMAGE_NT_HEADERS32*)(base+dos->e_lfanew);
        IMAGE_DATA_DIRECTORY relocation=nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_BASERELOC];
        unsigned char* block_start=base+relocation.VirtualAddress;
        unsigned char* block_end=block_start+relocation.Size;
        while(block_start<block_end) {
            IMAGE_BASE_RELOCATION* block=(IMAGE_BASE_RELOCATION*)block_start;
            if(block->SizeOfBlock<8 || block_start+block->SizeOfBlock>block_end) return 11;
            WORD* item=(WORD*)(block_start+8);
            for(unsigned i=0;i<(block->SizeOfBlock-8)/2;i++) {
                if((item[i]>>12)==IMAGE_REL_BASED_HIGHLOW) {
                    DWORD* location=(DWORD*)(base+block->VirtualAddress+(item[i]&0xfff));
                    DWORD old;
                    if(!VirtualProtect(location,4,PAGE_EXECUTE_READWRITE,&old)) return 12;
                    *location+=delta;
                    VirtualProtect(location,4,old,&old);
                } else if((item[i]>>12)!=IMAGE_REL_BASED_ABSOLUTE) return 13;
            }
            block_start+=block->SizeOfBlock;
        }
    }
    if(*(DWORD*)(base+0x8c87)!=(DWORD)(uintptr_t)(base+0x56878)) {
        fprintf(stderr,"Numeric code references are not correctly relocated\n"); return 14;
    }
    HMODULE math = LoadLibraryA("api-ms-win-crt-math-l1-1-0.dll");
    if (!math) { fprintf(stderr,"CRT load failed\n"); return 4; }
    DWORD previous;
    if (!VirtualProtect(base+0x56328,16,PAGE_READWRITE,&previous)) return 5;
    FARPROC sine = GetProcAddress(math,"_libm_sse2_sin_precise");
    FARPROC cosine = GetProcAddress(math,"_libm_sse2_cos_precise");
    if (!sine || !cosine) { fprintf(stderr,"CRT precise imports unavailable\n"); return 6; }
    *(FARPROC*)(base+0x56328) = sine;
    *(FARPROC*)(base+0x56330) = cosine;
    FILE* input = fopen(argv[2],"rb");
    if (!input) return 7;
    uint32_t n;
    double ph0, ph1;
    if (fread(&n,4,1,input)!=1 || fread(&ph0,8,1,input)!=1 || fread(&ph1,8,1,input)!=1
        || n>16777216 || n==0) return 8;
    double* data=malloc((size_t)n*16);
    if (!data || fread(data,16,n,input)!=n) return 9;
    fclose(input);
    phase_fn phase=(phase_fn)(base+0x8c70);
    if(argc==5) {
        double angle=0.0;
        double step=6.283185307179586/(double)n;
        for(uint32_t i=0;i<n;i++) {
            double value=0.0;
            if(i!=0) {
                __asm__ __volatile__("movsd %1, %%xmm0; call *%2; movsd %%xmm0, %0"
                    : "=m"(value) : "m"(angle), "r"(sine)
                    : "eax","ecx","edx","xmm0","xmm1","xmm2","xmm3","xmm4","xmm5","memory");
            }
            data[i]=value;
            angle+=step;
        }
        n/=2;
    } else {
        phase(data,n,NULL,ph0,ph1,0);
    }
    FILE* output = fopen(argv[3],"wb");
    if (!output || fwrite(data,16,n,output)!=n) return 10;
    fclose(output);
    free(data);
    return 0;
}
