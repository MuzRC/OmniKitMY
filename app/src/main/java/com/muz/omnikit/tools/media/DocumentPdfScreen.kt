package com.muz.omnikit.tools.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.RotateRight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.muz.omnikit.core.media.MediaExportHelper
import com.muz.omnikit.core.model.Language
import com.muz.omnikit.ui.components.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import android.graphics.Color as AndroidColor

private enum class DocPdfTab { BUILDER, VIEWER }
private enum class DocFilter { ORIGINAL, ENHANCED, BW_SCAN }

private data class DocPageItem(
    val id: Long,
    val originalBitmap: Bitmap,
    val displayBitmap: Bitmap,
    val rotationDeg: Int = 0,
    val filter: DocFilter = DocFilter.ENHANCED
)

@Composable
fun DocumentPdfScreen(
    lang: Language,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf(DocPdfTab.BUILDER) }

    // === BUILDER STATES ===
    var pages by remember { mutableStateOf<List<DocPageItem>>(emptyList()) }
    var docName by remember { mutableStateOf("Dokumen_OmniKitMY") }
    var defaultFilter by remember { mutableStateOf(DocFilter.ENHANCED) }
    var isExporting by remember { mutableStateOf(false) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    // === VIEWER STATES ===
    var viewerUri by remember { mutableStateOf<Uri?>(null) }
    var viewerFileName by remember { mutableStateOf("") }
    var viewerPageCount by remember { mutableIntStateOf(0) }
    var viewerCurrentPage by remember { mutableIntStateOf(0) }
    var viewerPageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var viewerLoading by remember { mutableStateOf(false) }
    var viewerError by remember { mutableStateOf<String?>(null) }
    var activeRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var activePfd by remember { mutableStateOf<android.os.ParcelFileDescriptor?>(null) }

    // Safely release PdfRenderer when leaving screen or switching file
    fun closeViewer() {
        runCatching { activeRenderer?.close() }
        runCatching { activePfd?.close() }
        activeRenderer = null
        activePfd = null
        viewerPageBitmap = null
        viewerPageCount = 0
        viewerCurrentPage = 0
    }

    DisposableEffect(Unit) {
        onDispose { closeViewer() }
    }

    // Camera capture launcher (full resolution photo via FileProvider)
    val takePhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCaptureUri
        if (success && uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { decodeOrientedBitmap(context, uri, 1800) }
                if (bmp != null) {
                    val processed = withContext(Dispatchers.Default) { applyDocFilter(bmp, defaultFilter) }
                    pages = pages + DocPageItem(
                        id = System.nanoTime(),
                        originalBitmap = bmp,
                        displayBitmap = processed,
                        filter = defaultFilter
                    )
                }
            }
        }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createTempCaptureUri(context)
            if (uri != null) {
                pendingCaptureUri = uri
                takePhotoLauncher.launch(uri)
            }
        } else {
            Toast.makeText(
                context,
                lang.tr("Kebenaran kamera diperlukan untuk mengimbas dokumen.", "Camera permission is required to capture documents."),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun launchCameraCapture() {
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            val uri = createTempCaptureUri(context)
            if (uri != null) {
                pendingCaptureUri = uri
                takePhotoLauncher.launch(uri)
            }
        } else {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Multi-image gallery picker for PDF pages
    val pickImagesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val added = withContext(Dispatchers.IO) {
                uris.mapNotNull { u ->
                    val bmp = decodeOrientedBitmap(context, u, 1800) ?: return@mapNotNull null
                    val processed = applyDocFilter(bmp, defaultFilter)
                    DocPageItem(
                        id = System.nanoTime(),
                        originalBitmap = bmp,
                        displayBitmap = processed,
                        filter = defaultFilter
                    )
                }
            }
            if (added.isNotEmpty()) {
                pages = pages + added
            }
        }
    }

    // Pick PDF file for native PdfRenderer viewer
    val pickPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        closeViewer()
        viewerUri = uri
        viewerFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
        viewerError = null
        viewerLoading = true

        scope.launch {
            val opened = withContext(Dispatchers.IO) {
                runCatching {
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                        ?: throw IllegalStateException("Cannot open PDF")
                    val renderer = PdfRenderer(pfd)
                    pfd to renderer
                }
            }
            opened.onSuccess { (pfd, renderer) ->
                activePfd = pfd
                activeRenderer = renderer
                viewerPageCount = renderer.pageCount
                viewerCurrentPage = 0
                if (renderer.pageCount > 0) {
                    viewerPageBitmap = withContext(Dispatchers.IO) { renderPdfPage(renderer, 0) }
                } else {
                    viewerError = lang.tr("Fail PDF ini tiada halaman.", "This PDF contains no pages.")
                }
                viewerLoading = false
            }.onFailure { err ->
                viewerError = err.localizedMessage ?: lang.tr("Gagal membuka fail PDF.", "Failed to open PDF file.")
                viewerLoading = false
            }
        }
    }

    // Render target page when page index changes in Viewer
    LaunchedEffect(viewerCurrentPage, activeRenderer) {
        val renderer = activeRenderer ?: return@LaunchedEffect
        if (viewerCurrentPage in 0 until renderer.pageCount) {
            viewerLoading = true
            viewerPageBitmap = withContext(Dispatchers.IO) { renderPdfPage(renderer, viewerCurrentPage) }
            viewerLoading = false
        }
    }

    fun updatePageTransform(index: Int, newRot: Int, newFilter: DocFilter) {
        val item = pages.getOrNull(index) ?: return
        scope.launch {
            val transformed = withContext(Dispatchers.Default) {
                val rotated = if (newRot % 360 != 0) {
                    val m = Matrix().apply { postRotate(newRot.toFloat()) }
                    Bitmap.createBitmap(
                        item.originalBitmap, 0, 0,
                        item.originalBitmap.width, item.originalBitmap.height, m, true
                    )
                } else {
                    item.originalBitmap
                }
                applyDocFilter(rotated, newFilter)
            }
            pages = pages.toMutableList().also { list ->
                if (index in list.indices) {
                    list[index] = item.copy(
                        displayBitmap = transformed,
                        rotationDeg = newRot % 360,
                        filter = newFilter
                    )
                }
            }
        }
    }

    fun exportPdf(shareAfter: Boolean) {
        if (pages.isEmpty() || isExporting) return
        isExporting = true
        val safeName = docName.trim()
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .ifBlank { "OmniKitMY_Doc" }
            .let { if (it.endsWith(".pdf", ignoreCase = true)) it else "$it.pdf" }

        scope.launch {
            val savedUri = withContext(Dispatchers.IO) {
                if (shareAfter) {
                    val outDir = File(context.cacheDir, "pdf_exports").apply { mkdirs() }
                    val outFile = File(outDir, safeName)
                    val ok = writeBitmapsToA4Pdf(pages.map { it.displayBitmap }, FileOutputStream(outFile))
                    if (ok) FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile) else null
                } else {
                    MediaExportHelper.saveBytesToDownloads(
                        context = context,
                        displayName = safeName,
                        mimeType = "application/pdf"
                    ) { stream ->
                        writeBitmapsToA4Pdf(pages.map { it.displayBitmap }, stream)
                    }
                }
            }
            isExporting = false
            if (savedUri != null) {
                if (shareAfter) {
                    MediaExportHelper.shareUri(context, savedUri, "application/pdf", lang.tr("Kongsi PDF", "Share PDF"))
                } else {
                    Toast.makeText(
                        context,
                        lang.tr("PDF disimpan ke Downloads/OmniKitMY/$safeName", "PDF saved to Downloads/OmniKitMY/$safeName"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Toast.makeText(
                    context,
                    lang.tr("Gagal menjana fail PDF.", "Failed to generate PDF."),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    ToolScaffold(
        title = lang.tr("Dokumen & PDF", "Document & PDF"),
        subtitle = lang.tr("Imbas dokumen, bina PDF & papar halaman PDF", "Scan pages, build PDFs & view PDFs"),
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mode Switch Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeTab == DocPdfTab.BUILDER,
                    onClick = { activeTab = DocPdfTab.BUILDER },
                    leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(lang.tr("Imbas & Bina PDF", "Scan & Build PDF"), maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = activeTab == DocPdfTab.VIEWER,
                    onClick = { activeTab = DocPdfTab.VIEWER },
                    leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(lang.tr("Papar & Ekstrak PDF", "View & Export PDF"), maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (activeTab == DocPdfTab.BUILDER) {
                // Capture & Import Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { launchCameraCapture() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(lang.tr("Tangkap", "Camera"), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { pickImagesLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(lang.tr("Galeri", "Gallery"), maxLines = 1)
                    }
                }

                // Default Scan Enhancement Filter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        DocFilter.ORIGINAL to lang.tr("Asal", "Original"),
                        DocFilter.ENHANCED to lang.tr("Dokumen Jelas", "Enhanced"),
                        DocFilter.BW_SCAN to lang.tr("Hitam Putih", "B&W Scan")
                    ).forEach { (flt, lbl) ->
                        FilterChip(
                            selected = defaultFilter == flt,
                            onClick = { defaultFilter = flt },
                            label = { Text(lbl, maxLines = 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (pages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                Icons.Rounded.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = lang.tr("Belum Ada Halaman Dokumen", "No Document Pages Yet"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = lang.tr(
                                    "Tangkap gambar kertas/resit guna kamera atau pilih beberapa gambar dari galeri untuk digabung jadi satu fail PDF A4.",
                                    "Capture documents with camera or select multiple gallery images to combine into a single A4 PDF."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = docName,
                        onValueChange = { docName = it },
                        label = { Text(lang.tr("Nama Fail PDF", "PDF File Name")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        itemsIndexed(pages, key = { _, item -> item.id }) { idx, item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Image(
                                        bitmap = item.displayBitmap.asImageBitmap(),
                                        contentDescription = "Page ${idx + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(width = 72.dp, height = 96.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White)
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = lang.tr("Halaman ${idx + 1}", "Page ${idx + 1}"),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            listOf(
                                                DocFilter.ORIGINAL to lang.tr("Asal", "Orig"),
                                                DocFilter.ENHANCED to lang.tr("Jelas", "Clr"),
                                                DocFilter.BW_SCAN to "B&W"
                                            ).forEach { (flt, lbl) ->
                                                FilterChip(
                                                    selected = item.filter == flt,
                                                    onClick = { updatePageTransform(idx, item.rotationDeg, flt) },
                                                    label = { Text(lbl, style = MaterialTheme.typography.labelSmall) }
                                                )
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            IconButton(
                                                onClick = { updatePageTransform(idx, item.rotationDeg + 90, item.filter) },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(Icons.Rounded.RotateRight, contentDescription = "Rotate", modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    if (idx > 0) {
                                                        val list = pages.toMutableList()
                                                        val moved = list.removeAt(idx)
                                                        list.add(idx - 1, moved)
                                                        pages = list
                                                    }
                                                },
                                                enabled = idx > 0,
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    if (idx < pages.lastIndex) {
                                                        val list = pages.toMutableList()
                                                        val moved = list.removeAt(idx)
                                                        list.add(idx + 1, moved)
                                                        pages = list
                                                    }
                                                },
                                                enabled = idx < pages.lastIndex,
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(
                                                onClick = { pages = pages.filterNot { it.id == item.id } },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.DeleteOutline,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Export & Share PDF Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { exportPdf(shareAfter = false) },
                            enabled = !isExporting,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(lang.tr("Simpan PDF (${pages.size})", "Save PDF (${pages.size})"), fontWeight = FontWeight.Bold)
                            }
                        }
                        OutlinedButton(
                            onClick = { exportPdf(shareAfter = true) },
                            enabled = !isExporting,
                            modifier = Modifier.height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(lang.tr("Kongsi", "Share"))
                        }
                    }
                }
            } else {
                // === TAB 2: NATIVE PDF VIEWER & PAGE-TO-IMAGE EXPORTER ===
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { pickPdfLauncher.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(lang.tr("Pilih Fail PDF dari Peranti", "Open PDF File from Storage"), fontWeight = FontWeight.Bold)
                    }

                    if (!viewerError.isNullOrBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = viewerError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    if (viewerLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (viewerPageBitmap != null) {
                        Text(
                            text = "$viewerFileName • ${lang.tr("Halaman", "Page")} ${viewerCurrentPage + 1} / $viewerPageCount",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.72f),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            shadowElevation = 4.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Image(
                                bitmap = viewerPageBitmap!!.asImageBitmap(),
                                contentDescription = "PDF Page",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Page Flip Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { if (viewerCurrentPage > 0) viewerCurrentPage -= 1 },
                                enabled = viewerCurrentPage > 0,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(lang.tr("Sebelum", "Prev"))
                            }

                            OutlinedButton(
                                onClick = { if (viewerCurrentPage < viewerPageCount - 1) viewerCurrentPage += 1 },
                                enabled = viewerCurrentPage < viewerPageCount - 1,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(lang.tr("Seterusnya", "Next"))
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }

                        // Export Current PDF Page as High-Res PNG
                        Button(
                            onClick = {
                                val bmp = viewerPageBitmap ?: return@Button
                                val name = "OmniKitMY_PDF_P${viewerCurrentPage + 1}_${System.currentTimeMillis()}.png"
                                val uri = MediaExportHelper.saveBitmapToGallery(context, bmp, name, Bitmap.CompressFormat.PNG)
                                Toast.makeText(
                                    context,
                                    if (uri != null) lang.tr("Halaman disimpan sebagai PNG di Galeri!", "Page saved as PNG to Gallery!")
                                    else lang.tr("Gagal menyimpan gambar.", "Failed to save image."),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(lang.tr("Eksport Halaman Ini ke PNG", "Export This Page as PNG"))
                        }
                    } else if (viewerUri == null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = lang.tr(
                                    "Buka mana-mana fail PDF untuk membaca luar talian dan menukar mana-mana halaman menjadi imej PNG berkualiti tinggi.",
                                    "Open any local PDF file to read offline and export individual pages as crisp PNG images."
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
    }
}

private fun createTempCaptureUri(context: Context): Uri? {
    return runCatching {
        val dir = File(context.cacheDir, "doc_captures").apply { mkdirs() }
        val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()
}

private fun decodeOrientedBitmap(context: Context, uri: Uri, maxDim: Int): Bitmap? {
    return runCatching {
        val optsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, optsBounds)
        }
        val origW = optsBounds.outWidth
        val origH = optsBounds.outHeight
        if (origW <= 0 || origH <= 0) return@runCatching null

        var sample = 1
        while ((origW / sample) > maxDim * 2 || (origH / sample) > maxDim * 2) {
            sample *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return@runCatching null

        val orientation = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val rotDeg = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        val scale = (maxDim.toFloat() / maxOf(raw.width, raw.height)).coerceAtMost(1f)
        if (rotDeg == 0f && scale >= 0.99f) return@runCatching raw

        val matrix = Matrix().apply {
            if (scale < 0.99f) postScale(scale, scale)
            if (rotDeg != 0f) postRotate(rotDeg)
        }
        val result = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (result != raw) raw.recycle()
        result
    }.getOrNull()
}

private fun applyDocFilter(source: Bitmap, filter: DocFilter): Bitmap {
    if (filter == DocFilter.ORIGINAL) return source
    val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val matrix = ColorMatrix()

    when (filter) {
        DocFilter.ENHANCED -> {
            // Boost clarity & paper whiteness for crisp readable documents
            val contrast = 1.32f
            val brightness = -18f
            matrix.set(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, brightness,
                    0f, contrast, 0f, 0f, brightness,
                    0f, 0f, contrast, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        DocFilter.BW_SCAN -> {
            val gray = ColorMatrix().apply { setSaturation(0f) }
            val contrast = 1.85f
            val brightness = -75f
            val highContrast = ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, brightness,
                    0f, contrast, 0f, 0f, brightness,
                    0f, 0f, contrast, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            matrix.setConcat(highContrast, gray)
        }
        DocFilter.ORIGINAL -> Unit
    }

    paint.colorFilter = ColorMatrixColorFilter(matrix)
    canvas.drawBitmap(source, 0f, 0f, paint)
    return out
}

private fun writeBitmapsToA4Pdf(bitmaps: List<Bitmap>, outStream: java.io.OutputStream): Boolean {
    val pdf = PdfDocument()
    return try {
        // Standard A4 @ 150 DPI = 1240 x 1754 px (clean crisp prints with compact size)
        val pageW = 1240
        val pageH = 1754
        val margin = 36f
        val bgPaint = Paint().apply { color = AndroidColor.WHITE }

        bitmaps.forEachIndexed { idx, bmp ->
            val info = PdfDocument.PageInfo.Builder(pageW, pageH, idx + 1).create()
            val page = pdf.startPage(info)
            val canvas = page.canvas
            canvas.drawRect(0f, 0f, pageW.toFloat(), pageH.toFloat(), bgPaint)

            val availW = pageW - margin * 2f
            val availH = pageH - margin * 2f
            val scale = minOf(availW / bmp.width.toFloat(), availH / bmp.height.toFloat())
            val drawW = bmp.width * scale
            val drawH = bmp.height * scale
            val left = (pageW - drawW) / 2f
            val top = (pageH - drawH) / 2f

            canvas.drawBitmap(bmp, null, RectF(left, top, left + drawW, top + drawH), Paint(Paint.FILTER_BITMAP_FLAG))
            pdf.finishPage(page)
        }
        outStream.use { pdf.writeTo(it) }
        true
    } catch (_: Exception) {
        false
    } finally {
        pdf.close()
    }
}

private fun renderPdfPage(renderer: PdfRenderer, pageIndex: Int): Bitmap? {
    return runCatching {
        renderer.openPage(pageIndex).use { page ->
            val targetW = 1200
            val scale = targetW.toFloat() / page.width.coerceAtLeast(1)
            val targetH = (page.height * scale).toInt().coerceIn(200, 2200)
            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(AndroidColor.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        }
    }.getOrNull()
}
