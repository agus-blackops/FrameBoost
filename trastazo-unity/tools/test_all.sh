#!/bin/sh
# translate game + tests, build and run the test suite headless:  tools/test_all.sh [TestName ...]
set -e
cd "$(dirname "$0")/.."
python3 tools/java2cs.py ../trastazo/src/com/frameboost/ragdoll Assets/Trastazo/Core/Generated
python3 tools/java2cs.py --tests ../trastazo/src/com/frameboost/ragdoll ../trastazo/test tests/Generated
cd tests && dotnet build Tests.csproj -nologo -v q -o /tmp/trz-cs-tests 2>&1 | grep -E "error|rror\(s\)" | sed 's/\[.*//' | sort -u
