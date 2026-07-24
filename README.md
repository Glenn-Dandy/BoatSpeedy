# BoatSpeedy

A **GPS boat speedometer for Android** with **Bluetooth battery analytics and range
estimation** — made for **electric / trolling motors** running on a **Bluetooth (BLE)
battery**. Large, easy-to-read speed readout for low-speed control (~5–10 km/h), live
battery data, and an estimate of how far and how long you can still go.

[![Build APK](https://github.com/Glenn-Dandy/BoatSpeedy/actions/workflows/build.yml/badge.svg)](https://github.com/Glenn-Dandy/BoatSpeedy/actions/workflows/build.yml)
![Version](https://img.shields.io/badge/version-0.4.1-blue)
![minSdk](https://img.shields.io/badge/minSdk-33-green)
![targetSdk](https://img.shields.io/badge/targetSdk-35-green)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

🇬🇧 **English** · [🇩🇪 Deutsch](#-deutsch)

---

## What it's for

Electric boats and kayaks with a trolling motor usually run on a LiFePO4 pack whose
BMS talks Bluetooth. BoatSpeedy pairs a **precise GPS speedometer** with that battery
so you can, on the water, see **how fast you're going, how much power you're drawing,
and how far you can still get** — all on one screen.

## Features

### Speed & trip
- **Dashboard** with a large speed readout as the main tile
- **Switchable unit**: km/h ↔ knots, configurable decimals (`xx` / `xx.x` / `xx.xx`)
- **Start/Stop trip** via a foreground service — keeps measuring with the screen off
  or the app in the background (persistent notification)
- **Trip distance** and **session stats** (max, average, elapsed); values stay after stop
- **Satellite & GPS status** (satellites used/visible, accuracy, fix), **smoothing** of
  the raw GPS value (important at slow speeds)

### Battery analytics (Bluetooth LE)
- **Multiple batteries**: add packs, keep several **connected at the same time**, mark
  which ones are **active**
- **Wiring mode** — **Single / Parallel / Series** — decides how active packs are
  combined: parallel/single sum up capacity & current, series sums up voltage
- **Live values**: voltage, current, state of charge, remaining Ah, temperature
- **Range & remaining time** at the current speed, **time-averaged** (Off / 15 s /
  30 s / 60 s) so it doesn't jitter with the motor load
- **Dashboard tiles** for battery and range (always visible, or hidden in Settings),
  plus a subtle **A · B · Σ** selector to view a single pack or the combined bank
- **BMS support**: **JBD / Jiabaida** (tested), **Daly** and **JK / Jikong** (experimental)

### App
- **Light / dark theme** (Light / Dark / System), optional **keep screen on**
- **Bilingual**: English (default) and German, switchable in Settings
- **About screen** with in-app update check (queries GitHub releases)

## Status & untested features

BoatSpeedy is developed and hardware-verified against an **EcoWorthy LiFePO4 100 Ah**
(JBD BMS). Some paths are implemented but **not yet verified on real hardware** — use
with a critical eye and please report back:

| Area | Status |
|---|---|
| GPS speed, trip, stats | ✅ working |
| JBD battery link, live values | ✅ verified on hardware |
| JBD current sign & range/time math | ✅ field-tested (negative = discharge) |
| **Daly BMS** | ⚠️ experimental — UUIDs/offsets from public docs, **untested** |
| **JK / Jikong BMS** | ⚠️ experimental — esp. JK02 offsets/SOC, **untested** |
| **Series / parallel combination** of multiple packs | ⚠️ **untested** on a real multi-pack setup |

## Roadmap / TODO

- [ ] Verify **series / parallel** combination on a real multi-battery setup
- [ ] Calibrate **Daly** and **JK** against real hardware (UUIDs/offsets)
- [ ] **Auto-reconnect** and auto-connect active packs on launch (remember last MAC)
- [ ] **App Bundle (`.aab`)** for the Play Store

See [`TODO.md`](TODO.md) for the full list, and [`CHANGELOG.md`](CHANGELOG.md) for changes.

## Install

Grab the signed APK from the [latest release](https://github.com/Glenn-Dandy/BoatSpeedy/releases/latest)
and install it on Android 13+. Since it isn't from the Play Store, allow installation
from this source when prompted. On first launch, grant the **location** permission; for
the battery link, grant **Bluetooth** when you first scan.

## Build

Requires a recent Android Studio or JDK 17+ and the Android SDK.

```bash
./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # signed release APK (needs keystore.properties)
```

The APK is written to `app/build/outputs/apk/`.

## Tech

| | |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| minSdk / targetSdk | 33 (Android 13) / 35 (Android 15) |
| Speed / satellites | `FusedLocationProviderClient` / `GnssStatus.Callback` |
| Battery | Bluetooth LE (`BluetoothGatt`), per-device connections |
| Background trip | Foreground service (`foregroundServiceType=location`) |
| Settings | Jetpack DataStore |
| Architecture | MVVM (ViewModel + StateFlow) |

## Permissions

- `ACCESS_FINE_LOCATION` – precise GPS for speed & satellites
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` – keep measuring during a trip
- `POST_NOTIFICATIONS` – trip notification (Android 13+)
- `INTERNET` – only for the in-app update check (GitHub releases)
- `BLUETOOTH_SCAN` (neverForLocation), `BLUETOOTH_CONNECT` – battery link (BLE)

No `ACCESS_BACKGROUND_LOCATION` (the service starts from the foreground).

## License

MIT – see [LICENSE](LICENSE).

---

## 🇩🇪 Deutsch

[🇬🇧 English](#boatspeedy) · **Deutsch**

Ein **GPS-Boots-Tacho für Android** mit **Bluetooth-Batterie-Auswertung und
Reichweiten­berechnung** — gemacht für **Elektro- / Trolling-Motoren** mit einer
**Bluetooth-Batterie (BLE)**. Große, gut ablesbare Geschwindigkeitsanzeige für die
Kontrolle im langsamen Bereich (~5–10 km/h), Live-Batteriedaten und eine Schätzung,
**wie weit und wie lange** du noch fahren kannst.

### Wofür

E-Boote und Kajaks mit Trolling-Motor laufen meist auf einem LiFePO4-Akku, dessen BMS
per Bluetooth funkt. BoatSpeedy verbindet einen **präzisen GPS-Tacho** mit diesem Akku,
sodass du auf dem Wasser **Tempo, Stromverbrauch und Restreichweite** auf einem Bild
siehst.

### Funktionen

**Tempo & Fahrt**
- **Dashboard** mit großer Geschwindigkeit als Haupt-Kachel
- **Einheit umschaltbar** km/h ↔ Knoten, Nachkommastellen `xx` / `xx.x` / `xx.xx`
- **Fahrt Start/Stopp** über Vordergrunddienst — misst auch bei ausgeschaltetem Display
  oder im Hintergrund weiter (dauerhafte Benachrichtigung)
- **Trip-Distanz** und **Session-Statistik** (Max, Ø, Zeit); bleiben nach dem Stopp stehen
- **Satelliten-/GPS-Status** und **Glättung** des rohen GPS-Werts (wichtig bei langsamer Fahrt)

**Batterie-Auswertung (Bluetooth LE)**
- **Mehrere Batterien**: Akkus hinzufügen, mehrere **gleichzeitig verbunden**, per
  Häkchen **aktiv** schalten
- **Verschaltung** — **Einzeln / Parallel / Reihe** — bestimmt die Zusammenrechnung:
  parallel/einzeln addieren Kapazität & Strom, Reihe addiert die Spannung
- **Live-Werte**: Spannung, Strom, Ladezustand, Rest-Ah, Temperatur
- **Reichweite & Restzeit** bei aktueller Geschwindigkeit, **zeitlich gemittelt**
  (Aus / 15 s / 30 s / 60 s), damit nichts mit der Motorlast zappelt
- **Dashboard-Kacheln** für Batterie und Reichweite (immer sichtbar oder ausblendbar),
  dazu ein dezenter **A · B · Σ**-Umschalter (einzeln oder kombiniert)
- **BMS-Unterstützung**: **JBD / Jiabaida** (getestet), **Daly** und **JK / Jikong**
  (experimentell)

**App**
- **Hell / Dunkel** (Hell / Dunkel / System), optional **Display anlassen**
- **Zweisprachig**: Englisch (Standard) und Deutsch, umschaltbar in den Einstellungen
- **„Über"-Screen** mit In-App-Update-Prüfung (fragt GitHub-Releases ab)

### Status & ungetestete Funktionen

Entwickelt und an einer **EcoWorthy LiFePO4 100 Ah** (JBD-BMS) verifiziert. Manches ist
umgesetzt, aber **noch nicht an echter Hardware geprüft** — bitte mit Vorsicht nutzen
und Rückmeldung geben:

| Bereich | Status |
|---|---|
| GPS-Tempo, Fahrt, Statistik | ✅ funktioniert |
| JBD-Anbindung, Live-Werte | ✅ an Hardware verifiziert |
| JBD Strom-Vorzeichen & Reichweiten-/Zeitrechnung | ✅ im Feldtest bestätigt (negativ = Entladen) |
| **Daly-BMS** | ⚠️ experimentell — UUIDs/Offsets aus Doku, **ungetestet** |
| **JK / Jikong-BMS** | ⚠️ experimentell — v. a. JK02-Offsets/SOC, **ungetestet** |
| **Reihen-/Parallel-Kombination** mehrerer Akkus | ⚠️ **ungetestet** an echtem Mehr-Akku-Aufbau |

### Roadmap / TODO

- [ ] **Reihen-/Parallel**-Kombination an echtem Mehr-Akku-Aufbau prüfen
- [ ] **Daly** und **JK** an echter Hardware kalibrieren (UUIDs/Offsets)
- [ ] **Auto-Reconnect** und aktive Akkus beim Start automatisch verbinden (MAC merken)
- [ ] **App-Bundle (`.aab`)** für den Play Store

Vollständige Liste in [`TODO.md`](TODO.md), Änderungen in [`CHANGELOG.md`](CHANGELOG.md).

### Installieren

Signierte APK aus dem [neuesten Release](https://github.com/Glenn-Dandy/BoatSpeedy/releases/latest)
laden und auf Android 13+ installieren. Da nicht aus dem Play Store, bei Nachfrage die
Installation aus dieser Quelle erlauben. Beim ersten Start die **Standort**-Berechtigung
erteilen; für die Batterie-Anbindung beim ersten Scan **Bluetooth** erlauben.

### Bauen

```bash
./gradlew assembleDebug      # Debug-APK
./gradlew assembleRelease    # signierte Release-APK (braucht keystore.properties)
```

### Lizenz

MIT – siehe [LICENSE](LICENSE).
