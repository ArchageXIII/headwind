# Build, review and install

This is the technical companion to the [cyclist's guide](../README.md). Work in
the repository root. Review [SECURITY.md](../SECURITY.md) before executing build
scripts or letting Android Studio sync the project. Keep the phone disconnected
until its owner has reviewed the results and agreed to installation.

## 1. Obtain and inspect the source

Install Git from [git-scm.com](https://git-scm.com/downloads), or use an existing
trusted Git installation. Clone the repository without running its scripts:

```sh
git clone https://github.com/ArchageXIII/headwind.git
cd headwind
git rev-parse HEAD
git status --short
git log --all --format=fuller
git ls-files
```

Record the commit hash. Review tracked files and historical versions for secrets
and personal information, as well as suspicious code. A secret scanner can help
but does not replace reading the code. Do not paste unredacted findings into a
public issue. A source archive omits Git history, so it is insufficient for a
history audit.

## 2. Set up the computer

Use official downloads and compare installer checksums where supplied:

- [Android Studio and its checksums](https://developer.android.com/studio).
- [Microsoft OpenJDK 17 and its checksums](https://learn.microsoft.com/en-us/java/openjdk/download#openjdk-17)
  (another trusted JDK 17 distribution is also suitable).

The tested build uses **JDK 17**, Gradle **8.13**, Android Gradle Plugin **8.13.2**,
and Kotlin **2.2.21**. Dependency versions are in
[`gradle/libs.versions.toml`](../gradle/libs.versions.toml). Use the committed
Gradle wrapper, not a separately installed or automatically upgraded Gradle.
Do not assume Android Studio's bundled Java matches this project's requirements.

In Android Studio's SDK Manager install:

- Android SDK Platform **36** (the phone itself does not have to run Android 16).
- Android SDK Build-Tools **35.0.0**.
- Android SDK Platform-Tools (includes `adb`).
- Android SDK Command-line Tools (includes `sdkmanager` and `apkanalyzer`).
- Optionally, Android Emulator and a supported system image for UI tests.

Read and accept the SDK licences yourself. After the source review, open the
project and set **Settings → Build, Execution, Deployment → Build Tools → Gradle
→ Gradle JDK** to JDK 17. Let Android Studio create `local.properties` with your
SDK location; this file is intentionally ignored by Git.

For terminal builds, set `JAVA_HOME` to the JDK directory and `ANDROID_HOME` to
your SDK directory. Add the JDK's `bin`, SDK's `platform-tools`, and SDK's
`cmdline-tools/latest/bin` directories to the current terminal's PATH. The exact
locations depend on your OS and installation; do not copy someone else's home
directory. Android Studio shows the SDK location in SDK Manager.

On Windows, the optional helper below locates Microsoft JDK 17 under Program
Files and the standard SDK under Local App Data. Review it before running it;
it changes only the current PowerShell session's environment and installs nothing.
For a different JDK layout, configure the variables yourself instead.

```powershell
. ./scripts/dev.ps1
java -version
adb version
```

Check the wrapper **before** running it. Compare the SHA-256 of
`gradle/wrapper/gradle-wrapper.jar` and the `distributionSha256Sum` in
`gradle/wrapper/gradle-wrapper.properties` against the **8.13** entries on
[Gradle's checksum page](https://gradle.org/release-checksums/).
Also review `gradlew` and `gradlew.bat`; a verified JAR does not validate scripts.

```powershell
# Windows PowerShell
Get-FileHash gradle/wrapper/gradle-wrapper.jar -Algorithm SHA256
```

```sh
# macOS; on Linux use sha256sum instead
shasum -a 256 gradle/wrapper/gradle-wrapper.jar
```

The wrapper distribution checksum protects the Gradle download, not every Maven
dependency. See [Gradle's verification explanation](https://docs.gradle.org/current/userguide/dependency_verification.html)
and the limitations in SECURITY.md. Do not disable a failed checksum check.

## 3. Build and run checks without the phone

Windows PowerShell:

```powershell
./gradlew.bat --version
./gradlew.bat assembleDebug lintDebug testDebugUnitTest assembleDebugAndroidTest --console=plain
```

macOS/Linux:

```sh
./gradlew --version
./gradlew assembleDebug lintDebug testDebugUnitTest assembleDebugAndroidTest --console=plain
```

Confirm Gradle uses Java 17 and that the tasks finish successfully. The first
build downloads dependencies. Do not add credentials or random repositories to
fix a failed download. Investigate failures before moving on.

- App APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Lint: `app/build/reports/lint-results-debug.html`.
- Unit tests: `app/build/reports/tests/testDebugUnitTest/index.html`.
- Test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.

`assembleDebugAndroidTest` **compiles** the UI tests; it does not run them. To run
them, start a dedicated emulator through Android Studio's Device Manager, keep
physical phones disconnected, and run `./gradlew connectedDebugAndroidTest`
(`./gradlew.bat` on Windows). This installs test packages on the emulator.
The UI tests use fake data and never control a real fan. Gradle-managed device
tests can remove installed app packages, so use an emulator rather than your
everyday phone for this command. Record separately whether these tests ran.

## 4. Inspect the exact APK you plan to install

[`apkanalyzer`](https://developer.android.com/tools/apkanalyzer) comes with SDK
Command-line Tools. Run:

```sh
apkanalyzer apk summary app/build/outputs/apk/debug/app-debug.apk
apkanalyzer manifest permissions app/build/outputs/apk/debug/app-debug.apk
apkanalyzer manifest print app/build/outputs/apk/debug/app-debug.apk
```

The application ID must be `com.archagexiii.headwind`, minimum SDK 26, target
SDK 36. Check the version against `app/build.gradle.kts`. Expected permissions:

| Permission | Purpose |
| --- | --- |
| `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` | Discover and control the chosen fan on Android 12+. |
| `BLUETOOTH`, `BLUETOOTH_ADMIN` | Legacy Bluetooth on Android 8–11, capped at SDK 30. |
| `ACCESS_FINE_LOCATION` | Legacy BLE scanning on Android 8–11, capped at SDK 30; no GPS tracking code. |
| `com.archagexiii.headwind.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX-generated signature permission protecting internal broadcasts. |

There should be no `INTERNET` permission. Check for unexpected permissions and
components, not just these names. The current debug APK includes the exported
launcher activity, Compose `PreviewActivity` and test `ComponentActivity` from
debug-only tooling, a non-exported AndroidX startup provider, and AndroidX's
`ProfileInstallReceiver` protected by `android.permission.DUMP`. Investigate
unexpected changes. Backup should be disabled.

The debug APK deliberately has `android:debuggable="true"`. It is intended for
personal testing, not as a hardened production release. Turning off USB debugging
limits computer access but does not convert it into a release build.

Verify its signature with [`apksigner`](https://developer.android.com/tools/apksigner)
from SDK Build-Tools. For example, on Windows:

```powershell
& "$env:ANDROID_HOME/build-tools/35.0.0/apksigner.bat" verify --verbose --print-certs app/build/outputs/apk/debug/app-debug.apk
Get-FileHash app/build/outputs/apk/debug/app-debug.apk -Algorithm SHA256
```

On macOS/Linux:

```sh
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs app/build/outputs/apk/debug/app-debug.apk
# macOS; use sha256sum on Linux
shasum -a 256 app/build/outputs/apk/debug/app-debug.apk
```

Record the certificate fingerprint and APK SHA-256 alongside the source commit,
tool versions and test results. A hash identifies the file; a signature checks
its integrity. Neither proves it is benign or matches a claimed source tree.
Local debug certificates differ between computers, and APK hashes can differ
between builds. There is no single universal fingerprint to expect here.

**Pause here:** explain findings, known limitations and the exact APK to the
phone's owner. Obtain their approval before installing or testing hardware.

## 5. Install only on the approved phone

Follow [Android's physical-device setup](https://developer.android.com/studio/run/device).
Enable Developer options (usually by tapping Build number seven times), enable
USB debugging, connect a data cable, unlock the phone, and approve the trusted
computer. On Windows, use Google's or the phone manufacturer's official USB
driver if required. On Linux, follow Android's documented USB/udev setup.

```sh
adb devices -l
```

Identify the owner's phone; its state must be `device`, not `unauthorized`.
Never guess when multiple devices are listed. Replace `YOUR_DEVICE_SERIAL` in
the command below locally; do not share the real serial in public logs.

```sh
adb -s YOUR_DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
```

This installs the app without granting all runtime permissions. Open **Headwind**
on the phone and follow the README's supervised first-use steps. Do not install
the `androidTest` APK for normal use. Keep Play Protect enabled. If Android blocks
installation, investigate the message rather than bypassing security controls.

When finished, unplug the cable, disable USB debugging and, if no longer needed,
revoke USB debugging authorizations. Ordinary app use does not need debugging.

## Updates, removal and troubleshooting

- **Update:** review the new source changes, rebuild, inspect, then use the same
  explicit-device install command. Keep your local signing key private and backed
  up. Android requires compatible signing identity for an in-place update.
- **Signature mismatch:** a CI APK, another computer's debug build, or a future
  release can have a different certificate. Do not automatically uninstall or
  bypass checks. Explain the mismatch; if the owner chooses to uninstall first,
  their saved fan selection will be lost.
- **Remove:** uninstall Headwind through Android Settings. Stop the fan first if
  desired; uninstalling or closing the app does not stop a running fan.
- **No phone shown:** check the cable, unlocked screen, debugging authorization,
  official drivers and `adb devices -l`; do not unlock the bootloader.
- **No fan shown:** check power, Bluetooth, Nearby devices permission, and legacy
  Location requirements. Close competing fan apps, then use Scan again.
- **Build Java error:** check `./gradlew --version` and the configured Gradle JDK.
  Use JDK 17 rather than upgrading build tools blindly.

## Project layout and releases

App code is under `app/src/main/java/com/archagexiii/headwind/`. Protocol and
identity tests are in `app/src/test/`; Compose tests are in `app/src/androidTest/`.
The [Bluetooth notes](bluetooth.md) document protocol sources and real tests.
CI runs build, lint and unit tests, compiles UI tests, and uploads debug artifacts;
it does not run emulator tests or validate physical fan operation.

Release builds enable R8 shrinking and require a private release signing setup
before they can be installed or published. The repository does not provide a
release keystore or a signed production release workflow. A public release needs
its own review, signed APK inspection and device testing. Never commit signing
keys or passwords; retain dependency licence notices when redistributing.
