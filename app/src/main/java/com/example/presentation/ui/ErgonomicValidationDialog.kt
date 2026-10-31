package com.example.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.geometry.ErgonomicIssue
import com.example.core.geometry.ErgonomicValidationReport
import com.example.core.geometry.ValidationSeverity
import com.example.core.geometry.WorkTriangleMetrics
import kotlin.math.roundToInt

/**
 * Interactive floating status pill displayed on the studio canvas showing real-time
 * ergonomic score and live counts of flagged design issues.
 */
@Composable
fun ErgonomicStatusChip(
    report: ErgonomicValidationReport,
    showOverlayOnCanvas: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val badgeColor = when {
        report.errorCount > 0 -> Color(0xFFDC2626)
        report.warningCount > 0 -> Color(0xFFD97706)
        else -> Color(0xFF059669)
    }

    val statusLabel = when {
        report.errorCount > 0 -> "أخطاء إرجونومية (${report.errorCount + report.warningCount})"
        report.warningCount > 0 -> "تنبيهات التصميم (${report.warningCount})"
        else -> "التصميم متوافق إرجونومياً (${report.score}%)"
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .testTag("ergonomic_status_chip"),
        color = Color(0xEB111827),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, badgeColor.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (report.errorCount == 0 && report.warningCount == 0) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.Warning
                    },
                    contentDescription = "حالة الفحص الإرجونومي",
                    tint = badgeColor,
                    modifier = Modifier.size(15.dp)
                )
            }

            Column {
                Text(
                    text = statusLabel,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                val subText = if (report.workTriangle != null) {
                    val sinkStoveCm = (report.workTriangle.sinkToStoveCenterMm / 10f).roundToInt()
                    "الحوض↔الموقد: $sinkStoveCm سم • التقييم: ${report.score}/100"
                } else {
                    "فحص الخلوص ومثلث العمل المباشر • ${report.score}/100"
                }
                Text(
                    text = subText,
                    color = if (showOverlayOnCanvas) Color(0xFF93C5FD) else Color(0xFF9CA3AF),
                    fontSize = 9.sp
                )
            }
        }
    }
}

/**
 * Full diagnostic dialog listing real-time ergonomic validation findings,
 * Kitchen Work Triangle measurements, and Cabinet Door Clearance checks.
 */
