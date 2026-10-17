@echo off
rem Trastazo para PC: necesita Java 8 o superior (https://adoptium.net)
where javaw >nul 2>nul
if errorlevel 1 (
  echo No se ha encontrado Java. Instala Java (gratis) desde https://adoptium.net y vuelve a abrir Trastazo.
  start "" https://adoptium.net/es/temurin/releases/
  pause
  exit /b 1
)
start "" javaw -Xmx768m -Dsun.java2d.d3d=false -jar "%~dp0Trastazo.jar"
