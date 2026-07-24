# osmdroid: R8/Minify darf die Tile-Provider/-Cache-Klassen nicht entfernen,
# sonst laden im Release-Build keine Kartenkacheln (nur Karo-Muster).
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
