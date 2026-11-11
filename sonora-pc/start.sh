#!/usr/bin/env bash
# Sonora para PC (Linux / macOS): abre el servidor local y el navegador.
# Requiere Node.js 18 o superior.
cd "$(dirname "$0")" || exit 1
if ! command -v node >/dev/null 2>&1; then
  echo "No se encontró Node.js. Instálalo desde https://nodejs.org" >&2
  exit 1
fi
exec node server.js --open "$@"
