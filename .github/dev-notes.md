## Since 1.3.4

**Navigation**
- Long-press the map to set a destination: straight line or a route along the waterways, with distance and estimated amp hours. Survives screen changes, clears at ten metres.
- A course arrow shows how far to turn to head at the destination. Greyed out while stopped.
- Routes avoid what the map forbids and cannot pass a weir. Locks and weirs near the route are listed bottom left.
- Settings → Navigation: **craft** (motorboat or canoe) decides which bans apply; **seamarks** from OpenSeaMap, on by default.
- **Speed limit signs show their value** — OpenSeaMap draws the sign but leaves it empty. Tap one for the original text.

- **The position marker glides**, the way a navigation device does: instead of interpolating towards the last fix — a place you have already passed — it computes from speed and heading where the boat is now, and eases that estimate towards each new measurement. Display only; the recorded track, distance and consumption still use the raw fixes. It freezes after three seconds without a fix, and jumps rather than sliding when a fix lands far away. Both maps do this.
- Turning off satellite details now hides the whole status line, dot and "Fix" included.

**Weather**
- A Weather entry in the menu: rain radar, plus what the nearest DWD station measures now — in its own strip under the title bar, large enough to read, with a button to get back to your position after panning around.
- The radar no longer reloads when you zoom, and no longer draws the raw grid as squares.

**Battery**
- Coulometers supported (the Hall-sensor kind reporting as `WBMS`).
- The scan no longer hides devices that advertise no service.

A route is a suggestion, not a fairway, and OpenSeaMap is no substitute for official charts.
