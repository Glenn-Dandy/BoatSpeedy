# BoatSpeedy

A simple **GPS speedometer for Android**, built for **speed control on a boat** in
the low range (~5–10 km/h). Large, easy-to-read digital readout, trip tracking and
a clean light/dark design.

[![Build APK](https://github.com/Glenn-Dandy/BoatSpeedy/actions/workflows/build.yml/badge.svg)](https://github.com/Glenn-Dandy/BoatSpeedy/actions/workflows/build.yml)
![Version](https://img.shields.io/badge/version-0.3.0-blue)
![minSdk](https://img.shields.io/badge/minSdk-33-green)
![targetSdk](https://img.shields.io/badge/targetSdk-35-green)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

## Features

- **Dashboard** with a large speed readout as the main tile, plus optional
  **battery** and **range/time** tiles (toggled in Settings)
- **Start/Stop trip**: keeps measuring via a foreground service, even with the
  screen off or the app in the background (persistent notification)
- **Trip distance** and **session stats** (max, average, elapsed time); values
  stay on screen after you stop
- **Battery link** over Bluetooth LE: pick your BMS, scan, choose the right pack
  from the list, connect (swap batteries on the go). Live voltage, current, state of
  charge, remaining capacity — plus estimated range & time at the current speed,
  also shown on the speedometer. BMS support: **JBD** (tested), **Daly** and **JK**
  (experimental)
- **Switchable unit**: km/h ↔ knots
- **Configurable decimals**: `xx` / `xx.x` / `xx.xx`
- **Satellite & GPS status**: satellites used/visible, accuracy, fix status
- **Smoothing** of the raw GPS value (important at slow speeds)
- **Light / dark theme** (Light / Dark / System)
- **Bilingual**: English (default) and German, switchable in Settings
- **About screen** with in-app update check (queries GitHub releases)
- **Keep screen on** (optional)

## Tech

| | |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| minSdk / targetSdk | 33 (Android 13) / 35 (Android 15) |
| Speed | `FusedLocationProviderClient` |
| Satellites | `GnssStatus.Callback` |
| Background trip | Foreground service (`foregroundServiceType=location`) |
| Settings | Jetpack DataStore |
| Architecture | MVVM (ViewModel + StateFlow) |

## Install

Grab the signed APK from the [latest release](https://github.com/Glenn-Dandy/BoatSpeedy/releases/latest)
and install it on your Android device (13+). Since it does not come from the Play
Store, allow installation from this source when prompted. On first launch, grant the
**location** permission.

## Build

Requires Android Studio (recent) or JDK 17+ and the Android SDK.

```bash
./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # signed release APK (needs keystore.properties)
```

The APK is written to `app/build/outputs/apk/`.

## Permissions

- `ACCESS_FINE_LOCATION` – precise GPS location for speed & satellites
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` – keep measuring during a trip
- `POST_NOTIFICATIONS` – trip notification (Android 13+)
- `INTERNET` – only for the in-app update check (GitHub releases)
- `BLUETOOTH_SCAN` (neverForLocation), `BLUETOOTH_CONNECT` – battery link (BLE)

No `ACCESS_BACKGROUND_LOCATION` (the service starts from the foreground).

## License

MIT – see [LICENSE](LICENSE).
