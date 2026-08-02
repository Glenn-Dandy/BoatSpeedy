# Changelog

Alle nennenswerten Änderungen an BoatSpeedy werden hier dokumentiert.

## [1.2.2] – 2026-08-02

### Geändert
- **Reproduzierbare Builds**: die automatisch eingebettete VCS-Info
  (`version-control-info.textproto`) wird nicht mehr in die Release-APK geschrieben —
  dadurch ist der Build byte-genau reproduzierbar (Voraussetzung, dass F-Droid die
  App mit deiner Original-Signatur ausliefern kann).

## [1.2.1] – 2026-08-01

### Geändert
- **Vollständig frei / Open Source**: Google Play Services entfernt — GPS läuft jetzt über
  den AOSP-`LocationManager` (Voraussetzung für F-Droid).
- **GPX-Export** enthält jetzt **Geschwindigkeit, Verbrauch (Ah) und SoC** (als
  Erweiterungen); der **Import** liest diese Werte wieder ein (voller Round-Trip).
- F-Droid-Metadaten (Store-Texte, Changelog, Screenshots) und README aktualisiert.

## [1.2.0] – 2026-08-01

### Neu
- **DWD-Wetterwarnungen** (Gewitter/Sturm): Prüfung sofort bei GPS-Fix und alle 10 Min –
  auch während der Fahrt im Hintergrund. Benachrichtigung + optionaler Alarmton +
  Dashboard-Banner mit „gültig bis". Schalter unter Einstellungen → Allgemein.
