@echo off
setlocal EnableExtensions DisableDelayedExpansion
set "JMETER_HOME=%~dp0apache-jmeter-5.6.3"
set "PLAN=%~dp0BCOH2H-2436.jmx"
if not exist "%JMETER_HOME%\bin\jmeter.bat" goto missing_files
if not exist "%PLAN%" goto missing_files

set "JDK21="
if defined JAVA_HOME call :try_jdk "%JAVA_HOME%"
if not defined JDK21 for /f "delims=" %%J in ('where.exe javac.exe 2^>nul') do if not defined JDK21 call :try_jdk "%%~dpJ.."
if not defined JDK21 for /d %%J in ("%ProgramFiles%\Eclipse Adoptium\jdk-21*") do if not defined JDK21 call :try_jdk "%%~fJ"
if not defined JDK21 goto missing_java

set "JAVA_HOME=%JDK21%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "JMETER_BIN=%JMETER_HOME%\bin\"
set "JM_LAUNCH=%JAVA_HOME%\bin\java.exe"
pushd "%~dp0" || goto missing_files
call "%JMETER_BIN%jmeter.bat" -t "%PLAN%"
set "LAUNCH_STATUS=%ERRORLEVEL%"
popd
exit /b %LAUNCH_STATUS%

:try_jdk
if not exist "%~1\bin\java.exe" exit /b 1
if not exist "%~1\bin\javac.exe" exit /b 1
if not exist "%~1\release" exit /b 1
set "JDK_VERSION="
for /f "usebackq tokens=1,* delims==" %%A in ("%~1\release") do if "%%A"=="JAVA_VERSION" set "JDK_VERSION=%%~B"
if "%JDK_VERSION%"=="21" set "JDK21=%~f1"
if "%JDK_VERSION:~0,3%"=="21." set "JDK21=%~f1"
exit /b 0

:missing_java
echo JDK 21 was not found. Set JAVA_HOME to your existing JDK 21 folder.
echo Example: C:\Program Files\Eclipse Adoptium\jdk-21.0.3.9-hotspot
pause
exit /b 1

:missing_files
echo Extract the entire ZIP before launching. Keep the test plan and JMeter folder together.
pause
exit /b 1
