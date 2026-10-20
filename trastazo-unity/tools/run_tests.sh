#!/bin/sh
# builds (tools/test_all.sh) and runs the whole headless suite; prints one line per test class
cd "$(dirname "$0")/.."
tools/test_all.sh || exit 1
OUT=${1:-/tmp/trz-out}; mkdir -p "$OUT"
for t in PhysTest PersonTest WorldTest AiTest BuildTest DestroyTest AnimalTest MachineTest Test39 Test40 InputTest; do
  r=$(dotnet /tmp/trz-cs-tests/Tests.dll $t "$OUT" 2>&1)
  echo "$t: $(echo "$r" | grep -c '^PASS') pass, $(echo "$r" | grep -c '^FAIL') fail"
  echo "$r" | grep '^FAIL' | sed 's/^/    /'
done
echo "MonkeyTest: $(dotnet /tmp/trz-cs-tests/Tests.dll MonkeyTest 4 2>&1 | tail -1)"
