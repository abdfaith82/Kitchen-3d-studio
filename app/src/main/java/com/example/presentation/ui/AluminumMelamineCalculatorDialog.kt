package com.example.presentation.ui

import androidx.compose.animation.AnimatedVisibility
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
import java.util.Locale
import kotlin.math.abs
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
    initialMaterialDimensionsConfig: StandardMaterialDimensionsConfig = StandardMaterialDimensionsConfig(),
    initialSavedSheetSizes: List<StandardMelamineSheetSize> = StandardMelamineSheetSize.DEFAULT_SIZES,
    onUpdateMaterialDimensionsConfig: (StandardMaterialDimensionsConfig) -> Unit = {},
    onSaveCustomSheetSize: (StandardMelamineSheetSize) -> Unit = {},
    onCreateOrUpdateCabinetIn3D: (ParametricCabinet) -> Unit,
    onOpenFullNestingOptimizer: () -> Unit,
    onOpenAluminumBarNesting: () -> Unit = {},
    onDismiss: () -> Unit
) {
    // Mode: 0 = مقاسات مخصصة أدخلها بنفسي (Custom Input), 1 = حساب شامل لكل خزائن المطبخ 3D الحالي
    var calcScopeMode by remember { mutableIntStateOf(0) }

    // Input unit toggle: true = سم (cm), false = مم (mm)
    var useCentimeters by remember { mutableStateOf(true) }
    val unitMultiplier = if (useCentimeters) 10f else 1f
    val unitLabel = if (useCentimeters) "سم" else "مم"

    // Toggle for Standard Material Dimensions Control Panel (خيار التحكم بالأبعاد القياسية للمواد)
    var isStandardMaterialControlExpanded by remember { mutableStateOf(true) }

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

    // =========================================================================
    // Standard Material Dimensions State (الأبعاد القياسية لقضبان الألمنيوم وألواح الميلامين)
    // =========================================================================
    var selectedSystem by remember { mutableStateOf(AluminumProfileSystem.FULL_ALUMINUM_MELAMINE) }

    // 1. Standard Aluminum Bar Stock Dimensions
    var availableBarLengthsMm by remember {
        mutableStateOf(
            listOf(6000f, 5800f, 6500f, 4500f, 3000f, initialMaterialDimensionsConfig.aluminumStandardBarLengthMm)
                .distinct()
                .sortedDescending()
        )
    }
    var standardBarLengthMm by remember {
        mutableFloatStateOf(initialMaterialDimensionsConfig.aluminumStandardBarLengthMm.coerceIn(500f, 12000f))
    }
    var customBarLengthText by remember(useCentimeters) {
        val v = if (useCentimeters) (standardBarLengthMm / 10f).roundToInt() else standardBarLengthMm.roundToInt()
        mutableStateOf(v.toString())
    }
    var aluminumSectionSizeText by remember {
        mutableStateOf(initialMaterialDimensionsConfig.aluminumProfileSectionMm.roundToInt().toString())
    }
    var aluminumThicknessText by remember {
        mutableStateOf(initialMaterialDimensionsConfig.aluminumWallThicknessMm.toString())
    }
    var aluminumWastePctText by remember { mutableStateOf("8") }
    var aluminumSawKerfText by remember {
        mutableStateOf(initialMaterialDimensionsConfig.aluminumSawKerfMm.toString())
    }
    var aluminumBarEndTrimText by remember {
        mutableStateOf(initialMaterialDimensionsConfig.aluminumBarEndTrimMm.roundToInt().toString())
    }

    // 2. Standard Melamine / MDF Board Stock Dimensions
    var savedSheetSizes by remember { mutableStateOf(initialSavedSheetSizes) }
    var sheetLengthMm by remember {
        mutableFloatStateOf(initialMaterialDimensionsConfig.melamineSheetLengthMm.coerceIn(600f, 5000f))
    }
    var sheetWidthMm by remember {
        mutableFloatStateOf(initialMaterialDimensionsConfig.melamineSheetWidthMm.coerceIn(500f, 3500f))
    }
    var sheetLengthInputText by remember(useCentimeters) {
        val v = if (useCentimeters) (sheetLengthMm / 10f).roundToInt() else sheetLengthMm.roundToInt()
        mutableStateOf(v.toString())
    }
    var sheetWidthInputText by remember(useCentimeters) {
        val v = if (useCentimeters) (sheetWidthMm / 10f).roundToInt() else sheetWidthMm.roundToInt()
        mutableStateOf(v.toString())
    }
    var melamineThicknessMm by remember {
        mutableFloatStateOf(initialMaterialDimensionsConfig.melamineBoardThicknessMm.coerceIn(8f, 36f))
    }
    var melamineThicknessInputText by remember {
        mutableStateOf(melamineThicknessMm.roundToInt().toString())
    }
    var backPanelThicknessMm by remember {
        mutableFloatStateOf(initialMaterialDimensionsConfig.backPanelThicknessMm.coerceIn(3f, 18f))
    }
    var sheetEdgeTrimText by remember {
        mutableStateOf(initialMaterialDimensionsConfig.sheetEdgeTrimMm.roundToInt().toString())
    }
    var woodSawKerfText by remember {
        mutableStateOf(initialMaterialDimensionsConfig.woodSawKerfMm.toString())
    }

    var topStretcherWidthText by remember { mutableStateOf("100") }
    var hasBackPanel by remember { mutableStateOf(true) }
    var backGrooveInsetText by remember { mutableStateOf("10") }
    var statusConfirmationBanner by remember { mutableStateOf<String?>(null) }

    val widthMm = ((widthInputText.toFloatOrNull() ?: if (useCentimeters) 80f else 800f) * unitMultiplier).coerceIn(250f, 3000f)
    val heightMm = ((heightInputText.toFloatOrNull() ?: if (useCentimeters) 85f else 850f) * unitMultiplier).coerceIn(300f, 2600f)
    val depthMm = ((depthInputText.toFloatOrNull() ?: if (useCentimeters) 60f else 600f) * unitMultiplier).coerceIn(250f, 1200f)
    val toeKickMm = ((toeKickInputText.toFloatOrNull() ?: 0f) * unitMultiplier).coerceIn(0f, 250f)

    val aluSectionMm = (aluminumSectionSizeText.toFloatOrNull() ?: selectedSystem.sectionMm).coerceIn(15f, 120f)
    val aluThickMm = (aluminumThicknessText.toFloatOrNull() ?: selectedSystem.wallThickMm).coerceIn(0.8f, 5f)
    val aluWastePct = (aluminumWastePctText.toFloatOrNull() ?: 8f).coerceIn(0f, 35f)
    val aluSawKerfMm = (aluminumSawKerfText.toFloatOrNull() ?: 4.0f).coerceIn(0f, 15f)
    val aluEndTrimMm = (aluminumBarEndTrimText.toFloatOrNull() ?: 10f).coerceIn(0f, 100f)
    val effectiveBarLengthMm = (standardBarLengthMm - aluEndTrimMm * 2f).coerceAtLeast(500f)

    val sheetTrimMm = (sheetEdgeTrimText.toFloatOrNull() ?: 10f).coerceIn(0f, 50f)
    val woodSawKerfMm = (woodSawKerfText.toFloatOrNull() ?: 3.2f).coerceIn(0f, 12f)
    val stretcherWidthMm = (topStretcherWidthText.toFloatOrNull() ?: 100f).coerceIn(50f, 250f)
    val grooveInsetMm = if (hasBackPanel) (backGrooveInsetText.toFloatOrNull() ?: 10f).coerceIn(0f, 25f) else 0f

    // Sync standard material dimensions back to parent when changed
    LaunchedEffect(
        sheetLengthMm, sheetWidthMm, melamineThicknessMm, backPanelThicknessMm,
        sheetTrimMm, woodSawKerfMm, standardBarLengthMm, aluSectionMm, aluThickMm, aluSawKerfMm, aluEndTrimMm
    ) {
        onUpdateMaterialDimensionsConfig(
            StandardMaterialDimensionsConfig(
                melamineSheetLengthMm = sheetLengthMm,
                melamineSheetWidthMm = sheetWidthMm,
                melamineBoardThicknessMm = melamineThicknessMm,
                backPanelThicknessMm = backPanelThicknessMm,
                sheetEdgeTrimMm = sheetTrimMm,
                woodSawKerfMm = woodSawKerfMm,
                aluminumStandardBarLengthMm = standardBarLengthMm,
                aluminumProfileSectionMm = aluSectionMm,
                aluminumWallThicknessMm = aluThickMm,
                aluminumSawKerfMm = aluSawKerfMm,
                aluminumBarEndTrimMm = aluEndTrimMm
            )
        )
    }

    val customCabinet = remember(
        widthMm, heightMm, depthMm, toeKickMm, shelvesCount, drawersCount,
        selectedSystem, aluSectionMm, aluThickMm, aluWastePct,
        melamineThicknessMm, backPanelThicknessMm, stretcherWidthMm, hasBackPanel, grooveInsetMm, selectedElement?.id
    ) {
        ParametricCabinet(
            id = selectedElement?.id ?: "alu_custom_cab",
            name = selectedElement?.name ?: "وحدة ألمنيوم وميلامين (${(widthMm / 10f).roundToInt()}×${(heightMm / 10f).roundToInt()} سم)",
            widthMm = widthMm,
            heightMm = heightMm,
            depthMm = depthMm,
            toeKickHeightMm = toeKickMm,
            panelThicknessMm = melamineThicknessMm,
            backPanelThicknessMm = if (hasBackPanel) backPanelThicknessMm else 0f,
            numberOfShelves = shelvesCount,
            numberOfDrawers = drawersCount,
            doorStyle = if (selectedSystem.includeDoorFrameProfile) DoorStyle.ALUMINUM_GLASS else DoorStyle.SLAB_MODERN,
            handleStyle = if (selectedSystem.includeGolaProfile) HandleStyle.J_PULL_GOLA else HandleStyle.BAR_PULL,
            carcassMaterial = if (melamineThicknessMm <= 16.5f) PanelMaterial.MDF_16MM else PanelMaterial.MDF_18MM,
            facadeMaterial = selectedElement?.material ?: MaterialFinish.NATURAL_OAK,
            hasBackPanel = hasBackPanel,
            backPanelMaterialName = if (hasBackPanel) "MDF ميلامين ${backPanelThicknessMm.roundToInt()} مم" else "بدون ظهر",
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
                    backPanelThicknessMm = if (hasBackPanel) backPanelThicknessMm else 0f,
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

    val packedBars = remember(aluSummary.cuts, effectiveBarLengthMm, aluSawKerfMm) {
        NestingEngine.packAluminumProfiles(
            cuts = aluSummary.cuts,
            barLengthMm = effectiveBarLengthMm,
            kerfMm = aluSawKerfMm
        )
    }

    val melamineParts = remember(targetCabinetsList) {
        targetCabinetsList.flatMap { it.generateCarcassCutParts() }
    }

    // Real-time 2D Guillotine Sheet Nesting using the controlled Standard Melamine Sheet Dimensions
    val packedMelamineSheets = remember(melamineParts, sheetLengthMm, sheetWidthMm, woodSawKerfMm, sheetTrimMm) {
        NestingEngine.packPanels(
            parts = melamineParts,
            sheetWidthMm = sheetLengthMm,
            sheetLengthMm = sheetWidthMm,
            bladeKerfMm = woodSawKerfMm,
            sheetTrimMm = sheetTrimMm
        )
    }

    val totalMelamineAreaSqM = remember(melamineParts) {
        melamineParts.sumOf { it.areaSqM.toDouble() }.toFloat()
    }

    val singleSheetAreaSqM = (sheetLengthMm * sheetWidthMm) / 1_000_000f
    val areaBasedSheets = remember(totalMelamineAreaSqM, singleSheetAreaSqM) {
        if (totalMelamineAreaSqM <= 0f) 0 else ceil((totalMelamineAreaSqM * 1.12f) / singleSheetAreaSqM.coerceAtLeast(0.1f)).toInt()
    }
    val estimatedMelamineSheets = maxOf(areaBasedSheets, packedMelamineSheets.size)

    val averageSheetUtilizationPct = remember(packedMelamineSheets) {
        if (packedMelamineSheets.isEmpty()) 0f
        else packedMelamineSheets.map { it.utilizationPercentage }.average().toFloat()
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
                            text = "تحكم بالأبعاد القياسية للمواد الخام (القضبان والألواح) واحسب كشوفات القص والهدر بدقة هندسية",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = isStandardMaterialControlExpanded,
                            onClick = { isStandardMaterialControlExpanded = !isStandardMaterialControlExpanded },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = if (isStandardMaterialControlExpanded) Color.White else Color(0xFFFBBF24),
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            label = {
                                Text(
                                    "الأبعاد القياسية للمواد (${(sheetLengthMm / 10f).roundToInt()}×${(sheetWidthMm / 10f).roundToInt()}سم | ${String.format(Locale.US, "%.1f", standardBarLengthMm / 1000f)}م)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFD97706),
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("toggle_standard_material_dims_btn")
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

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
                            label = { Text("سنتيمتر (سم)", fontSize = 10.sp) },
                            modifier = Modifier.testTag("calc_unit_cm_chip")
                        )
                        FilterChip(
                            selected = !useCentimeters,
                            onClick = { useCentimeters = false },
                            label = { Text("مليمتر (مم)", fontSize = 10.sp) },
                            modifier = Modifier.testTag("calc_unit_mm_chip")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Top Live Summary KPI Strip (dynamically reflects controlled Standard Material Dimensions)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    CalcKpiItem(
                        label = "قضبان الألمنيوم القياسية المطلوبة",
                        value = "${maxOf(aluSummary.requiredStandardBarsCount, packedBars.size)} قضيب (${String.format(Locale.US, "%.2f", standardBarLengthMm / 1000f)}م)",
                        subValue = "صافي: ${"%.2f".format(aluSummary.netLinearMeters)} م.ط | مع الهدر: ${"%.2f".format(aluSummary.grossLinearMetersWithWaste)} م.ط",
                        color = Color(0xFF38BDF8)
                    )
                    CalcKpiItem(
                        label = "تفصيل القضبان (أفقي / عمودي)",
                        value = "أفقي: ${"%.2f".format(aluSummary.horizontalTotalMeters)} م | عمودي: ${"%.2f".format(aluSummary.verticalTotalMeters)} م",
                        subValue = "إجمالي القطع المقصوصة: ${aluSummary.cuts.sumOf { it.quantity }} قطعة (مقطع ${aluSectionMm.roundToInt()}مم)",
                        color = Color(0xFFF59E0B)
                    )
                    CalcKpiItem(
                        label = "ألواح الميلامين القياسية المطلوبة",
                        value = "$estimatedMelamineSheets لوح (${(sheetLengthMm / 10f).roundToInt()}×${(sheetWidthMm / 10f).roundToInt()} سم × ${melamineThicknessMm.roundToInt()}مم)",
                        subValue = "مساحة اللوح: ${"%.2f".format(singleSheetAreaSqM)} م² | استغلال: ${"%.1f".format(averageSheetUtilizationPct)}% | شريط: ${"%.1f".format(totalEdgeBandingMeters)}م",
                        color = Color(0xFF10B981)
                    )
                }

                statusConfirmationBanner?.let { msg ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = Color(0xFF064E3B),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(msg, color = Color(0xFFA7F3D0), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                tint = Color(0xFFA7F3D0),
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable { statusConfirmationBanner = null }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // =========================================================================
                    // NEW SECTION: Standard Material Dimensions Control Card
                    // (خيار التحكم بالأبعاد القياسية للمواد: قضبان الألمنيوم + ألواح الميلامين)
                    // =========================================================================
                    item {
                        AnimatedVisibility(visible = isStandardMaterialControlExpanded) {
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.5.dp, Color(0xFFF59E0B)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("standard_material_dimensions_control_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Straighten,
                                                contentDescription = null,
                                                tint = Color(0xFFFBBF24),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = "التحكم بالأبعاد القياسية للمواد الخام (قضبان الألمنيوم وألواح الميلامين)",
                                                    color = Color(0xFFFBBF24),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = "حدد المقاسات القياسية المتوفرة في ورشتك أو المصنع ليتم تحديث عدد الألواح والقضبان ونسب الهدر تلقائياً",
                                                    color = Color(0xFFCBD5E1),
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                val newSize = StandardMelamineSheetSize(
                                                    id = "custom_${sheetLengthMm.roundToInt()}_${sheetWidthMm.roundToInt()}",
                                                    titleAr = "مخصص ${(sheetLengthMm / 10f).roundToInt()}×${(sheetWidthMm / 10f).roundToInt()} سم",
                                                    lengthMm = sheetLengthMm,
                                                    widthMm = sheetWidthMm
                                                )
                                                if (savedSheetSizes.none { abs(it.lengthMm - sheetLengthMm) < 2f && abs(it.widthMm - sheetWidthMm) < 2f }) {
                                                    savedSheetSizes = savedSheetSizes + newSize
                                                    onSaveCustomSheetSize(newSize)
                                                }
                                                if (availableBarLengthsMm.none { abs(it - standardBarLengthMm) < 2f }) {
                                                    availableBarLengthsMm = (availableBarLengthsMm + standardBarLengthMm).sortedDescending()
                                                }
                                                statusConfirmationBanner = "تم اعتماد الأبعاد القياسية: لوح ميلامين ${(sheetLengthMm / 10f).roundToInt()}×${(sheetWidthMm / 10f).roundToInt()} سم (${melamineThicknessMm.roundToInt()}مم) | قضيب ألمنيوم ${String.format(Locale.US, "%.2f", standardBarLengthMm / 1000f)} متر"
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier.testTag("apply_standard_material_dims_btn")
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("اعتماد وحفظ الأبعاد القياسية", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    HorizontalDivider(color = Color(0xFF334155))

                                    // Part 1: Melamine / MDF Standard Board Dimensions
                                    Text(
                                        text = "أولاً: الأبعاد القياسية لألواح الميلامين والـ MDF (الطول × العرض × السماكة):",
                                        color = Color(0xFF34D399),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )

                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(savedSheetSizes, key = { it.id }) { preset ->
                                            val isSelected = abs(sheetLengthMm - preset.lengthMm) < 2f &&
                                                abs(sheetWidthMm - preset.widthMm) < 2f
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    sheetLengthMm = preset.lengthMm
                                                    sheetWidthMm = preset.widthMm
                                                    sheetLengthInputText = if (useCentimeters) {
                                                        (preset.lengthMm / 10f).roundToInt().toString()
                                                    } else {
                                                        preset.lengthMm.roundToInt().toString()
                                                    }
                                                    sheetWidthInputText = if (useCentimeters) {
                                                        (preset.widthMm / 10f).roundToInt().toString()
                                                    } else {
                                                        preset.widthMm.roundToInt().toString()
                                                    }
                                                },
                                                label = { Text(preset.titleAr, fontSize = 10.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = Color(0xFF059669),
                                                    selectedLabelColor = Color.White
                                                ),
                                                modifier = Modifier.testTag("preset_sheet_size_${preset.id}")
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = sheetLengthInputText,
                                            onValueChange = { txt ->
                                                sheetLengthInputText = txt
                                                txt.toFloatOrNull()?.let { parsed ->
                                                    sheetLengthMm = (parsed * unitMultiplier).coerceIn(600f, 5000f)
                                                }
                                            },
                                            label = { Text("طول اللوح القياسي ($unitLabel)", fontSize = 9.5.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("std_sheet_length_input")
                                        )
                                        OutlinedTextField(
                                            value = sheetWidthInputText,
                                            onValueChange = { txt ->
                                                sheetWidthInputText = txt
                                                txt.toFloatOrNull()?.let { parsed ->
                                                    sheetWidthMm = (parsed * unitMultiplier).coerceIn(500f, 3500f)
                                                }
                                            },
                                            label = { Text("عرض اللوح القياسي ($unitLabel)", fontSize = 9.5.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("std_sheet_width_input")
                                        )
                                        OutlinedTextField(
                                            value = melamineThicknessInputText,
                                            onValueChange = { txt ->
                                                melamineThicknessInputText = txt
                                                txt.toFloatOrNull()?.let { parsed ->
                                                    melamineThicknessMm = parsed.coerceIn(8f, 36f)
                                                }
                                            },
                                            label = { Text("سمك الميلامين (مم)", fontSize = 9.5.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(0.85f)
                                                .testTag("std_melamine_thickness_input")
                                        )
                                        OutlinedTextField(
                                            value = sheetEdgeTrimText,
                                            onValueChange = { sheetEdgeTrimText = it },
                                            label = { Text("تشذيب الحافة (مم)", fontSize = 9.5.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(0.8f)
                                                .testTag("std_sheet_trim_input")
                                        )
                                        OutlinedTextField(
                                            value = woodSawKerfText,
                                            onValueChange = { woodSawKerfText = it },
                                            label = { Text("شفرة المنشار (مم)", fontSize = 9.5.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(0.8f)
                                                .testTag("std_wood_kerf_input")
                                        )
                                    }

                                    // Quick Board Thickness Chips + Back Panel Thickness Chips
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("سماكات الميلامين القياسية:", color = Color(0xFFCBD5E1), fontSize = 10.sp)
                                            listOf(16f, 18f, 22f, 25f).forEach { thick ->
                                                FilterChip(
                                                    selected = abs(melamineThicknessMm - thick) < 0.2f,
                                                    onClick = {
                                                        melamineThicknessMm = thick
                                                        melamineThicknessInputText = thick.roundToInt().toString()
                                                    },
                                                    label = { Text("${thick.roundToInt()} مم", fontSize = 9.5.sp) }
                                                )
                                            }
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("ظهر الخزانة:", color = Color(0xFFCBD5E1), fontSize = 10.sp)
                                            Switch(
                                                checked = hasBackPanel,
                                                onCheckedChange = { hasBackPanel = it },
                                                modifier = Modifier.height(22.dp)
                                            )
                                            if (hasBackPanel) {
                                                listOf(6f, 8f, 18f).forEach { bThick ->
                                                    FilterChip(
                                                        selected = abs(backPanelThicknessMm - bThick) < 0.2f,
                                                        onClick = { backPanelThicknessMm = bThick },
                                                        label = { Text("ظهر ${bThick.roundToInt()}مم", fontSize = 9.sp) }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = Color(0xFF334155))

                                    // Part 2: Standard Aluminum Profile Bar Stock Dimensions
                                    Text(
                                        text = "ثانياً: الأبعاد القياسية لقضبان وبروفايلات الألمنيوم (طول القضيب × المقطع × السماكة):",
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        LazyRow(
                                            modifier = Modifier.weight(1.6f),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            items(availableBarLengthsMm) { barLen ->
                                                val isSel = abs(standardBarLengthMm - barLen) < 2f
                                                FilterChip(
                                                    selected = isSel,
                                                    onClick = {
                                                        standardBarLengthMm = barLen
                                                        customBarLengthText = if (useCentimeters) {
                                                            (barLen / 10f).roundToInt().toString()
                                                        } else {
                                                            barLen.roundToInt().toString()
                                                        }
                                                    },
                                                    label = {
                                                        Text(
                                                            "${String.format(Locale.US, "%.2f", barLen / 1000f)} م (${barLen.roundToInt()}مم)",
                                                            fontSize = 10.sp
                                                        )
                                                    },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = Color(0xFF0284C7),
                                                        selectedLabelColor = Color.White
                                                    ),
                                                    modifier = Modifier.testTag("std_alu_bar_chip_${barLen.roundToInt()}")
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        OutlinedTextField(
                                            value = customBarLengthText,
                                            onValueChange = { txt ->
                                                customBarLengthText = txt
                                                txt.toFloatOrNull()?.let { parsed ->
                                                    standardBarLengthMm = (parsed * unitMultiplier).coerceIn(500f, 12000f)
                                                }
                                            },
                                            label = { Text("طول القضيب القياسي ($unitLabel)", fontSize = 9.5.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(0.9f)
                                                .testTag("calc_custom_bar_length_input")
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = aluminumSectionSizeText,
                                            onValueChange = { aluminumSectionSizeText = it },
                                            label = { Text("مقطع القضيب (مم)", fontSize = 9.5.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("std_alu_section_input")
                                        )
                                        OutlinedTextField(
                                            value = aluminumThicknessText,
                                            onValueChange = { aluminumThicknessText = it },
                                            label = { Text("سماكة الألمنيوم (مم)", fontSize = 9.5.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("std_alu_thickness_input")
                                        )
                                        OutlinedTextField(
                                            value = aluminumSawKerfText,
                                            onValueChange = { aluminumSawKerfText = it },
                                            label = { Text("شفرة منشار الألمنيوم (مم)", fontSize = 9.5.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("std_alu_kerf_input")
                                        )
                                        OutlinedTextField(
                                            value = aluminumBarEndTrimText,
                                            onValueChange = { aluminumBarEndTrimText = it },
                                            label = { Text("تهذيب طرف القضيب (مم)", fontSize = 9.5.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("std_alu_trim_input")
                                        )
                                        OutlinedTextField(
                                            value = aluminumWastePctText,
                                            onValueChange = { aluminumWastePctText = it },
                                            label = { Text("هامش الهدر %", fontSize = 9.5.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(0.85f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section A: Direct Cabinet Dimension Input Card (إدخال مقاسات الخزانة المباشر)
                    if (calcScopeMode == 0) {
                        item {
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF0284C7)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
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
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("alu_calc_width_input")
                                        )
                                        OutlinedTextField(
                                            value = heightInputText,
                                            onValueChange = { heightInputText = it },
                                            label = { Text("الارتفاع ($unitLabel)", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("alu_calc_height_input")
                                        )
                                        OutlinedTextField(
                                            value = depthInputText,
                                            onValueChange = { depthInputText = it },
                                            label = { Text("العمق ($unitLabel)", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("alu_calc_depth_input")
                                        )
                                        OutlinedTextField(
                                            value = toeKickInputText,
                                            onValueChange = { toeKickInputText = it },
                                            label = { Text("الأرجل/الوزرة ($unitLabel)", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = topStretcherWidthText,
                                            onValueChange = { topStretcherWidthText = it },
                                            label = { Text("سخة الميلامين (مم)", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(0.9f)
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
                                                    widthInputText = if (useCentimeters) "95" else "950"
                                                    heightInputText = if (useCentimeters) "85" else "850"
                                                    depthInputText = if (useCentimeters) "95" else "950"
                                                },
                                                label = { Text("كورنر زاوية 95×95", fontSize = 10.sp) }
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

                    // Section B: Aluminum Profile Structural System Selector
                    item {
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "2. نظام تركيب بروفايلات الألمنيوم والخزائن:",
                                    color = Color(0xFFF59E0B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(AluminumProfileSystem.entries) { sys ->
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
                                        text = "إجمالي القضبان القياسية: ${maxOf(aluSummary.requiredStandardBarsCount, packedBars.size)} قضيب × ${String.format(Locale.US, "%.2f", standardBarLengthMm / 1000f)}م (صافي القص ${(effectiveBarLengthMm / 10f).roundToInt()}سم)",
                                        color = Color(0xFF34D399),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
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
                                        text = "توزيع القص على القضبان القياسية (${(standardBarLengthMm / 10f).roundToInt()} سم - منشار ${aluSawKerfMm}مم):",
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

                    // Section D: Detailed Melamine Panels Cut-List & Standard Sheet Nesting Breakdown
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
                                        text = "4. كشف تفصيل وقص ألواح الميلامين القياسية (${(sheetLengthMm / 10f).roundToInt()}×${(sheetWidthMm / 10f).roundToInt()} سم):",
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

                                // Summary per Standard Melamine Sheet
                                if (packedMelamineSheets.isNotEmpty()) {
                                    Text(
                                        text = "توزيع الألواح القياسية (${packedMelamineSheets.size} لوح × ${(sheetLengthMm / 10f).roundToInt()}×${(sheetWidthMm / 10f).roundToInt()} سم | تشذيب ${sheetTrimMm.roundToInt()}مم | منشار ${woodSawKerfMm}مم):",
                                        color = Color(0xFFFDE68A),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    packedMelamineSheets.forEach { sheet ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp)
                                                .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 10.dp, vertical = 5.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "لوح #${sheet.sheetIndex} (${sheet.material.displayName}) — ${sheet.placements.size} قطعة",
                                                color = Color.White,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "استغلال: ${"%.1f".format(sheet.utilizationPercentage)}% | هدر: ${"%.2f".format(sheet.wasteAreaSqM)} م² (${"%.1f".format(sheet.wastePercentage)}%)",
                                                color = Color(0xFF34D399),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                }

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
