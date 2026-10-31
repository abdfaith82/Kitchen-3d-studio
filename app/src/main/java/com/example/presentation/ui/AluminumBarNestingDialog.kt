package com.example.presentation.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.cam.*
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Dedicated Module for 1D Aluminum Bar Stock Cutting & Nesting Optimization
 * (وحدة تحسين قص وتعشيق قضبان وبروفايلات الألمنيوم - 1D Nesting Algorithm).
 *
 * Allows users to:
 * 1. Input standard aluminum bar lengths (6000mm, 5800mm, 6500mm, 4500mm, or any custom length in mm/cm)
 *    and configure saw blade kerf, end trim, and Nesting Algorithm strategy (BFD, FFD, Multi-Stock).
 * 2. Automatically extract cabinetry aluminum profile cuts from the 3D kitchen project AND/OR add custom
 *    individual profile cuts.
 * 3. Inspect the optimal cut list per bar with proportional 2D Canvas bar diagrams, waste percentages,
 *    saw kerf losses, reusable offcuts, and individual cut piece coordinates.
 */
@Composable
fun AluminumBarNestingDialog(
    sceneCabinets: List<ParametricCabinet>,
    initialPrimaryBarLengthMm: Float = 6000f,
    initialAvailableBarLengthsMm: List<Float> = listOf(6000f, 5800f, 6500f, 4500f),
    initialCustomCuts: List<CabinetryAluminumCutInput> = emptyList(),
    onUpdateCustomCuts: (List<CabinetryAluminumCutInput>) -> Unit = {},
    onUpdatePrimaryBarLengthMm: (Float) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Unit toggle: false = mm (مم), true = cm (سم)
    var useCentimeters by remember { mutableStateOf(false) }
    val unitFactor = if (useCentimeters) 10f else 1f
    val unitLabel = if (useCentimeters) "سم" else "مم"

    // Standard Aluminum Bar Length inputs
    var primaryBarLengthMm by remember(initialPrimaryBarLengthMm) {
        mutableFloatStateOf(initialPrimaryBarLengthMm.coerceIn(500f, 12000f))
    }
    var customBarLengthInputText by remember(primaryBarLengthMm, useCentimeters) {
        val displayVal = if (useCentimeters) (primaryBarLengthMm / 10f).roundToInt() else primaryBarLengthMm.roundToInt()
        mutableStateOf(displayVal.toString())
    }
    var availableBarLengthsMm by remember {
        mutableStateOf(
            (initialAvailableBarLengthsMm + initialPrimaryBarLengthMm)
                .map { it.coerceIn(500f, 12000f) }
                .distinct()
                .sortedDescending()
        )
    }

    // Saw Kerf & End Trim inputs
    var sawKerfText by remember { mutableStateOf("4.0") }
    var endTrimText by remember { mutableStateOf("10") }
    var selectedStrategy by remember { mutableStateOf(AluminumNestingStrategy.BEST_FIT_DECREASING) }
    var groupByProfileType by remember { mutableStateOf(true) }
    var includeSceneCabinets by remember { mutableStateOf(true) }

    // Custom user-added profile cuts
    var customCuts by remember { mutableStateOf(initialCustomCuts) }
    var newCutNameText by remember { mutableStateOf("قائم/عارضة ألمنيوم مخصصة") }
    var newCutLengthText by remember(useCentimeters) { mutableStateOf(if (useCentimeters) "85" else "850") }
    var newCutQuantityText by remember { mutableStateOf("4") }
    var newCutTypeCode by remember { mutableStateOf("ALU_POST_V") }
    var newCutOrientation by remember { mutableStateOf(AluminumBarOrientation.VERTICAL) }

    // Active sub-tab: 0 = مخطط القص والقضبان والهدر, 1 = إدارة القطع المطلوبة وإضافة مقاسات, 2 = ملخص الفئات والتقرير
    var activeTab by remember { mutableIntStateOf(0) }
    var copiedReportBanner by remember { mutableStateOf<String?>(null) }

    val sawKerfMm = (sawKerfText.toFloatOrNull() ?: 4.0f).coerceIn(0f, 20f)
    val endTrimMm = (endTrimText.toFloatOrNull() ?: 10.0f).coerceIn(0f, 100f)

    // Combine cabinetry profile cuts from 3D scene + user custom cuts
    val sceneCutInputs = remember(sceneCabinets, includeSceneCabinets) {
        if (includeSceneCabinets) {
            AluminumBarNestingOptimizer.extractCabinetryCutInputs(sceneCabinets)
        } else {
            emptyList()
        }
    }

    val allCutInputs = remember(sceneCutInputs, customCuts) {
        sceneCutInputs + customCuts
    }

    val nestingConfig = remember(
        primaryBarLengthMm,
        availableBarLengthsMm,
        sawKerfMm,
        endTrimMm,
        selectedStrategy,
        groupByProfileType
    ) {
        AluminumNestingConfig(
            primaryBarLengthMm = primaryBarLengthMm,
            availableStandardBarLengthsMm = availableBarLengthsMm,
            sawKerfMm = sawKerfMm,
            endTrimMm = endTrimMm,
            strategy = selectedStrategy,
            groupByProfileType = groupByProfileType
        )
    }

    val nestingResult = remember(allCutInputs, nestingConfig) {
        AluminumBarNestingOptimizer.optimizeCutList(
            cutInputs = allCutInputs,
            config = nestingConfig
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .testTag("aluminum_bar_nesting_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A),
            tonalElevation = 12.dp,
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ContentCut,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "محرك تعشيق وقص قضبان الألمنيوم الذكي (1D Bar Nesting Optimizer)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "أدخل أطوال القضبان القياسية واحسب كشف القص الأمثل لبروفايلات الخزائن مع نسب الهدر والقطع الفردية",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = !useCentimeters,
                            onClick = { useCentimeters = false },
                            label = { Text("مليمتر (مم)", fontSize = 10.sp) },
                            modifier = Modifier.testTag("nesting_unit_mm_chip")
                        )
                        FilterChip(
                            selected = useCentimeters,
                            onClick = { useCentimeters = true },
                            label = { Text("سنتيمتر (سم)", fontSize = 10.sp) },
                            modifier = Modifier.testTag("nesting_unit_cm_chip")
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_alu_nesting_dialog_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Global Optimization KPI Cards (Waste %, Efficiency %, Bars Needed, Cut Pieces Count)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    NestingKpiMetric(
                        title = "إجمالي القضبان القياسية",
                        value = "${nestingResult.totalBarsCount} قضيب",
                        subValue = "إجمالي الخام: ${String.format(Locale.US, "%.2f", nestingResult.totalStockMeters)} م.ط",
                        color = Color(0xFF38BDF8),
                        tag = "kpi_total_bars"
                    )
                    NestingKpiMetric(
                        title = "إجمالي القطع الفردية المقصوصة",
                        value = "${nestingResult.totalIndividualCutPiecesCount} قطعة",
                        subValue = "الصافي: ${String.format(Locale.US, "%.2f", nestingResult.totalNetCutMeters)} م.ط",
                        color = Color(0xFF10B981),
                        tag = "kpi_total_cut_pieces"
                    )
                    NestingKpiMetric(
                        title = "نسبة الاستغلال (Yield)",
                        value = "${String.format(Locale.US, "%.1f", nestingResult.overallUtilizationPercentage)}%",
                        subValue = if (nestingResult.savedBarsCount > 0)
                            "وفّر ${nestingResult.savedBarsCount} قضيب مقارنة بالقص العشوائي"
                        else "توزيع مثالي بأعلى كفاءة",
                        color = Color(0xFF34D399),
                        tag = "kpi_utilization_pct"
                    )
                    NestingKpiMetric(
                        title = "نسبة الهدر الكلية (Waste %)",
                        value = "${String.format(Locale.US, "%.1f", nestingResult.overallWastePercentage)}%",
                        subValue = "فضلات: ${(nestingResult.totalRemnantOffcutMm / unitFactor).roundToInt()} $unitLabel | منشار: ${((nestingResult.totalKerfLossMm + nestingResult.totalEndTrimLossMm) / unitFactor).roundToInt()} $unitLabel",
                        color = if (nestingResult.overallWastePercentage <= 12f) Color(0xFFFBBF24) else Color(0xFFEF4444),
                        tag = "kpi_waste_pct"
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Standard Aluminum Bar Length Configuration Panel
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF0284C7)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "إعدادات أطوال قضبان الألمنيوم القياسية وخوارزمية التعشيق (Standard Bar Lengths & Nesting):",
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("فرز حسب نوع القطاع:", color = Color(0xFFCBD5E1), fontSize = 10.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Switch(
                                    checked = groupByProfileType,
                                    onCheckedChange = { groupByProfileType = it },
                                    modifier = Modifier.height(24.dp).testTag("group_by_profile_switch")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Preset Standard Bar Lengths + Custom Bar Length Input
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LazyRow(
                                modifier = Modifier.weight(1.6f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(availableBarLengthsMm) { barLenMm ->
                                    val isSelected = if (selectedStrategy == AluminumNestingStrategy.MULTI_STOCK_OPTIMAL) {
                                        true
                                    } else {
                                        kotlin.math.abs(primaryBarLengthMm - barLenMm) < 1f
                                    }
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            primaryBarLengthMm = barLenMm
                                            customBarLengthInputText = if (useCentimeters) {
                                                (barLenMm / 10f).roundToInt().toString()
                                            } else {
                                                barLenMm.roundToInt().toString()
                                            }
                                            onUpdatePrimaryBarLengthMm(barLenMm)
                                        },
                                        label = {
                                            Text(
                                                text = "${barLenMm.roundToInt()} مم (${String.format(Locale.US, "%.1f", barLenMm / 1000f)}م)",
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF0284C7),
                                            selectedLabelColor = Color.White
                                        ),
                                        modifier = Modifier.testTag("preset_bar_len_${barLenMm.roundToInt()}")
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = customBarLengthInputText,
                                onValueChange = { txt ->
                                    customBarLengthInputText = txt
                                    val parsed = txt.toFloatOrNull()
                                    if (parsed != null) {
                                        val mm = (parsed * unitFactor).coerceIn(500f, 12000f)
                                        primaryBarLengthMm = mm
                                        onUpdatePrimaryBarLengthMm(mm)
                                    }
                                },
                                label = { Text("طول القضيب القياسي ($unitLabel)", fontSize = 9.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.8f)
                                    .testTag("standard_bar_length_input")
                            )

                            Button(
                                onClick = {
                                    val parsed = customBarLengthInputText.toFloatOrNull()
                                    if (parsed != null) {
                                        val mm = (parsed * unitFactor).coerceIn(500f, 12000f)
                                        primaryBarLengthMm = mm
                                        if (availableBarLengthsMm.none { kotlin.math.abs(it - mm) < 1f }) {
                                            availableBarLengthsMm = (availableBarLengthsMm + mm).sortedDescending()
                                        }
                                        onUpdatePrimaryBarLengthMm(mm)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("add_standard_bar_length_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("اعتماد الطول", fontSize = 10.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Strategy Selector + Saw Kerf + End Trim
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LazyRow(
                                modifier = Modifier.weight(1.8f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(AluminumNestingStrategy.values()) { strategy ->
                                    FilterChip(
                                        selected = selectedStrategy == strategy,
                                        onClick = { selectedStrategy = strategy },
                                        label = { Text(strategy.titleAr, fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF10B981).copy(alpha = 0.28f),
                                            selectedLabelColor = Color(0xFF6EE7B7)
                                        ),
                                        modifier = Modifier.testTag("strategy_chip_${strategy.name}")
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = sawKerfText,
                                onValueChange = { sawKerfText = it },
                                label = { Text("شفرة المنشار Kerf (مم)", fontSize = 9.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.55f)
                                    .testTag("saw_kerf_input")
                            )

                            OutlinedTextField(
                                value = endTrimText,
                                onValueChange = { endTrimText = it },
                                label = { Text("تهذيب الطرف Trim (مم)", fontSize = 9.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.55f)
                                    .testTag("bar_end_trim_input")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Navigation Tabs
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color.White
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        modifier = Modifier.testTag("alu_nesting_tab_bars"),
                        text = {
                            Text(
                                "مخططات القص والقطع الفردية (${nestingResult.totalBarsCount} قضيب | هدر ${String.format(Locale.US, "%.1f", nestingResult.overallWastePercentage)}%)",
                                fontSize = 11.sp
                            )
                        }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        modifier = Modifier.testTag("alu_nesting_tab_inputs"),
                        text = {
                            Text(
                                "إدخال وتعديل القطع المطلوبة (${allCutInputs.sumOf { it.quantity }} قطعة)",
                                fontSize = 11.sp
                            )
                        }
                    )
                    Tab(
                        selected = activeTab == 2,
                        onClick = { activeTab = 2 },
                        modifier = Modifier.testTag("alu_nesting_tab_summary"),
                        text = {
                            Text(
                                "تحليل الهدر وتقرير الورشة",
                                fontSize = 11.sp
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Main Content Area
                when (activeTab) {
                    0 -> {
                        // Tab 0: Visual 1D Bar Cut List with Waste Percentages & Individual Cut Pieces
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("optimized_bars_list"),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (nestingResult.oversizedCutAdjustments.isNotEmpty()) {
                                item {
                                    Surface(
                                        color = Color(0xFF451A03),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            nestingResult.oversizedCutAdjustments.forEach { note ->
                                                Text(note, color = Color(0xFFFDE68A), fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            if (nestingResult.bars.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "لا توجد قطع قص مطلوبة حالياً. أضف خزائن أو أدخل مقاسات قطع مخصصة من التبويب الثاني.",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            } else {
                                itemsIndexed(
                                    items = nestingResult.bars,
                                    key = { _, bar -> "opt_bar_${bar.barIndex}_${bar.stockLengthMm.roundToInt()}" }
                                ) { _, bar ->
                                    OptimizedAluminumBarCard(
                                        bar = bar,
                                        useCentimeters = useCentimeters,
                                        minReusableOffcutMm = nestingConfig.minReusableOffcutMm
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        // Tab 1: Add Custom Cut Pieces & Manage Required Cabinetry Profile Cuts
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            // Custom Cut Piece Adder Card
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF10B981)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "إضافة مقاسات قطع ألمنيوم مطلوبة يدوياً لقائمة القص:",
                                            color = Color(0xFF34D399),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "تضمين خزائن المشهد 3D (${sceneCabinets.size} خزانة):",
                                                color = Color(0xFFCBD5E1),
                                                fontSize = 10.sp
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Switch(
                                                checked = includeSceneCabinets,
                                                onCheckedChange = { includeSceneCabinets = it },
                                                modifier = Modifier
                                                    .height(24.dp)
                                                    .testTag("include_scene_cabinets_switch")
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = newCutNameText,
                                            onValueChange = { newCutNameText = it },
                                            label = { Text("وصف القطعة / الخزانة", fontSize = 10.sp) },
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1.4f)
                                                .testTag("custom_cut_name_input")
                                        )
                                        OutlinedTextField(
                                            value = newCutLengthText,
                                            onValueChange = { newCutLengthText = it },
                                            label = { Text("طول القطعة ($unitLabel)", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(0.9f)
                                                .testTag("custom_cut_length_input")
                                        )
                                        OutlinedTextField(
                                            value = newCutQuantityText,
                                            onValueChange = { newCutQuantityText = it },
                                            label = { Text("العدد", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(0.6f)
                                                .testTag("custom_cut_qty_input")
                                        )
                                        Button(
                                            onClick = {
                                                val rawLen = newCutLengthText.toFloatOrNull() ?: 0f
                                                val lenMm = (rawLen * unitFactor).coerceIn(20f, 11900f)
                                                val qty = (newCutQuantityText.toIntOrNull() ?: 1).coerceIn(1, 200)
                                                val added = CabinetryAluminumCutInput(
                                                    id = "custom_cut_${System.currentTimeMillis()}_${customCuts.size}",
                                                    profileName = newCutNameText.ifBlank { "قطاع ألمنيوم مخصص" },
                                                    cabinetName = "إدخال يدوي مخصص",
                                                    lengthMm = lenMm,
                                                    quantity = qty,
                                                    profileTypeCode = newCutTypeCode,
                                                    orientation = newCutOrientation,
                                                    isCustomUserAdded = true
                                                )
                                                val updated = listOf(added) + customCuts
                                                customCuts = updated
                                                onUpdateCustomCuts(updated)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                            modifier = Modifier.testTag("add_custom_cut_btn")
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("إضافة للقائمة", fontSize = 11.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Profile Category Quick Selector for Custom Cuts
                                    val profileTypeOptions = listOf(
                                        "ALU_POST_V" to "قائم عمودي هيكلي",
                                        "ALU_CROSS_H" to "عارضة أفقية",
                                        "GOLA_L" to "مقبض غولا L",
                                        "GOLA_C" to "مقبض غولا C",
                                        "GLASS_FRAME_V" to "إطار درفة زجاج"
                                    )
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(profileTypeOptions) { (code, label) ->
                                            FilterChip(
                                                selected = newCutTypeCode == code,
                                                onClick = {
                                                    newCutTypeCode = code
                                                    newCutOrientation = if (code.endsWith("_V")) {
                                                        AluminumBarOrientation.VERTICAL
                                                    } else {
                                                        AluminumBarOrientation.HORIZONTAL
                                                    }
                                                },
                                                label = { Text(label, fontSize = 10.sp) }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // List of all required cuts (Custom + Scene Cabinets)
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(allCutInputs, key = { it.id }) { item ->
                                    Surface(
                                        color = Color(0xFF1E293B),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (item.isCustomUserAdded) Color(0xFF10B981) else Color(0xFF334155)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${item.cabinetName} — ${item.profileName}",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "${AluminumBarNestingOptimizer.profileCategoryTitleAr(item.profileTypeCode)} | ${item.orientation.labelAr}",
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 10.sp
                                                )
                                            }
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${item.quantity} قطعة × ${item.lengthMm.roundToInt()} مم (${String.format(Locale.US, "%.1f", item.lengthMm / 10f)} سم)",
                                                    color = Color(0xFF38BDF8),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (item.isCustomUserAdded) {
                                                    IconButton(
                                                        onClick = {
                                                            val updated = customCuts.filterNot { it.id == item.id }
                                                            customCuts = updated
                                                            onUpdateCustomCuts(updated)
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Delete,
                                                            contentDescription = "حذف القطعة",
                                                            tint = Color(0xFFEF4444),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // Tab 2: Category Breakdown, Waste Analytics & Workshop Report Preview
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Breakdown by Standard Bar Length Used
                            item {
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "تحليل الهدر والأطوال القياسية المستخدمة (Waste & Yield Analytics):",
                                            color = Color(0xFF38BDF8),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        nestingResult.barCountsByLengthMm.forEach { (lenMm, count) ->
                                            SummaryMetricRow(
                                                label = "قضبان قياسية بطول $lenMm مم (${String.format(Locale.US, "%.2f", lenMm / 1000f)} متر)",
                                                value = "$count قضيب"
                                            )
                                        }
                                        SummaryMetricRow(
                                            label = "إجمالي الطول الخام للقضبان",
                                            value = "${nestingResult.totalStockLengthMm.roundToInt()} مم (${String.format(Locale.US, "%.2f", nestingResult.totalStockMeters)} م.ط)"
                                        )
                                        SummaryMetricRow(
                                            label = "إجمالي الطول الصافي للقطع المقصوصة",
                                            value = "${nestingResult.totalNetCutLengthMm.roundToInt()} مم (${String.format(Locale.US, "%.2f", nestingResult.totalNetCutMeters)} م.ط)"
                                        )
                                        SummaryMetricRow(
                                            label = "إجمالي الفضلات المتبقية في نهاية القضبان (Offcuts)",
                                            value = "${nestingResult.totalRemnantOffcutMm.roundToInt()} مم (${String.format(Locale.US, "%.2f", nestingResult.totalRemnantOffcutMeters)} م.ط)"
                                        )
                                        SummaryMetricRow(
                                            label = "إجمالي فاقد شفرة المنشار وتهذيب الأطراف (Kerf + Trim)",
                                            value = "${(nestingResult.totalKerfLossMm + nestingResult.totalEndTrimLossMm).roundToInt()} مم"
                                        )
                                        SummaryMetricRow(
                                            label = "فضلات صالحة لإعادة الاستخدام (أطول من ${nestingConfig.minReusableOffcutMm.roundToInt()} مم)",
                                            value = "${nestingResult.reusableOffcutsCount} فضلة (بإجمالي ${nestingResult.reusableOffcutsTotalMm.roundToInt()} مم)"
                                        )
                                        SummaryMetricRow(
                                            label = "نسبة الهدر الكلية مقابل نسبة الاستغلال",
                                            value = "هدر ${String.format(Locale.US, "%.1f", nestingResult.overallWastePercentage)}% | استغلال ${String.format(Locale.US, "%.1f", nestingResult.overallUtilizationPercentage)}%",
                                            highlight = true
                                        )
                                    }
                                }
                            }

                            // Breakdown by Profile Category
                            items(nestingResult.categorySummaries) { cat ->
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = cat.categoryTitleAr,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = "${cat.barsCount} قضيب | ${cat.cutPiecesCount} قطعة",
                                                color = Color(0xFF38BDF8),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "صافي القص: ${String.format(Locale.US, "%.2f", cat.totalNetCutMeters)} م.ط من أصل ${String.format(Locale.US, "%.2f", cat.totalStockMeters)} م.ط | استغلال: ${String.format(Locale.US, "%.1f", cat.utilizationPercentage)}% | هدر: ${String.format(Locale.US, "%.1f", cat.wastePercentage)}%",
                                            color = Color(0xFFCBD5E1),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Copied feedback banner
                copiedReportBanner?.let { msg ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = Color(0xFF065F46),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Bar Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val reportText = AluminumBarNestingOptimizer.generateWorkshopCutListReport(nestingResult)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Aluminum Nesting Cut List", reportText))
                            copiedReportBanner = "تم نسخ كشف قص وتعشيق قضبان الألمنيوم (${nestingResult.totalBarsCount} قضيب / ${nestingResult.totalIndividualCutPiecesCount} قطعة) إلى الحافظة بنجاح."
                        },
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier.testTag("copy_alu_cut_list_btn")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("نسخ كشف القص للورشة", color = Color(0xFF38BDF8), fontSize = 11.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier.testTag("done_alu_nesting_btn")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تم", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Renders a single Optimized Aluminum Bar Card with:
 * - Header showing Bar Index, Stock Bar Length, Profile Category, Utilization %, and Waste % badge.
 * - Proportional 2D Canvas Diagram showing each cut piece, saw kerf ticks, and the waste/offcut tail.
 * - Complete list of individual cut pieces on this bar with exact start/end millimeter offsets.
 */
@Composable
fun OptimizedAluminumBarCard(
    bar: OptimizedAluminumBar,
    useCentimeters: Boolean = false,
    minReusableOffcutMm: Float = 250f
) {
    val unitFactor = if (useCentimeters) 10f else 1f
    val unitLabel = if (useCentimeters) "سم" else "مم"
    val isReusable = bar.isReusableOffcut(minReusableOffcutMm)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E293B),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("nested_bar_card_${bar.barIndex}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "قضيب ألمنيوم #${bar.barIndex} — طول قياسي ${bar.stockLengthMm.roundToInt()} مم (${String.format(Locale.US, "%.2f", bar.stockLengthMm / 1000f)} م)",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = Color(0xFF0284C7).copy(alpha = 0.25f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${bar.cutPieces.size} قطع مقصوصة",
                                color = Color(0xFF38BDF8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = bar.profileCategoryLabel,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }

                // Utilization & Waste % Badges
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = Color(0xFF065F46),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "استغلال ${String.format(Locale.US, "%.1f", bar.utilizationPercentage)}%",
                            color = Color(0xFF6EE7B7),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = if (bar.wastePercentage <= 12f) Color(0xFF78350F) else Color(0xFF7F1D1D),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "هدر ${String.format(Locale.US, "%.1f", bar.wastePercentage)}%",
                            color = if (bar.wastePercentage <= 12f) Color(0xFFFDE68A) else Color(0xFFFECACA),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Visual 1D Linear Bar Nesting Diagram (Canvas)
            AluminumBarVisualStrip(bar = bar)

            Spacer(modifier = Modifier.height(8.dp))

            // Individual Cut Pieces List
            Text(
                text = "تفصيل القطع الفردية المقصوصة من هذا القضيب (Individual Cut Pieces):",
                color = Color(0xFF38BDF8),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))

            bar.cutPieces.forEach { piece ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = cutPiecePaletteColor(piece.pieceNumberOnBar - 1),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "#${piece.pieceNumberOnBar}",
                                color = Color(0xFF0F172A),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "${piece.cabinetName} — ${piece.profileName}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "موقع القص على القضيب: ${piece.startOffsetMm.roundToInt()} مم ← ${piece.endOffsetMm.roundToInt()} مم (شفرة ${String.format(Locale.US, "%.1f", piece.kerfAfterMm)} مم)",
                                color = Color(0xFF94A3B8),
                                fontSize = 9.sp
                            )
                        }
                    }

                    Text(
                        text = "${piece.lengthMm.roundToInt()} مم (${String.format(Locale.US, "%.1f", piece.lengthCm)} سم)",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Footer Bar Metrics (Used vs Saw Kerf vs Offcut Remnant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الصافي المقصوص: ${(bar.netCutLengthMm / unitFactor).roundToInt()} $unitLabel | فاقد المنشار والتهذيب: ${((bar.totalKerfLossMm + bar.endTrimMm) / unitFactor).roundToInt()} $unitLabel",
                    color = Color(0xFFCBD5E1),
                    fontSize = 10.sp
                )
                Text(
                    text = if (isReusable) {
                        "الفضلة المتبقية (Offcut): ${(bar.remnantOffcutMm / unitFactor).roundToInt()} $unitLabel (صالحة للاستخدام)"
                    } else {
                        "الفضلة الهالكة (Scrap): ${(bar.remnantOffcutMm / unitFactor).roundToInt()} $unitLabel"
                    },
                    color = if (isReusable) Color(0xFFFBBF24) else Color(0xFFF87171),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * 2D Proportional Visual Strip for a single 1D Aluminum Bar showing:
 * - End trim margin
 * - Each placed cut piece with its #number and mm length label
 * - Saw kerf cut lines
 * - Tail offcut / waste region with hatch lines
 */
@Composable
fun AluminumBarVisualStrip(
    bar: OptimizedAluminumBar,
    modifier: Modifier = Modifier
) {
    val textPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            textSize = 24f
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Color(0xFF020617), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF475569), RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) {
        val totalBarMm = bar.stockLengthMm.coerceAtLeast(1f)
        val w = size.width
        val h = size.height

        // Draw background bar track
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset.Zero,
            size = Size(w, h),
            cornerRadius = CornerRadius(6f, 6f)
        )

        // Draw End Trim on left
        val trimWidthPx = ((bar.endTrimMm / totalBarMm) * w).coerceAtLeast(2f)
        drawRect(
            color = Color(0xFF64748B),
            topLeft = Offset(0f, 0f),
            size = Size(trimWidthPx, h)
        )

        // Draw each individual cut piece proportionally
        bar.cutPieces.forEachIndexed { idx, piece ->
            val startX = ((piece.startOffsetMm / totalBarMm) * w).coerceIn(0f, w)
            val endX = ((piece.endOffsetMm / totalBarMm) * w).coerceIn(startX, w)
            val pieceWidthPx = (endX - startX).coerceAtLeast(2f)
            val pieceColor = cutPiecePaletteColor(idx)

            drawRect(
                color = pieceColor.copy(alpha = 0.85f),
                topLeft = Offset(startX, 0f),
                size = Size(pieceWidthPx, h)
            )
            drawRect(
                color = Color(0xFF0F172A),
                topLeft = Offset(startX, 0f),
                size = Size(pieceWidthPx, h),
                style = Stroke(width = 1.5f)
            )

            // Draw Kerf line after piece
            if (piece.kerfAfterMm > 0f) {
                val kerfPx = ((piece.kerfAfterMm / totalBarMm) * w).coerceAtLeast(2f)
                drawRect(
                    color = Color(0xFFEF4444),
                    topLeft = Offset(endX, 0f),
                    size = Size(kerfPx.coerceAtMost(w - endX), h)
                )
            }

            // Label inside piece if wide enough
            if (pieceWidthPx >= 46f) {
                val label = if (pieceWidthPx >= 90f) {
                    "#${piece.pieceNumberOnBar}: ${piece.lengthMm.roundToInt()}مم"
                } else {
                    "${piece.lengthMm.roundToInt()}"
                }
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    startX + pieceWidthPx * 0.5f,
                    (h * 0.5f) + 8f,
                    textPaint
                )
            }
        }

        // Draw Tail Waste / Offcut segment on right
        if (bar.remnantOffcutMm > 1f) {
            val wasteStartX = ((bar.usedLengthWithKerfAndTrimMm / totalBarMm) * w).coerceIn(0f, w)
            val wasteWidthPx = (w - wasteStartX).coerceAtLeast(0f)
            if (wasteWidthPx > 1f) {
                drawRect(
                    color = Color(0xFF7F1D1D).copy(alpha = 0.65f),
                    topLeft = Offset(wasteStartX, 0f),
                    size = Size(wasteWidthPx, h)
                )
                // Diagonal hatch lines for waste region
                var hx = wasteStartX
                while (hx < w) {
                    drawLine(
                        color = Color(0xFFF87171).copy(alpha = 0.45f),
                        start = Offset(hx, h),
                        end = Offset((hx + 12f).coerceAtMost(w), 0f),
                        strokeWidth = 1.5f
                    )
                    hx += 12f
                }
                if (wasteWidthPx >= 55f) {
                    drawContext.canvas.nativeCanvas.drawText(
                        "فضلة ${bar.remnantOffcutMm.roundToInt()}مم",
                        wasteStartX + wasteWidthPx * 0.5f,
                        (h * 0.5f) + 8f,
                        textPaint
                    )
                }
            }
        }
    }
}

private fun cutPiecePaletteColor(index: Int): Color {
    val palette = listOf(
        Color(0xFF0284C7), // Sky Blue
        Color(0xFF0D9488), // Teal
        Color(0xFF2563EB), // Royal Blue
        Color(0xFF059669), // Emerald
        Color(0xFF7C3AED), // Violet
        Color(0xFFD97706), // Amber
        Color(0xFF0891B2)  // Cyan
    )
    return palette[index % palette.size]
}

@Composable
private fun NestingKpiMetric(
    title: String,
    value: String,
    subValue: String,
    color: Color,
    tag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .testTag(tag)
    ) {
        Text(text = title, color = Color(0xFF94A3B8), fontSize = 10.sp)
        Text(text = value, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(text = subValue, color = Color(0xFFCBD5E1), fontSize = 9.sp)
    }
}

@Composable
private fun SummaryMetricRow(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = if (highlight) Color(0xFFFDE68A) else Color(0xFFCBD5E1),
            fontSize = 11.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            color = if (highlight) Color(0xFF34D399) else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
