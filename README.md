# BoatSpeedy

Ein schlichter, GPS-basierter **Geschwindigkeitsmesser für Android**, gebaut für
die Geschwindigkeitskontrolle eines Bootes im niedrigen Bereich (ca. 5–10 km/h).

[![Build APK](https://github.com/Glenn-Dandy/BoatSpeedy/actions/workflows/build.yml/badge.svg)](https://github.com/Glenn-Dandy/BoatSpeedy/actions/workflows/build.yml)
![Version](https://img.shields.io/badge/version-0.0.1-blue)
![minSdk](https://img.shields.io/badge/minSdk-33-green)
![targetSdk](https://img.shields.io/badge/targetSdk-35-green)

## Funktionen

- **Große, gut ablesbare Geschwindigkeitsanzeige** (digitaler Zahlen-Tacho)
- **Fahrt Start/Stopp**: misst über einen Vordergrunddienst weiter, auch bei
  ausgeschaltetem Bildschirm / App im Hintergrund (dauerhafte Benachrichtigung)
- **Trip-Distanz** und **Session-Statistik** (Max, Ø, Fahrtzeit); nach „Stopp" bleiben die Werte stehen
- **Einheit umschaltbar**: km/h ↔ Knoten
- **Nachkommastellen einstellbar**: `xx` / `xx.x` / `xx.xx`
- **Satelliten- & GPS-Status**: verwendete/sichtbare Satelliten, Genauigkeit, Fix-Status
- **Glättung** des GPS-Werts (wichtig bei langsamer Fahrt)
- **Hell-/Dunkelmodus** (Hell / Dunkel / System)
- **Display bleibt an** (optional)

## Technik

| | |
|---|---|
| Sprache | Kotlin |
| UI | Jetpack Compose (Material 3) |
| minSdk / targetSdk | 33 (Android 13) / 35 (Android 15) |
| Geschwindigkeit | `FusedLocationProviderClient` |
| Satelliten | `GnssStatus.Callback` |
| Einstellungen | Jetpack DataStore |
| Architektur | MVVM (ViewModel + StateFlow) |

## Bauen

Voraussetzung: Android Studio (aktuell) oder JDK 17+ und Android SDK.

```bash
./gradlew assembleDebug      # Debug-APK bauen
./gradlew installDebug       # auf angeschlossenes Gerät installieren
```

Die APK liegt danach unter `app/build/outputs/apk/debug/`.

## Berechtigungen

- `ACCESS_FINE_LOCATION` – präziser GPS-Standort für Geschwindigkeit & Satelliten
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` – Weitermessen während einer Fahrt
- `POST_NOTIFICATIONS` – Fahrt-Benachrichtigung (Android 13+)

Kein Internet, keine `ACCESS_BACKGROUND_LOCATION` (der Dienst startet aus dem Vordergrund).

## Lizenz

MIT – siehe [LICENSE](LICENSE).
