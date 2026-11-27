package com.muz.omnikit.tools.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import com.muz.omnikit.ui.components.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class AudioTab { CLEANER, GENERATOR }
private enum class CleanMode(val durationSec: Int) { WATER_EJECT(30), DUST_CLEAR(25), DEEP_CYCLE(40) }
private enum class WaveType { SINE, SQUARE, TRIANGLE, SAWTOOTH }
private enum class StereoChannel { LEFT, BOTH, RIGHT }

@Composable
fun SpeakerAudioScreen(
    lang: Language,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(AudioTab.CLEANER) }

    // === Speaker Cleaner States ===
    var cleanMode by remember { mutableStateOf(CleanMode.WATER_EJECT) }
    var isCleaning by remember { mutableStateOf(false) }
    var cleanerVibrate by remember { mutableStateOf(true) }
    var cleanProgress by remember { mutableFloatStateOf(0f) }
    var cleanLiveFreq by remember { mutableIntStateOf(165) }
    var cleanSecondsLeft by remember { mutableIntStateOf(cleanMode.durationSec) }

    // === Tone Generator States ===
    var isTonePlaying by remember { mutableStateOf(false) }
    var toneFreqHz by remember { mutableFloatStateOf(440f) }
    var waveType by remember { mutableStateOf(WaveType.SINE) }
    var stereoChannel by remember { mutableStateOf(StereoChannel.BOTH) }
    var toneGain by remember { mutableFloatStateOf(0.55f) }

    // Live atomic parameters for click-free streaming PCM tone generator
    val freqRef = remember { AtomicReference(440.0) }
    val waveRef = remember { AtomicReference(WaveType.SINE) }
    val chanRef = remember { AtomicReference(StereoChannel.BOTH) }
    val gainRef = remember { AtomicReference(0.55) }

    freqRef.set(toneFreqHz.toDouble())
    waveRef.set(waveType)
    chanRef.set(stereoChannel)
    gainRef.set(toneGain.toDouble())

    // Speaker Cleaner Real PCM Sweep Engine
    LaunchedEffect(isCleaning, cleanMode, cleanerVibrate) {
        if (!isCleaning) {
            cleanProgress = 0f
            cleanSecondsLeft = cleanMode.durationSec
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            val sampleRate = 44100
            val track = buildStereoAudioTrack(sampleRate) ?: run {
                withContext(Dispatchers.Main) { isCleaning = false }
                return@withContext
            }

            val totalSec = cleanMode.durationSec
            val totalFrames = sampleRate * totalSec
            val chunkFrames = 2048
            val stereoBuf = ShortArray(chunkFrames * 2)
            var frameIndex = 0
            var phase = 0.0
            var lastUiMs = 0L
            var lastVibMs = 0L

            try {
                track.play()
                while (isActive && frameIndex < totalFrames) {
                    val framesThisChunk = minOf(chunkFrames, totalFrames - frameIndex)
                    val elapsedSec = frameIndex.toDouble() / sampleRate

                    // Compute physical water/dust ejection frequency profile
                    val freq = when (cleanMode) {
                        CleanMode.WATER_EJECT -> {
                            // Classic 165Hz-245Hz resonant air-pushing sweep with 2Hz pressure pulse
                            val cycle = (elapsedSec % 4.0) / 4.0
                            165.0 + 80.0 * sin(cycle * PI)
                        }
                        CleanMode.DUST_CLEAR -> {
                            // Higher-energy 180Hz-480Hz sweep to break static dust particles
                            val cycle = (elapsedSec % 2.5) / 2.5
                            180.0 + 300.0 * cycle
                        }
                        CleanMode.DEEP_CYCLE -> {
                            // Multi-band stepped resonant frequencies
                            val step = ((elapsedSec / 2.0).toInt()) % 5
                            when (step) {
                                0 -> 145.0
                                1 -> 165.0
                                2 -> 210.0
                                3 -> 285.0
                                else -> 360.0
                            }
                        }
                    }

                    val useDustHarmonic = cleanMode == CleanMode.DUST_CLEAR
                    val pulseAmp = if (cleanMode == CleanMode.WATER_EJECT) {
                        0.72 + 0.28 * sin(2.0 * PI * 2.5 * elapsedSec)
                    } else {
                        0.92
                    }

                    for (i in 0 until framesThisChunk) {
                        val globalFrame = frameIndex + i
                        // Smooth 25ms fade-in & fade-out envelope to prevent speaker clicks
                        val fadeSamples = (sampleRate * 0.025).toInt()
                        val env = when {
                            globalFrame < fadeSamples -> globalFrame.toDouble() / fadeSamples
                            globalFrame > totalFrames - fadeSamples -> (totalFrames - globalFrame).toDouble() / fadeSamples
                            else -> 1.0
                        }

                        phase += 2.0 * PI * freq / sampleRate
                        if (phase >= 2.0 * PI) phase -= 2.0 * PI

                        val sampleVal = if (useDustHarmonic) {
                            // Blended sine + subtle 2nd & 3rd harmonics for brisk diaphragm excursion
                            (0.75 * sin(phase) + 0.18 * sin(2.0 * phase) + 0.07 * sin(3.0 * phase))
                        } else {
                            sin(phase)
                        }

                        val pcm = (sampleVal * pulseAmp * env * 29500.0)
                            .toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            .toShort()

                        stereoBuf[i * 2] = pcm
                        stereoBuf[i * 2 + 1] = pcm
                    }

                    track.write(stereoBuf, 0, framesThisChunk * 2)
                    frameIndex += framesThisChunk

                    val now = System.currentTimeMillis()
                    if (cleanerVibrate && now - lastVibMs >= 950L) {
                        lastVibMs = now
                        triggerCleanPulseVibrate(context)
                    }

                    if (now - lastUiMs >= 120L || frameIndex >= totalFrames) {
                        lastUiMs = now
                        val prog = (frameIndex.toFloat() / totalFrames).coerceIn(0f, 1f)
                        val left = ((totalFrames - frameIndex) / sampleRate).coerceAtLeast(0)
                        withContext(Dispatchers.Main) {
                            cleanProgress = prog
                            cleanLiveFreq = freq.roundToInt()
                            cleanSecondsLeft = left
                        }
                    }
                }
            } finally {
                runCatching {
                    track.stop()
                    track.release()
                }
                withContext(Dispatchers.Main) {
                    isCleaning = false
                }
            }
        }
    }

    // Tone Generator Real Continuous Phase-Coherent PCM Engine
    LaunchedEffect(isTonePlaying) {
        if (!isTonePlaying) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            val sampleRate = 44100
            val track = buildStereoAudioTrack(sampleRate) ?: run {
                withContext(Dispatchers.Main) { isTonePlaying = false }
                return@withContext
            }

            val chunkFrames = 1024
            val stereoBuf = ShortArray(chunkFrames * 2)
            var phase = 0.0
            var currentGain = 0.0

            try {
                track.play()
                while (isActive) {
                    val targetFreq = freqRef.get().coerceIn(20.0, 20000.0)
                    val targetGain = gainRef.get().coerceIn(0.0, 1.0)
                    val wave = waveRef.get()
                    val chan = chanRef.get()

                    val leftMult = if (chan == StereoChannel.RIGHT) 0.0 else 1.0
                    val rightMult = if (chan == StereoChannel.LEFT) 0.0 else 1.0
                    val phaseStep = 2.0 * PI * targetFreq / sampleRate

                    for (i in 0 until chunkFrames) {
                        // Per-sample gain smoothing eliminates slider zipper noise
                        currentGain += (targetGain - currentGain) * 0.004
                        phase += phaseStep
                        if (phase >= 2.0 * PI) phase -= 2.0 * PI

                        val t = phase / (2.0 * PI) // 0.0 .. <1.0
                        val rawSample = when (wave) {
                            WaveType.SINE -> sin(phase)
                            WaveType.SQUARE -> if (t < 0.5) 0.72 else -0.72
                            WaveType.TRIANGLE -> 1.0 - 4.0 * abs(t - 0.5)
                            WaveType.SAWTOOTH -> 2.0 * t - 1.0
                        }

                        val scaled = rawSample * currentGain * 28000.0
                        stereoBuf[i * 2] = (scaled * leftMult).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        stereoBuf[i * 2 + 1] = (scaled * rightMult).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                    track.write(stereoBuf, 0, stereoBuf.size)
                }
            } finally {
                runCatching {
                    track.stop()
                    track.release()
                }
            }
        }
    }

    ToolScaffold(
        title = lang.tr("Audio & Pencuci Speaker", "Tone & Speaker Cleaner"),
        subtitle = lang.tr("Penjana gelombang PCM & halau air/habuk", "PCM wave generator & water eject"),
        onBack = {
            isCleaning = false
            isTonePlaying = false
            onBack()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Mode Switch Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeTab == AudioTab.CLEANER,
                    onClick = {
                        isTonePlaying = false
                        activeTab = AudioTab.CLEANER
                    },
                    leadingIcon = { Icon(Icons.Rounded.WaterDrop, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(lang.tr("Pencuci Speaker", "Speaker Cleaner")) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = activeTab == AudioTab.GENERATOR,
                    onClick = {
                        isCleaning = false
                        activeTab = AudioTab.GENERATOR
                    },
                    leadingIcon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(lang.tr("Penjana Nada", "Tone Generator")) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (activeTab == AudioTab.CLEANER) {
                // === TAB 1: SPEAKER CLEANER ===
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (cleanMode == CleanMode.DUST_CLEAR) Icons.Rounded.CleaningServices else Icons.Rounded.WaterDrop,
                            contentDescription = null,
                            tint = if (isCleaning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (isCleaning) "$cleanLiveFreq Hz" else "${cleanMode.durationSec}s",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isCleaning) {
                                lang.tr("Baki masa: ${cleanSecondsLeft}s • Sedang membersihkan...", "Time left: ${cleanSecondsLeft}s • Cleaning active...")
                            } else {
                                lang.tr("Sedia — Naikkan kelantangan media & halakan speaker ke bawah", "Ready — Raise media volume & face speaker downward")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LinearProgressIndicator(
                            progress = { cleanProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }

                // Cleaning Mode Picker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        CleanMode.WATER_EJECT to lang.tr("Halau Air", "Water Eject"),
                        CleanMode.DUST_CLEAR to lang.tr("Halau Habuk", "Dust Clear"),
                        CleanMode.DEEP_CYCLE to lang.tr("Kitaran Dalam", "Deep Cycle")
                    ).forEach { (m, lbl) ->
                        FilterChip(
                            selected = cleanMode == m,
                            onClick = {
                                if (!isCleaning) {
                                    cleanMode = m
                                    cleanSecondsLeft = m.durationSec
                                }
                            },
                            label = { Text(lbl, maxLines = 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Vibration Assist Toggle
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.Vibration, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(
                                    text = lang.tr("Bantuan Getaran Berirama", "Rhythmic Vibration Assist"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = lang.tr("Bantu longgarkan titisan air pada jaring speaker", "Helps dislodge trapped water droplets"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(checked = cleanerVibrate, onCheckedChange = { cleanerVibrate = it })
                    }
                }

                Button(
                    onClick = { isCleaning = !isCleaning },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = if (isCleaning) {
                        ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White)
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Icon(if (isCleaning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCleaning) lang.tr("Hentikan Pencucian", "Stop Cleaning") else lang.tr("Mula Cuci Speaker", "Start Speaker Cleaning"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // === TAB 2: PCM TONE GENERATOR ===
                val noteName = remember(toneFreqHz) { frequencyToMusicalNote(toneFreqHz.toDouble()) }
                // Logarithmic slider position 0..1 mapped to 20 Hz .. 20,000 Hz
                val logSliderPos = remember(toneFreqHz) {
                    ((ln(toneFreqHz.toDouble() / 20.0) / ln(1000.0))).toFloat().coerceIn(0f, 1f)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (toneFreqHz >= 1000f) {
                                String.format(Locale.US, "%.2f kHz", toneFreqHz / 1000f)
                            } else {
                                String.format(Locale.US, "%.1f Hz", toneFreqHz)
                            },
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = lang.tr("Not Muzik Terdekat: $noteName", "Nearest Musical Note: $noteName"),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        WaveformPreviewCanvas(
                            waveType = waveType,
                            active = isTonePlaying,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                        )
                    }
                }

                // Frequency Slider + Fine Nudge Controls
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(lang.tr("Frekuensi (20 Hz – 20 kHz)", "Frequency (20 Hz – 20 kHz)"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Text("${toneFreqHz.roundToInt()} Hz", style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = logSliderPos,
                            onValueChange = { pos ->
                                toneFreqHz = (20.0 * 1000.0.pow(pos.toDouble())).toFloat().coerceIn(20f, 20000f)
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { toneFreqHz = (toneFreqHz - 10f).coerceAtLeast(20f) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("-10") }
                            OutlinedButton(
                                onClick = { toneFreqHz = (toneFreqHz - 1f).coerceAtLeast(20f) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Icon(Icons.Rounded.Remove, contentDescription = "-1", modifier = Modifier.size(16.dp)) }
                            OutlinedButton(
                                onClick = { toneFreqHz = (toneFreqHz + 1f).coerceAtMost(20000f) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Icon(Icons.Rounded.Add, contentDescription = "+1", modifier = Modifier.size(16.dp)) }
                            OutlinedButton(
                                onClick = { toneFreqHz = (toneFreqHz + 10f).coerceAtMost(20000f) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("+10") }
                        }
                        // Quick Presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(100f to "100", 165f to "165", 440f to "440", 1000f to "1k", 8000f to "8k").forEach { (hz, lbl) ->
                                OutlinedButton(
                                    onClick = { toneFreqHz = hz },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Text(lbl, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }

                // Waveform Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        WaveType.SINE to lang.tr("Sinus", "Sine"),
                        WaveType.SQUARE to lang.tr("Petak", "Square"),
                        WaveType.TRIANGLE to lang.tr("Segitiga", "Tri"),
                        WaveType.SAWTOOTH to lang.tr("Gergaji", "Saw")
                    ).forEach { (w, lbl) ->
                        FilterChip(
                            selected = waveType == w,
                            onClick = { waveType = w },
                            label = { Text(lbl, maxLines = 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Stereo Channel & Output Level
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(lang.tr("Saluran Stereo (Uji Kiri / Kanan)", "Stereo Output Channel"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                StereoChannel.LEFT to lang.tr("Kiri (L)", "Left"),
                                StereoChannel.BOTH to "Stereo",
                                StereoChannel.RIGHT to lang.tr("Kanan (R)", "Right")
                            ).forEach { (ch, lbl) ->
                                FilterChip(
                                    selected = stereoChannel == ch,
                                    onClick = { stereoChannel = ch },
                                    label = { Text(lbl, maxLines = 1) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(lang.tr("Paras Output", "Output Gain"), style = MaterialTheme.typography.labelMedium)
                            Text("${(toneGain * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(value = toneGain, onValueChange = { toneGain = it }, valueRange = 0.05f..1.0f)
                    }
                }

                Button(
                    onClick = { isTonePlaying = !isTonePlaying },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = if (isTonePlaying) {
                        ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White)
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Icon(if (isTonePlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isTonePlaying) lang.tr("Hentikan Nada", "Stop Tone") else lang.tr("Mainkan Nada", "Play Tone"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun buildStereoAudioTrack(sampleRate: Int): AudioTrack? {
    val minBuf = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_STEREO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(4096)

    return runCatching {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        if (track.state == AudioTrack.STATE_INITIALIZED) track else {
            track.release()
            null
        }
    }.getOrNull()
}

@Composable
private fun WaveformPreviewCanvas(
    waveType: WaveType,
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val amp = h * 0.36f
        drawLine(grid, Offset(0f, midY), Offset(w, midY), 1.dp.toPx())

        val cycles = 3.0
        val steps = 160
        val path = Path()
        for (i in 0..steps) {
            val x = (i.toFloat() / steps) * w
            val phaseCycles = (i.toDouble() / steps) * cycles
            val frac = phaseCycles - phaseCycles.toInt()
            val yNorm = when (waveType) {
                WaveType.SINE -> sin(2.0 * PI * phaseCycles)
                WaveType.SQUARE -> if (frac < 0.5) 0.85 else -0.85
                WaveType.TRIANGLE -> 1.0 - 4.0 * abs(frac - 0.5)
                WaveType.SAWTOOTH -> 2.0 * frac - 1.0
            }
            val y = (midY - yNorm * amp).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = if (active) color else color.copy(alpha = 0.45f),
            style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

private fun frequencyToMusicalNote(freqHz: Double): String {
    if (freqHz < 16.0) return "--"
    val notes = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val midi = (69.0 + 12.0 * (ln(freqHz / 440.0) / ln(2.0))).roundToInt()
    val name = notes[((midi % 12) + 12) % 12]
    val octave = (midi / 12) - 1
    return "$name$octave"
}

private fun triggerCleanPulseVibrate(context: Context) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(45L, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
