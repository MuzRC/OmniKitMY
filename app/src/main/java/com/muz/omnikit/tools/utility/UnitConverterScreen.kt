package com.muz.omnikit.tools.utility

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muz.omnikit.core.model.Language
import com.muz.omnikit.ui.components.ToolScaffold
import java.math.BigDecimal
import java.math.MathContext
import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.abs

private data class UnitDef(
    val symbol: String,
    val nameMs: String,
    val nameEn: String,
    val toBase: (Double) -> Double,
    val fromBase: (Double) -> Double
) {
    fun name(lang: Language): String = lang.tr(nameMs, nameEn)
}

private data class UnitCategoryDef(
    val id: String,
    val titleMs: String,
    val titleEn: String,
    val units: List<UnitDef>
) {
    fun title(lang: Language): String = lang.tr(titleMs, titleEn)
}

private fun linearUnit(symbol: String, ms: String, en: String, factorToBase: Double): UnitDef =
    UnitDef(symbol, ms, en, toBase = { it * factorToBase }, fromBase = { it / factorToBase })

private val UNIT_CATEGORIES: List<UnitCategoryDef> = listOf(
    UnitCategoryDef(
        id = "length", titleMs = "Panjang", titleEn = "Length",
        units = listOf(
            linearUnit("mm", "Milimeter", "Millimeter", 0.001),
            linearUnit("cm", "Sentimeter", "Centimeter", 0.01),
            linearUnit("m", "Meter", "Meter", 1.0),
            linearUnit("km", "Kilometer", "Kilometer", 1000.0),
            linearUnit("in", "Inci", "Inch", 0.0254),
            linearUnit("ft", "Kaki", "Foot", 0.3048),
            linearUnit("yd", "Ela", "Yard", 0.9144),
            linearUnit("mi", "Batu", "Mile", 1609.344),
            linearUnit("nmi", "Batu Nautika", "Nautical Mile", 1852.0)
        )
    ),
    UnitCategoryDef(
        id = "mass", titleMs = "Berat / Jisim", titleEn = "Mass / Weight",
        units = listOf(
            linearUnit("mg", "Miligram", "Milligram", 1e-6),
            linearUnit("g", "Gram", "Gram", 0.001),
            linearUnit("kg", "Kilogram", "Kilogram", 1.0),
            linearUnit("t", "Tan Metrik", "Metric Ton", 1000.0),
            linearUnit("oz", "Auns", "Ounce", 0.028349523125),
            linearUnit("lb", "Paun", "Pound", 0.45359237),
            linearUnit("kati", "Kati (MY)", "Kati (MY)", 0.60478982),
            linearUnit("tahil", "Tahil (MY)", "Tahil (MY)", 0.03779936375)
        )
    ),
    UnitCategoryDef(
        id = "temperature", titleMs = "Suhu", titleEn = "Temperature",
        units = listOf(
            UnitDef("°C", "Celsius", "Celsius", { it }, { it }),
            UnitDef("°F", "Fahrenheit", "Fahrenheit", { (it - 32.0) * 5.0 / 9.0 }, { it * 9.0 / 5.0 + 32.0 }),
            UnitDef("K", "Kelvin", "Kelvin", { it - 273.15 }, { it + 273.15 })
        )
    ),
    UnitCategoryDef(
        id = "area", titleMs = "Luas", titleEn = "Area",
        units = listOf(
            linearUnit("cm²", "Sentimeter Persegi", "Square Centimeter", 0.0001),
            linearUnit("m²", "Meter Persegi", "Square Meter", 1.0),
            linearUnit("ft²", "Kaki Persegi", "Square Foot", 0.09290304),
            linearUnit("yd²", "Ela Persegi", "Square Yard", 0.83612736),
            linearUnit("ekar", "Ekar", "Acre", 4046.8564224),
            linearUnit("ha", "Hektar", "Hectare", 10000.0),
            linearUnit("km²", "Kilometer Persegi", "Square Kilometer", 1_000_000.0)
        )
    ),
    UnitCategoryDef(
        id = "volume", titleMs = "Isipadu", titleEn = "Volume",
        units = listOf(
            linearUnit("mL", "Mililiter", "Milliliter", 0.001),
            linearUnit("L", "Liter", "Liter", 1.0),
            linearUnit("m³", "Meter Padu", "Cubic Meter", 1000.0),
            linearUnit("tsp", "Sudu Teh", "Teaspoon (US)", 0.00492892159375),
            linearUnit("tbsp", "Sudu Besar", "Tablespoon (US)", 0.01478676478125),
            linearUnit("cup", "Cawan (250mL)", "Metric Cup", 0.25),
            linearUnit("fl oz", "Auns Cecair (US)", "Fluid Ounce (US)", 0.0295735295625),
            linearUnit("gal US", "Gelen (US)", "Gallon (US)", 3.785411784),
            linearUnit("gal UK", "Gelen (Imperial)", "Gallon (UK)", 4.54609)
        )
    ),
    UnitCategoryDef(
        id = "speed", titleMs = "Kelajuan", titleEn = "Speed",
        units = listOf(
            linearUnit("m/s", "Meter / Saat", "Meter / Second", 1.0),
            linearUnit("km/j", "Kilometer / Jam", "Kilometer / Hour", 1.0 / 3.6),
            linearUnit("mph", "Batu / Jam", "Mile / Hour", 0.44704),
            linearUnit("kn", "Knot", "Knot", 0.5144444444),
            linearUnit("Mach", "Mach (Udara)", "Mach (Sea Level)", 343.0)
        )
    ),
    UnitCategoryDef(
        id = "time", titleMs = "Masa", titleEn = "Time",
        units = listOf(
            linearUnit("ms", "Milisaat", "Millisecond", 0.001),
            linearUnit("s", "Saat", "Second", 1.0),
            linearUnit("min", "Minit", "Minute", 60.0),
            linearUnit("j", "Jam", "Hour", 3600.0),
            linearUnit("hari", "Hari", "Day", 86400.0),
            linearUnit("mgg", "Minggu", "Week", 604800.0),
            linearUnit("bln", "Bulan (30h)", "Month (30d)", 2_592_000.0),
            linearUnit("thn", "Tahun (365h)", "Year (365d)", 31_536_000.0)
        )
    ),
    UnitCategoryDef(
        id = "data", titleMs = "Data Digital", titleEn = "Digital Storage",
        units = listOf(
            linearUnit("bit", "Bit", "Bit", 0.125),
            linearUnit("B", "Bait", "Byte", 1.0),
            linearUnit("KB", "Kilobait (1024)", "Kibibyte (KB)", 1024.0),
            linearUnit("MB", "Megabait", "Megabyte (MB)", 1_048_576.0),
            linearUnit("GB", "Gigabait", "Gigabyte (GB)", 1_073_741_824.0),
            linearUnit("TB", "Terabait", "Terabyte (TB)", 1_099_511_627_776.0),
            linearUnit("Mbps", "Megabit (Mb)", "Megabit (Mb)", 131_072.0)
        )
    ),
    UnitCategoryDef(
        id = "pressure", titleMs = "Tekanan", titleEn = "Pressure",
        units = listOf(
            linearUnit("Pa", "Pascal", "Pascal", 1.0),
            linearUnit("kPa", "Kilopascal", "Kilopascal", 1000.0),
            linearUnit("bar", "Bar", "Bar", 100_000.0),
            linearUnit("PSI", "PSI (Tayar)", "PSI (Tire)", 6894.757293168),
            linearUnit("atm", "Atmosfera", "Atmosphere", 101_325.0),
            linearUnit("mmHg", "mmHg (Torr)", "mmHg (Torr)", 133.322387415)
        )
    ),
    UnitCategoryDef(
        id = "energy", titleMs = "Tenaga", titleEn = "Energy",
        units = listOf(
            linearUnit("J", "Joule", "Joule", 1.0),
            linearUnit("kJ", "Kilojoule", "Kilojoule", 1000.0),
            linearUnit("cal", "Kalori", "Calorie", 4.184),
            linearUnit("kcal", "Kilokalori (Makanan)", "Kilocalorie", 4184.0),
            linearUnit("Wh", "Watt-Jam", "Watt-Hour", 3600.0),
            linearUnit("kWh", "Kilowatt-Jam (Bil)", "Kilowatt-Hour", 3_600_000.0),
            linearUnit("BTU", "BTU (Aircond)", "BTU (AC)", 1055.05585262)
        )
    ),
    UnitCategoryDef(
        id = "power", titleMs = "Kuasa", titleEn = "Power",
        units = listOf(
            linearUnit("W", "Watt", "Watt", 1.0),
            linearUnit("kW", "Kilowatt", "Kilowatt", 1000.0),
            linearUnit("MW", "Megawatt", "Megawatt", 1_000_000.0),
            linearUnit("HP", "Kuasa Kuda (HP)", "Horsepower (Mech)", 745.699871582),
            linearUnit("PS", "PS (Metrik HP)", "Metric HP (PS)", 735.49875),
            linearUnit("BTU/h", "BTU / Jam", "BTU / Hour", 0.29307107)
        )
    ),
    UnitCategoryDef(
        id = "fuel", titleMs = "Jimat Minyak", titleEn = "Fuel Economy",
        units = listOf(
            UnitDef("km/L", "Kilometer / Liter", "Kilometers / Liter", { it }, { it }),
            UnitDef("L/100km", "Liter / 100 km", "Liters / 100 km", { if (it > 0.0) 100.0 / it else 0.0 }, { if (it > 0.0) 100.0 / it else 0.0 }),
            linearUnit("mpg US", "Batu / Gelen (US)", "Miles / Gallon (US)", 0.425143707),
            linearUnit("mpg UK", "Batu / Gelen (UK)", "Miles / Gallon (UK)", 0.35400619)
        )
    )
)

