param(
    [ValidateSet('M0', 'M1', 'M2', 'M3', 'M4', 'M5', 'M6', 'M7', 'M8')][string]$Milestone = 'M4',
    [string]$Slice = '',
    [string]$VerificationLog = '',
    [string]$VerificationCommand = '.\mvnw.cmd -B clean verify'
)

$ErrorActionPreference = 'Stop'
$workspacePath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
if (-not $VerificationLog) { $VerificationLog = ".build-cache/$Milestone-verify.log" }
$logPath = Join-Path $workspacePath $VerificationLog
if (-not (Select-String -LiteralPath $logPath -Pattern '\[INFO\] BUILD SUCCESS' -Quiet)) {
    throw 'The verification log does not confirm BUILD SUCCESS.'
}

$suites = @()
$sources = [ordered]@{}
$javaVersion = $null
[xml]$rootPom = Get-Content -LiteralPath (Join-Path $workspacePath 'pom.xml') -Raw
foreach ($module in $rootPom.project.modules.module) {
    $reportDirectory = Join-Path $workspacePath "$module/target/surefire-reports"
    $reports = @()
    if (Test-Path -LiteralPath $reportDirectory) { $reports = @(Get-ChildItem -LiteralPath $reportDirectory -Filter 'TEST-*.xml') }
    foreach ($report in $reports) {
        if ($report.LastWriteTimeUtc -gt (Get-Item -LiteralPath $logPath).LastWriteTimeUtc) { throw 'Test report is newer than the verification log.' }
        [xml]$xml = Get-Content -LiteralPath $report.FullName -Raw
        $suite = $xml.testsuite
        if (-not $javaVersion) {
            $javaVersion = ($suite.properties.property | Where-Object name -EQ 'java.version').value
        }
        $suites += [ordered]@{
            module = $module
            suite = [string]$suite.name
            tests = [int]$suite.tests
            failures = [int]$suite.failures
            errors = [int]$suite.errors
            skipped = [int]$suite.skipped
            cases = @($suite.testcase | ForEach-Object { [string]$_.name })
        }
    }
    $modulePath = Join-Path $workspacePath $module
    $sourceFiles = @(Get-ChildItem -LiteralPath (Join-Path $modulePath 'src') -Filter '*.java' -Recurse -File)
    $sourceFiles += Get-Item -LiteralPath (Join-Path $modulePath 'pom.xml')
    foreach ($source in $sourceFiles | Sort-Object FullName) {
        if ($source.LastWriteTimeUtc -gt (Get-Item -LiteralPath $logPath).LastWriteTimeUtc) { throw "Source changed after verification: $($source.Name)" }
        $relativePath = [IO.Path]::GetRelativePath($workspacePath, $source.FullName).Replace('\', '/')
        $sources[$relativePath] = (Get-FileHash -LiteralPath $source.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}
foreach ($buildFile in @('pom.xml', 'mvnw', 'mvnw.cmd', '.mvn/wrapper/maven-wrapper.properties', '.github/workflows/ci.yml', 'jharness', 'jharness.cmd', 'scripts/live-evaluate.py', 'scripts/collect-test-evidence.ps1', 'docs/examples/deepseek-config.json', 'docs/examples/m6-config.json', 'docs/development/M6-contracts.md', 'docs/development/M7-contracts.md', 'docs/examples/m7-config.json', 'docs/examples/m7-cli.md', 'docs/requirements/upstream-capabilities.md', 'docs/testing/M7-upstream-review.json')) {
    $sources[$buildFile] = (Get-FileHash -LiteralPath (Join-Path $workspacePath $buildFile) -Algorithm SHA256).Hash.ToLowerInvariant()
}

$totals = [ordered]@{}
if ($Milestone -eq 'M8') {
    foreach ($entry in @('docs/development/M8-contracts.md', 'docs/examples/m8-filter-config.json', 'docs/examples/m8-scheduling-config.json', 'docs/examples/m8-tool-scheduling.md', 'docs/examples/m8-model-resilience.json', 'docs/examples/m8-model-resilience.md')) {
        $sources[$entry] = (Get-FileHash -LiteralPath (Join-Path $workspacePath $entry) -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}
foreach ($field in @('tests', 'failures', 'errors', 'skipped')) {
    $totals[$field] = [int](($suites | ForEach-Object { $_[$field] } | Measure-Object -Sum).Sum)
}
$evidence = [ordered]@{
    milestone = $Milestone
    slice = $Slice
    capturedAt = [DateTimeOffset]::UtcNow.ToOffset([TimeSpan]::FromHours(8)).ToString('o')
    command = $VerificationCommand
    buildResult = 'SUCCESS'
    platform = [Runtime.InteropServices.RuntimeInformation]::OSDescription
    javaVersion = $javaVersion
    mavenVersion = '3.9.9'
    codeVersion = 'uncommitted; identify this tested source snapshot by sourceSha256'
    log = $VerificationLog
    logSha256 = (Get-FileHash -LiteralPath $logPath -Algorithm SHA256).Hash.ToLowerInvariant()
    totals = $totals
    suites = $suites
    sourceSha256 = $sources
    remoteCiResult = 'not run'
}
$outputPath = Join-Path $workspacePath "docs/testing/$Milestone-results.json"
$evidence | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $outputPath -Encoding utf8
$totals | ConvertTo-Json -Compress

