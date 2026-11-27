package com.muz.omnikit.tools.audio

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.muz.omnikit.core.model.Language
import com.muz.omnikit.core.updater.ApkInstaller
import com.muz.omnikit.ui.components.PermissionRequestCard
import com.muz.omnikit.ui.components.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SuaraDropTab { SEND_TEXT, SHARE_APK_FILE, RECEIVE }

@Composable
fun SoundTransferScreen(
    lang: Language,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf(SuaraDropTab.SEND_TEXT) }
    var acousticBand by remember { mutableStateOf(AcousticBand.AUDIBLE) }
    var acousticSpeed by remember { mutableStateOf(AcousticSpeed.ROBUST) }

    // === TAB 1: SEND TEXT OVER SOUND ===
    var messageInput by remember { mutableStateOf("") }
    var loopTransmit by remember { mutableStateOf(false) }
    var isTransmitting by remember { mutableStateOf(false) }
    var txProgress by remember { mutableFloatStateOf(0f) }
    var txActiveTone by remember { mutableIntStateOf(-1) }
    var txJob by remember { mutableStateOf<Job?>(null) }

    // === TAB 2: OFFLINE APK & FILE SHARE (LOCAL LAN HTTP + SOUND KEY + QR) ===
    var shareDownloadCount by remember { mutableIntStateOf(0) }
    val lanServer = remember(context) {
        LocalLanTransferServer(context) { count ->
            shareDownloadCount = count
        }
    }
    var sharedSource by remember { mutableStateOf<ShareSource?>(null) }
    var serverPort by remember { mutableIntStateOf(0) }
    var localIps by remember { mutableStateOf(LocalLanTransferServer.getLocalIpv4Addresses()) }
    var selectedIpIdx by remember { mutableIntStateOf(0) }
    var shareQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var shareError by remember { mutableStateOf<String?>(null) }

    // === TAB 3: RECEIVE OVER SOUND + AUTO OFFLINE DOWNLOAD ===
    var hasMicPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPerm = granted
    }

    var isListening by remember { mutableStateOf(false) }
    var rxPhase by remember { mutableStateOf(ReceiverPhase.LISTENING) }
    var rxSpectrum by remember { mutableStateOf(FloatArray(AcousticModemEngine.TONE_COUNT)) }
    var rxDominantTone by remember { mutableIntStateOf(-1) }
    var rxLiveBytes by remember { mutableIntStateOf(0) }
    var rxCrcWarning by remember { mutableStateOf(false) }
    var rxError by remember { mutableStateOf<String?>(null) }
    val receivedPackets = remember { mutableStateListOf<AcousticPacket>() }

    // Receiver file download states
    var isDownloadingFile by remember { mutableStateOf(false) }
    var dlBytes by remember { mutableLongStateOf(0L) }
    var dlTotalBytes by remember { mutableLongStateOf(-1L) }
    var downloadedFile by remember { mutableStateOf<DownloadedLocalFile?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            txJob?.cancel()
            lanServer.shutdown()
        }
    }

    LaunchedEffect(activeTab) {
        if (activeTab != SuaraDropTab.RECEIVE) {
            isListening = false
        }
        if (activeTab == SuaraDropTab.SHARE_APK_FILE) {
            localIps = LocalLanTransferServer.getLocalIpv4Addresses()
            if (selectedIpIdx >= localIps.size) selectedIpIdx = 0
        }
    }

    fun stopTransmit() {
        txJob?.cancel()
        txJob = null
        isTransmitting = false
        txProgress = 0f
        txActiveTone = -1
    }

    fun startTransmit(kind: PacketKind, text: String, loop: Boolean) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        stopTransmit()
        isTransmitting = true
        txJob = scope.launch {
            try {
                do {
                    val res = AcousticModemEngine.transmitPacket(
                        kind = kind,
                        text = trimmed,
                        band = acousticBand,
                        speed = acousticSpeed,
                        onProgress = { prog, tone ->
                            txProgress = prog
                            txActiveTone = tone
                        }
                    )
                    if (res.isFailure) break
                    if (loop && isActive) {
                        txActiveTone = -1
                        delay(700L)
                    }
                } while (loop && isActive)
            } finally {
                isTransmitting = false
                txProgress = 0f
                txActiveTone = -1
            }
        }
    }

    fun startSharingSource(source: ShareSource) {
        shareError = null
        localIps = LocalLanTransferServer.getLocalIpv4Addresses()
        if (selectedIpIdx >= localIps.size) selectedIpIdx = 0
        val started = lanServer.start(source)
        started.onSuccess { port ->
            sharedSource = source
            serverPort = port
            shareDownloadCount = 0
        }.onFailure { err ->
            shareError = err.localizedMessage ?: lang.tr("Gagal memulakan pelayan tempatan.", "Failed to start local server.")
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val src = LocalLanTransferServer.buildUriSource(context, uri)
        startSharingSource(src)
    }

    val activeIp = localIps.getOrNull(selectedIpIdx)?.ipAddress
    val shareBrowserUrl = remember(activeIp, serverPort, sharedSource) {
        if (!activeIp.isNullOrBlank() && serverPort > 0 && sharedSource != null) {
            "http://$activeIp:$serverPort/${lanServer.activeToken}"
        } else null
    }
    val compactSoundKey = remember(activeIp, serverPort, sharedSource) {
        if (!activeIp.isNullOrBlank() && serverPort > 0 && sharedSource != null) {
            "$activeIp:$serverPort/${lanServer.activeToken}"
        } else null
    }

    LaunchedEffect(shareBrowserUrl) {
        val url = shareBrowserUrl
        if (url.isNullOrBlank()) {
            shareQrBitmap = null
            return@LaunchedEffect
        }
        shareQrBitmap = withContext(Dispatchers.Default) {
            generateDropQr(url, 640)
        }
    }

    fun triggerLocalFileDownload(compactKey: String) {
        if (isDownloadingFile) return
        isDownloadingFile = true
        dlBytes = 0L
        dlTotalBytes = -1L
        downloadedFile = null
        downloadError = null

        scope.launch {
            val res = LocalLanTransferServer.downloadFromCompactKey(
                context = context,
                compactKey = compactKey,
                onProgress = { done, total ->
                    dlBytes = done
                    dlTotalBytes = total
                }
            )
            isDownloadingFile = false
            res.onSuccess { fileInfo ->
                downloadedFile = fileInfo
                vibrateDropSuccess(context)
                val toastMsg = if (fileInfo.isApk) {
                    lang.tr("APK selesai dimuat turun! Ketik 'Pasang Sekarang'.", "APK downloaded! Tap 'Install Now'.")
                } else {
                    lang.tr("Fail disimpan di Downloads/OmniKitMY/${fileInfo.fileName}", "Saved to Downloads/OmniKitMY/${fileInfo.fileName}")
                }
                Toast.makeText(context, toastMsg, Toast.LENGTH_LONG).show()
            }.onFailure { err ->
                downloadError = err.localizedMessage ?: lang.tr(
                    "Gagal muat turun. Pastikan bersambung ke Wi-Fi / Hotspot yang sama.",
                    "Download failed. Ensure both devices are on the same Wi-Fi / Hotspot."
                )
            }
        }
    }

    LaunchedEffect(isListening, hasMicPerm, acousticBand) {
        if (!isListening || !hasMicPerm) {
            rxSpectrum = FloatArray(AcousticModemEngine.TONE_COUNT)
            rxDominantTone = -1
            rxPhase = ReceiverPhase.LISTENING
            rxLiveBytes = 0
            return@LaunchedEffect
        }
        rxError = null
        rxCrcWarning = false

        AcousticModemEngine.runReceiverLoop(
            band = acousticBand,
            onTelemetry = { t ->
                rxPhase = t.phase
                rxSpectrum = t.spectrum
                rxDominantTone = t.dominantTone
                rxLiveBytes = t.receivedBytesCount
            },
            onPacketDecoded = { pkt ->
                rxCrcWarning = false
                vibrateDropSuccess(context)
                val latest = receivedPackets.firstOrNull()
                val isDuplicateBurst = latest != null &&
                    latest.payload == pkt.payload &&
                    (pkt.timestampMs - latest.timestampMs) < 3500L
                if (!isDuplicateBurst) {
                    receivedPackets.add(0, pkt)
                    if (receivedPackets.size > 20) receivedPackets.removeAt(receivedPackets.lastIndex)
                }
                if (pkt.kind == PacketKind.FILE_LINK) {
                    isListening = false
                    triggerLocalFileDownload(pkt.payload)
                }
            },
            onCrcError = {
                rxCrcWarning = true
            },
            onError = { msg ->
                rxError = msg
                isListening = false
            }
        )
    }

    ToolScaffold(
        title = "SuaraDrop Pro",
        subtitle = lang.tr("Hantar data guna bunyi & kongsi APK luar talian", "Data-over-Sound & offline APK/file drop"),
        onBack = {
            stopTransmit()
            isListening = false
            lanServer.stop()
            onBack()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 3 Main Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = activeTab == SuaraDropTab.SEND_TEXT,
                    onClick = { activeTab = SuaraDropTab.SEND_TEXT },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(lang.tr("Bunyi", "Sound"), maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = activeTab == SuaraDropTab.SHARE_APK_FILE,
                    onClick = { activeTab = SuaraDropTab.SHARE_APK_FILE },
                    leadingIcon = { Icon(Icons.Rounded.FolderShared, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(lang.tr("APK/Fail", "APK/File"), maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = activeTab == SuaraDropTab.RECEIVE,
                    onClick = {
                        stopTransmit()
                        activeTab = SuaraDropTab.RECEIVE
                    },
                    leadingIcon = { Icon(Icons.Rounded.Hearing, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(lang.tr("Terima", "Receive"), maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Shared Band Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = acousticBand == AcousticBand.AUDIBLE,
                    onClick = {
                        if (!isTransmitting) acousticBand = AcousticBand.AUDIBLE
                    },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(15.dp)) },
                    label = { Text(lang.tr("Boleh Dengar (1.8–4 kHz)", "Audible (1.8–4 kHz)"), maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = acousticBand == AcousticBand.ULTRASONIC,
                    onClick = {
                        if (!isTransmitting) acousticBand = AcousticBand.ULTRASONIC
                    },
                    leadingIcon = { Icon(Icons.Rounded.Podcasts, contentDescription = null, modifier = Modifier.size(15.dp)) },
                    label = { Text(lang.tr("Hampir Senyap (16+ kHz)", "Near-Silent (16+ kHz)"), maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }

            when (activeTab) {
                SuaraDropTab.SEND_TEXT -> {
                    val utf8Bytes = messageInput.toByteArray(StandardCharsets.UTF_8).size
                    val estSec = if (utf8Bytes > 0) AcousticModemEngine.estimateDurationSeconds(messageInput, acousticSpeed) else 0f

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = messageInput,
                                onValueChange = { candidate ->
                                    if (candidate.toByteArray(StandardCharsets.UTF_8).size <= AcousticModemEngine.MAX_PAYLOAD_BYTES) {
                                        messageInput = candidate
                                    }
                                },
                                label = { Text(lang.tr("Teks, Kata Laluan Wi-Fi, Pautan atau Nota", "Text, Wi-Fi Password, URL or Note")) },
                                minLines = 3,
                                maxLines = 5,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "$utf8Bytes / ${AcousticModemEngine.MAX_PAYLOAD_BYTES} ${lang.tr("bait (CRC-16)", "bytes (CRC-16)")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (estSec > 0f) {
                                    Text(
                                        text = String.format(Locale.US, "~%.1f s", estSec),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = acousticSpeed == AcousticSpeed.ROBUST,
                                    onClick = { if (!isTransmitting) acousticSpeed = AcousticSpeed.ROBUST },
                                    label = { Text(lang.tr("Lasak (Stabil)", "Robust"), maxLines = 1) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = acousticSpeed == AcousticSpeed.FAST,
                                    onClick = { if (!isTransmitting) acousticSpeed = AcousticSpeed.FAST },
                                    label = { Text(lang.tr("Pantas", "Fast"), maxLines = 1) },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = lang.tr("Ulang pancaran bunyi (Loop)", "Repeat broadcast (Loop)"),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Switch(
                                    checked = loopTransmit,
                                    onCheckedChange = { loopTransmit = it },
                                    enabled = !isTransmitting
                                )
                            }

                            if (isTransmitting) {
                                AcousticToneStripCanvas(
                                    activeTone = txActiveTone,
                                    spectrum = null,
                                    accent = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                )
                                LinearProgressIndicator(
                                    progress = { txProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }

                            Button(
                                onClick = {
                                    if (isTransmitting) {
                                        stopTransmit()
                                    } else {
                                        startTransmit(PacketKind.TEXT, messageInput, loopTransmit)
                                    }
                                },
                                enabled = isTransmitting || messageInput.trim().isNotEmpty(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = if (isTransmitting) {
                                    ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White)
                                } else {
                                    ButtonDefaults.buttonColors()
                                }
                            ) {
                                Icon(
                                    imageVector = if (isTransmitting) Icons.Rounded.Stop else Icons.Rounded.Podcasts,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isTransmitting) lang.tr("Hentikan Pancaran", "Stop Broadcast") else lang.tr("Pancar Teks Guna Bunyi", "Broadcast via Sound"),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    InfoHintCard(
                        text = lang.tr(
                            "Berfungsi 100% luar talian walaupun dalam Mod Pesawat! Naikkan kelantangan media ~75%, buka tab 'Terima' pada 1 atau lebih telefon rakan, dan tekan Pancar.",
                            "Works 100% offline even in Airplane Mode! Set media volume to ~75%, open the 'Receive' tab on one or more nearby phones, and tap Broadcast."
                        )
                    )
                }

                SuaraDropTab.SHARE_APK_FILE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val selfApk = LocalLanTransferServer.buildSelfApkSource(context)
                                if (selfApk != null) {
                                    startSharingSource(selfApk)
                                } else {
                                    shareError = lang.tr("Gagal membaca APK aplikasi ini.", "Unable to read own app APK.")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Android, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(lang.tr("Kongsi OmniKitMY", "Share OmniKitMY"), maxLines = 1, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(lang.tr("Pilih Fail / APK", "Pick File / APK"), maxLines = 1)
                        }
                    }

                    if (!shareError.isNullOrBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = shareError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

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
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = lang.tr("Rangkaian Tempatan (0 KB Internet)", "Local Network (0 KB Internet)"),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        localIps = LocalLanTransferServer.getLocalIpv4Addresses()
                                        if (selectedIpIdx >= localIps.size) selectedIpIdx = 0
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh IP", modifier = Modifier.size(18.dp))
                                }
                            }

                            if (localIps.isEmpty()) {
                                Text(
                                    text = lang.tr(
                                        "Tiada sambungan Wi-Fi atau Hotspot dikesan. Hidupkan Hotspot telefon ini (Mobile Data boleh mati) atau sambung ke Wi-Fi sama dengan rakan, lalu ketik segar semula.",
                                        "No Wi-Fi or Hotspot detected. Turn on this phone's Personal Hotspot (Mobile Data can be OFF) or join the same Wi-Fi as your friend, then tap refresh."
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    localIps.take(3).forEachIndexed { idx, item ->
                                        FilterChip(
                                            selected = selectedIpIdx == idx,
                                            onClick = { selectedIpIdx = idx },
                                            label = {
                                                Text(
                                                    text = if (item.isLikelyHotspot) "Hotspot (${item.ipAddress})" else item.ipAddress,
                                                    maxLines = 1,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val src = sharedSource
                    if (src != null && serverPort > 0) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = src.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${LocalLanTransferServer.formatLanBytes(src.sizeBytes)} • " +
                                                lang.tr("Dimuat turun: $shareDownloadCount kali", "Downloaded: $shareDownloadCount times"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            stopTransmit()
                                            lanServer.stop()
                                            sharedSource = null
                                            serverPort = 0
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(lang.tr("Henti", "Stop"))
                                    }
                                }

                                val keyStr = compactSoundKey
                                if (!keyStr.isNullOrBlank()) {
                                    val keySec = AcousticModemEngine.estimateDurationSeconds(keyStr, acousticSpeed)
                                    Button(
                                        onClick = {
                                            if (isTransmitting) {
                                                stopTransmit()
                                            } else {
                                                startTransmit(PacketKind.FILE_LINK, keyStr, loop = false)
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = if (isTransmitting) {
                                            ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White)
                                        } else {
                                            ButtonDefaults.buttonColors()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isTransmitting) Icons.Rounded.Stop else Icons.Rounded.Podcasts,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isTransmitting) {
                                                lang.tr("Memancar Kunci Bunyi...", "Broadcasting Sound Key...")
                                            } else {
                                                String.format(
                                                    Locale.US,
                                                    "%s (~%.1fs)",
                                                    lang.tr("Pancar Kunci Bunyi ke OmniKitMY", "Send via Sound Key"),
                                                    keySec
                                                )
                                            },
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (isTransmitting) {
                                        LinearProgressIndicator(
                                            progress = { txProgress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(7.dp)
                                                .clip(RoundedCornerShape(7.dp))
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                Text(
                                    text = lang.tr(
                                        "Kawan belum ada OmniKitMY? Imbas QR ini guna kamera biasa:",
                                        "Friend doesn't have OmniKitMY yet? Scan with any camera:"
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                shareQrBitmap?.let { bmp ->
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = Color.White,
                                        shadowElevation = 3.dp
                                    ) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Local Download QR",
                                            modifier = Modifier
                                                .size(210.dp)
                                                .padding(12.dp)
                                        )
                                    }
                                }

                                shareBrowserUrl?.let { url ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = url,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = {
                                                    copyDropText(context, "SuaraDrop URL", url)
                                                    Toast.makeText(context, lang.tr("Pautan disalin!", "URL copied!"), Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        InfoHintCard(
                            text = lang.tr(
                                "Tekan 'Kongsi OmniKitMY' untuk hantar terus aplikasi ini ke telefon rakan tanpa internet — sama ada melalui QR (kamera biasa) atau pancaran kunci bunyi ke telefon OmniKitMY lain.",
                                "Tap 'Share OmniKitMY' to transfer this app directly to a friend offline — via normal camera QR or a 3-second sound key to another OmniKitMY device."
                            )
                        )
                    }
                }

                SuaraDropTab.RECEIVE -> {
                    if (!hasMicPerm) {
                        PermissionRequestCard(
                            icon = Icons.Rounded.Mic,
                            title = lang.tr("Kebenaran Mikrofon Diperlukan", "Microphone Permission Needed"),
                            description = lang.tr(
                                "SuaraDrop Pro menggunakan mikrofon untuk mendengar isyarat gelombang bunyi dan menyahkod data secara masa nyata (100% luar talian).",
                                "SuaraDrop Pro uses your microphone to listen for acoustic waves and decode data in real time (100% offline)."
                            ),
                            buttonLabel = lang.tr("Benarkan Mikrofon", "Allow Microphone"),
                            onRequest = { micPermLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        )
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(
                                width = if (rxPhase == ReceiverPhase.LOCKED_RECEIVING) 1.5.dp else 1.dp,
                                color = if (rxPhase == ReceiverPhase.LOCKED_RECEIVING) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = when {
                                            !isListening -> lang.tr("Penerima Dimatikan", "Receiver Standby")
                                            rxPhase == ReceiverPhase.LOCKED_RECEIVING -> lang.tr("● Menerima Data ($rxLiveBytes bait)...", "● Receiving ($rxLiveBytes bytes)...")
                                            else -> lang.tr("● Mendengar Isyarat Bunyi...", "● Listening for Acoustic Signal...")
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            !isListening -> MaterialTheme.colorScheme.onSurfaceVariant
                                            rxPhase == ReceiverPhase.LOCKED_RECEIVING -> Color(0xFF10B981)
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    )
                                    Text(
                                        text = if (acousticBand == AcousticBand.AUDIBLE) "18-FSK" else "16kHz+",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                AcousticToneStripCanvas(
                                    activeTone = rxDominantTone,
                                    spectrum = rxSpectrum,
                                    accent = if (rxPhase == ReceiverPhase.LOCKED_RECEIVING) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(82.dp)
                                )

                                if (rxCrcWarning) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                                        Text(
                                            text = lang.tr(
                                                "Isyarat terganggu bising (CRC-16 gagal). Rapatkan telefon atau naikkan volum penghantar.",
                                                "Signal disrupted by noise (CRC-16 mismatch). Move closer or raise sender volume."
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFF59E0B)
                                        )
                                    }
                                }

                                if (!rxError.isNullOrBlank()) {
                                    Text(rxError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }

                                Button(
                                    onClick = { isListening = !isListening },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = if (isListening) {
                                        ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White)
                                    } else {
                                        ButtonDefaults.buttonColors()
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isListening) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isListening) lang.tr("Henti Mendengar", "Stop Listening") else lang.tr("Mula Terima Bunyi", "Start Listening"),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (isDownloadingFile || downloadedFile != null || !downloadError.isNullOrBlank()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.5.dp, Color(0xFF10B981))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    when {
                                        isDownloadingFile -> {
                                            val prog = if (dlTotalBytes > 0L) (dlBytes.toFloat() / dlTotalBytes).coerceIn(0f, 1f) else 0f
                                            Text(
                                                text = lang.tr("Memuat turun fail secara luar talian...", "Downloading file offline over Wi-Fi..."),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (dlTotalBytes > 0L) {
                                                LinearProgressIndicator(
                                                    progress = { prog },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(8.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                )
                                                Text(
                                                    text = "${LocalLanTransferServer.formatLanBytes(dlBytes)} / ${LocalLanTransferServer.formatLanBytes(dlTotalBytes)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            } else {
                                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                            }
                                        }

                                        downloadedFile != null -> {
                                            val df = downloadedFile!!
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(df.fileName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                    Text(
                                                        text = "${LocalLanTransferServer.formatLanBytes(df.sizeBytes)} • " +
                                                            if (df.isApk) lang.tr("APK sedia dipasang", "APK ready to install")
                                                            else lang.tr("Disimpan di Downloads/OmniKitMY", "Saved to Downloads/OmniKitMY"),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            if (df.isApk && !df.localApkPath.isNullOrBlank()) {
                                                Button(
                                                    onClick = { ApkInstaller.promptInstall(context, df.localApkPath) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(lang.tr("Pasang APK Sekarang", "Install APK Now"), fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        !downloadError.isNullOrBlank() -> {
                                            Text(
                                                text = downloadError!!,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (receivedPackets.isNotEmpty()) {
                            Text(
                                text = lang.tr("Data Diterima (${receivedPackets.size})", "Received Data (${receivedPackets.size})"),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            receivedPackets.forEach { pkt ->
                                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(pkt.timestampMs))
                                val isUrl = pkt.payload.startsWith("http://", ignoreCase = true) ||
                                    pkt.payload.startsWith("https://", ignoreCase = true)

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
                                            text = if (pkt.kind == PacketKind.FILE_LINK) {
                                                lang.tr("Kunci Fail / APK • $timeStr", "File / APK Key • $timeStr")
                                            } else {
                                                lang.tr("Teks Bunyi (CRC Sah) • $timeStr", "Acoustic Text (CRC OK) • $timeStr")
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = pkt.payload,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (pkt.kind == PacketKind.FILE_LINK) {
                                                Button(
                                                    onClick = { triggerLocalFileDownload(pkt.payload) },
                                                    enabled = !isDownloadingFile,
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(lang.tr("Muat Turun Semula", "Download File"))
                                                }
                                            } else {
                                                OutlinedButton(
                                                    onClick = {
                                                        copyDropText(context, "SuaraDrop", pkt.payload)
                                                        Toast.makeText(context, lang.tr("Disalin!", "Copied!"), Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(lang.tr("Salin", "Copy"))
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        val send = Intent(Intent.ACTION_SEND).apply {
                                                            type = "text/plain"
                                                            putExtra(Intent.EXTRA_TEXT, pkt.payload)
                                                        }
                                                        context.startActivity(Intent.createChooser(send, lang.tr("Kongsi", "Share")))
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(lang.tr("Kongsi", "Share"))
                                                }
                                                if (isUrl) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            runCatching {
                                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pkt.payload)))
                                                            }
                                                        },
                                                        shape = RoundedCornerShape(10.dp)
                                                    ) {
                                                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AcousticToneStripCanvas(
    activeTone: Int,
    spectrum: FloatArray?,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val bgTrack = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier) {
        val count = AcousticModemEngine.TONE_COUNT
        val gap = 4.dp.toPx()
        val barW = ((size.width - gap * (count - 1)) / count).coerceAtLeast(3f)
        val h = size.height

        for (i in 0 until count) {
            val left = i * (barW + gap)
            val level = when {
                spectrum != null && i < spectrum.size -> spectrum[i].coerceIn(0.08f, 1f)
                activeTone == i -> 0.95f
                else -> 0.14f
            }
            val barH = (h * level).coerceAtLeast(6.dp.toPx())
            val color = when {
                i == activeTone -> accent
                i >= 16 -> Color(0xFFFBBF24).copy(alpha = if (level > 0.35f) 0.9f else 0.35f)
                level > 0.45f -> accent.copy(alpha = 0.85f)
                else -> bgTrack
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(left, h - barH),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
            )
        }
    }
}

@Composable
private fun InfoHintCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp)
        )
    }
}

private fun generateDropQr(content: String, sizePx: Int): Bitmap? {
    return runCatching {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(sizePx * sizePx)
        val fg = Color(0xFF0A1222).toArgb()
        val bg = Color.White.toArgb()
        for (y in 0 until sizePx) {
            val row = y * sizePx
            for (x in 0 until sizePx) {
                pixels[row + x] = if (matrix.get(x, y)) fg else bg
            }
        }
        Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }.getOrNull()
}

private fun copyDropText(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun vibrateDropSuccess(context: Context) {
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
