package com.muz.omnikit.tools.utility

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muz.omnikit.core.model.Language
import com.muz.omnikit.ui.components.ToolScaffold
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private enum class DateCalcTab { DIFF, ADD_SUB, AGE_TIME }

@Composable
fun DateCalculatorScreen(
    lang: Language,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(DateCalcTab.DIFF) }

    // Tab 1: Date Difference
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var endDate by remember { mutableStateOf(LocalDate.now().plusMonths(1)) }
    var includeEndDay by remember { mutableStateOf(false) }

    // Tab 2: Add / Subtract Duration
    var baseDate by remember { mutableStateOf(LocalDate.now()) }
    var isAddOperation by remember { mutableStateOf(true) }
    var deltaYears by remember { mutableStateOf("0") }
    var deltaMonths by remember { mutableStateOf("0") }
    var deltaWeeks by remember { mutableStateOf("0") }
    var deltaDays by remember { mutableStateOf("30") }

    // Tab 3: Age & Shift / Work Hours Duration
    var birthDate by remember { mutableStateOf(LocalDate.of(1995, 1, 1)) }
    var shiftStartTime by remember { mutableStateOf(LocalTime.of(8, 0)) }
    var shiftEndTime by remember { mutableStateOf(LocalTime.of(17, 0)) }
    var breakMinutes by remember { mutableIntStateOf(60) }

    fun openDatePicker(current: LocalDate, onPicked: (LocalDate) -> Unit) {
        DatePickerDialog(
            context,
            { _, year, monthZero, day ->
                onPicked(LocalDate.of(year, monthZero + 1, day))
            },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth
        ).show()
    }

    fun openTimePicker(current: LocalTime, onPicked: (LocalTime) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                onPicked(LocalTime.of(hour, minute))
            },
            current.hour,
            current.minute,
            true
        ).show()
    }

    ToolScaffold(
        title = lang.tr("Kalkulator Tarikh & Masa", "Date & Time Calculator"),
        subtitle = lang.tr("Beza tarikh, tambah hari, umur & jam kerja", "Date diff, add/sub, age & shift hours"),
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
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = activeTab == DateCalcTab.DIFF,
                    onClick = { activeTab = DateCalcTab.DIFF },
                    leadingIcon = { Icon(Icons.Rounded.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(lang.tr("Beza", "Diff"), maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = activeTab == DateCalcTab.ADD_SUB,
                    onClick = { activeTab = DateCalcTab.ADD_SUB },
                    leadingIcon = { Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(lang.tr("Tambah", "Add/Sub"), maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = activeTab == DateCalcTab.AGE_TIME,
                    onClick = { activeTab = DateCalcTab.AGE_TIME },
                    leadingIcon = { Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(lang.tr("Umur/Jam", "Age/Time"), maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }

            when (activeTab) {
                DateCalcTab.DIFF -> {
                    val earlier = if (!startDate.isAfter(endDate)) startDate else endDate
                    val laterRaw = if (!startDate.isAfter(endDate)) endDate else startDate
                    val later = if (includeEndDay) laterRaw.plusDays(1) else laterRaw

                    val period = Period.between(earlier, later)
                    val totalDays = ChronoUnit.DAYS.between(earlier, later)
                    val weekdays = countWeekdays(earlier, later)
                    val weekendDays = (totalDays - weekdays).coerceAtLeast(0L)
                    val weeks = totalDays / 7L
                    val remDays = totalDays % 7L

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
                            DatePickerTile(
                                label = lang.tr("Tarikh Mula", "Start Date"),
                                date = startDate,
                                lang = lang,
                                onClick = { openDatePicker(startDate) { startDate = it } }
                            )
                            DatePickerTile(
                                label = lang.tr("Tarikh Tamat", "End Date"),
                                date = endDate,
                                lang = lang,
                                onClick = { openDatePicker(endDate) { endDate = it } }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = lang.tr("Sertakan hari terakhir (+1 hari)", "Include end day (+1 day)"),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Switch(checked = includeEndDay, onCheckedChange = { includeEndDay = it })
                            }
                        }
                    }

                    // Detailed Difference Breakdown
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
                            Text(
                                text = lang.tr("Tempoh Tepat", "Exact Duration"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = lang.tr(
                                    "${period.years} tahun, ${period.months} bulan, ${period.days} hari",
                                    "${period.years} years, ${period.months} months, ${period.days} days"
                                ),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            DateStatRow(lang.tr("Jumlah Hari", "Total Days"), String.format(Locale.US, "%,d %s", totalDays, lang.tr("hari", "days")))
                            DateStatRow(lang.tr("Minggu & Hari", "Weeks & Days"), "$weeks ${lang.tr("minggu", "wks")} $remDays ${lang.tr("hari", "d")}")
                            DateStatRow(lang.tr("Hari Bekerja (Isn–Jum)", "Weekdays (Mon–Fri)"), "$weekdays ${lang.tr("hari", "days")}")
                            DateStatRow(lang.tr("Hujung Minggu (Sab–Ahd)", "Weekends (Sat–Sun)"), "$weekendDays ${lang.tr("hari", "days")}")
                            DateStatRow(lang.tr("Jumlah Jam", "Total Hours"), String.format(Locale.US, "%,d %s", totalDays * 24L, lang.tr("jam", "hrs")))
                        }
                    }
                }

                DateCalcTab.ADD_SUB -> {
                    val y = deltaYears.toLongOrNull() ?: 0L
                    val m = deltaMonths.toLongOrNull() ?: 0L
                    val w = deltaWeeks.toLongOrNull() ?: 0L
                    val d = deltaDays.toLongOrNull() ?: 0L

                    val resultDate = if (isAddOperation) {
                        baseDate.plusYears(y).plusMonths(m).plusWeeks(w).plusDays(d)
                    } else {
                        baseDate.minusYears(y).minusMonths(m).minusWeeks(w).minusDays(d)
                    }

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
                            DatePickerTile(
                                label = lang.tr("Tarikh Asas", "Base Date"),
                                date = baseDate,
                                lang = lang,
                                onClick = { openDatePicker(baseDate) { baseDate = it } }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = isAddOperation,
                                    onClick = { isAddOperation = true },
                                    label = { Text(lang.tr("+ Tambah Tempoh", "+ Add Duration")) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = !isAddOperation,
                                    onClick = { isAddOperation = false },
                                    label = { Text(lang.tr("- Tolak Tempoh", "- Subtract")) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NumberFieldBox(lang.tr("Tahun", "Years"), deltaYears, { deltaYears = it }, Modifier.weight(1f))
                                NumberFieldBox(lang.tr("Bulan", "Months"), deltaMonths, { deltaMonths = it }, Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NumberFieldBox(lang.tr("Minggu", "Weeks"), deltaWeeks, { deltaWeeks = it }, Modifier.weight(1f))
                                NumberFieldBox(lang.tr("Hari", "Days"), deltaDays, { deltaDays = it }, Modifier.weight(1f))
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = lang.tr("Tarikh Hasil", "Resulting Date"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = formatReadableDate(resultDate, lang),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${dayOfWeekLabel(resultDate.dayOfWeek, lang)} • ${lang.tr("Hari ke-${resultDate.dayOfYear} dalam tahun", "Day ${resultDate.dayOfYear} of year")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                DateCalcTab.AGE_TIME -> {
                    // Section A: Work / Shift Hours Calculator (handles overnight night shifts seamlessly!)
                    val startDt = LocalDateTime.of(LocalDate.now(), shiftStartTime)
                    val endDt = if (!shiftEndTime.isBefore(shiftStartTime)) {
                        LocalDateTime.of(LocalDate.now(), shiftEndTime)
                    } else {
                        LocalDateTime.of(LocalDate.now().plusDays(1), shiftEndTime)
                    }
                    val rawMins = Duration.between(startDt, endDt).toMinutes()
                    val netMins = (rawMins - breakMinutes).coerceAtLeast(0L)
                    val netHours = netMins / 60L
                    val netRemMins = netMins % 60L
                    val netDecimal = netMins / 60.0

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
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = lang.tr("Kalkulator Tempoh Masa / Syif Kerja", "Time & Shift Duration Calculator"),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { openTimePicker(shiftStartTime) { shiftStartTime = it } },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(lang.tr("Mula", "Start"), style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            text = shiftStartTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = { openTimePicker(shiftEndTime) { shiftEndTime = it } },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(lang.tr("Tamat", "End"), style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            text = shiftEndTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Break deduction quick chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(0, 30, 45, 60).forEach { mins ->
                                    FilterChip(
                                        selected = breakMinutes == mins,
                                        onClick = { breakMinutes = mins },
                                        label = {
                                            Text(
                                                if (mins == 0) lang.tr("Tiada Rehat", "No Break") else "-${mins}m",
                                                maxLines = 1
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "${netHours} ${lang.tr("jam", "hr")} ${netRemMins} ${lang.tr("minit", "min")}",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "%.2f %s%s",
                                            netDecimal,
                                            lang.tr("jam perpuluhan", "decimal hours"),
                                            if (shiftEndTime.isBefore(shiftStartTime)) lang.tr(" • Lintas tengah malam", " • Overnight shift") else ""
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Section B: Exact Age & Next Birthday Calculator
                    val today = LocalDate.now()
                    val safeBirth = if (birthDate.isAfter(today)) today else birthDate
                    val agePeriod = Period.between(safeBirth, today)
                    val livedDays = ChronoUnit.DAYS.between(safeBirth, today)
                    var nextBday = safeBirth.withYear(today.year)
                    if (!nextBday.isAfter(today)) nextBday = nextBday.plusYears(1)
                    val daysToBday = ChronoUnit.DAYS.between(today, nextBday)

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
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.Cake, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = lang.tr("Kalkulator Umur Tepat", "Exact Age Calculator"),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            DatePickerTile(
                                label = lang.tr("Tarikh Lahir", "Date of Birth"),
                                date = safeBirth,
                                lang = lang,
                                onClick = { openDatePicker(safeBirth) { birthDate = it } }
                            )
                            DateStatRow(
                                lang.tr("Umur Semasa", "Exact Age"),
                                lang.tr(
                                    "${agePeriod.years} thn, ${agePeriod.months} bln, ${agePeriod.days} hari",
                                    "${agePeriod.years} yrs, ${agePeriod.months} mos, ${agePeriod.days} days"
                                )
                            )
                            DateStatRow(lang.tr("Lahir Pada Hari", "Born On"), dayOfWeekLabel(safeBirth.dayOfWeek, lang))
                            DateStatRow(lang.tr("Jumlah Hari Hidup", "Total Days Lived"), String.format(Locale.US, "%,d %s", livedDays, lang.tr("hari", "days")))
                            DateStatRow(lang.tr("Hari Jadi Seterusnya", "Next Birthday"), lang.tr("Dalam $daysToBday hari (${dayOfWeekLabel(nextBday.dayOfWeek, lang)})", "In $daysToBday days (${dayOfWeekLabel(nextBday.dayOfWeek, lang)})"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DatePickerTile(
    label: String,
    date: LocalDate,
    lang: Language,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "${formatReadableDate(date, lang)} (${dayOfWeekLabel(date.dayOfWeek, lang)})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun NumberFieldBox(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            if (raw.length <= 5 && raw.all { it.isDigit() }) onChange(raw)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun DateStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

private fun countWeekdays(start: LocalDate, endExclusive: LocalDate): Long {
    val totalDays = ChronoUnit.DAYS.between(start, endExclusive)
    if (totalDays <= 0) return 0L
    val fullWeeks = totalDays / 7L
    var count = fullWeeks * 5L
    var cursor = start.plusDays(fullWeeks * 7L)
    while (cursor.isBefore(endExclusive)) {
        if (cursor.dayOfWeek != DayOfWeek.SATURDAY && cursor.dayOfWeek != DayOfWeek.SUNDAY) {
            count++
        }
        cursor = cursor.plusDays(1)
    }
    return count
}

private fun formatReadableDate(date: LocalDate, lang: Language): String {
    val monthsMs = arrayOf("Jan", "Feb", "Mac", "Apr", "Mei", "Jun", "Jul", "Ogo", "Sep", "Okt", "Nov", "Dis")
    val monthsEn = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val m = if (lang == Language.MS) monthsMs[date.monthValue - 1] else monthsEn[date.monthValue - 1]
    return "${date.dayOfMonth} $m ${date.year}"
}

private fun dayOfWeekLabel(dow: DayOfWeek, lang: Language): String = when (dow) {
    DayOfWeek.MONDAY -> lang.tr("Isnin", "Monday")
    DayOfWeek.TUESDAY -> lang.tr("Selasa", "Tuesday")
    DayOfWeek.WEDNESDAY -> lang.tr("Rabu", "Wednesday")
    DayOfWeek.THURSDAY -> lang.tr("Khamis", "Thursday")
    DayOfWeek.FRIDAY -> lang.tr("Jumaat", "Friday")
    DayOfWeek.SATURDAY -> lang.tr("Sabtu", "Saturday")
    DayOfWeek.SUNDAY -> lang.tr("Ahad", "Sunday")
}
