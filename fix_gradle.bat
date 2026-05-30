@echo off
REM Fix Gradle Kotlin Plugin Error - Auto Script

echo.
echo ====================================
echo Fixing Gradle Kotlin Plugin Error
echo ====================================
echo.

REM Delete Gradle cache
echo Deleting Gradle cache...
rmdir /s /q %USERPROFILE%\.gradle 2>nul
if exist %USERPROFILE%\.gradle (
    icacls %USERPROFILE%\.gradle /grant %USERNAME%:F /T /C
    rmdir /s /q %USERPROFILE%\.gradle 2>nul
)
echo [OK] Gradle cache deleted

REM Delete build folders
echo Deleting build folders...
cd /d D:\MyDATA\SEMESTER6\Chuyende\Final
if exist app\build rmdir /s /q app\build 2>nul
if exist build rmdir /s /q build 2>nul
echo [OK] Build folders deleted

echo.
echo ====================================
echo DONE! Now:
echo 1. Close Android Studio
echo 2. Wait 10 seconds
echo 3. Open Android Studio again
echo 4. Build > Clean Project
echo 5. Build > Build Project
echo ====================================
echo.

pause

