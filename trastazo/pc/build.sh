#!/usr/bin/env bash
# Builds the PC version: the shared game code (src) + desktop stand-ins for the Android classes (pc/shim)
# + the desktop window (pc/src) -> dist/trastazo-pc.zip (Trastazo.jar + launchers). Needs only a JDK.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=build/pc
rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/pkg/Trastazo"
find pc/shim pc/src -name '*.java' > "$OUT/sources.txt"
find src -name '*.java' ! -name MainActivity.java ! -name GameView.java ! -name GLGameView.java >> "$OUT/sources.txt"
javac -nowarn --release 8 -encoding UTF-8 -d "$OUT/classes" @"$OUT/sources.txt" 2>&1 | grep -v "^warning: \[options\]" | grep -v "^Note:" | grep -v "^[0-9]* warning" || true
test -f "$OUT/classes/com/frameboost/ragdoll/PcMain.class"
cp dist/trastazo-logo-512.png "$OUT/classes/trastazo-logo.png"
printf 'Main-Class: com.frameboost.ragdoll.PcMain\n' > "$OUT/manifest.txt"
jar cfm "$OUT/pkg/Trastazo/Trastazo.jar" "$OUT/manifest.txt" -C "$OUT/classes" .
cp pc/launch/* "$OUT/pkg/Trastazo/"
chmod +x "$OUT/pkg/Trastazo/trastazo.sh"
rm -f dist/trastazo-pc.zip
( cd "$OUT/pkg" && zip -q -r ../../../dist/trastazo-pc.zip Trastazo )
ls -la dist/trastazo-pc.zip
