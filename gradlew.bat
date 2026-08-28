@rem Copyright 2010-2026 the original authors or license holders.
@rem Gradle wrapper batch script. Requires gradle\wrapper\gradle-wrapper.jar to be present.
@echo off
setlocal
set PRG=%~dp0%~nx0
:findloop
if exist "%PRG%" goto found
echo Wrapper script not found
goto :eof
:found
set APP_HOME=%~dp0
set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
if defined JAVA_HOME (
  set JAVA_CMD=%JAVA_HOME%\bin\java
) else (
  set JAVA_CMD=java
)
"%JAVA_CMD%" -cp "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
