package de.kewl.boatspeedy.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.kewl.boatspeedy.BuildConfig
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.update.Repo
import de.kewl.boatspeedy.update.UpdateChecker
import de.kewl.boatspeedy.update.UpdateResult
import de.kewl.boatspeedy.util.AppLanguage
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.launch

private sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data class Result(val result: UpdateResult) : UpdateUi
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    onOpenMenu: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateUi>(UpdateUi.Idle) }

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    fun openIssue(titlePrefix: String, label: String) {
        val body = "\n\n---\n" +
            "**App:** ${BuildConfig.VERSION_NAME}\n" +
            "**Android:** ${Build.VERSION.RELEASE}\n" +
            "**Device:** ${Build.MANUFACTURER} ${Build.MODEL}\n"
        val url = Repo.ISSUES_NEW_URL +
            "?labels=" + Uri.encode(label) +
            "&title=" + Uri.encode(titlePrefix) +
            "&body=" + Uri.encode(body)
        openUrl(url)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(onClick = onOpenMenu) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu))
                    }
                },
                actions = { LanguageToggle(language, onLanguage) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // --- Kopf: echtes App-Icon (Tacho + Welle), Name, Version ---
            Spacer(Modifier.size(20.dp))
            val appIcon = remember {
                runCatching {
                    context.packageManager.getApplicationIcon(context.packageName)
                        .toBitmap(width = 144, height = 144).asImageBitmap()
                }.getOrNull()
            }
            if (appIcon != null) {
                Image(
                    bitmap = appIcon,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)),
                )
            } else {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.DirectionsBoat, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${stringResource(R.string.version)} ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.size(24.dp))

            // --- Feedback ---
            SectionCard(stringResource(R.string.about_feedback)) {
                ActionRow(Icons.Filled.BugReport, stringResource(R.string.report_bug)) {
                    openIssue("[Bug] ", "bug")
                }
                RowDivider()
                ActionRow(Icons.Filled.Lightbulb, stringResource(R.string.request_feature)) {
                    openIssue("[Feature] ", "enhancement")
                }
            }
            Spacer(Modifier.size(16.dp))

            // --- Projekt ---
            SectionCard(stringResource(R.string.about_project)) {
                ActionRow(Icons.Filled.Star, stringResource(R.string.star_github)) { openUrl(Repo.URL) }
                RowDivider()
                ActionRow(Icons.Filled.Code, stringResource(R.string.view_source)) { openUrl(Repo.URL) }
                RowDivider()
                ActionRow(
                    Icons.Filled.Favorite,
                    stringResource(R.string.support_project),
                    tint = MaterialTheme.colorScheme.error,
                ) { openUrl(Repo.SUPPORT_URL) }
            }
            Spacer(Modifier.size(16.dp))

            // --- Update ---
            SectionCard(stringResource(R.string.update_section)) {
                UpdateBlock(
                    state = state,
                    onCheck = {
                        state = UpdateUi.Checking
                        scope.launch { state = UpdateUi.Result(UpdateChecker.check(BuildConfig.VERSION_NAME)) }
                    },
                    onOpen = ::openUrl,
                )
            }
            Spacer(Modifier.size(24.dp))

            Text(
                "${stringResource(R.string.license_value)} · © ${Repo.OWNER}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun LanguageToggle(language: AppLanguage, onLanguage: (AppLanguage) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 8.dp),
    ) {
        LangText("DE", language == AppLanguage.GERMAN) { onLanguage(AppLanguage.GERMAN) }
        Text(" · ", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        LangText("EN", language == AppLanguage.ENGLISH) { onLanguage(AppLanguage.ENGLISH) }
    }
}

@Composable
private fun LangText(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 14.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        color = if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 2.dp, vertical = 4.dp),
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth()) { Column { content() } }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, modifier = Modifier.weight(1f).padding(start = 16.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun RowDivider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
    )
}

@Composable
private fun UpdateBlock(state: UpdateUi, onCheck: () -> Unit, onOpen: (String) -> Unit) {
    when (state) {
        is UpdateUi.Idle -> ActionRow(Icons.Filled.SystemUpdate, stringResource(R.string.check_update)) { onCheck() }

        is UpdateUi.Checking -> Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.checking))
        }

        is UpdateUi.Result -> when (val r = state.result) {
            is UpdateResult.UpToDate -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.up_to_date))
                OutlinedButton(onClick = onCheck) { Text(stringResource(R.string.check_update)) }
            }
            is UpdateResult.Available -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.update_available, r.version),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Button(onClick = { onOpen(r.downloadUrl ?: r.releaseUrl) }) {
                        Text(stringResource(R.string.download))
                    }
                    OutlinedButton(onClick = { onOpen(r.releaseUrl) }) {
                        Text(stringResource(R.string.open_release))
                    }
                }
            }
            is UpdateResult.Failed -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.update_check_failed), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onCheck) { Text(stringResource(R.string.check_update)) }
            }
        }
    }
}
