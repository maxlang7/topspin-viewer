#include <jni.h>
#include <ft2build.h>
#include FT_FREETYPE_H
#include <stdlib.h>

/* Glyph masks use the measured target's normal hinting and 17 coverage levels. */
JNIEXPORT jintArray JNICALL Java_NativeText_glyph(JNIEnv *env,jclass cls,jstring path,jint height,jint index) {
    (void)cls;
    FT_Library library=NULL;FT_Face face=NULL;jintArray result=NULL;
    const char *filename=(*env)->GetStringUTFChars(env,path,NULL);
    if(!filename)return NULL;
    int error=FT_Init_FreeType(&library);
    if(!error)error=FT_New_Face(library,filename,0,&face);
    (*env)->ReleaseStringUTFChars(env,path,filename);
    if(!error && (height<1 || height>1024 || index<0 || index>=face->num_glyphs))error=1;
    if(!error)error=FT_Set_Pixel_Sizes(face,0,height);
    if(!error)error=FT_Load_Glyph(face,index,FT_LOAD_IGNORE_GLOBAL_ADVANCE_WIDTH|FT_LOAD_TARGET_NORMAL|FT_LOAD_NO_BITMAP|FT_LOAD_PEDANTIC);
    if(!error)error=FT_Render_Glyph(face->glyph,FT_RENDER_MODE_NORMAL);
    if(!error) {
        FT_Bitmap *bitmap=&face->glyph->bitmap;
        size_t count=4+(size_t)bitmap->width*bitmap->rows;
        jint *values=malloc(count*sizeof(jint));
        if(values) {
            values[0]=face->glyph->bitmap_left;values[1]=face->glyph->bitmap_top;
            values[2]=bitmap->width;values[3]=bitmap->rows;
            for(unsigned y=0;y<bitmap->rows;y++)for(unsigned x=0;x<bitmap->width;x++)
                values[4+(size_t)y*bitmap->width+x]=bitmap->buffer[(ptrdiff_t)y*bitmap->pitch+x]*17/256;
            result=(*env)->NewIntArray(env,(jsize)count);
            if(result)(*env)->SetIntArrayRegion(env,result,0,(jsize)count,values);
            free(values);
        }else error=1;
    }
    if(face)FT_Done_Face(face);
    if(library)FT_Done_FreeType(library);
    if(error && !(*env)->ExceptionCheck(env)) {
        jclass type=(*env)->FindClass(env,"java/lang/IllegalStateException");
        (*env)->ThrowNew(env,type,"Cannot rasterize the pinned interface font");
    }
    return result;
}
