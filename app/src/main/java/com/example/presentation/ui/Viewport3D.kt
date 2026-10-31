package com.example.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.camera.CameraMode
import com.example.core.camera.CameraNavigationPreset
import com.example.core.geometry.WallGeometryCutter
import com.example.core.math.Vector3
import com.example.domain.model.KitchenElementType
import com.example.domain.model.OpeningType
import com.example.domain.model.WallSide
import com.example.presentation.ui.RealisticKitchenRenderer3D.renderCabinetRealisticDetails
import com.example.presentation.ui.RealisticKitchenRenderer3D.renderCeilingAndBacksplashLighting
import com.example.presentation.ui.RealisticKitchenRenderer3D.renderRealisticFloor
import com.example.presentation.viewmodel.KitchenEditorUiState
import kotlinx.coroutines.delay
import kotlin.math.*

/**
 * Photorealistic 3D Projective Viewport rendering shaded parametric cabinets, recessed toe-kicks,
 * overhanging granite/marble countertops, smoked glass LED vitrines, illuminated niche columns,
 * recessed ceiling spotlights, linear LED channels, glossy floor reflections, and Section 1 Direct
 * Element 3D Gizmo Menus (التحكم المباشر على العنصر - Gizmo Menus).
 */
enum class Viewport3DInteractionMode(val labelAr: String) {
    AUTO_SMART("تلقائي 3D"),
    MOVE_MODEL_XZ("تحريك النماذج ✥"),
    MOVE_ELEVATION_Y("الارتفاع Y ↕"),
    CAMERA_ORBIT_ONLY("تدوير الكاميرا 🎥")
}

