## Seit 1.4.3

**Neu**
- Dashboard anordnen: eine Kachel drei Sekunden ruhig halten, dann verschieben, ausblenden
  und die Karte in der Größe ziehen. Ausgeblendetes steht grau unter einer Linie und lässt
  sich dort zurückholen. Das runde Häkchen unten beendet es.
- Fahrt und GPS sind eigene Kacheln wie Batterie und Reichweite.
- Anordnung zurücksetzen unter Einstellungen, Dashboard.
- Auf der Karte zeigt ein Motorboot oder ein Kanu die eigene Position, je nach gewähltem
  Fahrzeug. Unter Einstellungen, Navigation lässt sich wieder der Pfeil wählen.
- Navigation im Hauptmenü, direkt unter Dashboard; die Live-Karte heißt jetzt so.
- Kartenserver wählbar unter Navigation, Kartendaten. Standard bleibt der
  BoatSpeedy-Server; übernommen wird eine Adresse nur, wenn dort eine index.json liegt.
- Routen speichern: In der Navigation merkt sich das Lesezeichen an der Routenanzeige die
  gerechnete Route, auf Wunsch mit Namen. Unter Fahrten, Reiter Routen, steht sie mit
  Karte, Länge, Schleusen und Fahrzeug. So wie gespeichert wird sie gezeigt; Neu berechnen
  holt den aktuellen Stand der Kartendaten.
- Nachfahren: Navigieren bei einer Route oder einer aufgezeichneten Fahrt legt sie in die
  Navigation. Eine gestrichelte Luftlinie führt vom Boot zum Start und verschwindet dort,
  danach geht es der Strecke nach. Auch Rundfahrten, die am Start enden.
- Fahrten und Routen lassen sich umbenennen, etwa „Alter zur Linkenmühle". Der Name steht
  groß, Datum und Uhrzeit klein darunter. Der GPX-Export nimmt ihn mit, der Import
  übernimmt ihn.
- Routen lassen sich wie Fahrten markieren, teilen und löschen. Geteilt wird als GPX mit
  der Strecke als Route und Schleusen und Wehren als Wegpunkte. Eine solche Datei landet
  beim Import wieder unter Routen, ebenso eine Route aus anderen Programmen.
- Der Track zeichnet jetzt auch Strom und Leistung auf, wenn eine Batterie verbunden ist.
  Antippen des Tracks zeigt sie mit an, der GPX-Export nimmt sie mit.

**Geändert**
- Die App bleibt im Hochformat und dreht sich nicht mehr ins Querformat.
- Die Routenberechnung ist etwa doppelt so schnell. Kahla nach Lübeck braucht auf dem
  Rechner 6,5 statt 11,9 Sekunden, auf dem Handy entsprechend.

**Behoben**
- Schleusen- und Brückensymbole lagen unter der Route oder dem Track. Sie bleiben jetzt obenauf.
- Im Kanu nahm die Route bei Dorndorf den Kanal zum Wasserkraftwerk, statt auf der Saale zu
  bleiben und am Wehr umzutragen. Ein Kanal, der an einem Kraftwerk vorbeiführt, ist jetzt
  nur noch Notlösung.
- Ausstiege, die als canoe=egress eingetragen sind, wurden nicht erkannt und fehlten auch in
  den Kartendaten, nur whitewater=egress kam an. Braucht die neuen Kacheln.
- Im Kanu nahm die Route am Wehr oft eine gerade Linie von Anleger zu Anleger über das Wehr,
  statt den eingetragenen Umtrageweg, etwa am Burgauer Wehr in Jena. Eingetragene Wege gehen
  jetzt vor.
- Die Zahl der Wehre auf der Route stimmt: Jedes Wehr zählt einmal, wenn man durch muss oder
  daran vorbeiträgt, aber nicht, wenn die Route nur daneben vorbeiführt. Wehre, die in OSM
  aus mehreren Teilen bestehen, zählten vorher doppelt.
- Routen durch Seen brachen ab, etwa an der Müritz am Ende des Junkerkanals. Der Weg durch
  einen See steht in OSM oft als flowline, und die fehlte in den Kartendaten. Braucht die
  neuen Kacheln.
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
- A motorboat or a canoe marks your position on the map, depending on the chosen craft.
  Settings, Navigation switches back to the arrow.
- Navigation in the main menu, right under Dashboard; that is the live map's new name.
- Choose the map server under Navigation, Map data. The BoatSpeedy server stays the
  default; an address is only accepted if an index.json is found there.
- Save routes: in Navigation, the bookmark on the route display keeps the planned route,
  optionally with a name. Trips, tab Routes, lists it with map, length, locks and craft.
  It is shown as saved; Recalculate uses the current map data.
- Follow: Navigate on a route or a recorded trip puts it into Navigation. A dashed line
  leads from the boat to the start and disappears there, then you follow the track. Round
  trips that end at the start work too.
- Trips and routes can be renamed, for example "Harbour to the mill". The name is shown
  large with date and time small below. GPX export carries it and import picks it up.
- Routes can be selected, shared and deleted like trips. They are shared as GPX with the
  course as a route and locks and weirs as waypoints. Importing such a file puts it back
  under Routes, and so does a route from other apps.
- The track now also records current and power while a battery is connected. Tapping the
  track shows them, and GPX export carries them.

**Changed**
- The app stays in portrait and no longer turns to landscape.
- Route calculation is about twice as fast. Kahla to Lübeck takes 6.5 instead of 11.9
  seconds on a desktop, and the phone gains accordingly.

**Fixed**
- Lock and bridge symbols ended up underneath the route or the track. They now stay on top.
- By canoe, the route at Dorndorf took the canal to the hydro power plant instead of staying
  on the Saale and portaging at the weir. A canal passing a power plant is now only a last
  resort.
- Egress points tagged canoe=egress were not recognised and were missing from the map data;
  only whitewater=egress came through. Needs the new tiles.
- By canoe, the route often took a straight line from landing to landing across the weir
  instead of the mapped portage path, for example at the Burgau weir in Jena. Mapped paths
  now come first.
- The number of weirs on the route is right: each weir counts once if you have to pass
  through it or portage around it, but not if the route only passes nearby. Weirs mapped
  in several parts in OSM used to count twice.
- Routes through lakes broke off, for example at the Müritz at the end of the Junkerkanal.
  OSM often maps the course through a lake as a flowline, which was missing from the map
  data. Needs the new tiles.
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
