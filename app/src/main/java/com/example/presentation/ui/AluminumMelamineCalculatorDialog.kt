package com.example.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.cam.*
import com.example.domain.model.KitchenElement
import com.example.domain.model.MaterialFinish
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class AluminumProfileSystem(
    val titleAr: String,
    val sectionMm: Float,
    val wallThickMm: Float,
    val includeCarcassFrame: Boolean,
    val includeGolaProfile: Boolean,
    val includeDoorFrameProfile: Boolean
) {
    FULL_ALUMINUM_MELAMINE(
        titleAr = "هيكل ألمنيوم كامل + حشوات ميلامين + مقبض Gola",
        sectionMm = 45f,
        wallThickMm = 1.8f,
        includeCarcassFrame = true,
        includeGolaProfile = true,
        includeDoorFrameProfile = false
    ),
    ALUMINUM_GLASS_DOORS(
        titleAr = "هيكل ألمنيوم وميلامين + درف إطار ألمنيوم وزجاج",
        sectionMm = 45f,
        wallThickMm = 2.0f,
        includeCarcassFrame = true,
        includeGolaProfile = true,
        includeDoorFrameProfile = true
    ),
    MELAMINE_WITH_GOLA_AND_EDGE_ALU(
        titleAr = "صندوق ميلامين + قضبان مقبض مخفي G-Profile وإطار درف",
        sectionMm = 35f,
        wallThickMm = 1.6f,
        includeCarcassFrame = false,
        includeGolaProfile = true,
        includeDoorFrameProfile = true
    )
}

