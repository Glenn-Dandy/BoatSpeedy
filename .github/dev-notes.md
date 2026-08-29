## What's new since 1.3.3

Only net changes are listed. Anything tried and taken back again during the dev
cycle is not a change and does not appear here.

### Added

- **Redodo, LiTime and Power Queen batteries** are supported as their own BMS type.
  Batteries named `R-…` or `LT-…` are recognised automatically — they advertise the
  same Bluetooth service as JK, so the name is what tells them apart. Verified against
  a Redodo 12 V 100 Ah: voltage, current, state of charge, cell voltages and
  temperature all match the vendor app.
- **Charge cycles and total discharge** in a new "History" section of the battery card,
  above the cells. Both BMS report the cycle count; the total discharged amp hours come
  from Redodo only, so that line is left out for other types instead of showing a dash.
- **Rated capacity** between the battery type and its MAC address, in whole amp hours.
- **BLE diagnostic** for reporting unsupported batteries. It scans without any filter,
  lists the complete GATT tree and tries every protocol the app knows on every writable
  channel, then hands you the raw exchange as a text file to attach to an issue. It is
  hidden: seven taps on the version number in About unlock Settings → Developer, and a
  button in the diagnostic hides it again.

- **The radar is drawn like the DWD's own app: round outlines, crisp colour edges.** The
  measurements are interpolated between the one-kilometre grid cells and only then
  coloured, so the shapes get their curves back — but the colouring keeps the fifteen
  steps of the official ladder instead of fading between them, which is what makes it
  readable rather than hazy. The ladder is taken from the layer's own style definition, so
  the shades are the official ones. Each frame is fetched at the resolution of the data
  now, which also cuts the download to a few kilobytes.

### Fixed

- **The rain radar appears in a second or two instead of after several loops.** A request to the DWD
  server costs two to four seconds no matter how much ground it covers — one 256-pixel
  tile takes as long as one image spanning twenty tiles. Built from tiles, a single frame
  needed about twenty requests and the whole loop over four hundred, which overflowed the
  download queue: requests were dropped, and the current frame stayed empty until the
  animation had run several times. Each frame is now fetched as one image for the visible
  area, so the loop needs 21 requests instead of 400. It also waits for the map to be
  measured and centred before the first request, and keeps the radar image unsmoothed —
  the data is a one-kilometre grid, and blurring it into a haze at high zoom hides
  whether it is raining at all.
- **Merging and deleting trips ask first.** Both happened on a single tap on the toolbar,
  and neither can be undone.
- **The radar animation runs smoothly again.** Every frame change re-did the whole
  interpolation, so dragging the time slider queued up work and stuttered. Frames are
  smoothed once at a fixed size, kept ready, and the loop reuses them; they are also
  prepared in advance while the frames are still downloading.
- **The radar stays put while panning and zooming.** The image and the ground it covers
  were set at different moments, so between a pan and the new image arriving the old
  picture was stretched onto the new area — it slid along with the finger instead of
  staying on the ground, and disappeared while zooming. They are now only ever set
  together, and the previous image simply stays until its replacement is ready.
- **The radar no longer breaks when zooming far out and back in.** Zoomed out across a
  continent the requested area ran past the edges of the map projection, so the image came
  back skewed; and once a wide area had been loaded, zooming back in kept showing that
  coarse overview stretched over a small view.

- The remaining charge no longer prints a reference capacity that is smaller than the
  charge itself. Redodo packs report a configured capacity below what the cells actually
  hold, which read as "111.26 Ah / 100.99 Ah" — that looks like a defect and helps
  nobody. Where the reference cannot be one, only the remaining charge is shown.
