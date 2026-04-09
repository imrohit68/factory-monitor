@echo off
setlocal EnableExtensions
REM ============================================================================
REM Production Calling System — Windows launcher (place next to the Spring Boot JAR)
REM Requires: Java 17+ (set JAVA_HOME or ensure java.exe is on PATH)
REM Edit the CONFIG section below, then double-click or run: run-factory-monitor.bat
REM ============================================================================

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

REM --- JAR (override with set FACTORY_MONITOR_JAR=... before running) ------------
if not defined FACTORY_MONITOR_JAR set "FACTORY_MONITOR_JAR=production-calling-system-0.0.1-SNAPSHOT.jar"
set "JAR_PATH=%SCRIPT_DIR%%FACTORY_MONITOR_JAR%"
if not exist "%JAR_PATH%" (
  echo ERROR: JAR not found: "%JAR_PATH%"
  echo Place this .bat in the same folder as the JAR, or set FACTORY_MONITOR_JAR to the file name.
  exit /b 1
)

REM --- Java --------------------------------------------------------------------
if defined JAVA_HOME (
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_EXE=java"
)
"%JAVA_EXE%" -version >nul 2>&1
if errorlevel 1 (
  echo ERROR: Java not found. Install JDK 17+ and set JAVA_HOME, or add java to PATH.
  exit /b 1
)

REM ############################################################################
REM CONFIG — adjust for your PC (COM port, data folder, HTTP port, Modbus, etc.)
REM ############################################################################

REM JVM (optional)
set "JVM_OPTS=-Dfile.encoding=UTF-8 -Xms256m -Xmx512m"

REM HTTP
set "SERVER_PORT=8080"

REM First-run admin seed (only used when the user table is empty)
set "SYSTEM_SECURITY_INITIAL_USERNAME=admin"
set "SYSTEM_SECURITY_INITIAL_PASSWORD=admin@123"

REM Data: SQLite DB and alert audio live under this folder (use a fixed path on servers)
set "SYSTEM_DATA_DIR=%USERPROFILE%\production-calling-system-data"

REM Optional: separate folder for uploaded workstation audio (leave empty to use DATA_DIR\alert-audio)
set "SYSTEM_AUDIO_UPLOAD_DIR="

REM Event log CSV export window (days)
set "SYSTEM_LOG_RETENTION_DAYS=30"

REM Dashboard alert repeat behavior
set "SYSTEM_DASHBOARD_ALERT_REPEAT_INTERVAL_MINUTES=1"
set "SYSTEM_DASHBOARD_ALERT_MAX_REPEATS=2"

REM Modbus RTU — Windows serial port is usually COM3, COM4, ...
set "SYSTEM_MODBUS_INPUT_SLAVE_ID=1"
set "SYSTEM_MODBUS_RELAY_CHANNELS_PER_SLAVE=32"
set "SYSTEM_MODBUS_PORT_NAME=COM3"
set "SYSTEM_MODBUS_BAUD_RATE=9600"
set "SYSTEM_MODBUS_DATA_BITS=8"
set "SYSTEM_MODBUS_STOP_BITS=1"
set "SYSTEM_MODBUS_PARITY=None"
set "SYSTEM_MODBUS_ENCODING=rtu"
set "SYSTEM_MODBUS_INPUT_REGISTER_START=0"
set "SYSTEM_MODBUS_INPUT_REGISTER_COUNT=4"
set "SYSTEM_MODBUS_POLL_INTERVAL_MS=5000"
set "SYSTEM_MODBUS_LOG_EACH_READ=false"
set "SYSTEM_MODBUS_COIL_START_ADDRESS=0"

REM Optional overrides (leave blank to use defaults inside application.properties)
set "SPRING_DATASOURCE_URL="
set "SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE="
set "SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE="

REM ############################################################################
REM Launch — Spring Boot accepts --property=value (same keys as application.properties)
REM Quoted args below support spaces in paths and in passwords.
REM Browser UI; app runs until stopped (Admin → Shut down application). For JavaFX window instead:
REM   --system.desktop-mode=true --system.launch-browser=false
REM ############################################################################

