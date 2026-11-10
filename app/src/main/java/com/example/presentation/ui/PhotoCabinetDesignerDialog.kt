package com.example.presentation.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.math.Vector3
import com.example.domain.model.ElementCategory
import com.example.domain.model.FinishCategory
import com.example.domain.model.KitchenElementType
import com.example.domain.model.MaterialFinish
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dedicated Custom Cabinet Designer Panel from Front & Side Photos (لوحة تصميم الخزائن من صور أمامية وجانبية 360°).
 * Allows users to upload or capture front & side photos of a cabinet, extract colors/finishes,
 * configure dimensions, divisions, full 18mm left/right side panels, and full rear back panel,
 * preview the complete 6-sided 3D cabinet from all 360° angles, and add it to the project.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoCabinetDesignerDialog(
    onCreateCustomCabinet: (
        customName: String,
        baseType: KitchenElementType,
        dimensionsMm: Vector3,
        frontMaterial: MaterialFinish,
        sideMaterial: MaterialFinish,
        countertopMaterial: MaterialFinish?,
        hasBackPanel: Boolean,
        hasFullSidePanels: Boolean,
        boardThicknessMm: Float,
        divisionsCount: Int,
        frontPhotoUri: String?,
        sidePhotoUri: String?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var customName by remember { mutableStateOf("خزانة مخصصة كاملة (أمامي + جانبي)") }
    var selectedTemplateType by remember { mutableStateOf(KitchenElementType.BASE_DOUBLE_DOOR) }

    var widthCm by remember { mutableFloatStateOf(90f) }
    var heightCm by remember { mutableFloatStateOf(85f) }
    var depthCm by remember { mutableFloatStateOf(60f) }
    var divisionsCount by remember { mutableIntStateOf(2) }
    var boardThicknessMm by remember { mutableFloatStateOf(18f) }

    var hasBackPanel by remember { mutableStateOf(true) }
    var hasFullSidePanels by remember { mutableStateOf(true) }

    var frontMaterial by remember { mutableStateOf(MaterialFinish.NATURAL_OAK) }
    var sideMaterial by remember { mutableStateOf(MaterialFinish.WALNUT_DARK) }
    var countertopMaterial by remember { mutableStateOf<MaterialFinish?>(MaterialFinish.CALACATTA_GOLD) }

    var frontPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sidePhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var frontPhotoUriStr by remember { mutableStateOf<String?>(null) }
    var sidePhotoUriStr by remember { mutableStateOf<String?>(null) }

    // Interactive 360° preview camera angles
    var previewYawDeg by remember { mutableFloatStateOf(38f) }
    var previewPitchDeg by remember { mutableFloatStateOf(22f) }

    // Helper to extract dominant color from a Bitmap and create a custom MaterialFinish
    fun extractMaterialFromBitmap(bmp: Bitmap, labelPrefix: String, isFront: Boolean): MaterialFinish {
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0L
        val stepX = (bmp.width / 16).coerceAtLeast(1)
        val stepY = (bmp.height / 16).coerceAtLeast(1)
        for (x in (bmp.width / 6) until (bmp.width * 5 / 6) step stepX) {
            for (y in (bmp.height / 6) until (bmp.height * 5 / 6) step stepY) {
                val pixel = bmp.getPixel(x, y)
                rSum += (pixel shr 16) and 0xFF
                gSum += (pixel shr 8) and 0xFF
                bSum += pixel and 0xFF
                count++
            }
        }
        if (count == 0L) return if (isFront) MaterialFinish.NATURAL_OAK else MaterialFinish.WALNUT_DARK
        val r = (rSum / count).coerceIn(25L, 240L)
        val g = (gSum / count).coerceIn(25L, 240L)
        val b = (bSum / count).coerceIn(25L, 240L)
        val hex = (0xFF000000L) or (r shl 16) or (g shl 8) or b
        return MaterialFinish(
            id = "photo_${if (isFront) "front" else "side"}_${UUID.randomUUID().toString().take(5)}",
            name = "$labelPrefix (#${java.lang.Long.toHexString(hex).takeLast(6).uppercase()})",
            category = FinishCategory.WOOD,
            colorHex = hex,
            roughness = 0.42f,
            metallic = 0.04f
        )
    }

    // Zero-permission Photo Picker for Front Photo
    val frontPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        frontPhotoBitmap = bmp
                        frontPhotoUriStr = it.toString()
                        frontMaterial = extractMaterialFromBitmap(bmp, "خامة الواجهة الأمامية", true)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    // Camera capture for Front Photo
    val frontCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null) {
            frontPhotoBitmap = bmp
            frontPhotoUriStr = "camera://front_${System.currentTimeMillis()}"
            frontMaterial = extractMaterialFromBitmap(bmp, "خامة الكاميرا الأمامية", true)
        }
    }

    // Zero-permission Photo Picker for Side Photo
    val sidePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        sidePhotoBitmap = bmp
                        sidePhotoUriStr = it.toString()
                        sideMaterial = extractMaterialFromBitmap(bmp, "خامة الجنب الكامل", false)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    // Camera capture for Side Photo
    val sideCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null) {
            sidePhotoBitmap = bmp
            sidePhotoUriStr = "camera://side_${System.currentTimeMillis()}"
            sideMaterial = extractMaterialFromBitmap(bmp, "خامة الجنب من الكاميرا", false)
        }
    }

    val templateOptions = remember {
        listOf(
            KitchenElementType.BASE_DOUBLE_DOOR to "خزانة سفلية قياسية",
            KitchenElementType.BASE_CORNER_L to "كورنر سفلي زاوية L",
            KitchenElementType.BASE_CORNER_DIAGONAL_90 to "كورنر سفلي مشطوف 45°",
            KitchenElementType.BASE_3_DRAWERS_90 to "خزانة سفلية أدراج",
            KitchenElementType.WALL_DOUBLE_DOOR to "خزانة علوية جدارية",
            KitchenElementType.WALL_CORNER_L to "كورنر علوي زاوية L",
            KitchenElementType.WALL_CORNER_DIAGONAL_65 to "كورنر علوي مشطوف 45°",
            KitchenElementType.TALL_COFFEE_STATION_90 to "دولاب عمودي / ركن قهوة"
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .testTag("photo_cabinet_designer_dialog"),
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.ViewInAr, contentDescription = null, tint = Color(0xFFFBBF24))
                            }
                        }
                        Column {
                            Text(
                                text = "لوحة تصميم الخزائن من صور أمامية وجانبية (360° Complete Cabinet)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "استيراد صورة أمامية وجانبية • ظهر كامل وجنب كامل 18 مم لتكون الخزانة مكتملة من جميع الجهات",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_photo_cabinet_designer")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Live 360° 6-Face Complete Cabinet Preview Canvas
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "معاينة ثلاثية الأبعاد 360° للخزانة المكتملة (اسحب للتدوير وفحص الظهر والجنب):",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "زاوية العرض: ${previewYawDeg.toInt()}°",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Quick Angle Buttons to inspect all 6 faces
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    Triple("منظور 360°", 38f, 22f),
                                    Triple("الواجهة الأمامية (0°)", 0f, 10f),
                                    Triple("الجنب الأيمن الكامل (90°)", 90f, 12f),
                                    Triple("الظهر الكامل الخلفي (180°)", 180f, 12f),
                                    Triple("الجنب الأيسر الكامل (270°)", 270f, 12f)
                                ).forEach { (label, yaw, pitch) ->
                                    FilterChip(
                                        selected = kotlin.math.abs(previewYawDeg - yaw) < 12f,
                                        onClick = {
                                            previewYawDeg = yaw
                                            previewPitchDeg = pitch
                                        },
                                        label = { Text(label, fontSize = 10.sp) }
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF090D16))
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            previewYawDeg = (previewYawDeg + dragAmount.x * 0.6f) % 360f
                                            if (previewYawDeg < 0f) previewYawDeg += 360f
                                            previewPitchDeg = (previewPitchDeg + dragAmount.y * 0.35f).coerceIn(-25f, 65f)
                                        }
                                    }
                                    .testTag("photo_cabinet_360_canvas")
                            ) {
                                Complete360CabinetPreviewCanvas(
                                    widthCm = widthCm,
                                    heightCm = heightCm,
                                    depthCm = depthCm,
                                    frontColor = Color(frontMaterial.colorHex),
                                    sideColor = Color(sideMaterial.colorHex),
                                    countertopColor = countertopMaterial?.let { Color(it.colorHex) },
                                    hasBackPanel = hasBackPanel,
                                    hasFullSidePanels = hasFullSidePanels,
                                    divisionsCount = divisionsCount,
                                    isCorner = selectedTemplateType.isCornerUnit,
                                    yawDeg = previewYawDeg,
                                    pitchDeg = previewPitchDeg
                                )

                                // Status Badges on Preview
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = if (hasBackPanel) Color(0xFF065F46).copy(alpha = 0.9f) else Color(0xFF7F1D1D).copy(alpha = 0.9f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (hasBackPanel) "✓ ظهر كامل 8 مم محكم" else "بدون ظهر",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Surface(
                                        color = if (hasFullSidePanels) Color(0xFF0284C7).copy(alpha = 0.9f) else Color(0xFF475569).copy(alpha = 0.9f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (hasFullSidePanels) "✓ جنب كامل يمين ويسار (${boardThicknessMm.toInt()} مم)" else "جنب قياسي",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Front & Side Photo Upload / Capture Cards (من صور أمامية وجانبية)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Front Photo Card
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "1. الصورة الأمامية (Front View)",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(84.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0F172A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (frontPhotoBitmap != null) {
                                        Image(
                                            bitmap = frontPhotoBitmap!!.asImageBitmap(),
                                            contentDescription = "صورة أمامية للخزانة",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.PhotoCameraFront, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(28.dp))
                                            Text("تحديد لون وتقسيم الواجهة", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            frontPhotoPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).testTag("pick_front_photo_btn")
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("معرض الصور", fontSize = 10.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { frontCameraLauncher.launch(null) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).testTag("camera_front_photo_btn")
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("الكاميرا", fontSize = 10.sp)
                                    }
                                }
                            }
                        }

                        // Side Photo Card
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "2. الصورة الجانبية (Side Panel)",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(84.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0F172A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (sidePhotoBitmap != null) {
                                        Image(
                                            bitmap = sidePhotoBitmap!!.asImageBitmap(),
                                            contentDescription = "صورة جانبية للخزانة",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.ViewSidebar, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(28.dp))
                                            Text("تحديد خامة الجنب والعمق", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            sidePhotoPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).testTag("pick_side_photo_btn")
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("معرض الصور", fontSize = 10.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { sideCameraLauncher.launch(null) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).testTag("camera_side_photo_btn")
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("الكاميرا", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }

                    // 3. Structural Enclosure Options (جعل الخزائن أكثر واقعية بأن يكون لها ظهر وجنب كامل)
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "هيكل الإغلاق الواقعي 360° (ظهر كامل + أجناب كاملة من جميع الجهات):",
                                color = Color(0xFF34D399),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = hasBackPanel,
                                    onClick = { hasBackPanel = !hasBackPanel },
                                    label = { Text("ظهر كامل للخزانة (Full Back Panel)", fontSize = 11.sp) },
                                    leadingIcon = {
                                        Icon(
                                            if (hasBackPanel) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("toggle_full_back_panel")
                                )
                                FilterChip(
                                    selected = hasFullSidePanels,
                                    onClick = { hasFullSidePanels = !hasFullSidePanels },
                                    label = { Text("جنب كامل يمين ويسار (Full Side Gables)", fontSize = 11.sp) },
                                    leadingIcon = {
                                        Icon(
                                            if (hasFullSidePanels) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("toggle_full_side_panels")
                                )
                            }
                        }
                    }

                    // 4. Cabinet Type, Name & Dimensions (العرض × الارتفاع × العمق)
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customName,
                                onValueChange = { customName = it },
                                label = { Text("اسم الخزانة المخصصة في المشروع والـ BOM", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("custom_cabinet_name_input")
                            )

                            Text("نوع وفئة الخزانة (عادية أو كورنر علوي/سفلي):", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                templateOptions.forEach { (type, label) ->
                                    FilterChip(
                                        selected = selectedTemplateType == type,
                                        onClick = {
                                            selectedTemplateType = type
                                            widthCm = type.defaultDimensions.x / 10f
                                            heightCm = type.defaultDimensions.y / 10f
                                            depthCm = type.defaultDimensions.z / 10f
                                            customName = "خزانة مصممة - $label (${widthCm.toInt()}×${depthCm.toInt()} سم)"
                                        },
                                        label = { Text(label, fontSize = 11.sp) }
                                    )
                                }
                            }

                            // Dimension Sliders
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("العرض: ${widthCm.toInt()} سم", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Slider(
                                        value = widthCm,
                                        onValueChange = { widthCm = it },
                                        valueRange = 15f..160f
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("الارتفاع: ${heightCm.toInt()} سم", color = Color(0xFF34D399), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Slider(
                                        value = heightCm,
                                        onValueChange = { heightCm = it },
                                        valueRange = 30f..240f
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("العمق (الجنب): ${depthCm.toInt()} سم", color = Color(0xFFFBBF24), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Slider(
                                        value = depthCm,
                                        onValueChange = { depthCm = it },
                                        valueRange = 25f..110f
                                    )
                                }
                            }

                            // Divisions & Front/Side Material Swatches
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("عدد الدرف / الأدراج الأمامية:", color = Color.White, fontSize = 11.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    (1..4).forEach { count ->
                                        FilterChip(
                                            selected = divisionsCount == count,
                                            onClick = { divisionsCount = count },
                                            label = { Text("$count", fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }

                            Text("خامة الواجهة الأمامية:", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MaterialFinish.ALL_FINISHES.take(12).forEach { mat ->
                                    val isSel = frontMaterial.id == mat.id
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(mat.colorHex))
                                            .border(
                                                width = if (isSel) 2.5.dp else 1.dp,
                                                color = if (isSel) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.4f),
                                                shape = CircleShape
                                            )
                                            .clickable { frontMaterial = mat }
                                    )
                                }
                            }

                            Text("خامة الجنب الكامل والظهر (Side & Back Material):", color = Color(0xFFFBBF24), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MaterialFinish.ALL_FINISHES.take(12).forEach { mat ->
                                    val isSel = sideMaterial.id == mat.id
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(mat.colorHex))
                                            .border(
                                                width = if (isSel) 2.5.dp else 1.dp,
                                                color = if (isSel) Color(0xFFF59E0B) else Color.White.copy(alpha = 0.4f),
                                                shape = CircleShape
                                            )
                                            .clickable { sideMaterial = mat }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.35f).height(48.dp)
                    ) {
                        Text("إلغاء", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            val dimsMm = Vector3(widthCm * 10f, heightCm * 10f, depthCm * 10f)
                            val cTop = if (selectedTemplateType.category == ElementCategory.BASE_CABINET) countertopMaterial else null
                            onCreateCustomCabinet(
                                customName.ifBlank { selectedTemplateType.title },
                                selectedTemplateType,
                                dimsMm,
                                frontMaterial,
                                sideMaterial,
                                cTop,
                                hasBackPanel,
                                hasFullSidePanels,
                                boardThicknessMm,
                                divisionsCount,
                                frontPhotoUriStr,
                                sidePhotoUriStr
                            )
                            Toast.makeText(
                                context,
                                "تم تصميم وإدراج الخزانة الكاملة (بظهر وجنب كامل 360°) في المطبخ!",
                                Toast.LENGTH_SHORT
                            ).show()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(0.65f)
                            .height(48.dp)
                            .testTag("insert_photo_designed_cabinet_btn")
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("إدراج الخزانة الكاملة (360°) في التصميم", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * Renders an interactive 360° 3D projection of the cabinet with all 6 faces sorted back-to-front:
 * Front Doors, Rear Full Back Panel (ظهر كامل مع مجرى التثبيت), Left & Right Full Side Panels (جنب كامل 18 مم),
 * Top Countertop/Stretcher, and Bottom Carcass/Plinth.
 */
@Composable
private fun Complete360CabinetPreviewCanvas(
    widthCm: Float,
    heightCm: Float,
    depthCm: Float,
    frontColor: Color,
    sideColor: Color,
    countertopColor: Color?,
    hasBackPanel: Boolean,
    hasFullSidePanels: Boolean,
    divisionsCount: Int,
    isCorner: Boolean,
    yawDeg: Float,
    pitchDeg: Float
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.54f
        val maxDim = maxOf(widthCm, heightCm, depthCm).coerceAtLeast(40f)
        val scale = (minOf(size.width, size.height) * 0.58f) / maxDim

        val hw = widthCm * 0.5f * scale
        val hh = heightCm * 0.5f * scale
        val hd = depthCm * 0.5f * scale

        val yawRad = Math.toRadians(yawDeg.toDouble()).toFloat()
        val pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat()
        val cosY = cos(yawRad)
        val sinY = sin(yawRad)
        val cosP = cos(pitchRad)
        val sinP = sin(pitchRad)

        fun project(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
            val rx = x * cosY - z * sinY
            val rz = x * sinY + z * cosY
            val ry = y * cosP - rz * sinP
            val depthZ = y * sinP + rz * cosP
            val persp = 1f + (depthZ / 900f).coerceIn(-0.25f, 0.25f)
            return Triple(cx + rx * persp, cy - ry * persp, depthZ)
        }

        // 8 vertices of the cabinet box:
        // 0: (-hw, -hh, -hd) Back-Bottom-Left
        // 1: (+hw, -hh, -hd) Back-Bottom-Right
        // 2: (+hw, +hh, -hd) Back-Top-Right
        // 3: (-hw, +hh, -hd) Back-Top-Left
        // 4: (-hw, -hh, +hd) Front-Bottom-Left
        // 5: (+hw, -hh, +hd) Front-Bottom-Right
        // 6: (+hw, +hh, +hd) Front-Top-Right
        // 7: (-hw, +hh, +hd) Front-Top-Left
        val pts = listOf(
            project(-hw, -hh, -hd),
            project(+hw, -hh, -hd),
            project(+hw, +hh, -hd),
            project(-hw, +hh, -hd),
            project(-hw, -hh, +hd),
            project(+hw, -hh, +hd),
            project(+hw, +hh, +hd),
            project(-hw, +hh, +hd)
        )

        data class PreviewFace(
            val id: String,
            val indices: List<Int>,
            val color: Color,
            val avgZ: Float
        )

        val backColor = if (hasBackPanel) {
            sideColor.copy(
                red = (sideColor.red * 0.78f).coerceIn(0f, 1f),
                green = (sideColor.green * 0.78f).coerceIn(0f, 1f),
                blue = (sideColor.blue * 0.78f).coerceIn(0f, 1f)
            )
        } else {
            Color(0xFF1E293B).copy(alpha = 0.25f)
        }

        val gableColor = if (hasFullSidePanels) {
            sideColor
        } else {
            sideColor.copy(alpha = 0.75f)
        }

        val topCol = countertopColor ?: sideColor.copy(
            red = (sideColor.red * 1.1f).coerceIn(0f, 1f),
            green = (sideColor.green * 1.1f).coerceIn(0f, 1f),
            blue = (sideColor.blue * 1.1f).coerceIn(0f, 1f)
        )

        val faces = listOf(
            PreviewFace("BACK", listOf(0, 1, 2, 3), backColor, (pts[0].third + pts[1].third + pts[2].third + pts[3].third) * 0.25f),
            PreviewFace("FRONT", listOf(4, 5, 6, 7), frontColor, (pts[4].third + pts[5].third + pts[6].third + pts[7].third) * 0.25f),
            PreviewFace("LEFT", listOf(0, 4, 7, 3), gableColor.copy(red = gableColor.red * 0.88f, green = gableColor.green * 0.88f, blue = gableColor.blue * 0.88f), (pts[0].third + pts[4].third + pts[7].third + pts[3].third) * 0.25f),
            PreviewFace("RIGHT", listOf(5, 1, 2, 6), gableColor, (pts[5].third + pts[1].third + pts[2].third + pts[6].third) * 0.25f),
            PreviewFace("BOTTOM", listOf(0, 1, 5, 4), Color(0xFF0F172A), (pts[0].third + pts[1].third + pts[5].third + pts[4].third) * 0.25f),
            PreviewFace("TOP", listOf(7, 6, 2, 3), topCol, (pts[7].third + pts[6].third + pts[2].third + pts[3].third) * 0.25f)
        ).sortedBy { it.avgZ } // Painter's algorithm: furthest face first, closest face last

        faces.forEach { face ->
            val p0 = Offset(pts[face.indices[0]].first, pts[face.indices[0]].second)
            val p1 = Offset(pts[face.indices[1]].first, pts[face.indices[1]].second)
            val p2 = Offset(pts[face.indices[2]].first, pts[face.indices[2]].second)
            val p3 = Offset(pts[face.indices[3]].first, pts[face.indices[3]].second)

            val path = Path().apply {
                moveTo(p0.x, p0.y)
                lineTo(p1.x, p1.y)
                lineTo(p2.x, p2.y)
                lineTo(p3.x, p3.y)
                close()
            }
            drawPath(path = path, color = face.color)
            drawPath(path = path, color = Color(0xFF090D16), style = Stroke(width = 2f))

            // Draw realistic panel details on FRONT, BACK, and FULL SIDES
            when (face.id) {
                "FRONT" -> {
                    val divs = divisionsCount.coerceIn(1, 4)
                    for (i in 1 until divs) {
                        val t = i.toFloat() / divs.toFloat()
                        val topSplit = Offset(p3.x + (p2.x - p3.x) * t, p3.y + (p2.y - p3.y) * t)
                        val botSplit = Offset(p0.x + (p1.x - p0.x) * t, p0.y + (p1.y - p0.y) * t)
                        drawLine(Color(0xFF0F172A), topSplit, botSplit, strokeWidth = 2.2f)
                    }
                    // Handles
                    val handleStart = Offset(
                        (p0.x + p1.x) * 0.5f,
                        p3.y + (p0.y - p3.y) * 0.25f
                    )
                    val handleEnd = Offset(
                        (p0.x + p1.x) * 0.5f,
                        p3.y + (p0.y - p3.y) * 0.52f
                    )
                    drawLine(Color(0xFFE2E8F0), handleStart, handleEnd, strokeWidth = 3f)
                    if (isCorner) {
                        drawLine(Color(0xFF38BDF8), p3, p1, strokeWidth = 1.6f)
                    }
                }
                "BACK" -> {
                    if (hasBackPanel) {
                        // Draw router rabbet groove inset (مجرى ظهر الخزانة الخلفي 10 مم)
                        val inset = 0.08f
                        val g0 = Offset(p0.x + (p2.x - p0.x) * inset, p0.y + (p2.y - p0.y) * inset)
                        val g1 = Offset(p1.x + (p3.x - p1.x) * inset, p1.y + (p3.y - p1.y) * inset)
                        val g2 = Offset(p2.x + (p0.x - p2.x) * inset, p2.y + (p0.y - p2.y) * inset)
                        val g3 = Offset(p3.x + (p1.x - p3.x) * inset, p3.y + (p1.y - p3.y) * inset)
                        val groovePath = Path().apply {
                            moveTo(g0.x, g0.y)
                            lineTo(g1.x, g1.y)
                            lineTo(g2.x, g2.y)
                            lineTo(g3.x, g3.y)
                            close()
                        }
                        drawPath(groovePath, color = Color(0xFF38BDF8).copy(alpha = 0.55f), style = Stroke(width = 1.6f))
                    }
                }
                "LEFT", "RIGHT" -> {
                    if (hasFullSidePanels) {
                        // Draw 18mm structural gable perimeter edge banding highlight
                        drawPath(path = path, color = Color(0xFFFBBF24).copy(alpha = 0.45f), style = Stroke(width = 2.6f))
                    }
                }
            }
        }
    }
}
