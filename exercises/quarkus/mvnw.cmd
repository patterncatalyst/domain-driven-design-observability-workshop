@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_S%"=="" (SET __MVNW_ARG0_S=%0) ELSE (SET __MVNW_ARG0_S=%__MVNW_ARG0_S% %0)

@SET __MVNW_CMD=
@SET __MVNW_ERROR=
@SET __MVNW_PSMODULEP_SAVE=%PSModulePath%
@SET PSModulePath=
@FOR /F "usebackq tokens=1* delims==" %%A IN ("%~dp0.mvn\wrapper\maven-wrapper.properties") DO @(
    IF "%%~A"=="distributionUrl" SET "MVNW_DISTRO=%%~B"
)
@IF "%MVNW_DISTRO%"=="" (
    SET __MVNW_ERROR=distributionUrl is not set in .mvn\wrapper\maven-wrapper.properties
    GOTO error
)

@SET __MVNW_DIST_URL=%MVNW_DISTRO%
@FOR %%D IN ("%__MVNW_DIST_URL%") DO @SET "__MVNW_DIST_DIR=%%~nD"

@SET "MAVEN_USER_HOME=%MAVEN_USER_HOME:~0%"
@IF "%MAVEN_USER_HOME%"=="" SET "MAVEN_USER_HOME=%USERPROFILE%\.m2"
@SET "MAVEN_HOME=%MAVEN_USER_HOME%\wrapper\dists\%__MVNW_DIST_DIR%"

@IF EXIST "%MAVEN_HOME%\bin\mvn.cmd" GOTO :exec
@ECHO Downloading from: %__MVNW_DIST_URL%

@REM Download using PowerShell
powershell -Command "&{"^
  "$webclient = new-object System.Net.WebClient;"^
  "if (-not ([string]::IsNullOrEmpty('%MVNW_USERNAME%') -and [string]::IsNullOrEmpty('%MVNW_PASSWORD%'))) {"^
  "  $webclient.Credentials = new-object System.Net.NetworkCredential('%MVNW_USERNAME%', '%MVNW_PASSWORD%');"^
  "}"^
  "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12;"^
  "$webclient.DownloadFile('%__MVNW_DIST_URL%', '%MAVEN_HOME%\%__MVNW_DIST_DIR%.zip')"^
  "}"
@IF %ERRORLEVEL% NEQ 0 (
    SET __MVNW_ERROR=Failed to download %__MVNW_DIST_URL%
    GOTO error
)

@REM Extract
powershell -Command "Expand-Archive -Path '%MAVEN_HOME%\%__MVNW_DIST_DIR%.zip' -DestinationPath '%MAVEN_HOME%' -Force"
@IF %ERRORLEVEL% NEQ 0 (
    SET __MVNW_ERROR=Failed to extract Maven distribution
    GOTO error
)

:exec
@SET PSModulePath=%__MVNW_PSMODULEP_SAVE%
@FOR /F "tokens=* USEBACKQ" %%F IN (`DIR /S /B "%MAVEN_HOME%\bin\mvn.cmd" 2^>NUL`) DO @SET "MVNW_EXEC=%%F"
@IF "%MVNW_EXEC%"=="" (
    SET __MVNW_ERROR=Could not find mvn.cmd in %MAVEN_HOME%
    GOTO error
)
"%MVNW_EXEC%" %*
@ENDLOCAL & SET ERROR_CODE=%ERRORLEVEL%
@EXIT /B %ERROR_CODE%

:error
@ECHO.
@ECHO Error: %__MVNW_ERROR%
@ECHO.
@ENDLOCAL & EXIT /B 1