set "APP_ARGS=--server.port=%SERVER_PORT%"
set APP_ARGS=%APP_ARGS% --system.desktop-mode=false
set APP_ARGS=%APP_ARGS% --system.launch-browser=true
set APP_ARGS=%APP_ARGS% "--system.security.initial-username=%SYSTEM_SECURITY_INITIAL_USERNAME%"
set APP_ARGS=%APP_ARGS% "--system.security.initial-password=%SYSTEM_SECURITY_INITIAL_PASSWORD%"
set APP_ARGS=%APP_ARGS% "--system.data-dir=%SYSTEM_DATA_DIR%"
set APP_ARGS=%APP_ARGS% --system.log-retention-days=%SYSTEM_LOG_RETENTION_DAYS%
set APP_ARGS=%APP_ARGS% --system.dashboard-alert-repeat-interval-minutes=%SYSTEM_DASHBOARD_ALERT_REPEAT_INTERVAL_MINUTES%
set APP_ARGS=%APP_ARGS% --system.dashboard-alert-max-repeats=%SYSTEM_DASHBOARD_ALERT_MAX_REPEATS%
set APP_ARGS=%APP_ARGS% --system.modbus.input-slave-id=%SYSTEM_MODBUS_INPUT_SLAVE_ID%
set APP_ARGS=%APP_ARGS% --system.modbus.relay-channels-per-slave=%SYSTEM_MODBUS_RELAY_CHANNELS_PER_SLAVE%
set APP_ARGS=%APP_ARGS% --system.modbus.port-name=%SYSTEM_MODBUS_PORT_NAME%
set APP_ARGS=%APP_ARGS% --system.modbus.baud-rate=%SYSTEM_MODBUS_BAUD_RATE%
set APP_ARGS=%APP_ARGS% --system.modbus.data-bits=%SYSTEM_MODBUS_DATA_BITS%
set APP_ARGS=%APP_ARGS% --system.modbus.stop-bits=%SYSTEM_MODBUS_STOP_BITS%
set APP_ARGS=%APP_ARGS% --system.modbus.parity=%SYSTEM_MODBUS_PARITY%
set APP_ARGS=%APP_ARGS% --system.modbus.encoding=%SYSTEM_MODBUS_ENCODING%
set APP_ARGS=%APP_ARGS% --system.modbus.input-register-start=%SYSTEM_MODBUS_INPUT_REGISTER_START%
set APP_ARGS=%APP_ARGS% --system.modbus.input-register-count=%SYSTEM_MODBUS_INPUT_REGISTER_COUNT%
set APP_ARGS=%APP_ARGS% --system.modbus.poll-interval-ms=%SYSTEM_MODBUS_POLL_INTERVAL_MS%
set APP_ARGS=%APP_ARGS% --system.modbus.log-each-read=%SYSTEM_MODBUS_LOG_EACH_READ%
set APP_ARGS=%APP_ARGS% --system.modbus.coil-start-address=%SYSTEM_MODBUS_COIL_START_ADDRESS%

if not "%SYSTEM_AUDIO_UPLOAD_DIR%"=="" set APP_ARGS=%APP_ARGS% "--system.audio-upload-dir=%SYSTEM_AUDIO_UPLOAD_DIR%"
if not "%SPRING_DATASOURCE_URL%"=="" set APP_ARGS=%APP_ARGS% "--spring.datasource.url=%SPRING_DATASOURCE_URL%"
if not "%SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE%"=="" set APP_ARGS=%APP_ARGS% --spring.servlet.multipart.max-file-size=%SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE%
if not "%SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE%"=="" set APP_ARGS=%APP_ARGS% --spring.servlet.multipart.max-request-size=%SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE%

"%JAVA_EXE%" %JVM_OPTS% -jar "%JAR_PATH%" %APP_ARGS%

endlocal
exit /b %ERRORLEVEL%
