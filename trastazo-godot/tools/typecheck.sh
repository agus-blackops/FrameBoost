#!/bin/sh
# Builds the C# project against the real GodotSharp API (no Godot editor needed): catches every compile error.
cd "$(dirname "$0")/.."
dotnet build Trastazo.csproj -nologo -v q 2>&1 | grep -E "error|Build succeeded|Error\(s\)" | sed 's/\[.*//' | sort -u
