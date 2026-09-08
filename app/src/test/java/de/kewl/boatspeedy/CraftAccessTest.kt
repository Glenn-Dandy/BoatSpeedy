package de.kewl.boatspeedy

import de.kewl.boatspeedy.data.Craft
import de.kewl.boatspeedy.nav.LatLon
import de.kewl.boatspeedy.nav.RouteResult
import de.kewl.boatspeedy.nav.WaterRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * Die Zugangsmerkmale in OpenStreetMap sind gestuft: `access` gilt für alles, `boat` für
 * Boote, `motorboat`/`canoe` für die einzelne Art. Das Genauere schlägt das Allgemeinere.
 *
 * Der Anlass ist die obere Saale bei Zeutsch. Dort endete das Netz genau an der
 * Kanu-Einsetzstelle, weil der Abschnitt flussabwärts `boat=no` trägt — gemeint gegen
 * Motorboote, gelesen als Verbot für alles. 44 km Saale allein in der Kachel `n50e011`
 * sind so getaggt.
 *
 * Nachgestellt mit zwei Wegen auf einer Linie: der westliche frei, der östliche gesperrt.
 * Gefahren wird vom westlichen Ende zum östlichen — also einmal quer über die Naht.
 */
class CraftAccessTest {

    private val west = LatLon(50.5, 11.00)
    private val naht = LatLon(50.5, 11.02)
    private val ost = LatLon(50.5, 11.06)

    /** Länge des gesperrten Stücks: 0,04° Länge auf 50,5° Breite, rund 2,8 km. */
    private val gesperrtM = 2_833.0

    private fun weg(tags: String, vonLon: Double, bisLon: Double) =
        """{"type":"way","tags":{$tags},"geometry":[""" +
            """{"lat":50.5,"lon":$vonLon},{"lat":50.5,"lon":$bisLon}]}"""

    /** Schreibt die zwei Kacheln, die [WaterRouter] für diese Strecke anfasst. */
    private fun kacheln(vararg wege: String): File {
        val dir = createTempDir("zugang")
        val inhalt = """{"version":1,"tile":"n50e011","generated":"2026-09-08",""" +
            """"elements":[${wege.joinToString(",")}]}"""
        GZIPOutputStream(File(dir, "n50e011.json.gz").outputStream()).use {
            it.write(inhalt.toByteArray())
        }
        // Der Rand um die Strecke greift in die Nachbarkachel; fehlt sie, fällt der Router
        // auf Overpass zurück und der Test hinge am Netz.
        GZIPOutputStream(File(dir, "n50e010.json.gz").outputStream()).use {
            it.write("""{"version":1,"tile":"n50e010","generated":"2026-09-08","elements":[]}""".toByteArray())
        }
        return dir
    }

    private fun fahre(craft: Craft, vararg wege: String): RouteResult {
        val dir = kacheln(*wege)
        try {
            return WaterRouter.route(west, ost, craft, dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private val frei = weg(""""waterway":"river"""", west.lon, naht.lon)

    @Test
    fun `Kanu faehrt ueber boat=no und bekommt die Laenge gemeldet`() {
        val r = fahre(Craft.CANOE, frei, weg(""""waterway":"river","boat":"no"""", naht.lon, ost.lon))
        assertTrue("Kanu muss durchkommen, war: $r", r is RouteResult.Ok)
        val ok = r as RouteResult.Ok
        assertEquals(
            "die gesperrte Strecke muss gemeldet werden",
            gesperrtM, ok.restrictedM, 50.0,
        )
    }

    @Test
    fun `Motorboot bleibt an boat=no stehen`() {
        val r = fahre(Craft.MOTORBOAT, frei, weg(""""waterway":"river","boat":"no"""", naht.lon, ost.lon))
        // Auf der oberen Saale ist das richtig so — mit Motor darf man dort nicht.
        assertTrue("Motorboot darf nicht durchkommen, war: $r", r is RouteResult.Failed)
    }

    @Test
    fun `canoe=yes hebt boat=no auf und wird nicht mehr gemeldet`() {
        val r = fahre(
            Craft.CANOE, frei,
            weg(""""waterway":"river","boat":"no","canoe":"yes"""", naht.lon, ost.lon),
        )
        assertTrue("war: $r", r is RouteResult.Ok)
        assertEquals(
            "ausdrücklich erlaubt ist keine Einschränkung",
            0.0, (r as RouteResult.Ok).restrictedM, 1e-6,
        )
    }

    @Test
    fun `motorboat=yes hebt boat=no auf`() {
        val r = fahre(
            Craft.MOTORBOAT, frei,
            weg(""""waterway":"river","boat":"no","motorboat":"yes"""", naht.lon, ost.lon),
        )
        assertTrue("das Genauere schlägt das Allgemeinere, war: $r", r is RouteResult.Ok)
        assertEquals(0.0, (r as RouteResult.Ok).restrictedM, 1e-6)
    }

    @Test
    fun `canoe=no sperrt auch das Kanu`() {
        val r = fahre(
            Craft.CANOE, frei,
            weg(""""waterway":"river","canoe":"no"""", naht.lon, ost.lon),
        )
        assertTrue("war: $r", r is RouteResult.Failed)
    }

    @Test
    fun `access=no sperrt jedes Fahrzeug`() {
        for (craft in Craft.entries) {
            val r = fahre(craft, frei, weg(""""waterway":"river","access":"no"""", naht.lon, ost.lon))
            assertTrue("$craft darf hier nicht durch, war: $r", r is RouteResult.Failed)
        }
    }

    @Test
    fun `Rohrdurchlass bleibt unpassierbar, auch mit ausdruecklicher Erlaubnis`() {
        // Ein Rohr unter einer Straße ist kein Fahrwasser — daran ändert kein Merkmal etwas.
        val r = fahre(
            Craft.CANOE, frei,
            weg(""""waterway":"river","canoe":"yes","tunnel":"culvert"""", naht.lon, ost.lon),
        )
        assertTrue("war: $r", r is RouteResult.Failed)
    }
}
