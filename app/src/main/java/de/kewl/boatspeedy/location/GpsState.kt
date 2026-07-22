package de.kewl.boatspeedy.location

/** Momentaufnahme des GPS-Empfangs. */
data class GpsState(
    val speedMs: Float? = null,        // rohe Geschwindigkeit in m/s, null = kein Fix
    val accuracyM: Float? = null,      // horizontale Genauigkeit in Metern
    val latitude: Double? = null,      // Position (für Distanzberechnung)
    val longitude: Double? = null,
    val satellitesUsed: Int = 0,       // im Fix verwendete Satelliten
    val satellitesVisible: Int = 0,    // sichtbare Satelliten
    val hasFix: Boolean = false,
)
