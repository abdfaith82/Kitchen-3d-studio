package com.example.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ElementCategory
import com.example.domain.model.KitchenElementType
import com.example.domain.model.OpeningType
import com.example.domain.model.WallSide
import com.example.presentation.viewmodel.ActiveDialog
import com.example.presentation.viewmodel.KitchenEditorUiState

/**
 * Collapsible Floating CAD Mode Toolbar providing:
 * - Compact Header Pill when `state.isControlPanelsCollapsed` is true (so the 3D/2D design is NEVER covered!)
 * - Full expandable controls for:
 *   1. 'Wall Creation' mode
 *   2. 'Furniture Placement' mode (with 3D visual element thumbnails & direct drag-and-drop into workspace)
 *   3. 'Dimension Snapping' mode
 *   4. 'Aluminum & Melamine Calculator' quick button
 */
@Composable
fun FloatingEditorToolbar(
    state: KitchenEditorUiState,
    onToggleWallCreation: () -> Unit,
    onToggleFurniturePlacement: () -> Unit,
    onToggleDimensionSnapping: () -> Unit,
    onSelectPlacementType: (KitchenElementType) -> Unit,
    onQuickAddFurniture: (KitchenElementType) -> Unit,
    onSelectWallSide: (WallSide) -> Unit,
    onUpdateRoomDimensions: (Float, Float, Float) -> Unit,
    onUpdateWallThickness: (Float) -> Unit,
    onQuickAddOpening: (String, OpeningType) -> Unit,
    onSetSnapThreshold: (Float) -> Unit,
    onOpenDialog: (ActiveDialog) -> Unit,
    onToggleCollapsePanels: () -> Unit = {},
    onStartExternalDrag: ((KitchenElementType, Offset) -> Unit)? = null,
    onUpdateExternalDrag: ((Offset) -> Unit)? = null,
    onEndExternalDrag: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // When Clean Canvas mode is active, hide the floating toolbar completely
    if (state.isCleanCanvasMode) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("floating_toolbar"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (state.isControlPanelsCollapsed) {
            // Compact, ultra-slim translucent pill at the top that NEVER covers the 3D kitchen design
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.82f),
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Expand Control Panels Pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onToggleCollapsePanels() }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .testTag("expand_control_panels_btn"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = "إظهار لوحات التحكم",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "أدوات التصميم السريع",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Compact Mode Toggles (preserved testTags for Robolectric/unit tests)
                    CompactPillToggle(
                        label = "الجدران",
                        icon = Icons.Default.BorderOuter,
                        isSelected = state.isWallCreationMode,
                        activeColor = Color(0xFF0284C7),
                        testTag = "toggle_wall_creation",
                        onClick = onToggleWallCreation
                    )

                    CompactPillToggle(
                        label = "الأثاث",
                        icon = Icons.Default.Kitchen,
                        isSelected = state.isFurniturePlacementMode,
                        activeColor = Color(0xFFF59E0B),
                        testTag = "toggle_furniture_placement",
                        onClick = onToggleFurniturePlacement
                    )

                    CompactPillToggle(
                        label = if (state.isSmartSnappingEnabled) "لاصق ${state.snapThresholdMm.toInt()}مم" else "لاصق OFF",
                        icon = Icons.Default.Straighten,
                        isSelected = state.isSmartSnappingEnabled,
                        activeColor = Color(0xFF10B981),
                        testTag = "toggle_dimension_snapping",
                        onClick = onToggleDimensionSnapping
                    )

                    // Direct shortcut to Automatic 1mm Cabinet Filler & Spacer Tool
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0D9488).copy(alpha = 0.28f),
                        border = BorderStroke(1.dp, Color(0xFF14B8A6)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenDialog(ActiveDialog.CabinetFillerTool) }
                            .testTag("quick_open_cabinet_filler_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.ViewColumn,
                                contentDescription = null,
                                tint = Color(0xFF2DD4BF),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "فيلر الجدار 1مم",
                                color = Color(0xFFCCFBF1),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Direct shortcut to Aluminum & Melamine Calculator
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0284C7).copy(alpha = 0.28f),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenDialog(ActiveDialog.AluminumMelamineCalculator) }
                            .testTag("quick_open_alu_melamine_calc_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Calculate,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "حساب الألمنيوم والميلامين",
                                color = Color(0xFFE0F2FE),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Contextual Sub-Panels also work in compact mode without expanding the bulky header
            AnimatedVisibility(
                visible = state.isWallCreationMode,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                WallCreationSubPanel(
                    state = state,
                    onSelectWallSide = onSelectWallSide,
                    onUpdateRoomDimensions = onUpdateRoomDimensions,
                    onUpdateWallThickness = onUpdateWallThickness,
                    onQuickAddOpening = onQuickAddOpening,
                    onOpenSketch = { onOpenDialog(ActiveDialog.SketchCanvas) },
                    onOpenRoomSettings = { onOpenDialog(ActiveDialog.RoomSettings) },
                    onCloseSubPanel = onToggleWallCreation
                )
            }

            AnimatedVisibility(
                visible = state.isFurniturePlacementMode,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                FurniturePlacementSubPanel(
                    state = state,
                    onSelectPlacementType = onSelectPlacementType,
                    onQuickAddFurniture = onQuickAddFurniture,
                    onOpenCatalog = { onOpenDialog(ActiveDialog.Catalog) },
                    onCloseSubPanel = onToggleFurniturePlacement,
                    onStartExternalDrag = onStartExternalDrag,
                    onUpdateExternalDrag = onUpdateExternalDrag,
                    onEndExternalDrag = onEndExternalDrag
                )
            }
        } else {
            // Expanded Primary Floating Toggle Bar with explicit "إخفاء اللوحة ▴" button
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.94f),
                tonalElevation = 10.dp,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Collapse Button so the user can hide the floating cards anytime
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onToggleCollapsePanels() }
                            .testTag("collapse_control_panels_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.ExpandLess,
                                contentDescription = "طي لوحة التحكم",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "إخفاء اللوحة",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 1. Wall Creation Toggle Button
                    ModeTogglePill(
                        title = "Wall Creation",
                        subtitle = "إنشاء الجدران",
                        icon = Icons.Default.BorderOuter,
                        isSelected = state.isWallCreationMode,
                        activeColor = Color(0xFF0284C7),
                        activeBadgeText = if (state.isWallCreationMode) "ACTIVE" else null,
                        testTag = "toggle_wall_creation",
                        onClick = onToggleWallCreation
                    )

                    // 2. Furniture Placement Toggle Button
                    ModeTogglePill(
                        title = "Furniture Placement",
                        subtitle = "وضع الأثاث",
                        icon = Icons.Default.Kitchen,
                        isSelected = state.isFurniturePlacementMode,
                        activeColor = Color(0xFFF59E0B),
                        activeBadgeText = if (state.isFurniturePlacementMode) "ACTIVE" else null,
                        testTag = "toggle_furniture_placement",
                        onClick = onToggleFurniturePlacement
                    )

                    // 3. Dimension Snapping Toggle Button
                    ModeTogglePill(
                        title = "Dimension Snapping",
                        subtitle = "التقاط الأبعاد",
                        icon = Icons.Default.Straighten,
                        isSelected = state.isSmartSnappingEnabled,
                        activeColor = Color(0xFF10B981),
                        activeBadgeText = if (state.isSmartSnappingEnabled) "${state.snapThresholdMm.toInt()}mm" else "OFF",
                        testTag = "toggle_dimension_snapping",
                        onClick = onToggleDimensionSnapping
                    )

                    // 4. Automatic 1mm Cabinet Filler & Spacer Tool Pill
                    ModeTogglePill(
                        title = "Auto Filler 1mm",
                        subtitle = "فيلر وفواصل الجدار",
                        icon = Icons.Default.ViewColumn,
                        isSelected = state.activeDialog is ActiveDialog.CabinetFillerTool,
                        activeColor = Color(0xFF0D9488),
                        activeBadgeText = "1mm",
                        testTag = "expanded_open_cabinet_filler_btn",
                        onClick = { onOpenDialog(ActiveDialog.CabinetFillerTool) }
                    )
                }
            }

            // Contextual Sub-Panel 1: Wall Creation Tools
            AnimatedVisibility(
                visible = state.isWallCreationMode,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                WallCreationSubPanel(
                    state = state,
                    onSelectWallSide = onSelectWallSide,
                    onUpdateRoomDimensions = onUpdateRoomDimensions,
                    onUpdateWallThickness = onUpdateWallThickness,
                    onQuickAddOpening = onQuickAddOpening,
                    onOpenSketch = { onOpenDialog(ActiveDialog.SketchCanvas) },
                    onOpenRoomSettings = { onOpenDialog(ActiveDialog.RoomSettings) },
                    onCloseSubPanel = onToggleWallCreation
                )
            }

            // Contextual Sub-Panel 2: Furniture Placement Palette
            AnimatedVisibility(
                visible = state.isFurniturePlacementMode,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                FurniturePlacementSubPanel(
                    state = state,
                    onSelectPlacementType = onSelectPlacementType,
                    onQuickAddFurniture = onQuickAddFurniture,
                    onOpenCatalog = { onOpenDialog(ActiveDialog.Catalog) },
                    onCloseSubPanel = onToggleFurniturePlacement,
                    onStartExternalDrag = onStartExternalDrag,
                    onUpdateExternalDrag = onUpdateExternalDrag,
                    onEndExternalDrag = onEndExternalDrag
                )
            }

            // Contextual Sub-Panel 3: Dimension Snapping Precision Bar
            AnimatedVisibility(
                visible = state.isSmartSnappingEnabled && !state.isWallCreationMode && !state.isFurniturePlacementMode,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                DimensionSnappingSubPanel(
                    snapThresholdMm = state.snapThresholdMm,
                    onSetSnapThreshold = onSetSnapThreshold
                )
            }
        }
    }
}

