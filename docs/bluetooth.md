# HEADWIND Bluetooth control

## Sources

Protocol facts were cross-checked against these community implementations, then
verified with the actual HEADWIND 7AE9 on 3 November 2026. The Android controller
in this repository is an independent implementation; no third-party app source
was incorporated.

- [Wearwind](https://github.com/garanj/wearwind/blob/main/app/src/main/java/com/garan/wearwind/FanControlService.kt): Android/Wear OS control.
- [Tailwind](https://github.com/tuna-f1sh/tailwind/blob/main/src/main.rs): command acknowledgements and cyclic state reports.
- [Wahoo Headwind Controller](https://github.com/vvrnv/wahoo-headwind-controller/blob/master/WahooHeadwindController/HeadwindBLEManager.swift): manual and off commands.
- [Wahoo standby documentation](https://support.wahoofitness.com/hc/en-us/articles/360000780120-Standby-Mode-HEADWIND): Bluetooth remains available with the fan apparently off.
- [Android Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions).

## Discovery

The Pixel showed Wahoo holding an LE GATT connection to HEADWIND 7AE9 without
a normal pairing key. Discovery lists advertised names beginning with HEADWIND
(case-insensitive), including any matching fans already connected to Android.
The user selects a fan, then the app verifies its control service and saves its
name and address after a successful status read. Subsequent reconnection matches
only that address, never another fan with the same name. A changed address can
be selected again through the settings cog. Forget clears both saved fields.
The former address-only preference is retained and its name populated on the
next successful connection. Discovery times out after 12 seconds and can be
restarted manually; reconnect scans retry automatically for the chosen fan.
Do not require a service UUID in advertising: the community
implementations report that the control service is not advertised.

Service: `a026ee0c-0a7d-4ab3-97fa-f1500f9feb8b`

Control characteristic: `a026e038-0a7d-4ab3-97fa-f1500f9feb8b`

Observed properties: read, write without response, notify.

CCCD: `00002902-0000-1000-8000-00805f9b34fb`, notifications enabled with `01 00`.

## Verified packets

All bytes below are hexadecimal; speed is an integer percentage, not an index.

| Action / reply | Bytes |
| --- | --- |
| Enter manual mode | `04 04 00 00` |
| Set 10% | `02 0a 00 00` |
| Set 20% | `02 14 00 00` |
| Stop, leaving BLE available | `04 01 00 00` |
| Acknowledge manual mode | `fe 04 01 04` |
| Acknowledge 20% | `fe 02 01 14` |
| Report 20% in manual mode | `fd 01 14 04` |
| Report stopped | `fd 01 00 01` |
| Reject speed while off | `fe 02 04` |

General status: `fd 01 SPEED MODE`. Community mode values are 1 off, 2 heart
rate, 3 speed, 4 manual, and 5 sensor sleep. Only off/manual are commanded here.
An acknowledgement has `fe COMMAND RESULT VALUE`, with result 1 meaning success;
rejections may omit VALUE. The app waits for both the Android write callback and
the matching fan acknowledgement before sending another command. It then waits
for a matching status notification before marking the requested setting complete.

Reports arrive cyclically (roughly once a second in this test), and can briefly
describe the previous setting. A pending target is kept separate from reported
state. Rapid taps update one target instead of growing a queue; OFF replaces any
unsent speed. Timeouts close the link, clear the target, and retry discovery.

## Off versus sensor standby

The user-facing standby state uses the same stopped mode as Wahoo's Off control:
mode 1. It stops the motor, keeps the fan's Bluetooth radio reachable, and can be
reversed by entering manual mode and setting a speed. This was verified by
disconnecting and reconnecting while stopped. It does not cut mains power.

Mode 5 is a different sensor-related sleep state. We do not use it: this remote
should not accidentally allow a heart-rate or speed sensor to restart the fan.

## Startup detail

On this fan, entering manual mode resets the reported speed to 25%. Trying to
set the speed before manual mode is rejected. The app therefore sends the desired
speed immediately after the manual acknowledgement, without a fixed delay. A
brief firmware-level 25% transition may still occur; the app cannot make these
two protocol commands atomic. User-selected steps are 10/20/40/60/80/100%.

## Validation

- Direct workstation BLE discovery, service inspection, reads and notifications.
- Manual mode, speed commands, rejection handling, off and subsequent wake.
- Pixel USB installation, automatic discovery and control through the real UI.
- Real fan confirmed 10% startup, higher presets through 100%, decreasing and OFF.
- Leaving immediately after + allowed the 10% command to complete; reopening
  read the existing 10% setting without changing it. Force-stop/relaunch after
  OFF reconnected and reported stopped without sending any power command.
- Unit tests for stepping, off semantics, malformed packets and ACK matching.
- UI instrumentation uses fake data so it never controls physical hardware.

Protocol, discovery identity and Compose UI tests cover the controller's inputs
and settings actions. The Pixel 10 Pro (Android 17) was also used to verify
upgrade retention, forgetting, discovery without a saved fan, selection, and
automatic reconnection after process restart. These discovery operations sent
no speed or power commands. Only one physical HEADWIND was available; duplicate
names and different addresses are covered with synthetic test data.

The protocol is unofficial and firmware-specific. No firmware updates, factory
resets, sensor-pairing changes, or normal Bluetooth bonds are performed.
