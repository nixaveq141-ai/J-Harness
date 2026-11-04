#define _GNU_SOURCE
#include <dlfcn.h>
#include <ffi.h>
#include "p1-jna-callback.h"
#include <jni.h>
#include "com_sun_jna_Native.h"
#include <stdint.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>

static int add(int a, int b) { return a+b; }
int main(int argc, char **argv) {
    if(argc!=3 || argv[1][0]!='/') return 2;
    void *library=dlopen(argv[1],RTLD_NOW|RTLD_LOCAL);
    if(!strcmp(argv[2],"missing")) {
        if(library) { dlclose(library); return 3; }
        puts("PASS missing exact JNI library refused"); return 0;
    }
    if(strcmp(argv[2],"present") || !library) { fprintf(stderr,"exact dlopen failed: %s\n",dlerror()); return 4; }
    jint (*size)(JNIEnv *,jclass,jint)=(jint(*)(JNIEnv *,jclass,jint))dlsym(library,"Java_com_sun_jna_Native_sizeof");
    jlong (*allocate)(JNIEnv *,jclass,jlong)=(jlong(*)(JNIEnv *,jclass,jlong))dlsym(library,"Java_com_sun_jna_Native_malloc");
    void (*release)(JNIEnv *,jclass,jlong)=(void(*)(JNIEnv *,jclass,jlong))dlsym(library,"Java_com_sun_jna_Native_free");
    ffi_status (*prep)(ffi_cif *,ffi_abi,unsigned int,ffi_type *,ffi_type **)=(ffi_status(*)(ffi_cif *,ffi_abi,unsigned int,ffi_type *,ffi_type **))dlsym(library,"ffi_prep_cif");
    void (*call)(ffi_cif *,void(*)(void),void *,void **)=(void(*)(ffi_cif *,void(*)(void),void *,void **))dlsym(library,"ffi_call");
    void *(*closure_alloc)(size_t,void **)=(void*(*)(size_t,void **))dlsym(library,"ffi_closure_alloc");
    void (*closure_free)(void *)=(void(*)(void *))dlsym(library,"ffi_closure_free");
    ffi_status (*closure_prep)(ffi_closure *,ffi_cif *,void(*)(ffi_cif *,void *,void **,void *),void *,void *)=
        (ffi_status(*)(ffi_closure *,ffi_cif *,void(*)(ffi_cif *,void *,void **,void *),void *,void *))dlsym(library,"ffi_prep_closure_loc");
    ffi_type *sint=(ffi_type *)dlsym(library,"ffi_type_sint32");
    if(!size || !allocate || !release || !prep || !call || !closure_alloc || !closure_free || !closure_prep || !sint) return 5;
    const int types[]={com_sun_jna_Native_TYPE_VOIDP,com_sun_jna_Native_TYPE_LONG,com_sun_jna_Native_TYPE_WCHAR_T,
        com_sun_jna_Native_TYPE_SIZE_T,com_sun_jna_Native_TYPE_BOOL,com_sun_jna_Native_TYPE_LONG_DOUBLE};
    const size_t values[]={sizeof(void *),sizeof(long),sizeof(wchar_t),sizeof(size_t),sizeof(bool),sizeof(long double)};
    for(unsigned int i=0;i<sizeof(types)/sizeof(types[0]);i++) if((size_t)size(NULL,NULL,types[i])!=values[i]) return 6;
    jlong pointer=allocate(NULL,NULL,64);if(!pointer) return 7;
    memset((void *)(uintptr_t)pointer,0x5a,64);
    if(((unsigned char *)(uintptr_t)pointer)[63]!=0x5a) return 8;
    release(NULL,NULL,pointer);
    ffi_cif cif;ffi_type *args[]={sint,sint};int a=17,b=24,bias=5;void *av[]={&a,&b};ffi_arg result=0;
    if(prep(&cif,FFI_DEFAULT_ABI,2,sint,args)!=FFI_OK) return 9;
    call(&cif,FFI_FN(add),&result,av);if(result!=41) return 10;
    void *code=NULL;ffi_closure *closure=closure_alloc(sizeof(ffi_closure),&code);
    if(!closure || !code) return 11;
    if(closure_prep(closure,&cif,p1_callback,&bias,code)!=FFI_OK) return 12;
    result=0;call(&cif,FFI_FN(code),&result,av);closure_free(closure);if(result!=46) return 13;
    if(dlclose(library)) return 14;
    puts("PASS exact JNI dlopen, six native sizeof values, Native malloc/free, bundled ffi call41 and closure46, close");return 0;
}
