package com.example.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.ai.AiDesignUiState
import com.example.core.ai.KitchenAiStylePreference
import com.example.core.units.MeasurementUnitSystem
import com.example.domain.model.RoomLayoutShape
import com.example.domain.model.RoomStructure
import com.example.domain.repository.KitchenProject

@Composable
fun GeminiKitchenDesignDialog(
    currentRoom: RoomStructure,
    unitSystem: MeasurementUnitSystem,
    aiState: AiDesignUiState,
    onGenerateDesign: (
        widthMm: Float,
        depthMm: Float,
        heightMm: Float,
        layoutShape: RoomLayoutShape,
        style: KitchenAiStylePreference,
        includeIsland: Boolean,
        customNotes: String
    ) -> Unit,
    onApplyGeneratedProject: (KitchenProject) -> Unit,
    onDismiss: () -> Unit
) {
    var widthInput by remember(currentRoom.widthMm) { mutableStateOf(currentRoom.widthMm.toInt().toString()) }
    var depthInput by remember(currentRoom.depthMm) { mutableStateOf(currentRoom.depthMm.toInt().toString()) }
    var heightInput by remember(currentRoom.heightMm) { mutableStateOf(currentRoom.heightMm.toInt().toString()) }
    var selectedShape by remember(currentRoom.layoutShape) { mutableStateOf(currentRoom.layoutShape) }
    var selectedStyle by remember { mutableStateOf(KitchenAiStylePreference.ROYAL_OAK_AND_CHARCOAL) }
    var includeIsland by remember { mutableStateOf(currentRoom.widthMm >= 4200f && currentRoom.depthMm >= 3600f) }
    var customNotes by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF0F172A),
            tonalElevation = 16.dp,
            border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 720.dp)
                .padding(6.dp)
                .testTag("gemini_ai_designer_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF7C3AED).copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, Color(0xFFA78BFA))
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Gemini AI",
                                tint = Color(0xFFC4B5FD),
                                modifier = Modifier.padding(8.dp).size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "مهندس المطابخ الذكي بـ Gemini AI",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                            Text(
                                text = "اقتراح تصميم مطبخ متكامل ثلاثي الأبعاد حسب أبعاد غرفتك والنمط المفضل",
                                fontSize = 11.sp,
                                color = Color(0xFFA78BFA)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8))
                    }
                }

                // 1. Room Dimensions Input
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val wMm = widthInput.toFloatOrNull() ?: currentRoom.widthMm
                        val dMm = depthInput.toFloatOrNull() ?: currentRoom.depthMm
                        val hMm = heightInput.toFloatOrNull() ?: currentRoom.heightMm
                        Text(
                            text = "1. أبعاد الغرفة بالمليمتر (${MeasurementUnitSystem.formatLength(wMm, unitSystem)} × ${MeasurementUnitSystem.formatLength(dMm, unitSystem)}):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF38BDF8)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = widthInput,
                                onValueChange = { widthInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("العرض X (مم)", fontSize = 10.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("ai_room_width_input")
                            )
                            OutlinedTextField(
                                value = depthInput,
                                onValueChange = { depthInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("العمق Z (مم)", fontSize = 10.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("ai_room_depth_input")
                            )
                            OutlinedTextField(
                                value = heightInput,
                                onValueChange = { heightInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("الارتفاع Y (مم)", fontSize = 10.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("ai_room_height_input")
                            )
                        }

                        // Layout Shape Selector
                        Text(
                            text = "شكل التوزيع المعماري للغرفة:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            RoomLayoutShape.entries.forEach { shape ->
                                val isSel = selectedShape == shape
                                FilterChip(
                                    selected = isSel,
                                    onClick = { selectedShape = shape },
                                    label = { Text(shape.titleAr, fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "تضمين جزيرة مطبخ وسطية / بار إفطار (إن سمحت المساحة)",
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.sp
                            )
                            Switch(
                                checked = includeIsland,
                                onCheckedChange = { includeIsland = it }
                            )
                        }
                    }
                }

                // 2. Style Preferences
                Text(
                    text = "2. اختر تفضيلات النمط المعماري والخامات:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFFC4B5FD)
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    KitchenAiStylePreference.entries.forEach { style ->
                        val isSelected = selectedStyle == style
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF4C1D95).copy(alpha = 0.45f) else Color(0xFF1E293B),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) Color(0xFFA78BFA) else Color(0xFF334155)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedStyle = style }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Color(style.defaultCabinetMaterial.colorHex), CircleShape)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = style.arabicTitle,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = style.subtitleAr,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "محدد",
                                        tint = Color(0xFFA78BFA)
                                    )
                                }
                            }
                        }
                    }
                }

                // Optional custom notes
                OutlinedTextField(
                    value = customNotes,
                    onValueChange = { customNotes = it },
                    label = { Text("ملاحظات إضافية لـ Gemini (اختياري: مثل زيادة الأدراج أو فيترينة زجاجية)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("ai_custom_notes_input")
                )

                // Generate Button
                Button(
                    onClick = {
                        val w = (widthInput.toFloatOrNull() ?: currentRoom.widthMm).coerceIn(2400f, 8000f)
                        val d = (depthInput.toFloatOrNull() ?: currentRoom.depthMm).coerceIn(2200f, 8000f)
                        val h = (heightInput.toFloatOrNull() ?: currentRoom.heightMm).coerceIn(2400f, 3600f)
                        onGenerateDesign(w, d, h, selectedShape, selectedStyle, includeIsland, customNotes)
                    },
                    enabled = aiState !is AiDesignUiState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("generate_gemini_kitchen_button")
                ) {
                    if (aiState is AiDesignUiState.Loading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("جاري هندسة المطبخ عبر Gemini 3.8 Flash...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("توليد تصميم مطبخ متكامل بالذكاء الاصطناعي (Gemini AI)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // AI Result / Error Display
                when (aiState) {
                    is AiDesignUiState.Idle -> {}
                    is AiDesignUiState.Loading -> {}
                    is AiDesignUiState.Error -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF7F1D1D).copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, Color(0xFFEF4444)),
                            modifier = Modifier.fillMaxWidth().testTag("gemini_ai_error_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFCA5A5))
                                Text(
                                    text = aiState.message,
                                    color = Color(0xFFFECACA),
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    is AiDesignUiState.Success -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF064E3B).copy(alpha = 0.4f),
                            border = BorderStroke(1.5.dp, Color(0xFF10B981)),
                            modifier = Modifier.fillMaxWidth().testTag("gemini_ai_success_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "✨ التصميم المقترح: ${aiState.proposalTitle}",
                                    color = Color(0xFF34D399),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = aiState.architecturalSummaryAr,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 11.sp
                                )
                                if (aiState.ergonomicNotesAr.isNotBlank()) {
                                    Text(
                                        text = "📐 مثلث العمل ومعايير التركيب: ${aiState.ergonomicNotesAr}",
                                        color = Color(0xFFBAE6FD),
                                        fontSize = 11.sp
                                    )
                                }
                                if (aiState.lightingRecommendationAr.isNotBlank()) {
                                    Text(
                                        text = "💡 توصيات الإضاءة: ${aiState.lightingRecommendationAr}",
                                        color = Color(0xFFFDE68A),
                                        fontSize = 11.sp
                                    )
                                }
                                if (aiState.suggestedAccessoriesAr.isNotEmpty()) {
                                    Text(
                                        text = "🔩 الإكسسوارات الداخلية المقترحة: ${aiState.suggestedAccessoriesAr.joinToString(" • ")}",
                                        color = Color(0xFFC4B5FD),
                                        fontSize = 10.sp
                                    )
                                }
                                Text(
                                    text = "عدد الوحدات المولّدة: ${aiState.generatedProject.elements.size} وحدة ثلاثية الأبعاد جاهزة للتركيب",
                                    color = Color(0xFF6EE7B7),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Button(
                                    onClick = {
                                        onApplyGeneratedProject(aiState.generatedProject)
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("apply_gemini_design_button")
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "تطبيق التصميم فوراً في بيئة 3D / 2D",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF064E3B)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
