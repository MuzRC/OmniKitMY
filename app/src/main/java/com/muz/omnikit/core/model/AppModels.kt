package com.muz.omnikit.core.model

import androidx.compose.ui.graphics.vector.ImageVector

enum class Language(val code: String) {
    MS("ms"),
    EN("en");

    fun tr(ms: String, en: String): String = if (this == MS) ms else en

    companion object {
        fun fromCode(code: String?): Language =
            entries.firstOrNull { it.code == code } ?: MS
    }
}

enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    DARK("dark"),
    LIGHT("light");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class ToolCategory(val titleMs: String, val titleEn: String) {
    ALL("Semua", "All"),
    SENSORS("Sensor & Ukuran", "Sensors & Measure"),
    AUDIO_LIGHT("Audio & Cahaya", "Audio & Light"),
    CAMERA_SCAN("Kamera & Imbasan", "Camera & Scan"),
    IMAGE_MEDIA("Imej & Media", "Image & Media"),
    GPS_NETWORK("GPS, Isyarat & NFC", "GPS, Signal & NFC"),
    UTILITY("Utiliti & Peranti", "Utility & Device");

    fun title(lang: Language): String = lang.tr(titleMs, titleEn)
}

enum class ToolId(val routeKey: String) {
    // Phase 2: Sensors & Environment
    COMPASS("compass"),
    LEVELER("leveler"),
    METAL_DETECTOR("metal_detector"),
    LIGHT_METER("light_meter"),

    // Phase 3: Physical Measure & Vibration
    RULER("ruler"),
    PROTRACTOR("protractor"),
    VIBROMETER("vibrometer"),

    // Phase 4: Audio & Flashlight
    SOUND_METER("sound_meter"),
    SPEAKER_AUDIO("speaker_audio"),
    FLASHLIGHT("flashlight"),

    // Phase 5: Smart Camera & Scanners
    QR_SCANNER("qr_scanner"),
    OCR_SCANNER("ocr_scanner"),
    MAGNIFIER_COLOR("magnifier_color"),

    // Phase 6: Document, PDF, Rangefinder & Image
    DOC_PDF("doc_pdf"),
    RANGEFINDER("rangefinder"),
    EXIF_SCANNER("exif_scanner"),
    IMAGE_COMPRESSOR("image_compressor"),

    // Phase 7: GPS, Signal & NFC
    GPS_SPEED("gps_speed"),
    SIGNAL_METER("signal_meter"),
    NFC_TOOL("nfc_tool"),

    // Phase 8: Utilities, Counter & Device Tester
    UNIT_CONVERTER("unit_converter"),
    DATE_CALC("date_calc"),
    TALLY_STOPWATCH("tally_stopwatch"),
    DEVICE_TESTER("device_tester");

    companion object {
        fun fromRouteKey(key: String?): ToolId? =
            entries.firstOrNull { it.routeKey == key }
    }
}

data class ToolDefinition(
    val id: ToolId,
    val category: ToolCategory,
    val icon: ImageVector,
    val accentHex: Long,
    val nameMs: String,
    val nameEn: String,
    val descMs: String,
    val descEn: String,
    val buildPhase: Int
) {
    fun name(lang: Language): String = lang.tr(nameMs, nameEn)
    fun desc(lang: Language): String = lang.tr(descMs, descEn)
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: Language = Language.MS,
    val hiddenToolIds: Set<String> = emptySet()
)
