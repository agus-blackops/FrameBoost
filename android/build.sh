#!/usr/bin/env bash
# Builds the FrameBoost IA Android app into android/build/frameboost-ia.apk.
#
# No Android SDK, NDK or Gradle needed: the simulator runs as WebAssembly in
# a WebView, and the APK is assembled from pinned tools on Maven Central
# (aapt2 from apktool, dx, apksig) plus a platform android.jar. Requires a
# JDK (17+), Node 18+, Rust with the wasm32-unknown-unknown target, curl and
# unzip.
#
#   android/build.sh
#
# Signing: uses $FRAMEBOOST_KEYSTORE (PKCS#12) and $FRAMEBOOST_KEYSTORE_PASS
# if set; otherwise creates android/build/frameboost.p12 once and reuses it.
# Keep that file: Android only installs an update signed with the same key.

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$HERE")"
OUT="$HERE/build"
TOOLS="$OUT/tools"

VERSION_NAME="0.1.0"
VERSION_CODE=1
MIN_SDK=26
TARGET_SDK=34

mkdir -p "$TOOLS"

fetch() { # url sha256 dest
  local url=$1 sum=$2 dest=$3
  if [[ ! -f $dest ]] || ! echo "$sum  $dest" | sha256sum -c --status; then
    echo "fetch $(basename "$dest")"
    curl -fsSL "$url" -o "$dest.part"
    echo "$sum  $dest.part" | sha256sum -c --status || { echo "checksum mismatch: $url" >&2; exit 1; }
    mv "$dest.part" "$dest"
  fi
}

MAVEN=https://repo1.maven.org/maven2
fetch "$MAVEN/org/apktool/apktool-lib/3.0.3/apktool-lib-3.0.3.jar" \
  983773879fd89ede2cd938858e3efce2a90ac1123f6a5140e9d949dcf4464e3e "$TOOLS/apktool-lib.jar"
fetch "$MAVEN/com/jakewharton/android/repackaged/dalvik-dx/16.0.1/dalvik-dx-16.0.1.jar" \
  1e4b645628e3bdb097b5331d669e177ef235a551582a8c646dbe36865e541907 "$TOOLS/dx.jar"
fetch "$MAVEN/com/android/tools/build/apksig/2.3.0/apksig-2.3.0.jar" \
  9637078c0016244e4be0941836295365a7e2e5b164c59cb7885783c40460bfee "$TOOLS/apksig.jar"
fetch "https://raw.githubusercontent.com/Sable/android-platforms/master/android-30/android.jar" \
  ffb9f6f7bb313642d8c4abf28566af829405e370ad9151e2258811a514b8bbb6 "$TOOLS/android.jar"

AAPT2="$TOOLS/aapt2"
if [[ ! -x $AAPT2 ]]; then
  unzip -q -o -j "$TOOLS/apktool-lib.jar" prebuilt/linux/aapt2 -d "$TOOLS"
  chmod +x "$AAPT2"
fi

echo "== wasm"
cargo build --manifest-path "$ROOT/Cargo.toml" -p frameboost-wasm \
  --target wasm32-unknown-unknown --release --quiet
WASM="$ROOT/target/wasm32-unknown-unknown/release/frameboost_wasm.wasm"

echo "== web"
rm -rf "$OUT/assets"
(cd "$HERE/web" && npm ci --silent && node build.mjs "$OUT/assets/www" "$WASM")

echo "== resources"
rm -rf "$OUT/res.zip" "$OUT/linked.apk"
"$AAPT2" compile --dir "$HERE/app/res" -o "$OUT/res.zip"
"$AAPT2" link -o "$OUT/linked.apk" \
  -I "$TOOLS/android.jar" \
  --manifest "$HERE/app/AndroidManifest.xml" \
  --min-sdk-version "$MIN_SDK" --target-sdk-version "$TARGET_SDK" \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  -A "$OUT/assets" "$OUT/res.zip"

echo "== code"
rm -rf "$OUT/classes" && mkdir -p "$OUT/classes"
javac -nowarn -Xlint:-options --release 8 -cp "$TOOLS/android.jar" -d "$OUT/classes" \
  $(find "$HERE/app/src" -name '*.java')
java -cp "$TOOLS/dx.jar" com.android.dx.command.Main --dex --min-sdk-version="$MIN_SDK" \
  --output="$OUT/classes.dex" "$OUT/classes"

echo "== sign"
KEYSTORE="${FRAMEBOOST_KEYSTORE:-$OUT/frameboost.p12}"
PASS="${FRAMEBOOST_KEYSTORE_PASS:-frameboost}"
if [[ ! -f $KEYSTORE ]]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 -storepass "$PASS" \
    -alias frameboost -keyalg RSA -keysize 3072 -validity 10000 \
    -dname "CN=FrameBoost IA" 2>/dev/null
  echo "created signing key $KEYSTORE — keep it to install updates over this build"
fi
mkdir -p "$OUT/signer"
javac -nowarn -cp "$TOOLS/apksig.jar" -d "$OUT/signer" "$HERE/tools/SignApk.java"
# apksig 2.3.0 predates the module system and reaches into the JDK's X.509
# internals for v1 signatures.
java --add-exports java.base/sun.security.x509=ALL-UNNAMED \
  --add-exports java.base/sun.security.pkcs=ALL-UNNAMED \
  --add-exports java.base/sun.security.util=ALL-UNNAMED \
  -cp "$TOOLS/apksig.jar:$OUT/signer" SignApk \
  "$OUT/linked.apk" "$OUT/classes.dex" "$KEYSTORE" "$PASS" "$OUT/frameboost-ia.apk" "$MIN_SDK"
