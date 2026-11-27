package com.muz.omnikit.tools.measure

import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

private enum class ProtractorMode { TOUCH, GRAVITY }

@Composable
fun ProtractorScreen(
    lang: Language,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val hasGravity = remember(context) {
        SensorFlow.hasSensor(context, Sensor.TYPE_GRAVITY) ||
            SensorFlow.hasSensor(context, Sensor.TYPE_ACCELEROMETER)
    }

    var mode by remember { mutableStateOf(ProtractorMode.TOUCH) }

    // Touch Mode: two independently draggable rays on 0°..180° protractor
    var rayADeg by remember { mutableFloatStateOf(30f) }
    var rayBDeg by remember { mutableFloatStateOf(120f) }
    var activeRay by remember { mutableIntStateOf(0) } // 1 = A, 2 = B
    var snapHalfDeg by remember { mutableStateOf(true) }

    // Gravity Mode: live physical inclination & locked reference zero angle
    var liveGravityAngle by remember { mutableFloatStateOf(0f) }
    var lockedRefAngle by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(mode, hasGravity) {
        if (mode != ProtractorMode.GRAVITY || !hasGravity) return@LaunchedEffect
        val sensorType = if (SensorFlow.hasSensor(context, Sensor.TYPE_GRAVITY)) {
            Sensor.TYPE_GRAVITY
        } else {
            Sensor.TYPE_ACCELEROMETER
        }
        var fx = 0f
        var fy = 9.81f
        var initialized = false
        val alpha = 0.20f

        SensorFlow.observeSensor(context, sensorType, SensorManager.SENSOR_DELAY_UI)
            .collect { vals ->
                val x = vals[0]
                val y = vals[1]
                if (!initialized) {
                    fx = x; fy = y
                    initialized = true
                } else {
                    fx += alpha * (x - fx)
                    fy += alpha * (y - fy)
                }
                // Angle relative to vertical plumb axis in degrees (-180..+180)
                val angle = Math.toDegrees(atan2(fx.toDouble(), fy.toDouble())).toFloat()
                liveGravityAngle = angle
            }
    }

    val touchSpan = abs(rayBDeg - rayADeg)
    val touchSupplementary = (180f - touchSpan).coerceAtLeast(0f)

    val gravityDisplayAngle = remember(liveGravityAngle, lockedRefAngle) {
        val ref = lockedRefAngle ?: 0f
        var diff = liveGravityAngle - ref
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        abs(diff)
    }

    ToolScaffold(
        title = lang.tr("Protraktor Sudut", "Protractor"),
        subtitle = if (mode == ProtractorMode.TOUCH) {
            lang.tr("Mod Sentuhan 2-Garis (0° – 180°)", "2-Ray Touch Mode (0° – 180°)")
        } else {
            lang.tr("Mod Sudut Graviti Fizikal", "Physical Gravity Angle Mode")
        },
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                if (mode == ProtractorMode.TOUCH) {
                    rayADeg = 0f
                    rayBDeg = 90f
                } else {
                    lockedRefAngle = null
                }
            }) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = lang.tr("Set Semula", "Reset"))
            }
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
            // Mode Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                FilterChip(
                    selected = mode == ProtractorMode.TOUCH,
                    onClick = { mode = ProtractorMode.TOUCH },
                    leadingIcon = { Icon(Icons.Rounded.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(lang.tr("Sentuhan 2-Garis", "Touch 2-Ray")) }
                )
                FilterChip(
                    selected = mode == ProtractorMode.GRAVITY,
                    onClick = { mode = ProtractorMode.GRAVITY },
                    leadingIcon = { Icon(Icons.Rounded.ScreenRotation, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(lang.tr("Sudut Graviti", "Gravity Angle")) }
                )
            }

            if (mode == ProtractorMode.GRAVITY && !hasGravity) {
                HardwareMissingCard(
                    title = lang.tr("Penderia Graviti Tiada", "Gravity Sensor Unavailable"),
                    description = lang.tr(
                        "Peranti ini tidak mempunyai penderia graviti. Sila gunakan Mod Sentuhan.",
                        "This device has no gravity sensor. Please use Touch Mode."
                    )
                )
                return@ToolScaffold
            }

            // Main Numeric Angle Display
            val primaryAngle = if (mode == ProtractorMode.TOUCH) touchSpan else gravityDisplayAngle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format(Locale.US, "%.1f°", primaryAngle),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (mode == ProtractorMode.TOUCH) {
                        lang.tr(
                            "Sudut Penggenap: %.1f°  •  %.3f rad",
                            "Supplementary: %.1f°  •  %.3f rad"
                        ).format(Locale.US, touchSupplementary, primaryAngle * PI / 180.0)
                    } else {
                        val slopePct = kotlin.math.tan(primaryAngle.coerceAtMost(89.5f) * PI / 180.0) * 100.0
                        if (lockedRefAngle != null) {
                            lang.tr(
                                "Diukur dari rujukan terkunci • Cerun %.1f%%",
                                "Relative to locked reference • Slope %.1f%%"
                            ).format(Locale.US, slopePct)
                        } else {
                            lang.tr(
                                "Sudut dari menegak • Cerun %.1f%%",
                                "Angle from plumb vertical • Slope %.1f%%"
                            ).format(Locale.US, slopePct)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Interactive Semicircle Protractor Canvas
            val surfaceColor = MaterialTheme.colorScheme.surface
            val outlineColor = MaterialTheme.colorScheme.outline
            val primaryColor = MaterialTheme.colorScheme.primary
            val secondaryColor = Color(0xFF10B981)
            val tertiaryColor = MaterialTheme.colorScheme.tertiary
            val onSurfaceColor = MaterialTheme.colorScheme.onSurface
            val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.48f)
                        .padding(12.dp)
                        .pointerInput(mode, snapHalfDeg) {
                            if (mode != ProtractorMode.TOUCH) return@pointerInput
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val pivot = Offset(size.width / 2f, size.height * 0.86f)
                                    val touchDeg = pointToProtractorDeg(offset, pivot)
                                    activeRay = if (abs(touchDeg - rayADeg) <= abs(touchDeg - rayBDeg)) 1 else 2
                                },
                                onDragEnd = { activeRay = 0 },
                                onDragCancel = { activeRay = 0 },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val pivot = Offset(size.width / 2f, size.height * 0.86f)
                                    var deg = pointToProtractorDeg(change.position, pivot)
                                    if (snapHalfDeg) deg = round(deg * 2f) / 2f
                                    if (activeRay == 1) rayADeg = deg else if (activeRay == 2) rayBDeg = deg
                                }
                            )
                        }
                ) {
                    val pivot = Offset(size.width / 2f, size.height * 0.86f)
                    val radius = minOf(size.width * 0.45f, size.height * 0.78f)
                    val topLeft = Offset(pivot.x - radius, pivot.y - radius)
                    val arcSize = Size(radius * 2f, radius * 2f)

                    // Base baseline
                    drawLine(
                        color = outlineColor,
                        start = Offset(pivot.x - radius - 8.dp.toPx(), pivot.y),
                        end = Offset(pivot.x + radius + 8.dp.toPx(), pivot.y),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Outer semicircle border
                    drawArc(
                        color = outlineColor,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = 2.2.dp.toPx())
                    )
                    // Inner guide arc
                    drawArc(
                        color = outlineColor.copy(alpha = 0.4f),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(pivot.x - radius * 0.65f, pivot.y - radius * 0.65f),
                        size = Size(radius * 1.3f, radius * 1.3f),
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // Shaded active sector
                    val startRay = if (mode == ProtractorMode.TOUCH) minOf(rayADeg, rayBDeg) else 0f
                    val endRay = if (mode == ProtractorMode.TOUCH) maxOf(rayADeg, rayBDeg) else gravityDisplayAngle.coerceIn(0f, 180f)
                    drawArc(
                        color = primaryColor.copy(alpha = 0.18f),
                        startAngle = 360f - endRay,
                        sweepAngle = (endRay - startRay).coerceAtLeast(0.5f),
                        useCenter = true,
                        topLeft = Offset(pivot.x - radius * 0.86f, pivot.y - radius * 0.86f),
                        size = Size(radius * 1.72f, radius * 1.72f)
                    )

                    // Degree tick marks every 1° / 5° / 10° / 30°
                    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = onSurfaceColor.toArgb()
                        textAlign = Paint.Align.CENTER
                        textSize = (radius * 0.092f).coerceIn(18f, 34f)
                        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    }

                    for (d in 0..180 step 2) {
                        val isMajor = d % 30 == 0
                        val isTen = d % 10 == 0
                        val len = when {
                            isMajor -> 15.dp.toPx()
                            isTen -> 10.dp.toPx()
                            else -> 5.dp.toPx()
                        }
                        val rad = d * PI / 180.0
                        val cosA = cos(rad).toFloat()
                        val sinA = sin(rad).toFloat()
                        val outer = Offset(pivot.x + radius * cosA, pivot.y - radius * sinA)
                        val inner = Offset(pivot.x + (radius - len) * cosA, pivot.y - (radius - len) * sinA)
                        drawLine(
                            color = if (isMajor) primaryColor else if (isTen) onSurfaceColor.copy(alpha = 0.7f) else mutedColor.copy(alpha = 0.4f),
                            start = inner,
                            end = outer,
                            strokeWidth = if (isMajor) 2.2.dp.toPx() else if (isTen) 1.4.dp.toPx() else 0.9.dp.toPx()
                        )
                        if (isMajor) {
                            val lr = radius - 26.dp.toPx()
                            val lx = pivot.x + lr * cosA
                            val ly = pivot.y - lr * sinA + labelPaint.textSize * 0.35f
                            drawIntoCanvas { canvas ->
                                canvas.nativeCanvas.drawText("$d°", lx, ly, labelPaint)
                            }
                        }
                    }

                    // Draw Rays
                    fun drawRay(deg: Float, color: Color, drawKnob: Boolean, isSelected: Boolean) {
                        val rad = deg * PI / 180.0
                        val tip = Offset(
                            x = pivot.x + radius * 0.95f * cos(rad).toFloat(),
                            y = pivot.y - radius * 0.95f * sin(rad).toFloat()
                        )
                        drawLine(
                            color = color,
                            start = pivot,
                            end = tip,
                            strokeWidth = if (isSelected) 3.8.dp.toPx() else 2.8.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        if (drawKnob) {
                            val knobPos = Offset(
                                x = pivot.x + radius * 0.78f * cos(rad).toFloat(),
                                y = pivot.y - radius * 0.78f * sin(rad).toFloat()
                            )
                            drawCircle(color = color.copy(alpha = 0.28f), radius = 16.dp.toPx(), center = knobPos)
                            drawCircle(color = color, radius = 9.dp.toPx(), center = knobPos)
                            drawCircle(color = Color.White, radius = 3.5.dp.toPx(), center = knobPos)
                        }
                    }

                    if (mode == ProtractorMode.TOUCH) {
                        drawRay(rayADeg, primaryColor, drawKnob = true, isSelected = activeRay == 1)
                        drawRay(rayBDeg, secondaryColor, drawKnob = true, isSelected = activeRay == 2)
                    } else {
                        drawRay(0f, mutedColor, drawKnob = false, isSelected = false)
                        drawRay(gravityDisplayAngle.coerceIn(0f, 180f), tertiaryColor, drawKnob = false, isSelected = true)
                    }

                    // Center pivot pin
                    drawCircle(color = onSurfaceColor, radius = 6.dp.toPx(), center = pivot)
                    drawCircle(color = primaryColor, radius = 3.dp.toPx(), center = pivot)
                }
            }

            // Mode-specific controls & telemetry
            if (mode == ProtractorMode.TOUCH) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SmallAngleBox(lang.tr("Garis A (Biru)", "Ray A (Blue)"), rayADeg, primaryColor, Modifier.weight(1f))
                    SmallAngleBox(lang.tr("Garis B (Hijau)", "Ray B (Green)"), rayBDeg, secondaryColor, Modifier.weight(1f))
                }

                // Quick Preset Angles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    listOf(30f, 45f, 60f, 90f, 120f).forEach { preset ->
                        OutlinedButton(
                            onClick = {
                                rayADeg = 0f
                                rayBDeg = preset
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("${preset.toInt()}°", fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                FilterChip(
                    selected = snapHalfDeg,
                    onClick = { snapHalfDeg = !snapHalfDeg },
                    label = {
                        Text(
                            if (snapHalfDeg) {
                                lang.tr("Ketepatan: Kunci setiap 0.5°", "Precision: Snap 0.5°")
                            } else {
                                lang.tr("Ketepatan: Bebas (0.1°)", "Precision: Free (0.1°)")
                            }
                        )
                    }
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            lockedRefAngle = if (lockedRefAngle == null) liveGravityAngle else null
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = if (lockedRefAngle == null) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (lockedRefAngle == null) {
                                lang.tr("Kunci Rujukan 0°", "Lock 0° Reference")
                            } else {
                                lang.tr("Buka Kunci", "Unlock Reference")
                            }
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = lang.tr(
                            "Tip: Sandarkan sisi telefon pada permukaan pertama dan tekan 'Kunci Rujukan 0°', kemudian gerakkan telefon ke permukaan kedua untuk dapatkan sudut tepat antara kedua-duanya.",
                            "Tip: Rest the phone edge on Surface 1 and tap 'Lock 0° Reference', then move it to Surface 2 to measure the exact angle between them."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}

private fun pointToProtractorDeg(point: Offset, pivot: Offset): Float {
    val dx = (point.x - pivot.x).toDouble()
    val dy = (pivot.y - point.y).toDouble() // Upward is positive
    if (dy < 0.0) {
        return if (dx >= 0.0) 0f else 180f
    }
    return Math.toDegrees(atan2(dy, dx)).toFloat().coerceIn(0f, 180f)
}

@Composable
private fun SmallAngleBox(
    label: String,
    deg: Float,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = String.format(Locale.US, "%.1f°", deg),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}
