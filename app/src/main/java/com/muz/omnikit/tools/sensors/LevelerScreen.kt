package com.muz.omnikit.tools.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muz.omnikit.core.model.Language
import com.muz.omnikit.ui.components.HardwareMissingCard
import com.muz.omnikit.ui.components.ToolScaffold
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

private enum class LevelMode { FLAT, PORTRAIT, LANDSCAPE }
private enum class ModeLock { AUTO, FLAT, PORTRAIT, LANDSCAPE }

@Composable
fun LevelerScreen(
    lang: Language,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sensorManager = remember(context) {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    val hasHardware = remember(sensorManager) {
        sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY) != null ||
            sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    }

    // Filtered gravity vector (m/s^2) - completely immune to Euler gimbal lock!
    var gx by remember { mutableFloatStateOf(0f) }
    var gy by remember { mutableFloatStateOf(0f) }
    var gz by remember { mutableFloatStateOf(9.81f) }
    var sensorActive by remember { mutableStateOf(false) }

    var modeLock by remember { mutableStateOf(ModeLock.AUTO) }
    var autoMode by remember { mutableStateOf(LevelMode.FLAT) }

    // Separate calibration offsets per mode so zeroing NEVER breaks mode detection
    var calFlatPrimary by remember { mutableFloatStateOf(0f) }
    var calFlatSecondary by remember { mutableFloatStateOf(0f) }
    var calPortPrimary by remember { mutableFloatStateOf(0f) }
    var calPortSecondary by remember { mutableFloatStateOf(0f) }
    var calLandPrimary by remember { mutableFloatStateOf(0f) }
    var calLandSecondary by remember { mutableFloatStateOf(0f) }

    var hapticArmed by remember { mutableStateOf(true) }

    DisposableEffect(sensorManager, hasHardware) {
        if (sensorManager == null || !hasHardware) {
            return@DisposableEffect onDispose { }
        }
        val gravSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val activeSensor = gravSensor ?: accelSensor
        val alpha = if (gravSensor != null) 0.22f else 0.14f

        var fx = 0f
        var fy = 0f
        var fz = 9.81f
        var initialized = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val rx = event.values[0]
                val ry = event.values[1]
                val rz = event.values[2]
                if (!initialized) {
                    fx = rx; fy = ry; fz = rz
                    initialized = true
                } else {
                    fx += alpha * (rx - fx)
                    fy += alpha * (ry - fy)
                    fz += alpha * (rz - fz)
                }
                gx = fx
                gy = fy
                gz = fz
                sensorActive = true

                // Hysteresis mode detection based on RAW gravity vector BEFORE calibration
                val norm = sqrt((fx * fx + fy * fy + fz * fz).toDouble()).coerceAtLeast(0.01)
                val absX = abs(fx) / norm
                val absY = abs(fy) / norm
                val absZ = abs(fz) / norm

                autoMode = when (autoMode) {
                    LevelMode.FLAT -> when {
                        absZ < 0.62 && absX > absY -> LevelMode.LANDSCAPE
                        absZ < 0.62 && absY >= absX -> LevelMode.PORTRAIT
                        else -> LevelMode.FLAT
                    }
                    LevelMode.PORTRAIT -> when {
                        absZ > 0.78 -> LevelMode.FLAT
                        absX > absY + 0.16 -> LevelMode.LANDSCAPE
                        else -> LevelMode.PORTRAIT
                    }
                    LevelMode.LANDSCAPE -> when {
                        absZ > 0.78 -> LevelMode.FLAT
                        absY > absX + 0.16 -> LevelMode.PORTRAIT
                        else -> LevelMode.LANDSCAPE
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (activeSensor != null) {
            sensorManager.registerListener(listener, activeSensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    val activeMode = when (modeLock) {
        ModeLock.AUTO -> autoMode
        ModeLock.FLAT -> LevelMode.FLAT
        ModeLock.PORTRAIT -> LevelMode.PORTRAIT
        ModeLock.LANDSCAPE -> LevelMode.LANDSCAPE
    }

    // Raw physical tilt angles computed cleanly via atan2 (no gimbal lock at 90°)
    val (rawPrimary, rawSecondary) = when (activeMode) {
        LevelMode.FLAT -> {
            val roll = Math.toDegrees(atan2(-gx.toDouble(), hypot(gy.toDouble(), gz.toDouble()))).toFloat()
            val pitch = Math.toDegrees(atan2(gy.toDouble(), hypot(gx.toDouble(), gz.toDouble()))).toFloat()
            roll to pitch
        }
        LevelMode.PORTRAIT -> {
            // Side tilt (left/right plumb) & forward/back wall lean
            val sideTilt = Math.toDegrees(atan2(-gx.toDouble(), abs(gy.toDouble()).coerceAtLeast(0.01))).toFloat()
            val wallLean = Math.toDegrees(atan2(gz.toDouble(), abs(gy.toDouble()).coerceAtLeast(0.01))).toFloat()
            sideTilt to wallLean
        }
        LevelMode.LANDSCAPE -> {
            // Long-edge shelf level & face lean
            val edgeTilt = Math.toDegrees(atan2(gy.toDouble(), abs(gx.toDouble()).coerceAtLeast(0.01))).toFloat()
            val faceLean = Math.toDegrees(atan2(gz.toDouble(), abs(gx.toDouble()).coerceAtLeast(0.01))).toFloat()
            edgeTilt to faceLean
        }
    }

    val (calP, calS) = when (activeMode) {
        LevelMode.FLAT -> calFlatPrimary to calFlatSecondary
        LevelMode.PORTRAIT -> calPortPrimary to calPortSecondary
        LevelMode.LANDSCAPE -> calLandPrimary to calLandSecondary
    }

    val primaryAngle = rawPrimary - calP
    val secondaryAngle = rawSecondary - calS
    val hasCalibration = calP != 0f || calS != 0f

    val isLevel = when (activeMode) {
        LevelMode.FLAT -> abs(primaryAngle) <= 0.5f && abs(secondaryAngle) <= 0.5f
        LevelMode.PORTRAIT, LevelMode.LANDSCAPE -> abs(primaryAngle) <= 0.5f
    }

    // Haptic feedback with clean hysteresis so phone only pulses once upon hitting level
    LaunchedEffect(isLevel, primaryAngle, secondaryAngle, sensorActive) {
        if (!sensorActive) return@LaunchedEffect
        if (isLevel && hapticArmed) {
            hapticArmed = false
            triggerShortVibrate(context)
        } else if (!isLevel && abs(primaryAngle) > 1.3f) {
            hapticArmed = true
        }
    }

    val modeSubtitle = when (activeMode) {
        LevelMode.FLAT -> lang.tr("Mod Baring (Meja / Permukaan)", "Flat Mode (Table / Surface)")
        LevelMode.PORTRAIT -> lang.tr("Mod Menegak (Dinding / Tiang)", "Portrait Plumb (Wall / Pole)")
        LevelMode.LANDSCAPE -> lang.tr("Mod Melintang (Sisi Panjang)", "Landscape Mode (Long Edge)")
    }

    ToolScaffold(
        title = lang.tr("Peringkat Air", "Spirit Level"),
        subtitle = modeSubtitle,
        onBack = onBack,
        actions = {
            if (hasHardware) {
                IconButton(onClick = {
                    when (activeMode) {
                        LevelMode.FLAT -> { calFlatPrimary = rawPrimary; calFlatSecondary = rawSecondary }
                        LevelMode.PORTRAIT -> { calPortPrimary = rawPrimary; calPortSecondary = rawSecondary }
                        LevelMode.LANDSCAPE -> { calLandPrimary = rawPrimary; calLandSecondary = rawSecondary }
                    }
                }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = lang.tr("Tentukur Sifar", "Zero Set"))
                }
            }
        }
    ) {
        if (!hasHardware) {
            HardwareMissingCard(
                title = lang.tr("Penderia Graviti Tiada", "Gravity Sensor Missing"),
                description = lang.tr(
                    "Peranti ini tidak mempunyai penderia graviti atau akselerometer.",
                    "This device does not have a gravity or accelerometer sensor."
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
            // Mode Selector Chips (Auto / Flat / Portrait / Landscape)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
            ) {
                listOf(
                    ModeLock.AUTO to lang.tr("Auto", "Auto"),
                    ModeLock.FLAT to lang.tr("Baring", "Flat"),
                    ModeLock.PORTRAIT to lang.tr("Tegak", "Plumb"),
                    ModeLock.LANDSCAPE to lang.tr("Sisi", "Edge")
                ).forEach { (lock, label) ->
                    FilterChip(
                        selected = modeLock == lock,
                        onClick = { modeLock = lock },
                        label = { Text(label, maxLines = 1) }
                    )
                }
            }

            // 2D Bullseye Level Canvas
            BullseyeCanvas(
                angleX = primaryAngle,
                angleY = secondaryAngle,
                maxDeg = if (activeMode == LevelMode.FLAT) 35f else 25f,
                isLevel = isLevel,
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .widthIn(max = 270.dp)
                    .aspectRatio(1f)
            )

            // Status Pill
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (isLevel) Color(0xFF10B981).copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(
                    1.dp,
                    if (isLevel) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isLevel) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = if (isLevel) {
                            when (activeMode) {
                                LevelMode.PORTRAIT -> lang.tr("Tegak Sempurna (0.0°)", "Perfect Plumb (0.0°)")
                                else -> lang.tr("Sempurna Rata (0.0°)", "Perfectly Level (0.0°)")
                            }
                        } else {
                            lang.tr("Laraskan sehingga buih ke tengah", "Adjust until bubble centers")
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isLevel) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Numeric Angle Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AngleCard(
                    title = when (activeMode) {
                        LevelMode.FLAT -> lang.tr("Kiri / Kanan", "Roll (L / R)")
                        LevelMode.PORTRAIT -> lang.tr("Aras Tegak", "Plumb Tilt")
                        LevelMode.LANDSCAPE -> lang.tr("Aras Sisi", "Edge Level")
                    },
                    angle = primaryAngle,
                    highlighted = abs(primaryAngle) <= 0.5f,
                    modifier = Modifier.weight(1f)
                )
                AngleCard(
                    title = when (activeMode) {
                        LevelMode.FLAT -> lang.tr("Depan / Belakang", "Pitch (F / B)")
                        LevelMode.PORTRAIT -> lang.tr("Condong Dinding", "Wall Lean")
                        LevelMode.LANDSCAPE -> lang.tr("Condong Muka", "Face Lean")
                    },
                    angle = secondaryAngle,
                    highlighted = activeMode == LevelMode.FLAT && abs(secondaryAngle) <= 0.5f,
                    modifier = Modifier.weight(1f)
                )
            }

            // Responsive Horizontal Liquid Tube (scales dynamically to any screen width)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = lang.tr("Tiub Aras Cecair", "Liquid Bubble Tube"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(Locale.US, "%+.1f°", primaryAngle),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (abs(primaryAngle) <= 0.5f) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                        )
                    }
                    LiquidTubeCanvas(
                        angleDeg = primaryAngle,
                        maxDeg = 20f,
                        isLevel = abs(primaryAngle) <= 0.5f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                    )
                }
            }

            if (hasCalibration) {
                OutlinedButton(
                    onClick = {
                        when (activeMode) {
                            LevelMode.FLAT -> { calFlatPrimary = 0f; calFlatSecondary = 0f }
                            LevelMode.PORTRAIT -> { calPortPrimary = 0f; calPortSecondary = 0f }
                            LevelMode.LANDSCAPE -> { calLandPrimary = 0f; calLandSecondary = 0f }
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(lang.tr("Set Semula ke Sifar Kilang", "Reset to Factory Zero"))
                }
            }
        }
    }
}

@Composable
private fun BullseyeCanvas(
    angleX: Float,
    angleY: Float,
    maxDeg: Float,
    isLevel: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outline
    val accent = if (isLevel) Color(0xFF10B981) else MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(color = bgColor, radius = radius, center = center)
        drawCircle(color = gridColor, radius = radius - 3.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))

        // Concentric target rings & crosshairs
        drawCircle(color = gridColor.copy(alpha = 0.5f), radius = radius * 0.68f, center = center, style = Stroke(1.2.dp.toPx()))
        drawCircle(color = gridColor.copy(alpha = 0.7f), radius = radius * 0.36f, center = center, style = Stroke(1.2.dp.toPx()))
        drawLine(gridColor.copy(alpha = 0.55f), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.dp.toPx())
        drawLine(gridColor.copy(alpha = 0.55f), Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.dp.toPx())

        // Center sweet-spot ring
        drawCircle(
            color = accent.copy(alpha = if (isLevel) 0.25f else 0.1f),
            radius = radius * 0.20f,
            center = center
        )
        drawCircle(
            color = accent,
            radius = radius * 0.20f,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )

        // Clamp bubble inside circular rim cleanly
        val maxTravel = radius * 0.76f
        val nx = (angleX / maxDeg).coerceIn(-1f, 1f)
        val ny = (angleY / maxDeg).coerceIn(-1f, 1f)
        val dist = hypot(nx.toDouble(), ny.toDouble()).toFloat()
        val scale = if (dist > 1f) 1f / dist else 1f
        val bubbleCenter = Offset(
            x = center.x + nx * scale * maxTravel,
            y = center.y + ny * scale * maxTravel
        )
        val bubbleRadius = radius * 0.155f

        drawCircle(color = accent.copy(alpha = 0.28f), radius = bubbleRadius * 1.25f, center = bubbleCenter)
        drawCircle(color = accent, radius = bubbleRadius, center = bubbleCenter)
        drawCircle(
            color = Color.White.copy(alpha = 0.55f),
            radius = bubbleRadius * 0.32f,
            center = Offset(bubbleCenter.x - bubbleRadius * 0.28f, bubbleCenter.y - bubbleRadius * 0.28f)
        )
    }
}

@Composable
private fun LiquidTubeCanvas(
    angleDeg: Float,
    maxDeg: Float,
    isLevel: Boolean,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline
    val bubbleColor = if (isLevel) Color(0xFF10B981) else MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val h = size.height
        val w = size.width
        val corner = CornerRadius(h / 2f, h / 2f)

        drawRoundRect(color = trackColor, cornerRadius = corner)
        drawRoundRect(color = borderColor, cornerRadius = corner, style = Stroke(1.5.dp.toPx()))

        // Center calibration lines
        val centerBoxW = (w * 0.16f).coerceAtLeast(h * 1.15f)
        val cx = w / 2f
        drawLine(borderColor, Offset(cx - centerBoxW / 2f, 2.dp.toPx()), Offset(cx - centerBoxW / 2f, h - 2.dp.toPx()), 1.8.dp.toPx())
        drawLine(borderColor, Offset(cx + centerBoxW / 2f, 2.dp.toPx()), Offset(cx + centerBoxW / 2f, h - 2.dp.toPx()), 1.8.dp.toPx())

        val bubbleW = centerBoxW * 0.84f
        val bubbleH = h - 8.dp.toPx()
        val maxOffset = (w / 2f) - (bubbleW / 2f) - 4.dp.toPx()
        val norm = (angleDeg / maxDeg).coerceIn(-1f, 1f)
        val left = cx + norm * maxOffset - bubbleW / 2f

        drawRoundRect(
            color = bubbleColor,
            topLeft = Offset(left, 4.dp.toPx()),
            size = Size(bubbleW, bubbleH),
            cornerRadius = CornerRadius(bubbleH / 2f, bubbleH / 2f)
        )
    }
}

@Composable
private fun AngleCard(
    title: String,
    angle: Float,
    highlighted: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (highlighted) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = String.format(Locale.US, "%+.1f°", angle),
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = if (highlighted) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun triggerShortVibrate(context: Context) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(30L, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
