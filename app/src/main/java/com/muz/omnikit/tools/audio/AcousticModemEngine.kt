package com.muz.omnikit.tools.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class AcousticBand(val baseHz: Double, val stepHz: Double) {
    AUDIBLE(1850.0, 135.0),      // 1,850 Hz .. 4,145 Hz (universal across 100% Android devices)
    ULTRASONIC(16200.0, 145.0)   // 16,200 Hz .. 18,665 Hz (near-silent for adult ears)
}

enum class AcousticSpeed(val symbolMs: Int, val preambleMs: Int) {
    ROBUST(64, 86),
    FAST(44, 72)
}

enum class PacketKind(val code: Byte) {
    TEXT(0x54),      // 'T' plain text / link / coordinates
    FILE_LINK(0x46); // 'F' compact local LAN SuaraDrop file/APK link

    companion object {
        fun fromCode(b: Byte): PacketKind? = entries.firstOrNull { it.code == b }
    }
}

data class AcousticPacket(
    val kind: PacketKind,
    val payload: String,
    val timestampMs: Long = System.currentTimeMillis()
)

enum class ReceiverPhase {
    LISTENING,
    LOCKED_RECEIVING,
    CRC_ERROR
}

data class ReceiverTelemetry(
    val phase: ReceiverPhase,
    val spectrum: FloatArray,
    val dominantTone: Int,
    val receivedBytesCount: Int
)

object AcousticModemEngine {

    const val SAMPLE_RATE = 44100
    const val TONE_COUNT = 18
    const val MAX_PAYLOAD_BYTES = 240

    private const val TONE_REPEAT = 16
    private const val TONE_SYNC = 17
    private const val WIN_SIZE = 1024
    private const val HOP_SIZE = 352

