package com.muz.omnikit.tools.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.muz.omnikit.core.model.Language
import com.muz.omnikit.ui.components.PermissionRequestCard
import com.muz.omnikit.ui.components.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.log10
import kotlin.math.sqrt

@Composable
fun SoundMeterScreen(
    lang: Language,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
    }

    var isMeasuring by remember { mutableStateOf(false) }
    var currentDb by remember { mutableFloatStateOf(0f) }
    var minDb by remember { mutableStateOf<Float?>(null) }
    var maxDb by remember { mutableStateOf<Float?>(null) }
    var sumDb by remember { mutableFloatStateOf(0f) }
    var sampleCount by remember { mutableIntStateOf(0) }
    var calibrationOffset by remember { mutableFloatStateOf(0f) }
    var initError by remember { mutableStateOf<String?>(null) }
    val dbHistory = remember { mutableStateListOf<Float>() }

    val calOffsetRef = remember { AtomicReference(0f) }
    calOffsetRef.set(calibrationOffset)

    fun resetStats() {
        minDb = null
        maxDb = null
        sumDb = 0f
        sampleCount = 0
        dbHistory.clear()
        if (!isMeasuring) currentDb = 0f
    }

    // Real-time PCM AudioRecord Engine on Dispatchers.IO with throttled UI updates (~95ms)
    LaunchedEffect(hasMicPermission, isMeasuring) {
        if (!hasMicPermission || !isMeasuring) return@LaunchedEffect
        initError = null

        withContext(Dispatchers.IO) {
            val sampleRate = 44100
            val minBufBytes = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBufBytes <= 0) {
                withContext(Dispatchers.Main) {
                    initError = lang.tr("Mikrofon tidak menyokong rakaman PCM.", "Microphone does not support PCM capture.")
                    isMeasuring = false
                }
                return@withContext
            }

            val recorder = createBestAudioRecord(sampleRate, minBufBytes * 2)
            if (recorder == null) {
                withContext(Dispatchers.Main) {
                    initError = lang.tr("Gagal membuka mikrofon. Pastikan tiada aplikasi lain menggunakannya.", "Failed to open microphone. Ensure no other app is using it.")
                    isMeasuring = false
                }
                return@withContext
            }

            val readBuffer = ShortArray((minBufBytes / 2).coerceAtLeast(1024))
            var smoothDb = 35f
            var initialized = false
            var lastUiMs = 0L

            try {
                recorder.startRecording()
                while (isActive) {
                    val readCount = recorder.read(readBuffer, 0, readBuffer.size)
                    if (readCount <= 0) continue

                    var sumSquares = 0.0
                    for (i in 0 until readCount) {
                        val normalized = readBuffer[i] / 32768.0
                        sumSquares += normalized * normalized
                    }
                    val rms = sqrt(sumSquares / readCount).coerceAtLeast(1e-6)
                    val dbfs = 20.0 * log10(rms)
                    val rawSpl = (dbfs + 94.0 + calOffsetRef.get().toDouble()).toFloat().coerceIn(20f, 130f)

                    if (!initialized) {
                        smoothDb = rawSpl
                        initialized = true
                    } else {
                        smoothDb += 0.28f * (rawSpl - smoothDb)
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastUiMs >= 95L) {
                        lastUiMs = now
                        val reported = smoothDb
                        withContext(Dispatchers.Main) {
                            currentDb = reported
                            minDb = minDb?.let { minOf(it, reported) } ?: reported
                            maxDb = maxDb?.let { maxOf(it, reported) } ?: reported
                            sumDb += reported
                            sampleCount += 1
                            dbHistory.add(reported)
                            if (dbHistory.size > 60) dbHistory.removeAt(0)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    initError = e.localizedMessage
                    isMeasuring = false
                }
            } finally {
                runCatching {
                    if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        recorder.stop()
                    }
                    recorder.release()
                }
            }
        }
    }

    val avgDb = if (sampleCount > 0) sumDb / sampleCount else 0f
    val levelInfo = if (isMeasuring) {
        describeNoiseLevel(currentDb, lang)
    } else {
        NoiseInfo(lang.tr("Sedia Mengukur (Mikrofon Mati)", "Standby (Microphone Off)"), MaterialTheme.colorScheme.onSurfaceVariant)
    }

    ToolScaffold(
        title = lang.tr("Pengukur Bunyi (dB)", "Sound Meter (dB)"),
        subtitle = lang.tr("Tahap tekanan bunyi masa nyata", "Real-time decibel SPL monitor"),
        onBack = {
            isMeasuring = false
            onBack()
        },
        actions = {
            if (hasMicPermission) {
                IconButton(onClick = { resetStats() }) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = lang.tr("Set Semula", "Reset"))
                }
            }
        }
    ) {
        if (!hasMicPermission) {
            PermissionRequestCard(
                icon = Icons.Rounded.Mic,
                title = lang.tr("Kebenaran Mikrofon Diperlukan", "Microphone Permission Needed"),
                description = lang.tr(
                    "OmniKitMY memerlukan akses mikrofon untuk mengukur tahap bunyi (dB) secara langsung. Tiada audio disimpan atau dihantar keluar.",
                    "OmniKitMY needs microphone access to measure live decibel levels. No audio is ever saved or transmitted."
                ),
                buttonLabel = lang.tr("Benarkan Mikrofon", "Allow Microphone"),
                onRequest = { permLauncher.launch(Manifest.permission.RECORD_AUDIO) }
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
            if (!initError.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = initError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Main Arc Gauge & Live dB Readout
            DecibelArcGauge(
                db = if (isMeasuring) currentDb else 0f,
                active = isMeasuring,
                accentColor = if (isMeasuring) levelInfo.color else MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(185.dp)
            )

            // Noise Classification Pill
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = levelInfo.color.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, levelInfo.color.copy(alpha = 0.55f))
            ) {
                Text(
                    text = levelInfo.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = levelInfo.color,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                )
            }

            // Min / Avg / Max Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DbStatCard("Min", minDb?.let { String.format(Locale.US, "%.1f dB", it) } ?: "--", Color(0xFF10B981), Modifier.weight(1f))
                DbStatCard(lang.tr("Purata", "Avg"), if (sampleCount > 0) String.format(Locale.US, "%.1f dB", avgDb) else "--", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                DbStatCard(lang.tr("Puncak", "Peak"), maxDb?.let { String.format(Locale.US, "%.1f dB", it) } ?: "--", Color(0xFFEF4444), Modifier.weight(1f))
            }

            // Live Decibel Flow Graph with 85 dB WHO Danger Line
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
                            text = lang.tr("Graf Aliran Desibel", "Live Decibel Flow"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = lang.tr("Garis Merah: Had 85 dB (WHO)", "Red Line: 85 dB Limit (WHO)"),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFEF4444)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    DecibelGraphCanvas(
                        history = dbHistory,
                        lineColor = if (isMeasuring) levelInfo.color else MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(92.dp)
                    )
                }
            }

            // Start / Stop Button
            Button(
                onClick = {
                    if (!isMeasuring) {
                        resetStats()
                        isMeasuring = true
                    } else {
                        isMeasuring = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = if (isMeasuring) {
                    ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White)
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Icon(
                    imageVector = if (isMeasuring) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isMeasuring) lang.tr("Hentikan Ukuran", "Stop Measuring") else lang.tr("Mula Mengukur Bunyi", "Start Measuring"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            // Manual Microphone Calibration Offset
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
                            text = lang.tr("Pelaras Kalibrasi Mikrofon", "Microphone Calibration Offset"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = String.format(Locale.US, "%+.0f dB", calibrationOffset),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = calibrationOffset,
                        onValueChange = { calibrationOffset = it },
                        valueRange = -20f..20f
                    )
                    Text(
                        text = lang.tr(
                            "Sensitiviti mikrofon berbeza mengikut jenama telefon. Laraskan jika membandingkan dengan meter SPL rujukan.",
                            "Mic sensitivity varies across phone models. Adjust offset if matching a reference SPL meter."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // WHO Reference Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = lang.tr("Skala Rujukan Bunyi (WHO)", "Noise Safety Scale (WHO)"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    NoiseRefRow("< 40 dB", lang.tr("Bilik tidur senyap / Perpustakaan", "Quiet bedroom / Library"))
                    NoiseRefRow("40 – 60 dB", lang.tr("Perbualan biasa / Pejabat tenang", "Normal conversation / Quiet office"))
                    NoiseRefRow("60 – 75 dB", lang.tr("Restoran sibuk / Pembersih vakum", "Busy restaurant / Vacuum cleaner"))
                    NoiseRefRow("75 – 85 dB", lang.tr("Lalulintas padat / Mesin pengisar", "Heavy traffic / Workshop fan"))
                    NoiseRefRow("> 85 dB", lang.tr("Bahaya jika terdedah lama (Pakai pelindung)", "Hearing risk on long exposure (Wear protection)"))
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun createBestAudioRecord(sampleRate: Int, bufferBytes: Int): AudioRecord? {
    val sources = listOf(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        MediaRecorder.AudioSource.MIC
    )
    for (source in sources) {
        val rec = runCatching {
            AudioRecord(
                source,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes
            )
        }.getOrNull()
        if (rec != null && rec.state == AudioRecord.STATE_INITIALIZED) {
            return rec
        }
        runCatching { rec?.release() }
    }
    return null
}

@Composable
private fun DecibelArcGauge(
    db: Float,
    active: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val onBg = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val d = minOf(size.width * 0.68f, size.height * 1.15f)
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f + 12.dp.toPx())
            val arcSize = Size(d, d)
            val strokeW = 15.dp.toPx()

            drawArc(
                color = trackColor,
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            if (active) {
                val frac = ((db - 20f) / 100f).coerceIn(0f, 1f)
                drawArc(
                    color = accentColor,
                    startAngle = 140f,
                    sweepAngle = 260f * frac,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 18.dp)
        ) {
            Text(
                text = if (active) String.format(Locale.US, "%.1f", db) else "--.-",
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = onBg
            )
            Text(
                text = "dB (SPL)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = muted
            )
        }
    }
}

@Composable
private fun DecibelGraphCanvas(
    history: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f) }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val minDb = 20f
        val maxDb = 120f

        // 85 dB WHO danger threshold dashed line
        val dangerY = h - ((85f - minDb) / (maxDb - minDb) * h)
        drawLine(
            color = Color(0xFFEF4444).copy(alpha = 0.65f),
            start = Offset(0f, dangerY),
            end = Offset(w, dangerY),
            strokeWidth = 1.4.dp.toPx(),
            pathEffect = dash
        )
        drawLine(gridColor, Offset(0f, h - 1f), Offset(w, h - 1f), 1.dp.toPx())

        if (history.size < 2) return@Canvas
        val step = w / (history.size - 1)
        val path = Path()
        history.forEachIndexed { idx, v ->
            val x = idx * step
            val y = h - ((v - minDb) / (maxDb - minDb) * (h - 6.dp.toPx()) + 3.dp.toPx()).coerceIn(2f, h - 2f)
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun DbStatCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
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
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}

@Composable
private fun NoiseRefRow(range: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = range,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

private data class NoiseInfo(val label: String, val color: Color)

private fun describeNoiseLevel(db: Float, lang: Language): NoiseInfo = when {
    db < 40f -> NoiseInfo(lang.tr("Sangat Tenang", "Very Quiet"), Color(0xFF10B981))
    db < 60f -> NoiseInfo(lang.tr("Sederhana (Perbualan)", "Moderate (Conversation)"), Color(0xFF38BDF8))
    db < 75f -> NoiseInfo(lang.tr("Bising (Pejabat / Restoran)", "Loud (Busy Area)"), Color(0xFFFBBF24))
    db < 85f -> NoiseInfo(lang.tr("Sangat Bising (Lalulintas)", "Very Loud (Traffic)"), Color(0xFFF97316))
    else -> NoiseInfo(lang.tr("Bahaya Pendengaran (>85 dB)", "Hearing Risk (>85 dB)"), Color(0xFFEF4444))
}
