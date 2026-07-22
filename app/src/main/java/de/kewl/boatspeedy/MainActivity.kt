package de.kewl.boatspeedy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kewl.boatspeedy.data.ThemeMode
import de.kewl.boatspeedy.ui.SettingsScreen
import de.kewl.boatspeedy.ui.SpeedScreen
import de.kewl.boatspeedy.ui.SpeedViewModel
import de.kewl.boatspeedy.ui.theme.BoatSpeedyTheme
import androidx.compose.foundation.isSystemInDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

            var showSettings by rememberSaveable { mutableStateOf(false) }
            val gps by vm.gps.collectAsStateWithLifecycle()
            val speedText by vm.displaySpeed.collectAsStateWithLifecycle()

            if (showSettings) {
                SettingsScreen(
                    settings = settings,
                    onUnit = vm::setUnit,
                    onDecimals = vm::setDecimals,
                    onTheme = vm::setTheme,
                    onKeepScreenOn = vm::setKeepScreenOn,
                    onSmoothing = vm::setSmoothing,
                    onShowSatDetails = vm::setShowSatDetails,
                    onBack = { showSettings = false },
                )
            } else {
                SpeedScreen(
                    speedText = speedText,
                    gps = gps,
                    settings = settings,
                    onOpenSettings = { showSettings = true },
                )
            }
        }
    }
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
