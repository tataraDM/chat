@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo 启动聊天客户端 (JavaFX)...

set JAVA_HOME=D:\env\zulu21.48.17-ca-jdk21.0.10-win_x64

REM ===== JavaFX 模块路径 =====
set M2=%USERPROFILE%\.m2\repository\org\openjfx
set FX_VER=21.0.2
set JAVAFX_MODS=%M2%\javafx-controls\%FX_VER%\javafx-controls-%FX_VER%-win.jar;%M2%\javafx-graphics\%FX_VER%\javafx-graphics-%FX_VER%-win.jar;%M2%\javafx-base\%FX_VER%\javafx-base-%FX_VER%-win.jar

"%JAVA_HOME%\bin\java.exe" --module-path "%JAVAFX_MODS%" ^
     --add-modules javafx.controls ^
     -cp "target\classes;%M2%\javafx-controls\%FX_VER%\javafx-controls-%FX_VER%.jar;%M2%\javafx-graphics\%FX_VER%\javafx-graphics-%FX_VER%.jar;%M2%\javafx-base\%FX_VER%\javafx-base-%FX_VER%.jar" ^
     client.Main

if %errorlevel% neq 0 (
    echo.
    echo 启动失败！
    echo 请确保已运行 compile.bat 编译项目。
)
pause
