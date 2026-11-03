# Headwind

Dedicated Bluetooth remote for HEADWIND 7AE9, built with Kotlin and Jetpack Compose.

## Use the remote

Open Headwind and allow Nearby devices once. It finds and connects to your fan
automatically, without pairing it in Android's Bluetooth settings. The portrait
screen is three large touch targets:

- **+** starts at 10%, then steps through 20%, 40%, 60%, 80%, and 100%.
- **−** selects the next lower step; below 10% it stops the fan.
- **OFF** stops the fan and leaves it reachable over Bluetooth. Press + to restart
  at 10%, regardless of the previous speed.

The middle button displays the requested percentage while a change is pending,
then the fan's confirmed setting. The screen stays awake while the app is open;
swipe from an edge to reveal Android's navigation controls. Opening or reconnecting
never starts the fan. Leaving the app releases Bluetooth and preserves the fan's
current setting. A lost connection discards pending actions instead of replaying
them later. Reconnection retries automatically while the app is visible.

If the fan is not found, close Wahoo or another controller that may hold its BLE
connection. This app connects only to HEADWIND 7AE9 or its previously verified
address. It needs no internet access or account. On Android 8–11, Android requires
Location permission and Location enabled for BLE scanning; Android 12+ uses
Nearby devices instead.

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

- `app/src/main/java/com/archagexiii/headwind/`: activity, Compose UI, theme, and BLE controller.
- `app/src/main/res/`: strings, launch theme, and adaptive app icon.
- `app/src/test/`: protocol parsing, acknowledgements, and speed-step tests.
- `app/src/androidTest/`: three-button UI test using fake state (does not operate the fan).
- `docs/bluetooth.md`: protocol sources, observed packets, and hardware findings.
- `.github/workflows/android.yml`: build, lint, unit-test task, instrumentation
  test compilation, and debug APK upload for pushes and pull requests.

CI runs the protocol tests and compiles the UI test; run the UI test on a device
with `connectedDebugAndroidTest`. Real fan control is tested separately and
deliberately, so automated tests never unexpectedly operate nearby hardware.
Espresso is pinned to 3.7.0 for Android 17's input API compatibility;
this dependency is included only in the test APK, not the app.

## Release builds

Release builds enable R8 shrinking. Configure a private release keystore before
publishing; signing keys, credentials, local SDK paths, and build output are
excluded from Git. Confirm the final application ID before the first store release.
