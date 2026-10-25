$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$java=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/java.exe'}else{'java.exe'}
$bootstrap=Join-Path $PSScriptRoot 'Bootstrap.java'
$mavenHome=& $java $bootstrap $root
if($LASTEXITCODE -ne 0 -or -not $mavenHome){exit 1}
$env:MVNW_USERNAME=$null; $env:MVNW_PASSWORD=$null; $env:MVNW_REPOURL=$null
& (Join-Path $mavenHome 'bin/mvn.cmd') @args
exit $LASTEXITCODE
