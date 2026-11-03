# Privacy and safety review

Headwind is a small community project, not a security-certified product. Review
the exact version you intend to build. Passing checks reduce uncertainty; they
do not prove that software, dependencies or hardware are safe.

## Expected behaviour

- The app has no internet permission, account, analytics, advertising, remote
  update service, or telemetry SDK. Build tools do download dependencies on the
  computer; that is separate from the installed app.
- It stores the selected fan's Bluetooth name and address in Android app-private
  preferences. **Forget fan**, clearing app data, or uninstalling removes them.
  Android backup is disabled in the manifest.
- Android 12+ uses Nearby devices permission. Android 8–11 uses legacy Bluetooth
  and fine Location permission because Android requires it for BLE discovery.
  The app does not request GPS coordinates or record location history.
- Connection setup reads fan status; it does not send motor commands. Button
  actions enter manual mode, set speed, or stop the fan. No firmware flashing,
  factory reset, sensor-pairing changes, or Android Bluetooth bonding is used.
- Closing the app releases the connection after allowing an already requested
  command to finish, for up to 2.5 seconds. It does not stop the fan. Failed
  connections discard pending speed changes rather than replaying them later.
- Debug builds log BLE status/command bytes to Android logcat. Android's own
  Bluetooth logs may include device identifiers. Do not publish raw logs or
  phone backups without reviewing and redacting them.

BLE names and addresses identify candidates; they are not cryptographic proof
of a device's identity. The app checks the expected service and status format
before saving a selection, but this does not authenticate the fan against a
malicious impersonator. Only use it with equipment you own or have permission
to control.

## Review before executing or installing

1. Inspect repository code **before Gradle sync/build**: build scripts can execute
   code on the computer. Record `git rev-parse HEAD` and any local modifications.
2. Review `settings.gradle.kts`, both build files, `gradle/libs.versions.toml`,
   the wrapper scripts/JAR/properties, `scripts/dev.ps1`, and CI configuration.
   Repositories should be Google Maven, Maven Central and the Gradle Plugin
   Portal; the Gradle distribution comes from Gradle's official service.
3. Independently compare the wrapper JAR and distribution checksum with
   [Gradle's published checksums](https://gradle.org/release-checksums/).
   Versions are pinned and the distribution has a checksum. Maven dependency
   verification metadata is **not** currently committed, and CI action tags
   are not immutable commit pins. These are remaining supply-chain limitations.
4. Inspect `AndroidManifest.xml` and all app source, especially the Bluetooth
   controller. Look for network clients, native executables, dynamic code
   loading, unnecessary permissions and unexpected exported components.
5. Follow the [build guide](docs/build-and-install.md), then inspect the **built
   APK**, not just the source manifest: dependencies can add manifest entries.
   Check its identity, permissions, signature and SHA-256. A valid signature
   establishes file integrity/signing identity, not trustworthy behaviour.
6. Show the person installing the app the results and limitations before asking
   to install. Use a spare supported phone or emulator first when practical.
   Never silently install test APKs, clear app data, or drive a real fan.

Automated UI tests use synthetic state and do not send Bluetooth commands.
Hardware verification is separate and supervised. An emulator cannot establish
that a particular phone and real fan will work together.

## Before publishing changes

Review tracked files **and all reachable Git history**, including commit author
and committer metadata. Check for credentials, private keys, signing files,
personal emails, home-directory paths, device names/addresses/serials, captured
screenshots, logs and local configuration. Inspect workflow logs/artifacts and
issues separately when preparing a repository for public access. `.gitignore`
does not remove anything already committed, and deleting a file in a new commit
does not remove its earlier versions. Rotate exposed credentials even if history
is subsequently rewritten.

Keep local SDK paths, environment files, signing material, builds, captures and
device preferences out of Git. Share a clean Git clone, not a ZIP of a working
directory containing ignored local files. Release signing keys should remain
private and backed up outside the repository.

## Reporting a concern

For non-sensitive problems, open a GitHub issue with the app version, Android
version and a description of the behaviour. Do not include private identifiers
or credentials. For a vulnerability, use GitHub's **Report a vulnerability**
option if it is enabled. If it is unavailable, open a minimal issue asking for
a private contact channel without including exploit details or sensitive data.
There is no guaranteed response time or formal security support commitment.
