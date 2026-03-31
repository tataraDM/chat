@echo off
chcp 65001 >nul
echo 正在编译...

if not exist out mkdir out
if not exist data mkdir data

javac -encoding UTF-8 -d out -sourcepath src ^
    src/common/Message.java ^
    src/server/MessageStore.java ^
    src/server/ClientHandler.java ^
    src/server/ChatServer.java ^
    src/client/ChatClient.java ^
    src/client/LoginFrame.java ^
    src/client/MainFrame.java ^
    src/client/ChatFrame.java

if %errorlevel% == 0 (
    echo 编译成功！
) else (
    echo 编译失败，请检查错误信息。
    pause
)
