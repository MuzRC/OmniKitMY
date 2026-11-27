package com.muz.omnikit.tools.utility

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muz.omnikit.core.hardware.HardwareEventBus
import com.muz.omnikit.core.model.Language
import com.muz.omnikit.ui.components.ToolScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private enum class CounterTab { TALLY, STOPWATCH }

private data class TallyCounterItem(
    val id: String,
    val name: String,
    val count: Int,
    val step: Int = 1
)

private data class StopwatchLap(
    val index: Int,
    val splitMs: Long,
    val totalMs: Long
)

private const val TALLY_PREFS = "omnikit_tally_prefs"
private const val TALLY_LIST_KEY = "counters_json"

@Composable
fun TallyStopwatchScreen(
    lang: Language,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(CounterTab.TALLY) }

    // === TALLY COUNTER STATES ===
    var counters by remember { mutableStateOf(loadCounters(context, lang)) }
    var selectedCounterId by remember { mutableStateOf(counters.first().id) }
    var volumeKeysEnabled by remember { mutableStateOf(true) }
    var hapticEnabled by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }

    val selectedCounter = counters.firstOrNull { it.id == selectedCounterId } ?: counters.first()

    fun updateActiveCounter(transform: (TallyCounterItem) -> TallyCounterItem) {
        val targetId = selectedCounter.id
        val updated = counters.map { if (it.id == targetId) transform(it) else it }
        counters = updated
        saveCounters(context, updated)
    }

    fun stepCounter(deltaMultiplier: Int) {
        updateActiveCounter { item ->
            val next = (item.count + item.step * deltaMultiplier).coerceAtLeast(0)
            item.copy(count = next)
        }
        if (hapticEnabled) pulseTallyVibrate(context)
    }

    val latestStepCounter by rememberUpdatedState(::stepCounter)

    // Bind hardware Volume Up / Down buttons cleanly via MainActivity's HardwareEventBus
    DisposableEffect(activeTab, volumeKeysEnabled) {
        if (activeTab == CounterTab.TALLY && volumeKeysEnabled) {
            HardwareEventBus.volumeKeyInterceptor = { keyCode ->
                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        latestStepCounter(1)
                        true
                    }
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        latestStepCounter(-1)
                        true
                    }
                    else -> false
                }
            }
        } else {
            HardwareEventBus.volumeKeyInterceptor = null
        }
        onDispose {
            HardwareEventBus.volumeKeyInterceptor = null
        }
    }

    // === STOPWATCH STATES ===
    var swRunning by remember { mutableStateOf(false) }
    var swAccumulatedMs by remember { mutableLongStateOf(0L) }
    var swStartRealtimeMs by remember { mutableLongStateOf(0L) }
    var swDisplayMs by remember { mutableLongStateOf(0L) }
    val swLaps = remember { mutableStateListOf<StopwatchLap>() }

    // Drift-free monotonic stopwatch clock using SystemClock.elapsedRealtime()
    LaunchedEffect(swRunning, swStartRealtimeMs, swAccumulatedMs) {
        if (!swRunning) {
            swDisplayMs = swAccumulatedMs
            return@LaunchedEffect
        }
        while (isActive) {
            swDisplayMs = swAccumulatedMs + (SystemClock.elapsedRealtime() - swStartRealtimeMs)
            delay(25L)
        }
    }

    ToolScaffold(
        title = lang.tr("Kaunter & Jam Randik", "Tally & Stopwatch"),
        subtitle = lang.tr("Kira guna butang Volume & jam randik pusingan", "Volume-key tally counter & lap stopwatch"),
        onBack = onBack
    ) {
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
                    selected = activeTab == CounterTab.TALLY,
                    onClick = { activeTab = CounterTab.TALLY },
                    leadingIcon = { Icon(Icons.Rounded.Numbers, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(lang.tr("Kaunter Pintar", "Tally Counter"), maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = activeTab == CounterTab.STOPWATCH,
                    onClick = { activeTab = CounterTab.STOPWATCH },
                    leadingIcon = { Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(lang.tr("Jam Randik", "Stopwatch"), maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (activeTab == CounterTab.TALLY) {
                // Multi-Counter Selector Strip
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(counters, key = { it.id }) { item ->
                        FilterChip(
                            selected = item.id == selectedCounter.id,
                            onClick = { selectedCounterId = item.id },
                            label = { Text("${item.name} (${item.count})", maxLines = 1) }
                        )
                    }
                    item {
                        OutlinedButton(
                            onClick = { showAddDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(lang.tr("Baru", "New"), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Giant Tactile Counter Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedCounter.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row {
                                IconButton(
                                    onClick = { updateActiveCounter { it.copy(count = 0) } },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset", modifier = Modifier.size(20.dp))
                                }
                                if (counters.size > 1) {
                                    IconButton(
                                        onClick = {
                                            val rem = counters.filterNot { it.id == selectedCounter.id }
                                            counters = rem
                                            selectedCounterId = rem.first().id
                                            saveCounters(context, rem)
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Giant Tap-to-Increment Count Display
                        Box(
                            modifier = Modifier
                                .size(196.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                                .clickable { stepCounter(1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${selectedCounter.count}",
                                    fontSize = 58.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = lang.tr("KETIK +${selectedCounter.step}", "TAP +${selectedCounter.step}"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        // Large Minus & Plus Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { stepCounter(-1) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Remove, contentDescription = "Minus")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("-${selectedCounter.step}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Button(
                                onClick = { stepCounter(1) },
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(54.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Plus")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+${selectedCounter.step}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    }
                }

                // Step Size & Hardware Volume Key Toggle Card
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
                        Text(lang.tr("Nilai Langkah (Step)", "Increment Step"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(1, 2, 5, 10).forEach { st ->
                                FilterChip(
                                    selected = selectedCounter.step == st,
                                    onClick = { updateActiveCounter { it.copy(step = st) } },
                                    label = { Text("+$st", maxLines = 1) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text(lang.tr("Kira Guna Butang Volume", "Count with Volume Keys"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(lang.tr("Vol+ tambah, Vol- tolak tanpa tengok skrin", "Vol+ adds, Vol- subtracts eyes-free"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(checked = volumeKeysEnabled, onCheckedChange = { volumeKeysEnabled = it })
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(lang.tr("Getaran Haptik Setiap Kiraan", "Haptic Click on Count"), style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = hapticEnabled, onCheckedChange = { hapticEnabled = it })
                        }
                    }
                }
            } else {
                // === TAB 2: PRECISION LAP STOPWATCH ===
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = formatStopwatchMs(swDisplayMs),
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = if (swRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (swRunning) {
                                        swAccumulatedMs += SystemClock.elapsedRealtime() - swStartRealtimeMs
                                        swRunning = false
                                    } else {
                                        swStartRealtimeMs = SystemClock.elapsedRealtime()
                                        swRunning = true
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = if (swRunning) {
                                    ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White)
                                } else {
                                    ButtonDefaults.buttonColors()
                                }
                            ) {
                                Icon(if (swRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (swRunning) lang.tr("Jeda", "Pause") else lang.tr("Mula", "Start"), fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    if (swRunning) {
                                        val nowTotal = swAccumulatedMs + (SystemClock.elapsedRealtime() - swStartRealtimeMs)
                                        val prevTotal = swLaps.firstOrNull()?.totalMs ?: 0L
                                        swLaps.add(
                                            0,
                                            StopwatchLap(
                                                index = swLaps.size + 1,
                                                splitMs = (nowTotal - prevTotal).coerceAtLeast(0L),
                                                totalMs = nowTotal
                                            )
                                        )
                                    } else {
                                        swAccumulatedMs = 0L
                                        swDisplayMs = 0L
                                        swLaps.clear()
                                    }
                                },
                                enabled = swRunning || swDisplayMs > 0L,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(if (swRunning) Icons.Rounded.Flag else Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (swRunning) lang.tr("Pusingan", "Lap") else lang.tr("Reset", "Reset"))
                            }
                        }
                    }
                }

                if (swLaps.isNotEmpty()) {
                    val fastestSplit = if (swLaps.size >= 2) swLaps.minOf { it.splitMs } else -1L
                    val slowestSplit = if (swLaps.size >= 2) swLaps.maxOf { it.splitMs } else -1L

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(lang.tr("Pusingan", "Lap"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(lang.tr("Selang", "Split"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(lang.tr("Jumlah", "Total"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            swLaps.forEachIndexed { idx, lap ->
                                val splitColor = when (lap.splitMs) {
                                    fastestSplit -> Color(0xFF10B981)
                                    slowestSplit -> Color(0xFFEF4444)
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("#${lap.index}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    Text(formatStopwatchMs(lap.splitMs), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = splitColor)
                                    Text(formatStopwatchMs(lap.totalMs), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (idx < swLaps.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(lang.tr("Tambah Kaunter Baru", "Add New Counter")) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(24) },
                    label = { Text(lang.tr("Nama Kaunter (cth: Kenderaan / Stok)", "Counter Name (e.g., Vehicles / Stock)")) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    val clean = newName.trim().ifBlank { "${lang.tr("Kaunter", "Counter")} ${counters.size + 1}" }
                    val created = TallyCounterItem(id = System.currentTimeMillis().toString(), name = clean, count = 0)
                    val updated = counters + created
                    counters = updated
                    selectedCounterId = created.id
                    saveCounters(context, updated)
                    showAddDialog = false
                }) {
                    Text(lang.tr("Tambah", "Add"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(lang.tr("Batal", "Cancel"))
                }
            }
        )
    }
}

private fun formatStopwatchMs(ms: Long): String {
    val totalCentis = ms / 10L
    val centis = totalCentis % 100L
    val totalSecs = totalCentis / 100L
    val secs = totalSecs % 60L
    val mins = (totalSecs / 60L) % 60L
    val hrs = totalSecs / 3600L
    return if (hrs > 0L) {
        String.format(Locale.US, "%d:%02d:%02d.%02d", hrs, mins, secs, centis)
    } else {
        String.format(Locale.US, "%02d:%02d.%02d", mins, secs, centis)
    }
}

private fun loadCounters(context: Context, lang: Language): List<TallyCounterItem> {
    val raw = context.getSharedPreferences(TALLY_PREFS, Context.MODE_PRIVATE)
        .getString(TALLY_LIST_KEY, null)
    val parsed = if (!raw.isNullOrBlank()) {
        runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                TallyCounterItem(
                    id = obj.optString("id", i.toString()),
                    name = obj.optString("name", "Counter"),
                    count = obj.optInt("count", 0),
                    step = obj.optInt("step", 1).coerceIn(1, 100)
                )
            }
        }.getOrDefault(emptyList())
    } else emptyList()

    return parsed.ifEmpty {
        listOf(TallyCounterItem(id = "default_1", name = lang.tr("Kaunter Utama", "Main Counter"), count = 0, step = 1))
    }
}

private fun saveCounters(context: Context, items: List<TallyCounterItem>) {
    val arr = JSONArray()
    items.forEach { item ->
        arr.put(
            JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("count", item.count)
                put("step", item.step)
            }
        )
    }
    context.getSharedPreferences(TALLY_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(TALLY_LIST_KEY, arr.toString())
        .apply()
}

private fun pulseTallyVibrate(context: Context) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(22L, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
