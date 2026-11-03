# Headwind

Native Android app built with Kotlin, Jetpack Compose, and Material 3.

## Project

- Application ID: `com.archagexiii.headwind`
- Minimum Android version: Android 8.0 (API 26)
- Compile / target SDK: Android 16 (API 36)
- Java bytecode: 17; use JDK 17 or 21 to run Gradle
- Android Gradle Plugin 8.13.2, Gradle 8.13, Kotlin 2.2.21
- Dependencies are pinned in `gradle/libs.versions.toml`.

## Start developing

1. Install Android Studio and open this repository as a project.
2. In SDK Manager, install Android SDK Platform 36, Build Tools 35.0.0,
   Platform Tools, and the Android Emulator.
3. Install JDK 17 and select it as the Gradle JDK in Android Studio's
   Settings > Build, Execution, Deployment > Build Tools > Gradle. The current
   Android Studio bundles JDK 25, which this Gradle version does not support.
4. Let Gradle sync. Android Studio creates your untracked `local.properties`.
5. Select an emulator in Device Manager or connect a phone with USB debugging,
   then run the `app` configuration.

On this Windows workstation, `scripts/dev.ps1` configures Java and the SDK for the
current PowerShell session using Microsoft OpenJDK 17 and the standard SDK path:

```powershell
. ./scripts/dev.ps1
./gradlew.bat assembleDebug
./gradlew.bat lintDebug testDebugUnitTest assembleDebugAndroidTest
./gradlew.bat connectedDebugAndroidTest # requires a running emulator or phone
./gradlew.bat installDebug             # installs on the connected device
```

This workstation also has a Pixel 7 emulator named `Headwind_API_36`. Start it
from Android Studio's Device Manager or run `emulator -avd Headwind_API_36` after
loading `scripts/dev.ps1`. If both an emulator and a phone are connected, set
`$env:ANDROID_SERIAL = 'emulator-5554'` (using the ID from `adb devices`) to select
the emulator for command-line installs and device tests.

On macOS/Linux use `./gradlew` with the same tasks, and configure `JAVA_HOME` and
`ANDROID_HOME` for your installation. The Gradle wrapper is committed; no global
Gradle installation is needed.

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Layout

- `app/src/main/java/com/archagexiii/headwind/`: activity, Compose UI, and theme.
- `app/src/main/res/`: strings, launch theme, and adaptive app icon.
- `app/src/androidTest/`: device-based app launch smoke test.
- `.github/workflows/android.yml`: build, lint, unit-test task, instrumentation
  test compilation, and debug APK upload for pushes and pull requests.

The initial screen is a small welcome screen with light and dark themes. Product
features will be added as requirements are defined. There are no business-logic
unit tests yet; the instrumentation smoke test verifies the actual launcher UI.
CI compiles that test; run it on a device with `connectedDebugAndroidTest`.

## Release builds

Release builds enable R8 shrinking. Configure a private release keystore before
publishing; signing keys, credentials, local SDK paths, and build output are
excluded from Git. Confirm the final application ID before the first store release.
