package de.kewl.boatspeedy

import de.kewl.boatspeedy.data.Craft
import de.kewl.boatspeedy.nav.LatLon
import de.kewl.boatspeedy.nav.distanceM
import de.kewl.boatspeedy.nav.pathLengthM
import de.kewl.boatspeedy.nav.MapTiles
import de.kewl.boatspeedy.nav.ObstacleKind
import de.kewl.boatspeedy.nav.RouteResult
import de.kewl.boatspeedy.nav.WaterRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * Vier Wehre an der Saale, mit den Daten aus OpenStreetMap (Kachel n50e011).
 *
 * Beide liegen als **Weg** quer über den Fluss und teilen einen Punkt mit ihm. Genau die
 * fehlten in unseren Kacheln: Der Filter holte Wehre nur als Knoten, und so stand vor dem
 * Saale-Wehr Uhlstädt und dem Paradieswehr in Jena kein Wort.
 *
 * Die Stellen gehen verschieden aus. In Uhlstädt führt ein Kanal ums Wehr herum, aber
 * durch die Turbinen einer Wasserkraftanlage; getragen wird über die Rampen daneben. In
 * Jena gibt es nur den Weg über Land, mit `portage=designated` an beiden Rampen. In Kahla
 * steht kein Weg in OSM, nur zwei Slipanlagen mit dem Wehr dazwischen. In Bad Kösen hilft
 * nichts davon, dort führt die Strecke durch das Wehr und sagt es.
 */
class WeirPortageTest {

    /** Die Wasserkraftanlage Uhlstädt, mitten im Kanal neben dem Wehr. */
    private val kraftwerk = LatLon(50.74102, 11.46281)

    /** Oberhalb des Wehrs Uhlstädt. */
    private val uhlstaedtOben = LatLon(50.74208, 11.45333)

    /** Unterhalb, gut einen halben Kilometer weiter. */
    private val uhlstaedtUnten = LatLon(50.73706, 11.46652)

    /** Oberhalb des Wehrs Bad Kösen. */
    private val koesenOben = LatLon(51.12840, 11.71893)

    /** Unterhalb, rund einen Kilometer weiter. */
    private val koesenUnten = LatLon(51.14207, 11.71907)

    /** Oberhalb des Wehrs Kahla. */
    private val kahlaOben = LatLon(50.79508, 11.57369)

    /** Unterhalb, gut einen halben Kilometer weiter. */
    private val kahlaUnten = LatLon(50.80113, 11.59127)

    /** Oberhalb des Paradieswehrs in Jena. */
    private val jenaOben = LatLon(50.92299, 11.58690)

    /** Unterhalb, hinter der Wehrschwelle. */
    private val jenaUnten = LatLon(50.92700, 11.59300)

