package com.example.presentation.ui

import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
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
import com.example.core.geometry.AutomaticFillerEngine
import com.example.core.geometry.AutomaticFillerEngine.FillerDistributionStrategy
import com.example.core.geometry.AutomaticFillerEngine.FillerRequestConfig
import com.example.core.geometry.AutomaticFillerEngine.FillerTargetTier
import com.example.domain.model.MaterialFinish
import com.example.domain.model.WallSide
import com.example.presentation.viewmodel.KitchenEditorUiState
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Interactive CAD Dialog for the Automatic Cabinet Filler & Spacer Tool (أداة الفيلر والفواصل التلقائية بدقة 1 مم).
 * Given a user-defined wall length, computes and previews customizable filler strips/spacers using strict 1mm snapping.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CabinetFillerToolDialog(
    state: KitchenEditorUiState,
    onApplyFillerConfig: (FillerRequestConfig) -> Unit,
    onRemoveWallFillers: (WallSide) -> Unit,
    onDismiss: () -> Unit
) {
    val room = state.project.room

    var selectedWall by remember { mutableStateOf(state.selectedWallSide) }
    val defaultWallLenMm = remember(selectedWall, room) {
        when (selectedWall) {
            WallSide.NORTH, WallSide.SOUTH -> room.widthMm
            WallSide.WEST, WallSide.EAST -> room.depthMm
        }
    }

    var wallLengthTextMm by remember(selectedWall) {
        mutableStateOf(defaultWallLenMm.roundToInt().toString())
    }
    var selectedTier by remember { mutableStateOf(FillerTargetTier.BASE_ROW) }
    var selectedStrategy by remember { mutableStateOf(FillerDistributionStrategy.SYMMETRIC_BOTH_ENDS) }
    var minFillerWidthTextMm by remember { mutableStateOf("10") }
    var maxSingleStripTextMm by remember { mutableStateOf("600") }
    var splitWideGaps by remember { mutableStateOf(false) }
    var flushWithCabinetFront by remember { mutableStateOf(true) }
    var recessOffsetMm by remember { mutableFloatStateOf(20f) }
    var includeCountertopOnBase by remember { mutableStateOf(true) }
    var syncRoomWallLength by remember { mutableStateOf(false) }
    var selectedCustomMaterial by remember { mutableStateOf<MaterialFinish?>(null) }

    val parsedWallLengthMm = (wallLengthTextMm.toFloatOrNull() ?: defaultWallLenMm).coerceIn(400f, 15000f)
    val parsedMinWidthMm = (minFillerWidthTextMm.toFloatOrNull() ?: 10f).coerceIn(1f, 300f)
    val parsedMaxWidthMm = (maxSingleStripTextMm.toFloatOrNull() ?: 600f).coerceIn(50f, 2400f)

    val currentConfig = remember(
        selectedWall,
        parsedWallLengthMm,
        selectedTier,
        selectedStrategy,
        parsedMinWidthMm,
        parsedMaxWidthMm,
        splitWideGaps,
        flushWithCabinetFront,
        recessOffsetMm,
        includeCountertopOnBase,
        selectedCustomMaterial,
        syncRoomWallLength
    ) {
        FillerRequestConfig(
            wallSide = selectedWall,
            userWallLengthMm = parsedWallLengthMm,
            targetTier = selectedTier,
            strategy = selectedStrategy,
            minFillerWidthMm = parsedMinWidthMm,
            maxSingleFillerWidthMm = parsedMaxWidthMm,
            splitWideGapsIntoModularStrips = splitWideGaps,
            flushWithCabinetFront = flushWithCabinetFront,
            recessDepthOffsetMm = recessOffsetMm,
            includeCountertopOnBaseFiller = includeCountertopOnBase,
            customMaterial = selectedCustomMaterial,
            alsoUpdateRoomWallLength = syncRoomWallLength
        )
    }

    val livePreviewResult = remember(state.project.elements, room, currentConfig) {
        AutomaticFillerEngine.computeFillerLayout(
            elements = state.project.elements,
            room = room,
            config = currentConfig
        )
    }

    val primaryAnalysis = livePreviewResult.tierAnalyses.firstOrNull()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .testTag("cabinet_filler_tool_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0B1120),
            border = BorderStroke(1.5.dp, Color(0xFF334155)),
            tonalElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B))
                        ) {
                            Icon(
                                Icons.Default.ViewColumn,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.padding(8.dp).size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "أداة شرائح الفيلر والفواصل التلقائية (1 مم CAD)",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ملء الفراغات المتبقية لطول الجدار المحدد بشرائح فيلر أو فواصل مخصصة بدقة 1 مليمتر",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_filler_dialog_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8))
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 8.dp))

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Live 2D CAD Wall Strip Elevation Preview
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📐 معاينة توزيع الخزائن وشرائح الفيلر على ${selectedWall.title}",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "الطول المحدد: ${parsedWallLengthMm.roundToInt()} مم (${String.format(java.util.Locale.US, "%.1f", parsedWallLengthMm / 10f)} سم)",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Interactive 2D CAD Diagram Canvas
                            WallFillerCadPreviewCanvas(
                                analysis = primaryAnalysis,
                                userWallLengthMm = parsedWallLengthMm,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(148.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF020617))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                                    .testTag("wall_filler_cad_preview_canvas")
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Summary Metrics Strip
                            if (primaryAnalysis != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FillerMetricBadge(
                                        label = "طول الجدار المحدد",
                                        value = "${primaryAnalysis.userWallLengthMm.roundToInt()} مم",
                                        valueColor = Color.White
                                    )
                                    FillerMetricBadge(
                                        label = "إجمالي الخزائن (${primaryAnalysis.cabinetCount})",
                                        value = "${primaryAnalysis.totalCabinetsWidthMm.roundToInt()} مم",
                                        valueColor = Color(0xFF38BDF8)
                                    )
                                    FillerMetricBadge(
                                        label = "الفراغ المتبقي",
                                        value = "${primaryAnalysis.remainingGapMm.roundToInt()} مم",
                                        valueColor = if (primaryAnalysis.isOverfilled) Color(0xFFEF4444) else Color(0xFFFBBF24)
                                    )
                                    FillerMetricBadge(
                                        label = "شرائح الفيلر المولدة",
                                        value = "${livePreviewResult.addedFillers.size} (${livePreviewResult.totalGapFilledMm.roundToInt()} مم)",
                                        valueColor = Color(0xFF34D399)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = livePreviewResult.summaryAr,
                                    color = if (primaryAnalysis.isOverfilled) Color(0xFFFCA5A5) else Color(0xFF6EE7B7),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.testTag("filler_status_summary_text")
                                )
                            }
                        }
                    }

                    // 2. Wall Selection & User-Defined Wall Length Input (1mm Precision)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "1. اختيار الجدار وتحديد طول الجدار المطلوب (بالمليمتر 1mm):",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Wall Side Chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                WallSide.entries.forEach { side ->
                                    val isSel = selectedWall == side
                                    FilterChip(
                                        selected = isSel,
                                        onClick = {
                                            selectedWall = side
                                            val sideLen = when (side) {
                                                WallSide.NORTH, WallSide.SOUTH -> room.widthMm
                                                WallSide.WEST, WallSide.EAST -> room.depthMm
                                            }
                                            wallLengthTextMm = sideLen.roundToInt().toString()
                                        },
                                        label = { Text(side.title, fontSize = 11.sp) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.BorderOuter,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF0284C7),
                                            selectedLabelColor = Color.White
                                        ),
                                        modifier = Modifier.testTag("filler_wall_chip_${side.name.lowercase()}")
                                    )
                                }
                            }

                            // User-defined Wall Length Input + 1mm / 10mm / 100mm Nudge Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = wallLengthTextMm,
                                    onValueChange = { wallLengthTextMm = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                    label = { Text("طول الجدار المحدد (مم)", fontSize = 11.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF38BDF8),
                                        unfocusedBorderColor = Color(0xFF334155)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("filler_wall_length_input_mm")
                                )

                                // Quick 1mm / 10mm / 50mm step adjustments
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        SmallWallLenStepButton("-10 مم") {
                                            wallLengthTextMm = (parsedWallLengthMm - 10f).coerceAtLeast(400f).roundToInt().toString()
                                        }
                                        SmallWallLenStepButton("-1 مم") {
                                            wallLengthTextMm = (parsedWallLengthMm - 1f).coerceAtLeast(400f).roundToInt().toString()
                                        }
                                        SmallWallLenStepButton("+1 مم") {
                                            wallLengthTextMm = (parsedWallLengthMm + 1f).coerceAtMost(15000f).roundToInt().toString()
                                        }
                                        SmallWallLenStepButton("+10 مم") {
                                            wallLengthTextMm = (parsedWallLengthMm + 10f).coerceAtMost(15000f).roundToInt().toString()
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf(2400, 3000, 3600, 4000, 4500).forEach { presetMm ->
                                            SmallWallLenStepButton("${presetMm / 10}سم") {
                                                wallLengthTextMm = presetMm.toString()
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "تحديث طول جدار الغرفة أيضاً ليطابق الطول المدخل (${parsedWallLengthMm.roundToInt()} مم):",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp
                                )
                                Switch(
                                    checked = syncRoomWallLength,
                                    onCheckedChange = { syncRoomWallLength = it },
                                    modifier = Modifier.testTag("filler_sync_room_wall_switch")
                                )
                            }
                        }
                    }

                    // 3. Target Tier & Distribution Strategy
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "2. مستوى الخزائن وطريقة توزيع الفيلر / الفواصل:",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Tier Selector
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FillerTargetTier.entries.forEach { tier ->
                                    val isSel = selectedTier == tier
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { selectedTier = tier },
                                        label = { Text(tier.titleAr, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF0D9488),
                                            selectedLabelColor = Color.White
                                        ),
                                        modifier = Modifier.testTag("filler_tier_chip_${tier.name.lowercase()}")
                                    )
                                }
                            }

                            // Distribution Strategy Cards
                            FillerDistributionStrategy.entries.forEach { strategy ->
                                val isSel = selectedStrategy == strategy
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSel) Color(0xFF0284C7).copy(alpha = 0.22f) else Color(0xFF1E293B),
                                    border = BorderStroke(
                                        width = if (isSel) 1.5.dp else 1.dp,
                                        color = if (isSel) Color(0xFF38BDF8) else Color(0xFF334155)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { selectedStrategy = strategy }
                                        .testTag("filler_strategy_${strategy.name.lowercase()}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        RadioButton(
                                            selected = isSel,
                                            onClick = { selectedStrategy = strategy },
                                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF38BDF8))
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = strategy.titleAr,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = strategy.descriptionAr,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Customizable Filler Strip Geometry & Material Options
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "3. تخصيص أبعاد وخصائص شرائح الفيلر (Customizable Strip Specs):",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = minFillerWidthTextMm,
                                    onValueChange = { minFillerWidthTextMm = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                    label = { Text("أقل عرض للفيلر (مم)", fontSize = 10.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("filler_min_width_input")
                                )

                                OutlinedTextField(
                                    value = maxSingleStripTextMm,
                                    onValueChange = { maxSingleStripTextMm = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                    label = { Text("أقصى عرض للشريحة (مم)", fontSize = 10.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("filler_max_width_input")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "تقسيم الفراغات العريضة تلقائياً إلى عدة شرائح فيلر:",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp
                                )
                                Switch(
                                    checked = splitWideGaps,
                                    onCheckedChange = { splitWideGaps = it },
                                    modifier = Modifier.testTag("filler_split_wide_switch")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (flushWithCabinetFront) {
                                        "بروز الفيلر: مساوٍ لواجهة الدرف (Flush Front)"
                                    } else {
                                        "بروز الفيلر: غاطس مع البودي بمقدار ${recessOffsetMm.roundToInt()} مم (Recessed)"
                                    },
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp
                                )
                                Switch(
                                    checked = flushWithCabinetFront,
                                    onCheckedChange = { flushWithCabinetFront = it },
                                    modifier = Modifier.testTag("filler_flush_depth_switch")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "تمديد سطح الرخام (Countertop) فوق شرائح الفيلر السفلية:",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp
                                )
                                Switch(
                                    checked = includeCountertopOnBase,
                                    onCheckedChange = { includeCountertopOnBase = it },
                                    modifier = Modifier.testTag("filler_countertop_switch")
                                )
                            }

                            // Material finish selector
                            Text(
                                text = "لون وخامة شرائح الفيلر:",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = selectedCustomMaterial == null,
                                    onClick = { selectedCustomMaterial = null },
                                    label = { Text("مطابق للخزائن تلقائياً", fontSize = 10.sp) }
                                )
                                listOf(
                                    MaterialFinish.WHITE_HIGH_GLOSS,
                                    MaterialFinish.NATURAL_OAK,
                                    MaterialFinish.WALNUT_DARK,
                                    MaterialFinish.ANTHRACITE_BLACK,
                                    MaterialFinish.CHAMPAGNE_GLOSS,
                                    MaterialFinish.STAINLESS_STEEL
                                ).forEach { mat ->
                                    FilterChip(
                                        selected = selectedCustomMaterial == mat,
                                        onClick = { selectedCustomMaterial = mat },
                                        label = { Text(mat.name, fontSize = 10.sp) }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 8.dp))

                // Footer Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onRemoveWallFillers(selectedWall) },
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF87171)),
                        modifier = Modifier.testTag("remove_wall_fillers_btn")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إزالة الفيلر من الجدار", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(onClick = onDismiss) {
                        Text("إلغاء", color = Color(0xFF94A3B8))
                    }

                    Button(
                        onClick = {
                            onApplyFillerConfig(currentConfig)
                            onDismiss()
                        },
                        enabled = primaryAnalysis != null && !primaryAnalysis.isOverfilled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("apply_automatic_filler_btn")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تطبيق الفيلر التلقائي (1 مم)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallWallLenStepButton(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF1E293B),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = Color(0xFF38BDF8),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun FillerMetricBadge(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color(0xFF94A3B8), fontSize = 9.sp)
        Text(text = value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WallFillerCadPreviewCanvas(
    analysis: AutomaticFillerEngine.WallTierFillerAnalysis?,
    userWallLengthMm: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padH = 26f
        val barTop = 36f
        val barHeight = (h - 72f).coerceAtLeast(38f)
        val drawWidth = (w - padH * 2f).coerceAtLeast(10f)
        val safeWallMm = max(userWallLengthMm, 100f)
        val pxPerMm = drawWidth / safeWallMm

        val textPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 21f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val smallPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#38BDF8")
            textSize = 18f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        // Draw total wall ruler line at top
        drawLine(
            color = Color(0xFF475569),
            start = Offset(padH, 18f),
            end = Offset(padH + drawWidth, 18f),
            strokeWidth = 2f
        )
        drawContext.canvas.nativeCanvas.drawText(
            "0 مم",
            padH + 18f,
            15f,
            smallPaint
        )
        drawContext.canvas.nativeCanvas.drawText(
            "${userWallLengthMm.roundToInt()} مم",
            padH + drawWidth - 28f,
            15f,
            smallPaint
        )

        // Draw empty wall background track
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(padH, barTop),
            size = Size(drawWidth, barHeight),
            cornerRadius = CornerRadius(6f, 6f)
        )

        analysis?.segments?.forEach { seg ->
            val x0 = padH + seg.startOffsetMm * pxPerMm
            val segW = (seg.widthMm * pxPerMm).coerceAtLeast(4f)
            val fillColor = if (seg.isFillerStrip) {
                Color(0xFFF59E0B).copy(alpha = 0.85f)
            } else {
                Color(0xFF0284C7).copy(alpha = 0.72f)
            }
            val strokeColor = if (seg.isFillerStrip) Color(0xFFFDE68A) else Color(0xFF38BDF8)

            drawRoundRect(
                color = fillColor,
                topLeft = Offset(x0, barTop),
                size = Size(segW, barHeight),
                cornerRadius = CornerRadius(4f, 4f)
            )
            drawRoundRect(
                color = strokeColor,
                topLeft = Offset(x0, barTop),
                size = Size(segW, barHeight),
                cornerRadius = CornerRadius(4f, 4f),
                style = Stroke(width = if (seg.isFillerStrip) 2.5f else 1.5f)
            )

            val centerX = x0 + segW * 0.5f
            val widthLabel = "${seg.widthMm.roundToInt()} مم"
            if (segW >= 34f) {
                drawContext.canvas.nativeCanvas.drawText(
                    if (seg.isFillerStrip) "فيلر" else "خزانة",
                    centerX,
                    barTop + barHeight * 0.42f,
                    textPaint
                )
                drawContext.canvas.nativeCanvas.drawText(
                    widthLabel,
                    centerX,
                    barTop + barHeight * 0.76f,
                    textPaint
                )
            } else {
                // Draw callout below bar for narrow filler strips (e.g. 10mm - 45mm)
                drawLine(
                    color = Color(0xFFFBBF24),
                    start = Offset(centerX, barTop + barHeight),
                    end = Offset(centerX, barTop + barHeight + 14f),
                    strokeWidth = 2f
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "فيلر $widthLabel",
                    centerX.coerceIn(padH + 35f, padH + drawWidth - 35f),
                    barTop + barHeight + 28f,
                    smallPaint.apply { color = android.graphics.Color.parseColor("#FBBF24") }
                )
            }
        }
    }
}
