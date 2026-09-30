package de.kewl.boatspeedy.trip

/**
 * Die Punkte einer laufenden Fahrt, mit Obergrenze, aber ohne Ende.
 *
 * Vorher hörte die Aufzeichnung beim zehntausendsten Punkt einfach auf: Zeit und Strecke
 * zählten weiter, der Track endete mitten in der Fahrt, und nichts wies darauf hin. Jetzt
 * wird bei Erreichen der Grenze jeder zweite Punkt verworfen, und von da an nur noch
 * jeder zweite Fix aufgenommen. Der Track wird gröber, bleibt aber vollständig, und der
 * Speicher bleibt begrenzt, egal wie lange gefahren wird.
 */
internal class TrackBuffer(private val max: Int) {
    private val punkte = ArrayList<TrackPoint>()

    /** Jeder wievielte Fix aufgenommen wird; verdoppelt sich bei jedem Ausdünnen. */
    private var schritt = 1
    private var zaehler = 0

    val size: Int get() = punkte.size

    fun clear() {
        punkte.clear()
        schritt = 1
        zaehler = 0
    }

    /** Nimmt den Punkt auf, oder lässt ihn aus, solange ausgedünnt wird. `true` = aufgenommen. */
    fun add(p: TrackPoint): Boolean {
        zaehler++
        if (zaehler % schritt != 0) return false
        punkte.add(p)
        if (punkte.size >= max) {
            // Jeden zweiten behalten, den letzten immer: Er ist die aktuelle Position.
            val behalten = punkte.filterIndexed { i, _ -> i % 2 == 0 || i == punkte.lastIndex }
            punkte.clear()
            punkte.addAll(behalten)
            schritt *= 2
        }
        return true
    }

    fun toList(): List<TrackPoint> = punkte.toList()
}
