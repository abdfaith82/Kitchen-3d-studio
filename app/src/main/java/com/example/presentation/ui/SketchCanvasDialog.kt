package com.example.presentation.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.core.math.Vector3
import com.example.core.sketch.CalibrationMarker
import com.example.core.sketch.DrawnStroke
import com.example.core.sketch.SketchTo3DEngine
import com.example.core.sketch.StrokeType
import com.example.domain.model.KitchenElement
import com.example.domain.model.RoomStructure
import kotlin.math.*

@Composable
fun SketchCanvasDialog(
    onApplyGeneratedRoom: (RoomStructure) -> Unit,
    onApplyGeneratedScene: ((RoomStructure, List<KitchenElement>) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var strokes by remember { mutableStateOf(listOf<DrawnStroke>()) }
    var currentStrokePoints by remember { mutableStateOf(listOf<Offset>()) }
    var activeStrokeType by remember { mutableStateOf(StrokeType.WALL) }

    // Realistic Sketching & Instant 3D Settings (enabled by default as requested)
    var isMagneticAutoSnapEnabled by remember { mutableStateOf(true) }
    var isInstant3DConversionEnabled by remember { mutableStateOf(true) }
    var isFreehandMultiCornerEnabled by remember { mutableStateOf(true) }
    var autoPopulateKitchenUnits by remember { mutableStateOf(false) }
    var showLive3DSplitPreview by remember { mutableStateOf(true) }

    // Calibration state
    var isCalibrating by remember { mutableStateOf(false) }
    var calibrationMarker by remember { mutableStateOf<CalibrationMarker?>(null) }
    var calibrationKnownDistanceMm by remember { mutableFloatStateOf(1000f) }

    // Selected stroke for exact numeric length editing (like Room Planner / الفن الدمشقي)
    var selectedStrokeId by remember { mutableStateOf<String?>(null) }
    var customLengthCmInput by remember { mutableStateOf("") }

    // Optional background imported or camera-captured blueprint bitmap
    var backgroundBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var canvasSize by remember { mutableStateOf(Offset(800f, 600f)) }

    // Live 3D conversion result updated immediately whenever strokes change
    val liveConversionResult = remember(
        strokes, calibrationMarker, canvasSize, autoPopulateKitchenUnits
    ) {
        SketchTo3DEngine.convertStrokesToScene(
            strokes = strokes,
            calibration = calibrationMarker,
            canvasWidthPx = max(300f, canvasSize.x),
            canvasHeightPx = max(300f, canvasSize.y),
            ceilingHeightMm = 2700f,
            autoPopulateCabinetsOnWalls = autoPopulateKitchenUnits
        )
    }

    // Trigger instant 3D conversion to the main scene as soon as a stroke is drawn!
    fun triggerLive3DSync(updatedStrokes: List<DrawnStroke>) {
        if (!isInstant3DConversionEnabled || updatedStrokes.isEmpty()) return
        val result = SketchTo3DEngine.convertStrokesToScene(
            strokes = updatedStrokes,
            calibration = calibrationMarker,
            canvasWidthPx = max(300f, canvasSize.x),
            canvasHeightPx = max(300f, canvasSize.y),
            ceilingHeightMm = 2700f,
            autoPopulateCabinetsOnWalls = autoPopulateKitchenUnits
        )
        if (onApplyGeneratedScene != null) {
            onApplyGeneratedScene(result.room, result.generatedElements)
        } else {
            onApplyGeneratedRoom(result.room)
        }
    }

    // Zero-permission Photo Picker for importing floorplan images
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    backgroundBitmap = BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) {
            }
        }
    }

    // Camera capture for taking a live photo of a hand-drawn sketch or real kitchen wall
    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null) {
            backgroundBitmap = bmp
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraCaptureLauncher.launch(null)
        }
    }

    fun launchCameraForSketch() {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            cameraCaptureLauncher.launch(null)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A),
            tonalElevation = 12.dp,
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Top Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "الرسم اليدوي الهندسي الذكي وتحويله الفوري إلى 3D",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.22f),
                                border = BorderStroke(1.dp, Color(0xFF10B981))
                            ) {
                                Text(
                                    text = if (isInstant3DConversionEnabled) "تحويل 3D فوري نشط ⚡" else "وضع سكتش",
                                    color = Color(0xFF34D399),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "ارسم الجدران والخزائن باليد — الخطوط تلتصق ببعضها مغناطيسياً وتتحول إلى ثلاثي الأبعاد بمجرد رسمها",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Row 1: Smart Toggles (Magnetic Auto-Glue, Instant 3D, Live 3D Split Preview, Auto-Cabinets)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        FilterChip(
                            selected = isMagneticAutoSnapEnabled,
                            onClick = { isMagneticAutoSnapEnabled = !isMagneticAutoSnapEnabled },
                            label = { Text("لاصق تلقائي للخطوط 🧲", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF10B981).copy(alpha = 0.25f),
                                selectedLabelColor = Color(0xFF34D399)
                            ),
                            modifier = Modifier.testTag("toggle_magnetic_line_snap")
                        )
                    }
                    item {
                        FilterChip(
                            selected = isInstant3DConversionEnabled,
                            onClick = { isInstant3DConversionEnabled = !isInstant3DConversionEnabled },
                            label = { Text("تحويل 3D بمجرد الرسم ⚡", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0284C7).copy(alpha = 0.3f),
                                selectedLabelColor = Color(0xFF38BDF8)
                            ),
                            modifier = Modifier.testTag("toggle_instant_3d_conversion")
                        )
                    }
                    item {
                        FilterChip(
                            selected = showLive3DSplitPreview,
                            onClick = { showLive3DSplitPreview = !showLive3DSplitPreview },
                            label = { Text("معاينة 3D حية أثناء الرسم", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFA855F7).copy(alpha = 0.28f),
                                selectedLabelColor = Color(0xFFE9D5FF)
                            ),
                            modifier = Modifier.testTag("toggle_live_3d_split_preview")
                        )
                    }
                    item {
                        FilterChip(
                            selected = isFreehandMultiCornerEnabled,
                            onClick = { isFreehandMultiCornerEnabled = !isFreehandMultiCornerEnabled },
                            label = { Text("رسم حر متعدد الزوايا (L/U)", fontSize = 11.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = autoPopulateKitchenUnits,
                            onClick = {
                                autoPopulateKitchenUnits = !autoPopulateKitchenUnits
                                triggerLive3DSync(strokes)
                            },
                            label = { Text("فرش خزائن تلقائي", fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Row 2: Stroke Type Selector + Camera / Gallery / Quick Room Shapes / Undo / Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(StrokeType.values()) { type ->
                            FilterChip(
                                selected = activeStrokeType == type && !isCalibrating,
                                onClick = {
                                    activeStrokeType = type
                                    isCalibrating = false
                                },
                                label = { Text(type.title, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = when (type) {
                                        StrokeType.WALL -> Color(0xFF0284C7)
                                        StrokeType.BASE_CABINET -> Color(0xFF10B981)
                                        StrokeType.WALL_CABINET -> Color(0xFFA855F7)
                                        StrokeType.ISLAND -> Color(0xFFEC4899)
                                        StrokeType.DOOR -> Color(0xFFF59E0B)
                                        StrokeType.WINDOW -> Color(0xFF38BDF8)
                                    },
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF1E293B),
                                    labelColor = Color(0xFFCBD5E1)
                                )
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Camera Capture button for Sketch/Room background
                        FilledIconButton(
                            onClick = { launchCameraForSketch() },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF10B981)),
                            modifier = Modifier.size(34.dp).testTag("camera_sketch_btn")
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "تصوير بالكاميرا", modifier = Modifier.size(17.dp))
                        }

                        // Import blueprint background from gallery
                        FilledIconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.size(34.dp).testTag("import_blueprint_btn")
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "استيراد صورة مخطط", modifier = Modifier.size(17.dp))
                        }

                        // Calibration ruler toggle
                        FilledIconToggleButton(
                            checked = isCalibrating,
                            onCheckedChange = { isCalibrating = it },
                            modifier = Modifier.size(34.dp).testTag("toggle_calibration_btn")
                        ) {
                            Icon(Icons.Default.Straighten, contentDescription = "معايرة المقياس", modifier = Modifier.size(17.dp))
                        }

                        // Undo stroke
                        IconButton(
                            onClick = {
                                if (strokes.isNotEmpty()) {
                                    val updated = strokes.dropLast(1)
                                    strokes = updated
                                    triggerLive3DSync(updated)
                                }
                            },
                            enabled = strokes.isNotEmpty(),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Undo, contentDescription = "تراجع", tint = if (strokes.isNotEmpty()) Color.White else Color.Gray)
                        }

                        // Clear
                        IconButton(
                            onClick = {
                                strokes = emptyList()
                                selectedStrokeId = null
                                calibrationMarker = null
                                backgroundBitmap = null
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "مسح", tint = Color(0xFFEF4444))
                        }
                    }
                }

                // Selected stroke exact numeric length editor (like Room Planner & الفن الدمشقي)
                val selectedStroke = strokes.find { it.id == selectedStrokeId }
                if (selectedStroke != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            Text(
                                text = "تعديل طول ${selectedStroke.type.shortTitle} المحدد (سم):",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = customLengthCmInput,
                                onValueChange = { customLengthCmInput = it },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(100.dp).height(46.dp),
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.White)
                            )
                            Button(
                                onClick = {
                                    val cm = customLengthCmInput.toFloatOrNull()
                                    if (cm != null && cm >= 20f) {
                                        val newMm = cm * 10f
                                        val updated = strokes.map {
                                            if (it.id == selectedStroke.id) it.copy(customLengthMmOverride = newMm) else it
                                        }
                                        strokes = updated
                                        triggerLive3DSync(updated)
                                    }
                                    selectedStrokeId = null
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("تطبيق المقاس", fontSize = 11.sp)
                            }
                            TextButton(onClick = { selectedStrokeId = null }) {
                                Text("إغلاق", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Main Area: Realistic 2D Sketch Blueprint Canvas + Live 3D Split Preview
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Left/Main: Realistic Architectural Blueprint Sketch Pad
                    Box(
                        modifier = Modifier
                            .weight(if (showLive3DSplitPreview) 1.35f else 1f)
                            .fillMaxHeight()
                            .background(Color(0xFF040D1A), RoundedCornerShape(14.dp))
                            .border(1.dp, Color(0xFF1E3A5F), RoundedCornerShape(14.dp))
                            .pointerInput(strokes, canvasSize) {
                                detectTapGestures { tapOffset ->
                                    // Tap near an existing stroke to edit its exact length in cm
                                    val hit = strokes.minByOrNull { st ->
                                        val s = st.start ?: return@minByOrNull Float.MAX_VALUE
                                        val e = st.end ?: return@minByOrNull Float.MAX_VALUE
                                        val mid = Offset((s.x + e.x) * 0.5f, (s.y + e.y) * 0.5f)
                                        (mid - tapOffset).getDistance()
                                    }
                                    if (hit != null && hit.start != null && hit.end != null) {
                                        val mid = Offset((hit.start!!.x + hit.end!!.x) * 0.5f, (hit.start!!.y + hit.end!!.y) * 0.5f)
                                        if ((mid - tapOffset).getDistance() <= 55f) {
                                            selectedStrokeId = hit.id
                                            val scale = calibrationMarker?.mmPerPixel ?: (4200f / max(300f, canvasSize.x * 0.68f))
                                            val mm = hit.customLengthMmOverride ?: (hit.lengthPx * scale)
                                            customLengthCmInput = (mm / 10f).roundToInt().toString()
                                        }
                                    }
                                }
                            }
                            .pointerInput(
                                isCalibrating,
                                activeStrokeType,
                                isMagneticAutoSnapEnabled,
                                isFreehandMultiCornerEnabled,
                                isInstant3DConversionEnabled,
                                strokes
                            ) {
                                detectDragGestures(
                                    onDragStart = { startPos ->
                                        val initialPt = if (isMagneticAutoSnapEnabled && !isCalibrating) {
                                            SketchTo3DEngine.findNearestMagneticPoint(startPos, strokes)
                                                ?: strokes.lastOrNull()?.end?.takeIf { (it - startPos).getDistance() <= 95f }
                                                ?: startPos
                                        } else {
                                            startPos
                                        }
                                        currentStrokePoints = listOf(initialPt)
                                    },
                                    onDragEnd = {
                                        if (currentStrokePoints.size >= 2) {
                                            val s = currentStrokePoints.first()
                                            val rawEnd = currentStrokePoints.last()

                                            if (isCalibrating) {
                                                val snappedEnd = SketchTo3DEngine.snapToOrthogonal(s, rawEnd)
                                                calibrationMarker = CalibrationMarker(s, snappedEnd, calibrationKnownDistanceMm)
                                                isCalibrating = false
                                            } else if (isFreehandMultiCornerEnabled && currentStrokePoints.size >= 6) {
                                                val newSegments = SketchTo3DEngine.extractConnectedSegmentsFromFreehand(
                                                    rawPoints = currentStrokePoints,
                                                    strokeType = activeStrokeType,
                                                    existingStrokes = strokes,
                                                    snapRadiusPx = if (isMagneticAutoSnapEnabled) SketchTo3DEngine.DEFAULT_MAGNETIC_SNAP_RADIUS_PX else 8f,
                                                    autoStickToLastEndpoint = isMagneticAutoSnapEnabled
                                                )
                                                if (newSegments.isNotEmpty()) {
                                                    val updated = strokes + newSegments
                                                    strokes = updated
                                                    triggerLive3DSync(updated)
                                                }
                                            } else {
                                                val snapRes = if (isMagneticAutoSnapEnabled) {
                                                    SketchTo3DEngine.snapStrokeMagnetically(
                                                        rawStart = s,
                                                        rawEnd = rawEnd,
                                                        existingStrokes = strokes,
                                                        autoStickToLastEndpoint = true
                                                    )
                                                } else {
                                                    val ortho = SketchTo3DEngine.snapToOrthogonal(s, rawEnd)
                                                    com.example.core.sketch.SnappedStrokeResult(s, ortho, null, null)
                                                }
                                                if ((snapRes.end - snapRes.start).getDistance() >= 15f) {
                                                    val updated = strokes + DrawnStroke(
                                                        points = listOf(snapRes.start, snapRes.end),
                                                        type = activeStrokeType,
                                                        snappedToStartVertex = snapRes.snappedStartTarget != null,
                                                        snappedToEndVertex = snapRes.snappedEndTarget != null
                                                    )
                                                    strokes = updated
                                                    triggerLive3DSync(updated)
                                                }
                                            }
                                        }
                                        currentStrokePoints = emptyList()
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentStrokePoints = currentStrokePoints + change.position
                                    }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            canvasSize = Offset(size.width, size.height)
                            val scale = calibrationMarker?.mmPerPixel ?: (4200f / max(300f, size.width * 0.68f))

                            // 1. Background Blueprint / Camera Photo if present
                            backgroundBitmap?.let { bmp ->
                                drawImage(
                                    image = bmp.asImageBitmap(),
                                    dstOffset = IntOffset.Zero,
                                    dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                                    alpha = 0.52f
                                )
                            }

                            // 2. Realistic Architectural Millimeter / Centimeter Blueprint Grid
                            val minorStep = 20f
                            val majorStep = 100f
                            var gx = 0f
                            while (gx < size.width) {
                                drawLine(Color(0x1238BDF8), Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 0.8f)
                                gx += minorStep
                            }
                            var gy = 0f
                            while (gy < size.height) {
                                drawLine(Color(0x1238BDF8), Offset(0f, gy), Offset(size.width, gy), strokeWidth = 0.8f)
                                gy += minorStep
                            }
                            gx = 0f
                            while (gx < size.width) {
                                drawLine(Color(0x2838BDF8), Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1.4f)
                                gx += majorStep
                            }
                            gy = 0f
                            while (gy < size.height) {
                                drawLine(Color(0x2838BDF8), Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.4f)
                                gy += majorStep
                            }

                            val dimPaint = Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = 24f
                                isFakeBoldText = true
                                isAntiAlias = true
                            }
                            val subPaint = Paint().apply {
                                color = android.graphics.Color.parseColor("#34D399")
                                textSize = 20f
                                isFakeBoldText = true
                                isAntiAlias = true
                            }

                            // 3. Draw Completed Architectural Strokes (Double-line walls with hatching, cabinets with depth, live dimensions)
                            strokes.forEach { stroke ->
                                val s = stroke.start
                                val e = stroke.end
                                if (s != null && e != null) {
                                    val isSelected = stroke.id == selectedStrokeId
                                    val strokeColor = when (stroke.type) {
                                        StrokeType.WALL -> if (isSelected) Color(0xFF38BDF8) else Color(0xFF0284C7)
                                        StrokeType.BASE_CABINET -> Color(0xFF10B981)
                                        StrokeType.WALL_CABINET -> Color(0xFFA855F7)
                                        StrokeType.ISLAND -> Color(0xFFEC4899)
                                        StrokeType.DOOR -> Color(0xFFF59E0B)
                                        StrokeType.WINDOW -> Color(0xFF38BDF8)
                                    }

                                    val dx = e.x - s.x
                                    val dy = e.y - s.y
                                    val len = hypot(dx, dy).coerceAtLeast(1f)
                                    val nx = -dy / len
                                    val ny = dx / len

                                    if (stroke.type == StrokeType.WALL) {
                                        // Realistic Double-Line Architectural Wall with thickness & core fill
                                        val halfThick = 8f
                                        val p1 = Offset(s.x + nx * halfThick, s.y + ny * halfThick)
                                        val p2 = Offset(e.x + nx * halfThick, e.y + ny * halfThick)
                                        val p3 = Offset(e.x - nx * halfThick, e.y - ny * halfThick)
                                        val p4 = Offset(s.x - nx * halfThick, s.y - ny * halfThick)
                                        val wallPoly = Path().apply {
                                            moveTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); lineTo(p4.x, p4.y); close()
                                        }
                                        drawPath(wallPoly, color = strokeColor.copy(alpha = 0.38f))
                                        drawPath(wallPoly, color = strokeColor, style = Stroke(width = 2.6f))
                                    } else if (stroke.type == StrokeType.BASE_CABINET || stroke.type == StrokeType.WALL_CABINET || stroke.type == StrokeType.ISLAND) {
                                        // Realistic Cabinet Footprint Box on Sketch
                                        val cabDepthPx = when (stroke.type) {
                                            StrokeType.WALL_CABINET -> 22f
                                            StrokeType.ISLAND -> 44f
                                            else -> 34f
                                        }
                                        val p1 = s
                                        val p2 = e
                                        val p3 = Offset(e.x + nx * cabDepthPx, e.y + ny * cabDepthPx)
                                        val p4 = Offset(s.x + nx * cabDepthPx, s.y + ny * cabDepthPx)
                                        val cabPoly = Path().apply {
                                            moveTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); lineTo(p4.x, p4.y); close()
                                        }
                                        drawPath(cabPoly, color = strokeColor.copy(alpha = 0.30f))
                                        drawPath(cabPoly, color = strokeColor, style = Stroke(width = 2.4f))
                                    } else {
                                        drawLine(strokeColor, s, e, strokeWidth = 6f)
                                    }

                                    // Magnetic Joint Snap Rings at vertices
                                    drawCircle(
                                        color = if (stroke.snappedToStartVertex) Color(0xFF10B981) else strokeColor,
                                        radius = 7f,
                                        center = s
                                    )
                                    drawCircle(
                                        color = if (stroke.snappedToEndVertex) Color(0xFF10B981) else strokeColor,
                                        radius = 7f,
                                        center = e
                                    )

                                    // Dimension label in cm on every drawn segment
                                    val mid = Offset((s.x + e.x) * 0.5f, (s.y + e.y) * 0.5f)
                                    val lengthMm = stroke.customLengthMmOverride ?: (stroke.lengthPx * scale)
                                    val lengthCm = (lengthMm / 10f).roundToInt()
                                    val label = "${stroke.type.shortTitle}: $lengthCm سم"
                                    drawContext.canvas.nativeCanvas.drawText(
                                        label,
                                        mid.x - 45f,
                                        mid.y - 12f,
                                        if (isSelected) subPaint else dimPaint
                                    )
                                }
                            }

                            // 4. Draw Active Freehand / Magnetic Drag Stroke Preview with Live Dimension & Snap Rings
                            if (currentStrokePoints.size >= 2) {
                                val s = currentStrokePoints.first()
                                val cur = currentStrokePoints.last()

                                // Also show raw freehand trace faintly
                                val freehandPath = Path().apply {
                                    moveTo(s.x, s.y)
                                    currentStrokePoints.drop(1).forEach { pt -> lineTo(pt.x, pt.y) }
                                }
                                drawPath(
                                    path = freehandPath,
                                    color = Color.White.copy(alpha = 0.25f),
                                    style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                                )

                                val snapRes = if (isMagneticAutoSnapEnabled && !isCalibrating) {
                                    SketchTo3DEngine.snapStrokeMagnetically(
                                        rawStart = s,
                                        rawEnd = cur,
                                        existingStrokes = strokes,
                                        autoStickToLastEndpoint = true
                                    )
                                } else {
                                    val ortho = SketchTo3DEngine.snapToOrthogonal(s, cur)
                                    com.example.core.sketch.SnappedStrokeResult(s, ortho, null, null)
                                }

                                val previewColor = if (isCalibrating) Color(0xFF10B981) else Color(0xFF38BDF8)
                                drawLine(previewColor, snapRes.start, snapRes.end, strokeWidth = 6f)

                                // Draw magnetic snap target rings when glued to existing vertices!
                                snapRes.snappedStartTarget?.let { target ->
                                    drawCircle(Color(0xFF10B981), radius = 14f, center = target, style = Stroke(width = 3f))
                                }
                                snapRes.snappedEndTarget?.let { target ->
                                    drawCircle(Color(0xFF10B981), radius = 16f, center = target, style = Stroke(width = 3.5f))
                                }

                                // Draw alignment guide lines if aligned with parallel wall
                                snapRes.alignedAxisCoordX?.let { ax ->
                                    drawLine(
                                        color = Color(0xFF10B981).copy(alpha = 0.7f),
                                        start = Offset(ax, 0f),
                                        end = Offset(ax, size.height),
                                        strokeWidth = 1.5f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                                    )
                                }
                                snapRes.alignedAxisCoordY?.let { ay ->
                                    drawLine(
                                        color = Color(0xFF10B981).copy(alpha = 0.7f),
                                        start = Offset(0f, ay),
                                        end = Offset(size.width, ay),
                                        strokeWidth = 1.5f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                                    )
                                }

                                val liveDistMm = (snapRes.end - snapRes.start).getDistance() * scale
                                val liveCm = (liveDistMm / 10f).roundToInt()
                                val mid = Offset((snapRes.start.x + snapRes.end.x) * 0.5f, (snapRes.start.y + snapRes.end.y) * 0.5f)
                                drawContext.canvas.nativeCanvas.drawText(
                                    "📏 $liveCm سم (${snapRes.angleDegrees.roundToInt()}°)" +
                                            if (snapRes.snappedEndTarget != null) " [التصاق مغناطيسي ✓]" else "",
                                    mid.x - 50f,
                                    mid.y - 16f,
                                    subPaint
                                )
                            }

                            // 5. Calibration Marker
                            calibrationMarker?.let { cal ->
                                drawLine(Color(0xFF10B981), cal.startPx, cal.endPx, strokeWidth = 3f)
                                drawCircle(Color(0xFF10B981), radius = 6f, center = cal.startPx)
                                drawCircle(Color(0xFF10B981), radius = 6f, center = cal.endPx)
                            }
                        }

                        // Quick Preset Sketch Shapes Overlay at Bottom-Start of Sketch Canvas
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .background(Color(0xCC0F172A), RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("قوالب رسم فورية:", color = Color(0xFF94A3B8), fontSize = 10.sp)
                            AssistChip(
                                onClick = {
                                    val w = max(400f, canvasSize.x)
                                    val h = max(350f, canvasSize.y)
                                    val p1 = Offset(w * 0.18f, h * 0.22f)
                                    val p2 = Offset(w * 0.82f, h * 0.22f)
                                    val p3 = Offset(w * 0.82f, h * 0.78f)
                                    val p4 = Offset(w * 0.18f, h * 0.78f)
                                    val presetStrokes = listOf(
                                        DrawnStroke(points = listOf(p1, p2), type = StrokeType.WALL, snappedToStartVertex = true, snappedToEndVertex = true),
                                        DrawnStroke(points = listOf(p2, p3), type = StrokeType.WALL, snappedToStartVertex = true, snappedToEndVertex = true),
                                        DrawnStroke(points = listOf(p3, p4), type = StrokeType.WALL, snappedToStartVertex = true, snappedToEndVertex = true),
                                        DrawnStroke(points = listOf(p4, p1), type = StrokeType.WALL, snappedToStartVertex = true, snappedToEndVertex = true),
                                        DrawnStroke(points = listOf(Offset(w * 0.24f, h * 0.25f), Offset(w * 0.76f, h * 0.25f)), type = StrokeType.BASE_CABINET),
                                        DrawnStroke(points = listOf(Offset(w * 0.24f, h * 0.23f), Offset(w * 0.76f, h * 0.23f)), type = StrokeType.WALL_CABINET)
                                    )
                                    strokes = presetStrokes
                                    triggerLive3DSync(presetStrokes)
                                },
                                label = { Text("غرفة كاملة + مطبخ 3D", fontSize = 10.sp, color = Color(0xFF38BDF8)) }
                            )
                            AssistChip(
                                onClick = {
                                    val w = max(400f, canvasSize.x)
                                    val h = max(350f, canvasSize.y)
                                    val p1 = Offset(w * 0.18f, h * 0.75f)
                                    val p2 = Offset(w * 0.18f, h * 0.22f)
                                    val p3 = Offset(w * 0.82f, h * 0.22f)
                                    val presetStrokes = listOf(
                                        DrawnStroke(points = listOf(p1, p2), type = StrokeType.WALL, snappedToStartVertex = true, snappedToEndVertex = true),
                                        DrawnStroke(points = listOf(p2, p3), type = StrokeType.WALL, snappedToStartVertex = true, snappedToEndVertex = true),
                                        DrawnStroke(points = listOf(Offset(w * 0.22f, h * 0.25f), Offset(w * 0.78f, h * 0.25f)), type = StrokeType.BASE_CABINET),
                                        DrawnStroke(points = listOf(Offset(w * 0.21f, h * 0.28f), Offset(w * 0.21f, h * 0.70f)), type = StrokeType.BASE_CABINET)
                                    )
                                    strokes = presetStrokes
                                    triggerLive3DSync(presetStrokes)
                                },
                                label = { Text("سكتش مطبخ L-Shape", fontSize = 10.sp, color = Color(0xFF34D399)) }
                            )
                        }
                    }

                    // Right Pane: Live 3D Isometric Room & Cabinets Preview (updates in real time as user draws!)
                    if (showLive3DSplitPreview) {
                        Surface(
                            modifier = Modifier
                                .weight(0.95f)
                                .fillMaxHeight(),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF020617),
                            border = BorderStroke(1.dp, Color(0xFF0284C7))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "المجسم الثلاثي الأبعاد الفوري (3D Live)",
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "${(liveConversionResult.room.widthMm / 10f).toInt()}×${(liveConversionResult.room.depthMm / 10f).toInt()} سم",
                                        color = Color(0xFF34D399),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .background(Color(0xFF091326), RoundedCornerShape(10.dp))
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val rm = liveConversionResult.room
                                        val elems = liveConversionResult.generatedElements
                                        val cW = size.width
                                        val cH = size.height

                                        val maxDim = max(rm.widthMm, max(rm.depthMm, rm.heightMm)).coerceAtLeast(1000f)
                                        val isoScale = (min(cW, cH) * 0.46f) / maxDim
                                        val origin = Offset(cW * 0.5f, cH * 0.72f)
                                        val cosA = cos(Math.toRadians(30.0)).toFloat()
                                        val sinA = sin(Math.toRadians(30.0)).toFloat()

                                        fun toIso(v: Vector3): Offset {
                                            val cx = v.x - rm.widthMm * 0.5f
                                            val cz = v.z - rm.depthMm * 0.5f
                                            val sx = origin.x + (cx - cz) * cosA * isoScale
                                            val sy = origin.y - v.y * isoScale + (cx + cz) * sinA * isoScale
                                            return Offset(sx, sy)
                                        }

                                        // Draw 3D Floor slab
                                        val f0 = toIso(Vector3(0f, 0f, 0f))
                                        val f1 = toIso(Vector3(rm.widthMm, 0f, 0f))
                                        val f2 = toIso(Vector3(rm.widthMm, 0f, rm.depthMm))
                                        val f3 = toIso(Vector3(0f, 0f, rm.depthMm))
                                        val floorPath = Path().apply {
                                            moveTo(f0.x, f0.y); lineTo(f1.x, f1.y); lineTo(f2.x, f2.y); lineTo(f3.x, f3.y); close()
                                        }
                                        drawPath(floorPath, color = Color(0xFF1E293B))
                                        drawPath(floorPath, color = Color(0xFF38BDF8), style = Stroke(width = 2f))

                                        // Draw 3D Extruded Walls
                                        val h = rm.heightMm
                                        rm.walls.filter { it.isEnabled }.forEach { w ->
                                            val (b0, b1) = when (w.side) {
                                                com.example.domain.model.WallSide.NORTH -> Pair(Vector3(0f, 0f, 0f), Vector3(rm.widthMm, 0f, 0f))
                                                com.example.domain.model.WallSide.SOUTH -> Pair(Vector3(0f, 0f, rm.depthMm), Vector3(rm.widthMm, 0f, rm.depthMm))
                                                com.example.domain.model.WallSide.WEST -> Pair(Vector3(0f, 0f, 0f), Vector3(0f, 0f, rm.depthMm))
                                                com.example.domain.model.WallSide.EAST -> Pair(Vector3(rm.widthMm, 0f, 0f), Vector3(rm.widthMm, 0f, rm.depthMm))
                                            }
                                            val p0 = toIso(b0)
                                            val p1 = toIso(b1)
                                            val p2 = toIso(b1.copy(y = h))
                                            val p3 = toIso(b0.copy(y = h))
                                            val wPath = Path().apply {
                                                moveTo(p0.x, p0.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); close()
                                            }
                                            drawPath(wPath, color = Color(0xFF0284C7).copy(alpha = 0.28f))
                                            drawPath(wPath, color = Color(0xFF38BDF8), style = Stroke(width = 2f))
                                        }

                                        // Draw 3D Cabinets generated from sketch
                                        elems.forEach { el ->
                                            val hx = el.dimensions.x * 0.5f
                                            val hy = el.dimensions.y
                                            val hz = el.dimensions.z * 0.5f
                                            val c = el.position
                                            val top0 = toIso(Vector3(c.x - hx, c.y + hy, c.z - hz))
                                            val top1 = toIso(Vector3(c.x + hx, c.y + hy, c.z - hz))
                                            val top2 = toIso(Vector3(c.x + hx, c.y + hy, c.z + hz))
                                            val top3 = toIso(Vector3(c.x - hx, c.y + hy, c.z + hz))
                                            val bot2 = toIso(Vector3(c.x + hx, c.y, c.z + hz))
                                            val bot3 = toIso(Vector3(c.x - hx, c.y, c.z + hz))

                                            val frontFace = Path().apply {
                                                moveTo(bot3.x, bot3.y); lineTo(bot2.x, bot2.y); lineTo(top2.x, top2.y); lineTo(top3.x, top3.y); close()
                                            }
                                            val topFace = Path().apply {
                                                moveTo(top0.x, top0.y); lineTo(top1.x, top1.y); lineTo(top2.x, top2.y); lineTo(top3.x, top3.y); close()
                                            }
                                            val col = Color(el.material.colorHex)
                                            drawPath(frontFace, color = col.copy(alpha = 0.90f))
                                            drawPath(topFace, color = col)
                                            drawPath(frontFace, color = Color.White.copy(alpha = 0.5f), style = Stroke(width = 1.4f))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "الجدران المرسومة: ${liveConversionResult.wallSegmentsCount} | الوحدات 3D: ${liveConversionResult.generatedElements.size}" +
                                            if (liveConversionResult.isClosedLoop) " | [مخطط مغلق محكم ✓]" else "",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Convert & Apply to 3D Studio Action
                Button(
                    onClick = {
                        val res = SketchTo3DEngine.convertStrokesToScene(
                            strokes = strokes,
                            calibration = calibrationMarker,
                            canvasWidthPx = max(300f, canvasSize.x),
                            canvasHeightPx = max(300f, canvasSize.y),
                            ceilingHeightMm = 2700f,
                            autoPopulateCabinetsOnWalls = autoPopulateKitchenUnits
                        )
                        if (onApplyGeneratedScene != null) {
                            onApplyGeneratedScene(res.room, res.generatedElements)
                        } else {
                            onApplyGeneratedRoom(res.room)
                        }
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("convert_sketch_to_3d_btn")
                ) {
                    Icon(Icons.Default.ViewInAr, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "اعتماد السكتش والانتقال للتصميم الثلاثي الأبعاد (${(liveConversionResult.room.widthMm / 10f).toInt()}×${(liveConversionResult.room.depthMm / 10f).toInt()} سم)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
