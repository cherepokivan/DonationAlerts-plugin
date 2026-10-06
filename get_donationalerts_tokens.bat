@echo off
cd /d "%~dp0"
py -3 --version >nul 2>&1
if errorlevel 1 (
    python get_donationalerts_tokens.py
) else (
    py -3 get_donationalerts_tokens.py
)
echo.
pause
