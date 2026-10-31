package com.example.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.KitchenDatabase
import com.example.data.local.autosave.AutoSaveTrigger
import com.example.data.local.autosave.AutoSaveUiState
import com.example.data.local.autosave.ProjectAutoSaveManager
import com.example.data.local.entity.AutoSaveSnapshotEntity
import com.example.data.local.relation.SavedCameraState
import com.example.core.cam.AluminumBarNestingOptimizer
import com.example.core.cam.AluminumNestingConfig
import com.example.core.cam.AluminumNestingResult
import com.example.core.cam.CabinetryAluminumCutInput
import com.example.core.cam.CustomMaterialOverridePreset
import com.example.core.cam.DoorStyle
import com.example.core.cam.HandleStyle
import com.example.core.cam.ParametricCabinet
import com.example.core.command.*
import com.example.core.geometry.*
import com.example.core.math.MathUtils
import com.example.core.math.Vector3
import com.example.data.repository.KitchenProjectRepositoryImpl
import com.example.domain.model.*
import com.example.domain.repository.BillOfMaterials
import com.example.domain.repository.KitchenProject
import com.example.domain.repository.KitchenProjectRepository
import com.example.core.camera.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class ViewMode(val title: String) {
    PERSPECTIVE_3D("3D مجسم"),
    FLOORPLAN_2D("2D مخطط CAD"),
    SPLIT_VIEW("شاشة منقسمة")
}

enum class CameraPreset(val title: String, val yaw: Float, val pitch: Float) {
    PERSPECTIVE("منظور عام", 35f, 30f),
    ISOMETRIC("آيزومتري 45°", 45f, 35f),
    TOP_DOWN("مسقط علوي", 0f, 89f),
    FRONT("واجهة أمامية", 0f, 5f)
}

enum class GizmoMode(val title: String) {
    TRANSLATE("تحريك (Position)"),
    ROTATE("تدوير (Rotation)"),
    DIMENSIONS("الأبعاد (Dimensions)")
}

sealed class ActiveDialog {
    data object Catalog : ActiveDialog()
    data object RoomSettings : ActiveDialog()
    data object Materials : ActiveDialog()
    data object Layers : ActiveDialog()
    data object BillOfMaterialsDialog : ActiveDialog()
    data object Templates : ActiveDialog()
    data object SketchCanvas : ActiveDialog()
    data object PerspectiveOverlay : ActiveDialog()
    data object ModelImporterDialog : ActiveDialog()
    data object ParametricCabinet : ActiveDialog()
    data object NestingOptimizer : ActiveDialog()
    data object RenderStudio : ActiveDialog()
    data object ExportStudio : ActiveDialog()
    data object ErgonomicInspector : ActiveDialog()
    data object AutoSaveSettings : ActiveDialog()
    data object AluminumMelamineCalculator : ActiveDialog()
    data object AluminumBarNesting : ActiveDialog()
    data object ProjectsManager : ActiveDialog()
    data object CabinetFillerTool : ActiveDialog()
}

data class KitchenEditorUiState(
    val project: KitchenProject,
    val selectedElementIds: Set<String> = emptySet(),
    val isMultiSelectMode: Boolean = false,
    val isSmartSnappingEnabled: Boolean = true,
    val snapThresholdMm: Float = 80f,
    val activeSnapGuideLines: List<SnapGuideLine> = emptyList(),
    val collisionResult: CollisionResult? = null,
    val viewMode: ViewMode = ViewMode.PERSPECTIVE_3D,
    val cameraMode: CameraMode = CameraMode.ORBIT,
    val cameraYaw: Float = 35f,
    val cameraPitch: Float = 30f,
    val cameraZoom: Float = 1.0f,
    val cameraPanX: Float = 0f,
    val cameraPanY: Float = 0f,
    val walkCameraPosition: Vector3 = Vector3(2500f, 1600f, 2500f),
    val walkLookYaw: Float = 0f,
    val walkLookPitch: Float = 0f,
    val isWalkSprinting: Boolean = false,
    val walkCollisionFeedback: String? = null,
    val isCameraTweening: Boolean = false,
    val activeCameraPreset: CameraNavigationPreset? = null,
    val activeDialog: ActiveDialog? = null,
    val undoRedoState: UndoRedoState = UndoRedoState(),
    val gizmoMode: GizmoMode = GizmoMode.TRANSLATE,
    val billOfMaterials: BillOfMaterials? = null,
    val hiddenLayers: Set<LayerType> = emptySet(),
    val lockedLayers: Set<LayerType> = emptySet(),
    val isWallCreationMode: Boolean = false,
    val isFurniturePlacementMode: Boolean = false,
    val activePlacementType: KitchenElementType = KitchenElementType.BASE_SINGLE_DOOR,
    val selectedWallSide: WallSide = WallSide.NORTH,
    val showLiveDimensions: Boolean = true,
    val showErgonomicOverlay: Boolean = true,
    val validationReport: ErgonomicValidationReport = ErgonomicValidationEngine.validateProject(project),
    val autoSaveState: AutoSaveUiState = AutoSaveUiState(),
    val isCollapsibleSidebarOpen: Boolean = false,
    val isControlPanelsCollapsed: Boolean = true,
    val isCleanCanvasMode: Boolean = false,
    val isTransformBarExpanded: Boolean = false,
    val customCameraMaterials: List<MaterialFinish> = emptyList(),
    val safetyConstraintBanner: String? = null,
    val savedMaterialPresets: List<CustomMaterialOverridePreset> = CustomMaterialOverridePreset.DEFAULT_PRESETS,
    val activeMaterialPreset: CustomMaterialOverridePreset = CustomMaterialOverridePreset.DEFAULT_PRESETS.first(),
    val workshopName: String = "ورشة التصميم الهندسي للمطابخ الذكية",
    val workshopLogoBitmap: android.graphics.Bitmap? = null,
    val standardAluminumBarLengthMm: Float = 6000f,
    val availableAluminumBarLengthsMm: List<Float> = listOf(6000f, 5800f, 6500f, 4500f),
    val customAluminumCutInputs: List<CabinetryAluminumCutInput> = emptyList()
) {
    val singleSelectedElement: KitchenElement?
        get() = if (selectedElementIds.size == 1) {
            project.elements.find { it.id == selectedElementIds.first() }
        } else null

    val selectedElements: List<KitchenElement>
        get() = project.elements.filter { it.id in selectedElementIds }
}

class KitchenEditorViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: KitchenProjectRepository = KitchenProjectRepositoryImpl(
        dao = KitchenDatabase.getInstance(application).kitchenProjectDao()
    )
) : AndroidViewModel(application) {

    private val commandHistory = CommandHistory(maxCapacity = 150)

    private val autoSaveManager = ProjectAutoSaveManager(
        scope = viewModelScope,
        repository = repository
    )

    private val _uiState = MutableStateFlow(
        KitchenEditorUiState(
            project = KitchenProjectRepositoryImpl.createLShapedTemplate()
        )
    )
    val uiState: StateFlow<KitchenEditorUiState> = _uiState.asStateFlow()

    val savedProjects: StateFlow<List<com.example.data.local.entity.ProjectMetadataEntity>> = repository.getAllSavedProjects()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            autoSaveManager.state.collect { autoSaveUi ->
                _uiState.update { it.copy(autoSaveState = autoSaveUi) }
            }
        }
        viewModelScope.launch {
            repository.getProject().collect { proj ->
                // Automatically sanitize any legacy overlapping elements loaded from persistence
                val sanitizedElements = CollisionDetector.resolveAllSceneOverlaps(proj.elements, proj.room)
                val cleanProj = if (sanitizedElements != proj.elements) {
                    proj.copy(elements = sanitizedElements)
                } else {
                    proj
                }
                _uiState.update { it.copy(project = cleanProj) }
            }
        }
        viewModelScope.launch {
            repository.getSavedCameraState(_uiState.value.project.id).collect { savedCam ->
                if (savedCam != null) {
                    val mode = try { CameraMode.valueOf(savedCam.mode) } catch (e: Exception) { CameraMode.ORBIT }
                    _uiState.update {
                        it.copy(
                            cameraMode = mode,
                            cameraYaw = savedCam.yaw,
                            cameraPitch = savedCam.pitch,
                            cameraZoom = savedCam.zoom,
                            cameraPanX = savedCam.panX,
                            cameraPanY = savedCam.panY,
                            walkCameraPosition = Vector3(savedCam.walkPosX, savedCam.walkPosY, savedCam.walkPosZ),
                            walkLookYaw = savedCam.walkLookYaw,
                            walkLookPitch = savedCam.walkLookPitch
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            commandHistory.state.collect { historyState ->
                _uiState.update { it.copy(undoRedoState = historyState) }
            }
        }
        // Real-time ergonomic validation & periodic/debounced Room auto-save on any project change
        viewModelScope.launch {
            _uiState
                .map { it.project }
                .distinctUntilChanged()
                .collect { proj ->
                    val report = ErgonomicValidationEngine.validateProject(proj)
                    _uiState.update { it.copy(validationReport = report) }
                    repository.updateProject(proj)
                    autoSaveManager.onProjectStateModified(proj, currentSavedCameraState())
                }
        }
        // Auto-persist camera coordinates to SQLite Room database & update Auto-Save Manager
        viewModelScope.launch {
            _uiState
                .map {
                    SavedCameraState(
                        mode = it.cameraMode.name,
                        yaw = it.cameraYaw,
                        pitch = it.cameraPitch,
                        zoom = it.cameraZoom,
                        panX = it.cameraPanX,
                        panY = it.cameraPanY,
                        walkEyeHeightMm = 1600f,
                        walkPosX = it.walkCameraPosition.x,
                        walkPosY = it.walkCameraPosition.y,
                        walkPosZ = it.walkCameraPosition.z,
                        walkLookYaw = it.walkLookYaw,
                        walkLookPitch = it.walkLookPitch
                    )
                }
                .distinctUntilChanged()
                .collect { camState ->
                    autoSaveManager.updateCameraCoordinates(camState)
                    repository.saveCameraState(_uiState.value.project.id, camState)
                }
        }
    }

    private fun currentSavedCameraState(): SavedCameraState {
        val s = _uiState.value
        return SavedCameraState(
            mode = s.cameraMode.name,
            yaw = s.cameraYaw,
            pitch = s.cameraPitch,
            zoom = s.cameraZoom,
            panX = s.cameraPanX,
            panY = s.cameraPanY,
            walkEyeHeightMm = 1600f,
            walkPosX = s.walkCameraPosition.x,
            walkPosY = s.walkCameraPosition.y,
            walkPosZ = s.walkCameraPosition.z,
            walkLookYaw = s.walkLookYaw,
            walkLookPitch = s.walkLookPitch
        )
    }

    // ==========================================
    // 1. TRANSFORMATIONS & COMMAND PATTERN
    // ==========================================

    /**
     * Moves selected elements by an explicit delta step (e.g., 1mm, 10mm, 50mm, 100mm nudge buttons).
     * Defaults to [useSnapping] = false so explicit directional button clicks move by the exact
     * millimeter step without being pulled back by the 80mm magnetic wall/neighbor snap radius,
     * while still clamping inside room boundaries and preventing physical cabinet overlap.
     */
    fun moveSelected(deltaX: Float, deltaY: Float, deltaZ: Float, useSnapping: Boolean = false) {
        val selected = _uiState.value.selectedElements
        if (selected.isEmpty()) return

        val room = _uiState.value.project.room
        val allElements = _uiState.value.project.elements

        val updatedElements = allElements.toMutableList()
        val guideLines = mutableListOf<SnapGuideLine>()
        var lastCollision: CollisionResult? = null
        var safetyBannerMsg: String? = null

        selected.forEach { elem ->
            if (elem.isLocked || elem.layer in _uiState.value.lockedLayers) return@forEach

            val (effW, effD) = CollisionDetector.getEffectiveFootprint(elem)
            val halfW = effW * 0.5f
            val halfD = effD * 0.5f
            val maxRoomX = (room.widthMm - halfW).coerceAtLeast(halfW)
            val maxRoomZ = (room.depthMm - halfD).coerceAtLeast(halfD)
            val maxRoomY = (room.heightMm - elem.dimensions.y).coerceAtLeast(0f)

            val rawPos = Vector3(
                x = (elem.position.x + deltaX).coerceIn(halfW, maxRoomX),
                y = (elem.position.y + deltaY).coerceIn(0f, maxRoomY),
                z = (elem.position.z + deltaZ).coerceIn(halfD, maxRoomZ)
            ).roundTo1mm()

            val candidate = elem.copy(position = rawPos)

            val snappedElement = if (useSnapping && _uiState.value.isSmartSnappingEnabled) {
                val snapRes = SmartSnapper.snap(
                    target = candidate,
                    existingElements = updatedElements,
                    room = room,
                    snapThresholdMm = _uiState.value.snapThresholdMm,
                    autoAlignRotationToWall = false,
                    preventOverlap = true
                )
                guideLines.addAll(snapRes.guideLines)
                if (snapRes.wasOverlapPrevented && snapRes.collisionReasonAr != null) {
                    safetyBannerMsg = snapRes.collisionReasonAr
                }
                candidate.copy(position = snapRes.position, rotationY = snapRes.rotationY)
            } else {
                candidate.copy(position = rawPos.roundTo1mm())
            }

            // Section 2: Enforce lower safety limit (base height + 60 cm = 600 mm minimum gap)
            val elevConstraint = ElevationAndSafetyEngine.enforceWallCabinetLowerSafetyLimit(
                candidate = snappedElement,
                existingElements = updatedElements,
                roomHeightMm = room.heightMm
            )
            if (elevConstraint.wasClampedByLowerSafetyLimit) {
                safetyBannerMsg = elevConstraint.safetyMessageAr
                    ?: "قيد حد الأمان السفلي: يُمنع نزول الخزانة العلوية لأقل من 60 سم فوق الخزانة السفلية (حرية كاملة للأعلى)"
            }

            val preResolvedElement = snappedElement.copy(
                position = elevConstraint.resolvedPosition,
                linkedBaseElementId = elevConstraint.linkedBaseElementId ?: snappedElement.linkedBaseElementId
            )

            // Strict Cabinet Overlap Prevention: never allow physical intersection with other cabinets
            val antiOverlap = CollisionDetector.resolveNonOverlappingMovement(
                original = elem,
                candidate = preResolvedElement,
                existingElements = updatedElements,
                room = room,
                toleranceMm = 1.0f
            )
            val finalElement = antiOverlap.resolvedElement
            if (antiOverlap.wasOverlapPrevented && antiOverlap.messageAr != null) {
                safetyBannerMsg = antiOverlap.messageAr
                antiOverlap.blockedByElementIds.forEach { blockId ->
                    updatedElements.find { it.id == blockId }?.let { blockElem ->
                        guideLines.add(
                            SnapGuideLine(
                                start = finalElement.position + Vector3(0f, finalElement.dimensions.y * 0.5f, 0f),
                                end = blockElem.position + Vector3(0f, blockElem.dimensions.y * 0.5f, 0f),
                                labelMm = "🛡️ منع تداخل (0 mm)",
                                isWallGuide = false,
                                isCollisionWarning = false
                            )
                        )
                    }
                }
            }

            // Check remaining safety zones and standard ergonomic clearance with 1mm precision
            val colCheck = CollisionDetector.checkCollision(
                candidate = finalElement,
                existingElements = updatedElements,
                room = room,
                includeErgonomicClearance = true
            )
            if (colCheck.hasCollision) {
                lastCollision = colCheck
                if (safetyBannerMsg == null && colCheck.message.isNotBlank()) {
                    safetyBannerMsg = colCheck.message
                }
            }

            val idx = updatedElements.indexOfFirst { it.id == elem.id }
            if (idx != -1) {
                updatedElements[idx] = finalElement
            }
        }

        val updatedProject = _uiState.value.project.copy(elements = updatedElements)
        _uiState.update {
            it.copy(
                project = updatedProject,
                activeSnapGuideLines = guideLines,
                collisionResult = lastCollision,
                safetyConstraintBanner = safetyBannerMsg
            )
        }
    }

    /**
     * Moves a specific element to an absolute target position computed from cumulative drag delta
     * (`dragStartPos + totalWorldDelta`). This eliminates magnetic snap lock during continuous 2D/3D/AR
     * touch dragging while preserving 1mm wall/neighbor snapping and physical anti-overlap.
     */
    fun moveElementToTargetPosition(
        elementId: String,
        rawTargetPos: Vector3,
        useSnapping: Boolean = true,
        snapStepMm: Float = 1.0f,
        autoAlignRotation: Boolean = false
    ) {
        val allElements = _uiState.value.project.elements
        val elem = allElements.find { it.id == elementId } ?: return
        if (elem.isLocked || elem.layer in _uiState.value.lockedLayers) return

        val room = _uiState.value.project.room
        val (effW, effD) = CollisionDetector.getEffectiveFootprint(elem)
        val halfW = effW * 0.5f
        val halfD = effD * 0.5f
        val maxRoomX = (room.widthMm - halfW).coerceAtLeast(halfW)
        val maxRoomZ = (room.depthMm - halfD).coerceAtLeast(halfD)
        val maxRoomY = (room.heightMm - elem.dimensions.y).coerceAtLeast(0f)

        val clampedRawPos = Vector3(
            x = rawTargetPos.x.coerceIn(halfW, maxRoomX),
            y = rawTargetPos.y.coerceIn(0f, maxRoomY),
            z = rawTargetPos.z.coerceIn(halfD, maxRoomZ)
        )
        val quantizedRawPos = SmartSnapper.quantizeToGridMm(clampedRawPos, snapStepMm.coerceAtLeast(1f))
        val candidate = elem.copy(position = quantizedRawPos)

        val updatedElements = allElements.toMutableList()
        val guideLines = mutableListOf<SnapGuideLine>()
        var safetyBannerMsg: String? = null

        val snappedElement = if (useSnapping && _uiState.value.isSmartSnappingEnabled) {
            val snapRes = SmartSnapper.snap(
                target = candidate,
                existingElements = updatedElements,
                room = room,
                snapThresholdMm = _uiState.value.snapThresholdMm,
                gridStepMm = snapStepMm.coerceAtLeast(1f),
                autoAlignRotationToWall = autoAlignRotation,
                preventOverlap = true
            )
            guideLines.addAll(snapRes.guideLines)
            if (snapRes.wasOverlapPrevented && snapRes.collisionReasonAr != null) {
                safetyBannerMsg = snapRes.collisionReasonAr
            }
            candidate.copy(position = snapRes.position, rotationY = snapRes.rotationY)
        } else {
            candidate
        }

        val elevConstraint = ElevationAndSafetyEngine.enforceWallCabinetLowerSafetyLimit(
            candidate = snappedElement,
            existingElements = updatedElements,
            roomHeightMm = room.heightMm
        )
        if (elevConstraint.wasClampedByLowerSafetyLimit) {
            safetyBannerMsg = elevConstraint.safetyMessageAr
                ?: "قيد حد الأمان السفلي: يُمنع نزول الخزانة العلوية لأقل من 60 سم فوق الخزانة السفلية"
        }

        val preResolvedElement = snappedElement.copy(
            position = elevConstraint.resolvedPosition,
            linkedBaseElementId = elevConstraint.linkedBaseElementId ?: snappedElement.linkedBaseElementId
        )

        val antiOverlap = CollisionDetector.resolveNonOverlappingMovement(
            original = elem,
            candidate = preResolvedElement,
            existingElements = updatedElements,
            room = room,
            toleranceMm = 1.0f
        )
        val finalElement = antiOverlap.resolvedElement
        if (antiOverlap.wasOverlapPrevented && antiOverlap.messageAr != null) {
            safetyBannerMsg = antiOverlap.messageAr
            antiOverlap.blockedByElementIds.forEach { blockId ->
                updatedElements.find { it.id == blockId }?.let { blockElem ->
                    guideLines.add(
                        SnapGuideLine(
                            start = finalElement.position + Vector3(0f, finalElement.dimensions.y * 0.5f, 0f),
                            end = blockElem.position + Vector3(0f, blockElem.dimensions.y * 0.5f, 0f),
                            labelMm = "🛡️ منع تداخل (0 mm)",
                            isWallGuide = false,
                            isCollisionWarning = false
                        )
                    )
                }
            }
        }

        val colCheck = CollisionDetector.checkCollision(
            candidate = finalElement,
            existingElements = updatedElements,
            room = room,
            includeErgonomicClearance = true
        )

        val idx = updatedElements.indexOfFirst { it.id == elem.id }
        if (idx != -1) {
            updatedElements[idx] = finalElement
        }

        _uiState.update {
            it.copy(
                project = it.project.copy(elements = updatedElements),
                activeSnapGuideLines = guideLines,
                collisionResult = if (colCheck.hasCollision) colCheck else null,
                safetyConstraintBanner = safetyBannerMsg
            )
        }
    }

    fun commitTransformCommand(elementId: String, oldPos: Vector3, oldRot: Float, oldDims: Vector3) {
        val currentElem = _uiState.value.project.elements.find { it.id == elementId } ?: return
        if (currentElem.position == oldPos && currentElem.rotationY == oldRot && currentElem.dimensions == oldDims) return

        val cmd = TransformCommand(
            elementId = elementId,
            oldPosition = oldPos,
            newPosition = currentElem.position,
            oldRotationY = oldRot,
            newRotationY = currentElem.rotationY,
            oldDimensions = oldDims,
            newDimensions = currentElem.dimensions,
            applyTransform = { id, pos, rot, dims ->
                applyElementTransform(id, pos, rot, dims)
            },
            elementName = currentElem.name
        )
        commandHistory.execute(cmd)
    }

    fun applyElementTransform(id: String, pos: Vector3, rot: Float, dims: Vector3) {
        val currentList = _uiState.value.project.elements
        val room = _uiState.value.project.room
        val roomHeightMm = room.heightMm
        val targetElem = currentList.find { it.id == id } ?: return
        val oldBaseTopY = targetElem.position.y + targetElem.dimensions.y

        val elevConstraint = ElevationAndSafetyEngine.enforceWallCabinetLowerSafetyLimit(
            candidate = targetElem.copy(position = pos, rotationY = rot, dimensions = dims),
            existingElements = currentList,
            roomHeightMm = roomHeightMm
        )

        val preResolvedElem = targetElem.copy(
            position = elevConstraint.resolvedPosition,
            rotationY = rot,
            dimensions = dims,
            linkedBaseElementId = elevConstraint.linkedBaseElementId ?: targetElem.linkedBaseElementId
        )

        // Prevent physical overlap when rotating, resizing, or numerically moving a cabinet
        val antiOverlap = CollisionDetector.resolveNonOverlappingMovement(
            original = targetElem,
            candidate = preResolvedElem,
            existingElements = currentList,
            room = room,
            toleranceMm = 1.0f
        )
        val nonOverlappingElem = antiOverlap.resolvedElement

        var updatedElements = currentList.map {
            if (it.id == id) nonOverlappingElem else it
        }

        // Section 2: Dynamic vertical linking — if a base cabinet height/elevation changed, auto-shift upper wall cabinets to maintain 60cm gap
        var dynamicLinkMsg: String? = null
        if (targetElem.type.category == ElementCategory.BASE_CABINET &&
            (dims.y != targetElem.dimensions.y || nonOverlappingElem.position.y != targetElem.position.y)
        ) {
            val beforeYMap = updatedElements.associate { it.id to it.position.y }
            updatedElements = ElevationAndSafetyEngine.propagateDynamicBaseHeightToWallCabinets(
                modifiedBaseId = id,
                oldBaseTopY = oldBaseTopY,
                elements = updatedElements,
                roomHeightMm = roomHeightMm
            )
            val shiftedCount = updatedElements.count { (beforeYMap[it.id] ?: it.position.y) != it.position.y }
            if (shiftedCount > 0) {
                dynamicLinkMsg = "الارتباط الديناميكي: تم تحريك $shiftedCount خزانة علوية تلقائياً للحفاظ على مسافة الـ 60 سم الثابتة"
            }
        }

        val modifiedElem = updatedElements.find { it.id == id }
        val colCheck = modifiedElem?.let {
            CollisionDetector.checkCollision(
                candidate = it,
                existingElements = updatedElements,
                room = room,
                includeErgonomicClearance = true
            )
        }

        val safetyMsg = when {
            antiOverlap.wasOverlapPrevented && antiOverlap.messageAr != null -> antiOverlap.messageAr
            dynamicLinkMsg != null -> dynamicLinkMsg
            elevConstraint.wasClampedByLowerSafetyLimit ->
                elevConstraint.safetyMessageAr ?: "قيد حد الأمان السفلي: تم تثبيت الخزانة العلوية على مسافة 60 سم كحد أدنى فوق الخزانة السفلية"
            colCheck?.hasCollision == true && colCheck.message.isNotBlank() -> colCheck.message
            else -> _uiState.value.safetyConstraintBanner
        }

        _uiState.update {
            it.copy(
                project = it.project.copy(elements = updatedElements),
                collisionResult = if (colCheck?.hasCollision == true) colCheck else null,
                safetyConstraintBanner = safetyMsg
            )
        }
    }

    fun rotateSelected(deltaDegrees: Float) {
        val selected = _uiState.value.selectedElements
        if (selected.isEmpty()) return

        selected.forEach { elem ->
            val oldRot = elem.rotationY
            val newRot = (oldRot + deltaDegrees) % 360f
            applyElementTransform(elem.id, elem.position, newRot, elem.dimensions)
            commitTransformCommand(elem.id, elem.position, oldRot, elem.dimensions)
        }
    }

    fun setExactNumericTransform(
        elementId: String,
        posX: Float,
        posY: Float,
        posZ: Float,
        rotY: Float,
        dimW: Float,
        dimH: Float,
        dimD: Float
    ) {
        val elem = _uiState.value.project.elements.find { it.id == elementId } ?: return
        val oldPos = elem.position
        val oldRot = elem.rotationY
        val oldDims = elem.dimensions

        val newPos = Vector3(posX, posY, posZ).roundTo1mm()
        val newRot = rotY % 360f
        val newDims = Vector3(dimW, dimH, dimD).roundTo1mm()

        applyElementTransform(elementId, newPos, newRot, newDims)
        commitTransformCommand(elementId, oldPos, oldRot, oldDims)
    }

    // Quick Alignment Tools
    fun alignSelectedFlushToWall(side: WallSide) {
        val elem = _uiState.value.singleSelectedElement ?: return
        val oldPos = elem.position
        val oldRot = elem.rotationY
        val room = _uiState.value.project.room

        val isRot = elem.rotationY == 90f || elem.rotationY == 270f
        val effW = if (isRot) elem.dimensions.z else elem.dimensions.x
        val effD = if (isRot) elem.dimensions.x else elem.dimensions.z
        val halfW = effW * 0.5f
        val halfD = effD * 0.5f

        val newPos = when (side) {
            WallSide.NORTH -> elem.position.copy(z = halfD)
            WallSide.SOUTH -> elem.position.copy(z = room.depthMm - halfD)
            WallSide.WEST -> elem.position.copy(x = halfW)
            WallSide.EAST -> elem.position.copy(x = room.widthMm - halfW)
        }
        applyElementTransform(elem.id, newPos.roundTo1mm(), oldRot, elem.dimensions)
        commitTransformCommand(elem.id, oldPos, oldRot, elem.dimensions)
    }

    // ==========================================
    // 2. MULTI-SELECTION & GROUPING
    // ==========================================

    fun selectElement(id: String, isShiftOrToggle: Boolean = false) {
        val elem = _uiState.value.project.elements.find { it.id == id } ?: return
        if (elem.layer in _uiState.value.hiddenLayers) return

        _uiState.update { state ->
            val newSelection = when {
                state.isMultiSelectMode || isShiftOrToggle -> {
                    if (id in state.selectedElementIds) {
                        state.selectedElementIds - id
                    } else {
                        state.selectedElementIds + id
                    }
                }
                else -> setOf(id)
            }
            val colCheck = if (newSelection.size == 1) {
                state.project.elements.find { it.id == newSelection.first() }?.let { selElem ->
                    CollisionDetector.checkCollision(
                        candidate = selElem,
                        existingElements = state.project.elements,
                        room = state.project.room,
                        includeErgonomicClearance = true
                    ).takeIf { it.hasCollision }
                }
            } else null
            state.copy(
                selectedElementIds = newSelection,
                activeSnapGuideLines = emptyList(),
                collisionResult = colCheck
            )
        }
    }

    fun toggleMultiSelectMode() {
        _uiState.update { it.copy(isMultiSelectMode = !it.isMultiSelectMode) }
    }

    fun selectAll() {
        val allVisibleIds = _uiState.value.project.elements
            .filter { it.isVisible && it.layer !in _uiState.value.hiddenLayers }
            .map { it.id }
            .toSet()
        _uiState.update { it.copy(selectedElementIds = allVisibleIds) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedElementIds = emptySet(), activeSnapGuideLines = emptyList(), collisionResult = null) }
    }

    fun groupSelectedElements(customName: String? = null) {
        val selectedIds = _uiState.value.selectedElementIds.toList()
        if (selectedIds.size < 2) return

        val groupId = "group_${UUID.randomUUID().toString().take(8)}"
        val groupName = customName ?: "مجموعة خزائن (${selectedIds.size})"
        val newGroup = KitchenGroup(id = groupId, name = groupName, elementIds = selectedIds)

        val cmd = GroupElementsCommand(
            group = newGroup,
            onGroup = { grp ->
                val updatedElements = _uiState.value.project.elements.map {
                    if (it.id in grp.elementIds) it.copy(groupId = grp.id) else it
                }
                val updatedGroups = _uiState.value.project.groups + grp
                _uiState.update { it.copy(project = it.project.copy(elements = updatedElements, groups = updatedGroups)) }
            },
            onUngroup = { gId ->
                val updatedElements = _uiState.value.project.elements.map {
                    if (it.groupId == gId) it.copy(groupId = null) else it
                }
                val updatedGroups = _uiState.value.project.groups.filter { it.id != gId }
                _uiState.update { it.copy(project = it.project.copy(elements = updatedElements, groups = updatedGroups)) }
            }
        )
        commandHistory.execute(cmd)
    }

    fun ungroupSelected() {
        val selected = _uiState.value.selectedElements
        val groupIds = selected.mapNotNull { it.groupId }.distinct()
        if (groupIds.isEmpty()) return

        val targetGroups = _uiState.value.project.groups.filter { it.id in groupIds }
        if (targetGroups.isEmpty()) return

        val cmd = UngroupElementsCommand(
            groups = targetGroups,
            onUngroupAll = { idsToRemove ->
                val idSet = idsToRemove.toSet()
                val updatedElements = _uiState.value.project.elements.map {
                    if (it.groupId in idSet) it.copy(groupId = null) else it
                }
                val updatedGroups = _uiState.value.project.groups.filter { it.id !in idSet }
                _uiState.update { it.copy(project = it.project.copy(elements = updatedElements, groups = updatedGroups)) }
            },
            onRestoreGroups = { groupsToRestore ->
                var updatedElements = _uiState.value.project.elements
                groupsToRestore.forEach { grp ->
                    updatedElements = updatedElements.map {
                        if (it.id in grp.elementIds) it.copy(groupId = grp.id) else it
                    }
                }
                val existingIds = _uiState.value.project.groups.map { it.id }.toSet()
                val updatedGroups = _uiState.value.project.groups + groupsToRestore.filter { it.id !in existingIds }
                _uiState.update { it.copy(project = it.project.copy(elements = updatedElements, groups = updatedGroups)) }
            }
        )
        commandHistory.execute(cmd)
    }

    // ==========================================
    // 3. ROOM ARCHITECTURE & DYNAMIC OPENINGS
    // ==========================================

    fun updateRoomDimensions(widthMm: Float, depthMm: Float, heightMm: Float) {
        val oldRoom = _uiState.value.project.room
        val updatedRoom = oldRoom.updateDimensions(widthMm, depthMm, heightMm)
        if (oldRoom == updatedRoom) return

        val cmd = ModifyRoomLayoutCommand(
            oldRoom = oldRoom,
            newRoom = updatedRoom,
            title = "تعديل أبعاد الغرفة (${updatedRoom.widthMm.toInt()}×${updatedRoom.depthMm.toInt()} مم)",
            description = "العرض: ${updatedRoom.widthMm.toInt()} مم | العمق: ${updatedRoom.depthMm.toInt()} مم | الارتفاع: ${updatedRoom.heightMm.toInt()} مم",
            onApplyRoom = { room ->
                _uiState.update { it.copy(project = it.project.copy(room = room)) }
            }
        )
        commandHistory.execute(cmd)
    }

    fun addWallOpening(
        wallId: String,
        type: OpeningType,
        offsetMm: Float,
        widthMm: Float,
        heightMm: Float,
        sillHeightMm: Float
    ) {
        val wall = _uiState.value.project.room.walls.find { it.id == wallId } ?: return
        val newOpening = WallOpening(
            id = "opening_${UUID.randomUUID().toString().take(6)}",
            type = type,
            offsetMm = offsetMm,
            widthMm = widthMm,
            heightMm = heightMm,
            sillHeightMm = sillHeightMm
        )
        val updatedWall = wall.copy(openings = wall.openings + newOpening)

        val cmd = ModifyWallCommand(
            oldWall = wall,
            newWall = updatedWall,
            customTitle = "إضافة ${type.title} (${wall.side.title})",
            customDescription = "إزاحة: ${offsetMm.toInt()} مم | عرض: ${widthMm.toInt()} مم",
            onApply = { w ->
                val newRoom = _uiState.value.project.room.updateWall(w)
                _uiState.update { it.copy(project = it.project.copy(room = newRoom)) }
            }
        )
        commandHistory.execute(cmd)
    }

    fun removeWallOpening(wallId: String, openingId: String) {
        val wall = _uiState.value.project.room.walls.find { it.id == wallId } ?: return
        val removedOpening = wall.openings.find { it.id == openingId }
        val updatedWall = wall.copy(openings = wall.openings.filter { it.id != openingId })

        val cmd = ModifyWallCommand(
            oldWall = wall,
            newWall = updatedWall,
            customTitle = "حذف ${removedOpening?.type?.title ?: "فتحة"} (${wall.side.title})",
            onApply = { w ->
                val newRoom = _uiState.value.project.room.updateWall(w)
                _uiState.update { it.copy(project = it.project.copy(room = newRoom)) }
            }
        )
        commandHistory.execute(cmd)
    }

    fun changeFloorMaterial(material: MaterialFinish) {
        val oldRoom = _uiState.value.project.room
        if (oldRoom.floorMaterial == material) return
        val updatedRoom = oldRoom.copy(floorMaterial = material)

        val cmd = ModifyRoomLayoutCommand(
            oldRoom = oldRoom,
            newRoom = updatedRoom,
            title = "تغيير خامة الأرضية (${material.name})",
            description = "تبديل أرضية الغرفة إلى ${material.name}",
            onApplyRoom = { room ->
                _uiState.update { it.copy(project = it.project.copy(room = room)) }
            }
        )
        commandHistory.execute(cmd)
    }

    fun changeWallFinish(material: MaterialFinish) {
        val oldRoom = _uiState.value.project.room
        if (oldRoom.wallFinish == material) return
        val updatedRoom = oldRoom.copy(wallFinish = material)

        val cmd = ModifyRoomLayoutCommand(
            oldRoom = oldRoom,
            newRoom = updatedRoom,
            title = "تغيير خامة الجدران (${material.name})",
            description = "تبديل كسوة الجدران إلى ${material.name}",
            onApplyRoom = { room ->
                _uiState.update { it.copy(project = it.project.copy(room = room)) }
            }
        )
        commandHistory.execute(cmd)
    }

    // Section 3: Individual Wall Lengths, Corner Angles & Layout Shape (L-Shape, U-Shape, Rectangular)
    fun updateIndividualWall(
        wallId: String,
        lengthMm: Float,
        heightMm: Float? = null,
        cornerAngleDegrees: Float? = null,
        isEnabled: Boolean? = null
    ) {
        val oldRoom = _uiState.value.project.room
        val updatedRoom = oldRoom.updateIndividualWall(
            wallId = wallId,
            newLengthMm = lengthMm,
            newCornerAngleDeg = cornerAngleDegrees,
            isEnabled = isEnabled
        )
        if (oldRoom == updatedRoom) return

        val targetWall = updatedRoom.walls.find { it.id == wallId }
        val cmd = ModifyRoomLayoutCommand(
            oldRoom = oldRoom,
            newRoom = updatedRoom,
            title = "تعديل جدار (${targetWall?.side?.title ?: wallId})",
            description = "طول: ${lengthMm.toInt()} مم | زاوية: ${(cornerAngleDegrees ?: targetWall?.cornerAngleDegrees ?: 90f).toInt()}°",
            onApplyRoom = { room ->
                _uiState.update { it.copy(project = it.project.copy(room = room)) }
            }
        )
        commandHistory.execute(cmd)
    }

    fun applyRoomLayoutShape(shape: RoomLayoutShape) {
        val oldRoom = _uiState.value.project.room
        val updatedRoom = oldRoom.applyLayoutShape(shape)
        if (oldRoom == updatedRoom) return

        val cmd = ModifyRoomLayoutCommand(
            oldRoom = oldRoom,
            newRoom = updatedRoom,
            title = "تغيير شكل المخطط (${shape.titleAr})",
            description = "تحويل تخطيط الغرفة إلى ${shape.titleAr}",
            onApplyRoom = { room ->
                _uiState.update { it.copy(project = it.project.copy(room = room)) }
            }
        )
        commandHistory.execute(cmd)
    }

    // Section 3: Manual MEP Utility Points (Plumbing, Gas, Ventilation, Electrical Sockets in 60cm zone)
    fun addWallUtilityPoint(
        wallId: String,
        type: UtilityPointType,
        offsetMm: Float,
        elevationMm: Float = type.defaultElevationMm,
        keepClearRadiusMm: Float = 150f,
        label: String = ""
    ) {
        val wall = _uiState.value.project.room.walls.find { it.id == wallId } ?: return
        // Enforce 60cm backsplash zone (850..1450mm) for electrical sockets if needed
        val finalElevationMm = if (type.isIn60cmBacksplashZone) {
            elevationMm.coerceIn(850f, 1450f)
        } else {
            elevationMm.coerceIn(50f, wall.heightMm - 50f)
        }
        val newUtility = WallUtilityPoint(
            id = "util_${UUID.randomUUID().toString().take(6)}",
            type = type,
            offsetFromWallStartMm = offsetMm.coerceIn(50f, (wall.lengthMm - 50f).coerceAtLeast(50f)),
            elevationFromFloorMm = finalElevationMm,
            label = label.ifBlank { type.titleAr }
        )
        val updatedWall = wall.copy(utilityPoints = wall.utilityPoints + newUtility)
        val cmd = ModifyWallCommand(
            oldWall = wall,
            newWall = updatedWall,
            customTitle = "إضافة نقطة خدمة (${type.titleAr})",
            customDescription = "جدار ${wall.side.title} | إزاحة: ${offsetMm.toInt()} مم | ارتفاع: ${finalElevationMm.toInt()} مم",
            onApply = { w ->
                val newRoom = _uiState.value.project.room.updateWall(w)
                _uiState.update { it.copy(project = it.project.copy(room = newRoom)) }
            }
        )
        commandHistory.execute(cmd)
    }

    fun removeWallUtilityPoint(wallId: String, utilityId: String) {
        val wall = _uiState.value.project.room.walls.find { it.id == wallId } ?: return
        val removed = wall.utilityPoints.find { it.id == utilityId }
        val updatedWall = wall.copy(utilityPoints = wall.utilityPoints.filter { it.id != utilityId })
        val cmd = ModifyWallCommand(
            oldWall = wall,
            newWall = updatedWall,
            customTitle = "حذف نقطة خدمة (${removed?.label ?: ""})",
            onApply = { w ->
                val newRoom = _uiState.value.project.room.updateWall(w)
                _uiState.update { it.copy(project = it.project.copy(room = newRoom)) }
            }
        )
        commandHistory.execute(cmd)
    }

    // ==========================================
    // 4. ELEMENT CRUD & CATALOG
    // ==========================================

    fun addElement(type: KitchenElementType, material: MaterialFinish? = null) {
        val room = _uiState.value.project.room
        val existingElements = _uiState.value.project.elements
        val defaultMat = material ?: KitchenElement.defaultMaterialForType(type)
        val defaultDims = type.defaultDimensions
        val activePreset = _uiState.value.activeMaterialPreset

        val isIsland = type.category == ElementCategory.ISLAND_AND_SURFACE
        val selectedAnchor = _uiState.value.singleSelectedElement

        val (candX, candZ, initialRot) = when {
            isIsland -> Triple(
                MathUtils.roundToMillimeter(room.widthMm * 0.5f),
                MathUtils.roundToMillimeter(room.depthMm * 0.58f),
                0f
            )
            selectedAnchor != null && kotlin.math.abs(selectedAnchor.position.y - type.defaultElevationY) < 400f -> {
                val anchorAabb = selectedAnchor.getAABB()
                Triple(
                    MathUtils.roundToMillimeter(anchorAabb.max.x + defaultDims.x * 0.5f),
                    MathUtils.roundToMillimeter(selectedAnchor.position.z),
                    selectedAnchor.rotationY
                )
            }
            else -> Triple(
                MathUtils.roundToMillimeter(room.widthMm * 0.5f),
                MathUtils.roundToMillimeter(defaultDims.z * 0.5f),
                0f
            )
        }

        val tempCandidate = KitchenElement(
            id = "elem_${UUID.randomUUID().toString().take(8)}",
            name = type.title,
            type = type,
            position = Vector3(candX, type.defaultElevationY, candZ),
            rotationY = initialRot,
            dimensions = defaultDims,
            material = defaultMat,
            hasBackPanel = activePreset.hasBackPanel,
            backPanelMaterialType = activePreset.backPanelMaterialName,
            backGrooveInsetMm = activePreset.backGrooveInsetMm,
            topStretcherWidthMm = activePreset.topStretcherWidthMm,
            customBoardThicknessMm = activePreset.melamineThicknessMm,
            customAluminumProfileMm = activePreset.aluminumProfileSectionSizeMm
        )

        // Section 2: Automatic Elevation Formula for WALL_CABINET -> (Base Cabinet Height from floor + 60 cm [600 mm] clear gap)
        val autoElevResult = ElevationAndSafetyEngine.calculateAutomaticWallCabinetElevation(
            candidate = tempCandidate,
            existingElements = existingElements
        )

        val elevCandidate = tempCandidate.copy(
            position = autoElevResult.resolvedPosition,
            linkedBaseElementId = autoElevResult.linkedBaseElementId
        )

        // Strict Anti-Overlap: place new cabinet in nearest free flush slot so it NEVER overlaps existing cabinets
        val newElem = CollisionDetector.findNearestFreePosition(
            candidate = elevCandidate,
            existingElements = existingElements,
            room = room,
            toleranceMm = 1.0f
        )

        val cmd = AddElementCommand(
            element = newElem,
            onAdd = { elem ->
                val safeElem = CollisionDetector.findNearestFreePosition(
                    candidate = elem,
                    existingElements = _uiState.value.project.elements,
                    room = _uiState.value.project.room
                )
                val updated = _uiState.value.project.elements + safeElem
                _uiState.update {
                    it.copy(
                        project = it.project.copy(elements = updated),
                        selectedElementIds = setOf(safeElem.id),
                        collisionResult = null,
                        safetyConstraintBanner = if (safeElem.position != elevCandidate.position) {
                            "🛡️ منع تداخل الخزائن: تم وضع «${safeElem.name}» تلقائياً في أقرب موضع متاح بدون تداخل"
                        } else null
                    )
                }
            },
            onRemove = { id ->
                val updated = _uiState.value.project.elements.filter { it.id != id }
                _uiState.update {
                    it.copy(
                        project = it.project.copy(elements = updated),
                        selectedElementIds = it.selectedElementIds - id
                    )
                }
            }
        )
        commandHistory.execute(cmd)
    }

    fun deleteSelectedElements() {
        val selected = _uiState.value.selectedElements
        if (selected.isEmpty()) return

        val cmd = DeleteElementsCommand(
            elements = selected,
            onRestore = { restored ->
                val updated = _uiState.value.project.elements + restored
                _uiState.update { it.copy(project = it.project.copy(elements = updated)) }
            },
            onDelete = { deletedIds ->
                val updated = _uiState.value.project.elements.filter { it.id !in deletedIds }
                _uiState.update {
                    it.copy(
                        project = it.project.copy(elements = updated),
                        selectedElementIds = emptySet()
                    )
                }
            }
        )
        commandHistory.execute(cmd)
    }

    fun duplicateSelectedElements() {
        val selected = _uiState.value.selectedElements
        if (selected.isEmpty()) return

        selected.forEach { orig ->
            val (effW, _) = CollisionDetector.getEffectiveFootprint(orig)
            val rawDuplicated = orig.copy(
                id = "elem_${UUID.randomUUID().toString().take(8)}",
                name = "${orig.name} (نسخة)",
                position = orig.position + Vector3(effW, 0f, 0f)
            )
            val duplicated = CollisionDetector.findNearestFreePosition(
                candidate = rawDuplicated,
                existingElements = _uiState.value.project.elements,
                room = _uiState.value.project.room
            )
            val cmd = AddElementCommand(
                element = duplicated,
                onAdd = { elem ->
                    val safeElem = CollisionDetector.findNearestFreePosition(
                        candidate = elem,
                        existingElements = _uiState.value.project.elements,
                        room = _uiState.value.project.room
                    )
                    val updated = _uiState.value.project.elements + safeElem
                    _uiState.update {
                        it.copy(
                            project = it.project.copy(elements = updated),
                            selectedElementIds = setOf(safeElem.id),
                            collisionResult = null
                        )
                    }
                },
                onRemove = { id ->
                    val updated = _uiState.value.project.elements.filter { it.id != id }
                    _uiState.update {
                        it.copy(
                            project = it.project.copy(elements = updated),
                            selectedElementIds = it.selectedElementIds - id
                        )
                    }
                }
            )
            commandHistory.execute(cmd)
        }
    }

    fun changeSelectedMaterial(material: MaterialFinish) {
        val selectedIds = _uiState.value.selectedElementIds
        if (selectedIds.isEmpty()) return

        val before = _uiState.value.project.elements
        val after = before.map {
            if (it.id in selectedIds) it.copy(material = material) else it
        }
        if (before == after) return

        val cmd = BatchFloorPlanLayoutCommand(
            beforeElements = before,
            afterElements = after,
            beforeSelection = selectedIds,
            afterSelection = selectedIds,
            title = "تغيير خامة الخزائن (${material.name})",
            description = "تحديث خامة ${selectedIds.size} عنصر إلى ${material.name}",
            category = CommandCategory.MATERIAL_FINISH,
            onApplyElements = { elems, sel ->
                _uiState.update { it.copy(project = it.project.copy(elements = elems), selectedElementIds = sel) }
            }
        )
        commandHistory.execute(cmd)
    }

    fun changeSelectedCountertop(material: MaterialFinish?) {
        val selectedIds = _uiState.value.selectedElementIds
        if (selectedIds.isEmpty()) return

        val before = _uiState.value.project.elements
        val after = before.map {
            if (it.id in selectedIds) it.copy(countertopMaterial = material) else it
        }
        if (before == after) return

        val cmd = BatchFloorPlanLayoutCommand(
            beforeElements = before,
            afterElements = after,
            beforeSelection = selectedIds,
            afterSelection = selectedIds,
            title = "تغيير سطح الرخام (${material?.name ?: "بدون"})",
            description = "تحديث رخامة ${selectedIds.size} عنصر",
            category = CommandCategory.MATERIAL_FINISH,
            onApplyElements = { elems, sel ->
                _uiState.update { it.copy(project = it.project.copy(elements = elems), selectedElementIds = sel) }
            }
        )
        commandHistory.execute(cmd)
    }

    // ==========================================
    // 5. LAYERS & VISIBILITY
    // ==========================================

    fun toggleLayerVisibility(layer: LayerType) {
        _uiState.update { state ->
            val newHidden = if (layer in state.hiddenLayers) {
                state.hiddenLayers - layer
            } else {
                state.hiddenLayers + layer
            }
            state.copy(hiddenLayers = newHidden)
        }
    }

    fun toggleLayerLock(layer: LayerType) {
        _uiState.update { state ->
            val newLocked = if (layer in state.lockedLayers) {
                state.lockedLayers - layer
            } else {
                state.lockedLayers + layer
            }
            state.copy(lockedLayers = newLocked)
        }
    }

    // ==========================================
    // 6. UI MODES & CAMERA
    // ==========================================

    // ==========================================
    // 6. UI MODES & CAMERA ENGINE
    // ==========================================

    private var cameraAnimationJob: Job? = null

    fun setViewMode(mode: ViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun setGizmoMode(mode: GizmoMode) {
        _uiState.update { it.copy(gizmoMode = mode) }
    }

    fun toggleSmartSnapping() {
        _uiState.update { it.copy(isSmartSnappingEnabled = !it.isSmartSnappingEnabled) }
    }

    fun toggleWallCreationMode() {
        _uiState.update {
            val next = !it.isWallCreationMode
            it.copy(
                isWallCreationMode = next,
                isFurniturePlacementMode = if (next) false else it.isFurniturePlacementMode
            )
        }
    }

    fun toggleFurniturePlacementMode() {
        _uiState.update {
            val next = !it.isFurniturePlacementMode
            it.copy(
                isFurniturePlacementMode = next,
                isWallCreationMode = if (next) false else it.isWallCreationMode
            )
        }
    }

    fun setActivePlacementType(type: KitchenElementType) {
        _uiState.update { it.copy(activePlacementType = type) }
    }

    fun setSelectedWallSide(side: WallSide) {
        _uiState.update { it.copy(selectedWallSide = side) }
    }

    fun setSnapThreshold(thresholdMm: Float) {
        _uiState.update { it.copy(snapThresholdMm = thresholdMm.coerceIn(1f, 300f)) }
    }

    fun toggleLiveDimensions() {
        _uiState.update { it.copy(showLiveDimensions = !it.showLiveDimensions) }
    }

    fun updateWallThickness(thicknessMm: Float) {
        val currentRoom = _uiState.value.project.room
        val clamped = thicknessMm.coerceIn(80f, 400f)
        if (currentRoom.wallThicknessMm == clamped) return
        val updatedWalls = currentRoom.walls.map { it.copy(thicknessMm = clamped) }
        val updatedRoom = currentRoom.copy(wallThicknessMm = clamped, walls = updatedWalls)

        val cmd = ModifyRoomLayoutCommand(
            oldRoom = currentRoom,
            newRoom = updatedRoom,
            title = "تعديل سماكة الجدران (${clamped.toInt()} مم)",
            description = "تغيير سماكة جدران الغرفة من ${currentRoom.wallThicknessMm.toInt()} إلى ${clamped.toInt()} مم",
            onApplyRoom = { room ->
                _uiState.update { it.copy(project = it.project.copy(room = room)) }
            }
        )
        commandHistory.execute(cmd)
    }

    fun placeFurnitureAtCoordinate(
        xMm: Float,
        zMm: Float,
        explicitType: KitchenElementType? = null
    ) {
        val state = _uiState.value
        val type = explicitType ?: state.activePlacementType
        val room = state.project.room
        val defaultDims = type.defaultDimensions
        val activePreset = state.activeMaterialPreset
        val halfW = defaultDims.x * 0.5f
        val halfD = defaultDims.z * 0.5f

        val clampedX = xMm.coerceIn(halfW, (room.widthMm - halfW).coerceAtLeast(halfW))
        val clampedZ = zMm.coerceIn(halfD, (room.depthMm - halfD).coerceAtLeast(halfD))

        val tempCandidate = KitchenElement(
            id = "elem_${UUID.randomUUID().toString().take(8)}",
            name = type.title,
            type = type,
            position = Vector3(
                x = MathUtils.roundToMillimeter(clampedX),
                y = type.defaultElevationY,
                z = MathUtils.roundToMillimeter(clampedZ)
            ),
            dimensions = defaultDims,
            material = KitchenElement.defaultMaterialForType(type),
            hasBackPanel = activePreset.hasBackPanel,
            backPanelMaterialType = activePreset.backPanelMaterialName,
            backGrooveInsetMm = activePreset.backGrooveInsetMm,
            topStretcherWidthMm = activePreset.topStretcherWidthMm,
            customBoardThicknessMm = activePreset.melamineThicknessMm,
            customAluminumProfileMm = activePreset.aluminumProfileSectionSizeMm
        )

        val autoElevResult = ElevationAndSafetyEngine.calculateAutomaticWallCabinetElevation(
            candidate = tempCandidate,
            existingElements = state.project.elements
        )
        val rawCandidate = tempCandidate.copy(
            position = autoElevResult.resolvedPosition,
            linkedBaseElementId = autoElevResult.linkedBaseElementId
        )

        var snapGuideLines: List<SnapGuideLine> = emptyList()
        val snappedElem = if (state.isSmartSnappingEnabled) {
            val snapRes = SmartSnapper.snap(
                target = rawCandidate,
                existingElements = state.project.elements,
                room = room,
                snapThresholdMm = state.snapThresholdMm,
                preventOverlap = true
            )
            snapGuideLines = snapRes.guideLines
            rawCandidate.copy(position = snapRes.position, rotationY = snapRes.rotationY)
        } else {
            rawCandidate
        }

        val elevConstraint = ElevationAndSafetyEngine.enforceWallCabinetLowerSafetyLimit(
            candidate = snappedElem,
            existingElements = state.project.elements,
            roomHeightMm = room.heightMm
        )
        val preResolvedElem = snappedElem.copy(
            position = elevConstraint.resolvedPosition,
            linkedBaseElementId = elevConstraint.linkedBaseElementId ?: snappedElem.linkedBaseElementId
        )

        // Check window safety zone (block solid upper cabinet over window; allow sink below window)
        val safetyCheck = ElevationAndSafetyEngine.evaluateWindowAndUtilitySafetyZones(
            candidate = preResolvedElem,
            room = room
        )
        if (safetyCheck.isBlockedByWindowSafetyZone) {
            _uiState.update {
                it.copy(
                    safetyConstraintBanner = safetyCheck.safetyNoticeAr
                        ?: "منطقة أمان النافذة: يُمنع تركيب خزانة علوية مصمتة فوق الشباك (يُسمح بوضع المجلى أسفله)"
                )
            }
            return
        }

        // Strict Anti-Overlap: ensure placed cabinet never overlaps an existing cabinet
        val finalElem = CollisionDetector.findNearestFreePosition(
            candidate = preResolvedElem,
            existingElements = state.project.elements,
            room = room,
            toleranceMm = 1.0f
        )
        val wasRelocatedForOverlap = finalElem.position != preResolvedElem.position

        val placementColCheck = CollisionDetector.checkCollision(
            candidate = finalElem,
            existingElements = state.project.elements,
            room = room,
            includeErgonomicClearance = true
        )

        val previousSelection = state.selectedElementIds
        val cmd = PlaceFurniture2DCommand(
            element = finalElem,
            previousSelectedIds = previousSelection,
            onPlace = { elem ->
                val currentList = _uiState.value.project.elements.filter { it.id != elem.id }
                val safeElem = CollisionDetector.findNearestFreePosition(elem, currentList, _uiState.value.project.room)
                val updated = currentList + safeElem
                val bannerMsg = when {
                    wasRelocatedForOverlap -> "🛡️ منع تداخل الخزائن: تم وضع الخزانة بمحاذاة الوحدة المجاورة تلقائياً (0 مم)"
                    placementColCheck.hasCollision -> placementColCheck.message
                    else -> safetyCheck.safetyNoticeAr
                }
                _uiState.update {
                    it.copy(
                        project = it.project.copy(elements = updated),
                        selectedElementIds = setOf(safeElem.id),
                        activeSnapGuideLines = snapGuideLines,
                        collisionResult = placementColCheck.takeIf { c -> c.hasCollision },
                        safetyConstraintBanner = bannerMsg
                    )
                }
            },
            onRevertPlacement = { id, prevSelection ->
                val updated = _uiState.value.project.elements.filter { it.id != id }
                val validSelection = prevSelection.intersect(updated.map { it.id }.toSet())
                _uiState.update {
                    it.copy(
                        project = it.project.copy(elements = updated),
                        selectedElementIds = validSelection
                    )
                }
            }
        )
        commandHistory.execute(cmd)
    }

    fun setCameraMode(mode: CameraMode) {
        if (mode == CameraMode.FIRST_PERSON_WALK) {
            val safeSpawn = WalkthroughCollisionEngine.getSafeWalkSpawnPosition(
                room = _uiState.value.project.room,
                elements = _uiState.value.project.elements
            )
            _uiState.update {
                it.copy(
                    cameraMode = mode,
                    walkCameraPosition = safeSpawn,
                    walkLookYaw = it.cameraYaw,
                    walkLookPitch = 0f,
                    walkCollisionFeedback = null
                )
            }
        } else {
            _uiState.update { it.copy(cameraMode = mode) }
        }
    }

    fun updateCameraOrbit(deltaYaw: Float, deltaPitch: Float) {
        if (_uiState.value.isCameraTweening) return
        _uiState.update {
            val newYaw = (it.cameraYaw + deltaYaw) % 360f
            val maxPitch = if (it.cameraMode == CameraMode.TOP_DOWN) 89.8f else 88f
            val minPitch = if (it.cameraMode == CameraMode.TOP_DOWN) 75f else 5f
            val newPitch = (it.cameraPitch + deltaPitch).coerceIn(minPitch, maxPitch)
            it.copy(
                cameraYaw = (newYaw + 360f) % 360f,
                cameraPitch = newPitch
            )
        }
    }

    fun updateCameraPan(deltaX: Float, deltaY: Float) {
        if (_uiState.value.isCameraTweening) return
        _uiState.update {
            it.copy(
                cameraPanX = it.cameraPanX + deltaX,
                cameraPanY = it.cameraPanY + deltaY
            )
        }
    }

    fun resetCameraPan() {
        _uiState.update { it.copy(cameraPanX = 0f, cameraPanY = 0f) }
    }

    fun updateCameraZoom(factor: Float) {
        if (_uiState.value.isCameraTweening) return
        _uiState.update {
            val newZoom = (it.cameraZoom * factor).coerceIn(0.35f, 4.0f)
            it.copy(cameraZoom = newZoom)
        }
    }

    /**
     * Walkthrough movement: Forward/backward and strafe left/right.
     * Prevents penetration into walls and cabinets using sliding collision.
     */
    fun updateWalkMovement(forwardInput: Float, strafeInput: Float, deltaTimeSec: Float = 0.033f) {
        if (_uiState.value.cameraMode != CameraMode.FIRST_PERSON_WALK) return

        val state = _uiState.value
        val speedMultiplier = if (state.isWalkSprinting) 2.2f else 1.0f
        val distance = 1600f * speedMultiplier * deltaTimeSec

        val yawRad = Math.toRadians(state.walkLookYaw.toDouble()).toFloat()
        val fwd = Vector3(kotlin.math.sin(yawRad), 0f, kotlin.math.cos(yawRad)).normalized()
        val right = Vector3(kotlin.math.cos(yawRad), 0f, -kotlin.math.sin(yawRad)).normalized()

        val moveVec = (fwd * (forwardInput * distance)) + (right * (strafeInput * distance))
        val candidatePos = state.walkCameraPosition + moveVec

        val collisionResult = WalkthroughCollisionEngine.resolveMovement(
            currentPos = state.walkCameraPosition,
            candidatePos = candidatePos,
            room = state.project.room,
            elements = state.project.elements,
            eyeHeightMm = WalkthroughCollisionEngine.DEFAULT_EYE_HEIGHT_MM
        )

        _uiState.update {
            it.copy(
                walkCameraPosition = collisionResult.resolvedPosition,
                walkCollisionFeedback = if (collisionResult.hasCollided) {
                    "تم منع اختراق: ${collisionResult.collidedObstacleName ?: "جدار"}"
                } else null
            )
        }
    }

    /**
     * Look rotation during first-person walkthrough.
     */
    fun updateWalkLook(deltaYaw: Float, deltaPitch: Float) {
        if (_uiState.value.cameraMode != CameraMode.FIRST_PERSON_WALK) return
        _uiState.update {
            val newYaw = (it.walkLookYaw + deltaYaw) % 360f
            val newPitch = (it.walkLookPitch - deltaPitch).coerceIn(-75f, 75f)
            it.copy(
                walkLookYaw = (newYaw + 360f) % 360f,
                walkLookPitch = newPitch
            )
        }
    }

    fun toggleWalkSprint() {
        _uiState.update { it.copy(isWalkSprinting = !it.isWalkSprinting) }
    }

    /**
     * Cinematic camera tweening to preset views using Cubic Ease-In-Out and shortest-path angular lerp.
     */
    fun animateCameraToPreset(preset: CameraNavigationPreset, durationMs: Long = 750L) {
        cameraAnimationJob?.cancel()

        val room = _uiState.value.project.room
        val selected = _uiState.value.singleSelectedElement
        val currentPose = CameraPose(
            mode = _uiState.value.cameraMode,
            eyePosition = _uiState.value.walkCameraPosition,
            yawDegrees = if (_uiState.value.cameraMode == CameraMode.FIRST_PERSON_WALK) _uiState.value.walkLookYaw else _uiState.value.cameraYaw,
            pitchDegrees = if (_uiState.value.cameraMode == CameraMode.FIRST_PERSON_WALK) _uiState.value.walkLookPitch else _uiState.value.cameraPitch,
            zoomFactor = _uiState.value.cameraZoom,
            panOffset = Vector3(_uiState.value.cameraPanX, _uiState.value.cameraPanY, 0f)
        )

        val targetPose = CameraTweenEngine.computePresetPose(preset, room, selected, currentPose)

        cameraAnimationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCameraTweening = true,
                    activeCameraPreset = preset
                )
            }

            val steps = 30
            val stepDelay = durationMs / steps

            for (i in 1..steps) {
                val progress = i.toFloat() / steps
                val interpolated = CameraTweenEngine.interpolatePose(
                    start = currentPose,
                    target = targetPose,
                    progress = progress,
                    easing = CameraTweenEngine::easeInOutCubic
                )

                _uiState.update {
                    it.copy(
                        cameraMode = interpolated.mode,
                        cameraYaw = interpolated.yawDegrees,
                        cameraPitch = interpolated.pitchDegrees,
                        cameraZoom = interpolated.zoomFactor,
                        cameraPanX = interpolated.panOffset.x,
                        cameraPanY = interpolated.panOffset.y,
                        walkCameraPosition = interpolated.eyePosition,
                        walkLookYaw = interpolated.yawDegrees,
                        walkLookPitch = interpolated.pitchDegrees
                    )
                }
                delay(stepDelay)
            }

            _uiState.update {
                it.copy(
                    isCameraTweening = false,
                    cameraMode = targetPose.mode,
                    cameraYaw = targetPose.yawDegrees,
                    cameraPitch = targetPose.pitchDegrees,
                    cameraZoom = targetPose.zoomFactor,
                    cameraPanX = targetPose.panOffset.x,
                    cameraPanY = targetPose.panOffset.y,
                    walkCameraPosition = targetPose.eyePosition,
                    walkLookYaw = targetPose.yawDegrees,
                    walkLookPitch = targetPose.pitchDegrees
                )
            }
        }
    }

    fun applyCameraPreset(preset: CameraPreset) {
        val navPreset = when (preset) {
            CameraPreset.PERSPECTIVE -> CameraNavigationPreset.PERSPECTIVE
            CameraPreset.ISOMETRIC -> CameraNavigationPreset.ISOMETRIC
            CameraPreset.TOP_DOWN -> CameraNavigationPreset.TOP_VIEW
            CameraPreset.FRONT -> CameraNavigationPreset.FRONT_ELEVATION
        }
        animateCameraToPreset(navPreset)
    }

    fun openDialog(dialog: ActiveDialog?) {
        if (dialog == ActiveDialog.BillOfMaterialsDialog) {
            val bom = repository.generateBillOfMaterials(_uiState.value.project)
            _uiState.update { it.copy(activeDialog = dialog, billOfMaterials = bom) }
        } else {
            _uiState.update { it.copy(activeDialog = dialog) }
        }
    }

    fun loadTemplate(templateId: String) {
        viewModelScope.launch {
            repository.loadTemplate(templateId)
            commandHistory.clear()
            _uiState.update { it.copy(selectedElementIds = emptySet(), activeDialog = null) }
        }
    }

    fun applyCustomRoom(newRoom: RoomStructure) {
        val oldRoom = _uiState.value.project.room
        if (oldRoom == newRoom) return
        val cmd = ModifyRoomLayoutCommand(
            oldRoom = oldRoom,
            newRoom = newRoom,
            title = "تطبيق مخطط غرفة مخصص",
            description = "${newRoom.widthMm.toInt()}×${newRoom.depthMm.toInt()} مم",
            onApplyRoom = { room ->
                val updatedProject = _uiState.value.project.copy(room = room)
                _uiState.update { it.copy(project = updatedProject) }
            }
        )
        commandHistory.execute(cmd)
    }

    fun addCustomImportedElement(element: KitchenElement) {
        val safeElement = CollisionDetector.findNearestFreePosition(
            candidate = element,
            existingElements = _uiState.value.project.elements,
            room = _uiState.value.project.room
        )
        val cmd = AddElementCommand(
            element = safeElement,
            onAdd = { elem ->
                val resolved = CollisionDetector.findNearestFreePosition(
                    candidate = elem,
                    existingElements = _uiState.value.project.elements,
                    room = _uiState.value.project.room
                )
                val updated = _uiState.value.project.elements + resolved
                _uiState.update {
                    it.copy(
                        project = it.project.copy(elements = updated),
                        selectedElementIds = setOf(resolved.id)
                    )
                }
            },
            onRemove = { id ->
                val updated = _uiState.value.project.elements.filter { it.id != id }
                _uiState.update {
                    it.copy(
                        project = it.project.copy(elements = updated),
                        selectedElementIds = it.selectedElementIds - id
                    )
                }
            }
        )
        commandHistory.execute(cmd)
    }

    fun getParametricCabinetsFromScene(): List<ParametricCabinet> {
        val preset = _uiState.value.activeMaterialPreset
        val candidates = _uiState.value.project.elements.filter {
            it.type.category == ElementCategory.BASE_CABINET ||
                    it.type.category == ElementCategory.TALL_UNIT ||
                    it.type.category == ElementCategory.WALL_CABINET
        }
        if (candidates.isEmpty()) {
            return listOf(
                ParametricCabinet(
                    id = "cab_def_1",
                    name = "خزانة قياسية 60 سم",
                    widthMm = 600f,
                    heightMm = 850f,
                    depthMm = 600f,
                    panelThicknessMm = preset.melamineThicknessMm,
                    backPanelThicknessMm = preset.backPanelThicknessMm,
                    hasBackPanel = preset.hasBackPanel,
                    backPanelMaterialName = preset.backPanelMaterialName,
                    backGrooveInsetMm = preset.backGrooveInsetMm,
                    topStretcherWidthMm = preset.topStretcherWidthMm,
                    aluminumProfileSectionSizeMm = preset.aluminumProfileSectionSizeMm,
                    aluminumProfileThicknessMm = preset.aluminumProfileThicknessMm,
                    manualWidthDeductionMm = preset.customWidthDeductionMm,
                    aluminumWastePercentage = preset.aluminumWastePercentage
                )
            )
        }
        return candidates.map { elem ->
            val isDrawerUnit = elem.name.contains("أدراج") || elem.type == KitchenElementType.BASE_3_DRAWERS
            ParametricCabinet(
                id = elem.id,
                name = elem.name,
                widthMm = elem.dimensions.x,
                heightMm = elem.dimensions.y,
                depthMm = elem.dimensions.z,
                panelThicknessMm = elem.customBoardThicknessMm,
                backPanelThicknessMm = preset.backPanelThicknessMm,
                numberOfShelves = if (isDrawerUnit) 0 else 1,
                numberOfDrawers = if (isDrawerUnit) 3 else 0,
                doorStyle = DoorStyle.SLAB_MODERN,
                handleStyle = HandleStyle.BAR_PULL,
                hasBackPanel = elem.hasBackPanel,
                backPanelMaterialName = elem.backPanelMaterialType,
                backGrooveInsetMm = elem.backGrooveInsetMm,
                topStretcherWidthMm = elem.topStretcherWidthMm,
                aluminumProfileSectionSizeMm = elem.customAluminumProfileMm,
                aluminumProfileThicknessMm = preset.aluminumProfileThicknessMm,
                manualWidthDeductionMm = preset.customWidthDeductionMm,
                aluminumWastePercentage = preset.aluminumWastePercentage
            )
        }
    }

    fun applyParametricCabinet(cabinet: ParametricCabinet) {
        val elem = _uiState.value.project.elements.find { it.id == cabinet.id }
        if (elem != null) {
            val oldPos = elem.position
            val oldRot = elem.rotationY
            val oldDims = elem.dimensions
            val newDims = Vector3(cabinet.widthMm, cabinet.heightMm, cabinet.depthMm)

            // Update backing & material properties immediately (including 3D hollowing when hasBackPanel == false)
            val updatedElements = _uiState.value.project.elements.map {
                if (it.id == elem.id) {
                    it.copy(
                        hasBackPanel = cabinet.hasBackPanel,
                        backPanelMaterialType = cabinet.backPanelMaterialName,
                        backGrooveInsetMm = cabinet.backGrooveInsetMm,
                        topStretcherWidthMm = cabinet.topStretcherWidthMm,
                        customBoardThicknessMm = cabinet.panelThicknessMm,
                        customAluminumProfileMm = cabinet.aluminumProfileSectionSizeMm
                    )
                } else it
            }
            _uiState.update { it.copy(project = it.project.copy(elements = updatedElements)) }

            applyElementTransform(elem.id, oldPos, oldRot, newDims)
            commitTransformCommand(elem.id, oldPos, oldRot, oldDims)
        } else {
            val newElem = KitchenElement(
                id = "param_${UUID.randomUUID().toString().take(8)}",
                name = cabinet.name,
                type = if (cabinet.numberOfDrawers > 0) KitchenElementType.BASE_3_DRAWERS else KitchenElementType.BASE_SINGLE_DOOR,
                position = Vector3(2000f, 0f, 1500f),
                dimensions = Vector3(cabinet.widthMm, cabinet.heightMm, cabinet.depthMm),
                hasBackPanel = cabinet.hasBackPanel,
                backPanelMaterialType = cabinet.backPanelMaterialName,
                backGrooveInsetMm = cabinet.backGrooveInsetMm,
                topStretcherWidthMm = cabinet.topStretcherWidthMm,
                customBoardThicknessMm = cabinet.panelThicknessMm,
                customAluminumProfileMm = cabinet.aluminumProfileSectionSizeMm
            )
            addCustomImportedElement(newElem)
        }
    }

    // Section 1: Collapsible Sidebar & Floating Panels Toggles (لعدم تغطية التصميم)
    fun toggleCollapsibleSidebar() {
        _uiState.update { it.copy(isCollapsibleSidebarOpen = !it.isCollapsibleSidebarOpen) }
    }

    fun toggleControlPanelsCollapsed() {
        _uiState.update { it.copy(isControlPanelsCollapsed = !it.isControlPanelsCollapsed) }
    }

    fun toggleCleanCanvasMode() {
        _uiState.update {
            val nextClean = !it.isCleanCanvasMode
            it.copy(
                isCleanCanvasMode = nextClean,
                isCollapsibleSidebarOpen = if (nextClean) false else it.isCollapsibleSidebarOpen,
                isControlPanelsCollapsed = if (nextClean) true else it.isControlPanelsCollapsed
            )
        }
    }

    fun toggleTransformBarExpanded() {
        _uiState.update { it.copy(isTransformBarExpanded = !it.isTransformBarExpanded) }
    }

    fun addCustomCameraMaterial(finish: MaterialFinish) {
        _uiState.update {
            it.copy(customCameraMaterials = listOf(finish) + it.customCameraMaterials)
        }
    }

    fun cycleSelectedElementMaterial() {
        val allFinishes = _uiState.value.customCameraMaterials + MaterialFinish.ALL_FINISHES
        val currentMat = _uiState.value.singleSelectedElement?.material ?: return
        val nextIdx = (allFinishes.indexOfFirst { it.id == currentMat.id } + 1) % allFinishes.size
        changeSelectedMaterial(allFinishes[nextIdx])
    }

    /**
     * Applies both the 3D RoomStructure and any sketched 3D cabinets generated by the Sketch-to-3D engine.
     */
    fun applySketchScene(newRoom: RoomStructure, sketchedElements: List<KitchenElement>) {
        val currentProject = _uiState.value.project
        val rawUpdatedElements = if (sketchedElements.isNotEmpty()) {
            val nonSketched = currentProject.elements.filterNot { it.id.startsWith("sketch_") }
            nonSketched + sketchedElements
        } else {
            currentProject.elements
        }
        val updatedElements = CollisionDetector.resolveAllSceneOverlaps(rawUpdatedElements, newRoom)
        val updatedProject = currentProject.copy(
            room = newRoom,
            elements = updatedElements
        )
        val updatedReport = ErgonomicValidationEngine.validateProject(updatedProject)
        _uiState.update {
            it.copy(
                project = updatedProject,
                validationReport = updatedReport,
                selectedElementIds = sketchedElements.lastOrNull()?.let { el -> setOf(el.id) } ?: it.selectedElementIds
            )
        }
    }

    fun clearSafetyConstraintBanner() {
        _uiState.update { it.copy(safetyConstraintBanner = null) }
    }

    // Section 4: Interactive Cabinet Backing System (نعم يوجد ظهر / لا يوجد ظهر + تفريغ الـ 3D فوراً)
    fun toggleSelectedCabinetBackPanel(
        hasBackPanel: Boolean,
        backPanelMaterialType: String? = null,
        backGrooveInsetMm: Float? = null
    ) {
        val selectedIds = _uiState.value.selectedElementIds
        val updatedElements = _uiState.value.project.elements.map { elem ->
            if (selectedIds.isEmpty() || elem.id in selectedIds) {
                elem.copy(
                    hasBackPanel = hasBackPanel,
                    backPanelMaterialType = backPanelMaterialType ?: elem.backPanelMaterialType,
                    backGrooveInsetMm = if (hasBackPanel) (backGrooveInsetMm ?: elem.backGrooveInsetMm) else 0f
                )
            } else elem
        }
        val updatedPreset = _uiState.value.activeMaterialPreset.copy(
            hasBackPanel = hasBackPanel,
            backPanelMaterialName = backPanelMaterialType ?: _uiState.value.activeMaterialPreset.backPanelMaterialName,
            backGrooveInsetMm = if (hasBackPanel) (backGrooveInsetMm ?: _uiState.value.activeMaterialPreset.backGrooveInsetMm) else 0f
        )
        _uiState.update {
            it.copy(
                project = it.project.copy(elements = updatedElements),
                activeMaterialPreset = updatedPreset
            )
        }
    }

    // Section 4: Manual Override UI & Custom Presets
    fun applyManualMaterialOverride(preset: CustomMaterialOverridePreset, applyToAllCabinets: Boolean = true) {
        val updatedElements = if (applyToAllCabinets) {
            _uiState.value.project.elements.map { elem ->
                if (elem.type.category == ElementCategory.BASE_CABINET ||
                    elem.type.category == ElementCategory.WALL_CABINET ||
                    elem.type.category == ElementCategory.TALL_UNIT
                ) {
                    elem.copy(
                        hasBackPanel = preset.hasBackPanel,
                        backPanelMaterialType = preset.backPanelMaterialType,
                        backGrooveInsetMm = preset.backGrooveInsetMm,
                        topStretcherWidthMm = preset.topStretcherWidthMm,
                        customBoardThicknessMm = preset.melamineThicknessMm,
                        customAluminumProfileMm = preset.aluminumProfileWidthMm
                    )
                } else elem
            }
        } else {
            _uiState.value.project.elements
        }

        _uiState.update {
            it.copy(
                project = it.project.copy(elements = updatedElements),
                activeMaterialPreset = preset
            )
        }
    }

    fun saveCustomMaterialPreset(preset: CustomMaterialOverridePreset) {
        _uiState.update { state ->
            val existingIdx = state.savedMaterialPresets.indexOfFirst { it.id == preset.id || it.name == preset.name }
            val updatedList = if (existingIdx >= 0) {
                state.savedMaterialPresets.toMutableList().apply { set(existingIdx, preset) }
            } else {
                state.savedMaterialPresets + preset
            }
            state.copy(
                savedMaterialPresets = updatedList,
                activeMaterialPreset = preset
            )
        }
        applyManualMaterialOverride(preset, applyToAllCabinets = true)
    }

    // Section 6: Workshop Branding (Logo & Name for 2D Blueprint Export)
    fun updateWorkshopBranding(name: String, logoBitmap: android.graphics.Bitmap? = _uiState.value.workshopLogoBitmap) {
        _uiState.update {
            it.copy(
                workshopName = name.ifBlank { "ورشة التصميم الهندسي للمطابخ الذكية" },
                workshopLogoBitmap = logoBitmap
            )
        }
    }

    // Section 7: 1D Aluminum Bar Stock Nesting Optimizer State & Calculation
    fun updateStandardAluminumBarLength(lengthMm: Float) {
        val clamped = lengthMm.coerceIn(500f, 12000f)
        _uiState.update { state ->
            val updatedList = if (state.availableAluminumBarLengthsMm.none { kotlin.math.abs(it - clamped) < 1f }) {
                (state.availableAluminumBarLengthsMm + clamped).sortedDescending()
            } else {
                state.availableAluminumBarLengthsMm
            }
            state.copy(
                standardAluminumBarLengthMm = clamped,
                availableAluminumBarLengthsMm = updatedList
            )
        }
    }

    fun updateCustomAluminumCutInputs(cuts: List<CabinetryAluminumCutInput>) {
        _uiState.update { it.copy(customAluminumCutInputs = cuts) }
    }

    fun calculateAluminumNestingCutList(
        config: AluminumNestingConfig = AluminumNestingConfig(
            primaryBarLengthMm = _uiState.value.standardAluminumBarLengthMm,
            availableStandardBarLengthsMm = _uiState.value.availableAluminumBarLengthsMm
        ),
        includeSceneCabinets: Boolean = true
    ): AluminumNestingResult {
        val sceneCuts = if (includeSceneCabinets) {
            AluminumBarNestingOptimizer.extractCabinetryCutInputs(getParametricCabinetsFromScene())
        } else {
            emptyList()
        }
        val allCuts = sceneCuts + _uiState.value.customAluminumCutInputs
        return AluminumBarNestingOptimizer.optimizeCutList(
            cutInputs = allCuts,
            config = config
        )
    }

    fun undo() {
        commandHistory.undo()
    }

    fun redo() {
        commandHistory.redo()
    }

    fun undoSteps(steps: Int) {
        commandHistory.undoSteps(steps)
    }

    fun redoSteps(steps: Int) {
        commandHistory.redoSteps(steps)
    }

    fun getBillOfMaterials(): BillOfMaterials {
        return repository.generateBillOfMaterials(_uiState.value.project)
    }

    /**
     * Serializes the current 2D floor plan state and CNC cabinet cut lists (including active room topology,
     * openings, visible cabinets/appliances, MEP utility points, ergonomic work triangle, 2D nested melamine sheets,
     * router back grooves, 35mm hinge holes, and 1D aluminum profile bars) into a standard CAD/CNC ASCII DXF string.
     */
    fun serializeFloorPlanToDxf(
        options: com.example.core.export.DxfExporter.DxfExportOptions = com.example.core.export.DxfExporter.DxfExportOptions()
    ): String {
        val currentState = _uiState.value
        return com.example.core.export.DxfExporter.serializeFloorPlanWithMetadata(
            project = currentState.project,
            options = options,
            hiddenLayers = currentState.hiddenLayers,
            validationReport = currentState.validationReport,
            workshopName = currentState.workshopName,
            explicitCabinets = getParametricCabinetsFromScene(),
            materialPreset = currentState.activeMaterialPreset
        ).dxfContent
    }

    /**
     * Dedicated function to export BOTH the 2D room floorplan AND the cabinet CNC cut lists into DXF format
     * for professional CNC manufacturing software (AutoCAD, AlphaCAM, VCarve, Aspire, WoodWOP).
     */
    fun exportFloorPlanAndCutListsToDxf(
        options: com.example.core.export.DxfExporter.DxfExportOptions = com.example.core.export.DxfExporter.DxfExportOptions(
            includeCabinetCutList = true,
            includeCncNestedSheets = true,
            includeAluminumBarCutList = true,
            includeRouterGroovesAndDrilling = true,
            standardAluminumBarLengthMm = _uiState.value.standardAluminumBarLengthMm
        )
    ): String {
        val currentState = _uiState.value
        return com.example.core.export.DxfExporter.exportFloorPlanAndCutListsToDxf(
            project = currentState.project,
            cabinets = getParametricCabinetsFromScene(),
            materialPreset = currentState.activeMaterialPreset,
            options = options,
            hiddenLayers = currentState.hiddenLayers,
            validationReport = currentState.validationReport,
            workshopName = currentState.workshopName
        )
    }

    /**
     * Serializes the current 2D floor plan state and CNC cabinet cut lists into DXF and returns full CAD metadata
     * (entity counts per layer, bounding extents, and exported element/CNC totals).
     */
    fun serializeFloorPlanWithMetadata(
        options: com.example.core.export.DxfExporter.DxfExportOptions = com.example.core.export.DxfExporter.DxfExportOptions()
    ): com.example.core.export.DxfExporter.DxfSerializationResult {
        val currentState = _uiState.value
        return com.example.core.export.DxfExporter.serializeFloorPlanWithMetadata(
            project = currentState.project,
            options = options,
            hiddenLayers = currentState.hiddenLayers,
            validationReport = currentState.validationReport,
            workshopName = currentState.workshopName,
            explicitCabinets = getParametricCabinetsFromScene(),
            materialPreset = currentState.activeMaterialPreset
        )
    }

    /**
     * Exports the current 2D floor plan and CNC cabinet cut lists to a `.dxf` file on disk and returns the serialization metadata.
     */
    fun exportFloorPlanToDxfFile(
        outputFile: java.io.File,
        options: com.example.core.export.DxfExporter.DxfExportOptions = com.example.core.export.DxfExporter.DxfExportOptions()
    ): com.example.core.export.DxfExporter.DxfSerializationResult {
        val currentState = _uiState.value
        return com.example.core.export.DxfExporter.exportToFile(
            file = outputFile,
            project = currentState.project,
            options = options,
            hiddenLayers = currentState.hiddenLayers,
            validationReport = currentState.validationReport,
            workshopName = currentState.workshopName,
            explicitCabinets = getParametricCabinetsFromScene(),
            materialPreset = currentState.activeMaterialPreset
        )
    }

    /**
     * Serializes the current project, scene elements, catalog definitions, and CAM cut-lists/nesting into JSON format.
     */
    fun serializeProjectAndElementsToJson(
        options: com.example.core.export.JsonProjectExporter.JsonExportOptions = com.example.core.export.JsonProjectExporter.JsonExportOptions(
            standardAluminumBarLengthMm = _uiState.value.standardAluminumBarLengthMm
        )
    ): com.example.core.export.JsonProjectExporter.JsonSerializationResult {
        val currentState = _uiState.value
        val bom = repository.generateBillOfMaterials(currentState.project)
        return com.example.core.export.JsonProjectExporter.serializeToJsonWithMetadata(
            project = currentState.project,
            options = options,
            cabinets = getParametricCabinetsFromScene(),
            bom = bom,
            materialPreset = currentState.activeMaterialPreset,
            workshopName = currentState.workshopName
        )
    }

    /**
     * Exports the current project, scene elements, and CAM cut-lists/nesting to a `.json` file on disk.
     */
    fun exportProjectAndElementsToJsonFile(
        outputFile: java.io.File,
        options: com.example.core.export.JsonProjectExporter.JsonExportOptions = com.example.core.export.JsonProjectExporter.JsonExportOptions(
            standardAluminumBarLengthMm = _uiState.value.standardAluminumBarLengthMm
        )
    ): com.example.core.export.JsonProjectExporter.JsonSerializationResult {
        val currentState = _uiState.value
        val bom = repository.generateBillOfMaterials(currentState.project)
        return com.example.core.export.JsonProjectExporter.exportToFile(
            file = outputFile,
            project = currentState.project,
            options = options,
            cabinets = getParametricCabinetsFromScene(),
            bom = bom,
            materialPreset = currentState.activeMaterialPreset,
            workshopName = currentState.workshopName
        )
    }

    /**
     * Parses a JSON string (either full project JSON or elements list JSON) and applies it to the current scene
     * while resolving any cabinet overlap collisions.
     */
    fun importProjectOrElementsFromJson(jsonString: String): KitchenProject {
        val parsed = com.example.core.export.JsonProjectExporter.importProjectOrElementsFromJson(
            jsonString = jsonString,
            fallbackProject = _uiState.value.project
        )
        val nonOverlappingElements = CollisionDetector.resolveAllSceneOverlaps(parsed.elements, parsed.room)
        val safeProject = parsed.copy(elements = nonOverlappingElements)
        applyImportedProject(safeProject)
        return safeProject
    }

    fun applyImportedProject(importedProject: KitchenProject) {
        viewModelScope.launch {
            val nonOverlappingElements = CollisionDetector.resolveAllSceneOverlaps(importedProject.elements, importedProject.room)
            val safeProject = importedProject.copy(elements = nonOverlappingElements)
            repository.updateProject(safeProject)
            commandHistory.clear()
            val report = ErgonomicValidationEngine.validateProject(safeProject)
            _uiState.update {
                it.copy(
                    project = safeProject,
                    selectedElementIds = emptySet(),
                    validationReport = report
                )
            }
        }
    }

    fun toggleErgonomicOverlay(enabled: Boolean = !_uiState.value.showErgonomicOverlay) {
        _uiState.update { it.copy(showErgonomicOverlay = enabled) }
    }

    fun focusErgonomicIssue(issue: ErgonomicIssue) {
        val targetIds = issue.affectedElementIds.toSet()
        _uiState.update {
            it.copy(
                selectedElementIds = targetIds,
                isMultiSelectMode = targetIds.size > 1,
                activeDialog = null
            )
        }
    }

    /**
     * Automatically resolves an [ErgonomicIssue] by repositioning the affected cabinet/appliance
     * to satisfy NKBA ergonomic standards (e.g., Sink-to-Stove distance or Door Swing Clearance).
     */
    fun autoFixErgonomicIssue(issue: ErgonomicIssue) {
        val currentProject = _uiState.value.project
        val room = currentProject.room
        val elements = currentProject.elements.toMutableList()

        when (issue.code) {
            ErgonomicRuleCode.SINK_TOO_FAR_FROM_STOVE,
            ErgonomicRuleCode.SINK_TOO_CLOSE_TO_STOVE -> {
                if (issue.affectedElementIds.size >= 2) {
                    val sinkId = issue.affectedElementIds[0]
                    val stoveId = issue.affectedElementIds[1]
                    val sink = elements.find { it.id == sinkId }
                    val stoveIdx = elements.indexOfFirst { it.id == stoveId }
                    if (sink != null && stoveIdx >= 0) {
                        val stove = elements[stoveIdx]
                        val dir = (stove.position - sink.position)
                        val horizLen = kotlin.math.hypot(dir.x, dir.z).coerceAtLeast(1f)
                        val targetDistMm = 1650f // Optimal 165cm center-to-center
                        val ux = if (horizLen > 10f) dir.x / horizLen else 1f
                        val uz = if (horizLen > 10f) dir.z / horizLen else 0f
                        val halfW = stove.dimensions.x * 0.5f
                        val halfD = stove.dimensions.z * 0.5f
                        val newX = (sink.position.x + ux * targetDistMm)
                            .coerceIn(halfW, room.widthMm - halfW)
                        val newZ = (sink.position.z + uz * targetDistMm)
                            .coerceIn(halfD, room.depthMm - halfD)
                        elements[stoveIdx] = stove.copy(
                            position = Vector3(newX, stove.position.y, newZ)
                        )
                    }
                }
            }

            ErgonomicRuleCode.INSUFFICIENT_DOOR_CLEARANCE,
            ErgonomicRuleCode.CORNER_DOOR_INTERFERENCE -> {
                val elemId = issue.affectedElementIds.firstOrNull()
                val idx = elements.indexOfFirst { it.id == elemId }
                if (idx >= 0) {
                    val elem = elements[idx]
                    val obb = elem.getOBB()
                    val requiredMm = ErgonomicValidationEngine.MIN_ERGONOMIC_WALKWAY_MM
                    val deficitMm = (requiredMm - issue.measuredValueMm).coerceAtLeast(250f)
                    // Shift element backward opposite its front normal if room allows, or toward room center
                    val retreat = elem.position - (obb.axisZ * deficitMm)
                    val halfW = obb.toAABB().extents.x * 0.5f
                    val halfD = obb.toAABB().extents.z * 0.5f
                    val clampedX = retreat.x.coerceIn(halfW, room.widthMm - halfW)
                    val clampedZ = retreat.z.coerceIn(halfD, room.depthMm - halfD)
                    elements[idx] = elem.copy(
                        position = Vector3(clampedX, elem.position.y, clampedZ)
                    )
                }
            }

            ErgonomicRuleCode.DISHWASHER_FAR_FROM_SINK -> {
                if (issue.affectedElementIds.size >= 2) {
                    val dwId = issue.affectedElementIds[0]
                    val sinkId = issue.affectedElementIds[1]
                    val dwIdx = elements.indexOfFirst { it.id == dwId }
                    val sink = elements.find { it.id == sinkId }
                    if (dwIdx >= 0 && sink != null) {
                        val dw = elements[dwIdx]
                        val offset = (sink.dimensions.x + dw.dimensions.x) * 0.5f + 20f
                        val halfW = dw.dimensions.x * 0.5f
                        val newX = (sink.position.x + offset).coerceIn(halfW, room.widthMm - halfW)
                        elements[dwIdx] = dw.copy(
                            position = Vector3(newX, dw.position.y, sink.position.z),
                            rotationY = sink.rotationY
                        )
                    }
                }
            }

            ErgonomicRuleCode.HOOD_CLEARANCE_HEIGHT -> {
                val hoodId = issue.affectedElementIds.firstOrNull()
                val idx = elements.indexOfFirst { it.id == hoodId }
                if (idx >= 0) {
                    val hood = elements[idx]
                    elements[idx] = hood.copy(
                        position = Vector3(hood.position.x, 1600f, hood.position.z)
                    )
                }
            }

            ErgonomicRuleCode.ELEMENT_OVERLAP_COLLISION,
            ErgonomicRuleCode.WORK_TRIANGLE_PERIMETER,
            ErgonomicRuleCode.WORK_TRIANGLE_OBSTRUCTED -> {
                val resolvedAll = CollisionDetector.resolveAllSceneOverlaps(elements, room)
                elements.clear()
                elements.addAll(resolvedAll)
            }
        }

        // Guarantee no auto-fix action leaves any cabinet overlapping
        val nonOverlappingAfter = CollisionDetector.resolveAllSceneOverlaps(elements, room)
        val beforeElements = currentProject.elements
        val afterElements = nonOverlappingAfter.toList()
        if (beforeElements == afterElements) return

        val beforeSelection = _uiState.value.selectedElementIds
        val afterSelection = issue.affectedElementIds.toSet()

        val cmd = BatchFloorPlanLayoutCommand(
            beforeElements = beforeElements,
            afterElements = afterElements,
            beforeSelection = beforeSelection,
            afterSelection = afterSelection,
            title = "تصحيح هندسي تلقائي (${issue.code.name})",
            description = issue.titleAr,
            category = CommandCategory.LAYOUT_MODIFICATION,
            onApplyElements = { elems, sel ->
                val updatedProject = _uiState.value.project.copy(elements = elems)
                val updatedReport = ErgonomicValidationEngine.validateProject(updatedProject)
                _uiState.update {
                    it.copy(
                        project = updatedProject,
                        validationReport = updatedReport,
                        selectedElementIds = sel
                    )
                }
            }
        )
        commandHistory.execute(cmd)
    }

    // ==========================================
    // AUTO-SAVE CONFIGURATION & RECOVERY
    // ==========================================

    fun setAutoSaveEnabled(enabled: Boolean) {
        autoSaveManager.setAutoSaveEnabled(enabled)
    }

    fun setAutoSaveIntervalSeconds(seconds: Int) {
        autoSaveManager.setAutoSaveIntervalSeconds(seconds)
    }

    fun triggerImmediateAutoSave() {
        viewModelScope.launch {
            autoSaveManager.saveNow(
                project = _uiState.value.project,
                cameraState = currentSavedCameraState(),
                trigger = AutoSaveTrigger.MANUAL,
                createCheckpoint = true
            )
        }
    }

    fun restoreFromAutoSaveSnapshot(snapshot: AutoSaveSnapshotEntity) {
        viewModelScope.launch {
            val (restoredProject, restoredCam) = ProjectAutoSaveManager.deserializeSnapshot(snapshot)
            repository.updateProject(restoredProject)
            commandHistory.clear()
            val report = ErgonomicValidationEngine.validateProject(restoredProject)

            _uiState.update { current ->
                val mode = restoredCam?.let {
                    try { CameraMode.valueOf(it.mode) } catch (e: Exception) { current.cameraMode }
                } ?: current.cameraMode

                current.copy(
                    project = restoredProject,
                    validationReport = report,
                    selectedElementIds = emptySet(),
                    cameraMode = mode,
                    cameraYaw = restoredCam?.yaw ?: current.cameraYaw,
                    cameraPitch = restoredCam?.pitch ?: current.cameraPitch,
                    cameraZoom = restoredCam?.zoom ?: current.cameraZoom,
                    cameraPanX = restoredCam?.panX ?: current.cameraPanX,
                    cameraPanY = restoredCam?.panY ?: current.cameraPanY,
                    walkCameraPosition = restoredCam?.let {
                        Vector3(it.walkPosX, it.walkPosY, it.walkPosZ)
                    } ?: current.walkCameraPosition,
                    walkLookYaw = restoredCam?.walkLookYaw ?: current.walkLookYaw,
                    walkLookPitch = restoredCam?.walkLookPitch ?: current.walkLookPitch,
                    activeDialog = null
                )
            }
        }
    }

    // ==========================================
    // OFFLINE ROOM PROJECT STORAGE & JSON BACKUP
    // ==========================================

    fun openProjectsManager() {
        _uiState.update { it.copy(activeDialog = ActiveDialog.ProjectsManager) }
    }

    fun saveCurrentProjectAs(name: String) {
        viewModelScope.launch {
            val saved = repository.saveProjectAs(name, _uiState.value.project)
            commandHistory.clear()
            _uiState.update { it.copy(project = saved) }
        }
    }

    fun loadSavedProject(projectId: String) {
        viewModelScope.launch {
            repository.loadProjectById(projectId)
            commandHistory.clear()
            _uiState.update { it.copy(selectedElementIds = emptySet(), activeDialog = null) }
        }
    }

    fun createNewBlankProject(name: String = "تصميم مطبخ جديد") {
        viewModelScope.launch {
            val created = repository.createNewProject(name, _uiState.value.project.room)
            commandHistory.clear()
            _uiState.update { it.copy(project = created, selectedElementIds = emptySet(), activeDialog = null) }
        }
    }

    fun deleteSavedProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProjectById(projectId)
        }
    }

    suspend fun getProjectJsonString(projectId: String? = null): String {
        val targetProject = if (projectId == null || projectId == _uiState.value.project.id) {
            _uiState.value.project
        } else {
            repository.getProjectById(projectId) ?: _uiState.value.project
        }
        val bom = repository.generateBillOfMaterials(targetProject)
        return com.example.core.export.JsonProjectExporter.serializeToJsonWithMetadata(
            project = targetProject,
            options = com.example.core.export.JsonProjectExporter.JsonExportOptions(
                exportMode = com.example.core.export.JsonProjectExporter.JsonExportMode.FULL_PROJECT_AND_CAM,
                standardAluminumBarLengthMm = _uiState.value.standardAluminumBarLengthMm
            ),
            cabinets = getParametricCabinetsFromScene(),
            bom = bom,
            materialPreset = _uiState.value.activeMaterialPreset,
            workshopName = _uiState.value.workshopName
        ).jsonContent
    }

    fun exportAndShareProjectJson(context: android.content.Context, projectId: String? = null) {
        viewModelScope.launch {
            try {
                val targetProject = if (projectId == null || projectId == _uiState.value.project.id) {
                    _uiState.value.project
                } else {
                    repository.getProjectById(projectId) ?: _uiState.value.project
                }
                val cleanName = targetProject.name.replace(Regex("[^a-zA-Z0-9_\\-\\u0600-\\u06FF]"), "_")
                val fileName = "KitchenProject_${cleanName}_${System.currentTimeMillis()}.json"
                val exportDir = java.io.File(context.cacheDir, "project_exports").apply { mkdirs() }
                val file = java.io.File(exportDir, fileName)

                val bom = repository.generateBillOfMaterials(targetProject)
                com.example.core.export.JsonProjectExporter.exportToFile(
                    file = file,
                    project = targetProject,
                    options = com.example.core.export.JsonProjectExporter.JsonExportOptions(
                        exportMode = com.example.core.export.JsonProjectExporter.JsonExportMode.FULL_PROJECT_AND_CAM,
                        standardAluminumBarLengthMm = _uiState.value.standardAluminumBarLengthMm
                    ),
                    cabinets = getParametricCabinetsFromScene(),
                    bom = bom,
                    materialPreset = _uiState.value.activeMaterialPreset,
                    workshopName = _uiState.value.workshopName
                )

                if (file.exists()) {
                    com.example.core.export.ShareExportHelper.shareFile(
                        context = context,
                        file = file,
                        mimeType = com.example.core.export.ShareExportHelper.MIME_JSON,
                        dialogTitle = "مشاركة ملف المشروع بصيغة JSON"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ==========================================
    // 10. AUTOMATIC CABINET FILLER & SPACER TOOL (1MM PRECISION)
    // ==========================================

    fun applyAutomaticWallFillers(config: AutomaticFillerEngine.FillerRequestConfig): AutomaticFillerEngine.FillerLayoutResult {
        val currentState = _uiState.value
        val beforeElements = currentState.project.elements
        val beforeSelection = currentState.selectedElementIds

        // Optionally update the room wall dimension first if the user requested syncing room length
        if (config.alsoUpdateRoomWallLength) {
            val room = currentState.project.room
            val newWidth = if (config.wallSide == WallSide.NORTH || config.wallSide == WallSide.SOUTH) {
                config.userWallLengthMm.coerceIn(1000f, 15000f)
            } else room.widthMm
            val newDepth = if (config.wallSide == WallSide.WEST || config.wallSide == WallSide.EAST) {
                config.userWallLengthMm.coerceIn(1000f, 15000f)
            } else room.depthMm
            if (newWidth != room.widthMm || newDepth != room.depthMm) {
                updateRoomDimensions(newWidth, newDepth, room.heightMm)
            }
        }

        val activeRoom = _uiState.value.project.room
        val result = AutomaticFillerEngine.computeFillerLayout(
            elements = beforeElements,
            room = activeRoom,
            config = config
        )

        val afterElements = result.updatedElements
        val afterSelection = if (result.addedFillers.isNotEmpty()) {
            result.addedFillers.map { it.id }.toSet()
        } else {
            beforeSelection
        }

        if (beforeElements != afterElements) {
            val cmd = BatchFloorPlanLayoutCommand(
                beforeElements = beforeElements,
                afterElements = afterElements,
                beforeSelection = beforeSelection,
                afterSelection = afterSelection,
                title = "فيلر تلقائي 1 مم (${config.wallSide.title})",
                description = result.summaryAr,
                category = CommandCategory.LAYOUT_MODIFICATION,
                onApplyElements = { elems, sel ->
                    _uiState.update {
                        it.copy(
                            project = it.project.copy(elements = elems),
                            selectedElementIds = sel,
                            safetyConstraintBanner = result.summaryAr
                        )
                    }
                }
            )
            commandHistory.execute(cmd)
        } else {
            _uiState.update { it.copy(safetyConstraintBanner = result.summaryAr) }
        }

        return result
    }

    fun removeWallFillers(wallSide: WallSide) {
        val currentState = _uiState.value
        val room = currentState.project.room
        val beforeElements = currentState.project.elements
        val afterElements = beforeElements.filterNot { elem ->
            AutomaticFillerEngine.isFillerElement(elem) &&
                AutomaticFillerEngine.isElementOnWall(elem, wallSide, room)
        }
        val removedCount = beforeElements.size - afterElements.size
        if (removedCount <= 0) {
            _uiState.update {
                it.copy(safetyConstraintBanner = "لا توجد شرائح فيلر على ${wallSide.title} لإزالتها")
            }
            return
        }

        val beforeSelection = currentState.selectedElementIds
        val validSelection = beforeSelection.intersect(afterElements.map { it.id }.toSet())

        val cmd = BatchFloorPlanLayoutCommand(
            beforeElements = beforeElements,
            afterElements = afterElements,
            beforeSelection = beforeSelection,
            afterSelection = validSelection,
            title = "إزالة شرائح الفيلر (${wallSide.title})",
            description = "تمت إزالة $removedCount شريحة فيلر من ${wallSide.title}",
            category = CommandCategory.ELEMENT_DELETION,
            onApplyElements = { elems, sel ->
                _uiState.update {
                    it.copy(
                        project = it.project.copy(elements = elems),
                        selectedElementIds = sel,
                        safetyConstraintBanner = "تمت إزالة $removedCount شريحة فيلر من ${wallSide.title}"
                    )
                }
            }
        )
        commandHistory.execute(cmd)
    }
}
