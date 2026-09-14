@rem Gradle start-up script for Windows (bootstrap via Android Studio if wrapper JAR is missing).
@if "%DEBUG%"=="" @echo off

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

if not exist "%CLASSPATH%" (
  echo gradle-wrapper.jar is missing.
  echo Open the mobile/ folder in Android Studio to generate the Gradle Wrapper, or run:
  echo   gradle wrapper --gradle-version 8.11.1
  exit /b 1
)

set JAVA_EXE=java.exe
where java >NUL 2>&1
if %ERRORLEVEL% neq 0 (
  echo ERROR: java not found on PATH.
  exit /b 1
)

"%JAVA_EXE%" %DEFAULT_JVM_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
