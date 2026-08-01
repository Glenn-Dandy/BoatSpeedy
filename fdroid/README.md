# Publishing BoatSpeedy on F-Droid

BoatSpeedy is FOSS (MIT), uses only free dependencies (AndroidX/Compose, osmdroid)
and free network services (OpenStreetMap, Deutscher Wetterdienst / Bright Sky), and
since **v1.2.1** ships **without Google Play Services** (GPS via AOSP `LocationManager`).

## Steps

1. **Release the GMS-free version** first: tag `v1.2.1` on `main` (the F-Droid recipe
   points at it). Tags `<= v1.2.0` still bundle Play Services and won't build on F-Droid.
2. **Store listing** is provided via Fastlane metadata in this repo:
   `fastlane/metadata/android/{en-US,de-DE}/` (title, short/full description,
   `changelogs/<versionCode>.txt`, `images/phoneScreenshots/`). F-Droid pulls these
   automatically.
3. **Submit the recipe** [`de.kewl.boatspeedy.yml`](de.kewl.boatspeedy.yml) as a merge
   request to <https://gitlab.com/fdroid/fdroiddata> under
   `metadata/de.kewl.boatspeedy.yml` (optionally file a "Request For Packaging" issue
   first). `UpdateCheckMode: Tags` + `AutoUpdateMode: Version v%v` make each new
   `vX.Y.Z` tag build automatically.

## Notes

- **Signing:** F-Droid signs with its own key, so the F-Droid APK has a different
  signature than the GitHub-release APK. Switching between them requires an uninstall.
- **No anti-features expected:** no non-free deps, no tracking; network use is limited to
  free/open services.
