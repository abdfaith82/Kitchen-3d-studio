package com.example.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.units.MeasurementUnitSystem
import com.example.domain.repository.BillOfMaterials
import java.util.Locale
import kotlin.math.roundToInt

data class CostChartSegment(
    val labelAr: String,
    val subLabelAr: String,
    val amountUsd: Float,
    val color: Color
)

@Composable
fun BillOfMaterialsDialog(
    bom: BillOfMaterials?,
    unitSystem: MeasurementUnitSystem = MeasurementUnitSystem.METRIC,
    onToggleUnitSystem: () -> Unit = {},
    onOpenAccessoriesLibrary: () -> Unit = {},
    onDismiss: () -> Unit
) {
    if (bom == null) return

    var selectedChartMode by remember { mutableStateOf(0) } // 0 = Donut + Bars, 1 = Detailed Bars

    // Compute Materials, Labor, and Accessories breakdown for the visual chart component
    val materialsCostUsd = (bom.cabinetsEstimatedCostUsd + bom.countertopEstimatedCostUsd).coerceAtLeast(1f)
    val accessoriesCostUsd = bom.accessoriesEstimatedCostUsd.coerceAtLeast(0f)
    val laborCostUsd = ((materialsCostUsd * 0.22f) + (bom.totalCabinetsCount * 45f)).coerceAtLeast(50f)
    val grandTotalWithLaborUsd = materialsCostUsd + accessoriesCostUsd + laborCostUsd

    val chartSegments = remember(materialsCostUsd, laborCostUsd, accessoriesCostUsd) {
        listOf(
            CostChartSegment(
                labelAr = "المواد (الألواح، الخزائن والرخام)",
                subLabelAr = String.format(
                    Locale.US,
                    "خزائن $%.0f + رخام $%.0f",
                    bom.cabinetsEstimatedCostUsd,
                    bom.countertopEstimatedCostUsd
                ),
                amountUsd = materialsCostUsd,
                color = Color(0xFF38BDF8)
            ),
            CostChartSegment(
                labelAr = "العمالة والتصنيع والتركيب",
                subLabelAr = "قص CNC + تجميع الورشة + تركيب موقعي (22%)",
                amountUsd = laborCostUsd,
                color = Color(0xFF10B981)
            ),
            CostChartSegment(
                labelAr = "الإكسسوارات الذكية والمفصلات",
                subLabelAr = "${bom.totalAccessoriesCount} طقم إكسسوارات ومفصلات هيدروليك",
                amountUsd = accessoriesCostUsd.coerceAtLeast(45f),
                color = Color(0xFFF59E0B)
            )
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A),
            tonalElevation = 12.dp,
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 720.dp)
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header + Metric/Imperial Switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "جدول الكميات والتحليل البياني للتكلفة (BOM & Cost Charts)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Text(
                            text = "المشروع: ${bom.projectName} | ${bom.generatedDate}",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }

                    // Unit System Switcher Chip
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0284C7).copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier
                            .testTag("bom_toggle_unit_system_button")
                            .clickable { onToggleUnitSystem() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "تبديل نظام القياس",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = unitSystem.badgeLabelAr,
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Summary Metrics (Dynamic Metric vs Imperial)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    BOMMetric(label = "إجمالي الخزائن", value = "${bom.totalCabinetsCount} وحدة")
                    BOMMetric(
                        label = if (unitSystem == MeasurementUnitSystem.METRIC) "الأمتار الطولية" else "الطول (قدم ft)",
                        value = MeasurementUnitSystem.formatLinearLength(bom.totalLinearMeters, unitSystem)
                    )
                    BOMMetric(
                        label = if (unitSystem == MeasurementUnitSystem.METRIC) "مساحة الرخام (م²)" else "الرخام (sq.ft)",
                        value = MeasurementUnitSystem.formatArea(bom.countertopAreaSquareMeters, unitSystem)
                    )
                    BOMMetric(
                        label = "الإكسسوارات الذكية",
                        value = "${bom.totalAccessoriesCount} طقم"
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Interactive Visual Cost Distribution Chart Card (Materials, Labor, Accessories)
                    item {
                        ProjectCostDistributionChartCard(
                            segments = chartSegments,
                            grandTotalUsd = grandTotalWithLaborUsd,
                            selectedMode = selectedChartMode,
                            onSelectMode = { selectedChartMode = it }
                        )
                    }

                    item {
                        Text(
                            text = "1. قائمة بنود الخزائن والتفاصيل الفنية (${bom.items.size} بند):",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Color(0xFF38BDF8)
                        )
                    }

                    items(bom.items) { item ->
                        val formattedDims = MeasurementUnitSystem.formatDimensions(
                            widthMm = item.widthMm,
                            heightMm = item.heightMm,
                            depthMm = item.depthMm,
                            unitSystem = unitSystem
                        )
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
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "المقاس: $formattedDims | الخامة: ${item.finishName}",
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF0284C7).copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, Color(0xFF0284C7))
                                ) {
                                    Text(
                                        text = "العدد: ${item.quantity}",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "2. إكسسوارات المطبخ الداخلية المرتبطة تلقائياً (${bom.matchedAccessories.size} نوع):",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = Color(0xFFFBBF24)
                            )
                            TextButton(
                                onClick = onOpenAccessoriesLibrary,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تخصيص المكتبة الذكية", color = Color(0xFFFBBF24), fontSize = 11.sp)
                            }
                        }
                    }

                    if (bom.matchedAccessories.isEmpty()) {
                        item {
                            Text(
                                text = "لا توجد إكسسوارات داخلية مفعّلة حالياً.",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                    } else {
                        items(bom.matchedAccessories) { matched ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f)),
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
                                            text = "${matched.accessory.arabicName} (${matched.accessory.code})",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = Color(0xFFFDE68A)
                                        )
                                        Text(
                                            text = "مرتبط بـ: ${matched.matchedCabinetNames.distinct().joinToString("، ")}",
                                            fontSize = 10.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "العدد: ${matched.quantity}",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFBBF24),
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = String.format(Locale.US, "$%.0f", matched.totalCostUsd),
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

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onOpenAccessoriesLibrary,
                        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                        modifier = Modifier.testTag("bom_open_accessories_button")
                    ) {
                        Text("مكتبة الإكسسوارات الذكية", color = Color(0xFFFBBF24), fontSize = 11.sp)
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_bom_button")
                    ) {
                        Text("إغلاق / Close", color = Color(0xFF38BDF8))
                    }
                }
            }
        }
    }
}

