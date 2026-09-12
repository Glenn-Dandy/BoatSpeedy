package de.kewl.boatspeedy.nav

import org.json.JSONObject

/**
 * Öffnungszeiten so umbrechen, wie sie gemeint sind.
 *
 * In OpenStreetMap trennt das Semikolon die Regeln einer Zeitangabe, und bei Schleusen
 * sind das meist drei: die Sperrzeit im Winter, die Saison, die Sperrzeit danach. In einer
 * Zeile hintereinandergeschrieben liest sie niemand, gerade nicht vom Boot aus.
 *
 * Getrennt wird **nur** am Semikolon. Das Komma trennt innerhalb einer Regel die Tage
 * ("Mo-Th 07:00-19:00, Fr-Su 07:00-21:00") und gehört zusammen.
 */
fun openingHoursLines(raw: String): String =
    raw.split(";").map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

/** Ein Seezeichen an der angetippten Stelle, in lesbarer Form. */
data class SeamarkInfo(
    val title: String,
    /** Zeilen in Klartext, so weit die Merkmale sicher zu deuten sind. */
    val lines: List<String>,
    /** Was nicht übersetzt werden konnte — roh, damit nichts verschwiegen wird. */
    val raw: List<String>,
)

/**
 * Fragt, was an einer angetippten Stelle steht.
 *
 * Die Kacheln von OpenSeaMap **zeichnen** die Seezeichen, sind aber Bilder — antippbar
 * ist daran nichts, und die Werte stehen ohnehin nicht darin. Für die Auskunft führt
 * kein Weg an den Rohdaten vorbei.
 *
 * Deshalb genau **eine** Abfrage je bewusstem Antippen, nicht dauernd im Hintergrund:
 * Die öffentlichen Overpass-Server sind knapp, und ein Seezeichen ist Beiwerk gegenüber
 * einer Route.
 */
object SeamarkSource {

    /** Wie weit um den Tipppunkt gesucht wird. Ein Finger ist ungenauer als ein Zeiger. */
    const val RADIUS_M = 60

    /** Darunter trifft man ohnehin nichts Bestimmtes. */
    const val MIN_ZOOM = 14.0

    fun fetch(lat: Double, lon: Double): List<SeamarkInfo> {
        val q = """
            [out:json][timeout:20];
            node(around:$RADIUS_M,$lat,$lon)["seamark:type"];
            out tags;
        """.trimIndent()
        val body = WaterRouter.postOverpass(q) ?: return emptyList()
        return parse(body)
    }

    internal fun parse(json: String): List<SeamarkInfo> = runCatching {
        val els = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        (0 until els.length()).mapNotNull { i ->
            val tags = els.getJSONObject(i).optJSONObject("tags") ?: return@mapNotNull null
            val map = HashMap<String, String>()
            for (k in tags.keys()) map[k] = tags.getString(k)
            describe(map)
        }
    }.getOrDefault(emptyList())

    /* ------------------------- Übersetzung ------------------------- */

    private val TYPES = mapOf(
        "buoy_lateral" to "Seitenzeichen (Tonne)",
        "buoy_cardinal" to "Kardinalzeichen (Tonne)",
        "buoy_safe_water" to "Ansteuerungstonne",
        "buoy_special_purpose" to "Sonderzeichen (Tonne)",
        "buoy_isolated_danger" to "Gefahrenstelle (Tonne)",
        "beacon_lateral" to "Seitenzeichen (Bake)",
        "beacon_cardinal" to "Kardinalzeichen (Bake)",
        "beacon_safe_water" to "Ansteuerungsbake",
        "beacon_special_purpose" to "Sonderzeichen (Bake)",
        "beacon_isolated_danger" to "Gefahrenstelle (Bake)",
        "light_major" to "Leuchtfeuer",
        "light_minor" to "Leuchtfeuer",
        "light_vessel" to "Feuerschiff",
        "landmark" to "Landmarke",
        "notice" to "Hinweiszeichen",
        "mooring" to "Festmacher",
        "harbour" to "Hafen",
        "berth" to "Liegeplatz",
        "anchorage" to "Ankerplatz",
        "bridge" to "Brücke",
        "lock_basin" to "Schleuse",
        "distance_mark" to "Kilometertafel",
        "restricted_area" to "Sperrgebiet",
        "separation_zone" to "Trennzone",
        "pile" to "Dalbe",
        "platform" to "Plattform",
        "wreck" to "Wrack",
        "rock" to "Fels",
        "obstruction" to "Hindernis",
        "cable_submarine" to "Unterwasserkabel",
        "pipeline_submarine" to "Unterwasserleitung",
    )

    private val COLOURS = mapOf(
        "red" to "rot", "green" to "grün", "yellow" to "gelb", "black" to "schwarz",
        "white" to "weiß", "blue" to "blau", "orange" to "orange", "grey" to "grau",
        "brown" to "braun", "amber" to "bernstein", "violet" to "violett",
        "magenta" to "magenta",
    )

    private val SHAPES = mapOf(
        "conical" to "Spitztonne", "can" to "Stumpftonne", "spherical" to "Kugeltonne",
        "pillar" to "Bakentonne", "spar" to "Spierentonne", "barrel" to "Fasstonne",
        "super-buoy" to "Großtonne", "ice-buoy" to "Eistonne",
    )

    private val CARDINALS = mapOf(
        "north" to "Nord", "east" to "Ost", "south" to "Süd", "west" to "West",
    )

