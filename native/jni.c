#include <jni.h>
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

static double positive_angle(double value) {
    double remainder=fmod(value,360.0);
    return remainder<0?remainder+360.0:remainder;
}

JNIEXPORT jdoubleArray JNICALL Java_NativeKernel_phaseSaved(JNIEnv* env, jclass klass,
    jdoubleArray real, jdoubleArray imag, jdouble ph0, jdouble ph1, jint nc) {
    (void)klass;
    jsize n=(*env)->GetArrayLength(env,real);
    double* data=malloc((size_t)n*2*sizeof(double));
    double* output=malloc((size_t)(n*2+1)*sizeof(double));
    if(!data || !output) {
        free(data);free(output);
        jclass error=(*env)->FindClass(env,"java/lang/OutOfMemoryError");
        (*env)->ThrowNew(env,error,"Unable to allocate phase-correction buffers");
        return NULL;
    }
    jdouble* re=(*env)->GetDoubleArrayElements(env,real,NULL);
    jdouble* im=(*env)->GetDoubleArrayElements(env,imag,NULL);
    if(!re || !im) {
        if(re)(*env)->ReleaseDoubleArrayElements(env,real,re,JNI_ABORT);
        if(im)(*env)->ReleaseDoubleArrayElements(env,imag,im,JNI_ABORT);
        free(data);free(output);return NULL;
    }
    double input_scale=scalbn(1.0,-nc);
    for(jsize i=0;i<n;i++) {data[i*2]=re[i]*input_scale;data[i*2+1]=im[i]*input_scale;}
    (*env)->ReleaseDoubleArrayElements(env,real,re,JNI_ABORT);
    (*env)->ReleaseDoubleArrayElements(env,imag,im,JNI_ABORT);
    reconstructed_phase(positive_angle(ph0),positive_angle(ph1/(double)n),data,n,NULL,0);
    // A phase-only operation keeps NC_proc. The FFT path selects a new scale;
    // pk preserves the existing exponent and truncates in those same units.
    int shift=0;
    reconstructed_convdio((unsigned long)data,n*2,shift);
    int32_t* integers=(int32_t*)data;
    int output_nc=nc+shift;
    double output_scale=scalbn(1.0,output_nc);
    output[0]=output_nc;
    for(jsize i=0;i<n;i++) {output[i+1]=integers[i*2]*output_scale;output[n+i+1]=integers[i*2+1]*output_scale;}
    jdoubleArray result=(*env)->NewDoubleArray(env,n*2+1);
    if(result)(*env)->SetDoubleArrayRegion(env,result,0,n*2+1,output);
    free(data);free(output);
    return result;
}

JNIEXPORT jdoubleArray JNICALL Java_NativeKernel_processRaw(JNIEnv* env, jclass klass,
    jdoubleArray raw, jint size, jint delay, jdouble lb, jdouble sw,
    jdouble ph0, jdouble ph1, jint nc, jint cache) {
    (void)klass;
    int count=(*env)->GetArrayLength(env,raw)/2;
    double* data=calloc((size_t)size*2,sizeof(double));
    double* table=calloc((size_t)size+(size_t)cache/8+16,sizeof(double));
    double* output=malloc(((size_t)size*2+1)*sizeof(double));
    if(!data || !table || !output) {
        free(data);free(table);free(output);
        (*env)->ThrowNew(env,(*env)->FindClass(env,"java/lang/OutOfMemoryError"),"Unable to allocate Fourier buffers");
        return NULL;
    }
    jdouble* fid=(*env)->GetDoubleArrayElements(env,raw,NULL);
    if(!fid) {free(data);free(table);free(output);return NULL;}
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
    (*env)->ReleaseDoubleArrayElements(env,raw,fid,JNI_ABORT);
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
    jdoubleArray result=(*env)->NewDoubleArray(env,size*2+1);
    if(result)(*env)->SetDoubleArrayRegion(env,result,0,size*2+1,output);
    free(data);free(table);free(output);
    return result;
}
