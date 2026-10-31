package com.example.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ElementCategory
import com.example.domain.model.KitchenElementType

import androidx.compose.material.icons.filled.DataObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogBottomSheet(
    onSelectType: (KitchenElementType) -> Unit,
    onDismiss: () -> Unit,
    onSelectForDragPlacement: ((KitchenElementType) -> Unit)? = null,
    onExportJson: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf<ElementCategory?>(null) } // null = All or selected
    var activeCategory by remember { mutableStateOf(ElementCategory.BASE_CABINET) }
    var searchQuery by remember { mutableStateOf("") }
    var showAllCategories by remember { mutableStateOf(false) }

    val allUnits = remember { KitchenElementType.values().toList() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // Header with total library count badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "المكتبة الشاملة للخزائن والوحدات",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = "${allUnits.size} وحدة قياسية",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "خزائن سفلية، علوية، تاج للسقف، فيترينة LED، أعمدة نيش وأجهزة",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (onExportJson != null) {
                        OutlinedButton(
                            onClick = onExportJson,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7)),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("catalog_export_json_btn")
                        ) {
                            Icon(Icons.Default.DataObject, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("استخراج JSON", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("ابحث عن خزانة، فيترينة، تاج، نيش، مقاس (مثلاً 90 سم)...", fontSize = 12.sp, color = Color(0xFF64748B)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "مسح", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedContainerColor = Color(0xFF1E293B),
                    unfocusedContainerColor = Color(0xFF1E293B),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("catalog_search_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Category Filter Pills
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = showAllCategories,
                        onClick = { showAllCategories = true },
                        label = { Text("الكل (${allUnits.size})", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0284C7),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFF94A3B8)
                        )
                    )
                }
                items(ElementCategory.values()) { cat ->
                    val count = allUnits.count { it.category == cat }
                    FilterChip(
                        selected = !showAllCategories && activeCategory == cat,
                        onClick = {
                            showAllCategories = false
                            activeCategory = cat
                        },
                        label = { Text("${cat.displayName} ($count)", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0284C7),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFF94A3B8)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filtered Items List
            val filteredItems = remember(activeCategory, showAllCategories, searchQuery) {
                allUnits.filter { unit ->
                    val matchesCategory = showAllCategories || searchQuery.isNotBlank() || unit.category == activeCategory
                    val matchesSearch = searchQuery.isBlank() ||
                            unit.title.contains(searchQuery, ignoreCase = true) ||
                            unit.code.contains(searchQuery, ignoreCase = true) ||
                            unit.defaultDimensions.x.toInt().toString().contains(searchQuery)
                    matchesCategory && matchesSearch
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
            ) {
                items(filteredItems, key = { it.code }) { item ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E293B),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectType(item)
                                onDismiss()
                            }
                            .testTag("catalog_item_${item.code}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Visual architectural preview thumbnail
                            CabinetPreviewThumbnail(
                                type = item,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0F172A))
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "الأبعاد: ${item.defaultDimensions.x.toInt()} × ${item.defaultDimensions.y.toInt()} × ${item.defaultDimensions.z.toInt()} مم",
                                    fontSize = 12.sp,
                                    color = Color(0xFF38BDF8)
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (item.defaultElevationY > 0f) {
                                        Text(
                                            text = "ارتفاع التثبيت: ${item.defaultElevationY.toInt()} مم",
                                            fontSize = 11.sp,
                                            color = Color(0xFFFBBF24)
                                        )
                                    } else {
                                        Text(
                                            text = "تثبيت أرضي + وزرة 10 سم",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (onSelectForDragPlacement != null) {
                                    OutlinedButton(
                                        onClick = {
                                            onSelectForDragPlacement(item)
                                            onDismiss()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                                        modifier = Modifier
                                            .height(34.dp)
                                            .testTag("catalog_drag_place_${item.code}")
                                    ) {
                                        Icon(
                                            Icons.Default.TouchApp,
                                            contentDescription = "سحب للمخطط",
                                            tint = Color(0xFFF59E0B),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "سحب للمكان",
                                            fontSize = 10.sp,
                                            color = Color(0xFFFBBF24),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                FilledIconButton(
                                    onClick = {
                                        onSelectType(item)
                                        onDismiss()
                                    },
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF0284C7))
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "إضافة")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * Public 3D Isometric + Architectural Elevation Thumbnail for any [KitchenElementType].
 * Renders realistic 3D top/side depth faces, marble/granite countertops, recessed toe-kicks,
 * doors, drawers, handles, sink & faucet, cooktop burners & hood, refrigerator splits,
 * oven windows, and glowing warm LED vitrines across all menus and drag-and-drop cards.
 */
@Composable
fun CabinetPreviewThumbnail(
    type: KitchenElementType,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val isoX = (w * 0.12f).coerceIn(3f, 8f)
        val isoY = (h * 0.10f).coerceIn(3f, 7f)

        // Aspect-aware bounding box inside the thumbnail
        val isTall = type.category == ElementCategory.TALL_UNIT || type == KitchenElementType.REFRIGERATOR_FROST
        val isWallOrBridge = type.category == ElementCategory.WALL_CABINET || type.category == ElementCategory.BRIDGE_CABINET
        val isWide = type.defaultDimensions.x >= 1100f

        val padX = if (isWide) w * 0.10f else if (isTall) w * 0.22f else w * 0.15f
        val topPad = if (isTall) h * 0.14f else if (isWallOrBridge) h * 0.18f else h * 0.22f
        val bottomPad = if (isWallOrBridge) h * 0.20f else h * 0.10f

        val frontLeft = padX
        val frontRight = (w - padX - isoX).coerceAtLeast(frontLeft + 12f)
        val frontTop = topPad + isoY
        val frontBottom = (h - bottomPad).coerceAtLeast(frontTop + 14f)
        val boxW = frontRight - frontLeft
        val boxH = frontBottom - frontTop

        val hasToeKick = type.defaultElevationY == 0f && type.category != ElementCategory.APPLIANCE
        val toeKickH = if (hasToeKick) (boxH * 0.11f).coerceIn(2.5f, 5.5f) else 0f
        val carcassBottom = frontBottom - toeKickH
        val carcassH = (carcassBottom - frontTop).coerceAtLeast(10f)

        val bodyColor = when {
            type == KitchenElementType.REFRIGERATOR_FROST ||
                type == KitchenElementType.REFRIGERATOR_FRENCH_DOOR ||
                type == KitchenElementType.DISHWASHER_INTEGRATED ||
                type == KitchenElementType.WASHING_MACHINE_FRONT -> Color(0xFF94A3B8)
            type == KitchenElementType.COOKTOP_INDUCTION ||
                type == KitchenElementType.COOKTOP_GAS_90 ||
                type == KitchenElementType.RANGE_HOOD_BOX -> Color(0xFF475569)
            type.category == ElementCategory.BRIDGE_CABINET -> Color(0xFF334155)
            type.category == ElementCategory.TALL_UNIT -> Color(0xFFB48A5A)
            type.category == ElementCategory.ISLAND_AND_SURFACE -> Color(0xFF1E293B)
            else -> Color(0xFFC8A165)
        }
        val topDepthColor = Color(
            red = (bodyColor.red * 1.16f).coerceIn(0f, 1f),
            green = (bodyColor.green * 1.16f).coerceIn(0f, 1f),
            blue = (bodyColor.blue * 1.16f).coerceIn(0f, 1f),
            alpha = 0.95f
        )
        val sideDepthColor = Color(
            red = (bodyColor.red * 0.72f).coerceIn(0f, 1f),
            green = (bodyColor.green * 0.72f).coerceIn(0f, 1f),
            blue = (bodyColor.blue * 0.72f).coerceIn(0f, 1f),
            alpha = 0.95f
        )

        // 1. Recessed Toe-Kick (وزرة الألمنيوم السفلية)
        if (hasToeKick) {
            drawRect(
                color = Color(0xFF090D16),
                topLeft = Offset(frontLeft + 2f, carcassBottom),
                size = Size((boxW - 4f).coerceAtLeast(4f), toeKickH)
            )
        }

        // 2. 3D Isometric Top Face
        val topFacePath = Path().apply {
            moveTo(frontLeft, frontTop)
            lineTo(frontLeft + isoX, frontTop - isoY)
            lineTo(frontRight + isoX, frontTop - isoY)
            lineTo(frontRight, frontTop)
            close()
        }
        val isCountertopUnit = type.category == ElementCategory.BASE_CABINET || type.category == ElementCategory.ISLAND_AND_SURFACE
        drawPath(
            path = topFacePath,
            color = if (isCountertopUnit) Color(0xFFE2E8F0) else topDepthColor
        )
        drawPath(
            path = topFacePath,
            color = Color(0xFF475569),
            style = Stroke(width = 1f)
        )

        // 3. 3D Isometric Right Side Depth Face
        val sideFacePath = Path().apply {
            moveTo(frontRight, frontTop)
            lineTo(frontRight + isoX, frontTop - isoY)
            lineTo(frontRight + isoX, carcassBottom - isoY * 0.6f)
            lineTo(frontRight, carcassBottom)
            close()
        }
        drawPath(path = sideFacePath, color = sideDepthColor)
        drawPath(path = sideFacePath, color = Color(0xFF334155), style = Stroke(width = 1f))

        // 4. Front Elevation Carcass Face
        drawRoundRect(
            color = bodyColor,
            topLeft = Offset(frontLeft, frontTop),
            size = Size(boxW, carcassH),
            cornerRadius = CornerRadius(2.5f, 2.5f)
        )
        drawRoundRect(
            color = Color(0xFFCBD5E1),
            topLeft = Offset(frontLeft, frontTop),
            size = Size(boxW, carcassH),
            cornerRadius = CornerRadius(2.5f, 2.5f),
            style = Stroke(width = 1.3f)
        )

        // Countertop slab lip for base cabinets & islands
        if (isCountertopUnit) {
            drawLine(
                color = Color(0xFFF8FAFC),
                start = Offset(frontLeft - 1.5f, frontTop + 1f),
                end = Offset(frontRight + 1.5f, frontTop + 1f),
                strokeWidth = 2.8f
            )
        }

        // 5. Type-Specific Architectural Details
        when {
            type == KitchenElementType.BASE_SINK_UNIT || type == KitchenElementType.BASE_SINK_VENTED_100 -> {
                // Stainless sink basin on top + arched chrome faucet + 2 doors
                val midX = frontLeft + boxW * 0.5f
                drawLine(Color(0xFF1E293B), Offset(midX, frontTop + 2f), Offset(midX, carcassBottom), strokeWidth = 1.4f)
                // Handles
                drawLine(Color(0xFFE2E8F0), Offset(midX - 4f, frontTop + carcassH * 0.25f), Offset(midX - 4f, frontTop + carcassH * 0.48f), strokeWidth = 1.6f)
                drawLine(Color(0xFFE2E8F0), Offset(midX + 4f, frontTop + carcassH * 0.25f), Offset(midX + 4f, frontTop + carcassH * 0.48f), strokeWidth = 1.6f)
                // Faucet arch above countertop
                val faucetX = midX + isoX * 0.4f
                val faucetBaseY = frontTop - isoY * 0.4f
                drawLine(Color(0xFF38BDF8), Offset(faucetX, faucetBaseY), Offset(faucetX, faucetBaseY - 7f), strokeWidth = 2f)
                drawLine(Color(0xFF38BDF8), Offset(faucetX, faucetBaseY - 7f), Offset(faucetX + 5f, faucetBaseY - 4f), strokeWidth = 1.8f)
            }
            type == KitchenElementType.COOKTOP_INDUCTION || type == KitchenElementType.COOKTOP_GAS_90 || type == KitchenElementType.RANGE_HOOD_BOX -> {
                // Range hood chimney + canopy + 4 glowing red/orange induction rings on top
                val midX = frontLeft + boxW * 0.5f
                drawRect(
                    color = Color(0xFF090D16),
                    topLeft = Offset(frontLeft + 3f, frontTop + carcassH * 0.22f),
                    size = Size((boxW - 6f).coerceAtLeast(4f), carcassH * 0.55f)
                )
                // Red burner indicators along top
                drawCircle(Color(0xFFEF4444), radius = 2.2f, center = Offset(frontLeft + boxW * 0.32f, frontTop - isoY * 0.45f))
                drawCircle(Color(0xFFEF4444), radius = 2.2f, center = Offset(frontLeft + boxW * 0.68f, frontTop - isoY * 0.45f))
                // Hood bar above
                drawLine(Color(0xFF94A3B8), Offset(midX, frontTop - isoY - 5f), Offset(midX, frontTop - isoY), strokeWidth = 3.5f)
            }
            type == KitchenElementType.REFRIGERATOR_FROST || type == KitchenElementType.REFRIGERATOR_FRENCH_DOOR -> {
                // Side-by-side double doors + water/ice dispenser pocket
                val splitX = frontLeft + boxW * 0.44f
                drawLine(Color(0xFF1E293B), Offset(splitX, frontTop), Offset(splitX, carcassBottom), strokeWidth = 1.6f)
                // Ice dispenser
                drawRect(
                    color = Color(0xFF0F172A),
                    topLeft = Offset(frontLeft + 3f, frontTop + carcassH * 0.26f),
                    size = Size((splitX - frontLeft - 6f).coerceAtLeast(3f), carcassH * 0.20f)
                )
                // Long vertical handles
                drawLine(Color(0xFFE2E8F0), Offset(splitX - 2.5f, frontTop + carcassH * 0.15f), Offset(splitX - 2.5f, frontTop + carcassH * 0.78f), strokeWidth = 1.5f)
                drawLine(Color(0xFFE2E8F0), Offset(splitX + 2.5f, frontTop + carcassH * 0.15f), Offset(splitX + 2.5f, frontTop + carcassH * 0.78f), strokeWidth = 1.5f)
            }
            type == KitchenElementType.DISHWASHER_INTEGRATED || type == KitchenElementType.WASHING_MACHINE_FRONT -> {
                // Top digital control strip + recessed handle
                val ctrlH = (carcassH * 0.18f).coerceAtLeast(3f)
                drawRect(
                    color = Color(0xFF0F172A),
                    topLeft = Offset(frontLeft + 2f, frontTop + 2f),
                    size = Size((boxW - 4f).coerceAtLeast(4f), ctrlH)
                )
                drawCircle(Color(0xFF38BDF8), radius = 1.6f, center = Offset(frontRight - 5f, frontTop + 2f + ctrlH * 0.5f))
                drawLine(
                    color = Color(0xFFE2E8F0),
                    start = Offset(frontLeft + boxW * 0.25f, frontTop + ctrlH + 4f),
                    end = Offset(frontLeft + boxW * 0.75f, frontTop + ctrlH + 4f),
                    strokeWidth = 1.8f
                )
            }
            type.code.contains("vitrine") || type.code.contains("glass") -> {
                // Dark smoked glass panel + glowing vertical warm LED strip + shelves
                drawRect(
                    color = Color(0xFF0F172A),
                    topLeft = Offset(frontLeft + 3f, frontTop + 3f),
                    size = Size((boxW - 6f).coerceAtLeast(4f), (carcassH - 6f).coerceAtLeast(4f))
                )
                drawLine(
                    color = Color(0xFFFDE047),
                    start = Offset(frontLeft + 5f, frontTop + 4f),
                    end = Offset(frontLeft + 5f, carcassBottom - 4f),
                    strokeWidth = 2.2f
                )
                drawLine(
                    color = Color(0xFFFDE047),
                    start = Offset(frontRight - 5f, frontTop + 4f),
                    end = Offset(frontRight - 5f, carcassBottom - 4f),
                    strokeWidth = 2.2f
                )
                for (i in 1..3) {
                    val sy = frontTop + (carcassH / 4f) * i
                    drawLine(Color(0x99FDE68A), Offset(frontLeft + 4f, sy), Offset(frontRight - 4f, sy), strokeWidth = 1.2f)
                }
            }
            type.code.contains("niche") || type.code.contains("peninsula_lit") -> {
                val count = if (type.code.contains("peninsula")) 2 else 3
                val step = carcassH / count
                for (i in 0 until count) {
                    val ny = frontTop + i * step + 2f
                    drawRect(
                        color = Color(0xFF1E293B),
                        topLeft = Offset(frontLeft + 3f, ny),
                        size = Size((boxW - 6f).coerceAtLeast(4f), (step - 4f).coerceAtLeast(2f))
                    )
                    drawLine(
                        color = Color(0xFFFDE047),
                        start = Offset(frontLeft + 5f, ny + 1.5f),
                        end = Offset(frontRight - 5f, ny + 1.5f),
                        strokeWidth = 1.8f
                    )
                }
            }
            type.code.contains("4_drawers") -> {
                val step = carcassH / 4f
                for (i in 1..3) {
                    drawLine(Color(0xFF1E293B), Offset(frontLeft, frontTop + step * i), Offset(frontRight, frontTop + step * i), strokeWidth = 1.4f)
                }
                for (i in 0..3) {
                    val cy = frontTop + step * (i + 0.5f)
                    drawLine(Color(0xFF0F172A), Offset(frontLeft + boxW * 0.32f, cy), Offset(frontLeft + boxW * 0.68f, cy), strokeWidth = 1.8f)
                }
            }
            type.code.contains("drawers") || type.category == ElementCategory.ISLAND_AND_SURFACE -> {
                val step = carcassH / 3f
                for (i in 1..2) {
                    drawLine(Color(0xFF1E293B), Offset(frontLeft, frontTop + step * i), Offset(frontRight, frontTop + step * i), strokeWidth = 1.4f)
                }
                for (i in 0..2) {
                    val cy = frontTop + step * (i + 0.5f)
                    drawLine(Color(0xFF0F172A), Offset(frontLeft + boxW * 0.32f, cy), Offset(frontLeft + boxW * 0.68f, cy), strokeWidth = 1.8f)
                }
            }
            type.code.contains("oven") -> {
                drawRect(
                    color = Color(0xFF090D16),
                    topLeft = Offset(frontLeft + 3f, frontTop + carcassH * 0.30f),
                    size = Size((boxW - 6f).coerceAtLeast(4f), carcassH * 0.42f)
                )
                drawLine(Color(0xFFE2E8F0), Offset(frontLeft + 5f, frontTop + carcassH * 0.35f), Offset(frontRight - 5f, frontTop + carcassH * 0.35f), strokeWidth = 1.8f)
                drawLine(Color(0xFFF59E0B), Offset(frontLeft + 6f, frontTop + carcassH * 0.52f), Offset(frontRight - 6f, frontTop + carcassH * 0.52f), strokeWidth = 1.4f)
            }
            type.code.contains("corner") -> {
                // L-Corner vertical crease + two angled doors
                val midX = frontLeft + boxW * 0.5f
                drawLine(Color(0xFF0F172A), Offset(midX, frontTop), Offset(midX, carcassBottom), strokeWidth = 2f)
                drawLine(Color(0xFF38BDF8), Offset(frontLeft, frontTop), Offset(midX, frontTop + 4f), strokeWidth = 1.8f)
            }
            type.code.contains("triple") || type.code.contains("120") -> {
                val third = boxW / 3f
                drawLine(Color(0xFF1E293B), Offset(frontLeft + third, frontTop), Offset(frontLeft + third, carcassBottom), strokeWidth = 1.4f)
                drawLine(Color(0xFF1E293B), Offset(frontLeft + third * 2, frontTop), Offset(frontLeft + third * 2, carcassBottom), strokeWidth = 1.4f)
            }
            type.code.contains("double") || type.code.contains("80") || type.code.contains("90") -> {
                val midX = frontLeft + boxW * 0.5f
                drawLine(Color(0xFF1E293B), Offset(midX, frontTop), Offset(midX, carcassBottom), strokeWidth = 1.4f)
                val hY1 = if (isWallOrBridge) carcassBottom - carcassH * 0.35f else frontTop + carcassH * 0.22f
                val hY2 = if (isWallOrBridge) carcassBottom - carcassH * 0.12f else frontTop + carcassH * 0.45f
                drawLine(Color(0xFF0F172A), Offset(midX - 3.5f, hY1), Offset(midX - 3.5f, hY2), strokeWidth = 1.6f)
                drawLine(Color(0xFF0F172A), Offset(midX + 3.5f, hY1), Offset(midX + 3.5f, hY2), strokeWidth = 1.6f)
            }
            else -> {
                // Single door handle
                val handleX = frontRight - 4.5f
                val hY1 = if (isWallOrBridge) carcassBottom - carcassH * 0.35f else frontTop + carcassH * 0.22f
                val hY2 = if (isWallOrBridge) carcassBottom - carcassH * 0.12f else frontTop + carcassH * 0.48f
                drawLine(Color(0xFF0F172A), Offset(handleX, hY1), Offset(handleX, hY2), strokeWidth = 1.8f)
            }
        }

        // Under-cabinet warm LED strip glow for Wall cabinets
        if (type.category == ElementCategory.WALL_CABINET) {
            drawLine(
                color = Color(0xFFFDE047),
                start = Offset(frontLeft + 2f, carcassBottom + 2f),
                end = Offset(frontRight - 2f, carcassBottom + 2f),
                strokeWidth = 2.8f
            )
        }
    }
}

