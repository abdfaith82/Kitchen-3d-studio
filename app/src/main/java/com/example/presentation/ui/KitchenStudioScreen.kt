package com.example.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.camera.CameraMode
import com.example.domain.model.ElementCategory
import com.example.domain.model.KitchenElementType
import com.example.presentation.viewmodel.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitchenStudioScreen(
    viewModel: KitchenEditorViewModel,
    onSignOut: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.triggerLifecycleAutoSave()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    var showTemplatesDialog by remember { mutableStateOf(false) }
    var exportStudioInitialTab by remember { mutableStateOf(ExportTab.PDF_BLUEPRINT) }

    // External Menu Drag-and-Drop State (from Sidebar or Top Visual Bar directly into 3D/2D Workspace)
    var draggingCatalogType by remember { mutableStateOf<KitchenElementType?>(null) }
    var dragFingerRootOffset by remember { mutableStateOf<Offset?>(null) }
    var dragResolvedWorldCoord by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var workspaceRootOrigin by remember { mutableStateOf(Offset.Zero) }

    val handleStartExternalDrag: (KitchenElementType, Offset) -> Unit = { type, rootPt ->
        draggingCatalogType = type
        dragFingerRootOffset = rootPt
    }
    val handleUpdateExternalDrag: (Offset) -> Unit = { rootPt ->
        dragFingerRootOffset = rootPt
    }
    val handleEndExternalDrag: () -> Unit = {
        val droppedType = draggingCatalogType
        val droppedCoord = dragResolvedWorldCoord
        draggingCatalogType = null
        dragFingerRootOffset = null
        dragResolvedWorldCoord = null
        if (droppedType != null) {
            if (droppedCoord != null) {
                viewModel.placeFurnitureAtCoordinate(droppedCoord.first, droppedCoord.second, droppedType)
            } else {
                viewModel.addElement(droppedType)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF020617),
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier
                            .clickable { viewModel.openProjectsManager() }
                            .padding(vertical = 4.dp)
                            .testTag("top_bar_project_title_btn")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Kitchen3D Studio",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF0284C7).copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = "100% Offline CAD",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.FolderSpecial,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = state.project.name,
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                },
                actions = {
                    // Live Automatic Total Cost Badge (حساب إجمالي التكلفة تلقائياً بناءً على الوحدات المختارة)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF064E3B),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clickable { viewModel.openDialog(ActiveDialog.BillOfMaterialsDialog) }
                            .testTag("top_bar_live_total_cost_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.PieChart,
                                contentDescription = "توزيع التكاليف",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = String.format(java.util.Locale.US, "$%.0f", state.costDistribution.totalCostUsd),
                                color = Color(0xFF34D399),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // Projects Manager (Firestore Cloud Sync & Room Offline Storage)
                    IconButton(
                        onClick = { viewModel.openProjectsManager() },
                        modifier = Modifier.testTag("top_bar_open_projects_btn")
                    ) {
                        Icon(
                            Icons.Default.FolderSpecial,
                            contentDescription = "إدارة المشاريع والمزامنة السحابية Firestore",
                            tint = Color(0xFF38BDF8)
                        )
                    }

                    if (onSignOut != null) {
                        IconButton(
                            onClick = onSignOut,
                            modifier = Modifier.testTag("top_bar_sign_out_btn")
                        ) {
                            Icon(
                                Icons.Default.Logout,
                                contentDescription = "تسجيل الخروج من حساب Google",
                                tint = Color(0xFFF87171)
                            )
                        }
                    }

                    // Clean Canvas Mode Toggle (إخفاء/إظهار جميع اللوحات العائمة لرؤية التصميم بالكامل)
                    IconButton(
                        onClick = { viewModel.toggleCleanCanvasMode() },
                        modifier = Modifier.testTag("toggle_clean_canvas_btn")
                    ) {
                        Icon(
                            if (state.isCleanCanvasMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "إخفاء/إظهار لوحات التحكم",
                            tint = if (state.isCleanCanvasMode) Color(0xFFF59E0B) else Color(0xFF34D399)
                        )
                    }

                    // Section 1: Single-Click Collapsible Sidebar Toggle Button (إخفاء/إظهار اللوحة الجانبية بضغطة زر واحدة)
                    IconButton(
                        onClick = { viewModel.toggleCollapsibleSidebar() },
                        modifier = Modifier.testTag("toggle_collapsible_sidebar_btn")
                    ) {
                        Icon(
                            if (state.isCollapsibleSidebarOpen) Icons.Default.ViewSidebar else Icons.Default.MenuOpen,
                            contentDescription = "طي/فتح اللوحة الجانبية النحيفة",
                            tint = if (state.isCollapsibleSidebarOpen) Color(0xFF34D399) else Color(0xFF94A3B8)
                        )
                    }

                    // Gemini AI Kitchen Designer Button (Firebase AI Logic)
                    IconButton(
                        onClick = { viewModel.openDialog(ActiveDialog.GeminiAiDesigner) },
                        modifier = Modifier.testTag("open_gemini_ai_designer_button")
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "مهندس المطابخ الذكي بـ Gemini AI",
                            tint = Color(0xFFC084FC)
                        )
                    }

                    // Smart Internal Kitchen Accessories Library Button (Auto-BOM Link)
                    IconButton(
                        onClick = { viewModel.openDialog(ActiveDialog.SmartAccessories) },
                        modifier = Modifier.testTag("open_smart_accessories_button")
                    ) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = "مكتبة الإكسسوارات الداخلية الذكية",
                            tint = Color(0xFFFBBF24)
                        )
                    }

                    // Metric vs Imperial Unit System Switcher Pill
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E293B),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .clickable { viewModel.toggleUnitSystem() }
                            .testTag("top_bar_unit_system_toggle_btn")
                    ) {
                        Text(
                            text = if (state.unitSystem == com.example.core.units.MeasurementUnitSystem.METRIC) "📏 متري" else "📏 إمبراطوري",
                            color = Color(0xFF7DD3FC),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }

                    // Dedicated Automatic 1mm Cabinet Filler & Spacer Tool Button
                    IconButton(
                        onClick = { viewModel.openDialog(ActiveDialog.CabinetFillerTool) },
                        modifier = Modifier.testTag("open_cabinet_filler_button")
                    ) {
                        Icon(
                            Icons.Default.ViewColumn,
                            contentDescription = "أداة شرائح الفيلر والفواصل التلقائية بدقة 1مم",
                            tint = Color(0xFF2DD4BF)
                        )
                    }

                    // Dedicated Aluminum & Melamine Calculator Button
                    IconButton(
                        onClick = { viewModel.openDialog(ActiveDialog.AluminumMelamineCalculator) },
                        modifier = Modifier.testTag("open_alu_melamine_calc_button")
                    ) {
                        Icon(
                            Icons.Default.Calculate,
                            contentDescription = "حاسبة بروفايلات الألمنيوم والميلامين",
                            tint = Color(0xFF38BDF8)
                        )
                    }

                    // Dedicated 1D Aluminum Bar Nesting Optimizer Button
                    IconButton(
                        onClick = { viewModel.openDialog(ActiveDialog.AluminumBarNesting) },
                        modifier = Modifier.testTag("open_aluminum_bar_nesting_button")
                    ) {
                        Icon(
                            Icons.Default.Straighten,
                            contentDescription = "تعشيق وقص قضبان الألمنيوم القياسية",
                            tint = Color(0xFF10B981)
                        )
                    }

                    // Undo Button
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = state.undoRedoState.canUndo,
                        modifier = Modifier.testTag("undo_button")
                    ) {
                        Icon(
                            Icons.Default.Undo,
                            contentDescription = "تراجع (Undo)",
                            tint = if (state.undoRedoState.canUndo) Color(0xFF38BDF8) else Color(0xFF475569)
                        )
                    }

                    // Redo Button
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = state.undoRedoState.canRedo,
                        modifier = Modifier.testTag("redo_button")
                    ) {
                        Icon(
                            Icons.Default.Redo,
                            contentDescription = "إعادة (Redo)",
                            tint = if (state.undoRedoState.canRedo) Color(0xFF38BDF8) else Color(0xFF475569)
                        )
                    }

                    // View Mode Switcher
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.height(32.dp).padding(end = 4.dp)
                    ) {
                        SegmentedButton(
                            selected = state.viewMode == ViewMode.PERSPECTIVE_3D,
                            onClick = { viewModel.setViewMode(ViewMode.PERSPECTIVE_3D) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = Color(0xFF0284C7),
                                activeContentColor = Color.White
                            )
                        ) {
                            Text("3D", fontSize = 11.sp)
                        }
                        SegmentedButton(
                            selected = state.viewMode == ViewMode.FLOORPLAN_2D,
                            onClick = { viewModel.setViewMode(ViewMode.FLOORPLAN_2D) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = Color(0xFF0284C7),
                                activeContentColor = Color.White
                            )
                        ) {
                            Text("2D", fontSize = 11.sp)
                        }
                        SegmentedButton(
                            selected = state.viewMode == ViewMode.SPLIT_VIEW,
                            onClick = { viewModel.setViewMode(ViewMode.SPLIT_VIEW) },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = Color(0xFF0284C7),
                                activeContentColor = Color.White
                            )
                        ) {
                            Text("منقسم", fontSize = 11.sp)
                        }
                    }

                    // Sketch to 3D Button
                    IconButton(
                        onClick = { viewModel.openDialog(ActiveDialog.SketchCanvas) },
                        modifier = Modifier.testTag("open_sketch_button")
                    ) {
                        Icon(Icons.Default.Gesture, contentDescription = "سكتش المخطط", tint = Color(0xFF38BDF8))
                    }

                    // AR / Photo Perspective Overlay Button
                    IconButton(
                        onClick = { viewModel.openDialog(ActiveDialog.PerspectiveOverlay) },
                        modifier = Modifier.testTag("open_ar_overlay_button")
                    ) {
                        Icon(Icons.Default.ViewInAr, contentDescription = "منظور فوق صورة", tint = Color(0xFFF59E0B))
                    }

                    // Local 3D Importer Button
                    IconButton(
                        onClick = { viewModel.openDialog(ActiveDialog.ModelImporterDialog) },
                        modifier = Modifier.testTag("open_importer_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "استيراد 3D / DXF", tint = Color(0xFF34D399))
                    }

                    // Parametric Cabinet Customizer Button
                    IconButton(
                        onClick = { viewModel.openDialog(ActiveDialog.ParametricCabinet) },
                        modifier = Modifier.testTag("open_parametric_cabinet_button")
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "تخصيص الخزانة الإجرائي", tint = Color(0xFFF59E0B))
                    }

                    // 2D Nesting CAM Cut List Optimizer Button
                    IconButton(
                        onClick = { viewModel.openDialog(ActiveDialog.NestingOptimizer) },
                        modifier = Modifier.testTag("open_nesting_button")
                    ) {
                        Icon(Icons.Default.ContentCut, contentDescription = "قائمة القص والتعشيق CAM", tint = Color(0xFF38BDF8))
                    }

                    // PBR Render & Lighting Studio 4K Button
                    IconButton(
                        onClick = { viewModel.openDialog(ActiveDialog.RenderStudio) },
                        modifier = Modifier.testTag("open_render_studio_button")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "استوديو الرندر 4K والإضاءة", tint = Color(0xFFA855F7))
                    }

                    // Bill of Materials Export Icon
                    IconButton(
                        onClick = { viewModel.openDialog(ActiveDialog.BillOfMaterialsDialog) },
                        modifier = Modifier.testTag("open_bom_button")
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = "جدول الكميات BOM", tint = Color(0xFF10B981))
                    }

                    // Quick JSON Files & Elements Export Button
                    IconButton(
                        onClick = {
                            exportStudioInitialTab = ExportTab.JSON_DATA
                            viewModel.openDialog(ActiveDialog.ExportStudio)
                        },
                        modifier = Modifier.testTag("open_json_export_button")
                    ) {
                        Icon(Icons.Default.DataObject, contentDescription = "استخراج الملفات والعناصر JSON", tint = Color(0xFF34D399))
                    }

                    // Export & Packaging Studio Button (PDF Blueprint, DXF, JSON, .room Package, JPG)
                    IconButton(
                        onClick = {
                            exportStudioInitialTab = ExportTab.PDF_BLUEPRINT
                            viewModel.openDialog(ActiveDialog.ExportStudio)
                        },
                        modifier = Modifier.testTag("open_export_studio_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "تصدير المخططات وحزم المشاريع", tint = Color(0xFF38BDF8))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        },
        bottomBar = {
            // Bottom Studio Bar
            Surface(
                color = Color(0xFF0F172A),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomStudioAction(
                        icon = Icons.Default.AddBox,
                        label = "الكتالوج",
                        tag = "btn_open_catalog",
                        onClick = { viewModel.openDialog(ActiveDialog.Catalog) }
                    )
                    BottomStudioAction(
                        icon = Icons.Default.PhotoCameraFront,
                        label = "تصميم خزانة 360°",
                        tag = "btn_open_photo_cabinet_designer",
                        onClick = { viewModel.openDialog(ActiveDialog.PhotoCabinetDesigner) }
                    )
                    BottomStudioAction(
                        icon = Icons.Default.AutoAwesome,
                        label = "ذكاء Gemini",
                        tag = "btn_open_gemini_ai_designer",
                        onClick = { viewModel.openDialog(ActiveDialog.GeminiAiDesigner) }
                    )
                    BottomStudioAction(
                        icon = Icons.Default.Build,
                        label = "إكسسوارات ذكية",
                        tag = "btn_open_smart_accessories",
                        onClick = { viewModel.openDialog(ActiveDialog.SmartAccessories) }
                    )
                    BottomStudioAction(
                        icon = Icons.Default.ViewColumn,
                        label = "فيلر 1مم",
                        tag = "btn_open_cabinet_filler",
                        onClick = { viewModel.openDialog(ActiveDialog.CabinetFillerTool) }
                    )
                    BottomStudioAction(
                        icon = Icons.Default.Calculate,
                        label = "ألمنيوم وميلامين",
                        tag = "btn_open_alu_melamine_calc",
                        onClick = { viewModel.openDialog(ActiveDialog.AluminumMelamineCalculator) }
                    )
                    BottomStudioAction(
                        icon = Icons.Default.Straighten,
                        label = "تعشيق القضبان",
                        tag = "btn_open_alu_bar_nesting",
                        onClick = { viewModel.openDialog(ActiveDialog.AluminumBarNesting) }
                    )
                    BottomStudioAction(
                        icon = Icons.Default.Gesture,
                        label = "سكتش 3D",
                        tag = "btn_open_sketch",
                        onClick = { viewModel.openDialog(ActiveDialog.SketchCanvas) }
                    )
                    BottomStudioAction(
                        icon = Icons.Default.CameraEnhance,
                        label = "رندر 4K",
                        tag = "btn_open_render_4k",
                        onClick = { viewModel.openDialog(ActiveDialog.RenderStudio) }
                    )
                    BottomStudioAction(
                        icon = Icons.Default.PhotoCamera,
                        label = "كاميرا 3D",
                        tag = "btn_open_ar_overlay",
                        onClick = { viewModel.openDialog(ActiveDialog.PerspectiveOverlay) }
                    )
                    BottomStudioAction(
                        icon = Icons.Default.Palette,
                        label = "الخامات",
                        tag = "btn_open_materials",
                        onClick = { viewModel.openDialog(ActiveDialog.Materials) }
                    )
                    BottomStudioAction(
                        icon = Icons.Default.FolderOpen,
                        label = "استيراد 3D",
                        tag = "btn_open_importer",
                        onClick = { viewModel.openDialog(ActiveDialog.ModelImporterDialog) }
                    )
                    BottomStudioAction(
                        icon = Icons.Default.MeetingRoom,
                        label = "الغرفة",
                        tag = "btn_open_room",
                        onClick = { viewModel.openDialog(ActiveDialog.RoomSettings) }
                    )
                    BottomStudioAction(
                        icon = Icons.Default.Layers,
                        label = "الطبقات",
                        tag = "btn_open_layers",
                        onClick = { viewModel.openDialog(ActiveDialog.Layers) }
                    )
                    BottomStudioAction(
                        icon = Icons.Default.DataObject,
                        label = "تصدير JSON",
                        tag = "btn_open_json_export",
                        onClick = {
                            exportStudioInitialTab = ExportTab.JSON_DATA
                            viewModel.openDialog(ActiveDialog.ExportStudio)
                        }
                    )
                    BottomStudioAction(
                        icon = Icons.Default.FolderSpecial,
                        label = "المشاريع",
                        tag = "btn_open_projects_manager",
                        onClick = { viewModel.openProjectsManager() }
                    )
                    BottomStudioAction(
                        icon = Icons.Default.DashboardCustomize,
                        label = "نماذج",
                        tag = "btn_open_templates",
                        onClick = { showTemplatesDialog = true }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .onGloballyPositioned { coords ->
                    workspaceRootOrigin = coords.positionInRoot()
                }
        ) {
            // Viewport Rendering Canvas (3D, 2D, or Split)
            when (state.viewMode) {
                ViewMode.PERSPECTIVE_3D -> {
                    Viewport3D(
                        state = state,
                        onOrbit = { dyaw, dpitch -> viewModel.updateCameraOrbit(dyaw, dpitch) },
                        onZoom = { factor -> viewModel.updateCameraZoom(factor) },
                        onSelectElement = { id -> viewModel.selectElement(id) },
                        onMoveSelected = { dx, dy, dz -> viewModel.moveSelected(dx, dy, dz) },
                        onMoveElementToPosition = { id, targetPos ->
                            viewModel.moveElementToTargetPosition(id, targetPos)
                        },
                        onCommitTransform = { id, pos, rot, dims ->
                            viewModel.commitTransformCommand(id, pos, rot, dims)
                        },
                        onPlaceFurnitureAt = { xMm, zMm ->
                            viewModel.placeFurnitureAtCoordinate(xMm, zMm)
                        },
                        onResizeSelected = { id, newW, newH, newD ->
                            val elem = state.project.elements.find { it.id == id }
                            if (elem != null) {
                                viewModel.setExactNumericTransform(
                                    elementId = id,
                                    posX = elem.position.x,
                                    posY = elem.position.y,
                                    posZ = elem.position.z,
                                    rotY = elem.rotationY,
                                    dimW = newW,
                                    dimH = newH,
                                    dimD = newD
                                )
                            }
                        },
                        onPan = { dx, dy -> viewModel.updateCameraPan(dx, dy) },
                        onWalkMovement = { fwd, strafe -> viewModel.updateWalkMovement(fwd, strafe) },
                        onWalkLook = { dyaw, dpitch -> viewModel.updateWalkLook(dyaw, dpitch) },
                        onPresetSelected = { preset -> viewModel.animateCameraToPreset(preset) },
                        onToggleSprint = { viewModel.toggleWalkSprint() },
                        onExitWalk = { viewModel.setCameraMode(CameraMode.ORBIT) },
                        onResetPan = { viewModel.resetCameraPan() },
                        onOpenFillerTool = { viewModel.openDialog(ActiveDialog.CabinetFillerTool) },
                        onOpenRenderStudio = { viewModel.openDialog(ActiveDialog.RenderStudio) },
                        onToggleVrGyro = { viewModel.toggleVrGyroMode() },
                        onToggleVrSplit = { viewModel.toggleVrStereoscopicSplit() },
                        onToggleVrAutoWalk = { viewModel.toggleVrAutoWalk() },
                        onToggleVrCameraSensor = { viewModel.toggleVrCameraSensorMode() },
                        onUpdateVrCameraSensitivity = { sens -> viewModel.updateVrCameraSensitivity(sens) },
                        onUpdateVrCameraSubMode = { subMode -> viewModel.updateVrCameraSubMode(subMode) },
                        onUpdateLightingSimulation = { az, el, intensity ->
                            viewModel.updateLightingSimulation(az, el, intensity)
                        },
                        onChangeWallFinish = { mat -> viewModel.changeWallFinish(mat) },
                        onChangeFloorMaterial = { mat -> viewModel.changeFloorMaterial(mat) },
                        externalDragType = draggingCatalogType,
                        externalDragRootOffset = dragFingerRootOffset,
                        onExternalDragWorldUpdate = { xMm, zMm ->
                            dragResolvedWorldCoord = Pair(xMm, zMm)
                        }
                    )
                }
                ViewMode.FLOORPLAN_2D -> {
                    Viewport2D(
                        state = state,
                        onSelectElement = { id -> viewModel.selectElement(id) },
                        onMoveSelected = { dx, dy, dz -> viewModel.moveSelected(dx, dy, dz) },
                        onMoveElementToPosition = { id, targetPos, stepMm ->
                            viewModel.moveElementToTargetPosition(id, targetPos, snapStepMm = stepMm)
                        },
                        onCommitTransform = { id, pos, rot, dims ->
                            viewModel.commitTransformCommand(id, pos, rot, dims)
                        },
                        onPlaceFurnitureAt = { xMm, zMm ->
                            viewModel.placeFurnitureAtCoordinate(xMm, zMm)
                        },
                        onResizeSelected = { id, newW, newH, newD ->
                            val elem = state.project.elements.find { it.id == id }
                            if (elem != null) {
                                viewModel.setExactNumericTransform(
                                    elementId = id,
                                    posX = elem.position.x,
                                    posY = elem.position.y,
                                    posZ = elem.position.z,
                                    rotY = elem.rotationY,
                                    dimW = newW,
                                    dimH = newH,
                                    dimD = newD
                                )
                            }
                        },
                        onUndo = { viewModel.undo() },
                        onRedo = { viewModel.redo() },
                        onUndoToStep = { steps -> viewModel.undoSteps(steps) },
                        onRedoToStep = { steps -> viewModel.redoSteps(steps) },
                        onRotateSelected = { delta -> viewModel.rotateSelected(delta) },
                        onDeleteSelected = { viewModel.deleteSelectedElements() },
                        onDuplicateSelected = { viewModel.duplicateSelectedElements() },
                        onAlignSelectedToWall = { side -> viewModel.alignSelectedFlushToWall(side) },
                        onApplyRoomLayoutShape = { shape -> viewModel.applyRoomLayoutShape(shape) },
                        onExportFloorPlanDxf = { viewModel.openDialog(ActiveDialog.ExportStudio) },
                        onOpenFillerTool = { viewModel.openDialog(ActiveDialog.CabinetFillerTool) },
                        externalDragType = draggingCatalogType,
                        externalDragRootOffset = dragFingerRootOffset,
                        onExternalDragWorldUpdate = { xMm, zMm ->
                            dragResolvedWorldCoord = Pair(xMm, zMm)
                        }
                    )
                }
                ViewMode.SPLIT_VIEW -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Viewport3D(
                            state = state,
                            onOrbit = { dyaw, dpitch -> viewModel.updateCameraOrbit(dyaw, dpitch) },
                            onZoom = { factor -> viewModel.updateCameraZoom(factor) },
                            onSelectElement = { id -> viewModel.selectElement(id) },
                            onMoveSelected = { dx, dy, dz -> viewModel.moveSelected(dx, dy, dz) },
                            onMoveElementToPosition = { id, targetPos ->
                                viewModel.moveElementToTargetPosition(id, targetPos)
                            },
                            onCommitTransform = { id, pos, rot, dims ->
                                viewModel.commitTransformCommand(id, pos, rot, dims)
                            },
                            onPlaceFurnitureAt = { xMm, zMm ->
                                viewModel.placeFurnitureAtCoordinate(xMm, zMm)
                            },
                            onResizeSelected = { id, newW, newH, newD ->
                                val elem = state.project.elements.find { it.id == id }
                                if (elem != null) {
                                    viewModel.setExactNumericTransform(
                                        elementId = id,
                                        posX = elem.position.x,
                                        posY = elem.position.y,
                                        posZ = elem.position.z,
                                        rotY = elem.rotationY,
                                        dimW = newW,
                                        dimH = newH,
                                        dimD = newD
                                    )
                                }
                            },
                            onPan = { dx, dy -> viewModel.updateCameraPan(dx, dy) },
                            onWalkMovement = { fwd, strafe -> viewModel.updateWalkMovement(fwd, strafe) },
                            onWalkLook = { dyaw, dpitch -> viewModel.updateWalkLook(dyaw, dpitch) },
                            onPresetSelected = { preset -> viewModel.animateCameraToPreset(preset) },
                            onToggleSprint = { viewModel.toggleWalkSprint() },
                            onExitWalk = { viewModel.setCameraMode(CameraMode.ORBIT) },
                            onResetPan = { viewModel.resetCameraPan() },
                            onOpenFillerTool = { viewModel.openDialog(ActiveDialog.CabinetFillerTool) },
                            onOpenRenderStudio = { viewModel.openDialog(ActiveDialog.RenderStudio) },
                            onToggleVrGyro = { viewModel.toggleVrGyroMode() },
                            onToggleVrSplit = { viewModel.toggleVrStereoscopicSplit() },
                            onToggleVrAutoWalk = { viewModel.toggleVrAutoWalk() },
                            onToggleVrCameraSensor = { viewModel.toggleVrCameraSensorMode() },
                            onUpdateVrCameraSensitivity = { sens -> viewModel.updateVrCameraSensitivity(sens) },
                            onUpdateVrCameraSubMode = { subMode -> viewModel.updateVrCameraSubMode(subMode) },
                            onUpdateLightingSimulation = { az, el, intensity ->
                                viewModel.updateLightingSimulation(az, el, intensity)
                            },
                            onChangeWallFinish = { mat -> viewModel.changeWallFinish(mat) },
                            onChangeFloorMaterial = { mat -> viewModel.changeFloorMaterial(mat) },
                            externalDragType = draggingCatalogType,
                            externalDragRootOffset = dragFingerRootOffset,
                            onExternalDragWorldUpdate = { xMm, zMm ->
                                dragResolvedWorldCoord = Pair(xMm, zMm)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        HorizontalDivider(color = Color(0xFF334155), thickness = 2.dp)
                        Viewport2D(
                            state = state,
                            onSelectElement = { id -> viewModel.selectElement(id) },
                            onMoveSelected = { dx, dy, dz -> viewModel.moveSelected(dx, dy, dz) },
                            onMoveElementToPosition = { id, targetPos, stepMm ->
                                viewModel.moveElementToTargetPosition(id, targetPos, snapStepMm = stepMm)
                            },
                            onCommitTransform = { id, pos, rot, dims ->
                                viewModel.commitTransformCommand(id, pos, rot, dims)
                            },
                            onPlaceFurnitureAt = { xMm, zMm ->
                                viewModel.placeFurnitureAtCoordinate(xMm, zMm)
                            },
                            onResizeSelected = { id, newW, newH, newD ->
                                val elem = state.project.elements.find { it.id == id }
                                if (elem != null) {
                                    viewModel.setExactNumericTransform(
                                        elementId = id,
                                        posX = elem.position.x,
                                        posY = elem.position.y,
                                        posZ = elem.position.z,
                                        rotY = elem.rotationY,
                                        dimW = newW,
                                        dimH = newH,
                                        dimD = newD
                                    )
                                }
                            },
                            onUndo = { viewModel.undo() },
                            onRedo = { viewModel.redo() },
                            onUndoToStep = { steps -> viewModel.undoSteps(steps) },
                            onRedoToStep = { steps -> viewModel.redoSteps(steps) },
                            onRotateSelected = { delta -> viewModel.rotateSelected(delta) },
                            onDeleteSelected = { viewModel.deleteSelectedElements() },
                            onDuplicateSelected = { viewModel.duplicateSelectedElements() },
                            onAlignSelectedToWall = { side -> viewModel.alignSelectedFlushToWall(side) },
                            onApplyRoomLayoutShape = { shape -> viewModel.applyRoomLayoutShape(shape) },
                            onExportFloorPlanDxf = { viewModel.openDialog(ActiveDialog.ExportStudio) },
                            onOpenFillerTool = { viewModel.openDialog(ActiveDialog.CabinetFillerTool) },
                            externalDragType = draggingCatalogType,
                            externalDragRootOffset = dragFingerRootOffset,
                            onExternalDragWorldUpdate = { xMm, zMm ->
                                dragResolvedWorldCoord = Pair(xMm, zMm)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Section 1: Collapsible Slim Glassmorphic Sidebar (اللوحة الجانبية النحيفة القابلة للطي بضغطة زر واحدة)
            AnimatedVisibility(
                visible = state.isCollapsibleSidebarOpen,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it }),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp, top = 76.dp, bottom = 76.dp)
            ) {
                CollapsibleSlimGlassSidebar(
                    state = state,
                    onQuickAddElement = { type -> viewModel.addElement(type) },
                    onUpdateSelectedDimensions = { id, newW, newH, newD ->
                        val elem = state.project.elements.find { it.id == id }
                        if (elem != null) {
                            viewModel.setExactNumericTransform(
                                elementId = id,
                                posX = elem.position.x,
                                posY = elem.position.y,
                                posZ = elem.position.z,
                                rotY = elem.rotationY,
                                dimW = newW,
                                dimH = newH,
                                dimD = newD
                            )
                        }
                    },
                    onToggleBackPanel = { enabled ->
                        viewModel.toggleSelectedCabinetBackPanel(enabled)
                    },
                    onHideSidebar = { viewModel.toggleCollapsibleSidebar() },
                    onStartExternalDrag = handleStartExternalDrag,
                    onUpdateExternalDrag = handleUpdateExternalDrag,
                    onEndExternalDrag = handleEndExternalDrag
                )
            }

            // Floating expand pill when sidebar is hidden (hidden in Clean Canvas mode)
            if (!state.isCollapsibleSidebarOpen && !state.isCleanCanvasMode) {
                Surface(
                    shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.65f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable { viewModel.toggleCollapsibleSidebar() }
                        .testTag("expand_collapsible_sidebar_pill")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "إظهار اللوحة الجانبية", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        Text("المقاسات", color = Color.White, fontSize = 9.sp)
                    }
                }
            }

            // Top Floating Toolbar (Collapsible by default so it NEVER covers the 3D/2D design)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FloatingEditorToolbar(
                    state = state,
                    onToggleWallCreation = { viewModel.toggleWallCreationMode() },
                    onToggleFurniturePlacement = { viewModel.toggleFurniturePlacementMode() },
                    onToggleDimensionSnapping = { viewModel.toggleSmartSnapping() },
                    onSelectPlacementType = { type -> viewModel.setActivePlacementType(type) },
                    onQuickAddFurniture = { type -> viewModel.addElement(type) },
                    onSelectWallSide = { side -> viewModel.setSelectedWallSide(side) },
                    onUpdateRoomDimensions = { w, d, h -> viewModel.updateRoomDimensions(w, d, h) },
                    onUpdateWallThickness = { thick -> viewModel.updateWallThickness(thick) },
                    onQuickAddOpening = { wallId, openingType ->
                        val wall = state.project.room.walls.find { it.id == wallId }
                        val defaultOffset = ((wall?.lengthMm ?: 3000f) * 0.4f).coerceAtLeast(300f)
                        val defaultWidth = if (openingType == com.example.domain.model.OpeningType.DOOR) 900f else 1200f
                        val defaultHeight = if (openingType == com.example.domain.model.OpeningType.DOOR) 2100f else 1100f
                        val defaultSill = if (openingType == com.example.domain.model.OpeningType.DOOR) 0f else 950f
                        viewModel.addWallOpening(wallId, openingType, defaultOffset, defaultWidth, defaultHeight, defaultSill)
                    },
                    onSetSnapThreshold = { threshold -> viewModel.setSnapThreshold(threshold) },
                    onOpenDialog = { dialog -> viewModel.openDialog(dialog) },
                    onToggleCollapsePanels = { viewModel.toggleControlPanelsCollapsed() },
                    onStartExternalDrag = handleStartExternalDrag,
                    onUpdateExternalDrag = handleUpdateExternalDrag,
                    onEndExternalDrag = handleEndExternalDrag
                )

                // Section 2 & 3 Safety Constraint / Red Collision & Ergonomic Clearance Banner
                val activeCollisionMsg = state.collisionResult?.takeIf { it.hasCollision }?.message
                val bannerMsg = activeCollisionMsg?.takeIf { it.isNotBlank() } ?: state.safetyConstraintBanner
                val isRedCollisionAlert = state.collisionResult?.hasCollision == true
                bannerMsg?.let { msg ->
                    if (!state.isCleanCanvasMode) {
                        Surface(
                            color = if (isRedCollisionAlert) Color(0xFF7F1D1D).copy(alpha = 0.92f) else Color(0xFF0F172A).copy(alpha = 0.78f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isRedCollisionAlert) 1.5.dp else 1.dp,
                                color = if (isRedCollisionAlert) Color(0xFFEF4444) else Color(0xFFF59E0B)
                            ),
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                                .testTag("safety_constraint_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isRedCollisionAlert) Icons.Default.Warning else Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = if (isRedCollisionAlert) Color(0xFFEF4444) else Color(0xFFFBBF24),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    msg,
                                    color = if (isRedCollisionAlert) Color(0xFFFECACA) else Color(0xFFFDE68A),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "إغلاق التنبيه",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clickable { viewModel.clearSafetyConstraintBanner() }
                                )
                            }
                        }
                    }
                }

                // Always show Auto-Save status badge in CAD planning sessions (and Ergonomic chip when panels expanded)
                AnimatedVisibility(
                    visible = !state.isCleanCanvasMode
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AutoSaveStatusBadge(
                                autoSaveState = state.autoSaveState,
                                onClick = { viewModel.openDialog(ActiveDialog.AutoSaveSettings) }
                            )
                            ErgonomicStatusChip(
                                report = state.validationReport,
                                showOverlayOnCanvas = state.showErgonomicOverlay,
                                onClick = { viewModel.openDialog(ActiveDialog.ErgonomicInspector) }
                            )
                        }
                    }
                }
            }

            // Bottom-aligned Transform Controls Bar (Compact quick-action bar when element is selected)
            AnimatedVisibility(
                visible = state.selectedElementIds.isNotEmpty() && !state.isCleanCanvasMode,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .background(Color(0xFF0F172A).copy(alpha = 0.78f), RoundedCornerShape(10.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.duplicateSelectedElements() },
                            modifier = Modifier.size(30.dp).testTag("duplicate_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "تكرار", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = { viewModel.deleteSelectedElements() },
                            modifier = Modifier.size(30.dp).testTag("delete_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                        }
                        if (state.selectedElementIds.size >= 2) {
                            IconButton(
                                onClick = { viewModel.groupSelectedElements() },
                                modifier = Modifier.size(30.dp).testTag("group_button")
                            ) {
                                Icon(Icons.Default.GroupWork, contentDescription = "تجميع", tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            }
                        }
                        IconButton(
                            onClick = { viewModel.clearSelection() },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إلغاء التحديد", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }

                    TransformControlsBar(
                        state = state,
                        onMove = { dx, dy, dz ->
                            val elem = state.singleSelectedElement
                            if (elem != null) {
                                val oldPos = elem.position
                                val oldRot = elem.rotationY
                                val oldDims = elem.dimensions
                                viewModel.moveSelected(dx, dy, dz)
                                viewModel.commitTransformCommand(elem.id, oldPos, oldRot, oldDims)
                            }
                        },
                        onRotate = { delta -> viewModel.rotateSelected(delta) },
                        onSetGizmoMode = { mode -> viewModel.setGizmoMode(mode) },
                        onAlignWall = { side -> viewModel.alignSelectedFlushToWall(side) },
                        onToggleSnap = { viewModel.toggleSmartSnapping() },
                        onOpenAluminumCalculator = { viewModel.openDialog(ActiveDialog.AluminumMelamineCalculator) },
                        onOpenMaterials = { viewModel.openDialog(ActiveDialog.Materials) },
                        onOpenFillerTool = { viewModel.openDialog(ActiveDialog.CabinetFillerTool) },
                        onToggleExpanded = { viewModel.toggleTransformBarExpanded() }
                    )
                }
            }

            // Floating Drag Preview Card under Finger when dragging an element from a menu into the Workspace
            val activeDragType = draggingCatalogType
            val activeFingerOffset = dragFingerRootOffset
            if (activeDragType != null && activeFingerOffset != null) {
                val localOffset = activeFingerOffset - workspaceRootOrigin
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.90f),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF10B981)),
                    tonalElevation = 12.dp,
                    shadowElevation = 14.dp,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (localOffset.x - 95f).roundToInt(),
                                y = (localOffset.y - 110f).roundToInt()
                            )
                        }
                        .testTag("floating_drag_ghost_card")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CabinetPreviewThumbnail(
                            type = activeDragType,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E293B))
                        )
                        Column {
                            Text(
                                text = activeDragType.title,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val worldCoord = dragResolvedWorldCoord
                            Text(
                                text = if (worldCoord != null) {
                                    "أفلت للإدراج عند (${worldCoord.first.roundToInt()}، ${worldCoord.second.roundToInt()} مم)"
                                } else {
                                    "اسحب إلى مكان التركيب في المخطط"
                                },
                                color = Color(0xFF34D399),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogs & Sheets
    when (state.activeDialog) {
        is ActiveDialog.Catalog -> {
            CatalogBottomSheet(
                onSelectType = { type -> viewModel.addElement(type) },
                onSelectForDragPlacement = { type ->
                    viewModel.setActivePlacementType(type)
                    if (!state.isFurniturePlacementMode) {
                        viewModel.toggleFurniturePlacementMode()
                    }
                },
                onExportJson = {
                    exportStudioInitialTab = ExportTab.JSON_DATA
                    viewModel.openDialog(ActiveDialog.ExportStudio)
                },
                onOpenPhotoCabinetDesigner = {
                    viewModel.openDialog(ActiveDialog.PhotoCabinetDesigner)
                },
                onDismiss = { viewModel.openDialog(null) }
            )
        }
        is ActiveDialog.Materials -> {
            MaterialPickerSheet(
                customCameraMaterials = state.customCameraMaterials,
                onAddCustomCameraMaterial = { mat -> viewModel.addCustomCameraMaterial(mat) },
                onApplyCabinetMaterial = { mat -> viewModel.changeSelectedMaterial(mat) },
                onApplyCountertopMaterial = { mat -> viewModel.changeSelectedCountertop(mat) },
                onApplyFloorMaterial = { mat -> viewModel.changeFloorMaterial(mat) },
                onApplyWallFinish = { mat -> viewModel.changeWallFinish(mat) },
                onDismiss = { viewModel.openDialog(null) }
            )
        }
        is ActiveDialog.RoomSettings -> {
            RoomSettingsDialog(
                room = state.project.room,
                onUpdateDimensions = { w, d, h -> viewModel.updateRoomDimensions(w, d, h) },
                onAddOpening = { wallId, type, off, w, h, sill ->
                    viewModel.addWallOpening(wallId, type, off, w, h, sill)
                },
                onRemoveOpening = { wallId, opId ->
                    viewModel.removeWallOpening(wallId, opId)
                },
                onUpdateIndividualWall = { wallId, lenMm, hMm, angleDeg, enabled ->
                    viewModel.updateIndividualWall(wallId, lenMm, hMm, angleDeg, enabled)
                },
                onApplyRoomLayoutShape = { shape ->
                    viewModel.applyRoomLayoutShape(shape)
                },
                onAddUtilityPoint = { wallId, uType, offMm, elevMm, label ->
                    viewModel.addWallUtilityPoint(wallId, uType, offMm, elevMm, label = label)
                },
                onRemoveUtilityPoint = { wallId, uId ->
                    viewModel.removeWallUtilityPoint(wallId, uId)
                },
                onDismiss = { viewModel.openDialog(null) }
            )
        }
        is ActiveDialog.Layers -> {
            LayersPanelDialog(
                state = state,
                onSelectElement = { id, toggle -> viewModel.selectElement(id, toggle) },
                onSelectAll = { viewModel.selectAll() },
                onClearSelection = { viewModel.clearSelection() },
                onToggleMultiSelect = { viewModel.toggleMultiSelectMode() },
                onGroupSelected = { viewModel.groupSelectedElements() },
                onUngroupSelected = { viewModel.ungroupSelected() },
                onToggleLayerVisibility = { layer -> viewModel.toggleLayerVisibility(layer) },
                onToggleLayerLock = { layer -> viewModel.toggleLayerLock(layer) },
                onDismiss = { viewModel.openDialog(null) }
            )
        }
        is ActiveDialog.BillOfMaterialsDialog -> {
            BillOfMaterialsDialog(
                bom = state.billOfMaterials,
                unitSystem = state.unitSystem,
                onToggleUnitSystem = { viewModel.toggleUnitSystem() },
                onDismiss = { viewModel.openDialog(null) }
            )
        }
        is ActiveDialog.Templates -> {
            showTemplatesDialog = true
            viewModel.openDialog(null)
        }
        is ActiveDialog.SketchCanvas -> {
            SketchCanvasDialog(
                onApplyGeneratedRoom = { newRoom -> viewModel.applyCustomRoom(newRoom) },
                onApplyGeneratedScene = { newRoom, sketchedElements ->
                    viewModel.applySketchScene(newRoom, sketchedElements)
                },
                onDismiss = { viewModel.openDialog(null) }
            )
        }
        is ActiveDialog.PerspectiveOverlay -> {
            PerspectiveOverlayDialog(
                state = state,
                onMoveElement = { id, newPos ->
                    viewModel.moveElementToTargetPosition(
                        elementId = id,
                        rawTargetPos = newPos,
                        useSnapping = true,
                        snapStepMm = 1.0f,
                        autoAlignRotation = true
                    )
                },
                onSelectElement = { id ->
                    if (id != null) viewModel.selectElement(id) else viewModel.clearSelection()
                },
                onAddElement = { type -> viewModel.addElement(type) },
                onLoadTemplate = { templateId -> viewModel.loadTemplate(templateId) },
                onRotateSelected = { delta -> viewModel.rotateSelected(delta) },
                onResizeSelected = { id, newW, newH, newD ->
                    val elem = state.project.elements.find { it.id == id }
                    if (elem != null) {
                        viewModel.setExactNumericTransform(
                            elementId = id,
                            posX = elem.position.x,
                            posY = elem.position.y,
                            posZ = elem.position.z,
                            rotY = elem.rotationY,
                            dimW = newW,
                            dimH = newH,
                            dimD = newD
                        )
                    }
                },
                onDeleteSelected = { viewModel.deleteSelectedElements() },
                onCycleSelectedMaterial = { viewModel.cycleSelectedElementMaterial() },
                onApplyArMaterialSwap = { elementId, cabinetFinish, countertopFinish ->
                    viewModel.changeElementMaterialAndCountertop(
                        elementId = elementId,
                        cabinetMaterial = cabinetFinish,
                        countertopMaterial = countertopFinish
                    )
                },
                onImportOrUpdateJson = { jsonStr ->
                    viewModel.importProjectOrElementsFromJson(jsonStr).elements.size
                },
                onDismiss = { viewModel.openDialog(null) }
            )
        }
        is ActiveDialog.ModelImporterDialog -> {
            ModelImporterDialog(
                onAddModelToScene = { elem -> viewModel.addCustomImportedElement(elem) },
                onImportJsonToScene = { jsonStr ->
                    viewModel.importProjectOrElementsFromJson(jsonStr).elements.size
                },
                onAddCustomCameraMaterial = { mat -> viewModel.addCustomCameraMaterial(mat) },
                onOpenArCameraStudio = { viewModel.openDialog(ActiveDialog.PerspectiveOverlay) },
                onDismiss = { viewModel.openDialog(null) }
            )
        }
        is ActiveDialog.ParametricCabinet -> {
            ParametricCabinetDialog(
                initialElement = state.singleSelectedElement,
                onApplyCabinet = { cab -> viewModel.applyParametricCabinet(cab) },
                onDismiss = { viewModel.openDialog(null) }
            )
        }
        is ActiveDialog.AluminumMelamineCalculator -> {
            AluminumMelamineCalculatorDialog(
                selectedElement = state.singleSelectedElement,
                sceneCabinets = viewModel.getParametricCabinetsFromScene(),
                initialMaterialDimensionsConfig = state.standardMaterialDimensionsConfig,
                initialSavedSheetSizes = state.savedMelamineSheetSizes,
                onUpdateMaterialDimensionsConfig = { config -> viewModel.updateStandardMaterialDimensionsConfig(config) },
                onSaveCustomSheetSize = { sheetSize -> viewModel.saveCustomMelamineSheetSize(sheetSize) },
                onCreateOrUpdateCabinetIn3D = { cab -> viewModel.applyParametricCabinet(cab) },
                onOpenFullNestingOptimizer = { viewModel.openDialog(ActiveDialog.NestingOptimizer) },
                onOpenAluminumBarNesting = { viewModel.openDialog(ActiveDialog.AluminumBarNesting) },
                onDismiss = { viewModel.openDialog(null) }
            )
        }
        is ActiveDialog.AluminumBarNesting -> {
            AluminumBarNestingDialog(
                sceneCabinets = viewModel.getParametricCabinetsFromScene(),
                initialPrimaryBarLengthMm = state.standardAluminumBarLengthMm,
                initialAvailableBarLengthsMm = state.availableAluminumBarLengthsMm,
                initialCustomCuts = state.customAluminumCutInputs,
                onUpdateCustomCuts = { cuts -> viewModel.updateCustomAluminumCutInputs(cuts) },
                onUpdatePrimaryBarLengthMm = { lenMm -> viewModel.updateStandardAluminumBarLength(lenMm) },
                onDismiss = { viewModel.openDialog(null) }
            )
        }
        is ActiveDialog.NestingOptimizer -> {
            NestingOptimizerDialog(
                cabinets = viewModel.getParametricCabinetsFromScene(),
                savedPresets = state.savedMaterialPresets,
                activePreset = state.activeMaterialPreset,
                onApplyManualOverride = { preset -> viewModel.applyManualMaterialOverride(preset) },
                onSaveCustomPreset = { preset -> viewModel.saveCustomMaterialPreset(preset) },
                onToggleBackingAll = { hasBack, matType, groove ->
                    viewModel.toggleSelectedCabinetBackPanel(hasBack, matType, groove)
                },
                onOpenAluminumBarNesting = { viewModel.openDialog(ActiveDialog.AluminumBarNesting) },
                onExportCncDxf = { viewModel.openDialog(ActiveDialog.ExportStudio) },
                onDismiss = { viewModel.openDialog(null) }
            )
        }
        is ActiveDialog.RenderStudio -> {
            RenderStudioDialog(
                state = state,
                onDismiss = { viewModel.openDialog(null) }
            )
        }
        is ActiveDialog.ExportStudio -> {
            ExportStudioDialog(
                viewModel = viewModel,
                initialTab = exportStudioInitialTab,
                onDismiss = { viewModel.openDialog(null) }
            )
        }
        is ActiveDialog.ErgonomicInspector -> {
            ErgonomicValidationDialog(
                report = state.validationReport,
                showClearanceOverlay = state.showErgonomicOverlay,
                onToggleClearanceOverlay = { enabled -> viewModel.toggleErgonomicOverlay(enabled) },
                onFocusIssueElements = { issue -> viewModel.focusErgonomicIssue(issue) },
                onAutoFixIssue = { issue -> viewModel.autoFixErgonomicIssue(issue) },
                onDismiss = { viewModel.openDialog(null) }
            )
        }
        is ActiveDialog.AutoSaveSettings -> {
            AutoSaveStatusDialog(
                autoSaveState = state.autoSaveState,
                onToggleEnabled = { enabled -> viewModel.setAutoSaveEnabled(enabled) },
                onSelectIntervalSeconds = { sec -> viewModel.setAutoSaveIntervalSeconds(sec) },
                onSaveCheckpointNow = { viewModel.triggerImmediateAutoSave() },
                onRestoreSnapshot = { snapshot -> viewModel.restoreFromAutoSaveSnapshot(snapshot) },
                onDismiss = { viewModel.openDialog(null) }
            )
        }
        is ActiveDialog.ProjectsManager -> {
            ProjectsManagerDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.openDialog(null) }
            )
        }
        is ActiveDialog.CabinetFillerTool -> {
            CabinetFillerToolDialog(
                state = state,
                onApplyFillerConfig = { config -> viewModel.applyAutomaticWallFillers(config) },
                onRemoveWallFillers = { wallSide -> viewModel.removeWallFillers(wallSide) },
                onDismiss = { viewModel.openDialog(null) }
            )
        }
        is ActiveDialog.GeminiAiDesigner -> {
            GeminiKitchenDesignDialog(
                currentRoom = state.project.room,
                unitSystem = state.unitSystem,
                aiState = state.aiDesignState,
                onGenerateDesign = { w, d, h, shape, style, island, notes ->
                    viewModel.generateGeminiKitchenDesign(
                        roomWidthMm = w,
                        roomDepthMm = d,
                        roomHeightMm = h,
                        layoutShape = shape,
                        stylePreference = style,
                        includeIsland = island,
                        customNotes = notes
                    )
                },
                onApplyGeneratedProject = { generatedProject ->
                    viewModel.applyImportedProject(generatedProject)
                },
                onDismiss = { viewModel.openDialog(null) }
            )
        }
        is ActiveDialog.SmartAccessories -> {
            SmartAccessoriesDialog(
                elements = state.project.elements,
                enabledAccessoryIds = state.enabledAccessoryIds,
                onToggleAccessory = { id -> viewModel.toggleAccessoryEnabled(id) },
                onEnableAllCompatible = { viewModel.enableAllCompatibleAccessories() },
                onOpenBomDialog = { viewModel.openDialog(ActiveDialog.BillOfMaterialsDialog) },
                onDismiss = { viewModel.openDialog(null) }
            )
        }
        is ActiveDialog.PhotoCabinetDesigner -> {
            PhotoCabinetDesignerDialog(
                onCreateCustomCabinet = { customName, baseType, dimsMm, frontMat, sideMat, counterMat, hasBack, hasSides, boardThick, divs, frontUri, sideUri ->
                    viewModel.addPhotoDesignedCabinet(
                        customName = customName,
                        baseType = baseType,
                        dimensionsMm = dimsMm,
                        frontMaterial = frontMat,
                        sideMaterial = sideMat,
                        countertopMaterial = counterMat,
                        hasBackPanel = hasBack,
                        hasFullSidePanels = hasSides,
                        boardThicknessMm = boardThick,
                        divisionsCount = divs,
                        frontPhotoUri = frontUri,
                        sidePhotoUri = sideUri
                    )
                },
                onDismiss = { viewModel.openDialog(null) }
            )
        }
        null -> {}
    }

    // Templates Dialog
    if (showTemplatesDialog) {
        AlertDialog(
            onDismissRequest = { showTemplatesDialog = false },
            title = { Text("نماذج وتصاميم مطابخ معمارية جاهزة", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TemplateItem(
                        title = "مطبخ ملكي مزدوج للتاج مع فيترينة ونيش LED",
                        desc = "خزائن تاج للسقف، عمود 4 فتحات نيش مضاءة، فيترينة زجاج أسود وبرج فرن",
                        onClick = {
                            viewModel.loadTemplate("template_l_shape")
                            showTemplatesDialog = false
                        }
                    )
                    TemplateItem(
                        title = "مطبخ شامبين أكريليك مع بار مضاء وفيترينة LED",
                        desc = "خزائن شامبين لامعة، تاج زجاجي مضاء، بار بفتحات نيش وإضاءة سقف خطية",
                        onClick = {
                            viewModel.loadTemplate("template_luxury_vitrine")
                            showTemplatesDialog = false
                        }
                    )
                    TemplateItem(
                        title = "مطبخ الجزيرة الفاخر (Chef's Island)",
                        desc = "جزيرة وسطية رخامية مع أبراج مؤونة وفرن مدمج",
                        onClick = {
                            viewModel.loadTemplate("template_island")
                            showTemplatesDialog = false
                        }
                    )
                    TemplateItem(
                        title = "مطبخ متكامل شكل U-Shape",
                        desc = "مثلث حركة مريح مع استغلال أمثل للجدران",
                        onClick = {
                            viewModel.loadTemplate("template_u_shape")
                            showTemplatesDialog = false
                        }
                    )
                    TemplateItem(
                        title = "مطبخ خطي للشقق (Linear Studio)",
                        desc = "مطبخ أنيق على جدار واحد بمظهر ناعم وعصري",
                        onClick = {
                            viewModel.loadTemplate("template_linear")
                            showTemplatesDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTemplatesDialog = false }) {
                    Text("إلغاء")
                }
            },
            containerColor = Color(0xFF0F172A),
            textContentColor = Color.White,
            titleContentColor = Color.White
        )
    }
}

/**
 * Section 1: Slim Collapsible & Glassmorphic Side Panel (اللوحات الجانبية القابلة للطي + القوائم العائمة الشفافة)
 * Houses dimension controls, quick cabinet insertion, and instant back-panel 3D hollowing without blocking rear units.
 */
@Composable
private fun CollapsibleSlimGlassSidebar(
    state: KitchenEditorUiState,
    onQuickAddElement: (KitchenElementType) -> Unit,
    onUpdateSelectedDimensions: (String, Float, Float, Float) -> Unit,
    onToggleBackPanel: (Boolean) -> Unit,
    onHideSidebar: () -> Unit,
    onStartExternalDrag: ((KitchenElementType, Offset) -> Unit)? = null,
    onUpdateExternalDrag: ((Offset) -> Unit)? = null,
    onEndExternalDrag: (() -> Unit)? = null
) {
    val selected = state.singleSelectedElement
    var sidebarCategoryFilter by remember { mutableStateOf<ElementCategory?>(null) }
    val allCatalogTypes = remember { KitchenElementType.values().toList() }
    val displayedTypes = remember(sidebarCategoryFilter) {
        if (sidebarCategoryFilter == null) allCatalogTypes
        else allCatalogTypes.filter { it.category == sidebarCategoryFilter }
    }

    val sidebarCategories: List<Pair<ElementCategory?, String>> = remember {
        listOf(
            null to "الكل",
            ElementCategory.BASE_CABINET to "سفلية",
            ElementCategory.WALL_CABINET to "علوية",
            ElementCategory.TALL_UNIT to "أبراج",
            ElementCategory.APPLIANCE to "أجهزة",
            ElementCategory.ISLAND_AND_SURFACE to "جزر"
        )
    }

    Surface(
        modifier = Modifier
            .width(214.dp)
            .fillMaxHeight()
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            .testTag("collapsible_slim_sidebar"),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.68f), // Translucent Glassmorphic UI
        tonalElevation = 6.dp
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
                    text = "قائمة العناصر والمقاسات",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onHideSidebar,
                    modifier = Modifier.size(22.dp).testTag("hide_sidebar_inside_btn")
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "إخفاء", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.14f), modifier = Modifier.padding(vertical = 4.dp))

            if (selected != null) {
                Text(
                    text = "المحدد: ${selected.name}",
                    color = Color(0xFF38BDF8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "العرض ${(selected.dimensions.x / 10f).toInt()}×الارتفاع ${(selected.dimensions.y / 10f).toInt()}×العمق ${(selected.dimensions.z / 10f).toInt()} سم",
                    color = Color.White,
                    fontSize = 10.sp
                )
                Text(
                    text = if (selected.type.category == ElementCategory.WALL_CABINET)
                        "ارتفاع القاع: ${(selected.position.y / 10f).toInt()} سم (سفلية + 60سم)"
                    else "الارتفاع Y: ${(selected.position.y / 10f).toInt()} سم",
                    color = Color(0xFF34D399),
                    fontSize = 9.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ظهر الخزانة 3D:", color = Color(0xFFCBD5E1), fontSize = 10.sp)
                    Switch(
                        checked = selected.hasBackPanel,
                        onCheckedChange = { onToggleBackPanel(it) },
                        modifier = Modifier.testTag("sidebar_back_panel_switch")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onUpdateSelectedDimensions(
                                selected.id,
                                selected.dimensions.x,
                                (selected.dimensions.y + 50f).coerceAtMost(2400f),
                                selected.dimensions.z
                            )
                        },
                        contentPadding = PaddingValues(2.dp),
                        modifier = Modifier.weight(1f).height(26.dp).testTag("sidebar_increase_height_btn")
                    ) {
                        Text("+ارتفاع 5سم", fontSize = 9.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            onUpdateSelectedDimensions(
                                selected.id,
                                (selected.dimensions.x + 50f).coerceAtMost(1800f),
                                selected.dimensions.y,
                                selected.dimensions.z
                            )
                        },
                        contentPadding = PaddingValues(2.dp),
                        modifier = Modifier.weight(1f).height(26.dp)
                    ) {
                        Text("+عرض 5سم", fontSize = 9.sp)
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.14f), modifier = Modifier.padding(vertical = 4.dp))
            }

            Text(
                text = "اسحب العنصر للمخطط أو اضغط للإدراج:",
                color = Color(0xFFFBBF24),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Category Filter Chips inside Sidebar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                sidebarCategories.forEach { (cat, label) ->
                    val isSelectedCat = sidebarCategoryFilter == cat
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelectedCat) Color(0xFF0284C7) else Color(0xFF1E293B),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelectedCat) Color(0xFF38BDF8) else Color(0xFF334155)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { sidebarCategoryFilter = cat }
                    ) {
                        Text(
                            text = label,
                            color = if (isSelectedCat) Color.White else Color(0xFFCBD5E1),
                            fontSize = 9.sp,
                            fontWeight = if (isSelectedCat) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(displayedTypes, key = { it.code }) { type ->
                    var itemRootPos by remember { mutableStateOf(Offset.Zero) }
                    var activeFingerRoot by remember { mutableStateOf(Offset.Zero) }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.75f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                itemRootPos = coords.positionInRoot()
                            }
                            .clickable { onQuickAddElement(type) }
                            .pointerInput(type) {
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        activeFingerRoot = itemRootPos + startOffset
                                        onStartExternalDrag?.invoke(type, activeFingerRoot)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        activeFingerRoot += dragAmount
                                        onUpdateExternalDrag?.invoke(activeFingerRoot)
                                    },
                                    onDragEnd = {
                                        onEndExternalDrag?.invoke()
                                    },
                                    onDragCancel = {
                                        onEndExternalDrag?.invoke()
                                    }
                                )
                            }
                            .testTag("sidebar_visual_item_${type.code}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Visual 3D Isometric + Elevation Shape of the Element
                            CabinetPreviewThumbnail(
                                type = type,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF0F172A))
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = type.title,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 12.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${(type.defaultDimensions.x / 10f).toInt()}×${(type.defaultDimensions.z / 10f).toInt()}سم",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "اسحب ✥",
                                        color = Color(0xFFF59E0B),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
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

@Composable
private fun BottomStudioAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(tag)
    ) {
        Icon(icon, contentDescription = label, tint = Color(0xFF38BDF8), modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, color = Color(0xFFCBD5E1), fontSize = 11.sp)
    }
}

@Composable
private fun TemplateItem(title: String, desc: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1E293B),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Color(0xFF38BDF8), fontSize = 13.sp)
            Text(desc, color = Color(0xFF94A3B8), fontSize = 11.sp)
        }
    }
}
