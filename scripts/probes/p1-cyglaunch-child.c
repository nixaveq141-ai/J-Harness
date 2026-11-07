#include <stdio.h>
#include <string.h>
#include <unistd.h>

int main(void) {
    if (!isatty(0) || !isatty(1) || !isatty(2)) { fputs("CHILD_NOT_TTY\n",stderr);return 31; }
    FILE *marker=fopen("jh-owned-marker","r");
    char data[64]={0};
    if (!marker || !fgets(data,sizeof(data),marker)) { fputs("CHILD_CWD_MARKER_UNREADABLE\n",stderr);return 32; }
    fclose(marker);
    if (strcmp(data,"owned-directory\n")) { fputs("CHILD_CWD_MARKER_MISMATCH\n",stderr);return 33; }
    puts("CYG_CHILD_READY");fflush(stdout);
    fputs("CYG_CHILD_ERROR\n",stderr);fflush(stderr);
    if (!fgets(data,sizeof(data),stdin) || strcmp(data,"HELLO\n")) return 34;
    puts("CYG_CHILD_ACK");fflush(stdout);
    return 23;
}
