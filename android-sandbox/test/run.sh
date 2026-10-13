#!/usr/bin/env bash
# Compiles the game logic (no Android view code) against a Java2D shim and runs the desktop harness.
set -euo pipefail
cd "$(dirname "$0")"
rm -rf /tmp/rpg-test && mkdir -p /tmp/rpg-test
CP=$(ls lib/*.jar 2>/dev/null | tr '\n' ':')
javac -nowarn -cp "$CP" -d /tmp/rpg-test $(find shim -name '*.java') \
  $(find ../src -name '*.java' ! -name GameView.java ! -name MainActivity.java ! -name GLGameView.java) Harness.java InputTest.java Start.java NewTest.java BrainTest.java WalkTest.java MusicTest.java FlowTest.java CrashTest.java MonkeyTest.java SmallTest.java CacheTest.java BuildTest.java IconTest.java GLTest.java GLStress.java PPGTest.java 2>&1 | grep -v "Picked up" || true
EGL_PLATFORM=surfaceless java -Djava.awt.headless=true -cp "/tmp/rpg-test:$CP" com.frameboost.ragdoll.${MAIN:-Harness} "$@" 2>&1 | grep -v "Picked up"
