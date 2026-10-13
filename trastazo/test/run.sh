#!/usr/bin/env bash
# Compiles the game logic (no Android view code) against a Java2D shim and runs a test class: MAIN=<class> run.sh args
set -euo pipefail
cd "$(dirname "$0")"
rm -rf /tmp/trz-test && mkdir -p /tmp/trz-test
CP=$(ls lib/*.jar 2>/dev/null | tr '\n' ':')
TESTS=$(ls *.java | tr '\n' ' ')
javac -nowarn -cp "$CP" -d /tmp/trz-test $(find shim -name '*.java') \
  $(find ../src -name '*.java' ! -name GameView.java ! -name MainActivity.java ! -name GLGameView.java) $TESTS 2>&1 | grep -v "Picked up" || true
EGL_PLATFORM=surfaceless java -Djava.awt.headless=true ${JOPTS:-} -cp "/tmp/trz-test:$CP" com.frameboost.ragdoll.${MAIN:-PhysTest} "$@" 2>&1 | grep -v "Picked up"
