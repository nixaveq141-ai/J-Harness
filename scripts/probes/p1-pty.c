#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <termios.h>
#include <unistd.h>

/* Component ABI probe only; this does not certify a Java application platform. */
int main(int argc, char **argv) {
    if (argc == 2 && !strcmp(argv[1], "--child")) {
        alarm(5);
        const char ready[] = "P1-child-ready";
        char input[3] = {0};
        if (write(1, ready, sizeof(ready)) != (ssize_t)sizeof(ready) ||
            read(0, input, sizeof(input)) != (ssize_t)sizeof(input) || memcmp(input, "go!", 3)) return 24;
        return 23;
    }
    if (argc < 3 || argv[1][0] != '/') return 2;
    int missing = !strcmp(argv[2], "missing");
    if (!missing && strcmp(argv[2], "present")) return 3;
    void *library = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    if (missing) {
        if (library) { dlclose(library); return 4; }
        puts("PASS missing exact library refused"); return 0;
    }
    if (!library) { fprintf(stderr, "%s\n", dlerror()); return 5; }
    if (argc != 5 || argv[3][0] != '/' || argv[4][0] != '/') return 19;
    int (*get_size)(int, struct winsize *) = (int (*)(int, struct winsize *))dlsym(library, "get_window_size");
    int (*set_size)(int, const struct winsize *) = (int (*)(int, const struct winsize *))dlsym(library, "set_window_size");
    int (*valid_fd)(int) = (int (*)(int))dlsym(library, "is_valid_fd");
    pid_t (*spawn)(const char *, char *const[], char *const[], const char *, const char *, int, const char *, int, int) =
        (pid_t (*)(const char *, char *const[], char *const[], const char *, const char *, int, const char *, int, int))dlsym(library, "exec_pty");
    int (*wait_child)(int) = (int (*)(int))dlsym(library, "wait_for_child_process_exit");
    if (!get_size || !set_size || !valid_fd || !spawn || !wait_child) return 6;
    int master = posix_openpt(O_RDWR | O_NOCTTY);
    if (master < 0 || grantpt(master) || unlockpt(master)) return 7;
    char *name = ptsname(master);
    if (!name) return 8;
    int slave = open(name, O_RDWR | O_NOCTTY);
    if (slave < 0) return 9;
    struct termios raw;
    if (tcgetattr(slave, &raw)) return 10;
    cfmakeraw(&raw);
    if (tcsetattr(slave, TCSANOW, &raw)) return 11;
    struct winsize desired = {37, 109, 0, 0}, observed = {0};
    if (!valid_fd(master) || valid_fd(-1) || set_size(master, &desired) || get_size(slave, &observed)) return 12;
    if (observed.ws_row != 37 || observed.ws_col != 109 || !get_size(-1, &observed)) return 13;
    const char payload[] = "P1-pty-transport";
    char buffer[sizeof(payload)] = {0};
    if (write(slave, payload, sizeof(payload)) != (ssize_t)sizeof(payload)) return 14;
    struct pollfd item = {master, POLLIN, 0};
    if (poll(&item, 1, 2000) != 1 || read(master, buffer, sizeof(buffer)) != (ssize_t)sizeof(buffer) ||
        memcmp(buffer, payload, sizeof(payload))) return 15;
    memset(buffer, 0, sizeof(buffer));
    if (write(master, payload, sizeof(payload)) != (ssize_t)sizeof(payload)) return 16;
    item.fd = slave;
    if (poll(&item, 1, 2000) != 1 || read(slave, buffer, sizeof(buffer)) != (ssize_t)sizeof(buffer) ||
        memcmp(buffer, payload, sizeof(payload))) return 17;
    /* Keep the slave open until the child is ready. Otherwise the master can
       report HUP before the emulated child has reopened the slave. */
    char *child_argv[] = {argv[3], "-L", argv[4], argv[0], "--child", NULL};
    char *child_env[] = {"PATH=/usr/bin:/bin", NULL};
    pid_t child = spawn(argv[3], child_argv, child_env, "/", name, master, NULL, -1, 0);
    if (child <= 0) return 20;
    const char ready[] = "P1-child-ready";
    char child_output[sizeof(ready)] = {0};
    item.fd = master;
    int polled = poll(&item, 1, 3000);
    ssize_t count = polled == 1 ? read(master, child_output, sizeof(child_output)) : -1;
    if (polled != 1 || count != (ssize_t)sizeof(child_output) ||
        memcmp(child_output, ready, sizeof(ready)) || write(master, "go!", 3) != 3) {
        fprintf(stderr, "child exchange failed poll=%d revents=%d read=%ld errno=%d\n", polled, item.revents, (long)count, errno);
        kill(child, SIGKILL); wait_child(child); close(slave); close(master); dlclose(library); return 21;
    }
    if (close(slave)) { kill(child, SIGKILL); wait_child(child); return 18; }
    int status = wait_child(child);
    if (status != 23 || close(master) || dlclose(library)) {
        fprintf(stderr, "child wait/close failed status=%d\n", status); return 22;
    }
    puts("PASS exact dlopen, exports, fd validity, native resize, invalid fd rejection, bidirectional PTY bytes, native fork/exec child exchange, exit23, close");
    return 0;
}
