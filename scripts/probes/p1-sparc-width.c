#include <stdio.h>

int main(void) {
    printf("P1_SPARC_CROSS_BITS=%u\n", (unsigned)(8 * sizeof(void *)));
    return sizeof(void *) != sizeof(long);
}
