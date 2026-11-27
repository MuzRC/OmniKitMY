package com.muz.omnikit.tools.sensors

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import java.util.Locale
import kotlin.math.log10

@Composable
fun LightMeterScreen(
    lang: Language,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val hasLightSensor = remember(context) {
        SensorFlow.hasSensor(context, Sensor.TYPE_LIGHT)
    }

    var isPaused by remember { mutableStateOf(false) }
    var useFootCandles by remember { mutableStateOf(false) }
    var calibrationFactor by remember { mutableFloatStateOf(1.0f) }

    var currentLux by remember { mutableFloatStateOf(0f) }
    var minLux by remember { mutableStateOf<Float?>(null) }
    var maxLux by remember { mutableStateOf<Float?>(null) }
    var sumLux by remember { mutableFloatStateOf(0f) }
    var sampleCount by remember { mutableIntStateOf(0) }
    val history = remember { mutableStateListOf<Float>() }

    LaunchedEffect(hasLightSensor, isPaused, calibrationFactor) {
        if (!hasLightSensor || isPaused) return@LaunchedEffect
        var lastRecordMs = 0L
        SensorFlow.observeSensor(context, Sensor.TYPE_LIGHT, SensorManager.SENSOR_DELAY_UI)
            .collect { values ->
                val adjusted = (values[0] * calibrationFactor).coerceAtLeast(0f)
                currentLux = adjusted

                val now = System.currentTimeMillis()
                if (now - lastRecordMs >= 120L) {
                    lastRecordMs = now
                    minLux = minLux?.let { minOf(it, adjusted) } ?: adjusted
                    maxLux = maxLux?.let { maxOf(it, adjusted) } ?: adjusted
                    sumLux += adjusted
                    sampleCount += 1
                    history.add(adjusted)
                    if (history.size > 50) history.removeAt(0)
                }
            }
    }

    fun resetStats() {
        minLux = null
        maxLux = null
        sumLux = 0f
        sampleCount = 0
        history.clear()
    }

    val avgLux = if (sampleCount > 0) sumLux / sampleCount else 0f
    val unitFactor = if (useFootCandles) 1f / 10.7639f else 1f
    val unitLabel = if (useFootCandles) "fc" else "lx"
    val (envLabel, envColor) = describeLighting(currentLux, lang)

    ToolScaffold(
        title = lang.tr("Meter Cahaya", "Light Meter"),
        subtitle = lang.tr("Penderia cahaya ambien sebenar (Lux)", "Real hardware ambient light (Lux)"),
        onBack = onBack,
        actions = {
            if (hasLightSensor) {
                IconButton(onClick = { resetStats() }) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = lang.tr("Set Semula", "Reset"))
                }
            }
        }
    ) {
        if (!hasLightSensor) {
            HardwareMissingCard(
                title = lang.tr("Penderia Cahaya Tiada", "Ambient Light Sensor Missing"),
                description = lang.tr(
                    "Peranti ini tidak mempunyai penderia cahaya ambien perkakasan (TYPE_LIGHT).",
                    "This device does not include a hardware ambient light sensor."
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
            // Logarithmic Arc Meter + Center Value
            LightArcMeter(
                lux = currentLux,
                displayValue = formatLight(currentLux * unitFactor),
                unit = unitLabel,
                accentColor = envColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
            )

            // Lighting Environment Badge
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = envColor.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, envColor.copy(alpha = 0.6f))
            ) {
                Text(
                    text = envLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = envColor,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                )
            }

            // Min / Avg / Max Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LightStatCard(
                    title = "Min",
                    value = minLux?.let { "${formatLight(it * unitFactor)} $unitLabel" } ?: "--",
                    modifier = Modifier.weight(1f)
                )
                LightStatCard(
                    title = lang.tr("Purata", "Avg"),
                    value = if (sampleCount > 0) "${formatLight(avgLux * unitFactor)} $unitLabel" else "--",
                    modifier = Modifier.weight(1f)
                )
                LightStatCard(
                    title = lang.tr("Maks", "Peak"),
                    value = maxLux?.let { "${formatLight(it * unitFactor)} $unitLabel" } ?: "--",
                    modifier = Modifier.weight(1f)
                )
            }

            // Live Log-Scale History Chart
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
                            text = lang.tr("Graf Kecerahan (Skala Log)", "Illuminance History (Log Scale)"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isPaused) lang.tr("DIBEKU", "HELD") else lang.tr("LANGSUNG", "LIVE"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isPaused) Color(0xFFF59E0B) else Color(0xFF10B981)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LuxHistoryCanvas(
                        values = history,
                        lineColor = envColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(78.dp)
                    )
                }
            }

            // Controls: Freeze/Resume & Lux/Foot-candle Unit Switch
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
                    Text(if (isPaused) lang.tr("Sambung", "Resume") else lang.tr("Beku Bacaan", "Hold"))
                }
                OutlinedButton(
                    onClick = { useFootCandles = !useFootCandles },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (useFootCandles) lang.tr("Tukar ke Lux", "Switch to Lux") else lang.tr("Tukar ke fc", "Switch to fc"))
                }
            }

            // Calibration Multiplier
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
                            text = lang.tr("Faktor Kalibrasi Sensor", "Sensor Calibration Multiplier"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = String.format(Locale.US, "%.2fx", calibrationFactor),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = calibrationFactor,
                        onValueChange = { calibrationFactor = it },
                        valueRange = 0.5f..2.0f
                    )
                }
            }

            // Recommended Lighting Reference Guide
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = lang.tr("Panduan Tahap Pencahayaan", "Lighting Level Reference"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    ReferenceRow("0 – 20 lx", lang.tr("Gelap / Malam / Lampu Tidur", "Dark / Night / Dim Mood"))
                    ReferenceRow("50 – 150 lx", lang.tr("Ruang Tamu / Koridor", "Living Room / Hallway"))
                    ReferenceRow("300 – 500 lx", lang.tr("Pejabat / Meja Belajar", "Office / Study Desk"))
                    ReferenceRow("750 – 1,500 lx", lang.tr("Bengkel / Kerja Teliti", "Workshop / Precision Work"))
                    ReferenceRow("10,000+ lx", lang.tr("Cahaya Matahari Terang", "Bright Direct Sunlight"))
                }
            }
        }
    }
}

