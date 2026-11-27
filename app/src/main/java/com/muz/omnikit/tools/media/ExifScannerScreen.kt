package com.muz.omnikit.tools.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private data class ExifReport(
    val previewBitmap: Bitmap?,
    val widthPx: Int,
    val heightPx: Int,
    val gpsLatLon: DoubleArray?,
    val gpsAltitudeMeters: Double?,
    val cameraEntries: List<Pair<String, String>>,
    val photoEntries: List<Pair<String, String>>,
    val totalTagsFound: Int
)

@Composable
fun ExifScannerScreen(
    lang: Language,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var report by remember { mutableStateOf<ExifReport?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isStripping by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        selectedUri = uri
        isLoading = true
        scope.launch {
            report = withContext(Dispatchers.IO) { inspectImageExif(context, uri, lang) }
            isLoading = false
        }
    }

    fun stripMetadataAndExport(shareAfter: Boolean) {
        val uri = selectedUri ?: return
        if (isStripping) return
        isStripping = true
        scope.launch {
            val cleanUri = withContext(Dispatchers.IO) {
                createStrippedCleanImage(context, uri)
            }
            isStripping = false
            if (cleanUri != null) {
                if (shareAfter) {
                    MediaExportHelper.shareUri(
                        context,
                        cleanUri,
                        "image/jpeg",
                        lang.tr("Kongsi Gambar Bersih Tanpa EXIF", "Share Clean EXIF-Free Photo")
                    )
                } else {
                    Toast.makeText(
                        context,
                        lang.tr(
                            "Gambar bersih (0% GPS & EXIF) disimpan ke Galeri (Pictures/OmniKitMY)!",
                            "Clean photo (0% GPS & EXIF) saved to Gallery (Pictures/OmniKitMY)!"
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Toast.makeText(
                    context,
                    lang.tr("Gagal memproses gambar bersih.", "Failed to create clean image."),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    ToolScaffold(
        title = lang.tr("Pengimbas & Buang EXIF", "EXIF Inspector & Stripper"),
        subtitle = lang.tr("Semak GPS/kamera & padam metadata privasi", "Inspect GPS/camera & wipe privacy metadata"),
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
                onClick = { imagePicker.launch("image/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.ImageSearch, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(lang.tr("Pilih Gambar untuk Disemak", "Select Photo to Inspect"), fontWeight = FontWeight.Bold)
            }

            if (isLoading) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(lang.tr("Membaca metadata EXIF...", "Reading EXIF metadata..."), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            val rep = report
            if (rep != null && !isLoading) {
                // Image Preview
                rep.previewBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Selected Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                }

                // Privacy & GPS Alert Banner
                val gps = rep.gpsLatLon
                val hasPrivacyRisk = gps != null || rep.totalTagsFound > 2
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (gps != null) Color(0xFFEF4444).copy(alpha = 0.14f)
                        else if (hasPrivacyRisk) Color(0xFFF59E0B).copy(alpha = 0.14f)
                        else Color(0xFF10B981).copy(alpha = 0.14f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (gps != null) Color(0xFFEF4444)
                        else if (hasPrivacyRisk) Color(0xFFF59E0B)
                        else Color(0xFF10B981)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (gps != null) Icons.Rounded.LocationOn
                                else if (hasPrivacyRisk) Icons.Rounded.WarningAmber
                                else Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = if (gps != null) Color(0xFFEF4444)
                                else if (hasPrivacyRisk) Color(0xFFF59E0B)
                                else Color(0xFF10B981)
                            )
                            Text(
                                text = when {
                                    gps != null -> lang.tr("AMARAN PRIVASI: Koordinat GPS Terdedah!", "PRIVACY ALERT: GPS Coordinates Embedded!")
                                    hasPrivacyRisk -> lang.tr("${rep.totalTagsFound} Tag Metadata EXIF Ditemui", "${rep.totalTagsFound} EXIF Metadata Tags Found")
                                    else -> lang.tr("Gambar Bersih (Tiada GPS / EXIF Sensitif)", "Clean Photo (No GPS / Sensitive EXIF)")
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (gps != null) {
                            val coordStr = String.format(Locale.US, "%.6f, %.6f", gps[0], gps[1])
                            val altStr = rep.gpsAltitudeMeters?.let { String.format(Locale.US, " • Alt %.1f m", it) }.orEmpty()
                            Text(
                                text = "GPS: $coordStr$altStr",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                            OutlinedButton(
                                onClick = {
                                    val geoUri = Uri.parse("geo:${gps[0]},${gps[1]}?q=${gps[0]},${gps[1]}(Photo+Location)")
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, geoUri)) }
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(lang.tr("Lihat Lokasi pada Peta", "View Location on Map"))
                            }
                        }
                    }
                }

                // Strip EXIF & Save / Share Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { stripMetadataAndExport(shareAfter = false) },
                        enabled = !isStripping,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isStripping) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(lang.tr("Buang EXIF & Simpan", "Strip EXIF & Save"), fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedButton(
                        onClick = { stripMetadataAndExport(shareAfter = true) },
                        enabled = !isStripping,
                        modifier = Modifier.height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(lang.tr("Kongsi", "Share"))
                    }
                }

                // Metadata Section: Camera & Device
                if (rep.cameraEntries.isNotEmpty()) {
                    ExifGroupCard(
                        title = lang.tr("Peranti & Kamera", "Device & Camera"),
                        entries = rep.cameraEntries
                    )
                }

                // Metadata Section: Photo & Exposure Settings
                if (rep.photoEntries.isNotEmpty()) {
                    ExifGroupCard(
                        title = lang.tr("Tetapan Gambar & Pendedahan", "Photo & Exposure Details"),
                        entries = rep.photoEntries
                    )
                }
            } else if (!isLoading) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = lang.tr(
                            "Pilih mana-mana gambar untuk melihat maklumat tersembunyi (model telefon, tarikh, ISO, lokasi GPS) dan jana salinan bersih 100% bebas metadata sebelum dikongsi ke media sosial.",
                            "Pick any photo to inspect embedded metadata (phone model, timestamp, ISO, GPS location) and export a 100% metadata-free clean copy before sharing online."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExifGroupCard(title: String, entries: List<Pair<String, String>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            entries.forEachIndexed { idx, (k, v) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = k,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = v,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
                if (idx < entries.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

private fun inspectImageExif(context: Context, uri: Uri, lang: Language): ExifReport {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

    val preview = runCatching {
        val sample = (maxOf(bounds.outWidth, bounds.outHeight) / 900).coerceAtLeast(1)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()

    val camList = mutableListOf<Pair<String, String>>()
    val photoList = mutableListOf<Pair<String, String>>()
    var latLon: DoubleArray? = null
    var altMeters: Double? = null
    var tagCount = 0

    if (bounds.outWidth > 0 && bounds.outHeight > 0) {
        photoList.add(lang.tr("Resolusi", "Resolution") to "${bounds.outWidth} × ${bounds.outHeight} px")
    }

    runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)

            fun addTag(target: MutableList<Pair<String, String>>, label: String, tag: String, suffix: String = "") {
                val value = exif.getAttribute(tag)?.trim()
                if (!value.isNullOrBlank()) {
                    target.add(label to "$value$suffix")
                    tagCount++
                }
            }

            addTag(camList, lang.tr("Jenama", "Make"), ExifInterface.TAG_MAKE)
            addTag(camList, lang.tr("Model", "Model"), ExifInterface.TAG_MODEL)
            addTag(camList, lang.tr("Perisian", "Software"), ExifInterface.TAG_SOFTWARE)
            addTag(camList, lang.tr("Lensa", "Lens"), ExifInterface.TAG_LENS_MODEL)

            addTag(photoList, lang.tr("Tarikh & Masa", "Date Taken"), ExifInterface.TAG_DATETIME_ORIGINAL)
            if (exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL).isNullOrBlank()) {
                addTag(photoList, lang.tr("Tarikh Fail", "Date Modified"), ExifInterface.TAG_DATETIME)
            }
            addTag(photoList, lang.tr("Apertur", "Aperture"), ExifInterface.TAG_F_NUMBER, " f/")
            addTag(photoList, lang.tr("Kelajuan Pengatup", "Exposure"), ExifInterface.TAG_EXPOSURE_TIME, " s")
            addTag(photoList, "ISO", ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
            addTag(photoList, lang.tr("Panjang Fokus", "Focal Length"), ExifInterface.TAG_FOCAL_LENGTH, " mm")
            addTag(photoList, lang.tr("Kilat (Flash)", "Flash"), ExifInterface.TAG_FLASH)
            addTag(photoList, lang.tr("Orientasi", "Orientation"), ExifInterface.TAG_ORIENTATION)

            val coords = exif.latLong
            if (coords != null && coords.size == 2) {
                latLon = coords
                tagCount += 2
                val alt = exif.getAltitude(Double.NaN)
                if (!alt.isNaN()) {
                    altMeters = alt
                    tagCount++
                }
            }
        }
    }

    return ExifReport(
        previewBitmap = preview,
        widthPx = bounds.outWidth,
        heightPx = bounds.outHeight,
        gpsLatLon = latLon,
        gpsAltitudeMeters = altMeters,
        cameraEntries = camList,
        photoEntries = photoList,
        totalTagsFound = tagCount
    )
}

/**
 * Re-encodes pure pixel data into a fresh JPEG stream (applying EXIF rotation visually first)
 * so 100% of GPS, camera model, timestamps, and hidden EXIF/XMP/IPTC chunks are permanently removed.
 */
private fun createStrippedCleanImage(context: Context, uri: Uri): Uri? {
    return runCatching {
        val orientation = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return@runCatching null

        val rotDeg = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        val upright = if (rotDeg != 0f) {
            val m = Matrix().apply { postRotate(rotDeg) }
            val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            if (rotated != raw) raw.recycle()
            rotated
        } else {
            raw
        }

        val name = "OmniKitMY_Clean_${System.currentTimeMillis()}.jpg"
        val outUri = MediaExportHelper.saveBitmapToGallery(
            context = context,
            bitmap = upright,
            displayName = name,
            format = Bitmap.CompressFormat.JPEG,
            quality = 95
        )
        upright.recycle()
        outUri
    }.getOrNull()
}
