#!/usr/bin/env bash
# Compila el APK sin Android Studio ni Gradle.
# Requiere (Ubuntu/Debian): aapt apksigner zipalign dalvik-exchange libandroid-23-java default-jdk
set -euo pipefail
cd "$(dirname "$0")"
OUT=build; rm -rf $OUT; mkdir -p $OUT/classes $OUT/assets ../dist
cp ../index.html ../style.css ../catalog.js ../engine.js ../app.js $OUT/assets/
ANDROID_JAR=/usr/share/java/com.android.android-23.jar
aapt package -f -M AndroidManifest.xml -S res -A $OUT/assets -I $ANDROID_JAR -F $OUT/unsigned.apk
javac -nowarn --release 8 -cp $ANDROID_JAR -d $OUT/classes src/cl/frameboost/armadortorre/*.java 2>&1 | grep -v "^warning\|^Note" || true
java -cp /usr/share/java/com.android.dx.jar com.android.dx.command.Main --dex --output=$OUT/classes.dex $OUT/classes
(cd $OUT && aapt add -f unsigned.apk classes.dex >/dev/null)
zipalign -f 4 $OUT/unsigned.apk $OUT/aligned.apk
KS=${KEYSTORE:-$HOME/.android-armador.keystore}
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias key \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Armador de Torre" 2>/dev/null
apksigner sign --ks "$KS" --ks-pass pass:android --key-pass pass:android --out ../dist/ArmadorDeTorre.apk $OUT/aligned.apk
apksigner verify -v ../dist/ArmadorDeTorre.apk | head -5
ls -la ../dist/ArmadorDeTorre.apk
