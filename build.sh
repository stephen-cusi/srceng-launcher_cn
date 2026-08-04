#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MODE=${1:-release}
if [ "$#" -gt 0 ]; then
    shift
fi

export JAVA_HOME=/home/arale/.local/jdk-17
export ANDROID_HOME=/home/arale/.local/android-build-toolchain/usr/lib/android-sdk
export LD_LIBRARY_PATH=/home/arale/.local/android-build-toolchain/usr/lib:/home/arale/.local/android-build-toolchain/usr/lib/aarch64-linux-gnu:/home/arale/.local/android-build-toolchain/usr/lib/aarch64-linux-gnu/android
AAPT2=/home/arale/.local/android-build-toolchain/usr/lib/android-sdk/build-tools/debian/aapt2
APKSIGNER=/home/arale/.local/android-build-toolchain/usr/lib/android-sdk/build-tools/35.0.0/apksigner

case "$MODE" in
    debug)
        TASKS="assembleDebug lintDebug"
        APK="$ROOT/build/outputs/apk/debug/srceng-launcher-debug.apk"
        EXPECTED_DN="CN=Android Debug"
        ;;
    release)
        TASKS="assembleRelease lintRelease"
        APK="$ROOT/build/outputs/apk/release/srceng-launcher-release.apk"
        EXPECTED_DN="CN=Source Engine Launcher"
        ;;
    *)
        printf 'Usage: %s [debug|release] [Gradle options...]\n' "$0" >&2
        exit 2
        ;;
esac

# shellcheck disable=SC2086
"$ROOT/gradlew" $TASKS \
    -Pandroid.aapt2FromMavenOverride="$AAPT2" "$@"

CERTS=$(PATH="$JAVA_HOME/bin:$PATH" "$APKSIGNER" verify --verbose --print-certs "$APK")
printf '%s\n' "$CERTS"
printf '%s\n' "$CERTS" | grep -F "Signer #1 certificate DN: $EXPECTED_DN" >/dev/null || {
    printf 'Unexpected %s APK signer; expected %s\n' "$MODE" "$EXPECTED_DN" >&2
    exit 1
}

if [ "$MODE" = release ]; then
    printf '%s\n' "$CERTS" | grep -F "Signer #1 certificate SHA-256 digest: 66f644d323384660db2ded5f46f51b8a1a2cec5a2262e26a7fafc3bc3a71eac5" >/dev/null || {
        printf 'Release certificate fingerprint mismatch\n' >&2
        exit 1
    }
    cp "$APK" /home/arale/srceng-launcher-release.apk
fi
