[CmdletBinding()]
param(
    [string]$Workspace,
    [string]$Config,
    [string]$JavaHome,
    [switch]$NoBrowser,
    [switch]$NoClipboard
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$hostProcess = $null
$exitCode = 0

# Background pumps never retain unbounded stdout/stderr or stop consuming after readiness.
Add-Type -TypeDefinition @"
using System;
using System.IO;
using System.Threading.Tasks;
using System.Collections.Concurrent;
public sealed class HarnessLauncherPump {
  public readonly Task Completion;
  readonly ConcurrentQueue<string> lines=new ConcurrentQueue<string>();
  public HarnessLauncherPump(TextReader reader, bool capture) {
    Completion=Task.Run(async () => { var buffer=new char[1024]; var line=new System.Text.StringBuilder();
      int count; while((count=await reader.ReadAsync(buffer,0,buffer.Length))>0) {
        if(!capture) continue;
        for(int i=0;i<count;i++){ char c=buffer[i];
          if(c=='\n'){ if(lines.Count>=64){string old;lines.TryDequeue(out old);} lines.Enqueue(line.ToString().TrimEnd('\r'));line.Clear(); }
          else if(line.Length<4096)line.Append(c);
        }
      }
    });
  }
  public bool TryLine(out string value){return lines.TryDequeue(out value);}
  public static Task<string> Input(){return Task.Run(()=>Console.ReadLine());}
}
"@

function Quote-ProcessArgument([string]$Value) {
    # Windows CreateProcess quoting: escape embedded quotes and trailing backslashes.
    return '"' + [regex]::Replace([regex]::Replace($Value, '(\\*)"', '$1$1\"'), '(\\+)$', '$1$1') + '"'
}

function Resolve-Java {
    $candidates = New-Object 'System.Collections.Generic.List[string]'
    if ($JavaHome) {
        $candidates.Add((Join-Path $JavaHome 'bin\java.exe'))
    } else {
        if ($env:JAVA_HOME) { $candidates.Add((Join-Path $env:JAVA_HOME 'bin\java.exe')) }
        $onPath = Get-Command java.exe -ErrorAction SilentlyContinue
        if ($onPath) { $candidates.Add($onPath.Source) }
    }
    foreach ($candidate in $candidates) {
        if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) { continue }
        $probeInfo = New-Object System.Diagnostics.ProcessStartInfo
        $probeInfo.FileName = $candidate
        $probeInfo.Arguments = '-version'
        $probeInfo.UseShellExecute = $false
        $probeInfo.CreateNoWindow = $true
        $probeInfo.RedirectStandardError = $true
        $probeInfo.RedirectStandardOutput = $true
        $probe = New-Object System.Diagnostics.Process
        $probe.StartInfo = $probeInfo
        try {
            [void]$probe.Start()
            $versionPump=New-Object HarnessLauncherPump($probe.StandardError,$true)
            $discard=New-Object HarnessLauncherPump($probe.StandardOutput,$false)
            if (-not $probe.WaitForExit(5000)) { $probe.Kill(); [void]$probe.WaitForExit(1000); continue }
            [void]$versionPump.Completion.Wait(1000)
            $versionLine='';$version=''
            while($versionPump.TryLine([ref]$versionLine)){ $version+=$versionLine }
            if($version -match 'version "21(?:\.|\+|")'){return $candidate}
        } catch { continue } finally { $probe.Dispose() }
    }
    throw 'Java 21 was not found. Set JAVA_HOME to your JDK 21 directory, then reopen the workbench.'
}

function Test-Ready([uri]$Address, [string]$Token) {
    foreach ($path in @('/v1/info', '/')) {
        $request = [System.Net.HttpWebRequest]::Create([uri]::new($Address, $path))
        $request.Proxy = $null
        $request.AllowAutoRedirect = $false
        $request.Timeout = 1500
        $request.ReadWriteTimeout = 1500
        if ($path -eq '/v1/info') { $request.Headers['Authorization'] = 'Bearer ' + $Token }
        $response = $null
        try {
            $response = $request.GetResponse()
            if ([int]$response.StatusCode -ne 200) { return $false }
            if ($path -eq '/' -and $response.ContentType -notlike 'text/html*') { return $false }
        } catch { return $false } finally { if ($response) { $response.Dispose() } }
    }
    return $true
}

