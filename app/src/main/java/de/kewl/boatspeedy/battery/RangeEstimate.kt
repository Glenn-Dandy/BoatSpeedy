package de.kewl.boatspeedy.battery

/** Geschätzte Restreichweite (km) und Restzeit (Stunden). */
data class RangeEstimate(val km: Double, val hours: Double)

/**
 * Schätzt Reichweite/Zeit aus Restkapazität ÷ Entladestrom × Geschwindigkeit.
 * Fällt auf `Nennkapazität × SoC` zurück, wenn das BMS keine Restkapazität liefert.
 * Null, wenn kein Verbrauch/keine Fahrt oder keine Kapazitätsangabe vorliegt.
 */
fun estimateRange(data: BatteryData?, speedMs: Float?): RangeEstimate? {
    if (data == null) return null
    val speedKmh = (speedMs ?: 0f) * 3.6
    val dischargeA = data.dischargeA
    if (dischargeA <= 0.1f || speedKmh <= 0.1) return null
    val remaining = when {
        data.remainingAh > 0f -> data.remainingAh.toDouble()
        data.nominalAh > 0f -> data.nominalAh * data.soc / 100.0
        else -> return null
    }
    if (remaining <= 0.0) return null
    val hours = remaining / dischargeA
    return RangeEstimate(km = hours * speedKmh, hours = hours)
}
