package com.muz.omnikit.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muz.omnikit.core.model.AppSettings
import com.muz.omnikit.core.model.Language
import com.muz.omnikit.core.model.ToolCategory
import com.muz.omnikit.core.model.ToolDefinition
import com.muz.omnikit.core.model.ToolId
import com.muz.omnikit.core.registry.ToolRegistry
import com.muz.omnikit.core.updater.UpdateStatus
import com.muz.omnikit.core.updater.UpdateUiState
import com.muz.omnikit.ui.components.OmniKitLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    settings: AppSettings,
    updateState: UpdateUiState,
    onOpenTool: (ToolId) -> Unit,
    onOpenSettings: (section: String) -> Unit,
    onInstallUpdate: (String) -> Unit
) {
    val lang = settings.language
    var menuExpanded by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf(ToolCategory.ALL) }

    val visibleTools = remember(settings.hiddenToolIds, selectedCategory, searchQuery, lang) {
        ToolRegistry.allTools.filter { tool ->
            val enabled = tool.id.routeKey !in settings.hiddenToolIds
            val matchesCat = selectedCategory == ToolCategory.ALL || tool.category == selectedCategory
            val q = searchQuery.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                tool.nameMs.lowercase().contains(q) ||
                tool.nameEn.lowercase().contains(q) ||
                tool.descMs.lowercase().contains(q) ||
                tool.descEn.lowercase().contains(q)
            enabled && matchesCat && matchesQuery
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OmniKitLogo(size = 34.dp)
                        Column {
                            Text(
                                text = "OmniKitMY",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = lang.tr("Menu Tetapan", "Settings Menu")
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                                text = { Text(lang.tr("Tetapan", "Settings")) },
                                onClick = {
                                    menuExpanded = false
                                    onOpenSettings("all")
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                                text = { Text(lang.tr("Pilih Paparan Alatan", "Choose Visible Tools")) },
                                onClick = {
                                    menuExpanded = false
                                    onOpenSettings("tools")
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Rounded.Palette, contentDescription = null) },
                                text = { Text(lang.tr("Tema Warna", "Color Theme")) },
                                onClick = {
                                    menuExpanded = false
                                    onOpenSettings("theme")
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Rounded.SystemUpdate, contentDescription = null) },
                                text = { Text(lang.tr("Kemaskini Aplikasi", "App Updates")) },
                                onClick = {
                                    menuExpanded = false
                                    onOpenSettings("update")
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Ready-to-install banner if a background update finished downloading
            if (updateState.status == UpdateStatus.COMPLETED && !updateState.downloadedApkPath.isNullOrBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.InstallMobile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = lang.tr(
                                    "Kemaskini v${updateState.updateInfo?.versionName.orEmpty()} sedia dipasang",
                                    "Update v${updateState.updateInfo?.versionName.orEmpty()} ready to install"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onInstallUpdate(updateState.downloadedApkPath) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = lang.tr("Pasang", "Install"),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                placeholder = {
                    Text(
                        text = lang.tr("Cari alatan...", "Search tools..."),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            // Category Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ToolCategory.entries) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat.title(lang), maxLines = 1) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (visibleTools.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = lang.tr(
                                "Tiada alatan dipaparkan.",
                                "No tools currently visible."
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedButton(onClick = { onOpenSettings("tools") }) {
                            Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(lang.tr("Urus Paparan Alatan", "Manage Visible Tools"))
                        }
                    }
                }
            } else {
                // Adaptive Grid automatically scales cleanly from compact 5" phones to wide tablets & foldables
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(visibleTools, key = { it.id.routeKey }) { tool ->
                        ToolGridCard(
                            tool = tool,
                            lang = lang,
                            onClick = { onOpenTool(tool.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolGridCard(
    tool: ToolDefinition,
    lang: Language,
    onClick: () -> Unit
) {
    val accent = Color(tool.accentHex)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = tool.name(lang),
                    tint = accent,
                    modifier = Modifier.size(25.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = tool.name(lang),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tool.desc(lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
