# Source Engine Launcher

Android launcher written in Kotlin with the SDL compatibility layer retained in Java.

The launcher uses the official Material Components library with a Material 3 DayNight theme. Its activities, settings, update flow, asset extraction, and theme integration are implemented in Kotlin; the SDL and native compatibility code remains in Java to preserve engine behavior.

## Build

Requirements:

- JDK 17
- Android 7.0 (API 24) or newer
- Android SDK Platform 29
- Android SDK Build Tools 35.0.0 or newer

Build the debug APK with the Gradle Wrapper:

```sh
./gradlew assembleDebug
```

The APK is generated at `build/outputs/apk/debug/srceng-launcher-debug.apk`.

Native engine libraries are optional during launcher-only development. To build a runnable engine package, place each ABI's libraries under `libs/<abi>/`, for example `libs/arm64-v8a/libSDL2.so` and `libs/arm64-v8a/liblauncher.so`.
