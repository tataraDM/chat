@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo 启动聊天服务器（端口 9090）...
if not exist data mkdir data
java -cp out server.ChatServer
pause
