package com.muz.omnikit.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.muz.omnikit.BuildConfig
import com.muz.omnikit.core.model.AppSettings
import com.muz.omnikit.core.model.Language
import com.muz.omnikit.core.model.ThemeMode
import com.muz.omnikit.core.model.ToolCategory
import com.muz.omnikit.core.model.ToolId
import com.muz.omnikit.core.registry.ToolRegistry
import com.muz.omnikit.core.updater.UpdateChecker
import com.muz.omnikit.core.updater.UpdateDownloadService
import com.muz.omnikit.core.updater.UpdateInfo
import com.muz.omnikit.core.updater.UpdateStateHolder
import com.muz.omnikit.core.updater.UpdateStatus
import com.muz.omnikit.core.updater.UpdateUiState
import com.muz.omnikit.ui.components.OmniKitLogo
import com.muz.omnikit.ui.components.ToolScaffold
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SettingsScreen(
    initialSection: String,
    settings: AppSettings,
    updateState: UpdateUiState,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetLanguage: (Language) -> Unit,
    onSetToolVisible: (ToolId, Boolean) -> Unit,
    onShowAllTools: () -> Unit,
    onHideAllTools: () -> Unit,
    onInstallUpdate: (String) -> Unit,
    onBack: () -> Unit
) {
    val lang = settings.language
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var pendingDownloadInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Proceed with foreground service download regardless so update still runs reliably
        pendingDownloadInfo?.let { info ->
            UpdateDownloadService.startDownload(context, info)
            pendingDownloadInfo = null
        }
    }

    fun startBackgroundDownload(info: UpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownloadInfo = info
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            UpdateDownloadService.startDownload(context, info)
        }
    }

    // Smoothly scroll to targeted section when entered via 3-dot shortcut
    LaunchedEffect(initialSection) {
        when (initialSection) {
            "theme" -> listState.animateScrollToItem(0)
            "update" -> listState.animateScrollToItem(2)
            "tools" -> listState.animateScrollToItem(3)
        }
    }

    val activeCount = ToolRegistry.allTools.size - settings.hiddenToolIds.size
    val groupedTools = remember {
        ToolCategory.entries
            .filter { it != ToolCategory.ALL }
            .map { cat -> cat to ToolRegistry.allTools.filter { it.category == cat } }
    }

    ToolScaffold(
        title = lang.tr("Tetapan", "Settings"),
        subtitle = lang.tr("Tema, kemaskini & paparan alatan", "Theme, updates & visible tools"),
        onBack = onBack
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // === SECTION 1: COLOR THEME ===
            item(key = "sec_theme") {
                SettingsSectionCard(
                    icon = Icons.Rounded.Palette,
                    title = lang.tr("Pilihan Warna Tema", "Color Theme")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeOptionTile(
                            label = lang.tr("Ikut Sistem", "System"),
                            icon = Icons.Rounded.BrightnessAuto,
                            selected = settings.themeMode == ThemeMode.SYSTEM,
                            onClick = { onSetThemeMode(ThemeMode.SYSTEM) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionTile(
                            label = lang.tr("Gelap", "Dark"),
                            icon = Icons.Rounded.DarkMode,
                            selected = settings.themeMode == ThemeMode.DARK,
                            onClick = { onSetThemeMode(ThemeMode.DARK) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionTile(
                            label = lang.tr("Cerah", "Light"),
                            icon = Icons.Rounded.LightMode,
                            selected = settings.themeMode == ThemeMode.LIGHT,
                            onClick = { onSetThemeMode(ThemeMode.LIGHT) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // === SECTION 2: LANGUAGE ===
            item(key = "sec_language") {
                SettingsSectionCard(
                    icon = Icons.Rounded.Language,
                    title = lang.tr("Bahasa Antaramuka", "App Language")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LanguageChipButton(
                            label = "Bahasa Melayu",
                            selected = settings.language == Language.MS,
                            onClick = { onSetLanguage(Language.MS) },
                            modifier = Modifier.weight(1f)
                        )
                        LanguageChipButton(
                            label = "English",
                            selected = settings.language == Language.EN,
                            onClick = { onSetLanguage(Language.EN) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // === SECTION 3: MANUAL BACKGROUND UPDATE ===
            item(key = "sec_update") {
                SettingsSectionCard(
                    icon = Icons.Rounded.SystemUpdate,
                    title = lang.tr("Kawasan Kemaskini Manual", "Manual App Updates")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OmniKitLogo(size = 42.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "OmniKitMY v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = lang.tr(
                                    "Build ${BuildConfig.VERSION_CODE} • Muat turun latar belakang (terus jalan walau skrin padam)",
                                    "Build ${BuildConfig.VERSION_CODE} • Screen-off resilient background download"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when (updateState.status) {
                        UpdateStatus.CHECKING -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                                Text(
                                    text = lang.tr("Menyemak versi terkini...", "Checking latest version..."),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        UpdateStatus.UP_TO_DATE -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981)
                                )
                                Text(
                                    text = lang.tr("Aplikasi sudah versi terkini.", "You are on the latest version."),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    scope.launch { performManualCheck() }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(lang.tr("Semak Semula", "Check Again"))
                            }
                        }

                        UpdateStatus.AVAILABLE -> {
                            val info = updateState.updateInfo
                            if (info != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(12.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = lang.tr(
                                                "Versi baru tersedia: v${info.versionName}",
                                                "New version available: v${info.versionName}"
                                            ),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (info.releaseNotes.isNotBlank()) {
                                            Text(
                                                text = info.releaseNotes,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { startBackgroundDownload(info) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(lang.tr("Muat Turun Kemaskini", "Download Update"))
                                }
                            }
                        }

                        UpdateStatus.DOWNLOADING -> {
                            val mbDone = String.format(Locale.US, "%.1f", updateState.downloadedBytes / 1_048_576.0)
                            val mbTotal = if (updateState.totalBytes > 0) {
                                String.format(Locale.US, " / %.1f MB", updateState.totalBytes / 1_048_576.0)
                            } else {
                                " MB"
                            }
                            Text(
                                text = lang.tr(
                                    "Memuat turun di latar belakang: ${updateState.progressPercent}% ($mbDone$mbTotal)",
                                    "Downloading in background: ${updateState.progressPercent}% ($mbDone$mbTotal)"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (updateState.totalBytes > 0) {
                                LinearProgressIndicator(
                                    progress = { updateState.progressPercent / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = lang.tr(
                                    "Anda boleh tutup skrin atau guna aplikasi lain. Pilihan pasang akan dipaparkan sebaik sahaja selesai.",
                                    "You may lock your screen or switch apps. Install prompt appears automatically once finished."
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { UpdateDownloadService.cancelDownload(context) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(lang.tr("Batal Muat Turun", "Cancel Download"))
                            }
                        }

                        UpdateStatus.COMPLETED -> {
                            val path = updateState.downloadedApkPath
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                                Text(
                                    text = lang.tr(
                                        "Muat turun selesai! Sedia untuk dipasang.",
                                        "Download complete! Ready to install."
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            if (!path.isNullOrBlank()) {
                                Button(
                                    onClick = { onInstallUpdate(path) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(lang.tr("Pasang Kemaskini Sekarang", "Install Update Now"))
                                }
                            }
                        }

                        UpdateStatus.ERROR -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = updateState.errorMessage ?: lang.tr("Gagal menyemak kemaskini.", "Update check failed."),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { scope.launch { performManualCheck() } },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(lang.tr("Cuba Semula", "Try Again"))
                            }
                        }

                        UpdateStatus.IDLE -> {
                            Button(
                                onClick = { scope.launch { performManualCheck() } },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(lang.tr("Semak Kemaskini Manual", "Check for Updates"))
                            }
                        }
                    }
                }
            }

            // === SECTION 4: TOOL VISIBILITY MANAGER ===
            item(key = "sec_tools_header") {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = lang.tr("Paparan Alatan", "Visible Tools"),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "$activeCount / ${ToolRegistry.allTools.size} " +
                                            lang.tr("dipaparkan di skrin utama", "shown on home screen"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onShowAllTools) {
                                Text(lang.tr("Papar Semua", "Show All"))
                            }
                            TextButton(onClick = onHideAllTools) {
                                Text(lang.tr("Sembunyi Semua", "Hide All"))
                            }
                        }
                    }
                }
            }

            items(groupedTools, key = { it.first.name }) { (category, tools) ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(
                            text = category.title(lang),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        tools.forEachIndexed { idx, tool ->
                            val isVisible = tool.id.routeKey !in settings.hiddenToolIds
                            val accent = Color(tool.accentHex)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSetToolVisible(tool.id, !isVisible) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(accent.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = tool.icon,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tool.name(lang),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = tool.desc(lang),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = isVisible,
                                    onCheckedChange = { onSetToolVisible(tool.id, it) }
                                )
                            }
                            if (idx < tools.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun performManualCheck() {
    UpdateStateHolder.update {
        it.copy(status = UpdateStatus.CHECKING, errorMessage = null)
    }
    val result = UpdateChecker.checkForUpdate()
    result.onSuccess { info ->
        if (info != null) {
            UpdateStateHolder.update {
                it.copy(status = UpdateStatus.AVAILABLE, updateInfo = info, errorMessage = null)
            }
        } else {
            UpdateStateHolder.update {
                it.copy(status = UpdateStatus.UP_TO_DATE, updateInfo = null, errorMessage = null)
            }
        }
    }.onFailure { err ->
        UpdateStateHolder.update {
            it.copy(status = UpdateStatus.ERROR, errorMessage = err.localizedMessage ?: "Network error")
        }
    }
}

@Composable
private fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun ThemeOptionTile(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = border
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = label, tint = content, modifier = Modifier.size(22.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LanguageChipButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = border
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = content
            )
        }
    }
}