- **Wetterradar auf der Live-Karte** („Wetter anzeigen"): DWD-Regenradar (RADOLAN-RV)
  als Overlay, animiert von jetzt bis +100 Min in 5-Min-Schritten, mit Play/Pause,
  Schieberegler und Hintergrund-Vorladen; zuschaltbare **Blitzdichte**. Datenbasis:
  Deutscher Wetterdienst (CC BY 4.0).
- **Lademodus**: erkennt Laden (positiver Strom) → GPS aus, Tempo „--", Reichweiten-Kachel
  wird zur Lade-Kachel („Voll in" / „Fertig um"), laufende Lade-Meldung mit SoC,
  „Batterie voll"-Meldung sowie individuelle Meldung bei einstellbarem Ladestand.
- **Live-Karte**: Schwenken pausiert das Folgen (Recenter-Button); Antippen des Tracks
  zeigt dieselbe Sprechblase wie bei den Fahrten.
- **Über**: neuer Abschnitt „Daten & Karten" (OpenStreetMap, DWD/Bright Sky).

### Behoben / Geändert
- **GPS erschien bei Fake-GPS/Mock-Location nicht** (Position kam ohne Satelliten-Status
  nicht durch) – behoben; auch schnellerer Start.
- Einstellungen „Allgemein" gruppiert (Ladestand-Block zusammen); **Auto-Pause** nach
  Einstellungen → Fahrten verschoben.

## [1.1.0] – 2026-07-30

### Neu
- **Neu gestaltete „Über"-Seite**: App-Icon + Version im Kopf, Karten für **Feedback**
  (Fehler melden / Funktion anfragen — öffnet vorausgefüllte GitHub-Issues mit App-,
  Android- und Geräte-Info), **Projekt** (Stern auf GitHub, Quellcode, **Projekt
  unterstützen** via PayPal) und **Update**. Dezenter **DE·EN**-Umschalter oben rechts;
  der alte rohe GitHub-Link ist raus.

## [1.0.9] – 2026-07-28

### Neu
- **GPX-Import**: Fahrten aus GPX-Dateien laden — Import-Symbol in „Fahrten" **und**
  BoatSpeedy erscheint im „Öffnen mit"-Dialog (auch für Cloud-/Download-Dateien).
- **Start-/Ziel-Marker** auf der Track-Karte (grüne Fahne / karierte Zielflagge).
- **Linienstärke** der Track-Linie einstellbar (Dünn / Normal / Dick) unter
  **Einstellungen → Fahrten**, dazu die durchgehende, geglättete Linie (kein Punkt
  verschoben).

### Behoben / Geändert
- **Richtungspfeile** zeigen jetzt korrekt in Fahrtrichtung (Dreh-Vorzeichen für
  osmdroid korrigiert).
- Einstellungs-Kategorie heißt jetzt **„Fahrten"** (DE) / **„Trips"** (EN).

## [1.0.8] – 2026-07-28

### Behoben
- **Richtungspfeile zeigen jetzt zuverlässig in Fahrtrichtung**: der Kurs wird über eine
  ~25-m-Basislinie entlang des **unveränderten** Tracks berechnet (nicht mehr aus
  benachbarten Punkten, deren Abstand bei Langsamfahrt kleiner als das GPS-Rauschen war).
  Zu kurze/verrauschte Stellen bekommen keinen Pfeil statt eines zufälligen. Der Track
  selbst wird nicht geglättet.

## [1.0.7] – 2026-07-25

### Neu
- **Einstellungen → Tracks** (zwischen Darstellung und GPS): **Track-Farbe**
  (Blau/Rot/Schwarz) und **Richtungspfeile an/aus**. Wirkt auf die Mini-Karte im
  Fahrt-Detail und die große Karte; die Pfeile werden in der Track-Farbe eingefärbt.

### Geändert
- **Richtungspfeile** stabiler: der Kurs wird über ein kleines Punkt-Fenster gemittelt
  (weniger GPS-Jitter).
- Die große Track-Karte zeigt im Kopf **Datum · Uhrzeit** der Fahrt (statt „In Karte
  anzeigen").
- Menü: dezente Linie **zwischen Anker und Einstellungen**.

## [1.0.6] – 2026-07-25

### Geändert
- **Track-Sprechblase direkt am angetippten Punkt** (folgt der Karte) statt Info-Karte
  am unteren Rand.
- **Richtungspfeile größer** und besser sichtbar.

## [1.0.5] – 2026-07-25

### Neu
- **Track auf Karte**: im Fahrt-Detail wird der Track direkt als **kleine Karte**
  gezeigt (Antippen öffnet die große Ansicht), mit **dezenten Richtungspfeilen**.
  **Antippen des Tracks** zeigt eine Info-Karte mit **Zeit, Geschwindigkeit,
  Verbrauch (Ah) und Ladestand** an dieser Stelle. Neue Fahrten zeichnen diese
  Telemetrie je Wegpunkt auf.

### Geändert / Behoben
- **Ruhigere Geschwindigkeit**: Fixes mit schlechter Genauigkeit werden fürs Tempo
  **ignoriert** und kurze Aussetzer **überbrückt** (letzter Wert wird gehalten) — kein
  kurzes „Zusammenbrechen" der Anzeige mehr. Auch die Max-Geschwindigkeit ignoriert
  jetzt schlechte Fixes.

## [1.0.4] – 2026-07-25

### Neu
- **Anker-Alarm** (neues Menü „Anker"): Anker an der aktuellen Position mit Radius
  setzen; ein Vordergrunddienst überwacht die Distanz **auch bei Display aus** und löst
  bei Überschreiten einen **Alarmton in Schleife** aus. „Stumm" / „Anker einholen".
  Karte mit Ankerpunkt, Radius-Kreis und Bootsposition.
- **Alarmtöne** (mitgeliefert: Piep / Glocke / Sirene) über den Alarm-Stream. Getrennt
  einstellbar: **Ankeralarm-Ton** an/aus + Auswahl, **SoC-Alarm-Ton** an/aus + Auswahl,
  je mit Test-Knopf.
- **Fahrt-Zeiten aufgeteilt**: gespeicherte Fahrten zeigen **Gesamtzeit / Fahrzeit /
  Pause** (Pause = Auto-Pause-Zeit).
- **Auto-Pause einstellbar**: Strom-Schwelle als Eingabefeld (0/leer = aus).

### Geändert
- **Einstellungen umstrukturiert**: **Dashboard · Allgemein · Darstellung · GPS**.
  „Allgemein" bündelt niedriger Ladestand, Auto-Pause und Alarme. Sprache liegt jetzt
  in „Darstellung" (plus dezent unter Lizenz im „Über").
- Dashboard-Meldung „Pausiert (0 A)" heißt jetzt **„Auto-Pause"**.
- „Über": die „Build …"-Zeile entfernt.

### Behoben
- Anker-Wache verschickt keine Dauer-Benachrichtigungen mehr; Karte gleitet/überlappt
  nicht mehr.
- SoC-Alarm löst nicht mehr durch die kurzen 0-Werte direkt nach dem Batterie-Verbinden aus.

## [1.0.3] – 2026-07-24

### Geändert
- **Satelliten-Details an/aus** wieder unter **Einstellungen → Dashboard** (das
  GPS-Menü behält die Live-Werte).
- Englisches Label gekürzt: „Range &amp; time tile" → „Range &amp; time".

## [1.0.2] – 2026-07-24

### Neu
- **Scrollbares Dashboard** — die Geschwindigkeit bleibt oben fix, der Rest scrollt.
- **Karten-Kachel** auf dem Dashboard (Position + Live-Track als nicht-interaktive
  Vorschau); Tippen öffnet die **Vollbild-Live-Karte**, die der Position folgt. Ist die
  Kachel ausgeblendet, erscheint oben rechts ein kleiner Karten-Button. An/aus unter
  **Einstellungen → Dashboard → Karte**.
- **Einstellungen → GPS** (neu): Satelliten-Details-Schalter hierher verschoben, plus
  Live-Werte **Satelliten, Genauigkeit, Kurs + Himmelsrichtung, Signal (C/N0),
  Konstellationen, Höhe**. (Wir nutzen den Fused-Provider — alle GNSS, nicht nur GPS.)
- **Ah-Verbrauch** wird auf dem Dashboard ab Fahrtbeginn angezeigt, sobald eine
  Batterie verbunden ist (vorher erst ab Verbrauch > 0).

### Behoben
- **Randwischen öffnet das Menü nicht mehr** — kollidierte mit dem horizontalen
  Schwenken der Karte (Menü öffnet über das Symbol).

### Build & Versionierung
- **versionName kommt jetzt aus dem Git-Tag** (Single Source of Truth) — Release = Tag
  setzen, keine Hand-Bumps mehr; Dev-Builds heißen automatisch `1.0.1-3-gabc123`.
  `versionCode` bleibt der manuelle kleine Integer (jetzt 20).
- **Commit-SHA im „Über"-Screen** (`BuildConfig.GIT_SHA`).
- **DEV-Build** ist ein eigenes Paket mit Label **„BoatSpeedy DEV"**, signiert mit dem
  Release-Keystore (Dev-über-Dev-Updates ohne Deinstallieren).
- **CI**: jeder Feature-Branch-Push baut einen DEV-Build und aktualisiert **ein**
  rollendes `dev`-Pre-Release (immer nur der aktuelle Dev-Build); Release weiterhin per
  `v*`-Tag.

### Behoben / Geändert
- **Karten-Kacheln laden im Release** (osmdroid Keep-Regeln für R8; vorher nur
  Karo-Muster).
- Label **„Glättung" → „Geschwindigkeit-Glättung"** (klarere Abgrenzung zur
  Reichweiten-Glättung).

## [0.4.11] – 2026-07-24

### Neu
- **Track auf Karte anzeigen**: In der Fahrt-Detailansicht öffnet **„In Karte anzeigen"**
  eine OpenStreetMap-Karte (osmdroid) mit der gefahrenen Strecke als Linie. Braucht
  Internet (Kartenkacheln werden geladen).

## [0.4.10] – 2026-07-24

### Neu
- **GPX-Export**: In der Fahrten-Historie ausgewählte Fahrten (Häkchen) über das
  **Teilen-Symbol** als `.gpx` exportieren/teilen; auch im Detail-Screen einer Fahrt.
  Mehrere Fahrten kommen als separate `<trk>`-Segmente in eine Datei.

## [0.4.9] – 2026-07-24

### Neu
- **Fahrten-Historie**: neuer Menüpunkt **Fahrten** (zwischen Dashboard und Batterie).
  Fahrten werden beim Stoppen automatisch gespeichert; Liste neueste zuerst mit
  **Mehrfachauswahl (Häkchen)** und **Löschen** oben; **Antippen zeigt die Details**
  (Distanz, Zeit, Ø/Max, Ah, Wh, Wh/km).
- **Track-Aufzeichnung**: Wegpunkte werden je Fahrt mitgespeichert — Grundlage für den
  kommenden GPX-Export und die Kartenansicht.

## [0.4.8] – 2026-07-24

### Neu
- **Verbrauch in Ah** pro Fahrt (aus dem Strom integriert), zusätzlich zu Wh und Wh/km.
- **Auto-Pause bei 0 A**: Fließt kein Strom (Motor aus) und ist eine Batterie verbunden,
  ruht die **ganze Fahrt** — Zeit, Distanz und Verbrauch werden nicht weitergezählt.
  Sobald wieder Strom fließt, läuft alles weiter. Anzeige „⏸ Pausiert (0 A)". Ohne
  verbundene Batterie gibt es keine Auto-Pause.

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
