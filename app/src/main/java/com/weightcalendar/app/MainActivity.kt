package com.weightcalendar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val Blue = Color(0xFF2878F0)
private val BlueDeep = Color(0xFF1556BE)
private val PageBg = Color(0xFFF3F7FE)
private val Green = Color(0xFF16A979)
private val Red = Color(0xFFE05A52)
private val Ink = Color(0xFF1D2C45)
private val Muted = Color(0xFF71819A)
private val SoftBlue = Color(0xFFEAF2FF)
private val SoftGreen = Color(0xFFE6F8F0)
private val SoftRed = Color(0xFFFFEEEC)

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(243, 247, 254)
        window.navigationBarColor = android.graphics.Color.WHITE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Blue,
                    onPrimary = Color.White,
                    secondary = Green,
                    background = PageBg,
                    onBackground = Ink,
                    surface = Color.White,
                    onSurface = Ink,
                    surfaceVariant = SoftBlue,
                    outline = Color(0xFFDCE6F5)
                )
            ) {
                WeightCalendarApp()
            }
        }
    }
}

private enum class WeightUnit(val label: String, val short: String, val multiplier: Double) {
    KG("千克", "kg", 1.0),
    JIN("斤", "斤", 2.0),
    LIANG("两", "两", 20.0);

    fun toKg(value: Double): Double = value / multiplier
    fun fromKg(kg: Double): Double = kg * multiplier
}

private data class WeightEntry(val date: String, val kg: Double)

private object WeightStore {
    private const val PREFS = "weight_calendar_local_v1"
    private const val RECORDS_KEY = "records_json"
    private const val UNIT_KEY = "preferred_unit"

    fun load(context: android.content.Context): List<WeightEntry> {
        val raw = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getString(RECORDS_KEY, "[]") ?: "[]"
        return runCatching { parseJson(raw) }.getOrDefault(emptyList())
    }

    fun save(context: android.content.Context, records: List<WeightEntry>) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putString(RECORDS_KEY, toJson(records)).apply()
    }

    fun loadUnit(context: android.content.Context): WeightUnit {
        val name = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getString(UNIT_KEY, WeightUnit.KG.name)
        return runCatching { WeightUnit.valueOf(name ?: WeightUnit.KG.name) }
            .getOrDefault(WeightUnit.KG)
    }

    fun saveUnit(context: android.content.Context, unit: WeightUnit) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putString(UNIT_KEY, unit.name).apply()
    }

    fun toJson(records: List<WeightEntry>): String {
        val array = JSONArray()
        records.sortedBy { it.date }.forEach { entry ->
            array.put(JSONObject().put("date", entry.date).put("kg", entry.kg))
        }
        return array.toString(2)
    }

    fun parseJson(raw: String): List<WeightEntry> {
        val array = JSONArray(raw)
        val unique = linkedMapOf<String, WeightEntry>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val date = obj.getString("date")
            val kg = obj.getDouble("kg")
            LocalDate.parse(date)
            require(kg.isFinite() && kg > 0.0) { "体重必须是正数" }
            unique[date] = WeightEntry(date, kg)
        }
        return unique.values.sortedBy { it.date }
    }
}

