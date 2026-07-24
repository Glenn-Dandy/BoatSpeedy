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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Info
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
import de.kewl.boatspeedy.ui.AppearanceSettingsScreen
import de.kewl.boatspeedy.ui.BatteryOption
import de.kewl.boatspeedy.ui.BatteryScreen
import de.kewl.boatspeedy.ui.DashboardScreen
import de.kewl.boatspeedy.ui.DashboardSettingsScreen
import de.kewl.boatspeedy.ui.LanguageSettingsScreen
import de.kewl.boatspeedy.ui.SettingsHomeScreen
import de.kewl.boatspeedy.ui.SpeedViewModel
import de.kewl.boatspeedy.ui.theme.BoatSpeedyTheme
import de.kewl.boatspeedy.util.LanguageHelper
import kotlinx.coroutines.launch

private enum class Screen { SPEED, BATTERY, SETTINGS, SETTINGS_DASHBOARD, SETTINGS_APPEARANCE, SETTINGS_LANGUAGE, ABOUT }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LanguageHelper.ensureDefault(this) // beim ersten Start Englisch erzwingen
        enableEdgeToEdge()
        setContent { BoatSpeedyApp() }
    }
}

@Composable
private fun BoatSpeedyApp(vm: SpeedViewModel = viewModel()) {
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
            val battery by vm.battery.collectAsStateWithLifecycle()
            val dashBattery by vm.dashboardBattery.collectAsStateWithLifecycle()
            val dashRange by vm.dashboardRange.collectAsStateWithLifecycle()

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
                    Screen.SETTINGS_DASHBOARD, Screen.SETTINGS_APPEARANCE, Screen.SETTINGS_LANGUAGE -> Screen.SETTINGS
                    else -> Screen.SPEED
                }
            }

            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(16.dp),
                        )
                        HorizontalDivider()
                        DrawerItem(R.string.nav_speed, Icons.Filled.Speed, screen == Screen.SPEED) { goTo(Screen.SPEED) }
                        DrawerItem(R.string.nav_battery, Icons.Filled.BatteryFull, screen == Screen.BATTERY) { goTo(Screen.BATTERY) }
                        DrawerItem(R.string.settings, Icons.Filled.Settings, screen.name.startsWith("SETTINGS")) { goTo(Screen.SETTINGS) }
                        DrawerItem(R.string.about, Icons.Filled.Info, screen == Screen.ABOUT) { goTo(Screen.ABOUT) }
                    }
                },
            ) {
                when (screen) {
                    Screen.SETTINGS -> SettingsHomeScreen(
                        onDashboard = { screen = Screen.SETTINGS_DASHBOARD },
                        onAppearance = { screen = Screen.SETTINGS_APPEARANCE },
                        onLanguage = { screen = Screen.SETTINGS_LANGUAGE },
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
                        onShowSatDetails = vm::setShowSatDetails,
                        onBack = { screen = Screen.SETTINGS },
                    )

                    Screen.SETTINGS_APPEARANCE -> AppearanceSettingsScreen(
                        settings = settings,
                        onTheme = vm::setTheme,
                        onKeepScreenOn = vm::setKeepScreenOn,
                        onBack = { screen = Screen.SETTINGS },
                    )

                    Screen.SETTINGS_LANGUAGE -> LanguageSettingsScreen(
                        language = LanguageHelper.current(context),
                        onLanguage = { LanguageHelper.set(context, it) },
                        onBack = { screen = Screen.SETTINGS },
                    )

                    Screen.ABOUT -> AboutScreen(onOpenMenu = { openDrawer() })

                    Screen.BATTERY -> BatteryScreen(
                        settings = settings,
                        hub = battery,
                        onScan = { withBt { vm.scanBattery() } },
                        onStopScan = vm::stopScan,
                        onAdd = { device -> withBt { vm.addBattery(device) } },
                        onConnect = { address -> withBt { vm.connectBattery(address) } },
                        onDisconnect = vm::disconnectBattery,
                        onToggleActive = vm::setBatteryActive,
                        onRemove = vm::removeBattery,
                        onBms = vm::setBms,
                        onBankMode = vm::setBankMode,
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
                            batteryData = dashBattery,
                            range = dashRange,
                            batteryOptions = batteryOptions,
                            selectedBattery = settings.dashboardBattery,
                            onSelectBattery = vm::setDashboardBattery,
                            onStartTrip = vm::startTrip,
                            onStopTrip = vm::stopTrip,
                            onOpenMenu = { openDrawer() },
                        )
                    }
                }
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