/**
 * Interactive Cost Distribution Chart Component (Donut Chart + Stacked Bar + Percentage Breakdown)
 * displaying Materials, Labor, and Accessories costs for the active kitchen project.
 */
@Composable
fun ProjectCostDistributionChartCard(
    segments: List<CostChartSegment>,
    grandTotalUsd: Float,
    selectedMode: Int,
    onSelectMode: (Int) -> Unit
) {
    val total = segments.sumOf { it.amountUsd.toDouble() }.toFloat().coerceAtLeast(1f)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF131D31),
        border = BorderStroke(1.dp, Color(0xFF1E3A8A)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cost_distribution_chart_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
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
                        imageVector = Icons.Default.PieChart,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "التحليل البياني لتوزيع تكاليف المشروع",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "المواد • العمالة والتركيب • الإكسسوارات الذكية",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                    }
                }

                // Switch between Donut Chart & Horizontal Bars
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = selectedMode == 0,
                        onClick = { onSelectMode(0) },
                        label = { Text("دائري Donut", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0284C7),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                    FilterChip(
                        selected = selectedMode == 1,
                        onClick = { onSelectMode(1) },
                        label = { Text("أعمدة Bars", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0284C7),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedMode == 0) {
                // Donut Chart + Legend side by side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier.size(122.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(112.dp)) {
                            val strokeW = 22f
                            var startAngle = -90f
                            segments.forEach { seg ->
                                val sweep = (seg.amountUsd / total) * 360f
                                drawArc(
                                    color = seg.color,
                                    startAngle = startAngle + 1.5f,
                                    sweepAngle = (sweep - 3f).coerceAtLeast(2f),
                                    useCenter = false,
                                    topLeft = Offset(strokeW / 2f, strokeW / 2f),
                                    size = Size(size.width - strokeW, size.height - strokeW),
                                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                                )
                                startAngle += sweep
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "الإجمالي",
                                color = Color(0xFF94A3B8),
                                fontSize = 9.sp
                            )
                            Text(
                                text = String.format(Locale.US, "$%.0f", grandTotalUsd),
                                color = Color(0xFF34D399),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Legend & Percentage Breakdown
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        segments.forEach { seg ->
                            val pct = ((seg.amountUsd / total) * 100f).roundToInt()
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1E293B),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(seg.color)
                                        )
                                        Column {
                                            Text(
                                                text = seg.labelAr,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = seg.subLabelAr,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = String.format(Locale.US, "$%.0f", seg.amountUsd),
                                            color = seg.color,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = "$pct%",
                                            color = Color(0xFFCBD5E1),
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Detailed Horizontal Bar Chart View
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    segments.forEach { seg ->
                        val ratio = (seg.amountUsd / total).coerceIn(0.04f, 1f)
                        val pct = (ratio * 100f).roundToInt()
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${seg.labelAr} ($pct%)",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = String.format(Locale.US, "$%.0f", seg.amountUsd),
                                    color = seg.color,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFF0F172A))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(ratio)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(seg.color)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Stacked Proportional Bar at Bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF0F172A))
            ) {
                segments.forEach { seg ->
                    val weightVal = (seg.amountUsd / total).coerceAtLeast(0.05f)
                    Box(
                        modifier = Modifier
                            .weight(weightVal)
                            .fillMaxHeight()
                            .background(seg.color)
                    )
                }
            }
        }
    }
}

@Composable
private fun BOMMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color(0xFF94A3B8), fontSize = 10.sp)
        Text(text = value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}