@Composable
private fun CompactPillToggle(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    activeColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) activeColor.copy(alpha = 0.25f) else Color(0xFF1E293B),
        border = BorderStroke(1.dp, if (isSelected) activeColor else Color(0xFF334155)),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Switch, onClick = onClick)
            .semantics {
                this.selected = isSelected
                this.role = Role.Switch
                this.contentDescription = label
            }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) activeColor else Color(0xFF94A3B8),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ModeTogglePill(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    activeColor: Color,
    activeBadgeText: String?,
    testTag: String,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) activeColor.copy(alpha = 0.22f) else Color(0xFF1E293B)
    val borderColor = if (isSelected) activeColor else Color(0xFF334155)
    val contentColor = if (isSelected) Color.White else Color(0xFFCBD5E1)
    val iconTint = if (isSelected) activeColor else Color(0xFF94A3B8)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                role = Role.Switch,
                onClick = onClick
            )
            .semantics {
                this.selected = isSelected
                this.role = Role.Switch
                this.contentDescription = "$title ($subtitle)"
            }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(
                        color = if (isSelected) activeColor.copy(alpha = 0.25f) else Color(0xFF0F172A),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        color = contentColor,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                    if (activeBadgeText != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isSelected) activeColor else Color(0xFF475569)
                        ) {
                            Text(
                                text = activeBadgeText,
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    color = if (isSelected) activeColor else Color(0xFF64748B),
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
private fun WallCreationSubPanel(
    state: KitchenEditorUiState,
    onSelectWallSide: (WallSide) -> Unit,
    onUpdateRoomDimensions: (Float, Float, Float) -> Unit,
    onUpdateWallThickness: (Float) -> Unit,
    onQuickAddOpening: (String, OpeningType) -> Unit,
    onOpenSketch: () -> Unit,
    onOpenRoomSettings: () -> Unit,
    onCloseSubPanel: () -> Unit
) {
    val room = state.project.room
    val selectedWall = room.walls.find { it.side == state.selectedWallSide } ?: room.walls.firstOrNull()

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.92f),
        border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.6f)),
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wall_creation_subpanel")
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                        Icons.Default.Architecture,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "أدوات الجدران (${room.widthMm.toInt()}×${room.depthMm.toInt()} مم)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = onOpenSketch,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(26.dp)
                            .testTag("wall_sketch_btn"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF0284C7).copy(alpha = 0.3f),
                            contentColor = Color(0xFF38BDF8)
                        )
                    ) {
                        Icon(Icons.Default.Gesture, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("رسم سكتش", fontSize = 10.sp)
                    }

                    FilledTonalButton(
                        onClick = onOpenRoomSettings,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(26.dp)
                            .testTag("wall_settings_btn"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إعدادات", fontSize = 10.sp)
                    }

                    IconButton(
                        onClick = onCloseSubPanel,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إخفاء", tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WallSide.values().forEach { side ->
                    val isWallSelected = state.selectedWallSide == side
                    FilterChip(
                        selected = isWallSelected,
                        onClick = { onSelectWallSide(side) },
                        label = { Text(side.title, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0284C7),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFFCBD5E1)
                        )
                    )
                }

                AssistChip(
                    onClick = {
                        onUpdateRoomDimensions(
                            (room.widthMm + 200f).coerceAtMost(12000f),
                            room.depthMm,
                            room.heightMm
                        )
                    },
                    label = { Text("+200 عرض", fontSize = 10.sp, color = Color(0xFF38BDF8)) }
                )
                AssistChip(
                    onClick = {
                        onUpdateRoomDimensions(
                            (room.widthMm - 200f).coerceAtLeast(1800f),
                            room.depthMm,
                            room.heightMm
                        )
                    },
                    label = { Text("-200 عرض", fontSize = 10.sp, color = Color(0xFF94A3B8)) }
                )
                AssistChip(
                    onClick = {
                        onUpdateRoomDimensions(
                            room.widthMm,
                            (room.depthMm + 200f).coerceAtMost(12000f),
                            room.heightMm
                        )
                    },
                    label = { Text("+200 عمق", fontSize = 10.sp, color = Color(0xFF38BDF8)) }
                )

                if (selectedWall != null) {
                    AssistChip(
                        onClick = { onQuickAddOpening(selectedWall.id, OpeningType.WINDOW) },
                        label = { Text("+ نافذة", fontSize = 10.sp, color = Color(0xFF34D399)) }
                    )
                    AssistChip(
                        onClick = { onQuickAddOpening(selectedWall.id, OpeningType.DOOR) },
                        label = { Text("+ باب", fontSize = 10.sp, color = Color(0xFFF59E0B)) }
                    )
                }

                AssistChip(
                    onClick = {
                        val nextThick = when (room.wallThicknessMm.toInt()) {
                            150 -> 200f
                            200 -> 250f
                            else -> 150f
                        }
                        onUpdateWallThickness(nextThick)
                    },
                    label = { Text("سماكة: ${room.wallThicknessMm.toInt()} مم", fontSize = 10.sp, color = Color.White) }
                )
            }
        }
    }
}