@Composable
fun UnitConverterScreen(
    lang: Language,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var catIdx by remember { mutableIntStateOf(0) }
    var fromIdx by remember { mutableIntStateOf(2) } // e.g., meters
    var toIdx by remember { mutableIntStateOf(4) }   // e.g., inches
    var inputText by remember { mutableStateOf("1") }

    val category = UNIT_CATEGORIES[catIdx]
    val safeFromIdx = fromIdx.coerceIn(0, category.units.lastIndex)
    val safeToIdx = toIdx.coerceIn(0, category.units.lastIndex)
    val fromUnit = category.units[safeFromIdx]
    val toUnit = category.units[safeToIdx]

    val parsedInput = inputText.replace(',', '.').toDoubleOrNull()
    val baseValue = parsedInput?.let { fromUnit.toBase(it) }
    val convertedValue = baseValue?.let { toUnit.fromBase(it) }
    val formattedOutput = convertedValue?.let { formatConverterNumber(it) } ?: "--"

    ToolScaffold(
        title = lang.tr("Penukar Unit Universal", "Universal Converter"),
        subtitle = lang.tr("12 kategori ukuran fizikal, data & bahan api", "12 physical, digital & fuel categories"),
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Horizontal Category Selector Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(UNIT_CATEGORIES) { idx, cat ->
                    FilterChip(
                        selected = catIdx == idx,
                        onClick = {
                            if (catIdx != idx) {
                                catIdx = idx
                                fromIdx = 0
                                toIdx = if (cat.units.size > 1) 1 else 0
                            }
                        },
                        label = { Text(cat.title(lang), maxLines = 1) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Input & Unit Selection Card
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
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { raw ->
                                if (raw.length <= 18 && raw.all { it.isDigit() || it in ".-," }) {
                                    inputText = raw
                                }
                            },
                            label = { Text("${lang.tr("Nilai dalam", "Value in")} ${fromUnit.name(lang)} (${fromUnit.symbol})") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            trailingIcon = {
                                if (inputText.isNotEmpty()) {
                                    IconButton(onClick = { inputText = "" }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // From <-> Swap <-> To Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            UnitDropdownSelector(
                                label = lang.tr("Dari", "From"),
                                units = category.units,
                                selectedIdx = safeFromIdx,
                                lang = lang,
                                onSelect = { fromIdx = it },
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val tmp = safeFromIdx
                                    fromIdx = safeToIdx
                                    toIdx = tmp
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.SwapHoriz,
                                    contentDescription = lang.tr("Tukar Arah", "Swap Units"),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            UnitDropdownSelector(
                                label = lang.tr("Kepada", "To"),
                                units = category.units,
                                selectedIdx = safeToIdx,
                                lang = lang,
                                onSelect = { toIdx = it },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Highlighted Conversion Result Box
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${toUnit.name(lang)} (${toUnit.symbol})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "$formattedOutput ${toUnit.symbol}",
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (convertedValue != null) {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            cm?.setPrimaryClip(ClipData.newPlainText("Conversion", "$formattedOutput ${toUnit.symbol}"))
                                            Toast.makeText(context, lang.tr("Keputusan disalin!", "Result copied!"), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy")
                                }
                            }
                        }
                    }
                }

                // All Units Instant Comparison Table
                if (baseValue != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = lang.tr("Jadual Semua Unit (${category.title(lang)})", "All Units Comparison (${category.title(lang)})"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            category.units.forEachIndexed { idx, unit ->
                                val valStr = formatConverterNumber(unit.fromBase(baseValue))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            toIdx = idx
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            cm?.setPrimaryClip(ClipData.newPlainText("Unit", "$valStr ${unit.symbol}"))
                                            Toast.makeText(context, "$valStr ${unit.symbol}", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${unit.name(lang)} (${unit.symbol})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (idx == safeToIdx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (idx == safeToIdx) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = valStr,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (idx == safeToIdx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (idx < category.units.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
private fun UnitDropdownSelector(
    label: String,
    units: List<UnitDef>,
    selectedIdx: Int,
    lang: Language,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = units[selectedIdx]

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "${selected.symbol} • ${selected.name(lang)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            units.forEachIndexed { idx, u ->
                DropdownMenuItem(
                    text = { Text("${u.name(lang)} (${u.symbol})") },
                    onClick = {
                        onSelect(idx)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun formatConverterNumber(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "--"
    if (value == 0.0) return "0"
    val absVal = abs(value)
    return when {
        absVal >= 1e12 || absVal < 1e-6 -> String.format(Locale.US, "%.4e", value)
        else -> {
            val rounded = BigDecimal(value, MathContext(9)).stripTrailingZeros()
            val df = DecimalFormat("#,##0.######")
            df.format(rounded.toDouble())
        }
    }
}