private data class TabInfo(val title: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeightCalendarApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var records by remember { mutableStateOf(WeightStore.load(context)) }
    var unit by remember { mutableStateOf(WeightStore.loadUnit(context)) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var displayMonth by remember { mutableStateOf(YearMonth.now()) }
    var input by remember { mutableStateOf("") }
    var fundLiangInput by remember { mutableStateOf("1") }
    var activeTab by remember { mutableIntStateOf(0) }

    fun notifyUser(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun selectDate(date: LocalDate) {
        selectedDate = date
        val saved = records.firstOrNull { it.date == date.toString() }
        input = saved?.let { fmt(unit.fromKg(it.kg)) } ?: ""
    }

    fun selectUnit(next: WeightUnit) {
        if (next == unit) return
        val value = input.toDoubleOrNull()
        input = value?.let { fmt(next.fromKg(unit.toKg(it))) } ?: input
        unit = next
        WeightStore.saveUnit(context, next)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val result = runCatching {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error("无法打开保存位置")
                output.bufferedWriter().use { it.write(WeightStore.toJson(records)) }
            }
            notifyUser(if (result.isSuccess) "备份文件已导出" else "导出失败：${result.exceptionOrNull()?.message ?: "未知错误"}")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val result = runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("无法读取备份文件")
                WeightStore.parseJson(raw)
            }
            result.onSuccess { imported ->
                records = imported
                WeightStore.save(context, imported)
                selectDate(selectedDate)
                notifyUser("恢复完成，共 ${imported.size} 条记录")
            }.onFailure { notifyUser("恢复失败：${it.message ?: "文件格式不正确"}") }
        }
    }

    val tabs = listOf(
        TabInfo("首页", Icons.Filled.Home),
        TabInfo("日历", Icons.Filled.CalendarMonth),
        TabInfo("基金计算", Icons.Filled.AccountBalanceWallet),
        TabInfo("历史", Icons.Filled.History)
    )

    Scaffold(
        containerColor = PageBg,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 4.dp) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = activeTab == index,
                        onClick = { activeTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { insets ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
        ) {
            when (activeTab) {
                0 -> HomePage(
                    records = records,
                    unit = unit,
                    selectedDate = selectedDate,
                    input = input,
                    onInputChange = { input = it },
                    onSelectDate = { activeTab = 1; displayMonth = YearMonth.from(selectedDate) },
                    onSelectUnit = ::selectUnit,
                    onSave = {
                        val value = input.toDoubleOrNull()
                        if (value == null || !value.isFinite() || value <= 0.0) {
                            notifyUser("请输入大于 0 的体重")
                        } else {
                            val kg = unit.toKg(value)
                            val dateText = selectedDate.toString()
                            val next = (records.filterNot { it.date == dateText } + WeightEntry(dateText, kg))
                                .sortedBy { it.date }
                            records = next
                            WeightStore.save(context, next)
                            input = fmt(unit.fromKg(kg))
                            notifyUser("已保存 ${dateText} 的体重")
                        }
                    },
                    onOpenReward = { activeTab = 2 }
                )
                1 -> CalendarPage(
                    records = records,
                    unit = unit,
                    selectedDate = selectedDate,
                    month = displayMonth,
                    onMonthChange = { displayMonth = it },
                    onDateSelected = { date -> selectDate(date); displayMonth = YearMonth.from(date) },
                    onEditSelected = { activeTab = 0; selectDate(selectedDate) }
                )
                2 -> RewardPage(records = records, liangInput = fundLiangInput, onLiangInputChange = { text ->
                    if (text.length <= 12 && text.all { it.isDigit() || it == '.' }) fundLiangInput = text
                })
                else -> HistoryPage(
                    records = records,
                    unit = unit,
                    onExport = { exportLauncher.launch("体重日历备份.json") },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    onSelect = { date -> selectDate(date); activeTab = 0 }
                )
            }
        }
    }
}

