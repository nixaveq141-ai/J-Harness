#include "p1-jna-callback.h"
#include <stdio.h>

int main(void) {
    const ffi_arg sentinel=(ffi_arg)-1;
    struct { ffi_arg before, result, after; } storage={sentinel,sentinel,sentinel};
    int a=17,b=24,bias=5;void *args[]={&a,&b};
    p1_callback(NULL,&storage.result,args,&bias);
    if(storage.result!=46 || storage.before!=sentinel || storage.after!=sentinel) {
        fprintf(stderr,"callback register promotion incomplete; ffi_arg_bytes=%zu\n",sizeof(ffi_arg));return 1;
    }
    puts("PASS complete ffi_arg result and adjacent guards");return 0;
}
