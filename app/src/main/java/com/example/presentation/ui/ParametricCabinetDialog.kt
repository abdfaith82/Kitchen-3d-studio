package com.example.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.cam.DoorStyle
import com.example.core.cam.HandleStyle
import com.example.core.cam.ParametricCabinet
import com.example.core.filament.CabinetAssetLibrary
import com.example.core.filament.CabinetMeshPartRole
import com.example.core.filament.CabinetStyleArchetype
import com.example.domain.model.KitchenElement
import com.example.domain.model.MaterialFinish
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

@Composable
fun ParametricCabinetDialog(
    initialElement: KitchenElement?,
    onApplyCabinet: (ParametricCabinet) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedArchetype by remember { mutableStateOf(CabinetStyleArchetype.MODERN_SLAB) }
    val archetypePresets = remember(selectedArchetype) {
        CabinetAssetLibrary.getPresetsByArchetype(selectedArchetype)
    }

    var widthMm by remember { mutableFloatStateOf(initialElement?.dimensions?.x ?: 600f) }
    var heightMm by remember { mutableFloatStateOf(initialElement?.dimensions?.y ?: 850f) }
    var depthMm by remember { mutableFloatStateOf(initialElement?.dimensions?.z ?: 600f) }
    var toeKickMm by remember {
        mutableFloatStateOf(if ((initialElement?.position?.y ?: 0f) >= 800f) 0f else 100f)
    }

    var shelvesCount by remember { mutableIntStateOf(if (initialElement?.name?.contains("أدراج") == true) 0 else 1) }
    var drawersCount by remember { mutableIntStateOf(if (initialElement?.name?.contains("أدراج") == true) 3 else 0) }

    var doorStyle by remember { mutableStateOf(DoorStyle.SLAB_MODERN) }
    var handleStyle by remember { mutableStateOf(HandleStyle.BAR_PULL) }
    var facadeMaterial by remember { mutableStateOf(initialElement?.material ?: MaterialFinish.NATURAL_OAK) }
    var includeCountertop by remember { mutableStateOf((initialElement?.position?.y ?: 0f) < 200f && heightMm <= 950f) }
    var hasInternalLed by remember { mutableStateOf(doorStyle == DoorStyle.ALUMINUM_GLASS) }
    var hasGlassMullions by remember { mutableStateOf(false) }
    var hasClassicCrown by remember { mutableStateOf(false) }
    var cabinetName by remember { mutableStateOf(initialElement?.name ?: "خزانة مخصصة إجرائياً") }

    // Section 4: Interactive Cabinet Backing System (نعم يوجد ظهر / لا يوجد ظهر + تفريغ مجسم الـ 3D فوراً)
    var hasBackPanel by remember { mutableStateOf(initialElement?.hasBackPanel ?: true) }
    var backPanelMaterialType by remember { mutableStateOf(initialElement?.backPanelMaterialType ?: "MDF 6mm") }
    var backGrooveInsetMmText by remember { mutableStateOf((initialElement?.backGrooveInsetMm ?: 10f).toInt().toString()) }
    var boardThicknessMmText by remember { mutableStateOf((initialElement?.customBoardThicknessMm ?: 18f).toInt().toString()) }
    var topStretcherWidthMmText by remember { mutableStateOf((initialElement?.topStretcherWidthMm ?: 100f).toInt().toString()) }
    var aluminumProfileWidthMmText by remember { mutableStateOf((initialElement?.customAluminumProfileMm ?: 45f).toInt().toString()) }

    val parsedGrooveInset = backGrooveInsetMmText.toFloatOrNull() ?: 10f
    val parsedBoardThickness = boardThicknessMmText.toFloatOrNull() ?: 18f
    val parsedStretcherWidth = topStretcherWidthMmText.toFloatOrNull() ?: 100f
    val parsedAluminumWidth = aluminumProfileWidthMmText.toFloatOrNull() ?: 45f

    val parametricModel = remember(
        widthMm, heightMm, depthMm, toeKickMm,
        shelvesCount, drawersCount, doorStyle, handleStyle, facadeMaterial, cabinetName,
        hasBackPanel, backPanelMaterialType, parsedGrooveInset, parsedBoardThickness, parsedStretcherWidth, parsedAluminumWidth
    ) {
        ParametricCabinet(
            id = initialElement?.id ?: "param_cab",
            name = cabinetName,
            widthMm = widthMm,
            heightMm = heightMm,
            depthMm = depthMm,
            toeKickHeightMm = toeKickMm,
            panelThicknessMm = parsedBoardThickness,
            numberOfShelves = shelvesCount,
            numberOfDrawers = drawersCount,
            doorStyle = doorStyle,
            handleStyle = handleStyle,
            facadeMaterial = facadeMaterial,
            hasBackPanel = hasBackPanel,
            backPanelMaterialName = backPanelMaterialType,
            backGrooveInsetMm = if (hasBackPanel) parsedGrooveInset else 0f,
            topStretcherWidthMm = parsedStretcherWidth,
            aluminumProfileSectionSizeMm = parsedAluminumWidth
        )
    }

    // Generate live procedural box mesh assembly from CabinetAssetLibrary (hollows back immediately when hasBackPanel == false)
    val generatedAssetMesh = remember(
        parametricModel, includeCountertop, hasInternalLed, hasGlassMullions, hasClassicCrown
    ) {
        CabinetAssetLibrary.generateCabinetAssetMesh(
            cabinet = parametricModel,
            includeCountertop = includeCountertop,
            countertopMaterial = MaterialFinish.BLACK_GRANITE,
            hasInternalLed = hasInternalLed,
            hasGlassMullions = hasGlassMullions,
            hasClassicCrownMolding = hasClassicCrown
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A),
            tonalElevation = 12.dp
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "مكتبة ومولّد الخزائن الإجرائي (CabinetAssetLibrary)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF0284C7).copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = "${generatedAssetMesh.subMeshCount} صندوق إجرائي | ${generatedAssetMesh.totalTriangleCount} مثلث",
                                    fontSize = 10.sp,
                                    color = Color(0xFF38BDF8),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "توليد شبكات 3D إجرائية مع التحكم الفوري بالظهر (نعم/لا) وسخات الميلامين وبروفايلات الألمنيوم",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Style Archetype Selector Tabs (Modern / Classic / Glass-Door)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(CabinetStyleArchetype.values()) { arch ->
                        FilterChip(
                            selected = selectedArchetype == arch,
                            onClick = { selectedArchetype = arch },
                            label = { Text(arch.titleAr, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0284C7),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF1E293B),
                                labelColor = Color(0xFF94A3B8)
                            ),
                            modifier = Modifier.testTag("archetype_tab_${arch.id}")
                        )
                    }
                }

                // Preset Carousel from CabinetAssetLibrary
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    items(archetypePresets, key = { it.id }) { preset ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1E293B),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (cabinetName == preset.titleAr) Color(0xFF38BDF8) else Color(0xFF334155)
                            ),
                            modifier = Modifier
                                .clickable {
                                    val def = preset.defaultCabinet
                                    cabinetName = preset.titleAr
                                    widthMm = def.widthMm
                                    heightMm = def.heightMm
                                    depthMm = def.depthMm
                                    toeKickMm = def.toeKickHeightMm
                                    shelvesCount = def.numberOfShelves
                                    drawersCount = def.numberOfDrawers
                                    doorStyle = def.doorStyle
                                    handleStyle = def.handleStyle
                                    facadeMaterial = def.facadeMaterial
                                    includeCountertop = preset.includeCountertop
                                    hasInternalLed = preset.hasInternalLed
                                    hasGlassMullions = preset.hasGlassMullions
                                    hasClassicCrown = preset.hasClassicCrownMolding
                                }
                                .testTag("preset_${preset.id}")
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text(preset.titleAr, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${preset.defaultCabinet.widthMm.toInt()}×${preset.defaultCabinet.heightMm.toInt()}×${preset.defaultCabinet.depthMm.toInt()} مم",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    // Left Column: Interactive 3D Procedural Sub-Mesh Preview
                    Box(
                        modifier = Modifier
                            .weight(1.05f)
                            .fillMaxHeight()
                            .background(Color(0xFF020617), RoundedCornerShape(14.dp))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
                            .padding(8.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cW = size.width
                            val cH = size.height

                            val scale = (cH * 0.56f) / max(heightMm + 120f, max(widthMm + 100f, depthMm + 100f))
                            val origin = Offset(cW * 0.5f, cH * 0.78f)

                            val isoAngle = Math.toRadians(28.0).toFloat()
                            val cosIso = cos(isoAngle)
                            val sinIso = sin(isoAngle)

                            fun toIso(v: com.example.core.math.Vector3): Offset {
                                val screenX = origin.x + (v.x - v.z) * cosIso * scale
                                val screenY = origin.y - (v.y * scale) + (v.x + v.z) * sinIso * scale
                                return Offset(screenX, screenY)
                            }

                            val sortedSubMeshes = generatedAssetMesh.subMeshes.sortedBy {
                                it.localCenter.z + it.localCenter.y * 0.15f + it.localCenter.x * 0.1f
                            }

                            sortedSubMeshes.forEach { sub ->
                                val c = sub.localCenter
                                val hx = sub.dimensions.x * 0.5f
                                val hy = sub.dimensions.y * 0.5f
                                val hz = sub.dimensions.z * 0.5f
                                val baseCol = Color(sub.mesh.colorHex).copy(alpha = sub.opacity)

                                val t0 = toIso(c.copy(x = c.x - hx, y = c.y + hy, z = c.z + hz))
                                val t1 = toIso(c.copy(x = c.x + hx, y = c.y + hy, z = c.z + hz))
                                val t2 = toIso(c.copy(x = c.x + hx, y = c.y + hy, z = c.z - hz))
                                val t3 = toIso(c.copy(x = c.x - hx, y = c.y + hy, z = c.z - hz))
                                val topPath = Path().apply {
                                    moveTo(t0.x, t0.y); lineTo(t1.x, t1.y); lineTo(t2.x, t2.y); lineTo(t3.x, t3.y); close()
                                }
                                drawPath(topPath, color = baseCol)

                                val r0 = toIso(c.copy(x = c.x + hx, y = c.y - hy, z = c.z + hz))
                                val r1 = toIso(c.copy(x = c.x + hx, y = c.y - hy, z = c.z - hz))
                                val r2 = toIso(c.copy(x = c.x + hx, y = c.y + hy, z = c.z - hz))
                                val r3 = toIso(c.copy(x = c.x + hx, y = c.y + hy, z = c.z + hz))
                                val rightPath = Path().apply {
                                    moveTo(r0.x, r0.y); lineTo(r1.x, r1.y); lineTo(r2.x, r2.y); lineTo(r3.x, r3.y); close()
                                }
                                drawPath(
                                    rightPath,
                                    color = baseCol.copy(
                                        red = (baseCol.red * 0.78f).coerceIn(0f, 1f),
                                        green = (baseCol.green * 0.78f).coerceIn(0f, 1f),
                                        blue = (baseCol.blue * 0.78f).coerceIn(0f, 1f)
                                    )
                                )

                                val f0 = toIso(c.copy(x = c.x - hx, y = c.y - hy, z = c.z + hz))
                                val f1 = toIso(c.copy(x = c.x + hx, y = c.y - hy, z = c.z + hz))
                                val f2 = toIso(c.copy(x = c.x + hx, y = c.y + hy, z = c.z + hz))
                                val f3 = toIso(c.copy(x = c.x - hx, y = c.y + hy, z = c.z + hz))
                                val frontPath = Path().apply {
                                    moveTo(f0.x, f0.y); lineTo(f1.x, f1.y); lineTo(f2.x, f2.y); lineTo(f3.x, f3.y); close()
                                }
                                drawPath(
                                    frontPath,
                                    color = baseCol.copy(
                                        red = (baseCol.red * 0.92f).coerceIn(0f, 1f),
                                        green = (baseCol.green * 0.92f).coerceIn(0f, 1f),
                                        blue = (baseCol.blue * 0.92f).coerceIn(0f, 1f)
                                    )
                                )

                                val strokeCol = if (sub.role == CabinetMeshPartRole.LED_LIGHT_STRIP) {
                                    Color(0xFFFEF08A)
                                } else {
                                    Color(0x660F172A)
                                }
                                drawPath(frontPath, color = strokeCol, style = Stroke(width = 1.2f))
                            }
                        }

                        // Overlay summary of generated procedural parts + Back Panel 3D status
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .background(Color(0xCC0F172A), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = cabinetName,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (hasBackPanel)
                                    "الظهر: موجود ($backPanelMaterialType | ريشة ${parsedGrooveInset.toInt()} مم)"
                                else
                                    "الظهر: لا يوجد ظهر (تم تفريغ مجسم الـ 3D وتصفير حساب الظهر فوراً)",
                                color = if (hasBackPanel) Color(0xFF34D399) else Color(0xFFF59E0B),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Right Column: Controls, Cabinet Backing System & Manual Material Overrides
                    LazyColumn(
                        modifier = Modifier
                            .weight(1.25f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Section 4: Interactive Cabinet Backing Options (نظام التحكم بالظهر)
                        item {
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (hasBackPanel) Color(0xFF10B981) else Color(0xFFF59E0B)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                "نظام التحكم بالظهر (Cabinet Backing Options)",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                if (hasBackPanel) "نعم يوجد ظهر (حساب الظهر + مجرى الريشة مفعّل)"
                                                else "لا يوجد ظهر (تفريغ الـ 3D وتصفير حسابات الظهر فوراً)",
                                                color = if (hasBackPanel) Color(0xFF34D399) else Color(0xFFFBBF24),
                                                fontSize = 10.sp
                                            )
                                        }
                                        Switch(
                                            checked = hasBackPanel,
                                            onCheckedChange = { hasBackPanel = it },
                                            modifier = Modifier.testTag("cabinet_backing_toggle")
                                        )
                                    }

                                    if (hasBackPanel) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = backPanelMaterialType,
                                                onValueChange = { backPanelMaterialType = it },
                                                label = { Text("نوع خامة الظهر", fontSize = 10.sp) },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f).testTag("back_material_type_input")
                                            )
                                            OutlinedTextField(
                                                value = backGrooveInsetMmText,
                                                onValueChange = { backGrooveInsetMmText = it },
                                                label = { Text("تداخل المجرى / الريشة (مم)", fontSize = 10.sp) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                singleLine = true,
                                                modifier = Modifier.weight(1f).testTag("back_groove_inset_input")
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Section 4: Manual Override UI for Board Thickness, Top Stretchers & Aluminum Profile
                        item {
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        "لوحة التحكم اليدوي بالخامات وسخات الميلامين (Manual Override):",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = boardThicknessMmText,
                                            onValueChange = { boardThicknessMmText = it },
                                            label = { Text("سُمك الميلامين (مم)", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).testTag("override_board_thickness_input")
                                        )
                                        OutlinedTextField(
                                            value = topStretcherWidthMmText,
                                            onValueChange = { topStretcherWidthMmText = it },
                                            label = { Text("عرض السخات العلوية (مم)", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).testTag("override_stretcher_width_input")
                                        )
                                        OutlinedTextField(
                                            value = aluminumProfileWidthMmText,
                                            onValueChange = { aluminumProfileWidthMmText = it },
                                            label = { Text("بروفايل الألمنيوم (مم)", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).testTag("override_aluminum_width_input")
                                        )
                                    }
                                }
                            }
                        }

                        // 1. Dimensions
                        item {
                            Text("أبعاد الصندوق المرن (Parametric Dimensions):", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            ParametricSlider(
                                label = "العرض (Width): ${widthMm.toInt()} مم (${(widthMm / 10f).toInt()} سم)",
                                value = widthMm,
                                range = 300f..1600f,
                                onValueChange = { widthMm = it }
                            )
                            ParametricSlider(
                                label = "الارتفاع (Height): ${heightMm.toInt()} مم (${(heightMm / 10f).toInt()} سم)",
                                value = heightMm,
                                range = 450f..2450f,
                                onValueChange = { heightMm = it }
                            )
                            ParametricSlider(
                                label = "العمق (Depth): ${depthMm.toInt()} مم (${(depthMm / 10f).toInt()} سم)",
                                value = depthMm,
                                range = 300f..900f,
                                onValueChange = { depthMm = it }
                            )
                        }

                        // 2. Shelves, Drawers & Architectural Options
                        item {
                            HorizontalDivider(color = Color(0xFF334155))
                            Text("التقسيمات والإضافات المعمارية:", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("الأرفف الداخلية: $shelvesCount", color = Color.White, fontSize = 12.sp)
                                Row {
                                    IconButton(onClick = { if (shelvesCount > 0) shelvesCount-- }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White)
                                    }
                                    IconButton(onClick = { if (shelvesCount < 6) shelvesCount++ }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("الأدراج (Drawers Stack): $drawersCount", color = Color.White, fontSize = 12.sp)
                                Row {
                                    IconButton(onClick = { if (drawersCount > 0) drawersCount-- }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White)
                                    }
                                    IconButton(onClick = { if (drawersCount < 5) drawersCount++ }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = includeCountertop,
                                    onClick = { includeCountertop = !includeCountertop },
                                    label = { Text("رخامة بارزة", fontSize = 10.sp) }
                                )
                                FilterChip(
                                    selected = hasInternalLed,
                                    onClick = { hasInternalLed = !hasInternalLed },
                                    label = { Text("شريط LED 3000K", fontSize = 10.sp) }
                                )
                                FilterChip(
                                    selected = hasClassicCrown,
                                    onClick = { hasClassicCrown = !hasClassicCrown },
                                    label = { Text("تاج كلاسيك", fontSize = 10.sp) }
                                )
                            }
                        }

                        // 3. Door Profile Styles
                        item {
                            HorizontalDivider(color = Color(0xFF334155))
                            Text("موديل ونوع الباب (Door Profile):", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                items(DoorStyle.values()) { style ->
                                    FilterChip(
                                        selected = doorStyle == style,
                                        onClick = { doorStyle = style },
                                        label = { Text(style.title.take(15), fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF0284C7),
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }

                        // 4. Handle Styles
                        item {
                            HorizontalDivider(color = Color(0xFF334155))
                            Text("شكل المقابض (Handle Style):", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                items(HandleStyle.values()) { style ->
                                    FilterChip(
                                        selected = handleStyle == style,
                                        onClick = { handleStyle = style },
                                        label = { Text(style.title.take(15), fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF0284C7),
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("إلغاء", color = Color(0xFF94A3B8))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onApplyCabinet(parametricModel)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier.testTag("apply_parametric_cabinet_btn")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تطبيق وتحديث الخزانة (Apply Model)")
                    }
                }
            }
        }
    }
}

@Composable
private fun ParametricSlider(
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
