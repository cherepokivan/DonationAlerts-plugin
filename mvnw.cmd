@ECHO OFF
SETLOCAL
SET "BASE_DIR=%~dp0"
SET "MAVEN_VERSION=3.9.11"
SET "MAVEN_HOME=%BASE_DIR%.mvn\wrapper\apache-maven-%MAVEN_VERSION%"
IF EXIST "%MAVEN_HOME%\bin\mvn.cmd" GOTO run
SET "ARCHIVE=%BASE_DIR%.mvn\wrapper\apache-maven-%MAVEN_VERSION%-bin.zip"
IF NOT EXIST "%ARCHIVE%" powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip' -OutFile '%ARCHIVE%'"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ARCHIVE%' '%BASE_DIR%.mvn\wrapper'"
:run
CALL "%MAVEN_HOME%\bin\mvn.cmd" %*
ENDLOCAL
