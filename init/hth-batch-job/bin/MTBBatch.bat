@echo off
setlocal
REM Independent Java 21 HTH launcher. Usage: MTBBatch.bat CRM_EXTRACT [yyyyMMdd]
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Java\jdk-21"
if not defined BATCH_HOME set "BATCH_HOME=%~dp0.."
if not defined CONFIG_DIR set "CONFIG_DIR=%BATCH_HOME%\hth-mtb\config"
if not defined LOG_DIR set "LOG_DIR=%BATCH_HOME%\logs"
if not defined BATCH_JAR set "BATCH_JAR=%BATCH_HOME%\hth-mtb\target\hth-batch-job-mtb-1.0.0.jar"
if not defined JVM_OPTS set "JVM_OPTS=-Xmx1024M"
if "%~1"=="" exit /b 2
if not exist "%BATCH_JAR%" exit /b 50
if not exist "%JAVA_HOME%\bin\java.exe" exit /b 50
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
if errorlevel 1 exit /b 50
set "HTH_LOG_FILE=%LOG_DIR%\MTBBatch.%RANDOM%.%RANDOM%.log"
echo HTH_BATCH log=%HTH_LOG_FILE%
cd /d "%BATCH_HOME%"
if errorlevel 1 exit /b 50
"%JAVA_HOME%\bin\java" %JVM_OPTS% "-Dconfig.path=%CONFIG_DIR%" "-Dbatch.log.dir=%LOG_DIR%" -jar "%BATCH_JAR%" %* > "%HTH_LOG_FILE%" 2>&1
set "HTH_EXIT_CODE=%ERRORLEVEL%"
if not "%HTH_EXIT_CODE%"=="0" if not "%HTH_EXIT_CODE%"=="2" set "HTH_EXIT_CODE=50"
echo HTH_BATCH exit=%HTH_EXIT_CODE% log=%HTH_LOG_FILE%
exit /b %HTH_EXIT_CODE%
