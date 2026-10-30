// SPDX-License-Identifier: MIT
// Query only the child handle already owned by the WinPTY agent. Windows
// RTL_USER_PROCESS_PARAMETERS omits CurrentDirectory in public headers;
// native x64/x86 layouts place its UNICODE_STRING at 0x38/0x24 respectively.
// Cross-bitness and changing descriptors fail explicitly, never return a guess.
#pragma once
#include <windows.h>
#include <winternl.h>
#include <cstring>
#include <string>

inline bool jhDirectoryDescriptorValid(const UNICODE_STRING &value) {
    return value.Buffer && value.Length && (value.Length % sizeof(wchar_t)) == 0 &&
        value.Length <= value.MaximumLength && (value.MaximumLength % sizeof(wchar_t)) == 0;
}

inline DWORD jhOwnedChildDirectory(HANDLE child, std::wstring &result) {
    result.clear();
    DWORD exitCode = 0;
    if (!GetExitCodeProcess(child, &exitCode)) return GetLastError();
    if (exitCode != STILL_ACTIVE) return ERROR_PROCESS_ABORTED;
    BOOL ownWow = FALSE, childWow = FALSE;
    if (!IsWow64Process(GetCurrentProcess(), &ownWow) || !IsWow64Process(child, &childWow)) return GetLastError();
    if (ownWow != childWow) return ERROR_NOT_SUPPORTED;
    typedef NTSTATUS (NTAPI *Query)(HANDLE, PROCESSINFOCLASS, PVOID, ULONG, PULONG);
    auto symbol = GetProcAddress(GetModuleHandleW(L"ntdll.dll"), "NtQueryInformationProcess");
    Query query = nullptr;
    static_assert(sizeof(query) == sizeof(symbol), "Windows function pointer representation required");
    std::memcpy(&query, &symbol, sizeof(query));
    if (!query) return ERROR_PROC_NOT_FOUND;
    PROCESS_BASIC_INFORMATION info = {};
    if (query(child, ProcessBasicInformation, &info, sizeof(info), nullptr) < 0) return ERROR_ACCESS_DENIED;
    PEB peb = {};
    SIZE_T count = 0;
    if (!ReadProcessMemory(child, info.PebBaseAddress, &peb, sizeof(peb), &count) || count != sizeof(peb)) return ERROR_PARTIAL_COPY;
    if (!peb.ProcessParameters) return ERROR_INVALID_DATA;
    auto address = reinterpret_cast<const unsigned char *>(peb.ProcessParameters) + (sizeof(void *) == 8 ? 0x38 : 0x24);
    UNICODE_STRING before = {}, after = {};
    if (!ReadProcessMemory(child, address, &before, sizeof(before), &count) || count != sizeof(before)) return ERROR_PARTIAL_COPY;
    if (!jhDirectoryDescriptorValid(before)) return ERROR_INVALID_DATA;
    std::wstring value(before.Length / sizeof(wchar_t), L'\0');
    if (!ReadProcessMemory(child, before.Buffer, &value[0], before.Length, &count) || count != before.Length) return ERROR_PARTIAL_COPY;
    if (!ReadProcessMemory(child, address, &after, sizeof(after), &count) || count != sizeof(after)) return ERROR_PARTIAL_COPY;
    if (before.Length != after.Length || before.MaximumLength != after.MaximumLength || before.Buffer != after.Buffer) return ERROR_RETRY;
    if (value.find(L'\0') != std::wstring::npos) return ERROR_INVALID_DATA;
    result.swap(value);
    return ERROR_SUCCESS;
}
