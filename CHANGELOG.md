# Changelog

Alle nennenswerten Änderungen an BoatSpeedy werden hier dokumentiert.

## [0.3.0] – 2026-07-23

### Neu
- **Dashboard mit Kacheln**: km/h ist die Haupt-Kachel; zusätzlich zuschaltbare
  Kacheln für **Batterie** (SoC, Spannung, Strom, Rest-Ah, Temp) und
  **Reichweite/Restzeit** (an/aus in den Einstellungen unter „Dashboard")
- **Sprache umschaltbar** (English/Deutsch) in den Einstellungen; **Englisch ist
  jetzt Standard** (per-App-Sprache über LocaleManager)
- **Einstellungen gruppiert**: Speed / Dashboard / Display / Language / GPS

### Geändert
- „Tacho" heißt jetzt **Dashboard**
- Batterie-Konfiguration (Hersteller/Typ/Größe) **entfernt** — Kapazität kommt vom BMS
- Reichweiten-Fallback nutzt jetzt die BMS-Nennkapazität × SoC

### Behoben
- **„Scan-Fehler 1" beim „Batterie wechseln"** (SCAN_FAILED_ALREADY_STARTED):
  vor jedem Scan wird nun sauber getrennt und ein alter Scan gestoppt

## [0.2.1] – 2026-07-23

### Neu
- **SoC, Restreichweite und Restzeit** werden bei verbundener Batterie direkt auf
  dem Tacho-Screen eingeblendet
- **Neuer Batterie-Flow**: BMS-Typ wählen → scannen → gefundene Batterien in einer
  Liste → die richtige antippen → verbinden. Unterwegs über „Batterie wechseln"
  neu scannen und eine andere Batterie wählen (mehrere in der Nähe möglich)
- **Mehrere BMS-Typen**: JBD (getestet) sowie **Daly** und **JK** (experimentell,
  nach öffentlichen Protokoll-Docs, ungetestet)
- Reichweite fällt auf „konfigurierte Kapazität × SoC" zurück, wenn das BMS keine
  Restkapazität liefert (Daly/JK)

## [0.2.0] – 2026-07-23

### Neu
- **Batterie-Anbindung** an das JBD-BMS per Bluetooth LE (EcoWorthy LiFePO4 100 Ah,
  Gerät „DP04S007L4S100A"): Verbinden, zyklisches Auslesen von Spannung, **Strom**,
  **State of Charge**, Restkapazität, Temperatur und Zellspannungen
- **Reichweiten-/Zeitschätzung**: aus Restkapazität ÷ Entladestrom × aktueller
  Geschwindigkeit
- **Seitliches Navigationsmenü** (Drawer): Tacho / Batterie / Einstellungen / Über
- Batterie-Konfiguration (Hersteller / Typ / Größe) in den Einstellungen persistiert

### Geändert
- Neue Berechtigungen `BLUETOOTH_SCAN` (neverForLocation) und `BLUETOOTH_CONNECT`
- Navigation von Zahnrad/Zurück auf ein Hamburger-Menü umgestellt

> Hinweis: BLE nur build-verifiziert; Strom-Vorzeichen und Reichweiten-Rechnung
> müssen mit laufendem Trolling-Motor im Feldtest geprüft werden.

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
