#include <math.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
void reconstructed_phase(double,double,double*,int,double*,unsigned long);
int reconstructed_getscale(double*,int);
void reconstructed_convdio(unsigned long,int,int);
void reconstructed_bitrev(long,unsigned int);
void reconstructed_bitdigrev(long,int);
void reconstructed_setsin4(double*,unsigned int);
void reconstructed_radix4(double*,long,unsigned int,int,unsigned char);
static double positive_angle(double value){double remainder=fmod(value,360.0);return remainder<0?remainder+360.0:remainder;}
/* Thin non-JNI entry point for the existing independently reconstructed FFT.
 * Keep arithmetic identical to the validated JNI adapter; input/output owned by Rust. */
int nmr_process_raw(const double* fid,int count,int size,int delay,double lb,double sw,double ph0,double ph1,int nc,int cache,double* output){
    double* data=calloc((size_t)size*2,sizeof(double));
    double* table=calloc((size_t)size+(size_t)cache/8+16,sizeof(double));
    if(!data||!table){free(data);free(table);return 1;}
    double step=log(exp(-3.14159265358979323846*lb/sw));
    double exponent=0.0;
    for(int i=delay;i<count;i++) {
        double factor=exp(exponent);
        data[2*i]=fid[2*i]*factor;data[2*i+1]=-(double)fid[2*i+1]*factor;
        exponent+=step;
    }
    exponent=-step;
    for(int i=delay-1;i>=0;i--) {
        double factor=exp(exponent);
        data[2*i]=fid[2*i]*factor;data[2*i+1]=-(double)fid[2*i+1]*factor;
        exponent-=step;
    }
    reconstructed_bitrev((long)data,size);
    reconstructed_bitdigrev((long)data,size);
    unsigned int skip=0;
    /* The native FFT splits early stages according to the CPU's L1 cache.
       Reusing a full-size sine table here changes final integer samples. */
    if(cache>128 && (unsigned int)cache < (unsigned int)size*8) {
        do {skip+=2;} while((128UL<<skip)<=(unsigned int)cache);
        if(skip>2) {
            unsigned int small=1U<<skip;
            unsigned int chunk=(1U+(((cache>>3)&0x0aaaaaaa)!=0))<<skip;
            reconstructed_setsin4(table,small);
            reconstructed_radix4(data+chunk*2,(long)table,chunk,size,0);
            double* second=(double*)((char*)table+cache/2);
            memcpy(second,table,(size_t)small*sizeof(double));
            reconstructed_radix4(data,(long)second,chunk,size,0);
        } else skip=0;
    }
    reconstructed_setsin4(table,size);
    reconstructed_radix4(data,(long)table,size,size,(unsigned char)skip);
    for(int i=0;i<size;i++) {double value=data[i];data[i]=data[size+i];data[size+i]=value;}
    reconstructed_phase(positive_angle(ph0),positive_angle((ph1+360.0*delay)/size),data,size,NULL,0);
    int shift=reconstructed_getscale(data,size*2);
    reconstructed_convdio((unsigned long)data,size*2,shift);
    int32_t* integers=(int32_t*)data;
    output[0]=nc+shift;
    double scale=scalbn(1.0,nc+shift);
    for(int i=0;i<size;i++) {output[i+1]=integers[i*2]*scale;output[size+i+1]=integers[i*2+1]*scale;}
    free(data);free(table);return 0;
}
