#define _GNU_SOURCE
#include "native_reference_elf.h"
#include <sys/mman.h>

/* Entire original extrema routine, copied unchanged. No external calls or stubs. */
int main(int argc,char **argv) {
    if(argc!=2 || !reference_open(argv[1]))return 2;
    unsigned char *code=mmap(NULL,4096,PROT_READ|PROT_WRITE,MAP_PRIVATE|MAP_ANONYMOUS,-1,0);
    if(code==MAP_FAILED)return 2;
    read_address(0x56a810,code,0xfe);fclose(source);
    if(mprotect(code,4096,PROT_READ|PROT_EXEC))return 2;
    void (*original)(double,double,void*,double*,double*)=(void (*)(double,double,void*,double*,double*))code;
    int count,type;
    while(scanf("%d %d",&count,&type)==2) {
        if(count<0 || count>1048576 || (type!=0 && type!=2))return 2;
        size_t bytes=(size_t)count*(type==2?8:4);void *data=malloc(bytes?bytes:1);if(!data)return 2;
        for(int i=0;i<count;i++) {
            uint64_t bits;if(scanf("%" SCNx64,&bits)!=1)return 2;double value;memcpy(&value,&bits,8);
            if(type==2)memcpy((char*)data+8*i,&value,8);
            else {int32_t integer=(int32_t)value;memcpy((char*)data+4*i,&integer,4);}
        }
        unsigned char fixture[0x120]={0};int length=(int)bytes;
        memcpy(fixture+0xec,&length,4);memcpy(fixture+0xf8,&data,sizeof data);memcpy(fixture+0x10c,&type,4);
        double low,high;original(0,2147483647.0,fixture,&low,&high);uint64_t a,b;memcpy(&a,&low,8);memcpy(&b,&high,8);
        printf("%016" PRIx64 " %016" PRIx64 "\n",a,b);fflush(stdout);free(data);
    }
    munmap(code,4096);return 0;
}
