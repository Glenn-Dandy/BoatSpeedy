## What's new since 1.3.4

Only net changes are listed. Anything tried and taken back again during the dev
cycle is not a change and does not appear here.

### Added

- **Coulometers are supported** — the Hall-sensor kind that reports as `WBMS` over a CH9141 radio.
  It reports state of charge, remaining amp hours, voltage, current and the energy it has
  counted — but no cell voltages, no temperature and no capacity of its own,
  and it sends by itself every few seconds rather than answering questions. Its three
  settings — zero the current, mark the battery full, clear the counted energy — sit in
  the battery card and ask before they are sent, since none of them can be undone.
- **The scan no longer hides devices that advertise no service.** It used to filter on the
  service UUIDs of the known BMS, and a device that advertises none — the meter shows its
  services only after connecting — could never appear. Scanning is unfiltered now: known
  devices stay at the top of the list, everything else with a name sits behind "show other
  devices". Unnamed devices, mostly phones nearby, are left out.

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

  The position marker is an arrow that turns the way you are travelling, on the dashboard
  tile as well as the full map, and the destination is a flag rather than another pin.

  The line is tied to the boat, not to where you set out from: it starts at the arrow and
  the distance follows you, shrinking as you close in and growing if you turn away. On a
  route the part already behind you drops off, so what is drawn is what is left.

  The destination belongs to the trip, not to the screen: it survives switching between
  dashboard and map, shows up on the small map tile too, and stays until you remove it or
  come within ten metres of it — close enough that GPS accuracy, which is three to ten
  metres in practice, does not leave the route standing while you are already there. It
  says "destination reached" when it goes, so its disappearance is never a mystery.

- **A Weather entry in the menu** showing the map with the rain radar already running. The
  radar moved out of the live map, which is for getting somewhere.
- **The map glides instead of jumping** while under way. Below walking pace it still snaps:
  GPS wanders a few metres when you are stopped, and animating that looks like drifting.
- **A course arrow.** The position marker turns the way you are travelling, and next to
  the destination an arrow shows how far to turn to head straight at it — upright means the
  course is right, to the right means starboard. It is the difference from your course, not
  a compass direction, so there is no map to read while steering. Standing still, GPS has no
  course to give: the arrow keeps its last direction and greys out rather than spinning.

  On the dashboard the destination gets its own line under the speed, showing the arrow,
  the distance and the estimated amp hours. It appears only while a destination is set.

  A route also reports the **locks and weirs** it passes, fetched in the same request that
  builds it. Locks cost time; a weir usually means the way is closed, so it is called out
  in red. Around Leer alone the map knows 57 lock gates and 44 weirs, many of them named,
  so this is not a theoretical feature — but it reports what the map knows, and a weir
  missing from the data cannot be reported at all.

  It is a suggestion, not a fairway: the map data knows nothing about depth, clearance or
  locks, and how complete it is varies from place to place.
