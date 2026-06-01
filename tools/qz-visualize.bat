@echo off
REM ============================================================
REM Qz Layout Visualizer — 不进游戏看 Qz UI 布局结果
REM
REM 用法：双击运行此脚本，自动编译并在浏览器中打开生成的 HTML
REM ============================================================
setlocal enabledelayedexpansion

set QZ_JAR=%APPDATA%\PrismLauncher\instances\GTNH290配方\.minecraft\mods\qz_uilib-4.1.3-LTS.jar
set OUT_DIR=docs\html-reference
set BUILD_DIR=build\tmp\qz-visualizer

echo ========================================
echo   Qz Layout Visualizer
echo ========================================
echo.

REM 查找 log4j JAR（从 Gradle 缓存）
set LOG4J_JAR=
for /d %%d in ("%USERPROFILE%\.gradle\caches\modules-2\files-2.1\org.apache.logging.log4j\log4j-api") do (
  for /r "%%d" %%f in (log4j-api-*.jar) do (
    set LOG4J_JAR=%%f
    goto :found_log4j
  )
)
:found_log4j
if "%LOG4J_JAR%"=="" (
  echo [WARN] log4j JAR not found, layout may fail to run.
  set LOG4J_CP=%QZ_JAR%
) else (
  echo [INFO] log4j: !LOG4J_JAR!
  set LOG4J_CP=%QZ_JAR%;!LOG4J_JAR!
)

REM 清理并创建构建目录
if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"
del "%BUILD_DIR%\*.class" 2>nul

echo [1/2] Compiling...
javac -cp "%QZ_JAR%" -d "%BUILD_DIR%" tools\LayoutVisualizer.java tools\LayoutPreview.java 2>&1
if %ERRORLEVEL% NEQ 0 (
  echo.
  echo [FAIL] Compilation failed. Check that Java 17+ is available.
  pause
  exit /b 1
)

echo [2/2] Running layout engine...
java -cp "!LOG4J_CP!;%BUILD_DIR%" LayoutPreview 2>&1

echo.
echo [OK] Layout HTML file written to %OUT_DIR%\
echo      - qz-layout-terminal.html (多视口标签页切换)
echo.

REM 打开 HTML
start "" "%OUT_DIR%\qz-layout-terminal.html"

endlocal
pause
