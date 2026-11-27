package com.muz.omnikit.core.registry

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Architecture
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.BuildCircle
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SquareFoot
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ZoomIn
import com.muz.omnikit.core.model.ToolCategory
import com.muz.omnikit.core.model.ToolDefinition
import com.muz.omnikit.core.model.ToolId

object ToolRegistry {
    val allTools: List<ToolDefinition> = listOf(
        // === SENSORS & MEASURE ===
        ToolDefinition(
            id = ToolId.COMPASS,
            category = ToolCategory.SENSORS,
            icon = Icons.Rounded.Explore,
            accentHex = 0xFF38BDF8,
            nameMs = "Kompas Digital",
            nameEn = "Digital Compass",
            descMs = "Arah mata angin magnetik tepat",
            descEn = "Accurate magnetic heading",
            buildPhase = 2
        ),
        ToolDefinition(
            id = ToolId.LEVELER,
            category = ToolCategory.SENSORS,
            icon = Icons.Rounded.Balance,
            accentHex = 0xFF10B981,
            nameMs = "Peringkat Air",
            nameEn = "Spirit Level",
            descMs = "Aras baring, dinding & sisi 3-mod",
            descEn = "3-mode flat, plumb & edge level",
            buildPhase = 2
        ),
        ToolDefinition(
            id = ToolId.METAL_DETECTOR,
            category = ToolCategory.SENSORS,
            icon = Icons.Rounded.Radar,
            accentHex = 0xFFF59E0B,
            nameMs = "Pengesan Logam",
            nameEn = "Metal Detector",
            descMs = "Fluks medan magnet sebenar (µT)",
            descEn = "Real magnetic flux detector (µT)",
            buildPhase = 2
        ),
        ToolDefinition(
            id = ToolId.LIGHT_METER,
            category = ToolCategory.SENSORS,
            icon = Icons.Rounded.LightMode,
            accentHex = 0xFFFBBF24,
            nameMs = "Meter Cahaya",
            nameEn = "Light Meter",
            descMs = "Ukur kecerahan persekitaran (Lux)",
            descEn = "Measure ambient illuminance (Lux)",
            buildPhase = 2
        ),
        ToolDefinition(
            id = ToolId.RULER,
            category = ToolCategory.SENSORS,
            icon = Icons.Rounded.SquareFoot,
            accentHex = 0xFF60A5FA,
            nameMs = "Pembaris Skrin",
            nameEn = "Screen Ruler",
            descMs = "Skala mm & inci tepat ikut DPI",
            descEn = "DPI-calibrated mm & inch ruler",
            buildPhase = 3
        ),
        ToolDefinition(
            id = ToolId.PROTRACTOR,
            category = ToolCategory.SENSORS,
            icon = Icons.Rounded.Architecture,
            accentHex = 0xFF818CF8,
            nameMs = "Protraktor Sudut",
            nameEn = "Protractor",
            descMs = "Ukur sudut sentuhan & graviti",
            descEn = "Touch & gravity angle measurer",
            buildPhase = 3
        ),
        ToolDefinition(
            id = ToolId.VIBROMETER,
            category = ToolCategory.SENSORS,
            icon = Icons.Rounded.Vibration,
            accentHex = 0xFFFB7185,
            nameMs = "Pengukur Getaran",
            nameEn = "Vibrometer",
            descMs = "Graf gegaran enjin & permukaan",
            descEn = "Live vibration & seismic graph",
            buildPhase = 3
        ),

        // === AUDIO & LIGHT ===
        ToolDefinition(
            id = ToolId.SOUND_METER,
            category = ToolCategory.AUDIO_LIGHT,
            icon = Icons.Rounded.GraphicEq,
            accentHex = 0xFF22D3EE,
            nameMs = "Pengukur Bunyi",
            nameEn = "Sound Meter",
            descMs = "Tahap hingar desibel (dB) langsung",
            descEn = "Real-time decibel (dB) monitor",
            buildPhase = 4
        ),
        ToolDefinition(
            id = ToolId.SPEAKER_AUDIO,
            category = ToolCategory.AUDIO_LIGHT,
            icon = Icons.Rounded.VolumeUp,
            accentHex = 0xFF3B82F6,
            nameMs = "Audio & Pencuci Speaker",
            nameEn = "Tone & Speaker Cleaner",
            descMs = "Penjana nada PCM & halau air/habuk",
            descEn = "PCM wave generator & water eject",
            buildPhase = 4
        ),
        ToolDefinition(
            id = ToolId.FLASHLIGHT,
            category = ToolCategory.AUDIO_LIGHT,
            icon = Icons.Rounded.FlashlightOn,
            accentHex = 0xFFFACC15,
            nameMs = "Lampu Suluh Pro",
            nameEn = "Pro Flashlight",
            descMs = "Kecerahan, strob, SOS & kod Morse",
            descEn = "Brightness, strobe, SOS & Morse",
            buildPhase = 4
        ),

        // === CAMERA & SCAN ===
        ToolDefinition(
            id = ToolId.QR_SCANNER,
            category = ToolCategory.CAMERA_SCAN,
            icon = Icons.Rounded.QrCodeScanner,
            accentHex = 0xFF2DD4BF,
            nameMs = "Pengimbas & Penjana QR",
            nameEn = "QR Scanner & Creator",
            descMs = "Imbas kamera/galeri & jana PNG",
            descEn = "Live/gallery scan & PNG generator",
            buildPhase = 5
        ),
        ToolDefinition(
            id = ToolId.OCR_SCANNER,
            category = ToolCategory.CAMERA_SCAN,
            icon = Icons.Rounded.DocumentScanner,
            accentHex = 0xFFA78BFA,
            nameMs = "Pengimbas Teks OCR",
            nameEn = "OCR Text Scanner",
            descMs = "Ekstrak teks dari kamera & gambar",
            descEn = "Offline image-to-text extractor",
            buildPhase = 5
        ),
        ToolDefinition(
            id = ToolId.MAGNIFIER_COLOR,
            category = ToolCategory.CAMERA_SCAN,
            icon = Icons.Rounded.ZoomIn,
            accentHex = 0xFFF472B6,
            nameMs = "Kanta & Pengesan Warna",
            nameEn = "Magnifier & Color Picker",
            descMs = "Zum tinggi, beku gambar & kod HEX",
            descEn = "Zoom lens, freeze & HEX/RGB eyedropper",
            buildPhase = 5
        ),
        ToolDefinition(
            id = ToolId.DOC_PDF,
            category = ToolCategory.CAMERA_SCAN,
            icon = Icons.Rounded.PictureAsPdf,
            accentHex = 0xFFEF4444,
            nameMs = "Dokumen & PDF",
            nameEn = "Document & PDF",
            descMs = "Imbas halaman, bina & papar PDF",
            descEn = "Scan pages, build & view PDFs",
            buildPhase = 6
        ),
        ToolDefinition(
            id = ToolId.RANGEFINDER,
            category = ToolCategory.CAMERA_SCAN,
            icon = Icons.Rounded.Height,
            accentHex = 0xFF34D399,
            nameMs = "Pengukur Jarak & Tinggi",
            nameEn = "Camera Rangefinder",
            descMs = "Kira jarak & ketinggian objek",
            descEn = "Estimate distance & object height",
            buildPhase = 6
        ),

        // === IMAGE & MEDIA ===
        ToolDefinition(
            id = ToolId.EXIF_SCANNER,
            category = ToolCategory.IMAGE_MEDIA,
            icon = Icons.Rounded.ImageSearch,
            accentHex = 0xFF0EA5E9,
            nameMs = "Pengimbas & Buang EXIF",
            nameEn = "EXIF Inspector & Stripper",
            descMs = "Semak & padam data GPS gambar",
            descEn = "Inspect & wipe photo GPS metadata",
            buildPhase = 6
        ),
        ToolDefinition(
            id = ToolId.IMAGE_COMPRESSOR,
            category = ToolCategory.IMAGE_MEDIA,
            icon = Icons.Rounded.Compress,
            accentHex = 0xFF8B5CF6,
            nameMs = "Pemampat & Penukar Imej",
            nameEn = "Image Compressor",
            descMs = "Kecilkan saiz & tukar JPG/PNG/WebP",
            descEn = "Resize & convert JPG/PNG/WebP",
            buildPhase = 6
        ),

        // === GPS, SIGNAL & NFC ===
        ToolDefinition(
            id = ToolId.GPS_SPEED,
            category = ToolCategory.GPS_NETWORK,
            icon = Icons.Rounded.Speed,
            accentHex = 0xFF06B6D4,
            nameMs = "GPS Speedometer",
            nameEn = "GPS Speed & Altitude",
            descMs = "Kelajuan km/j, altitud & koordinat",
            descEn = "Live speed, altitude & coordinates",
            buildPhase = 7
        ),
        ToolDefinition(
            id = ToolId.SIGNAL_METER,
            category = ToolCategory.GPS_NETWORK,
            icon = Icons.Rounded.NetworkCheck,
            accentHex = 0xFF14B8A6,
            nameMs = "Meter Isyarat Rangkaian",
            nameEn = "Wi-Fi & Cellular Meter",
            descMs = "Kekuatan dBm Wi-Fi & talian 4G/5G",
            descEn = "Real dBm Wi-Fi & 4G/5G signal",
            buildPhase = 7
        ),
        ToolDefinition(
            id = ToolId.NFC_TOOL,
            category = ToolCategory.GPS_NETWORK,
            icon = Icons.Rounded.Nfc,
            accentHex = 0xFF6366F1,
            nameMs = "Pembaca & Penulis NFC",
            nameEn = "NFC Reader & Writer",
            descMs = "Baca UID/rekod & tulis tag NFC",
            descEn = "Read tag UID/NDEF & write tags",
            buildPhase = 7
        ),

        // === UTILITY & DEVICE ===
        ToolDefinition(
            id = ToolId.UNIT_CONVERTER,
            category = ToolCategory.UTILITY,
            icon = Icons.Rounded.SwapHoriz,
            accentHex = 0xFF3B82F6,
            nameMs = "Penukar Unit Universal",
            nameEn = "Universal Converter",
            descMs = "12 kategori unit ukuran & data",
            descEn = "12 measurement & data categories",
            buildPhase = 8
        ),
        ToolDefinition(
            id = ToolId.DATE_CALC,
            category = ToolCategory.UTILITY,
            icon = Icons.Rounded.DateRange,
            accentHex = 0xFFEC4899,
            nameMs = "Kalkulator Tarikh & Masa",
            nameEn = "Date & Time Calculator",
            descMs = "Beza tarikh, umur & tempoh kerja",
            descEn = "Date diff, age & duration math",
            buildPhase = 8
        ),
        ToolDefinition(
            id = ToolId.TALLY_STOPWATCH,
            category = ToolCategory.UTILITY,
            icon = Icons.Rounded.Timer,
            accentHex = 0xFFF97316,
            nameMs = "Kaunter & Jam Randik",
            nameEn = "Tally & Stopwatch",
            descMs = "Kira guna butang Volume & pusingan",
            descEn = "Volume-key counter & lap timer",
            buildPhase = 8
        ),
        ToolDefinition(
            id = ToolId.DEVICE_TESTER,
            category = ToolCategory.UTILITY,
            icon = Icons.Rounded.BuildCircle,
            accentHex = 0xFF22C55E,
            nameMs = "Penguji Perkakasan",
            nameEn = "Device Hardware Tester",
            descMs = "Uji piksel, sentuhan, bunyi & sensor",
            descEn = "Test display, touch, audio & sensors",
            buildPhase = 8
        )
    )

    fun findById(id: ToolId): ToolDefinition =
        allTools.first { it.id == id }
}
