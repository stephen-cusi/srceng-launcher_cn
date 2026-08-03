# Source Engine Launcher

Android launcher written in Kotlin with the SDL compatibility layer retained in Java.

## Build

Requirements:

- JDK 17
- Android SDK Platform 29
- Android SDK Build Tools 35.0.0 or newer

Build the debug APK with the Gradle Wrapper:

```sh
./gradlew assembleDebug
```

The APK is generated at `build/outputs/apk/debug/srceng-launcher-debug.apk`.

Native engine libraries are optional during launcher-only development. To build a runnable engine package, place each ABI's libraries under `libs/<abi>/`, for example `libs/arm64-v8a/libSDL2.so` and `libs/arm64-v8a/liblauncher.so`.
