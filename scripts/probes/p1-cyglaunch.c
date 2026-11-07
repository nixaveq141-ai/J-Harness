#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
#include <string.h>

_Static_assert(sizeof(void*)==8,"x64 probe required");

static int finish(HANDLE pipe,OVERLAPPED *over,DWORD *count) {
    if (WaitForSingleObject(over->hEvent,10000)!=WAIT_OBJECT_0) {
        CancelIoEx(pipe,over);GetOverlappedResult(pipe,over,count,TRUE);return 0;
    }
    return GetOverlappedResult(pipe,over,count,FALSE)!=FALSE;
}

static int connect_pipe(HANDLE pipe) {
    OVERLAPPED over={0};over.hEvent=CreateEventA(NULL,TRUE,FALSE,NULL);
    if (!over.hEvent) return 0;
    BOOL ok=ConnectNamedPipe(pipe,&over);DWORD error=ok?0:GetLastError(),count=0;
    int result=ok || error==ERROR_PIPE_CONNECTED || (error==ERROR_IO_PENDING && finish(pipe,&over,&count));
    CloseHandle(over.hEvent);return result;
}

static int transfer(HANDLE pipe,char *buffer,DWORD length,int writing,DWORD *count) {
    OVERLAPPED over={0};over.hEvent=CreateEventA(NULL,TRUE,FALSE,NULL);
    if (!over.hEvent) return 0;
    BOOL ok=writing?WriteFile(pipe,buffer,length,count,&over):ReadFile(pipe,buffer,length,count,&over);
    int result=ok || (GetLastError()==ERROR_IO_PENDING && finish(pipe,&over,count));
    CloseHandle(over.hEvent);return result;
}

static int expect(HANDLE pipe,const char *marker) {
    char data[8192]={0};DWORD at=0;
    while (at<sizeof(data)-1) {
        DWORD count=0;
        if (!transfer(pipe,data+at,(DWORD)sizeof(data)-1-at,0,&count) || !count) return 0;
        at+=count;data[at]=0;
        if (strstr(data,marker)) return 1;
    }
    return 0;
}

int main(int argc,char **argv) {
    if (argc!=4 || strchr(argv[1],'"') || strchr(argv[2],'"')) return 2;
    STARTUPINFOA startup={0};startup.cb=sizeof(startup);startup.dwFlags=STARTF_USESHOWWINDOW;startup.wShowWindow=SW_HIDE;
    PROCESS_INFORMATION process={0};char command[4096];
    if (!strcmp(argv[3],"missing")) {
        snprintf(command,sizeof(command),"\"%s\"",argv[1]);
        if (CreateProcessA(argv[1],command,NULL,NULL,FALSE,CREATE_NO_WINDOW,NULL,NULL,&startup,&process)) {
            TerminateProcess(process.hProcess,1);WaitForSingleObject(process.hProcess,5000);return 3;
        }
        if (GetLastError()!=ERROR_FILE_NOT_FOUND && GetLastError()!=ERROR_PATH_NOT_FOUND) return 4;
        puts("PASS missing exact Cygwin launcher refused");return 0;
    }
    if (strcmp(argv[3],"present")) return 2;
    char names[3][128];HANDLE pipes[3];
    for (int i=0;i<3;i++) {
        snprintf(names[i],sizeof(names[i]),"\\\\.\\pipe\\jh-p1-cyg-%lu-%d",GetCurrentProcessId(),i);
        pipes[i]=CreateNamedPipeA(names[i],(i?PIPE_ACCESS_INBOUND:PIPE_ACCESS_OUTBOUND)|FILE_FLAG_OVERLAPPED,PIPE_TYPE_BYTE|PIPE_WAIT,1,4096,4096,0,NULL);
        if (pipes[i]==INVALID_HANDLE_VALUE) return 5;
    }
    HANDLE job=CreateJobObjectA(NULL,NULL);JOBOBJECT_EXTENDED_LIMIT_INFORMATION limits={0};
    limits.BasicLimitInformation.LimitFlags=JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
    if (!job || !SetInformationJobObject(job,JobObjectExtendedLimitInformation,&limits,sizeof(limits))) return 6;
    int size=snprintf(command,sizeof(command),"\"%s\" null 1 %s %s %s \"%s\"",argv[1],names[0],names[1],names[2],argv[2]);
    if (size<0 || size>=(int)sizeof(command)) return 7;
    if (!CreateProcessA(argv[1],command,NULL,NULL,FALSE,CREATE_NO_WINDOW|CREATE_SUSPENDED,NULL,NULL,&startup,&process)) return 8;
    if (!AssignProcessToJobObject(job,process.hProcess)) { TerminateProcess(process.hProcess,1);return 9; }
    ResumeThread(process.hThread);CloseHandle(process.hThread);
    for (int i=0;i<3;i++) if (!connect_pipe(pipes[i])) return 10;
    if (!expect(pipes[1],"CYG_CHILD_READY") || !expect(pipes[2],"CYG_CHILD_ERROR")) return 11;
    char input[]="HELLO\n";DWORD count=0;
    if (!transfer(pipes[0],input,6,1,&count) || count!=6 || !expect(pipes[1],"CYG_CHILD_ACK")) return 12;
    if (WaitForSingleObject(process.hProcess,20000)!=WAIT_OBJECT_0) return 13;
    DWORD status=0;if (!GetExitCodeProcess(process.hProcess,&status) || status!=23) return 14;
    CloseHandle(process.hProcess);
    for (int i=0;i<3;i++) CloseHandle(pipes[i]);
    CloseHandle(job);
    puts("PASS exact Cygwin launcher, three named pipes, PTY stdin/stdout/stderr, owned cwd, input roundtrip, exit23");return 0;
}
