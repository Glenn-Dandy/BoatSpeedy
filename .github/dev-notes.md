## What's new since 1.3.4

Net changes only. Anything tried and taken back during the dev cycle is not listed.

### Navigation

- **Set a destination** by long-pressing the map: straight line, or a route along the
  waterways. Shows the distance and, once the trip knows your consumption, the amp hours
  it will take. The destination survives switching screens, appears on the dashboard map
  too, and clears at ten metres with "destination reached".
- **A course arrow** next to the destination shows how far to turn to head straight at it —
  the difference from your course, not a compass bearing. Greyed out while standing still.
  The position marker turns with your course; the destination is a flag.
- **Routes avoid what the map forbids** — `boat=no`, private water, culverts, and anything
  cut off by a weir, dam or no-entry sign. **Locks and weirs** near the route are listed
  bottom left, counted separately.
- **Settings → Navigation**: pick your **craft** (motorboat or canoe — it decides which
  bans apply and whether streams count), and switch **seamarks** on or off. Seamarks come
  from OpenSeaMap (tiles CC-BY-SA, data ODbL) and are on by default.

  A route is a suggestion, not a fairway: the data knows nothing about depth or clearance,
  and OpenSeaMap is no substitute for official charts.

- **The map glides** instead of jumping while under way, and snaps below walking pace.

### Weather

- **A Weather entry in the menu** — the map with the rain radar running, and in the title
  what the nearest DWD station measures now: temperature, sky symbol, wind and gusts.
  That is a measurement some kilometres away, not a forecast for where you float.

### Battery

- **Coulometers are supported** (the Hall-sensor kind reporting as `WBMS`). State of
  charge, remaining amp hours, voltage, current and counted energy — no cell voltages,
  no temperature. Its three settings ask before they are sent, since none can be undone.
- **The scan no longer hides devices that advertise no service.** Known devices stay on
  top, everything else with a name sits behind "show other devices".
