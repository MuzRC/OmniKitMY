package com.muz.omnikit.tools.network

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muz.omnikit.core.hardware.HardwareEventBus
import com.muz.omnikit.core.model.Language
import com.muz.omnikit.ui.components.HardwareMissingCard
import com.muz.omnikit.ui.components.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class NfcTab { READ, WRITE }
private enum class WriteRecordKind { TEXT, URL, PHONE }

private data class DecodedNdefRecord(
    val typeLabel: String,
    val payloadText: String,
    val isUri: Boolean
)

private data class ScannedTagReport(
    val uidHex: String,
    val techList: List<String>,
    val ndefType: String,
    val maxSizeBytes: Int,
    val isWritable: Boolean,
    val records: List<DecodedNdefRecord>,
    val scannedAt: String
)

private val NFC_URI_PREFIXES = arrayOf(
    "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:",
    "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://", "sftp://", "smb://",
    "nfs://", "ftp://", "dav://", "news:", "telnet://", "imap:", "rtsp://", "urn:",
    "pop:", "sip:", "sips:", "tftp:", "btspp://", "btl2cap://", "btgoep://",
    "tcpobex://", "irdaobex://", "file://", "urn:epc:id:", "urn:epc:tag:",
    "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:"
)

@Composable
fun NfcToolScreen(
    lang: Language,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val nfcAdapter = remember(context) { NfcAdapter.getDefaultAdapter(context) }
    val hasNfcHardware = nfcAdapter != null
    var isNfcEnabled by remember { mutableStateOf(nfcAdapter?.isEnabled == true) }

    var activeTab by remember { mutableStateOf(NfcTab.READ) }

    // Read states
    var lastReport by remember { mutableStateOf<ScannedTagReport?>(null) }
    val sessionHistory = remember { mutableStateListOf<ScannedTagReport>() }

    // Write states
    var writeKind by remember { mutableStateOf(WriteRecordKind.TEXT) }
    var writePayload by remember { mutableStateOf("") }
    var writeArmed by remember { mutableStateOf(false) }
    var writeResultSuccess by remember { mutableStateOf<Boolean?>(null) }
    var writeStatusMsg by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        HardwareEventBus.nfcForegroundActive = true
        onDispose {
            HardwareEventBus.nfcForegroundActive = false
        }
    }

    // Listen to real NFC tag taps dispatched from MainActivity via HardwareEventBus
    LaunchedEffect(activeTab, writeArmed, writeKind, writePayload) {
        HardwareEventBus.nfcIntents.collect { intent ->
            isNfcEnabled = nfcAdapter?.isEnabled == true
            val tag = extractTagFromIntent(intent) ?: return@collect
            vibrateNfcTap(context)

            if (activeTab == NfcTab.WRITE && writeArmed) {
                val content = writePayload.trim()
                if (content.isBlank()) {
                    writeArmed = false
                    return@collect
                }
                val message = buildNdefMessage(writeKind, content)
                val outcome = withContext(Dispatchers.IO) {
                    writeNdefToTag(tag, message, lang)
                }
                writeArmed = false
                writeResultSuccess = outcome.first
                writeStatusMsg = outcome.second
            } else {
                val report = withContext(Dispatchers.IO) {
                    inspectScannedTag(tag, intent)
                }
                lastReport = report
                sessionHistory.removeAll { it.uidHex == report.uidHex && it.scannedAt == report.scannedAt }
                sessionHistory.add(0, report)
                if (sessionHistory.size > 15) sessionHistory.removeAt(sessionHistory.lastIndex)
            }
        }
    }

    ToolScaffold(
        title = lang.tr("Pembaca & Penulis NFC", "NFC Reader & Writer"),
        subtitle = lang.tr("Baca UID, rekod NDEF & tulis tag NFC", "Inspect UID/NDEF & write NFC tags"),
        onBack = onBack
    ) {
        if (!hasNfcHardware) {
            HardwareMissingCard(
                title = lang.tr("Cip NFC Tidak Ditemui", "NFC Hardware Not Available"),
                description = lang.tr(
                    "Telefon atau tablet ini tidak mempunyai cip perkakasan NFC.",
                    "This phone or tablet does not include an NFC hardware chip."
                )
            )
            return@ToolScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!isNfcEnabled) {
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
                            text = lang.tr("NFC dimatikan dalam tetapan telefon.", "NFC is disabled in phone settings."),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                runCatching { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
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

            // Mode Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeTab == NfcTab.READ,
                    onClick = {
                        writeArmed = false
                        activeTab = NfcTab.READ
                    },
                    leadingIcon = { Icon(Icons.Rounded.Nfc, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(lang.tr("Baca Tag", "Read Tag"), maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = activeTab == NfcTab.WRITE,
                    onClick = { activeTab = NfcTab.WRITE },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(lang.tr("Tulis Tag", "Write Tag"), maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (activeTab == NfcTab.READ) {
                // Ready-to-Tap NFC Radar Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Nfc,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = lang.tr("Sedia Mengimbas Tag NFC", "Ready to Scan NFC Tag"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = lang.tr(
                                "Sentuhkan kad akses, pelekat NFC, atau tag ke bahagian belakang telefon.",
                                "Hold an NFC sticker, keycard, or tag against the back of your phone."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                // Scanned Tag Details
                val rep = lastReport
                if (rep != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = lang.tr("Maklumat Tag (${rep.scannedAt})", "Tag Details (${rep.scannedAt})"),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = {
                                        copyNfcText(context, "NFC UID", rep.uidHex)
                                        Toast.makeText(context, lang.tr("UID disalin!", "UID copied!"), Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy UID", modifier = Modifier.size(18.dp))
                                }
                            }

                            NfcKeyValRow("UID (Hex)", rep.uidHex)
                            NfcKeyValRow(lang.tr("Teknologi", "Technologies"), rep.techList.joinToString(", "))
                            if (rep.ndefType.isNotBlank()) {
                                NfcKeyValRow(lang.tr("Jenis NDEF", "NDEF Type"), rep.ndefType)
                            }
                            if (rep.maxSizeBytes > 0) {
                                NfcKeyValRow(
                                    lang.tr("Kapasiti / Status", "Capacity / Status"),
                                    "${rep.maxSizeBytes} B • " + if (rep.isWritable) lang.tr("Boleh Tulis", "Writable") else lang.tr("Baca Sahaja", "Read-Only")
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Text(
                                text = lang.tr("Rekod NDEF (${rep.records.size})", "NDEF Records (${rep.records.size})"),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )

                            if (rep.records.isEmpty()) {
                                Text(
                                    text = lang.tr("Tiada rekod NDEF (tag kosong atau format proprietari).", "No NDEF records (blank or proprietary tag)."),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                rep.records.forEachIndexed { idx, rec ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "#${idx + 1} • ${rec.typeLabel}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = rec.payloadText,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        copyNfcText(context, "NFC Record", rec.payloadText)
                                                        Toast.makeText(context, lang.tr("Disalin!", "Copied!"), Toast.LENGTH_SHORT).show()
                                                    },
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(lang.tr("Salin", "Copy"), style = MaterialTheme.typography.labelSmall)
                                                }
                                                if (rec.isUri) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            runCatching {
                                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(rec.payloadText)))
                                                            }
                                                        },
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(lang.tr("Buka", "Open"), style = MaterialTheme.typography.labelSmall)
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

                // Session Scan History
                if (sessionHistory.size > 1) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = lang.tr("Sejarah Sesi (${sessionHistory.size})", "Session History (${sessionHistory.size})"),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { sessionHistory.clear() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                            sessionHistory.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(item.uidHex, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                    Text(item.scannedAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            } else {
                // === TAB 2: WRITE NDEF TAG ===
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = lang.tr("Pilih Jenis Rekod NDEF", "Choose NDEF Record Type"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                WriteRecordKind.TEXT to lang.tr("Teks", "Text"),
                                WriteRecordKind.URL to "URL",
                                WriteRecordKind.PHONE to lang.tr("Telefon", "Phone")
                            ).forEach { (kind, lbl) ->
                                FilterChip(
                                    selected = writeKind == kind,
                                    onClick = {
                                        writeKind = kind
                                        writeArmed = false
                                    },
                                    label = { Text(lbl, maxLines = 1) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = writePayload,
                            onValueChange = {
                                writePayload = it
                                writeArmed = false
                            },
                            label = {
                                Text(
                                    when (writeKind) {
                                        WriteRecordKind.TEXT -> lang.tr("Kandungan Teks", "Text Content")
                                        WriteRecordKind.URL -> lang.tr("Pautan (cth: https://contoh.com)", "URL (e.g., https://example.com)")
                                        WriteRecordKind.PHONE -> lang.tr("Nombor Telefon (cth: +60123456789)", "Phone Number (e.g., +60123456789)")
                                    }
                                )
                            },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Button(
                            onClick = {
                                if (writeArmed) {
                                    writeArmed = false
                                } else if (writePayload.trim().isNotEmpty()) {
                                    writeResultSuccess = null
                                    writeStatusMsg = null
                                    writeArmed = true
                                }
                            },
                            enabled = isNfcEnabled && writePayload.trim().isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = if (writeArmed) {
                                ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B), contentColor = Color.Black)
                            } else {
                                ButtonDefaults.buttonColors()
                            }
                        ) {
                            Icon(Icons.Rounded.Nfc, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (writeArmed) {
                                    lang.tr("SENTUH TAG SEKARANG... (Ketik untuk Batal)", "TAP TAG NOW... (Tap to Cancel)")
                                } else {
                                    lang.tr("Sedia & Tulis ke Tag NFC", "Arm & Write to NFC Tag")
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (!writeStatusMsg.isNullOrBlank()) {
                    val ok = writeResultSuccess == true
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (ok) Color(0xFF10B981).copy(alpha = 0.14f) else MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                        ),
                        border = BorderStroke(1.dp, if (ok) Color(0xFF10B981) else MaterialTheme.colorScheme.error)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = if (ok) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = writeStatusMsg!!,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NfcKeyValRow(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(10.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

private fun extractTagFromIntent(intent: Intent): Tag? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
    }
}

private fun inspectScannedTag(tag: Tag, intent: Intent): ScannedTagReport {
    val uidHex = tag.id?.joinToString(":") { String.format(Locale.US, "%02X", it) } ?: "--"
    val techs = tag.techList.map { it.substringAfterLast('.') }
    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    var ndefType = ""
    var maxSize = 0
    var writable = false
    val records = mutableListOf<DecodedNdefRecord>()

    val ndef = Ndef.get(tag)
    if (ndef != null) {
        ndefType = ndef.type.orEmpty()
        maxSize = ndef.maxSize
        writable = ndef.isWritable
        val msg = runCatching {
            ndef.connect()
            val m = ndef.ndefMessage ?: ndef.cachedNdefMessage
            ndef.close()
            m
        }.getOrNull() ?: ndef.cachedNdefMessage
        msg?.records?.forEach { rec -> records.add(decodeNdefRecord(rec)) }
    } else {
        // Fallback: check NDEF messages attached directly to the intent
        val rawMsgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        }
        rawMsgs?.forEach { parcel ->
            (parcel as? NdefMessage)?.records?.forEach { rec -> records.add(decodeNdefRecord(rec)) }
        }
    }

    return ScannedTagReport(
        uidHex = uidHex,
        techList = techs,
        ndefType = ndefType,
        maxSizeBytes = maxSize,
        isWritable = writable,
        records = records,
        scannedAt = timeStr
    )
}

private fun decodeNdefRecord(record: NdefRecord): DecodedNdefRecord {
    val payload = record.payload ?: ByteArray(0)
    if (record.tnf == NdefRecord.TNF_WELL_KNOWN) {
        if (record.type.contentEquals(NdefRecord.RTD_TEXT) && payload.isNotEmpty()) {
            val status = payload[0].toInt() and 0xFF
            val isUtf16 = (status and 0x80) != 0
            val langLen = status and 0x3F
            val charset = if (isUtf16) StandardCharsets.UTF_16 else StandardCharsets.UTF_8
            val text = if (payload.size > 1 + langLen) {
                String(payload, 1 + langLen, payload.size - 1 - langLen, charset)
            } else ""
            return DecodedNdefRecord("Text", text, isUri = false)
        }
        if (record.type.contentEquals(NdefRecord.RTD_URI) && payload.isNotEmpty()) {
            val prefixCode = payload[0].toInt() and 0xFF
            val prefix = NFC_URI_PREFIXES.getOrElse(prefixCode) { "" }
            val body = String(payload, 1, payload.size - 1, StandardCharsets.UTF_8)
            return DecodedNdefRecord("URI", prefix + body, isUri = true)
        }
    }
    if (record.tnf == NdefRecord.TNF_ABSOLUTE_URI) {
        val uriStr = String(payload, StandardCharsets.UTF_8)
        return DecodedNdefRecord("Absolute URI", uriStr, isUri = true)
    }
    val typeStr = String(record.type, StandardCharsets.UTF_8).ifBlank { "Raw" }
    val textAttempt = String(payload, StandardCharsets.UTF_8)
    val printable = textAttempt.all { it.code in 32..126 || it == '\n' || it == '\r' || it == '\t' }
    val display = if (printable && textAttempt.isNotBlank()) {
        textAttempt
    } else {
        payload.joinToString(" ") { String.format(Locale.US, "%02X", it) }
    }
    return DecodedNdefRecord(typeStr, display, isUri = false)
}

private fun buildNdefMessage(kind: WriteRecordKind, rawInput: String): NdefMessage {
    val record = when (kind) {
        WriteRecordKind.TEXT -> NdefRecord.createTextRecord("en", rawInput)
        WriteRecordKind.URL -> {
            val normalized = if (rawInput.contains("://")) rawInput else "https://$rawInput"
            NdefRecord.createUri(normalized)
        }
        WriteRecordKind.PHONE -> {
            val normalized = if (rawInput.startsWith("tel:", ignoreCase = true)) rawInput else "tel:$rawInput"
            NdefRecord.createUri(normalized)
        }
    }
    return NdefMessage(arrayOf(record))
}

private fun writeNdefToTag(tag: Tag, message: NdefMessage, lang: Language): Pair<Boolean, String> {
    val byteSize = message.toByteArray().size
    val ndef = Ndef.get(tag)
    if (ndef != null) {
        return try {
            ndef.connect()
            if (!ndef.isWritable) {
                ndef.close()
                false to lang.tr("Gagal: Tag NFC ini terkunci (Baca Sahaja).", "Failed: This NFC tag is read-only.")
            } else if (ndef.maxSize < byteSize) {
                ndef.close()
                false to lang.tr(
                    "Gagal: Data ($byteSize B) melebihi kapasiti tag (${ndef.maxSize} B).",
                    "Failed: Data ($byteSize B) exceeds tag capacity (${ndef.maxSize} B)."
                )
            } else {
                ndef.writeNdefMessage(message)
                ndef.close()
                true to lang.tr("Berjaya menulis $byteSize bait ke tag NFC!", "Successfully wrote $byteSize bytes to NFC tag!")
            }
        } catch (e: Exception) {
            runCatching { ndef.close() }
            false to (e.localizedMessage ?: lang.tr("Gagal menulis. Kekalkan tag rapat sehingga selesai.", "Write failed. Keep tag steady until complete."))
        }
    }

    val formatable = NdefFormatable.get(tag)
    if (formatable != null) {
        return try {
            formatable.connect()
            formatable.format(message)
            formatable.close()
            true to lang.tr("Tag diformat & berjaya ditulis ($byteSize bait)!", "Tag formatted & written ($byteSize bytes)!")
        } catch (e: Exception) {
            runCatching { formatable.close() }
            false to (e.localizedMessage ?: lang.tr("Gagal memformat tag NFC.", "Failed to format NFC tag."))
        }
    }

    return false to lang.tr("Tag ini tidak menyokong penulisan NDEF.", "This tag does not support NDEF writing.")
}

private fun copyNfcText(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun vibrateNfcTap(context: Context) {
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
