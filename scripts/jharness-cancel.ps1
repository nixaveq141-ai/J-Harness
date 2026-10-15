param(
    [Parameter(Mandatory = $true)][string]$RunId,
    [string]$StateDirectory = ''
)
$ErrorActionPreference = 'Stop'
$workspacePath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
if (-not $StateDirectory) { $StateDirectory = Join-Path $workspacePath '.jharness/runs' }
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 21.' }
$processJar = Join-Path $workspacePath 'harness-process/target/harness-process-0.1.0-SNAPSHOT.jar'
$apiJar = Join-Path $workspacePath 'harness-api/target/harness-api-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $processJar) -or -not (Test-Path -LiteralPath $apiJar)) { throw 'Build first with .\mvnw.cmd -B package.' }
$runtimeClasspath = $processJar + [IO.Path]::PathSeparator + $apiJar
& (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp $runtimeClasspath io.jharness.process.CancelMain --state-dir $StateDirectory --run $RunId
exit $LASTEXITCODE
