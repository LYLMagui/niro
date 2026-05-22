@echo off
setlocal
chcp 65001 >nul

where python >nul 2>nul
if %errorlevel%==0 goto run_script
where py >nul 2>nul
if %errorlevel%==0 goto run_with_py

echo 未检测到 Python，开始自动安装 Python 3.11 到 C:\Program Files\Python311 ...
set "PYTHON_INSTALLER=%TEMP%\python-3.11.9-amd64.exe"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://www.python.org/ftp/python/3.11.9/python-3.11.9-amd64.exe' -OutFile '%PYTHON_INSTALLER%'"
if not exist "%PYTHON_INSTALLER%" (
  echo Python 安装包下载失败。
  pause
  exit /b 1
)

"%PYTHON_INSTALLER%" /quiet InstallAllUsers=1 TargetDir="C:\Program Files\Python311" PrependPath=1 Include_test=0 SimpleInstall=1
if %errorlevel% neq 0 (
  echo Python 安装失败，请以管理员身份运行此脚本后重试。
  pause
  exit /b 1
)

set "PATH=C:\Program Files\Python311;C:\Program Files\Python311\Scripts;%PATH%"
where python >nul 2>nul
if %errorlevel% neq 0 (
  echo Python 安装完成，但当前会话未找到 python 命令，请重新打开脚本再试。
  pause
  exit /b 1
)

:run_script
python "D:\MySpace\niro\docs\demo\market_query.py"
goto end

:run_with_py
py "D:\MySpace\niro\docs\demo\market_query.py"

:end
echo.
pause
