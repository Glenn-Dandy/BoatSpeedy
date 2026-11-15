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

/**
 * Ein Schild von mehreren am selben Mast: was es ist ([function], etwa „Verbot") und was
 * es sagt ([heading], etwa „Ankern verboten").
 */
data class SeamarkGroup(val function: String?, val heading: String, val lines: List<String>)

/** Ein Seezeichen an der angetippten Stelle, in lesbarer Form. */
data class SeamarkInfo(
    val title: String,
    /** Zeilen in Klartext, so weit die Merkmale sicher zu deuten sind. */
    val lines: List<String>,
    /** Was nicht übersetzt werden konnte, roh, damit nichts verschwiegen wird. */
    val raw: List<String>,
    /** Mehrere Schilder an einem Mast (`notice:1:`, `notice:2:` …), je eins mit Überschrift. */
    val groups: List<SeamarkGroup> = emptyList(),
    /** Zweite Zeile unter dem Titel, fett: bei einem Schild seine Bedeutung. */
    val subtitle: String? = null,
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
        "small_craft_facility" to "Für Sportboote",
        "seabed_area" to "Gewässergrund",
        "gate" to "Tor",
        "signal_station_traffic" to "Signalstelle",
        "signal_station_warning" to "Warnsignalstelle",
        "signal_station" to "Signalstelle",
        "cable_overhead" to "Freileitung",
        "pipeline_overhead" to "Rohrleitung über dem Wasser",
        "weed" to "Bewuchs",
        "seagrass" to "Seegras",
        "rescue_station" to "Rettungsstation",
        "building" to "Gebäude",
        "anchor_berth" to "Ankerplatz",
        "crane" to "Kran",
        "buoy_installation" to "Anlagetonne",
        "waterway_gauge" to "Pegel",
        "calling-in_point" to "Meldepunkt",
        "shoreline_construction" to "Uferbauwerk",
        "sea_area" to "Gewässer",
        "radar_station" to "Radarstation",
        "radar_reflector" to "Radarreflektor",
        "pilot_boarding" to "Lotsenversetzpunkt",
        "fishing_facility" to "Fischereianlage",
        "daymark" to "Tageszeichen",
        "pylon" to "Mast",
        "radio_station" to "Funkstation",
        "virtual_aton" to "Virtuelles Seezeichen",
        "marine_farm" to "Aquakultur",
        "coastguard_station" to "Küstenwache",
        "minor_berth" to "Liegeplatz",
        "turning_basin" to "Wendebecken",
        "bunker_station" to "Bunkerstation",
        "pontoon" to "Ponton",
        "fog_signal" to "Nebelsignal",
        "tank" to "Tank",
        "dam" to "Damm",
        "buoy" to "Tonne",
        "light" to "Leuchtfeuer",
        "light_float" to "Leuchttonne",
    )

    private val COLOURS = mapOf(
        "red" to "rot", "green" to "grün", "yellow" to "gelb", "black" to "schwarz",
        "white" to "weiß", "blue" to "blau", "orange" to "orange", "grey" to "grau",
        "brown" to "braun", "amber" to "bernstein", "violet" to "violett",
        "magenta" to "magenta",
        "pink" to "rosa",
    )

    private val BUOY_SHAPES = mapOf(
        "conical" to "Spitztonne", "can" to "Stumpftonne", "spherical" to "Kugeltonne",
        "pillar" to "Bakentonne", "spar" to "Spierentonne", "barrel" to "Fasstonne",
        "super-buoy" to "Großtonne", "ice-buoy" to "Eistonne",
        "sphere" to "Kugeltonne",
    )

    private val BEACON_SHAPES = mapOf(
        "stake" to "Stangenbake", "pole" to "Stangenbake", "pile" to "Pfahlbake",
        "withy" to "Pricke", "tower" to "Turmbake", "lattice" to "Gitterbake",
        "cairn" to "Steinbake", "buoyant" to "schwimmende Bake",
        "perch" to "Pricke", "post" to "Pfahlbake", "dolphin" to "Dalbe",
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
        "sphere over rhombus" to "Kugel über Raute",
        "cylinder over sphere" to "Zylinder über Kugel",
        "cone, point up over sphere" to "Kegel, Spitze oben, über Kugel",
        "besom, point up" to "Besen, Spitze oben",
        "besom, point down" to "Besen, Spitze unten",
        "rectangle, vertical" to "Rechteck, senkrecht",
        "rectangle, horizontal" to "Rechteck, waagerecht",
        "circle" to "Kreis",
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
        "channel_left_bank" to "Fahrrinne am linken Ufer",
        "channel_right_bank" to "Fahrrinne am rechten Ufer",
        "crossover_left" to "Übergang der Fahrrinne zum linken Ufer",
        "crossover_right" to "Übergang der Fahrrinne zum rechten Ufer",
        "waterway_separation" to "Wasserstraßenteilung",
        "lateral_port" to "Backbord",
        "lateral_starboard" to "Steuerbord",
    )

    /** Was ein Schild ist: die fünf Gruppen der Binnenschifffahrtszeichen (A bis E). */
    private val NOTICE_FUNCTIONS = mapOf(
        "prohibition" to "Verbot",
        "regulation" to "Gebot",
        "restriction" to "Einschränkung",
        "recommendation" to "Empfehlung",
        "information" to "Hinweis",
        "recommandation" to "Empfehlung",
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
        // Kommen so in den Daten vor, meist als Schreibvariante.
        "berthing_lateral_limits" to "Liegen erlaubt in der angegebenen Breite",
        "berthing_rafting_limit" to "Liegen erlaubt, Fahrzeuge nebeneinander höchstens",
        "no_swimming" to "Baden verboten",
        "entry_permitted" to "Einfahrt erlaubt",
        "lock" to "Schleuse",
        "use_shore_power" to "Landstrom benutzen",
        "restricted_area" to "Sperrgebiet",
        "kitesurfing_permitted" to "Kitesurfen erlaubt",
        "no_kitesurfing" to "Kitesurfen verboten",
        "sailing_craft" to "Segelboote",
        "sport_craft" to "Sportboote",
        "sports_craft" to "Sportboote",
        "motor_craft" to "Motorboote",
        "unpowered_craft" to "Boote ohne Motor",
        "waterskiing" to "Wasserski",
        "waterbikes" to "Wassermotorräder",
    )

    /** Für welche Fahrtrichtung oder Seite ein Schild gilt. */
    private val IMPACTS = mapOf(
        "upstream" to "zu Berg",
        "downstream" to "zu Tal",
        "left_bank" to "am linken Ufer",
        "right_bank" to "am rechten Ufer",
        "to_harbour" to "in den Hafen",
        "from_harbour" to "aus dem Hafen",
        "bank_to_bank" to "von Ufer zu Ufer",
    )

    /** Arten der übrigen Zeichen, je Typ. Sonderzeichen gelten für Tonne und Bake. */
    private val TYPE_CATEGORIES: Map<String, Map<String, String>> = mapOf(
        "small_craft_facility" to mapOf(
            "slipway" to "Slipanlage", "visitor_berth" to "Gastliegeplatz",
            "visitors_mooring" to "Gastfestmacher", "gelbe_welle" to "Gastliegeplatz (Gelbe Welle)",
            "toilets" to "Toiletten", "showers" to "Duschen", "fuel_station" to "Tankstelle",
            "berth" to "Liegeplatz", "pump-out" to "Fäkalienabsaugung", "boat_hoist" to "Bootskran",
            "electricity" to "Landstrom", "laundrette" to "Waschmaschine",
            "launderette" to "Waschmaschine", "boatyard" to "Bootswerft",
            "chandler" to "Bootszubehör", "water_tap" to "Trinkwasser", "water" to "Trinkwasser",
            "refuse_bin" to "Müllentsorgung", "nautical_club" to "Wassersportverein",
            "sailmaker" to "Segelmacher", "restaurant" to "Restaurant", "public_inn" to "Gaststätte",
            "provisions" to "Lebensmittel", "picnic_area" to "Picknickplatz",
            "car_park" to "Parkplatz", "bottle_gas" to "Gasflaschen",
            "mechanics_workshop" to "Motorenwerkstatt", "crane" to "Kran",
            "emergency_telephone" to "Notruftelefon", "kitchen" to "Küche",
        ),
        "mooring" to mapOf(
            "dolphin" to "Dalbe", "deviation_dolphin" to "Deviationsdalbe", "pile" to "Pfahl",
            "post" to "Pfosten", "bollard" to "Poller", "buoy" to "Festmachetonne",
            "wall" to "Kaimauer", "ring" to "Festmachering", "chain" to "Kette",
        ),
        "pile" to mapOf("post" to "Pfosten", "stake" to "Pfahl", "bollard" to "Poller"),
        "landmark" to mapOf(
            "windmotor" to "Windrad", "mast" to "Mast", "tower" to "Turm",
            "flagstaff" to "Flaggenmast", "monument" to "Denkmal", "chimney" to "Schornstein",
            "dish_aerial" to "Parabolantenne", "windsock" to "Windsack", "windmill" to "Windmühle",
            "spire" to "Turmspitze", "column" to "Säule", "cross" to "Kreuz", "statue" to "Statue",
            "dome" to "Kuppel", "cairn" to "Steinmal", "boulder" to "Findling",
        ),
        "harbour" to mapOf(
            "marina" to "Sportboothafen", "marina_no_facilities" to "Sportboothafen ohne Versorgung",
            "harbour" to "Hafen", "ferry" to "Fährhafen", "fishing" to "Fischereihafen",
            "passenger" to "Fahrgasthafen", "cargo" to "Frachthafen", "bulk" to "Massenguthafen",
            "tanker" to "Tankerhafen", "container" to "Containerhafen", "roro" to "RoRo-Hafen",
            "shipyard" to "Werft", "naval" to "Marinehafen", "commercial" to "Handelshafen",
        ),
        "bridge" to mapOf(
            "fixed" to "feste Brücke", "opening" to "bewegliche Brücke", "swing" to "Drehbrücke",
            "bascule" to "Klappbrücke", "lifting" to "Hubbrücke", "drawbridge" to "Zugbrücke",
            "footbridge" to "Fußgängerbrücke", "retractable" to "Schiebebrücke",
            "pontoon" to "Pontonbrücke",
        ),
        "wreck" to mapOf(
            "dangerous" to "gefährlich", "non-dangerous" to "ungefährlich",
            "distributed_remains" to "verstreute Reste", "hull_showing" to "Rumpf sichtbar",
            "mast_showing" to "Mast sichtbar",
        ),
        "obstruction" to mapOf(
            "foul_ground" to "unreiner Grund", "foul_area" to "unreiner Grund",
            "stump" to "Baumstumpf", "explosives" to "Munition", "reef" to "Riff", "wreck" to "Wrack",
        ),
        "signal_station_traffic" to mapOf(
            "lock" to "Schleusensignal", "bridge_passage" to "Brückensignal",
            "bridge" to "Brückensignal", "traffic_control_light" to "Verkehrsregelung",
            "traffic_control" to "Verkehrsregelung", "port_entry_departure" to "Hafensignal",
            "flood_barrage" to "Sperrwerk", "dock" to "Docksignal", "port_control" to "Hafenaufsicht",
        ),
        "signal_station" to mapOf("lock" to "Schleusensignal"),
        "signal_station_warning" to mapOf(
            "vertical_clearance" to "Durchfahrtshöhe", "depth" to "Wassertiefe",
            "tide_gauge" to "Tidepegel", "tide_scale" to "Pegellatte", "storm" to "Sturmwarnung",
            "water_level_gauge" to "Pegel", "level" to "Wasserstand", "current" to "Strömung",
            "military" to "militärisch",
        ),
        "special_purpose" to mapOf(
            "warning" to "Warnzeichen", "recreation_zone" to "Badezone", "recreational" to "Freizeit",
            "yachting" to "Wassersport", "recording" to "Messstelle", "odas" to "Messboje",
            "anchorage" to "Ankerplatz", "anchoring" to "Ankerplatz", "mooring" to "Festmacher",
            "berthing" to "Liegeplatz", "unknown_purpose" to "Zweck unbekannt", "cable" to "Kabel",
            "pipeline" to "Leitung", "outfall" to "Auslass", "firing_danger_area" to "Schießgebiet",
            "notice" to "Hinweis", "restricted_entry" to "Einfahrt beschränkt",
            "restricted_vertical_clearance" to "Begrenzte Durchfahrtshöhe",
            "marine_farm" to "Aquakultur", "tss" to "Verkehrstrennung",
            "spoil_ground" to "Verklappungsgebiet", "nature_reserve" to "Naturschutzgebiet",
            "foul_ground" to "unreiner Grund", "target" to "Zielgebiet", "diving" to "Tauchgebiet",
            "work_in_progress" to "Baustelle", "leading" to "Leitzeichen",
            "maximum_vessel_draught" to "Größter Tiefgang", "refuge_beacon" to "Rettungsbake",
            "customs" to "Zoll", "private" to "privat", "control" to "Kontrolle",
            "ferry_crossing" to "Fährstelle",
        ),
        "rescue_station" to mapOf(
            "lifeboat_on_mooring" to "Rettungsboot an der Boje", "lifeboat" to "Rettungsboot",
            "first_aid" to "Erste Hilfe",
        ),
        "waterway_gauge" to mapOf(
            "recording_remote_access" to "Fernpegel",
            "recording_remote_access_indicator" to "Fernpegel mit Anzeige",
            "recording_external_indicator" to "Pegel mit Anzeige",
            "recording" to "Schreibpegel", "staff" to "Pegellatte",
        ),
        "anchor_berth" to mapOf(
            "small_craft" to "für Sportboote", "tanker" to "für Tanker",
            "dry_cargo" to "für Trockenfracht", "limited_period" to "begrenzte Dauer",
            "unrestricted" to "ohne Beschränkung", "24_hour" to "24 Stunden", "48_hour" to "48 Stunden",
        ),
        "gate" to mapOf(
            "flood_barrage" to "Sperrwerk", "lock" to "Schleusentor", "sluice" to "Siel",
            "sluice_gate" to "Siel", "dyke" to "Deichtor",
        ),
        "cable_overhead" to mapOf("power" to "Stromleitung", "ferry" to "Fährseil"),
        "pylon" to mapOf("power" to "Strommast", "ferry" to "Fährmast"),
        "berth" to mapOf(
            "loading" to "Ladestelle", "unloading" to "Entladestelle",
            "passenger" to "Fahrgastanleger", "vehicle_loading" to "Fahrzeugverladung",
            "police" to "Polizei", "overnight_accommodation" to "Übernachtung",
        ),
        "weed" to mapOf("sea_grass" to "Seegras", "sea_weed" to "Seetang"),
        "shoreline_construction" to mapOf("fender" to "Fender", "slipway" to "Slipanlage", "ramp" to "Rampe"),
        "fishing_facility" to mapOf("stake" to "Fischpfahl", "trap" to "Reuse"),
        "radar_station" to mapOf("surveillance" to "Überwachung", "coast" to "Küstenradar"),
    )

    /** Die Arten für einen Typ. Sonderzeichen teilen sich Tonne und Bake. */
    private fun typeCategories(type: String): Map<String, String>? = when {
        type.endsWith("_special_purpose") -> TYPE_CATEGORIES["special_purpose"]
        type == "anchorage" -> TYPE_CATEGORIES["anchor_berth"]
        else -> TYPE_CATEGORIES[type]
    }

    /** Wie weit ein Stein, Wrack oder Festmacher aus dem Wasser ragt. */
    private val WATER_LEVELS = mapOf(
        "submerged" to "unter Wasser", "always_submerged" to "immer unter Wasser",
        "dry" to "über Wasser", "above_mwl" to "über Mittelwasser",
        "awash" to "in Höhe der Wasserlinie", "covers" to "fällt trocken",
        "floods" to "zeitweise überflutet", "floating" to "schwimmend",
        "partly-submerged" to "teilweise unter Wasser",
    )

    /** Radar: auffällig oder mit Reflektor. */
    private val REFLECTIVITY = mapOf(
        "conspicuous" to "radarauffällig", "reflector" to "mit Radarreflektor",
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

    /* ------------------------- Englisch ------------------------- */

    /**
     * Die OSM-Werte sind selbst englisch. Für Englisch genügt meist der Wert ohne
     * Unterstrich (`no_turning` wird „No turning"), nur wo das holpert, steht hier eine
     * eigene Fassung. Bekannt sein muss der Wert trotzdem in der deutschen Liste, sonst
     * gilt er als unübersetzt.
     */
    private val EN_TYPES = mapOf(
        "buoy_lateral" to "Lateral buoy", "buoy_cardinal" to "Cardinal buoy",
        "buoy_safe_water" to "Safe water buoy", "buoy_special_purpose" to "Special purpose buoy",
        "buoy_isolated_danger" to "Isolated danger buoy", "beacon_lateral" to "Lateral beacon",
        "beacon_cardinal" to "Cardinal beacon", "beacon_safe_water" to "Safe water beacon",
        "beacon_special_purpose" to "Special purpose beacon",
        "beacon_isolated_danger" to "Isolated danger beacon",
        "light_major" to "Light", "light_minor" to "Light", "notice" to "Notice mark",
        "lock_basin" to "Lock", "small_craft_facility" to "For small craft",
        "calling-in_point" to "Calling-in point", "virtual_aton" to "Virtual aid to navigation",
        "signal_station_traffic" to "Traffic signal station",
        "signal_station_warning" to "Warning signal station",
        "cable_overhead" to "Overhead cable", "cable_submarine" to "Submarine cable",
        "pipeline_submarine" to "Submarine pipeline", "pipeline_overhead" to "Overhead pipeline",
        "buoy_installation" to "Installation buoy", "waterway_gauge" to "Gauge",
        "seabed_area" to "Seabed", "pile" to "Pile", "obstruction" to "Obstruction",
    )
    private val EN_BUOY_SHAPES = mapOf(
        "conical" to "Conical buoy", "can" to "Can buoy", "spherical" to "Spherical buoy",
        "sphere" to "Spherical buoy", "pillar" to "Pillar buoy", "spar" to "Spar buoy",
        "barrel" to "Barrel buoy", "super-buoy" to "Super buoy", "ice-buoy" to "Ice buoy",
    )
    private val EN_BEACON_SHAPES = mapOf(
        "pile" to "Pile beacon", "tower" to "Beacon tower", "lattice" to "Lattice beacon",
        "buoyant" to "Buoyant beacon",
    )
    private val EN_LATERAL = mapOf(
        "preferred_channel_port" to "Port, preferred channel",
        "preferred_channel_starboard" to "Starboard, preferred channel",
        "channel_left" to "Channel, left side", "channel_right" to "Channel, right side",
        "waterway_left" to "Waterway, left side", "waterway_right" to "Waterway, right side",
        "danger_left" to "Danger point, left side", "danger_right" to "Danger point, right side",
        "channel_left_bank" to "Channel near the left bank",
        "channel_right_bank" to "Channel near the right bank",
        "crossover_left" to "Channel crosses to the left bank",
        "crossover_right" to "Channel crosses to the right bank",
        "turnoff_left" to "Turn-off left", "turnoff_right" to "Turn-off right",
        "harbour_left" to "Harbour entrance left", "harbour_right" to "Harbour entrance right",
        "lateral_port" to "Port", "lateral_starboard" to "Starboard",
    )
    private val EN_NOTICE = mapOf(
        "no_passing" to "No passing or overtaking", "no_mooring" to "No mooring to the bank",
        "no_wash" to "Avoid wash", "no_passage_left" to "No passage on the left",
        "no_passage_right" to "No passage on the right", "no_sport_craft" to "No pleasure craft",
        "no_sports_craft" to "No pleasure craft", "no_high_speeds" to "No high-speed craft",
        "no_launching_beaching" to "No launching or beaching", "no_waterbikes" to "No water bikes",
        "no_berthing_lateral_limit" to "No berthing within the width shown",
        "no_sailboards" to "No windsurfing", "no_convoy_overtaking" to "No overtaking of convoys",
        "move_to_left" to "Move to the left", "move_to_right" to "Move to the right",
        "move_to_port" to "Move to port", "move_to_starboard" to "Move to starboard",
        "keep_to_port" to "Keep to port", "keep_to_starboard" to "Keep to starboard",
        "keep_to_left" to "Keep to the left", "keep_to_right" to "Keep to the right",
        "cross_to_port" to "Cross to port", "cross_to_starboard" to "Cross to starboard",
        "cross_to_left" to "Cross to the left", "cross_to_right" to "Cross to the right",
        "proceed_to_left" to "Proceed to the left", "proceed_to_right" to "Proceed to the right",
        "keep_lookout" to "Keep a sharp lookout",
        "give_way_junction" to "Give way at the junction",
        "give_way_crossing" to "Give way at the crossing",
        "limited_width" to "Limited channel width",
        "channel_distance_left" to "Channel at a distance from the left bank",
        "channel_distance_right" to "Channel at a distance from the right bank",
        "channel_two_way" to "Two-way traffic", "channel_one_way" to "One-way traffic",
        "opening_to_left" to "Recommended passage on the left",
        "opening_to_right" to "Recommended passage on the right",
        "prohibition_ends" to "End of prohibition", "power_cable" to "Power line",
        "ferry_non_independent" to "Ferry, not moving independently",
        "ferry_independent" to "Ferry, moving independently",
        "berthing_lateral_limit" to "Berthing permitted within the width shown",
        "berthing_lateral_limits" to "Berthing permitted within the width shown",
        "berth_rafting_limit" to "Berthing permitted, maximum abreast",
        "berthing_rafting_limit" to "Berthing permitted, maximum abreast",
        "berthing_unmarked" to "Berth for vessels without a blue cone",
        "berthing_marked_1" to "Berth for vessels with one blue cone",
        "berthing_marked_2" to "Berth for vessels with two blue cones",
        "berthing_marked_3" to "Berth for vessels with three blue cones",
        "berthing_unmarked_pushing" to "Berth for pushed convoys without a blue cone",
        "berthing_marked_pushing_1" to "Berth for pushed convoys with one blue cone",
        "berthing_marked_pushing_2" to "Berth for pushed convoys with two blue cones",
        "berthing_marked_pushing_3" to "Berth for pushed convoys with three blue cones",
        "berthing_unmarked_non-pushing" to "Berth without a blue cone, except pushed convoys",
        "berthing_marked_non-pushing_1" to "Berth with one blue cone, except pushed convoys",
        "berthing_marked_non-pushing_2" to "Berth with two blue cones, except pushed convoys",
        "berthing_marked_non-pushing_3" to "Berth with three blue cones, except pushed convoys",
        "secondary_waterway_crossing" to "Crossing with a secondary waterway",
        "secondary_waterway_right" to "Secondary waterway on the right",
        "secondary_waterway_left" to "Secondary waterway on the left",
        "main_waterway_crossing" to "Crossing with a main waterway",
        "main_waterway_right" to "Main waterway on the right",
        "main_waterway_left" to "Main waterway on the left",
        "main_waterway_junction" to "Junction with a main waterway",
        "secondary_waterway_junction" to "Junction with a secondary waterway",
        "sport_craft_permitted" to "Pleasure craft permitted",
        "sailboards_permitted" to "Windsurfing permitted",
        "high_speeds_permitted" to "High-speed craft permitted",
        "launching_beaching_permitted" to "Launching and beaching permitted",
        "waterbikes_permitted" to "Water bikes permitted",
        "radio_information" to "Nautical radio information",
        "sport_craft" to "Pleasure craft", "sports_craft" to "Pleasure craft",
        "waterskiing" to "Water skiing", "waterskiing_permitted" to "Water skiing permitted",
        "no_waterskiing" to "No water skiing", "waterbikes" to "Water bikes",
        "use_shore_power" to "Use shore power",
    )
    private val EN_FUNCTIONS = mapOf("recommandation" to "Recommendation")
    private val EN_IMPACTS = mapOf(
        "left_bank" to "on the left bank", "right_bank" to "on the right bank",
        "to_harbour" to "into the harbour", "from_harbour" to "out of the harbour",
    )
    private val EN_WATER_LEVELS = mapOf(
        "covers" to "covers and uncovers", "above_mwl" to "above mean water level",
    )
    private val EN_REFLECTIVITY = mapOf(
        "conspicuous" to "radar conspicuous", "reflector" to "with radar reflector",
    )
    private val EN_TYPE_CATEGORIES: Map<String, Map<String, String>> = mapOf(
        "small_craft_facility" to mapOf(
            "gelbe_welle" to "Visitor berth (Gelbe Welle)", "electricity" to "Shore power",
            "chandler" to "Chandlery", "water_tap" to "Drinking water", "water" to "Drinking water",
            "refuse_bin" to "Waste disposal", "nautical_club" to "Water sports club",
            "public_inn" to "Inn", "boat_hoist" to "Boat hoist", "pump-out" to "Pump-out",
        ),
        "mooring" to mapOf(
            "buoy" to "Mooring buoy", "wall" to "Quay wall", "ring" to "Mooring ring",
        ),
        "harbour" to mapOf(
            "marina_no_facilities" to "Marina without facilities", "ferry" to "Ferry port",
            "fishing" to "Fishing port", "passenger" to "Passenger port", "cargo" to "Cargo port",
            "bulk" to "Bulk port", "tanker" to "Tanker port", "container" to "Container port",
            "roro" to "RoRo port", "naval" to "Naval port", "commercial" to "Commercial port",
        ),
        "bridge" to mapOf(
            "fixed" to "Fixed bridge", "opening" to "Opening bridge", "swing" to "Swing bridge",
            "bascule" to "Bascule bridge", "lifting" to "Lift bridge",
            "retractable" to "Retractable bridge", "pontoon" to "Pontoon bridge",
        ),
        "signal_station_traffic" to mapOf(
            "lock" to "Lock signal", "bridge_passage" to "Bridge signal", "bridge" to "Bridge signal",
            "traffic_control_light" to "Traffic control", "port_entry_departure" to "Port entry and departure",
            "dock" to "Dock signal",
        ),
        "signal_station" to mapOf("lock" to "Lock signal"),
        "special_purpose" to mapOf(
            "odas" to "Data buoy", "tss" to "Traffic separation",
            "maximum_vessel_draught" to "Maximum draught", "foul_ground" to "Foul ground",
        ),
        "waterway_gauge" to mapOf(
            "recording_remote_access" to "Remote reading gauge",
            "recording_remote_access_indicator" to "Remote reading gauge with display",
            "recording_external_indicator" to "Gauge with display",
            "recording" to "Recording gauge", "staff" to "Gauge staff",
        ),
        "anchor_berth" to mapOf(
            "small_craft" to "For small craft", "tanker" to "For tankers",
            "dry_cargo" to "For dry cargo", "24_hour" to "24 hours", "48_hour" to "48 hours",
        ),
        "gate" to mapOf("lock" to "Lock gate", "dyke" to "Dyke gate"),
        "cable_overhead" to mapOf("power" to "Power line", "ferry" to "Ferry cable"),
        "pylon" to mapOf("power" to "Power pylon", "ferry" to "Ferry pylon"),
        "fishing_facility" to mapOf("stake" to "Fishing stake", "trap" to "Fish trap"),
        "radar_station" to mapOf("coast" to "Coastal radar"),
        "obstruction" to mapOf("foul_ground" to "Foul ground", "foul_area" to "Foul ground"),
    )

    /** Je deutscher Liste die englischen Ausnahmen; klein bleiben Farben und Zustände. */
    private val EN_OVERRIDES: java.util.IdentityHashMap<Map<String, String>, Map<String, String>> by lazy {
        java.util.IdentityHashMap<Map<String, String>, Map<String, String>>().apply {
            put(TYPES, EN_TYPES); put(BUOY_SHAPES, EN_BUOY_SHAPES)
            put(BEACON_SHAPES, EN_BEACON_SHAPES); put(LATERAL_CATEGORIES, EN_LATERAL)
            put(NOTICE_CATEGORIES, EN_NOTICE); put(NOTICE_FUNCTIONS, EN_FUNCTIONS)
            put(IMPACTS, EN_IMPACTS); put(WATER_LEVELS, EN_WATER_LEVELS)
            put(REFLECTIVITY, EN_REFLECTIVITY)
            TYPE_CATEGORIES.forEach { (t, m) -> put(m, EN_TYPE_CATEGORIES[t].orEmpty()) }
        }
    }
    private val EN_LOWERCASE: Set<Map<String, String>> by lazy {
        java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Map<String, String>, Boolean>()).apply {
            add(COLOURS); add(IMPACTS); add(WATER_LEVELS); add(REFLECTIVITY); add(TOPMARK_SHAPES)
        }
    }

    /** Feste Wörter der Auskunft. */
    private class Words(val en: Boolean) {
        fun w(de: String, en: String) = if (this.en) en else de
        val colour get() = w("Farbe", "Colour")
        val topmark get() = w("Toppzeichen", "Topmark")
        val daymark get() = w("Tageszeichen", "Daymark")
        val light get() = w("Feuer", "Light")
        val period get() = w("Wiederkehr", "period")
        val range get() = w("Tragweite", "Range")
        val nm get() = w("sm", "nm")
        val km get() = w("Kilometer", "km")
        val applies get() = w("gilt", "applies")
        val and get() = w("und", "and")
        fun cardinal(dir: String) = w("$dir-Kardinalzeichen", "$dir cardinal mark")
        fun quoted(name: String) = w("„$name\"", "“$name”")
    }

    private val CARDINALS_EN = mapOf("north" to "North", "east" to "East", "south" to "South", "west" to "West")

    private val LIGHT_CHARACTER_EN = mapOf(
        "F" to "fixed", "Fl" to "flashing", "LFl" to "long-flashing", "Oc" to "occulting",
        "Iso" to "isophase", "Q" to "quick", "VQ" to "very quick", "IQ" to "interrupted quick",
        "Mo" to "Morse code", "Al" to "alternating", "FFl" to "fixed and flashing",
    )

    /** Sprache der laufenden Übersetzung, für [describe] gesetzt. */
    private val english = ThreadLocal.withInitial { false }

    private fun colours(v: String) =
        v.split(";").joinToString(", ") { lookup(COLOURS, it) ?: readable(it) }

    /**
     * Schlüssel ohne Unterschied zwischen `-` und `_` und in Kleinschrift: In den Daten
     * steht `berthing_unmarked_non-pushing` neben `berthing_unmarked_non_pushing`.
     */
    private fun norm(v: String) = v.trim().lowercase().replace('-', '_')

    private val normalized = java.util.IdentityHashMap<Map<String, String>, Map<String, String>>()

    private fun normalizedOf(map: Map<String, String>): Map<String, String> =
        synchronized(normalized) { normalized.getOrPut(map) { map.mapKeys { norm(it.key) } } }

    /** Übersetzung eines Werts aus einer der Listen, `null` wenn er dort nicht steht. */
    private fun lookup(map: Map<String, String>, v: String): String? {
        val de = map[v.trim()] ?: normalizedOf(map)[norm(v)] ?: return null
        if (!english.get()) return de
        val over = EN_OVERRIDES[map]
        over?.let { o -> (o[v.trim()] ?: normalizedOf(o)[norm(v)])?.let { return it } }
        val plain = readable(v)
        return if (map in EN_LOWERCASE) plain else plain.replaceFirstChar { it.uppercase() }
    }

    /**
     * Unbekanntes bleibt stehen, aber lesbar: `sailing_craft` wird `sailing craft`.
     * Eine erfundene Bedeutung wäre schlimmer als ein englisches Wort.
     */
    private fun readable(v: String) = v.trim().replace('_', ' ')

    /** Mehrere Werte (`a;b`) übersetzen, Unbekanntes bleibt lesbar stehen. */
    private fun many(v: String, map: Map<String, String>) =
        v.split(";").map { it.trim() }.filter { it.isNotEmpty() }
            .joinToString(", ") { lookup(map, it) ?: readable(it) }

    /**
     * Für welche Richtung ein Schild gilt. Steht dort freier Text wie „gesamte Wakenitz",
     * bleibt er, wie er ist.
     */
    private fun impact(v: String): String {
        val teile = v.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        val de = teile.map { lookup(IMPACTS, it) }
        val w = Words(english.get())
        return if (de.all { it != null }) "${w.applies} " + de.joinToString(" ${w.and} ") else v.trim()
    }

    /**
     * Ein Schild als Paar aus Funktion („Verbot") und Bedeutung („Wenden verboten"). Ohne
     * Bedeutung bleibt nur die Funktion, ohne beides das allgemeine Wort.
     */
    internal fun noticeHeading(function: String?, category: String?): Pair<String?, String> {
        val f = function?.let { many(it, NOTICE_FUNCTIONS) }
        val c = category?.let { many(it, NOTICE_CATEGORIES) }
        return when {
            c != null -> f to c
            f != null -> null to f
            else -> null to lookup(TYPES, "notice")!!
        }
    }

    /**
     * Baut aus den Merkmalen eines Zeichens lesbare Zeilen.
     *
     * Was nicht sicher zu deuten ist, landet **roh** in [SeamarkInfo.raw] statt
     * weggelassen oder geraten zu werden: Bei einem Zeichen auf dem Wasser ist eine
     * erfundene Bedeutung schlimmer als ein unübersetztes Kürzel.
     */
    fun describe(tags: Map<String, String>, inEnglish: Boolean = false): SeamarkInfo? {
        val vorher = english.get()
        english.set(inEnglish)
        try {
            return describeIn(tags, Words(inEnglish))
        } finally {
            english.set(vorher)
        }
    }

    private fun describeIn(tags: Map<String, String>, w: Words): SeamarkInfo? {
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

        if (type == "notice") return describeNotice(tags, name, ::take, used, ::rawOf, w)

        val title = buildString {
            append(lookup(TYPES, type) ?: readable(type))
            if (!name.isNullOrBlank()) append(' ').append(w.quoted(name))
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
            val eigene = typeCategories(type)
            lines.add(
                lookup(LATERAL_CATEGORIES, c)
                    ?: (if (w.en) CARDINALS_EN[c] else CARDINALS[c])?.let { w.cardinal(it) }
                    ?: eigene?.let { many(c, it) } ?: many(c, NOTICE_CATEGORIES),
            )
        }
        attr("colour")?.let { (_, c) -> lines.add("${w.colour}: ${colours(c)}") }
        attr("shape")?.let { (von, s) ->
            lines.add(lookup(if (von.startsWith("beacon_")) BEACON_SHAPES else BUOY_SHAPES, s) ?: readable(s))
        }
        attr("water_level")?.let { (_, w) -> lines.add(lookup(WATER_LEVELS, w) ?: readable(w)) }
        attr("reflectivity")?.let { (von, r) ->
            val de = lookup(REFLECTIVITY, r)
            if (de != null) lines.add(de) else used.remove("seamark:$von:reflectivity")
        }
        // Das Betonnungssystem und das Farbmuster sagt die Farbe schon.
        attr("system")
        attr("colour_pattern")
        take("seamark:topmark:shape")?.let { s ->
            val c = take("seamark:topmark:colour")
            lines.add("${w.topmark}: ${lookup(TOPMARK_SHAPES, s) ?: readable(s)}" + if (c != null) " (${colours(c)})" else "")
        }
        take("seamark:daymark:shape")?.let { s ->
            val c = take("seamark:daymark:colour")
            lines.add("${w.daymark}: ${lookup(TOPMARK_SHAPES, s) ?: readable(s)}" + if (c != null) " (${colours(c)})" else "")
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
                if (period != null) append(", ${w.period} ").append(period).append(" s")
            }
            lines.add("${w.light}: $short")
            (if (w.en) LIGHT_CHARACTER_EN else LIGHT_CHARACTER)[character]?.let { lines.add("  $it") }
            if (range != null) lines.add("  ${w.range} $range ${w.nm}")
        }

        attr("information")?.let { (_, v) -> lines.add(v) }
        attr("national_information")?.let { (_, v) -> lines.add(v) }
        take("seamark:information")?.let { lines.add(it) }
        take("seamark:national_information")?.let { lines.add(it) }
        take("seamark:distance_mark:distance")?.let { lines.add("${w.km} $it") }

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
        w: Words,
    ): SeamarkInfo {
        val nummern = tags.keys.mapNotNull {
            Regex("^seamark:notice:(\\d+):").find(it)?.groupValues?.get(1)?.toInt()
        }.distinct().sorted()

        fun schild(prefix: String): SeamarkGroup {
            val (function, heading) = noticeHeading(take("${prefix}function"), take("${prefix}category"))
            val lines = ArrayList<String>()
            take("${prefix}information")?.let { lines.add(it) }
            take("${prefix}national_information")?.let { lines.add(it) }
            take("${prefix}impact")?.let { lines.add(impact(it)) }
            take("${prefix}system")
            tags.keys.filter { k -> k.startsWith(prefix) && NOTICE_HIDDEN.any { k == prefix + it } }
                .forEach { used.add(it) }
            return SeamarkGroup(function, heading, lines)
        }

        // Ein Schild ohne Nummer zählt nur, wenn es mehr trägt als Angaben zum Aufbau.
        val hatEinfach = tags.keys.any { k ->
            val rest = k.removePrefix("seamark:notice:")
            k.startsWith("seamark:notice:") && !rest.first().isDigit() && rest !in NOTICE_HIDDEN &&
                rest != "system"
        }
        val schilder = buildList {
            if (hatEinfach || nummern.isEmpty()) add(schild("seamark:notice:"))
            nummern.forEach { add(schild("seamark:notice:$it:")) }
        }
        // Tafeln und Dreiecke am Schild ohne Nummer, auch wenn es selbst nichts sagt.
        NOTICE_HIDDEN.forEach { take("seamark:notice:$it") }

        val kopf = listOfNotNull(name?.takeIf { it.isNotBlank() }?.let { w.quoted(it) })
        val extra = listOfNotNull(take("seamark:information"), take("seamark:national_information"))
        // Das Schildersystem (fast immer CEVNI) sagt das Schild selbst.
        take("seamark:notice:system")
        if (schilder.size == 1) {
            val s = schilder.first()
            return if (s.function != null) {
                SeamarkInfo(s.function, kopf + s.lines + extra, rawOf(), subtitle = s.heading)
            } else {
                SeamarkInfo(s.heading, kopf + s.lines + extra, rawOf())
            }
        }
        val mehrere = if (w.en) "Notice marks" else TYPES.getValue("notice")
        return SeamarkInfo("$mehrere (${schilder.size})", kopf + extra, rawOf(), schilder)
    }
}
