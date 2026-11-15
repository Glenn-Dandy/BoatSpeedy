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

/** Ein Schild von mehreren am selben Mast. */
data class SeamarkGroup(val heading: String, val lines: List<String>)

/** Ein Seezeichen an der angetippten Stelle, in lesbarer Form. */
data class SeamarkInfo(
    val title: String,
    /** Zeilen in Klartext, so weit die Merkmale sicher zu deuten sind. */
    val lines: List<String>,
    /** Was nicht übersetzt werden konnte, roh, damit nichts verschwiegen wird. */
    val raw: List<String>,
    /** Mehrere Schilder an einem Mast (`notice:1:`, `notice:2:` …), je eins mit Überschrift. */
    val groups: List<SeamarkGroup> = emptyList(),
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
        "notice" to "Schifffahrtszeichen",
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

    private val BUOY_SHAPES = mapOf(
        "conical" to "Spitztonne", "can" to "Stumpftonne", "spherical" to "Kugeltonne",
        "pillar" to "Bakentonne", "spar" to "Spierentonne", "barrel" to "Fasstonne",
        "super-buoy" to "Großtonne", "ice-buoy" to "Eistonne",
    )

    private val BEACON_SHAPES = mapOf(
        "stake" to "Stangenbake", "pole" to "Stangenbake", "pile" to "Pfahlbake",
        "withy" to "Pricke", "tower" to "Turmbake", "lattice" to "Gitterbake",
        "cairn" to "Steinbake", "buoyant" to "schwimmende Bake",
    )

    /** Toppzeichen und Tageszeichen, so wie sie in der Seekarte beschrieben werden. */
    private val TOPMARK_SHAPES = mapOf(
        "cone, point up" to "Kegel, Spitze oben",
        "cone, point down" to "Kegel, Spitze unten",
        "2 cones up" to "zwei Kegel, Spitzen oben",
        "2 cones down" to "zwei Kegel, Spitzen unten",
        "2 cones point together" to "zwei Kegel, Spitzen zueinander",
        "2 cones base together" to "zwei Kegel, Grundflächen zueinander",
        "cylinder" to "Zylinder", "sphere" to "Kugel", "2 spheres" to "zwei Kugeln",
        "x-shape" to "Kreuz (X)", "cross" to "Kreuz", "square" to "Quadrat",
        "board" to "Tafel", "rhombus" to "Raute", "triangle, point up" to "Dreieck, Spitze oben",
        "triangle, point down" to "Dreieck, Spitze unten", "flag" to "Flagge",
    )

    private val CARDINALS = mapOf(
        "north" to "Nord", "east" to "Ost", "south" to "Süd", "west" to "West",
    )

    /**
     * Seitenzeichen. Links und rechts gelten auf Binnengewässern zu Tal gesehen,
     * Backbord und Steuerbord von See kommend.
     */
    private val LATERAL_CATEGORIES = mapOf(
        "port" to "Backbord", "starboard" to "Steuerbord",
        "preferred_channel_port" to "Backbord, bevorzugtes Fahrwasser",
        "preferred_channel_starboard" to "Steuerbord, bevorzugtes Fahrwasser",
        "channel_left" to "Fahrrinne, linke Seite",
        "channel_right" to "Fahrrinne, rechte Seite",
        "waterway_left" to "Wasserstraße, linke Seite",
        "waterway_right" to "Wasserstraße, rechte Seite",
        "danger_left" to "Gefahrenstelle, linke Seite",
        "danger_right" to "Gefahrenstelle, rechte Seite",
        "channel_separation" to "Fahrwasserteilung",
        "turnoff_left" to "Abzweigung links",
        "turnoff_right" to "Abzweigung rechts",
        "junction_left" to "Einmündung links",
        "junction_right" to "Einmündung rechts",
        "harbour_left" to "Hafeneinfahrt links",
        "harbour_right" to "Hafeneinfahrt rechts",
        "bridge_pier" to "Brückenpfeiler",
    )

    /** Was ein Schild ist: die fünf Gruppen der Binnenschifffahrtszeichen (A bis E). */
    private val NOTICE_FUNCTIONS = mapOf(
        "prohibition" to "Verbot",
        "regulation" to "Gebot",
        "restriction" to "Einschränkung",
        "recommendation" to "Empfehlung",
        "information" to "Hinweis",
    )

    /** Was auf dem Schild steht. */
    private val NOTICE_CATEGORIES = mapOf(
        // Verbote
        "no_entry" to "Durchfahrt verboten",
        "closed_area" to "Gesperrte Wasserfläche",
        "no_overtaking" to "Überholen verboten",
        "no_convoy_overtaking" to "Überholen von Verbänden verboten",
        "no_passing" to "Begegnen und Überholen verboten",
        "no_berthing" to "Liegen verboten",
        "no_berthing_lateral_limit" to "Liegen verboten in der angegebenen Breite",
        "no_anchoring" to "Ankern verboten",
        "no_mooring" to "Festmachen am Ufer verboten",
        "no_turning" to "Wenden verboten",
        "no_wash" to "Sog und Wellenschlag vermeiden",
        "no_passage_left" to "Durchfahrt links verboten",
        "no_passage_right" to "Durchfahrt rechts verboten",
        "no_motor_craft" to "Motorboote verboten",
        "no_sport_craft" to "Sportboote verboten",
        "no_sports_craft" to "Sportboote verboten",
        "no_waterskiing" to "Wasserski verboten",
        "no_sailing_craft" to "Segelboote verboten",
        "no_unpowered_craft" to "Boote ohne Motor verboten",
        "no_sailboards" to "Segelsurfen verboten",
        "no_high_speeds" to "Schnelle Fahrt verboten",
        "no_launching_beaching" to "Einsetzen und Herausnehmen von Booten verboten",
        "no_waterbikes" to "Wassermotorräder verboten",
        // Gebote
        "move_to_left" to "Nach links fahren",
        "move_to_right" to "Nach rechts fahren",
        "move_to_port" to "Nach Backbord fahren",
        "move_to_starboard" to "Nach Steuerbord fahren",
        "keep_to_port" to "Backbordseite halten",
        "keep_to_starboard" to "Steuerbordseite halten",
        "keep_to_left" to "Linke Seite halten",
        "keep_to_right" to "Rechte Seite halten",
        "cross_to_port" to "Zur Backbordseite queren",
        "cross_to_starboard" to "Zur Steuerbordseite queren",
        "cross_to_left" to "Zur linken Seite queren",
        "cross_to_right" to "Zur rechten Seite queren",
        "proceed_to_left" to "Fahrtrichtung links",
        "proceed_to_right" to "Fahrtrichtung rechts",
        "stop" to "Anhalten",
        "speed_limit" to "Höchstgeschwindigkeit",
        "sound_horn" to "Schallzeichen geben",
        "keep_lookout" to "Besondere Vorsicht",
        "give_way_junction" to "Vorfahrt achten an der Einmündung",
        "give_way_crossing" to "Vorfahrt achten an der Kreuzung",
        "make_radio_contact" to "Sprechfunk benutzen",
        // Einschränkungen
        "limited_depth" to "Begrenzte Wassertiefe",
        "limited_headroom" to "Begrenzte Durchfahrtshöhe",
        "limited_width" to "Begrenzte Fahrrinnenbreite",
        "navigation_restrictions" to "Einschränkungen der Schifffahrt",
        "channel_distance_left" to "Fahrrinne mit Abstand zum linken Ufer",
        "channel_distance_right" to "Fahrrinne mit Abstand zum rechten Ufer",
        // Empfehlungen
        "channel_two_way" to "Durchfahrt in beiden Richtungen",
        "channel_one_way" to "Durchfahrt nur in einer Richtung",
        "opening_to_left" to "Empfohlene Durchfahrt links",
        "opening_to_right" to "Empfohlene Durchfahrt rechts",
        "proceed_parallel" to "Parallel weiterfahren",
        // Hinweise
        "prohibition_ends" to "Ende des Verbots",
        "overhead_cable" to "Freileitung",
        "power_cable" to "Hochspannungsleitung",
        "weir" to "Wehr",
        "culvert" to "Durchlass",
        "ferry_non_independent" to "Fähre, nicht frei fahrend",
        "ferry_independent" to "Fähre, frei fahrend",
        "berthing_permitted" to "Liegen erlaubt",
        "berthing_lateral_limit" to "Liegen erlaubt in der angegebenen Breite",
        "berth_rafting_limit" to "Liegen erlaubt, Fahrzeuge nebeneinander höchstens",
        "berthing_unmarked" to "Liegestelle für Fahrzeuge ohne blauen Kegel",
        "berthing_marked_1" to "Liegestelle für Fahrzeuge mit einem blauen Kegel",
        "berthing_marked_2" to "Liegestelle für Fahrzeuge mit zwei blauen Kegeln",
        "berthing_marked_3" to "Liegestelle für Fahrzeuge mit drei blauen Kegeln",
        "berthing_unmarked_pushing" to "Liegestelle für Schubverbände ohne blauen Kegel",
        "berthing_marked_pushing_1" to "Liegestelle für Schubverbände mit einem blauen Kegel",
        "berthing_marked_pushing_2" to "Liegestelle für Schubverbände mit zwei blauen Kegeln",
        "berthing_marked_pushing_3" to "Liegestelle für Schubverbände mit drei blauen Kegeln",
        "berthing_unmarked_non-pushing" to "Liegestelle ohne blauen Kegel, außer Schubverbände",
        "berthing_marked_non-pushing_1" to "Liegestelle mit einem blauen Kegel, außer Schubverbände",
        "berthing_marked_non-pushing_2" to "Liegestelle mit zwei blauen Kegeln, außer Schubverbände",
        "berthing_marked_non-pushing_3" to "Liegestelle mit drei blauen Kegeln, außer Schubverbände",
        "anchoring_permitted" to "Ankern erlaubt",
        "mooring_permitted" to "Festmachen erlaubt",
        "vehicle_loading_berth" to "Ladestelle für Fahrzeuge",
        "turning_area" to "Wendestelle",
        "secondary_waterway_crossing" to "Kreuzung mit Nebenwasserstraße",
        "secondary_waterway_right" to "Nebenwasserstraße rechts",
        "secondary_waterway_left" to "Nebenwasserstraße links",
        "main_waterway_crossing" to "Kreuzung mit Hauptwasserstraße",
        "main_waterway_right" to "Hauptwasserstraße rechts",
        "main_waterway_left" to "Hauptwasserstraße links",
        "main_waterway_junction" to "Einmündung einer Hauptwasserstraße",
        "secondary_waterway_junction" to "Einmündung einer Nebenwasserstraße",
        "drinking_water" to "Trinkwasser",
        "telephone" to "Telefon",
        "motor_craft_permitted" to "Motorboote erlaubt",
        "sport_craft_permitted" to "Sportboote erlaubt",
        "waterskiing_permitted" to "Wasserski erlaubt",
        "sailing_craft_permitted" to "Segelboote erlaubt",
        "unpowered_craft_permitted" to "Boote ohne Motor erlaubt",
        "sailboards_permitted" to "Segelsurfen erlaubt",
        "high_speeds_permitted" to "Schnelle Fahrt erlaubt",
        "launching_beaching_permitted" to "Einsetzen und Herausnehmen von Booten erlaubt",
        "waterbikes_permitted" to "Wassermotorräder erlaubt",
        "radio_information" to "Nautischer Informationsfunk",
        "speed_limit_ends" to "Ende der Geschwindigkeitsbegrenzung",
    )

    /** Für welche Fahrtrichtung oder Seite ein Schild gilt. */
    private val IMPACTS = mapOf(
        "upstream" to "zu Berg",
        "downstream" to "zu Tal",
        "left_bank" to "am linken Ufer",
        "right_bank" to "am rechten Ufer",
    )

    /**
     * Am Schild nur für die Kartenzeichner: Wie es steht und welche Tafeln und Dreiecke
     * daran hängen. Vom Boot aus sieht man das Schild selbst, die Angabe hilft nicht.
     */
    private val NOTICE_HIDDEN = setOf("orientation", "addition")

    /** Kennungen der Feuer, die Abkürzungen stehen so auf jeder Seekarte. */
    private val LIGHT_CHARACTER = mapOf(
        "F" to "Festfeuer", "Fl" to "Blitzfeuer", "LFl" to "langes Blitzfeuer",
        "Oc" to "Unterbrochenes Feuer", "Iso" to "Gleichtaktfeuer",
        "Q" to "Funkelfeuer", "VQ" to "schnelles Funkelfeuer",
        "IQ" to "unterbrochenes Funkelfeuer", "Mo" to "Morsefeuer",
        "Al" to "Wechselfeuer", "FFl" to "Fest- und Blitzfeuer",
    )

    private fun colours(v: String) =
        v.split(";").joinToString(", ") { COLOURS[it] ?: it }

    /** Mehrere Werte (`a;b`) übersetzen, Unbekanntes bleibt wie es ist. */
    private fun many(v: String, map: Map<String, String>) =
        v.split(";").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ") { map[it] ?: it }

    private fun impact(v: String): String {
        val teile = v.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        return "gilt " + teile.joinToString(" und ") { IMPACTS[it] ?: it }
    }

    /**
     * Ein Schild in der Form „Verbot: Wenden verboten". Ohne Funktion bleibt nur die
     * Bedeutung, ohne beides das allgemeine Wort.
     */
    internal fun noticeHeading(function: String?, category: String?): String {
        val f = function?.let { many(it, NOTICE_FUNCTIONS) }
        val c = category?.let { many(it, NOTICE_CATEGORIES) }
        return when {
            f != null && c != null -> "$f: $c"
            c != null -> c
            f != null -> f
            else -> TYPES.getValue("notice")
        }
    }

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
        // „gate". Die Schleuse daneben weiß Name, Zeiten und Telefon, sie ist gemeint.
        if (type == "gate" && tags["waterway"] == "lock_gate") return null
        // Brücken mit Maß haben ein eigenes Symbol auf der Karte und eine eigene
        // Auskunft. Zweimal am selben Punkt wäre einer davon nur im Weg.
        if (type == "bridge" &&
            (tags.containsKey("seamark:bridge:clearance_height") ||
                tags.containsKey("seamark:bridge:clearance_width"))
        ) {
            return null
        }

        val name = tags["seamark:name"] ?: tags["name"]
        val used = HashSet<String>(listOf("seamark:type", "seamark:name", "name"))

        fun take(key: String): String? {
            val v = tags[key] ?: return null
            used.add(key)
            return v
        }

        fun rawOf(): List<String> = tags.entries
            .filter { it.key.startsWith("seamark:") && it.key !in used }
            .sortedBy { it.key }
            .map { "${it.key.removePrefix("seamark:")} = ${it.value}" }

        if (type == "notice") return describeNotice(tags, name, ::take, used, ::rawOf)

        val title = buildString {
            append(TYPES[type] ?: type.replace('_', ' '))
            if (!name.isNullOrBlank()) append(" „").append(name).append('"')
        }
        val lines = ArrayList<String>()

        // Manche Tonne trägt zusätzlich die Merkmale einer Bake (und umgekehrt), in
        // Berlin mehrfach. Fehlt eine Angabe am eigenen Typ, gilt die des Geschwisters.
        val sibling = when {
            type.startsWith("buoy_") -> "beacon_" + type.removePrefix("buoy_")
            type.startsWith("beacon_") -> "buoy_" + type.removePrefix("beacon_")
            else -> null
        }

        fun attr(a: String): Pair<String, String>? {
            take("seamark:$type:$a")?.let { return type to it }
            if (sibling != null) take("seamark:$sibling:$a")?.let { return sibling to it }
            return null
        }

        // Art und Farbe, der Kern jedes Zeichens.
        attr("category")?.let { (_, c) ->
            lines.add(
                LATERAL_CATEGORIES[c] ?: CARDINALS[c]?.let { "$it-Kardinalzeichen" }
                    ?: many(c, NOTICE_CATEGORIES),
            )
        }
        attr("colour")?.let { (_, c) -> lines.add("Farbe: ${colours(c)}") }
        attr("shape")?.let { (von, s) ->
            lines.add((if (von.startsWith("beacon_")) BEACON_SHAPES[s] else BUOY_SHAPES[s]) ?: s)
        }
        // Das Betonnungssystem und das Farbmuster sagt die Farbe schon.
        attr("system")
        attr("colour_pattern")
        take("seamark:topmark:shape")?.let { s ->
            val c = take("seamark:topmark:colour")
            lines.add("Toppzeichen: ${TOPMARK_SHAPES[s] ?: s}" + if (c != null) " (${colours(c)})" else "")
        }
        take("seamark:daymark:shape")?.let { s ->
            val c = take("seamark:daymark:colour")
            lines.add("Tageszeichen: ${TOPMARK_SHAPES[s] ?: s}" + if (c != null) " (${colours(c)})" else "")
        }

        // Feuer: Kennung, Farbe, Wiederkehr.
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

        attr("information")?.let { (_, v) -> lines.add(v) }
        attr("national_information")?.let { (_, v) -> lines.add(v) }
        take("seamark:information")?.let { lines.add(it) }
        take("seamark:national_information")?.let { lines.add(it) }
        take("seamark:distance_mark:distance")?.let { lines.add("Kilometer $it") }

        return SeamarkInfo(title, lines, rawOf())
    }

    /**
     * Schifffahrtszeichen. An einem Mast hängen oft zwei oder drei Schilder, dann stehen
     * sie unter `notice:1:`, `notice:2:` und so weiter, sonst direkt unter `notice:`.
     */
    private fun describeNotice(
        tags: Map<String, String>,
        name: String?,
        take: (String) -> String?,
        used: MutableSet<String>,
        rawOf: () -> List<String>,
    ): SeamarkInfo {
        val nummern = tags.keys.mapNotNull {
            Regex("^seamark:notice:(\\d+):").find(it)?.groupValues?.get(1)?.toInt()
        }.distinct().sorted()

        fun schild(prefix: String): SeamarkGroup {
            val heading = noticeHeading(take("${prefix}function"), take("${prefix}category"))
            val lines = ArrayList<String>()
            take("${prefix}information")?.let { lines.add(it) }
            take("${prefix}national_information")?.let { lines.add(it) }
            take("${prefix}impact")?.let { lines.add(impact(it)) }
            tags.keys.filter { k -> k.startsWith(prefix) && NOTICE_HIDDEN.any { k == prefix + it } }
                .forEach { used.add(it) }
            return SeamarkGroup(heading, lines)
        }

        // Ein Schild ohne Nummer zählt nur, wenn es mehr trägt als Angaben zum Aufbau.
        val hatEinfach = tags.keys.any { k ->
            val rest = k.removePrefix("seamark:notice:")
            k.startsWith("seamark:notice:") && !rest.first().isDigit() && rest !in NOTICE_HIDDEN
        }
        val schilder = buildList {
            if (hatEinfach || nummern.isEmpty()) add(schild("seamark:notice:"))
            nummern.forEach { add(schild("seamark:notice:$it:")) }
        }
        // Tafeln und Dreiecke am Schild ohne Nummer, auch wenn es selbst nichts sagt.
        NOTICE_HIDDEN.forEach { take("seamark:notice:$it") }

        val kopf = listOfNotNull(name?.takeIf { it.isNotBlank() }?.let { "„$it\"" })
        val extra = listOfNotNull(take("seamark:information"), take("seamark:national_information"))
        if (schilder.size == 1) {
            val s = schilder.first()
            return SeamarkInfo(s.heading, kopf + s.lines + extra, rawOf())
        }
        return SeamarkInfo("${TYPES["notice"]} (${schilder.size})", kopf + extra, rawOf(), schilder)
    }
}
