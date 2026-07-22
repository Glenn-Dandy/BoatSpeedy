package de.kewl.boatspeedy.battery

/** Geschätzte Restreichweite (km) und Restzeit (Stunden). */
data class RangeEstimate(val km: Double, val hours: Double)

/**
 * Schätzt Reichweite/Zeit aus Restkapazität ÷ Entladestrom × Geschwindigkeit.
 * Fällt auf `konfigurierte Kapazität × SoC` zurück, wenn das BMS keine
 * Restkapazität liefert (z. B. Daly/JK). Null, wenn kein Verbrauch/keine Fahrt.
 */
fun estimateRange(data: BatteryData?, capacityAh: Int, speedMs: Float?): RangeEstimate? {
    if (data == null) return null
    val speedKmh = (speedMs ?: 0f) * 3.6
    val dischargeA = data.dischargeA
    if (dischargeA <= 0.1f || speedKmh <= 0.1) return null
    val remaining =
        if (data.remainingAh > 0f) data.remainingAh.toDouble()
        else capacityAh * data.soc / 100.0
    if (remaining <= 0.0) return null
    val hours = remaining / dischargeA
    return RangeEstimate(km = hours * speedKmh, hours = hours)
}
