#ifndef P1_JNA_CALLBACK_H
#define P1_JNA_CALLBACK_H
#include <ffi.h>
static void p1_callback(ffi_cif *cif, void *result, void **args, void *data) {
    (void)cif;
    *(ffi_arg *)result=(ffi_arg)*(int *)args[0]+*(int *)args[1]+*(int *)data;
}
#endif
