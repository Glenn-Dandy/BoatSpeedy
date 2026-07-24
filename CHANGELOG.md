# Changelog

Alle nennenswerten Änderungen an BoatSpeedy werden hier dokumentiert.

## [0.4.7] – 2026-07-24

### Geändert
- **Verbinden/Trennen** je Batterie ist jetzt ein einfacher **An/Aus-Schalter**
  (Spinner während des Verbindens).
- **SoC-Warnschwelle frei einstellbar** per Regler (0 = aus, sonst % in 5er-Schritten,
  Standard 20 %) statt fester Stufen.
- Labels gekürzt: „Batterie-Kachel" → **„Batterie"**, „Satelliten-Details anzeigen" →
  **„Satelliten-Details"**.

### Behoben / Hinweis
- **Debug-Paket abgetrennt** (`…​.debug`): Debug- und Release-App überschreiben sich
  nicht mehr gegenseitig. Damit Einstellungen ein Update überstehen, immer die
  **Release-APK aus den GitHub-Releases** installieren (gleiche Signatur).

## [0.4.6] – 2026-07-24

### Neu
- **Energie-Verbrauch pro Fahrt**: Die Bank-Leistung (alle aktiven Akkus) wird während
  einer Fahrt über die Zeit zu **Wh** aufintegriert und in der Fahrt-Statistik
  angezeigt, dazu die **Effizienz in Wh/km**. Erscheint nur, wenn während der Fahrt
  eine Batterie verbunden ist.

## [0.4.5] – 2026-07-24

### Neu
- **Warnung bei niedrigem Ladestand**: Der SoC auf der Batterie-Kachel wird **rot**,
  sobald er unter eine einstellbare Schwelle fällt (Aus / 10 % / 20 % / 30 %,
  Standard 20 %) — kein Ton. Einstellbar unter **Einstellungen → Dashboard**.

## [0.4.4] – 2026-07-24

### Neu
- **Auto-Reconnect**: Aktive gespeicherte Akkus werden beim App-Start automatisch
  verbunden (sofern die Bluetooth-Berechtigung bereits erteilt ist). Bricht ein Link
  unterwegs ab, verbindet die App selbstständig neu (alle ~5 s).

## [0.4.3] – 2026-07-24

### Geändert
- **Leistung wird als Betrag angezeigt** (immer positiv) — auf der Batterie-Kachel
  und in der Detail-Karte.

## [0.4.2] – 2026-07-24

### Neu
- **Leistung (W)** auf der Batterie-Kachel — berechnet aus Spannung × Strom (das BMS
  liefert keine Leistung). Kachel-Layout jetzt: Zeile 1 **Leistung · Spannung · Strom**,
  Zeile 2 **SoC · Rest · Temp**.
- **Ausführlicher Batterie-Status im Batterie-Menü**: Tippt man eine hinzugefügte
  Batterie an, klappt eine Detailkarte auf — alle Werte plus **Zellen &
  Zellspannungen**.

### Geändert
- Batterie-Menü: die Karte „Reichweite bei aktueller Geschwindigkeit" **entfernt**
  (Reichweite/Restzeit stehen weiterhin als Kachel auf dem Dashboard).

## [0.4.1] – 2026-07-24

### Neu
- **Reichweiten-Glättung**: Reichweite und Restzeit zappeln nicht mehr mit dem
  Momentanstrom. Entladestrom und Geschwindigkeit werden über ein **Zeitfenster
  gemittelt** (einstellbar: Aus / 15 s / 30 s / 60 s, Standard 30 s), bevor gerechnet
  wird. Neue Auswahl unter **Einstellungen → Dashboard**.

### Bestätigt
- JBD-Strom-Vorzeichen im Feldtest verifiziert (negativ = Entladen); Reichweiten-
  rechnung funktioniert.

## [0.4.0] – 2026-07-23

### Neu
- **Mehrere Batterien**: Akkus dauerhaft hinzufügen, verwalten und **gleichzeitig**
  verbinden. Neuer Batterie-Screen mit Liste (Aktiv-Häkchen, Live-Werte,
  Verbinden/Trennen, Entfernen) statt Einzel-Verbindung.
- **Verschaltungs-Modus** (Einzeln / Parallel / Reihe): bestimmt, wie die aktiven
  Akkus zusammengerechnet werden — **Parallel/Einzeln** summiert Kapazität & Strom,
  **Reihe** summiert die Spannung. Nicht aktive Akkus werden ignoriert.
- **Dashboard-Auswahl** (dezent): zwischen einzelnen aktiven Batterien und der
  kombinierten Ansicht (Σ) umschalten; Reichweite/Restzeit rechnet mit der Auswahl.

### Geändert
- **Batterie- und Reichweiten-Kachel sind jetzt immer sichtbar** (Platzhalter ohne
  Werte), sofern nicht in den Einstellungen ausgeblendet. Neue Reihenfolge:
  Geschwindigkeit → Reichweite → Batterie.

## [0.3.1] – 2026-07-23

### Geändert
- **Einstellungen neu strukturiert**: Übersicht mit Kategorien, die jeweils eine
  eigene Unterseite öffnen — **Dashboard** (Einheit, Nachkommastellen, Glättung,
  Kacheln, Satelliten), **Darstellung** (Theme, Display anlassen) und **Sprache**.
  Trennt Dashboard-spezifische von app-weiten Einstellungen.

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
