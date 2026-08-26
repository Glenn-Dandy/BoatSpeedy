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

## Bauen und Veröffentlichen

- Entwicklung auf `dev`, `main` nur über PR und Tag.
- **Nur DEV-Builds**, solange nicht ausdrücklich „build haupt" gesagt wurde.
- `versionCode`/`versionName` sind manuelle Literale in `app/build.gradle.kts`
  (F-Droid liest den versionName statisch aus).
- Release-APK immer aus einem frischen Clone des Tags, von Gradle signiert.
