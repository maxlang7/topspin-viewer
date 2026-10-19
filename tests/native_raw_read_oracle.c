#define _GNU_SOURCE
#include "native_reference_elf.h"
#include <sys/mman.h>
#include <unistd.h>

/* Original read routine and byte-swap helpers. Only read@plt is relocated to libc read. */
int main(int argc,char **argv) {
    if(argc!=2 || !reference_open(argv[1]))return 2;
    unsigned char *code=mmap(NULL,4096,PROT_READ|PROT_WRITE,MAP_PRIVATE|MAP_ANONYMOUS,-1,0);
    if(code==MAP_FAILED)return 2;
    read_address(0x568340,code,0x175);fclose(source);
    if(code[0xb9]!=0xe8)return 2;
    int32_t displacement=0x800-(0xb9+5);memcpy(code+0xba,&displacement,4);
    code[0x800]=0x48;code[0x801]=0xb8;uintptr_t target=(uintptr_t)read;memcpy(code+0x802,&target,8);
    code[0x80a]=0xff;code[0x80b]=0xe0;
    if(mprotect(code,4096,PROT_READ|PROT_EXEC))return 2;
    uint32_t (*original)(void*)=(uint32_t (*)(void*))(code+0xa0);
    int count,type,order;
    while(scanf("%d %d %d",&count,&type,&order)==3) {
        if(count<1 || count>1048576 || (type!=0 && type!=2) || (order!=0 && order!=1))return 2;
        size_t width=type==2?8:4,length=(size_t)count*width;
        unsigned char *encoded=malloc(length),*decoded=malloc(length);FILE *file=tmpfile();
        if(!encoded || !decoded || !file)return 2;
        for(int i=0;i<count;i++) {
            uint64_t bits;if(scanf("%" SCNx64,&bits)!=1)return 2;
            for(size_t j=0;j<width;j++)encoded[i*width+j]=(unsigned char)(bits>>(8*j));
        }
        if(fwrite(encoded,1,length,file)!=length || fflush(file) || fseek(file,0,SEEK_SET))return 2;
        unsigned char fixture[48]={0};int fd=fileno(file),bytes=(int)length;
        memcpy(fixture,&fd,4);memcpy(fixture+4,&bytes,4);memcpy(fixture+12,&bytes,4);
        memcpy(fixture+16,&decoded,sizeof decoded);memcpy(fixture+36,&type,4);memcpy(fixture+40,&order,4);
        if(original(fixture)!=(uint32_t)length)return 2;
        for(int i=0;i<count;i++) {
            double value;
            if(type==2)memcpy(&value,decoded+i*8,8);
            else {int32_t integer;memcpy(&integer,decoded+i*4,4);value=integer;}
            uint64_t bits;memcpy(&bits,&value,8);printf("%016" PRIx64 " ",bits);
        }
        putchar('\n');fflush(stdout);fclose(file);free(encoded);free(decoded);
    }
    munmap(code,4096);return 0;
}
