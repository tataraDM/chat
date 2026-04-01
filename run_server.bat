@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo 启动聊天服务器（端口 9090）...
if not exist data mkdir data

set JAVA_HOME=D:\env\zulu21.48.17-ca-jdk21.0.10-win_x64
set M2=%USERPROFILE%\.m2\repository
set MYSQL_JAR=%M2%\mysql\mysql-connector-java\8.0.33\mysql-connector-java-8.0.33.jar

"%JAVA_HOME%\bin\java.exe" -cp "target\classes;%MYSQL_JAR%" server.ChatServer

if %errorlevel% neq 0 (
    echo.
    echo 启动失败！请确保已运行 compile.bat 编译项目，且 MySQL 服务已启动。
)
pause
