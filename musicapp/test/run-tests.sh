#!/usr/bin/env bash
# Runs Sonora's tests on the JVM with Robolectric (no emulator, no Gradle).
#
#   ./test/run-tests.sh                 # every test class
#   ./test/run-tests.sh SmokeTest       # one class
#
# Needs: JDK 17+, Maven (mvn), git, aapt, and a successful ./build.sh (it compiles the app into
# build/classes). The first run downloads Robolectric from Maven Central and compiles the small
# androidx.test "monitor" library from its GitHub sources (it is not on Maven Central).
set -euo pipefail
cd "$(dirname "$0")/.."
M=$PWD
H=$M/build/test-harness
MVN=${MVN:-$(command -v mvn || echo /opt/maven/bin/mvn)}
ANDROID_JAR=$M/build/android-34.jar
[ -d build/classes/com/agus/sonora ] || { echo "Run ./build.sh first"; exit 1; }
mkdir -p "$H"

# 1. Robolectric and JUnit from Maven Central (through Google's mirror of it: big jars on the main
#    site redirect to a host some networks block), without androidx.test (Google Maven only)
if [ ! -s "$H/cp.txt" ]; then
  cat > "$H/pom.xml" <<'E'
<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
<groupId>sonora</groupId><artifactId>harness</artifactId><version>1</version>
<dependencies>
<dependency><groupId>org.robolectric</groupId><artifactId>robolectric</artifactId><version>4.14.1</version>
<exclusions><exclusion><groupId>androidx.test</groupId><artifactId>*</artifactId></exclusion>
<exclusion><groupId>androidx.test.espresso</groupId><artifactId>*</artifactId></exclusion></exclusions></dependency>
<dependency><groupId>junit</groupId><artifactId>junit</artifactId><version>4.13.2</version></dependency>
</dependencies></project>
E
  cat > "$H/settings.xml" <<'X'
<settings><mirrors><mirror><id>google-central-mirror</id><mirrorOf>central</mirrorOf>
<url>https://maven-central.storage-download.googleapis.com/maven2</url></mirror></mirrors></settings>
X
  (cd "$H" && "$MVN" -q -s settings.xml -Dmaven.repo.local="$H/m2" dependency:build-classpath -Dmdep.outputFile=cp.txt)
fi
CP=$(cat "$H/cp.txt")
# the Android system images Robolectric uses (14 runs the tests, 15 loads the app's resources), fetched
# the same way so that Robolectric can run offline
for V in 14-robolectric-10818077-i7 15-robolectric-12650502-i7; do
  [ -s "$H/android-all/android-all-instrumented-$V.jar" ] ||
    (cd "$H" && "$MVN" -q -s settings.xml -Dmaven.repo.local="$H/m2" dependency:copy \
      -Dartifact=org.robolectric:android-all-instrumented:$V -DoutputDirectory="$H/android-all")
done

# 2. androidx.test monitor + idling resources, compiled from source with tiny stubs
if [ ! -d "$H/atc/androidx/test/platform" ]; then
  [ -d "$H/at" ] || git clone -q --depth 1 --branch axt_08_14_2024 --filter=blob:none --sparse https://github.com/android/android-test.git "$H/at"  # monitor 1.7.2, the version Robolectric 4.14 expects
  (cd "$H/at" && git sparse-checkout set runner/monitor/java espresso/idling_resource/java)
  mkdir -p "$H/stubs/androidx/annotation" "$H/stubs/androidx/tracing"
  cat > "$H/stubs/androidx/annotation/RestrictTo.java" <<'E'
package androidx.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS) public @interface RestrictTo { Scope[] value(); enum Scope { LIBRARY, LIBRARY_GROUP, LIBRARY_GROUP_PREFIX, GROUP_ID, TESTS, SUBCLASSES } }
E
  for a in NonNull Nullable; do echo "package androidx.annotation; public @interface $a {}" > "$H/stubs/androidx/annotation/$a.java"; done
  echo "package androidx.annotation; public @interface VisibleForTesting { int otherwise() default 2; }" > "$H/stubs/androidx/annotation/VisibleForTesting.java"
  cat > "$H/stubs/androidx/tracing/Trace.java" <<'E'
package androidx.tracing;
public final class Trace { public static void beginSection(String s){} public static void endSection(){} public static boolean isEnabled(){return false;} public static void beginAsyncSection(String s,int c){} public static void endAsyncSection(String s,int c){} }
E
  sed -i 's/public static boolean isEnabled(){return false;}/public static boolean isEnabled(){return false;} public static void forceEnableAppTracing(){}/' "$H/stubs/androidx/tracing/Trace.java"
  # the two Kotlin classes of the monitor, in Java
  mkdir -p "$H/stubs/androidx/test/platform/app" "$H/stubs/androidx/test/platform/io"
  cat > "$H/stubs/androidx/test/platform/app/AppComponentFactoryRegistry.java" <<'X'
