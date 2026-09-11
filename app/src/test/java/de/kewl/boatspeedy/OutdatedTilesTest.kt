package de.kewl.boatspeedy

import de.kewl.boatspeedy.nav.MapTiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * Eine einmal geholte Kachel wurde nie wieder aufgefrischt — `missing` fragt nur, ob die
 * Datei existiert. Daran ist eine Fahrt gescheitert: Auf dem Gerät lagen Kacheln aus dem
 * Deutschland-Lauf, in denen der Grand Canal d'Alsace fehlte. Ab Basel läuft die
 * Fahrrinne über die französische Seite, und so riss das Netz mitten im Rhein, während
 * der Server die vollständigen Daten längst hatte.
 */
class OutdatedTilesTest {

    private fun kachel(dir: File, name: String, stand: String) {
        val f = File(dir, "$name.json.gz")
        GZIPOutputStream(f.outputStream()).use {
            it.write("""{"version":1,"tile":"$name","generated":"$stand","elements":[]}""".toByteArray())
        }
    }

    private fun index(stand: String) = MapTiles.Index(stand, emptyMap())

    @Test
    fun `aeltere Kachel wird als veraltet erkannt`() {
        val dir = createTempDir()
        try {
            kachel(dir, "n47e007", "2026-09-05")
            kachel(dir, "n50e011", "2026-09-08")
            val alt = MapTiles.outdated(dir, index("2026-09-08"))
            assertEquals(1, alt.size)
            assertEquals("n47e007", alt.first().id.name)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `aktuelle Kacheln gelten nicht als veraltet`() {
        val dir = createTempDir()
        try {
            kachel(dir, "n50e011", "2026-09-08")
            assertTrue(MapTiles.outdated(dir, index("2026-09-08")).isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `ohne Verzeichnis wird nichts als veraltet gemeldet`() {
        val dir = createTempDir()
        try {
            kachel(dir, "n50e011", "2026-09-01")
            // Kein Verzeichnis vom Server: dann ist unbekannt, was aktuell wäre —
            // und Raten wäre schlimmer als Schweigen.
            assertTrue(MapTiles.outdated(dir, null).isEmpty())
            assertTrue(MapTiles.outdated(dir, index("")).isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `Kachel ohne Datum gilt als veraltet`() {
        val dir = createTempDir()
        try {
            val f = File(dir, "n50e011.json.gz")
            GZIPOutputStream(f.outputStream()).use {
                it.write("""{"version":1,"elements":[]}""".toByteArray())
            }
            assertEquals(1, MapTiles.outdated(dir, index("2026-09-08")).size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `nur die Kachel mit neuerem Datum wird aufgefrischt`() {
        val dir = createTempDir()
        try {
            // Beide Kacheln liegen vom 5. auf dem Gerät. Der Lauf vom 8. hat nur die eine
            // wirklich geändert — die andere hat der Server auf ihrem alten Datum gelassen.
            kachel(dir, "n50e011", "2026-09-05")
            kachel(dir, "n38w009", "2026-09-05")
            val index = MapTiles.Index(
                generated = "2026-09-08",
                tiles = emptyMap(),
                dates = mapOf("n50e011" to "2026-09-08", "n38w009" to "2026-09-05"),
            )
            val alt = MapTiles.outdated(dir, index)
            assertEquals("Portugal hat sich nicht geändert: $alt", 1, alt.size)
            assertEquals("n50e011", alt.first().id.name)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `ohne Datum je Kachel gilt weiter das Gesamtdatum`() {
        val dir = createTempDir()
        try {
            // Ein Server mit älterem Verzeichnis. Dann lieber einmal zu viel laden als
            // eine Lücke im Netz übersehen.
            kachel(dir, "n50e011", "2026-09-05")
            assertEquals(1, MapTiles.outdated(dir, index("2026-09-08")).size)
        } finally {
            dir.deleteRecursively()
        }
    }
}
