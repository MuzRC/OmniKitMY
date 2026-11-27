package com.muz.omnikit.tools.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellIdentityNr
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.muz.omnikit.core.model.Language
import com.muz.omnikit.ui.components.PermissionRequestCard
import com.muz.omnikit.ui.components.ToolScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.net.Inet4Address
import java.util.Locale

private enum class SignalTab { WIFI, CELLULAR }

private data class WifiSnapshot(
    val connected: Boolean,
    val ssid: String,
    val bssid: String,
    val rssiDbm: Int,
    val freqMhz: Int,
    val channel: Int,
    val bandLabel: String,
    val linkSpeedMbps: Int,
    val txSpeedMbps: Int?,
    val rxSpeedMbps: Int?,
    val localIp: String
)

private data class NearbyAp(
    val ssid: String,
    val bssid: String,
    val rssiDbm: Int,
    val freqMhz: Int,
    val channel: Int,
    val band: String
)

private data class CellTowerSnapshot(
    val registered: Boolean,
    val tech: String,
    val primaryDbm: Int,
    val metricLabel: String,
    val details: List<Pair<String, String>>
)

private data class CellularSnapshot(
    val operatorName: String,
    val networkTypeLabel: String,
    val simCountry: String,
    val primaryTower: CellTowerSnapshot?,
    val neighborCount: Int
)

