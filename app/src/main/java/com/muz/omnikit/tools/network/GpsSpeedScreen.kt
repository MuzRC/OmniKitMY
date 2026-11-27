package com.muz.omnikit.tools.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.muz.omnikit.core.model.Language
import com.muz.omnikit.core.sensors.SensorFlow
import com.muz.omnikit.ui.components.PermissionRequestCard
import com.muz.omnikit.ui.components.ToolScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.roundToInt

private enum class SpeedUnit(val label: String, val factorFromMs: Float, val maxGauge: Float) {
    KMH("km/j", 3.6f, 180f),
    MPH("mph", 2.23694f, 120f),
    KNOTS("knots", 1.94384f, 100f)
}

@Composable
fun GpsSpeedScreen(
    lang: Language,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val locationManager = remember(context) {
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }
    val hasBarometer = remember(context) {
        SensorFlow.hasSensor(context, Sensor.TYPE_PRESSURE)
    }

    var hasLocPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocPerm = granted
    }

    var speedUnit by remember { mutableStateOf(SpeedUnit.KMH) }
    var hudMirror by remember { mutableStateOf(false) }
    var isTripRunning by remember { mutableStateOf(true) }
    var gpsEnabled by remember {
        mutableStateOf(locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true)
    }

    // Live GNSS telemetry
    var speedMs by remember { mutableFloatStateOf(0f) }
    var maxSpeedMs by remember { mutableFloatStateOf(0f) }
    var tripDistanceMeters by remember { mutableDoubleStateOf(0.0) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var gpsAltitudeM by remember { mutableStateOf<Double?>(null) }
    var accuracyM by remember { mutableStateOf<Float?>(null) }
    var bearingDeg by remember { mutableStateOf<Float?>(null) }
    var satsInView by remember { mutableIntStateOf(0) }
    var satsUsed by remember { mutableIntStateOf(0) }
    var pressureHpa by remember { mutableStateOf<Float?>(null) }

    // Trip elapsed seconds clock
    LaunchedEffect(hasLocPerm, isTripRunning) {
        if (!hasLocPerm || !isTripRunning) return@LaunchedEffect
        while (isActive) {
            delay(1000L)
            elapsedSeconds += 1L
        }
    }

    // Hardware Barometer flow (if present) for atmospheric pressure & barometric altitude
    LaunchedEffect(hasBarometer) {
        if (!hasBarometer) return@LaunchedEffect
        SensorFlow.observeSensor(context, Sensor.TYPE_PRESSURE, SensorManager.SENSOR_DELAY_NORMAL)
            .collect { vals ->
                pressureHpa = vals[0]
            }
    }

    // Native Android LocationManager + GnssStatus Satellite Monitor (100% Offline, No Play Services needed)
    DisposableEffect(locationManager, hasLocPerm, isTripRunning) {
        if (locationManager == null || !hasLocPerm) {
            return@DisposableEffect onDispose { }
        }

        gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        var lastValidLoc: Location? = null

        val locListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                gpsEnabled = true
                latitude = location.latitude
                longitude = location.longitude
                if (location.hasAltitude()) gpsAltitudeM = location.altitude
                if (location.hasAccuracy()) accuracyM = location.accuracy
                if (location.hasBearing()) bearingDeg = location.bearing

                val rawSpeed = if (location.hasSpeed()) location.speed else 0f
                // Filter low-speed stationary GPS drift (< 0.55 m/s ≈ 2 km/h)
                val cleanSpeed = if (rawSpeed < 0.55f) 0f else rawSpeed
                speedMs = cleanSpeed
                if (cleanSpeed > maxSpeedMs) maxSpeedMs = cleanSpeed

                val prev = lastValidLoc
                if (isTripRunning && prev != null && (!location.hasAccuracy() || location.accuracy <= 35f)) {
                    val stepDist = prev.distanceTo(location)
                    // Accumulate realistic movement only (ignore standing noise & GPS teleport jumps)
                    if (stepDist in 1.5f..500f && cleanSpeed >= 0.55f) {
                        tripDistanceMeters += stepDist
                    }
                }
                lastValidLoc = location
            }

            override fun onProviderEnabled(provider: String) {
                if (provider == LocationManager.GPS_PROVIDER) gpsEnabled = true
            }

            override fun onProviderDisabled(provider: String) {
                if (provider == LocationManager.GPS_PROVIDER) gpsEnabled = false
            }

            @Deprecated("Required on older Android APIs")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

        val gnssCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                val total = status.satelliteCount
                var used = 0
                for (i in 0 until total) {
                    if (status.usedInFix(i)) used++
                }
                satsInView = total
                satsUsed = used
            }
        }

        @SuppressLint("MissingPermission")
        fun registerProviders() {
            runCatching {
                if (locationManager.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        800L,
                        0f,
                        locListener,
                        Looper.getMainLooper()
                    )
                    locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { last ->
                        if (latitude == null) {
                            latitude = last.latitude
                            longitude = last.longitude
                            if (last.hasAltitude()) gpsAltitudeM = last.altitude
                            if (last.hasAccuracy()) accuracyM = last.accuracy
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    locationManager.registerGnssStatusCallback(
                        ContextCompat.getMainExecutor(context),
                        gnssCallback
                    )
                } else {
                    @Suppress("DEPRECATION")
                    locationManager.registerGnssStatusCallback(gnssCallback, Handler(Looper.getMainLooper()))
                }
            }
        }

        registerProviders()

        onDispose {
            runCatching { locationManager.removeUpdates(locListener) }
            runCatching { locationManager.unregisterGnssStatusCallback(gnssCallback) }
        }
    }

    fun resetTrip() {
        maxSpeedMs = 0f
        tripDistanceMeters = 0.0
        elapsedSeconds = 0L
    }

    val displaySpeed = speedMs * speedUnit.factorFromMs
    val displayMaxSpeed = maxSpeedMs * speedUnit.factorFromMs
    val avgSpeedMs = if (elapsedSeconds > 0L) (tripDistanceMeters / elapsedSeconds).toFloat() else 0f
    val displayAvgSpeed = avgSpeedMs * speedUnit.factorFromMs
    val displayDist = when (speedUnit) {
        SpeedUnit.KMH -> String.format(Locale.US, "%.2f km", tripDistanceMeters / 1000.0)
        SpeedUnit.MPH -> String.format(Locale.US, "%.2f mi", tripDistanceMeters / 1609.344)
        SpeedUnit.KNOTS -> String.format(Locale.US, "%.2f nm", tripDistanceMeters / 1852.0)
    }

    val baroAltM = pressureHpa?.let {
        SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, it)
    }
    val unitDisplayLabel = if (speedUnit == SpeedUnit.KMH) lang.tr("km/j", "km/h") else speedUnit.label

    ToolScaffold(
        title = lang.tr("GPS Speedometer", "GPS Speed & Altitude"),
        subtitle = lang.tr("GNSS 100% luar talian • Kelajuan & altitud", "100% offline GNSS • Speed & altitude"),
        onBack = onBack,
        actions = {
            if (hasLocPerm) {
                IconButton(onClick = { hudMirror = !hudMirror }) {
                    Icon(
                        Icons.Rounded.Flip,
                        contentDescription = lang.tr("Mod HUD Cermin", "HUD Mirror Mode"),
                        tint = if (hudMirror) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = { resetTrip() }) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = lang.tr("Reset Perjalanan", "Reset Trip"))
                }
            }
        }
    ) {
        if (!hasLocPerm) {
            PermissionRequestCard(
                icon = Icons.Rounded.LocationOn,
                title = lang.tr("Kebenaran Lokasi GPS Diperlukan", "GPS Location Permission Needed"),
                description = lang.tr(
                    "OmniKitMY membaca isyarat satelit GPS/GNSS terus dari cip telefon secara luar talian untuk mengira kelajuan, jarak dan ketinggian.",
                    "OmniKitMY reads direct offline satellite GNSS signals to calculate real-time speed, trip distance, and altitude."
                ),
                buttonLabel = lang.tr("Benarkan Lokasi GPS", "Allow GPS Location"),
                onRequest = { permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
            )
            return@ToolScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!gpsEnabled) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = lang.tr("GPS peranti dimatikan.", "Device GPS is turned off."),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(lang.tr("Hidupkan", "Enable"))
                        }
                    }
                }
            }

            // Unit Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpeedUnit.entries.forEach { u ->
                    val lbl = if (u == SpeedUnit.KMH) lang.tr("km/j", "km/h") else u.label
                    FilterChip(
                        selected = speedUnit == u,
                        onClick = { speedUnit = u },
                        label = { Text(lbl, maxLines = 1) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Speedometer Arc Gauge (supports HUD windshield mirror flip!)
            SpeedometerArcCanvas(
                speed = displaySpeed,
                maxGauge = speedUnit.maxGauge,
                unitLabel = unitDisplayLabel,
                mirrored = hudMirror,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            )

            // Satellite & Accuracy Status Pill
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.GpsFixed,
                        contentDescription = null,
                        tint = if (satsUsed >= 4) Color(0xFF10B981) else Color(0xFFF59E0B),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = lang.tr(
                            "Satelit: $satsUsed/$satsInView  •  Ketepatan: ${accuracyM?.let { "±${it.roundToInt()} m" } ?: "Mencari..."}",
                            "Satellites: $satsUsed/$satsInView  •  Accuracy: ${accuracyM?.let { "±${it.roundToInt()} m" } ?: "Searching..."}"
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Trip Telemetry Grid (Max Speed, Avg Speed, Distance, Duration)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GpsMetricCard(lang.tr("Maks", "Max Speed"), String.format(Locale.US, "%.1f %s", displayMaxSpeed, unitDisplayLabel), Modifier.weight(1f), highlight = true)
                GpsMetricCard(lang.tr("Purata", "Avg Speed"), String.format(Locale.US, "%.1f %s", displayAvgSpeed, unitDisplayLabel), Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GpsMetricCard(lang.tr("Jarak", "Distance"), displayDist, Modifier.weight(1f))
                GpsMetricCard(lang.tr("Tempoh", "Trip Time"), formatDurationHms(elapsedSeconds), Modifier.weight(1f))
            }

            // Trip Pause/Resume Control
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { isTripRunning = !isTripRunning },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(if (isTripRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isTripRunning) lang.tr("Jeda Rekod", "Pause Trip") else lang.tr("Sambung Rekod", "Resume Trip"))
                }
                OutlinedButton(
                    onClick = { resetTrip() },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(lang.tr("Reset", "Reset"))
                }
            }

            // Altimeter & Barometer Card
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
                        text = lang.tr("Altimeter & Tekanan Udara", "Altimeter & Barometer"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    GpsInfoRow(
                        lang.tr("Ketinggian GPS", "GPS Altitude"),
                        gpsAltitudeM?.let { String.format(Locale.US, "%.1f m (%.0f ft)", it, it * 3.28084) } ?: "--"
                    )
                    GpsInfoRow(
                        lang.tr("Arah Pergerakan (Bearing)", "Course Bearing"),
                        bearingDeg?.let { String.format(Locale.US, "%.0f°", it) } ?: "--"
                    )
                    if (hasBarometer) {
                        GpsInfoRow(
                            lang.tr("Tekanan Barometer", "Barometric Pressure"),
                            pressureHpa?.let { String.format(Locale.US, "%.2f hPa", it) } ?: "--"
                        )
                        GpsInfoRow(
                            lang.tr("Altitud Barometer", "Barometric Altitude"),
                            baroAltM?.let { String.format(Locale.US, "%.1f m", it) } ?: "--"
                        )
                    }
                }
            }

            // Coordinates Card with Copy & Maps Actions
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = lang.tr("Koordinat Semasa", "Current Coordinates"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val lat = latitude
                    val lon = longitude
                    val coordStr = if (lat != null && lon != null) {
                        String.format(Locale.US, "%.6f, %.6f", lat, lon)
                    } else {
                        lang.tr("Menunggu isyarat satelit (elok di kawasan terbuka)...", "Acquiring satellite lock (best outdoors)...")
                    }
                    Text(
                        text = coordStr,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    if (lat != null && lon != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("GPS", coordStr))
                                    Toast.makeText(context, lang.tr("Koordinat disalin!", "Coordinates copied!"), Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(lang.tr("Salin", "Copy"))
                            }
                            OutlinedButton(
                                onClick = {
                                    val geo = Uri.parse("geo:$lat,$lon?q=$lat,$lon(OmniKitMY)")
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, geo)) }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(lang.tr("Peta", "Maps"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedometerArcCanvas(
    speed: Float,
    maxGauge: Float,
    unitLabel: String,
    mirrored: Boolean,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val onBg = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val frac = (speed / maxGauge).coerceIn(0f, 1f)
    val gaugeColor = when {
        frac > 0.72f -> Color(0xFFEF4444)
        frac > 0.45f -> Color(0xFFF59E0B)
        else -> primaryColor
    }

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = if (mirrored) -1f else 1f
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val d = minOf(size.width * 0.72f, size.height * 1.15f)
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f + 16.dp.toPx())
            val arcSize = Size(d, d)
            val stroke = 16.dp.toPx()

            drawArc(
                color = trackColor,
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = gaugeColor,
                startAngle = 140f,
                sweepAngle = 260f * frac,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 20.dp)
        ) {
            Text(
                text = String.format(Locale.US, "%.0f", speed),
                fontSize = 58.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = onBg
            )
            Text(
                text = unitLabel.uppercase(Locale.US),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = muted
            )
        }
    }
}

@Composable
private fun GpsMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun GpsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatDurationHms(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}
