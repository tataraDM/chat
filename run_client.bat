@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo 启动聊天客户端...
java -cp out client.LoginFrame
if %errorlevel% neq 0 (
    echo.
    echo 启动失败，错误码: %errorlevel%
    pause
)
