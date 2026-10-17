package io.jharness.cli;

import io.jharness.api.HarnessException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Current-user native vault. No secret in argv, environment or temporary files. */
class SystemCredentials {
    private Boolean available;

    synchronized boolean supported() {
        if (System.getProperty("os.name").startsWith("Windows")) return true;
        if (available == null) {
            try {
                available =
                        "available"
                                .equals(
                                        invokeNative(
                                                List.of(
                                                        Map.of(
                                                                "action",
                                                                "probe",
                                                                "target",
                                                                "J-Harness/probe",
                                                                "value",
                                                                ""))));
            } catch (IOException | RuntimeException failure) {
                available = false;
            }
        }
        return available;
    }

    String description() {
        if (!supported()) return "unavailable";
        String os = System.getProperty("os.name");
        if (os.startsWith("Windows")) return "Windows Credential Manager";
        return os.startsWith("Mac") ? "macOS Keychain" : "Linux Secret Service";
    }

    private String invokeNative(List<Map<String, String>> operations) throws IOException {
        String os = System.getProperty("os.name");
        if (!os.startsWith("Mac") && !os.equals("Linux"))
            throw new IOException("Credential store unavailable on this platform");
        var builder =
                new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-cp",
                        System.getProperty("java.class.path"),
                        NativeCredentials.class.getName());
        return run(
                builder,
                operations,
                List.of(
                        "HOME",
                        "TMPDIR",
                        "TMP",
                        "TEMP",
                        "DBUS_SESSION_BUS_ADDRESS",
                        "XDG_RUNTIME_DIR",
                        "XDG_DATA_HOME"));
    }

    String read(String target) throws IOException {
        return invoke("read", target, "");
    }

    void write(String target, String value) throws IOException {
        invoke("write", target, value);
    }

    void delete(String target) throws IOException {
        invoke("delete", target, "");
    }

    private String invoke(String action, String target, String value) throws IOException {
        return invoke(List.of(Map.of("action", action, "target", target, "value", value)));
    }

    void update(Map<String, String> writes, List<String> deletes) throws IOException {
        var operations = new ArrayList<Map<String, String>>();
        writes.forEach(
                (target, value) ->
                        operations.add(
                                Map.of("action", "write", "target", target, "value", value)));
        deletes.forEach(
                target ->
                        operations.add(Map.of("action", "delete", "target", target, "value", "")));
        if (!operations.isEmpty()) invoke(operations);
    }

    private String invoke(List<Map<String, String>> operations) throws IOException {
        if (!supported())
            throw new HarnessException(
                    "CREDENTIAL_STORE_UNAVAILABLE",
                    "Native credential store unavailable; use an environment variable");
        if (!System.getProperty("os.name").startsWith("Windows")) return invokeNative(operations);
        String source =
                """
                using System;
                using System.Runtime.InteropServices;
                using System.Text;
                public static class Vault {
                  [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)]
                  public struct C { public uint Flags, Type; public string Target, Comment;
                    public System.Runtime.InteropServices.ComTypes.FILETIME Last;
                    public uint Size; public IntPtr Blob; public uint Persist, Count;
                    public IntPtr Attr; public string Alias, User; }
                  [DllImport("advapi32.dll", EntryPoint="CredWriteW", CharSet=CharSet.Unicode, SetLastError=true)]
                  static extern bool Write(ref C c, uint flags);
                  [DllImport("advapi32.dll", EntryPoint="CredReadW", CharSet=CharSet.Unicode, SetLastError=true)]
                  static extern bool Read(string target, uint type, uint flags, out IntPtr p);
                  [DllImport("advapi32.dll", EntryPoint="CredDeleteW", CharSet=CharSet.Unicode, SetLastError=true)]
                  static extern bool Delete(string target, uint type, uint flags);
                  [DllImport("advapi32.dll")] static extern void CredFree(IntPtr p);
                  public static string Run(string action, string target, string value) {
                    if (action=="write") {
                      byte[] bytes=Encoding.UTF8.GetBytes(value); IntPtr p=Marshal.AllocHGlobal(bytes.Length);
                      try { Marshal.Copy(bytes,0,p,bytes.Length);
                        C c=new C { Type=1, Target=target, Size=(uint)bytes.Length, Blob=p, Persist=2, User="J-Harness" };
                        if(!Write(ref c,0)) throw new Exception();
                      } finally { for(int i=0;i<bytes.Length;i++) Marshal.WriteByte(p,i,0); Marshal.FreeHGlobal(p); Array.Clear(bytes,0,bytes.Length); }
                      return "";
                    }
                    if(action=="delete") { if(!Delete(target,1,0) && Marshal.GetLastWin32Error()!=1168) throw new Exception(); return ""; }
                    IntPtr data; if(!Read(target,1,0,out data)) { if(Marshal.GetLastWin32Error()==1168) return ""; throw new Exception(); }
                    try { C c=(C)Marshal.PtrToStructure(data,typeof(C)); if(c.Size>1024) throw new Exception();
                      byte[] bytes=new byte[c.Size]; Marshal.Copy(c.Blob,bytes,0,bytes.Length); return Encoding.UTF8.GetString(bytes);
                    } finally { CredFree(data); }
                  }
                }
                """;
        String script =
                "$ErrorActionPreference='Stop'; try { Add-Type -TypeDefinition @'\n"
                        + source
                        + "'@; $items=([Console]::In.ReadToEnd() | ConvertFrom-Json); $r=''; foreach($v in $items) { $r=[Vault]::Run($v.action,$v.target,$v.value) }; [Console]::Out.Write([Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($r))) } catch { exit 7 }";
        String executable =
                Path.of(
                                System.getenv("SystemRoot"),
                                "System32",
                                "WindowsPowerShell",
                                "v1.0",
                                "powershell.exe")
                        .toString();
        var builder =
                new ProcessBuilder(
                                executable,
                                "-NoProfile",
                                "-NonInteractive",
                                "-EncodedCommand",
                                Base64.getEncoder()
                                        .encodeToString(script.getBytes(StandardCharsets.UTF_16LE)))
                        .redirectError(ProcessBuilder.Redirect.DISCARD);
        return run(
                builder,
                operations,
                List.of("SystemRoot", "TEMP", "TMP", "USERPROFILE", "LOCALAPPDATA"));
    }

    private String run(
            ProcessBuilder builder,
            List<Map<String, String>> operations,
            List<String> permittedEnvironment)
            throws IOException {
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        var inherited = new HashMap<>(builder.environment());
        builder.environment().clear();
        for (String name : permittedEnvironment) {
            if (inherited.containsKey(name)) builder.environment().put(name, inherited.get(name));
        }
        var process = builder.start();
        try {
            try (var input = process.getOutputStream()) {
                input.write(Json.text(operations).getBytes(StandardCharsets.UTF_8));
            }
            if (!process.waitFor(7, TimeUnit.SECONDS))
                throw new IOException("Credential store timeout");
            if (process.exitValue() != 0)
                throw new IOException("Credential store operation failed");
            byte[] bytes = process.getInputStream().readNBytes(2049);
            if (bytes.length > 2048) throw new IOException("Credential store response limit");
            try {
                return new String(Base64.getDecoder().decode(bytes), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException invalid) {
                throw new IOException("Credential store response invalid");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Credential store interrupted");
        } finally {
            process.destroyForcibly();
        }
    }
}
