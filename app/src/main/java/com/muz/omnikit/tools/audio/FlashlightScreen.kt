package com.muz.omnikit.tools.audio

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.muz.omnikit.core.model.Language
import com.muz.omnikit.ui.components.ToolScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.roundToInt

private enum class FlashMode { TORCH, STROBE, MORSE, SCREEN }

private val MORSE_MAP: Map<Char, String> = mapOf(
    'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
    'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
    'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
    'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
    'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
    'Z' to "--..",
    '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
    '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----."
)

private data class ScreenLightPreset(val nameMs: String, val nameEn: String, val color: Color, val isEmergency: Boolean = false)

private val SCREEN_PRESETS = listOf(
    ScreenLightPreset("Putih Terang", "Pure White", Color(0xFFFFFFFF)),
    ScreenLightPreset("Putih Suam", "Warm White", Color(0xFFFFF1D0)),
    ScreenLightPreset("Merah Malam", "Night Red", Color(0xFFDC2626)),
    ScreenLightPreset("Ambar", "Amber", Color(0xFFF59E0B)),
    ScreenLightPreset("Sian", "Cyan", Color(0xFF38BDF8)),
    ScreenLightPreset("Amaran", "Emergency", Color(0xFFEF4444), isEmergency = true)
)

@Composable
fun FlashlightScreen(
    lang: Language,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val cameraManager = remember(context) {
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    }

    // Discover camera ID with rear flash & Android 13+ variable strength capability
    val flashInfo = remember(cameraManager) { discoverFlashCamera(cameraManager) }
    val hasRearFlash = flashInfo.cameraId != null

    var activeMode by remember { mutableStateOf(if (hasRearFlash) FlashMode.TORCH else FlashMode.SCREEN) }
    var torchOn by remember { mutableStateOf(false) }
    var strengthLevel by remember { mutableIntStateOf(flashInfo.maxStrength.coerceAtLeast(1)) }
    var torchError by remember { mutableStateOf<String?>(null) }

    // Strobe states
    var strobeActive by remember { mutableStateOf(false) }
    var strobeHz by remember { mutableFloatStateOf(5f) }

    // Morse / SOS states
    var morseText by remember { mutableStateOf("SOS") }
    var morseActive by remember { mutableStateOf(false) }
    var morseLoop by remember { mutableStateOf(true) }
    var morseUnitMs by remember { mutableFloatStateOf(160f) }
    var morseCurrentChar by remember { mutableStateOf("") }
    var morseFlashLit by remember { mutableStateOf(false) }

    // Screen Light states
    var selectedPresetIdx by remember { mutableIntStateOf(0) }
    var fullScreenLightOpen by remember { mutableStateOf(false) }

    fun setHardwareTorch(on: Boolean, level: Int = strengthLevel) {
        val camId = flashInfo.cameraId ?: return
        val cm = cameraManager ?: return
        try {
            if (on) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && flashInfo.maxStrength > 1) {
                    cm.turnOnTorchWithStrengthLevel(camId, level.coerceIn(1, flashInfo.maxStrength))
                } else {
                    cm.setTorchMode(camId, true)
                }
            } else {
                cm.setTorchMode(camId, false)
            }
            torchError = null
        } catch (e: Exception) {
            torchError = e.localizedMessage
        }
    }

    fun stopAllFlashActivity() {
        strobeActive = false
        morseActive = false
        torchOn = false
        morseFlashLit = false
        setHardwareTorch(false)
    }

    // Ensure hardware LED always switches off cleanly when leaving tool
    DisposableEffect(flashInfo.cameraId) {
        onDispose {
            setHardwareTorch(false)
        }
    }

    // Strobe Blink Loop
    LaunchedEffect(strobeActive, strobeHz, strengthLevel) {
        if (!strobeActive || !hasRearFlash) {
            if (activeMode == FlashMode.STROBE) setHardwareTorch(false)
            return@LaunchedEffect
        }
        try {
            while (isActive) {
                val halfPeriodMs = (500f / strobeHz.coerceIn(1f, 15f)).toLong().coerceAtLeast(30L)
                setHardwareTorch(true, strengthLevel)
                delay(halfPeriodMs)
                setHardwareTorch(false)
                delay(halfPeriodMs)
            }
        } finally {
            setHardwareTorch(false)
        }
    }

    // Morse Code / SOS Transmitter Loop
    LaunchedEffect(morseActive, morseText, morseLoop, morseUnitMs, strengthLevel) {
        if (!morseActive || !hasRearFlash) {
            morseFlashLit = false
            morseCurrentChar = ""
            if (activeMode == FlashMode.MORSE) setHardwareTorch(false)
            return@LaunchedEffect
        }
        val cleanText = morseText.uppercase(Locale.US).trim().ifBlank { "SOS" }
        val unit = morseUnitMs.toLong().coerceIn(80L, 350L)

        try {
            do {
                for (ch in cleanText) {
                    if (!isActive) break
                    if (ch == ' ') {
                        morseCurrentChar = "⎵"
                        morseFlashLit = false
                        setHardwareTorch(false)
                        delay(unit * 4)
                        continue
                    }
                    val code = MORSE_MAP[ch] ?: continue
                    morseCurrentChar = "$ch ($code)"
                    for (idx in code.indices) {
                        if (!isActive) break
                        val symbol = code[idx]
                        morseFlashLit = true
                        setHardwareTorch(true, strengthLevel)
                        delay(if (symbol == '-') unit * 3 else unit)
                        morseFlashLit = false
                        setHardwareTorch(false)
                        if (idx < code.lastIndex) delay(unit)
                    }
                    delay(unit * 3)
                }
                if (morseLoop && isActive) {
                    morseCurrentChar = "..."
                    delay(unit * 5)
                }
            } while (morseLoop && isActive)
        } finally {
            morseFlashLit = false
            morseCurrentChar = ""
            setHardwareTorch(false)
            morseActive = false
        }
    }

    ToolScaffold(
        title = lang.tr("Lampu Suluh Pro", "Pro Flashlight"),
        subtitle = lang.tr("Kecerahan, strob, SOS, Morse & lampu skrin", "Brightness, strobe, SOS, Morse & screen light"),
        onBack = {
            stopAllFlashActivity()
            onBack()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 4-Mode Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    FlashMode.TORCH to lang.tr("Suluh", "Torch"),
                    FlashMode.STROBE to lang.tr("Strob", "Strobe"),
                    FlashMode.MORSE to "SOS/Morse",
                    FlashMode.SCREEN to lang.tr("Skrin", "Screen")
                ).forEach { (m, lbl) ->
                    FilterChip(
                        selected = activeMode == m,
                        onClick = {
                            if (activeMode != m) {
                                stopAllFlashActivity()
                                activeMode = m
                            }
                        },
                        label = { Text(lbl, maxLines = 1) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (!torchError.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = torchError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (activeMode != FlashMode.SCREEN && !hasRearFlash) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = lang.tr(
                            "Peranti ini tidak mempunyai lampu flash LED belakang. Sila gunakan tab 'Skrin' untuk mod lampu skrin penuh.",
                            "This device has no rear LED flash. Please switch to the 'Screen' tab for full-screen light."
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                when (activeMode) {
                    FlashMode.TORCH -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        // Giant Tactile Power Button
                        val btnColor = if (torchOn) Color(0xFFFACC15) else MaterialTheme.colorScheme.surfaceVariant
                        val iconTint = if (torchOn) Color(0xFF1E1B00) else MaterialTheme.colorScheme.onSurfaceVariant

                        Box(
                            modifier = Modifier
                                .size(190.dp)
                                .clip(CircleShape)
                                .background(btnColor.copy(alpha = if (torchOn) 0.22f else 0.35f))
                                .padding(16.dp)
                                .clip(CircleShape)
                                .background(btnColor)
                                .clickable {
                                    val next = !torchOn
                                    torchOn = next
                                    setHardwareTorch(next, strengthLevel)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (torchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (torchOn) lang.tr("HIDUP", "ON") else lang.tr("MATI", "OFF"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = iconTint
                                )
                            }
                        }

                        // Variable Hardware Brightness Slider (Android 13+ when supported by hardware)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && flashInfo.maxStrength > 1) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = lang.tr("Tahap Kecerahan LED", "LED Brightness Level"),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "$strengthLevel / ${flashInfo.maxStrength}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Slider(
                                        value = strengthLevel.toFloat(),
                                        onValueChange = { v ->
                                            val lvl = v.roundToInt().coerceIn(1, flashInfo.maxStrength)
                                            strengthLevel = lvl
                                            if (torchOn) setHardwareTorch(true, lvl)
                                        },
                                        valueRange = 1f..flashInfo.maxStrength.toFloat(),
                                        steps = (flashInfo.maxStrength - 2).coerceAtLeast(0)
                                    )
                                } else {
                                    Text(
                                        text = lang.tr(
                                            "Perkakasan flash peranti ini menggunakan mod nyalaan piawai (Hidup/Mati).",
                                            "This device's LED flash hardware uses standard On/Off brightness."
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    FlashMode.STROBE -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Bolt,
                                    contentDescription = null,
                                    tint = if (strobeActive) Color(0xFFFACC15) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f Hz", strobeHz),
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = lang.tr("Kelipan setiap ${(1000f / strobeHz).roundToInt()} ms", "Flash every ${(1000f / strobeHz).roundToInt()} ms"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = strobeHz,
                                    onValueChange = { strobeHz = it },
                                    valueRange = 1f..15f
                                )
                            }
                        }

                        Button(
                            onClick = { strobeActive = !strobeActive },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = if (strobeActive) {
                                ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White)
                            } else {
                                ButtonDefaults.buttonColors()
                            }
                        ) {
                            Icon(if (strobeActive) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (strobeActive) lang.tr("Hentikan Strob", "Stop Strobe") else lang.tr("Aktifkan Lampu Strob", "Start Strobe"),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    FlashMode.MORSE -> {
                        val morsePreview = remember(morseText) {
                            morseText.uppercase(Locale.US).mapNotNull { ch ->
                                if (ch == ' ') "/" else MORSE_MAP[ch]
                            }.joinToString(" ")
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(
                                1.dp,
                                if (morseFlashLit) Color(0xFFFACC15) else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = morseText,
                                    onValueChange = { morseText = it.take(40) },
                                    label = { Text(lang.tr("Teks Kod Morse", "Morse Message")) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { morseText = "SOS" },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("SOS")
                                    }
                                    OutlinedButton(
                                        onClick = { morseText = "TOLONG" },
                                        shape = RoundedCornerShape(10.dp)
                                    ) { Text("TOLONG") }
                                    OutlinedButton(
                                        onClick = { morseText = "OK" },
                                        shape = RoundedCornerShape(10.dp)
                                    ) { Text("OK") }
                                }
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = morsePreview.ifBlank { "..." },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = if (morseFlashLit) Color(0xFFFACC15) else MaterialTheme.colorScheme.primary
                                        )
                                        if (morseActive && morseCurrentChar.isNotBlank()) {
                                            Text(
                                                text = lang.tr("Memancar: $morseCurrentChar", "Transmitting: $morseCurrentChar"),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(lang.tr("Ulang Tanpa Henti", "Loop Continuously"), style = MaterialTheme.typography.bodyMedium)
                                    Switch(checked = morseLoop, onCheckedChange = { morseLoop = it })
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(lang.tr("Tempoh Titik (Dot)", "Dot Speed"), style = MaterialTheme.typography.labelMedium)
                                    Text("${morseUnitMs.roundToInt()} ms", style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                                }
                                Slider(value = morseUnitMs, onValueChange = { morseUnitMs = it }, valueRange = 90f..320f)
                            }
                        }

                        Button(
                            onClick = { morseActive = !morseActive },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = if (morseActive) {
                                ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White)
                            } else {
                                ButtonDefaults.buttonColors()
                            }
                        ) {
                            Icon(if (morseActive) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (morseActive) lang.tr("Hentikan Pancaran", "Stop Transmission") else lang.tr("Pancar Kod Morse", "Transmit Morse Code"),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    FlashMode.SCREEN -> {
                        val preset = SCREEN_PRESETS[selectedPresetIdx]
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = lang.tr("Pilih Warna Lampu Skrin", "Choose Screen Light Color"),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SCREEN_PRESETS.forEachIndexed { idx, item ->
                                        val isSel = idx == selectedPresetIdx
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(item.color)
                                                .clickable { selectedPresetIdx = idx },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSel) {
                                                Icon(
                                                    Icons.Rounded.LightMode,
                                                    contentDescription = null,
                                                    tint = if (item.color == Color.White || item.color == Color(0xFFFFF1D0)) Color.Black else Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Text(
                                    text = lang.tr(
                                        "Pilihan: ${preset.nameMs} — Skrin akan dilaras ke kecerahan maksimum secara automatik.",
                                        "Selected: ${preset.nameEn} — Screen boosts to maximum brightness automatically."
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { fullScreenLightOpen = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.Fullscreen, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(lang.tr("Buka Lampu Skrin Penuh", "Launch Full-Screen Light"), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (fullScreenLightOpen) {
        FullScreenLightDialog(
            preset = SCREEN_PRESETS[selectedPresetIdx],
            lang = lang,
            onDismiss = { fullScreenLightOpen = false }
        )
    }
}

@Composable
private fun FullScreenLightDialog(
    preset: ScreenLightPreset,
    lang: Language,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var emergencyToggle by remember { mutableStateOf(false) }

    // Temporarily boost screen brightness to 100% and restore cleanly on exit
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val window = activity?.window
        val prevBrightness = window?.attributes?.screenBrightness ?: -1f
        if (window != null) {
            val lp = window.attributes
            lp.screenBrightness = 1.0f
            window.attributes = lp
        }
        onDispose {
            if (window != null) {
                val lp = window.attributes
                lp.screenBrightness = prevBrightness
                window.attributes = lp
            }
        }
    }

    LaunchedEffect(preset.isEmergency) {
        if (!preset.isEmergency) return@LaunchedEffect
        while (isActive) {
            emergencyToggle = !emergencyToggle
            delay(260)
        }
    }

    val bg = if (preset.isEmergency) {
        if (emergencyToggle) Color(0xFFDC2626) else Color(0xFF2563EB)
    } else {
        preset.color
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bg)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier.padding(bottom = 44.dp),
                shape = RoundedCornerShape(999.dp),
                color = Color.Black.copy(alpha = 0.55f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text(
                        text = lang.tr("Ketik mana-mana untuk tutup", "Tap anywhere to close"),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

private data class FlashCameraInfo(val cameraId: String?, val maxStrength: Int)

private fun discoverFlashCamera(cameraManager: CameraManager?): FlashCameraInfo {
    if (cameraManager == null) return FlashCameraInfo(null, 1)
    return runCatching {
        var fallbackId: String? = null
        var fallbackStrength = 1

        for (id in cameraManager.cameraIdList) {
            val chars = cameraManager.getCameraCharacteristics(id)
            val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            if (!hasFlash) continue

            val maxStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
            } else {
                1
            }

            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                return@runCatching FlashCameraInfo(id, maxStr)
            }
            if (fallbackId == null) {
                fallbackId = id
                fallbackStrength = maxStr
            }
        }
        FlashCameraInfo(fallbackId, fallbackStrength)
    }.getOrDefault(FlashCameraInfo(null, 1))
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