@Composable
fun SignalMeterScreen(
    lang: Language,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    fun checkPermissions(): Boolean {
        val loc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val phone = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        return loc && phone
    }

    var hasPerms by remember { mutableStateOf(checkPermissions()) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPerms = checkPermissions()
    }

    var activeTab by remember { mutableStateOf(SignalTab.WIFI) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    var wifiData by remember { mutableStateOf<WifiSnapshot?>(null) }
    var nearbyAps by remember { mutableStateOf<List<NearbyAp>>(emptyList()) }
    val wifiDbmHistory = remember { mutableStateListOf<Int>() }

    var cellData by remember { mutableStateOf<CellularSnapshot?>(null) }
    val cellDbmHistory = remember { mutableStateListOf<Int>() }

    // Poll real Wi-Fi & Cellular hardware telemetry every 1.2s while screen is visible
    LaunchedEffect(hasPerms, refreshTrigger) {
        if (!hasPerms) return@LaunchedEffect
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val telManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        @Suppress("DEPRECATION")
        runCatching { wifiManager?.startScan() }

        while (isActive) {
            val wSnap = readWifiSnapshot(connManager, wifiManager)
            wifiData = wSnap
            if (wSnap.connected && wSnap.rssiDbm in -120..-10) {
                wifiDbmHistory.add(wSnap.rssiDbm)
                if (wifiDbmHistory.size > 45) wifiDbmHistory.removeAt(0)
            }
            nearbyAps = readNearbyWifiAps(wifiManager)

            val cSnap = readCellularSnapshot(context, telManager)
            cellData = cSnap
            val cellDbm = cSnap.primaryTower?.primaryDbm
            if (cellDbm != null && cellDbm in -145..-40) {
                cellDbmHistory.add(cellDbm)
                if (cellDbmHistory.size > 45) cellDbmHistory.removeAt(0)
            }

            delay(1200L)
        }
    }

    ToolScaffold(
        title = lang.tr("Meter Isyarat Rangkaian", "Wi-Fi & Cellular Meter"),
        subtitle = lang.tr("Kekuatan sebenar dBm Wi-Fi & 4G/5G", "Real dBm Wi-Fi & 4G/5G telemetry"),
        onBack = onBack,
        actions = {
            if (hasPerms) {
                IconButton(onClick = { refreshTrigger++ }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = lang.tr("Imbas Semula", "Refresh"))
                }
            }
        }
    ) {
        if (!hasPerms) {
            PermissionRequestCard(
                icon = Icons.Rounded.NetworkCheck,
                title = lang.tr("Kebenaran Lokasi & Telefon Diperlukan", "Location & Phone Permissions Needed"),
                description = lang.tr(
                    "Android memerlukan kebenaran Lokasi dan Status Telefon untuk membaca SSID Wi-Fi, saluran frekuensi, serta kekuatan dBm menara 4G/5G.",
                    "Android requires Location and Phone State permissions to inspect Wi-Fi SSID, frequency channels, and 4G/5G tower dBm readings."
                ),
                buttonLabel = lang.tr("Benarkan Akses", "Grant Permissions"),
                onRequest = {
                    permLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.READ_PHONE_STATE
                        )
                    )
                }
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
            // Mode Switch Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeTab == SignalTab.WIFI,
                    onClick = { activeTab = SignalTab.WIFI },
                    leadingIcon = { Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Wi-Fi", maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = activeTab == SignalTab.CELLULAR,
                    onClick = { activeTab = SignalTab.CELLULAR },
                    leadingIcon = { Icon(Icons.Rounded.SignalCellularAlt, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(lang.tr("Selular 4G/5G", "Cellular 4G/5G"), maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (activeTab == SignalTab.WIFI) {
                val w = wifiData
                if (w == null || !w.connected) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = lang.tr("Tidak Bersambung ke Wi-Fi", "Not Connected to Wi-Fi"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = lang.tr("Sambungkan ke rangkaian Wi-Fi untuk ukur dBm langsung. Senarai Wi-Fi berdekatan masih dipaparkan di bawah.", "Connect to a Wi-Fi network for live dBm tracking. Nearby networks are still listed below."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    val quality = classifyWifiQuality(w.rssiDbm, lang)
                    SignalHeroCard(
                        title = w.ssid,
                        dbm = w.rssiDbm,
                        metricUnit = "dBm (RSSI)",
                        qualityLabel = quality.label,
                        qualityColor = quality.color,
                        progress = ((w.rssiDbm + 100f) / 60f).coerceIn(0f, 1f)
                    )

                    // Live Wi-Fi dBm Graph
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = lang.tr("Graf Kekuatan Isyarat Wi-Fi (-100 ke -30 dBm)", "Live Wi-Fi Signal History (-100 to -30 dBm)"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SignalGraphCanvas(
                                history = wifiDbmHistory,
                                minDbm = -100,
                                maxDbm = -30,
                                lineColor = quality.color,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(84.dp)
                            )
                        }
                    }

                    // Detailed Wi-Fi Radio Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = lang.tr("Butiran Sambungan Wi-Fi", "Wi-Fi Radio Details"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            SignalDetailRow(lang.tr("Jalur & Saluran", "Band & Channel"), "${w.bandLabel} • Ch ${w.channel} (${w.freqMhz} MHz)")
                            SignalDetailRow(lang.tr("Kelajuan Pautan", "Link Speed"), "${w.linkSpeedMbps} Mbps")
                            if (w.txSpeedMbps != null && w.rxSpeedMbps != null) {
                                SignalDetailRow("Tx / Rx", "${w.txSpeedMbps} / ${w.rxSpeedMbps} Mbps")
                            }
                            SignalDetailRow(lang.tr("Alamat IP Tempatan", "Local IP"), w.localIp)
                            SignalDetailRow("BSSID", w.bssid)
                        }
                    }
                }

                // Nearby Access Points List
                if (nearbyAps.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = lang.tr("Rangkaian Wi-Fi Berdekatan (${nearbyAps.size})", "Nearby Wi-Fi Networks (${nearbyAps.size})"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            nearbyAps.take(15).forEachIndexed { idx, ap ->
                                val q = classifyWifiQuality(ap.rssiDbm, lang)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ap.ssid.ifBlank { lang.tr("(Rangkaian Tersembunyi)", "(Hidden SSID)") },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${ap.band} • Ch ${ap.channel} (${ap.freqMhz} MHz)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${ap.rssiDbm} dBm",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = q.color
                                    )
                                }
                                if (idx < minOf(nearbyAps.size, 15) - 1) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            } else {
                // === TAB 2: CELLULAR 4G / 5G SIGNAL ===
                val c = cellData
                val tower = c?.primaryTower
                if (c == null || tower == null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.SignalCellularAlt, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = lang.tr("Tiada Isyarat Menara Selular", "No Cellular Tower Signal"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = lang.tr(
                                    "Pastikan kad SIM aktif, mod pesawat dimatikan, dan Lokasi (GPS) telefon dihidupkan untuk bacaan menara selular.",
                                    "Ensure SIM is active, Airplane mode is off, and device Location is turned on to read cellular tower metrics."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    val quality = classifyCellularQuality(tower.primaryDbm, lang)
                    SignalHeroCard(
                        title = "${c.operatorName.ifBlank { lang.tr("Selular", "Cellular") }} • ${c.networkTypeLabel}",
                        dbm = tower.primaryDbm,
                        metricUnit = "dBm (${tower.metricLabel})",
                        qualityLabel = quality.label,
                        qualityColor = quality.color,
                        progress = ((tower.primaryDbm + 130f) / 60f).coerceIn(0f, 1f)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = lang.tr("Graf Isyarat Selular (-130 ke -65 dBm)", "Live Cellular Signal History (-130 to -65 dBm)"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SignalGraphCanvas(
                                history = cellDbmHistory,
                                minDbm = -130,
                                maxDbm = -65,
                                lineColor = quality.color,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(84.dp)
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
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = lang.tr("Maklumat Menara Berdaftar (${tower.tech})", "Registered Cell Tower (${tower.tech})"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            SignalDetailRow(lang.tr("Rangkaian", "Carrier"), c.operatorName.ifBlank { "--" })
                            SignalDetailRow(lang.tr("Jenis Data", "Data Tech"), c.networkTypeLabel)
                            if (c.simCountry.isNotBlank()) {
                                SignalDetailRow(lang.tr("Negara SIM", "SIM Country"), c.simCountry.uppercase(Locale.US))
                            }
                            tower.details.forEach { (k, v) ->
                                SignalDetailRow(k, v)
                            }
                            SignalDetailRow(lang.tr("Sel Jiran Dikesan", "Neighboring Cells"), "${c.neighborCount}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalHeroCard(
    title: String,
    dbm: Int,
    metricUnit: String,
    qualityLabel: String,
    qualityColor: Color,
    progress: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$dbm",
                fontSize = 54.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = qualityColor
            )
            Text(
                text = metricUnit,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = qualityColor.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, qualityColor.copy(alpha = 0.55f))
            ) {
                Text(
                    text = qualityLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = qualityColor,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                color = qualityColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }
    }
}

@Composable
private fun SignalGraphCanvas(
    history: List<Int>,
    minDbm: Int,
    maxDbm: Int,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawLine(grid, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), 1.dp.toPx())
        drawLine(grid, Offset(0f, h - 1f), Offset(w, h - 1f), 1.dp.toPx())
        if (history.size < 2) return@Canvas
        val span = (maxDbm - minDbm).toFloat().coerceAtLeast(10f)
        val step = w / (history.size - 1)
        val path = Path()
        history.forEachIndexed { idx, v ->
            val x = idx * step
            val norm = ((v - minDbm) / span).coerceIn(0f, 1f)
            val y = h - norm * (h - 8.dp.toPx()) - 4.dp.toPx()
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun SignalDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(10.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

private data class SignalQuality(val label: String, val color: Color)

private fun classifyWifiQuality(dbm: Int, lang: Language): SignalQuality = when {
    dbm >= -55 -> SignalQuality(lang.tr("Cemerlang", "Excellent"), Color(0xFF10B981))
    dbm >= -67 -> SignalQuality(lang.tr("Sangat Baik", "Very Good"), Color(0xFF38BDF8))
    dbm >= -75 -> SignalQuality(lang.tr("Sederhana", "Fair"), Color(0xFFFBBF24))
    dbm >= -85 -> SignalQuality(lang.tr("Lemah", "Weak"), Color(0xFFF97316))
    else -> SignalQuality(lang.tr("Sangat Lemah", "Poor"), Color(0xFFEF4444))
}

private fun classifyCellularQuality(dbm: Int, lang: Language): SignalQuality = when {
    dbm >= -85 -> SignalQuality(lang.tr("Cemerlang", "Excellent"), Color(0xFF10B981))
    dbm >= -98 -> SignalQuality(lang.tr("Baik", "Good"), Color(0xFF38BDF8))
    dbm >= -108 -> SignalQuality(lang.tr("Sederhana", "Fair"), Color(0xFFFBBF24))
    dbm >= -118 -> SignalQuality(lang.tr("Lemah", "Weak"), Color(0xFFF97316))
    else -> SignalQuality(lang.tr("Sangat Lemah", "Very Poor"), Color(0xFFEF4444))
}

@SuppressLint("MissingPermission")
private fun readWifiSnapshot(connManager: ConnectivityManager?, wifiManager: WifiManager?): WifiSnapshot {
    val activeNet = connManager?.activeNetwork
    val caps = activeNet?.let { connManager.getNetworkCapabilities(it) }
    val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

    val wifiInfo: WifiInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        (caps?.transportInfo as? WifiInfo) ?: @Suppress("DEPRECATION") wifiManager?.connectionInfo
    } else {
        @Suppress("DEPRECATION") wifiManager?.connectionInfo
    }

    if (!isWifi || wifiInfo == null || wifiInfo.networkId == -1 && wifiInfo.rssi <= -127) {
        return WifiSnapshot(false, "", "", -127, 0, 0, "--", 0, null, null, "--")
    }

    val rawSsid = wifiInfo.ssid?.trim('"').orEmpty()
    val cleanSsid = if (rawSsid.isBlank() || rawSsid == "<unknown ssid>") "Wi-Fi" else rawSsid
    val freq = wifiInfo.frequency
    val (band, ch) = frequencyToBandAndChannel(freq)

    val ip = activeNet?.let { net ->
        connManager.getLinkProperties(net)?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    } ?: "--"

    val tx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) wifiInfo.txLinkSpeedMbps else null
    val rx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) wifiInfo.rxLinkSpeedMbps else null

    return WifiSnapshot(
        connected = true,
        ssid = cleanSsid,
        bssid = wifiInfo.bssid ?: "--",
        rssiDbm = wifiInfo.rssi,
        freqMhz = freq,
        channel = ch,
        bandLabel = band,
        linkSpeedMbps = wifiInfo.linkSpeed,
        txSpeedMbps = tx,
        rxSpeedMbps = rx,
        localIp = ip
    )
}

@SuppressLint("MissingPermission")
private fun readNearbyWifiAps(wifiManager: WifiManager?): List<NearbyAp> {
    val results: List<ScanResult> = runCatching { wifiManager?.scanResults }.getOrNull() ?: return emptyList()
    return results.map { sr ->
        val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            sr.wifiSsid?.toString()?.trim('"').orEmpty()
        } else {
            @Suppress("DEPRECATION")
            sr.SSID.orEmpty()
        }
        val (band, ch) = frequencyToBandAndChannel(sr.frequency)
        NearbyAp(
            ssid = ssid,
            bssid = sr.BSSID.orEmpty(),
            rssiDbm = sr.level,
            freqMhz = sr.frequency,
            channel = ch,
            band = band
        )
    }.sortedByDescending { it.rssiDbm }
}

private fun frequencyToBandAndChannel(freqMhz: Int): Pair<String, Int> = when (freqMhz) {
    2484 -> "2.4 GHz" to 14
    in 2412..2472 -> "2.4 GHz" to ((freqMhz - 2407) / 5)
    in 5170..5885 -> "5 GHz" to ((freqMhz - 5000) / 5)
    in 5955..7115 -> "6 GHz" to ((freqMhz - 5950) / 5)
    else -> "--" to 0
}

@SuppressLint("MissingPermission")
private fun readCellularSnapshot(context: Context, tm: TelephonyManager?): CellularSnapshot {
    if (tm == null) return CellularSnapshot("", "--", "", null, 0)

    val operator = runCatching { tm.networkOperatorName.orEmpty() }.getOrDefault("")
    val country = runCatching { tm.simCountryIso.orEmpty() }.getOrDefault("")
    val dataType = runCatching { networkTypeToLabel(tm.dataNetworkType) }.getOrDefault("--")

    val cells: List<CellInfo> = runCatching { tm.allCellInfo }.getOrNull() ?: emptyList()
    val registered = cells.firstOrNull { it.isRegistered } ?: cells.firstOrNull()
    val primary = registered?.let { parseCellInfo(it) }
    val neighbors = (cells.size - if (primary != null) 1 else 0).coerceAtLeast(0)

    return CellularSnapshot(
        operatorName = operator,
        networkTypeLabel = dataType,
        simCountry = country,
        primaryTower = primary,
        neighborCount = neighbors
    )
}

private fun parseCellInfo(cell: CellInfo): CellTowerSnapshot? {
    val unavail = Int.MAX_VALUE
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cell is CellInfoNr) {
        val sig = cell.cellSignalStrength as? CellSignalStrengthNr ?: return null
        val id = cell.cellIdentity as? CellIdentityNr
        val rsrp = sig.ssRsrp.takeIf { it != unavail && it in -150..-40 } ?: sig.dbm
        val details = mutableListOf<Pair<String, String>>()
        if (sig.ssRsrq != unavail) details.add("SS-RSRQ" to "${sig.ssRsrq} dB")
        if (sig.ssSinr != unavail) details.add("SS-SINR" to "${sig.ssSinr} dB")
        if (id != null) {
            if (id.pci != unavail) details.add("PCI" to "${id.pci}")
            if (id.nrarfcn != unavail) details.add("NR-ARFCN" to "${id.nrarfcn}")
            if (id.tac != unavail) details.add("TAC" to "${id.tac}")
        }
        return CellTowerSnapshot(cell.isRegistered, "5G NR", rsrp, "SS-RSRP", details)
    }

    return when (cell) {
        is CellInfoLte -> {
            val sig = cell.cellSignalStrength
            val id = cell.cellIdentity
            val rsrp = sig.rsrp.takeIf { it != unavail && it in -150..-40 } ?: sig.dbm
            val details = mutableListOf<Pair<String, String>>()
            if (sig.rsrq != unavail) details.add("RSRQ" to "${sig.rsrq} dB")
            if (sig.rssnr != unavail) details.add("RSSNR" to "${sig.rssnr} dB")
            if (id.pci != unavail) details.add("PCI" to "${id.pci}")
            if (id.earfcn != unavail) details.add("EARFCN" to "${id.earfcn}")
            if (id.ci != unavail) details.add("Cell ID" to "${id.ci}")
            if (id.tac != unavail) details.add("TAC" to "${id.tac}")
            CellTowerSnapshot(cell.isRegistered, "4G LTE", rsrp, "RSRP", details)
        }

        is CellInfoWcdma -> {
            val sig = cell.cellSignalStrength
            val id = cell.cellIdentity
            val details = mutableListOf<Pair<String, String>>()
            if (id.psc != unavail) details.add("PSC" to "${id.psc}")
            if (id.cid != unavail) details.add("CID" to "${id.cid}")
            if (id.lac != unavail) details.add("LAC" to "${id.lac}")
            CellTowerSnapshot(cell.isRegistered, "3G WCDMA", sig.dbm, "RSCP", details)
        }

        is CellInfoGsm -> {
            val sig = cell.cellSignalStrength
            val id = cell.cellIdentity
            val details = mutableListOf<Pair<String, String>>()
            if (id.cid != unavail) details.add("CID" to "${id.cid}")
            if (id.lac != unavail) details.add("LAC" to "${id.lac}")
            CellTowerSnapshot(cell.isRegistered, "2G GSM", sig.dbm, "RSSI", details)
        }

        else -> null
    }
}

private fun networkTypeToLabel(type: Int): String = when (type) {
    20 -> "5G NR" // TelephonyManager.NETWORK_TYPE_NR
    TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
    TelephonyManager.NETWORK_TYPE_HSPAP -> "3G HSPA+"
    TelephonyManager.NETWORK_TYPE_HSPA,
    TelephonyManager.NETWORK_TYPE_HSDPA,
    TelephonyManager.NETWORK_TYPE_HSUPA -> "3G HSPA"
    TelephonyManager.NETWORK_TYPE_UMTS -> "3G UMTS"
    TelephonyManager.NETWORK_TYPE_EDGE -> "2G EDGE"
    TelephonyManager.NETWORK_TYPE_GPRS -> "2G GPRS"
    else -> "Mobile"
}
