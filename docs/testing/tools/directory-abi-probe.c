/* Owned ABI probe: compiler headers certify the offsets used by DirectoryCapability. */
#define _GNU_SOURCE
#include <fcntl.h>
#include <stddef.h>
#include <stdio.h>
#include <sys/stat.h>

int main(void) {
    size_t mode, uid, gid;
    int directory, nofollow;
#if defined(__APPLE__)
    mode = 4; uid = 16; gid = 20;
    directory = 0x100000; nofollow = 0x100;
#elif defined(__linux__) && defined(__aarch64__)
    mode = 16; uid = 24; gid = 28;
    directory = 0x4000; nofollow = 0x8000;
#elif defined(__linux__) && defined(__x86_64__)
    mode = 24; uid = 28; gid = 32;
    directory = 0x10000; nofollow = 0x20000;
#else
    fputs("Unreviewed directory ABI\n", stderr);
    return 1;
#endif
    printf("{\"statBytes\":%zu,\"modeOffset\":%zu,\"uidOffset\":%zu,\"gidOffset\":%zu,"
           "\"directoryFlag\":%d,\"noFollowFlag\":%d}\n",
           sizeof(struct stat), offsetof(struct stat, st_mode), offsetof(struct stat, st_uid),
           offsetof(struct stat, st_gid), O_DIRECTORY, O_NOFOLLOW);
    return !(offsetof(struct stat, st_mode) == mode && offsetof(struct stat, st_uid) == uid &&
             offsetof(struct stat, st_gid) == gid && O_DIRECTORY == directory && O_NOFOLLOW == nofollow);
}
