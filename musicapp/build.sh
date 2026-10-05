#!/usr/bin/env bash
# Builds Sonora into build/Sonora.apk without Gradle or the Android SDK manager.
# Needs: JDK (javac), aapt, dalvik-exchange (dx), zipalign, apksigner
#   (Debian/Ubuntu: apt install aapt dalvik-exchange zipalign apksigner)
# and an android.jar for API 34 (downloaded on first run).
set -euo pipefail
cd "$(dirname "$0")"

OUT=build
ANDROID_JAR=${ANDROID_JAR:-$OUT/android-34.jar}
KEYSTORE=${KEYSTORE:-sonora-release.jks}

mkdir -p "$OUT"
if [ ! -f "$ANDROID_JAR" ]; then
  echo "Downloading android.jar (API 34)…"
  curl -fsSL -o "$ANDROID_JAR" \
    https://raw.githubusercontent.com/Sable/android-platforms/master/android-34/android.jar
fi

rm -rf "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT"/*.apk
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "1/5 Compiling resources"
aapt package -f -m -J "$OUT/gen" -M AndroidManifest.xml -S res -I "$ANDROID_JAR" \
  --min-sdk-version 21 --target-sdk-version 34 -F "$OUT/unsigned.apk"

echo "2/5 Compiling Java"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$ANDROID_JAR" -d "$OUT/classes" \
  $(find src "$OUT/gen" -name '*.java')

echo "3/5 Dexing"
dalvik-exchange --dex --min-sdk-version=21 --output="$OUT/dex/classes.dex" "$OUT/classes"

echo "4/5 Packaging"
(cd "$OUT/dex" && zip -q -j ../unsigned.apk classes.dex)
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "5/5 Signing"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -alias sonora -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass sonora123 -keypass sonora123 \
    -dname "CN=Sonora, O=Sonora, C=AR" >/dev/null 2>&1
fi
apksigner sign --ks "$KEYSTORE" --ks-key-alias sonora --ks-pass pass:sonora123 \
  --key-pass pass:sonora123 --min-sdk-version 21 --out "$OUT/Sonora.apk" "$OUT/aligned.apk"
apksigner verify "$OUT/Sonora.apk"
rm -f "$OUT/unsigned.apk" "$OUT/aligned.apk" "$OUT/Sonora.apk.idsig"
echo "OK -> $OUT/Sonora.apk ($(du -h "$OUT/Sonora.apk" | cut -f1))"
