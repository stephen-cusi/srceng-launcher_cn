#!/bin/bash
# Manual APK build script for srceng-android (no ant required)
set -e
set -o pipefail

ANDROID_HOME=${ANDROID_HOME:-/usr/lib/android-sdk}
PLATFORM=${PLATFORM:-$ANDROID_HOME/platforms/android-29}
BUILD_TOOLS=${BUILD_TOOLS:-$ANDROID_HOME/build-tools/29.0.3}
ANDROID_JAR=${ANDROID_JAR:-$PLATFORM/android.jar}
AAPT=${AAPT:-/usr/bin/aapt}
DX=${DX:-$BUILD_TOOLS/dx}
ZIPALIGN=${ZIPALIGN:-$BUILD_TOOLS/zipalign}
APKSIGNER=${APKSIGNER:-$BUILD_TOOLS/apksigner}
if [ -z "${JAVA_HOME:-}" ]; then
    JAVAC=$(command -v javac || true)
    [ -n "$JAVAC" ] && JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$JAVAC")")")
fi
NAME=srceng

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
cd "$SCRIPT_DIR"

for TOOL in "$AAPT" "$DX" "$ZIPALIGN" "$JAVA_HOME/bin/javac"; do
    if [ ! -x "$TOOL" ]; then
        echo "Missing build tool: $TOOL" >&2
        exit 1
    fi
done
if [ ! -f "$ANDROID_JAR" ]; then
    echo "Missing Android platform jar: $ANDROID_JAR" >&2
    exit 1
fi
HAS_NATIVE_LIBS=false
for LIB_DIR in libs/*; do
    if [ -f "$LIB_DIR/libSDL2.so" ] && [ -f "$LIB_DIR/liblauncher.so" ]; then
        HAS_NATIVE_LIBS=true
        break
    fi
done
if [ "$HAS_NATIVE_LIBS" != true ]; then
    echo "Missing required native libraries under libs/<abi>/" >&2
    exit 1
fi

echo "=== Clean ==="
rm -rf gen bin
mkdir -p gen bin/classes bin

echo "=== Step 1: aapt generate R.java ==="
$AAPT package -m -J gen/ -M AndroidManifest.xml -S res -A assets -I $ANDROID_JAR --auto-add-overlay

echo "=== Step 2: collect all source files ==="
# Find all .java under src/
find src -name "*.java" > bin/sources.list
# Also include generated R.java
find gen -name "*.java" >> bin/sources.list
echo "Total sources: $(wc -l < bin/sources.list)"

echo "=== Step 3: javac compile ==="
# Use Java 1.8 target for dx compatibility
$JAVA_HOME/bin/javac -Xlint:deprecation -source 1.8 -target 1.8 \
    -encoding UTF-8 \
    -bootclasspath $ANDROID_JAR \
    -classpath $ANDROID_JAR \
    -d bin/classes \
    @bin/sources.list 2>&1 | tail -30
echo "Compile done, class count: $(find bin/classes -name "*.class" | wc -l)"

echo "=== Step 4: dx -> classes.dex ==="
$DX --dex --output=bin/classes.dex bin/classes/ 2>&1 | tail -5
ls -la bin/classes.dex

echo "=== Step 5: aapt package resources + assets into unsigned unaligned apk ==="
$AAPT package -f \
    --debug-mode \
    -M AndroidManifest.xml \
    -S res \
    -A assets \
    -I $ANDROID_JAR \
    -F bin/$NAME.apk.unaligned \
    --auto-add-overlay

echo "=== Step 6: add classes.dex to apk (zip into root) ==="
# Include libs/ if present (with ABI subdirs like armeabi-v7a/*.so)
cd bin
if [ -d ../libs ]; then
    # Build lib/ structure in apk
    mkdir -p lib
    cp -r ../libs/* lib/ 2>/dev/null || true
    zip -r -q $NAME.apk.unaligned classes.dex lib
else
    zip -q $NAME.apk.unaligned classes.dex
fi
cd ..

echo "=== Step 7: zipalign ==="
$ZIPALIGN -f 4 bin/$NAME.apk.unaligned bin/$NAME.apk.aligned

echo "=== Step 8: sign with debug keystore ==="
if [ -f debug.keystore ]; then
    echo "Using project debug.keystore"
    KS=debug.keystore
    KS_PASS=android
    KS_ALIAS=androiddebugkey
else
    KS="$HOME/.android/debug.keystore"
    KS_PASS=android
    KS_ALIAS=androiddebugkey
    mkdir -p "$HOME/.android"
    if [ ! -f "$KS" ]; then
        echo "Generating debug keystore..."
        keytool -genkey -v -keystore "$KS" -storepass "$KS_PASS" -keypass "$KS_PASS" \
            -alias "$KS_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
            -dname "CN=Android Debug,O=Android,C=US" < /dev/null || true
    fi
fi

# Use apksigner if available, otherwise fallback to jarsigner
if [ -f $APKSIGNER ]; then
    $APKSIGNER sign --ks "$KS" --ks-pass pass:$KS_PASS \
        --ks-key-alias "$KS_ALIAS" --key-pass pass:$KS_PASS \
        --out bin/$NAME-debug.apk bin/$NAME.apk.aligned 2>&1 | tail -5
else
    cp bin/$NAME.apk.aligned bin/$NAME-debug.apk
    jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 \
        -keystore "$KS" -storepass "$KS_PASS" -keypass "$KS_PASS" \
        bin/$NAME-debug.apk "$KS_ALIAS" 2>&1 | tail -5
    $ZIPALIGN -f 4 bin/$NAME-debug.apk bin/$NAME-debug.apk.tmp
    mv bin/$NAME-debug.apk.tmp bin/$NAME-debug.apk
fi

echo ""
echo "=== BUILD SUCCESS ==="
ls -lh bin/$NAME-debug.apk
echo "APK path: $SCRIPT_DIR/bin/$NAME-debug.apk"
