# Source Engine Launcher

Android launcher written in Kotlin with the SDL compatibility layer retained in Java.

The launcher uses the official Material Components library with a Material 3 DayNight theme. Its activities, settings, update flow, asset extraction, and theme integration are implemented in Kotlin; the SDL and native compatibility code remains in Java to preserve engine behavior.

## Requirements

- JDK 17
- Android 7.0 (API 24) or newer
- Android SDK Platform 34
- Android SDK Build Tools 35.0.0 or newer

## Debug build

Build the debug APK with the Gradle Wrapper:

```sh
./gradlew assembleDebug
```

Output:

```text
build/outputs/apk/debug/srceng-launcher-debug.apk
```

The Debug build uses the repository's `debug.keystore` and the standard Android debug certificate. It is intended for local development and testing only.

## Release build

Release builds require local signing material that is intentionally excluded from Git:

```text
release.keystore
release-signing.properties
```

`release-signing.properties` uses the following format:

```properties
storeFile=release.keystore
storePassword=<keystore password>
keyAlias=<key alias>
keyPassword=<key password>
```

Build the signed Release APK with:

```sh
./gradlew assembleRelease
```

Output:

```text
build/outputs/apk/release/srceng-launcher-release.apk
```

The build fails if the local Release signing configuration is missing. It does not fall back to the Android debug certificate.

Never commit or distribute the Release keystore or its credentials. Keep secure backups: losing the signing key prevents compatible updates, while exposing it allows unauthorized APKs to be signed as official updates. Existing installations can only be updated by APKs signed with the same certificate.

## Workspace build helper

The repository includes a workspace-specific helper that builds, runs lint, and verifies the APK signer:

```sh
./build.sh debug
./build.sh release
```

Running `./build.sh` without an argument defaults to Release. The helper contains paths for the current ARM64/WSL workspace; on another machine, use the Gradle Wrapper directly or adapt those toolchain paths.

For a release candidate, run a clean build and lint before distribution:

```sh
./gradlew clean assembleRelease lintRelease
```

Verify the resulting certificate with `apksigner verify --verbose --print-certs`. A successful signature-integrity check alone is not enough—also confirm that the signer is the expected Release certificate.

## Native libraries

Native engine libraries are required for a runnable engine package. Place each ABI's libraries under `lib/<abi>/`, for example `lib/arm64-v8a/libSDL2.so` and `lib/arm64-v8a/liblauncher.so`.

Runnable packages should include matching `arm64-v8a` and `armeabi-v7a` sets, including at least:

- `libSDL2.so`
- `liblauncher.so`
- `libfilesystem_stdio.so`

Keep legacy JNI library packaging enabled because the engine loads libraries from the application's extracted `nativeLibraryDir`.
