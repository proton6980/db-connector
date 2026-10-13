@echo off
set "APP_HOME=%~dp0.."
"%APP_HOME%\jre\bin\java.exe" -jar "%APP_HOME%\db-connector.jar" %*