    private val HANN_WINDOW: FloatArray = FloatArray(WIN_SIZE) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (WIN_SIZE - 1)))).toFloat()
    }

    fun frequenciesFor(band: AcousticBand): DoubleArray =
        DoubleArray(TONE_COUNT) { i -> band.baseHz + i * band.stepHz }

    fun crc16Ccitt(data: ByteArray, length: Int = data.size): Int {
        var crc = 0xFFFF
        for (i in 0 until length) {
            crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) {
                    ((crc shl 1) xor 0x1021) and 0xFFFF
                } else {
                    (crc shl 1) and 0xFFFF
                }
            }
        }
        return crc
    }

    /**
     * Encodes payload into an 18-tone sequence where NO two consecutive tones are ever equal.
     * Tone 0..15 = data nibbles; Tone 16 = repeat previous data nibble; Tone 17 = preamble / EOF.
     */
    fun encodeToTones(kind: PacketKind, text: String): IntArray {
        val rawUtf8 = text.toByteArray(StandardCharsets.UTF_8)
        val trimmedLen = rawUtf8.size.coerceAtMost(MAX_PAYLOAD_BYTES)
        val body = ByteArray(2 + trimmedLen)
        body[0] = kind.code
        body[1] = trimmedLen.toByte()
        System.arraycopy(rawUtf8, 0, body, 2, trimmedLen)

        val crc = crc16Ccitt(body)
        val frame = ByteArray(body.size + 2)
        System.arraycopy(body, 0, frame, 0, body.size)
        frame[body.size] = ((crc ushr 8) and 0xFF).toByte()
        frame[body.size + 1] = (crc and 0xFF).toByte()

        val tones = IntArray(4 + frame.size * 2 + 1)
        tones[0] = TONE_SYNC
        tones[1] = TONE_REPEAT
        tones[2] = TONE_SYNC
        tones[3] = TONE_REPEAT

        var cursor = 4
        var prevTone = TONE_REPEAT
        for (b in frame) {
            val hi = (b.toInt() ushr 4) and 0x0F
            val lo = b.toInt() and 0x0F
            if (hi == prevTone) {
                tones[cursor++] = TONE_REPEAT
                prevTone = TONE_REPEAT
            } else {
                tones[cursor++] = hi
                prevTone = hi
            }
            if (lo == prevTone) {
                tones[cursor++] = TONE_REPEAT
                prevTone = TONE_REPEAT
            } else {
                tones[cursor++] = lo
                prevTone = lo
            }
        }
        tones[cursor] = TONE_SYNC
        return tones
    }

    fun estimateDurationSeconds(text: String, speed: AcousticSpeed): Float {
        val byteCount = text.toByteArray(StandardCharsets.UTF_8).size.coerceIn(1, MAX_PAYLOAD_BYTES)
        val dataSymbols = (byteCount + 4) * 2 + 1
        val totalMs = 4 * speed.preambleMs + dataSymbols * speed.symbolMs + 180
        return totalMs / 1000f
    }

    /**
     * Synthesizes smooth cosine-tapered FSK tones and plays them cleanly over Android AudioTrack.
     */
    suspend fun transmitPacket(
        kind: PacketKind,
        text: String,
        band: AcousticBand,
        speed: AcousticSpeed,
        onProgress: (progress: Float, activeTone: Int) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanBytes = text.toByteArray(StandardCharsets.UTF_8)
        if (cleanBytes.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Empty payload"))
        }

        val tones = encodeToTones(kind, text)
        val freqs = frequenciesFor(band)
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrElse { return@withContext Result.failure(it) }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { track.release() }
            return@withContext Result.failure(IllegalStateException("AudioTrack init failed"))
        }

        try {
            track.play()
            val lead = ShortArray((SAMPLE_RATE * 0.08).toInt())
            track.write(lead, 0, lead.size)

            val amp = if (band == AcousticBand.ULTRASONIC) 27000.0 else 28500.0
            for (idx in tones.indices) {
                if (!isActive) break
                val toneIdx = tones[idx]
                val durMs = if (idx < 4) speed.preambleMs else speed.symbolMs
                val sampleCount = (SAMPLE_RATE * durMs) / 1000
                val ramp = (sampleCount * 0.14).toInt().coerceAtLeast(1)
                val freq = freqs[toneIdx]
                val pcm = ShortArray(sampleCount)

                for (i in 0 until sampleCount) {
                    val env = when {
                        i < ramp -> 0.5 * (1.0 - cos(PI * i / ramp))
                        i > sampleCount - ramp -> 0.5 * (1.0 - cos(PI * (sampleCount - i) / ramp))
                        else -> 1.0
                    }
                    val v = sin(2.0 * PI * freq * i / SAMPLE_RATE) * env * amp
                    pcm[i] = v.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }

                track.write(pcm, 0, pcm.size)
                val progress = (idx + 1).toFloat() / tones.size
                withContext(Dispatchers.Main) {
                    onProgress(progress, toneIdx)
                }
            }

            if (isActive) {
                val tail = ShortArray((SAMPLE_RATE * 0.14).toInt())
                track.write(tail, 0, tail.size)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching {
                track.stop()
                track.release()
            }
        }
    }

    /**
     * Continuously listens via AudioRecord and decodes incoming FSK packets using a real-time
     * 18-bin Goertzel bank with SNR gating, 2-hop debounce, and CRC-16 verification.
     */
    @SuppressLint("MissingPermission")
    suspend fun runReceiverLoop(
        band: AcousticBand,
        onTelemetry: (ReceiverTelemetry) -> Unit,
        onPacketDecoded: (AcousticPacket) -> Unit,
        onCrcError: () -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val minBufBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufBytes <= 0) {
            withContext(Dispatchers.Main) { onError("AudioRecord unavailable") }
            return@withContext
        }

        val recorder = openBestRecorder(minBufBytes * 4)
        if (recorder == null) {
            withContext(Dispatchers.Main) { onError("Microphone busy or unavailable") }
            return@withContext
        }

        val freqs = frequenciesFor(band)
        val coeffs = DoubleArray(TONE_COUNT) { i ->
            val k = freqs[i] * WIN_SIZE / SAMPLE_RATE
            2.0 * cos(2.0 * PI * k / WIN_SIZE)
        }

        val ring = FloatArray(WIN_SIZE)
        val hopBuf = ShortArray(HOP_SIZE)
        val powers = DoubleArray(TONE_COUNT)
        val uiSpectrum = FloatArray(TONE_COUNT)

        var receiving = false
        var preambleStage = 0
        var candidateTone = -1
        var candidateHits = 0
        var lockedTone = -1
        var lastDataNibble = 0
        var lastToneActivityMs = 0L
        var lastUiEmitMs = 0L
        val nibbles = ArrayList<Int>(520)

        try {
            recorder.startRecording()
            while (isActive) {
                val read = recorder.read(hopBuf, 0, HOP_SIZE)
                if (read <= 0) continue

                val shift = read.coerceAtMost(WIN_SIZE)
                if (shift < WIN_SIZE) {
                    System.arraycopy(ring, shift, ring, 0, WIN_SIZE - shift)
                }
                val startWrite = WIN_SIZE - shift
                for (i in 0 until shift) {
                    ring[startWrite + i] = hopBuf[i] / 32768f
                }

                var sumSq = 0.0
                for (i in 0 until WIN_SIZE) {
                    val x = ring[i].toDouble()
                    sumSq += x * x
                }
                val rms = sqrt(sumSq / WIN_SIZE)

                val now = System.currentTimeMillis()
                if (receiving && now - lastToneActivityMs > 1300L) {
                    receiving = false
                    nibbles.clear()
                    lockedTone = -1
                    preambleStage = 0
                }

                var bestIdx = -1
                var bestPower = 0.0
                var secondPower = 0.0

                if (rms > 0.004) {
                    for (t in 0 until TONE_COUNT) {
                        val c = coeffs[t]
                        var s1 = 0.0
                        var s2 = 0.0
                        for (i in 0 until WIN_SIZE) {
                            val s0 = ring[i] * HANN_WINDOW[i] + c * s1 - s2
                            s2 = s1
                            s1 = s0
                        }
                        val p = (s1 * s1 + s2 * s2 - c * s1 * s2).coerceAtLeast(0.0)
                        powers[t] = p
                        if (p > bestPower) {
                            secondPower = bestPower
                            bestPower = p
                            bestIdx = t
                        } else if (p > secondPower) {
                            secondPower = p
                        }
                    }
                } else {
                    powers.fill(0.0)
                }

                val minPowerThreshold = if (band == AcousticBand.ULTRASONIC) 0.08 else 0.35
                val snrRatio = bestPower / secondPower.coerceAtLeast(1e-9)

                if (bestIdx >= 0 && bestPower >= minPowerThreshold && snrRatio >= 2.6) {
                    lastToneActivityMs = now
                    if (bestIdx == candidateTone) {
                        candidateHits++
                    } else {
                        candidateTone = bestIdx
                        candidateHits = 1
                    }

                    if (candidateHits == 2 && bestIdx != lockedTone) {
                        lockedTone = bestIdx
                        if (!receiving) {
                            val expected = when (preambleStage) {
                                0, 2 -> TONE_SYNC
                                else -> TONE_REPEAT
                            }
                            if (lockedTone == expected) {
                                preambleStage++
                                if (preambleStage == 4) {
                                    receiving = true
                                    preambleStage = 0
                                    nibbles.clear()
                                    lastDataNibble = 0
                                }
                            } else {
                                preambleStage = if (lockedTone == TONE_SYNC) 1 else 0
                            }
                        } else {
                            when (lockedTone) {
                                TONE_SYNC -> {
                                    if (nibbles.size >= 8 && nibbles.size % 2 == 0) {
                                        val byteLen = nibbles.size / 2
                                        val raw = ByteArray(byteLen)
                                        for (i in 0 until byteLen) {
                                            raw[i] = ((nibbles[i * 2] shl 4) or nibbles[i * 2 + 1]).toByte()
                                        }
                                        val kind = PacketKind.fromCode(raw[0])
                                        val declaredLen = raw[1].toInt() and 0xFF
                                        if (kind != null && raw.size == 2 + declaredLen + 2) {
                                            val rxCrc = ((raw[raw.size - 2].toInt() and 0xFF) shl 8) or
                                                (raw[raw.size - 1].toInt() and 0xFF)
                                            val calcCrc = crc16Ccitt(raw, 2 + declaredLen)
                                            if (rxCrc == calcCrc) {
                                                val payloadText = String(raw, 2, declaredLen, StandardCharsets.UTF_8)
                                                val pkt = AcousticPacket(kind = kind, payload = payloadText)
                                                withContext(Dispatchers.Main) {
                                                    onPacketDecoded(pkt)
                                                }
                                            } else {
                                                withContext(Dispatchers.Main) { onCrcError() }
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) { onCrcError() }
                                        }
                                    }
                                    receiving = false
                                    nibbles.clear()
                                    preambleStage = 1
                                }

                                TONE_REPEAT -> {
                                    if (nibbles.size < (MAX_PAYLOAD_BYTES + 6) * 2) {
                                        nibbles.add(lastDataNibble)
                                    }
                                }

                                else -> {
                                    if (nibbles.size < (MAX_PAYLOAD_BYTES + 6) * 2) {
                                        nibbles.add(lockedTone)
                                        lastDataNibble = lockedTone
                                    }
                                }
                            }
                        }
                    }
                } else {
                    candidateTone = -1
                    candidateHits = 0
                }

                if (now - lastUiEmitMs >= 65L) {
                    lastUiEmitMs = now
                    val normBase = bestPower.coerceAtLeast(minPowerThreshold * 2.0)
                    for (t in 0 until TONE_COUNT) {
                        val mag = sqrt(powers[t] / normBase).toFloat().coerceIn(0f, 1f)
                        uiSpectrum[t] = uiSpectrum[t] * 0.45f + mag * 0.55f
                    }
                    val snap = uiSpectrum.copyOf()
                    val phase = if (receiving) ReceiverPhase.LOCKED_RECEIVING else ReceiverPhase.LISTENING
                    val rxBytes = (nibbles.size / 2 - 2).coerceAtLeast(0)
                    val domTone = if (bestPower >= minPowerThreshold) bestIdx else -1
                    withContext(Dispatchers.Main) {
                        onTelemetry(ReceiverTelemetry(phase, snap, domTone, rxBytes))
                    }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError(e.localizedMessage ?: "Audio capture error")
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

    @SuppressLint("MissingPermission")
    private fun openBestRecorder(bufferBytes: Int): AudioRecord? {
        val sources = listOf(
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )
        for (src in sources) {
            val rec = runCatching {
                AudioRecord(
                    src,
                    SAMPLE_RATE,
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
}