    private val CATEGORIES = mapOf(
        "port" to "Backbord", "starboard" to "Steuerbord",
        "preferred_channel_port" to "Backbord, bevorzugtes Fahrwasser",
        "preferred_channel_starboard" to "Steuerbord, bevorzugtes Fahrwasser",
        "speed_limit" to "Geschwindigkeitsbegrenzung",
        "no_entry" to "Einfahrt verboten",
        "no_anchoring" to "Ankern verboten",
        "no_overtaking" to "Überholverbot",
        "no_wash" to "Sog und Wellenschlag vermeiden",
        "no_berthing" to "Anlegen verboten",
    )

    /** Kennungen der Feuer — die Abkürzungen stehen so auf jeder Seekarte. */
    private val LIGHT_CHARACTER = mapOf(
        "F" to "Festfeuer", "Fl" to "Blitzfeuer", "LFl" to "langes Blitzfeuer",
        "Oc" to "Unterbrochenes Feuer", "Iso" to "Gleichtaktfeuer",
        "Q" to "Funkelfeuer", "VQ" to "schnelles Funkelfeuer",
        "IQ" to "unterbrochenes Funkelfeuer", "Mo" to "Morsefeuer",
        "Al" to "Wechselfeuer", "FFl" to "Fest- und Blitzfeuer",
    )

    private fun colours(v: String) =
        v.split(";").joinToString(", ") { COLOURS[it] ?: it }

    /**
     * Baut aus den Merkmalen eines Zeichens lesbare Zeilen.
     *
     * Was nicht sicher zu deuten ist, landet **roh** in [SeamarkInfo.raw] statt
     * weggelassen oder geraten zu werden: Bei einem Zeichen auf dem Wasser ist eine
     * erfundene Bedeutung schlimmer als ein unübersetztes Kürzel.
     */
    internal fun describe(tags: Map<String, String>): SeamarkInfo? {
        val type = tags["seamark:type"] ?: return null
        if (type == "sounding" || type.isBlank()) return null
        // **Schleusentore sind keine eigene Auskunft.** An der Oeblitzschleuse tragen die
        // beiden Tore `seamark:type=gate` und sonst nichts; sie liegen 24 m neben der
        // Kammer, und wer die Schleuse antippen wollte, bekam ein Fenster mit dem Wort
        // „gate". Die Schleuse daneben weiß Name, Zeiten und Telefon — sie ist gemeint.
        if (type == "gate" && tags["waterway"] == "lock_gate") return null

        val name = tags["seamark:name"] ?: tags["name"]
        val title = buildString {
            append(TYPES[type] ?: type.replace('_', ' '))
            if (!name.isNullOrBlank()) append(" „").append(name).append('"')
        }

        val lines = ArrayList<String>()
        val used = HashSet<String>(listOf("seamark:type", "seamark:name", "name"))

        fun take(key: String): String? {
            val v = tags[key] ?: return null
            used.add(key)
            return v
        }

        // Art und Farbe – der Kern jedes Zeichens.
        take("seamark:$type:category")?.let { c ->
            lines.add(CATEGORIES[c] ?: CARDINALS[c]?.let { "$it-Kardinalzeichen" } ?: c)
        }
        take("seamark:$type:colour")?.let { lines.add("Farbe: ${colours(it)}") }
        take("seamark:$type:shape")?.let { lines.add(SHAPES[it] ?: it) }
        take("seamark:$type:system")?.let { lines.add("System ${it.uppercase()}") }
        take("seamark:$type:colour_pattern")
        take("seamark:topmark:shape")?.let { s ->
            val c = take("seamark:topmark:colour")
            lines.add("Toppzeichen: $s" + if (c != null) " (${colours(c)})" else "")
        }

        // Feuer – Kennung, Farbe, Wiederkehr.
        val character = take("seamark:light:character")
        if (character != null) {
            val group = take("seamark:light:group")
            val col = take("seamark:light:colour")
            val period = take("seamark:light:period")
            val range = take("seamark:light:range")
            val short = buildString {
                append(character)
                if (!group.isNullOrBlank()) append('(').append(group).append(')')
                if (col != null) append(' ').append(colours(col))
                if (period != null) append(", Wiederkehr ").append(period).append(" s")
            }
            lines.add("Feuer: $short")
            if (LIGHT_CHARACTER[character] != null) lines.add("  ${LIGHT_CHARACTER[character]}")
            if (range != null) lines.add("  Tragweite $range sm")
        }

        // Hinweiszeichen – hier steckt der eigentliche Text.
        take("seamark:notice:category")?.let { lines.add(CATEGORIES[it] ?: it) }
        take("seamark:notice:information")?.let { lines.add(it) }
        take("seamark:notice:impact")?.let {
            lines.add(
                when (it) {
                    "upstream" -> "gilt zu Berg"
                    "downstream" -> "gilt zu Tal"
                    else -> it
                },
            )
        }
        take("seamark:information")?.let { lines.add(it) }
        take("seamark:distance_mark:distance")?.let { lines.add("Kilometer $it") }

        val raw = tags.entries
            .filter { it.key.startsWith("seamark:") && it.key !in used }
            .sortedBy { it.key }
            .map { "${it.key.removePrefix("seamark:")} = ${it.value}" }

        if (lines.isEmpty() && raw.isEmpty()) return SeamarkInfo(title, emptyList(), emptyList())
        return SeamarkInfo(title, lines, raw)
    }
}
