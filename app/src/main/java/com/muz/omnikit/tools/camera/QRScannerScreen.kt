package com.muz.omnikit.tools.camera

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
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.muz.omnikit.core.media.MediaExportHelper
import com.muz.omnikit.core.model.Language
import com.muz.omnikit.ui.components.PermissionRequestCard
import com.muz.omnikit.ui.components.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private enum class QrTab { SCAN, GENERATE, HISTORY }

private data class QrHistoryEntry(
    val id: String,
    val content: String,
    val formatLabel: String,
    val timestamp: String
)

private const val QR_PREFS_NAME = "omnikit_qr_prefs"
private const val QR_HISTORY_KEY = "scan_history_json"

private val QR_FG_PRESETS = listOf(
    Color(0xFF000000),
    Color(0xFF0F172A),
    Color(0xFF1D4ED8),
    Color(0xFF047857),
    Color(0xFFB91C1C),
    Color(0xFF6D28D9)
)

private val QR_BG_PRESETS = listOf(
    Color(0xFFFFFFFF),
    Color(0xFFF8FAFC),
    Color(0xFFFEF3C7),
    Color(0xFFE0F2FE),
    Color(0xFFDCFCE7)
)

@Composable
fun QRScannerScreen(
    lang: Language,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf(QrTab.SCAN) }
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

    // Scanner states
    var isScanning by remember { mutableStateOf(true) }
    var torchOn by remember { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var scannedValue by remember { mutableStateOf<String?>(null) }
    var scannedFormat by remember { mutableStateOf("") }
    var scanError by remember { mutableStateOf<String?>(null) }
    var copiedFeedback by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(loadQrHistory(context)) }

    // Generator states
    var qrInputText by remember { mutableStateOf("https://omnikitmy.app") }
    var fgIdx by remember { mutableIntStateOf(0) }
    var bgIdx by remember { mutableIntStateOf(0) }
    var generatedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    fun recordScan(value: String, formatName: String) {
        val nowStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val entry = QrHistoryEntry(
            id = System.currentTimeMillis().toString(),
            content = value,
            formatLabel = formatName,
            timestamp = nowStr
        )
        val updated = (listOf(entry) + history.filterNot { it.content == value }).take(30)
        history = updated
        saveQrHistory(context, updated)
    }

    // Gallery image QR picker
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scanError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                scanBarcodeFromUri(context, uri)
            }
            if (result != null) {
                vibrateScanSuccess(context)
                scannedValue = result.first
                scannedFormat = result.second
                isScanning = false
                recordScan(result.first, result.second)
            } else {
                scanError = lang.tr(
                    "Tiada kod QR atau kod bar dikesan pada gambar yang dipilih.",
                    "No QR code or barcode detected in the selected image."
                )
            }
        }
    }

    // Regenerate QR bitmap off main thread whenever text or colors change
    LaunchedEffect(activeTab, qrInputText, fgIdx, bgIdx) {
        if (activeTab != QrTab.GENERATE) return@LaunchedEffect
        val text = qrInputText.trim()
        if (text.isEmpty()) {
            generatedBitmap = null
            return@LaunchedEffect
        }
        val fg = QR_FG_PRESETS[fgIdx].toArgb()
        val bg = QR_BG_PRESETS[bgIdx].toArgb()
        generatedBitmap = withContext(Dispatchers.Default) {
            generateQrBitmap(text, 768, fg, bg)
        }
    }

    ToolScaffold(
        title = lang.tr("Pengimbas & Penjana QR", "QR Scanner & Creator"),
        subtitle = lang.tr("Kamera ML Kit luar talian & eksport PNG", "Offline ML Kit scanner & PNG generator"),
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mode Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeTab == QrTab.SCAN,
                    onClick = { activeTab = QrTab.SCAN },
                    leadingIcon = { Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(lang.tr("Imbas", "Scan"), maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = activeTab == QrTab.GENERATE,
                    onClick = {
                        torchOn = false
                        activeTab = QrTab.GENERATE
                    },
                    leadingIcon = { Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(lang.tr("Jana QR", "Create"), maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = activeTab == QrTab.HISTORY,
                    onClick = {
                        torchOn = false
                        activeTab = QrTab.HISTORY
                    },
                    leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(lang.tr("Sejarah", "History"), maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }

            when (activeTab) {
                QrTab.SCAN -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!hasCameraPerm) {
                            PermissionRequestCard(
                                icon = Icons.Rounded.CameraAlt,
                                title = lang.tr("Kebenaran Kamera Diperlukan", "Camera Permission Needed"),
                                description = lang.tr(
                                    "Benarkan akses kamera untuk mengimbas kod QR dan kod bar secara langsung, atau pilih imej dari galeri di bawah.",
                                    "Allow camera access to scan QR and barcodes live, or pick an image from your gallery below."
                                ),
                                buttonLabel = lang.tr("Benarkan Kamera", "Allow Camera"),
                                onRequest = { permLauncher.launch(Manifest.permission.CAMERA) }
                            )
                        } else {
                            // Live CameraX Viewfinder Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.05f)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isScanning) {
                                    QrLiveCameraPreview(
                                        torchOn = torchOn,
                                        onCameraBound = { cam ->
                                            cameraRef = cam
                                            hasTorch = cam.cameraInfo.hasFlashUnit()
                                        },
                                        onBarcodeDetected = { rawVal, fmt ->
                                            vibrateScanSuccess(context)
                                            scannedValue = rawVal
                                            scannedFormat = fmt
                                            scanError = null
                                            isScanning = false
                                            torchOn = false
                                            recordScan(rawVal, fmt)
                                        },
                                        onCameraError = { msg -> scanError = msg }
                                    )

                                    // Precision Corner Target Reticle Overlay
                                    ScannerReticleOverlay(modifier = Modifier.fillMaxSize())

                                    if (hasTorch) {
                                        IconButton(
                                            onClick = {
                                                val next = !torchOn
                                                torchOn = next
                                                cameraRef?.cameraControl?.enableTorch(next)
                                            },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(12.dp)
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
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(52.dp)
                                        )
                                        Text(
                                            text = lang.tr("Imbasan Berjaya!", "Scan Captured!"),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Button(
                                            onClick = {
                                                scannedValue = null
                                                scanError = null
                                                isScanning = true
                                            },
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(lang.tr("Imbas Semula", "Scan Again"))
                                        }
                                    }
                                }
                            }
                        }

                        // Gallery Pick Button
                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(lang.tr("Imbas dari Gambar Galeri", "Scan from Gallery Image"))
                        }

                        if (!scanError.isNullOrBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = scanError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        // Scanned Result Card
                        val resultText = scannedValue
                        if (!resultText.isNullOrBlank()) {
                            val isUrl = resultText.startsWith("http://", ignoreCase = true) ||
                                resultText.startsWith("https://", ignoreCase = true)

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
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
                                            text = lang.tr("Keputusan ($scannedFormat)", "Result ($scannedFormat)"),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        TextButton(onClick = {
                                            scannedValue = null
                                            isScanning = true
                                        }) {
                                            Text(lang.tr("Tutup", "Clear"))
                                        }
                                    }

                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = resultText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                copyPlainText(context, "QR", resultText)
                                                copiedFeedback = true
                                                Toast.makeText(context, lang.tr("Disalin!", "Copied!"), Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(
                                                if (copiedFeedback) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(lang.tr("Salin", "Copy"))
                                        }

                                        if (isUrl) {
                                            OutlinedButton(
                                                onClick = {
                                                    runCatching {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(resultText)))
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(lang.tr("Buka", "Open"))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                QrTab.GENERATE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OutlinedTextField(
                            value = qrInputText,
                            onValueChange = { qrInputText = it },
                            label = { Text(lang.tr("Teks, URL, Wi-Fi atau Nombor", "Text, URL, Wi-Fi or Number")) },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        )

                        // Foreground & Background Color Pickers
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
                                Text(lang.tr("Warna Kod QR", "QR Foreground Color"), style = MaterialTheme.typography.labelMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    QR_FG_PRESETS.forEachIndexed { idx, col ->
                                        ColorDotSwatch(color = col, selected = fgIdx == idx, onClick = { fgIdx = idx })
                                    }
                                }
                                Text(lang.tr("Warna Latar Belakang", "Background Color"), style = MaterialTheme.typography.labelMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    QR_BG_PRESETS.forEachIndexed { idx, col ->
                                        ColorDotSwatch(color = col, selected = bgIdx == idx, onClick = { bgIdx = idx })
                                    }
                                }
                            }
                        }

                        val bmp = generatedBitmap
                        if (bmp != null) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = QR_BG_PRESETS[bgIdx],
                                shadowElevation = 4.dp,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Generated QR",
                                    modifier = Modifier
                                        .size(240.dp)
                                        .padding(14.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val name = "OmniKitMY_QR_${System.currentTimeMillis()}.png"
                                        val saved = MediaExportHelper.saveBitmapToGallery(
                                            context = context,
                                            bitmap = bmp,
                                            displayName = name,
                                            format = Bitmap.CompressFormat.PNG
                                        )
                                        val msg = if (saved != null) {
                                            lang.tr("QR disimpan ke Galeri (Pictures/OmniKitMY)", "QR saved to Gallery (Pictures/OmniKitMY)")
                                        } else {
                                            lang.tr("Gagal menyimpan gambar QR.", "Failed to save QR image.")
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(lang.tr("Simpan PNG", "Save PNG"))
                                }

                                OutlinedButton(
                                    onClick = {
                                        val name = "OmniKitMY_QR_${System.currentTimeMillis()}.png"
                                        val uri = MediaExportHelper.saveBitmapToGallery(
                                            context = context,
                                            bitmap = bmp,
                                            displayName = name,
                                            format = Bitmap.CompressFormat.PNG
                                        )
                                        if (uri != null) {
                                            MediaExportHelper.shareUri(
                                                context,
                                                uri,
                                                "image/png",
                                                lang.tr("Kongsi Kod QR", "Share QR Code")
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(lang.tr("Kongsi", "Share"))
                                }
                            }
                        } else {
                            Text(
                                text = lang.tr("Masukkan teks di atas untuk menjana kod QR.", "Enter text above to generate a QR code."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                QrTab.HISTORY -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = lang.tr("Sejarah Imbasan (${history.size})", "Scan History (${history.size})"),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (history.isNotEmpty()) {
                            TextButton(onClick = {
                                history = emptyList()
                                saveQrHistory(context, emptyList())
                            }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(lang.tr("Kosongkan", "Clear All"))
                            }
                        }
                    }

                    if (history.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = lang.tr("Belum ada sejarah imbasan.", "No scan history yet."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(history, key = { it.id }) { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${item.formatLabel} • ${item.timestamp}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            IconButton(
                                                onClick = {
                                                    copyPlainText(context, "QR", item.content)
                                                    Toast.makeText(context, lang.tr("Disalin!", "Copied!"), Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        Text(
                                            text = item.content,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
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

@OptIn(ExperimentalGetImage::class)
@Composable
private fun QrLiveCameraPreview(
    torchOn: Boolean,
    onCameraBound: (Camera) -> Unit,
    onBarcodeDetected: (value: String, formatName: String) -> Unit,
    onCameraError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val detectedLock = remember { AtomicBoolean(false) }

    val scanner = remember {
        val opts = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
        BarcodeScanning.getClient(opts)
    }

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
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(executor) { imageProxy ->
                    if (detectedLock.get()) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val mediaImage = imageProxy.image
                    if (mediaImage == null) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    scanner.process(input)
                        .addOnSuccessListener { barcodes ->
                            val hit = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                            if (hit != null && detectedLock.compareAndSet(false, true)) {
                                val raw = hit.rawValue.orEmpty()
                                val fmt = barcodeFormatName(hit.format)
                                ContextCompat.getMainExecutor(context).execute {
                                    onBarcodeDetected(raw, fmt)
                                }
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                }

                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                camera.cameraControl.enableTorch(torchOn)
                onCameraBound(camera)
            } catch (e: Exception) {
                onCameraError(e.localizedMessage ?: "Camera initialization error")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { boundProvider?.unbindAll() }
            runCatching { scanner.close() }
            runCatching { executor.shutdown() }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun ScannerReticleOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val boxSize = size.minDimension * 0.64f
        val left = (size.width - boxSize) / 2f
        val top = (size.height - boxSize) / 2f
        val right = left + boxSize
        val bottom = top + boxSize
        val cornerLen = boxSize * 0.16f
        val strokeW = 4.dp.toPx()
        val accent = Color(0xFF38BDF8)

        // Four precision L-corners
        listOf(
            Offset(left, top) to Offset(left + cornerLen, top),
            Offset(left, top) to Offset(left, top + cornerLen),
            Offset(right, top) to Offset(right - cornerLen, top),
            Offset(right, top) to Offset(right, top + cornerLen),
            Offset(left, bottom) to Offset(left + cornerLen, bottom),
            Offset(left, bottom) to Offset(left, bottom - cornerLen),
            Offset(right, bottom) to Offset(right - cornerLen, bottom),
            Offset(right, bottom) to Offset(right, bottom - cornerLen)
        ).forEach { (start, end) ->
            drawLine(accent, start, end, strokeW, cap = StrokeCap.Round)
        }

        // Center laser guide line
        drawLine(
            color = Color(0xCCEF4444),
            start = Offset(left + 12.dp.toPx(), size.height / 2f),
            end = Offset(right - 12.dp.toPx(), size.height / 2f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun ColorDotSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            val checkTint = if (color == Color.White || color == Color(0xFFF8FAFC) || color == Color(0xFFFEF3C7) || color == Color(0xFFE0F2FE) || color == Color(0xFFDCFCE7)) {
                Color.Black
            } else {
                Color.White
            }
            Icon(Icons.Rounded.Check, contentDescription = null, tint = checkTint, modifier = Modifier.size(18.dp))
        }
    }
}

private suspend fun scanBarcodeFromUri(context: Context, uri: Uri): Pair<String, String>? {
    return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        try {
            val image = InputImage.fromFilePath(context, uri)
            val scanner = BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build()
            )
            scanner.process(image)
                .addOnSuccessListener { list ->
                    val hit = list.firstOrNull { !it.rawValue.isNullOrBlank() }
                    scanner.close()
                    if (cont.isActive) {
                        cont.resumeWith(Result.success(hit?.let { it.rawValue.orEmpty() to barcodeFormatName(it.format) }))
                    }
                }
                .addOnFailureListener {
                    scanner.close()
                    if (cont.isActive) cont.resumeWith(Result.success(null))
                }
        } catch (_: Exception) {
            if (cont.isActive) cont.resumeWith(Result.success(null))
        }
    }
}

private fun generateQrBitmap(content: String, sizePx: Int, fgArgb: Int, bgArgb: Int): Bitmap? {
    return runCatching {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val rowOffset = y * sizePx
            for (x in 0 until sizePx) {
                pixels[rowOffset + x] = if (matrix.get(x, y)) fgArgb else bgArgb
            }
        }
        Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }.getOrNull()
}

private fun barcodeFormatName(format: Int): String = when (format) {
    Barcode.FORMAT_QR_CODE -> "QR Code"
    Barcode.FORMAT_EAN_13 -> "EAN-13"
    Barcode.FORMAT_EAN_8 -> "EAN-8"
    Barcode.FORMAT_CODE_128 -> "Code 128"
    Barcode.FORMAT_CODE_39 -> "Code 39"
    Barcode.FORMAT_UPC_A -> "UPC-A"
    Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
    Barcode.FORMAT_PDF417 -> "PDF417"
    Barcode.FORMAT_AZTEC -> "Aztec"
    else -> "Barcode"
}

private fun loadQrHistory(context: Context): List<QrHistoryEntry> {
    val raw = context.getSharedPreferences(QR_PREFS_NAME, Context.MODE_PRIVATE)
        .getString(QR_HISTORY_KEY, null) ?: return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            QrHistoryEntry(
                id = obj.optString("id"),
                content = obj.optString("content"),
                formatLabel = obj.optString("format", "QR"),
                timestamp = obj.optString("time")
            )
        }
    }.getOrDefault(emptyList())
}

private fun saveQrHistory(context: Context, list: List<QrHistoryEntry>) {
    val arr = JSONArray()
    list.forEach { item ->
        arr.put(
            JSONObject().apply {
                put("id", item.id)
                put("content", item.content)
                put("format", item.formatLabel)
                put("time", item.timestamp)
            }
        )
    }
    context.getSharedPreferences(QR_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(QR_HISTORY_KEY, arr.toString())
        .apply()
}

private fun copyPlainText(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun vibrateScanSuccess(context: Context) {
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
