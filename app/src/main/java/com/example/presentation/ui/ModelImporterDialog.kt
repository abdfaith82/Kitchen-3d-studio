package com.example.presentation.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.export.JsonProjectExporter
import com.example.core.export.ShareExportHelper
import com.example.core.importer.*
import com.example.core.math.Vector3
import com.example.domain.model.KitchenElement
import com.example.domain.model.KitchenElementType
import com.example.domain.model.MaterialFinish
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Import source choice modes inside the unified Importer Dialog:
 * 1. CAMERA_SCAN: Import object & material directly via device Camera (Live CameraX Scanner or Snapshot)
 * 2. FILE_STORAGE: Import 3D/CAD/JSON files (*.glb, *.gltf, *.obj, *.dxf, *.json)
 * 3. PHOTO_GALLERY: Import object & material from a photo in the gallery (Zero-Permission PhotoPicker)
 */
enum class ImportSourceChoice(val titleAr: String, val subtitleAr: String) {
    CAMERA_SCAN("استيراد عبر الكاميرا 📷", "مسح خزانة أو جهاز بالكاميرا وتحويله لكائن 3D و JSON"),
    FILE_STORAGE("استيراد من ملف 📁", "ملفات GLB, glTF, OBJ, DXF, JSON"),
    PHOTO_GALLERY("استيراد من صورة بالمعرض 🖼️", "تحليل صورة مطبخ أو قطعة وتحويلها لكائن 3D")
}

