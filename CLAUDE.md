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

## Bauen und Veröffentlichen

- Entwicklung auf `dev`, `main` nur über PR und Tag.
- **Nur DEV-Builds**, solange nicht ausdrücklich „build haupt" gesagt wurde.
- `versionCode`/`versionName` sind manuelle Literale in `app/build.gradle.kts`
  (F-Droid liest den versionName statisch aus).
- Release-APK immer aus einem frischen Clone des Tags, von Gradle signiert.
