package com.muz.omnikit.tools.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muz.omnikit.core.model.Language
import com.muz.omnikit.core.sensors.SensorFlow
import com.muz.omnikit.ui.components.HardwareMissingCard
import com.muz.omnikit.ui.components.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun MetalDetectorScreen(
    lang: Language,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val hasMagnetometer = remember(context) {
        SensorFlow.hasSensor(context, Sensor.TYPE_MAGNETIC_FIELD)
    }

    var axisX by remember { mutableFloatStateOf(0f) }
    var axisY by remember { mutableFloatStateOf(0f) }
    var axisZ by remember { mutableFloatStateOf(0f) }
    var totalFlux by remember { mutableFloatStateOf(0f) }
    var baselineFlux by remember { mutableFloatStateOf(0f) }
    var baselineInitialized by remember { mutableStateOf(false) }
    var peakFlux by remember { mutableFloatStateOf(0f) }
    var alertThreshold by remember { mutableFloatStateOf(15f) }
    var soundEnabled by remember { mutableStateOf(true) }
    val history = remember { mutableStateListOf<Float>() }

    // Atomic holders so the audio beep coroutine reads live flux smoothly without timer restarts
    val liveDeltaRef = remember { AtomicReference(0f) }
    val liveThresholdRef = remember { AtomicReference(15f) }
    val liveSoundRef = remember { AtomicBoolean(true) }
    val testBeepTrigger = remember { AtomicBoolean(false) }

    liveThresholdRef.set(alertThreshold)
    liveSoundRef.set(soundEnabled)

    // Real Hardware Magnetometer Flow (auto-unregisters when leaving screen)
    LaunchedEffect(hasMagnetometer) {
        if (!hasMagnetometer) return@LaunchedEffect
        var lastUiUpdateMs = 0L
        var filtMag = 0f
        var initialized = false

        SensorFlow.observeSensor(context, Sensor.TYPE_MAGNETIC_FIELD, SensorManager.SENSOR_DELAY_UI)
            .collect { values ->
                val x = values[0]
                val y = values[1]
                val z = values[2]
                val mag = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

                if (!initialized) {
                    filtMag = mag
                    initialized = true
                } else {
                    filtMag += 0.25f * (mag - filtMag)
                }

                if (!baselineInitialized) {
                    baselineFlux = filtMag
                    baselineInitialized = true
                }

                val delta = abs(filtMag - baselineFlux)
                liveDeltaRef.set(delta)

                val now = System.currentTimeMillis()
                if (now - lastUiUpdateMs >= 65L) {
                    lastUiUpdateMs = now
                    axisX = x
                    axisY = y
                    axisZ = z
                    totalFlux = filtMag
                    if (filtMag > peakFlux) peakFlux = filtMag
                    history.add(filtMag)
                    if (history.size > 60) history.removeAt(0)
                }
            }
    }

    // Continuous Geiger-style PCM Beep Engine on IO Dispatcher (never leaks AudioTrack)
    LaunchedEffect(hasMagnetometer) {
        if (!hasMagnetometer) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val sampleRate = 22050
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            }.getOrNull() ?: return@withContext

            val beepSamples = (sampleRate * 0.045f).toInt()
            val pcmBuf = ShortArray(beepSamples)

            fun playTone(freqHz: Double) {
                val fadeSamples = (sampleRate * 0.006f).toInt().coerceAtLeast(1)
                for (i in 0 until beepSamples) {
                    val env = when {
                        i < fadeSamples -> i.toDouble() / fadeSamples
                        i > beepSamples - fadeSamples -> (beepSamples - i).toDouble() / fadeSamples
                        else -> 1.0
                    }
                    val sample = sin(2.0 * PI * freqHz * i / sampleRate) * env * 14000.0
                    pcmBuf[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                runCatching {
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
                    track.write(pcmBuf, 0, pcmBuf.size)
                }
            }

            try {
                while (isActive) {
                    if (testBeepTrigger.compareAndSet(true, false)) {
                        playTone(1050.0)
                        delay(120)
                        continue
                    }

                    val delta = liveDeltaRef.get()
                    val threshold = liveThresholdRef.get()
                    if (liveSoundRef.get() && delta >= threshold) {
                        val excess = (delta - threshold).coerceIn(0f, 150f)
                        val freq = 680.0 + (excess / 150.0) * 1150.0
                        playTone(freq)

                        if (excess > 60f) {
                            pulseLightHaptic(context)
                        }

                        // Faster cadence as metal gets closer (520ms down to 85ms)
                        val waitMs = (520f - (excess / 150f) * 435f).toLong().coerceIn(85L, 520L)
                        delay(waitMs)
                    } else {
                        delay(70)
                    }
                }
            } finally {
                runCatching {
                    track.stop()
                    track.release()
                }
            }
        }
    }

    val deltaFlux = abs(totalFlux - baselineFlux)
    val isDetected = baselineInitialized && deltaFlux >= alertThreshold
    val statusColor = when {
        deltaFlux >= alertThreshold + 60f -> Color(0xFFEF4444)
        deltaFlux >= alertThreshold -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    ToolScaffold(
        title = lang.tr("Pengesan Logam", "Metal Detector"),
        subtitle = lang.tr("Penderia medan magnet sebenar (µT)", "Real hardware magnetometer (µT)"),
        onBack = onBack,
        actions = {
            if (hasMagnetometer) {
                IconButton(onClick = { soundEnabled = !soundEnabled }) {
                    Icon(
                        imageVector = if (soundEnabled) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                        contentDescription = lang.tr("Bunyi Bip", "Beep Sound")
                    )
                }
                IconButton(onClick = {
                    baselineFlux = totalFlux
                    peakFlux = totalFlux
                }) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = lang.tr("Tentukur Sifar", "Zero Baseline")
                    )
                }
            }
        }
    ) {
        if (!hasMagnetometer) {
            HardwareMissingCard(
                title = lang.tr("Penderia Magnetometer Tiada", "No Magnetometer Found"),
                description = lang.tr(
                    "Telefon ini tidak mempunyai cip magnetometer perkakasan untuk mengesan logam feromagnetik atau magnet.",
                    "This phone lacks a hardware magnetometer chip required to measure magnetic flux."
                )
            )
            return@ToolScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Main Microtesla Readout
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format(Locale.US, "%.1f", totalFlux),
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = statusColor
                )
                Text(
                    text = lang.tr("Mikrotesla (µT)", "Microtesla (µT)"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isDetected) {
                        lang.tr(
                            "LOGAM / MAGNET DIKESAN (+%.1f µT)",
                            "METAL / MAGNET DETECTED (+%.1f µT)"
                        ).format(Locale.US, deltaFlux)
                    } else {
                        lang.tr(
                            "Perubahan dari Asas: %.1f µT (Asas %.1f µT)",
                            "Delta from Baseline: %.1f µT (Base %.1f µT)"
                        ).format(Locale.US, deltaFlux, baselineFlux)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }

            // Analog Semi-Circle Gauge
            MetalGaugeCanvas(
                deltaFlux = deltaFlux,
                threshold = alertThreshold,
                accentColor = statusColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
            )

            // 3-Axis Raw Vector Readout + Peak
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AxisCard("X", axisX, Modifier.weight(1f))
                AxisCard("Y", axisY, Modifier.weight(1f))
                AxisCard("Z", axisZ, Modifier.weight(1f))
                AxisCard(lang.tr("Puncak", "Peak"), peakFlux, Modifier.weight(1f), highlight = true)
            }

            // Live Magnetic Waveform History
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = lang.tr("Graf Medan Magnet Masa Nyata", "Live Magnetic Flux Graph"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FluxHistoryCanvas(
                        values = history,
                        baseline = baselineFlux,
                        threshold = alertThreshold,
                        lineColor = statusColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(82.dp)
                    )
                }
            }

            // Sensitivity Threshold Slider
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = lang.tr("Ambang Amaran Bip", "Beep Alert Threshold"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "+${alertThreshold.toInt()} µT",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = alertThreshold,
                        onValueChange = { alertThreshold = it },
                        valueRange = 5f..80f
                    )
                    Text(
                        text = lang.tr(
                            "Medan magnet semula jadi bumi ialah ~35–55 µT. Rapatkan belakang telefon ke objek besi/magnet.",
                            "Earth's natural magnetic field is ~35–55 µT. Move the back of your phone near ferrous metal or magnets."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { testBeepTrigger.set(true) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(lang.tr("Uji Bunyi", "Test Beep"))
                }
                OutlinedButton(
                    onClick = {
                        baselineFlux = totalFlux
                        peakFlux = totalFlux
                        history.clear()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(lang.tr("Set Asas", "Zero Base"))
                }
            }
        }
    }
}

