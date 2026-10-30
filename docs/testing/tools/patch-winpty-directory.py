"""Apply bounded owned-child CWD RPC to the exact reviewed WinPTY source."""
import hashlib
from pathlib import Path

ROOT=Path(__file__).resolve().parents[3]
REVISION='7e59fe2d09adf0fa2aa606492e7ca98efbc5184e'
EXPECTED={
    'src/shared/AgentMsg.h':'8ffd9941716b1b056beeff5dd128d0ca3573f4f4a081b3c8155d8afe11b132cc',
    'src/agent/Agent.h':'a9522c989c09818ab0751249e6278f83118853619a5d67e4ae7f60204bc746da',
    'src/agent/Agent.cc':'b665784b6eef00607848ba04142a2120f59c4171995ed0171492debfe2a52e65',
    'src/include/winpty.h':'07be0f1cad79c8439c40448381e24a212deeb6bbf22e90713da1a8297b3016f6',
    'src/libwinpty/winpty.cc':'10a1e68fcd02e7978c612c69be397bb4774d153556fc637203ebc178cabfb19c',
}


def apply(target):
    rules={
        'src/shared/AgentMsg.h': [('        GetConsoleProcessList,','        GetConsoleProcessList,\n        GetOwnedChildDirectory,')],
        'src/agent/Agent.h': [('    void handleGetConsoleProcessListPacket(ReadBuffer &packet);',
            '    void handleGetConsoleProcessListPacket(ReadBuffer &packet);\n    void handleGetOwnedChildDirectoryPacket(ReadBuffer &packet);')],
        'src/agent/Agent.cc': [('#include "Agent.h"','#include "Agent.h"\n#include "jh-owned-directory.h"'),
            ('    case AgentMsg::GetConsoleProcessList:', '    case AgentMsg::GetOwnedChildDirectory:\n        handleGetOwnedChildDirectoryPacket(packet);\n        break;\n    case AgentMsg::GetConsoleProcessList:'),
            ('void Agent::handleGetConsoleProcessListPacket(ReadBuffer &packet)', '''void Agent::handleGetOwnedChildDirectoryPacket(ReadBuffer &packet)
{
    packet.assertEof();
    std::wstring directory;
    DWORD error = jhOwnedChildDirectory(m_childProcess, directory);
    auto reply = newPacket();
    reply.putInt32(error);
    reply.putWString(directory);
    writePacket(reply);
}

void Agent::handleGetConsoleProcessListPacket(ReadBuffer &packet)''')],
        'src/include/winpty.h': [('WINPTY_API void winpty_free(winpty_t *wp);',
            'WINPTY_API int winpty_get_current_directory(winpty_t *wp, int length, wchar_t *buffer, winpty_error_ptr_t *err);\n\nWINPTY_API void winpty_free(winpty_t *wp);')],
        'src/libwinpty/winpty.cc': [('WINPTY_API void winpty_free(winpty_t *wp) {', '''WINPTY_API int
winpty_get_current_directory(winpty_t *wp, int length, wchar_t *buffer, winpty_error_ptr_t *err) {
    API_TRY {
        if (wp == nullptr || buffer == nullptr || length <= 0 || length > 32768)
            throwWinptyException(L"Invalid current-directory arguments");
        buffer[0] = 0;
        LockGuard<Mutex> lock(wp->mutex);
        RpcOperation rpc(*wp);
        auto packet = newPacket();
        packet.putInt32(AgentMsg::GetOwnedChildDirectory);
        writePacket(*wp, packet);
        auto reply = readPacket(*wp);
        auto error = reply.getInt32();
        auto directory = reply.getWString();
        reply.assertEof();
        rpc.success();
        if (error != 0) throwWinptyException(L"Owned child current-directory query failed");
        if (directory.empty() || directory.size() >= static_cast<size_t>(length))
            throwWinptyException(L"Current-directory buffer is insufficient");
        for (size_t i = 0; i <= directory.size(); ++i) buffer[i] = directory.c_str()[i];
        return static_cast<int>(directory.size());
    } API_CATCH(0)
}

WINPTY_API void winpty_free(winpty_t *wp) {''')],
    }
    changes=[];pending=[]
    helper=target/'src/agent/jh-owned-directory.h'
    if helper.exists():raise ValueError('Directory extension already exists')
    for name,replacements in rules.items():
        path=target/name;before=path.read_bytes();canonical=before.replace(b'\r\n',b'\n');after=canonical.decode()
        if hashlib.sha256(canonical).hexdigest()!=EXPECTED[name]:raise ValueError('Pinned directory source hash rejected: '+name)
        for old,new in replacements:
            if after.count(old)!=1:raise ValueError('Pinned directory patch anchor rejected: '+name)
            after=after.replace(old,new,1)
        pending.append((path,after.encode()))
        changes.append(dict(path=name,beforeSha256=hashlib.sha256(before).hexdigest(),
            canonicalBeforeSha256=hashlib.sha256(canonical).hexdigest(),
            lineEndingConversion='CRLF_TO_LF' if canonical!=before else 'NONE',
            afterSha256=hashlib.sha256(after.encode()).hexdigest()))
    # Validate every anchor before changing any source.
    helper_data=(ROOT/'docs/testing/native/winpty-owned-directory.h').read_bytes()
    for path,data in pending:path.write_bytes(data)
    helper.write_bytes(helper_data)
    return dict(baseRevision=REVISION,changes=changes,ownedChildHelperSha256=hashlib.sha256(helper.read_bytes()).hexdigest(),
                policy='Only agent-owned child handle; cross-bitness, changing descriptors and insufficient buffers fail explicitly')
