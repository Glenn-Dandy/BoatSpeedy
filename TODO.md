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

## Batterie (v0.2.0) – noch zu prüfen/erweitern
- [ ] **Feldtest mit laufendem Motor**: Strom-Vorzeichen (Laden/Entladen) und Reichweiten-/Zeitrechnung verifizieren
- [ ] Auto-Reconnect bei Verbindungsabbruch; MAC merken (schnellerer Reconnect ohne Scan)
- [ ] Reichweite mit geglättetem Strom (schwankt) rechnen
- [ ] Batterie-Kurzinfo (SoC) evtl. auf dem Tacho-Screen einblenden

## Erledigt
- [x] v0.1.0: Fahrt Start/Stopp (Hintergrund), Trip-Distanz, Session-Statistik
- [x] v0.1.1: „Über"-Screen mit GitHub-Infos, Versionsanzeige und Update-Prüfung (Download bei neuer Version)
- [x] v0.2.0: Batterie-Anbindung (JBD-BLE), Reichweite/Zeit, seitliches Drawer-Menü
