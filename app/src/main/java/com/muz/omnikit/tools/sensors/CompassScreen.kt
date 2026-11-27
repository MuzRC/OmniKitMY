package com.muz.omnikit.tools.sensors

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface as ComposeSurface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muz.omnikit.core.model.Language
import com.muz.omnikit.ui.components.HardwareMissingCard
import com.muz.omnikit.ui.components.ToolScaffold
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun CompassScreen(
    lang: Language,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sensorManager = remember(context) {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }

    val hasHardware = remember(sensorManager) {
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null ||
            (sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null &&
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null)
    }

    var smoothHeading by remember { mutableFloatStateOf(0f) }
    var calibrationOffset by remember { mutableFloatStateOf(0f) }
    var pitchDeg by remember { mutableFloatStateOf(0f) }
    var rollDeg by remember { mutableFloatStateOf(0f) }
    var magneticMicroTesla by remember { mutableFloatStateOf(0f) }
    var accuracyLevel by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) }
    var sensorActive by remember { mutableStateOf(false) }

    DisposableEffect(sensorManager, hasHardware) {
        if (sensorManager == null || !hasHardware) {
            return@DisposableEffect onDispose { }
        }

        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val rotMatrix = FloatArray(9)
        val remappedMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        val accelValues = FloatArray(3)
        val magValues = FloatArray(3)
        var hasAccel = false
        var hasMag = false

        // Circular low-pass filter using sin/cos to avoid 359° <-> 0° discontinuity
        var filtSin = 0f
        var filtCos = 1f
        var initialized = false
        val alpha = 0.16f

        fun applyOrientation(matrix: FloatArray) {
            val displayRot = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.display?.rotation ?: Surface.ROTATION_0
                } else {
                    @Suppress("DEPRECATION")
                    (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                        ?.defaultDisplay?.rotation ?: Surface.ROTATION_0
                }
            }.getOrDefault(Surface.ROTATION_0)

            val (axisX, axisY) = when (displayRot) {
                Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
            }

            SensorManager.remapCoordinateSystem(matrix, axisX, axisY, remappedMatrix)
            SensorManager.getOrientation(remappedMatrix, orientationAngles)

            val rawRad = orientationAngles[0].toDouble()
            val s = sin(rawRad).toFloat()
            val c = cos(rawRad).toFloat()
            if (!initialized) {
                filtSin = s
                filtCos = c
                initialized = true
            } else {
                filtSin += alpha * (s - filtSin)
                filtCos += alpha * (c - filtCos)
            }

            val deg = ((Math.toDegrees(atan2(filtSin.toDouble(), filtCos.toDouble())) + 360.0) % 360.0).toFloat()
            smoothHeading = deg
            pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
            rollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
            sensorActive = true
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                        applyOrientation(rotMatrix)
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, accelValues, 0, 3)
                        hasAccel = true
                        if (rotationSensor == null && hasMag &&
                            SensorManager.getRotationMatrix(rotMatrix, null, accelValues, magValues)
                        ) {
                            applyOrientation(rotMatrix)
                        }
                    }

                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        val x = event.values[0]
                        val y = event.values[1]
                        val z = event.values[2]
                        magneticMicroTesla = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                        System.arraycopy(event.values, 0, magValues, 0, 3)
                        hasMag = true
                        if (rotationSensor == null && hasAccel &&
                            SensorManager.getRotationMatrix(rotMatrix, null, accelValues, magValues)
                        ) {
                            applyOrientation(rotMatrix)
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD || sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                    accuracyLevel = accuracy
                }
            }
        }

        if (rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            accelSensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        }
        magSensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    val netHeading = ((smoothHeading - calibrationOffset) % 360f + 360f) % 360f
    val displayDeg = netHeading.roundToInt() % 360
    val cardinal = remember(displayDeg, lang) { getCardinalLabel(displayDeg, lang) }
    val isFlatEnough = kotlin.math.abs(pitchDeg) < 20f && kotlin.math.abs(rollDeg) < 20f

    ToolScaffold(
        title = lang.tr("Kompas Digital", "Digital Compass"),
        subtitle = lang.tr("Arah mata angin magnetik sebenar", "True magnetic heading & orientation"),
        onBack = onBack,
        actions = {
            if (hasHardware) {
                IconButton(onClick = { calibrationOffset = smoothHeading }) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = lang.tr("Tentukur Sifar", "Zero Set")
                    )
                }
            }
        }
    ) {
        if (!hasHardware) {
            HardwareMissingCard(
                title = lang.tr("Penderia Magnetometer Tiada", "Magnetometer Not Found"),
                description = lang.tr(
                    "Peranti ini tidak mempunyai cip penderia kompas magnetik perkakasan.",
                    "This device does not include a hardware magnetic compass sensor."
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Digital Heading Readout
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format(Locale.US, "%03d°", displayDeg),
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = cardinal,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (calibrationOffset != 0f) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { calibrationOffset = 0f },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(lang.tr("Set Semula ke Utara Asal", "Reset to True North"))
                    }
                }
            }

            // Precision Interactive Compass Dial
            CompassDialCanvas(
                headingDeg = netHeading,
                pitchDeg = pitchDeg,
                rollDeg = rollDeg,
                lang = lang,
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .widthIn(max = 310.dp)
                    .aspectRatio(1f)
            )

            // Calibration warning when magnetic accuracy is low or interference is strong
            val lowAccuracy = accuracyLevel <= SensorManager.SENSOR_STATUS_ACCURACY_LOW ||
                magneticMicroTesla > 75f || (magneticMicroTesla in 0.1f..20f)
            if (lowAccuracy && sensorActive) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = lang.tr(
                                "Gangguan magnet dikesan atau ketepatan rendah. Gerakkan telefon membentuk angka 8 beberapa kali untuk kalibrasi.",
                                "Magnetic interference or low accuracy detected. Wave phone in a Figure-8 motion to calibrate."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Sensor Telemetry Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryCard(
                    label = lang.tr("Medan Magnet", "Magnetic Field"),
                    value = if (magneticMicroTesla > 0f) String.format(Locale.US, "%.1f µT", magneticMicroTesla) else "--",
                    modifier = Modifier.weight(1f)
                )
                TelemetryCard(
                    label = lang.tr("Ketepatan", "Accuracy"),
                    value = when (accuracyLevel) {
                        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> lang.tr("Tinggi", "High")
                        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> lang.tr("Sederhana", "Medium")
                        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> lang.tr("Rendah", "Low")
                        else -> lang.tr("Perlu Kalibrasi", "Uncalibrated")
                    },
                    valueColor = when (accuracyLevel) {
                        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> Color(0xFF10B981)
                        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> MaterialTheme.colorScheme.primary
                        else -> Color(0xFFF59E0B)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Tilt & Status Bar
            ComposeSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        !sensorActive -> Color(0xFF94A3B8)
                                        isFlatEnough -> Color(0xFF10B981)
                                        else -> Color(0xFFF59E0B)
                                    }
                                )
                        )
                        Text(
                            text = when {
                                !sensorActive -> lang.tr("Menunggu bacaan sensor...", "Waiting for sensor...")
                                isFlatEnough -> lang.tr("Posisi telefon rata & tepat", "Phone level & accurate")
                                else -> lang.tr("Ratakan telefon untuk bacaan tepat", "Hold phone flat for accuracy")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = String.format(Locale.US, "P:%.0f° R:%.0f°", pitchDeg, rollDeg),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun CompassDialCanvas(
    headingDeg: Float,
    pitchDeg: Float,
    rollDeg: Float,
    lang: Language,
    modifier: Modifier = Modifier
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceVarColor = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // Outer casing
        drawCircle(color = surfaceColor, radius = radius, center = center)
        drawCircle(
            color = outlineColor,
            radius = radius - 3.dp.toPx(),
            center = center,
            style = Stroke(width = 3.dp.toPx())
        )
        drawCircle(
            color = surfaceVarColor.copy(alpha = 0.45f),
            radius = radius * 0.72f,
            center = center
        )

        // Fixed Top Red Lubber Line (Phone heading marker)
        val markerPath = Path().apply {
            moveTo(center.x, center.y - radius + 2.dp.toPx())
            lineTo(center.x - 7.dp.toPx(), center.y - radius + 14.dp.toPx())
            lineTo(center.x + 7.dp.toPx(), center.y - radius + 14.dp.toPx())
            close()
        }
        drawPath(markerPath, color = Color(0xFFEF4444))

        // Rotating Compass Rose (-headingDeg keeps North pointed to real magnetic North)
        rotate(degrees = -headingDeg, pivot = center) {
            val tickOuter = radius * 0.90f
            for (deg in 0 until 360 step 5) {
                val isMajor = deg % 90 == 0
                val isMedium = deg % 30 == 0
                val tickLen = when {
                    isMajor -> 14.dp.toPx()
                    isMedium -> 9.dp.toPx()
                    else -> 4.5.dp.toPx()
                }
                val angleRad = (deg - 90) * PI / 180.0
                val cosA = cos(angleRad).toFloat()
                val sinA = sin(angleRad).toFloat()
                val start = Offset(center.x + (tickOuter - tickLen) * cosA, center.y + (tickOuter - tickLen) * sinA)
                val end = Offset(center.x + tickOuter * cosA, center.y + tickOuter * sinA)

                drawLine(
                    color = when {
                        deg == 0 -> Color(0xFFEF4444)
                        isMajor -> primaryColor
                        isMedium -> onSurfaceColor.copy(alpha = 0.75f)
                        else -> mutedColor.copy(alpha = 0.45f)
                    },
                    start = start,
                    end = end,
                    strokeWidth = if (isMajor) 2.6.dp.toPx() else if (isMedium) 1.6.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Cardinal letters N/E/S/W (or U/T/S/B)
            val labels = if (lang == Language.MS) {
                listOf("U" to 0, "T" to 90, "S" to 180, "B" to 270)
            } else {
                listOf("N" to 0, "E" to 90, "S" to 180, "W" to 270)
            }
            val textRadius = radius * 0.67f
            drawIntoCanvas { canvas ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textSize = radius * 0.145f
                }
                labels.forEach { (text, deg) ->
                    val rad = (deg - 90) * PI / 180.0
                    val x = center.x + textRadius * cos(rad).toFloat()
                    val y = center.y + textRadius * sin(rad).toFloat() + paint.textSize * 0.35f
                    paint.color = if (deg == 0) Color(0xFFEF4444).toArgb() else onSurfaceColor.toArgb()
                    canvas.nativeCanvas.drawText(text, x, y, paint)
                }
            }

            // Classic Dual-Tone Compass Needle
            val needleLen = radius * 0.46f
            val needleHalfW = radius * 0.08f
            val northNeedle = Path().apply {
                moveTo(center.x, center.y - needleLen)
                lineTo(center.x + needleHalfW, center.y)
                lineTo(center.x - needleHalfW, center.y)
                close()
            }
            val southNeedle = Path().apply {
                moveTo(center.x, center.y + needleLen)
                lineTo(center.x + needleHalfW, center.y)
                lineTo(center.x - needleHalfW, center.y)
                close()
            }
            drawPath(northNeedle, color = Color(0xFFEF4444))
            drawPath(southNeedle, color = mutedColor.copy(alpha = 0.65f))
        }

        // Center Level Crosshair & Live Tilt Bubble
        val bubbleLimit = radius * 0.20f
        val bx = (rollDeg / 35f).coerceIn(-1f, 1f) * bubbleLimit
        val by = (pitchDeg / 35f).coerceIn(-1f, 1f) * bubbleLimit
        drawCircle(
            color = outlineColor.copy(alpha = 0.7f),
            radius = radius * 0.13f,
            center = center,
            style = Stroke(width = 1.2.dp.toPx())
        )
        drawCircle(
            color = primaryColor.copy(alpha = 0.85f),
            radius = 5.dp.toPx(),
            center = Offset(center.x + bx, center.y + by)
        )
        drawCircle(
            color = Color(0xFFFBBF24),
            radius = 4.dp.toPx(),
            center = center
        )
    }
}

@Composable
private fun TelemetryCard(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = valueColor
            )
        }
    }
}

private fun getCardinalLabel(deg: Int, lang: Language): String {
    val dirsMs = listOf(
        "Utara (U)", "Timur Laut (TL)", "Timur (T)", "Tenggara (TG)",
        "Selatan (S)", "Barat Daya (BD)", "Barat (B)", "Barat Laut (BL)"
    )
    val dirsEn = listOf(
        "North (N)", "North East (NE)", "East (E)", "South East (SE)",
        "South (S)", "South West (SW)", "West (W)", "North West (NW)"
    )
    val idx = ((deg + 22.5) / 45.0).toInt() % 8
    return if (lang == Language.MS) dirsMs[idx] else dirsEn[idx]
}
