@echo off
setlocal
if exist "%~dp0Budora\windows\main.py" (
    cd /d "%~dp0Budora\windows"
) else if exist "%~dp0windows\main.py" (
    cd /d "%~dp0windows"
) else if exist "%~dp0main.py" (
    cd /d "%~dp0"
)

set "PY_EXE="
if exist "%LOCALAPPDATA%\Programs\Python\Python311\pythonw.exe" (
    set "PY_EXE=%LOCALAPPDATA%\Programs\Python\Python311\pythonw.exe"
) else (
    where pythonw >nul 2>nul
    if %ERRORLEVEL% equ 0 (
        set "PY_EXE=pythonw"
    ) else (
        set "PY_EXE=python"
    )
)

start "" /d "%CD%" "%PY_EXE%" main.py
exit
