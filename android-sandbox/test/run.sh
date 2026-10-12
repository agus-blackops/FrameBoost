#!/usr/bin/env bash
# Compiles the game logic (no Android view code) against a Java2D shim and runs the desktop harness.
set -euo pipefail
cd "$(dirname "$0")"
rm -rf /tmp/rpg-test && mkdir -p /tmp/rpg-test
javac -nowarn -d /tmp/rpg-test $(find shim -name '*.java') \
  $(find ../src -name '*.java' ! -name GameView.java ! -name MainActivity.java) Harness.java InputTest.java Start.java NewTest.java 2>&1 | grep -v "Picked up" || true
java -Djava.awt.headless=true -cp /tmp/rpg-test com.frameboost.ragdoll.${MAIN:-Harness} "$@" 2>&1 | grep -v "Picked up"
