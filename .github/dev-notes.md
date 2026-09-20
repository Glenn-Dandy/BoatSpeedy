## Seit 1.4.2

**Neu**
- Umtragen: Im Kanu führt die Route ums Wehr herum, über die Umtragewege aus
  OpenStreetMap oder, wo es keine gibt, von Anleger zu Anleger. Sie läuft am Ufer entlang
  und quert weder Fluss noch Wehr. Grün gestrichelt, mit Meterangabe im Kasten.
- Ein- und Ausstiege am Ufer (Slipanlagen, Kanu-Einstiege) ab Zoomstufe 13, antippbar.
- Wasserkraftanlagen: eigenes Symbol auf der Karte, und die Route fährt nicht mehr durch
  die Turbinen. Der Kanal durch die Anlage führt oft am Wehr vorbei und war der kürzeste
  Weg.

**Geändert**
- Ein Wehr sperrt die Route nicht mehr, es kostet. Gibt es keinen Weg darum herum, führt
  die Strecke hindurch und meldet das Wehr, statt vorher abzubrechen.
- Das Ziel hängt sich an den günstigsten Punkt in der Nähe, nicht an den nächsten. Sonst
  fuhr die Route durchs Wehr, um einen Anleger fünfzehn Meter jenseits zu erreichen.

**Geändert**
- Der Kasten zählt nur noch Wehre, die die Strecke wirklich kreuzt. Was umtragen wird,
  steht nicht mehr als Hindernis darin.
- Im Kanu kostet ein Kanal mehr als ein Fluss. Mühlgräben und Seitenarme wie die Lache
  bei Porstendorf sind kürzer als die Flussschleife daneben; im Kanu zählt der Fluss.

**Behoben**
- Ein Wehr, das keinen Punkt mit dem Fluss teilt, sperrte gar nichts. Am Burgauer Wehr in
  Jena fuhr die Strecke mitten hindurch, obwohl daneben umtragen wird.
- Routen, die oberhalb eines Wehrs begannen, scheiterten ganz. Kahla nach Lübeck geht
  wieder: 581 km, davon 646 m umtragen.
- Wehre, die in OSM als Weg quer über den Fluss stehen, fehlten ganz. In Deutschland ist
  das fast jedes zweite. Kacheln auffrischen.
- Hinweise gelten jetzt für die ganze Route, auch für den Luftlinienteil hinter einem Wehr.
- Am Wehr wurde oft nicht umtragen, obwohl Aus- und Einstieg eingetragen sind. Zwei
  Fehler: Der Anschluss ans Ufer galt als Queren des Flusses, und beim Anschluss geteilte
  Flussstücke kosteten keinen Aufschlag mehr, womit sich jedes Wehr umgehen ließ.

## Since 1.4.2

**New**
- Portage: in canoe mode the route goes around a weir, along the portage paths from
  OpenStreetMap or, where there are none, from landing to landing. It follows the bank and
  crosses neither the river nor the weir. Green dashed, with the distance in the box.
- Put-ins, egress points and slipways on the bank from zoom 13, tappable.
- Hydro power plants get their own symbol on the map, and the route no longer runs through
  the turbines. The channel through the plant often bypasses the weir and used to win.

**Changed**
- A weir no longer blocks the route, it costs. With no way around it, the route goes
  through and reports the weir instead of stopping short.
- The destination snaps to the cheapest point nearby, not the closest one. Otherwise the
  route crossed a weir to reach a landing fifteen metres beyond it.

**Changed**
- The box counts only weirs the route actually crosses. What you carry around is no longer
  listed as an obstacle.
- In canoe mode a canal costs more than a river. Mill races and side arms are shorter than
  the river loop beside them; in a canoe the river is what you want.

**Fixed**
- A weir sharing no node with the river blocked nothing. At the Burgauer Wehr in Jena the
  route went straight through although there is a portage beside it.
- Routes starting above a weir failed outright. Kahla to Lübeck works again: 581 km, of
  which 646 m are carried.
- Weirs mapped as a way across the river were missing entirely, which is nearly half of
  them in Germany. Refresh the map tiles.
- Warnings now cover the whole route, including the straight-line part beyond a weir.
- Portage was often skipped although put-in and egress are mapped. Two causes: the link to
  the bank counted as crossing the river, and river pieces split by that link lost their
  surcharge, which let any weir be bypassed.
