# Arbeitsweise in diesem Projekt

## Nicht sofort bauen

**Erst rückfragen, dann umsetzen.** Anweisungen von Florian kommen oft als Frage oder
Überlegung („nennen wir das um in X?", „sollen wir Y?"). Das ist eine Einladung zum
Gegenprüfen, kein Auftrag. Also: verstanden zusammenfassen, offene Punkte benennen,
Bestätigung abwarten — auch wenn die Änderung klein aussieht.

Beim Nachfragen konkret werden: was genau geändert wird, welche Stellen es sonst noch
betrifft, und was dagegen spricht.

**Nichts anfassen, wonach nicht gefragt wurde.** Keine Umbenennung, kein Aufräumen und
keine „Verbesserung" nebenbei. Fällt beim Arbeiten etwas auf, wird es erwähnt — nicht
geändert.

## Änderungen dokumentieren

Landet etwas auf `dev`, kommt der passende Punkt **sofort** mit in
`.github/dev-notes.md` — auf Englisch, kurz, aus Sicht der Nutzer. Daraus baut der
Workflow den Text des Dev-Pre-Releases.

Aufgenommen wird nur, was unterm Strich anders ist als vorher. Nehmen wir etwas
wieder zurück, fliegt der Punkt raus, statt einen zweiten dazuzuschreiben — zwei
Einträge für null Änderung sind schlimmer als keiner. Nicht hinein gehören
Zwischenschritte an einer Sache, die es vorher gar nicht gab, und alles rund um
Workflow, Release-Seite und Werkzeuge, was Tester nicht betrifft.

Beim Release wandern die Punkte nach `CHANGELOG.md` (deutsch, ausführlicher) und in
`fastlane/metadata/android/{de-DE,en-US}/changelogs/<versionCode>.txt`. Danach fängt
`dev-notes.md` mit der neuen Versionsnummer wieder bei null an.

## Tests

`app/src/test/` prüft, was reine Rechnerei ist: die Protokoll-Auswerter (JBD, Redodo)
gegen **echte, mitgeschnittene Frames** und das Zusammenführen von Fahrten. Läuft mit
`./gradlew testDebugUnitTest` und im Workflow vor jedem Build.

Wer an Byte-Offsets, Einheiten oder Zeitrechnung rührt, ergänzt dort einen Fall. Der Grund:
Fehler dieser Art erzeugen **plausible falsche Werte** — das sieht man der App nicht an,
das merkt man erst auf dem Wasser.

## Bauen und Veröffentlichen

- Entwicklung auf `dev`, `main` nur über PR und Tag.
- **Nur DEV-Builds**, solange nicht ausdrücklich „build haupt" gesagt wurde.
- `versionCode`/`versionName` sind manuelle Literale in `app/build.gradle.kts`
  (F-Droid liest den versionName statisch aus).
- Release-APK immer aus einem frischen Clone des Tags, von Gradle signiert.

# Projektstand (13.10.2026)

## Eckdaten

- App: GPS-Tacho und Wasserweg-Navigation für kleine Boote (Motor oder Paddel), dazu
  BLE-Akkuanalyse. Kotlin/Compose, minSdk 33 (bleibt), Paket `de.kewl.boatspeedy`, MIT.
- Repo öffentlich: `Glenn-Dandy/BoatSpeedy`, lokal `~/claude/BoatSpeedy`.
- Kachelserver: Repo `Glenn-Dandy/boatspeedy-mapdata` (lokal `~/claude/boatspeedy-mapdata`,
  Commits direkt auf main). Läuft als Kopie ohne `.git` auf `boatspeedy@192.168.10.20`.
  Seit 15.10.2026 räumt ein vollständiger Lauf seine Zwischenstände selbst weg, der
  nächste ist von sich aus frisch. `FRESH=1` nur noch, um einen abgebrochenen Lauf von
  vorn zu beginnen. Geofabrik liefert einmal am Tag (ca. 20 Uhr UTC), OSM-Änderungen
  vom selben Tag fehlen also noch. Kein Cron auf dem Server.
- Website: privates Repo `Glenn-Dandy/boatspeedy-web`, nach jedem Release anpassen.
- Keystore `~/BoatSpeedy-release.jks`, Zugangsdaten in `keystore.properties`
  (gitignoriert) und als GitHub-Secrets. Nie committen.
- Lokal bauen:
  `export JAVA_HOME=/home/florian/jdk17 ANDROID_HOME=/home/florian/android-sdk;
  ./gradlew --no-daemon -q -Dkotlin.compiler.execution.strategy=in-process
  -Dorg.gradle.jvmargs=-Xmx5g testDebugUnitTest assembleDebug lintDebug`
- `gradle.properties` nicht ändern (wurde abgelehnt).

