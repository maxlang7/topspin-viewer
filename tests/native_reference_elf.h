/* Test-only reader for bytes at a virtual address in the original ELF. */
#include <elf.h>
#include <inttypes.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
static FILE *source;
static Elf64_Ehdr header;
static int reference_open(const char *path) {
    source=fopen(path,"rb");
    return source && fread(&header,sizeof header,1,source)==1 &&
        !memcmp(header.e_ident,ELFMAG,SELFMAG) && header.e_machine==EM_X86_64 && header.e_ident[EI_DATA]==ELFDATA2LSB;
}
static void read_address(uint64_t address,void *output,size_t size) {
    for(int i=0;i<header.e_phnum;i++) {
        Elf64_Phdr segment;
        if(fseek(source,(long)header.e_phoff+i*header.e_phentsize,SEEK_SET) || fread(&segment,sizeof segment,1,source)!=1)abort();
        if(segment.p_type==PT_LOAD && address>=segment.p_vaddr && address+size<=segment.p_vaddr+segment.p_filesz) {
            if(fseek(source,(long)(segment.p_offset+address-segment.p_vaddr),SEEK_SET) || fread(output,size,1,source)!=1)abort();
            return;
        }
    }
    fprintf(stderr,"Unmapped original address: 0x%" PRIx64 "\n",address);exit(2);
}
