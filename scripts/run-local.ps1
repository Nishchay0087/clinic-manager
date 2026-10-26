$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (!(Test-Path Env:JAVA_HOME) -or !(Test-Path Env:CATALINA_HOME)) { throw 'Set JAVA_HOME to a Java 25+ JDK and set CATALINA_HOME first.' }
if (!(Test-Path Env:DB_PASSWORD) -or ($env:DOCUMENT_STORAGE_MODE -ne "mysql" -and !(Test-Path Env:DOCUMENT_STORAGE_DIR))) { throw 'Set DB_PASSWORD and DOCUMENT_STORAGE_DIR first.' }
mvn clean package
if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }
Copy-Item target/clinic-manager.war "$env:CATALINA_HOME/webapps/clinic-manager.war"
& "$env:CATALINA_HOME/bin/catalina.bat" run