try {
    $jar = Join-Path $projectRoot 'harness-cli\target\harness-cli-0.1.0-SNAPSHOT.jar'
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
        throw 'Packaged JAR is missing. Build frontend (npm ci; npm run build), then run mvnw.cmd -B package.'
    }
    if (-not $Workspace) { $Workspace = $projectRoot }
    $Workspace = (Resolve-Path -LiteralPath $Workspace).ProviderPath
    if (-not (Test-Path -LiteralPath $Workspace -PathType Container)) { throw 'Workspace must be a directory.' }
    if (-not $Config) {
        $saved = Join-Path $Workspace '.jharness\config.json'
        $Config = if (Test-Path -LiteralPath $saved -PathType Leaf) { $saved } else { Join-Path $projectRoot 'docs\examples\deepseek-config.json' }
    }
    $Config = (Resolve-Path -LiteralPath $Config).ProviderPath
    # Read only routing names, never write configuration or display its contents.
    $settings = Get-Content -LiteralPath $Config -Raw -Encoding UTF8 | ConvertFrom-Json
    $tokenName = 'JHARNESS_WEB_TOKEN'
    if ($settings.ecosystem -and $settings.ecosystem.web -and $settings.ecosystem.web.tokenEnv) {
        $tokenName = [string]$settings.ecosystem.web.tokenEnv
    }
    if ($tokenName -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') { throw 'Invalid web token environment variable name.' }
    $modelKeyName = if ($settings.apiKeyEnv) { [string]$settings.apiKeyEnv } else { 'JHARNESS_API_KEY' }
    if ($tokenName -ieq $modelKeyName -or @('PATH', 'PATHEXT', 'JAVA_HOME', 'HOME', 'CODEX_HOME', 'USERPROFILE', 'APPDATA', 'LOCALAPPDATA', 'SystemRoot', 'WINDIR', 'ComSpec', 'TEMP', 'TMP', 'PSModulePath') -contains $tokenName) {
        throw 'Web token and model API key must use different environment variable names.'
    }
    $token = [Environment]::GetEnvironmentVariable($tokenName)
    if (-not $token) {
        $bytes = New-Object byte[] 32
        $random = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $random.GetBytes($bytes) } finally { $random.Dispose() }
        $token = [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
    }
    if ($token -notmatch '^[A-Za-z0-9_-]{32,256}$') { throw 'Configured web token has an invalid format.' }

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = Resolve-Java
    $arguments = @('-Dfile.encoding=UTF-8', '-jar', $jar, '--workspace', $Workspace, '--config', $Config, 'serve')
    $startInfo.Arguments = ($arguments | ForEach-Object { Quote-ProcessArgument $_ }) -join ' '
    $startInfo.WorkingDirectory = $projectRoot
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.EnvironmentVariables[$tokenName] = $token
    $hostProcess = New-Object System.Diagnostics.Process
    $hostProcess.StartInfo = $startInfo
    [void]$hostProcess.Start()
    $errors=New-Object HarnessLauncherPump($hostProcess.StandardError,$false)
    $lines=New-Object HarnessLauncherPump($hostProcess.StandardOutput,$true)
    $deadline=[DateTime]::UtcNow.AddSeconds(30)
    $address=$null;$ready=$false
    while([DateTime]::UtcNow -lt $deadline){
        $text=''
        while($lines.TryLine([ref]$text)){
            if($text -match '^WEB (http://127\.0\.0\.1:[0-9]{1,5}) PROFILE default;'){$address=[uri]($Matches[1]+'/')}
        }
        if($address -and (Test-Ready $address $token)){$ready=$true;break}
        if($hostProcess.HasExited){break}
        Start-Sleep -Milliseconds 80
    }
    if(-not $ready){throw 'Workbench did not become ready. Check that the UI is built, configuration is valid, and this workspace is not already served in another window. No existing process was stopped.'}
    if (-not $NoBrowser) { Start-Process -FilePath $address.AbsoluteUri -ErrorAction Stop | Out-Null }
    Write-Host ('WORKBENCH_READY ' + $address.AbsoluteUri)
    Write-Host 'Workbench opens directly. Keep this window open. Enter :quit to stop.'
    $pendingInput=[HarnessLauncherPump]::Input()
    while(-not $hostProcess.HasExited){
        if($pendingInput.IsCompleted){
            $inputLine=$pendingInput.Result
            if($null -eq $inputLine -or $inputLine -eq ':quit'){break}
            $pendingInput=[HarnessLauncherPump]::Input()
        }
        Start-Sleep -Milliseconds 80
    }
    if($hostProcess.HasExited -and $hostProcess.ExitCode -ne 0){$exitCode=1}

} catch {
    # Messages from PowerShell/.NET can contain untrusted configuration text; keep failures bounded.
    $known = @('Java 21 was not found.', 'Packaged JAR is missing.', 'Workspace must be a directory.',
        'Invalid web token environment variable name.', 'Web token and model API key must use different',
        'Configured web token has an invalid format.', 'Workbench did not become ready.')
    $message = [string]$_.Exception.Message
    if ($known | Where-Object { $message.StartsWith($_) }) { Write-Host ('ERROR: ' + $message) }
    else { Write-Host 'ERROR: Could not open the workbench. Check the workspace/config paths, JSON configuration and Java 21 installation.' }
    $exitCode = 1
} finally {
    if ($hostProcess) {
        try {
            if (-not $hostProcess.HasExited) {
                $hostProcess.StandardInput.WriteLine(':quit')
                $hostProcess.StandardInput.Close()
                if (-not $hostProcess.WaitForExit(10000)) {
                    Write-Host 'Host did not stop within the deadline. Stopping this launcher-owned process; inspect UNKNOWN tasks before restarting.'
                    $hostProcess.Kill()
                    [void]$hostProcess.WaitForExit(5000)
                }
            }
        } catch { Write-Host 'Host cleanup was not confirmed. Inspect the workspace before restarting.'; $exitCode = 1 }
        finally {
            if($hostProcess.HasExited -and $hostProcess.ExitCode -ne 0){$exitCode=1}
            $hostProcess.Dispose()
        }
    }
    $token = $null
}
exit $exitCode
