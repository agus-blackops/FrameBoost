#!/bin/sh
# translate the Java sources and compile the pure-C# core with dotnet
set -e
cd "$(dirname "$0")/.."
python3 tools/java2cs.py ../trastazo/src/com/frameboost/ragdoll Assets/Trastazo/Core/Generated
cd build && dotnet build Core.csproj -nologo -v q 2>&1 | grep -E "error|Warn|Error" | sed 's/\[.*//' | sort -u
