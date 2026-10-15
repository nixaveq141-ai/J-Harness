@echo off
setlocal
if "%JAVA_HOME%"=="" (
  echo Set JAVA_HOME to JDK 21. 1>&2
  exit /b 5
)
if not exist "%~dp0harness-cli\target\harness-cli-0.1.0-SNAPSHOT.jar" (
  echo Build first with mvnw.cmd -B package. 1>&2
  exit /b 5
)
"%JAVA_HOME%\bin\java.exe" -jar "%~dp0harness-cli\target\harness-cli-0.1.0-SNAPSHOT.jar" %*
exit /b %ERRORLEVEL%
