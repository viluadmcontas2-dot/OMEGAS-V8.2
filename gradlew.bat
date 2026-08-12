@echo off
setlocal
set "DIR=%~dp0"
set "APP_HOME=%DIR:~0,-1%"
java "-Dgradle.wrapper.appHome=%APP_HOME%" -classpath "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%

