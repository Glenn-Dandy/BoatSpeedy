## Seit 1.4.2

**Neu**
- Umtragen: Im Kanu führt die Route ums Wehr herum, über die Umtragewege aus
  OpenStreetMap oder, wo es keine gibt, von Anleger zu Anleger. Sie läuft am Ufer entlang
  und quert weder Fluss noch Wehr. Grün gestrichelt, mit Meterangabe im Kasten.
- Ein- und Ausstiege am Ufer (Slipanlagen, Kanu-Einstiege) ab Zoomstufe 13, antippbar.

**Geändert**
- Ein Wehr sperrt die Route nicht mehr, es kostet. Gibt es keinen Weg darum herum, führt
  die Strecke hindurch und meldet das Wehr, statt vorher abzubrechen.
- Das Ziel hängt sich an den günstigsten Punkt in der Nähe, nicht an den nächsten. Sonst
  fuhr die Route durchs Wehr, um einen Anleger fünfzehn Meter jenseits zu erreichen.

**Behoben**
- Routen, die oberhalb eines Wehrs begannen, scheiterten ganz. Kahla nach Lübeck geht
  wieder: 581 km, davon 646 m umtragen.
- Wehre, die in OSM als Weg quer über den Fluss stehen, fehlten ganz. In Deutschland ist
  das fast jedes zweite. Kacheln auffrischen.
- Hinweise gelten jetzt für die ganze Route, auch für den Luftlinienteil hinter einem Wehr.

## Since 1.4.2

**New**
- Portage: in canoe mode the route goes around a weir, along the portage paths from
  OpenStreetMap or, where there are none, from landing to landing. It follows the bank and
  crosses neither the river nor the weir. Green dashed, with the distance in the box.
- Put-ins, egress points and slipways on the bank from zoom 13, tappable.

**Changed**
- A weir no longer blocks the route, it costs. With no way around it, the route goes
  through and reports the weir instead of stopping short.
- The destination snaps to the cheapest point nearby, not the closest one. Otherwise the
  route crossed a weir to reach a landing fifteen metres beyond it.

**Fixed**
- Routes starting above a weir failed outright. Kahla to Lübeck works again: 581 km, of
  which 646 m are carried.
- Weirs mapped as a way across the river were missing entirely, which is nearly half of
  them in Germany. Refresh the map tiles.
- Warnings now cover the whole route, including the straight-line part beyond a weir.
