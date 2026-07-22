# TODO

Geparkte Aufgaben / Ideen für BoatSpeedy.

## Build & Release
- [ ] **App-Bundle (`.aab`) für den Play Store** ergänzen
  - `./gradlew bundleRelease`, signiert mit demselben Keystore
  - CI: bei `v*`-Tags zusätzlich `.aab` bauen (Play-Upload ggf. via `r0adkll/upload-google-play` später)
  - Hinweis: für den Play Store „Play App Signing" beachten (Upload-Key vs. App-Signing-Key)

## Feature-Ideen (noch offen)
- [ ] Visuelle Tempo-Warnschwelle (Zahl wird rot ab konfigurierbarer Grenze, kein Ton)
- [ ] Bildschirm-Ausrichtung sperren (Quer/Hoch)
- [ ] Kurs/Heading + Himmelsrichtung
- [ ] Mini-Verlaufsdiagramm (Sparkline)
- [ ] Nachtmodus (gedimmte rote Anzeige)
- [ ] Zusätzliche Einheit mph; GPS-Intervall in den Einstellungen
- [ ] Track-Aufzeichnung + GPX-Export
- [ ] Uhr + Fahrtzeit-Anzeige

## Erledigt
- [x] v0.1.0: Fahrt Start/Stopp (Hintergrund), Trip-Distanz, Session-Statistik
- [x] v0.1.1: „Über"-Screen mit GitHub-Infos, Versionsanzeige und Update-Prüfung (Download bei neuer Version)
