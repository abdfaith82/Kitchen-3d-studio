package com.example.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.InternalAccessoryType
import com.example.domain.model.KitchenElement
import com.example.domain.model.SmartAccessoryMatcher
import java.util.Locale

@Composable
fun SmartAccessoriesDialog(
    elements: List<KitchenElement>,
    enabledAccessoryIds: Set<String>,
    onToggleAccessory: (String) -> Unit,
    onEnableAllCompatible: () -> Unit,
    onOpenBomDialog: () -> Unit,
    onDismiss: () -> Unit
) {
    val matchedItems = SmartAccessoryMatcher.matchAccessoriesForElements(elements, enabledAccessoryIds)
    val totalAccessoriesCost = matchedItems.sumOf { it.totalCostUsd.toDouble() }.toFloat()
    val totalLinkedUnits = matchedItems.sumOf { it.quantity }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A),
            tonalElevation = 14.dp,
            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 680.dp)
                .padding(4.dp)
                .testTag("smart_accessories_dialog")
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "مكتبة الإكسسوارات الذكية",
                            tint = Color(0xFFFBBF24)
                        )
                        Column {
                            Text(
                                text = "مكتبة الإكسسوارات الداخلية الذكية (Auto-BOM Link)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Text(
                                text = "ترتبط تلقائياً بالخزائن المتوافقة وتحدّث جدول الكميات (BOM) والتكلفة",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Summary Bar
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "إجمالي القطع المرتبطة بالخزائن الحالية: $totalLinkedUnits طقم",
                                color = Color(0xFFFDE68A),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = String.format(Locale.US, "إجمالي تكلفة الإكسسوارات في BOM: $%.0f", totalAccessoriesCost),
                                color = Color(0xFF34D399),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        TextButton(
                            onClick = onEnableAllCompatible,
                            modifier = Modifier.testTag("enable_all_accessories_button")
                        ) {
                            Text("تفعيل الكل تلقائياً", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(InternalAccessoryType.entries) { accessory ->
                        val isEnabled = accessory.id in enabledAccessoryIds
                        val compatibleUnits = elements.filter { it.type in accessory.compatibleTypes }
                        val isCompatibleWithCurrentLayout = compatibleUnits.isNotEmpty()

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isEnabled && isCompatibleWithCurrentLayout) {
                                Color(0xFF1E293B)
                            } else {
                                Color(0xFF0F172A)
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = when {
                                    isEnabled && isCompatibleWithCurrentLayout -> Color(0xFFF59E0B)
                                    isCompatibleWithCurrentLayout -> Color(0xFF334155)
                                    else -> Color(0xFF1E293B)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleAccessory(accessory.id) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Checkbox(
                                    checked = isEnabled,
                                    onCheckedChange = { onToggleAccessory(accessory.id) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFFF59E0B),
                                        checkmarkColor = Color.Black
                                    )
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = accessory.arabicName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color.White
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF334155)
                                        ) {
                                            Text(
                                                text = accessory.code,
                                                color = Color(0xFF38BDF8),
                                                fontSize = 9.sp,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = accessory.descriptionAr,
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                    Text(
                                        text = "المقاس: ${accessory.dimensionsMm} | المواصفة: ${accessory.materialSpecAr}",
                                        fontSize = 9.sp,
                                        color = Color(0xFF64748B)
                                    )
                                    if (isCompatibleWithCurrentLayout) {
                                        Text(
                                            text = "✓ متوافق مع (${compatibleUnits.size}) وحدة في تصميمك: ${compatibleUnits.map { it.name }.distinct().joinToString("، ")}",
                                            fontSize = 10.sp,
                                            color = Color(0xFF34D399),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    } else {
                                        val targetNames = accessory.compatibleTypes.joinToString(" / ") { it.title }
                                        Text(
                                            text = "يتطلب إضافة وحدة من نوع: $targetNames",
                                            fontSize = 10.sp,
                                            color = Color(0xFFF59E0B)
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = String.format(Locale.US, "$%.0f / طقم", accessory.unitPriceUsd),
                                        color = Color(0xFFFBBF24),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    if (isEnabled && isCompatibleWithCurrentLayout) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "مرتبط بـ BOM",
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onOpenBomDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier.testTag("accessories_view_bom_button")
                    ) {
                        Text("عرض جدول الكميات المحدّث (BOM)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_accessories_dialog_button")
                    ) {
                        Text("تم / إغلاق", color = Color(0xFF38BDF8))
                    }
                }
            }
        }
    }
}
