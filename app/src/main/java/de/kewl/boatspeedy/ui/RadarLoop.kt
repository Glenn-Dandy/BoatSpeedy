package de.kewl.boatspeedy.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Ein Vorhersagelauf des DWD und die Frames darin. */
class RadarRun(val refMs: Long, val frames: List<RadarFrame>) {
    val refIso: String = radarIso(refMs)
}

/**
 * Die Radarschleife der Wetteransicht: welcher Vorhersagelauf gezeigt wird und welche
 * seiner Frames schon da sind.
 */
@Stable
class RadarLoopState {
    var run by mutableStateOf<RadarRun?>(null)
        internal set

    /** Die geladenen Frames des Laufs, nach ihrer Stelle in [RadarRun.frames]. */
    val grids = mutableStateMapOf<Int, RadarGrid>()

    /** Der DWD war nicht zu erreichen, und es ist noch nichts da, was man zeigen könnte. */
    var unreachable by mutableStateOf(false)
        internal set
}

/** So oft wird nach einem neuen Vorhersagelauf gefragt. Der DWD rechnet alle fünf Minuten. */
private const val RADAR_POLL_MS = 60_000L

/** So viele Frames werden gleichzeitig geholt. */
private const val RADAR_PARALLEL = 4

/**
 * So viele werden gleichzeitig entschlüsselt. Weniger als geholt werden: Der gezeigte Frame
 * wird auf denselben Rechenkernen aufbereitet und soll nicht hinter der ganzen Schleife
 * anstehen.
 */
private const val RADAR_DECODE_PARALLEL = 2

/**
 * Lädt die Radarschleife, solange [active] ist und die App im Vordergrund steht.
 *
 * **Ein Lauf, nicht eine Uhrzeit.** Vorher wurde jeder Frame ohne Lauf angefragt, und der
 * DWD lieferte den jeweils neuesten. Erschien während des Ladens ein neuer, mischte die
 * Schleife zwei Vorhersagen, und der Regen sprang beim Abspielen. Jetzt gehört jede
 * Schleife zu genau einem Lauf. Ein neuer wird im Hintergrund vollständig geladen und
 * erst dann gegen den alten getauscht, so bleibt das Bild stehen, bis der neue ganz da ist.
 *
 * Nur der erste Lauf erscheint Frame für Frame, damit sofort etwas zu sehen ist.
 */
@Composable
fun rememberRadarLoop(active: Boolean): RadarLoopState {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = remember { RadarLoopState() }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val cache = radarCacheDir(context)
        withContext(Dispatchers.IO) { pruneRadarCache(cache) }
        // Im Hintergrund wird nicht gefragt. Kommt die App zurück, sofort.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val shown = state.run
                val latest = withContext(Dispatchers.IO) {
                    findLatestRadarRun(System.currentTimeMillis(), shown?.refMs)
                }
                if (latest == null) {
                    state.unreachable = shown == null
                } else if (shown == null || latest != shown.refMs) {
                    state.unreachable = false
                    val run = RadarRun(latest, radarFramesFor(latest))
                    if (shown == null) {
                        state.grids.clear()
                        state.run = run
                        loadRun(run, cache) { i, g -> state.grids[i] = g }
                    } else {
                        val neu = HashMap<Int, RadarGrid>()
                        loadRun(run, cache) { i, g -> neu[i] = g }
                        // Unvollständig bleibt der alte Lauf stehen; beim nächsten
                        // Durchgang wird es wieder versucht, das Geladene liegt dann
                        // schon auf der Platte. Fehlen nur ein, zwei Frames hinten, wird
                        // trotzdem getauscht und der Rest nachgeholt: Sonst hielte ein
                        // einzelner Aussetzer beim DWD die alte Vorhersage für immer fest.
                        if (neu.size == run.frames.size || (0 in neu && neu.size >= run.frames.size - 2)) {
                            state.grids.clear()
                            state.grids.putAll(neu)
                            state.run = run
                        }
                    }
                } else if (state.grids.size < shown.frames.size) {
                    // Es fehlen noch Frames, etwa weil das Netz kurz weg war.
                    loadRun(shown, cache, skip = state.grids.keys.toSet()) { i, g -> state.grids[i] = g }
                }
                delay(RADAR_POLL_MS)
            }
        }
    }
    return state
}

/**
 * Holt die Frames eines Laufs, von der Platte, sonst vom DWD. In der Reihenfolge der
 * Schleife, damit der Anfang zuerst steht, aber [RADAR_PARALLEL] auf einmal. Vorher ging
 * es in Vierergruppen, und jede Gruppe wartete auf ihren langsamsten Frame.
 */
private suspend fun loadRun(
    run: RadarRun,
    cache: java.io.File,
    skip: Set<Int> = emptySet(),
    onFrame: (Int, RadarGrid) -> Unit,
) = coroutineScope {
    val gate = Semaphore(RADAR_PARALLEL)
    val decode = Semaphore(RADAR_DECODE_PARALLEL)
    for (i in run.frames.indices) {
        if (i in skip) continue
        launch {
            gate.withPermit {
                val time = run.frames[i].timeIso
                val png = withContext(Dispatchers.IO) {
                    readCachedRadar(cache, DWD_RADAR_LAYER, run.refIso, time)
                        ?: fetchRadarPng(
                            DWD_RADAR_LAYER, time, RADAR_AREA_GER, RADAR_AREA_W, RADAR_AREA_H, run.refIso,
                        )?.also { writeCachedRadar(cache, DWD_RADAR_LAYER, run.refIso, time, it) }
                } ?: return@withPermit
                val grid = decode.withPermit {
                    withContext(Dispatchers.Default) { decodeRadarGrid(png) }
                } ?: return@withPermit
                onFrame(i, grid)
            }
        }
    }
}
