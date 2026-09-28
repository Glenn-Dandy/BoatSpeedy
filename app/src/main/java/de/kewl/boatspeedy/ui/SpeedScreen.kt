package de.kewl.boatspeedy.ui

import android.os.SystemClock
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.Job
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.round
import kotlinx.coroutines.launch
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.battery.BatteryData
import de.kewl.boatspeedy.battery.ChargeState
import de.kewl.boatspeedy.battery.RangeEstimate
import de.kewl.boatspeedy.data.Settings
import de.kewl.boatspeedy.location.GpsState
import de.kewl.boatspeedy.trip.TrackPoint
import de.kewl.boatspeedy.trip.TripStats
import de.kewl.boatspeedy.ui.theme.SpeedTextStyle
import de.kewl.boatspeedy.ui.theme.StatusGood
import de.kewl.boatspeedy.ui.theme.StatusNone
import de.kewl.boatspeedy.ui.theme.StatusWeak
import de.kewl.boatspeedy.weather.WeatherWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PLACEHOLDER = "--"

/** Eine wählbare Batterie-Anzeige auf dem Dashboard (id == Adresse, oder leer für „kombiniert"). */
data class BatteryOption(val id: String, val label: String)

@Composable
fun DashboardScreen(
    speedText: String,
    gps: GpsState,
    settings: Settings,
    tracking: Boolean,
    tripStats: TripStats,
    tripPaused: Boolean,
    autoPauseOverride: Boolean,
    batteryData: BatteryData?,
    range: RangeEstimate?,
    charge: ChargeState,
    weatherWarnings: List<WeatherWarning>,
    batteryOptions: List<BatteryOption>,
    selectedBattery: String,
    livePoints: List<TrackPoint>,
    onSelectBattery: (String) -> Unit,
    onAutoPauseOverride: (Boolean) -> Unit,
    onStartTrip: () -> Unit,
    onStopTrip: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenMap: () -> Unit,
    onHideTile: (DashboardTile) -> Unit = {},
    onShowTile: (DashboardTile) -> Unit = {},
    onOrderChange: (String) -> Unit = {},
    onMapSizeChange: (Int) -> Unit = {},
) {
    // **Anordnen.** Drei Sekunden ruhig halten öffnet es, nur „Fertig" schließt es. In
    // einem schaukelnden Boot passiert ein kurzer langer Druck schnell aus Versehen;
    // jedes Wischen bricht das Halten deshalb ab.
    var bearbeiten by remember { mutableStateOf(false) }
    var halten by remember { mutableStateOf<Pair<Offset, Float>?>(null) }
    val gespeichert = remember(settings.dashboardOrder) { reihenfolgeAus(settings.dashboardOrder) }
    // **Ein Zustand, der bleibt.** Die Ziehgeste wird einmal angelegt und behält, was sie
    // beim Anlegen gesehen hat. Wurde die Reihenfolge nach dem Speichern als neues Objekt
    // angelegt, schrieb die Geste weiter ins alte, und ab dem dritten Verschieben tauschte
    // nichts mehr. Jetzt gibt es nur diesen einen Zustand, und das Gespeicherte wird nur
    // übernommen, solange nicht gezogen wird.
    var reihe by remember { mutableStateOf(gespeichert) }
    var gezogen by remember { mutableStateOf<DashboardTile?>(null) }
    LaunchedEffect(gespeichert) { if (gezogen == null) reihe = gespeichert }
    val aktuelleSettings by rememberUpdatedState(settings)
    val speichern by rememberUpdatedState(onOrderChange)
    val tasten = LocalHapticFeedback.current
    fun sichtbar(t: DashboardTile) = when (t) {
        DashboardTile.RANGE -> aktuelleSettings.showRangeTile
        DashboardTile.BATTERY -> aktuelleSettings.showBatteryTile
        DashboardTile.MAP -> aktuelleSettings.showMapTile
        DashboardTile.TRIP -> aktuelleSettings.showTripTile
        DashboardTile.GPS -> aktuelleSettings.showSatDetails
    }
    val hoehen = remember { mutableStateMapOf<DashboardTile, Int>() }
    // Die Karte gleitet in ihre Stufe. Beim Ziehen folgt nur ein leichter Rahmen dem
    // Finger; die echte Karte bei jedem Zucken neu aufzubauen, ruckelte.
    val stufenHoehe = KARTEN_HOEHEN_DP[settings.mapTileSize.coerceIn(KARTEN_HOEHEN_DP.indices)].toFloat()
    val kartenHoehe by animateFloatAsState(stufenHoehe, tween(260), label = "karte")
    var ziehtKarte by remember { mutableStateOf(false) }
    var zugY by remember { mutableFloatStateOf(0f) }
    val abstandPx = with(LocalDensity.current) { 12.dp.toPx() }
    val scope = rememberCoroutineScope()
    // Sanftes Wackeln, drei Sekunden lang, dann Ruhe: Es zeigt, dass man jetzt anordnet,
    // und soll dabei nicht nerven.
    val wackeln = remember { Animatable(0f) }
    LaunchedEffect(bearbeiten) {
        if (bearbeiten) {
            repeat(5) {
                wackeln.animateTo(0.35f, tween(300, easing = FastOutSlowInEasing))
                wackeln.animateTo(-0.35f, tween(300, easing = FastOutSlowInEasing))
            }
            wackeln.animateTo(0f, tween(250))
        } else {
            wackeln.snapTo(0f)
        }
    }

    /**
     * Die gezogene Kachel gleitet in ihren Platz zurück, statt zu springen. Beginnt vorher
     * schon der nächste Zug, wird das Zurückgleiten abgebrochen; sonst schrieb es in den
     * neuen Zug hinein und setzte ihn mittendrin zurück.
     */
    var rueckgleiten by remember { mutableStateOf<Job?>(null) }
    fun zugBeginn(tile: DashboardTile) {
        rueckgleiten?.cancel()
        rueckgleiten = null
        gezogen = tile
        zugY = 0f
    }
    fun zugEnde(sichern: Boolean) {
        if (sichern) speichern(reihenfolgeText(reihe))
        rueckgleiten = scope.launch {
            animate(zugY, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> zugY = v }
            gezogen = null
        }
    }

    /** Zieht die Kachel über die Mitte ihrer Nachbarin, tauschen beide den Platz. */
    fun tauschen(tile: DashboardTile) {
        val sicht = reihe.filter(::sichtbar)
        val i = sicht.indexOf(tile)
        if (zugY > 0 && i < sicht.lastIndex) {
            val nachbarin = sicht[i + 1]
            val h = (hoehen[nachbarin] ?: 0) + abstandPx
            if (zugY > h / 2) {
                reihe = verschoben(reihe, reihe.indexOf(tile), reihe.indexOf(nachbarin))
                zugY -= h
                tasten.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        } else if (zugY < 0 && i > 0) {
            val nachbarin = sicht[i - 1]
            val h = (hoehen[nachbarin] ?: 0) + abstandPx
            if (-zugY > h / 2) {
                reihe = verschoben(reihe, reihe.indexOf(tile), reihe.indexOf(nachbarin))
                zugY += h
                tasten.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    Scaffold { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            // --- Fixer Kopf: Menü, optional Karten-Button, Geschwindigkeit ---
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = onOpenMenu,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                ) {
                    Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu))
                }
                if (!settings.showMapTile) {
                    IconButton(
                        onClick = onOpenMap,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    ) {
                        Icon(Icons.Filled.Map, contentDescription = stringResource(R.string.live_map))
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(56.dp))
                    Text(
                        text = speedText,
                        style = SpeedTextStyle,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = settings.unit.label,
                        fontSize = 28.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            }

            // --- Scrollbarer Rest ---
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .stillesHalten(
                            aktiv = !bearbeiten,
                            onFortschritt = { halten = it },
                            onAusgeloest = { bearbeiten = true },
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(16.dp))

                    // Zielzeile und Wetterwarnung stehen fest oben: Sie erscheinen nur, wenn
                    // sie gebraucht werden, und dann gehören sie direkt unter die große Zahl.
                    NavRow(
                        lat = gps.latitude,
                        lon = gps.longitude,
                        tripDistanceM = tripStats.distanceM,
                        tripChargeAh = tripStats.chargeAh,
                    )

                    if (weatherWarnings.isNotEmpty()) {
                        WeatherBanner(weatherWarnings)
                        Spacer(Modifier.height(12.dp))
                    }

                    // Eine Liste für sichtbare und ausgeblendete Kacheln, damit eine Kachel
                    // beim Ausblenden als dieselbe unter die Linie gleitet, statt dort neu
                    // zu entstehen. Die Linie und alles darunter gibt es nur beim Anordnen.
                    val sichtbare = reihe.filter(::sichtbar)
                    val versteckte = if (bearbeiten) reihe.filterNot(::sichtbar) else emptyList()
                    val anzeige: List<Any> = sichtbare +
                        (if (versteckte.isNotEmpty()) listOf(LINIE) + versteckte else emptyList())

                    for (eintrag in anzeige) {
                        key(eintrag) {
                            // Ohne Rücksprung aus key(): Den übersetzt der Compose-Compiler
                            // zu Code, den das Dexing nicht annimmt.
                            if (eintrag == LINIE) {
                                AusgeblendetLinie(Modifier.gleitend(true))
                            } else {
                                val tile = eintrag as DashboardTile
                                val versteckt = tile in versteckte
                                val istGezogen = gezogen == tile
                                KachelRahmen(
                                    bearbeiten = bearbeiten,
                                    versteckt = versteckt,
                                    modifier = Modifier
                                        .gleitend(!istGezogen)
                                        .onGloballyPositioned { hoehen[tile] = it.size.height }
                                        .zIndex(if (istGezogen || (tile == DashboardTile.MAP && ziehtKarte)) 1f else 0f)
                                        .graphicsLayer {
                                            translationY = if (istGezogen) zugY else 0f
                                            rotationZ = if (istGezogen || versteckt) 0f else wackeln.value
                                            val g = if (istGezogen) 1.03f else 1f
                                            scaleX = g
                                            scaleY = g
                                        },
                                    griff = Modifier.pointerInput(bearbeiten, versteckt) {
                                        if (!bearbeiten || versteckt) return@pointerInput
                                        detectDragGestures(
                                            onDragStart = { zugBeginn(tile) },
                                            onDrag = { change, weg ->
                                                change.consume()
                                                zugY += weg.y
                                                tauschen(tile)
                                            },
                                            onDragEnd = { zugEnde(sichern = true) },
                                            onDragCancel = { zugEnde(sichern = false) },
                                        )
                                    },
                                    onAusblenden = { onHideTile(tile) },
                                    onEinblenden = { onShowTile(tile) },
                                    zusatz = if (tile == DashboardTile.MAP && !versteckt) {
                                        {
                                            KartenGroesse(
                                                aktuell = kartenHoehe,
                                                onZieht = { ziehtKarte = it },
                                                onStufe = onMapSizeChange,
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                ) {
                                    when (tile) {
                                        DashboardTile.RANGE ->
                                            if (charge.charging) ChargeTile(charge) else RangeTile(range)
                                        DashboardTile.BATTERY -> Column {
                                            BatterySelectorRow(batteryOptions, selectedBattery, onSelectBattery)
                                            BatteryTile(batteryData, settings.lowSocPercent)
                                        }
                                        DashboardTile.MAP -> MapMiniTile(
                                            livePoints, gps.latitude, gps.longitude, gps.speedMs,
                                            settings.mapOrientation, onOpenMap,
                                            hoeheDp = kartenHoehe,
                                            bearbeiten = bearbeiten,
                                        )
                                        DashboardTile.TRIP -> TripTile(
                                            tracking = tracking,
                                            stats = tripStats,
                                            settings = settings,
                                            showConsumption = batteryData != null,
                                            paused = tripPaused,
                                            autoPauseShown = tracking && (settings.autoPauseOn || autoPauseOverride),
                                            onAutoPauseToggle = { onAutoPauseOverride(!autoPauseOverride) },
                                            onStart = onStartTrip,
                                            onStop = onStopTrip,
                                        )
                                        DashboardTile.GPS -> GpsTile(gps)
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }

                    // Ohne Fahrt-Kachel bleibt der Knopf: Eine Fahrt muss sich immer starten
                    // lassen, auch wenn jemand die Kachel ausgeblendet hat.
                    if (!settings.showTripTile) {
                        if (tracking && (settings.autoPauseOn || autoPauseOverride)) {
                            AutoPauseChip(
                                paused = tripPaused,
                                onToggle = { onAutoPauseOverride(!autoPauseOverride) },
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        TripButton(tracking = tracking, onStart = onStartTrip, onStop = onStopTrip)
                        Spacer(Modifier.height(16.dp))
                    }
                    Spacer(Modifier.height(if (bearbeiten) 112.dp else 16.dp))
                }

                halten?.let { (wo, anteil) -> HalteRing(wo, anteil) }

                if (bearbeiten) {
                    FloatingActionButton(
                        onClick = { bearbeiten = false },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                            .size(72.dp),
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = stringResource(R.string.dashboard_edit_done),
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Bis zum Ausblenden des Rings vergeht so viel Zeit: Kurze Tipper sollen ihn nicht zeigen. */
private const val RING_AB_MS = 400L

/** So lange muss der Finger ruhig liegen, bis das Anordnen beginnt. */
private const val HALTEN_MS = 3_000L

/**
 * Erkennt ruhiges Halten: [HALTEN_MS] lang ein Finger, der sich nicht über die Tipp-Toleranz
 * hinaus bewegt. Wischen, Scrollen, ein zweiter Finger oder Loslassen brechen ab.
 *
 * Die Ereignisse werden nur **mitgelesen**, nicht verbraucht: Tippen und Scrollen gehen
 * wie gewohnt weiter. Erst wenn das Halten auslöst, gehört das Loslassen dem Anordnen;
 * sonst löste es zusätzlich einen Klick auf Knopf oder Karte aus.
 */
private fun Modifier.stillesHalten(
    aktiv: Boolean,
    onFortschritt: (Pair<Offset, Float>?) -> Unit,
    onAusgeloest: () -> Unit,
): Modifier = pointerInput(aktiv) {
    if (!aktiv) return@pointerInput
    awaitEachGesture {
        val runter = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val start = runter.position
        val beginn = SystemClock.uptimeMillis()
        val toleranz = viewConfiguration.touchSlop
        var ausgeloest = false
        while (true) {
            val ereignis = withTimeoutOrNull(50) { awaitPointerEvent(PointerEventPass.Initial) }
            if (ereignis != null) {
                if (ereignis.changes.size > 1) break
                val c = ereignis.changes.firstOrNull { it.id == runter.id } ?: break
                if (!c.pressed || (c.position - start).getDistance() > toleranz) break
            }
            val vergangen = SystemClock.uptimeMillis() - beginn
            if (vergangen >= RING_AB_MS) {
                onFortschritt(start to ((vergangen - RING_AB_MS).toFloat() / (HALTEN_MS - RING_AB_MS)).coerceIn(0f, 1f))
            }
            if (vergangen >= HALTEN_MS) {
                ausgeloest = true
                break
            }
        }
        onFortschritt(null)
        if (ausgeloest) {
            onAusgeloest()
            do {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                e.changes.forEach { it.consume() }
            } while (e.changes.any { it.pressed })
        }
    }
}

/** Der Ring unter dem Finger, der sich beim ruhigen Halten füllt. */
@Composable
private fun HalteRing(wo: Offset, anteil: Float) {
    val halb = with(LocalDensity.current) { 32.dp.toPx() }
    CircularProgressIndicator(
        progress = { anteil },
        strokeWidth = 5.dp,
        modifier = Modifier
            .offset { IntOffset((wo.x - halb).roundToInt(), (wo.y - halb * 2.2f).roundToInt()) }
            .size(64.dp),
    )
}

/** Platzhalter für die Linie zwischen sichtbaren und ausgeblendeten Kacheln. */
private const val LINIE = "linie"

/**
 * Eine Kachel, im Anordnen mit Griff oben links und Auge oben rechts. Ausgeblendet ist sie
 * grau, ohne Griff, und ihr Auge holt sie zurück. Der Inhalt nimmt beim Anordnen keine
 * Tipper an, damit keine Fahrt startet und die Karte nicht aufgeht; Scrollen geht weiter.
 */
@Composable
private fun KachelRahmen(
    bearbeiten: Boolean,
    versteckt: Boolean,
    modifier: Modifier,
    griff: Modifier,
    onAusblenden: () -> Unit,
    onEinblenden: () -> Unit,
    zusatz: (@Composable BoxScope.() -> Unit)? = null,
    inhalt: @Composable () -> Unit,
) {
    val deckkraft by animateFloatAsState(if (versteckt) 0.4f else 1f, tween(300), label = "grau")
    Box(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.graphicsLayer { alpha = deckkraft }) { inhalt() }
        if (bearbeiten) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) { detectTapGestures { } },
            )
            if (!versteckt) {
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = stringResource(R.string.dashboard_edit_move),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .then(griff)
                        .padding(8.dp),
                )
            }
            Icon(
                if (versteckt) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                contentDescription = stringResource(
                    if (versteckt) R.string.dashboard_edit_show else R.string.dashboard_edit_hide,
                ),
                tint = if (versteckt) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (versteckt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    .clickable(onClick = if (versteckt) onEinblenden else onAusblenden)
                    .padding(9.dp),
            )
            // Über der Sperrschicht, sonst käme kein Ziehen bei ihm an.
            zusatz?.invoke(this)
        }
    }
}

/** Die Linie, unter der die ausgeblendeten Kacheln stehen. */
@Composable
private fun AusgeblendetLinie(modifier: Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.dashboard_hidden),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

/**
 * Ändert sich der Platz, gleitet der Inhalt hin, statt zu springen: beim Tauschen zweier
 * Kacheln und beim Ausblenden, wenn eine unter die Linie rutscht. [aktiv] aus heißt, die
 * Bewegung übernimmt jemand anders, etwa der Finger beim Verschieben.
 */
private fun Modifier.gleitend(aktiv: Boolean): Modifier = composed {
    val scope = rememberCoroutineScope()
    var ziel by remember { mutableStateOf<IntOffset?>(null) }
    val lage = remember { mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null) }
    this
        .onPlaced { ziel = it.positionInParent().round() }
        .offset {
            val z = ziel ?: return@offset IntOffset.Zero
            val a = lage.value ?: Animatable(z, IntOffset.VectorConverter).also { lage.value = it }
            if (a.targetValue != z) {
                scope.launch { a.animateTo(z, spring(stiffness = Spring.StiffnessMediumLow)) }
            }
            if (aktiv) a.value - z else IntOffset.Zero
        }
}

/**
 * Der Größengriff der Karte, **gerastert**. Beim Ziehen bleibt der Griff stehen, und ein
 * Rahmen springt von Stufe zu Stufe, jede mit einem kurzen Tastenklick. Erst beim
 * Loslassen gleitet die Karte in die gewählte Stufe.
 *
 * Vorher wanderte der Griff mit, und die Geste merkte sich die Höhe vom ersten Mal: Jeder
 * weitere Zug begann von der falschen Höhe und endete meist auf der größten Stufe.
 */
@Composable
private fun BoxScope.KartenGroesse(aktuell: Float, onZieht: (Boolean) -> Unit, onStufe: (Int) -> Unit) {
    val hoehe by rememberUpdatedState(aktuell)
    val melden by rememberUpdatedState(onZieht)
    val festlegen by rememberUpdatedState(onStufe)
    var ziel by remember { mutableStateOf<Int?>(null) }
    var start by remember { mutableStateOf(0) }
    var weg by remember { mutableFloatStateOf(0f) }
    val dichte = LocalDensity.current
    val tasten = LocalHapticFeedback.current
    val farbe = MaterialTheme.colorScheme.primary
    ziel?.let { stufe ->
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .requiredHeight(KARTEN_HOEHEN_DP[stufe].dp)
                .background(farbe.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                .border(2.dp, farbe, RoundedCornerShape(12.dp)),
        )
    }
    GroessenGriff(
        modifier = Modifier.align(Alignment.BottomCenter),
        onStart = {
            start = naechsteKartenStufe(hoehe)
            weg = 0f
            ziel = start
            melden(true)
        },
        onZiehen = { px ->
            weg += with(dichte) { px.toDp().value }
            val neu = naechsteKartenStufe(KARTEN_HOEHEN_DP[start] + weg)
            if (neu != ziel) {
                ziel = neu
                tasten.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        },
        onLoslassen = {
            val gewaehlt = ziel ?: start
            ziel = null
            melden(false)
            festlegen(gewaehlt)
        },
    )
}

/** Fix, Satelliten und Genauigkeit als Kachel. */
@Composable
private fun GpsTile(gps: GpsState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            StatusRow(gps = gps, showSatDetails = true)
        }
    }
}

/** Der Griff unten an der Karte, mit dem sich ihre Höhe ziehen lässt. */
@Composable
private fun GroessenGriff(
    modifier: Modifier,
    onStart: () -> Unit,
    onZiehen: (Float) -> Unit,
    onLoslassen: () -> Unit,
) {
    val beginnen by rememberUpdatedState(onStart)
    val ziehen by rememberUpdatedState(onZiehen)
    val loslassen by rememberUpdatedState(onLoslassen)
    Icon(
        Icons.Filled.UnfoldMore,
        contentDescription = stringResource(R.string.dashboard_edit_resize),
        tint = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier
            .padding(6.dp)
            .size(width = 72.dp, height = 36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { beginnen() },
                    onDragEnd = { loslassen() },
                    onDragCancel = { loslassen() },
                    onVerticalDrag = { change, weg ->
                        change.consume()
                        ziehen(weg)
                    },
                )
            }
            .padding(4.dp),
    )
}

/** Die Fahrt als Kachel, wie Batterie und Reichweite: Knopf, Zahlen und Auto-Pause. */
@Composable
private fun TripTile(
    tracking: Boolean,
    stats: TripStats,
    settings: Settings,
    showConsumption: Boolean,
    paused: Boolean,
    autoPauseShown: Boolean,
    onAutoPauseToggle: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.tile_trip),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth(),
            )
            if (tracking || stats.hasData) {
                StatsPanel(stats = stats, settings = settings, showConsumption = showConsumption)
            }
            if (autoPauseShown) AutoPauseChip(paused = paused, onToggle = onAutoPauseToggle)
            TripButton(tracking = tracking, onStart = onStart, onStop = onStop)
        }
    }
}

@Composable
private fun MapMiniTile(
    points: List<TrackPoint>,
    lat: Double?,
    lon: Double?,
    speedMs: Float?,
    orientation: de.kewl.boatspeedy.data.MapOrientation,
    onOpenMap: () -> Unit,
    hoeheDp: Float = KARTEN_HOEHEN_DP[1].toFloat(),
    bearbeiten: Boolean = false,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(hoeheDp.dp)) {
            // Der Weg zum Ziel gehört auch auf die kleine Kachel – sonst müsste man für
            // einen Blick darauf jedes Mal die große Karte öffnen.
            val navTarget by de.kewl.boatspeedy.nav.NavRepository.target.collectAsStateWithLifecycle()
            val mapCourse by de.kewl.boatspeedy.nav.NavRepository.course.collectAsStateWithLifecycle()
            OsmMap(
                points = points,
                currentLat = lat,
                currentLon = lon,
                interactive = false,
                navPath = navTarget?.path.orEmpty(),
                navWaterPath = navTarget?.water.orEmpty(),
                courseDeg = mapCourse?.deg,
                speedMs = speedMs,
                orientation = orientation,
                modifier = Modifier.matchParentSize(),
            )
            // Nicht-interaktive Vorschau: Overlay fängt den Tap (→ große Karte),
            // vertikales Ziehen wandert an das Dashboard-Scrollen weiter.
            Box(modifier = Modifier.matchParentSize().clickable(enabled = !bearbeiten, onClick = onOpenMap))
        }
    }
}

@Composable
private fun BatterySelectorRow(options: List<BatteryOption>, selected: String, onSelect: (String) -> Unit) {
    if (options.size < 2) return
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        options.forEach { opt ->
            FilterChip(
                selected = opt.id == selected,
                onClick = { onSelect(opt.id) },
                label = { Text(opt.label) },
            )
        }
    }
}

