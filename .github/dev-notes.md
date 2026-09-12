## Seit 1.4.0

**Neu**
- Entwicklereinstellungen: Schalter „Entwicklerversionen anbieten". Die Update-Prüfung
  berücksichtigt dann auch den Entwicklungsbau, sonst nur Vollversionen.

**Geändert**
- Öffnungszeiten einer Schleuse stehen untereinander. Das Semikolon trennt in
  OpenStreetMap die Regeln, das Komma die Tage innerhalb einer Regel.

**Behoben**
- Schleusentore fingen den Tipp ab und zeigten nur „gate" statt der Schleusenauskunft.
- Zeiten unter `service_times` wurden nicht gelesen, nur `opening_hours`. Drei von vier
  Schleusen an der unteren Unstrut standen dadurch ohne Zeiten da. Kacheln auffrischen.
- Die Update-Prüfung meldete „aktuell", wenn ein Entwicklungsbau installiert war:
  `1.4.0-dev267` zählte als 1.4.0.

## Since 1.4.0

**New**
- Developer settings: an "offer development builds" switch. The update check then also
  considers the dev build, otherwise only full releases.

**Changed**
- A lock's opening hours are listed one rule per line. In OpenStreetMap the semicolon
  separates rules, the comma separates days within a rule.

**Fixed**
- Lock gates caught the tap and showed only "gate" instead of the lock details.
- Hours recorded under `service_times` were ignored, only `opening_hours` was read. Three
  of four locks on the lower Unstrut had no times because of it. Refresh the map tiles.
- The update check said "up to date" on a development build: `1.4.0-dev267` counted as
  1.4.0.
