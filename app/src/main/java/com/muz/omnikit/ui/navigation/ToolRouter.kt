package com.muz.omnikit.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.muz.omnikit.core.model.AppSettings
import com.muz.omnikit.core.model.Language
import com.muz.omnikit.core.model.ThemeMode
import com.muz.omnikit.core.model.ToolId
import com.muz.omnikit.core.updater.UpdateUiState
import com.muz.omnikit.tools.audio.FlashlightScreen
import com.muz.omnikit.tools.audio.SoundMeterScreen
import com.muz.omnikit.tools.audio.SoundTransferScreen
import com.muz.omnikit.tools.audio.SpeakerAudioScreen
import com.muz.omnikit.tools.camera.MagnifierColorScreen
import com.muz.omnikit.tools.camera.OcrScannerScreen
import com.muz.omnikit.tools.camera.QRScannerScreen
import com.muz.omnikit.tools.camera.RangefinderScreen
import com.muz.omnikit.tools.measure.ProtractorScreen
import com.muz.omnikit.tools.measure.RulerScreen
import com.muz.omnikit.tools.measure.VibrometerScreen
import com.muz.omnikit.tools.media.DocumentPdfScreen
import com.muz.omnikit.tools.media.ExifScannerScreen
import com.muz.omnikit.tools.media.ImageCompressorScreen
import com.muz.omnikit.tools.network.GpsSpeedScreen
import com.muz.omnikit.tools.network.NfcToolScreen
import com.muz.omnikit.tools.network.SignalMeterScreen
import com.muz.omnikit.tools.sensors.CompassScreen
import com.muz.omnikit.tools.sensors.LevelerScreen
import com.muz.omnikit.tools.sensors.LightMeterScreen
import com.muz.omnikit.tools.sensors.MetalDetectorScreen
import com.muz.omnikit.tools.utility.DateCalculatorScreen
import com.muz.omnikit.tools.utility.DeviceTesterScreen
import com.muz.omnikit.tools.utility.TallyStopwatchScreen
import com.muz.omnikit.tools.utility.UnitConverterScreen
import com.muz.omnikit.ui.home.HomeScreen
import com.muz.omnikit.ui.settings.SettingsScreen

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
    when (toolId) {
        // === PHASE 2: SENSORS & ENVIRONMENT ===
        ToolId.COMPASS -> CompassScreen(lang = lang, onBack = onBack)
        ToolId.LEVELER -> LevelerScreen(lang = lang, onBack = onBack)
        ToolId.METAL_DETECTOR -> MetalDetectorScreen(lang = lang, onBack = onBack)
        ToolId.LIGHT_METER -> LightMeterScreen(lang = lang, onBack = onBack)

        // === PHASE 3: PHYSICAL MEASURE & VIBRATION ===
        ToolId.RULER -> RulerScreen(lang = lang, onBack = onBack)
        ToolId.PROTRACTOR -> ProtractorScreen(lang = lang, onBack = onBack)
        ToolId.VIBROMETER -> VibrometerScreen(lang = lang, onBack = onBack)

        // === PHASE 4: AUDIO & FLASHLIGHT ===
        ToolId.SOUND_METER -> SoundMeterScreen(lang = lang, onBack = onBack)
        ToolId.SPEAKER_AUDIO -> SpeakerAudioScreen(lang = lang, onBack = onBack)
        ToolId.FLASHLIGHT -> FlashlightScreen(lang = lang, onBack = onBack)

        // === PHASE 5: SMART CAMERA & SCANNERS ===
        ToolId.QR_SCANNER -> QRScannerScreen(lang = lang, onBack = onBack)
        ToolId.OCR_SCANNER -> OcrScannerScreen(lang = lang, onBack = onBack)
        ToolId.MAGNIFIER_COLOR -> MagnifierColorScreen(lang = lang, onBack = onBack)

        // === PHASE 6: DOCUMENT, PDF, RANGEFINDER & IMAGE ===
        ToolId.DOC_PDF -> DocumentPdfScreen(lang = lang, onBack = onBack)
        ToolId.RANGEFINDER -> RangefinderScreen(lang = lang, onBack = onBack)
        ToolId.EXIF_SCANNER -> ExifScannerScreen(lang = lang, onBack = onBack)
        ToolId.IMAGE_COMPRESSOR -> ImageCompressorScreen(lang = lang, onBack = onBack)

        // === PHASE 7: GPS, SIGNAL & NFC ===
        ToolId.GPS_SPEED -> GpsSpeedScreen(lang = lang, onBack = onBack)
        ToolId.SIGNAL_METER -> SignalMeterScreen(lang = lang, onBack = onBack)
        ToolId.NFC_TOOL -> NfcToolScreen(lang = lang, onBack = onBack)

        // === PHASE 8: UTILITIES, COUNTER & DEVICE TESTER ===
        ToolId.UNIT_CONVERTER -> UnitConverterScreen(lang = lang, onBack = onBack)
        ToolId.DATE_CALC -> DateCalculatorScreen(lang = lang, onBack = onBack)
        ToolId.TALLY_STOPWATCH -> TallyStopwatchScreen(lang = lang, onBack = onBack)
        ToolId.DEVICE_TESTER -> DeviceTesterScreen(lang = lang, onBack = onBack)

        // === PHASE 9: SUARADROP PRO (ACOUSTIC + OFFLINE LAN DROP) ===
        ToolId.SUARA_DROP -> SoundTransferScreen(lang = lang, onBack = onBack)
    }
}
