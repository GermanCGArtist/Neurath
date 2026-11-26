@echo off
rem Builds and tests Neurath. Output: build\libs\neurath-<version>.jar
rem Uses portable JDK 25 in tools\jdk-25* or ..\Manhattan\tools\jdk-25* if present, otherwise JAVA_HOME.
set "ROOT=%~dp0"
for /d %%D in ("%ROOT%tools\jdk-25*") do set "JAVA_HOME=%%~fD"
if not defined JAVA_HOME (
    for /d %%D in ("%ROOT%..\Manhattan\tools\jdk-25*") do set "JAVA_HOME=%%~fD"
)
if not exist "%ROOT%tmp" mkdir "%ROOT%tmp"
set "TMP=%ROOT%tmp"
set "TEMP=%ROOT%tmp"
set "JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=%ROOT%tmp"
if "%~1"=="" (
    call "%~dp0gradlew.bat" test build
) else (
    call "%~dp0gradlew.bat" %*
)
