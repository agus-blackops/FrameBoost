#!/usr/bin/env bash
# Builds the APK without the Android SDK: aapt + javac + dx + zipalign + apksigner
# (all from Ubuntu packages: aapt zipalign apksigner dalvik-exchange libandroid-23-java)
set -euo pipefail
export PATH="$PATH:/usr/lib/android-sdk/build-tools/debian"
cd "$(dirname "$0")"

ANDROID_JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
OUT=build
APP=trastazo
KEYSTORE=tools/debug.keystore

rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/gen"

if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias debug -keyalg RSA -keysize 2048 -validity 36500 \
    -dname "CN=Ragdoll Playground,O=FrameBoost,C=ES" >/dev/null 2>&1
fi

echo "[1/6] aapt: resources + manifest"
aapt package -f -m -J "$OUT/gen" -M AndroidManifest.xml -S res -I "$ANDROID_JAR" -F "$OUT/res.apk"

echo "[2/6] javac"
find src "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -nowarn -source 8 -target 8 -encoding UTF-8 -bootclasspath "$ANDROID_JAR" \
  -d "$OUT/classes" @"$OUT/sources.txt" 2>&1 | grep -v "^warning: \[options\]" | grep -v "^Note:" | grep -v "^[0-9]* warning" || true
test -f "$OUT/classes/com/frameboost/ragdoll/MainActivity.class"

echo "[3/6] dx: classes -> classes.dex"
dx --dex --min-sdk-version=21 --output="$OUT/classes.dex" "$OUT/classes"

echo "[4/6] package"
cp "$OUT/res.apk" "$OUT/unaligned.apk"
( cd "$OUT" && zip -q -j unaligned.apk classes.dex )

echo "[5/6] zipalign"
zipalign -f -p 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"

echo "[6/6] apksigner (v1+v2+v3)"
apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --min-sdk-version 21 --out "$OUT/$APP.apk" "$OUT/aligned.apk"
apksigner verify --verbose "$OUT/$APP.apk" | head -5
ls -la "$OUT/$APP.apk"
