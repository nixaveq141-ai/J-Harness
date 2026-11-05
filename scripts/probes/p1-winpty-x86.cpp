// Actual 32-bit DLL and owned agent lifecycle; no fallback or user process access.
#include <windows.h>
#include <cstdio>
#include <cwchar>
#include "winpty.h"

template<class T> static T symbol(HMODULE dll,const char* name) {
    union { FARPROC raw; T typed; } value;
    value.raw=GetProcAddress(dll,name);
    if (!value.raw) { std::fprintf(stderr,"Missing export %s\n",name); ExitProcess(11); }
    return value.typed;
}
#define API(name) auto fn_##name=symbol<decltype(&name)>(dll,#name)

int wmain(int argc,wchar_t** argv) {
    if (argc!=3 || sizeof(void*)!=4 || !SetDefaultDllDirectories(LOAD_LIBRARY_SEARCH_SYSTEM32)) return 2;
    HMODULE dll=LoadLibraryExW(argv[1],nullptr,LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR|LOAD_LIBRARY_SEARCH_SYSTEM32);
    if (std::wcscmp(argv[2],L"missing")==0) {
        if (dll) { FreeLibrary(dll); return 3; }
        std::puts("PASS missing exact WinPTY DLL refused"); return 0;
    }
    if (!dll) return 4;
    wchar_t actual[32768];
    if (!GetModuleFileNameW(dll,actual,32768) || _wcsicmp(actual,argv[1])) return 5;
    API(winpty_config_new); API(winpty_config_free); API(winpty_config_set_initial_size);
    API(winpty_config_set_agent_timeout); API(winpty_open); API(winpty_free);
    API(winpty_agent_process); API(winpty_set_size); API(winpty_get_console_process_list);
    API(winpty_error_free);
    winpty_error_ptr_t error=nullptr;
    auto cfg=fn_winpty_config_new(0,&error);
    if (!cfg || error) return 6;
    fn_winpty_config_set_initial_size(cfg,91,29);
    fn_winpty_config_set_agent_timeout(cfg,10000);
    auto session=fn_winpty_open(cfg,&error);fn_winpty_config_free(cfg);
    if (!session || error) { if(error)fn_winpty_error_free(error); return 7; }
    HANDLE agent=nullptr;
    if (!DuplicateHandle(GetCurrentProcess(),fn_winpty_agent_process(session),GetCurrentProcess(),&agent,SYNCHRONIZE,FALSE,0)) {
        fn_winpty_free(session); return 8;
    }
    int processes[64];
    const bool resized=fn_winpty_set_size(session,103,37,&error)!=FALSE;
    if(error) { fn_winpty_error_free(error); error=nullptr; }
    const int count=fn_winpty_get_console_process_list(session,processes,64,&error);
    if(error)fn_winpty_error_free(error);
    fn_winpty_free(session);
    DWORD ended=WaitForSingleObject(agent,15000);CloseHandle(agent);
    if (!resized || count<1 || ended!=WAIT_OBJECT_0) return 9;
    if (!FreeLibrary(dll)) return 10;
    std::puts("PASS exact x86 WinPTY DLL, owned agent, resize, process list, agent exit, unload");return 0;
}
