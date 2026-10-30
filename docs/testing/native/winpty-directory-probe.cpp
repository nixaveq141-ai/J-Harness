// SPDX-License-Identifier: MIT
#include "winpty-owned-directory.h"
#include <iostream>

int wmain(int argc, wchar_t **argv) {
    if (argc != 3) return 1;
    UNICODE_STRING bad = {};
    if (jhDirectoryDescriptorValid(bad)) return 2;
    bad.Buffer = reinterpret_cast<wchar_t *>(1); bad.Length = 1; bad.MaximumLength = 2;
    if (jhDirectoryDescriptorValid(bad)) return 3;
    bad.Length = 4; bad.MaximumLength = 2;
    if (jhDirectoryDescriptorValid(bad)) return 4;
    bad.Length = 2; bad.MaximumLength = 65535;
    if (jhDirectoryDescriptorValid(bad)) return 5;
    std::wstring value = L"stale";
    if (jhOwnedChildDirectory(nullptr, value) == ERROR_SUCCESS || !value.empty()) return 6;
    for (int i = 1; i < 3; ++i) {
        if (!SetCurrentDirectoryW(argv[i])) return 7;
        wchar_t expected[32768];
        DWORD length = GetCurrentDirectoryW(32768, expected);
        if (!length || length >= 32768) return 8;
        if (jhOwnedChildDirectory(GetCurrentProcess(), value) != ERROR_SUCCESS) return 9;
        // The PEB DosPath may retain a directory separator that Win32's
        // GetCurrentDirectory removes; these probe directories are non-roots.
        if (value.size() > 3 && value.back() == L'\\') value.pop_back();
        if (value != expected) return 10;
    }
    std::cout << "OWNED_DIRECTORY_NATIVE_PROBE_PASS\n";
    return 0;
}