## Ablauf

- Push auf `dev` baut über die CI den „Latest dev build" (Pre-Release `dev-build`).
  Hängt der Upload, `gh run rerun`.
- Release nur auf ausdrücklichen Wunsch: PR `dev` → `main`, Tag `vX.Y.Z`, die CI baut und
  signiert die APK. Danach Release-Notes DE/EN, `CHANGELOG.md`, fastlane-Changelogs,
  README-Badge, dev-notes leeren, `dev` auf die nächste Version öffnen.
- Versionen: auf `dev` versionCode N, das Release bekommt N+1.
- Commits mit `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Texte kurz halten, keine Gedankenstriche. Bei Texten erst Beispiele zeigen.

## Erledigt

- **1.5.2** (versionCode 53, 25.10.2026): Regenradar neu (fester Vorhersagelauf, Zoom 8,
  ohne GPS Deutschland, Ladebalken, runde Umrisse), Kanu umträgt `canoe=no`-Stücke wie ein
  Wehr (Zeiss-Wehr Saalfeld), wozise.de-Link auf der Über-Seite.
- **1.5.1** (versionCode 51, 13.10.2026): Lachen und Mühlgräben (`lache`, `leat`,
  `mill race` im Namen) gelten als gesperrt, außer `boat`/`canoe`/`motorboat` erlauben
  sie ausdrücklich.
- **1.5.0** (versionCode 49, 30.09.2026): Routen speichern, teilen (GPX mit `<rte>`),
  importieren und nachfahren; Mehrfachauswahl bei Routen; große Karte in der
  gespeicherten Route; Strom und Leistung im Track; Wehrzählung (nur umtragene oder
  nicht umfahrbare Wehre); Werkkanäle am Kraftwerk fürs Kanu stark verteuert;
  `canoe=egress`, `flowline` und `link` erkannt; erfundene Uferbrücken ×30 und nie
  über ein Wehr; Hinweis zur Navigation unter „Über → Daten, Karten & Navigation";
  Symbole wieder über der Route. F-Droid hat 1.5.0 aufgenommen.
- Ältere Versionen siehe `CHANGELOG.md`.
- `dev` steht nach dem Release noch auf **1.5.2 / versionCode 53** (auf Wunsch nicht
  weitergezählt). Vor der nächsten Änderung auf 1.5.3 / 54 öffnen, sonst lehnt Android die
  Dev-Builds als Downgrade bzw. gleiche Version ab.

## Wichtige Entscheidungen

- Kanu: `boat=no` allein ist Aufschlag ×3, keine Sperre. Motorboot: `boat=no` sperrt.
- Kanu: Ein Flussstück mit `canoe=no` oder `access=no` zählt beim Umtragen wie ein Wehr,
  zwei Anleger daneben werden verbunden (seit 1.5.2-dev, Zeiss-Wehr Saalfeld).
- „Kanu nur bei `canoe=yes`" ist verworfen: die meisten Wasserwege tragen kein Merkmal.
- Wehr: weicher Aufschlag 100 km; Kraftwerk kreuzen 1000 km; Umtragewege ×8.
- Werkkanal-Aufschlag gilt unabhängig von Tags am Kanal (ob `canoe=yes` ihn aufheben soll,
  ist offen).
- Play Store zurückgestellt, erst F-Droid.
- F-Droid-MRs macht Florian normalerweise nicht selbst; Dateien nur auf Wunsch vorbereiten.

## Offen

- **F-Droid Anti-Feature:** TetheredNet stimmt seit 1.5.0 nicht mehr (Kachelserver
  einstellbar). MR-Text und YAML waren vorbereitet, lagen aber in einem temporären
  Ordner und sind weg. Bei Bedarf aus dem aktuellen fdroiddata neu erstellen.
- Werkkanal: soll `canoe=yes` am Kanal den Aufschlag aufheben? Nicht entschieden.
- Einmaliger Navigationshinweis bei der ersten Route: angeboten, nicht beauftragt.
- Karten-Ideen (vorgemerkt 08.09., noch nicht gebaut): Fahrzeugschalter oben links,
  gesperrte Abschnitte in Rot, Absage mit Grund und „Als Kanu rechnen", Schleusen als
  Wege mit Öffnungszeiten antippbar. `nav_err_far` sagt 400 km, Code erlaubt 600 km.
- Akku: Daly/JK an echter Hardware prüfen, Redodo-Stromvorzeichen unter Last,
  Reihe/Parallel mit echtem Mehr-Akku-Aufbau.
- Website: Radar- und Akku-Screenshots veraltet; Impressum/Datenschutz fehlen noch.
- `TODO.md` ist seit Juli nicht gepflegt (`.aab` für Play Store usw.).
