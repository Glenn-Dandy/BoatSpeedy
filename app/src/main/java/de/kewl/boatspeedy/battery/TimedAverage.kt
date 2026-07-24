package de.kewl.boatspeedy.battery

/**
 * Gleitender Mittelwert über ein Zeitfenster. Es werden Messwerte mit Zeitstempel
 * gehalten (bis [maxWindowMs]); [average] liefert den Durchschnitt über die letzten
 * `windowMs` – so lässt sich die Fenstergröße zur Laufzeit ändern, ohne neu zu puffern.
 */
class TimedAverage(private val maxWindowMs: Long = 120_000L) {

    private data class Sample(val t: Long, val v: Float)

    private val samples = ArrayDeque<Sample>()

    fun add(value: Float, now: Long) {
        samples.addLast(Sample(now, value))
        prune(now)
    }

    /** Durchschnitt über die letzten [windowMs]; null, wenn keine Werte im Fenster liegen. */
    fun average(windowMs: Long, now: Long): Float? {
        val cutoff = now - windowMs
        var sum = 0.0
        var n = 0
        for (s in samples) if (s.t >= cutoff) { sum += s.v; n++ }
        return if (n == 0) null else (sum / n).toFloat()
    }

    fun reset() = samples.clear()

    private fun prune(now: Long) {
        val cutoff = now - maxWindowMs
        while (samples.isNotEmpty() && samples.first().t < cutoff) samples.removeFirst()
    }
}