    private fun fahre(craft: Craft, kachel: String, von: LatLon, nach: LatLon): RouteResult {
        val inhalt = javaClass.classLoader!!.getResource(kachel)!!.readText()
        val dir = createTempDir("uhlstaedt")
        try {
            for (id in MapTiles.tilesForRoute(von, nach)) {
                val kachelname = if (kachel == "koesen.json") "n51e011" else "n50e011"
                val text = if (id.name == kachelname) {
                    inhalt
                } else {
                    """{"version":1,"tile":"${id.name}","generated":"2026-09-20","elements":[]}"""
                }
                GZIPOutputStream(File(dir, "${id.name}.json.gz").outputStream()).use {
                    it.write(text.toByteArray())
                }
            }
            return WaterRouter.route(von, nach, craft, dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * In Jena führt kein Wasser ums Wehr. Die Route geht über die beiden Rampen an Land,
     * rund fünfzig Meter am Ufer entlang und wieder hinein.
     */
    @Test
    fun `in Jena traegt das Kanu ums Paradieswehr`() {
        val r = fahre(Craft.CANOE, "jena.json", jenaOben, jenaUnten) as RouteResult.Ok
        assertTrue("nichts getragen: ${r.portageM} m", r.portageM > 50)
        assertTrue("der getragene Teil fehlt auf der Karte", r.portage.isNotEmpty())
        // Oberhalb losgefahren und unterhalb angekommen, nicht am Wehr stehen geblieben.
        assertTrue("Route endet zu früh: ${r.water.last()}", r.water.last().lat > 50.9260)
    }

    /**
     * Für ein Motorboot gibt es in Jena keine Strecke: Die Saale trägt dort `boat=no`,
     * und über Land wird nur ein Kanu getragen.
     */
    @Test
    fun `in Jena faehrt kein Motorboot`() {
        assertTrue(fahre(Craft.MOTORBOAT, "jena.json", jenaOben, jenaUnten) is RouteResult.Failed)
    }

    /**
     * In Kahla gibt es keinen Umtrageweg, nur zwei Slipanlagen mit dem Wehr dazwischen.
     * Ohne eine Verbindung zwischen ihnen brach die Route am Wehr ab.
     */
    @Test
    fun `in Kahla traegt das Kanu von Anleger zu Anleger`() {
        val r = fahre(Craft.CANOE, "kahla.json", kahlaOben, kahlaUnten) as RouteResult.Ok
        assertTrue("nichts getragen: ${r.portageM} m", r.portageM > 30)
        assertTrue("Route endet zu früh: ${r.water.last()}", r.water.last().lat > 50.800)
    }

    /**
     * Bad Kösen zeigt die Grenze. Das Wehr liegt schräg im Fluss, und Ausstieg wie
     * Einstieg sind beide **unterhalb** der Kreuzung eingetragen; ein Weg zwischen ihnen
     * führt an nichts vorbei. Der Umtrageweg selbst liegt in zwei Stücken, sein
     * Mittelstück ist ein gewöhnlicher Fußweg und steht nicht in unseren Kacheln.
     *
     * Also bleibt nur das Wehr. Genau dafür ist es kein Verbot mehr, sondern teuer: Die
     * Strecke geht hindurch, meldet das Wehr, und die Fahrt endet nicht vorher.
     */
    @Test
    fun `in Bad Koesen kommt die Route durch und meldet das Wehr`() {
        val r = fahre(Craft.CANOE, "koesen.json", koesenOben, koesenUnten) as RouteResult.Ok
        assertTrue("Route endet zu früh: ${r.water.last()}", r.water.last().lat > 51.140)
        assertTrue("kein Wehr gemeldet: ${r.obstacles}", r.obstacles.any { it.kind == ObstacleKind.WEIR })
        assertTrue("nichts getragen: ${r.portageM} m", r.portageM > 50)
    }

    /**
     * An allen drei Wehren wird getragen, und zwar in kurzen Stücken am Ufer. Eine
     * erfundene Gerade quer über den Fluss wäre lang und sähe auf der Karte aus wie ein
     * Sprung über das Wehr; solche Verbindungen entstehen nicht mehr.
     */
    @Test
    fun `an jedem Wehr wird kurz und am Ufer getragen`() {
        val stellen = listOf(
            Triple("kahla.json", kahlaOben, kahlaUnten),
            Triple("koesen.json", koesenOben, koesenUnten),
            Triple("jena.json", jenaOben, jenaUnten),
        )
        for ((kachel, von, nach) in stellen) {
            val r = fahre(Craft.CANOE, kachel, von, nach) as RouteResult.Ok
            assertTrue("$kachel: nichts getragen", r.portageM > 50)
            for (zug in r.portage) {
                for ((p, q) in zug.zipWithNext()) {
                    assertTrue("$kachel: Stück von ${distanceM(p, q)} m", distanceM(p, q) < 150)
                }
            }
        }
    }

    /**
     * In Uhlstädt führt ein Kanal ums Wehr herum — **durch die Turbinen** der
     * Wasserkraftanlage. Für die Wegsuche war das lange der bequemste Weg, in
     * Wirklichkeit ist es keiner. Getragen wird stattdessen über die Rampen am Westufer.
     */
    @Test
    fun `in Uhlstaedt faehrt niemand durch die Turbinen`() {
        val r = fahre(Craft.CANOE, "uhlstaedt.json", uhlstaedtOben, uhlstaedtUnten) as RouteResult.Ok
        assertTrue("nichts getragen: ${r.portageM} m", r.portageM > 50)
        assertTrue(
            "die Strecke läuft durch die Anlage",
            r.water.none { distanceM(it, kraftwerk) < 30 },
        )
    }

    /**
     * Das Burgauer Wehr in Jena teilt **keinen Punkt** mit der Saale, es kreuzt sie nur.
     * Über die Punkte gesperrt war dort nichts: Die Strecke fuhr mitten hindurch, und die
     * Umtragung daneben blieb ungenutzt. Der Schnitt liegt 30 cm hinter einem Flusspunkt.
     */
    @Test
    fun `ein Wehr ohne gemeinsamen Punkt sperrt trotzdem`() {
        val r = fahre(
            Craft.CANOE, "burgau.json",
            LatLon(50.87794, 11.59478), LatLon(50.90988, 11.58122),
        ) as RouteResult.Ok
        assertTrue("nichts getragen: ${r.portageM} m", r.portageM > 50)
    }

    /**
     * Bei Porstendorf schneidet die Lache, ein Mühlgraben, die Saaleschleife ab: kürzer,
     * aber mitten durch die Wasserkraftanlage. Im Kanu zählt der Fluss, und ein Kanal
     * kostet mehr.
     */
    @Test
    fun `das Kanu bleibt im Fluss statt in den Muehlgraben zu gehen`() {
        val r = fahre(
            Craft.CANOE, "porstendorf.json",
            LatLon(50.95577, 11.63309), LatLon(50.98375, 11.66556),
        ) as RouteResult.Ok
        assertTrue(
            "die Strecke läuft durch die Anlage",
            r.obstacles.none { it.kind == ObstacleKind.POWER },
        )
        // Die Schleife ist rund einen Kilometer länger als der Graben.
        assertTrue("zu kurz, also durch die Lache: ${pathLengthM(r.water)}", pathLengthM(r.water) > 5_000)
    }

    /**
     * An Reschwitz und Fischersdorf liegen Aus- und Einstieg am **selben** Ufer, und die
     * Linie zwischen ihnen streift das Wehr an seinem Ende. Genau dort trägt man vorbei.
     */
    @Test
    fun `an der Saale bei Saalfeld wird an beiden Wehren getragen`() {
        val r = fahre(
            Craft.CANOE, "saalfeld.json",
            LatLon(50.62328, 11.40642), LatLon(50.61801, 11.38566),
        ) as RouteResult.Ok
        assertTrue("nichts getragen: ${r.portageM} m", r.portageM > 100)
        assertEquals("beide Wehre umtragen", 2, r.portage.size)
    }

    @Test
    fun `das Wehr wird gemeldet`() {
        val r = fahre(Craft.CANOE, "uhlstaedt.json", uhlstaedtOben, uhlstaedtUnten) as RouteResult.Ok
        assertTrue("kein Wehr: ${r.obstacles}", r.obstacles.any { it.kind == ObstacleKind.WEIR })
    }

    /** Ein Motorboot trägt nicht. Unterhalb liegt auf der Saale ein Bootsverbot. */
    @Test
    fun `das Motorboot kommt in Uhlstaedt nicht durch`() {
        val r = fahre(Craft.MOTORBOAT, "uhlstaedt.json", uhlstaedtOben, uhlstaedtUnten)
        if (r is RouteResult.Ok) {
            assertTrue("getragen wurde nichts", r.portageM == 0.0)
            assertTrue("zu weit gekommen: ${r.water.last()}", r.water.last().lat > 50.7400)
        }
    }
}
