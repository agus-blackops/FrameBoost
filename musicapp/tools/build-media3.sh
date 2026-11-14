#!/usr/bin/env bash
# Builds Media3 (ExoPlayer) 1.4.1 from its source code into libs/media3-bundle.jar, ready for
# build.sh: Media3 + Guava as Java 8 bytecode. build.sh dexes it with dx --min-sdk-version=26, the
# first Android version (8.0) where lambdas and default methods run without a desugaring step.
#
# Why from source: the official artifacts live only on Google's Maven repository. This script needs
# just GitHub (git clone) and Maven Central. The few androidx classes Media3 touches are replaced by
# tiny shims in third_party/media3-shims (annotations, CircularIntArray, ExifInterface).
#
# Needs: git, curl, JDK 11+ (javac, jar), unzip, and build/android-34.jar (build.sh downloads it).
set -euo pipefail
cd "$(dirname "$0")/.."

MEDIA3_TAG=1.4.1
WORK=${WORK:-build/media3}
ANDROID_JAR=${ANDROID_JAR:-build/android-34.jar}
OUT_JAR=libs/media3-bundle.jar
CENTRAL=https://repo1.maven.org/maven2

# group/artifact/version (same versions Media3 1.4.1 declares)
DEPS_RUNTIME="
com/google/guava/guava/33.0.0-android
com/google/guava/failureaccess/1.0.2
"
DEPS_COMPILE="
com/google/code/findbugs/jsr305/3.0.2
com/google/errorprone/error_prone_annotations/2.18.0
org/checkerframework/checker-qual/3.13.0
org/jetbrains/kotlin/kotlin-annotations-jvm/1.9.0
"

[ -f "$ANDROID_JAR" ] || { echo "Missing $ANDROID_JAR (run ./build.sh once)"; exit 1; }
mkdir -p "$WORK/deps" libs

fetch() { # path/of/group/artifact/version -> jar
  local p=$1 a v
  v=${p##*/}; a=${p%/*}; a=${a##*/}
  local f="$WORK/deps/$a-$v.jar"
  [ -s "$f" ] || curl -fsSL -o "$f" "$CENTRAL/$p/$a-$v.jar"
  echo "$f"
}

echo "1/4 Media3 $MEDIA3_TAG source"
MODULES="common exoplayer exoplayer_hls decoder extractor datasource container database"
if [ ! -d "$WORK/src/.git" ] || [ ! -d "$WORK/src/libraries/exoplayer_hls" ]; then
  rm -rf "$WORK/src"
  git clone -q --depth 1 --branch "$MEDIA3_TAG" --filter=blob:none --sparse \
    https://github.com/androidx/media "$WORK/src"
  (cd "$WORK/src" && git sparse-checkout set $(for m in $MODULES; do echo libraries/$m/src/main; done))
fi

echo "2/4 Dependencies (Maven Central)"
RUNTIME_CP=""
for d in $(echo "$DEPS_RUNTIME" | awk "NF{print \$1}"); do RUNTIME_CP="$RUNTIME_CP:$(fetch "$d")"; done
COMPILE_CP="$RUNTIME_CP"
for d in $DEPS_COMPILE; do COMPILE_CP="$COMPILE_CP:$(fetch "$d")"; done

echo "3/4 Compiling Media3 (this takes a minute)"
rm -rf "$WORK/classes"
mkdir -p "$WORK/classes"
{
  for m in $MODULES; do find "$WORK/src/libraries/$m/src/main/java" -name '*.java'; done |
    grep -v '/offline/DownloadNotificationHelper.java$'   # needs androidx.core; Sonora has no downloads UI
  find third_party/media3-shims -name '*.java'
} > "$WORK/sources.txt"
javac -nowarn -Xlint:none --release 8 -encoding UTF-8 -proc:none \
  -cp "$ANDROID_JAR:${COMPILE_CP#:}" -d "$WORK/classes" @"$WORK/sources.txt" 2>&1 |
  grep -v '^warning: \[options\]\|^Note:\|^[0-9]* warning' || true
[ -f "$WORK/classes/androidx/media3/exoplayer/ExoPlayer.class" ] || { echo "Media3 did not compile"; exit 1; }
# compile-only annotations are not shipped
rm -rf "$WORK/classes/androidx/annotation"

echo "4/4 Packaging $OUT_JAR (Media3 + Guava)"
for j in $(echo "${RUNTIME_CP#:}" | tr ':' ' '); do (cd "$WORK/classes" && unzip -qo "$OLDPWD/$j" -x 'META-INF/*'); done
cp "$WORK/src/LICENSE" libs/LICENSE-media3.txt 2>/dev/null || true
rm -f "$OUT_JAR"
(cd "$WORK/classes" && jar cf "$OLDPWD/$OUT_JAR" .)
echo "OK -> $OUT_JAR ($(du -h "$OUT_JAR" | cut -f1))"
