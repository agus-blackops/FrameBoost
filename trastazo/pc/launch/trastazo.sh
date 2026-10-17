#!/bin/sh
# Trastazo para PC (Linux/macOS): necesita Java 8 o superior
cd "$(dirname "$0")"
if ! command -v java >/dev/null 2>&1; then
  echo "No se ha encontrado Java. Instálalo (por ejemplo: sudo apt install default-jre) y vuelve a intentarlo."
  exit 1
fi
exec java -Xmx768m -Dsun.java2d.opengl=true -jar Trastazo.jar "$@"
