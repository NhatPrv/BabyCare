@echo off
setlocal

powershell -ExecutionPolicy Bypass -File "%~dp0collect_colab_ml_files.ps1"

endlocal