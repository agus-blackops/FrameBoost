@echo off
rem Sonora para PC: abre el servidor local y el navegador.
rem Requiere Node.js 18 o superior (https://nodejs.org).
cd /d "%~dp0"
where node >nul 2>nul
if errorlevel 1 (
  echo No se encontro Node.js. Instalalo desde https://nodejs.org y vuelve a abrir este archivo.
  pause
  exit /b 1
)
node server.js --open %*
pause
