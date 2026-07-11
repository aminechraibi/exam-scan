@echo off
setlocal
set VERSION=8.10.2
set BASE=%USERPROFILE%\.gradle\wrapper\dists\gradle-%VERSION%-bin\manual
set ZIP=%BASE%\gradle-%VERSION%-bin.zip
set GRADLE_HOME=%BASE%\gradle-%VERSION%
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  if not exist "%BASE%" mkdir "%BASE%"
  if not exist "%ZIP%" powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip' -OutFile '%ZIP%'"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%ZIP%' -DestinationPath '%BASE%' -Force"
)
call "%GRADLE_HOME%\bin\gradle.bat" %*
endlocal
