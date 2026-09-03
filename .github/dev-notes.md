## What's new since 1.3.4

Only net changes are listed. Anything tried and taken back again during the dev
cycle is not a change and does not appear here.

### Added

- **Seamarks can be shown on the live map** — buoys, beacons and notice marks, from
  OpenSeaMap's ready-made transparent tile layer rather than symbols of our own. It is on
  by default and switches off under Settings → Navigation. It needs a connection, and the
  tiles are only fetched while it is on. The layer also brings the notice boards along,
  including the speed signs at Saalburg on the Bleiloch reservoir, which are mapped as
  seamarks. Tiles are CC-BY-SA and the data ODbL, so the attribution and OpenSeaMap's own
  warning — that this is no substitute for official charts — are shown in the settings.
  It is a picture layer, so the marks cannot be tapped for detail. The weather map leaves
  it off; that view is for the rain radar.

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

  A straight line is tied to the boat: it starts at the arrow, and the distance shrinks as
  you close in and grows if you turn away. A route stays drawn as it was computed — only
  the distance changes, counting what is left of it from where you are.

  The destination belongs to the trip, not to the screen: it survives switching between
  dashboard and map, shows up on the small map tile too, and stays until you remove it or
  come within ten metres of it — close enough that GPS accuracy, which is three to ten
  metres in practice, does not leave the route standing while you are already there. It
  says "destination reached" when it goes, so its disappearance is never a mystery.

- **A Weather entry in the menu** showing the map with the rain radar already running, and
  in its title bar what the nearest DWD station measures right now: temperature, a symbol
  for the sky, and wind with gusts and the direction it blows from. It is a measurement
  from a station some kilometres away, not a forecast for the spot you float on. The
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

  Ways that may not be used are left out of the route: `boat=no`, private water, and
  culverts — a pipe under a road is tagged as a canal but is not one. Weirs, dams and
  no-entry signs cut the network, so a route cannot lead through them. In the test area
  that removes 96 of 307 ways and 23 crossings; without it, routes went round locks
  through culverts they had no business in.

  Which further bans apply depends on the **craft** set under Settings → Navigation. A
  motorboat is kept off `motorboat=no` and `ship=no`; a canoe off `canoe=no`, and it may
  use streams as well as rivers, canals and fairways. Those are different sets of ways —
  45 against 26 in the test area — so applying both would take from each craft water that
  is expressly open to it.

  A route also reports the **locks and weirs** near it, fetched in the same request that
  builds it, in their own box at the bottom left. They are counted separately and worded
  differently on purpose: since weirs now cut the network, a weir can no longer lie on the
  route at all — it stands beside it, usually right next to the lock you actually pass
  through. Reporting that as an obstacle in the way, in red, next to the kilometres, said
  the opposite of what was happening. Around Leer alone the map knows 57 lock gates and
  44 weirs, many of them named — but it reports what the map knows, and a weir missing
  from the data cannot be reported at all.

  It is a suggestion, not a fairway: the map data knows nothing about depth, clearance or
  locks, and how complete it is varies from place to place.
