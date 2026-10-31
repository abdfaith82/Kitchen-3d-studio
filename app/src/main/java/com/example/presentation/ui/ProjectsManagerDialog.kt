package com.example.presentation.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.ProjectMetadataEntity
import com.example.presentation.viewmodel.KitchenEditorViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsManagerDialog(
    viewModel: KitchenEditorViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedProjects by viewModel.savedProjects.collectAsStateWithLifecycle()

    var newProjectName by remember { mutableStateOf("") }
    var projectToDeleteId by remember { mutableStateOf<String?>(null) }
    var showNewBlankConfirmation by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(20.dp))
                .testTag("projects_manager_dialog"),
            color = Color(0xFF0F172A)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
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
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.2f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.FolderSpecial,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "إدارة المشاريع والتخزين المحلي (Room)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            Text(
                                text = "حفظ واسترجاع مشاريع المطابخ محلياً 100% بدون إنترنت + تصدير JSON",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_projects_manager_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Content
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Section 1: Current Active Project
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF0284C7).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF10B981).copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "المشروع النشط حالياً",
                                                color = Color(0xFF34D399),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Text(
                                            text = uiState.project.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = Color.White
                                        )
                                    }

                                    // Quick Export JSON Button
                                    Button(
                                        onClick = {
                                            viewModel.exportAndShareProjectJson(context)
                                            Toast.makeText(context, "جاري تصدير ومشاركة ملف JSON...", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("export_current_json_btn")
                                    ) {
                                        Icon(
                                            Icons.Default.Share,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("تصدير JSON", fontSize = 12.sp, color = Color.White)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Dimensions and stats chips
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF334155),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("أبعاد الغرفة", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                            Text(
                                                "${(uiState.project.room.widthMm / 1000f)}×${(uiState.project.room.depthMm / 1000f)}×${(uiState.project.room.heightMm / 1000f)} م",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF334155),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("عدد العناصر", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                            Text(
                                                "${uiState.project.elements.size} عنصر / خزانة",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF38BDF8)
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF334155),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("الحفظ التلقائي", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                            Text(
                                                if (uiState.autoSaveState.status == com.example.data.local.autosave.AutoSaveStatus.SAVING) "جاري الحفظ..." else "محفوظ محلياً",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF34D399)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Save As New Project
                                Text(
                                    text = "حفظ التصميم الحالي كنسخة جديدة في قاعدة البيانات:",
                                    fontSize = 12.sp,
                                    color = Color(0xFFCBD5E1)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = newProjectName,
                                        onValueChange = { newProjectName = it },
                                        placeholder = { Text("أدخل اسم المشروع الجديد...", color = Color(0xFF64748B), fontSize = 13.sp) },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("new_project_name_field"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF0284C7),
                                            unfocusedBorderColor = Color(0xFF475569),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    )

                                    Button(
                                        onClick = {
                                            val name = if (newProjectName.isNotBlank()) newProjectName else "مطبخ جديد ${dateFormat.format(Date())}"
                                            viewModel.saveCurrentProjectAs(name)
                                            newProjectName = ""
                                            Toast.makeText(context, "تم حفظ المشروع '$name' في Room بنجاح!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .height(52.dp)
                                            .testTag("save_as_new_project_btn")
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("حفظ باسم", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Actions row: New Project & Trigger Checkpoint
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showNewBlankConfirmation = true },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF59E0B)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("create_new_blank_project_btn")
                                    ) {
                                        Icon(Icons.Default.AddBox, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("مشروع فارغ جديد", fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            viewModel.triggerImmediateAutoSave()
                                            Toast.makeText(context, "تم حفظ نقطة استرجاع (Snapshot) فورية!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("save_checkpoint_btn")
                                    ) {
                                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("حفظ نقطة استرجاع", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Section 2: Saved Projects List Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Storage, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                                Text(
                                    text = "المشاريع المحفوظة محلياً (${savedProjects.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "مخزنة في SQLite Room",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    // Section 3: Saved Projects Items
                    if (savedProjects.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E293B).copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.CloudQueue, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(40.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("لا توجد مشاريع سابقة محفوظة بعد", color = Color(0xFF94A3B8), fontSize = 14.sp)
                                    Text("احفظ مشروعك الحالي بواسطة الزر أعلاه للرجوع إليه دون إنترنت في أي وقت", color = Color(0xFF64748B), fontSize = 11.sp)
                                }
                            }
                        }
                    } else {
                        items(savedProjects, key = { it.id }) { projectMeta ->
                            val isCurrentlyActive = projectMeta.id == uiState.project.id
                            SavedProjectCard(
                                projectMeta = projectMeta,
                                isCurrentlyActive = isCurrentlyActive,
                                formattedDate = dateFormat.format(Date(projectMeta.updatedAt)),
                                onLoad = {
                                    viewModel.loadSavedProject(projectMeta.id)
                                    Toast.makeText(context, "تم فتح المشروع '${projectMeta.name}'", Toast.LENGTH_SHORT).show()
                                },
                                onExportJson = {
                                    viewModel.exportAndShareProjectJson(context, projectMeta.id)
                                    Toast.makeText(context, "جاري تصدير المشروع '${projectMeta.name}' كملف JSON...", Toast.LENGTH_SHORT).show()
                                },
                                onDelete = {
                                    projectToDeleteId = projectMeta.id
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (projectToDeleteId != null) {
        val targetProj = savedProjects.find { it.id == projectToDeleteId }
        AlertDialog(
            onDismissRequest = { projectToDeleteId = null },
            title = { Text("تأكيد حذف المشروع", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Text(
                    "هل أنت متأكد من حذف المشروع '${targetProj?.name ?: ""}' نهائياً من قاعدة البيانات المحلية؟ لن يمكن التراجع عن هذه العملية.",
                    color = Color(0xFFCBD5E1)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        projectToDeleteId?.let { id ->
                            viewModel.deleteSavedProject(id)
                            Toast.makeText(context, "تم حذف المشروع بنجاح", Toast.LENGTH_SHORT).show()
                        }
                        projectToDeleteId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("حذف نهائي", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDeleteId = null }) {
                    Text("إلغاء", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // New Blank Project Confirmation
    if (showNewBlankConfirmation) {
        AlertDialog(
            onDismissRequest = { showNewBlankConfirmation = false },
            title = { Text("بدء مشروع جديد فارغ", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Text(
                    "سيتم تهيئة مساحة تصميم فارغة بأبعاد الغرفة الحالية. تأكد من حفظ مشروعك الحالي أولاً إذا أردت الاحتفاظ به.",
                    color = Color(0xFFCBD5E1)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createNewBlankProject("تصميم مطبخ جديد ${dateFormat.format(Date())}")
                        showNewBlankConfirmation = false
                        Toast.makeText(context, "تم إنشاء مساحة تصميم فارغة جديدة", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Text("إنشاء تصميم جديد", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewBlankConfirmation = false }) {
                    Text("إلغاء", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}

@Composable
private fun SavedProjectCard(
    projectMeta: ProjectMetadataEntity,
    isCurrentlyActive: Boolean,
    formattedDate: String,
    onLoad: () -> Unit,
    onExportJson: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentlyActive) Color(0xFF1E293B) else Color(0xFF131D31)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isCurrentlyActive) Color(0xFF0284C7) else Color(0xFF334155),
                RoundedCornerShape(12.dp)
            )
            .testTag("saved_project_item_${projectMeta.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = projectMeta.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isCurrentlyActive) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF0284C7).copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = "مفتوح الآن",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "آخر تحديث: $formattedDate • ${(projectMeta.roomWidthMm / 1000f)}×${(projectMeta.roomDepthMm / 1000f)}×${(projectMeta.roomHeightMm / 1000f)} م",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                // Actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Export JSON button
                    IconButton(
                        onClick = onExportJson,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("export_project_json_btn_${projectMeta.id}")
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "تصدير كملف JSON",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Delete button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("delete_project_btn_${projectMeta.id}")
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "حذف المشروع",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Load Project button
                    Button(
                        onClick = onLoad,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCurrentlyActive) Color(0xFF334155) else Color(0xFF0284C7)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("load_project_btn_${projectMeta.id}")
                    ) {
                        Icon(
                            if (isCurrentlyActive) Icons.Default.Check else Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isCurrentlyActive) "مفتوح" else "فتح",
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
