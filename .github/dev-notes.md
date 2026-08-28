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
- **Merging trips asks first.** Trips could be merged with a single tap on the toolbar,
  and merging cannot be undone.

- The remaining charge no longer prints a reference capacity that is smaller than the
  charge itself. Redodo packs report a configured capacity below what the cells actually
  hold, which read as "111.26 Ah / 100.99 Ah" — that looks like a defect and helps
  nobody. Where the reference cannot be one, only the remaining charge is shown.
