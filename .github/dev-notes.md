## Seit 1.4.3

**Neu**
- Dashboard anordnen: eine Kachel drei Sekunden ruhig halten, dann verschieben, ausblenden
  und die Karte in der Größe ziehen. Ausgeblendetes steht grau unter einer Linie und lässt
  sich dort zurückholen. Das runde Häkchen unten beendet es.
- Fahrt und GPS sind eigene Kacheln wie Batterie und Reichweite.
- Anordnung zurücksetzen unter Einstellungen, Dashboard.
- Navigation im Hauptmenü, direkt unter Dashboard; die Live-Karte heißt jetzt so.
- Kartenserver wählbar unter Navigation, Kartendaten. Standard bleibt der
  BoatSpeedy-Server; übernommen wird eine Adresse nur, wenn dort eine index.json liegt.

**Geändert**
- Die App bleibt im Hochformat und dreht sich nicht mehr ins Querformat.
- Die Routenberechnung ist etwa doppelt so schnell. Kahla nach Lübeck braucht auf dem
  Rechner 6,5 statt 11,9 Sekunden, auf dem Handy entsprechend.

**Behoben**
- Ohne Fix, etwa drinnen, stand oft eine alte Geschwindigkeit fest auf dem Tacho. Sie kam
  vom letzten bekannten Standort des Handys, teils Stunden alt. Jetzt steht dort "--".
- Der Track hörte nach 10.000 Punkten mitten in der Fahrt auf, Zeit und Strecke liefen
  weiter. Die Grenze liegt jetzt bei 50.000 Punkten, knapp 14 Stunden am Stück; darüber
  wird ausgedünnt statt aufgehört.
- Jede Position wurde doppelt aufgezeichnet, sobald sich Batterie oder Satelliten meldeten.
  Das halbiert Punkte und Dateigröße und verdoppelt die Zeit bis zum Ausdünnen.
- Nach Export und Import einer Fahrt fehlten Energie und Effizienz. Der Export schrieb die
  Energie nicht mit; ältere Exporte bringen sie deshalb auch künftig nicht zurück.
- Der Standortdialog fragt genauen und groben Standort zusammen an, wie Android es seit
  Version 12 verlangt. „Ungefähr" wird damit richtig erkannt.

## Since 1.4.3

**New**
- Arrange the dashboard: hold a tile still for three seconds, then move it, hide it and
  resize the map. Hidden tiles sit greyed out below a line and can be brought back there.
  The round tick at the bottom finishes.
- Trip and GPS are tiles of their own, like battery and range.
- Reset the arrangement under Settings, Dashboard.
- Navigation in the main menu, right under Dashboard; that is the live map's new name.
- Choose the map server under Navigation, Map data. The BoatSpeedy server stays the
  default; an address is only accepted if an index.json is found there.

**Changed**
- The app stays in portrait and no longer turns to landscape.
- Route calculation is about twice as fast. Kahla to Lübeck takes 6.5 instead of 11.9
  seconds on a desktop, and the phone gains accordingly.

**Fixed**
- Without a fix, indoors for instance, an old speed often stuck on the speedometer. It came
  from the phone's last known location, sometimes hours old. It now shows "--".
- The track stopped after 10,000 points in the middle of a trip while time and distance
  went on. The limit is now 50,000 points, almost 14 hours in one go; beyond that the
  track gets thinned out instead of stopping.
- Every position was recorded twice whenever the battery or the satellites reported.
  This halves points and file size and doubles the time before thinning starts.
- After exporting and importing a trip, energy and efficiency were missing. The export
  did not write the energy, so older exports still cannot bring it back.
- The location dialog asks for precise and approximate location together, as Android
  requires since version 12. "Approximate" is now recognised properly.
