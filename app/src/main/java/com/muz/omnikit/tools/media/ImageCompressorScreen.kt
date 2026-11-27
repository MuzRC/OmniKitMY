package com.muz.omnikit.tools.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.muz.omnikit.core.media.MediaExportHelper
import com.muz.omnikit.core.model.Language
import com.muz.omnikit.ui.components.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.roundToInt

private enum class OutImageFormat(val label: String, val ext: String, val mime: String) {
    JPEG("JPG", "jpg", "image/jpeg"),
    WEBP("WebP", "webp", "image/webp"),
    PNG("PNG", "png", "image/png");

    fun toBitmapFormat(): Bitmap.CompressFormat = when (this) {
        JPEG -> Bitmap.CompressFormat.JPEG
        PNG -> Bitmap.CompressFormat.PNG
        WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
    }
}

@Composable
fun ImageCompressorScreen(
    lang: Language,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var originalSizeBytes by remember { mutableLongStateOf(0L) }
    var origWidth by remember { mutableIntStateOf(0) }
    var origHeight by remember { mutableIntStateOf(0) }

    var outFormat by remember { mutableStateOf(OutImageFormat.JPEG) }
    var quality by remember { mutableFloatStateOf(78f) }
    var scalePercent by remember { mutableIntStateOf(100) } // 100, 75, 50, 25

    var estimatedBytes by remember { mutableLongStateOf(0L) }
    var outWidth by remember { mutableIntStateOf(0) }
    var outHeight by remember { mutableIntStateOf(0) }
    var isCalculating by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isCalculating = true
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val fileSize = queryUriSize(context, uri)
                val bmp = loadFullOrientedBitmap(context, uri)
                fileSize to bmp
            }
            originalSizeBytes = loaded.first
            sourceBitmap = loaded.second
            origWidth = loaded.second?.width ?: 0
            origHeight = loaded.second?.height ?: 0
            isCalculating = false
        }
    }

    // Recompute exact real compressed output size whenever format, quality or scale changes
    LaunchedEffect(sourceBitmap, outFormat, quality, scalePercent) {
        val src = sourceBitmap ?: return@LaunchedEffect
        isCalculating = true
        delay(120) // Debounce slider drags cleanly
        val result = withContext(Dispatchers.Default) {
            val scaled = resizeBitmapPercent(src, scalePercent)
            val baos = ByteArrayOutputStream()
            scaled.compress(outFormat.toBitmapFormat(), quality.roundToInt().coerceIn(10, 100), baos)
            val bytesLen = baos.size().toLong()
            val w = scaled.width
            val h = scaled.height
            if (scaled != src) scaled.recycle()
            Triple(bytesLen, w, h)
        }
        estimatedBytes = result.first
        outWidth = result.second
        outHeight = result.third
        isCalculating = false
    }

    fun exportCompressed(shareAfter: Boolean) {
        val src = sourceBitmap ?: return
        if (isSaving) return
        isSaving = true
        scope.launch {
            val savedUri = withContext(Dispatchers.IO) {
                val scaled = resizeBitmapPercent(src, scalePercent)
                val name = "OmniKitMY_Comp_${System.currentTimeMillis()}.${outFormat.ext}"
                val uri = MediaExportHelper.saveBitmapToGallery(
                    context = context,
                    bitmap = scaled,
                    displayName = name,
                    format = outFormat.toBitmapFormat(),
                    quality = quality.roundToInt().coerceIn(10, 100)
                )
                if (scaled != src) scaled.recycle()
                uri
            }
            isSaving = false
            if (savedUri != null) {
                if (shareAfter) {
                    MediaExportHelper.shareUri(context, savedUri, outFormat.mime, lang.tr("Kongsi Imej", "Share Image"))
                } else {
                    Toast.makeText(
                        context,
                        lang.tr("Imej dimampatkan & disimpan ke Galeri!", "Compressed image saved to Gallery!"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Toast.makeText(
                    context,
                    lang.tr("Gagal menyimpan imej.", "Failed to save image."),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    ToolScaffold(
        title = lang.tr("Pemampat & Penukar Imej", "Image Compressor"),
        subtitle = lang.tr("Kecilkan saiz fail & tukar JPG / WebP / PNG", "Reduce file size & convert JPG / WebP / PNG"),
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { picker.launch("image/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(lang.tr("Pilih Gambar dari Galeri", "Pick Image from Gallery"), fontWeight = FontWeight.Bold)
            }

            val bmp = sourceBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(185.dp)
                        .clip(RoundedCornerShape(18.dp))
                )

                // Before vs After Size Comparison Card
                val savedPct = if (originalSizeBytes > 0 && estimatedBytes > 0) {
                    ((originalSizeBytes - estimatedBytes).toDouble() / originalSizeBytes * 100.0)
                } else {
                    0.0
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SizeBoxCard(
                        title = lang.tr("Asal", "Original"),
                        sizeText = if (originalSizeBytes > 0) formatBytes(originalSizeBytes) else "--",
                        dimText = "${origWidth}×${origHeight}",
                        accent = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    SizeBoxCard(
                        title = lang.tr("Hasil (${outFormat.label})", "Output (${outFormat.label})"),
                        sizeText = if (isCalculating) "..." else formatBytes(estimatedBytes),
                        dimText = "${outWidth}×${outHeight}",
                        accent = if (savedPct >= 0) Color(0xFF10B981) else Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (!isCalculating && originalSizeBytes > 0 && estimatedBytes > 0) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (savedPct >= 0) Color(0xFF10B981).copy(alpha = 0.14f) else Color(0xFFF59E0B).copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = if (savedPct >= 0) {
                                lang.tr(
                                    "Jimat ruang sebanyak %.1f%% (%s dikurangkan)",
                                    "Space saved: %.1f%% (%s smaller)"
                                ).format(Locale.US, savedPct, formatBytes(originalSizeBytes - estimatedBytes))
                            } else {
                                lang.tr(
                                    "Saiz meningkat %.1f%% (PNG tanpa mampatan biasanya lebih besar dari JPG)",
                                    "Size increased %.1f%% (Lossless PNG is usually larger than JPG)"
                                ).format(Locale.US, -savedPct)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (savedPct >= 0) Color(0xFF10B981) else Color(0xFFF59E0B),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Controls Card: Format, Quality, Resolution Scale
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
                        Text(lang.tr("Format Fail Keluaran", "Output Format"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutImageFormat.entries.forEach { fmt ->
                                FilterChip(
                                    selected = outFormat == fmt,
                                    onClick = { outFormat = fmt },
                                    label = { Text(fmt.label, maxLines = 1) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (outFormat != OutImageFormat.PNG) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(lang.tr("Kualiti Mampatan", "Compression Quality"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${quality.roundToInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = quality,
                                onValueChange = { quality = it },
                                valueRange = 10f..100f
                            )
                        }

                        Text(lang.tr("Skala Resolusi", "Resolution Scale"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(100, 75, 50, 25).forEach { pct ->
                                FilterChip(
                                    selected = scalePercent == pct,
                                    onClick = { scalePercent = pct },
                                    label = { Text("$pct%", maxLines = 1) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Save & Share Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { exportCompressed(shareAfter = false) },
                        enabled = !isSaving && !isCalculating,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(lang.tr("Simpan ke Galeri", "Save to Gallery"), fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedButton(
                        onClick = { exportCompressed(shareAfter = true) },
                        enabled = !isSaving && !isCalculating,
                        modifier = Modifier.height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(lang.tr("Kongsi", "Share"))
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Compress, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                        Text(
                            text = lang.tr(
                                "Pilih gambar untuk lihat anggaran saiz fail masa nyata sebelum simpan dalam format JPG, WebP, atau PNG.",
                                "Pick a photo to preview exact real-time file size savings before saving as JPG, WebP, or PNG."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SizeBoxCard(
    title: String,
    sizeText: String,
    dimText: String,
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
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(3.dp))
            Text(sizeText, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = accent)
            Text(dimText, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun queryUriSize(context: Context, uri: Uri): Long {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0) return@runCatching cursor.getLong(idx)
            }
        }
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().size.toLong() } ?: 0L
    }.getOrDefault(0L)
}

private fun loadFullOrientedBitmap(context: Context, uri: Uri): Bitmap? {
    return runCatching {
        val orientation = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 4096) sample *= 2

        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null

        val rotDeg = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotDeg == 0f) return@runCatching raw
        val m = Matrix().apply { postRotate(rotDeg) }
        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        if (rotated != raw) raw.recycle()
        rotated
    }.getOrNull()
}

private fun resizeBitmapPercent(source: Bitmap, percent: Int): Bitmap {
    if (percent >= 100) return source
    val factor = (percent / 100f).coerceIn(0.1f, 1f)
    val nw = (source.width * factor).roundToInt().coerceAtLeast(16)
    val nh = (source.height * factor).roundToInt().coerceAtLeast(16)
    return Bitmap.createScaledBitmap(source, nw, nh, true)
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576L -> String.format(Locale.US, "%.2f MB", bytes / 1_048_576.0)
    bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
