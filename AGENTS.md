# Repository Notes

## Build And Verification

- This is one Android application module with nonstandard root source sets: `AndroidManifest.xml`, `src/`, `res/`, `assets/`, and `lib/` are wired explicitly in `build.gradle.kts`; do not assume `app/src/main` exists.
- Use the Gradle wrapper, not the obsolete Ant/NDK scripts under `scripts/`: `./gradlew assembleDebug lintDebug`. There are currently no automated tests.
- The APK is `build/outputs/apk/debug/srceng-launcher-debug.apk`. For a release candidate, use `./gradlew clean assembleDebug lintDebug` and inspect the APK's ABI entries and signature, not just Gradle success.
- On this ARM64 workspace, Gradle needs JDK 17, the local SDK, the Debian `aapt2` override, and all three native-library paths:

```sh
LD_LIBRARY_PATH=/home/arale/.local/android-build-toolchain/usr/lib:/home/arale/.local/android-build-toolchain/usr/lib/aarch64-linux-gnu:/home/arale/.local/android-build-toolchain/usr/lib/aarch64-linux-gnu/android \
JAVA_HOME=/home/arale/.local/jdk-17 \
ANDROID_HOME=/tmp/opencode/android-sdk-gradle \
./gradlew clean assembleDebug lintDebug \
-Pandroid.aapt2FromMavenOverride=/home/arale/.local/android-build-toolchain/usr/lib/android-sdk/build-tools/debian/aapt2
```

## Runtime Boundaries

- Launcher/settings/update/theme code is Kotlin under `src/me/nillerusr/`. Keep SDL, audio/controller, and native compatibility code in Java (`src/org/libsdl/` and `src/com/valvesoftware/`) unless the task explicitly requires changing that boundary.
- `LauncherActivity` starts `SDLActivity`; `SDLActivity` loads `libSDL2.so` and `liblauncher.so`, then `ValveActivity2` validates game data and sets native environment variables.
- `APP_LIB_PATH` must remain `ApplicationInfo.nativeLibraryDir`. Engine modules, including `libfilesystem_stdio.so`, are APK libraries, not files loaded from the external game-data directory.

## Native Libraries

- A launcher-only APK can compile without `.so` files but cannot run the engine. Runnable APKs require complete matching sets in `lib/arm64-v8a/` and `lib/armeabi-v7a/`, including `libSDL2.so`, `liblauncher.so`, and `libfilesystem_stdio.so`.
- Keep `jniLibs.useLegacyPackaging = true`: the engine expects Android to extract compressed libraries into `nativeLibraryDir`. Removing it can leave no filesystem path for `APP_LIB_PATH` and makes the APK roughly the uncompressed library size.
- After native-library changes, verify both ABIs are reported by `aapt dump badging` and that the APK contains `lib/<abi>/libSDL2.so`, `liblauncher.so`, and `libfilesystem_stdio.so`.

## Versions And Publishing

- An update release has two independent values: `versionName` in `build.gradle.kts` and monotonic `update_build` in `res/values/build_info.xml`. Update both before building; update availability requires both a different version name and a greater feed build.
- The feed lives in the sibling repo `/home/arale/srceng-launcher-updates`. Publish only a verified APK with `./publish.sh <stable|dev> <version-name> <build> <apk> <changelog.md>`, then commit the generated APK, changelog, and manifest together.
- Automatic update checks intentionally try GitHub Raw before CDN mirrors. Branch-based jsDelivr manifest URLs have served stale builds; preserve cache busting and GitHub-first ordering in `UpdateSystem.kt`.
