package com.example.presentation.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.example.core.export.DxfExporter
import com.example.core.export.ImageExporter
import com.example.core.export.JsonProjectExporter
import com.example.core.export.PdfBlueprintExporter
import com.example.core.export.ShareExportHelper
import com.example.core.packaging.RoomPackageManifest
import com.example.core.packaging.RoomProjectPackager
import com.example.domain.repository.KitchenProject
import com.example.presentation.viewmodel.KitchenEditorViewModel
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportTab(val title: String, val icon: ImageVector) {
    PDF_BLUEPRINT("مخطط 2D و PDF", Icons.Default.PictureAsPdf),
    DXF_CAD("هندسي DXF", Icons.Default.Architecture),
    JSON_DATA("ملفات وعناصر JSON", Icons.Default.DataObject),
    ROOM_PACKAGE("حزمة .room", Icons.Default.Inventory2),
    JPG_SNAPSHOT("صورة للطباعة", Icons.Default.Image)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportStudioDialog(
    viewModel: KitchenEditorViewModel,
    onDismiss: () -> Unit,
    initialTab: ExportTab = ExportTab.PDF_BLUEPRINT
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember(initialTab) { mutableStateOf(initialTab) }

    // Section 6: Layer Separation & PDF Configuration state
    var includeBlueprint by remember { mutableStateOf(true) }
    var includeBom by remember { mutableStateOf(true) }
    var includeCutList by remember { mutableStateOf(true) }
    var includeStructuralLayer by remember { mutableStateOf(true) }
    var includeMepUtilitiesLayer by remember { mutableStateOf(true) }
    var includeCabinetsLayer by remember { mutableStateOf(true) }
    var useCentimeters by remember { mutableStateOf(true) }
    var workshopNameText by remember(state.workshopName) { mutableStateOf(state.workshopName) }

    var isProcessing by remember { mutableStateOf(false) }
    var exportStatusMessage by remember { mutableStateOf<String?>(null) }
    var lastImportedManifest by remember { mutableStateOf<RoomPackageManifest?>(null) }

    // Section 6: Custom Workshop Logo Picker (إضافة شعار ورشتك يدوياً)
    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        viewModel.updateWorkshopBranding(workshopNameText, bmp)
                        exportStatusMessage = "تمت إضافة شعار الورشة للمخطط التنفيذي بنجاح"
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    // File Picker for importing .room archive
    val roomFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                isProcessing = true
                val unpacked = RoomProjectPackager.importFromUri(context, uri)
                viewModel.applyImportedProject(unpacked.project)
                lastImportedManifest = unpacked.manifest
                exportStatusMessage = "تم استيراد المشروع بنجاح: ${unpacked.project.name} (${unpacked.project.elements.size} وحدة)"
                Toast.makeText(context, "تم استيراد وقراءة المشروع أوفلاين بنجاح!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                exportStatusMessage = "خطأ أثناء قراءة الحزمة: ${e.localizedMessage}"
                Toast.makeText(context, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isProcessing = false
            }
        }
    }

    // File Picker for importing .json project or elements file
    val jsonFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                isProcessing = true
                val jsonText = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.readBytes().toString(StandardCharsets.UTF_8)
                } ?: throw IllegalArgumentException("تعذر قراءة ملف JSON المحدد")
                val importedProject = viewModel.importProjectOrElementsFromJson(jsonText)
                exportStatusMessage = "تم استيراد ملف وعناصر JSON بنجاح: ${importedProject.name} (${importedProject.elements.size} عنصر)"
                Toast.makeText(context, "تم استيراد عناصر وبيانات JSON بنجاح!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                exportStatusMessage = "خطأ أثناء استيراد ملف JSON: ${e.localizedMessage}"
                Toast.makeText(context, "خطأ JSON: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isProcessing = false
            }
        }
    }

    val currentPdfConfig = remember(
        includeBlueprint, includeBom, includeCutList,
        includeStructuralLayer, includeMepUtilitiesLayer, includeCabinetsLayer,
        useCentimeters, workshopNameText, state.workshopLogoBitmap
    ) {
        PdfBlueprintExporter.PdfExportConfig(
            includeBlueprint = includeBlueprint,
            includeBom = includeBom,
            includeCutList = includeCutList,
            previewBitmap = null,
            includeStructuralAndOpeningsLayer = includeStructuralLayer,
            includeMepUtilitiesLayer = includeMepUtilitiesLayer,
            includeCabinetsLayer = includeCabinetsLayer,
            useCentimeters = useCentimeters,
            workshopName = workshopNameText.ifBlank { "ورشة التصميم الهندسي للمطابخ الذكية" },
            workshopLogoBitmap = state.workshopLogoBitmap
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
                .clip(RoundedCornerShape(20.dp)),
            color = Color(0xFF0F172A),
            tonalElevation = 12.dp
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
                            color = Color(0xFF0284C7).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF38BDF8))
                            }
                        }
                        Column {
                            Text(
                                text = "6. نظام استخراج المخطط الهندسي والتنفيذي (2D Blueprint)",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "فصل طبقات المخطط بالسنتمتر (الإنشائي + الكهرباء والسباكة والغاز) • إضافة شعار ورشتك يدوياً",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val apkFile = ShareExportHelper.exportAndShareInstalledApk(context)
                                    val sizeMb = String.format(Locale.US, "%.1f", apkFile.length() / (1024f * 1024f))
                                    exportStatusMessage = "تم استخراج ملف التطبيق Kitchen3DStudio_Release.apk ($sizeMb MB) وجاهز للحفظ والمشاركة"
                                } catch (e: Exception) {
                                    exportStatusMessage = "تعذر تصدير ملف الـ APK المباشر: ${e.localizedMessage}"
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF34D399)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_export_apk_package")
                        ) {
                            Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تصدير ملف APK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_export_dialog")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Navigation Tabs
                PrimaryTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color(0xFF38BDF8),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    ExportTab.values().forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            text = { Text(tab.title, fontSize = 11.sp, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1E293B).copy(alpha = 0.6f))
                        .padding(16.dp)
                ) {
                    when (selectedTab) {
                        ExportTab.PDF_BLUEPRINT -> {
                            PdfBlueprintExportSection(
                                workshopName = workshopNameText,
                                onWorkshopNameChange = {
                                    workshopNameText = it
                                    viewModel.updateWorkshopBranding(it, state.workshopLogoBitmap)
                                },
                                workshopLogoBitmap = state.workshopLogoBitmap,
                                onPickWorkshopLogo = { logoPickerLauncher.launch("image/*") },
                                includeStructuralLayer = includeStructuralLayer,
                                onToggleStructuralLayer = { includeStructuralLayer = it },
                                includeMepUtilitiesLayer = includeMepUtilitiesLayer,
                                onToggleMepUtilitiesLayer = { includeMepUtilitiesLayer = it },
                                includeCabinetsLayer = includeCabinetsLayer,
                                onToggleCabinetsLayer = { includeCabinetsLayer = it },
                                useCentimeters = useCentimeters,
                                onToggleUseCentimeters = { useCentimeters = it },
                                includeBlueprint = includeBlueprint,
                                onToggleBlueprint = { includeBlueprint = it },
                                includeBom = includeBom,
                                onToggleBom = { includeBom = it },
                                includeCutList = includeCutList,
                                onToggleCutList = { includeCutList = it },
                                isProcessing = isProcessing,
                                onGeneratePdf = {
                                    isProcessing = true
                                    try {
                                        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                        val cleanName = state.project.name.replace("\\s+".toRegex(), "_")
                                        val pdfFile = File(exportDir, "${cleanName}_BLUEPRINT_$timeStamp.pdf")

                                        val bom = viewModel.getBillOfMaterials()
                                        val cabinets = viewModel.getParametricCabinetsFromScene()

                                        PdfBlueprintExporter.exportToPdfFile(
                                            file = pdfFile,
                                            project = state.project,
                                            bom = bom,
                                            cabinets = cabinets,
                                            config = currentPdfConfig
                                        )

                                        exportStatusMessage = "تم توليد المخطط التنفيذي PDF بالسنتمتر مع شعار الورشة (${pdfFile.length() / 1024} كيلوبايت)"
                                        ShareExportHelper.shareFile(
                                            context = context,
                                            file = pdfFile,
                                            mimeType = ShareExportHelper.MIME_PDF,
                                            dialogTitle = "مشاركة المخطط التنفيذي PDF"
                                        )
                                    } catch (e: Exception) {
                                        exportStatusMessage = "خطأ في إنشاء PDF: ${e.localizedMessage}"
                                        Toast.makeText(context, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isProcessing = false
                                    }
                                },
                                onGeneratePrintReadyImage = {
                                    isProcessing = true
                                    try {
                                        val highResBmp = PdfBlueprintExporter.renderHighResBlueprintBitmap(
                                            project = state.project,
                                            config = currentPdfConfig
                                        )
                                        val jpgFile = ImageExporter.exportBitmapToJpgFile(
                                            context = context,
                                            bitmap = highResBmp,
                                            projectName = "${state.project.name}_2D_Blueprint"
                                        )
                                        exportStatusMessage = "تم تصدير صورة المخطط التنفيذي عالية الجودة جاهزة للطباعة (${jpgFile.length() / 1024} كيلوبايت)"
                                        ShareExportHelper.shareFile(
                                            context = context,
                                            file = jpgFile,
                                            mimeType = ShareExportHelper.MIME_JPEG,
                                            dialogTitle = "مشاركة صورة المخطط التنفيذي للطباعة"
                                        )
                                    } catch (e: Exception) {
                                        exportStatusMessage = "خطأ في تصدير صورة المخطط: ${e.localizedMessage}"
                                    } finally {
                                        isProcessing = false
                                    }
                                }
                            )
                        }

                        ExportTab.DXF_CAD -> {
                            DxfCadExportSection(
                                viewModel = viewModel,
                                project = state.project,
                                isProcessing = isProcessing,
                                onExportDxfWithOptions = { dxfOptions ->
                                    isProcessing = true
                                    try {
                                        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                        val cleanName = state.project.name.replace("\\s+".toRegex(), "_")
                                        val dxfFile = File(exportDir, "${cleanName}_CAD_$timeStamp.dxf")

                                        val result = viewModel.exportFloorPlanToDxfFile(dxfFile, dxfOptions)

                                        exportStatusMessage = "تم تسلسل وتصدير مخطط 2D إلى ملف DXF بنجاح (${result.totalEntitiesCount} كيان هندسي • ${dxfFile.length() / 1024} كيلوبايت)"
                                        ShareExportHelper.shareFile(
                                            context = context,
                                            file = dxfFile,
                                            mimeType = ShareExportHelper.MIME_DXF,
                                            dialogTitle = "مشاركة ملف CAD DXF"
                                        )
                                    } catch (e: Exception) {
                                        exportStatusMessage = "خطأ في إنشاء DXF: ${e.localizedMessage}"
                                        Toast.makeText(context, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isProcessing = false
                                    }
                                }
                            )
                        }

                        ExportTab.JSON_DATA -> {
                            JsonDataExportSection(
                                viewModel = viewModel,
                                project = state.project,
                                isProcessing = isProcessing,
                                onExportJsonWithOptions = { jsonOptions ->
                                    isProcessing = true
                                    try {
                                        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                        val cleanName = state.project.name.replace("\\s+".toRegex(), "_")
                                        val jsonFile = File(exportDir, "${cleanName}_${jsonOptions.exportMode.fileSuffix}_$timeStamp.json")

                                        val result = viewModel.exportProjectAndElementsToJsonFile(jsonFile, jsonOptions)

                                        exportStatusMessage = "تم استخراج وتصدير ملف JSON بنجاح (${result.exportedElementsCount} عنصر • ${String.format(Locale.US, "%.1f", jsonFile.length() / 1024f)} KB)"
                                        ShareExportHelper.shareFile(
                                            context = context,
                                            file = jsonFile,
                                            mimeType = ShareExportHelper.MIME_JSON,
                                            dialogTitle = "مشاركة ملف وعناصر المطبخ JSON"
                                        )
                                    } catch (e: Exception) {
                                        exportStatusMessage = "خطأ في تصدير ملف JSON: ${e.localizedMessage}"
                                        Toast.makeText(context, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isProcessing = false
                                    }
                                },
                                onCopyJsonToClipboard = { jsonString, label ->
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText(label, jsonString))
                                    exportStatusMessage = "تم نسخ كود JSON ($label) إلى الحافظة بنجاح!"
                                    Toast.makeText(context, "تم نسخ كود JSON إلى الحافظة", Toast.LENGTH_SHORT).show()
                                },
                                onImportJsonFile = {
                                    jsonFilePickerLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                }
                            )
                        }

                        ExportTab.ROOM_PACKAGE -> {
                            RoomPackageSection(
                                project = state.project,
                                lastImportedManifest = lastImportedManifest,
                                isProcessing = isProcessing,
                                onExportRoom = {
                                    isProcessing = true
                                    try {
                                        val roomFile = RoomProjectPackager.exportProjectToRoomPackage(
                                            context = context,
                                            project = state.project,
                                            thumbnailBitmap = null,
                                            enableEncryption = true
                                        )
                                        exportStatusMessage = "تم إنشاء حزمة .room بنجاح (${roomFile.length() / 1024} كيلوبايت)"
                                        ShareExportHelper.shareFile(
                                            context = context,
                                            file = roomFile,
                                            mimeType = ShareExportHelper.MIME_ROOM,
                                            dialogTitle = "مشاركة حزمة مشروع المطبخ .room"
                                        )
                                    } catch (e: Exception) {
                                        exportStatusMessage = "خطأ في تصدير الحزمة: ${e.localizedMessage}"
                                        Toast.makeText(context, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isProcessing = false
                                    }
                                },
                                onImportRoom = {
                                    roomFilePickerLauncher.launch(arrayOf("*/*"))
                                }
                            )
                        }

                        ExportTab.JPG_SNAPSHOT -> {
                            JpgSnapshotSection(
                                project = state.project,
                                isProcessing = isProcessing,
                                onExportJpg = {
                                    isProcessing = true
                                    try {
                                        val highResBmp = PdfBlueprintExporter.renderHighResBlueprintBitmap(
                                            project = state.project,
                                            config = currentPdfConfig
                                        )
                                        val jpgFile = ImageExporter.exportBitmapToJpgFile(
                                            context = context,
                                            bitmap = highResBmp,
                                            projectName = state.project.name
                                        )
                                        exportStatusMessage = "تم حفظ صورة المخطط التنفيذي عالية الجودة للطباعة (${jpgFile.length() / 1024} كيلوبايت)"
                                        ShareExportHelper.shareFile(
                                            context = context,
                                            file = jpgFile,
                                            mimeType = ShareExportHelper.MIME_JPEG,
                                            dialogTitle = "مشاركة صورة المخطط التنفيذي JPG"
                                        )
                                    } catch (e: Exception) {
                                        exportStatusMessage = "خطأ في تصدير الصورة: ${e.localizedMessage}"
                                        Toast.makeText(context, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isProcessing = false
                                    }
                                }
                            )
                        }
                    }
                }

                if (isProcessing) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("جاري المعالجة والتوليد أوفلاين...", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    }
                } else if (exportStatusMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFF0284C7).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = exportStatusMessage!!,
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfBlueprintExportSection(
    workshopName: String,
    onWorkshopNameChange: (String) -> Unit,
    workshopLogoBitmap: Bitmap?,
    onPickWorkshopLogo: () -> Unit,
    includeStructuralLayer: Boolean,
    onToggleStructuralLayer: (Boolean) -> Unit,
    includeMepUtilitiesLayer: Boolean,
    onToggleMepUtilitiesLayer: (Boolean) -> Unit,
    includeCabinetsLayer: Boolean,
    onToggleCabinetsLayer: (Boolean) -> Unit,
    useCentimeters: Boolean,
    onToggleUseCentimeters: (Boolean) -> Unit,
    includeBlueprint: Boolean,
    onToggleBlueprint: (Boolean) -> Unit,
    includeBom: Boolean,
    onToggleBom: (Boolean) -> Unit,
    includeCutList: Boolean,
    onToggleCutList: (Boolean) -> Unit,
    isProcessing: Boolean,
    onGeneratePdf: () -> Unit,
    onGeneratePrintReadyImage: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Custom Workshop Logo & Name Card (إضافة شعار ورشتك يدوياً)
        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "إضافة شعار واسم ورشتك يدوياً على المخطط التنفيذي:",
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = workshopName,
                        onValueChange = onWorkshopNameChange,
                        label = { Text("اسم الورشة / المعرض", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("workshop_name_input")
                    )
                    OutlinedButton(
                        onClick = onPickWorkshopLogo,
                        modifier = Modifier.testTag("pick_workshop_logo_btn")
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (workshopLogoBitmap == null) "رفع شعار الورشة" else "تغيير الشعار", fontSize = 11.sp)
                    }
                    if (workshopLogoBitmap != null) {
                        Image(
                            bitmap = workshopLogoBitmap.asImageBitmap(),
                            contentDescription = "شعار الورشة",
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(6.dp))
                        )
                    }
                }
            }
        }

        // 2. Layer Separation Card (فصل طبقات المخطط بالسنتمتر كدليل عمل للموقع)
        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "فصل طبقات المخطط التنفيذي (Layer Separation):",
                        color = Color(0xFF34D399),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    FilterChip(
                        selected = useCentimeters,
                        onClick = { onToggleUseCentimeters(!useCentimeters) },
                        label = { Text(if (useCentimeters) "الوحدة: سنتمتر (سم)" else "الوحدة: مليمتر (مم)", fontSize = 10.sp) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = includeStructuralLayer,
                        onClick = { onToggleStructuralLayer(!includeStructuralLayer) },
                        label = { Text("طبقة الغرفة والفتحات", fontSize = 10.sp) },
                        modifier = Modifier.testTag("layer_toggle_structural")
                    )
                    FilterChip(
                        selected = includeMepUtilitiesLayer,
                        onClick = { onToggleMepUtilitiesLayer(!includeMepUtilitiesLayer) },
                        label = { Text("طبقة الخدمات (كهرباء/سباكة/غاز)", fontSize = 10.sp) },
                        modifier = Modifier.testTag("layer_toggle_mep_utilities")
                    )
                    FilterChip(
                        selected = includeCabinetsLayer,
                        onClick = { onToggleCabinetsLayer(!includeCabinetsLayer) },
                        label = { Text("طبقة الخزائن", fontSize = 10.sp) },
                        modifier = Modifier.testTag("layer_toggle_cabinets")
                    )
                }
            }
        }

        // 3. Pages selection
        ExportCheckboxItem(
            title = "صفحة 1: المخطط الهندسي والتنفيذي للموقع 2D (بالسنتمتر)",
            desc = "يعزل طبقة الغرفة الإنشائية والفتحات وطبقة تمديدات الخدمات (الكهرباء والسباكة والغاز) بالسنتمتر ليعمل كدليل عمل للموقع",
            icon = Icons.Default.Architecture,
            checked = includeBlueprint,
            onCheckedChange = onToggleBlueprint
        )

        ExportCheckboxItem(
            title = "صفحة 2: جدول حصر الكميات والمواد (BOM)",
            desc = "تفصيل أبعاد وخامات كل خزانة، الأمتار الطولية، مسطح الرخام ومؤشرات تكلفة التصنيع",
            icon = Icons.Default.ReceiptLong,
            checked = includeBom,
            onCheckedChange = onToggleBom
        )

        ExportCheckboxItem(
            title = "صفحة 3: قائمة القص والتعشيق الصناعي (CAM Cut List)",
            desc = "جدول مقاسات جوانب وأرضيات وسخات الميلامين وبروفايلات الألمنيوم بالمتر الطولي",
            icon = Icons.Default.ContentCut,
            checked = includeCutList,
            onCheckedChange = onToggleCutList
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onGeneratePdf,
                enabled = !isProcessing && (includeBlueprint || includeBom || includeCutList),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("btn_generate_pdf"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("تصدير PDF تنفيذي", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Button(
                onClick = onGeneratePrintReadyImage,
                enabled = !isProcessing,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("btn_export_blueprint_print_image"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("صورة عالية الجودة للطباعة", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DxfCadExportSection(
    viewModel: KitchenEditorViewModel,
    project: KitchenProject,
    isProcessing: Boolean,
    onExportDxfWithOptions: (DxfExporter.DxfExportOptions) -> Unit
) {
    var includeWalls by remember { mutableStateOf(true) }
    var includeOpenings by remember { mutableStateOf(true) }
    var includeCabinets by remember { mutableStateOf(true) }
    var includeAppliances by remember { mutableStateOf(true) }
    var includeMepUtilities by remember { mutableStateOf(true) }
    var includeWorkTriangle by remember { mutableStateOf(true) }
    var includeDimensions by remember { mutableStateOf(true) }
    var useCentimeters by remember { mutableStateOf(false) }
    var includeCabinetCutList by remember { mutableStateOf(true) }
    var includeCncNestedSheets by remember { mutableStateOf(true) }
    var includeAluminumBarCutList by remember { mutableStateOf(true) }
    var includeRouterGroovesAndDrilling by remember { mutableStateOf(true) }
    var showRawDxfPreview by remember { mutableStateOf(false) }

    val dxfOptions = remember(
        includeWalls, includeOpenings, includeCabinets, includeAppliances,
        includeMepUtilities, includeWorkTriangle, includeDimensions, useCentimeters,
        includeCabinetCutList, includeCncNestedSheets, includeAluminumBarCutList, includeRouterGroovesAndDrilling
    ) {
        DxfExporter.DxfExportOptions(
            includeWalls = includeWalls,
            includeOpenings = includeOpenings,
            includeCabinets = includeCabinets,
            includeAppliances = includeAppliances,
            includeMepUtilities = includeMepUtilities,
            includeWorkTriangle = includeWorkTriangle,
            includeDimensions = includeDimensions,
            includeLabels = true,
            includeTitleBlock = true,
            useCentimeters = useCentimeters,
            includeCabinetCutList = includeCabinetCutList,
            includeCncNestedSheets = includeCncNestedSheets,
            includeAluminumBarCutList = includeAluminumBarCutList,
            includeRouterGroovesAndDrilling = includeRouterGroovesAndDrilling
        )
    }

    val dxfResult = remember(project, dxfOptions) {
        viewModel.serializeFloorPlanWithMetadata(dxfOptions)
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "تصدير المخطط 2D + قوائم القص والتعشيق لماكينات CNC (DXF R12 / CAM)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "متوافق مع AutoCAD, AlphaCAM, VCarve, Aspire, WoodWOP وماكينات CNC Router",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            FilterChip(
                selected = useCentimeters,
                onClick = { useCentimeters = !useCentimeters },
                label = { Text(if (useCentimeters) "الوحدة: سم (INSUNITS=5)" else "الوحدة: مم (INSUNITS=4)", fontSize = 10.sp) },
                modifier = Modifier.testTag("dxf_unit_toggle_chip")
            )
        }

        // Layer Filter Chips for 2D Floor Plan DXF Serialization
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = includeWalls,
                onClick = { includeWalls = !includeWalls },
                label = { Text("الجدران (${dxfResult.exportedWallsCount})", fontSize = 10.sp) }
            )
            FilterChip(
                selected = includeOpenings,
                onClick = { includeOpenings = !includeOpenings },
                label = { Text("الفتحات (${dxfResult.exportedOpeningsCount})", fontSize = 10.sp) }
            )
            FilterChip(
                selected = includeCabinets,
                onClick = { includeCabinets = !includeCabinets },
                label = { Text("الخزائن (${dxfResult.exportedCabinetsCount})", fontSize = 10.sp) }
            )
            FilterChip(
                selected = includeMepUtilities,
                onClick = { includeMepUtilities = !includeMepUtilities },
                label = { Text("الخدمات MEP (${dxfResult.exportedMepPointsCount})", fontSize = 10.sp) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = includeAppliances,
                onClick = { includeAppliances = !includeAppliances },
                label = { Text("الأجهزة (${dxfResult.exportedAppliancesCount})", fontSize = 10.sp) }
            )
            FilterChip(
                selected = includeWorkTriangle,
                onClick = { includeWorkTriangle = !includeWorkTriangle },
                label = { Text("مثلث العمل والخلوص", fontSize = 10.sp) }
            )
            FilterChip(
                selected = includeDimensions,
                onClick = { includeDimensions = !includeDimensions },
                label = { Text("الأبعاد (${dxfResult.exportedDimensionsCount})", fontSize = 10.sp) }
            )
            FilterChip(
                selected = showRawDxfPreview,
                onClick = { showRawDxfPreview = !showRawDxfPreview },
                label = { Text(if (showRawDxfPreview) "إخفاء كود DXF" else "معاينة كود DXF", fontSize = 10.sp) },
                modifier = Modifier.testTag("toggle_raw_dxf_preview_chip")
            )
        }

        // CNC Cut-List & Nesting Layer Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = includeCabinetCutList,
                onClick = { includeCabinetCutList = !includeCabinetCutList },
                label = { Text("قوائم قص الخزائن CNC (${dxfResult.exportedCncCutPartsCount} قطعة)", fontSize = 10.sp) },
                modifier = Modifier.testTag("dxf_toggle_cnc_cutlist_chip")
            )
            FilterChip(
                selected = includeCncNestedSheets,
                onClick = { includeCncNestedSheets = !includeCncNestedSheets },
                enabled = includeCabinetCutList,
                label = { Text("تعشيق الألواح (${dxfResult.exportedCncSheetsCount} لوح)", fontSize = 10.sp) }
            )
            FilterChip(
                selected = includeRouterGroovesAndDrilling,
                onClick = { includeRouterGroovesAndDrilling = !includeRouterGroovesAndDrilling },
                enabled = includeCabinetCutList,
                label = { Text("مجرى الريشة وثقوب 35مم", fontSize = 10.sp) }
            )
            FilterChip(
                selected = includeAluminumBarCutList,
                onClick = { includeAluminumBarCutList = !includeAluminumBarCutList },
                enabled = includeCabinetCutList,
                label = { Text("قضبان الألمنيوم (${dxfResult.exportedAluminumBarsCount})", fontSize = 10.sp) }
            )
        }

        // Live CAD Statistics & Layer Table Summary
        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "إجمالي الكيانات المسلسلة: ${dxfResult.totalEntitiesCount} كيان CAD/CNC",
                        color = Color(0xFF34D399),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "الحجم: ${String.format(Locale.US, "%.1f", dxfResult.fileSizeBytes / 1024f)} KB",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                DxfLayerBadge(name = "WALLS", colorCode = 1, desc = "الجدران الإنشائية وزوايا الغرفة (${dxfResult.layerEntityCounts["WALLS"] ?: 0} كيان)")
                DxfLayerBadge(name = "OPENINGS", colorCode = 2, desc = "الأبواب مع قوس الفتح ARC والنوافذ (${dxfResult.layerEntityCounts["OPENINGS"] ?: 0} كيان)")
                DxfLayerBadge(name = "CABINETS_BASE", colorCode = 3, desc = "مساقط الخزائن السفلية والأحواض (${dxfResult.layerEntityCounts["CABINETS_BASE"] ?: 0} كيان)")
                DxfLayerBadge(name = "CABINETS_WALL", colorCode = 4, desc = "الخزائن العلوية والتاج بخط متقطع DASHED (${dxfResult.layerEntityCounts["CABINETS_WALL"] ?: 0} كيان)")
                DxfLayerBadge(name = "CNC_SHEET_BORDER", colorCode = 7, desc = "حدود ألواح الميلامين 2440×1220 مم وجدول القص (${dxfResult.layerEntityCounts["CNC_SHEET_BORDER"] ?: 0} كيان)")
                DxfLayerBadge(name = "CNC_PANEL_CUT", colorCode = 3, desc = "مسارات قص قطع الخزائن والألمنيوم 1:1 (${dxfResult.layerEntityCounts["CNC_PANEL_CUT"] ?: 0} كيان)")
                DxfLayerBadge(name = "CNC_ROUTER_GROOVE", colorCode = 1, desc = "مجرى تفريز الظهر (الريشة) وثقوب المفصلات (${dxfResult.layerEntityCounts["CNC_ROUTER_GROOVE"] ?: 0} كيان)")
                DxfLayerBadge(name = "CNC_EDGE_BANDING", colorCode = 2, desc = "علامات شريط الحواف PVC/ABS (${dxfResult.layerEntityCounts["CNC_EDGE_BANDING"] ?: 0} كيان)")
                DxfLayerBadge(name = "CNC_ALU_BARS", colorCode = 4, desc = "قضبان الألمنيوم القياسية ومخطط القص (${dxfResult.layerEntityCounts["CNC_ALU_BARS"] ?: 0} كيان)")
                DxfLayerBadge(name = "CNC_PART_LABELS", colorCode = 2, desc = "تسميات القطع والمقاسات واتجاه العرق (${dxfResult.layerEntityCounts["CNC_PART_LABELS"] ?: 0} كيان)")
            }
        }

        if (showRawDxfPreview) {
            Surface(
                color = Color(0xFF020617),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("raw_dxf_code_preview_box")
            ) {
                Text(
                    text = dxfResult.dxfContent.take(1200) + "\n... [EOF]",
                    color = Color(0xFF38BDF8),
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Button(
            onClick = { onExportDxfWithOptions(dxfOptions) },
            enabled = !isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_export_dxf"),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Architecture, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("تصدير المخطط 2D + قوائم القص CNC (DXF)", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RoomPackageSection(
    project: KitchenProject,
    lastImportedManifest: RoomPackageManifest?,
    isProcessing: Boolean,
    onExportRoom: () -> Unit,
    onImportRoom: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "حزمة المشروع الموحدة المشفرة (.room)",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "تجميع ملف تعريف المخطط (JSON)، الخامات، الأبعاد، وتوزيع الخزائن وضغطها وتشفيرها في ملف واحد بامتداد .room لمشاركتها أوفلاين.",
            fontSize = 12.sp,
            color = Color(0xFF94A3B8),
            lineHeight = 18.sp
        )

        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("بيانات الحزمة الحالية:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("المشروع: ${project.name}", fontSize = 11.sp, color = Color(0xFF38BDF8))
                Text("عدد الخزائن والعناصر: ${project.elements.size} عنصر", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                Text("أبعاد الغرفة: ${(project.room.widthMm / 10f).toInt()} × ${(project.room.depthMm / 10f).toInt()} × ${(project.room.heightMm / 10f).toInt()} سم", fontSize = 11.sp, color = Color(0xFFCBD5E1))
            }
        }

        if (lastImportedManifest != null) {
            Surface(
                color = Color(0xFF064E3B).copy(alpha = 0.3f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "آخر استيراد ناجح: ${lastImportedManifest.projectName} (${lastImportedManifest.elementCount} خزانة)",
                        fontSize = 11.sp,
                        color = Color(0xFF6EE7B7)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onImportRoom,
                enabled = !isProcessing,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("btn_import_room"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("استيراد حزمة .room", fontSize = 12.sp)
            }

            Button(
                onClick = onExportRoom,
                enabled = !isProcessing,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("btn_export_room"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("تصدير الحزمة .room", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun JpgSnapshotSection(
    project: KitchenProject,
    isProcessing: Boolean,
    onExportJpg: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "تصدير المخطط التنفيذي كصورة عالية الجودة جاهزة للطباعة (Print-Ready JPG)",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "استخراج صورة هندسية عالية الدقة للمخطط التنفيذي بالسنتمتر مع طبقات الغرفة وتمديدات الخدمات (الكهرباء والسباكة والغاز) وشعار ورشتك جاهزة للطباعة الفورية.",
            fontSize = 12.sp,
            color = Color(0xFF94A3B8),
            lineHeight = 18.sp
        )

        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("مواصفات الصورة التنفيذية المصدرة:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("• الصيغة: JPEG فائقة الوضوح للطباعة (Quality: 95%)", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                Text("• الدقة: A4 Print Resolution (1785 × 2526 px)", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                Text("• المحتوى: الطبقات المختارة بالسنتمتر + شعار واسم الورشة", fontSize = 11.sp, color = Color(0xFFCBD5E1))
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onExportJpg,
            enabled = !isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_export_jpg"),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("حفظ صورة المخطط للطباعة ومشاركتها", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ExportCheckboxItem(
    title: String,
    desc: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = if (checked) Color(0xFF0284C7).copy(alpha = 0.12f) else Color(0xFF0F172A),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF0284C7))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                icon,
                contentDescription = null,
                tint = if (checked) Color(0xFF38BDF8) else Color(0xFF64748B),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(desc, fontSize = 10.sp, color = Color(0xFF94A3B8), lineHeight = 14.sp)
            }
        }
    }
}

@Composable
private fun DxfLayerBadge(name: String, colorCode: Int, desc: String) {
    val layerColor = when (colorCode) {
        1 -> Color(0xFFEF4444)
        2 -> Color(0xFFFBBF24)
        3 -> Color(0xFF10B981)
        4 -> Color(0xFF38BDF8)
        6 -> Color(0xFFD946EF)
        else -> Color.White
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(layerColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = layerColor)
        Spacer(modifier = Modifier.width(8.dp))
        Text("• $desc", fontSize = 10.sp, color = Color(0xFF94A3B8))
    }
}

@Composable
private fun JsonDataExportSection(
    viewModel: KitchenEditorViewModel,
    project: KitchenProject,
    isProcessing: Boolean,
    onExportJsonWithOptions: (JsonProjectExporter.JsonExportOptions) -> Unit,
    onCopyJsonToClipboard: (String, String) -> Unit,
    onImportJsonFile: () -> Unit
) {
    var selectedMode by remember { mutableStateOf(JsonProjectExporter.JsonExportMode.FULL_PROJECT_AND_CAM) }
    var includeRoomAndMep by remember { mutableStateOf(true) }
    var includeSceneElements by remember { mutableStateOf(true) }
    var includeBoundingBoxes by remember { mutableStateOf(true) }
    var includeCatalogDefinitions by remember { mutableStateOf(false) }
    var includeParametricCutList by remember { mutableStateOf(true) }
    var includeAluminumAndSheetNesting by remember { mutableStateOf(true) }
    var includeBillOfMaterials by remember { mutableStateOf(true) }
    var useCentimeters by remember { mutableStateOf(true) }
    var prettyPrint by remember { mutableStateOf(true) }
    var showRawJsonPreview by remember { mutableStateOf(true) }

    val jsonOptions = remember(
        selectedMode,
        includeRoomAndMep,
        includeSceneElements,
        includeBoundingBoxes,
        includeCatalogDefinitions,
        includeParametricCutList,
        includeAluminumAndSheetNesting,
        includeBillOfMaterials,
        useCentimeters,
        prettyPrint
    ) {
        JsonProjectExporter.JsonExportOptions(
            exportMode = selectedMode,
            includeRoomAndMep = includeRoomAndMep,
            includeSceneElements = includeSceneElements,
            includeElementBoundingBoxes = includeBoundingBoxes,
            includeCatalogDefinitions = includeCatalogDefinitions,
            includeParametricCutList = includeParametricCutList,
            includeAluminumAndSheetNesting = includeAluminumAndSheetNesting,
            includeBillOfMaterials = includeBillOfMaterials,
            useCentimeters = useCentimeters,
            prettyPrint = prettyPrint
        )
    }

    val jsonResult = remember(project, jsonOptions) {
        viewModel.serializeProjectAndElementsToJson(jsonOptions)
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "استخراج وتصدير الملفات وعناصر المطبخ بصيغة JSON القياسية",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "تصدير واستيراد هيكل المشروع، عناصر وخزائن التصميم 3D/2D، الكتالوج، وقوائم القص والتعشيق بصيغة JSON",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = prettyPrint,
                    onClick = { prettyPrint = !prettyPrint },
                    label = { Text(if (prettyPrint) "منسق (Pretty)" else "مضغوط (Minified)", fontSize = 10.sp) },
                    modifier = Modifier.testTag("json_pretty_toggle_chip")
                )
                FilterChip(
                    selected = showRawJsonPreview,
                    onClick = { showRawJsonPreview = !showRawJsonPreview },
                    label = { Text(if (showRawJsonPreview) "إخفاء المعاينة" else "معاينة JSON", fontSize = 10.sp) },
                    modifier = Modifier.testTag("toggle_raw_json_preview_chip")
                )
            }
        }

        // 1. Export Mode Selector Pills
        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "اختر نمط استخراج ملف الـ JSON:",
                    color = Color(0xFF38BDF8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedMode == JsonProjectExporter.JsonExportMode.FULL_PROJECT_AND_CAM,
                        onClick = { selectedMode = JsonProjectExporter.JsonExportMode.FULL_PROJECT_AND_CAM },
                        label = { Text("المشروع والعناصر الكامل", fontSize = 10.sp) },
                        modifier = Modifier.testTag("json_mode_full_project")
                    )
                    FilterChip(
                        selected = selectedMode == JsonProjectExporter.JsonExportMode.ELEMENTS_ONLY,
                        onClick = { selectedMode = JsonProjectExporter.JsonExportMode.ELEMENTS_ONLY },
                        label = { Text("عناصر التصميم فقط (${project.elements.size})", fontSize = 10.sp) },
                        modifier = Modifier.testTag("json_mode_elements_only")
                    )
                    FilterChip(
                        selected = selectedMode == JsonProjectExporter.JsonExportMode.CATALOG_AND_ELEMENTS,
                        onClick = { selectedMode = JsonProjectExporter.JsonExportMode.CATALOG_AND_ELEMENTS },
                        label = { Text("كتالوج العناصر + التصميم", fontSize = 10.sp) },
                        modifier = Modifier.testTag("json_mode_catalog_elements")
                    )
                    FilterChip(
                        selected = selectedMode == JsonProjectExporter.JsonExportMode.CUT_LIST_AND_NESTING,
                        onClick = { selectedMode = JsonProjectExporter.JsonExportMode.CUT_LIST_AND_NESTING },
                        label = { Text("قوائم القص والتعشيق CAM", fontSize = 10.sp) },
                        modifier = Modifier.testTag("json_mode_cam_cutlist")
                    )
                    FilterChip(
                        selected = selectedMode == JsonProjectExporter.JsonExportMode.ARCHVIZ_CONTROLNET_PROMPT,
                        onClick = { selectedMode = JsonProjectExporter.JsonExportMode.ARCHVIZ_CONTROLNET_PROMPT },
                        label = { Text("Image-to-Realism ArchViz JSON", fontSize = 10.sp) },
                        modifier = Modifier.testTag("json_mode_archviz_render")
                    )
                }
                Text(
                    text = selectedMode.subtitleAr,
                    color = Color(0xFFCBD5E1),
                    fontSize = 10.sp
                )
            }
        }

        // 2. Granular JSON Section Toggles
        if (selectedMode == JsonProjectExporter.JsonExportMode.FULL_PROJECT_AND_CAM) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = includeRoomAndMep,
                    onClick = { includeRoomAndMep = !includeRoomAndMep },
                    label = { Text("الغرفة والخدمات MEP (${jsonResult.exportedWallsCount} جدران)", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = includeSceneElements,
                    onClick = { includeSceneElements = !includeSceneElements },
                    label = { Text("عناصر المطبخ (${jsonResult.exportedElementsCount})", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = includeBoundingBoxes,
                    onClick = { includeBoundingBoxes = !includeBoundingBoxes },
                    enabled = includeSceneElements,
                    label = { Text("إحداثيات AABB وأركان 2D", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = includeCatalogDefinitions,
                    onClick = { includeCatalogDefinitions = !includeCatalogDefinitions },
                    label = { Text("تعريفات الكتالوج", fontSize = 10.sp) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = includeParametricCutList,
                    onClick = { includeParametricCutList = !includeParametricCutList },
                    label = { Text("تفصيل القطع (${jsonResult.exportedCutPartsCount} قطعة)", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = includeAluminumAndSheetNesting,
                    onClick = { includeAluminumAndSheetNesting = !includeAluminumAndSheetNesting },
                    label = { Text("تعشيق الألمنيوم والميلامين", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = includeBillOfMaterials,
                    onClick = { includeBillOfMaterials = !includeBillOfMaterials },
                    label = { Text("جدول الكميات BOM", fontSize = 10.sp) }
                )
            }
        }

        // 3. Live JSON Metadata Summary Card
        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ملخص البيانات المسلسلة في JSON: ${jsonResult.exportedElementsCount} عنصر مطبخي",
                        color = Color(0xFF34D399),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "حجم الملف: ${String.format(Locale.US, "%.1f", jsonResult.fileSizeBytes / 1024f)} KB",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                DxfLayerBadge(
                    name = "elements[]",
                    colorCode = 3,
                    desc = "عناصر وخزائن المطبخ مع الإحداثيات (X,Y,Z)، المقاسات (مم/سم)، الخامات، والظهر (${jsonResult.exportedElementsCount} عنصر)"
                )
                if (jsonResult.exportedWallsCount > 0) {
                    DxfLayerBadge(
                        name = "room.walls[]",
                        colorCode = 4,
                        desc = "الجدران (${jsonResult.exportedWallsCount})، الفتحات (${jsonResult.exportedOpeningsCount})، ونقاط خدمات MEP (${jsonResult.exportedMepPointsCount})"
                    )
                }
                if (jsonResult.exportedCutPartsCount > 0) {
                    DxfLayerBadge(
                        name = "parametricCabinetCutLists[]",
                        colorCode = 2,
                        desc = "تفصيل جوانب وأرضيات وسخات الميلامين وظهر الخزانة وبروفايلات الألمنيوم (${jsonResult.exportedCutPartsCount} قطعة)"
                    )
                }
                if (jsonResult.exportedMelamineSheetsCount > 0 || jsonResult.exportedAluminumBarsCount > 0) {
                    DxfLayerBadge(
                        name = "camNesting{}",
                        colorCode = 6,
                        desc = "ألواح الميلامين المعشقة (${jsonResult.exportedMelamineSheetsCount} لوح) وقضبان الألمنيوم القياسية (${jsonResult.exportedAluminumBarsCount} قضيب)"
                    )
                }
                if (jsonResult.exportedCatalogTypesCount > 0) {
                    DxfLayerBadge(
                        name = "catalogDefinitions[]",
                        colorCode = 1,
                        desc = "مكتبة العناصر القياسية الكاملة مع الأبعاد الافتراضية (${jsonResult.exportedCatalogTypesCount} نوع)"
                    )
                }
            }
        }

        // 4. Interactive Live JSON Code Preview & In-Place Object Editor Box
        var isLiveJsonEditing by remember { mutableStateOf(false) }
        var editableJsonText by remember(jsonResult.jsonContent) { mutableStateOf(jsonResult.jsonContent) }
        var liveEditFeedback by remember { mutableStateOf<String?>(null) }

        if (showRawJsonPreview) {
            Surface(
                color = Color(0xFF020617),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isLiveJsonEditing) Color(0xFF10B981) else Color(0xFF334155)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("raw_json_code_preview_box")
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isLiveJsonEditing) {
                                "محرر كود JSON التفاعلي (عدّل القيم وطبّقها فوراً على المطبخ):"
                            } else {
                                "معاينة وتعديل هيكل البيانات (JSON Schema Preview & Editor):"
                            },
                            color = if (isLiveJsonEditing) Color(0xFF34D399) else Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = isLiveJsonEditing,
                                onClick = {
                                    isLiveJsonEditing = !isLiveJsonEditing
                                    if (isLiveJsonEditing && editableJsonText.isBlank()) {
                                        editableJsonText = jsonResult.jsonContent
                                    }
                                },
                                label = {
                                    Text(
                                        if (isLiveJsonEditing) "وضع التعديل نشط ✎" else "تعديل JSON مباشرة ✎",
                                        fontSize = 10.sp
                                    )
                                },
                                modifier = Modifier.testTag("toggle_live_json_editor_chip")
                            )
                            OutlinedButton(
                                onClick = {
                                    onCopyJsonToClipboard(
                                        if (isLiveJsonEditing) editableJsonText else jsonResult.jsonContent,
                                        selectedMode.titleAr
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(30.dp)
                                    .testTag("btn_copy_json_clipboard")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("نسخ كود JSON", fontSize = 10.sp)
                            }
                        }
                    }

                    // Quick Object Selector chips when editing individual scene elements in JSON
                    if (project.elements.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "اختر كائناً لتعديله بصيغة JSON:",
                                color = Color(0xFFCBD5E1),
                                fontSize = 10.sp
                            )
                            AssistChip(
                                onClick = {
                                    editableJsonText = JsonProjectExporter.serializeElementsListToJsonString(
                                        elements = project.elements,
                                        includeBoundingBoxes = includeBoundingBoxes,
                                        prettyPrint = true
                                    )
                                    isLiveJsonEditing = true
                                    liveEditFeedback = "تم تحميل مصفوفة جميع العناصر (${project.elements.size}) للتعديل"
                                },
                                label = { Text("كل العناصر (${project.elements.size})", fontSize = 10.sp, color = Color(0xFF38BDF8)) }
                            )
                            project.elements.forEachIndexed { idx, elem ->
                                AssistChip(
                                    onClick = {
                                        editableJsonText = JsonProjectExporter.serializeSingleElementToJsonString(
                                            elem = elem,
                                            includeBoundingBoxes = includeBoundingBoxes,
                                            prettyPrint = true
                                        )
                                        isLiveJsonEditing = true
                                        liveEditFeedback = "تم تحويل الكائن '${elem.name}' إلى JSON للتعديل المباشر"
                                    },
                                    label = {
                                        Text(
                                            "${idx + 1}. ${elem.name} (${elem.dimensions.x.toInt()}×${elem.dimensions.z.toInt()})",
                                            fontSize = 10.sp,
                                            color = Color.White
                                        )
                                    }
                                )
                            }
                        }
                    }

                    if (isLiveJsonEditing) {
                        OutlinedTextField(
                            value = editableJsonText,
                            onValueChange = { editableJsonText = it },
                            label = { Text("حرّر كود JSON هنا ثم اضغط تطبيق التعديلات", fontSize = 10.sp) },
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                color = Color(0xFF38BDF8)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .testTag("live_json_editor_text_field")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = liveEditFeedback ?: "يمكنك تعديل الأبعاد أو الإحداثيات أو الخامات وتطبيقها فوراً",
                                color = Color(0xFF34D399),
                                fontSize = 10.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    try {
                                        val updatedProj = viewModel.importProjectOrElementsFromJson(editableJsonText)
                                        liveEditFeedback = "تم تطبيق تعديلات JSON بنجاح على ${updatedProj.elements.size} عنصر في المطبخ!"
                                    } catch (e: Exception) {
                                        liveEditFeedback = "خطأ في صيغة JSON: ${e.localizedMessage}"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("btn_apply_live_json_edits")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تطبيق التعديلات على التصميم", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Text(
                            text = if (jsonResult.jsonContent.length > 1400) {
                                jsonResult.jsonContent.take(1400) + "\n  ...\n}"
                            } else {
                                jsonResult.jsonContent
                            },
                            color = Color(0xFF38BDF8),
                            fontSize = 9.sp,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }

        // 5. Primary Export, Elements-Only Export & Import Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onExportJsonWithOptions(jsonOptions) },
                enabled = !isProcessing,
                modifier = Modifier
                    .weight(1.2f)
                    .height(48.dp)
                    .testTag("btn_export_json_file"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.DataObject, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("تصدير ملف JSON", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Button(
                onClick = {
                    val elementsOnlyOptions = jsonOptions.copy(
                        exportMode = JsonProjectExporter.JsonExportMode.ELEMENTS_ONLY,
                        includeSceneElements = true,
                        includeElementBoundingBoxes = true
                    )
                    onExportJsonWithOptions(elementsOnlyOptions)
                },
                enabled = !isProcessing,
                modifier = Modifier
                    .weight(1.2f)
                    .height(48.dp)
                    .testTag("btn_export_elements_json_file"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ViewInAr, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("استخراج العناصر فقط JSON", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }

            OutlinedButton(
                onClick = onImportJsonFile,
                enabled = !isProcessing,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("btn_import_json_file"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("استيراد JSON", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
