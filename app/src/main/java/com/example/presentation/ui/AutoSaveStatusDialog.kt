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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import com.example.data.local.autosave.AutoSaveStatus
import com.example.data.local.autosave.AutoSaveUiState
import com.example.data.local.autosave.ProjectAutoSaveManager
import com.example.data.local.entity.AutoSaveSnapshotEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compact floating badge showing real-time Room auto-save status and interval.
 */
@Composable
fun AutoSaveStatusBadge(
    autoSaveState: AutoSaveUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dotColor = when (autoSaveState.status) {
        AutoSaveStatus.SAVED, AutoSaveStatus.IDLE -> Color(0xFF10B981)
        AutoSaveStatus.SAVING, AutoSaveStatus.PENDING_CHANGES -> Color(0xFF38BDF8)
        AutoSaveStatus.PAUSED -> Color(0xFF9CA3AF)
        AutoSaveStatus.ERROR -> Color(0xFFEF4444)
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .testTag("autosave_status_badge"),
        color = Color(0xEB111827),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, dotColor.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Icon(
                imageVector = if (autoSaveState.status == AutoSaveStatus.SAVING) {
                    Icons.Default.Sync
                } else {
                    Icons.Default.Save
                },
                contentDescription = "حالة الحفظ التلقائي",
                tint = dotColor,
                modifier = Modifier.size(14.dp)
            )
            Column {
                Text(
                    text = autoSaveState.status.labelAr,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (autoSaveState.isEnabled) {
                        "دوري كل ${autoSaveState.intervalSeconds}ث (القادم: ${autoSaveState.secondsUntilNextSave}ث) • ${autoSaveState.lastSavedFormattedTime}"
                    } else {
                        "الحفظ الدوري متوقف مؤقتاً"
                    },
                    color = Color(0xFF9CA3AF),
                    fontSize = 8.5.sp
                )
            }
        }
    }
}

/**
 * Dialog for configuring periodic Room auto-save intervals, triggering manual checkpoints,
 * and restoring serialized snapshots from the local SQLite database.
 */
@Composable
fun AutoSaveStatusDialog(
    autoSaveState: AutoSaveUiState,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectIntervalSeconds: (Int) -> Unit,
    onSaveCheckpointNow: () -> Unit,
    onRestoreSnapshot: (AutoSaveSnapshotEntity) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 660.dp)
                .testTag("autosave_status_dialog"),
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
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "نظام الحفظ التلقائي المحلي (Room Auto-Save)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "حفظ دوري لأبعاد الغرفة ومواقع الخزائن وإحداثيات الكاميرا بدون إنترنت",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_autosave_dialog_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Enable / Disable + Interval Picker Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "تفعيل الحفظ التلقائي الدوري في قاعدة بيانات Room",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "عدد عمليات الحفظ في الجلسة: ${autoSaveState.totalAutoSaveCount} • آخر حفظ: ${autoSaveState.lastSavedFormattedTime}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoSaveState.isEnabled,
                                onCheckedChange = onToggleEnabled,
                                modifier = Modifier.testTag("autosave_enable_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "الفاصل الزمني:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ProjectAutoSaveManager.AVAILABLE_INTERVAL_SECONDS.forEach { sec ->
                                    FilterChip(
                                        selected = autoSaveState.intervalSeconds == sec,
                                        onClick = { onSelectIntervalSeconds(sec) },
                                        label = { Text("$sec ث", fontSize = 11.sp) },
                                        modifier = Modifier.testTag("autosave_interval_chip_$sec")
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Manual Checkpoint Save Button
                Button(
                    onClick = onSaveCheckpointNow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("autosave_save_now_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("حفظ نقطة استعادة فورية الآن (Serialize & Save Now)", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "سجل نقاط الحفظ التلقائي المخزنة (${autoSaveState.recentSnapshots.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (autoSaveState.recentSnapshots.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                            Text(
                                text = "سيتم تسجيل نقاط الحفظ التلقائي تباعاً أثناء التعديل أو عند انتهاء الفاصل الزمني.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(autoSaveState.recentSnapshots, key = { it.snapshotId }) { snap ->
                            AutoSaveSnapshotRow(
                                snapshot = snap,
                                onRestore = { onRestoreSnapshot(snap) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoSaveSnapshotRow(
    snapshot: AutoSaveSnapshotEntity,
    onRestore: () -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(snapshot.savedAtTimestamp))
    val kbSize = String.format(Locale.US, "%.1f KB", snapshot.payloadSizeBytes / 1024f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("autosave_snapshot_item_${snapshot.snapshotId}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${snapshot.projectName} • ${snapshot.furnitureCount} وحدة",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "الغرفة: ${snapshot.roomWidthMm.toInt()}×${snapshot.roomDepthMm.toInt()}×${snapshot.roomHeightMm.toInt()} ملم • $kbSize",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "التوقيت: $formattedDate • ${snapshot.triggerType} • CRC: ${snapshot.stateChecksumCrc32.toString(16).uppercase()}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedButton(
                onClick = onRestore,
                modifier = Modifier
                    .height(34.dp)
                    .testTag("restore_snapshot_button_${snapshot.snapshotId}"),
                contentPadding = ButtonDefaults.TextButtonContentPadding
            ) {
                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("استعادة", fontSize = 11.sp)
            }
        }
    }
}
