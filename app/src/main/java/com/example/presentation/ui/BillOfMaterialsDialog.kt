package com.example.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.domain.repository.BillOfMaterials

@Composable
fun BillOfMaterialsDialog(
    bom: BillOfMaterials?,
    onDismiss: () -> Unit
) {
    if (bom == null) return

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
                        text = "جدول الكميات والتصنيع / Bill of Materials (BOM)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8))
                    }
                }

                Text(
                    text = "المشروع: ${bom.projectName} | التاريخ: ${bom.generatedDate}",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Summary Metrics
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    BOMMetric(label = "إجمالي الخزائن", value = "${bom.totalCabinetsCount} وحدة")
                    BOMMetric(label = "الأمتار الطولية", value = "%.2f م.ط".format(bom.totalLinearMeters))
                    BOMMetric(label = "مساحة الرخام", value = "%.2f م²".format(bom.countertopAreaSquareMeters))
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "قائمة بنود الخزائن والتفاصيل الفنية:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color(0xFF38BDF8)
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(bom.items) { item ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E293B),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "المقاس: ${item.dimensionsMm} | الخامة: ${item.finishName}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF0284C7).copy(alpha = 0.2f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7))
                                ) {
                                    Text(
                                        text = "العدد: ${item.quantity}",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).testTag("close_bom_button")
                ) {
                    Text("إغلاق / Close", color = Color(0xFF38BDF8))
                }
            }
        }
    }
}

@Composable
private fun BOMMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color(0xFF94A3B8), fontSize = 10.sp)
        Text(text = value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
