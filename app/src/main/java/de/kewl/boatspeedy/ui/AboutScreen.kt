package de.kewl.boatspeedy.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.kewl.boatspeedy.BuildConfig
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.update.Repo
import de.kewl.boatspeedy.update.UpdateChecker
import de.kewl.boatspeedy.update.UpdateResult
import kotlinx.coroutines.launch

private sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data class Result(val result: UpdateResult) : UpdateUi
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onOpenMenu: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateUi>(UpdateUi.Idle) }

    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(onClick = onOpenMenu) {
                        Icon(
                            Icons.Filled.Menu,
                            contentDescription = stringResource(R.string.menu),
                        )
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
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            InfoRow(stringResource(R.string.version), BuildConfig.VERSION_NAME)
            InfoRow(stringResource(R.string.build_label), BuildConfig.GIT_SHA)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            LinkRow(stringResource(R.string.source_code), Repo.OWNER + "/" + Repo.NAME) {
                openUrl(Repo.URL)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            InfoRow(stringResource(R.string.license_label), stringResource(R.string.license_value))
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Update-Prüfung
            UpdateSection(
                state = state,
                onCheck = {
                    state = UpdateUi.Checking
                    scope.launch {
                        state = UpdateUi.Result(UpdateChecker.check(BuildConfig.VERSION_NAME))
                    }
                },
                onOpen = ::openUrl,
            )
        }
    }
}

@Composable
private fun UpdateSection(
    state: UpdateUi,
    onCheck: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state) {
            is UpdateUi.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.checking))
            }

            is UpdateUi.Result -> when (val r = state.result) {
                is UpdateResult.UpToDate -> {
                    Text(stringResource(R.string.up_to_date))
                    OutlinedButton(onClick = onCheck) { Text(stringResource(R.string.check_update)) }
                }

                is UpdateResult.Available -> {
                    Text(
                        text = stringResource(R.string.update_available, r.version),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onOpen(r.downloadUrl ?: r.releaseUrl) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.download))
                        }
                        OutlinedButton(onClick = { onOpen(r.releaseUrl) }) {
                            Text(stringResource(R.string.open_release))
                        }
                    }
                }

                is UpdateResult.Failed -> {
                    Text(
                        text = stringResource(R.string.update_check_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = onCheck) { Text(stringResource(R.string.check_update)) }
                }
            }

            UpdateUi.Idle -> Button(onClick = onCheck) {
                Text(stringResource(R.string.check_update))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(value, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
    }
}

@Composable
private fun LinkRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = stringResource(R.string.open_on_github),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
