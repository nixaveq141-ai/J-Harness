#include <windows.h>
#include <jni.h>
#include "com_sun_jna_Native.h"
#include <stdint.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <wchar.h>

typedef jint (JNICALL *Size)(JNIEnv *,jclass,jint);
typedef jlong (JNICALL *Allocate)(JNIEnv *,jclass,jlong);
typedef void (JNICALL *Release)(JNIEnv *,jclass,jlong);

int wmain(int argc,wchar_t **argv) {
    if(argc!=3 || wcslen(argv[1])<3 || !((argv[1][1]==L':' && (argv[1][2]==L'\\' || argv[1][2]==L'/')))) return 2;
    HMODULE library=LoadLibraryExW(argv[1],NULL,LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR|LOAD_LIBRARY_SEARCH_SYSTEM32);
    if(!wcscmp(argv[2],L"missing")) {
        if(library) { FreeLibrary(library);return 3; }
        puts("PASS missing exact Windows JNI library refused");return 0;
    }
    if(wcscmp(argv[2],L"present") || !library) { fprintf(stderr,"exact DLL load failed code=%lu\n",GetLastError());return 4; }
    wchar_t actual[32768],expected[32768];
    DWORD a=GetModuleFileNameW(library,actual,32768),b=GetFullPathNameW(argv[1],32768,expected,NULL);
    if(!a || a>=32768 || !b || b>=32768 || _wcsicmp(actual,expected)) return 5;
    union { FARPROC symbol;Size call; } sz={.symbol=GetProcAddress(library,"Java_com_sun_jna_Native_sizeof")};
    union { FARPROC symbol;Allocate call; } alloc={.symbol=GetProcAddress(library,"Java_com_sun_jna_Native_malloc")};
    union { FARPROC symbol;Release call; } release={.symbol=GetProcAddress(library,"Java_com_sun_jna_Native_free")};
    if(!sz.call || !alloc.call || !release.call) return 6;
    const int types[]={com_sun_jna_Native_TYPE_VOIDP,com_sun_jna_Native_TYPE_LONG,com_sun_jna_Native_TYPE_WCHAR_T,
        com_sun_jna_Native_TYPE_SIZE_T,com_sun_jna_Native_TYPE_BOOL,com_sun_jna_Native_TYPE_LONG_DOUBLE};
    const size_t values[]={sizeof(void *),sizeof(long),sizeof(wchar_t),sizeof(size_t),sizeof(bool),sizeof(long double)};
    if(sizeof(void *)!=8) return 7;
    for(unsigned int i=0;i<sizeof(types)/sizeof(types[0]);i++) if((size_t)sz.call(NULL,NULL,types[i])!=values[i]) return 8;
    jlong pointer=alloc.call(NULL,NULL,64);if(!pointer) return 9;
    memset((void *)(uintptr_t)pointer,0x5a,64);
    if(((unsigned char *)(uintptr_t)pointer)[63]!=0x5a) return 10;
    release.call(NULL,NULL,pointer);
    if(!FreeLibrary(library)) return 11;
    puts("PASS exact Windows DLL, ARM64 pointer ABI, six native sizes, Native malloc/free, unload");return 0;
}
