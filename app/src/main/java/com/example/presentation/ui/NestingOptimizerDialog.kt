package com.example.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.cam.*
import kotlin.math.min

/**
 * Section 4: Technical Material Calculation, Cut-List & Manual Override Screen
 * (شاشة حساب الخامات والتفصيل الفني والتقطيع اليدوي)
 * - Aluminum Profiles Calculation (Horizontal & Vertical bars per cabinet, total linear meters, 6m bars with waste %)
 * - Melamine Boards & Cut Parts (Sides, Bottoms & Shelves with manual board deduction, Top Stretchers / سخات الميلامين)
 * - Interactive Cabinet Backing System (نعم يوجد ظهر / لا يوجد ظهر + تفريغ مجسم الـ 3D وتصفير الحساب فوراً)
 * - Dynamic Manual Override UI with Save as Custom Preset (حفظ التشكيلة كقالب مخصص)
 */
@Composable
fun NestingOptimizerDialog(
    cabinets: List<ParametricCabinet>,
    savedPresets: List<CustomMaterialOverridePreset> = CustomMaterialOverridePreset.DEFAULT_PRESETS,
    activePreset: CustomMaterialOverridePreset = CustomMaterialOverridePreset.DEFAULT_PRESETS.first(),
    onApplyManualOverride: (CustomMaterialOverridePreset) -> Unit = {},
    onSaveCustomPreset: (CustomMaterialOverridePreset) -> Unit = {},
    onToggleBackingAll: (Boolean, String, Float) -> Unit = { _, _, _ -> },
    onOpenAluminumBarNesting: () -> Unit = {},
    onExportCncDxf: () -> Unit = {},
    onDismiss: () -> Unit
) {
    // Dynamic Manual Override UI state
    var presetNameText by remember(activePreset.name) { mutableStateOf(activePreset.name) }
    var melamineThickText by remember(activePreset.melamineThicknessMm) {
        mutableStateOf(String.format(java.util.Locale.US, "%.1f", activePreset.melamineThicknessMm))
    }
    var manualWidthDeductionText by remember(activePreset.manualBoardWidthDeductionMm) {
        mutableStateOf(
            if (activePreset.manualBoardWidthDeductionMm > 0f)
                String.format(java.util.Locale.US, "%.1f", activePreset.manualBoardWidthDeductionMm)
            else ""
        )
    }
    var stretcherWidthText by remember(activePreset.topStretcherWidthMm) {
        mutableStateOf(activePreset.topStretcherWidthMm.toInt().toString())
    }
    var aluProfileWidthText by remember(activePreset.aluminumProfileWidthMm) {
        mutableStateOf(activePreset.aluminumProfileWidthMm.toInt().toString())
    }
    var aluProfileThickText by remember(activePreset.aluminumProfileThicknessMm) {
        mutableStateOf(String.format(java.util.Locale.US, "%.1f", activePreset.aluminumProfileThicknessMm))
    }
    var aluWastePctText by remember(activePreset.aluminumWastePercentage) {
        mutableStateOf(activePreset.aluminumWastePercentage.toInt().toString())
    }
    var hasBackPanel by remember(activePreset.hasBackPanel) { mutableStateOf(activePreset.hasBackPanel) }
    var backMaterialTypeText by remember(activePreset.backPanelMaterialType) { mutableStateOf(activePreset.backPanelMaterialType) }
    var backGrooveInsetText by remember(activePreset.backGrooveInsetMm) {
        mutableStateOf(activePreset.backGrooveInsetMm.toInt().toString())
    }

    val liveOverridePreset = remember(
        presetNameText, melamineThickText, manualWidthDeductionText, stretcherWidthText,
        aluProfileWidthText, aluProfileThickText, aluWastePctText, hasBackPanel, backMaterialTypeText, backGrooveInsetText
    ) {
        val boardThick = (melamineThickText.toFloatOrNull() ?: 18f).coerceIn(8f, 36f)
        val manualDed = (manualWidthDeductionText.toFloatOrNull() ?: -1f)
        val stretchW = (stretcherWidthText.toFloatOrNull() ?: 100f).coerceIn(50f, 250f)
        val aluW = (aluProfileWidthText.toFloatOrNull() ?: 45f).coerceIn(15f, 120f)
        val aluT = (aluProfileThickText.toFloatOrNull() ?: 2f).coerceIn(0.8f, 6f)
        val aluWaste = (aluWastePctText.toFloatOrNull() ?: 8f).coerceIn(0f, 35f)
        val groove = if (hasBackPanel) (backGrooveInsetText.toFloatOrNull() ?: 10f).coerceIn(0f, 30f) else 0f

        CustomMaterialOverridePreset(
            id = "preset_${presetNameText.hashCode()}",
            nameAr = presetNameText.ifBlank { "قالب مخصص للورشة" },
            melamineThicknessMm = boardThick,
            backPanelThicknessMm = if (hasBackPanel) 6f else 0f,
            hasBackPanel = hasBackPanel,
            backPanelMaterialName = backMaterialTypeText,
            backGrooveInsetMm = groove,
            topStretcherWidthMm = stretchW,
            aluminumProfileSectionSizeMm = aluW,
            aluminumProfileThicknessMm = aluT,
            customWidthDeductionMm = if (manualDed > 0f) manualDed else (boardThick * 2f),
            aluminumWastePercentage = aluWaste
        )
    }

    val overriddenCabinets = remember(cabinets, liveOverridePreset) {
        cabinets.map { cab ->
            cab.copy(
                panelThicknessMm = liveOverridePreset.melamineThicknessMm,
                backPanelThicknessMm = liveOverridePreset.backPanelThicknessMm,
                hasBackPanel = liveOverridePreset.hasBackPanel,
                backPanelMaterialName = liveOverridePreset.backPanelMaterialName,
                backGrooveInsetMm = liveOverridePreset.backGrooveInsetMm,
                topStretcherWidthMm = liveOverridePreset.topStretcherWidthMm,
                aluminumProfileSectionSizeMm = liveOverridePreset.aluminumProfileSectionSizeMm,
                aluminumProfileThicknessMm = liveOverridePreset.aluminumProfileThicknessMm,
                manualWidthDeductionMm = liveOverridePreset.customWidthDeductionMm,
                aluminumWastePercentage = liveOverridePreset.aluminumWastePercentage
            )
        }
    }

    val report = remember(overriddenCabinets) {
        NestingEngine.calculateCompleteReport(cabinets = overriddenCabinets)
    }

    val allCutParts = remember(overriddenCabinets) {
        overriddenCabinets.flatMap { it.generateCarcassCutParts() }
    }

    var selectedSheetIndex by remember { mutableIntStateOf(0) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: التفصيل الفني والميلامين والظهر, 1: حساب بروفايلات الألمنيوم, 2: تعشيق الألواح 2D, 3: التكلفة والإكسسوارات

    // Aluminum 1D Bar Nesting interactive parameters in Tab 1
    var aluBarLengthInputText by remember { mutableStateOf("6000") }
    var aluSawKerfInputText by remember { mutableStateOf("4.0") }
    var aluNestingStrategy by remember { mutableStateOf(AluminumNestingStrategy.BEST_FIT_DECREASING) }

    val aluNestingResult = remember(overriddenCabinets, aluBarLengthInputText, aluSawKerfInputText, aluNestingStrategy) {
        val barLenMm = (aluBarLengthInputText.toFloatOrNull() ?: 6000f).coerceIn(500f, 12000f)
        val kerfMm = (aluSawKerfInputText.toFloatOrNull() ?: 4.0f).coerceIn(0f, 20f)
        val cutInputs = AluminumBarNestingOptimizer.extractCabinetryCutInputs(overriddenCabinets)
        AluminumBarNestingOptimizer.optimizeCutList(
            cutInputs = cutInputs,
            config = AluminumNestingConfig(
                primaryBarLengthMm = barLenMm,
                availableStandardBarLengthsMm = listOf(barLenMm, 6000f, 5800f, 6500f, 4500f).distinct(),
                sawKerfMm = kerfMm,
                strategy = aluNestingStrategy
            )
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A),
            tonalElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "4. شاشة حساب الخامات والتفصيل الفني والتقطيع اليدوي",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                        Text(
                            text = "حساب بروفايلات الألمنيوم بالمتر الطولي، جوانب وأرضيات وسخات الميلامين، التحكم الفوري بالظهر، وحفظ القوالب المخصصة",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onExportCncDxf,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(36.dp).testTag("btn_nesting_export_cnc_dxf")
                        ) {
                            Icon(Icons.Default.Architecture, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تصدير CNC DXF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Global KPI Cards
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    KpiCard(
                        title = "إجمالي الألمنيوم (مع الهدر)",
                        value = "%.2f م.ط (%d قضيب 6م)".format(
                            report.aluminumSummary.totalMetersWithWaste,
                            report.aluminumSummary.requiredStandardBarsCount
                        ),
                        color = Color(0xFF38BDF8)
                    )
                    KpiCard(
                        title = "ألواح الميلامين المطلوبة",
                        value = "${report.totalRawSheetsCount} ألواح",
                        color = Color(0xFF10B981)
                    )
                    KpiCard(
                        title = "حالة ظهر الخزائن",
                        value = if (hasBackPanel) "مفعّل ($backMaterialTypeText)" else "ملغى (0 لوح | مفرّغ 3D)",
                        color = if (hasBackPanel) Color(0xFF34D399) else Color(0xFFF59E0B)
                    )
                    KpiCard(
                        title = "نسبة الهدر (Waste)",
                        value = "%.1f%%".format(report.overallWoodWastePct),
                        color = if (report.overallWoodWastePct > 20f) Color(0xFFEF4444) else Color(0xFFF59E0B)
                    )
                    KpiCard(
                        title = "التكلفة التقديرية",
                        value = "$%.1f".format(report.estimatedTotalCost),
                        color = Color(0xFF34D399)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Navigation Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color.White
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.testTag("nesting_tab_manual_override"),
                        text = { Text("التحكم اليدوي وتفصيل الميلامين", fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.testTag("nesting_tab_aluminum"),
                        text = { Text("بروفايلات الألمنيوم (${report.totalAluminumBarsCount} قضيب)", fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        modifier = Modifier.testTag("nesting_tab_sheets"),
                        text = { Text("مخططات قص الألواح (${report.packedWoodSheets.size})", fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        modifier = Modifier.testTag("nesting_tab_bom"),
                        text = { Text("جدول المواد والتكلفة (BOM)", fontSize = 11.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                when (selectedTab) {
                    0 -> {
                        // Tab 0: Manual Override UI + Cabinet Backing System + Melamine Cut Breakdown (Sides, Bottoms/Shelves, Top Stretchers)
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 1. Saved Custom Presets Bar
                            item {
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            "القوالب المخصصة المحفوظة (Saved Workshop Presets):",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(savedPresets, key = { it.id }) { p ->
                                                FilterChip(
                                                    selected = presetNameText == p.name,
                                                    onClick = {
                                                        presetNameText = p.name
                                                        melamineThickText = p.melamineThicknessMm.toString()
                                                        manualWidthDeductionText = if (p.manualBoardWidthDeductionMm > 0f) p.manualBoardWidthDeductionMm.toString() else ""
                                                        stretcherWidthText = p.topStretcherWidthMm.toInt().toString()
                                                        aluProfileWidthText = p.aluminumProfileWidthMm.toInt().toString()
                                                        aluProfileThickText = p.aluminumProfileThicknessMm.toString()
                                                        aluWastePctText = p.aluminumWastePercentage.toInt().toString()
                                                        hasBackPanel = p.hasBackPanel
                                                        backMaterialTypeText = p.backPanelMaterialType
                                                        backGrooveInsetText = p.backGrooveInsetMm.toInt().toString()
                                                        onApplyManualOverride(p)
                                                    },
                                                    label = { Text(p.name, fontSize = 11.sp) },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = Color(0xFF0284C7),
                                                        selectedLabelColor = Color.White
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. Interactive Cabinet Backing System (نظام التحكم بالظهر)
                            item {
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (hasBackPanel) Color(0xFF10B981) else Color(0xFFF59E0B)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "نظام التحكم بالظهر (Cabinet Backing Options): ${if (hasBackPanel) "نعم يوجد ظهر" else "لا يوجد ظهر"}",
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    if (hasBackPanel)
                                                        "الحساب نشط: يتم حساب مقاس الظهر مع إضافة تداخل مجرى الريشة (${liveOverridePreset.backGrooveInsetMm.toInt()} مم من كل جهة)."
                                                    else
                                                        "تم الإلغاء: تم تصفير حسابات الظهر تماماً وتفريغ مجسم الـ 3D للخزائن فوراً.",
                                                    color = if (hasBackPanel) Color(0xFF34D399) else Color(0xFFFBBF24),
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Switch(
                                                checked = hasBackPanel,
                                                onCheckedChange = { enabled ->
                                                    hasBackPanel = enabled
                                                    val groove = if (enabled) (backGrooveInsetText.toFloatOrNull() ?: 10f) else 0f
                                                    onToggleBackingAll(enabled, backMaterialTypeText, groove)
                                                },
                                                modifier = Modifier.testTag("nesting_backing_switch")
                                            )
                                        }

                                        if (hasBackPanel) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = backMaterialTypeText,
                                                    onValueChange = { backMaterialTypeText = it },
                                                    label = { Text("نوع خامة الظهر (مثال: MDF 6mm / كلادينج)", fontSize = 10.sp) },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                OutlinedTextField(
                                                    value = backGrooveInsetText,
                                                    onValueChange = { backGrooveInsetText = it },
                                                    label = { Text("نسبة التداخل للمجرى / الريشة (مم)", fontSize = 10.sp) },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Dynamic Manual Override UI (لوحة التحكم الديناميكية بالخامات والخصومات)
                            item {
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "لوحة التحكم الديناميكية بالخامات والخصومات (Manual Override UI):",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = melamineThickText,
                                                onValueChange = { melamineThickText = it },
                                                label = { Text("سُمك لوح الميلامين (مم)", fontSize = 10.sp) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                singleLine = true,
                                                modifier = Modifier.weight(1f).testTag("manual_melamine_thick_input")
                                            )
                                            OutlinedTextField(
                                                value = manualWidthDeductionText,
                                                onValueChange = { manualWidthDeductionText = it },
                                                label = { Text("خصم سمك اللوح يدوياً (افتراضي: ${(liveOverridePreset.melamineThicknessMm * 2).toInt()} مم)", fontSize = 10.sp) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                singleLine = true,
                                                modifier = Modifier.weight(1f).testTag("manual_width_deduction_input")
                                            )
                                            OutlinedTextField(
                                                value = stretcherWidthText,
                                                onValueChange = { stretcherWidthText = it },
                                                label = { Text("عرض سخات الميلامين العلوية (مم)", fontSize = 10.sp) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                modifier = Modifier.weight(1f).testTag("manual_stretcher_width_input")
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = aluProfileWidthText,
                                                onValueChange = { aluProfileWidthText = it },
                                                label = { Text("عرض بروفايل الألمنيوم (مم)", fontSize = 10.sp) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedTextField(
                                                value = aluProfileThickText,
                                                onValueChange = { aluProfileThickText = it },
                                                label = { Text("سُمك جدار الألمنيوم (مم)", fontSize = 10.sp) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedTextField(
                                                value = aluWastePctText,
                                                onValueChange = { aluWastePctText = it },
                                                label = { Text("نسبة هدر الألمنيوم (%)", fontSize = 10.sp) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = presetNameText,
                                                onValueChange = { presetNameText = it },
                                                label = { Text("اسم التشكيلة / القالب المخصص", fontSize = 10.sp) },
                                                singleLine = true,
                                                modifier = Modifier.weight(1.2f).testTag("custom_preset_name_input")
                                            )
                                            Button(
                                                onClick = { onApplyManualOverride(liveOverridePreset) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("تطبيق على المشهد 3D", fontSize = 11.sp)
                                            }
                                            Button(
                                                onClick = { onSaveCustomPreset(liveOverridePreset) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                                modifier = Modifier.weight(1f).testTag("save_custom_preset_btn")
                                            ) {
                                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("حفظ كقالب مخصص", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            // 4. Melamine Cut Parts Breakdown by Category (جوانب الميلامين، أرضيات الميلامين، سخات الميلامين، الظهر)
                            val groupedCategories = listOf(
                                CutPartCategory.MELAMINE_SIDE,
                                CutPartCategory.MELAMINE_BOTTOM_AND_SHELF,
                                CutPartCategory.MELAMINE_TOP_STRETCHER,
                                CutPartCategory.CABINET_BACK_PANEL
                            )

                            items(groupedCategories) { cat ->
                                val partsInCat = allCutParts.filter { it.category == cat }
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
                                                text = cat.arabicTitle,
                                                color = Color(0xFF38BDF8),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "إجمالي القطع: ${partsInCat.sumOf { it.quantity }} قطعة",
                                                color = Color(0xFF34D399),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        if (partsInCat.isEmpty()) {
                                            Text(
                                                text = if (cat == CutPartCategory.CABINET_BACK_PANEL && !hasBackPanel)
                                                    "تم تصفير حساب الظهر (لا يوجد ظهر مفعّل)"
                                                else "لا توجد قطع ضمن هذه الفئة",
                                                color = Color(0xFFF59E0B),
                                                fontSize = 11.sp
                                            )
                                        } else {
                                            partsInCat.take(12).forEach { part ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 2.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "• ${part.cabinetName} — ${part.partName}",
                                                        color = Color.White,
                                                        fontSize = 11.sp
                                                    )
                                                    Text(
                                                        text = "${part.quantity} × [${part.lengthMm.toInt()} × ${part.widthMm.toInt()} مم | (${(part.lengthMm / 10f)}×${(part.widthMm / 10f)} سم)]",
                                                        color = Color(0xFFCBD5E1),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // Tab 1: Aluminum Profiles Engineering Summary + Interactive 1D Bar Nesting Cut List
                        val aluSummary = report.aluminumSummary
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "1. إعداد طول القضيب القياسي وخوارزمية تعشيق الألمنيوم (1D Bar Nesting):",
                                                color = Color(0xFF38BDF8),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Button(
                                                onClick = onOpenAluminumBarNesting,
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.testTag("open_dedicated_alu_nesting_from_tab")
                                            ) {
                                                Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("فتح وحدة تعشيق الألمنيوم المتقدمة", fontSize = 10.sp)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = aluBarLengthInputText,
                                                onValueChange = { aluBarLengthInputText = it },
                                                label = { Text("طول القضيب القياسي (مم)", fontSize = 10.sp) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                modifier = Modifier.weight(1f).testTag("tab_alu_bar_length_input")
                                            )
                                            OutlinedTextField(
                                                value = aluSawKerfInputText,
                                                onValueChange = { aluSawKerfInputText = it },
                                                label = { Text("شفرة المنشار Kerf (مم)", fontSize = 10.sp) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                singleLine = true,
                                                modifier = Modifier.weight(0.8f).testTag("tab_alu_kerf_input")
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                listOf("6000", "5800", "6500", "4500").forEach { presetLen ->
                                                    FilterChip(
                                                        selected = aluBarLengthInputText == presetLen,
                                                        onClick = { aluBarLengthInputText = presetLen },
                                                        label = { Text("${presetLen}مم", fontSize = 10.sp) }
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(AluminumNestingStrategy.values()) { strat ->
                                                FilterChip(
                                                    selected = aluNestingStrategy == strat,
                                                    onClick = { aluNestingStrategy = strat },
                                                    label = { Text(strat.titleAr, fontSize = 10.sp) }
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider(color = Color(0xFF334155))
                                        Spacer(modifier = Modifier.height(6.dp))

                                        CostRow(
                                            "إجمالي أطوال القضبان الأفقية (Horizontal Bars)",
                                            "%.2f متر طولي".format(aluSummary.totalHorizontalMeters)
                                        )
                                        CostRow(
                                            "إجمالي أطوال القضبان العمودية (Vertical Bars)",
                                            "%.2f متر طولي".format(aluSummary.totalVerticalMeters)
                                        )
                                        CostRow(
                                            "الصافي الكلي للقطع الفردية (${aluNestingResult.totalIndividualCutPiecesCount} قطعة)",
                                            "%.2f متر طولي (%d مم)".format(aluNestingResult.totalNetCutMeters, aluNestingResult.totalNetCutLengthMm.toInt())
                                        )
                                        CostRow(
                                            "نسبة الاستغلال الكلية مقابل نسبة الهدر (Waste %)",
                                            "استغلال %.1f%% | هدر %.1f%% (فضلات: %d مم)".format(
                                                aluNestingResult.overallUtilizationPercentage,
                                                aluNestingResult.overallWastePercentage,
                                                aluNestingResult.totalRemnantOffcutMm.toInt()
                                            )
                                        )
                                        CostRow(
                                            "عدد القضبان القياسية المطلوبة بعد التعشيق",
                                            "${aluNestingResult.totalBarsCount} قضيب قياسي (${aluNestingResult.totalStockMeters} م.ط)",
                                            isTotal = true
                                        )
                                    }
                                }
                            }

                            items(aluNestingResult.bars, key = { "tab1_bar_${it.barIndex}" }) { bar ->
                                OptimizedAluminumBarCard(
                                    bar = bar,
                                    useCentimeters = false
                                )
                            }
                        }
                    }

                    2 -> {
                        // Tab 2: 2D Wood Sheets Nesting Diagram Viewer
                        if (report.packedWoodSheets.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                Text("لا توجد قطع قص متاحة", color = Color.Gray)
                            }
                        } else {
                            val activeSheet = report.packedWoodSheets.getOrElse(selectedSheetIndex) { report.packedWoodSheets.first() }

                            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    itemsIndexed(report.packedWoodSheets) { idx, sheet ->
                                        FilterChip(
                                            selected = selectedSheetIndex == idx,
                                            onClick = { selectedSheetIndex = idx },
                                            label = {
                                                Text(
                                                    "لوح #${sheet.sheetIndex} (${sheet.material.thicknessMm.toInt()}مم) - استغلال %.0f%%".format(sheet.utilizationPercentage),
                                                    fontSize = 11.sp
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFF0284C7),
                                                selectedLabelColor = Color.White
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .background(Color(0xFF020617), RoundedCornerShape(12.dp))
                                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val canvasW = size.width
                                        val canvasH = size.height

                                        val rawW = activeSheet.rawWidthMm
                                        val rawL = activeSheet.rawLengthMm

                                        val scale = min((canvasW - 40f) / rawW, (canvasH - 40f) / rawL)
                                        val originX = (canvasW - (rawW * scale)) * 0.5f
                                        val originY = (canvasH - (rawL * scale)) * 0.5f

                                        drawRect(
                                            color = Color(0xFF1E293B),
                                            topLeft = Offset(originX, originY),
                                            size = Size(rawW * scale, rawL * scale)
                                        )
                                        drawRect(
                                            color = Color(0xFF475569),
                                            topLeft = Offset(originX, originY),
                                            size = Size(rawW * scale, rawL * scale),
                                            style = Stroke(2f)
                                        )

                                        val paint = android.graphics.Paint().apply {
                                            color = android.graphics.Color.WHITE
                                            textSize = 22f
                                            textAlign = android.graphics.Paint.Align.CENTER
                                            isAntiAlias = true
                                        }

                                        activeSheet.placements.forEachIndexed { i, p ->
                                            val px = originX + (p.x * scale)
                                            val py = originY + (p.y * scale)
                                            val pw = p.widthMm * scale
                                            val pl = p.lengthMm * scale

                                            val partColor = if (i % 2 == 0) Color(0xFF0284C7).copy(alpha = 0.85f) else Color(0xFF0369A1).copy(alpha = 0.85f)
                                            drawRect(
                                                color = partColor,
                                                topLeft = Offset(px, py),
                                                size = Size(pw, pl)
                                            )
                                            drawRect(
                                                color = Color(0xFFF59E0B),
                                                topLeft = Offset(px, py),
                                                size = Size(pw, pl),
                                                style = Stroke(1.5f)
                                            )

                                            if (pw > 50f && pl > 30f) {
                                                drawContext.canvas.nativeCanvas.drawText(
                                                    "${p.widthMm.toInt()}×${p.lengthMm.toInt()}",
                                                    px + (pw * 0.5f),
                                                    py + (pl * 0.5f) + 6f,
                                                    paint
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "مقاس اللوح: ${activeSheet.rawWidthMm.toInt()} × ${activeSheet.rawLengthMm.toInt()} مم | المساحة المستغلة: %.2f م² | الهدر: %.2f م² (%.1f%%)".format(
                                        activeSheet.usedAreaSqMm / 1_000_000f,
                                        activeSheet.wasteAreaSqM,
                                        activeSheet.wastePercentage
                                    ),
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    3 -> {
                        // Tab 3: Cost & BOM Summary
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Text("تفاصيل التكاليف التقديرية (Cost Breakdown):", color = Color(0xFF38BDF8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            item {
                                CostRow("تكلفة ألواح الميلامين والـ MDF (${report.totalMdf18Sheets} لوح هيكل + ${report.totalMdf8Sheets} لوح ظهر)", "$%.2f".format(report.estimatedWoodCost))
                                CostRow("تكلفة بروفايلات الألمنيوم (${report.totalAluminumBarsCount} قضبان قياسية 6م)", "$%.2f".format(report.estimatedAluminumCost))
                                CostRow("شريط القشاط PVC (%.1f متر طولي)".format(report.totalEdgeBandingMeters), "$%.2f".format(report.estimatedEdgeBandingCost))
                                CostRow("الإكسسوارات والمفصلات والمجاري (${report.hardwareList.values.sum()} قطعة)", "$%.2f".format(report.estimatedHardwareCost))
                                HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 4.dp))
                                CostRow("إجمالي التكلفة التقديرية", "$%.2f".format(report.estimatedTotalCost), isTotal = true)
                            }

                            item {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("حصر الإكسسوارات والعتاد (Hardware Inventory):", color = Color(0xFF38BDF8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            items(report.hardwareList.entries.toList()) { (item, qty) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(item, color = Color.White, fontSize = 12.sp)
                                    Text("$qty قطعة", color = Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    modifier = Modifier.align(Alignment.End).testTag("close_nesting_dialog_btn")
                ) {
                    Text("إغلاق / Done")
                }
            }
        }
    }
}

@Composable
private fun KpiCard(title: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Color(0xFF94A3B8), fontSize = 10.sp)
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun CostRow(title: String, amount: String, isTotal: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = if (isTotal) Color(0xFFF59E0B) else Color.White, fontSize = if (isTotal) 13.sp else 12.sp, fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal)
        Text(amount, color = if (isTotal) Color(0xFF34D399) else Color(0xFF38BDF8), fontSize = if (isTotal) 14.sp else 12.sp, fontWeight = FontWeight.Bold)
    }
}