@Composable
private fun FurniturePlacementSubPanel(
    state: KitchenEditorUiState,
    onSelectPlacementType: (KitchenElementType) -> Unit,
    onQuickAddFurniture: (KitchenElementType) -> Unit,
    onOpenCatalog: () -> Unit,
    onCloseSubPanel: () -> Unit,
    onStartExternalDrag: ((KitchenElementType, Offset) -> Unit)? = null,
    onUpdateExternalDrag: ((Offset) -> Unit)? = null,
    onEndExternalDrag: (() -> Unit)? = null
) {
    var selectedCategoryFilter by remember { mutableStateOf<ElementCategory?>(null) }
    val allElements = remember { KitchenElementType.values().toList() }
    val filteredElements = remember(selectedCategoryFilter) {
        if (selectedCategoryFilter == null) allElements
        else allElements.filter { it.category == selectedCategoryFilter }
    }

    val categoryTabs: List<Pair<ElementCategory?, String>> = remember {
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
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.92f),
        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)),
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("furniture_placement_subpanel")
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "اسحب العنصر وضعه في مكانه بالمخطط",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { onQuickAddFurniture(state.activePlacementType) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(26.dp)
                            .testTag("quick_place_furniture_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF59E0B),
                            contentColor = Color(0xFF0F172A)
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("إدراج الآن", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onOpenCatalog,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(26.dp)
                            .testTag("full_catalog_btn"),
                        border = BorderStroke(1.dp, Color(0xFF475569))
                    ) {
                        Text("الكتالوج الكامل", fontSize = 10.sp, color = Color(0xFFCBD5E1))
                    }

                    IconButton(
                        onClick = onCloseSubPanel,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إخفاء", tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                    }
                }
            }

            // Category Filter Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                categoryTabs.forEach { (cat, label) ->
                    val isSelectedCat = selectedCategoryFilter == cat
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelectedCat) Color(0xFF0284C7) else Color(0xFF1E293B),
                        border = BorderStroke(1.dp, if (isSelectedCat) Color(0xFF38BDF8) else Color(0xFF334155)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedCategoryFilter = cat }
                    ) {
                        Text(
                            text = label,
                            color = if (isSelectedCat) Color.White else Color(0xFFCBD5E1),
                            fontSize = 10.sp,
                            fontWeight = if (isSelectedCat) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Visual 3D Shape Carousel with Direct Touch Drag-and-Drop into Design Workspace
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                filteredElements.forEach { itemType ->
                    val isSelected = state.activePlacementType == itemType
                    var cardRootPos by remember { mutableStateOf(Offset.Zero) }
                    var currentFingerRoot by remember { mutableStateOf(Offset.Zero) }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFFF59E0B).copy(alpha = 0.22f) else Color(0xFF1E293B),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) Color(0xFFF59E0B) else Color(0xFF334155)
                        ),
                        modifier = Modifier
                            .width(154.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .onGloballyPositioned { coords ->
                                cardRootPos = coords.positionInRoot()
                            }
                            .clickable { onSelectPlacementType(itemType) }
                            .pointerInput(itemType) {
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        onSelectPlacementType(itemType)
                                        currentFingerRoot = cardRootPos + startOffset
                                        onStartExternalDrag?.invoke(itemType, currentFingerRoot)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        currentFingerRoot += dragAmount
                                        onUpdateExternalDrag?.invoke(currentFingerRoot)
                                    },
                                    onDragEnd = {
                                        onEndExternalDrag?.invoke()
                                    },
                                    onDragCancel = {
                                        onEndExternalDrag?.invoke()
                                    }
                                )
                            }
                            .testTag("visual_menu_item_${itemType.code}")
                    ) {
                        Row(
                            modifier = Modifier.padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            CabinetPreviewThumbnail(
                                type = itemType,
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
                                    text = itemType.title,
                                    color = if (isSelected) Color(0xFFFDE68A) else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 12.sp
                                )
                                Text(
                                    text = "${(itemType.defaultDimensions.x / 10f).toInt()}×${(itemType.defaultDimensions.z / 10f).toInt()} سم",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "اسحب للمخطط ✥",
                                    color = Color(0xFFF59E0B),
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
}

@Composable
private fun DimensionSnappingSubPanel(
    snapThresholdMm: Float,
    onSetSnapThreshold: (Float) -> Unit
) {
    val thresholds = listOf(1f, 10f, 50f, 80f, 120f)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.88f),
        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f)),
        modifier = Modifier.testTag("dimension_snapping_subpanel")
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.GridOn,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "حساسية الالتقاط المغناطيسي:",
                color = Color(0xFFCBD5E1),
                fontSize = 10.sp
            )
            thresholds.forEach { value ->
                val isSelected = snapThresholdMm.toInt() == value.toInt()
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) Color(0xFF10B981) else Color(0xFF1E293B),
                    modifier = Modifier
                        .clickable { onSetSnapThreshold(value) }
                        .testTag("snap_threshold_${value.toInt()}")
                ) {
                    Text(
                        text = "${value.toInt()}mm",
                        color = if (isSelected) Color(0xFF020617) else Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
