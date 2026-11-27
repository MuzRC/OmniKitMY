package com.muz.omnikit.tools.camera

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.ZoomIn
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.muz.omnikit.core.media.MediaExportHelper
import com.muz.omnikit.core.model.Language
import com.muz.omnikit.ui.components.PermissionRequestCard
import com.muz.omnikit.ui.components.ToolScaffold
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

@Composable
fun MagnifierColorScreen(
    lang: Language,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPerm = granted
    }

    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(8f) }

    var exposureIdx by remember { mutableIntStateOf(0) }
    var minExp by remember { mutableIntStateOf(0) }
    var maxExp by remember { mutableIntStateOf(0) }

    var frozenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var crosshairFracX by remember { mutableFloatStateOf(0.5f) }
    var crosshairFracY by remember { mutableFloatStateOf(0.5f) }
    var sampledRgb by remember { mutableIntStateOf(AndroidColor.rgb(56, 189, 248)) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val freezeTrigger = remember { AtomicBoolean(false) }
    val isFrozen = frozenBitmap != null

    val r = AndroidColor.red(sampledRgb)
    val g = AndroidColor.green(sampledRgb)
    val b = AndroidColor.blue(sampledRgb)
    val hexCode = String.format(Locale.US, "#%02X%02X%02X", r, g, b)
    val rgbText = "RGB($r, $g, $b)"
    val hslText = remember(r, g, b) { rgbToHslString(r, g, b) }
    val colorName = remember(r, g, b, lang) { nearestColorName(r, g, b, lang) }
    val sampledComposeColor = Color(r, g, b)

    ToolScaffold(
        title = lang.tr("Kanta & Pengesan Warna", "Magnifier & Color Picker"),
        subtitle = lang.tr("Zum tinggi, beku gambar & kod HEX/RGB", "High zoom, freeze frame & HEX/RGB picker"),
        onBack = onBack
    ) {
        if (!hasCameraPerm) {
            PermissionRequestCard(
                icon = Icons.Rounded.CameraAlt,
                title = lang.tr("Kebenaran Kamera Diperlukan", "Camera Permission Needed"),
                description = lang.tr(
                    "Benarkan akses kamera untuk menggunakan kanta pembesar dan pengesan kod warna masa nyata.",
                    "Allow camera access to use the live magnifier lens and color eyedropper."
                ),
                buttonLabel = lang.tr("Benarkan Kamera", "Allow Camera"),
                onRequest = { permLauncher.launch(Manifest.permission.CAMERA) }
            )
            return@ToolScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Viewport: Live CameraX OR Frozen High-Res Frame (tap anywhere on frozen frame to sample pixel!)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.05f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.Black)
                    .pointerInput(isFrozen, minZoom, maxZoom) {
                        if (!isFrozen) {
                            detectTransformGestures { _, _, gestureZoom, _ ->
                                val next = (zoomRatio * gestureZoom).coerceIn(minZoom, maxZoom)
                                zoomRatio = next
                                cameraRef?.cameraControl?.setZoomRatio(next)
                            }
                        }
                    }
                    .pointerInput(frozenBitmap) {
                        val bmp = frozenBitmap ?: return@pointerInput
                        detectTapGestures { offset ->
                            val fx = (offset.x / size.width).coerceIn(0f, 1f)
                            val fy = (offset.y / size.height).coerceIn(0f, 1f)
                            crosshairFracX = fx
                            crosshairFracY = fy
                            sampledRgb = sampleBitmapRegionColor(bmp, fx, fy)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val bmp = frozenBitmap
                if (bmp == null) {
                    MagnifierCameraView(
                        torchOn = torchOn,
                        freezeTrigger = freezeTrigger,
                        onCameraBound = { cam ->
                            cameraRef = cam
                            hasTorch = cam.cameraInfo.hasFlashUnit()
                            val zs = cam.cameraInfo.zoomState.value
                            if (zs != null) {
                                minZoom = zs.minZoomRatio
                                maxZoom = zs.maxZoomRatio.coerceAtLeast(1.5f)
                                zoomRatio = zs.zoomRatio.coerceIn(minZoom, maxZoom)
                            }
                            val expRange = cam.cameraInfo.exposureState.exposureCompensationRange
                            minExp = expRange.lower
                            maxExp = expRange.upper
                            exposureIdx = cam.cameraInfo.exposureState.exposureCompensationIndex
                        },
                        onCenterColorSampled = { rgbInt ->
                            sampledRgb = rgbInt
                        },
                        onFrameFrozen = { captured ->
                            frozenBitmap = captured
                            crosshairFracX = 0.5f
                            crosshairFracY = 0.5f
                            sampledRgb = sampleBitmapRegionColor(captured, 0.5f, 0.5f)
                            torchOn = false
                        },
                        onError = { err -> cameraError = err }
                    )
                } else {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Frozen Frame",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Precision Color Crosshair Overlay
                ColorCrosshairOverlay(
                    fracX = if (isFrozen) crosshairFracX else 0.5f,
                    fracY = if (isFrozen) crosshairFracY else 0.5f,
                    ringColor = sampledComposeColor,
                    modifier = Modifier.fillMaxSize()
                )

                // Top-left Zoom / Frozen Pill
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = 0.62f)
                ) {
                    Text(
                        text = if (isFrozen) {
                            lang.tr("DIBEKU • Ketik untuk bidik warna", "FROZEN • Tap anywhere to pick color")
                        } else {
                            String.format(Locale.US, "ZUM %.1fx", zoomRatio)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isFrozen) Color(0xFFFACC15) else Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                // Top-right Torch Toggle (live mode only)
                if (!isFrozen && hasTorch) {
                    IconButton(
                        onClick = {
                            val next = !torchOn
                            torchOn = next
                            cameraRef?.cameraControl?.enableTorch(next)
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .clip(CircleShape)
                            .background(if (torchOn) Color(0xFFFACC15) else Color.Black.copy(alpha = 0.55f))
                    ) {
                        Icon(
                            imageVector = if (torchOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                            contentDescription = "Torch",
                            tint = if (torchOn) Color.Black else Color.White
                        )
                    }
                }
            }

            if (!cameraError.isNullOrBlank()) {
                Text(
                    text = cameraError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Freeze / Unfreeze & Save Snapshot Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (isFrozen) {
                            frozenBitmap = null
                        } else {
                            freezeTrigger.set(true)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = if (isFrozen) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isFrozen) lang.tr("Sambung Kamera", "Resume Live") else lang.tr("Beku Gambar", "Freeze Frame"),
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isFrozen) {
                    OutlinedButton(
                        onClick = {
                            val bmp = frozenBitmap ?: return@OutlinedButton
                            val uri = MediaExportHelper.saveBitmapToGallery(
                                context = context,
                                bitmap = bmp,
                                displayName = "OmniKitMY_Zoom_${System.currentTimeMillis()}.jpg",
                                format = Bitmap.CompressFormat.JPEG,
                                quality = 94
                            )
                            Toast.makeText(
                                context,
                                if (uri != null) lang.tr("Gambar disimpan ke Galeri!", "Snapshot saved to Gallery!")
                                else lang.tr("Gagal menyimpan gambar.", "Failed to save image."),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(lang.tr("Simpan", "Save"))
                    }
                }
            }

            // Live Color Telemetry Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(sampledComposeColor)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = colorName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$hexCode  •  $rgbText",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = hslText,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("Color", "$hexCode | $rgbText"))
                            Toast.makeText(context, lang.tr("$hexCode disalin!", "$hexCode copied!"), Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy HEX")
                    }
                }
            }

            // Optical/Digital Zoom & Exposure Controls
            if (!isFrozen) {
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Rounded.ZoomIn, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(lang.tr("Zum Kanta (atau cubit skrin)", "Lens Zoom (or pinch)"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Text(
                                text = String.format(Locale.US, "%.1fx", zoomRatio),
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = zoomRatio,
                            onValueChange = { v ->
                                zoomRatio = v
                                cameraRef?.cameraControl?.setZoomRatio(v)
                            },
                            valueRange = minZoom..maxZoom
                        )

                        if (maxExp > minExp) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(lang.tr("Pendedahan Cahaya (EV)", "Exposure (EV)"), style = MaterialTheme.typography.labelMedium)
                                Text(
                                    text = if (exposureIdx > 0) "+$exposureIdx" else "$exposureIdx",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Slider(
                                value = exposureIdx.toFloat(),
                                onValueChange = { v ->
                                    val idx = v.roundToInt().coerceIn(minExp, maxExp)
                                    exposureIdx = idx
                                    cameraRef?.cameraControl?.setExposureCompensationIndex(idx)
                                },
                                valueRange = minExp.toFloat()..maxExp.toFloat()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MagnifierCameraView(
    torchOn: Boolean,
    freezeTrigger: AtomicBoolean,
    onCameraBound: (Camera) -> Unit,
    onCenterColorSampled: (Int) -> Unit,
    onFrameFrozen: (Bitmap) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val lastSampleMs = remember { AtomicLong(0L) }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var boundProvider: ProcessCameraProvider? = null

        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                boundProvider = provider
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(executor) { imageProxy ->
                    try {
                        val shouldFreeze = freezeTrigger.compareAndSet(true, false)
                        if (shouldFreeze) {
                            val rawBmp = imageProxy.toBitmap()
                            val rot = imageProxy.imageInfo.rotationDegrees
                            val upright = rotateBitmapIfNeeded(rawBmp, rot)
                            ContextCompat.getMainExecutor(context).execute {
                                onFrameFrozen(upright)
                            }
                        } else {
                            val now = System.currentTimeMillis()
                            if (now - lastSampleMs.get() >= 110L) {
                                lastSampleMs.set(now)
                                val plane = imageProxy.planes[0]
                                val buf = plane.buffer
                                val rowStride = plane.rowStride
                                val pixelStride = plane.pixelStride
                                val cx = imageProxy.width / 2
                                val cy = imageProxy.height / 2

                                // Average 3x3 patch around center for steady, noise-free color reading
                                var sr = 0; var sg = 0; var sb = 0; var cnt = 0
                                for (dy in -1..1) {
                                    for (dx in -1..1) {
                                        val px = (cx + dx).coerceIn(0, imageProxy.width - 1)
                                        val py = (cy + dy).coerceIn(0, imageProxy.height - 1)
                                        val offset = py * rowStride + px * pixelStride
                                        if (offset + 2 < buf.limit()) {
                                            sr += buf.get(offset).toInt() and 0xFF
                                            sg += buf.get(offset + 1).toInt() and 0xFF
                                            sb += buf.get(offset + 2).toInt() and 0xFF
                                            cnt++
                                        }
                                    }
                                }
                                if (cnt > 0) {
                                    val rgb = AndroidColor.rgb(sr / cnt, sg / cnt, sb / cnt)
                                    ContextCompat.getMainExecutor(context).execute {
                                        onCenterColorSampled(rgb)
                                    }
                                }
                            }
                        }
                    } finally {
                        imageProxy.close()
                    }
                }

                provider.unbindAll()
                val cam = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                cam.cameraControl.enableTorch(torchOn)
                onCameraBound(cam)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Camera error")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { boundProvider?.unbindAll() }
            runCatching { executor.shutdown() }
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun ColorCrosshairOverlay(
    fracX: Float,
    fracY: Float,
    ringColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width * fracX, size.height * fracY)
        val outerR = 20.dp.toPx()
        val innerR = 5.dp.toPx()

        drawCircle(color = Color.Black.copy(alpha = 0.65f), radius = outerR + 2.dp.toPx(), center = center, style = Stroke(4.dp.toPx()))
        drawCircle(color = ringColor, radius = outerR, center = center, style = Stroke(3.dp.toPx()))
        drawCircle(color = Color.White, radius = innerR, center = center, style = Stroke(1.6.dp.toPx()))
    }
}

private fun rotateBitmapIfNeeded(source: Bitmap, rotationDeg: Int): Bitmap {
    if (rotationDeg == 0) return source
    val matrix = Matrix().apply { postRotate(rotationDeg.toFloat()) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

private fun sampleBitmapRegionColor(bmp: Bitmap, fracX: Float, fracY: Float): Int {
    val cx = (fracX * (bmp.width - 1)).roundToInt().coerceIn(0, bmp.width - 1)
    val cy = (fracY * (bmp.height - 1)).roundToInt().coerceIn(0, bmp.height - 1)
    var sr = 0; var sg = 0; var sb = 0; var cnt = 0
    for (dy in -2..2) {
        for (dx in -2..2) {
            val px = (cx + dx).coerceIn(0, bmp.width - 1)
            val py = (cy + dy).coerceIn(0, bmp.height - 1)
            val c = bmp.getPixel(px, py)
            sr += AndroidColor.red(c)
            sg += AndroidColor.green(c)
            sb += AndroidColor.blue(c)
            cnt++
        }
    }
    return AndroidColor.rgb(sr / cnt, sg / cnt, sb / cnt)
}

private fun rgbToHslString(r: Int, g: Int, b: Int): String {
    val rf = r / 255f
    val gf = g / 255f
    val bf = b / 255f
    val max = maxOf(rf, gf, bf)
    val min = minOf(rf, gf, bf)
    val delta = max - min
    val l = (max + min) / 2f
    val s = if (delta == 0f) 0f else delta / (1f - kotlin.math.abs(2f * l - 1f))
    val h = when {
        delta == 0f -> 0f
        max == rf -> 60f * (((gf - bf) / delta) % 6f)
        max == gf -> 60f * (((bf - rf) / delta) + 2f)
        else -> 60f * (((rf - gf) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return String.format(Locale.US, "HSL(%.0f°, %.0f%%, %.0f%%)", h, s * 100f, l * 100f)
}

private data class NamedColor(val ms: String, val en: String, val r: Int, val g: Int, val b: Int)

private val COLOR_PALETTE = listOf(
    NamedColor("Hitam Pekat", "Jet Black", 15, 15, 18),
    NamedColor("Kelabu Gelap", "Charcoal Gray", 55, 65, 81),
    NamedColor("Kelabu", "Slate Gray", 128, 138, 150),
    NamedColor("Perak Cerah", "Light Silver", 203, 213, 225),
    NamedColor("Putih Tulen", "Pure White", 248, 250, 252),
    NamedColor("Putih Krim", "Warm Ivory", 245, 240, 225),
    NamedColor("Merah Tua", "Maroon", 127, 29, 29),
    NamedColor("Merah Terang", "Crimson Red", 220, 38, 38),
    NamedColor("Merah Jambu", "Pink", 244, 114, 182),
    NamedColor("Merah Koral", "Coral", 251, 113, 133),
    NamedColor("Jingga", "Orange", 249, 115, 22),
    NamedColor("Ambar", "Amber", 245, 158, 11),
    NamedColor("Kuning Emas", "Gold Yellow", 250, 204, 21),
    NamedColor("Kuning Lembut", "Soft Yellow", 254, 240, 138),
    NamedColor("Coklat Gelap", "Dark Brown", 92, 58, 33),
    NamedColor("Coklat Kayu", "Warm Brown", 154, 104, 64),
    NamedColor("Kuning Air / Beige", "Sand Beige", 214, 193, 158),
    NamedColor("Hijau Zaitun", "Olive Green", 101, 130, 52),
    NamedColor("Hijau Limau", "Lime Green", 132, 204, 22),
    NamedColor("Hijau Daun", "Leaf Green", 34, 197, 94),
    NamedColor("Hijau Zamrud", "Emerald", 16, 185, 129),
    NamedColor("Hijau Hutan", "Forest Green", 20, 83, 45),
    NamedColor("Hijau Kebiruan (Teal)", "Teal", 13, 148, 136),
    NamedColor("Sian", "Cyan", 6, 182, 212),
    NamedColor("Biru Langit", "Sky Blue", 56, 189, 248),
    NamedColor("Biru Diraja", "Royal Blue", 37, 99, 235),
    NamedColor("Biru Laut Gelap", "Navy Blue", 23, 37, 84),
    NamedColor("Indigo", "Indigo", 99, 102, 241),
    NamedColor("Ungu Violet", "Violet", 139, 92, 246),
    NamedColor("Ungu Gelap", "Deep Purple", 88, 28, 135),
    NamedColor("Magenta", "Magenta", 217, 70, 239)
)

private fun nearestColorName(r: Int, g: Int, b: Int, lang: Language): String {
    var best = COLOR_PALETTE[0]
    var bestDist = Long.MAX_VALUE
    for (c in COLOR_PALETTE) {
        val dr = (r - c.r).toLong()
        val dg = (g - c.g).toLong()
        val db = (b - c.b).toLong()
        // Weighted perceptual RGB distance
        val dist = 2 * dr * dr + 4 * dg * dg + 3 * db * db
        if (dist < bestDist) {
            bestDist = dist
            best = c
        }
    }
    return lang.tr(best.ms, best.en)
}