@Composable
fun AluminumMelamineCalculatorDialog(
    selectedElement: KitchenElement?,
    sceneCabinets: List<ParametricCabinet>,
    onCreateOrUpdateCabinetIn3D: (ParametricCabinet) -> Unit,
    onOpenFullNestingOptimizer: () -> Unit,
    onOpenAluminumBarNesting: () -> Unit = {},
    onDismiss: () -> Unit
) {
    // Mode: 0 = مقاسات مخصصة أدخلها بنفسي (Custom Input), 1 = حساب شامل لكل خزائن المطبخ 3D الحالي
    var calcScopeMode by remember { mutableIntStateOf(0) }

    // Input unit toggle: true = سم (cm), false = مم (mm)
    var useCentimeters by remember { mutableStateOf(true) }

    // Custom Dimension Inputs (initialized from selected 3D cabinet if any, else 80x85x60 cm)
    var widthInputText by remember(selectedElement?.id, useCentimeters) {
        val mm = selectedElement?.dimensions?.x ?: 800f
        mutableStateOf(if (useCentimeters) (mm / 10f).roundToInt().toString() else mm.roundToInt().toString())
    }
    var heightInputText by remember(selectedElement?.id, useCentimeters) {
        val mm = selectedElement?.dimensions?.y ?: 850f
        mutableStateOf(if (useCentimeters) (mm / 10f).roundToInt().toString() else mm.roundToInt().toString())
    }
    var depthInputText by remember(selectedElement?.id, useCentimeters) {
        val mm = selectedElement?.dimensions?.z ?: 600f
        mutableStateOf(if (useCentimeters) (mm / 10f).roundToInt().toString() else mm.roundToInt().toString())
    }
    var toeKickInputText by remember(selectedElement?.id, useCentimeters) {
        val mm = if ((selectedElement?.position?.y ?: 0f) >= 800f) 0f else 100f
        mutableStateOf(if (useCentimeters) (mm / 10f).roundToInt().toString() else mm.roundToInt().toString())
    }

    var unitQuantity by remember { mutableIntStateOf(1) }
    var shelvesCount by remember { mutableIntStateOf(1) }
    var drawersCount by remember { mutableIntStateOf(0) }

    // Aluminum & Melamine Technical Parameters
    var selectedSystem by remember { mutableStateOf(AluminumProfileSystem.FULL_ALUMINUM_MELAMINE) }
    var standardBarLengthMm by remember { mutableFloatStateOf(6000f) } // 6.0m, 5.8m, 6.5m, 4.5m or custom
    var customBarLengthText by remember { mutableStateOf("6000") }
    var aluminumSectionSizeText by remember { mutableStateOf("45") }
    var aluminumThicknessText by remember { mutableStateOf("1.8") }
    var aluminumWastePctText by remember { mutableStateOf("8") }

    var melamineThicknessMm by remember { mutableFloatStateOf(18f) }
    var topStretcherWidthText by remember { mutableStateOf("100") }
    var hasBackPanel by remember { mutableStateOf(true) }
    var backGrooveInsetText by remember { mutableStateOf("10") }
    var sheetWidthMm by remember { mutableFloatStateOf(2440f) }
    var sheetLengthMm by remember { mutableFloatStateOf(1220f) }

    val unitMultiplier = if (useCentimeters) 10f else 1f
    val widthMm = ((widthInputText.toFloatOrNull() ?: if (useCentimeters) 80f else 800f) * unitMultiplier).coerceIn(250f, 3000f)
    val heightMm = ((heightInputText.toFloatOrNull() ?: if (useCentimeters) 85f else 850f) * unitMultiplier).coerceIn(300f, 2600f)
    val depthMm = ((depthInputText.toFloatOrNull() ?: if (useCentimeters) 60f else 600f) * unitMultiplier).coerceIn(250f, 1200f)
    val toeKickMm = ((toeKickInputText.toFloatOrNull() ?: 0f) * unitMultiplier).coerceIn(0f, 250f)

    val aluSectionMm = (aluminumSectionSizeText.toFloatOrNull() ?: selectedSystem.sectionMm).coerceIn(15f, 120f)
    val aluThickMm = (aluminumThicknessText.toFloatOrNull() ?: selectedSystem.wallThickMm).coerceIn(0.8f, 5f)
    val aluWastePct = (aluminumWastePctText.toFloatOrNull() ?: 8f).coerceIn(0f, 35f)
    val stretcherWidthMm = (topStretcherWidthText.toFloatOrNull() ?: 100f).coerceIn(50f, 250f)
    val grooveInsetMm = if (hasBackPanel) (backGrooveInsetText.toFloatOrNull() ?: 10f).coerceIn(0f, 25f) else 0f

    val customCabinet = remember(
        widthMm, heightMm, depthMm, toeKickMm, shelvesCount, drawersCount,
        selectedSystem, aluSectionMm, aluThickMm, aluWastePct,
        melamineThicknessMm, stretcherWidthMm, hasBackPanel, grooveInsetMm, selectedElement?.id
    ) {
        ParametricCabinet(
            id = selectedElement?.id ?: "alu_custom_cab",
            name = selectedElement?.name ?: "وحدة ألمنيوم وميلامين (${(widthMm / 10f).roundToInt()}×${(heightMm / 10f).roundToInt()} سم)",
            widthMm = widthMm,
            heightMm = heightMm,
            depthMm = depthMm,
            toeKickHeightMm = toeKickMm,
            panelThicknessMm = melamineThicknessMm,
            numberOfShelves = shelvesCount,
            numberOfDrawers = drawersCount,
            doorStyle = if (selectedSystem.includeDoorFrameProfile) DoorStyle.ALUMINUM_GLASS else DoorStyle.SLAB_MODERN,
            handleStyle = if (selectedSystem.includeGolaProfile) HandleStyle.J_PULL_GOLA else HandleStyle.BAR_PULL,
            carcassMaterial = if (melamineThicknessMm <= 16.5f) PanelMaterial.MDF_16MM else PanelMaterial.MDF_18MM,
            facadeMaterial = selectedElement?.material ?: MaterialFinish.NATURAL_OAK,
            hasBackPanel = hasBackPanel,
            backPanelMaterialName = if (hasBackPanel) "MDF ميلامين 8 مم" else "بدون ظهر",
            backGrooveInsetMm = grooveInsetMm,
            topStretcherWidthMm = stretcherWidthMm,
            aluminumProfileThicknessMm = aluThickMm,
            aluminumProfileSectionSizeMm = aluSectionMm,
            aluminumWastePercentage = aluWastePct,
            includeStructuralAluminumFrame = selectedSystem.includeCarcassFrame
        )
    }

    val targetCabinetsList = remember(calcScopeMode, customCabinet, unitQuantity, sceneCabinets) {
        if (calcScopeMode == 0) {
            List(unitQuantity.coerceIn(1, 20)) { idx ->
                if (unitQuantity == 1) customCabinet else customCabinet.copy(id = "${customCabinet.id}_${idx + 1}", name = "${customCabinet.name} #${idx + 1}")
            }
        } else {
            sceneCabinets.map {
                it.copy(
                    panelThicknessMm = melamineThicknessMm,
                    hasBackPanel = hasBackPanel,
                    backGrooveInsetMm = grooveInsetMm,
                    topStretcherWidthMm = stretcherWidthMm,
                    aluminumProfileSectionSizeMm = aluSectionMm,
                    aluminumProfileThicknessMm = aluThickMm,
                    aluminumWastePercentage = aluWastePct,
                    includeStructuralAluminumFrame = selectedSystem.includeCarcassFrame
                )
            }
        }
    }

    val aluSummary = remember(targetCabinetsList, standardBarLengthMm, aluWastePct) {
        ParametricCabinet.calculateAluminumSummaryForCabinets(
            cabinets = targetCabinetsList,
            wastePercentageOverride = aluWastePct,
            standardBarLengthMeters = standardBarLengthMm / 1000f
        )
    }

    val packedBars = remember(aluSummary.cuts, standardBarLengthMm) {
        NestingEngine.packAluminumProfiles(
            cuts = aluSummary.cuts,
            barLengthMm = standardBarLengthMm,
            kerfMm = 4.0f
        )
    }

    val melamineParts = remember(targetCabinetsList) {
        targetCabinetsList.flatMap { it.generateCarcassCutParts() }
    }

    val totalMelamineAreaSqM = remember(melamineParts) {
        melamineParts.sumOf { it.areaSqM.toDouble() }.toFloat()
    }

    val singleSheetAreaSqM = (sheetWidthMm * sheetLengthMm) / 1_000_000f
    val estimatedMelamineSheets = remember(totalMelamineAreaSqM, singleSheetAreaSqM) {
        if (totalMelamineAreaSqM <= 0f) 0 else ceil((totalMelamineAreaSqM * 1.12f) / singleSheetAreaSqM).toInt()
    }

    val totalEdgeBandingMeters = remember(melamineParts) {
        melamineParts.sumOf { it.edgeBandingMeters.toDouble() }.toFloat()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .testTag("aluminum_melamine_calculator_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A),
            tonalElevation = 12.dp,
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "لوحة حساب بروفايلات الألمنيوم (قضبان) وألواح الميلامين حسب المقاسات",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Text(
                            text = "أدخل مقاسات الخزانة مباشرة لحساب أطوال وعدد قضبان الألمنيوم ومقاسات ألواح الميلامين بدقة هندسية",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Scope Switcher (Custom Entered Dimensions vs Full 3D Scene) + Unit Switcher (cm / mm)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = calcScopeMode == 0,
                            onClick = { calcScopeMode = 0 },
                            label = { Text("حسب المقاسات التي أدخلها (خزانة مخصصة)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0284C7),
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("calc_mode_custom_input")
                        )
                        FilterChip(
                            selected = calcScopeMode == 1,
                            onClick = { calcScopeMode = 1 },
                            label = { Text("حساب شامل للمطبخ 3D (${sceneCabinets.size} خزانة)", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF10B981),
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("calc_mode_full_scene")
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("وحدة الإدخال:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        FilterChip(
                            selected = useCentimeters,
                            onClick = { useCentimeters = true },
                            label = { Text("سنتيمتر (سم)", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = !useCentimeters,
                            onClick = { useCentimeters = false },
                            label = { Text("مليمتر (مم)", fontSize = 10.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Top Live Summary KPI Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    CalcKpiItem(
                        label = "قضبان الألمنيوم المطلوبة",
                        value = "${maxOf(aluSummary.requiredStandardBarsCount, packedBars.size)} قضيب (${(standardBarLengthMm / 1000f)}م)",
                        subValue = "صافي: ${"%.2f".format(aluSummary.netLinearMeters)} م.ط | مع الهدر: ${"%.2f".format(aluSummary.grossLinearMetersWithWaste)} م.ط",
                        color = Color(0xFF38BDF8)
                    )
                    CalcKpiItem(
                        label = "تفصيل القضبان (أفقي / عمودي)",
                        value = "أفقي: ${"%.2f".format(aluSummary.horizontalTotalMeters)} م | عمودي: ${"%.2f".format(aluSummary.verticalTotalMeters)} م",
                        subValue = "إجمالي القطع المقصوصة: ${aluSummary.cuts.sumOf { it.quantity }} قطعة",
                        color = Color(0xFFF59E0B)
                    )
                    CalcKpiItem(
                        label = "ألواح الميلامين المطلوبة",
                        value = "$estimatedMelamineSheets لوح (${(sheetWidthMm / 10f).toInt()}×${(sheetLengthMm / 10f).toInt()} سم)",
                        subValue = "المساحة: ${"%.2f".format(totalMelamineAreaSqM)} م² | شريط PVC: ${"%.1f".format(totalEdgeBandingMeters)} م.ط",
                        color = Color(0xFF10B981)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Section A: Direct Dimension Input Card (إدخال المقاسات المباشر)
                    if (calcScopeMode == 0) {
                        item {
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF0284C7)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    val unitLabel = if (useCentimeters) "سم" else "مم"
                                    Text(
                                        text = "1. أدخل مقاسات الخزانة / الوحدة المراد تفصيلها ($unitLabel):",
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = widthInputText,
                                            onValueChange = { widthInputText = it },
                                            label = { Text("العرض ($unitLabel)", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).testTag("alu_calc_width_input")
                                        )
                                        OutlinedTextField(
                                            value = heightInputText,
                                            onValueChange = { heightInputText = it },
                                            label = { Text("الارتفاع ($unitLabel)", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).testTag("alu_calc_height_input")
                                        )
                                        OutlinedTextField(
                                            value = depthInputText,
                                            onValueChange = { depthInputText = it },
                                            label = { Text("العمق ($unitLabel)", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).testTag("alu_calc_depth_input")
                                        )
                                        OutlinedTextField(
                                            value = toeKickInputText,
                                            onValueChange = { toeKickInputText = it },
                                            label = { Text("الأرجل/الوزرة ($unitLabel)", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Quick Standard Sizes Chips + Shelves/Drawers/Quantity Steppers
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            AssistChip(
                                                onClick = {
                                                    widthInputText = if (useCentimeters) "60" else "600"
                                                    heightInputText = if (useCentimeters) "85" else "850"
                                                    depthInputText = if (useCentimeters) "60" else "600"
                                                },
                                                label = { Text("سفلية 60×85×60", fontSize = 10.sp) }
                                            )
                                            AssistChip(
                                                onClick = {
                                                    widthInputText = if (useCentimeters) "90" else "900"
                                                    heightInputText = if (useCentimeters) "85" else "850"
                                                    depthInputText = if (useCentimeters) "60" else "600"
                                                },
                                                label = { Text("مجلى/سفلية 90×85×60", fontSize = 10.sp) }
                                            )
                                            AssistChip(
                                                onClick = {
                                                    widthInputText = if (useCentimeters) "80" else "800"
                                                    heightInputText = if (useCentimeters) "75" else "750"
                                                    depthInputText = if (useCentimeters) "35" else "350"
                                                    toeKickInputText = "0"
                                                },
                                                label = { Text("علوية 80×75×35", fontSize = 10.sp) }
                                            )
                                            AssistChip(
                                                onClick = {
                                                    widthInputText = if (useCentimeters) "60" else "600"
                                                    heightInputText = if (useCentimeters) "220" else "2200"
                                                    depthInputText = if (useCentimeters) "60" else "600"
                                                },
                                                label = { Text("برج مؤونة 60×220", fontSize = 10.sp) }
                                            )
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            StepperMini(label = "رفوف", count = shelvesCount, onChange = { shelvesCount = it.coerceIn(0, 6) })
                                            StepperMini(label = "أدراج", count = drawersCount, onChange = { drawersCount = it.coerceIn(0, 5) })
                                            StepperMini(label = "العدد", count = unitQuantity, onChange = { unitQuantity = it.coerceIn(1, 20) })
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section B: Aluminum Profile System & Melamine Sheet Specifications
                    item {
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "2. مواصفات قضبان الألمنيوم وألواح الميلامين:",
                                    color = Color(0xFFF59E0B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(AluminumProfileSystem.values()) { sys ->
                                        FilterChip(
                                            selected = selectedSystem == sys,
                                            onClick = {
                                                selectedSystem = sys
                                                aluminumSectionSizeText = sys.sectionMm.toInt().toString()
                                                aluminumThicknessText = sys.wallThickMm.toString()
                                            },
                                            label = { Text(sys.titleAr, fontSize = 10.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFFF59E0B).copy(alpha = 0.25f),
                                                selectedLabelColor = Color(0xFFFDE68A)
                                            )
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
                                        value = aluminumSectionSizeText,
                                        onValueChange = { aluminumSectionSizeText = it },
                                        label = { Text("مقطع القضيب (مم)", fontSize = 10.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = aluminumThicknessText,
                                        onValueChange = { aluminumThicknessText = it },
                                        label = { Text("سماكة الألمنيوم (مم)", fontSize = 10.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = aluminumWastePctText,
                                        onValueChange = { aluminumWastePctText = it },
                                        label = { Text("نسبة هدر القضبان %", fontSize = 10.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = topStretcherWidthText,
                                        onValueChange = { topStretcherWidthText = it },
                                        label = { Text("سخة الميلامين (مم)", fontSize = 10.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("طول القضيب القياسي:", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                                        FilterChip(
                                            selected = standardBarLengthMm == 6000f,
                                            onClick = {
                                                standardBarLengthMm = 6000f
                                                customBarLengthText = "6000"
                                            },
                                            label = { Text("6.0 م (6000مم)", fontSize = 10.sp) }
                                        )
                                        FilterChip(
                                            selected = standardBarLengthMm == 5800f,
                                            onClick = {
                                                standardBarLengthMm = 5800f
                                                customBarLengthText = "5800"
                                            },
                                            label = { Text("5.8 م (5800مم)", fontSize = 10.sp) }
                                        )
                                        FilterChip(
                                            selected = standardBarLengthMm == 6500f,
                                            onClick = {
                                                standardBarLengthMm = 6500f
                                                customBarLengthText = "6500"
                                            },
                                            label = { Text("6.5 م (6500مم)", fontSize = 10.sp) }
                                        )
                                        OutlinedTextField(
                                            value = customBarLengthText,
                                            onValueChange = { txt ->
                                                customBarLengthText = txt
                                                txt.toFloatOrNull()?.let { parsed ->
                                                    standardBarLengthMm = parsed.coerceIn(500f, 12000f)
                                                }
                                            },
                                            label = { Text("طول مخصص (مم)", fontSize = 9.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.width(110.dp).testTag("calc_custom_bar_length_input")
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("سمك الميلامين:", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                                        FilterChip(
                                            selected = melamineThicknessMm == 18f,
                                            onClick = { melamineThicknessMm = 18f },
                                            label = { Text("18 مم", fontSize = 10.sp) }
                                        )
                                        FilterChip(
                                            selected = melamineThicknessMm == 16f,
                                            onClick = { melamineThicknessMm = 16f },
                                            label = { Text("16 مم", fontSize = 10.sp) }
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("ظهر الخزانة:", color = Color(0xFFCBD5E1), fontSize = 10.sp)
                                            Switch(checked = hasBackPanel, onCheckedChange = { hasBackPanel = it })
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section C: Detailed Aluminum Bars Cut-List & Bar Packing Breakdown
                    item {
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "3. كشف قص قضبان وبروفايلات الألمنيوم بالتفصيل:",
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "إجمالي القضبان: ${maxOf(aluSummary.requiredStandardBarsCount, packedBars.size)} قضيب × ${(standardBarLengthMm / 1000f)}م",
                                        color = Color(0xFF34D399),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                aluSummary.cuts.forEach { cut ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(cut.profileName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            Text(cut.orientation.labelAr, color = Color(0xFF94A3B8), fontSize = 10.sp)
                                        }
                                        Text(
                                            text = "العدد: ${cut.quantity} × الطول: ${cut.lengthMm.roundToInt()} مم (${(cut.lengthMm / 10f)} سم) = ${"%.2f".format(cut.totalLengthMeters)} م.ط",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (packedBars.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "توزيع القص على القضبان القياسية (1D Bar Nesting - شامل نسب الهدر والقطع الفردية):",
                                        color = Color(0xFFFDE68A),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    packedBars.forEach { bar ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                                .padding(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "قضيب #${bar.barIndex} (مستخدم ${(bar.usedLengthMm / 10f).roundToInt()} سم | فضلة ${(bar.remainingWasteMm / 10f).roundToInt()} سم)",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "استغلال ${"%.1f".format(bar.utilizationPercentage)}% | هدر ${"%.1f".format(bar.wastePercentage)}%",
                                                    color = Color(0xFF10B981),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(3.dp))
                                            LinearProgressIndicator(
                                                progress = { (bar.usedLengthMm / bar.standardBarLengthMm).coerceIn(0f, 1f) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(10.dp),
                                                color = Color(0xFF38BDF8),
                                                trackColor = Color(0xFF334155)
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = "القطع الفردية (${bar.cuts.size}): ${bar.cuts.joinToString(" ، ") { "${it.first} (${it.second.roundToInt()} مم)" }}",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section D: Detailed Melamine Panels Cut-List Breakdown
                    item {
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
                                        text = "4. كشف تفصيل وقص ألواح الميلامين (جوانب، قاع، سخات، رفوف، درف، ظهر):",
                                        color = Color(0xFF34D399),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "العرض الداخلي الصافي: ${customCabinet.internalWidthMm.roundToInt()} مم (${(customCabinet.internalWidthMm / 10f)} سم)",
                                        color = Color(0xFFFDE68A),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                melamineParts.forEach { part ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${part.partName} (${part.category.titleAr})",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (part.formulaNoteAr.isNotBlank()) {
                                                Text(
                                                    text = part.formulaNoteAr,
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                        Text(
                                            text = "العدد: ${part.quantity} × [${part.lengthMm.roundToInt()}×${part.widthMm.roundToInt()} مم | (${(part.lengthMm / 10f)}×${(part.widthMm / 10f)} سم)]",
                                            color = Color(0xFF34D399),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = onOpenAluminumBarNesting,
                            border = BorderStroke(1.dp, Color(0xFF10B981)),
                            modifier = Modifier.testTag("open_alu_bar_nesting_from_calc_btn")
                        ) {
                            Icon(Icons.Default.Straighten, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تعشيق قضبان الألمنيوم (1D Nesting)", color = Color(0xFF10B981), fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = onOpenFullNestingOptimizer,
                            border = BorderStroke(1.dp, Color(0xFF38BDF8))
                        ) {
                            Icon(Icons.Default.ContentCut, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مخطط تعشيق الألواح 2D", color = Color(0xFF38BDF8), fontSize = 11.sp)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) {
                            Text("إغلاق", color = Color(0xFF94A3B8))
                        }
                        Button(
                            onClick = {
                                onCreateOrUpdateCabinetIn3D(customCabinet)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            modifier = Modifier.testTag("apply_alu_calc_to_3d_btn")
                        ) {
                            Icon(Icons.Default.AddBox, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (selectedElement != null)
                                    "تحديث الخزانة المحددة بهذه المقاسات في 3D"
                                else
                                    "إدراج هذه الخزانة في التصميم 3D الآن",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcKpiItem(
    label: String,
    value: String,
    subValue: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color(0xFF94A3B8), fontSize = 10.sp)
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(subValue, color = Color(0xFFCBD5E1), fontSize = 9.sp)
    }
}

@Composable
private fun StepperMini(
    label: String,
    count: Int,
    onChange: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text("$label: $count", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            Icons.Default.Remove,
            contentDescription = null,
            tint = Color(0xFF94A3B8),
            modifier = Modifier
                .size(16.dp)
                .clickable { onChange(count - 1) }
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = Color(0xFF38BDF8),
            modifier = Modifier
                .size(16.dp)
                .clickable { onChange(count + 1) }
        )
    }
}