@Composable
fun ModelImporterDialog(
    onAddModelToScene: (KitchenElement) -> Unit,
    onImportJsonToScene: (String) -> Int = { 0 },
    onAddCustomCameraMaterial: (MaterialFinish) -> Unit = {},
    onOpenArCameraStudio: (() -> Unit)? = null,
    initialSourceChoice: ImportSourceChoice = ImportSourceChoice.CAMERA_SCAN,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedSourceChoice by remember { mutableStateOf(initialSourceChoice) }
    var isLoading by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<ImportResult<Any>?>(null) }
    var importedMesh by remember { mutableStateOf<ImportedMesh?>(null) }
    var importedDxf by remember { mutableStateOf<DxfDrawing?>(null) }
    var importedElements by remember { mutableStateOf<List<KitchenElement>>(emptyList()) }

    // Camera Import state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var isLiveScannerActive by remember { mutableStateOf(false) }
    var pendingLiveScannerPermission by remember { mutableStateOf(false) }
    var livePreviewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var capturedCameraBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedCameraCategory by remember {
        mutableStateOf(CameraObjectScanner.CameraImportPresetCategory.AUTO_DETECT)
    }
    var latestCameraAnalysis by remember { mutableStateOf<CameraObjectScanner.CameraScanAnalysis?>(null) }

    // Interactive Object-to-JSON Editor state
    var activeElement by remember { mutableStateOf<KitchenElement?>(null) }
    var editableJsonString by remember { mutableStateOf("") }
    var editName by remember { mutableStateOf("") }
    var editWidthMm by remember { mutableStateOf("800") }
    var editHeightMm by remember { mutableStateOf("900") }
    var editDepthMm by remember { mutableStateOf("600") }
    var editPosXMm by remember { mutableStateOf("1800") }
    var editPosYMm by remember { mutableStateOf("0") }
    var editPosZMm by remember { mutableStateOf("1200") }
    var editRotYDeg by remember { mutableStateOf("0") }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    fun syncEditorFromElement(elem: KitchenElement) {
        activeElement = elem
        editName = elem.name
        editWidthMm = elem.dimensions.x.roundToInt().toString()
        editHeightMm = elem.dimensions.y.roundToInt().toString()
        editDepthMm = elem.dimensions.z.roundToInt().toString()
        editPosXMm = elem.position.x.roundToInt().toString()
        editPosYMm = elem.position.y.roundToInt().toString()
        editPosZMm = elem.position.z.roundToInt().toString()
        editRotYDeg = elem.rotationY.roundToInt().toString()
        editableJsonString = JsonProjectExporter.serializeSingleElementToJsonString(
            elem = elem,
            includeBoundingBoxes = true,
            prettyPrint = true
        )
    }

    fun applyCameraBitmapImport(bmp: Bitmap?, sourceLabel: String) {
        isLoading = true
        try {
            if (bmp != null) {
                capturedCameraBitmap = bmp
            }
            val analysis = CameraObjectScanner.analyzeCapturedBitmap(
                bitmap = bmp ?: capturedCameraBitmap,
                presetCategory = selectedCameraCategory
            )
            latestCameraAnalysis = analysis
            importedMesh = analysis.generatedMesh
            importedDxf = null
            onAddCustomCameraMaterial(analysis.customCameraMaterial)
            importResult = ImportResult.Success(
                data = analysis.generatedMesh,
                stats = ImportStats(
                    fileName = sourceLabel,
                    format = "CAMERA 3D ➔ JSON",
                    fileSizeBytes = (bmp?.byteCount ?: 4096).toLong(),
                    vertexCount = analysis.generatedMesh.vertexCount,
                    triangleCount = analysis.generatedMesh.triangleCount,
                    dimensionsMm = analysis.element.dimensions,
                    loadDurationMs = 8L
                )
            )
            syncEditorFromElement(analysis.element)
            statusBanner = analysis.summaryAr
        } catch (e: Exception) {
            importResult = ImportResult.Error("تعذر تحليل صورة الكاميرا: ${e.localizedMessage}", e)
        } finally {
            isLoading = false
        }
    }

    // 1. Camera Snapshot Capture Launcher
    val cameraSnapshotLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            isLiveScannerActive = false
            applyCameraBitmapImport(bitmap, "CameraCapture_${System.currentTimeMillis()}.jpg")
        }
    }

    // 2. Runtime Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            if (pendingLiveScannerPermission) {
                isLiveScannerActive = true
                pendingLiveScannerPermission = false
            } else {
                cameraSnapshotLauncher.launch(null)
            }
        } else {
            pendingLiveScannerPermission = false
            statusBanner = "يرجى منح صلاحية الكاميرا لاستيراد الكائنات عبر الكاميرا، أو استخدم الماسح التقديري المباشر"
        }
    }

    // 3. Zero-Permission Photo Picker for Gallery Import Choice
    val galleryPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val decoded = BitmapFactory.decodeStream(stream)
                    if (decoded != null) {
                        isLiveScannerActive = false
                        applyCameraBitmapImport(decoded, "GalleryImport_${System.currentTimeMillis()}.jpg")
                    }
                }
            } catch (e: Exception) {
                importResult = ImportResult.Error("تعذر قراءة الصورة من المعرض: ${e.localizedMessage}", e)
            }
        }
    }

    // 4. File Storage Picker (GLB, glTF, OBJ, DXF, JSON)
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isLoading = true
            try {
                var fileName = "model"
                var fileSize = 0L

                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx >= 0) fileName = cursor.getString(nameIdx)
                        if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                    }
                }

                val ext = fileName.substringAfterLast('.', "").lowercase()

                context.contentResolver.openInputStream(uri)?.use { stream ->
                    when (ext) {
                        "json" -> {
                            val rawText = stream.bufferedReader(Charsets.UTF_8).readText()
                            val parsedList = JsonProjectExporter.parseElementsFromJsonString(rawText)
                            if (parsedList.isNotEmpty()) {
                                importedElements = parsedList
                                val firstElem = parsedList.first()
                                importResult = ImportResult.Success(
                                    data = parsedList,
                                    stats = ImportStats(
                                        fileName = fileName,
                                        format = "JSON",
                                        fileSizeBytes = fileSize,
                                        vertexCount = parsedList.size * 8,
                                        triangleCount = parsedList.size * 12,
                                        dimensionsMm = firstElem.dimensions,
                                        loadDurationMs = 4L
                                    )
                                )
                                syncEditorFromElement(firstElem)
                                if (parsedList.size > 1) {
                                    editableJsonString = JsonProjectExporter.serializeElementsListToJsonString(
                                        elements = parsedList,
                                        includeBoundingBoxes = true,
                                        prettyPrint = true
                                    )
                                }
                                statusBanner = "تم استيراد ${parsedList.size} كائن من ملف JSON — يمكنك تعديل خصائصه أو كود JSON قبل الإدراج"
                            } else {
                                editableJsonString = rawText
                                statusBanner = "تم فتح ملف JSON — يمكنك تعديل الكود وتطبيقه على التصميم"
                            }
                        }
                        "dxf" -> {
                            val res = ModelImporter.importDxf(stream, fileName, fileSize)
                            importResult = res
                            if (res is ImportResult.Success) {
                                importedDxf = res.data
                                val dxfElem = KitchenElement(
                                    id = "dxf_obj_${System.currentTimeMillis()}",
                                    name = fileName.substringBeforeLast('.'),
                                    type = KitchenElementType.BASE_DOUBLE_DOOR,
                                    position = Vector3(1800f, 0f, 1200f),
                                    dimensions = Vector3(
                                        res.data.bounds.extents.x.coerceIn(300f, 2400f),
                                        900f,
                                        res.data.bounds.extents.z.coerceIn(300f, 1200f)
                                    )
                                )
                                syncEditorFromElement(dxfElem)
                                statusBanner = "تم تحويل مخطط DXF إلى كائن JSON قابل للتعديل المباشر"
                            }
                        }
                        else -> {
                            val res = ModelImporter.import3DModel(stream, fileName, fileSize)
                            importResult = res
                            if (res is ImportResult.Success) {
                                importedMesh = res.data
                                val newElem = ModelImporter.createKitchenElementFromMesh(
                                    res.data,
                                    Vector3(2000f, 0f, 1500f)
                                )
                                syncEditorFromElement(newElem)
                                statusBanner = "تم تحويل المجسم ثلاثي الأبعاد تلقائياً إلى كائن JSON قابل للتعديل"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                importResult = ImportResult.Error("حدث خطأ أثناء فتح الملف: ${e.localizedMessage}", e)
            } finally {
                isLoading = false
            }
        }
    }

    // Initialize with a default editable JSON object if none loaded yet
    LaunchedEffect(Unit) {
        if (activeElement == null && editableJsonString.isBlank()) {
            val sampleElem = KitchenElement(
                id = "custom_json_${System.currentTimeMillis()}",
                name = "خزانة مخصصة JSON",
                type = KitchenElementType.BASE_DOUBLE_DOOR,
                position = Vector3(1800f, 0f, 600f),
                dimensions = Vector3(800f, 900f, 600f)
            )
            syncEditorFromElement(sampleElem)
        }
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
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "استيراد الكائنات عبر الكاميرا أو الملفات (Camera & 3D/JSON Importer)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Text(
                            text = "اختر طريقة الاستيراد: عبر الكاميرا مباشرة 📷 أو من ملف (GLB, OBJ, DXF, JSON) 📁 أو من المعرض 🖼️ مع التحويل الفوري إلى كائن 3D و JSON قابل للتعديل",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8))
                    }
                }

                // Import Source Choice Selector Bar (Camera / File / Gallery)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ImportSourceChoice.values().forEach { choice ->
                        val isSelected = selectedSourceChoice == choice
                        val activeColor = when (choice) {
                            ImportSourceChoice.CAMERA_SCAN -> Color(0xFF10B981)
                            ImportSourceChoice.FILE_STORAGE -> Color(0xFF0284C7)
                            ImportSourceChoice.PHOTO_GALLERY -> Color(0xFFF59E0B)
                        }
                        val icon = when (choice) {
                            ImportSourceChoice.CAMERA_SCAN -> Icons.Default.PhotoCamera
                            ImportSourceChoice.FILE_STORAGE -> Icons.Default.FolderOpen
                            ImportSourceChoice.PHOTO_GALLERY -> Icons.Default.PhotoLibrary
                        }
                        val tag = when (choice) {
                            ImportSourceChoice.CAMERA_SCAN -> "import_choice_camera_btn"
                            ImportSourceChoice.FILE_STORAGE -> "import_choice_file_btn"
                            ImportSourceChoice.PHOTO_GALLERY -> "import_choice_gallery_btn"
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) activeColor.copy(alpha = 0.22f) else Color(0xFF1E293B),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) activeColor else Color(0xFF334155)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedSourceChoice = choice }
                                .testTag(tag)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = choice.titleAr,
                                    tint = if (isSelected) activeColor else Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = choice.titleAr,
                                        color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = choice.subtitleAr,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Source-Specific Action Panel
                when (selectedSourceChoice) {
                    ImportSourceChoice.CAMERA_SCAN -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF1E293B),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("camera_import_panel")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                            Icons.Default.CameraEnhance,
                                            contentDescription = null,
                                            tint = Color(0xFF34D399),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "الاستيراد الذكي عبر الكاميرا (Camera 3D & Material Scanner)",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (onOpenArCameraStudio != null) {
                                        TextButton(
                                            onClick = onOpenArCameraStudio,
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier
                                                .height(28.dp)
                                                .testTag("open_ar_camera_studio_from_importer_btn")
                                        ) {
                                            Icon(
                                                Icons.Default.ViewInAr,
                                                contentDescription = null,
                                                tint = Color(0xFFF59E0B),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("فتح كاميرا AR الكاملة", color = Color(0xFFFBBF24), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // Target Category Filter Chips for Camera Scan
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("نوع الكائن الممسوح:", color = Color(0xFFCBD5E1), fontSize = 10.sp)
                                    CameraObjectScanner.CameraImportPresetCategory.values().forEach { cat ->
                                        FilterChip(
                                            selected = selectedCameraCategory == cat,
                                            onClick = {
                                                selectedCameraCategory = cat
                                                if (capturedCameraBitmap != null || latestCameraAnalysis != null) {
                                                    applyCameraBitmapImport(
                                                        capturedCameraBitmap,
                                                        "CameraScan_${cat.name}.jpg"
                                                    )
                                                }
                                            },
                                            label = { Text(cat.titleAr, fontSize = 10.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFF0D9488),
                                                selectedLabelColor = Color.White
                                            ),
                                            modifier = Modifier.testTag("camera_import_cat_${cat.name}")
                                        )
                                    }
                                }

                                // Primary Camera Import Buttons Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // 1. Capture Snapshot via Camera
                                    Button(
                                        onClick = {
                                            val granted = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.CAMERA
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (granted) {
                                                hasCameraPermission = true
                                                cameraSnapshotLauncher.launch(null)
                                            } else {
                                                pendingLiveScannerPermission = false
                                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        modifier = Modifier
                                            .weight(1.1f)
                                            .height(42.dp)
                                            .testTag("import_via_camera_btn")
                                    ) {
                                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("التقاط عبر الكاميرا", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // 2. Toggle Live CameraX Scanner Viewfinder
                                    FilledTonalButton(
                                        onClick = {
                                            if (isLiveScannerActive) {
                                                isLiveScannerActive = false
                                            } else {
                                                val granted = ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.CAMERA
                                                ) == PackageManager.PERMISSION_GRANTED
                                                if (granted) {
                                                    hasCameraPermission = true
                                                    isLiveScannerActive = true
                                                } else {
                                                    pendingLiveScannerPermission = true
                                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (isLiveScannerActive) Color(0xFFDC2626) else Color(0xFF0284C7),
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .weight(1.1f)
                                            .height(42.dp)
                                            .testTag("toggle_live_camera_import_scanner_btn")
                                    ) {
                                        Icon(
                                            if (isLiveScannerActive) Icons.Default.VideocamOff else Icons.Default.QrCodeScanner,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            if (isLiveScannerActive) "إيقاف الماسح المباشر" else "الماسح المباشر بالكاميرا",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // 3. Instant Calibrated Scan (also works in emulator / without physical camera)
                                    OutlinedButton(
                                        onClick = {
                                            val liveBitmap = try {
                                                livePreviewViewRef?.bitmap
                                            } catch (_: Exception) {
                                                null
                                            }
                                            applyCameraBitmapImport(
                                                liveBitmap ?: capturedCameraBitmap,
                                                "InstantCameraScan_${System.currentTimeMillis()}.jpg"
                                            )
                                        },
                                        border = BorderStroke(1.dp, Color(0xFF34D399)),
                                        modifier = Modifier
                                            .weight(0.95f)
                                            .height(42.dp)
                                            .testTag("instant_camera_scan_to_json_btn")
                                    ) {
                                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("مسح فوري 3D", fontSize = 10.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Live CameraX Scanner Viewfinder Box
                                AnimatedVisibility(visible = isLiveScannerActive) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(190.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF020617))
                                            .border(1.5.dp, Color(0xFF10B981), RoundedCornerShape(12.dp))
                                            .testTag("live_camera_scanner_viewfinder")
                                    ) {
                                        if (hasCameraPermission) {
                                            val lifecycleOwner = LocalLifecycleOwner.current
                                            AndroidView(
                                                factory = { ctx ->
                                                    PreviewView(ctx).apply {
                                                        layoutParams = ViewGroup.LayoutParams(
                                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                                            ViewGroup.LayoutParams.MATCH_PARENT
                                                        )
                                                        scaleType = PreviewView.ScaleType.FILL_CENTER
                                                        livePreviewViewRef = this
                                                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                                        cameraProviderFuture.addListener({
                                                            try {
                                                                val provider = cameraProviderFuture.get()
                                                                val preview = Preview.Builder().build().also {
                                                                    it.setSurfaceProvider(surfaceProvider)
                                                                }
                                                                provider.unbindAll()
                                                                provider.bindToLifecycle(
                                                                    lifecycleOwner,
                                                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                                                    preview
                                                                )
                                                            } catch (_: Exception) {
                                                            }
                                                        }, ContextCompat.getMainExecutor(ctx))
                                                    }
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }

                                        // CAD Reticle Overlay
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val padX = size.width * 0.18f
                                            val padY = size.height * 0.15f
                                            val rectW = size.width - padX * 2f
                                            val rectH = size.height - padY * 2f
                                            drawRect(
                                                color = Color(0xFF10B981),
                                                topLeft = Offset(padX, padY),
                                                size = Size(rectW, rectH),
                                                style = Stroke(
                                                    width = 2.5f,
                                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
                                                )
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                val bmp = try {
                                                    livePreviewViewRef?.bitmap
                                                } catch (_: Exception) {
                                                    null
                                                }
                                                applyCameraBitmapImport(
                                                    bmp,
                                                    "LiveCameraScan_${System.currentTimeMillis()}.jpg"
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = 8.dp)
                                                .height(34.dp)
                                                .testTag("capture_from_live_scanner_btn")
                                        ) {
                                            Icon(Icons.Default.Camera, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("مسح واستيراد الكائن الآن 📸", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // Captured Camera Image & Extracted Material/Dimensions Preview
                                latestCameraAnalysis?.let { scan ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF0F172A),
                                        border = BorderStroke(1.dp, Color(0xFF334155)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("camera_scan_result_summary_card")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            // Thumbnail or Color Swatch Preview
                                            Box(
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(scan.sampledColorHex))
                                                    .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(8.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                capturedCameraBitmap?.let { bmp ->
                                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                                        drawImage(
                                                            image = bmp.asImageBitmap(),
                                                            dstOffset = IntOffset.Zero,
                                                            dstSize = IntSize(size.width.toInt(), size.height.toInt())
                                                        )
                                                        val b = scan.boundingBoxNormalized
                                                        drawRect(
                                                            color = Color(0xFF10B981),
                                                            topLeft = Offset(size.width * b[0], size.height * b[1]),
                                                            size = Size(
                                                                size.width * (b[2] - b[0]),
                                                                size.height * (b[3] - b[1])
                                                            ),
                                                            style = Stroke(width = 2f)
                                                        )
                                                    }
                                                } ?: Icon(
                                                    Icons.Default.ViewInAr,
                                                    contentDescription = null,
                                                    tint = Color.White
                                                )
                                            }

                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = scan.element.name,
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "الأبعاد المستنتجة (1مم): ${scan.element.dimensions.x.roundToInt()}×${scan.element.dimensions.y.roundToInt()}×${scan.element.dimensions.z.roundToInt()} مم",
                                                    color = Color(0xFF38BDF8),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(12.dp)
                                                            .background(Color(scan.sampledColorHex), CircleShape)
                                                            .border(1.dp, Color.White, CircleShape)
                                                    )
                                                    Text(
                                                        text = "الخامة: ${scan.matchedMaterial.name}",
                                                        color = Color(0xFF34D399),
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    ImportSourceChoice.FILE_STORAGE -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { filePicker.launch("*/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("pick_3d_file_btn")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("استيراد ملف كائن (*.json, *.glb, *.obj, *.dxf)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    ImportSourceChoice.PHOTO_GALLERY -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    galleryPhotoLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("pick_gallery_photo_for_import_btn")
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "اختيار صورة كائن أو مطبخ من المعرض وتحويلها إلى 3D & JSON",
                                    fontSize = 11.sp,
                                    color = Color(0xFF0F172A),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Always keep pick_3d_file_btn accessible in compact row if user is on Camera tab so existing file workflows & tests work seamlessly
                if (selectedSourceChoice != ImportSourceChoice.FILE_STORAGE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { filePicker.launch("*/*") },
                            border = BorderStroke(1.dp, Color(0xFF0284C7)),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("pick_3d_file_btn")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("أو استيراد ملف كائن (*.json, *.glb, *.obj, *.dxf)", fontSize = 10.sp, color = Color(0xFF38BDF8))
                        }
                    }
                }

                // Loading State
                if (isLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color(0xFF38BDF8))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("جاري تحليل الكائن وتحويله إلى هيكل JSON قابل للتعديل...", color = Color.White, fontSize = 12.sp)
                    }
                }

                // Error State
                if (importResult is ImportResult.Error) {
                    val err = importResult as ImportResult.Error
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFFCA5A5))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = err.message, color = Color(0xFFFCA5A5), fontSize = 11.sp)
                        }
                    }
                }

                // Mesh Stats Card (when 3D model or Camera 3D Mesh loaded)
                importedMesh?.let { mesh ->
                    val stats = (importResult as? ImportResult.Success<*>)?.stats
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(mesh.name, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8), fontSize = 13.sp)
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "${stats?.format ?: "3D"} ➔ JSON",
                                        color = Color(0xFF34D399),
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "النقاط: ${mesh.vertexCount} • المثلثات: ${mesh.triangleCount} • الأبعاد الأصلية: ${mesh.aabb.extents.x.toInt()}×${mesh.aabb.extents.y.toInt()}×${mesh.aabb.extents.z.toInt()} مم",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // DXF Stats Card (when DXF loaded)
                importedDxf?.let { dxf ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("${dxf.fileName} (CAD DXF ➔ JSON)", fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8), fontSize = 13.sp)
                            Text(
                                text = "إجمالي الخطوط: ${dxf.lines.size} • الطبقات: ${dxf.layers.joinToString(", ")} • الأبعاد: ${dxf.bounds.extents.x.toInt()} × ${dxf.bounds.extents.z.toInt()} مم",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Interactive Object-to-JSON Converter & Editor Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0B1120),
                    border = BorderStroke(1.dp, Color(0xFF0D9488)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("imported_object_json_editor_card")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "محرر خصائص وكود الكائن بصيغة JSON (Import & Edit Object JSON):",
                                color = Color(0xFF2DD4BF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = {
                                    val base = activeElement ?: KitchenElement(
                                        id = "custom_json_${System.currentTimeMillis()}",
                                        name = "خزانة مخصصة JSON",
                                        type = KitchenElementType.BASE_DOUBLE_DOOR,
                                        position = Vector3(1800f, 0f, 600f)
                                    )
                                    val updated = base.copy(
                                        name = editName.ifBlank { base.name },
                                        dimensions = Vector3(
                                            (editWidthMm.toFloatOrNull() ?: base.dimensions.x).coerceIn(200f, 3600f),
                                            (editHeightMm.toFloatOrNull() ?: base.dimensions.y).coerceIn(200f, 3000f),
                                            (editDepthMm.toFloatOrNull() ?: base.dimensions.z).coerceIn(200f, 1800f)
                                        ),
                                        position = Vector3(
                                            editPosXMm.toFloatOrNull() ?: base.position.x,
                                            editPosYMm.toFloatOrNull() ?: base.position.y,
                                            editPosZMm.toFloatOrNull() ?: base.position.z
                                        ),
                                        rotationY = editRotYDeg.toFloatOrNull() ?: base.rotationY
                                    )
                                    syncEditorFromElement(updated)
                                    statusBanner = "تم تحديث كود JSON من حقول المقاسات والإحداثيات"
                                },
                                modifier = Modifier.testTag("sync_fields_to_json_btn")
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تحديث الكود من الأبعاد", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Structured Parameter Fields (Name, W, H, D, X, Y, Z, Rot)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text("اسم الكائن", fontSize = 9.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1.4f).testTag("json_obj_name_input")
                            )
                            OutlinedTextField(
                                value = editWidthMm,
                                onValueChange = { editWidthMm = it },
                                label = { Text("العرض W (مم)", fontSize = 9.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("json_obj_width_input")
                            )
                            OutlinedTextField(
                                value = editDepthMm,
                                onValueChange = { editDepthMm = it },
                                label = { Text("العمق D (مم)", fontSize = 9.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("json_obj_depth_input")
                            )
                            OutlinedTextField(
                                value = editHeightMm,
                                onValueChange = { editHeightMm = it },
                                label = { Text("الارتفاع H (مم)", fontSize = 9.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("json_obj_height_input")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = editPosXMm,
                                onValueChange = { editPosXMm = it },
                                label = { Text("الموضع X (مم)", fontSize = 9.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = editPosYMm,
                                onValueChange = { editPosYMm = it },
                                label = { Text("الارتفاع Y (مم)", fontSize = 9.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = editPosZMm,
                                onValueChange = { editPosZMm = it },
                                label = { Text("الموضع Z (مم)", fontSize = 9.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = editRotYDeg,
                                onValueChange = { editRotYDeg = it },
                                label = { Text("الدوران °", fontSize = 9.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(0.9f)
                            )
                        }

                        // Editable Raw JSON Text Field
                        OutlinedTextField(
                            value = editableJsonString,
                            onValueChange = { editableJsonString = it },
                            label = { Text("كود الكائن بصيغة JSON (عدّل القيم مباشرة أو استورد/صدّر)", fontSize = 10.sp) },
                            textStyle = LocalTextStyle.current.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFF38BDF8)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(165.dp)
                                .testTag("importer_json_editor_input")
                        )

                        statusBanner?.let { msg ->
                            Text(
                                text = msg,
                                color = Color(0xFF34D399),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Action Buttons: Insert/Update from Edited JSON, Copy JSON, Save/Share JSON File
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    try {
                                        val parsed = JsonProjectExporter.parseElementsFromJsonString(editableJsonString)
                                        if (parsed.isNotEmpty()) {
                                            if (importedMesh != null && parsed.size == 1) {
                                                val meshElem = ModelImporter.createKitchenElementFromMesh(
                                                    importedMesh!!,
                                                    parsed.first().position
                                                ).copy(
                                                    name = parsed.first().name,
                                                    type = latestCameraAnalysis?.element?.type ?: parsed.first().type,
                                                    dimensions = parsed.first().dimensions,
                                                    rotationY = parsed.first().rotationY,
                                                    material = latestCameraAnalysis?.customCameraMaterial ?: parsed.first().material,
                                                    countertopMaterial = parsed.first().countertopMaterial,
                                                    hasBackPanel = parsed.first().hasBackPanel
                                                )
                                                onAddModelToScene(meshElem)
                                            } else {
                                                val count = onImportJsonToScene(editableJsonString)
                                                if (count == 0) {
                                                    parsed.forEach { onAddModelToScene(it) }
                                                }
                                            }
                                            Toast.makeText(context, "تم إدراج الكائن المعدل بصيغة JSON في التصميم", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        } else {
                                            val count = onImportJsonToScene(editableJsonString)
                                            if (count > 0) {
                                                onDismiss()
                                            } else {
                                                statusBanner = "يرجى التأكد من صحة صيغة كود JSON قبل الإدراج"
                                            }
                                        }
                                    } catch (e: Exception) {
                                        statusBanner = "خطأ في قراءة JSON: ${e.localizedMessage}"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(42.dp)
                                    .testTag("add_imported_mesh_btn")
                            ) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("إدراج الكائن المعدل في المطبخ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("ImportedObjectJSON", editableJsonString))
                                    statusBanner = "تم نسخ كود JSON للكائن إلى الحافظة"
                                },
                                modifier = Modifier
                                    .weight(0.75f)
                                    .height(42.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("نسخ JSON", fontSize = 10.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    try {
                                        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                        val cleanName = (editName.ifBlank { "KitchenObject" }).replace("\\s+".toRegex(), "_")
                                        val jsonFile = File(exportDir, "${cleanName}_$timeStamp.json")
                                        jsonFile.writeText(editableJsonString, Charsets.UTF_8)
                                        ShareExportHelper.shareFile(
                                            context = context,
                                            file = jsonFile,
                                            mimeType = ShareExportHelper.MIME_JSON,
                                            dialogTitle = "مشاركة ملف كائن المطبخ JSON"
                                        )
                                    } catch (e: Exception) {
                                        statusBanner = "خطأ في تصدير ملف JSON: ${e.localizedMessage}"
                                    }
                                },
                                modifier = Modifier
                                    .weight(0.85f)
                                    .height(42.dp)
                                    .testTag("export_converted_object_json_btn")
                            ) {
                                Icon(Icons.Default.DataObject, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تصدير .json", fontSize = 10.sp)
                            }
                        }
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("إغلاق", color = Color(0xFF94A3B8))
                }
            }
        }
    }
}