@Composable
private fun BatteryTile(d: BatteryData?, lowSocPercent: Int) {
    val socLow = d != null && lowSocPercent > 0 && d.soc <= lowSocPercent
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.battery), style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TileStat(stringResource(R.string.bat_power), d?.let { watts(kotlin.math.abs(it.powerW)) } ?: PLACEHOLDER)
                TileStat(stringResource(R.string.bat_voltage), d?.let { num(it.voltage, "V") } ?: PLACEHOLDER)
                TileStat(stringResource(R.string.bat_current), d?.let { num(it.currentA, "A") } ?: PLACEHOLDER)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TileStat(stringResource(R.string.soc_short), d?.let { "${it.soc} %" } ?: PLACEHOLDER, alert = socLow)
                TileStat(
                    stringResource(R.string.bat_remaining),
                    d?.takeIf { it.remainingAh > 0f }?.let { num(it.remainingAh, "Ah") } ?: PLACEHOLDER,
                )
                TileStat(
                    stringResource(R.string.bat_temp),
                    d?.tempC?.let { num(it, "°C") } ?: PLACEHOLDER,
                )
            }
        }
    }
}

@Composable
private fun RangeTile(range: RangeEstimate?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TileStat(
                stringResource(R.string.bat_est_range),
                range?.let { formatDistance(it.km * 1000.0) } ?: PLACEHOLDER,
                big = true,
            )
            TileStat(
                stringResource(R.string.bat_est_time),
                range?.let { formatDuration((it.hours * 3600_000).toLong()) } ?: PLACEHOLDER,
                big = true,
            )
        }
    }
}

