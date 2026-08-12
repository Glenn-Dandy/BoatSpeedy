package de.kewl.boatspeedy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kewl.boatspeedy.data.COMBINED_SELECTION
import de.kewl.boatspeedy.data.ThemeMode
import de.kewl.boatspeedy.ui.AboutScreen
import de.kewl.boatspeedy.ui.AnchorScreen
import de.kewl.boatspeedy.ui.AppearanceSettingsScreen
import de.kewl.boatspeedy.ui.BatteryOption
import de.kewl.boatspeedy.ui.BatteryScreen
import de.kewl.boatspeedy.ui.DashboardScreen
import de.kewl.boatspeedy.ui.DashboardSettingsScreen
import de.kewl.boatspeedy.ui.GeneralSettingsScreen
import de.kewl.boatspeedy.ui.GpsSettingsScreen
import de.kewl.boatspeedy.ui.NotificationSettingsScreen
import de.kewl.boatspeedy.ui.LiveMapScreen
import de.kewl.boatspeedy.ui.SettingsHomeScreen
import de.kewl.boatspeedy.ui.TracksSettingsScreen
import de.kewl.boatspeedy.trip.SavedTrip
import de.kewl.boatspeedy.ui.SpeedViewModel
import de.kewl.boatspeedy.ui.TripDetailScreen
import de.kewl.boatspeedy.ui.TripMapScreen
import de.kewl.boatspeedy.ui.TripsScreen
import de.kewl.boatspeedy.ui.theme.BoatSpeedyTheme
import de.kewl.boatspeedy.util.LanguageHelper
import kotlinx.coroutines.launch

private enum class Screen { SPEED, LIVE_MAP, TRIPS, TRIP_DETAIL, TRIP_MAP, BATTERY, ANCHOR, SETTINGS, SETTINGS_DASHBOARD, SETTINGS_NOTIF, SETTINGS_GENERAL, SETTINGS_TRACKS, SETTINGS_GPS, SETTINGS_APPEARANCE, ABOUT }

class MainActivity : ComponentActivity() {
    // Von außen zum Import übergebene GPX-Datei (Öffnen-mit / Teilen an BoatSpeedy).
    private val pendingGpx = mutableStateOf<android.net.Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LanguageHelper.ensureDefault(this) // beim ersten Start Englisch erzwingen
        pendingGpx.value = extractGpx(intent)
        enableEdgeToEdge()
        setContent { BoatSpeedyApp(pendingGpx = pendingGpx.value, onGpxConsumed = { pendingGpx.value = null }) }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        extractGpx(intent)?.let { pendingGpx.value = it }
    }

    /** Lautstärketasten quittieren einen laufenden Alarm (wie bei Weckern). */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val isVolume = keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN
        if (isVolume && de.kewl.boatspeedy.alarm.AlarmController.isActive) {
            de.kewl.boatspeedy.alarm.AlarmController.stop(this)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun extractGpx(intent: android.content.Intent?): android.net.Uri? = when (intent?.action) {
        android.content.Intent.ACTION_VIEW -> intent.data
        android.content.Intent.ACTION_SEND ->
            androidx.core.content.IntentCompat.getParcelableExtra(
                intent, android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java,
            )
        else -> null
    }
}

