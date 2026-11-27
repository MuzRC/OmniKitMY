package com.muz.omnikit.tools.camera

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.muz.omnikit.core.model.Language
import com.muz.omnikit.ui.components.PermissionRequestCard
import com.muz.omnikit.ui.components.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@Composable
fun OcrScannerScreen(
    lang: Language,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    var extractedText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var liveAutoScan by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val singleCaptureTrigger = remember { AtomicBoolean(false) }
    val liveAutoRef = remember { AtomicBoolean(false) }
    liveAutoRef.set(liveAutoScan)

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isProcessing = true
        statusMessage = null
        scope.launch {
            val text = withContext(Dispatchers.IO) { recognizeTextFromUri(context, uri) }
            isProcessing = false
            if (!text.isNullOrBlank()) {
                extractedText = text
                statusMessage = null
            } else {
                statusMessage = lang.tr(
                    "Tiada teks jelas ditemui dalam gambar ini.",
                    "No clear text found in this image."
                )
            }
        }
    }

    val wordCount = remember(extractedText) {
        extractedText.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    }

    ToolScaffold(
        title = lang.tr("Pengimbas Teks OCR", "OCR Text Scanner"),
        subtitle = lang.tr("Ekstrak teks 100% luar talian (ML Kit)", "100% offline text extraction (ML Kit)"),
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!hasCameraPerm) {
                PermissionRequestCard(
                    icon = Icons.Rounded.CameraAlt,
                    title = lang.tr("Kebenaran Kamera Diperlukan", "Camera Permission Needed"),
                    description = lang.tr(
                        "Benarkan akses kamera untuk mengimbas teks dari resit, papan tanda, atau dokumen secara langsung.",
                        "Allow camera access to scan text from receipts, signs, or documents live."
                    ),
                    buttonLabel = lang.tr("Benarkan Kamera", "Allow Camera"),
                    onRequest = { permLauncher.launch(Manifest.permission.CAMERA) }
                )
            } else {
                // CameraX Live Viewfinder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.35f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    OcrCameraView(
                        torchOn = torchOn,
                        singleCaptureTrigger = singleCaptureTrigger,
                        liveAutoRef = liveAutoRef,
                        onCameraBound = { cam ->
                            cameraRef = cam
                            hasTorch = cam.cameraInfo.hasFlashUnit()
                        },
                        onTextRecognized = { text ->
                            isProcessing = false
                            if (text.isNotBlank()) {
                                extractedText = text
                                statusMessage = null
                            } else if (!liveAutoScan) {
                                statusMessage = lang.tr("Tiada teks dikesan. Halakan lebih dekat atau nyalakan flash.", "No text detected. Move closer or enable flash.")
                            }
                        },
                        onError = { err ->
                            isProcessing = false
                            statusMessage = err
                        }
                    )

                    // Top Torch Button
                    if (hasTorch) {
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

                    // Live Mode Badge
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = if (liveAutoScan) lang.tr("● IMBASAN LANGSUNG", "● LIVE AUTO SCAN") else lang.tr("SEDIA TANGKAP", "TAP TO SCAN"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (liveAutoScan) Color(0xFF10B981) else Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    if (isProcessing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // Scan Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            isProcessing = true
                            statusMessage = null
                            singleCaptureTrigger.set(true)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(lang.tr("Tangkap & Baca Teks", "Scan Frame"), fontWeight = FontWeight.Bold)
                    }

                    FilterChip(
                        selected = liveAutoScan,
                        onClick = { liveAutoScan = !liveAutoScan },
                        leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(lang.tr("Auto", "Live")) }
                    )
                }
            }

            // Pick Image from Gallery
            OutlinedButton(
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(lang.tr("Ekstrak Teks dari Galeri", "Extract Text from Gallery"))
            }

            if (!statusMessage.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = statusMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Extracted Editable Result Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = lang.tr("Hasil Teks Diekstrak", "Extracted Text"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$wordCount ${lang.tr("perkataan", "words")} • ${extractedText.length} ${lang.tr("aksara", "chars")}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedTextField(
                        value = extractedText,
                        onValueChange = { extractedText = it },
                        placeholder = {
                            Text(
                                lang.tr(
                                    "Teks yang dikesan akan muncul di sini dan boleh disunting terus...",
                                    "Detected text will appear here and can be edited directly..."
                                )
                            )
                        },
                        minLines = 5,
                        maxLines = 12,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (extractedText.isNotBlank()) {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("OCR Text", extractedText))
                                    Toast.makeText(context, lang.tr("Teks disalin!", "Text copied!"), Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = extractedText.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(lang.tr("Salin", "Copy"))
                        }

                        OutlinedButton(
                            onClick = {
                                if (extractedText.isNotBlank()) {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, extractedText)
                                    }
                                    context.startActivity(Intent.createChooser(intent, lang.tr("Kongsi Teks", "Share Text")))
                                }
                            },
                            enabled = extractedText.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(lang.tr("Kongsi", "Share"))
                        }

                        IconButton(
                            onClick = { extractedText = "" },
                            enabled = extractedText.isNotBlank()
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = lang.tr("Padam", "Clear"))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun OcrCameraView(
    torchOn: Boolean,
    singleCaptureTrigger: AtomicBoolean,
    liveAutoRef: AtomicBoolean,
    onCameraBound: (Camera) -> Unit,
    onTextRecognized: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val busy = remember { AtomicBoolean(false) }
    val lastLiveScanMs = remember { AtomicLong(0L) }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

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
                    val manual = singleCaptureTrigger.compareAndSet(true, false)
                    val now = System.currentTimeMillis()
                    val autoDue = liveAutoRef.get() && (now - lastLiveScanMs.get() >= 900L)

                    if ((!manual && !autoDue) || !busy.compareAndSet(false, true)) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    val mediaImage = imageProxy.image
                    if (mediaImage == null) {
                        busy.set(false)
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    if (autoDue) lastLiveScanMs.set(now)

                    val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    recognizer.process(input)
                        .addOnSuccessListener { visionText ->
                            val result = visionText.text.trim()
                            ContextCompat.getMainExecutor(context).execute {
                                onTextRecognized(result)
                            }
                        }
                        .addOnFailureListener { e ->
                            ContextCompat.getMainExecutor(context).execute {
                                onError(e.localizedMessage ?: "OCR error")
                            }
                        }
                        .addOnCompleteListener {
                            busy.set(false)
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
            runCatching { recognizer.close() }
            runCatching { executor.shutdown() }
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

private suspend fun recognizeTextFromUri(context: Context, uri: Uri): String? {
    return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        try {
            val input = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(input)
                .addOnSuccessListener { visionText ->
                    recognizer.close()
                    if (cont.isActive) cont.resumeWith(Result.success(visionText.text.trim()))
                }
                .addOnFailureListener {
                    recognizer.close()
                    if (cont.isActive) cont.resumeWith(Result.success(null))
                }
        } catch (_: Exception) {
            if (cont.isActive) cont.resumeWith(Result.success(null))
        }
    }
}
