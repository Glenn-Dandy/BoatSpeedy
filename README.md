# BoatSpeedy

Ein schlichter, GPS-basierter **Geschwindigkeitsmesser für Android**, gebaut für
die Geschwindigkeitskontrolle eines Bootes im niedrigen Bereich (ca. 5–10 km/h).

![Version](https://img.shields.io/badge/version-0.0.1-blue)
![minSdk](https://img.shields.io/badge/minSdk-33-green)
![targetSdk](https://img.shields.io/badge/targetSdk-35-green)

## Funktionen

- **Große, gut ablesbare Geschwindigkeitsanzeige** (digitaler Zahlen-Tacho)
- **Einheit umschaltbar**: km/h ↔ Knoten
- **Nachkommastellen einstellbar**: `xx` / `xx.x` / `xx.xx`
- **Satelliten- & GPS-Status**: verwendete/sichtbare Satelliten, Genauigkeit, Fix-Status
- **Glättung** des GPS-Werts (wichtig bei langsamer Fahrt)
- **Hell-/Dunkelmodus** (Hell / Dunkel / System)
- **Display bleibt an** (optional)
- Misst **nur im Vordergrund** – keine Hintergrundortung, minimale Berechtigungen

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

- `ACCESS_FINE_LOCATION` – präziser GPS-Standort für Geschwindigkeit & Satelliten.

Kein Internet, kein Hintergrunddienst.

## Lizenz

MIT – siehe [LICENSE](LICENSE).