@Composable
private fun LightArcMeter(
    lux: Float,
    displayValue: String,
    unit: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onBackground
    val subColor = MaterialTheme.colorScheme.onSurfaceVariant

    androidx.compose.foundation.layout.Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val d = minOf(size.width * 0.68f, size.height * 1.15f)
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f + 12.dp.toPx())
            val arcSize = Size(d, d)
            val stroke = 14.dp.toPx()

            drawArc(
                color = trackColor,
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            // Logarithmic progress maps 1 lx -> 0%, 100,000 lx -> 100% smoothly
            val progress = (log10(lux.coerceAtLeast(1f).toDouble()) / 5.0).toFloat().coerceIn(0f, 1f)
            drawArc(
                color = accentColor,
                startAngle = 140f,
                sweepAngle = 260f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 18.dp)
        ) {
            Text(
                text = displayValue,
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = textColor
            )
            Text(
                text = unit.uppercase(Locale.US),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = subColor
            )
        }
    }
}

@Composable
private fun LuxHistoryCanvas(
    values: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawLine(grid, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), 1.dp.toPx())
        drawLine(grid, Offset(0f, h - 1f), Offset(w, h - 1f), 1.dp.toPx())

        if (values.size < 2) return@Canvas
        val step = w / (values.size - 1)
        val path = Path()
        values.forEachIndexed { idx, v ->
            val norm = (log10(v.coerceAtLeast(1f).toDouble()) / 5.0).toFloat().coerceIn(0f, 1f)
            val x = idx * step
            val y = h - norm * (h - 8.dp.toPx()) - 4.dp.toPx()
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun LightStatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ReferenceRow(range: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = range,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatLight(value: Float): String = when {
    value >= 1000f -> String.format(Locale.US, "%,.0f", value)
    value >= 100f -> String.format(Locale.US, "%.0f", value)
    else -> String.format(Locale.US, "%.1f", value)
}

private fun describeLighting(lux: Float, lang: Language): Pair<String, Color> = when {
    lux < 15f -> lang.tr("Sangat Gelap", "Very Dark") to Color(0xFF64748B)
    lux < 100f -> lang.tr("Malap (Bilik / Rehat)", "Dim (Bedroom / Lounge)") to Color(0xFF60A5FA)
    lux < 350f -> lang.tr("Sederhana (Dalam Rumah)", "Moderate (Indoor)") to Color(0xFF38BDF8)
    lux < 800f -> lang.tr("Cerah (Pejabat / Belajar)", "Bright (Office / Study)") to Color(0xFF10B981)
    lux < 5000f -> lang.tr("Sangat Terang (Teduh Luar)", "Very Bright (Outdoor Shade)") to Color(0xFFFBBF24)
    else -> lang.tr("Cahaya Matahari Terik", "Direct Sunlight") to Color(0xFFF97316)
}
