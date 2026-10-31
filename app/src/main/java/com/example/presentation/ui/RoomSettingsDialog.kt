package com.example.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.domain.model.*

/**
 * Section 3: Dedicated Kitchen Room Dimensions & Details Screen
 * (شاشة مقاسات وتفاصيل غرف المطبخ المستقلة)
 * - Individual wall lengths & manual corner junction angles (L-Shape, U-Shape, Rectangular + معالجة عيوب البناء)
 * - Architectural openings (Windows & Doors) with active Window Safety Zones (blocking upper solid cabinets above window, allowing sink below)
 * - Manual MEP utility points (Plumbing/Drain, Gas, Ventilation, Electrical Sockets in the 60cm backsplash zone)
 */
@Composable
fun RoomSettingsDialog(
    room: RoomStructure,
    onUpdateDimensions: (Float, Float, Float) -> Unit,
    onAddOpening: (wallId: String, type: OpeningType, offset: Float, width: Float, height: Float, sill: Float) -> Unit,
    onRemoveOpening: (wallId: String, openingId: String) -> Unit,
    onUpdateIndividualWall: (wallId: String, lengthMm: Float, heightMm: Float?, cornerAngleDegrees: Float?, isEnabled: Boolean?) -> Unit = { _, _, _, _, _ -> },
    onApplyRoomLayoutShape: (RoomLayoutShape) -> Unit = {},
    onAddUtilityPoint: (wallId: String, type: UtilityPointType, offsetMm: Float, elevationMm: Float, label: String) -> Unit = { _, _, _, _, _ -> },
    onRemoveUtilityPoint: (wallId: String, utilityId: String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: أطوال الجدران وزوايا البناء, 1: النوافذ والأبواب ومناطق الأمان, 2: توزيع نقاط الخدمات (السباكة والغاز والفيش)
    var widthMm by remember(room.widthMm) { mutableFloatStateOf(room.widthMm) }
    var depthMm by remember(room.depthMm) { mutableFloatStateOf(room.depthMm) }
    var heightMm by remember(room.heightMm) { mutableFloatStateOf(room.heightMm) }
    var selectedWallSide by remember { mutableStateOf(WallSide.NORTH) }

    val currentWall = room.walls.find { it.side == selectedWallSide } ?: room.walls.firstOrNull()

    // Individual wall manual inputs (in cm for workshop convenience, converted to mm)
    var wallLengthCmText by remember(currentWall?.id, currentWall?.lengthMm) {
        mutableStateOf(((currentWall?.lengthMm ?: 4000f) / 10f).toInt().toString())
    }
    var wallCornerAngleText by remember(currentWall?.id, currentWall?.cornerAngleDegrees) {
        mutableStateOf(String.format(java.util.Locale.US, "%.1f", currentWall?.cornerAngleDegrees ?: 90f))
    }

    // Opening manual input fields (in cm)
    var openingType by remember { mutableStateOf(OpeningType.WINDOW) }
    var openingOffsetCmText by remember { mutableStateOf("140") }
    var openingWidthCmText by remember { mutableStateOf("120") }
    var openingHeightCmText by remember { mutableStateOf("110") }
    var openingSillCmText by remember { mutableStateOf("100") }

    // MEP Utility manual input fields (in cm)
    var selectedUtilityType by remember { mutableStateOf(UtilityPointType.ELECTRICAL_SOCKET) }
    var utilityOffsetCmText by remember { mutableStateOf("120") }
    var utilityElevationCmText by remember(selectedUtilityType) {
        mutableStateOf((selectedUtilityType.defaultElevationMm / 10f).toInt().toString())
    }
    var utilityLabelText by remember(selectedUtilityType) {
        mutableStateOf(selectedUtilityType.arabicTitle)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A),
            tonalElevation = 14.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "3. شاشة مقاسات وتفاصيل غرف المطبخ المستقلة",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                        Text(
                            text = "أطوال الجدران الفردية، زوايا معالجة عيوب البناء، مناطق أمان النوافذ، وتوزيع نقاط السباكة والغاز والفيش",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3 Section Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color.White
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.testTag("room_tab_walls"),
                        text = { Text("أطوال الجدران والزوايا", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.testTag("room_tab_openings"),
                        text = { Text("النوافذ والأبواب والأمان", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        modifier = Modifier.testTag("room_tab_utilities"),
                        text = { Text("نقاط الخدمات (الفيش والسباكة)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Wall Selector Strip (Shared across tabs)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("الجدار المحدد:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    WallSide.values().forEach { side ->
                        val wallObj = room.walls.find { it.side == side }
                        FilterChip(
                            selected = selectedWallSide == side,
                            onClick = { selectedWallSide = side },
                            label = {
                                Text(
                                    "${side.title} (${((wallObj?.lengthMm ?: 0f) / 10f).toInt()} سم | ${wallObj?.cornerAngleDegrees?.toInt() ?: 90}°)",
                                    fontSize = 10.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0284C7),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF1E293B),
                                labelColor = Color(0xFFCBD5E1)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            // 1. Kitchen Layout Shape Selector (L-Shape, U-Shape, Rectangular)
                            item {
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "شكل تصميم المطبخ (L-Shape / U-Shape / مستطيل):",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            RoomLayoutShape.values().forEach { shape ->
                                                FilterChip(
                                                    selected = room.layoutShape == shape,
                                                    onClick = { onApplyRoomLayoutShape(shape) },
                                                    label = { Text(shape.arabicTitle, fontSize = 11.sp) },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = Color(0xFF10B981),
                                                        selectedLabelColor = Color.White
                                                    ),
                                                    modifier = Modifier.testTag("room_shape_${shape.name}")
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. Individual Wall Length & Corner Junction Angle (معالجة عيوب البناء)
                            if (currentWall != null) {
                                item {
                                    Surface(
                                        color = Color(0xFF1E293B),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "ضبط الجدار الفردي: ${currentWall.side.title}",
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("تفعيل الجدار بالرسم", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Switch(
                                                        checked = currentWall.isEnabled,
                                                        onCheckedChange = { enabled ->
                                                            onUpdateIndividualWall(
                                                                currentWall.id,
                                                                currentWall.lengthMm,
                                                                currentWall.heightMm,
                                                                currentWall.cornerAngleDegrees,
                                                                enabled
                                                            )
                                                        }
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = wallLengthCmText,
                                                    onValueChange = { wallLengthCmText = it },
                                                    label = { Text("طول الجدار الفردي (سم)", fontSize = 11.sp) },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f).testTag("individual_wall_length_cm_input")
                                                )
                                                OutlinedTextField(
                                                    value = wallCornerAngleText,
                                                    onValueChange = { wallCornerAngleText = it },
                                                    label = { Text("زاوية الالتقاء (70°–135° لعيوب البناء)", fontSize = 11.sp) },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f).testTag("wall_corner_angle_input")
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Button(
                                                onClick = {
                                                    val lenMm = (wallLengthCmText.toFloatOrNull() ?: (currentWall.lengthMm / 10f)) * 10f
                                                    val angle = (wallCornerAngleText.toFloatOrNull() ?: currentWall.cornerAngleDegrees).coerceIn(70f, 135f)
                                                    onUpdateIndividualWall(
                                                        currentWall.id,
                                                        lenMm,
                                                        heightMm,
                                                        angle,
                                                        currentWall.isEnabled
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                                modifier = Modifier.fillMaxWidth().testTag("apply_individual_wall_btn")
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("تطبيق طول الجدار وزاوية معالجة عيوب البناء")
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Global Room Bounding Sliders
                            item {
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("أبعاد الغرفة الإجمالية والارتفاع العام:", color = Color(0xFF94A3B8), fontSize = 12.sp)

                                        DimensionSlider(
                                            label = "العرض الكلي (X): ${(widthMm / 10f).toInt()} سم (${widthMm.toInt()} مم)",
                                            value = widthMm,
                                            range = 1800f..8000f,
                                            onValueChange = { widthMm = it }
                                        )

                                        DimensionSlider(
                                            label = "العمق الكلي (Z): ${(depthMm / 10f).toInt()} سم (${depthMm.toInt()} مم)",
                                            value = depthMm,
                                            range = 1800f..7000f,
                                            onValueChange = { depthMm = it }
                                        )

                                        DimensionSlider(
                                            label = "ارتفاع السقف (Y): ${(heightMm / 10f).toInt()} سم (${heightMm.toInt()} مم)",
                                            value = heightMm,
                                            range = 2200f..3800f,
                                            onValueChange = { heightMm = it }
                                        )

                                        Button(
                                            onClick = { onUpdateDimensions(widthMm, depthMm, heightMm) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                            modifier = Modifier.fillMaxWidth().testTag("apply_room_dimensions")
                                        ) {
                                            Text("تطبيق أبعاد الغرفة / Apply Dimensions")
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // Tab 1: Windows & Doors + Active Window Safety Zone
                            item {
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFFF59E0B))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                "منطقة أمان الشباك المفعّلة تلقائياً (Window Safety Zone)",
                                                color = Color(0xFFFDE68A),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                "تمنع تركيب خزائن علوية مصمتة أمام أو فوق الشباك لحماية الإضاءة والتهوية، وتسمح بوضع حوض المجلى والخزائن السفلية أسفله مباشرة.",
                                                color = Color(0xFFCBD5E1),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }

                            if (currentWall != null) {
                                item {
                                    Surface(
                                        color = Color(0xFF1E293B),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                "إدخال يدوي لفتحة معمارية على ${currentWall.side.title}:",
                                                color = Color(0xFF38BDF8),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OpeningType.values().forEach { t ->
                                                    FilterChip(
                                                        selected = openingType == t,
                                                        onClick = {
                                                            openingType = t
                                                            if (t == OpeningType.DOOR) {
                                                                openingWidthCmText = "90"
                                                                openingHeightCmText = "210"
                                                                openingSillCmText = "0"
                                                            } else {
                                                                openingWidthCmText = "120"
                                                                openingHeightCmText = "110"
                                                                openingSillCmText = "100"
                                                            }
                                                        },
                                                        label = { Text(t.title, fontSize = 11.sp) }
                                                    )
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = openingOffsetCmText,
                                                    onValueChange = { openingOffsetCmText = it },
                                                    label = { Text("البعد من الزاوية (سم)", fontSize = 10.sp) },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                OutlinedTextField(
                                                    value = openingWidthCmText,
                                                    onValueChange = { openingWidthCmText = it },
                                                    label = { Text("العرض (سم)", fontSize = 10.sp) },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = openingHeightCmText,
                                                    onValueChange = { openingHeightCmText = it },
                                                    label = { Text("الارتفاع (سم)", fontSize = 10.sp) },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                OutlinedTextField(
                                                    value = openingSillCmText,
                                                    onValueChange = { openingSillCmText = it },
                                                    label = { Text("ارتفاع الجلسة عن الأرض (سم)", fontSize = 10.sp) },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        val offMm = (openingOffsetCmText.toFloatOrNull() ?: 120f) * 10f
                                                        val wMm = (openingWidthCmText.toFloatOrNull() ?: 100f) * 10f
                                                        val hMm = (openingHeightCmText.toFloatOrNull() ?: 110f) * 10f
                                                        val sillMm = (openingSillCmText.toFloatOrNull() ?: 0f) * 10f
                                                        onAddOpening(currentWall.id, openingType, offMm, wMm, hMm, sillMm)
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("+ إضافة بالمقاس اليدوي", fontSize = 11.sp)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        onAddOpening(currentWall.id, OpeningType.DOOR, 600f, 900f, 2100f, 0f)
                                                    },
                                                    modifier = Modifier.testTag("add_door_button")
                                                ) {
                                                    Text("+ باب 90سم", fontSize = 11.sp)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        onAddOpening(currentWall.id, OpeningType.WINDOW, 1500f, 1200f, 1100f, 1000f)
                                                    },
                                                    modifier = Modifier.testTag("add_window_button")
                                                ) {
                                                    Text("+ شباك 120سم", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }

                                items(currentWall.openings, key = { it.id }) { op ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "${op.type.title}: عرض ${(op.widthMm / 10f).toInt()} سم × ارتفاع ${(op.heightMm / 10f).toInt()} سم",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "البعد: ${(op.offsetMm / 10f).toInt()} سم | جلسة: ${(op.sillHeightMm / 10f).toInt()} سم" +
                                                        if (op.type == OpeningType.WINDOW) " | [منطقة أمان الشباك نشطة]" else "",
                                                color = Color(0xFF38BDF8),
                                                fontSize = 10.sp
                                            )
                                        }
                                        IconButton(onClick = { onRemoveOpening(currentWall.id, op.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFEF4444))
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            // Tab 2: Manual MEP Utility Points Distribution (Plumbing, Gas, Ventilation, Electrical Sockets in 60cm zone)
                            if (currentWall != null) {
                                item {
                                    Surface(
                                        color = Color(0xFF1E293B),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                "توزيع نقاط الخدمات يدوياً على ${currentWall.side.title}:",
                                                color = Color(0xFF34D399),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "تحديد دقيق لمواضع السباكة والصرف، الغاز، التهوية، ومقابس الكهرباء (الفيش) في منطقة الـ 60 سم الفاصلة (ارتفاع 85 إلى 145 سم) لضمان عدم تغطيتها بالخزائن.",
                                                color = Color(0xFFCBD5E1),
                                                fontSize = 11.sp
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                items(UtilityPointType.values()) { uType ->
                                                    FilterChip(
                                                        selected = selectedUtilityType == uType,
                                                        onClick = {
                                                            selectedUtilityType = uType
                                                            utilityElevationCmText = (uType.defaultElevationMm / 10f).toInt().toString()
                                                            utilityLabelText = uType.arabicTitle
                                                        },
                                                        label = { Text("${uType.codeBadge} - ${uType.arabicTitle}", fontSize = 10.sp) },
                                                        colors = FilterChipDefaults.filterChipColors(
                                                            selectedContainerColor = Color(0xFF0284C7),
                                                            selectedLabelColor = Color.White
                                                        ),
                                                        modifier = Modifier.testTag("utility_type_${uType.name}")
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = utilityOffsetCmText,
                                                    onValueChange = { utilityOffsetCmText = it },
                                                    label = { Text("البعد الأفقي عن بداية الجدار (سم)", fontSize = 10.sp) },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f).testTag("utility_offset_cm_input")
                                                )
                                                OutlinedTextField(
                                                    value = utilityElevationCmText,
                                                    onValueChange = { utilityElevationCmText = it },
                                                    label = {
                                                        Text(
                                                            if (selectedUtilityType.mustStayInBacksplash60cmZone)
                                                                "الارتفاع (منطقة الـ60سم: 85–145 سم)"
                                                            else "الارتفاع عن الأرض (سم)",
                                                            fontSize = 10.sp
                                                        )
                                                    },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f).testTag("utility_elevation_cm_input")
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Button(
                                                onClick = {
                                                    val offMm = (utilityOffsetCmText.toFloatOrNull() ?: 120f) * 10f
                                                    val elevMm = (utilityElevationCmText.toFloatOrNull() ?: (selectedUtilityType.defaultElevationMm / 10f)) * 10f
                                                    onAddUtilityPoint(
                                                        currentWall.id,
                                                        selectedUtilityType,
                                                        offMm,
                                                        elevMm,
                                                        utilityLabelText
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                                modifier = Modifier.fillMaxWidth().testTag("add_utility_point_btn")
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("إضافة نقطة خدمة (${selectedUtilityType.arabicTitle}) على المخطط")
                                            }
                                        }
                                    }
                                }

                                items(currentWall.utilityPoints, key = { it.id }) { pt ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(pt.type.colorHex), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "[${pt.type.codeBadge}] ${pt.label.ifBlank { pt.type.arabicTitle }}",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "البعد الأفقي: ${pt.offsetCm.toInt()} سم | الارتفاع: ${pt.elevationCm.toInt()} سم" +
                                                        if (pt.isInBacksplash60cmZone) " (ضمن منطقة الـ 60 سم الفاصلة الآمنة)" else "",
                                                color = Color(0xFF34D399),
                                                fontSize = 11.sp
                                            )
                                        }
                                        IconButton(onClick = { onRemoveUtilityPoint(currentWall.id, pt.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFEF4444))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Text("إغلاق وحفظ الإعدادات / Done", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun DimensionSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = label, color = Color.White, fontSize = 11.sp)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF38BDF8),
                activeTrackColor = Color(0xFF0284C7),
                inactiveTrackColor = Color(0xFF334155)
            )
        )
    }
}
