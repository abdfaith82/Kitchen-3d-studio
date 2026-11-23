package com.example.presentation.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.export.JsonProjectExporter
import com.example.core.export.ShareExportHelper
import com.example.core.math.Vector3
import com.example.core.perspective.CalibratedCamera
import com.example.core.perspective.FloorCalibrationQuad
import com.example.core.perspective.PerspectiveCalibrator
import androidx.compose.ui.layout.onGloballyPositioned
import com.example.domain.model.KitchenElementType
import com.example.domain.model.MaterialFinish
import com.example.presentation.ui.RealisticKitchenRenderer3D.renderCabinetRealisticDetails
import com.example.presentation.viewmodel.KitchenEditorUiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Modes for Real-Time Camera Design & JSON Object Import/Edit:
 * 1. CALIBRATE_PERSPECTIVE (المعايرة التلقائية للمنظور وخط الأفق)
 * 2. SMART_INPAINTING (التعديل الإحلالي الذكي لمسح الخزائن القديمة وتنظيف الحائط)
 * 3. PLACE_CABINETS (إسقاط وتحريك وتعديل الخزائن والتصميمات 3D بالوقت الفعلي فوق الكاميرا)
 * 4. IMAGE_TO_IMAGE_8K_RENDER (محرك الرندر والتوليد الواقعي الفائق 8K: لمعان الألمنيوم، انعكاس الزجاج الأسود، والظلال الطبيعية)
 * 5. JSON_OBJECT_EDITOR (استيراد وتعديل كائنات المطبخ بصيغة JSON مباشرة على الكاميرا)
 */
enum class OverlayMode(val arabicTitle: String) {
    CALIBRATE_PERSPECTIVE("1. معايرة المنظور"),
    SMART_INPAINTING("2. مسح الحائط الذكي"),
    PLACE_CABINETS("3. تصميم الكاميرا الفوري 3D"),
    IMAGE_TO_IMAGE_8K_RENDER("4. رندر 8K الواقعي"),
    JSON_OBJECT_EDITOR("5. استيراد وتعديل JSON"),
    AR_MATERIAL_SWAPPER("6. تعديل الخامة بالواقع المعزز")
}

data class InpaintCleanRegion(
    val center: Offset,
    val radiusPx: Float,
    val sampledWallColor: Color
)

