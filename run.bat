@echo off
echo Compiling Light Polarizer Simulation...
if not exist bin mkdir bin
javac -d bin src\com\simulation\LightPolarizerApp.java
if %ERRORLEVEL% neq 0 (
    echo Compilation failed!
    pause
    exit /b %ERRORLEVEL%
)
echo Running Light Polarizer Simulation...
java -cp bin com.simulation.LightPolarizerApp
pause
