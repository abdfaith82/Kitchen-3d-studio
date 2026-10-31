package com.example.presentation.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.domain.model.FinishCategory
import com.example.domain.model.MaterialFinish

enum class MaterialTarget(val title: String) {
    CABINET("الخزائن المحددة"),
    COUNTERTOP("أسطح العمل والرخام"),
    FLOOR("أرضية المطبخ"),
    WALL("طلاء الجدران")
}

/**
 * Extracts the average perceptual color from a captured camera or gallery Bitmap
 * so the user can photograph real melamine/wood/aluminum/marble samples and apply them to 3D cabinets.
 */
private fun extractDominantColorHex(bitmap: Bitmap): Long {
    val w = bitmap.width.coerceAtLeast(1)
    val h = bitmap.height.coerceAtLeast(1)
    val startX = (w * 0.25f).toInt()
    val endX = (w * 0.75f).toInt().coerceAtLeast(startX + 1)
    val startY = (h * 0.25f).toInt()
    val endY = (h * 0.75f).toInt().coerceAtLeast(startY + 1)
    val stepX = ((endX - startX) / 12).coerceAtLeast(1)
    val stepY = ((endY - startY) / 12).coerceAtLeast(1)

    var rSum = 0L
    var gSum = 0L
    var bSum = 0L
    var count = 0L

    var y = startY
    while (y < endY) {
        var x = startX
        while (x < endX) {
            val pixel = bitmap.getPixel(x, y)
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            rSum += r
            gSum += g
            bSum += b
            count++
            x += stepX
        }
        y += stepY
    }

    if (count == 0L) return 0xFFD4A373
    val avgR = (rSum / count).coerceIn(0, 255)
    val avgG = (gSum / count).coerceIn(0, 255)
    val avgB = (bSum / count).coerceIn(0, 255)
    return (0xFFL shl 24) or (avgR shl 16) or (avgG shl 8) or avgB
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialPickerSheet(
    customCameraMaterials: List<MaterialFinish> = emptyList(),
    onAddCustomCameraMaterial: (MaterialFinish) -> Unit = {},
    onApplyCabinetMaterial: (MaterialFinish) -> Unit,
    onApplyCountertopMaterial: (MaterialFinish) -> Unit,
    onApplyFloorMaterial: (MaterialFinish) -> Unit,
    onApplyWallFinish: (MaterialFinish) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var target by remember { mutableStateOf(MaterialTarget.CABINET) }
    var capturedTextureBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var customMaterialName by remember { mutableStateOf("خامة ميلامين/خشب مصورة بالكاميرا") }

    val applyFinishToTarget: (MaterialFinish) -> Unit = { finish ->
        when (target) {
            MaterialTarget.CABINET -> onApplyCabinetMaterial(finish)
            MaterialTarget.COUNTERTOP -> onApplyCountertopMaterial(finish)
            MaterialTarget.FLOOR -> onApplyFloorMaterial(finish)
            MaterialTarget.WALL -> onApplyWallFinish(finish)
        }
    }

    val handleCapturedBitmap: (Bitmap) -> Unit = { bmp ->
        capturedTextureBitmap = bmp
        val hex = extractDominantColorHex(bmp)
        val newMat = MaterialFinish(
            id = "cam_mat_${System.currentTimeMillis()}",
            name = "$customMaterialName (#${java.lang.Long.toHexString(hex and 0xFFFFFF).uppercase()})",
            colorHex = hex,
            roughness = 0.25f,
            metallic = 0.1f,
            category = FinishCategory.WOOD
        )
        onAddCustomCameraMaterial(newMat)
        applyFinishToTarget(newMat)
    }

    // Camera live capture launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        bmp?.let { handleCapturedBitmap(it) }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraLauncher.launch(null)
        }
    }

    // Zero-permission Photo Picker for gallery texture images
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { bmp ->
                        handleCapturedBitmap(bmp)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    val allFinishes = remember(customCameraMaterials) {
        customCameraMaterials + MaterialFinish.ALL_FINISHES
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "مكتبة الخامات وتصوير العينات بالكاميرا",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White
                    )
                    Text(
                        text = "صوّر أي عينة ميلامين أو خشب أو رخام بالكاميرا لتطبيق لونها وخامتها مباشرة على الخزائن 3D",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Camera & Gallery Texture Capture Card (Inspired by Reference Video 2)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        capturedTextureBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "عينة مصورة",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.5.dp, Color(0xFF10B981), RoundedCornerShape(8.dp))
                            )
                        } ?: Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFF38BDF8))
                        }

                        Column {
                            Text(
                                text = "إدخال خامة حقيقية بالكاميرا",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = if (capturedTextureBitmap != null)
                                    "تم التقاط الخامة وتطبيقها على ${target.title} ✓"
                                else
                                    "التقط صورة للون الميلامين أو الرخام من الواقع",
                                color = if (capturedTextureBitmap != null) Color(0xFF34D399) else Color(0xFF94A3B8),
                                fontSize = 10.sp
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    cameraLauncher.launch(null)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("capture_camera_material_btn")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تصوير بالكاميرا", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("pick_gallery_material_btn")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("المعرض", fontSize = 11.sp, color = Color(0xFF38BDF8))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Target selector (Cabinet / Countertop / Floor / Wall)
            Text("تطبيق الخامة على:", color = Color(0xFF94A3B8), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MaterialTarget.values().forEach { t ->
                    FilterChip(
                        selected = target == t,
                        onClick = { target = t },
                        label = { Text(t.title, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0284C7),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFF94A3B8)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Finishes List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
            ) {
                items(allFinishes) { finish ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E293B),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (finish.id.startsWith("cam_mat_")) Color(0xFF10B981) else Color(0xFF334155)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                applyFinishToTarget(finish)
                                onDismiss()
                            }
                            .testTag("finish_item_${finish.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(finish.colorHex))
                                    .border(1.5.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = finish.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = if (finish.id.startsWith("cam_mat_")) "خامة مخصصة ملتقطة بالكاميرا" else finish.category.title,
                                    fontSize = 11.sp,
                                    color = if (finish.id.startsWith("cam_mat_")) Color(0xFF34D399) else Color(0xFF94A3B8)
                                )
                            }
                            Text(
                                text = "تطبيق",
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
