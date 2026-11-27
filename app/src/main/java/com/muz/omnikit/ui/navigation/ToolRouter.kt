package com.muz.omnikit.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.muz.omnikit.core.model.AppSettings
import com.muz.omnikit.core.model.Language
import com.muz.omnikit.core.model.ThemeMode
import com.muz.omnikit.core.model.ToolId
import com.muz.omnikit.core.registry.ToolRegistry
import com.muz.omnikit.core.updater.UpdateUiState
import com.muz.omnikit.ui.components.ToolScaffold
import com.muz.omnikit.ui.home.HomeScreen
import com.muz.omnikit.ui.settings.SettingsScreen

/**
 * Central Navigation Router.
 * In Phases 2 through 8, only this router file is updated to bind each newly
 * added real native tool screen to its ToolId.
 */
@Composable
fun OmniKitNavHost(
    settings: AppSettings,
    updateState: UpdateUiState,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetLanguage: (Language) -> Unit,
    onSetToolVisible: (ToolId, Boolean) -> Unit,
    onShowAllTools: () -> Unit,
    onHideAllTools: () -> Unit,
    onInstallUpdate: (String) -> Unit
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                settings = settings,
                updateState = updateState,
                onOpenTool = { toolId ->
                    navController.navigate("tool/${toolId.routeKey}") {
                        launchSingleTop = true
                    }
                },
                onOpenSettings = { section ->
                    navController.navigate("settings/$section") {
                        launchSingleTop = true
                    }
                },
                onInstallUpdate = onInstallUpdate
            )
        }

        composable(
            route = "settings/{section}",
            arguments = listOf(navArgument("section") { type = NavType.StringType })
        ) { backStack ->
            val section = backStack.arguments?.getString("section") ?: "all"
            SettingsScreen(
                initialSection = section,
                settings = settings,
                updateState = updateState,
                onSetThemeMode = onSetThemeMode,
                onSetLanguage = onSetLanguage,
                onSetToolVisible = onSetToolVisible,
                onShowAllTools = onShowAllTools,
                onHideAllTools = onHideAllTools,
                onInstallUpdate = onInstallUpdate,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "tool/{toolId}",
            arguments = listOf(navArgument("toolId") { type = NavType.StringType })
        ) { backStack ->
            val toolId = ToolId.fromRouteKey(backStack.arguments?.getString("toolId"))
            if (toolId == null) {
                navController.popBackStack()
                return@composable
            }
            ToolScreenDispatcher(
                toolId = toolId,
                lang = settings.language,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun ToolScreenDispatcher(
    toolId: ToolId,
    lang: Language,
    onBack: () -> Unit
) {
    // Phases 2-8 bind real native tool screens here
    val def = ToolRegistry.findById(toolId)
    ToolScaffold(
        title = def.name(lang),
        subtitle = def.desc(lang),
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = def.icon,
                contentDescription = null,
                tint = Color(def.accentHex),
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = lang.tr(
                    "Modul Fasa ${def.buildPhase} Belum Dipasang",
                    "Phase ${def.buildPhase} Module Pending"
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = lang.tr(
                    "Tambah fail ZIP Fasa ${def.buildPhase} ke projek untuk mengaktifkan alatan native penuh ini.",
                    "Merge Phase ${def.buildPhase} ZIP into the repository to activate this full native tool."
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
