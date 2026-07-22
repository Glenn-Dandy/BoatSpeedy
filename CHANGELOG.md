# Changelog

Alle nennenswerten Änderungen an BoatSpeedy werden hier dokumentiert.

## [0.1.1] – 2026-07-22

### Neu
- **„Über"-Screen** in den Einstellungen: App-Version, Link zum GitHub-Repo, Lizenz
- **Update-Prüfung**: fragt das neueste GitHub-Release ab; bei neuerer Version
  Hinweis „Update verfügbar" mit Direkt-Download der APK bzw. Link zur Release-Seite

### Geändert
- Neue Berechtigung `INTERNET` (ausschließlich für die Update-Prüfung)

## [0.1.0] – 2026-07-22

### Neu
- **Fahrt Start/Stopp** über Vordergrunddienst: misst weiter bei ausgeschaltetem
  Bildschirm / App im Hintergrund, mit dauerhafter Benachrichtigung (Live-Tempo + Distanz)
- **Trip-Distanz** – zurückgelegte Strecke während der Fahrt
- **Session-Statistik** – Max- & Durchschnittsgeschwindigkeit sowie Fahrtzeit;
  nach „Stopp" bleiben die Werte stehen

### Geändert
- Kehrt die ursprüngliche „nur Vordergrund"-Entscheidung um. Neue Berechtigungen:
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`.
  `ACCESS_BACKGROUND_LOCATION` wird bewusst **nicht** genutzt (Dienst startet aus dem Vordergrund).

## [0.0.1] – 2026-07-22

Erste Version (Grundgerüst).

### Enthalten
- Digitale km/h-Anzeige aus GPS-Geschwindigkeit
- Einheit umschaltbar km/h ↔ Knoten
- Nachkommastellen einstellbar (`xx` / `xx.x` / `xx.xx`)
- Satelliten- & GPS-Status (verwendet/sichtbar, Genauigkeit, Fix)
- Glättung (Aus / Leicht / Stark)
- Hell-/Dunkelmodus (Hell / Dunkel / System)
- Option „Display anlassen"
- Messung nur im Vordergrund (keine Hintergrundortung)