@Composable
private fun AppHeader(subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text("体重日历", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun HomePage(
    records: List<WeightEntry>,
    unit: WeightUnit,
    selectedDate: LocalDate,
    input: String,
    onInputChange: (String) -> Unit,
    onSelectDate: () -> Unit,
    onSelectUnit: (WeightUnit) -> Unit,
    onSave: () -> Unit,
    onOpenReward: () -> Unit
) {
    val selected = records.firstOrNull { it.date == selectedDate.toString() }
    val yesterday = records.firstOrNull { it.date == selectedDate.minusDays(1).toString() }
    val first = records.firstOrNull()
    val previewKg = input.toDoubleOrNull()?.let { unit.toKg(it) } ?: selected?.kg

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppHeader("每天记录一点点，慢慢看见变化")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(if (selectedDate == LocalDate.now()) "今天的体重" else "记录日期", color = Muted, fontSize = 13.sp)
                            Text(selectedDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        }
                        TextButton(onClick = onSelectDate) {
                            Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("选日期")
                        }
                    }
                    Text("体重数值", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        WeightUnit.values().forEach { option ->
                            FilterChip(
                                selected = unit == option,
                                onClick = { onSelectUnit(option) },
                                label = { Text(option.label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { text ->
                            if (text.length <= 12 && text.all { it.isDigit() || it == '.' }) onInputChange(text)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("输入体重（${unit.label}）") },
                        placeholder = { Text(if (unit == WeightUnit.KG) "例如 62.5" else if (unit == WeightUnit.JIN) "例如 125" else "例如 1250") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Button(
                        onClick = onSave,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (selected != null) "保存 / 更新这一天" else if (selectedDate == LocalDate.now()) "保存今日体重" else "保存这一天的体重", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("只保存在本机 · 不需要注册或联网", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }

            Text("体重变化", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DifferenceCard(
                    modifier = Modifier.weight(1f),
                    title = "较上一日",
                    value = if (selected != null && yesterday != null) selected.kg - yesterday.kg else null,
                    unit = unit,
                    helper = if (selected != null && yesterday != null) "与${selectedDate.minusDays(1)}相比" else "需有连续两天记录"
                )
                DifferenceCard(
                    modifier = Modifier.weight(1f),
                    title = "较第一天",
                    value = if (selected != null && first != null) selected.kg - first.kg else null,
                    unit = unit,
                    helper = if (first != null) "起始 ${fmt(unit.fromKg(first.kg))} ${unit.short}" else "记录第一天后显示"
                )
            }

            Card(colors = CardDefaults.cardColors(containerColor = SoftBlue), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = Blue)
                        Spacer(Modifier.width(8.dp))
                        Text("单位换算", color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    if (previewKg != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            ConversionValue("千克", "${fmt(previewKg)} kg")
                            ConversionValue("斤", "${fmt(previewKg * 2)} 斤")
                            ConversionValue("两", "${fmt(previewKg * 20)} 两")
                        }
                        Text("换算关系：1 千克 = 2 斤 = 20 两；1 斤 = 10 两", color = Muted, fontSize = 11.sp)
                    } else {
                        Text("保存一条体重后，这里会自动显示三种单位。", color = Muted, fontSize = 12.sp)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenReward),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E5)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = Color(0xFFE4A11B), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("基金计算", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("输入两数，按每两 ¥10 计算并显示换算过程", color = Muted, fontSize = 11.sp)
                    }
                    Text("查看 ›", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("最近记录", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
                TextButton(onClick = onSelectDate) { Text("打开日历") }
            }
            val recent = records.sortedByDescending { it.date }.take(3)
            if (recent.isEmpty()) {
                EmptyCard("还没有体重记录", "输入今天的体重，保存后就会出现在这里。")
            } else {
                recent.forEach { entry ->
                    val prevDay = records.firstOrNull { it.date == LocalDate.parse(entry.date).minusDays(1).toString() }
                    MiniHistoryRow(entry, unit, prevDay)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ConversionValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 2.dp)) {
        Text(label, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = BlueDeep, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DifferenceCard(modifier: Modifier, title: String, value: Double?, unit: WeightUnit, helper: String) {
    val tone = when {
        value == null -> Muted
        value < -0.000001 -> Green
        value > 0.000001 -> Red
        else -> Ink
    }
    val background = when {
        value == null -> Color.White
        value < -0.000001 -> SoftGreen
        value > 0.000001 -> SoftRed
        else -> Color.White
    }
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = background), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Muted, fontSize = 12.sp)
            Text(value?.let { deltaLabel(unit.fromKg(it), unit) } ?: "—", color = tone, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(helper, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun CalendarPage(
    records: List<WeightEntry>,
    unit: WeightUnit,
    selectedDate: LocalDate,
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onEditSelected: () -> Unit
) {
    val days = month.lengthOfMonth()
    val offset = month.atDay(1).dayOfWeek.value - 1
    val cellCount = ((offset + days + 6) / 7) * 7
    val selectedEntry = records.firstOrNull { it.date == selectedDate.toString() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppHeader("按日历查看你的体重记录")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("${month.year}年${month.monthValue}月", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onMonthChange(month.minusMonths(1)) }) { Text("‹", fontSize = 25.sp) }
                            TextButton(onClick = { onMonthChange(YearMonth.now()) }) { Text("本月", fontSize = 12.sp) }
                            TextButton(onClick = { onMonthChange(month.plusMonths(1)) }) { Text("›", fontSize = 25.sp) }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                            Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Muted, fontSize = 12.sp)
                        }
                    }
                    for (rowStart in 0 until cellCount step 7) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (column in 0..6) {
                                val cell = rowStart + column
                                val day = cell - offset + 1
                                if (day in 1..days) {
                                    val date = month.atDay(day)
                                    val entry = records.firstOrNull { it.date == date.toString() }
                                    CalendarCell(
                                        modifier = Modifier.weight(1f),
                                        day = day,
                                        weight = entry?.let { unit.fromKg(it.kg) },
                                        unit = unit,
                                        selected = date == selectedDate,
                                        isToday = date == LocalDate.now(),
                                        onClick = { onDateSelected(date) }
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f).height(54.dp))
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LegendDot(Blue, "选中日期")
                        LegendDot(Green, "有记录")
                        Text("单位：${unit.label}", color = Muted, fontSize = 11.sp)
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(selectedDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)), color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    if (selectedEntry != null) {
                        Text("${fmt(unit.fromKg(selectedEntry.kg))} ${unit.short}", color = Blue, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                        Text("原始存储值：${fmt(selectedEntry.kg)} 千克", color = Muted, fontSize = 12.sp)
                    } else {
                        Text("这一天还没有记录", color = Muted, fontSize = 14.sp)
                    }
                    Button(onClick = onEditSelected, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Text(if (selectedEntry == null) "记录这一天" else "修改这一天")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CalendarCell(
    modifier: Modifier,
    day: Int,
    weight: Double?,
    unit: WeightUnit,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val background = when {
        selected -> Blue
        weight != null -> SoftGreen
        else -> Color.Transparent
    }
    val textColor = if (selected) Color.White else Ink
    Column(
        modifier = modifier.padding(2.dp).height(54.dp).clip(RoundedCornerShape(12.dp))
            .background(background).clickable(onClick = onClick).padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(if (isToday) "$day·" else day.toString(), color = textColor, fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
        if (weight != null) {
            Text(fmt(weight), color = if (selected) Color.White else Green, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, maxLines = 1)
        } else {
            Spacer(Modifier.height(11.dp))
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun RewardPage(records: List<WeightEntry>, liangInput: String, onLiangInputChange: (String) -> Unit) {
    val liang = liangInput.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
    val jin = liang / 10.0
    val kg = liang * 0.05
    val reward = liang * 10.0
    val latest = records.sortedBy { it.date }.lastOrNull()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppHeader("基金计算 · 输入数量，自动换算金额")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = Blue), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("计算结果", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    Text("¥${fmt(reward)}", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${fmt(liang)} 两 × ¥10 / 两", color = Color(0xFFDCE9FF), fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RewardPill("${fmt(jin)} 斤")
                        RewardPill("${fmt(kg)} 千克")
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("输入计算数量", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
                    OutlinedTextField(
                        value = liangInput,
                        onValueChange = onLiangInputChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("两（可输入小数）") },
                        placeholder = { Text("例如 1 或 2.5") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Surface(color = SoftBlue, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text("计算过程", fontWeight = FontWeight.Bold, color = BlueDeep, fontSize = 14.sp)
                            CalculationLine("1", "计算金额", "${fmt(liang)} 两 × ¥10/两 = ¥${fmt(reward)}")
                            CalculationLine("2", "换算成斤", "${fmt(liang)} 两 ÷ 10 = ${fmt(jin)} 斤")
                            CalculationLine("3", "换算成千克", "${fmt(liang)} 两 × 0.05 = ${fmt(kg)} 千克")
                        }
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("单位换算表", fontWeight = FontWeight.Bold, color = Ink, fontSize = 16.sp)
                    DetailLine("千克与斤", "1 千克 = 2 斤")
                    DetailLine("斤与两", "1 斤 = 10 两")
                    DetailLine("千克与两", "1 千克 = 20 两")
                    DetailLine("两与千克", "1 两 = 0.05 千克")
                    DetailLine("金额规则", "1 两 = ¥10；2 两 = ¥20")
                    if (latest != null) {
                        Spacer(Modifier.height(4.dp))
                        Text("最近体重记录（仅供单位参考）", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("${latest.date} · ${fmt(latest.kg)} kg = ${fmt(latest.kg * 2)} 斤 = ${fmt(latest.kg * 20)} 两", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                    Text("基金金额计算与体重记录分开，不会自动把体重变化换成奖励。体重记录用于观察数据变化，不建议为了金额刻意追求体重变化。", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RewardPill(text: String) {
    Surface(color = Color(0x33FFFFFF), shape = RoundedCornerShape(100.dp)) {
        Text(text, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.width(82.dp))
        Text(value, color = Ink, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CalculationLine(number: String, title: String, formula: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Text(number, color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(formula, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun HistoryPage(
    records: List<WeightEntry>,
    unit: WeightUnit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onSelect: (LocalDate) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        AppHeader("所有记录保存在本机，可随时备份")
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("导出备份")
            }
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("恢复备份")
            }
        }
        Text("共 ${records.size} 条记录", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.TopCenter) {
                EmptyCard("暂无历史记录", "你保存的每日体重会按日期显示在这里。")
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)
            ) {
                items(records.size, key = { records[records.lastIndex - it].date }) { index ->
                    val entry = records[records.lastIndex - index]
                    val date = LocalDate.parse(entry.date)
                    val prevDate = date.minusDays(1).toString()
                    val prev = records.firstOrNull { it.date == prevDate }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(date) },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(date.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(if (prev != null) "较上一日：${deltaLabel(unit.fromKg(entry.kg - prev.kg), unit)}" else "上一日无记录", color = if (prev != null && entry.kg < prev.kg) Green else Muted, fontSize = 11.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(fmt(unit.fromKg(entry.kg)), color = Blue, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                                Text(unit.short, color = Muted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniHistoryRow(entry: WeightEntry, unit: WeightUnit, previousDay: WeightEntry?) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(entry.date, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(if (previousDay != null) "较上一日 ${deltaLabel(unit.fromKg(entry.kg - previousDay.kg), unit)}" else "暂无上一日记录", color = Muted, fontSize = 10.sp)
            }
            Text("${fmt(unit.fromKg(entry.kg))} ${unit.short}", color = Blue, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}

@Composable
private fun EmptyCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.DateRange, contentDescription = null, tint = Blue, modifier = Modifier.size(30.dp))
            Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(body, color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
        }
    }
}

private fun fmt(value: Double): String {
    if (!value.isFinite()) return "0"
    val rounded = if (kotlin.math.abs(value) < 0.0000001) 0.0 else value
    return String.format(Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
}

private fun deltaLabel(value: Double, unit: WeightUnit): String {
    val prefix = when {
        value > 0.000001 -> "+"
        value < -0.000001 -> "−"
        else -> ""
    }
    return "$prefix${fmt(kotlin.math.abs(value))} ${unit.short}"
}
