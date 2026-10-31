package com.example.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.domain.model.KitchenElement
import com.example.domain.model.LayerType
import com.example.presentation.viewmodel.KitchenEditorUiState

@Composable
fun LayersPanelDialog(
    state: KitchenEditorUiState,
    onSelectElement: (String, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onToggleMultiSelect: () -> Unit,
    onGroupSelected: () -> Unit,
    onUngroupSelected: () -> Unit,
    onToggleLayerVisibility: (LayerType) -> Unit,
    onToggleLayerLock: (LayerType) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A),
            tonalElevation = 12.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الطبقات والمجموعات / Layers & Groups",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Multi-selection & Grouping Command Toolbar
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "العناصر المحددة: ${state.selectedElementIds.size}",
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = onSelectAll, modifier = Modifier.height(32.dp)) {
                                    Text("تحديد الكل", fontSize = 11.sp, color = Color(0xFF38BDF8))
                                }
                                TextButton(onClick = onClearSelection, modifier = Modifier.height(32.dp)) {
                                    Text("إلغاء", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Group & Ungroup Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onGroupSelected,
                                enabled = state.selectedElementIds.size >= 2,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                modifier = Modifier.weight(1f).testTag("group_selected_button")
                            ) {
                                Icon(Icons.Default.GroupWork, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تجميع (Group)", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = onUngroupSelected,
                                enabled = state.selectedElements.any { it.groupId != null },
                                modifier = Modifier.weight(1f).testTag("ungroup_selected_button")
                            ) {
                                Icon(Icons.Default.LayersClear, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("فك التجميع", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Layers Visibility & Lock Manager
                Text("إدارة طبقات المطبخ (Layers):", color = Color(0xFF94A3B8), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(LayerType.values()) { layer ->
                        val isHidden = layer in state.hiddenLayers
                        val isLocked = layer in state.lockedLayers
                        val elementCount = state.project.elements.count { it.layer == layer }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${layer.title} ($elementCount)",
                                color = if (isHidden) Color(0xFF64748B) else Color.White,
                                fontSize = 12.sp
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onToggleLayerVisibility(layer) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "إظهار/إخفاء",
                                        tint = if (isHidden) Color.Gray else Color(0xFF38BDF8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onToggleLayerLock(layer) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = "قفل",
                                        tint = if (isLocked) Color(0xFFF59E0B) else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Active Groups Section
                if (state.project.groups.isNotEmpty()) {
                    Text("المجموعات النشطة (${state.project.groups.size}):", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.project.groups) { grp ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E293B), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${grp.name} (${grp.elementIds.size} عنصر)",
                                    color = Color(0xFF34D399),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("تم / Done", color = Color(0xFF38BDF8))
                }
            }
        }
    }
}
