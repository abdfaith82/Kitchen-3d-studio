package com.example.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.geometry.DrawnGridWallSegment
import com.example.core.geometry.GridPointMm
import com.example.core.geometry.RoomBuilderGridEngine
import com.example.domain.model.OpeningType
import com.example.domain.model.RoomLayoutShape
import com.example.domain.model.RoomStructure
import com.example.domain.model.WallOpening
import com.example.domain.model.WallSide
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Interactive CAD RoomBuilder Screen:
 * Allows architects and users to draw kitchen walls on an interactive millimeter/cm grid,
 * apply room presets (Rectangular, L-Shape, U-Shape), set room Width/Depth/Height/Thickness,
 * add doors/windows onto drawn walls, and commit the resulting RoomStructure to the 2D/3D workspace.
 */
@Composable
fun RoomBuilderScreen(
    initialRoom: RoomStructure,
    onApplyRoom: (RoomStructure) -> Unit,
    onDismiss: () -> Unit
) {
    var roomWidthMm by remember(initialRoom) { mutableFloatStateOf(initialRoom.widthMm.coerceIn(1800f, 10000f)) }
    var roomDepthMm by remember(initialRoom) { mutableFloatStateOf(initialRoom.depthMm.coerceIn(1800f, 10000f)) }
    var ceilingHeightMm by remember(initialRoom) { mutableFloatStateOf(initialRoom.heightMm.coerceIn(2200f, 4000f)) }
    var wallThicknessMm by remember(initialRoom) { mutableFloatStateOf(initialRoom.wallThicknessMm.coerceIn(100f, 300f)) }
    var gridStepMm by remember { mutableFloatStateOf(250f) } // 25 cm default CAD grid step
    var orthogonalLock by remember { mutableStateOf(true) }

    var segments by remember(initialRoom) {
        mutableStateOf(
            RoomBuilderGridEngine.createPresetSegments(
                shape = initialRoom.layoutShape,
                widthMm = initialRoom.widthMm,
                depthMm = initialRoom.depthMm,
                heightMm = initialRoom.heightMm,
                thicknessMm = initialRoom.wallThicknessMm
            )
        )
    }
    var selectedSegmentId by remember { mutableStateOf<String?>(null) }

    // Live interactive wall-drawing state on the CAD grid
    var drawingStartGrid by remember { mutableStateOf<GridPointMm?>(null) }
    var drawingCurrentGrid by remember { mutableStateOf<GridPointMm?>(null) }

    // CAD World Viewport extents in mm (7000mm x 6000mm drawing board or expanded if room is larger)
    val boardWidthMm = max(7000f, roomWidthMm + 1500f)
    val boardDepthMm = max(6000f, roomDepthMm + 1500f)

    val selectedSegment = remember(segments, selectedSegmentId) {
        segments.find { it.id == selectedSegmentId }
    }

    val totalWallMeters = remember(segments) {
        segments.sumOf { it.lengthMm.toDouble() } / 1000.0
    }
    val approxAreaM2 = remember(roomWidthMm, roomDepthMm) {
        (roomWidthMm / 1000.0) * (roomDepthMm / 1000.0)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(8.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                .testTag("room_builder_screen"),
            color = Color(0xFF020617)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Surface(
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF0284C7).copy(alpha = 0.22f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Architecture,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "مهندس بناء الغرفة والجدران (RoomBuilder CAD)",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ارسم الجدران على الشبكة المتريّة أو اختر نمطاً وحدد أبعاد المطبخ",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("close_room_builder_btn")
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "إغلاق مهندس الغرفة",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Quick Preset & Drawing Tools Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoomLayoutShape.entries.forEach { shape ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                segments = RoomBuilderGridEngine.createPresetSegments(
                                    shape = shape,
                                    widthMm = roomWidthMm,
                                    depthMm = roomDepthMm,
                                    heightMm = ceilingHeightMm,
                                    thicknessMm = wallThicknessMm
                                )
                                selectedSegmentId = null
                            },
                            label = { Text(shape.titleAr, fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.CropSquare,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color(0xFF1E293B),
                                labelColor = Color(0xFFE2E8F0),
                                iconColor = Color(0xFF38BDF8)
                            ),
                            modifier = Modifier.testTag("room_preset_${shape.name}")
                        )
                    }

                    FilterChip(
                        selected = orthogonalLock,
                        onClick = { orthogonalLock = !orthogonalLock },
                        label = { Text(if (orthogonalLock) "زاوية قائمة 90°/45° ✓" else "رسم حر", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF065F46),
                            selectedLabelColor = Color(0xFF6EE7B7),
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFFCBD5E1)
                        ),
                        modifier = Modifier.testTag("room_ortho_lock_chip")
                    )

                    listOf(100f to "10سم", 250f to "25سم", 500f to "50سم").forEach { (step, label) ->
                        val active = gridStepMm.toInt() == step.toInt()
                        FilterChip(
                            selected = active,
                            onClick = { gridStepMm = step },
                            label = { Text("شبكة $label", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0284C7),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF1E293B),
                                labelColor = Color(0xFFCBD5E1)
                            )
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            if (segments.isNotEmpty()) {
                                segments = segments.dropLast(1)
                                if (selectedSegmentId !in segments.map { it.id }) selectedSegmentId = null
                            }
                        },
                        enabled = segments.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("room_builder_undo_wall_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "تراجع عن آخر جدار",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تراجع", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            segments = emptyList()
                            selectedSegmentId = null
                        },
                        enabled = segments.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("room_builder_clear_walls_btn")
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "مسح الجدران",
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مسح", fontSize = 11.sp, color = Color(0xFFFCA5A5))
                    }
                }

                // Interactive CAD Grid Drawing Canvas
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF071022))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
                        .testTag("room_builder_grid_canvas")
                ) {
                    val canvasWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                    val canvasHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                    val scalePxPerMm = min(canvasWidthPx / boardWidthMm, canvasHeightPx / boardDepthMm)

                    fun screenToWorld(pos: Offset): GridPointMm {
                        return GridPointMm(
                            xMm = (pos.x / scalePxPerMm).coerceIn(0f, boardWidthMm),
                            zMm = (pos.y / scalePxPerMm).coerceIn(0f, boardDepthMm)
                        )
                    }

                    fun worldToScreen(ptXMm: Float, ptZMm: Float): Offset {
                        return Offset(ptXMm * scalePxPerMm, ptZMm * scalePxPerMm)
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(segments, scalePxPerMm) {
                                detectTapGestures { tapOffset ->
                                    val worldPt = screenToWorld(tapOffset)
                                    val hitThresholdMm = max(200f, gridStepMm)
                                    val hit = segments.minByOrNull { seg ->
                                        distancePointToSegment(
                                            worldPt.xMm,
                                            worldPt.zMm,
                                            seg.startXMm,
                                            seg.startZMm,
                                            seg.endXMm,
                                            seg.endZMm
                                        )
                                    }
                                    selectedSegmentId = if (hit != null &&
                                        distancePointToSegment(
                                            worldPt.xMm,
                                            worldPt.zMm,
                                            hit.startXMm,
                                            hit.startZMm,
                                            hit.endXMm,
                                            hit.endZMm
                                        ) <= hitThresholdMm
                                    ) hit.id else null
                                }
                            }
                            .pointerInput(gridStepMm, orthogonalLock, wallThicknessMm, ceilingHeightMm, scalePxPerMm) {
                                detectDragGestures(
                                    onDragStart = { startPx ->
                                        val raw = screenToWorld(startPx)
                                        val snappedStart = snapToExistingEndpointOrGrid(
                                            raw = raw,
                                            segments = segments,
                                            gridStepMm = gridStepMm
                                        )
                                        drawingStartGrid = snappedStart
                                        drawingCurrentGrid = snappedStart
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val start = drawingStartGrid ?: return@detectDragGestures
                                        val rawEnd = screenToWorld(change.position)
                                        drawingCurrentGrid = RoomBuilderGridEngine.applyOrthogonalSnap(
                                            startXMm = start.xMm,
                                            startZMm = start.zMm,
                                            rawEndXMm = rawEnd.xMm,
                                            rawEndZMm = rawEnd.zMm,
                                            gridStepMm = gridStepMm,
                                            enableAngleSnap = orthogonalLock
                                        )
                                    },
                                    onDragEnd = {
                                        val start = drawingStartGrid
                                        val end = drawingCurrentGrid
                                        drawingStartGrid = null
                                        drawingCurrentGrid = null
                                        if (start != null && end != null) {
                                            val snappedEnd = snapToExistingEndpointOrGrid(
                                                raw = end,
                                                segments = segments,
                                                gridStepMm = gridStepMm
                                            )
                                            val len = hypot(
                                                (snappedEnd.xMm - start.xMm).toDouble(),
                                                (snappedEnd.zMm - start.zMm).toDouble()
                                            ).toFloat()
                                            if (len >= 300f) {
                                                val newSeg = DrawnGridWallSegment(
                                                    id = "wall_drawn_${System.currentTimeMillis()}_${segments.size}",
                                                    startXMm = start.xMm,
                                                    startZMm = start.zMm,
                                                    endXMm = snappedEnd.xMm,
                                                    endZMm = snappedEnd.zMm,
                                                    thicknessMm = wallThicknessMm,
                                                    heightMm = ceilingHeightMm
                                                )
                                                segments = segments + newSeg
                                                selectedSegmentId = newSeg.id
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        drawingStartGrid = null
                                        drawingCurrentGrid = null
                                    }
                                )
                            }
                    ) {
                        // Minor & Major Metric CAD Grid
                        var gx = 0f
                        while (gx <= boardWidthMm) {
                            val sx = gx * scalePxPerMm
                            val isMeter = (gx.toInt() % 1000) == 0
                            drawLine(
                                color = if (isMeter) Color(0xFF1E3A5F) else Color(0xFF0F2138),
                                start = Offset(sx, 0f),
                                end = Offset(sx, size.height),
                                strokeWidth = if (isMeter) 1.6f else 0.8f
                            )
                            gx += gridStepMm
                        }

                        var gz = 0f
                        while (gz <= boardDepthMm) {
                            val sz = gz * scalePxPerMm
                            val isMeter = (gz.toInt() % 1000) == 0
                            drawLine(
                                color = if (isMeter) Color(0xFF1E3A5F) else Color(0xFF0F2138),
                                start = Offset(0f, sz),
                                end = Offset(size.width, sz),
                                strokeWidth = if (isMeter) 1.6f else 0.8f
                            )
                            gz += gridStepMm
                        }

                        val textPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#E2E8F0")
                            textSize = 26f
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                            isFakeBoldText = true
                        }

                        // Drawn Walls
                        segments.forEach { seg ->
                            val isSelected = seg.id == selectedSegmentId
                            val p1 = worldToScreen(seg.startXMm, seg.startZMm)
                            val p2 = worldToScreen(seg.endXMm, seg.endZMm)
                            val wallStrokePx = (seg.thicknessMm * scalePxPerMm).coerceIn(8f, 28f)

                            drawLine(
                                color = if (isSelected) Color(0xFFF59E0B) else Color(0xFF38BDF8),
                                start = p1,
                                end = p2,
                                strokeWidth = wallStrokePx,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = Color(0xFF0F172A),
                                start = p1,
                                end = p2,
                                strokeWidth = (wallStrokePx * 0.4f).coerceAtLeast(2f),
                                cap = StrokeCap.Round
                            )

                            // Corner Endpoint Dots
                            drawCircle(color = Color(0xFF6EE7B7), radius = 7f, center = p1)
                            drawCircle(color = Color(0xFF6EE7B7), radius = 7f, center = p2)

                            // Wall Openings Preview
                            seg.openings.forEach { opening ->
                                val ratio = ((opening.offsetMm + opening.widthMm * 0.5f) / seg.lengthMm.coerceAtLeast(1f))
                                    .coerceIn(0.1f, 0.9f)
                                val cx = p1.x + (p2.x - p1.x) * ratio
                                val cy = p1.y + (p2.y - p1.y) * ratio
                                drawCircle(
                                    color = if (opening.type == OpeningType.DOOR) Color(0xFFF59E0B) else Color(0xFF34D399),
                                    radius = 10f,
                                    center = Offset(cx, cy)
                                )
                            }

                            // Dimension Badge
                            val midX = (p1.x + p2.x) * 0.5f
                            val midY = (p1.y + p2.y) * 0.5f - 14f
                            val cmLabel = "${(seg.lengthMm / 10f).roundToInt()} سم"
                            drawContext.canvas.nativeCanvas.drawText(cmLabel, midX, midY, textPaint)
                        }

                        // Active Rubberband Wall Being Drawn
                        val activeStart = drawingStartGrid
                        val activeEnd = drawingCurrentGrid
                        if (activeStart != null && activeEnd != null) {
                            val sp = worldToScreen(activeStart.xMm, activeStart.zMm)
                            val ep = worldToScreen(activeEnd.xMm, activeEnd.zMm)
                            drawLine(
                                color = Color(0xFF34D399),
                                start = sp,
                                end = ep,
                                strokeWidth = 10f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 10f), 0f),
                                cap = StrokeCap.Round
                            )
                            drawCircle(color = Color(0xFF10B981), radius = 9f, center = sp)
                            drawCircle(color = Color(0xFFFBBF24), radius = 9f, center = ep)
                            val liveLenMm = hypot(
                                (activeEnd.xMm - activeStart.xMm).toDouble(),
                                (activeEnd.zMm - activeStart.zMm).toDouble()
                            ).toFloat()
                            drawContext.canvas.nativeCanvas.drawText(
                                "${(liveLenMm / 10f).roundToInt()} سم",
                                (sp.x + ep.x) * 0.5f,
                                (sp.y + ep.y) * 0.5f - 18f,
                                textPaint
                            )
                        }
                    }

                    // Overlay Drawing Guide Pill
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0F172A).copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "اسحب بإصبعك لرسم جدار جديد • اضغط على جدار لتحديده وإضافة باب/نافذة",
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    // Live CAD Telemetry Pill
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0F172A).copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = String.format(
                                Locale.US,
                                "الجدران: %d | الطول الكلي: %.2f م | المساحة التقريبية: %.1f م²",
                                segments.size,
                                totalWallMeters,
                                approxAreaM2
                            ),
                            color = Color(0xFF7DD3FC),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // Bottom Room Dimensions, Openings & Apply Bar
                Surface(
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectedSegment != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "جدار محدد (${(selectedSegment.lengthMm / 10f).roundToInt()} سم)",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AssistChip(
                                        onClick = {
                                            val off = (selectedSegment.lengthMm * 0.3f).coerceAtLeast(200f)
                                            val newOp = WallOpening(
                                                id = "door_${System.currentTimeMillis()}",
                                                type = OpeningType.DOOR,
                                                offsetMm = off,
                                                widthMm = 900f,
                                                heightMm = 2100f,
                                                sillHeightMm = 0f
                                            )
                                            segments = segments.map {
                                                if (it.id == selectedSegment.id) it.copy(openings = it.openings + newOp) else it
                                            }
                                        },
                                        label = { Text("+ باب 90سم", fontSize = 10.sp, color = Color(0xFFFBBF24)) }
                                    )
                                    AssistChip(
                                        onClick = {
                                            val off = (selectedSegment.lengthMm * 0.3f).coerceAtLeast(200f)
                                            val newOp = WallOpening(
                                                id = "win_${System.currentTimeMillis()}",
                                                type = OpeningType.WINDOW,
                                                offsetMm = off,
                                                widthMm = 1200f,
                                                heightMm = 1100f,
                                                sillHeightMm = 1000f
                                            )
                                            segments = segments.map {
                                                if (it.id == selectedSegment.id) it.copy(openings = it.openings + newOp) else it
                                            }
                                        },
                                        label = { Text("+ شباك 120سم", fontSize = 10.sp, color = Color(0xFF34D399)) }
                                    )
                                    IconButton(
                                        onClick = {
                                            segments = segments.filterNot { it.id == selectedSegment.id }
                                            selectedSegmentId = null
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف الجدار", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // Compact Sliders for Room Dimensions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DimensionCompactSlider(
                                label = "العرض: ${(roomWidthMm / 10f).roundToInt()} سم",
                                value = roomWidthMm,
                                range = 1800f..8000f,
                                onValueChange = { roomWidthMm = RoomBuilderGridEngine.snapValue(it, 100f) },
                                modifier = Modifier.weight(1f)
                            )
                            DimensionCompactSlider(
                                label = "العمق: ${(roomDepthMm / 10f).roundToInt()} سم",
                                value = roomDepthMm,
                                range = 1800f..8000f,
                                onValueChange = { roomDepthMm = RoomBuilderGridEngine.snapValue(it, 100f) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DimensionCompactSlider(
                                label = "ارتفاع السقف: ${(ceilingHeightMm / 10f).roundToInt()} سم",
                                value = ceilingHeightMm,
                                range = 2200f..4000f,
                                onValueChange = { ceilingHeightMm = RoomBuilderGridEngine.snapValue(it, 50f) },
                                modifier = Modifier.weight(1f)
                            )
                            DimensionCompactSlider(
                                label = "سماكة الجدار: ${wallThicknessMm.roundToInt()} مم",
                                value = wallThicknessMm,
                                range = 100f..300f,
                                onValueChange = { wallThicknessMm = RoomBuilderGridEngine.snapValue(it, 10f) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Button(
                            onClick = {
                                val built = RoomBuilderGridEngine.buildRoomStructure(
                                    segments = segments,
                                    fallbackWidthMm = roomWidthMm,
                                    fallbackDepthMm = roomDepthMm,
                                    ceilingHeightMm = ceilingHeightMm,
                                    wallThicknessMm = wallThicknessMm,
                                    floorMaterial = initialRoom.floorMaterial,
                                    wallFinish = initialRoom.wallFinish,
                                    existingWalls = initialRoom.walls
                                )
                                onApplyRoom(built)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("apply_room_builder_btn")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "اعتماد مخطط الغرفة والجدران في التصميم 3D/2D",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DimensionCompactSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(text = label, color = Color(0xFFCBD5E1), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF38BDF8),
                activeTrackColor = Color(0xFF0284C7),
                inactiveTrackColor = Color(0xFF334155)
            ),
            modifier = Modifier.height(28.dp)
        )
    }
}

private fun snapToExistingEndpointOrGrid(
    raw: GridPointMm,
    segments: List<DrawnGridWallSegment>,
    gridStepMm: Float
): GridPointMm {
    val snapRadius = max(200f, gridStepMm * 0.9f)
    var bestPt: GridPointMm? = null
    var bestDist = Float.MAX_VALUE
    segments.forEach { seg ->
        val dStart = hypot((raw.xMm - seg.startXMm).toDouble(), (raw.zMm - seg.startZMm).toDouble()).toFloat()
        if (dStart < snapRadius && dStart < bestDist) {
            bestDist = dStart
            bestPt = GridPointMm(seg.startXMm, seg.startZMm)
        }
        val dEnd = hypot((raw.xMm - seg.endXMm).toDouble(), (raw.zMm - seg.endZMm).toDouble()).toFloat()
        if (dEnd < snapRadius && dEnd < bestDist) {
            bestDist = dEnd
            bestPt = GridPointMm(seg.endXMm, seg.endZMm)
        }
    }
    return bestPt ?: RoomBuilderGridEngine.snapPointToGrid(raw.xMm, raw.zMm, gridStepMm)
}

private fun distancePointToSegment(
    px: Float,
    pz: Float,
    x1: Float,
    z1: Float,
    x2: Float,
    z2: Float
): Float {
    val dx = x2 - x1
    val dz = z2 - z1
    val lenSq = dx * dx + dz * dz
    if (lenSq < 1e-4f) return hypot((px - x1).toDouble(), (pz - z1).toDouble()).toFloat()
    val t = (((px - x1) * dx + (pz - z1) * dz) / lenSq).coerceIn(0f, 1f)
    val projX = x1 + t * dx
    val projZ = z1 + t * dz
    return hypot((px - projX).toDouble(), (pz - projZ).toDouble()).toFloat()
}
