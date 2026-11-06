#include <windows.h>
#include <cstdio>
#include <cstdlib>
#include <cwchar>
#include <string>
#ifndef _M_ARM64
#error Actual ARM64 compiler required
#endif
using CurrentDirectory = wchar_t* (__cdecl *)(DWORD,wchar_t**);
int wmain(int argc,wchar_t** argv) {
 if(argc!=5 || sizeof(void*)!=8) return 2;
 HMODULE library=LoadLibraryExW(argv[1],nullptr,LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR|LOAD_LIBRARY_SEARCH_SYSTEM32);
 if(!wcscmp(argv[2],L"missing")) {
  if(library){FreeLibrary(library);return 3;}
  puts("PASS missing exact ARM64 helper refused");return 0;
 }
 if(wcscmp(argv[2],L"present") || !library)return 4;
 wchar_t actual[32768],expected[32768];
 if(!GetModuleFileNameW(library,actual,32768) || !GetFullPathNameW(argv[1],32768,expected,nullptr) || _wcsicmp(actual,expected))return 5;
 union {FARPROC symbol;CurrentDirectory call;} api;
 api.symbol=GetProcAddress(library,"getCurrentDirectory");if(!api.symbol)return 6;
 for(int i=3;i<5;++i) {
  if(!SetCurrentDirectoryW(argv[i]))return 7;
  if(!GetCurrentDirectoryW(32768,expected))return 8;
  wchar_t* error=nullptr;wchar_t* value=api.call(GetCurrentProcessId(),&error);
  if(!value || error){free(value);free(error);return 9;}
  std::wstring directory(value);free(value);
  if(directory.size()>3 && directory.back()==L'\\')directory.pop_back();
  if(directory!=expected)return 10;
 }
 wchar_t* error=nullptr;wchar_t* value=api.call(MAXDWORD,&error);
 if(value || !error || !*error){free(value);free(error);return 11;}
 free(error);
 if(!FreeLibrary(library))return 12;
 puts("PASS exact ARM64 helper directory ABI, two owned directories, invalid PID, unload");return 0;
}