@Composable
private fun BoatSpeedyApp(
    pendingGpx: android.net.Uri? = null,
    onGpxConsumed: () -> Unit = {},
    vm: SpeedViewModel = viewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()

    val darkTheme = when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    BoatSpeedyTheme(darkTheme = darkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val context = LocalContext.current
            var hasPermission by rememberSaveable {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED,
                )
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted -> hasPermission = granted }

            if (!hasPermission) {
                PermissionGate(onRequest = {
                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                })
                return@Surface
            }

            // GPS nur im Vordergrund messen – an den Lifecycle koppeln.
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> vm.startUpdates()
                        Lifecycle.Event.ON_PAUSE -> vm.stopUpdates()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            KeepScreenOn(settings.keepScreenOn)

            // Notification-Berechtigung (Android 13+) für die Fahrt-Benachrichtigung.
            val notifLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { /* Ergebnis ist unkritisch – der Dienst läuft auch ohne sichtbare Notification. */ }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            var screen by rememberSaveable { mutableStateOf(Screen.SPEED) }
            val gps by vm.gps.collectAsStateWithLifecycle()
            val speedText by vm.displaySpeed.collectAsStateWithLifecycle()
            val tracking by vm.tracking.collectAsStateWithLifecycle()
            val tripStats by vm.tripStats.collectAsStateWithLifecycle()
            val tripPaused by vm.tripPaused.collectAsStateWithLifecycle()
            val autoPauseOverride by vm.autoPauseOverride.collectAsStateWithLifecycle()
            val battery by vm.battery.collectAsStateWithLifecycle()
            val dashBattery by vm.dashboardBattery.collectAsStateWithLifecycle()
            val dashRange by vm.dashboardRange.collectAsStateWithLifecycle()
            val charge by vm.charge.collectAsStateWithLifecycle()
            val weatherWarnings by vm.weatherWarnings.collectAsStateWithLifecycle()
            val trips by vm.trips.collectAsStateWithLifecycle()
            val livePoints by vm.livePoints.collectAsStateWithLifecycle()
            val anchor by vm.anchor.collectAsStateWithLifecycle()
            var selectedTrip by remember { mutableStateOf<SavedTrip?>(null) }

            LaunchedEffect(screen) { if (screen == Screen.TRIPS) vm.refreshTrips() }

            // Von außen geöffnete GPX-Datei importieren und zu den Fahrten wechseln.
            LaunchedEffect(pendingGpx) {
                val uri = pendingGpx ?: return@LaunchedEffect
                vm.importGpx(uri) { ok ->
                    android.widget.Toast.makeText(
                        context,
                        context.getString(if (ok) R.string.import_ok else R.string.import_failed),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
                screen = Screen.TRIPS
                onGpxConsumed()
            }

            // Bluetooth-Berechtigungen für die Batterie-Verbindung.
            var pendingBt by remember { mutableStateOf<(() -> Unit)?>(null) }
            val btLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { result ->
                if (result.values.all { it }) pendingBt?.invoke()
                pendingBt = null
            }
            val withBt: (() -> Unit) -> Unit = { action ->
                val need = arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                )
                if (need.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
                    action()
                } else {
                    pendingBt = action
                    btLauncher.launch(need)
                }
            }

            // Aktive gespeicherte Akkus beim ersten Start automatisch verbinden –
            // nur wenn die Bluetooth-Berechtigung bereits erteilt ist (kein Prompt).
            var autoConnected by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(settings.batteries) {
                if (!autoConnected && settings.batteries.any { it.active } &&
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.BLUETOOTH_CONNECT,
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    autoConnected = true
                    vm.autoConnectActive()
                }
            }

            val drawerState = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            val openDrawer = { scope.launch { drawerState.open() } }
            val goTo: (Screen) -> Unit = { s -> screen = s; scope.launch { drawerState.close() } }

            BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
            BackHandler(enabled = !drawerState.isOpen && screen != Screen.SPEED) {
                screen = when (screen) {
                    Screen.SETTINGS_DASHBOARD, Screen.SETTINGS_NOTIF, Screen.SETTINGS_GENERAL, Screen.SETTINGS_TRACKS, Screen.SETTINGS_GPS, Screen.SETTINGS_APPEARANCE -> Screen.SETTINGS
                    Screen.TRIP_DETAIL -> Screen.TRIPS
                    Screen.TRIP_MAP -> Screen.TRIP_DETAIL
                    else -> Screen.SPEED
                }
            }

            ModalNavigationDrawer(
                drawerState = drawerState,
                // Nur Wisch-zum-Schließen erlauben, nicht zum Öffnen – sonst beißt sich
                // die Randwischgeste mit dem horizontalen Schwenken der Karte. Öffnen
                // geht über das Menü-Symbol.
                gesturesEnabled = drawerState.isOpen,
                drawerContent = {
                    ModalDrawerSheet {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(16.dp),
                        )
                        HorizontalDivider()
                        DrawerItem(R.string.nav_speed, Icons.Filled.Speed, screen == Screen.SPEED) { goTo(Screen.SPEED) }
                        DrawerItem(R.string.nav_trips, Icons.Filled.Route, screen.name.startsWith("TRIP")) { goTo(Screen.TRIPS) }
                        DrawerItem(R.string.nav_battery, Icons.Filled.BatteryFull, screen == Screen.BATTERY) { goTo(Screen.BATTERY) }
                        DrawerItem(R.string.nav_anchor, Icons.Filled.Anchor, screen == Screen.ANCHOR) { goTo(Screen.ANCHOR) }
                        HorizontalDivider()
                        DrawerItem(R.string.settings, Icons.Filled.Settings, screen.name.startsWith("SETTINGS")) { goTo(Screen.SETTINGS) }
                        DrawerItem(R.string.about, Icons.Filled.Info, screen == Screen.ABOUT) { goTo(Screen.ABOUT) }
                    }
                },
            ) {
                when (screen) {
                    Screen.SETTINGS -> SettingsHomeScreen(
                        onDashboard = { screen = Screen.SETTINGS_DASHBOARD },
                        onNotifications = { screen = Screen.SETTINGS_NOTIF },
                        onGeneral = { screen = Screen.SETTINGS_GENERAL },
                        onAppearance = { screen = Screen.SETTINGS_APPEARANCE },
                        onTracks = { screen = Screen.SETTINGS_TRACKS },
                        onGps = { screen = Screen.SETTINGS_GPS },
                        onOpenMenu = { openDrawer() },
                    )

                    Screen.SETTINGS_DASHBOARD -> DashboardSettingsScreen(
                        settings = settings,
                        onUnit = vm::setUnit,
                        onDecimals = vm::setDecimals,
                        onSmoothing = vm::setSmoothing,
                        onRangeSmoothing = vm::setRangeSmoothing,
                        onShowBatteryTile = vm::setShowBatteryTile,
                        onShowRangeTile = vm::setShowRangeTile,
                        onShowMapTile = vm::setShowMapTile,
                        onShowSatDetails = vm::setShowSatDetails,
                        onBack = { screen = Screen.SETTINGS },
                    )

                    Screen.SETTINGS_NOTIF -> NotificationSettingsScreen(
                        settings = settings,
                        onNotifEnabled = vm::setNotifEnabled,
                        onNotifAlways = vm::setNotifAlways,
                        onNotifFields = vm::setNotifFields,
                        onBack = { screen = Screen.SETTINGS },
                    )

                    Screen.SETTINGS_GENERAL -> GeneralSettingsScreen(
                        settings = settings,
                        onLowSocPercent = vm::setLowSocPercent,
                        onAnchorAlarmOn = vm::setAnchorAlarmOn,
                        onAnchorSound = vm::setAnchorSound,
                        onSocAlarmOn = vm::setSocAlarmOn,
                        onSocSound = vm::setSocSound,
                        onChargeTargetSoc = vm::setChargeTargetSoc,
                        onWeatherEnabled = vm::setWeatherEnabled,
                        onWeatherAlarmOn = vm::setWeatherAlarmOn,
                        onWeatherSound = vm::setWeatherSound,
                        onTestAnchor = vm::testAnchorSound,
                        onTestSoc = vm::testSocSound,
                        onTestWeather = vm::testWeatherSound,
                        onBack = { screen = Screen.SETTINGS },
                    )

                    Screen.SETTINGS_TRACKS -> TracksSettingsScreen(
                        settings = settings,
                        onTrackColor = vm::setTrackColor,
                        onTrackWidth = vm::setTrackWidth,
                        onTrackArrows = vm::setTrackArrows,
                        onAutoPauseOn = vm::setAutoPauseOn,
                        onAutoPauseAmps = vm::setAutoPauseAmps,
                        onAutoPauseSpeedMs = vm::setAutoPauseSpeedMs,
                        onBack = { screen = Screen.SETTINGS },
                    )

                    Screen.SETTINGS_GPS -> GpsSettingsScreen(
                        gps = gps,
                        onBack = { screen = Screen.SETTINGS },
                    )

                    Screen.SETTINGS_APPEARANCE -> AppearanceSettingsScreen(
                        settings = settings,
                        onTheme = vm::setTheme,
                        onKeepScreenOn = vm::setKeepScreenOn,
                        language = LanguageHelper.current(context),
                        onLanguage = { LanguageHelper.set(context, it) },
                        onBack = { screen = Screen.SETTINGS },
                    )

                    Screen.TRIPS -> TripsScreen(
                        trips = trips,
                        onOpenDetail = { trip -> selectedTrip = trip; screen = Screen.TRIP_DETAIL },
                        onDelete = vm::deleteTrips,
                        onMerge = { ids ->
                            vm.mergeTrips(ids) { ok ->
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(if (ok) R.string.merge_ok else R.string.merge_failed),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        onImport = { uri ->
                            vm.importGpx(uri) { ok ->
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(if (ok) R.string.import_ok else R.string.import_failed),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        onOpenMenu = { openDrawer() },
                    )

                    Screen.TRIP_DETAIL -> {
                        val trip = selectedTrip
                        if (trip == null) {
                            screen = Screen.TRIPS
                        } else {
                            TripDetailScreen(
                                trip = trip,
                                settings = settings,
                                onShowMap = { screen = Screen.TRIP_MAP },
                                onBack = { screen = Screen.TRIPS },
                            )
                        }
                    }

                    Screen.TRIP_MAP -> {
                        val trip = selectedTrip
                        if (trip == null) {
                            screen = Screen.TRIPS
                        } else {
                            TripMapScreen(trip = trip, settings = settings, onBack = { screen = Screen.TRIP_DETAIL })
                        }
                    }

                    Screen.ABOUT -> AboutScreen(
                        language = LanguageHelper.current(context),
                        onLanguage = { LanguageHelper.set(context, it) },
                        onOpenMenu = { openDrawer() },
                    )

                    Screen.LIVE_MAP -> LiveMapScreen(
                        currentLat = gps.latitude,
                        currentLon = gps.longitude,
                        points = livePoints,
                        settings = settings,
                        onBack = { screen = Screen.SPEED },
                    )

                    Screen.BATTERY -> BatteryScreen(
                        settings = settings,
                        hub = battery,
                        onScan = { withBt { vm.scanBattery() } },
                        onStopScan = vm::stopScan,
                        onAdd = { device, bms -> withBt { vm.addBattery(device, bms) } },
                        onConnect = { address -> withBt { vm.connectBattery(address) } },
                        onDisconnect = vm::disconnectBattery,
                        onToggleActive = vm::setBatteryActive,
                        onRemove = vm::removeBattery,
                        onRename = vm::renameBattery,
                        onBatteryBms = vm::setBatteryBms,
                        onBankMode = vm::setBankMode,
                        onOpenMenu = { openDrawer() },
                    )

                    Screen.ANCHOR -> AnchorScreen(
                        anchor = anchor,
                        gps = gps,
                        radiusM = settings.anchorRadiusM,
                        onRadiusChange = vm::setAnchorRadius,
                        onSetAnchor = vm::setAnchor,
                        onRaise = vm::raiseAnchor,
                        onSilence = vm::silenceAnchor,
                        onOpenMenu = { openDrawer() },
                    )

                    Screen.SPEED -> {
                        val activeBatteries = settings.batteries.filter { it.active }
                        val batteryOptions = if (activeBatteries.size >= 2) {
                            activeBatteries.map { BatteryOption(it.address, it.name) } +
                                BatteryOption(COMBINED_SELECTION, stringResource(R.string.combined_short))
                        } else {
                            emptyList()
                        }
                        DashboardScreen(
                            speedText = speedText,
                            gps = gps,
                            settings = settings,
                            tracking = tracking,
                            tripStats = tripStats,
                            tripPaused = tripPaused,
                            autoPauseOverride = autoPauseOverride,
                            batteryData = dashBattery,
                            range = dashRange,
                            charge = charge,
                            weatherWarnings = weatherWarnings,
                            batteryOptions = batteryOptions,
                            selectedBattery = settings.dashboardBattery,
                            livePoints = livePoints,
                            onSelectBattery = vm::setDashboardBattery,
                            onAutoPauseOverride = vm::setAutoPauseOverride,
                            onStartTrip = vm::startTrip,
                            onStopTrip = vm::stopTrip,
                            onOpenMenu = { openDrawer() },
                            onOpenMap = { screen = Screen.LIVE_MAP },
                        )
                    }
                }
            }

            // Quittierpflichtiger Alarm: Banner über allem, Antippen bestätigt.
            val pendingAlarm by vm.pendingAlarm.collectAsStateWithLifecycle()
            pendingAlarm?.let { text ->
                AlarmBanner(text = text, onAcknowledge = vm::acknowledgeAlarm)
            }
        }
    }
}

/** Alarm-Banner über der App: bleibt, bis der Nutzer es antippt (quittiert). */
@Composable
private fun AlarmBanner(text: String, onAcknowledge: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        androidx.compose.material3.Card(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
                .clickable(onClick = onAcknowledge),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "⚠ $text",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.alarm_ack_hint),
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DrawerItem(labelRes: Int, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(stringResource(labelRes)) },
        icon = { Icon(icon, contentDescription = null) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = (context as? ComponentActivity)?.window
        if (enabled) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.permission_needed),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onRequest, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.grant_permission))
            }
        }
    }
}
