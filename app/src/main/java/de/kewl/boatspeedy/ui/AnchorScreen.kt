package de.kewl.boatspeedy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.anchor.AnchorState
import de.kewl.boatspeedy.location.GpsState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnchorScreen(
    anchor: AnchorState,
    gps: GpsState,
    radiusM: Int,
    onRadiusChange: (Int) -> Unit,
    onSetAnchor: () -> Unit,
    onRaise: () -> Unit,
    onSilence: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.anchor_watch_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenMenu) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!anchor.active) {
                val hasFix = gps.latitude != null && gps.longitude != null
                Text(
                    stringResource(R.string.anchor_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                RadiusField(radiusM, onRadiusChange)
                Button(onClick = onSetAnchor, enabled = hasFix, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Anchor, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.anchor_set))
                }
                if (!hasFix) {
                    Text(
                        stringResource(R.string.anchor_no_fix),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                    )
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (anchor.alarming) {
                            Text(
                                stringResource(R.string.anchor_dragging),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Text(
                                stringResource(R.string.anchor_holding),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        ValueRow(stringResource(R.string.anchor_distance), "${anchor.distanceM.roundToInt()} m")
                        ValueRow(stringResource(R.string.anchor_radius), "${anchor.radiusM} m")
                    }
                }
                if (anchor.alarming) {
                    OutlinedButton(onClick = onSilence, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.NotificationsOff, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.anchor_silence))
                    }
                }
                Button(onClick = onRaise, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.anchor_raise))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RadiusField(radiusM: Int, onRadiusChange: (Int) -> Unit) {
    var text by remember(radiusM) { mutableStateOf(radiusM.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new.filter { it.isDigit() }.take(4)
            text.toIntOrNull()?.let { if (it in 5..1000) onRadiusChange(it) }
        },
        label = { Text(stringResource(R.string.anchor_radius)) },
        suffix = { Text("m") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
