#define _GNU_SOURCE
#include "native_reference_elf.h"
#include <inttypes.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>

/* Test only. The original arithmetic instructions are unchanged. Parameter-cache
 * refresh, string allocation and cleanup calls are stubbed against an already
 * populated fixture. This checks scalar axis arithmetic, not file loading. */
typedef struct {char *unit;int64_t points;double lower,upper;unsigned char continuous;} Scale;
static void relative(unsigned char *code,size_t displacement,size_t next,size_t target) {
    int32_t delta=(int32_t)((int64_t)target-(int64_t)next);memcpy(code+displacement,&delta,sizeof delta);
}
int main(int argc,char **argv) {
    if(argc!=2)return 2;
    if(!reference_open(argv[1]))return 2;
    unsigned char *code=mmap(NULL,4096,PROT_READ|PROT_WRITE,MAP_PRIVATE|MAP_ANONYMOUS,-1,0);
    if(code==MAP_FAILED)return 2;
    read_address(0x63c340,code,0xda);
    const size_t refresh[]={0xf,0x25,0x4d,0x60,0xc3};
    for(size_t i=0;i<sizeof refresh/sizeof *refresh;i++) {
        size_t call=refresh[i];if(code[call]!=0xe8)abort();relative(code,call+1,call+5,0x200);
    }
    code[0x200]=0xc3; /* populated fixture requires no cache refresh or cleanup */
    code[0x210]=0x48;code[0x211]=0x89;code[0x212]=0xf8;code[0x213]=0xc3; /* return unit pointer */
    if(code[0x9d]!=0xe8)abort();
    relative(code,0x9e,0xa2,0x210);
    read_address(0x67ff18,code+0x300,8);read_address(0x6c4048,code+0x308,8);read_address(0x6ccc47,code+0x310,2);
    relative(code,0x3f,0x43,0x300);relative(code,0x94,0x98,0x308);relative(code,0x81,0x85,0x310);
    fclose(source);
    if(mprotect(code,4096,PROT_READ|PROT_EXEC))return 2;
    void (*original)(Scale *,unsigned char *)=(void (*)(Scale *,unsigned char *))code;
    unsigned char *parameters=calloc(1,0xb000);if(!parameters)return 2;
    void *vtable[6]={0};vtable[5]=code+0x200;memcpy(parameters,&(void *){vtable},sizeof(void *));
    double sfo1,sw;int td,loop;
    while(scanf("%la %la %d %d",&sfo1,&sw,&td,&loop)==4) {
        memcpy(parameters+0x8ba8,&sfo1,8);memcpy(parameters+0xa3d0,&sw,8);
        memcpy(parameters+0xa478,&td,4);memcpy(parameters+0x6810,&loop,4);
        Scale output={0};original(&output,parameters);uint64_t lower,upper;
        memcpy(&lower,&output.lower,8);memcpy(&upper,&output.upper,8);
        if(strcmp(output.unit,"s") || output.continuous!=1)abort();
        printf("%" PRId64 " %016" PRIx64 " %016" PRIx64 "\n",output.points,lower,upper);fflush(stdout);
    }
    free(parameters);munmap(code,4096);return 0;
}
