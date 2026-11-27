package com.muz.omnikit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Crisp vector-matched Compose emblem mirroring the OmniKitMY launcher icon
 * for consistent branding in the Top Bar and Settings header.
 */
@Composable
fun OmniKitLogo(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val s = { v: Float -> v / 108f * w }

        // Rounded badge background
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF1A3A6E), Color(0xFF0E1E3B), Color(0xFF060D1A)),
                center = Offset(w * 0.35f, h * 0.30f),
                radius = w * 0.9f
            ),
            cornerRadius = CornerRadius(w * 0.24f, h * 0.24f)
        )

        // Outer Hex Shield
        val hexPath = Path().apply {
            moveTo(s(54f), s(16f))
            lineTo(s(85f), s(34f))
            lineTo(s(85f), s(74f))
            lineTo(s(54f), s(92f))
            lineTo(s(23f), s(74f))
            lineTo(s(23f), s(34f))
            close()
        }
        drawPath(hexPath, color = Color(0xFF10213E))
        drawPath(
            path = hexPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF38BDF8), Color(0xFF3B82F6), Color(0xFFFBBF24)),
                start = Offset(s(23f), s(16f)),
                end = Offset(s(85f), s(92f))
            ),
            style = Stroke(width = s(3.8f), join = StrokeJoin.Round)
        )

        // Inner Dial Circle
        drawCircle(
            color = Color(0xFF0A152B),
            radius = s(26f),
            center = Offset(w / 2f, h / 2f)
        )
        drawCircle(
            color = Color(0x5538BDF8),
            radius = s(26f),
            center = Offset(w / 2f, h / 2f),
            style = Stroke(width = s(1.8f))
        )

        // Kit Handle
        drawRoundRect(
            color = Color(0xFF38BDF8),
            topLeft = Offset(s(45f), s(33f)),
            size = Size(s(18f), s(11f)),
            cornerRadius = CornerRadius(s(3.5f), s(3.5f)),
            style = Stroke(width = s(2.8f), cap = StrokeCap.Round)
        )

        // Kit Case
        drawRoundRect(
            color = Color(0xFF18335E),
            topLeft = Offset(s(35f), s(40f)),
            size = Size(s(38f), s(32f)),
            cornerRadius = CornerRadius(s(6f), s(6f))
        )
        drawRoundRect(
            color = Color(0xFFE2E8F0),
            topLeft = Offset(s(35f), s(40f)),
            size = Size(s(38f), s(32f)),
            cornerRadius = CornerRadius(s(6f), s(6f)),
            style = Stroke(width = s(2.5f))
        )

        // Compass Diamond Core (Gold North + Cyan South)
        val northPath = Path().apply {
            moveTo(s(54f), s(44f))
            lineTo(s(61f), s(56f))
            lineTo(s(54f), s(53.5f))
            lineTo(s(47f), s(56f))
            close()
        }
        val southPath = Path().apply {
            moveTo(s(54f), s(68f))
            lineTo(s(61f), s(56f))
            lineTo(s(54f), s(58.5f))
            lineTo(s(47f), s(56f))
            close()
        }
        drawPath(northPath, color = Color(0xFFFBBF24))
        drawPath(southPath, color = Color(0xFF38BDF8))
        drawCircle(color = Color.White, radius = s(2.4f), center = Offset(s(54f), s(56f)))

        // Top Gold MY Star Spark
        val starPath = Path().apply {
            moveTo(s(54f), s(11f))
            lineTo(s(55.8f), s(15.2f))
            lineTo(s(60f), s(17f))
            lineTo(s(55.8f), s(18.8f))
            lineTo(s(54f), s(23f))
            lineTo(s(52.2f), s(18.8f))
            lineTo(s(48f), s(17f))
            lineTo(s(52.2f), s(15.2f))
            close()
        }
        drawPath(starPath, color = Color(0xFFFBBF24))
    }
}