package androidx.test.platform.app;
import android.app.Activity;
import android.app.AppComponentFactory;
import android.app.Application;
import android.content.Intent;
import android.os.Build;
public final class AppComponentFactoryRegistry {
  private static volatile AppComponentFactory factory;
  public static AppComponentFactory getAppComponentFactory() { return factory; }
  public static void setAppComponentFactory(AppComponentFactory f) { factory = f; }
  // Kotlin has no checked exceptions: rethrow them unchanged, like the original
  @SuppressWarnings("unchecked")
  private static <T extends Throwable> RuntimeException sneaky(Throwable t) throws T { throw (T) t; }
  public static Application instantiateApplication(ClassLoader cl, String className) {
    try { return Build.VERSION.SDK_INT >= 28 && factory != null ? factory.instantiateApplication(cl, className) : null; }
    catch (Exception e) { throw AppComponentFactoryRegistry.<RuntimeException>sneaky(e); }
  }
  public static Activity instantiateActivity(ClassLoader cl, String className, Intent intent) {
    try { return Build.VERSION.SDK_INT >= 28 && factory != null ? factory.instantiateActivity(cl, className, intent) : null; }
    catch (Exception e) { throw AppComponentFactoryRegistry.<RuntimeException>sneaky(e); }
  }
  public static Activity instantiateActivity(ClassLoader cl, String className) {
    return instantiateActivity(cl, className, null);
  }
}
X
  cat > "$H/stubs/androidx/test/platform/io/TestDirCalculator.java" <<'X'
package androidx.test.platform.io;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
public class TestDirCalculator {
  private File root() {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    return c.getExternalCacheDir() != null ? c.getExternalCacheDir() : c.getCacheDir();
  }
  public File getOutputDir() {
    String d = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir");
    return d != null ? new File(d) : new File(root(), "additionalTestOutputDir");
  }
  public File getInputDir() {
    String d = InstrumentationRegistry.getArguments().getString("testInputDir");
    return d != null ? new File(d) : new File(root(), "testInputDir");
  }
}
X
  mkdir -p "$H/atc"
  # internal/runner/hidden exposes hidden Instrumentation methods to the compiler; the class that
  # ships is the one in internal/runner/runtime, compiled afterwards over it
  javac -nowarn -d "$H/atc" -cp "$CP:$ANDROID_JAR" $(find "$H/stubs" "$H/at" -name '*.java' -not -path '*/internal/runner/runtime/*') 2>&1 | grep error || true
  javac -nowarn -d "$H/atc" -cp "$H/atc:$CP:$ANDROID_JAR" "$H/at/runner/monitor/java/androidx/test/internal/runner/runtime/ExposedInstrumentationApi.java"
fi

# 3. App resources for Robolectric
aapt package -f -M AndroidManifest.xml -S res -I "$ANDROID_JAR" --min-sdk-version 26 --target-sdk-version 34 -F "$H/res.apk"
mkdir -p "$H/res-cp/com/android/tools"
cat > "$H/res-cp/com/android/tools/test_config.properties" <<E
android_merged_manifest=$M/AndroidManifest.xml
android_resource_apk=$H/res.apk
android_custom_package=com.agus.sonora
E

# 4. Compile and run the tests
LIBS=$( (ls libs/*.jar 2>/dev/null || true) | tr "\n" ":")
FULL="$CP:$M/build/classes:$LIBS$H/res-cp:$H/atc:$ANDROID_JAR"
rm -rf "$H/tc" && mkdir -p "$H/tc"
javac -nowarn -encoding UTF-8 -d "$H/tc" -cp "$FULL" test/*.java
CLASSES=()
if [ $# -gt 0 ]; then for c in "$@"; do CLASSES+=("com.agus.sonora.$c"); done
else for f in test/*Test.java; do c=$(basename "$f" .java); CLASSES+=("com.agus.sonora.$c"); done; fi
java -Drobolectric.enabledSdks=34 -Drobolectric.offline=true -Drobolectric.dependency.dir="$H/android-all" -cp "$H/tc:$FULL" org.junit.runner.JUnitCore "${CLASSES[@]}" 2>&1 |
  grep -v 'JAVA_TOOL_OPTIONS\|^\s*at org.junit\|^\s*at java.base\|^\s*at jdk\.\|^\s*at org.robolectric\|^\s*at com.google'
