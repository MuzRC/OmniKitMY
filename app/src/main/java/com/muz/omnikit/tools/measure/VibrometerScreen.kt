package com.muz.omnikit.tools.measure

import android.hardware.Sensor
import android.hardware.SensorManager
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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import java.util.Locale
import kotlin.math.sqrt

private data class VibSample(val x: Float, val y: Float, val z: Float, val mag: Float)

@Composable
fun VibrometerScreen(
    lang: Language,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val hasLinearAccel = remember(context) {
        SensorFlow.hasSensor(context, Sensor.TYPE_LINEAR_ACCELERATION)
    }
    val hasRawAccel = remember(context) {
        SensorFlow.hasSensor(context, Sensor.TYPE_ACCELEROMETER)
    }
    val hasHardware = hasLinearAccel || hasRawAccel

    var isPaused by remember { mutableStateOf(false) }
    var showThreeAxis by remember { mutableStateOf(false) }
    var graphMaxScale by remember { mutableFloatStateOf(5f) } // 0f = Auto

    var currentMag by remember { mutableFloatStateOf(0f) }
    var rmsMag by remember { mutableFloatStateOf(0f) }
    var peakMag by remember { mutableFloatStateOf(0f) }
    var axisX by remember { mutableFloatStateOf(0f) }
    var axisY by remember { mutableFloatStateOf(0f) }
    var axisZ by remember { mutableFloatStateOf(0f) }
    var sampleRateHz by remember { mutableIntStateOf(0) }
    val waveHistory = remember { mutableStateListOf<VibSample>() }

    LaunchedEffect(hasHardware, isPaused) {
        if (!hasHardware || isPaused) return@LaunchedEffect

        val sensorType = if (hasLinearAccel) Sensor.TYPE_LINEAR_ACCELERATION else Sensor.TYPE_ACCELEROMETER
        // High-pass gravity isolation state (used only when falling back to raw accelerometer)
        val grav = floatArrayOf(0f, 0f, 9.81f)
        var gravInit = false
        val hpAlpha = 0.85f

        val rmsWindow = ArrayDeque<Float>(25)
        var windowSumSq = 0f
        var frameCount = 0
        var rateWindowStartMs = System.currentTimeMillis()
        var lastUiUpdateMs = 0L

        SensorFlow.observeSensor(context, sensorType, SensorManager.SENSOR_DELAY_GAME)
            .collect { vals ->
                val lx: Float
                val ly: Float
                val lz: Float
                if (hasLinearAccel) {
                    lx = vals[0]
                    ly = vals[1]
                    lz = vals[2]
                } else {
                    if (!gravInit) {
                        grav[0] = vals[0]; grav[1] = vals[1]; grav[2] = vals[2]
                        gravInit = true
                    } else {
                        grav[0] = hpAlpha * grav[0] + (1f - hpAlpha) * vals[0]
                        grav[1] = hpAlpha * grav[1] + (1f - hpAlpha) * vals[1]
                        grav[2] = hpAlpha * grav[2] + (1f - hpAlpha) * vals[2]
                    }
                    lx = vals[0] - grav[0]
                    ly = vals[1] - grav[1]
                    lz = vals[2] - grav[2]
                }

                val mag = sqrt((lx * lx + ly * ly + lz * lz).toDouble()).toFloat()

                // Maintain rolling RMS window
                val sq = mag * mag
                rmsWindow.addLast(sq)
                windowSumSq += sq
                if (rmsWindow.size > 25) {
                    windowSumSq = (windowSumSq - rmsWindow.removeFirst()).coerceAtLeast(0f)
                }
                val liveRms = sqrt((windowSumSq / rmsWindow.size.coerceAtLeast(1)).toDouble()).toFloat()

                frameCount += 1
                val now = System.currentTimeMillis()
                if (now - rateWindowStartMs >= 1000L) {
                    sampleRateHz = frameCount
                    frameCount = 0
                    rateWindowStartMs = now
                }

                if (mag > peakMag) peakMag = mag

                if (now - lastUiUpdateMs >= 40L) {
                    lastUiUpdateMs = now
                    currentMag = mag
                    rmsMag = liveRms
                    axisX = lx
                    axisY = ly
                    axisZ = lz
                    waveHistory.add(VibSample(lx, ly, lz, mag))
                    if (waveHistory.size > 90) waveHistory.removeAt(0)
                }
            }
    }

    fun resetSession() {
        peakMag = 0f
        currentMag = 0f
        rmsMag = 0f
        waveHistory.clear()
    }

    val condition = classifyVibration(rmsMag, lang)
    val mmiLevel = classifyMercalli(peakMag, lang)

    ToolScaffold(
        title = lang.tr("Pengukur Getaran", "Vibrometer"),
        subtitle = lang.tr("Graf gegaran enjin, mesin & seismik", "Engine, machinery & seismic monitor"),
        onBack = onBack,
        actions = {
            if (hasHardware) {
                IconButton(onClick = { resetSession() }) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = lang.tr("Set Semula", "Reset"))
                }
            }
        }
    ) {
        if (!hasHardware) {
            HardwareMissingCard(
                title = lang.tr("Penderia Akselerometer Tiada", "Accelerometer Not Found"),
                description = lang.tr(
                    "Peranti ini tidak mempunyai cip akselerometer untuk mengukur getaran.",
                    "This device does not include an accelerometer sensor."
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
            // Primary RMS & Condition Readout
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format(Locale.US, "%.2f", rmsMag),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = condition.color
                )
                Text(
                    text = "m/s² (RMS)  •  ${String.format(Locale.US, "%.3f", rmsMag / 9.80665f)} g",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = condition.color.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, condition.color.copy(alpha = 0.65f))
                ) {
                    Text(
                        text = condition.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = condition.color,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            // Live Seismograph Waveform Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showThreeAxis) {
                                lang.tr("Gelombang 3-Paksi (X / Y / Z)", "3-Axis Waveform (X / Y / Z)")
                            } else {
                                lang.tr("Graf Magnitud Getaran", "Vibration Magnitude Seismograph")
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isPaused) lang.tr("DIBEKU", "PAUSED") else "$sampleRateHz Hz",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (isPaused) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    SeismographCanvas(
                        samples = waveHistory,
                        threeAxis = showThreeAxis,
                        fixedScale = graphMaxScale,
                        accentColor = condition.color,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(145.dp)
                    )
                    if (showThreeAxis) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                        ) {
                            Text("● X", style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF4444))
                            Text("● Y", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981))
                            Text("● Z", style = MaterialTheme.typography.labelSmall, color = Color(0xFF38BDF8))
                        }
                    }
                }
            }

            // Scale & Graph Mode Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
            ) {
                listOf(
                    0f to lang.tr("Auto", "Auto"),
                    2f to "2 m/s²",
                    5f to "5 m/s²",
                    15f to "15 m/s²"
                ).forEach { (sc, lbl) ->
                    FilterChip(
                        selected = graphMaxScale == sc,
                        onClick = { graphMaxScale = sc },
                        label = { Text(lbl, maxLines = 1) }
                    )
                }
            }

            // Telemetry Summary Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VibStatCard(lang.tr("Semasa", "Instant"), String.format(Locale.US, "%.2f", currentMag), Modifier.weight(1f))
                VibStatCard(lang.tr("Puncak", "Peak"), String.format(Locale.US, "%.2f", peakMag), Modifier.weight(1f), highlight = true)
                VibStatCard("MMI", mmiLevel.first, Modifier.weight(1f))
            }

            // Live 3-Axis Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VibStatCard("X (m/s²)", String.format(Locale.US, "%+.2f", axisX), Modifier.weight(1f))
                VibStatCard("Y (m/s²)", String.format(Locale.US, "%+.2f", axisY), Modifier.weight(1f))
                VibStatCard("Z (m/s²)", String.format(Locale.US, "%+.2f", axisZ), Modifier.weight(1f))
            }

            // Pause / 3-Axis Toggle Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { isPaused = !isPaused },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isPaused) lang.tr("Sambung", "Resume") else lang.tr("Beku Graf", "Pause"))
                }
                OutlinedButton(
                    onClick = { showThreeAxis = !showThreeAxis },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (showThreeAxis) lang.tr("Mod Magnitud", "Magnitude") else lang.tr("Mod 3-Paksi", "3-Axis"))
                }
            }

            // Peak Mercalli Scale Note
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = lang.tr("Skala Puncak Mercalli: ${mmiLevel.first}", "Peak Mercalli Scale: ${mmiLevel.first}"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = mmiLevel.second,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SeismographCanvas(
    samples: List<VibSample>,
    threeAxis: Boolean,
    fixedScale: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h / 2f

        // Background reference grid
        for (i in 0..4) {
            val y = (h / 4f) * i
            drawLine(gridColor, Offset(0f, y), Offset(w, y), 1.dp.toPx())
        }

        if (samples.size < 2) return@Canvas
        val step = w / (samples.size - 1)

        val maxAbs = if (fixedScale > 0f) {
            fixedScale
        } else {
            val peak = samples.maxOfOrNull { maxOf(it.mag, kotlin.math.abs(it.x), kotlin.math.abs(it.y), kotlin.math.abs(it.z)) } ?: 1f
            peak.coerceAtLeast(1.0f) * 1.2f
        }

        if (threeAxis) {
            fun drawAxis(color: Color, selector: (VibSample) -> Float) {
                val path = Path()
                samples.forEachIndexed { idx, s ->
                    val x = idx * step
                    val y = (midY - (selector(s) / maxAbs) * (h * 0.45f)).coerceIn(2f, h - 2f)
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
            drawAxis(Color(0xFFEF4444)) { it.x }
            drawAxis(Color(0xFF10B981)) { it.y }
            drawAxis(Color(0xFF38BDF8)) { it.z }
        } else {
            val path = Path()
            samples.forEachIndexed { idx, s ->
                val x = idx * step
                val y = (h - (s.mag / maxAbs) * (h - 8.dp.toPx()) - 4.dp.toPx()).coerceIn(2f, h - 2f)
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = accentColor, style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun VibStatCard(
    label: String,
    value: String,
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
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private data class VibCondition(val label: String, val color: Color)

private fun classifyVibration(rms: Float, lang: Language): VibCondition = when {
    rms < 0.15f -> VibCondition(lang.tr("Tenang / Stabil", "Still / Smooth"), Color(0xFF10B981))
    rms < 0.60f -> VibCondition(lang.tr("Getaran Halus", "Slight Vibration"), Color(0xFF38BDF8))
    rms < 1.80f -> VibCondition(lang.tr("Getaran Sederhana", "Moderate Vibration"), Color(0xFFFBBF24))
    rms < 4.50f -> VibCondition(lang.tr("Getaran Kuat", "Strong Vibration"), Color(0xFFF97316))
    else -> VibCondition(lang.tr("Getaran Sangat Kuat", "Severe Vibration"), Color(0xFFEF4444))
}

private fun classifyMercalli(peak: Float, lang: Language): Pair<String, String> = when {
    peak < 0.05f -> "I" to lang.tr("Tidak dirasai manusia, hanya dikesan instrumen.", "Not felt by people; instrument-only.")
    peak < 0.25f -> "II–III" to lang.tr("Lemah — terasa ringan seperti kenderaan berat lalu.", "Weak — light tremor like a passing truck.")
    peak < 0.90f -> "IV" to lang.tr("Ringan — tingkap, pinggan atau kipas mula bergetar.", "Light — dishes, windows, or machinery noticeably vibrate.")
    peak < 2.20f -> "V" to lang.tr("Sederhana — terasa jelas, objek ringan bergerak.", "Moderate — clearly felt; small unsecured objects shift.")
    peak < 4.80f -> "VI" to lang.tr("Kuat — gegaran enjin/mesin berat atau hentakan kasar.", "Strong — heavy machinery shake or rough impact.")
    else -> "VII+" to lang.tr("Sangat Kuat — gegaran ekstrem, periksa punca mekanikal.", "Very Strong — extreme vibration; inspect mechanical source.")
}