@Composable
private fun MetalGaugeCanvas(
    deltaFlux: Float,
    threshold: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val needlePivotColor = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = modifier) {
        val diameter = minOf(size.width * 0.85f, size.height * 1.8f)
        val radius = diameter / 2f
        val center = Offset(size.width / 2f, size.height - 14.dp.toPx())
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(diameter, diameter)
        val strokeW = 16.dp.toPx()

        // Background track
        drawArc(
            color = trackColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        // Colored zones: Green (safe), Amber (near), Red (strong metal)
        val maxScale = (threshold + 120f).coerceAtLeast(100f)
        val safeSweep = (threshold / maxScale * 180f).coerceIn(15f, 90f)
        drawArc(
            color = Color(0xFF10B981).copy(alpha = 0.75f),
            startAngle = 180f,
            sweepAngle = safeSweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeW)
        )
        drawArc(
            color = Color(0xFFF59E0B).copy(alpha = 0.8f),
            startAngle = 180f + safeSweep,
            sweepAngle = (180f - safeSweep) * 0.45f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeW)
        )
        drawArc(
            color = Color(0xFFEF4444).copy(alpha = 0.85f),
            startAngle = 180f + safeSweep + (180f - safeSweep) * 0.45f,
            sweepAngle = (180f - safeSweep) * 0.55f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeW)
        )

        // Dynamic Needle
        val progress = (deltaFlux / maxScale).coerceIn(0f, 1f)
        val angleRad = (180.0 + progress * 180.0) * PI / 180.0
        val tip = Offset(
            x = center.x + (radius - strokeW * 0.6f) * cos(angleRad).toFloat(),
            y = center.y + (radius - strokeW * 0.6f) * sin(angleRad).toFloat()
        )
        drawLine(
            color = accentColor,
            start = center,
            end = tip,
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(color = needlePivotColor, radius = 7.dp.toPx(), center = center)
        drawCircle(color = accentColor, radius = 3.5.dp.toPx(), center = center)
    }
}

@Composable
private fun FluxHistoryCanvas(
    values: List<Float>,
    baseline: Float,
    threshold: Float,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val minV = (baseline - 20f).coerceAtLeast(0f)
        val maxV = maxOf(baseline + threshold + 60f, (values.maxOrNull() ?: 100f) + 10f)
        val span = (maxV - minV).coerceAtLeast(20f)

        // Alert threshold line
        val alertY = h - ((baseline + threshold - minV) / span * h).coerceIn(0f, h)
        drawLine(
            color = Color(0xFFF59E0B).copy(alpha = 0.55f),
            start = Offset(0f, alertY),
            end = Offset(w, alertY),
            strokeWidth = 1.2.dp.toPx()
        )
        drawLine(gridColor, Offset(0f, h - 1f), Offset(w, h - 1f), 1.dp.toPx())

        if (values.size < 2) return@Canvas
        val step = w / (values.size - 1)
        val path = Path()
        values.forEachIndexed { idx, v ->
            val x = idx * step
            val y = h - ((v - minV) / span * h).coerceIn(2f, h - 2f)
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun AxisCard(
    label: String,
    value: Float,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = String.format(Locale.US, "%.0f", value),
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun pulseLightHaptic(context: Context) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
