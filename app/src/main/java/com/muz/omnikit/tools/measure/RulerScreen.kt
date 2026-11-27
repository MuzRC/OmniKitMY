package com.muz.omnikit.tools.measure

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muz.omnikit.core.model.Language
import com.muz.omnikit.ui.components.ToolScaffold
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class RulerUnitMode { BOTH, METRIC, IMPERIAL }

private const val RULER_PREFS = "omnikit_ruler_prefs"
private const val KEY_CAL_SCALE = "dpi_scale_factor"
private const val ISO_CARD_SHORT_MM = 53.98f // Standard MyKad / Bank Card short edge (ISO/IEC 7810 ID-1)

@Composable
fun RulerScreen(
    lang: Language,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(RULER_PREFS, Context.MODE_PRIVATE)
    }

    val rawYdpi = remember(context) {
        val dm = context.resources.displayMetrics
        if (dm.ydpi in 80f..1200f) dm.ydpi else dm.densityDpi.toFloat().coerceAtLeast(160f)
    }

    var calibrationScale by remember {
        mutableFloatStateOf(prefs.getFloat(KEY_CAL_SCALE, 1.0f).coerceIn(0.75f, 1.25f))
    }
    var unitMode by remember { mutableStateOf(RulerUnitMode.BOTH) }
    var showCalibrateDialog by remember { mutableStateOf(false) }
    var canvasHeightPx by remember { mutableFloatStateOf(0f) }

    // Caliper jaw positions stored in physical millimeters from top edge (0mm)
    var jaw1Mm by remember { mutableFloatStateOf(10f) }
    var jaw2Mm by remember { mutableFloatStateOf(50f) }
    var activeJaw by remember { mutableIntStateOf(0) } // 0 = none, 1 = jaw1, 2 = jaw2

    val effectiveYdpi = rawYdpi * calibrationScale
    val pxPerMm = effectiveYdpi / 25.4f
    val pxPerInch = effectiveYdpi

    val maxVisibleMm = remember(canvasHeightPx, pxPerMm) {
        if (canvasHeightPx > 0f && pxPerMm > 0f) canvasHeightPx / pxPerMm else 120f
    }

    val deltaMm = abs(jaw2Mm - jaw1Mm)
    val deltaCm = deltaMm / 10f
    val deltaInch = deltaMm / 25.4f

    fun saveScale(newScale: Float) {
        val clamped = newScale.coerceIn(0.75f, 1.25f)
        calibrationScale = clamped
        prefs.edit().putFloat(KEY_CAL_SCALE, clamped).apply()
    }

    ToolScaffold(
        title = lang.tr("Pembaris Skrin", "Screen Ruler"),
        subtitle = lang.tr("Skala fizikal tepat & angkup digital", "Physical DPI scale & digital caliper"),
        onBack = onBack,
        actions = {
            IconButton(onClick = { showCalibrateDialog = true }) {
                Icon(Icons.Rounded.Tune, contentDescription = lang.tr("Kalibrasi DPI", "Calibrate DPI"))
            }
            IconButton(onClick = {
                jaw1Mm = 0f
                jaw2Mm = (maxVisibleMm * 0.45f).coerceIn(10f, 50f)
            }) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = lang.tr("Set Semula", "Reset"))
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Digital Caliper Readout Panel
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = lang.tr("JARAK ANGKUP (SERET GARIS)", "CALIPER SPAN (DRAG LINES)"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.1f mm", deltaMm),
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = String.format(Locale.US, "%.2f cm", deltaCm),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = String.format(Locale.US, "%.2f in", deltaInch),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { showCalibrateDialog = true },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = String.format(Locale.US, "%.0f%%", calibrationScale * 100f),
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Scale Selector Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = unitMode == RulerUnitMode.BOTH,
                            onClick = { unitMode = RulerUnitMode.BOTH },
                            label = { Text(lang.tr("cm + inci", "cm + in"), maxLines = 1) }
                        )
                        FilterChip(
                            selected = unitMode == RulerUnitMode.METRIC,
                            onClick = { unitMode = RulerUnitMode.METRIC },
                            label = { Text("mm / cm", maxLines = 1) }
                        )
                        FilterChip(
                            selected = unitMode == RulerUnitMode.IMPERIAL,
                            onClick = { unitMode = RulerUnitMode.IMPERIAL },
                            label = { Text(lang.tr("Inci (1/16)", "Inches (1/16)"), maxLines = 1) }
                        )
                    }
                }
            }

            // Full-Height Interactive Physical Ruler & Caliper Canvas
            val bgColor = MaterialTheme.colorScheme.background
            val tickColor = MaterialTheme.colorScheme.onBackground
            val minorTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
            val primaryColor = MaterialTheme.colorScheme.primary
            val tertiaryColor = MaterialTheme.colorScheme.tertiary
            val surfaceColor = MaterialTheme.colorScheme.surface

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(bgColor)
                    .onSizeChanged { size ->
                        canvasHeightPx = size.height.toFloat()
                        val maxMm = (size.height / pxPerMm).coerceAtLeast(10f)
                        if (jaw2Mm > maxMm) jaw2Mm = maxMm * 0.75f
                        if (jaw1Mm > maxMm) jaw1Mm = 0f
                    }
                    .pointerInput(pxPerMm) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val y1 = jaw1Mm * pxPerMm
                                val y2 = jaw2Mm * pxPerMm
                                activeJaw = if (abs(offset.y - y1) <= abs(offset.y - y2)) 1 else 2
                            },
                            onDragEnd = { activeJaw = 0 },
                            onDragCancel = { activeJaw = 0 },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val maxMm = (size.height / pxPerMm).coerceAtLeast(10f)
                                val deltaMmDrag = dragAmount.y / pxPerMm
                                if (activeJaw == 1) {
                                    jaw1Mm = (jaw1Mm + deltaMmDrag).coerceIn(0f, maxMm)
                                } else if (activeJaw == 2) {
                                    jaw2Mm = (jaw2Mm + deltaMmDrag).coerceIn(0f, maxMm)
                                }
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val totalMm = (h / pxPerMm).toInt() + 1
                    val totalSixteenths = ((h / pxPerInch) * 16f).toInt() + 1

                    // Shaded measured span between caliper jaws
                    val topY = minOf(jaw1Mm, jaw2Mm) * pxPerMm
                    val botY = maxOf(jaw1Mm, jaw2Mm) * pxPerMm
                    drawRect(
                        color = primaryColor.copy(alpha = 0.14f),
                        topLeft = Offset(0f, topY),
                        size = Size(w, (botY - topY).coerceAtLeast(1f))
                    )

                    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = 12.sp.toPx()
                        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    }

                    // LEFT EDGE: Metric Scale (mm & cm)
                    if (unitMode == RulerUnitMode.BOTH || unitMode == RulerUnitMode.METRIC) {
                        labelPaint.color = tickColor.toArgb()
                        labelPaint.textAlign = Paint.Align.LEFT
                        for (mm in 0..totalMm) {
                            val y = mm * pxPerMm
                            if (y > h) break
                            val isCm = mm % 10 == 0
                            val isHalfCm = mm % 5 == 0 && !isCm
                            val tickLen = when {
                                isCm -> 38.dp.toPx()
                                isHalfCm -> 26.dp.toPx()
                                else -> 15.dp.toPx()
                            }
                            drawLine(
                                color = if (isCm) primaryColor else if (isHalfCm) tickColor else minorTickColor,
                                start = Offset(0f, y),
                                end = Offset(tickLen, y),
                                strokeWidth = if (isCm) 2.dp.toPx() else if (isHalfCm) 1.4.dp.toPx() else 1.dp.toPx()
                            )
                            if (isCm) {
                                val cmVal = mm / 10
                                drawIntoCanvas { canvas ->
                                    canvas.nativeCanvas.drawText(
                                        "$cmVal cm",
                                        tickLen + 6.dp.toPx(),
                                        y + labelPaint.textSize * 0.35f,
                                        labelPaint
                                    )
                                }
                            }
                        }
                    }

                    // RIGHT EDGE: Imperial Scale (1/16 inch)
                    if (unitMode == RulerUnitMode.BOTH || unitMode == RulerUnitMode.IMPERIAL) {
                        labelPaint.color = tertiaryColor.toArgb()
                        labelPaint.textAlign = Paint.Align.RIGHT
                        for (sixteenth in 0..totalSixteenths) {
                            val y = (sixteenth / 16f) * pxPerInch
                            if (y > h) break
                            val isInch = sixteenth % 16 == 0
                            val isHalf = sixteenth % 8 == 0 && !isInch
                            val isQuarter = sixteenth % 4 == 0 && !isInch && !isHalf
                            val tickLen = when {
                                isInch -> 38.dp.toPx()
                                isHalf -> 28.dp.toPx()
                                isQuarter -> 21.dp.toPx()
                                else -> 13.dp.toPx()
                            }
                            drawLine(
                                color = if (isInch) tertiaryColor else if (isHalf || isQuarter) tickColor else minorTickColor,
                                start = Offset(w, y),
                                end = Offset(w - tickLen, y),
                                strokeWidth = if (isInch) 2.dp.toPx() else if (isHalf) 1.5.dp.toPx() else 1.dp.toPx()
                            )
                            if (isInch) {
                                val inchVal = sixteenth / 16
                                drawIntoCanvas { canvas ->
                                    canvas.nativeCanvas.drawText(
                                        "$inchVal"",
                                        w - tickLen - 6.dp.toPx(),
                                        y + labelPaint.textSize * 0.35f,
                                        labelPaint
                                    )
                                }
                            }
                        }
                    }

                    // Draw Draggable Caliper Jaw Lines + Center Grab Handles
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    listOf(
                        Triple(1, jaw1Mm * pxPerMm, primaryColor),
                        Triple(2, jaw2Mm * pxPerMm, Color(0xFF10B981))
                    ).forEach { (idx, yPos, jawColor) ->
                        val isActive = activeJaw == idx
                        drawLine(
                            color = jawColor,
                            start = Offset(0f, yPos),
                            end = Offset(w, yPos),
                            strokeWidth = if (isActive) 3.dp.toPx() else 2.dp.toPx(),
                            pathEffect = if (isActive) null else dashEffect
                        )

                        // Center pill handle
                        val pillW = 68.dp.toPx()
                        val pillH = 24.dp.toPx()
                        val cx = w / 2f
                        drawRoundRect(
                            color = surfaceColor,
                            topLeft = Offset(cx - pillW / 2f, yPos - pillH / 2f),
                            size = Size(pillW, pillH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillH / 2f, pillH / 2f)
                        )
                        drawRoundRect(
                            color = jawColor,
                            topLeft = Offset(cx - pillW / 2f, yPos - pillH / 2f),
                            size = Size(pillW, pillH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillH / 2f, pillH / 2f),
                            style = Stroke(width = 2.dp.toPx())
                        )

                        // Label position on handle
                        val mmText = String.format(Locale.US, "%.1f", if (idx == 1) jaw1Mm else jaw2Mm)
                        val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = jawColor.toArgb()
                            textSize = 11.sp.toPx()
                            textAlign = Paint.Align.CENTER
                            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                        }
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(
                                mmText,
                                cx,
                                yPos + handlePaint.textSize * 0.35f,
                                handlePaint
                            )
                        }
                    }

                    // Vertical double-arrow span indicator in center
                    if (botY - topY > 36.dp.toPx()) {
                        val cx = w / 2f
                        drawLine(
                            color = primaryColor.copy(alpha = 0.7f),
                            start = Offset(cx, topY + 14.dp.toPx()),
                            end = Offset(cx, botY - 14.dp.toPx()),
                            strokeWidth = 1.6.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }

    // Physical DPI Calibration Dialog using Standard MyKad / Bank Card (53.98 mm short side)
    if (showCalibrateDialog) {
        var tempScale by remember(calibrationScale) { mutableFloatStateOf(calibrationScale) }
        val previewPxPerMm = (rawYdpi * tempScale) / 25.4f

        AlertDialog(
            onDismissRequest = { showCalibrateDialog = false },
            title = {
                Text(
                    text = lang.tr("Kalibrasi Pembaris (Kad Bank / MyKad)", "Calibrate Ruler (Bank Card / ID)"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = lang.tr(
                            "Letakkan sisi pendek MyKad atau kad bank (tepat 53.98 mm) di atas bar biru di bawah, lalu laraskan slider sehingga saiznya ngam tepat.",
                            "Place the short edge of a standard ID or bank card (exact 53.98 mm) over the blue bar below, then slide until the height matches exactly."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Visual Calibration Reference Box (exact 53.98mm rendered height)
                    val barHeightDp = with(androidx.compose.ui.platform.LocalDensity.current) {
                        (ISO_CARD_SHORT_MM * previewPxPerMm).toDp()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barHeightDp.coerceIn(80.dp, 260.dp))
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRoundRect(
                                color = Color(0xFF38BDF8),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                        Text(
                            text = "53.98 mm (ISO Card)",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = lang.tr("Skala DPI:", "DPI Scale:"),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f%% (%.1f DPI)", tempScale * 100f, rawYdpi * tempScale),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = tempScale,
                        onValueChange = { tempScale = it },
                        valueRange = 0.80f..1.20f
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    saveScale(tempScale)
                    showCalibrateDialog = false
                }) {
                    Text(lang.tr("Simpan", "Save"))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { tempScale = 1.0f }) {
                        Text(lang.tr("Kilang (100%)", "Factory"))
                    }
                    TextButton(onClick = { showCalibrateDialog = false }) {
                        Text(lang.tr("Batal", "Cancel"))
                    }
                }
            }
        )
    }
}
