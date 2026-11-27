package com.muz.omnikit.tools.camera

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.MyLocation
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.muz.omnikit.core.model.Language
import com.muz.omnikit.core.sensors.SensorFlow
import com.muz.omnikit.ui.components.HardwareMissingCard
import com.muz.omnikit.ui.components.PermissionRequestCard
import com.muz.omnikit.ui.components.ToolScaffold
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.tan

@Composable
fun RangefinderScreen(
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

    val hasGravity = remember(context) {
        SensorFlow.hasSensor(context, Sensor.TYPE_GRAVITY) ||
            SensorFlow.hasSensor(context, Sensor.TYPE_ACCELEROMETER)
    }

    // Camera lens height above flat ground in meters (default 1.50m = typical eye-level hold)
    var cameraHeightMeters by remember { mutableFloatStateOf(1.50f) }
    var useFeet by remember { mutableStateOf(false) }

    // Camera optical axis elevation angle in degrees:
    // 0° = pointing perfectly horizontal; negative = tilting down toward ground; positive = tilting upward
    var elevationDeg by remember { mutableFloatStateOf(-15f) }

    // Step 1: lock base -> distance; Step 2: lock top -> object height
    var lockedDistanceMeters by remember { mutableStateOf<Float?>(null) }
    var lockedHeightMeters by remember { mutableStateOf<Float?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(hasGravity) {
        if (!hasGravity) return@LaunchedEffect
        val sensorType = if (SensorFlow.hasSensor(context, Sensor.TYPE_GRAVITY)) {
            Sensor.TYPE_GRAVITY
        } else {
            Sensor.TYPE_ACCELEROMETER
        }
        var fy = 9.81f
        var fz = 0f
        var fx = 0f
        var init = false
        val alpha = 0.20f

        SensorFlow.observeSensor(context, sensorType, SensorManager.SENSOR_DELAY_UI)
            .collect { vals ->
                if (!init) {
                    fx = vals[0]; fy = vals[1]; fz = vals[2]
                    init = true
                } else {
                    fx += alpha * (vals[0] - fx)
                    fy += alpha * (vals[1] - fy)
                    fz += alpha * (vals[2] - fz)
                }
                // Rear camera faces -Z; in portrait upright phone gravity is along +Y (~+9.81).
                // When rear camera tilts DOWN toward ground, gravity gains a -Z component (fz < 0).
                // When rear camera tilts UP toward sky/top of building, gravity gains a +Z component (fz > 0).
                val verticalPlane = hypot(fx.toDouble(), fy.toDouble()).coerceAtLeast(0.01)
                val elevRad = atan2(fz.toDouble(), verticalPlane)
                elevationDeg = Math.toDegrees(elevRad).toFloat().coerceIn(-86f, 86f)
            }
    }

    // Real-time live ground distance when aiming below horizontal (-85° .. -1.5°)
    val liveGroundDistance = remember(elevationDeg, cameraHeightMeters) {
        if (elevationDeg < -1.5f) {
            val downRad = (-elevationDeg) * PI / 180.0
            (cameraHeightMeters / tan(downRad)).toFloat().coerceIn(0.2f, 250f)
        } else {
            null
        }
    }

    // Real-time live object height preview once base distance is locked
    val livePreviewHeight = remember(lockedDistanceMeters, elevationDeg, cameraHeightMeters) {
        val dist = lockedDistanceMeters ?: return@remember null
        val elevRad = elevationDeg * PI / 180.0
        (cameraHeightMeters + dist * tan(elevRad)).toFloat().coerceAtLeast(0f)
    }

    fun formatLen(meters: Float?): String {
        if (meters == null) return "--"
        return if (useFeet) {
            String.format(Locale.US, "%.2f ft", meters * 3.28084f)
        } else {
            String.format(Locale.US, "%.2f m", meters)
        }
    }

    ToolScaffold(
        title = lang.tr("Pengukur Jarak & Tinggi", "Camera Rangefinder"),
        subtitle = lang.tr("Trigonometri kamera & sudut graviti", "Camera crosshair & gravity trigonometry"),
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                lockedDistanceMeters = null
                lockedHeightMeters = null
            }) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = lang.tr("Set Semula", "Reset"))
            }
        }
    ) {
        if (!hasGravity) {
            HardwareMissingCard(
                title = lang.tr("Penderia Graviti Tiada", "Gravity Sensor Missing"),
                description = lang.tr(
                    "Pengukur jarak memerlukan penderia graviti/akselerometer untuk mengira sudut kecondongan kamera.",
                    "Rangefinder requires a gravity or accelerometer sensor to measure camera tilt angles."
                )
            )
            return@ToolScaffold
        }

        if (!hasCameraPerm) {
            PermissionRequestCard(
                icon = Icons.Rounded.CameraAlt,
                title = lang.tr("Kebenaran Kamera Diperlukan", "Camera Permission Needed"),
                description = lang.tr(
                    "Benarkan akses kamera untuk membidik tapak dan puncak objek bagi mengira jarak serta ketinggiannya.",
                    "Allow camera access to aim at the base and top of objects to estimate distance and height."
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
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Live Camera Viewfinder with Crosshair & Angle HUD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.05f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.Black)
            ) {
                RangefinderCameraPreview(
                    onError = { cameraError = it }
                )

                // Precision Sight Reticle
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val r = 24.dp.toPx()
                    val accent = if (lockedDistanceMeters == null) Color(0xFF38BDF8) else Color(0xFF10B981)

                    drawCircle(Color.Black.copy(alpha = 0.5f), r + 2.dp.toPx(), Offset(cx, cy), style = Stroke(3.dp.toPx()))
                    drawCircle(accent, r, Offset(cx, cy), style = Stroke(2.2.dp.toPx()))
                    drawLine(accent, Offset(cx - r * 1.8f, cy), Offset(cx - r * 0.45f, cy), 2.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(accent, Offset(cx + r * 0.45f, cy), Offset(cx + r * 1.8f, cy), 2.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(accent, Offset(cx, cy - r * 1.8f), Offset(cx, cy - r * 0.45f), 2.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(accent, Offset(cx, cy + r * 0.45f), Offset(cx, cy + r * 1.8f), 2.dp.toPx(), cap = StrokeCap.Round)
                    drawCircle(Color(0xFFEF4444), 3.dp.toPx(), Offset(cx, cy))
                }

                // Top HUD: Live Tilt Angle & Step Guide
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(
                            text = String.format(Locale.US, "%s %+.1f°", lang.tr("Sudut:", "Tilt:"), elevationDeg),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                        Text(
                            text = when {
                                lockedDistanceMeters == null -> lang.tr("Langkah 1: Bidik TAPAK objek di atas tanah", "Step 1: Aim at object BASE on ground")
                                lockedHeightMeters == null -> lang.tr("Langkah 2: Bidik PUNCAK objek & kunci tinggi", "Step 2: Aim at object TOP & lock height")
                                else -> lang.tr("Selesai! Tekan Reset untuk ukur objek lain", "Done! Tap Reset to measure another object")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }

                // Bottom Live Preview Readout Pill
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(10.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    val hudText = if (lockedDistanceMeters == null) {
                        if (liveGroundDistance != null) {
                            "${lang.tr("Anggaran Jarak:", "Live Distance:")} ${formatLen(liveGroundDistance)}"
                        } else {
                            lang.tr("Condongkan kamera ke bawah menuju tapak", "Tilt phone downward toward ground base")
                        }
                    } else {
                        "${lang.tr("Anggaran Tinggi:", "Live Height:")} ${formatLen(livePreviewHeight)}"
                    }
                    Text(
                        text = hudText,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFACC15),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            if (!cameraError.isNullOrBlank()) {
                Text(cameraError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            // Measured Results Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RangeResultCard(
                    label = lang.tr("Jarak Objek", "Distance"),
                    value = formatLen(lockedDistanceMeters ?: liveGroundDistance),
                    locked = lockedDistanceMeters != null,
                    modifier = Modifier.weight(1f)
                )
                RangeResultCard(
                    label = lang.tr("Tinggi Objek", "Object Height"),
                    value = formatLen(lockedHeightMeters ?: livePreviewHeight),
                    locked = lockedHeightMeters != null,
                    modifier = Modifier.weight(1f)
                )
            }

            // Step Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val dist = liveGroundDistance
                        if (dist != null) {
                            lockedDistanceMeters = dist
                            lockedHeightMeters = null
                        }
                    },
                    enabled = liveGroundDistance != null,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(lang.tr("1. Kunci Tapak", "1. Lock Base"), fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        val h = livePreviewHeight
                        if (h != null) lockedHeightMeters = h
                    },
                    enabled = lockedDistanceMeters != null,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Height, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(lang.tr("2. Kunci Puncak", "2. Lock Top"), fontWeight = FontWeight.Bold)
                }
            }

            // Camera Lens Height Calibration Card
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
                        Text(
                            text = lang.tr("Tinggi Lensa Kamera dari Tanah", "Camera Lens Height from Ground"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = formatLen(cameraHeightMeters),
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = cameraHeightMeters,
                        onValueChange = { cameraHeightMeters = it },
                        valueRange = 0.50f..2.20f
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = lang.tr(
                                "Tip: Tinggi kamera paras mata ≈ tinggi badan anda tolak 12 cm.",
                                "Tip: Eye-level lens height ≈ your standing height minus 12 cm."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { useFeet = !useFeet },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (useFeet) "ft → m" else "m → ft")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RangefinderCameraPreview(onError: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var boundProvider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val provider = future.get()
                boundProvider = provider
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Camera preview error")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { boundProvider?.unbindAll() }
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun RangeResultCard(
    label: String,
    value: String,
    locked: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (locked) 1.5.dp else 1.dp,
            color = if (locked) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant
        )
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
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = if (locked) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
            )
        }
    }
}
