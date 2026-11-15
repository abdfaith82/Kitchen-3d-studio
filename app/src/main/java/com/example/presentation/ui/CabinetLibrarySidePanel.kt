package com.example.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.KitchenCostCalculator
import com.example.domain.model.ElementCategory
import com.example.domain.model.KitchenElementType
import com.example.presentation.viewmodel.KitchenEditorUiState
import java.util.Locale

/**
 * Collapsible Side-Panel displaying the interactive 3D Kitchen Cabinet Library.
 * Supports:
 * - Instant search & category filtering (سفلية، علوية، أبراج، أجهزة، جزر، تاج)
 * - 3D isometric cabinet previews, real-time unit pricing, and millimeter/cm badges
 * - Drag-and-drop placement directly into 2D CAD / 3D Viewport workspaces
 * - Selected cabinet live dimension adjustment & 3D full back-panel toggle
 */
@Composable
fun CabinetLibrarySidePanel(
    state: KitchenEditorUiState,
    onQuickAddElement: (KitchenElementType) -> Unit,
    onUpdateSelectedDimensions: (String, Float, Float, Float) -> Unit,
    onToggleBackPanel: (Boolean) -> Unit,
    onHideSidebar: () -> Unit,
    onOpenFullCatalog: () -> Unit = {},
    onStartExternalDrag: ((KitchenElementType, Offset) -> Unit)? = null,
    onUpdateExternalDrag: ((Offset) -> Unit)? = null,
    onEndExternalDrag: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val selected = state.singleSelectedElement
    var sidebarCategoryFilter by remember { mutableStateOf<ElementCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val allCatalogTypes = remember { KitchenElementType.entries.toList() }
    val displayedTypes = remember(sidebarCategoryFilter, searchQuery) {
        allCatalogTypes.filter { type ->
            val matchesCat = sidebarCategoryFilter == null || type.category == sidebarCategoryFilter
            val q = searchQuery.trim()
            val matchesQuery = q.isEmpty() ||
                type.title.contains(q, ignoreCase = true) ||
                type.code.contains(q, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }

    val sidebarCategories: List<Pair<ElementCategory?, String>> = remember {
        listOf(
            null to "الكل",
            ElementCategory.BASE_CABINET to "سفلية",
            ElementCategory.WALL_CABINET to "علوية",
            ElementCategory.TALL_UNIT to "أبراج",
            ElementCategory.APPLIANCE to "أجهزة",
            ElementCategory.ISLAND_AND_SURFACE to "جزر",
            ElementCategory.BRIDGE_CABINET to "جسر"
        )
    }

    Surface(
        modifier = modifier
            .width(236.dp)
            .fillMaxHeight()
            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.28f), RoundedCornerShape(16.dp))
            .testTag("collapsible_slim_sidebar"),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.86f),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            // Header
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
                        imageVector = Icons.Default.ViewInAr,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "مكتبة خزائن 3D",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onOpenFullCatalog)
                            .testTag("sidebar_open_full_catalog_btn")
                    ) {
                        Text(
                            text = "الكل",
                            color = Color(0xFF7DD3FC),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    IconButton(
                        onClick = onHideSidebar,
                        modifier = Modifier
                            .size(26.dp)
                            .testTag("hide_sidebar_inside_btn")
                    ) {
                        Icon(
                            Icons.Default.ChevronLeft,
                            contentDescription = "إخفاء المكتبة الجانبية",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.padding(vertical = 5.dp)
            )

            // Selected Element Quick Dimension Inspector
            if (selected != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "المحدد: ${selected.name}",
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "العرض ${(selected.dimensions.x / 10f).toInt()}×الارتفاع ${(selected.dimensions.y / 10f).toInt()}×العمق ${(selected.dimensions.z / 10f).toInt()} سم",
                            color = Color.White,
                            fontSize = 10.sp
                        )
                        Text(
                            text = if (selected.type.category == ElementCategory.WALL_CABINET)
                                "ارتفاع القاع: ${(selected.position.y / 10f).toInt()} سم (سفلية + 60سم)"
                            else
                                "الارتفاع Y: ${(selected.position.y / 10f).toInt()} سم",
                            color = Color(0xFF34D399),
                            fontSize = 9.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ظهر الخزانة 3D:", color = Color(0xFFCBD5E1), fontSize = 10.sp)
                            Switch(
                                checked = selected.hasBackPanel,
                                onCheckedChange = onToggleBackPanel,
                                modifier = Modifier.testTag("sidebar_back_panel_switch")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onUpdateSelectedDimensions(
                                        selected.id,
                                        selected.dimensions.x,
                                        (selected.dimensions.y + 50f).coerceAtMost(2400f),
                                        selected.dimensions.z
                                    )
                                },
                                contentPadding = PaddingValues(2.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(26.dp)
                                    .testTag("sidebar_increase_height_btn")
                            ) {
                                Text("+ارتفاع 5سم", fontSize = 9.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    onUpdateSelectedDimensions(
                                        selected.id,
                                        (selected.dimensions.x + 50f).coerceAtMost(1800f),
                                        selected.dimensions.y,
                                        selected.dimensions.z
                                    )
                                },
                                contentPadding = PaddingValues(2.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(26.dp)
                            ) {
                                Text("+عرض 5سم", fontSize = 9.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Compact CAD Search Bar
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .testTag("sidebar_cabinet_search_box")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "بحث في مكتبة الخزائن...",
                                color = Color(0xFF64748B),
                                fontSize = 10.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 11.sp),
                            cursorBrush = SolidColor(Color(0xFF38BDF8)),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "مسح البحث",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = "اسحب أي وحدة 3D وأفلتها في مساحة التصميم:",
                color = Color(0xFFFBBF24),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                sidebarCategories.forEach { (cat, label) ->
                    val isSelectedCat = sidebarCategoryFilter == cat
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelectedCat) Color(0xFF0284C7) else Color(0xFF1E293B),
                        border = BorderStroke(
                            1.dp,
                            if (isSelectedCat) Color(0xFF38BDF8) else Color(0xFF334155)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { sidebarCategoryFilter = cat }
                    ) {
                        Text(
                            text = label,
                            color = if (isSelectedCat) Color.White else Color(0xFFCBD5E1),
                            fontSize = 9.sp,
                            fontWeight = if (isSelectedCat) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (displayedTypes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد خزائن مطابقة للبحث",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(displayedTypes, key = { it.code }) { type ->
                        CabinetLibrarySidePanelItem(
                            type = type,
                            onQuickAddElement = onQuickAddElement,
                            onStartExternalDrag = onStartExternalDrag,
                            onUpdateExternalDrag = onUpdateExternalDrag,
                            onEndExternalDrag = onEndExternalDrag
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CabinetLibrarySidePanelItem(
    type: KitchenElementType,
    onQuickAddElement: (KitchenElementType) -> Unit,
    onStartExternalDrag: ((KitchenElementType, Offset) -> Unit)?,
    onUpdateExternalDrag: ((Offset) -> Unit)?,
    onEndExternalDrag: (() -> Unit)?
) {
    var itemRootPos by remember { mutableStateOf(Offset.Zero) }
    var activeFingerRoot by remember { mutableStateOf(Offset.Zero) }
    val unitPriceUsd = remember(type) { KitchenCostCalculator.calculateUnitPriceForType(type) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF1E293B).copy(alpha = 0.82f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                itemRootPos = coords.positionInRoot()
            }
            .clickable { onQuickAddElement(type) }
            .pointerInput(type) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        activeFingerRoot = itemRootPos + startOffset
                        onStartExternalDrag?.invoke(type, activeFingerRoot)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        activeFingerRoot += dragAmount
                        onUpdateExternalDrag?.invoke(activeFingerRoot)
                    },
                    onDragEnd = {
                        onEndExternalDrag?.invoke()
                    },
                    onDragCancel = {
                        onEndExternalDrag?.invoke()
                    }
                )
            }
            .testTag("sidebar_visual_item_${type.code}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CabinetPreviewThumbnail(
                type = type,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A))
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = type.title,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 12.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${(type.defaultDimensions.x / 10f).toInt()}×${(type.defaultDimensions.y / 10f).toInt()}×${(type.defaultDimensions.z / 10f).toInt()} سم",
                        color = Color(0xFF38BDF8),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format(Locale.US, "$%.0f", unitPriceUsd),
                        color = Color(0xFF34D399),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragIndicator,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "اسحب للمساحة",
                            color = Color(0xFFF59E0B),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF0284C7).copy(alpha = 0.25f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "إدراج سريع",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(9.dp)
                            )
                            Text(
                                text = "إدراج",
                                color = Color(0xFF7DD3FC),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
