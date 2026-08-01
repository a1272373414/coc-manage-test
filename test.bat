@echo off
REM 一键运行前后端测试（固定使用系统 mvn，不再探测 mvnw）
setlocal
cd /d %~dp0

echo [1/2] 运行后端测试 (mvn test)
call mvn -B -s settings.xml test
if %ERRORLEVEL% neq 0 (
  echo [FAIL] 后端测试未通过
  exit /b %ERRORLEVEL%
)

echo [2/2] 运行前端测试 (jest)
cd /d %~dp0frontend-test
call npx jest
if %ERRORLEVEL% neq 0 (
  echo [FAIL] 前端测试未通过
  exit /b %ERRORLEVEL%
)

echo [OK] 前后端测试全部通过
endlocal
