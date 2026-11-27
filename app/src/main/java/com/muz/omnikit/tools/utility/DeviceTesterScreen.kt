package com.muz.omnikit.tools.utility

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.muz.omnikit.core.model.Language
import com.muz.omnikit.ui.components.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

private val PIXEL_TEST_COLORS = listOf(
    "Merah / Red" to Color(0xFFFF0000),
    "Hijau / Green" to Color(0xFF00FF00),
    "Biru / Blue" to Color(0xFF0000FF),
    "Putih / White" to Color(0xFFFFFFFF),
    "Hitam / Black" to Color(0xFF000000),
    "Kelabu / Gray 50%" to Color(0xFF808080)
)

private val POINTER_COLORS = listOf(
    Color(0xFF38BDF8), Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFFEF4444),
    Color(0xFFA78BFA), Color(0xFFF472B6), Color(0xFF2DD4BF), Color(0xFFFACC15)
)

@Composable
fun DeviceTesterScreen(
    lang: Language,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var openPixelTest by remember { mutableStateOf(false) }
    var openTouchTest by remember { mutableStateOf(false) }
    var maxFingersRecorded by remember { mutableIntStateOf(0) }
    var audioTestingChannel by remember { mutableStateOf<String?>(null) }

    val deviceSummary = remember(context) { collectDeviceDiagnostics(context, lang) }

    fun playTestChime(channel: String) {
        if (audioTestingChannel != null) return
        audioTestingChannel = channel
        scope.launch {
            withContext(Dispatchers.IO) {
                playStereoDiagnosticBeep(
                    left = channel == "L" || channel == "LR",
                    right = channel == "R" || channel == "LR"
                )
            }
            audioTestingChannel = null
        }
    }

    ToolScaffold(
        title = lang.tr("Penguji Perkakasan", "Device Hardware Tester"),
        subtitle = lang.tr("Uji skrin, sentuhan, speaker, getaran & sensor", "Test display, multi-touch, audio, haptic & sensors"),
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Display & Multi-Touch Tests
            DiagSectionCard(
                icon = Icons.Rounded.PhoneAndroid,
                title = lang.tr("Ujian Skrin & Sentuhan", "Display & Multi-Touch Tests")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { openPixelTest = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(lang.tr("Uji Piksel Skrin", "Dead Pixel Test"), maxLines = 1)
                    }
                    Button(
                        onClick = { openTouchTest = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(lang.tr("Multi-Touch", "Multi-Touch"), maxLines = 1)
                    }
                }
                if (maxFingersRecorded > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = lang.tr(
                            "Rekod sentuhan serentak dikesan: $maxFingersRecorded jari",
                            "Max simultaneous fingers detected: $maxFingersRecorded"
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 2. Stereo Speaker & Vibration Motor Tests
            DiagSectionCard(
                icon = Icons.Rounded.VolumeUp,
                title = lang.tr("Ujian Speaker Stereo & Motor Getaran", "Stereo Audio & Vibration Motor")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { playTestChime("L") },
                        enabled = audioTestingChannel == null,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text(lang.tr("Kiri (L)", "Left (L)")) }
                    OutlinedButton(
                        onClick = { playTestChime("LR") },
                        enabled = audioTestingChannel == null,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Stereo") }
                    OutlinedButton(
                        onClick = { playTestChime("R") },
                        enabled = audioTestingChannel == null,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text(lang.tr("Kanan (R)", "Right (R)")) }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { runVibrationPattern(context, longArrayOf(0, 60)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Rounded.Vibration, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(lang.tr("Ringkas", "Short"))
                    }
                    OutlinedButton(
                        onClick = { runVibrationPattern(context, longArrayOf(0, 120, 80, 120)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text(lang.tr("Berganda", "Double")) }
                    OutlinedButton(
                        onClick = { runVibrationPattern(context, longArrayOf(0, 400)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text(lang.tr("Panjang", "Long")) }
                }
            }

            // 3. Battery & Thermal Health
            DiagSectionCard(
                icon = Icons.Rounded.BatteryChargingFull,
                title = lang.tr("Kesihatan Bateri & Suhu", "Battery & Thermal Health")
            ) {
                deviceSummary.batteryInfo.forEachIndexed { idx, (k, v) ->
                    DiagKeyValRow(k, v)
                    if (idx < deviceSummary.batteryInfo.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            // 4. Hardware, Memory & Display Specs
            DiagSectionCard(
                icon = Icons.Rounded.Memory,
                title = lang.tr("Spesifikasi Sistem & Storan", "System, Memory & Storage")
            ) {
                deviceSummary.systemInfo.forEachIndexed { idx, (k, v) ->
                    DiagKeyValRow(k, v)
                    if (idx < deviceSummary.systemInfo.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            // 5. Key Hardware Sensor Audit
            DiagSectionCard(
                icon = Icons.Rounded.Sensors,
                title = lang.tr(
                    "Audit Penderia Perkakasan (${deviceSummary.totalSensors} dikesan)",
                    "Hardware Sensor Audit (${deviceSummary.totalSensors} total)"
                )
            ) {
                deviceSummary.sensorAudit.forEachIndexed { idx, (name, present) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(name, style = MaterialTheme.typography.bodySmall)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (present) Icons.Rounded.CheckCircle else Icons.Rounded.Close,
                                contentDescription = null,
                                tint = if (present) Color(0xFF10B981) else Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (present) lang.tr("Ada", "OK") else lang.tr("Tiada", "None"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (present) Color(0xFF10B981) else Color(0xFFEF4444)
                            )
                        }
                    }
                    if (idx < deviceSummary.sensorAudit.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    if (openPixelTest) {
        DeadPixelTestDialog(lang = lang, onDismiss = { openPixelTest = false })
    }

    if (openTouchTest) {
        MultiTouchTestDialog(
            lang = lang,
            onMaxPointers = { count ->
                if (count > maxFingersRecorded) maxFingersRecorded = count
            },
            onDismiss = { openTouchTest = false }
        )
    }
}

@Composable
private fun DeadPixelTestDialog(lang: Language, onDismiss: () -> Unit) {
    var colorIdx by remember { mutableIntStateOf(0) }
    val (label, col) = PIXEL_TEST_COLORS[colorIdx]
    val darkText = col == Color.White || col == Color(0xFF00FF00)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(col)
                .clickable {
                    if (colorIdx < PIXEL_TEST_COLORS.lastIndex) {
                        colorIdx += 1
                    } else {
                        onDismiss()
                    }
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier.padding(bottom = 44.dp),
                shape = RoundedCornerShape(999.dp),
                color = if (darkText) Color.Black.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.22f)
            ) {
                Text(
                    text = "$label (${colorIdx + 1}/${PIXEL_TEST_COLORS.size}) • ${lang.tr("Ketik untuk warna seterusnya", "Tap for next color")}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                )
            }
        }
    }
}

@Composable
private fun MultiTouchTestDialog(
    lang: Language,
    onMaxPointers: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val activePointers = remember { mutableStateMapOf<Long, Offset>() }
    var peakCount by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A1222))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            activePointers.clear()
                            event.changes.forEach { ch ->
                                if (ch.pressed) {
                                    activePointers[ch.id.value] = ch.position
                                }
                            }
                            val live = activePointers.size
                            if (live > peakCount) {
                                peakCount = live
                                onMaxPointers(live)
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                activePointers.entries.forEachIndexed { idx, (_, pos) ->
                    val c = POINTER_COLORS[idx % POINTER_COLORS.size]
                    drawLine(c.copy(alpha = 0.35f), Offset(0f, pos.y), Offset(size.width, pos.y), 1.5.dp.toPx())
                    drawLine(c.copy(alpha = 0.35f), Offset(pos.x, 0f), Offset(pos.x, size.height), 1.5.dp.toPx())
                    drawCircle(c.copy(alpha = 0.25f), 46.dp.toPx(), pos)
                    drawCircle(c, 32.dp.toPx(), pos, style = Stroke(3.dp.toPx()))
                    drawCircle(Color.White, 6.dp.toPx(), pos)
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${activePointers.size}",
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF38BDF8)
                )
                Text(
                    text = lang.tr(
                        "Sentuhan Aktif  •  Maksimum: $peakCount jari",
                        "Active Touches  •  Max: $peakCount fingers"
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 44.dp),
                shape = RoundedCornerShape(999.dp)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(lang.tr("Tamat Ujian Sentuhan", "Close Touch Test"))
            }
        }
    }
}

@Composable
private fun DiagSectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun DiagKeyValRow(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

private data class DeviceDiagnostics(
    val batteryInfo: List<Pair<String, String>>,
    val systemInfo: List<Pair<String, String>>,
    val sensorAudit: List<Pair<String, Boolean>>,
    val totalSensors: Int
)

private fun collectDeviceDiagnostics(context: Context, lang: Language): DeviceDiagnostics {
    // Battery telemetry via sticky ACTION_BATTERY_CHANGED intent
    val batIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = batIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    val tempC = (batIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
    val voltageV = (batIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000.0
    val healthCode = batIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
    val healthLabel = when (healthCode) {
        BatteryManager.BATTERY_HEALTH_GOOD -> lang.tr("Baik", "Good")
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> lang.tr("Terlampau Panas", "Overheat")
        BatteryManager.BATTERY_HEALTH_DEAD -> lang.tr("Rosak", "Dead")
        BatteryManager.BATTERY_HEALTH_COLD -> lang.tr("Sejuk", "Cold")
        else -> lang.tr("Tidak Diketahui", "Unknown")
    }
    val statusCode = batIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val statusLabel = when (statusCode) {
        BatteryManager.BATTERY_STATUS_CHARGING -> lang.tr("Sedang Mengecas", "Charging")
        BatteryManager.BATTERY_STATUS_FULL -> lang.tr("Penuh", "Full")
        BatteryManager.BATTERY_STATUS_DISCHARGING -> lang.tr("Menyahcas", "Discharging")
        else -> lang.tr("Tidak Mengecas", "Not Charging")
    }

    val batteryList = listOf(
        lang.tr("Paras & Status", "Level & Status") to if (pct >= 0) "$pct% ($statusLabel)" else statusLabel,
        lang.tr("Kesihatan", "Health") to healthLabel,
        lang.tr("Suhu Bateri", "Temperature") to String.format(Locale.US, "%.1f °C", tempC),
        lang.tr("Voltan", "Voltage") to String.format(Locale.US, "%.3f V", voltageV)
    )

    // System, RAM, Storage & Display
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    am?.getMemoryInfo(memInfo)
    val totalRamGb = memInfo.totalMem / 1_073_741_824.0
    val availRamGb = memInfo.availMem / 1_073_741_824.0

    val stat = StatFs(Environment.getDataDirectory().path)
    val totalStorageGb = (stat.blockCountLong * stat.blockSizeLong) / 1_073_741_824.0
    val freeStorageGb = (stat.availableBlocksLong * stat.blockSizeLong) / 1_073_741_824.0
    val dm = context.resources.displayMetrics

    val sysList = listOf(
        lang.tr("Peranti", "Device") to "${Build.MANUFACTURER} ${Build.MODEL}",
        lang.tr("Versi Android", "Android Version") to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        lang.tr("Seni Bina CPU", "CPU ABIs") to Build.SUPPORTED_ABIS.take(2).joinToString(", "),
        lang.tr("RAM Tersedia / Jumlah", "RAM Free / Total") to String.format(Locale.US, "%.2f / %.2f GB", availRamGb, totalRamGb),
        lang.tr("Storan Kosong / Jumlah", "Storage Free / Total") to String.format(Locale.US, "%.1f / %.1f GB", freeStorageGb, totalStorageGb),
        lang.tr("Skrin & Kepadatan", "Display & Density") to "${dm.widthPixels}×${dm.heightPixels} px (${dm.densityDpi} dpi)"
    )

    // Key Sensors Audit
    val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    val allSensors = sm?.getSensorList(Sensor.TYPE_ALL) ?: emptyList()
    fun has(type: Int): Boolean = sm?.getDefaultSensor(type) != null

    val audit = listOf(
        lang.tr("Akselerometer", "Accelerometer") to has(Sensor.TYPE_ACCELEROMETER),
        lang.tr("Giroskop", "Gyroscope") to has(Sensor.TYPE_GYROSCOPE),
        lang.tr("Magnetometer (Kompas)", "Magnetometer (Compass)") to has(Sensor.TYPE_MAGNETIC_FIELD),
        lang.tr("Vektor Putaran", "Rotation Vector") to has(Sensor.TYPE_ROTATION_VECTOR),
        lang.tr("Penderia Graviti", "Gravity Sensor") to has(Sensor.TYPE_GRAVITY),
        lang.tr("Pecutan Linear (Getaran)", "Linear Acceleration") to has(Sensor.TYPE_LINEAR_ACCELERATION),
        lang.tr("Penderia Cahaya (Lux)", "Ambient Light (Lux)") to has(Sensor.TYPE_LIGHT),
        lang.tr("Penderia Jarak (Proximity)", "Proximity Sensor") to has(Sensor.TYPE_PROXIMITY),
        lang.tr("Barometer (Tekanan)", "Barometer (Pressure)") to has(Sensor.TYPE_PRESSURE),
        lang.tr("Pembilang Langkah", "Step Counter") to has(Sensor.TYPE_STEP_COUNTER)
    )

    return DeviceDiagnostics(batteryList, sysList, audit, allSensors.size)
}

private fun playStereoDiagnosticBeep(left: Boolean, right: Boolean) {
    val sampleRate = 44100
    val durationSec = 0.55
    val frames = (sampleRate * durationSec).toInt()
    val buf = ShortArray(frames * 2)
    val freq = if (left && !right) 523.25 else if (!left && right) 659.25 else 587.33
    val fade = (sampleRate * 0.03).toInt()

    for (i in 0 until frames) {
        val env = when {
            i < fade -> i.toDouble() / fade
            i > frames - fade -> (frames - i).toDouble() / fade
            else -> 1.0
        }
        val sample = (sin(2.0 * PI * freq * i / sampleRate) * env * 22000.0).toInt().toShort()
        buf[i * 2] = if (left) sample else 0
        buf[i * 2 + 1] = if (right) sample else 0
    }

    runCatching {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(buf.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(buf, 0, buf.size)
        track.play()
        Thread.sleep((durationSec * 1000).toLong() + 60L)
        track.stop()
        track.release()
    }
}

private fun runVibrationPattern(context: Context, timings: LongArray) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(timings, -1))
    }
}
