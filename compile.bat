@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ============================================
echo   ChaTTE 编译脚本 (Maven)
echo ============================================
echo.

set JAVA_HOME=D:\env\zulu21.48.17-ca-jdk21.0.10-win_x64

echo 正在编译...
call mvnw.cmd compile
if %errorlevel% == 0 (
    echo.
    echo 编译成功！
    echo 输出目录: target\classes
) else (
    echo.
    echo 编译失败，请检查错误信息。
)
pause