@Composable
fun PerspectiveOverlayDialog(
    state: KitchenEditorUiState,
    onMoveElement: (String, Vector3) -> Unit,
    onSelectElement: (String?) -> Unit = {},
    onAddElement: (KitchenElementType) -> Unit = {},
    onLoadTemplate: (String) -> Unit = {},
    onRotateSelected: (Float) -> Unit = {},
    onResizeSelected: (String, Float, Float, Float) -> Unit = { _, _, _, _ -> },
    onDeleteSelected: () -> Unit = {},
    onCycleSelectedMaterial: () -> Unit = {},
    onApplyArMaterialSwap: (elementId: String, cabinetFinish: MaterialFinish?, countertopFinish: MaterialFinish?) -> Unit = { _, _, _ -> },
    onImportOrUpdateJson: (String) -> Int = { 0 },
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var roomPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var liveCameraPreviewRef by remember { mutableStateOf<PreviewView?>(null) }
    var isLiveCameraStreamActive by remember { mutableStateOf(true) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var mode by remember { mutableStateOf(OverlayMode.PLACE_CABINETS) }

    // Section 5.1: Static Background Canvas Lock
    var isBackgroundCanvasLocked by remember { mutableStateOf(true) }
    // Section 5.2: Hidden/Togglable Engineering Perspective Grid
    var showPerspectiveEngineeringGrid by remember { mutableStateOf(true) }
    // Real-time dimension labels toggle on camera view
    var showAllCameraDimensions by remember { mutableStateOf(true) }
    // Collapsible top controls inside AR Camera view so the photo & 3D kitchen are unobstructed
    var isTopControlsCollapsed by remember { mutableStateOf(false) }

    // Section 5.3: Smart Inpainting Wall Cleaner strokes/regions
    val inpaintRegions = remember { mutableStateListOf<InpaintCleanRegion>() }
    var inpaintBrushRadius by remember { mutableFloatStateOf(48f) }

    // Section 5.4: Natural light source estimation from photo for 8K Image-to-Image rendering
    var estimatedLightIntensity by remember { mutableFloatStateOf(0.92f) }
    var aluminumSpecularBoost by remember { mutableFloatStateOf(0.95f) }
    var blackGlassReflectionStrength by remember { mutableFloatStateOf(0.88f) }

    // JSON Object Editor state inside Real-Time Camera View
    val selectedElement = state.singleSelectedElement
    var jsonEditorText by remember { mutableStateOf("") }
    var jsonStatusMessage by remember { mutableStateOf<String?>(null) }
    var editNameText by remember { mutableStateOf("") }
    var editWidthMmText by remember { mutableStateOf("") }
    var editHeightMmText by remember { mutableStateOf("") }
    var editDepthMmText by remember { mutableStateOf("") }
    var editPosXMmText by remember { mutableStateOf("") }
    var editPosYMmText by remember { mutableStateOf("") }
    var editPosZMmText by remember { mutableStateOf("") }

    // Keep JSON editor synced when user selects a cabinet or modifies its material/countertop in AR
    LaunchedEffect(
        selectedElement?.id,
        selectedElement?.dimensions,
        selectedElement?.position,
        selectedElement?.material,
        selectedElement?.countertopMaterial
    ) {
        if (selectedElement != null) {
            jsonEditorText = JsonProjectExporter.serializeSingleElementToJsonString(
                elem = selectedElement,
                includeBoundingBoxes = true,
                prettyPrint = true
            )
            editNameText = selectedElement.name
            editWidthMmText = selectedElement.dimensions.x.roundToInt().toString()
            editHeightMmText = selectedElement.dimensions.y.roundToInt().toString()
            editDepthMmText = selectedElement.dimensions.z.roundToInt().toString()
            editPosXMmText = selectedElement.position.x.roundToInt().toString()
            editPosYMmText = selectedElement.position.y.roundToInt().toString()
            editPosZMmText = selectedElement.position.z.roundToInt().toString()
        } else if (jsonEditorText.isBlank() && state.project.elements.isNotEmpty()) {
            jsonEditorText = JsonProjectExporter.serializeElementsListToJsonString(
                elements = state.project.elements,
                includeBoundingBoxes = true,
                prettyPrint = true
            )
        }
    }

    var quadPinsInitialized by remember { mutableStateOf(false) }
    var quadPins by remember {
        mutableStateOf(
            FloorCalibrationQuad(
                frontLeft = Offset(180f, 760f),
                frontRight = Offset(860f, 760f),
                backRight = Offset(700f, 440f),
                backLeft = Offset(340f, 440f),
                floorWidthMm = state.project.room.widthMm,
                floorDepthMm = state.project.room.depthMm
            )
        )
    }

    // Non-reactive projection cache so updating projected bounds inside Canvas never causes recomposition flicker
    val arHitCache = remember {
        object {
            var calibratedCamera: CalibratedCamera? = null
            val projectedCabinetCenters = HashMap<String, Offset>()
            val projectedCabinetBounds = HashMap<String, RectF>()
            var frontToBackIds: List<String> = emptyList()
        }
    }
    var draggedPinIndex by remember { mutableIntStateOf(-1) }
    var draggedElementId by remember { mutableStateOf<String?>(null) }
    var arDragStartPos by remember { mutableStateOf<Vector3?>(null) }
    var arAccumDeltaWorld by remember { mutableStateOf(Vector3.ZERO) }
    val latestState by rememberUpdatedState(state)

    fun hitTestCabinetOnCamera(screenPt: Offset): String? {
        for (id in arHitCache.frontToBackIds) {
            val rect = arHitCache.projectedCabinetBounds[id]
            if (rect != null && rect.contains(screenPt.x, screenPt.y)) {
                return id
            }
        }
        val nearest = arHitCache.projectedCabinetCenters.entries
            .minByOrNull { (_, pt) -> (pt - screenPt).getDistanceSquared() }
        if (nearest != null && (nearest.value - screenPt).getDistance() <= 130f) {
            return nearest.key
        }
        return null
    }

    // Live Camera Snapshot Launcher
    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            roomPhotoBitmap = bitmap
            isLiveCameraStreamActive = false
            inpaintRegions.clear()
        }
    }

    var pendingLiveStreamRequest by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            if (pendingLiveStreamRequest) {
                isLiveCameraStreamActive = true
                pendingLiveStreamRequest = false
            } else {
                cameraCaptureLauncher.launch(null)
            }
        } else {
            pendingLiveStreamRequest = false
        }
    }

    // Zero-permission Photo Picker for Gallery
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    roomPhotoBitmap = BitmapFactory.decodeStream(stream)
                    isLiveCameraStreamActive = false
                    inpaintRegions.clear()
                }
            } catch (_: Exception) {
            }
        }
    }

    // JSON Object File Picker Launcher
    val jsonObjectPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val rawJson = context.contentResolver.openInputStream(it)?.bufferedReader(Charsets.UTF_8)?.use { r -> r.readText() }
                if (!rawJson.isNullOrBlank()) {
                    jsonEditorText = rawJson
                    val count = onImportOrUpdateJson(rawJson)
                    mode = OverlayMode.JSON_OBJECT_EDITOR
                    jsonStatusMessage = "تم استيراد وتحميل $count كائن من ملف JSON بنجاح — يمكنك تعديله مباشرة الآن"
                }
            } catch (e: Exception) {
                jsonStatusMessage = "خطأ أثناء قراءة ملف JSON: ${e.localizedMessage}"
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A),
            tonalElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                // Header with Collapsible Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "تصميم الكاميرا الفوري (Real-Time AR Camera) واستيراد وتعديل كائنات JSON",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Text(
                            text = "افتح البث المباشر للكاميرا أو صوّر المطبخ، صمّم خزائن 3D بالوقت الفعلي مع المقاسات الفورية، واستورد وعدّل كائنات JSON مباشرة",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF064E3B),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
                            modifier = Modifier.testTag("camera_live_autosave_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Save,
                                    contentDescription = null,
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Room Auto-Save (${state.autoSaveState.secondsUntilNextSave}ث)",
                                    color = Color(0xFFA7F3D0),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        TextButton(
                            onClick = { isTopControlsCollapsed = !isTopControlsCollapsed },
                            modifier = Modifier.testTag("toggle_ar_controls_collapse_btn")
                        ) {
                            Icon(
                                if (isTopControlsCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                if (isTopControlsCollapsed) "إظهار الأدوات" else "إخفاء الأدوات",
                                color = Color(0xFF38BDF8),
                                fontSize = 10.sp
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8))
                        }
                    }
                }

                if (!isTopControlsCollapsed) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // Row 1: Live Camera AR Stream, Photo Capture, Gallery Upload, JSON Import, Dimension Toggle, and 5 Mode Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Real-Time Live Camera Stream Toggle Button
                        Button(
                            onClick = {
                                if (isLiveCameraStreamActive) {
                                    isLiveCameraStreamActive = false
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        hasCameraPermission = true
                                        isLiveCameraStreamActive = true
                                    } else {
                                        pendingLiveStreamRequest = true
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLiveCameraStreamActive) Color(0xFFDC2626) else Color(0xFF10B981)
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("live_camera_ar_stream_btn")
                        ) {
                            Icon(
                                if (isLiveCameraStreamActive) Icons.Default.VideocamOff else Icons.Default.Videocam,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (isLiveCameraStreamActive) "إيقاف البث المباشر ●" else "بث الكاميرا الفوري AR",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Snapshot Camera Capture Button
                        Button(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    hasCameraPermission = true
                                    cameraCaptureLauncher.launch(null)
                                } else {
                                    pendingLiveStreamRequest = false
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("capture_room_camera_btn")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تصوير لقطة", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        // Gallery Photo Picker Button
                        OutlinedButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("upload_room_photo_btn")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (roomPhotoBitmap == null) "صورة من المعرض" else "تغيير الصورة", fontSize = 10.sp)
                        }

                        // Quick Import JSON Object Button (File or Camera Choice)
                        OutlinedButton(
                            onClick = {
                                jsonObjectPickerLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("camera_import_json_object_btn")
                        ) {
                            Icon(Icons.Default.DataObject, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("استيراد كائن JSON", fontSize = 10.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                        }

                        // Import Object via Camera Scan Button (analyzes live camera frame into Professional CAD/BIM & PBR JSON)
                        Button(
                            onClick = {
                                val capturedBmp = try {
                                    val pv = liveCameraPreviewRef
                                    if (pv != null &&
                                        pv.previewStreamState.value == PreviewView.StreamState.STREAMING &&
                                        pv.width > 0 && pv.height > 0
                                    ) {
                                        pv.bitmap ?: roomPhotoBitmap
                                    } else {
                                        roomPhotoBitmap
                                    }
                                } catch (_: Exception) {
                                    roomPhotoBitmap
                                }
                                val scan = com.example.core.importer.CameraObjectScanner.analyzeCapturedBitmap(
                                    bitmap = capturedBmp
                                )
                                val proJson = com.example.core.importer.CameraObjectScanner.serializeScanToProfessionalJson(
                                    analysis = scan,
                                    prettyPrint = true
                                )
                                jsonEditorText = proJson
                                onImportOrUpdateJson(proJson)
                                mode = OverlayMode.JSON_OBJECT_EDITOR
                                jsonStatusMessage = "${scan.summaryAr} • JSON احترافي (CAD/BIM v2.0)"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("ar_scan_import_object_via_camera_btn")
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("استيراد كائن عبر الكاميرا ➔ JSON احترافي 📷", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        FilterChip(
                            selected = showAllCameraDimensions,
                            onClick = { showAllCameraDimensions = !showAllCameraDimensions },
                            label = { Text(if (showAllCameraDimensions) "📏 الأبعاد الفورية: مفعلة" else "📏 الأبعاد: المحدد", fontSize = 10.sp) },
                            modifier = Modifier.testTag("camera_dimensions_toggle_chip")
                        )

                        FilterChip(
                            selected = isBackgroundCanvasLocked,
                            onClick = { isBackgroundCanvasLocked = !isBackgroundCanvasLocked },
                            label = { Text(if (isBackgroundCanvasLocked) "الخلفية ثابتة 🔒" else "خلفية حرة", fontSize = 10.sp) },
                            modifier = Modifier.testTag("background_canvas_lock_chip")
                        )

                        FilterChip(
                            selected = showPerspectiveEngineeringGrid,
                            onClick = { showPerspectiveEngineeringGrid = !showPerspectiveEngineeringGrid },
                            label = { Text("شبكة المنظور", fontSize = 10.sp) },
                            modifier = Modifier.testTag("perspective_grid_toggle_chip")
                        )

                        OverlayMode.values().forEach { m ->
                            FilterChip(
                                selected = mode == m,
                                onClick = { mode = m },
                                label = { Text(m.arabicTitle, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = when (m) {
                                        OverlayMode.CALIBRATE_PERSPECTIVE -> Color(0xFF0284C7)
                                        OverlayMode.SMART_INPAINTING -> Color(0xFFF59E0B)
                                        OverlayMode.PLACE_CABINETS -> Color(0xFF10B981)
                                        OverlayMode.IMAGE_TO_IMAGE_8K_RENDER -> Color(0xFFA855F7)
                                        OverlayMode.JSON_OBJECT_EDITOR -> Color(0xFF0D9488)
                                        OverlayMode.AR_MATERIAL_SWAPPER -> Color(0xFFEC4899)
                                    },
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.testTag("overlay_mode_${m.name}")
                            )
                        }
                    }

                    // Row 2: Live Camera Visual 3D Cabinet & Template Catalog Strip (tap or drag directly onto Live Camera view)
                    val liveCatalogItems = remember {
                        listOf(
                            KitchenElementType.BASE_DOUBLE_DOOR to "خزانة سفلية",
                            KitchenElementType.BASE_3_DRAWERS to "وحدة أدراج",
                            KitchenElementType.BASE_SINK_UNIT to "وحدة حوض",
                            KitchenElementType.BASE_UNDER_OVEN to "وحدة فرن/بوتاجاز",
                            KitchenElementType.WALL_GLASS_BLACK_FRAME to "علوية ألمنيوم",
                            KitchenElementType.TALL_GLASS_VITRINE_LED to "دولاب فيترينة",
                            KitchenElementType.ISLAND_COUNTER to "جزيرة مطبخ",
                            KitchenElementType.REFRIGERATOR_FROST to "ثلاجة ذكية"
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .horizontalScroll(rememberScrollState())
                            .testTag("live_camera_design_catalog_bar"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تصميم مباشر على الكاميرا (Live Mode):",
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        AssistChip(
                            onClick = {
                                onLoadTemplate("template_l_shape")
                                mode = OverlayMode.PLACE_CABINETS
                            },
                            label = { Text("مطبخ L-Shape كامل", fontSize = 10.sp, color = Color.White) }
                        )
                        AssistChip(
                            onClick = {
                                onLoadTemplate("template_luxury_vitrine")
                                mode = OverlayMode.PLACE_CABINETS
                            },
                            label = { Text("مطبخ ألمنيوم وفيترينة LED", fontSize = 10.sp, color = Color.White) }
                        )
                        AssistChip(
                            onClick = {
                                onLoadTemplate("template_island")
                                mode = OverlayMode.PLACE_CABINETS
                            },
                            label = { Text("مطبخ جزيرة", fontSize = 10.sp, color = Color.White) }
                        )
                        liveCatalogItems.forEach { (elemType, shortTitle) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF0F172A),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier
                                    .clickable {
                                        onAddElement(elemType)
                                        mode = OverlayMode.PLACE_CABINETS
                                    }
                                    .testTag("live_camera_add_${elemType.name}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    CabinetPreviewThumbnail(
                                        type = elemType,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "+ $shortTitle",
                                            color = Color(0xFF34D399),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${elemType.defaultDimensions.x.toInt() / 10}×${elemType.defaultDimensions.y.toInt() / 10} سم",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 8.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Contextual sub-controls for Smart Inpainting or 8K Render
                    if (mode == OverlayMode.SMART_INPAINTING) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "التعديل الإحلالي الذكي (Inpainting): اسحب فوق أي خزانة قديمة بالصورة لمسحها وتنظيف الحائط تلقائياً (${inpaintRegions.size} منطقة منظفة)",
                                color = Color(0xFFFDE68A),
                                fontSize = 10.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { if (inpaintRegions.isNotEmpty()) inpaintRegions.removeAt(inpaintRegions.lastIndex) }
                                ) {
                                    Text("تراجع مسح", color = Color(0xFF38BDF8), fontSize = 10.sp)
                                }
                                TextButton(
                                    onClick = { inpaintRegions.clear() },
                                    modifier = Modifier.testTag("clear_inpaint_regions_btn")
                                ) {
                                    Text("استعادة الأصل", color = Color(0xFFEF4444), fontSize = 10.sp)
                                }
                            }
                        }
                    } else if (mode == OverlayMode.IMAGE_TO_IMAGE_8K_RENDER) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "محرك Image-to-Image 8K نشط: قراءة الضوء الطبيعي + لمعان بروفايلات الألمنيوم + انعكاس الزجاج الأسود المدخن + الظلال الواقعية",
                                color = Color(0xFFE9D5FF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "الدقة: 8K Ultra PBR",
                                color = Color(0xFF34D399),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Main Content Area: Camera 3D Design Canvas + Optional JSON Object Import & Edit Side/Overlay Panel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Left/Main Box: Live Camera Feed or Photo + 3D Kitchen Overlay Canvas
                    Box(
                        modifier = Modifier
                            .weight(if (mode == OverlayMode.JSON_OBJECT_EDITOR) 1.15f else 1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF020617), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                            .pointerInput(mode) {
                                detectTapGestures { tapPos ->
                                    if (mode != OverlayMode.CALIBRATE_PERSPECTIVE && mode != OverlayMode.SMART_INPAINTING) {
                                        val hitId = hitTestCabinetOnCamera(tapPos)
                                        if (hitId != null) {
                                            onSelectElement(hitId)
                                        } else {
                                            onSelectElement(null)
                                        }
                                    }
                                }
                            }
                            .pointerInput(mode) {
                                detectDragGestures(
                                    onDragStart = { startPos ->
                                        val curState = latestState
                                        when (mode) {
                                            OverlayMode.CALIBRATE_PERSPECTIVE -> {
                                                val radius = 55f
                                                draggedPinIndex = when {
                                                    (startPos - quadPins.frontLeft).getDistance() <= radius -> 0
                                                    (startPos - quadPins.frontRight).getDistance() <= radius -> 1
                                                    (startPos - quadPins.backRight).getDistance() <= radius -> 2
                                                    (startPos - quadPins.backLeft).getDistance() <= radius -> 3
                                                    else -> -1
                                                }
                                            }
                                            OverlayMode.SMART_INPAINTING -> {
                                                inpaintRegions.add(
                                                    InpaintCleanRegion(
                                                        center = startPos,
                                                        radiusPx = inpaintBrushRadius,
                                                        sampledWallColor = Color(0xFFE2DDD5)
                                                    )
                                                )
                                            }
                                            OverlayMode.PLACE_CABINETS,
                                            OverlayMode.IMAGE_TO_IMAGE_8K_RENDER,
                                            OverlayMode.JSON_OBJECT_EDITOR,
                                            OverlayMode.AR_MATERIAL_SWAPPER -> {
                                                val hitId = hitTestCabinetOnCamera(startPos)
                                                val resolvedId = if (hitId != null) {
                                                    if (curState.singleSelectedElement?.id != hitId) {
                                                        onSelectElement(hitId)
                                                    }
                                                    hitId
                                                } else curState.singleSelectedElement?.id
                                                draggedElementId = resolvedId
                                                arDragStartPos = curState.project.elements.find { it.id == resolvedId }?.position
                                                arAccumDeltaWorld = Vector3.ZERO
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        draggedPinIndex = -1
                                        draggedElementId = null
                                        arDragStartPos = null
                                        arAccumDeltaWorld = Vector3.ZERO
                                    },
                                    onDragCancel = {
                                        draggedPinIndex = -1
                                        draggedElementId = null
                                        arDragStartPos = null
                                        arAccumDeltaWorld = Vector3.ZERO
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val curState = latestState
                                        when (mode) {
                                            OverlayMode.CALIBRATE_PERSPECTIVE -> {
                                                if (draggedPinIndex >= 0) {
                                                    val newPos = change.position
                                                    quadPins = when (draggedPinIndex) {
                                                        0 -> quadPins.copy(frontLeft = newPos)
                                                        1 -> quadPins.copy(frontRight = newPos)
                                                        2 -> quadPins.copy(backRight = newPos)
                                                        3 -> quadPins.copy(backLeft = newPos)
                                                        else -> quadPins
                                                    }
                                                }
                                            }
                                            OverlayMode.SMART_INPAINTING -> {
                                                inpaintRegions.add(
                                                    InpaintCleanRegion(
                                                        center = change.position,
                                                        radiusPx = inpaintBrushRadius,
                                                        sampledWallColor = Color(0xFFE2DDD5)
                                                    )
                                                )
                                            }
                                            OverlayMode.PLACE_CABINETS,
                                            OverlayMode.IMAGE_TO_IMAGE_8K_RENDER,
                                            OverlayMode.JSON_OBJECT_EDITOR,
                                            OverlayMode.AR_MATERIAL_SWAPPER -> {
                                                val targetId = draggedElementId ?: curState.singleSelectedElement?.id
                                                val elem = curState.project.elements.find { it.id == targetId }
                                                if (elem != null && !elem.isLocked) {
                                                    val startOrigin = arDragStartPos ?: elem.position.also { arDragStartPos = it }
                                                    val cam = arHitCache.calibratedCamera
                                                    val rawRight = if (cam != null) Vector3(cam.right.x, 0f, cam.right.z) else Vector3.UNIT_X
                                                    val floorRight = if (rawRight.lengthSquared() > 0.001f) rawRight.normalized() else Vector3.UNIT_X
                                                    val rawFwd = if (cam != null) Vector3(cam.forward.x, 0f, cam.forward.z) else Vector3(0f, 0f, -1f)
                                                    val floorFwd = if (rawFwd.lengthSquared() > 0.001f) rawFwd.normalized() else Vector3(0f, 0f, -1f)

                                                    val distToCam = if (cam != null) {
                                                        (elem.position - cam.cameraPosition).length().coerceIn(900f, 8000f)
                                                    } else 3400f
                                                    val fovPx = (cam?.focalLengthPx ?: 800f).coerceAtLeast(350f)
                                                    val mmPerPixel = (distToCam / fovPx).coerceIn(1.5f, 5.5f)

                                                    val deltaStep = (floorRight * (dragAmount.x * mmPerPixel)) + (floorFwd * (-dragAmount.y * mmPerPixel))
                                                    arAccumDeltaWorld = arAccumDeltaWorld + Vector3(deltaStep.x, 0f, deltaStep.z)
                                                    val rawTargetPos = startOrigin + arAccumDeltaWorld
                                                    onMoveElement(elem.id, rawTargetPos)
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        // 0. Live CameraX Stream Preview Layer (when active and permission granted)
                        if (isLiveCameraStreamActive && hasCameraPermission) {
                            LiveCameraPreviewLayer(
                                onPreviewReady = { liveCameraPreviewRef = it },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { coords ->
                                    val cw = coords.size.width.toFloat()
                                    val ch = coords.size.height.toFloat()
                                    if (!quadPinsInitialized && cw > 120f && ch > 120f) {
                                        quadPinsInitialized = true
                                        quadPins = FloorCalibrationQuad(
                                            frontLeft = Offset(cw * 0.14f, ch * 0.82f),
                                            frontRight = Offset(cw * 0.86f, ch * 0.82f),
                                            backRight = Offset(cw * 0.72f, ch * 0.48f),
                                            backLeft = Offset(cw * 0.28f, ch * 0.48f),
                                            floorWidthMm = state.project.room.widthMm,
                                            floorDepthMm = state.project.room.depthMm
                                        )
                                    }
                                }
                        ) {
                            val canvasW = size.width
                            val canvasH = size.height

                            // 1. Static Background Photo or Live Architectural Camera Viewfinder Layer
                            if (!isLiveCameraStreamActive || !hasCameraPermission) {
                                roomPhotoBitmap?.let { bmp ->
                                    drawImage(
                                        image = bmp.asImageBitmap(),
                                        dstOffset = IntOffset.Zero,
                                        dstSize = androidx.compose.ui.unit.IntSize(canvasW.toInt(), canvasH.toInt())
                                    )
                                } ?: run {
                                    // Studio Architectural Room Wall + Floor Backdrop
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color(0xFFD6D1C7), Color(0xFFC2BBB0)),
                                            startY = 0f,
                                            endY = canvasH * 0.56f
                                        ),
                                        topLeft = Offset.Zero,
                                        size = Size(canvasW, canvasH * 0.56f)
                                    )
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color(0xFFACA395), Color(0xFF8A8072)),
                                            startY = canvasH * 0.56f,
                                            endY = canvasH
                                        ),
                                        topLeft = Offset(0f, canvasH * 0.56f),
                                        size = Size(canvasW, canvasH * 0.44f)
                                    )
                                }
                            }

                            // Live Camera Optical Viewfinder Corner Brackets when Live Mode is active
                            if (isLiveCameraStreamActive) {
                                val bracketLen = 28f
                                val pad = 16f
                                val bracketColor = Color(0xAA10B981)
                                // Top-left
                                drawLine(bracketColor, Offset(pad, pad), Offset(pad + bracketLen, pad), strokeWidth = 3f)
                                drawLine(bracketColor, Offset(pad, pad), Offset(pad, pad + bracketLen), strokeWidth = 3f)
                                // Top-right
                                drawLine(bracketColor, Offset(canvasW - pad, pad), Offset(canvasW - pad - bracketLen, pad), strokeWidth = 3f)
                                drawLine(bracketColor, Offset(canvasW - pad, pad), Offset(canvasW - pad, pad + bracketLen), strokeWidth = 3f)
                                // Bottom-left
                                drawLine(bracketColor, Offset(pad, canvasH - pad), Offset(pad + bracketLen, canvasH - pad), strokeWidth = 3f)
                                drawLine(bracketColor, Offset(pad, canvasH - pad), Offset(pad, canvasH - pad - bracketLen), strokeWidth = 3f)
                                // Bottom-right
                                drawLine(bracketColor, Offset(canvasW - pad, canvasH - pad), Offset(canvasW - pad - bracketLen, canvasH - pad), strokeWidth = 3f)
                                drawLine(bracketColor, Offset(canvasW - pad, canvasH - pad), Offset(canvasW - pad, canvasH - pad - bracketLen), strokeWidth = 3f)
                            }

                            // 2. Smart Inpainting Wall Cleaner Layer
                            inpaintRegions.forEach { reg ->
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            reg.sampledWallColor.copy(alpha = 0.96f),
                                            reg.sampledWallColor.copy(alpha = 0.85f),
                                            reg.sampledWallColor.copy(alpha = 0.0f)
                                        ),
                                        center = reg.center,
                                        radius = reg.radiusPx * 1.25f
                                    ),
                                    radius = reg.radiusPx * 1.25f,
                                    center = reg.center
                                )
                            }

                            // 3. Compute Calibrated Perspective Camera from Floor Quad & Room Dimensions
                            val activeQuad = quadPins.copy(
                                floorWidthMm = state.project.room.widthMm,
                                floorDepthMm = state.project.room.depthMm
                            )
                            val cam = PerspectiveCalibrator.calibrateFromFloorQuad(activeQuad, canvasW, canvasH)
                            arHitCache.calibratedCamera = cam

                            // 4. Perspective Matching Engineering Grid
                            if (showPerspectiveEngineeringGrid || mode == OverlayMode.CALIBRATE_PERSPECTIVE) {
                                drawLine(
                                    color = Color(0xFFF59E0B).copy(alpha = 0.85f),
                                    start = Offset(0f, cam.horizonY),
                                    end = Offset(canvasW, cam.horizonY),
                                    strokeWidth = 2f
                                )

                                val roomW = state.project.room.widthMm
                                val roomD = state.project.room.depthMm
                                val gridSteps = 6
                                for (i in 0..gridSteps) {
                                    val t = i.toFloat() / gridSteps
                                    val xMm = roomW * t
                                    val zMm = roomD * t
                                    val pNorth = cam.project3DToImage(Vector3(xMm, 0f, 0f))
                                    val pSouth = cam.project3DToImage(Vector3(xMm, 0f, roomD))
                                    if (pNorth != null && pSouth != null) {
                                        drawLine(
                                            color = Color(0x5538BDF8),
                                            start = pNorth,
                                            end = pSouth,
                                            strokeWidth = 1.2f
                                        )
                                    }
                                    val pWest = cam.project3DToImage(Vector3(0f, 0f, zMm))
                                    val pEast = cam.project3DToImage(Vector3(roomW, 0f, zMm))
                                    if (pWest != null && pEast != null) {
                                        drawLine(
                                            color = Color(0x4438BDF8),
                                            start = pWest,
                                            end = pEast,
                                            strokeWidth = 1.2f
                                        )
                                    }
                                }

                                if (mode == OverlayMode.CALIBRATE_PERSPECTIVE) {
                                    val quadPath = Path().apply {
                                        moveTo(quadPins.frontLeft.x, quadPins.frontLeft.y)
                                        lineTo(quadPins.frontRight.x, quadPins.frontRight.y)
                                        lineTo(quadPins.backRight.x, quadPins.backRight.y)
                                        lineTo(quadPins.backLeft.x, quadPins.backLeft.y)
                                        close()
                                    }
                                    drawPath(quadPath, color = Color(0x280284C7))
                                    drawPath(quadPath, color = Color(0xFF0284C7), style = Stroke(width = 2.5f))

                                    val pins = listOf(quadPins.frontLeft, quadPins.frontRight, quadPins.backRight, quadPins.backLeft)
                                    pins.forEach { pin ->
                                        drawCircle(Color.White, radius = 11f, center = pin)
                                        drawCircle(Color(0xFF0284C7), radius = 7.5f, center = pin)
                                    }
                                }
                            }

                            // 5. Render 3D Cabinets Depth-Sorted Back-to-Front with Realistic Details & Dimension Pills
                            val visibleElements = state.project.elements.filter { it.isVisible }
                            val sortedElements = visibleElements.map { elem ->
                                val center3D = elem.position + Vector3(0f, elem.dimensions.y * 0.5f, 0f)
                                val dist = (center3D - cam.cameraPosition).length()
                                Pair(elem, dist)
                            }.sortedByDescending { it.second }

                            arHitCache.projectedCabinetCenters.clear()
                            arHitCache.projectedCabinetBounds.clear()
                            arHitCache.frontToBackIds = sortedElements.asReversed().map { it.first.id }

                            val collidingIds = state.collisionResult?.collidingElementIds?.toSet() ?: emptySet()
                            val lightDir = Vector3(
                                x = 0.38f,
                                y = (0.85f * estimatedLightIntensity).coerceIn(0.45f, 1.0f),
                                z = 0.36f
                            ).normalized()

                            val dimLabelPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = 18f
                                isAntiAlias = true
                                textAlign = android.graphics.Paint.Align.CENTER
                                isFakeBoldText = true
                            }
                            val dimPillBgPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.argb(225, 15, 23, 42)
                                style = android.graphics.Paint.Style.FILL
                                isAntiAlias = true
                            }
                            val dimPillBorderPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.argb(245, 16, 185, 129)
                                style = android.graphics.Paint.Style.STROKE
                                strokeWidth = 2f
                                isAntiAlias = true
                            }

                            sortedElements.forEach { (elem, _) ->
                                val isSelected = elem.id in state.selectedElementIds
                                val isColliding = elem.id in collidingIds

                                val center3D = elem.position + Vector3(0f, elem.dimensions.y * 0.5f, 0f)
                                val centerScreen = cam.project3DToImage(center3D)
                                if (centerScreen != null) {
                                    arHitCache.projectedCabinetCenters[elem.id] = centerScreen
                                }

                                // Compute full 2D projected bounding box across all 8 corners for touch hit-testing
                                val aabb = elem.getAABB()
                                val corners3D = listOf(
                                    Vector3(aabb.min.x, aabb.min.y, aabb.min.z),
                                    Vector3(aabb.max.x, aabb.min.y, aabb.min.z),
                                    Vector3(aabb.max.x, aabb.min.y, aabb.max.z),
                                    Vector3(aabb.min.x, aabb.min.y, aabb.max.z),
                                    Vector3(aabb.min.x, aabb.max.y, aabb.min.z),
                                    Vector3(aabb.max.x, aabb.max.y, aabb.min.z),
                                    Vector3(aabb.max.x, aabb.max.y, aabb.max.z),
                                    Vector3(aabb.min.x, aabb.max.y, aabb.max.z)
                                )
                                var minX = Float.POSITIVE_INFINITY
                                var minY = Float.POSITIVE_INFINITY
                                var maxX = Float.NEGATIVE_INFINITY
                                var maxY = Float.NEGATIVE_INFINITY
                                var validCount = 0
                                for (c in corners3D) {
                                    val pt = cam.project3DToImage(c)
                                    if (pt != null) {
                                        minX = min(minX, pt.x)
                                        minY = min(minY, pt.y)
                                        maxX = max(maxX, pt.x)
                                        maxY = max(maxY, pt.y)
                                        validCount++
                                    }
                                }
                                if (validCount >= 2) {
                                    val pad = 12f
                                    arHitCache.projectedCabinetBounds[elem.id] = RectF(
                                        minX - pad,
                                        minY - pad,
                                        maxX + pad,
                                        maxY + pad
                                    )
                                }

                                // Render realistic 3D shaded cabinet with doors, handles, granite countertop & glass vitrines
                                renderCabinetRealisticDetails(
                                    elem = elem,
                                    cameraEye = cam.cameraPosition,
                                    lightDir = lightDir,
                                    isSelected = isSelected,
                                    project = { p: Vector3 -> cam.project3DWithDepth(p) },
                                    isColliding = isColliding
                                )

                                // Real-Time Dimension Pill on Camera 3D Cabinets
                                if ((isSelected || showAllCameraDimensions) && centerScreen != null) {
                                    val wMm = elem.dimensions.x.roundToInt()
                                    val dMm = elem.dimensions.z.roundToInt()
                                    val hMm = elem.dimensions.y.roundToInt()
                                    val labelText = if (isSelected) {
                                        "${elem.name}: ${wMm}×${dMm}×${hMm} مم (${wMm / 10}×${dMm / 10}×${hMm / 10} سم)"
                                    } else {
                                        "${wMm / 10}×${dMm / 10}×${hMm / 10} سم"
                                    }
                                    dimLabelPaint.textSize = if (isSelected) 18f else 14f
                                    val tw = dimLabelPaint.measureText(labelText)
                                    val clampedX = centerScreen.x.coerceIn(tw * 0.5f + 14f, canvasW - tw * 0.5f - 14f)
                                    val clampedY = centerScreen.y.coerceIn(42f, canvasH - 20f)
                                    val pillRect = RectF(
                                        clampedX - tw / 2f - 10f,
                                        clampedY - 32f,
                                        clampedX + tw / 2f + 10f,
                                        clampedY - 6f
                                    )
                                    dimPillBorderPaint.color = if (isSelected) {
                                        android.graphics.Color.argb(255, 16, 185, 129)
                                    } else {
                                        android.graphics.Color.argb(180, 56, 189, 248)
                                    }
                                    drawContext.canvas.nativeCanvas.drawRoundRect(pillRect, 8f, 8f, dimPillBgPaint)
                                    drawContext.canvas.nativeCanvas.drawRoundRect(pillRect, 8f, 8f, dimPillBorderPaint)
                                    drawContext.canvas.nativeCanvas.drawText(
                                        labelText,
                                        clampedX,
                                        clampedY - 13f,
                                        dimLabelPaint
                                    )
                                }
                            }
                        }

                        // Live AR Camera Active Badge (shown whenever Live Mode is active)
                        if (isLiveCameraStreamActive) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(10.dp)
                                    .testTag("live_camera_active_hud_badge"),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xCC064E3B),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF10B981))
                                    )
                                    Text(
                                        text = "وضع التصميم المباشر بالكاميرا نشط (Live Camera Design Mode • 1mm CAD)",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Interactive Floating Quick-Edit Bar when a 3D Cabinet is selected on the Camera View
                        if (selectedElement != null) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(10.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xEE0F172A),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${selectedElement.name} (${selectedElement.dimensions.x.roundToInt()}×${selectedElement.dimensions.z.roundToInt()}×${selectedElement.dimensions.y.roundToInt()} مم)",
                                        color = Color(0xFF34D399),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    // Direct X / Z / Y Nudge Controls on Camera View
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("X:", color = Color(0xFFEF4444), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        IconButton(
                                            onClick = { onMoveElement(selectedElement.id, selectedElement.position + Vector3(-50f, 0f, 0f)) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "X-", tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = { onMoveElement(selectedElement.id, selectedElement.position + Vector3(50f, 0f, 0f)) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "X+", tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Z:", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        IconButton(
                                            onClick = { onMoveElement(selectedElement.id, selectedElement.position + Vector3(0f, 0f, -50f)) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Z-", tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = { onMoveElement(selectedElement.id, selectedElement.position + Vector3(0f, 0f, 50f)) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Z+", tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Y:", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        IconButton(
                                            onClick = { onMoveElement(selectedElement.id, selectedElement.position + Vector3(0f, -50f, 0f)) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Y-", tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = { onMoveElement(selectedElement.id, selectedElement.position + Vector3(0f, 50f, 0f)) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Y+", tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { onRotateSelected(90f) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("تدوير 90°", fontSize = 10.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            onResizeSelected(
                                                selectedElement.id,
                                                (selectedElement.dimensions.x + 100f).coerceAtMost(2400f),
                                                selectedElement.dimensions.y,
                                                selectedElement.dimensions.z
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("+10سم عرض", fontSize = 10.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            onResizeSelected(
                                                selectedElement.id,
                                                (selectedElement.dimensions.x - 100f).coerceAtLeast(300f),
                                                selectedElement.dimensions.y,
                                                selectedElement.dimensions.z
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("-10سم عرض", fontSize = 10.sp)
                                    }
                                    OutlinedButton(
                                        onClick = onCycleSelectedMaterial,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("تغيير الخامة", fontSize = 10.sp)
                                    }
                                    Button(
                                        onClick = {
                                            jsonEditorText = JsonProjectExporter.serializeSingleElementToJsonString(
                                                elem = selectedElement,
                                                includeBoundingBoxes = true,
                                                prettyPrint = true
                                            )
                                            mode = OverlayMode.JSON_OBJECT_EDITOR
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .height(28.dp)
                                            .testTag("open_selected_element_json_editor_btn")
                                    ) {
                                        Icon(Icons.Default.DataObject, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("تحويل وتعديل JSON", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { mode = OverlayMode.AR_MATERIAL_SWAPPER },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .height(28.dp)
                                            .testTag("open_ar_material_swapper_btn")
                                    ) {
                                        Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("خامات AR + JSON", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(
                                        onClick = onDeleteSelected,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // AR Live Wood & Marble Swapper Overlay Bar (تعديل الخامة بالواقع المعزز مع تحديث ملف JSON فوراً)
                        if (mode == OverlayMode.AR_MATERIAL_SWAPPER) {
                            val targetElem = selectedElement ?: state.project.elements.firstOrNull()
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 44.dp, start = 10.dp, end = 10.dp)
                                    .fillMaxWidth()
                                    .testTag("ar_live_material_swapper_bar"),
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xF00F172A),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFA855F7))
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (targetElem != null) {
                                                "🎨 تعديل الخامة بالواقع المعزز للوحدة: ${targetElem.name} (يحدّث JSON فوراً)"
                                            } else {
                                                "🎨 وجّه الكاميرا واضغط على أي خزانة لتغيير الخشب أو الرخام فوراً"
                                            },
                                            color = Color(0xFFE9D5FF),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        jsonStatusMessage?.let { msg ->
                                            Text(
                                                text = msg,
                                                color = Color(0xFF34D399),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    if (targetElem != null) {
                                        // 1. Wood & Acrylic Cabinet Finishes
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("الخشب/الواجهة:", color = Color(0xFFCBD5E1), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            val woodFinishes = listOf(
                                                MaterialFinish.NATURAL_OAK,
                                                MaterialFinish.WALNUT_DARK,
                                                MaterialFinish.CHAMPAGNE_GLOSS,
                                                MaterialFinish.CHARCOAL_MATTE,
                                                MaterialFinish.WHITE_HIGH_GLOSS,
                                                MaterialFinish.CAPPUCCINO_MATTE,
                                                MaterialFinish.SAGE_GREEN,
                                                MaterialFinish.SMOKED_GLASS_ALU
                                            )
                                            woodFinishes.forEach { finish ->
                                                val isCurr = targetElem.material == finish
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isCurr) Color(0xFF4C1D95) else Color(0xFF1E293B),
                                                    border = androidx.compose.foundation.BorderStroke(
                                                        width = if (isCurr) 1.5.dp else 1.dp,
                                                        color = if (isCurr) Color(0xFFC084FC) else Color(0xFF334155)
                                                    ),
                                                    modifier = Modifier
                                                        .clickable {
                                                            onApplyArMaterialSwap(targetElem.id, finish, null)
                                                            val updatedElem = targetElem.copy(material = finish)
                                                            val syncedJson = JsonProjectExporter.serializeSingleElementToJsonString(
                                                                elem = updatedElem,
                                                                includeBoundingBoxes = true,
                                                                prettyPrint = true
                                                            )
                                                            jsonEditorText = syncedJson
                                                            try {
                                                                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                                                                File(exportDir, "AR_Cabinet_${targetElem.id}.json").writeText(syncedJson, Charsets.UTF_8)
                                                            } catch (_: Exception) {}
                                                            jsonStatusMessage = "✓ تم تطبيق (${finish.name}) وتحديث ملف JSON فوراً"
                                                        }
                                                        .testTag("ar_swap_wood_${finish.id}")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(12.dp)
                                                                .background(Color(finish.colorHex), RoundedCornerShape(3.dp))
                                                        )
                                                        Text(
                                                            text = finish.name,
                                                            color = Color.White,
                                                            fontSize = 10.sp,
                                                            fontWeight = if (isCurr) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // 2. Marble & Granite Countertop Finishes
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("الرخام/الجرانيت:", color = Color(0xFFCBD5E1), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            val marbleFinishes = listOf(
                                                MaterialFinish.CARRARA_MARBLE,
                                                MaterialFinish.BLACK_GRANITE,
                                                MaterialFinish.CALACATTA_GOLD,
                                                MaterialFinish.TERRAZZO
                                            )
                                            marbleFinishes.forEach { counterFinish ->
                                                val isCurr = targetElem.countertopMaterial == counterFinish
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isCurr) Color(0xFF065F46) else Color(0xFF1E293B),
                                                    border = androidx.compose.foundation.BorderStroke(
                                                        width = if (isCurr) 1.5.dp else 1.dp,
                                                        color = if (isCurr) Color(0xFF34D399) else Color(0xFF334155)
                                                    ),
                                                    modifier = Modifier
                                                        .clickable {
                                                            onApplyArMaterialSwap(targetElem.id, null, counterFinish)
                                                            val updatedElem = targetElem.copy(countertopMaterial = counterFinish)
                                                            val syncedJson = JsonProjectExporter.serializeSingleElementToJsonString(
                                                                elem = updatedElem,
                                                                includeBoundingBoxes = true,
                                                                prettyPrint = true
                                                            )
                                                            jsonEditorText = syncedJson
                                                            try {
                                                                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                                                                File(exportDir, "AR_Cabinet_${targetElem.id}.json").writeText(syncedJson, Charsets.UTF_8)
                                                            } catch (_: Exception) {}
                                                            jsonStatusMessage = "✓ تم تطبيق رخام (${counterFinish.name}) وتحديث ملف JSON فوراً"
                                                        }
                                                        .testTag("ar_swap_marble_${counterFinish.id}")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(12.dp)
                                                                .background(Color(counterFinish.colorHex), RoundedCornerShape(3.dp))
                                                        )
                                                        Text(
                                                            text = counterFinish.name,
                                                            color = Color.White,
                                                            fontSize = 10.sp,
                                                            fontWeight = if (isCurr) FontWeight.Bold else FontWeight.Normal
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

                    // Right Panel: Interactive JSON Object Importer & Real-Time Editor (when in JSON_OBJECT_EDITOR mode)
                    AnimatedVisibility(visible = mode == OverlayMode.JSON_OBJECT_EDITOR) {
                        Surface(
                            modifier = Modifier
                                .width(390.dp)
                                .fillMaxHeight()
                                .testTag("camera_json_object_editor_panel"),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF0B1120),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0D9488))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "استيراد وتعديل كائنات JSON بالوقت الفعلي",
                                        color = Color(0xFF2DD4BF),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Button(
                                            onClick = {
                                                val capturedBmp = try {
                                                    val pv = liveCameraPreviewRef
                                                    if (pv != null &&
                                                        pv.previewStreamState.value == PreviewView.StreamState.STREAMING &&
                                                        pv.width > 0 && pv.height > 0
                                                    ) {
                                                        pv.bitmap ?: roomPhotoBitmap
                                                    } else {
                                                        roomPhotoBitmap
                                                    }
                                                } catch (_: Exception) {
                                                    roomPhotoBitmap
                                                }
                                                val scan = com.example.core.importer.CameraObjectScanner.analyzeCapturedBitmap(
                                                    bitmap = capturedBmp
                                                )
                                                val proJson = com.example.core.importer.CameraObjectScanner.serializeScanToProfessionalJson(
                                                    analysis = scan,
                                                    prettyPrint = true
                                                )
                                                jsonEditorText = proJson
                                                onImportOrUpdateJson(proJson)
                                                jsonStatusMessage = "${scan.summaryAr} • JSON احترافي (CAD/BIM v2.0)"
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier
                                                .height(28.dp)
                                                .testTag("panel_import_via_camera_btn")
                                        ) {
                                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("عبر الكاميرا", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                jsonObjectPickerLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("ملف JSON", fontSize = 10.sp)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                jsonEditorText = JsonProjectExporter.serializeElementsListToJsonString(
                                                    elements = state.project.elements,
                                                    includeBoundingBoxes = true,
                                                    prettyPrint = true
                                                )
                                                jsonStatusMessage = "تم استخراج جميع كائنات التصميم (${state.project.elements.size}) بصيغة JSON"
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("كل العناصر (${state.project.elements.size})", fontSize = 10.sp)
                                        }
                                    }
                                }

                                // Quick Numeric & Property Editor synced to JSON
                                if (selectedElement != null) {
                                    Surface(
                                        color = Color(0xFF1E293B),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "تعديل سريع للكائن المحدد (${selectedElement.name}) وتوليد JSON:",
                                                color = Color(0xFF38BDF8),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = editWidthMmText,
                                                    onValueChange = { editWidthMmText = it },
                                                    label = { Text("العرض W (مم)", fontSize = 9.sp) },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                OutlinedTextField(
                                                    value = editDepthMmText,
                                                    onValueChange = { editDepthMmText = it },
                                                    label = { Text("العمق D (مم)", fontSize = 9.sp) },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                OutlinedTextField(
                                                    value = editHeightMmText,
                                                    onValueChange = { editHeightMmText = it },
                                                    label = { Text("الارتفاع H (مم)", fontSize = 9.sp) },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = editPosXMmText,
                                                    onValueChange = { editPosXMmText = it },
                                                    label = { Text("الموضع X (مم)", fontSize = 9.sp) },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                OutlinedTextField(
                                                    value = editPosYMmText,
                                                    onValueChange = { editPosYMmText = it },
                                                    label = { Text("الارتفاع Y (مم)", fontSize = 9.sp) },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                OutlinedTextField(
                                                    value = editPosZMmText,
                                                    onValueChange = { editPosZMmText = it },
                                                    label = { Text("الموضع Z (مم)", fontSize = 9.sp) },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            Button(
                                                onClick = {
                                                    val newW = editWidthMmText.toFloatOrNull() ?: selectedElement.dimensions.x
                                                    val newH = editHeightMmText.toFloatOrNull() ?: selectedElement.dimensions.y
                                                    val newD = editDepthMmText.toFloatOrNull() ?: selectedElement.dimensions.z
                                                    val newX = editPosXMmText.toFloatOrNull() ?: selectedElement.position.x
                                                    val newY = editPosYMmText.toFloatOrNull() ?: selectedElement.position.y
                                                    val newZ = editPosZMmText.toFloatOrNull() ?: selectedElement.position.z
                                                    val updatedElem = selectedElement.copy(
                                                        name = editNameText.ifBlank { selectedElement.name },
                                                        dimensions = Vector3(
                                                            newW.coerceIn(200f, 3600f),
                                                            newH.coerceIn(200f, 3000f),
                                                            newD.coerceIn(200f, 1800f)
                                                        ),
                                                        position = Vector3(newX, newY, newZ)
                                                    )
                                                    val newJson = JsonProjectExporter.serializeSingleElementToJsonString(
                                                        elem = updatedElem,
                                                        includeBoundingBoxes = true,
                                                        prettyPrint = true
                                                    )
                                                    jsonEditorText = newJson
                                                    onImportOrUpdateJson(newJson)
                                                    jsonStatusMessage = "تم تحديث أبعاد وموضع الكائن وتوليد كود JSON فوراً"
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(32.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text("تحديث المقاسات وتوليد JSON فوري", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // Raw JSON Code Editor Box
                                OutlinedTextField(
                                    value = jsonEditorText,
                                    onValueChange = { jsonEditorText = it },
                                    label = { Text("كود الكائن بصيغة JSON (قابل للتعديل المباشر)", fontSize = 10.sp) },
                                    textStyle = LocalTextStyle.current.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color(0xFF38BDF8)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(210.dp)
                                        .testTag("camera_json_code_editor_input")
                                )

                                jsonStatusMessage?.let { msg ->
                                    Text(
                                        text = msg,
                                        color = Color(0xFF34D399),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                // Action Buttons: Apply Edited JSON to Scene, Copy JSON, Share JSON File
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            try {
                                                val updatedCount = onImportOrUpdateJson(jsonEditorText)
                                                jsonStatusMessage = "تم تطبيق تعديلات JSON على $updatedCount عنصر في مشهد الكاميرا"
                                                Toast.makeText(context, "تم تحديث عناصر التصميم من JSON ($updatedCount)", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                jsonStatusMessage = "خطأ في صيغة JSON: ${e.localizedMessage}"
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .height(36.dp)
                                            .testTag("apply_edited_json_to_camera_btn"),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("تطبيق JSON على التصميم", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            clipboard?.setPrimaryClip(ClipData.newPlainText("KitchenObjectJSON", jsonEditorText))
                                            jsonStatusMessage = "تم نسخ كود JSON إلى الحافظة"
                                        },
                                        modifier = Modifier
                                            .weight(0.8f)
                                            .height(36.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("نسخ", fontSize = 10.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                                                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                                val outFile = File(exportDir, "KitchenObject_$timeStamp.json")
                                                outFile.writeText(jsonEditorText, Charsets.UTF_8)
                                                ShareExportHelper.shareFile(
                                                    context = context,
                                                    file = outFile,
                                                    mimeType = ShareExportHelper.MIME_JSON,
                                                    dialogTitle = "مشاركة ملف كائن المطبخ JSON"
                                                )
                                            } catch (e: Exception) {
                                                jsonStatusMessage = "تعذر تصدير الملف: ${e.localizedMessage}"
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(0.9f)
                                            .height(36.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("حفظ JSON", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom Status & Done
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (mode) {
                            OverlayMode.CALIBRATE_PERSPECTIVE ->
                                "حرّك النقاط الأربع لتطابق زوايا الأرضية وخط الأفق في الكاميرا المباشرة أو صورة المطبخ"
                            OverlayMode.SMART_INPAINTING ->
                                "امسح فوق أي خزانة قديمة أو عيب بالحائط ليقوم النظام بتنظيف الجدار وتجهيزه للتصميم"
                            OverlayMode.PLACE_CABINETS ->
                                "صمّم بالوقت الفعلي فوق الكاميرا: اضغط أو اسحب أي خزانة 3D لمشاهدة مقاساتها الفورية وتعديلها"
                            OverlayMode.IMAGE_TO_IMAGE_8K_RENDER ->
                                "رندر Image-to-Image 8K: دمج واقعي يبرز لمعان الألمنيوم وانعكاس الزجاج الأسود والظلال الطبيعية"
                            OverlayMode.JSON_OBJECT_EDITOR ->
                                "استورد كائنات بصيغة JSON أو حوّل أي خزانة في المشهد إلى كود JSON وعدّل أبعادها وخصائصها مباشرة"
                            OverlayMode.AR_MATERIAL_SWAPPER ->
                                "وجّه الكاميرا نحو أي خزانة مصممة واضغط لتبديل الخشب أو الرخام فوراً وتحديث ملف JSON الخاص بها تلقائياً"
                        },
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Text("تم / حفظ التعديلات")
                    }
                }
            }
        }
    }
}

/**
 * Real-Time CameraX Hardware Preview Layer for designing 3D kitchens over a live camera feed.
 */
@Composable
private fun LiveCameraPreviewLayer(
    onPreviewReady: (PreviewView) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            onPreviewReady(this)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        var cameraProvider: ProcessCameraProvider? = null

        val listener = Runnable {
            try {
                cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val selector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(lifecycleOwner, selector, preview)
            } catch (_: Exception) {
                // Gracefully fallback if camera hardware is unavailable in headless/test environments
            }
        }
        cameraProviderFuture.addListener(listener, executor)

        onDispose {
            try {
                cameraProvider?.unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.testTag("live_camera_preview_view")
    )
}
