package de.kewl.boatspeedy

import de.kewl.boatspeedy.nav.LatLon
import de.kewl.boatspeedy.nav.MapTiles
import de.kewl.boatspeedy.nav.TileId
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die untere Saale, an der derselbe Fehler ein zweites Mal auftrat.
 *
 * Zwischen Merseburg und Bad Dürrenberg weicht sie über den zwölften Längengrad nach Osten
 * aus — 37,5 km, die ausschließlich in der Kachel `e012` liegen. Von Uhlstädt aus ist die
 * Luftlinie nach Bernburg in der Länge nur 0,27° breit; ein Fünftel davon sind 0,215° Rand,
 * die Ostkante liegt damit bei 11,955°, und die Nachbarkachel fällt heraus. Das Netz riss
 * bei Merseburg, gemeldet wurde „kein durchgehender Wasserweg".
 *
 * Nachgestellt gegen die echten Kacheln: Salzmünde, Calbe und Barby gingen durch, Wettin,
 * Rothenburg, Alsleben, Bernburg und Nienburg nicht — allein deshalb, weil deren Länge die
 * Ostkante zufällig nicht über 12° schob.
 */
class SaaleCorridorTest {

    private val uhlstaedt = LatLon(50.7167, 11.4667)

    /** Die Kachel mit dem Ostbogen der Saale. */
    private val bogen = TileId(51, 12)

    private val ziele = mapOf(
        "Wettin" to LatLon(51.5872, 11.8047),
        "Rothenburg" to LatLon(51.6383, 11.7300),
        "Alsleben" to LatLon(51.7000, 11.6900),
        "Bernburg" to LatLon(51.7930, 11.7400),
        "Nienburg" to LatLon(51.8380, 11.7690),
        "Calbe" to LatLon(51.9070, 11.7760),
        "Barby" to LatLon(51.9700, 11.8800),
        "Salzmünde" to LatLon(51.5276, 11.8421),
    )

    @Test
    fun `der Ostbogen der Saale ist bei jedem Ziel dabei`() {
        for ((name, ziel) in ziele) {
            val t = MapTiles.tilesForRoute(uhlstaedt, ziel)
            assertTrue(
                "$name: n51e012 fehlt — genau daran riss das Netz. Angefordert: " +
                    t.joinToString(" ") { it.name },
                t.contains(bogen),
            )
        }
    }

    @Test
    fun `auch die Kachel um den Start und um das Ziel ist dabei`() {
        for ((name, ziel) in ziele) {
            val t = MapTiles.tilesForRoute(uhlstaedt, ziel)
            assertTrue("$name: Startkachel fehlt", t.contains(TileId(50, 11)))
            assertTrue(
                "$name: Zielkachel fehlt",
                t.contains(TileId(ziel.lat.toInt(), ziel.lon.toInt())),
            )
        }
    }

    /**
     * Der Rand ist immer mindestens eine Kachelbreite — sonst hängt es wieder am Zufall,
     * ob die Luftlinie die Nachbarkachel gerade eben streift.
     */
    @Test
    fun `der Nachbar ist auch bei einem Ziel um die Ecke dabei`() {
        val a = LatLon(50.72, 11.34)
        val t = MapTiles.tilesForRoute(a, LatLon(50.73, 11.36))
        for (lat in 49..51) {
            for (lon in 10..12) {
                assertTrue("n${lat}e0$lon fehlt", t.contains(TileId(lat, lon)))
            }
        }
    }

    /** Und er ufert nicht aus: über ganz Europa bleibt es bei zwei Grad. */
    @Test
    fun `der Rand bleibt gedeckelt`() {
        val weit = MapTiles.tilesForRoute(LatLon(43.0, 3.0), LatLon(55.0, 15.0))
        val lats = weit.map { it.lat }
        assertTrue(lats.min() == 41 && lats.max() == 57, "Breiten: ${lats.min()}..${lats.max()}")
    }

    private fun assertTrue(bedingung: Boolean, meldung: String) = assertTrue(meldung, bedingung)
}