@Composable
fun Viewport3D(
    state: KitchenEditorUiState,
    onOrbit: (Float, Float) -> Unit,
    onZoom: (Float) -> Unit,
    onSelectElement: (String) -> Unit,
    onMoveSelected: (Float, Float, Float) -> Unit,
    onMoveElementToPosition: ((String, Vector3) -> Unit)? = null,
    onCommitTransform: ((String, Vector3, Float, Vector3) -> Unit)? = null,
    onPlaceFurnitureAt: ((Float, Float) -> Unit)? = null,
    onResizeSelected: ((String, Float, Float, Float) -> Unit)? = null,
    onPan: ((Float, Float) -> Unit)? = null,
    onWalkMovement: ((Float, Float) -> Unit)? = null,
    onWalkLook: ((Float, Float) -> Unit)? = null,
    onPresetSelected: ((CameraNavigationPreset) -> Unit)? = null,
    onToggleSprint: (() -> Unit)? = null,
    onExitWalk: (() -> Unit)? = null,
    onResetPan: (() -> Unit)? = null,
    onOpenFillerTool: (() -> Unit)? = null,
    externalDragType: KitchenElementType? = null,
    externalDragRootOffset: Offset? = null,
    onExternalDragWorldUpdate: ((Float, Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val room = state.project.room
    val elements = state.project.elements.filter { it.isVisible && it.layer !in state.hiddenLayers }
    val selectedIds = state.selectedElementIds
    val singleSelected = state.singleSelectedElement

    var interactionMode3D by remember { mutableStateOf(Viewport3DInteractionMode.AUTO_SMART) }

    // Center of the room in 3D mm (eye-level architectural target)
    val roomCenter = Vector3(room.widthMm * 0.5f, room.heightMm * 0.34f, room.depthMm * 0.5f)

    // Store projected screen centers, full 2D projected bounding boxes, & front-to-back order for accurate 3D hit-testing
    val projectedElementCenters = remember { mutableStateMapOf<String, Offset>() }
    val projectedElementBounds = remember { mutableStateMapOf<String, android.graphics.RectF>() }
    var frontToBackElementIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedTopScreenAnchor by remember { mutableStateOf<Offset?>(null) }
    var gizmoOriginScreen by remember { mutableStateOf<Offset?>(null) }
    var gizmoAxisXEndScreen by remember { mutableStateOf<Offset?>(null) }
    var gizmoAxisYEndScreen by remember { mutableStateOf<Offset?>(null) }
    var gizmoAxisZEndScreen by remember { mutableStateOf<Offset?>(null) }
    var activeDraggedGizmoAxis by remember { mutableStateOf<Char?>(null) }

    // Camera basis state for accurate screen-to-world 3D raycasting & camera-aligned dragging
    var currentCameraEye by remember { mutableStateOf(Vector3(room.widthMm * 0.5f, 1800f, room.depthMm * 1.8f)) }
    var currentForward by remember { mutableStateOf(Vector3(0f, -0.25f, -0.96f).normalized()) }
    var currentRight by remember { mutableStateOf(Vector3.UNIT_X) }
    var currentUp by remember { mutableStateOf(Vector3.UNIT_Y) }
    var currentFovScale by remember { mutableFloatStateOf(900f) }
    var currentCanvasSize by remember { mutableStateOf(Offset(1080f, 1920f)) }
    var viewportOriginInRoot by remember { mutableStateOf(Offset.Zero) }

    // Direct 3D Element Dragging & Drop Placement State (uses cumulative delta from dragStartPos3D so models never stick to walls)
    var isDraggingElement3D by remember { mutableStateOf(false) }
    var draggedElementId3D by remember { mutableStateOf<String?>(null) }
    var dragStartPos3D by remember { mutableStateOf<Vector3?>(null) }
    var dragStartRot3D by remember { mutableStateOf<Float?>(null) }
    var dragStartDims3D by remember { mutableStateOf<Vector3?>(null) }
    var accumDragDelta3D by remember { mutableStateOf(Vector3.ZERO) }
    var placementGhostWorldPos by remember { mutableStateOf<Vector3?>(null) }

    // Helper: Find the frontmost 3D element under a 2D screen point using full projected 3D bounding box + center fallback
    fun hitTestElement3D(screenPt: Offset): String? {
        for (elemId in frontToBackElementIds) {
            val rect = projectedElementBounds[elemId]
            if (rect != null && rect.contains(screenPt.x, screenPt.y)) {
                return elemId
            }
        }
        val nearestCenter = projectedElementCenters.entries
            .minByOrNull { (_, centerPt) -> (centerPt - screenPt).getDistanceSquared() }
        if (nearestCenter != null && (nearestCenter.value - screenPt).getDistance() <= 105f) {
            return nearestCenter.key
        }
        return null
    }

    // Helper: Intersect a 2D screen point with a horizontal 3D plane Y = planeY (with horizon fallback)
    fun screenToHorizontalPlane(screenPt: Offset, planeY: Float): Vector3? {
        val cw = currentCanvasSize.x
        val ch = currentCanvasSize.y
        if (cw <= 1f || ch <= 1f || currentFovScale <= 1f) return null
        val nx = (screenPt.x - cw * 0.5f) / currentFovScale
        val ny = (ch * 0.5f - screenPt.y) / currentFovScale
        val rayDir = (currentForward + (currentRight * nx) + (currentUp * ny)).normalized()
        if (abs(rayDir.y) >= 0.015f) {
            val t = (planeY - currentCameraEye.y) / rayDir.y
            if (t in 50f..35000f) {
                val hit = currentCameraEye + (rayDir * t)
                return Vector3(
                    x = hit.x.coerceIn(0f, room.widthMm),
                    y = planeY,
                    z = hit.z.coerceIn(0f, room.depthMm)
                )
            }
        }
        // Fallback when finger is near/above the horizon: map screen X/Y smoothly into room coordinates
        val uRatio = (screenPt.x / cw).coerceIn(0.05f, 0.95f)
        val vRatio = (screenPt.y / ch).coerceIn(0.05f, 0.95f)
        return Vector3(
            x = (uRatio * room.widthMm).coerceIn(0f, room.widthMm),
            y = planeY,
            z = (vRatio * room.depthMm).coerceIn(0f, room.depthMm)
        )
    }

    // Update live 3D ghost position when dragging an element directly from a menu into the 3D workspace
    var externalGhostWorldPos by remember { mutableStateOf<Vector3?>(null) }
    LaunchedEffect(externalDragType, externalDragRootOffset, viewportOriginInRoot, currentCanvasSize) {
        if (externalDragType != null && externalDragRootOffset != null) {
            val localPt = externalDragRootOffset - viewportOriginInRoot
            if (localPt.x in -40f..(currentCanvasSize.x + 40f) && localPt.y in -40f..(currentCanvasSize.y + 40f)) {
                val clampedLocal = Offset(
                    localPt.x.coerceIn(0f, currentCanvasSize.x),
                    localPt.y.coerceIn(0f, currentCanvasSize.y)
                )
                val hit = screenToHorizontalPlane(clampedLocal, externalDragType.defaultElevationY)
                if (hit != null) {
                    externalGhostWorldPos = hit
                    onExternalDragWorldUpdate?.invoke(hit.x, hit.z)
                }
            }
        } else {
            externalGhostWorldPos = null
        }
    }

    // Touch Inertia Momentum Tracking
    var orbitVelocity by remember { mutableStateOf(Offset.Zero) }
    var panVelocity by remember { mutableStateOf(Offset.Zero) }
    var isInteracting by remember { mutableStateOf(false) }

    LaunchedEffect(isInteracting) {
        if (!isInteracting) {
            while (orbitVelocity.getDistanceSquared() > 0.04f || panVelocity.getDistanceSquared() > 0.04f) {
                if (state.cameraMode == CameraMode.FIRST_PERSON_WALK) {
                    onWalkLook?.invoke(orbitVelocity.x, orbitVelocity.y)
                } else {
                    onOrbit(orbitVelocity.x, orbitVelocity.y)
                    if (panVelocity != Offset.Zero) {
                        onPan?.invoke(panVelocity.x, panVelocity.y)
                    }
                }
                orbitVelocity *= 0.91f
                panVelocity *= 0.91f
                delay(16L)
            }
            orbitVelocity = Offset.Zero
            panVelocity = Offset.Zero
        }
    }

    val currentState by rememberUpdatedState(state)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                viewportOriginInRoot = coords.positionInRoot()
            }
            .pointerInput(state.cameraMode) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val latestState = currentState
                    val selectedElem = latestState.singleSelectedElement

                    // 1. If user is pinching to zoom, always zoom camera
                    if (abs(zoom - 1f) > 0.008f) {
                        activeDraggedGizmoAxis = null
                        isDraggingElement3D = false
                        isInteracting = true
                        onZoom(zoom)
                        isInteracting = false
                        return@detectTransformGestures
                    }

                    // 2. If in Furniture Placement Mode, dragging moves the 3D ghost preview on the floor/wall
                    if (latestState.isFurniturePlacementMode && pan != Offset.Zero) {
                        val targetY = latestState.activePlacementType.defaultElevationY
                        val hitPt = screenToHorizontalPlane(centroid, targetY)
                        if (hitPt != null) {
                            placementGhostWorldPos = hitPt
                            return@detectTransformGestures
                        }
                    }

                    // 3. Direct 3D Element Dragging (Gizmo Axis OR Cabinet/Model Body Free Floor/Wall Plane Move)
                    if (pan != Offset.Zero && interactionMode3D != Viewport3DInteractionMode.CAMERA_ORBIT_ONLY) {
                        val gO = gizmoOriginScreen
                        val gX = gizmoAxisXEndScreen
                        val gY = gizmoAxisYEndScreen
                        val gZ = gizmoAxisZEndScreen

                        if (!isDraggingElement3D && activeDraggedGizmoAxis == null) {
                            val hitRadius = 76f
                            val hitGizmo = if (selectedElem != null && gO != null) {
                                when {
                                    gX != null && (centroid - gX).getDistance() <= hitRadius -> 'X'
                                    gY != null && (centroid - gY).getDistance() <= hitRadius -> 'Y'
                                    gZ != null && (centroid - gZ).getDistance() <= hitRadius -> 'Z'
                                    (centroid - gO).getDistance() <= hitRadius -> {
                                        if (interactionMode3D == Viewport3DInteractionMode.MOVE_ELEVATION_Y) 'Y' else 'F'
                                    }
                                    else -> null
                                }
                            } else null

                            if (hitGizmo != null && selectedElem != null && !selectedElem.isLocked) {
                                activeDraggedGizmoAxis = hitGizmo
                                isDraggingElement3D = true
                                draggedElementId3D = selectedElem.id
                                dragStartPos3D = selectedElem.position
                                dragStartRot3D = selectedElem.rotationY
                                dragStartDims3D = selectedElem.dimensions
                                accumDragDelta3D = Vector3.ZERO
                            } else if (interactionMode3D == Viewport3DInteractionMode.MOVE_MODEL_XZ ||
                                interactionMode3D == Viewport3DInteractionMode.MOVE_ELEVATION_Y
                            ) {
                                // In explicit Model Move mode, dragging anywhere moves the touched or currently selected model
                                val hitId = hitTestElement3D(centroid) ?: selectedElem?.id
                                val targetElem = latestState.project.elements.find { it.id == hitId }
                                if (targetElem != null && !targetElem.isLocked) {
                                    if (selectedElem?.id != targetElem.id) {
                                        onSelectElement(targetElem.id)
                                    }
                                    activeDraggedGizmoAxis = if (interactionMode3D == Viewport3DInteractionMode.MOVE_ELEVATION_Y) 'Y' else 'F'
                                    isDraggingElement3D = true
                                    draggedElementId3D = targetElem.id
                                    dragStartPos3D = targetElem.position
                                    dragStartRot3D = targetElem.rotationY
                                    dragStartDims3D = targetElem.dimensions
                                    accumDragDelta3D = Vector3.ZERO
                                }
                            } else {
                                // Auto Smart Mode: check if user touched inside any 3D cabinet/model projected bounding box
                                val hitId = hitTestElement3D(centroid)
                                if (hitId != null) {
                                    val targetElem = latestState.project.elements.find { it.id == hitId }
                                    if (targetElem != null && !targetElem.isLocked) {
                                        if (selectedElem?.id != targetElem.id) {
                                            onSelectElement(targetElem.id)
                                        }
                                        activeDraggedGizmoAxis = 'F'
                                        isDraggingElement3D = true
                                        draggedElementId3D = targetElem.id
                                        dragStartPos3D = targetElem.position
                                        dragStartRot3D = targetElem.rotationY
                                        dragStartDims3D = targetElem.dimensions
                                        accumDragDelta3D = Vector3.ZERO
                                    }
                                }
                            }
                        }

                        if (isDraggingElement3D && activeDraggedGizmoAxis != null) {
                            // Calculate camera-relative horizontal basis vectors so dragging matches finger direction!
                            val rawRight = Vector3(currentRight.x, 0f, currentRight.z)
                            val floorRight = if (rawRight.lengthSquared() > 0.001f) rawRight.normalized() else Vector3.UNIT_X
                            val rawFwd = Vector3(currentForward.x, 0f, currentForward.z)
                            val floorFwd = if (rawFwd.lengthSquared() > 0.001f) rawFwd.normalized() else Vector3.UNIT_Z

                            // Scale movement speed by camera distance to element
                            val activeElem = latestState.project.elements.find { it.id == draggedElementId3D } ?: selectedElem
                            val distToCam = if (activeElem != null) {
                                (activeElem.position - currentCameraEye).length().coerceIn(800f, 9000f)
                            } else 3200f
                            val mmPerPixel = (distToCam / currentFovScale.coerceAtLeast(350f)).coerceIn(1.2f, 5.5f)

                            val stepDelta = when (activeDraggedGizmoAxis) {
                                'X' -> {
                                    val signX = if (floorRight.x >= -0.15f) 1f else -1f
                                    Vector3(pan.x * mmPerPixel * signX, 0f, 0f)
                                }
                                'Y' -> {
                                    Vector3(0f, -pan.y * mmPerPixel, 0f)
                                }
                                'Z' -> {
                                    val deltaZ = (-pan.y * floorFwd.z + pan.x * floorRight.z) * mmPerPixel
                                    Vector3(0f, 0f, deltaZ)
                                }
                                else -> {
                                    (floorRight * (pan.x * mmPerPixel)) + (floorFwd * (-pan.y * mmPerPixel))
                                }
                            }

                            accumDragDelta3D = accumDragDelta3D + stepDelta
                            val startPos = dragStartPos3D
                            val draggedId = draggedElementId3D
                            if (onMoveElementToPosition != null && startPos != null && draggedId != null) {
                                onMoveElementToPosition(draggedId, startPos + accumDragDelta3D)
                            } else {
                                onMoveSelected(stepDelta.x, stepDelta.y, stepDelta.z)
                            }
                            return@detectTransformGestures
                        }
                    }

                    activeDraggedGizmoAxis = null
                    isDraggingElement3D = false
                    isInteracting = true

                    if (pan != Offset.Zero) {
                        if (latestState.cameraMode == CameraMode.FIRST_PERSON_WALK) {
                            val dyaw = pan.x * 0.32f
                            val dpitch = pan.y * 0.32f
                            onWalkLook?.invoke(dyaw, dpitch)
                            orbitVelocity = Offset(dyaw, dpitch)
                        } else {
                            val dyaw = -pan.x * 0.35f
                            val dpitch = pan.y * 0.35f
                            onOrbit(dyaw, dpitch)
                            orbitVelocity = Offset(dyaw, dpitch)
                        }
                    }
                    isInteracting = false
                }
            }
            .pointerInput(elements, state.isFurniturePlacementMode, state.activePlacementType) {
                detectTapGestures(
                    onPress = { pressPt ->
                        val latestState = currentState
                        if (latestState.isFurniturePlacementMode) {
                            val targetY = latestState.activePlacementType.defaultElevationY
                            placementGhostWorldPos = screenToHorizontalPlane(pressPt, targetY)
                        }
                        tryAwaitRelease()
                        // Commit Undo/Redo command if an element was dragged in 3D
                        if (isDraggingElement3D && draggedElementId3D != null && dragStartPos3D != null && dragStartRot3D != null && dragStartDims3D != null) {
                            onCommitTransform?.invoke(
                                draggedElementId3D!!,
                                dragStartPos3D!!,
                                dragStartRot3D!!,
                                dragStartDims3D!!
                            )
                        }
                        // If user dragged & released in Furniture Placement mode, drop and lock furniture at ghost pos
                        if (latestState.isFurniturePlacementMode && placementGhostWorldPos != null) {
                            val dropPt = placementGhostWorldPos!!
                            placementGhostWorldPos = null
                            onPlaceFurnitureAt?.invoke(dropPt.x, dropPt.z)
                        }
                        isDraggingElement3D = false
                        activeDraggedGizmoAxis = null
                        draggedElementId3D = null
                        accumDragDelta3D = Vector3.ZERO
                    },
                    onTap = { tapOffset ->
                        val latestState = currentState
                        activeDraggedGizmoAxis = null
                        isDraggingElement3D = false
                        accumDragDelta3D = Vector3.ZERO

                        // Check if tapping an existing element first (using full 2D projected bounding box + center fallback)
                        val hitId = hitTestElement3D(tapOffset)
                        if (hitId != null) {
                            onSelectElement(hitId)
                            return@detectTapGestures
                        }

                        // Otherwise, if in Furniture Placement Mode, place & lock furniture at the tapped 3D floor/wall point
                        if (latestState.isFurniturePlacementMode && onPlaceFurnitureAt != null) {
                            val targetY = latestState.activePlacementType.defaultElevationY
                            val worldHit = screenToHorizontalPlane(tapOffset, targetY)
                            if (worldHit != null) {
                                onPlaceFurnitureAt(worldHit.x, worldHit.z)
                            }
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height
            currentCanvasSize = Offset(canvasW, canvasH)

            // Studio Architectural Backdrop Gradient (prevents flat void appearance)
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B1325),
                        Color(0xFF14213D),
                        Color(0xFF090F1E)
                    ),
                    startY = 0f,
                    endY = canvasH
                )
            )

            val cameraEye: Vector3
            val forward: Vector3
            val right: Vector3
            val up: Vector3
            // Aspect-ratio-aware perspective focal length (~48° natural architectural lens)
            val fovScale = min(canvasW, canvasH) * 1.05f

            if (state.cameraMode == CameraMode.FIRST_PERSON_WALK) {
                cameraEye = state.walkCameraPosition
                val yawRad = Math.toRadians(state.walkLookYaw.toDouble()).toFloat()
                val pitchRad = Math.toRadians(state.walkLookPitch.toDouble()).toFloat()

                forward = Vector3(
                    x = cos(pitchRad) * sin(yawRad),
                    y = sin(pitchRad),
                    z = cos(pitchRad) * cos(yawRad)
                ).normalized()
                val worldUp = Vector3.UNIT_Y
                val r = forward.cross(worldUp)
                right = if (r.lengthSquared() > 0.001f) r.normalized() else Vector3.UNIT_X
                up = right.cross(forward).normalized()
            } else {
                val yawRad = Math.toRadians(state.cameraYaw.toDouble()).toFloat()
                val pitchRad = Math.toRadians(state.cameraPitch.toDouble()).toFloat()
                val distance = (max(room.widthMm, room.depthMm) * 1.38f) / state.cameraZoom

                val target = roomCenter + Vector3(state.cameraPanX, state.cameraPanY * 0.6f, 0f)
                val eyeX = target.x + distance * cos(pitchRad) * sin(yawRad)
                val eyeY = (target.y + distance * sin(pitchRad)).coerceAtLeast(180f)
                val eyeZ = target.z + distance * cos(pitchRad) * cos(yawRad)
                cameraEye = Vector3(eyeX, eyeY, eyeZ)

                val fwd = (target - cameraEye)
                forward = if (fwd.lengthSquared() > 0.001f) fwd.normalized() else Vector3(0f, 0f, -1f)
                val worldUp = Vector3.UNIT_Y
                val r = forward.cross(worldUp)
                right = if (r.lengthSquared() > 0.001f) r.normalized() else Vector3.UNIT_X
                up = right.cross(forward).normalized()
            }

            currentCameraEye = cameraEye
            currentForward = forward
            currentRight = right
            currentUp = up
            currentFovScale = fovScale

            fun project3DTo2D(p: Vector3): Pair<Offset, Float>? {
                val toPoint = p - cameraEye
                val camZ = toPoint.dot(forward)
                // Near-plane clamp so close floor/wall vertices still project instead of vanishing
                val safeZ = if (camZ <= 25f) {
                    if (camZ <= -400f) return null
                    25f
                } else camZ

                val camX = toPoint.dot(right)
                val camY = toPoint.dot(up)

                val screenX = canvasW * 0.5f + (camX / safeZ) * fovScale
                val screenY = canvasH * 0.5f - (camY / safeZ) * fovScale
                return Pair(Offset(screenX, screenY), safeZ)
            }

            val lightDir = Vector3(0.42f, 0.84f, 0.34f).normalized()

            renderRealisticFloor(
                room = room,
                elements = elements,
                project = ::project3DTo2D
            )

            // Draw Room Walls with Smart Architectural Cutaway (foreground walls auto-fade so they never block the kitchen)
            val wallColor = Color(room.wallFinish.colorHex)
            room.walls.filter { it.isEnabled }.forEach { wall ->
                val wallSamplePt = when (wall.side) {
                    WallSide.NORTH -> Vector3(room.widthMm * 0.5f, room.heightMm * 0.5f, 0f)
                    WallSide.SOUTH -> Vector3(room.widthMm * 0.5f, room.heightMm * 0.5f, room.depthMm)
                    WallSide.WEST -> Vector3(0f, room.heightMm * 0.5f, room.depthMm * 0.5f)
                    WallSide.EAST -> Vector3(room.widthMm, room.heightMm * 0.5f, room.depthMm * 0.5f)
                }
                val toCamera = (cameraEye - wallSamplePt).normalized()
                val facingCameraDot = wall.innerNormal.dot(toCamera)
                val isForegroundCutaway = state.cameraMode != CameraMode.FIRST_PERSON_WALK && facingCameraDot < 0.08f

                if (isForegroundCutaway) {
                    // Draw a sleek 60mm low architectural plinth curb for foreground cutaway walls so room bounds remain clear
                    val (c0, c1) = when (wall.side) {
                        WallSide.NORTH -> Pair(Vector3(0f, 0f, 0f), Vector3(room.widthMm, 45f, 0f))
                        WallSide.SOUTH -> Pair(Vector3(0f, 0f, room.depthMm), Vector3(room.widthMm, 45f, room.depthMm))
                        WallSide.WEST -> Pair(Vector3(0f, 0f, 0f), Vector3(0f, 45f, room.depthMm))
                        WallSide.EAST -> Pair(Vector3(room.widthMm, 0f, 0f), Vector3(room.widthMm, 45f, room.depthMm))
                    }
                    val p0 = project3DTo2D(c0)?.first
                    val p1 = project3DTo2D(Vector3(c1.x, 0f, c1.z))?.first
                    if (p0 != null && p1 != null) {
                        drawLine(Color(0x5594A3B8), p0, p1, strokeWidth = 2.5f)
                    }
                    return@forEach
                }

                val blocks = WallGeometryCutter.decomposeWall(wall)
                for (block in blocks) {
                    val startOffset = block.startOffsetMm
                    val endOffset = block.startOffsetMm + block.lengthMm
                    val bY = block.bottomElevationMm
                    val tY = block.topElevationMm

                    val (w0, w1) = when (wall.side) {
                        WallSide.NORTH -> Pair(Vector3(startOffset, bY, 0f), Vector3(endOffset, bY, 0f))
                        WallSide.SOUTH -> Pair(Vector3(startOffset, bY, room.depthMm), Vector3(endOffset, bY, room.depthMm))
                        WallSide.WEST -> Pair(Vector3(0f, bY, startOffset), Vector3(0f, bY, endOffset))
                        WallSide.EAST -> Pair(Vector3(room.widthMm, bY, startOffset), Vector3(room.widthMm, bY, endOffset))
                    }
                    val w0Top = w0.copy(y = tY)
                    val w1Top = w1.copy(y = tY)

                    val pt0 = project3DTo2D(w0)
                    val pt1 = project3DTo2D(w1)
                    val pt1T = project3DTo2D(w1Top)
                    val pt0T = project3DTo2D(w0Top)

                    if (pt0 != null && pt1 != null && pt1T != null && pt0T != null) {
                        val wallPath = Path().apply {
                            moveTo(pt0.first.x, pt0.first.y)
                            lineTo(pt1.first.x, pt1.first.y)
                            lineTo(pt1T.first.x, pt1T.first.y)
                            lineTo(pt0T.first.x, pt0T.first.y)
                            close()
                        }
                        val shade = (0.58f + 0.42f * max(0f, wall.innerNormal.dot(lightDir))).coerceIn(0.52f, 1.0f)
                        val shadedTop = wallColor.copy(
                            red = (wallColor.red * shade).coerceIn(0f, 1f),
                            green = (wallColor.green * shade).coerceIn(0f, 1f),
                            blue = (wallColor.blue * shade).coerceIn(0f, 1f),
                            alpha = 0.96f
                        )
                        val shadedBottom = wallColor.copy(
                            red = (wallColor.red * shade * 0.86f).coerceIn(0f, 1f),
                            green = (wallColor.green * shade * 0.86f).coerceIn(0f, 1f),
                            blue = (wallColor.blue * shade * 0.86f).coerceIn(0f, 1f),
                            alpha = 0.98f
                        )
                        drawPath(
                            wallPath,
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(shadedTop, shadedBottom),
                                startY = min(pt0T.first.y, pt1T.first.y),
                                endY = max(pt0.first.y, pt1.first.y)
                            )
                        )
                        drawPath(wallPath, color = Color(0x441E293B), style = Stroke(width = 1.3f))
                    }
                }

                // Render Window Zebra Blinds / Daylight Glass & Door Frames
                wall.openings.forEach { opening ->
                    val (opStart, opEnd) = when (wall.side) {
                        WallSide.NORTH -> Pair(
                            Vector3(opening.offsetMm, opening.sillHeightMm, 0f),
                            Vector3(opening.offsetMm + opening.widthMm, opening.sillHeightMm + opening.heightMm, 0f)
                        )
                        WallSide.SOUTH -> Pair(
                            Vector3(opening.offsetMm, opening.sillHeightMm, room.depthMm),
                            Vector3(opening.offsetMm + opening.widthMm, opening.sillHeightMm + opening.heightMm, room.depthMm)
                        )
                        WallSide.WEST -> Pair(
                            Vector3(0f, opening.sillHeightMm, opening.offsetMm),
                            Vector3(0f, opening.sillHeightMm + opening.heightMm, opening.offsetMm + opening.widthMm)
                        )
                        WallSide.EAST -> Pair(
                            Vector3(room.widthMm, opening.sillHeightMm, opening.offsetMm),
                            Vector3(room.widthMm, opening.sillHeightMm + opening.heightMm, opening.offsetMm + opening.widthMm)
                        )
                    }
                    val opP0 = project3DTo2D(opStart)
                    val opP1 = project3DTo2D(Vector3(opEnd.x, opStart.y, opEnd.z))
                    val opP2 = project3DTo2D(opEnd)
                    val opP3 = project3DTo2D(Vector3(opStart.x, opEnd.y, opStart.z))

                    if (opP0 != null && opP1 != null && opP2 != null && opP3 != null) {
                        val framePath = Path().apply {
                            moveTo(opP0.first.x, opP0.first.y)
                            lineTo(opP1.first.x, opP1.first.y)
                            lineTo(opP2.first.x, opP2.first.y)
                            lineTo(opP3.first.x, opP3.first.y)
                            close()
                        }
                        val frameColor = if (opening.type == OpeningType.DOOR) Color(0xCC4A3525) else Color(0xAABAE6FD)
                        drawPath(framePath, color = frameColor)
                        drawPath(framePath, color = Color(0xFF0F172A), style = Stroke(width = 3.5f))
                    }
                }
            }

            renderCeilingAndBacksplashLighting(
                room = room,
                elements = elements,
                project = ::project3DTo2D
            )

            // Draw live 3D Volumetric Cabinet Ghost Preview when dragging from a menu OR dragging in 3D placement mode
            val activeGhostType = externalDragType ?: state.activePlacementType
            val activeRawGhostPos = externalGhostWorldPos ?: placementGhostWorldPos
            activeRawGhostPos?.let { rawGhostPos ->
                val ghostDims = activeGhostType.defaultDimensions
                val tempCandidate = com.example.domain.model.KitchenElement(
                    id = "ghost_preview_3d",
                    name = activeGhostType.title,
                    type = activeGhostType,
                    position = rawGhostPos,
                    dimensions = ghostDims
                )
                val snapRes = com.example.core.geometry.SmartSnapper.snap(
                    target = tempCandidate,
                    existingElements = elements,
                    room = room,
                    snapThresholdMm = state.snapThresholdMm,
                    preventOverlap = true
                )
                val ghostPos = snapRes.position
                val isGhostColliding = snapRes.hasCollisionOrViolation
                val isRot = snapRes.rotationY == 90f || snapRes.rotationY == 270f
                val hw = (if (isRot) ghostDims.z else ghostDims.x) * 0.5f
                val hd = (if (isRot) ghostDims.x else ghostDims.z) * 0.5f
                val gh = ghostDims.y

                val b0 = project3DTo2D(Vector3(ghostPos.x - hw, ghostPos.y, ghostPos.z - hd))?.first
                val b1 = project3DTo2D(Vector3(ghostPos.x + hw, ghostPos.y, ghostPos.z - hd))?.first
                val b2 = project3DTo2D(Vector3(ghostPos.x + hw, ghostPos.y, ghostPos.z + hd))?.first
                val b3 = project3DTo2D(Vector3(ghostPos.x - hw, ghostPos.y, ghostPos.z + hd))?.first

                val t0 = project3DTo2D(Vector3(ghostPos.x - hw, ghostPos.y + gh, ghostPos.z - hd))?.first
                val t1 = project3DTo2D(Vector3(ghostPos.x + hw, ghostPos.y + gh, ghostPos.z - hd))?.first
                val t2 = project3DTo2D(Vector3(ghostPos.x + hw, ghostPos.y + gh, ghostPos.z + hd))?.first
                val t3 = project3DTo2D(Vector3(ghostPos.x - hw, ghostPos.y + gh, ghostPos.z + hd))?.first

                val ghostFill = if (isGhostColliding) Color(0x66EF4444) else Color(0x4410B981)
                val ghostTopFill = if (isGhostColliding) Color(0x88EF4444) else Color(0x6634D399)
                val ghostStroke = if (isGhostColliding) Color(0xFFEF4444) else Color(0xFF10B981)

                if (b0 != null && b1 != null && b2 != null && b3 != null) {
                    val ghostFloorPath = Path().apply {
                        moveTo(b0.x, b0.y)
                        lineTo(b1.x, b1.y)
                        lineTo(b2.x, b2.y)
                        lineTo(b3.x, b3.y)
                        close()
                    }
                    drawPath(ghostFloorPath, color = ghostFill)
                    drawPath(ghostFloorPath, color = ghostStroke, style = Stroke(width = if (isGhostColliding) 4.2f else 3f))
                    if (isGhostColliding) {
                        drawLine(Color(0xFFEF4444), b0, b2, strokeWidth = 3f)
                        drawLine(Color(0xFFEF4444), b1, b3, strokeWidth = 3f)
                    }
                }

                // Draw 3D volumetric cabinet prism so user sees the exact 3D cabinet height & shape while dragging
                if (b0 != null && b1 != null && b2 != null && b3 != null && t0 != null && t1 != null && t2 != null && t3 != null) {
                    val topPath = Path().apply {
                        moveTo(t0.x, t0.y)
                        lineTo(t1.x, t1.y)
                        lineTo(t2.x, t2.y)
                        lineTo(t3.x, t3.y)
                        close()
                    }
                    drawPath(topPath, color = ghostTopFill)
                    drawPath(topPath, color = ghostStroke, style = Stroke(width = 2.6f))
                    drawLine(ghostStroke, b0, t0, strokeWidth = 2.2f)
                    drawLine(ghostStroke, b1, t1, strokeWidth = 2.2f)
                    drawLine(ghostStroke, b2, t2, strokeWidth = 2.2f)
                    drawLine(ghostStroke, b3, t3, strokeWidth = 2.2f)
                }
            }

            val sortedElements = elements.map { elem ->
                val center = elem.position + Vector3(0f, elem.dimensions.y * 0.5f, 0f)
                val dist = (center - cameraEye).length()
                Pair(elem, dist)
            }.sortedByDescending { it.second }

            val activeCol = state.collisionResult
            val collidingIdSet = activeCol?.collidingElementIds?.toSet() ?: emptySet()

            projectedElementCenters.clear()
            projectedElementBounds.clear()
            frontToBackElementIds = sortedElements.asReversed().map { it.first.id }
            selectedTopScreenAnchor = null
            gizmoOriginScreen = null
            gizmoAxisXEndScreen = null
            gizmoAxisYEndScreen = null
            gizmoAxisZEndScreen = null

            sortedElements.forEach { (elem, _) ->
                val center3D = elem.position + Vector3(0f, elem.dimensions.y * 0.5f, 0f)
                project3DTo2D(center3D)?.first?.let { screenPt ->
                    projectedElementCenters[elem.id] = screenPt
                }

                // Compute 2D projected bounding box across all 8 corners of the 3D element for accurate touch hit-testing
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
                var minScreenX = Float.POSITIVE_INFINITY
                var minScreenY = Float.POSITIVE_INFINITY
                var maxScreenX = Float.NEGATIVE_INFINITY
                var maxScreenY = Float.NEGATIVE_INFINITY
                var projectedCornerCount = 0
                for (corner in corners3D) {
                    val pCorner = project3DTo2D(corner)?.first
                    if (pCorner != null) {
                        minScreenX = min(minScreenX, pCorner.x)
                        minScreenY = min(minScreenY, pCorner.y)
                        maxScreenX = max(maxScreenX, pCorner.x)
                        maxScreenY = max(maxScreenY, pCorner.y)
                        projectedCornerCount++
                    }
                }
                if (projectedCornerCount >= 2) {
                    val padPx = 12f
                    projectedElementBounds[elem.id] = android.graphics.RectF(
                        minScreenX - padPx,
                        minScreenY - padPx,
                        maxScreenX + padPx,
                        maxScreenY + padPx
                    )
                }

                val isElemSelected = elem.id in selectedIds
                val isElemColliding = (isElemSelected && activeCol?.hasCollision == true) || elem.id in collidingIdSet

                renderCabinetRealisticDetails(
                    elem = elem,
                    cameraEye = cameraEye,
                    lightDir = lightDir,
                    isSelected = isElemSelected,
                    project = ::project3DTo2D,
                    isColliding = isElemColliding
                )

                // Section 1: Draw Direct 3D Axis Gizmo Arrows (X/Y/Z) directly above/on the selected cabinet
                if (singleSelected != null && elem.id == singleSelected.id) {
                    val topCenter3D = elem.position + Vector3(0f, elem.dimensions.y + 40f, 0f)
                    val axisLenMm = 320f
                    val pO = project3DTo2D(center3D)?.first
                    val pX = project3DTo2D(center3D + Vector3(axisLenMm, 0f, 0f))?.first
                    val pY = project3DTo2D(center3D + Vector3(0f, axisLenMm, 0f))?.first
                    val pZ = project3DTo2D(center3D + Vector3(0f, 0f, axisLenMm))?.first
                    val pTop = project3DTo2D(topCenter3D)?.first

                    selectedTopScreenAnchor = pTop ?: pO
                    gizmoOriginScreen = pO
                    gizmoAxisXEndScreen = pX
                    gizmoAxisYEndScreen = pY
                    gizmoAxisZEndScreen = pZ

                    val labelPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 22f
                        isFakeBoldText = true
                        isAntiAlias = true
                    }

                    if (pO != null && pX != null) {
                        drawLine(Color(0xFFEF4444), pO, pX, strokeWidth = 5f)
                        drawCircle(Color(0xFFEF4444), radius = 10f, center = pX)
                        drawContext.canvas.nativeCanvas.drawText("X", pX.x + 8f, pX.y + 6f, labelPaint)
                    }
                    if (pO != null && pY != null) {
                        drawLine(Color(0xFF10B981), pO, pY, strokeWidth = 5f)
                        drawCircle(Color(0xFF10B981), radius = 10f, center = pY)
                        drawContext.canvas.nativeCanvas.drawText("Y", pY.x + 8f, pY.y - 6f, labelPaint)
                    }
                    if (pO != null && pZ != null) {
                        drawLine(Color(0xFF38BDF8), pO, pZ, strokeWidth = 5f)
                        drawCircle(Color(0xFF38BDF8), radius = 10f, center = pZ)
                        drawContext.canvas.nativeCanvas.drawText("Z", pZ.x + 8f, pZ.y + 6f, labelPaint)
                    }
                    if (pO != null) {
                        drawCircle(if (isElemColliding) Color(0xFFEF4444) else Color.White, radius = 8f, center = pO)
                    }
                }
            }

            state.activeSnapGuideLines.forEach { guide ->
                val pStart = project3DTo2D(guide.start)
                val pEnd = project3DTo2D(guide.end)
                if (pStart != null && pEnd != null) {
                    val guideColor = when {
                        guide.isCollisionWarning -> Color(0xFFEF4444)
                        guide.isWallGuide -> Color(0xFF10B981)
                        else -> Color(0xFF06B6D4)
                    }
                    drawLine(
                        color = guideColor,
                        start = pStart.first,
                        end = pEnd.first,
                        strokeWidth = if (guide.isCollisionWarning) 4.5f else 3.5f
                    )
                    drawCircle(color = guideColor, radius = if (guide.isCollisionWarning) 7f else 5.5f, center = pStart.first)
                    drawCircle(color = guideColor, radius = if (guide.isCollisionWarning) 7f else 5.5f, center = pEnd.first)
                }
            }
        }

        // Section 1: Direct On-Element Glassmorphic Gizmo Menu (التحكم المباشر على العنصر - قابل للإخفاء)
        var isGizmoCardCollapsed by remember { mutableStateOf(false) }
        val anchor = selectedTopScreenAnchor
        if (singleSelected != null && anchor != null && !state.isCleanCanvasMode) {
            val isSelectedColliding = state.collisionResult?.hasCollision == true
            val density = LocalDensity.current
            val cardWidthPx = with(density) { (if (isGizmoCardCollapsed) 160.dp else 265.dp).toPx() }
            val cardHeightPx = with(density) { (if (isGizmoCardCollapsed) 36.dp else 95.dp).toPx() }
            val offsetX = (anchor.x - cardWidthPx * 0.5f).roundToInt().coerceAtLeast(12)
            val offsetY = (anchor.y - cardHeightPx - 18f).roundToInt().coerceAtLeast(70)

            Surface(
                modifier = Modifier
                    .offset { IntOffset(offsetX, offsetY) }
                    .width(if (isGizmoCardCollapsed) 165.dp else 270.dp)
                    .border(
                        width = if (isSelectedColliding) 1.8.dp else 1.dp,
                        color = if (isSelectedColliding) Color(0xFFEF4444) else Color.White.copy(alpha = 0.28f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .testTag("direct_element_gizmo_menu"),
                shape = RoundedCornerShape(14.dp),
                color = if (isSelectedColliding) Color(0xFF7F1D1D).copy(alpha = 0.84f) else Color(0xFF0F172A).copy(alpha = 0.68f),
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${(singleSelected.dimensions.x / 10f).toInt()}×${(singleSelected.dimensions.y / 10f).toInt()}×${(singleSelected.dimensions.z / 10f).toInt()} سم",
                            color = if (isSelectedColliding) Color(0xFFFCA5A5) else Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { isGizmoCardCollapsed = !isGizmoCardCollapsed },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                if (isGizmoCardCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                contentDescription = "طي/توسيع",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    if (isSelectedColliding) {
                        Text(
                            text = state.collisionResult?.message?.takeIf { it.isNotBlank() }
                                ?: "⛔ مؤشر التصادم: تداخل أو انتهاك خلوص",
                            color = Color(0xFFFECACA),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("collision_indicator_3d_badge")
                        )
                    }
                    if (!isGizmoCardCollapsed) {
                        Text(
                            text = "الارتفاع Y: ${(singleSelected.position.y / 10f).toInt()} سم" +
                                    if (!singleSelected.hasBackPanel) " | [بدون ظهر 3D]" else "",
                            color = Color(0xFF34D399),
                            fontSize = 9.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    // Direct 3D Axis Nudge & Dimension Buttons on the element itself (X, Z, Y, Width)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // X Axis
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("X:", color = Color(0xFFEF4444), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { onMoveSelected(-50f, 0f, 0f) }, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "X-", tint = Color.White, modifier = Modifier.size(15.dp))
                            }
                            IconButton(onClick = { onMoveSelected(50f, 0f, 0f) }, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "X+", tint = Color.White, modifier = Modifier.size(15.dp))
                            }
                        }
                        // Z Axis (Depth)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Z:", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { onMoveSelected(0f, 0f, -50f) }, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Z-", tint = Color.White, modifier = Modifier.size(15.dp))
                            }
                            IconButton(onClick = { onMoveSelected(0f, 0f, 50f) }, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Z+", tint = Color.White, modifier = Modifier.size(15.dp))
                            }
                        }
                        // Y Axis (Enforces Section 2 Lower Safety Limit >= Base + 60cm)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Y:", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            IconButton(
                                onClick = { onMoveSelected(0f, -50f, 0f) },
                                modifier = Modifier.size(22.dp).testTag("gizmo_move_down_y")
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Y-", tint = Color.White, modifier = Modifier.size(15.dp))
                            }
                            IconButton(
                                onClick = { onMoveSelected(0f, 50f, 0f) },
                                modifier = Modifier.size(22.dp).testTag("gizmo_move_up_y")
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Y+", tint = Color.White, modifier = Modifier.size(15.dp))
                            }
                        }
                        // Width +/- 5cm
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("عرض:", color = Color(0xFFFBBF24), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            IconButton(
                                onClick = {
                                    val minAllowedW = if (singleSelected.type.isFillerStrip) 10f else 100f
                                    val newW = (singleSelected.dimensions.x - 50f).coerceAtLeast(minAllowedW)
                                    onResizeSelected?.invoke(singleSelected.id, newW, singleSelected.dimensions.y, singleSelected.dimensions.z)
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "W-", tint = Color.White, modifier = Modifier.size(13.dp))
                            }
                            IconButton(
                                onClick = {
                                    val newW = (singleSelected.dimensions.x + 50f).coerceAtMost(2400f)
                                    onResizeSelected?.invoke(singleSelected.id, newW, singleSelected.dimensions.y, singleSelected.dimensions.z)
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "W+", tint = Color.White, modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                    }
                }
            }
        }

        // 3D Interaction Mode Selector & Quick Filler Button (Top-End floating pill so users can lock Model Dragging vs Camera Orbit)
        if (!state.isCleanCanvasMode) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.84f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 52.dp, end = 10.dp)
                    .testTag("viewport_3d_interaction_mode_bar")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Viewport3DInteractionMode.entries.forEach { mode ->
                        val isSel = interactionMode3D == mode
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) Color(0xFF0284C7) else Color(0xFF1E293B),
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = if (isSel) Color(0xFF38BDF8) else Color(0xFF334155),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .pointerInput(mode) {
                                    detectTapGestures { interactionMode3D = mode }
                                }
                                .testTag("mode_3d_${mode.name.lowercase()}")
                        ) {
                            Text(
                                text = mode.labelAr,
                                color = if (isSel) Color.White else Color(0xFFCBD5E1),
                                fontSize = 9.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    if (onOpenFillerTool != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.25f),
                            modifier = Modifier
                                .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures { onOpenFillerTool() }
                                }
                                .testTag("viewport_3d_open_filler_btn")
                        ) {
                            Text(
                                text = "📏 فيلر 1مم",
                                color = Color(0xFFFDE68A),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Overlay camera navigation presets and walkthrough HUD
        CameraNavigationOverlay(
            state = state,
            onPresetSelected = { preset -> onPresetSelected?.invoke(preset) },
            onWalkMovement = { fwd, strafe -> onWalkMovement?.invoke(fwd, strafe) },
            onToggleSprint = { onToggleSprint?.invoke() },
            onExitWalk = { onExitWalk?.invoke() },
            onResetPan = { onResetPan?.invoke() }
        )
    }
}
