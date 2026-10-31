package com.example.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.WallSide
import com.example.presentation.viewmodel.GizmoMode
import com.example.presentation.viewmodel.KitchenEditorUiState
import kotlin.math.roundToInt

@Composable
fun TransformControlsBar(
    state: KitchenEditorUiState,
    onMove: (Float, Float, Float) -> Unit,
    onRotate: (Float) -> Unit,
    onSetGizmoMode: (GizmoMode) -> Unit,
    onAlignWall: (WallSide) -> Unit,
    onToggleSnap: () -> Unit,
    onOpenAluminumCalculator: () -> Unit = {},
    onOpenMaterials: () -> Unit = {},
    onOpenFillerTool: () -> Unit = {},
    onToggleExpanded: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val selected = state.singleSelectedElement ?: return
    var activeStepMm by remember { mutableFloatStateOf(50f) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.90f),
        tonalElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            // Compact Primary Quick-Action Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(selected.material.colorHex))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = selected.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${(selected.dimensions.x / 10f).toInt()}×${(selected.dimensions.y / 10f).toInt()}×${(selected.dimensions.z / 10f).toInt()} سم | (${selected.position.x.roundToInt()}, ${selected.position.z.roundToInt()} مم)",
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Quick 4-Direction Step Pad directly on Primary Row so user can move X/Z anytime with 1 tap
                Row(
                    modifier = Modifier
                        .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = { onMove(-activeStepMm, 0f, 0f) },
                        modifier = Modifier.size(26.dp).testTag("quick_nudge_x_neg")
                    ) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "يسار X-", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { onMove(activeStepMm, 0f, 0f) },
                        modifier = Modifier.size(26.dp).testTag("quick_nudge_x_pos")
                    ) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "يمين X+", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { onMove(0f, 0f, -activeStepMm) },
                        modifier = Modifier.size(26.dp).testTag("quick_nudge_z_neg")
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "عمق للداخل Z-", tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { onMove(0f, 0f, activeStepMm) },
                        modifier = Modifier.size(26.dp).testTag("quick_nudge_z_pos")
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "عمق للخارج Z+", tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                    }
                }

                // Automatic 1mm Filler Strip Tool Button
                FilledTonalButton(
                    onClick = onOpenFillerTool,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFF59E0B).copy(alpha = 0.25f),
                        contentColor = Color(0xFFFBBF24)
                    ),
                    modifier = Modifier.height(28.dp).testTag("transform_bar_filler_tool_btn")
                ) {
                    Icon(Icons.Default.ViewColumn, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("فيلر تلقائي 1مم", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                // Quick Aluminum & Melamine Calc Button for Selected Cabinet
                FilledTonalButton(
                    onClick = onOpenAluminumCalculator,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF0284C7).copy(alpha = 0.28f),
                        contentColor = Color(0xFF38BDF8)
                    ),
                    modifier = Modifier.height(28.dp).testTag("transform_bar_alu_calc_btn")
                ) {
                    Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("حساب ألمنيوم وميلامين", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                // Quick Material / Camera Texture Button
                FilledTonalButton(
                    onClick = onOpenMaterials,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF1E293B),
                        contentColor = Color(0xFFFBBF24)
                    ),
                    modifier = Modifier.height(28.dp).testTag("transform_bar_material_btn")
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("الخامة/الكاميرا", fontSize = 10.sp)
                }

                // Quick Rotate 90°
                FilledTonalButton(
                    onClick = { onRotate(90f) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp).testTag("rotate_90_button")
                ) {
                    Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("تدوير 90°", fontSize = 10.sp)
                }

                // Smart Snapping 1mm Badge (Sticky)
                FilterChip(
                    selected = state.isSmartSnappingEnabled,
                    onClick = onToggleSnap,
                    label = {
                        Text(
                            text = if (state.isSmartSnappingEnabled) "لاصق 1 مم" else "حر",
                            fontSize = 10.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AllInclusive,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = if (state.isSmartSnappingEnabled) Color(0xFF10B981) else Color.Gray
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF10B981).copy(alpha = 0.2f),
                        selectedLabelColor = Color(0xFF34D399)
                    ),
                    modifier = Modifier.height(28.dp).testTag("toggle_snap_chip")
                )

                // Expand / Collapse Detailed Nudge & Alignment Controls
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier
                        .clickable { onToggleExpanded() }
                        .testTag("toggle_transform_details_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            if (state.isTransformBarExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            contentDescription = "تفاصيل التحريك",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (state.isTransformBarExpanded) "طي" else "تحريك دقيق X/Y/Z",
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Expandable Detailed Numeric Nudge (X, Z, Y) & Wall Alignment Controls
            AnimatedVisibility(visible = state.isTransformBarExpanded) {
                Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Coordinate Metrics (X, Y, Z in mm, Rotation, Dimensions) + Step Size Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MetricItem(label = "X (أفقي)", value = "${selected.position.x.toInt()} مم")
                        MetricItem(label = "Y (ارتفاع)", value = "${selected.position.y.toInt()} مم")
                        MetricItem(label = "Z (عمق)", value = "${selected.position.z.toInt()} مم")
                        MetricItem(label = "زاوية", value = "${selected.rotationY.toInt()}°")
                        MetricItem(label = "العرض", value = "${selected.dimensions.x.toInt()} مم")

                        // Step selector chips (1mm, 10mm, 50mm, 100mm)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text("الخطوة:", color = Color(0xFF94A3B8), fontSize = 9.sp)
                            listOf(1f to "1مم", 10f to "1سم", 50f to "5سم", 100f to "10سم").forEach { (stepVal, stepLbl) ->
                                val isStepSelected = activeStepMm == stepVal
                                Surface(
                                    shape = RoundedCornerShape(5.dp),
                                    color = if (isStepSelected) Color(0xFF0284C7) else Color(0xFF0F172A),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .clickable { activeStepMm = stepVal }
                                ) {
                                    Text(
                                        text = stepLbl,
                                        color = if (isStepSelected) Color.White else Color(0xFF94A3B8),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Row 1: X Axis Nudge Buttons + Z Axis (Depth) Nudge Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // X-Axis Nudge Buttons (Preserves exact testTags move_left_100..move_right_100)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "محور X (يمين/يسار):",
                                color = Color(0xFF38BDF8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { onMove(-100f, 0f, 0f) },
                                modifier = Modifier.size(30.dp).testTag("move_left_100")
                            ) {
                                Text("-100", color = Color(0xFF38BDF8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { onMove(-10f, 0f, 0f) },
                                modifier = Modifier.size(30.dp).testTag("move_left_10")
                            ) {
                                Text("-10", color = Color(0xFF38BDF8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { onMove(-1f, 0f, 0f) },
                                modifier = Modifier.size(30.dp).testTag("move_left_1")
                            ) {
                                Text("-1", color = Color(0xFF10B981), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { onMove(1f, 0f, 0f) },
                                modifier = Modifier.size(30.dp).testTag("move_right_1")
                            ) {
                                Text("+1", color = Color(0xFF10B981), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { onMove(10f, 0f, 0f) },
                                modifier = Modifier.size(30.dp).testTag("move_right_10")
                            ) {
                                Text("+10", color = Color(0xFF38BDF8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { onMove(100f, 0f, 0f) },
                                modifier = Modifier.size(30.dp).testTag("move_right_100")
                            ) {
                                Text("+100", color = Color(0xFF38BDF8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Z-Axis (Depth Front/Back) Nudge Buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "محور Z (عمق):",
                                color = Color(0xFF34D399),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { onMove(0f, 0f, -100f) },
                                modifier = Modifier.size(30.dp).testTag("move_back_z_100")
                            ) {
                                Text("-100", color = Color(0xFF34D399), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { onMove(0f, 0f, -10f) },
                                modifier = Modifier.size(30.dp).testTag("move_back_z_10")
                            ) {
                                Text("-10", color = Color(0xFF34D399), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { onMove(0f, 0f, -1f) },
                                modifier = Modifier.size(30.dp).testTag("move_back_z_1")
                            ) {
                                Text("-1", color = Color(0xFF10B981), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { onMove(0f, 0f, 1f) },
                                modifier = Modifier.size(30.dp).testTag("move_front_z_1")
                            ) {
                                Text("+1", color = Color(0xFF10B981), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { onMove(0f, 0f, 10f) },
                                modifier = Modifier.size(30.dp).testTag("move_front_z_10")
                            ) {
                                Text("+10", color = Color(0xFF34D399), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { onMove(0f, 0f, 100f) },
                                modifier = Modifier.size(30.dp).testTag("move_front_z_100")
                            ) {
                                Text("+100", color = Color(0xFF34D399), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Row 2: Y Axis (Elevation) Nudge Buttons + Quick Wall Alignment + Rotation
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "محور Y (ارتفاع):",
                                color = Color(0xFFFBBF24),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { onMove(0f, -50f, 0f) },
                                modifier = Modifier.size(30.dp).testTag("move_down_y_50")
                            ) {
                                Text("-50", color = Color(0xFFFBBF24), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { onMove(0f, -10f, 0f) },
                                modifier = Modifier.size(30.dp).testTag("move_down_y_10")
                            ) {
                                Text("-10", color = Color(0xFFFBBF24), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { onMove(0f, -1f, 0f) },
                                modifier = Modifier.size(30.dp).testTag("move_down_y_1")
                            ) {
                                Text("-1", color = Color(0xFF10B981), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { onMove(0f, 1f, 0f) },
                                modifier = Modifier.size(30.dp).testTag("move_up_y_1")
                            ) {
                                Text("+1", color = Color(0xFF10B981), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { onMove(0f, 10f, 0f) },
                                modifier = Modifier.size(30.dp).testTag("move_up_y_10")
                            ) {
                                Text("+10", color = Color(0xFFFBBF24), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { onMove(0f, 50f, 0f) },
                                modifier = Modifier.size(30.dp).testTag("move_up_y_50")
                            ) {
                                Text("+50", color = Color(0xFFFBBF24), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("محاذاة للجدار:", color = Color(0xFF94A3B8), fontSize = 10.sp)
                            WallAlignmentChip(title = "شمالي", onClick = { onAlignWall(WallSide.NORTH) })
                            WallAlignmentChip(title = "جنوبي", onClick = { onAlignWall(WallSide.SOUTH) })
                            WallAlignmentChip(title = "غربي", onClick = { onAlignWall(WallSide.WEST) })
                            WallAlignmentChip(title = "شرقي", onClick = { onAlignWall(WallSide.EAST) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color(0xFF64748B), fontSize = 9.sp)
        Text(text = value, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
    }
}

@Composable
private fun WallAlignmentChip(title: String, onClick: () -> Unit) {
    SuggestionChip(
        onClick = onClick,
        label = { Text(title, fontSize = 9.sp) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = Color(0xFF1E293B),
            labelColor = Color(0xFF38BDF8)
        ),
        modifier = Modifier.height(26.dp)
    )
}
