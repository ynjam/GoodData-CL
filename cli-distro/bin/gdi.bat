@echo off
setlocal ENABLEDELAYEDEXPANSION

SET PRJ_BIN=%~dp0
SET PRJ=%PRJ_BIN%..

if not "%JAVA_HOME%" == "" goto jhomeok

if not "%JAVA_HOME%" == "" goto javaHomeAlreadySet
for %%P in (%PATH%) do if exist %%P\java.exe set JAVA_EXE=%%P\java.exe

if not "%JAVA_EXE%" == "" goto execute

echo.
echo ERROR: JAVA_HOME not found in your environment.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation
echo.
goto error

:jhomeok
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory.
echo JAVA_HOME = "%JAVA_HOME%"
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation
echo.
goto error

:execute

if defined CLASSPATH (set CLSPTH=%CLASSPATH%;%PRJ%;.) else (set CLSPTH=%PRJ%)

FOR /R "%PRJ%\lib" %%G IN (*.jar) DO set CLSPTH=!CLSPTH!;%%G

setlocal DISABLEDELAYEDEXPANSION
"%JAVA_EXE%" -Xmx1024M -Dlog4j.configuration="%PRJ%\log4j.configuration" -Dderby.system.home="%PRJ%\db" -Djava.io.tmpdir="%PRJ%\tmp" -classpath "%CLSPTH%" com.gooddata.processor.GdcDI %*
goto end

:error
@REM error sink

:end
@REM end of the script