@Composable
private fun ChargeTile(charge: ChargeState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.charge_mode),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TileStat(stringResource(R.string.charge_current), num(charge.chargeA, "A"))
                TileStat(stringResource(R.string.soc_short), "${charge.soc} %")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TileStat(
                    stringResource(R.string.charge_time_to_full),
                    charge.hoursToFull?.let { formatDuration((it * 3600_000).toLong()) } ?: PLACEHOLDER,
                    big = true,
                )
                TileStat(
                    stringResource(R.string.charge_full_at),
                    charge.fullAtEpochMs?.let { clockTime(it) } ?: PLACEHOLDER,
                    big = true,
                )
            }
        }
    }
}

@Composable
private fun WeatherBanner(warnings: List<WeatherWarning>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            warnings.forEach { w ->
                Text(
                    "⚠ ${w.event}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                if (w.headline.isNotBlank()) {
                    Text(
                        w.headline,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                    )
                }
                w.expiresMs?.let { exp ->
                    Text(
                        stringResource(R.string.weather_valid_until, clockTime(exp)),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

private fun clockTime(epochMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))

@Composable
private fun TileStat(label: String, value: String, big: Boolean = false, alert: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(
            value,
            fontSize = if (big) 22.sp else 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = when {
                alert -> MaterialTheme.colorScheme.error
                big -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun StatsPanel(stats: TripStats, settings: Settings, showConsumption: Boolean) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem(stringResource(R.string.stat_distance), formatDistance(stats.distanceM))
            StatItem(
                stringResource(R.string.stat_max),
                "${formatSpeed(stats.maxSpeedMs, settings.unit, settings.decimals)} ${settings.unit.label}",
            )
            StatItem(
                stringResource(R.string.stat_avg),
                "${formatSpeed(stats.avgSpeedMs, settings.unit, settings.decimals)} ${settings.unit.label}",
            )
            StatItem(stringResource(R.string.stat_time), formatDuration(stats.elapsedMs))
        }
        if (showConsumption || stats.chargeAh > 0f || stats.energyWh > 0f) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem(
                    stringResource(R.string.stat_consumed),
                    String.format(Locale.getDefault(), "%.1f Ah", stats.chargeAh),
                )
                StatItem(
                    stringResource(R.string.stat_energy),
                    String.format(Locale.getDefault(), "%.0f Wh", stats.energyWh),
                )
                StatItem(
                    stringResource(R.string.stat_efficiency),
                    stats.whPerKm?.let { String.format(Locale.getDefault(), "%.0f Wh/km", it) } ?: PLACEHOLDER,
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Zustand der Auto-Pause als antippbarer Chip. Bewusst dezenter als der Start/Stopp-Knopf,
 * damit dieser der einzige „große" Bedienknopf auf dem Dashboard bleibt.
 */
@Composable
private fun AutoPauseChip(paused: Boolean, onToggle: () -> Unit) {
    val tint = if (paused) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    AssistChip(
        onClick = onToggle,
        label = {
            Text(
                stringResource(if (paused) R.string.trip_paused else R.string.trip_recording),
                fontWeight = FontWeight.SemiBold,
            )
        },
        leadingIcon = {
            Icon(
                if (paused) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        trailingIcon = {
            Icon(
                if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = stringResource(
                    if (paused) R.string.trip_keep_recording else R.string.auto_pause_reenable,
                ),
                modifier = Modifier.size(18.dp),
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = tint,
            leadingIconContentColor = tint,
            trailingIconContentColor = tint,
        ),
    )
}

@Composable
private fun TripButton(tracking: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    if (tracking) {
        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(
                containerColor = StatusNone,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(Icons.Filled.Stop, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.trip_stop))
        }
    } else {
        Button(onClick = onStart) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.trip_start))
        }
    }
}

/**
 * Punkt, „Fix", Satellitenzahl und Genauigkeit. Sind die Satelliten-Details abgeschaltet,
 * verschwindet die Zeile ganz — vorher blieben Punkt und „Fix" stehen, und genau die
 * wollte man ja loswerden.
 */
@Composable
private fun StatusRow(gps: GpsState, showSatDetails: Boolean) {
    if (!showSatDetails) return
    val statusColor = when {
        !gps.hasFix -> StatusNone
        (gps.accuracyM ?: Float.MAX_VALUE) <= 10f && gps.satellitesUsed >= 4 -> StatusGood
        else -> StatusWeak
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(statusColor))
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (gps.hasFix) stringResource(R.string.status_fix) else stringResource(R.string.status_no_fix),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.sat_label, gps.satellitesUsed, gps.satellitesVisible),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
        gps.accuracyM?.let { acc ->
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.accuracy_label, acc.toInt()),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        }
    }
}

private fun num(v: Float, unit: String) = String.format(Locale.getDefault(), "%.2f %s", v, unit)
private fun watts(v: Float) = String.format(Locale.getDefault(), "%.0f W", v)

/**
 * Entfernung, geschätzter Verbrauch und der Kurspfeil zum gesetzten Ziel.
 *
 * Zeigt sich nur, solange ein Ziel gesetzt ist — ohne Ziel bleibt das Dashboard
 * unverändert. Der Pfeil zeigt die Drehung zum Ziel, nicht die Himmelsrichtung.
 */
@Composable
private fun NavRow(lat: Double?, lon: Double?, tripDistanceM: Double, tripChargeAh: Float) {
    val target by de.kewl.boatspeedy.nav.NavRepository.target.collectAsStateWithLifecycle()
    val course by de.kewl.boatspeedy.nav.NavRepository.course.collectAsStateWithLifecycle()
    val t = target ?: return

    val ahPerKm = if (tripDistanceM > 300.0 && tripChargeAh > 0f) {
        (tripChargeAh / (tripDistanceM / 1000.0)).toFloat()
    } else {
        null
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (lat != null && lon != null) {
            course?.let { c ->
                CourseArrow(
                    relativeDeg = de.kewl.boatspeedy.nav.relativeBearing(
                        c.deg,
                        de.kewl.boatspeedy.nav.bearingDeg(de.kewl.boatspeedy.nav.LatLon(lat, lon), t.target),
                    ),
                    stale = c.stale,
                    size = 30.dp,
                )
                Spacer(Modifier.width(10.dp))
            }
        }
        Text(
            buildString {
                append(String.format(Locale.getDefault(), "%.2f km", t.distanceM / 1000.0))
                ahPerKm?.let {
                    append(" · ~")
                    append(String.format(Locale.getDefault(), "%.1f Ah", it * (t.distanceM / 1000.0)))
                }
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
