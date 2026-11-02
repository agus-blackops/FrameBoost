#!/bin/sh
# Copies the platform-independent game (Core) from the Unity project, where the Java -> C# translator writes it.
# Run it after tools/java2cs.py has been run (trastazo-unity/tools/test_all.sh does that) to bring Godot up to date.
set -e
cd "$(dirname "$0")/.."
SRC=../trastazo-unity/Assets/Trastazo/Core
rm -rf Core
mkdir -p Core
cp -r "$SRC"/* Core/
rm -f Core/*.asmdef Core/*.meta
find Core -name '*.meta' -delete
echo "Core synced from $SRC"
