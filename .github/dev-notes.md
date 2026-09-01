## What's new since 1.3.4

Only net changes are listed. Anything tried and taken back again during the dev
cycle is not a change and does not appear here.

### Added

- **Set a destination by long-pressing the map.** You are then asked how it should be
  measured: as a **straight line**, or as a **route along the waterways**. Either way the
  bar at the top shows the distance in kilometres and, once the current trip has enough
  data to know your consumption, roughly how many amp hours it will take to get there.
  The straight line needs nothing but GPS and works without a signal.

  The route is computed from OpenStreetMap waterways (same data as the map, ODbL). Only
  navigable water is used — rivers, canals and fairways; ditches and drains are numerous
  in the data, barely connected and no use to a boat. If the spot you picked has no
  waterway near it, if there is no continuous connection, or if there is no signal, it
  says so plainly and offers the straight line instead.

  A route is a mixture: open water from where you are to the fairway, along the fairway,
  then open water to the spot you picked. The map draws the two apart — dashed where you
  navigate yourself, solid along the waterway.

  The destination belongs to the trip, not to the screen: it survives switching between
  dashboard and map, shows up on the small map tile too, and stays until you remove it or
  come within ten metres of it — close enough that GPS accuracy, which is three to ten
  metres in practice, does not leave the route standing while you are already there. It
  says "destination reached" when it goes, so its disappearance is never a mystery.

  It is a suggestion, not a fairway: the map data knows nothing about depth, clearance or
  locks, and how complete it is varies from place to place.