@Composable
fun ErgonomicValidationDialog(
    report: ErgonomicValidationReport,
    showClearanceOverlay: Boolean,
    onToggleClearanceOverlay: (Boolean) -> Unit,
    onFocusIssueElements: (ErgonomicIssue) -> Unit,
    onAutoFixIssue: (ErgonomicIssue) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .testTag("ergonomic_validation_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
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
                        val scoreColor = when {
                            report.score >= 85 -> Color(0xFF059669)
                            report.score >= 65 -> Color(0xFFD97706)
                            else -> Color(0xFFDC2626)
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(scoreColor.copy(alpha = 0.15f))
                                .border(2.dp, scoreColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${report.score}",
                                color = scoreColor,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                        }
                        Column {
                            Text(
                                text = "محرك الفحص الإرجونومي اللحظي (NKBA)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Real-Time Ergonomic & Clearance Validation Engine",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_ergonomic_dialog_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Toggle for visual 2D overlay of work triangle & door clearance zones
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "إظهار مثلث العمل ونطاق فتح الأبواب على المخطط",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "يرسم خطوط المسافات بين الحوض والموقد والثلاجة وقوس فتح الضلف في 2D",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showClearanceOverlay,
                            onCheckedChange = onToggleClearanceOverlay,
                            modifier = Modifier.testTag("toggle_ergonomic_overlay_switch")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Work Triangle Summary Card
                report.workTriangle?.let { triangle ->
                    WorkTriangleSummaryCard(triangle)
                    Spacer(modifier = Modifier.height(10.dp))
                }

                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // Issues List Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "نتائج الفحص المعماري (${report.issues.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SeverityCounterPill("أخطاء: ${report.errorCount}", Color(0xFFDC2626))
                        SeverityCounterPill("تنبيهات: ${report.warningCount}", Color(0xFFD97706))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (report.issues.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF059669).copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(32.dp)
                            )
                            Column {
                                Text(
                                    text = "التصميم خالٍ من التعارضات الإرجونومية!",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF059669)
                                )
                                Text(
                                    text = "جميع مسافات مثلث العمل (الحوض - الموقد - الثلاجة) وخلوص فتح أبواب الخزائن والممرات مطابقة للمعايير القياسية.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(report.issues, key = { it.id }) { issue ->
                            ErgonomicIssueCard(
                                issue = issue,
                                onFocusClick = { onFocusIssueElements(issue) },
                                onAutoFixClick = { onAutoFixIssue(issue) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkTriangleSummaryCard(triangle: WorkTriangleMetrics) {
    val sinkStoveCm = (triangle.sinkToStoveCenterMm / 10f).roundToInt()
    val isOptimal = triangle.isSinkStoveOptimal && triangle.isTriangleOptimal
    val borderColor = if (isOptimal) Color(0xFF059669) else Color(0xFFD97706)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "قياسات مثلث العمل (Work Triangle)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isOptimal) "ضمن النطاق القياسي ✓" else "يتطلب مراجعة ⚠",
                    color = borderColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn(
                    label = "الحوض ↔ الموقد",
                    value = "$sinkStoveCm سم (${triangle.sinkToStoveCenterMm.roundToInt()} ملم)",
                    sub = "القياسي: 120–270 سم",
                    isOk = triangle.isSinkStoveOptimal
                )
                triangle.sinkToFridgeCenterMm?.let { s2f ->
                    MetricColumn(
                        label = "الحوض ↔ الثلاجة",
                        value = "${(s2f / 10f).roundToInt()} سم",
                        sub = "القياسي: 120–270 سم",
                        isOk = s2f in 1000f..2900f
                    )
                }
                triangle.totalPerimeterMm?.let { perim ->
                    MetricColumn(
                        label = "محيط المثلث الكلي",
                        value = "${String.format(java.util.Locale.US, "%.2f", perim / 1000f)} م",
                        sub = "القياسي: 3.4–7.9 م",
                        isOk = triangle.isTriangleOptimal
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
    sub: String,
    isOk: Boolean
) {
    Column {
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isOk) Color(0xFF059669) else Color(0xFFD97706)
        )
        Text(
            text = sub,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SeverityCounterPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ErgonomicIssueCard(
    issue: ErgonomicIssue,
    onFocusClick: () -> Unit,
    onAutoFixClick: () -> Unit
) {
    val accentColor = when (issue.severity) {
        ValidationSeverity.ERROR -> Color(0xFFDC2626)
        ValidationSeverity.WARNING -> Color(0xFFD97706)
        ValidationSeverity.INFO -> Color(0xFF2563EB)
    }

    val icon = when (issue.severity) {
        ValidationSeverity.ERROR -> Icons.Default.Warning
        ValidationSeverity.WARNING -> Icons.Default.Warning
        ValidationSeverity.INFO -> Icons.Default.Info
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .testTag("ergonomic_issue_card_${issue.code.name.lowercase()}"),
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.06f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = issue.severity.labelAr,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = issue.titleAr,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = issue.titleEn,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.16f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = issue.standardRangeLabel,
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = issue.descriptionAr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "💡 التوصية: ${issue.recommendationAr}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = accentColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onFocusClick,
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("focus_issue_button_${issue.code.name.lowercase()}"),
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تحديد الخزانة", fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onAutoFixClick,
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("autofix_issue_button_${issue.code.name.lowercase()}"),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تصحيح تلقائي", fontSize = 11.sp)
                }
            }
        }
    }
}
