@ECHO OFF
SETLOCAL

SET "MAVEN_PROJECTBASEDIR=%~dp0"
IF "%MAVEN_PROJECTBASEDIR:~-1%"=="\" SET "MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%"

SET "WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties"
IF NOT EXIST "%WRAPPER_PROPERTIES%" (
    ECHO Error: Could not find .mvn\wrapper\maven-wrapper.properties 1>&2
    EXIT /B 1
)

FOR /F "usebackq tokens=1,* delims==" %%A IN ("%WRAPPER_PROPERTIES%") DO (
    IF "%%A"=="distributionUrl" SET "DISTRIBUTION_URL=%%B"
    IF "%%A"=="wrapperUrl" SET "WRAPPER_URL=%%B"
)

SET "WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"

IF NOT EXIST "%WRAPPER_JAR%" (
    IF "%MVNW_VERBOSE%"=="true" ECHO Downloading maven-wrapper.jar from %WRAPPER_URL% 1>&2
    powershell -Command "(New-Object System.Net.WebClient).DownloadFile('%WRAPPER_URL%', '%WRAPPER_JAR%')"
    IF NOT EXIST "%WRAPPER_JAR%" (
        ECHO Error: Failed to download maven-wrapper.jar 1>&2
        EXIT /B 1
    )
)

IF "%JAVA_HOME%"=="" (
    SET "JAVA_CMD=java"
) ELSE (
    SET "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
)

SET "MAVEN_OPTS=%MAVEN_OPTS% -Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%"

"%JAVA_CMD%" %MAVEN_OPTS% -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*

SET MAVEN_EXITCODE=%ERRORLEVEL%
ENDLOCAL
EXIT /B %MAVEN_EXITCODE%
