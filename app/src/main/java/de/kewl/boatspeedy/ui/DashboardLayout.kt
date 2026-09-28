package de.kewl.boatspeedy.ui

/**
 * Die verschiebbaren Kacheln des Dashboards. Die Geschwindigkeit gehört nicht dazu: Sie
 * steht fest oben und lässt sich weder verschieben noch ausblenden.
 */
enum class DashboardTile(val key: String) {
    RANGE("range"),
    BATTERY("battery"),
    MAP("map"),
    TRIP("trip"),
}

/** Reihenfolge ab Werk, so wie die Kacheln vor dem Anordnen standen. */
val STANDARD_REIHENFOLGE = listOf(
    DashboardTile.RANGE,
    DashboardTile.BATTERY,
    DashboardTile.MAP,
    DashboardTile.TRIP,
)

/**
 * Liest die gespeicherte Reihenfolge. Unbekanntes wird übergangen, Doppeltes nur einmal
 * gezählt, und eine Kachel, die es beim Speichern noch nicht gab, wird an ihrer Stelle
 * aus der Standardreihenfolge eingefügt. Eine neue Kachel verschwindet also nicht, nur
 * weil jemand vorher schon angeordnet hatte.
 */
fun reihenfolgeAus(gespeichert: String): List<DashboardTile> {
    val gelesen = gespeichert.split(',')
        .mapNotNull { k -> DashboardTile.entries.firstOrNull { it.key == k.trim() } }
        .distinct()
        .toMutableList()
    for ((i, fehlend) in STANDARD_REIHENFOLGE.withIndex()) {
        if (fehlend !in gelesen) gelesen.add(minOf(i, gelesen.size), fehlend)
    }
    return gelesen
}

/** Die Reihenfolge zum Speichern. */
fun reihenfolgeText(reihe: List<DashboardTile>): String = reihe.joinToString(",") { it.key }

/** Verschiebt die Kachel an Stelle [von] nach [nach]; die übrigen rücken auf. */
fun verschoben(reihe: List<DashboardTile>, von: Int, nach: Int): List<DashboardTile> {
    if (von == nach || von !in reihe.indices || nach !in reihe.indices) return reihe
    val neu = reihe.toMutableList()
    neu.add(nach, neu.removeAt(von))
    return neu
}

/**
 * Höhen der Kartenkachel in dp, von klein bis sehr groß. Gezogen wird stufenlos, beim
 * Loslassen rastet sie auf der nächsten Stufe ein: Frei auf den Pixel gezogen sähe es nur
 * auf dem Handy richtig aus, auf dem es eingestellt wurde.
 */
val KARTEN_HOEHEN_DP = listOf(140, 220, 320, 440)

/** Die Stufe, deren Höhe [hoeheDp] am nächsten liegt. */
fun naechsteKartenStufe(hoeheDp: Float): Int =
    KARTEN_HOEHEN_DP.indices.minBy { kotlin.math.abs(KARTEN_HOEHEN_DP[it] - hoeheDp) }
