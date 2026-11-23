package com.example.presentation.ui

import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.command.CommandCategory
import com.example.core.math.Vector3
import com.example.core.units.MeasurementUnitSystem
import com.example.domain.model.*
import com.example.presentation.viewmodel.KitchenEditorUiState
import kotlin.math.*

enum class ResizeHandle2D(val labelAr: String) {
    NONE(""),
    EAST_WIDTH("تغيير العرض (شرق)"),
    WEST_WIDTH("تغيير العرض (غرب)"),
    SOUTH_DEPTH("تغيير العمق (جنوب)"),
    NORTH_DEPTH("تغيير العمق (شمال)"),
    CORNER_SE("تغيير العرض والعمق (زاوية)")
}

/**
 * High-precision 2D CAD Floorplan Viewport with integrated Command Pattern Undo/Redo state management,
 * real-time dimension labels when moving or resizing cabinets, 4-way wall distance callouts,
 * neighbor cabinet gap measurements, and interactive 2D resize handles.
 */
@Composable
fun Viewport2D(
    state: KitchenEditorUiState,
    onSelectElement: (String) -> Unit,
    onMoveSelected: (Float, Float, Float) -> Unit,
    onMoveElementToPosition: ((String, Vector3, Float) -> Unit)? = null,
    onCommitTransform: (String, Vector3, Float, Vector3) -> Unit,
    onResizeSelected: (String, Float, Float, Float) -> Unit = { _, _, _, _ -> },
    onPlaceFurnitureAt: (Float, Float) -> Unit = { _, _ -> },
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onUndoToStep: (Int) -> Unit = {},
    onRedoToStep: (Int) -> Unit = {},
    onRotateSelected: (Float) -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onDuplicateSelected: () -> Unit = {},
    onAlignSelectedToWall: (WallSide) -> Unit = {},
    onApplyRoomLayoutShape: (RoomLayoutShape) -> Unit = {},
    onExportFloorPlanDxf: () -> Unit = {},
    onOpenFillerTool: (() -> Unit)? = null,
    externalDragType: KitchenElementType? = null,
    externalDragRootOffset: Offset? = null,
    onExternalDragWorldUpdate: ((Float, Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val room = state.project.room
    val elements = state.project.elements.filter { it.isVisible && it.layer !in state.hiddenLayers }
    val selectedIds = state.selectedElementIds
    val undoRedo = state.undoRedoState

    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var viewportOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    var canvasSizePx by remember { mutableStateOf(Offset(1080f, 1920f)) }

    // Precision snapping step in millimeters (defaults to strict 1mm CAD precision)
    var snapGridStepMm by remember { mutableFloatStateOf(1.0f) }

    // Toggle for showing real-time dimension labels on all cabinets vs active cabinet
    var showAllCabinetDimensions2D by remember { mutableStateOf(true) }

    // Expandable Command History Stack drawer in 2D viewport
    var showCommandHistoryPanel by remember { mutableStateOf(false) }

    // Interactive Virtual Ruler (المسطرة الافتراضية الذكية) with 2 draggable pins A & B in world mm
    var isVirtualRulerActive by remember { mutableStateOf(false) }
    var rulerPinAMm by remember(room.widthMm, room.depthMm) {
        mutableStateOf(Offset(room.widthMm * 0.25f, room.depthMm * 0.45f))
    }
    var rulerPinBMm by remember(room.widthMm, room.depthMm) {
        mutableStateOf(Offset(room.widthMm * 0.75f, room.depthMm * 0.45f))
    }
    var activeDraggedRulerPin by remember { mutableStateOf<Char?>(null) }

    // Track active drag or resize state for committing undo command on release and sub-millimeter quantization
    var dragStartPos by remember { mutableStateOf<Vector3?>(null) }
    var dragStartRot by remember { mutableFloatStateOf(0f) }
    var dragStartDims by remember { mutableStateOf<Vector3?>(null) }
    var draggingElemId by remember { mutableStateOf<String?>(null) }
    var isDraggingFurniture by remember { mutableStateOf(false) }
    var isResizingFurniture by remember { mutableStateOf(false) }
    var activeResizeHandle by remember { mutableStateOf(ResizeHandle2D.NONE) }
    var subMmAccumX by remember { mutableFloatStateOf(0f) }
    var subMmAccumZ by remember { mutableFloatStateOf(0f) }

    // Keep latest references for gesture lambdas
    val latestElements by rememberUpdatedState(elements)
    val latestSelectedIds by rememberUpdatedState(selectedIds)
    val latestRoom by rememberUpdatedState(room)
    val latestZoomScale by rememberUpdatedState(zoomScale)
    val latestPanOffset by rememberUpdatedState(panOffset)
    val latestSnapStepMm by rememberUpdatedState(snapGridStepMm)

    // Update live 2D ghost position when dragging an element directly from a menu into the 2D floorplan
    var externalGhostWorldPos2D by remember { mutableStateOf<Vector3?>(null) }
    LaunchedEffect(externalDragType, externalDragRootOffset, viewportOriginInRoot, canvasSizePx, zoomScale, panOffset, room) {
        if (externalDragType != null && externalDragRootOffset != null) {
            val localPt = externalDragRootOffset - viewportOriginInRoot
            val cw = canvasSizePx.x
            val ch = canvasSizePx.y
            if (cw > 1f && ch > 1f && localPt.x in -30f..(cw + 30f) && localPt.y in -30f..(ch + 30f)) {
                val marginPx = 60f
                val availableW = (cw - marginPx * 2f).coerceAtLeast(1f)
                val availableH = (ch - marginPx * 2f).coerceAtLeast(1f)
                val baseScale = min(availableW / room.widthMm, availableH / room.depthMm) * zoomScale
                val originX = cw * 0.5f - (room.widthMm * 0.5f * baseScale) + panOffset.x
                val originY = ch * 0.5f - (room.depthMm * 0.5f * baseScale) + panOffset.y
                val worldXMm = ((localPt.x - originX) / baseScale).coerceIn(0f, room.widthMm)
                val worldZMm = ((localPt.y - originY) / baseScale).coerceIn(0f, room.depthMm)
                val hit = Vector3(worldXMm, externalDragType.defaultElevationY, worldZMm)
                externalGhostWorldPos2D = hit
                onExternalDragWorldUpdate?.invoke(worldXMm, worldZMm)
            }
        } else {
            externalGhostWorldPos2D = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                viewportOriginInRoot = coords.positionInRoot()
                val newW = coords.size.width.toFloat()
                val newH = coords.size.height.toFloat()
                if (newW > 1f && newH > 1f) {
                    canvasSizePx = Offset(newW, newH)
                }
            }
            .testTag("viewport_2d_canvas_container")
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (!isDraggingFurniture && !isResizingFurniture) {
                        if (abs(zoom - 1f) > 0.006f) {
                            zoomScale = (zoomScale * zoom).coerceIn(0.5f, 6f)
                        } else if (latestSelectedIds.isEmpty()) {
                            panOffset += pan
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        val canvasW = size.width.toFloat()
                        val canvasH = size.height.toFloat()
                        val marginPx = 60f
                        val availableW = (canvasW - marginPx * 2f).coerceAtLeast(1f)
                        val availableH = (canvasH - marginPx * 2f).coerceAtLeast(1f)
                        val baseScale = min(availableW / latestRoom.widthMm, availableH / latestRoom.depthMm) * latestZoomScale
                        val originX = canvasW * 0.5f - (latestRoom.widthMm * 0.5f * baseScale) + latestPanOffset.x
                        val originY = canvasH * 0.5f - (latestRoom.depthMm * 0.5f * baseScale) + latestPanOffset.y

                        fun worldToScreen(xMm: Float, zMm: Float): Offset =
                            Offset(originX + xMm * baseScale, originY + zMm * baseScale)

                        // 0. Hit-test Virtual Ruler Pins A & B first when Virtual Ruler is active
                        if (isVirtualRulerActive) {
                            val pinAScreen = worldToScreen(rulerPinAMm.x, rulerPinAMm.y)
                            val pinBScreen = worldToScreen(rulerPinBMm.x, rulerPinBMm.y)
                            val pinHitRadius = 42f
                            when {
                                (startOffset - pinAScreen).getDistance() <= pinHitRadius -> {
                                    activeDraggedRulerPin = 'A'
                                    return@detectDragGestures
                                }
                                (startOffset - pinBScreen).getDistance() <= pinHitRadius -> {
                                    activeDraggedRulerPin = 'B'
                                    return@detectDragGestures
                                }
                            }
                        }
                        activeDraggedRulerPin = null

                        // 1. First hit-test 2D Resize Handles on currently selected element
                        val currentSelected = latestElements.find { it.id in latestSelectedIds }
                        if (currentSelected != null && !currentSelected.isLocked) {
                            val aabb = currentSelected.getAABB()
                            val midX = (aabb.min.x + aabb.max.x) * 0.5f
                            val midZ = (aabb.min.z + aabb.max.z) * 0.5f
                            val eastHandlePt = worldToScreen(aabb.max.x, midZ)
                            val westHandlePt = worldToScreen(aabb.min.x, midZ)
                            val southHandlePt = worldToScreen(midX, aabb.max.z)
                            val northHandlePt = worldToScreen(midX, aabb.min.z)
                            val cornerSePt = worldToScreen(aabb.max.x, aabb.max.z)
                            val handleHitRadiusPx = 30f

                            val hitResizeHandle = when {
                                (startOffset - cornerSePt).getDistance() <= handleHitRadiusPx -> ResizeHandle2D.CORNER_SE
                                (startOffset - eastHandlePt).getDistance() <= handleHitRadiusPx -> ResizeHandle2D.EAST_WIDTH
                                (startOffset - westHandlePt).getDistance() <= handleHitRadiusPx -> ResizeHandle2D.WEST_WIDTH
                                (startOffset - southHandlePt).getDistance() <= handleHitRadiusPx -> ResizeHandle2D.SOUTH_DEPTH
                                (startOffset - northHandlePt).getDistance() <= handleHitRadiusPx -> ResizeHandle2D.NORTH_DEPTH
                                else -> ResizeHandle2D.NONE
                            }

                            if (hitResizeHandle != ResizeHandle2D.NONE) {
                                activeResizeHandle = hitResizeHandle
                                isResizingFurniture = true
                                isDraggingFurniture = false
                                dragStartPos = currentSelected.position
                                dragStartRot = currentSelected.rotationY
                                dragStartDims = currentSelected.dimensions
                                draggingElemId = currentSelected.id
                                subMmAccumX = 0f
                                subMmAccumZ = 0f
                                return@detectDragGestures
                            }
                        }

                        val worldXMm = (startOffset.x - originX) / baseScale
                        val worldZMm = (startOffset.y - originY) / baseScale
                        val touchToleranceMm = (18f / baseScale.coerceAtLeast(0.001f)).coerceIn(20f, 120f)

                        // 2. Hit-test furniture under drag start pointer (prioritize currently selected or top-most element)
                        val hitElem = latestElements.lastOrNull { elem ->
                            val aabb = elem.getAABB()
                            worldXMm in (aabb.min.x - touchToleranceMm)..(aabb.max.x + touchToleranceMm) &&
                                worldZMm in (aabb.min.z - touchToleranceMm)..(aabb.max.z + touchToleranceMm)
                        }

                        if (hitElem != null && !hitElem.isLocked) {
                            if (hitElem.id !in latestSelectedIds) {
                                onSelectElement(hitElem.id)
                            }
                            dragStartPos = hitElem.position
                            dragStartRot = hitElem.rotationY
                            dragStartDims = hitElem.dimensions
                            draggingElemId = hitElem.id
                            isDraggingFurniture = true
                            isResizingFurniture = false
                            activeResizeHandle = ResizeHandle2D.NONE
                            subMmAccumX = 0f
                            subMmAccumZ = 0f
                        } else {
                            isDraggingFurniture = false
                            isResizingFurniture = false
                            activeResizeHandle = ResizeHandle2D.NONE
                        }
                    },
                    onDragEnd = {
                        activeDraggedRulerPin = null
                        val elemId = draggingElemId
                        val startPos = dragStartPos
                        val startRot = dragStartRot
                        val startDims = dragStartDims
                        val currentElem = latestElements.find { it.id == elemId }
                        if (elemId != null && startPos != null && currentElem != null) {
                            onCommitTransform(
                                elemId,
                                startPos,
                                startRot,
                                startDims ?: currentElem.dimensions
                            )
                        }
                        dragStartPos = null
                        dragStartDims = null
                        draggingElemId = null
                        isDraggingFurniture = false
                        isResizingFurniture = false
                        activeResizeHandle = ResizeHandle2D.NONE
                        subMmAccumX = 0f
                        subMmAccumZ = 0f
                    },
                    onDragCancel = {
                        activeDraggedRulerPin = null
                        val elemId = draggingElemId
                        val startPos = dragStartPos
                        val startRot = dragStartRot
                        val startDims = dragStartDims
                        val currentElem = latestElements.find { it.id == elemId }
                        if (elemId != null && startPos != null && currentElem != null) {
                            onCommitTransform(
                                elemId,
                                startPos,
                                startRot,
                                startDims ?: currentElem.dimensions
                            )
                        }
                        dragStartPos = null
                        dragStartDims = null
                        draggingElemId = null
                        isDraggingFurniture = false
                        isResizingFurniture = false
                        activeResizeHandle = ResizeHandle2D.NONE
                        subMmAccumX = 0f
                        subMmAccumZ = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (isVirtualRulerActive && activeDraggedRulerPin != null) {
                            val canvasW = size.width.toFloat()
                            val canvasH = size.height.toFloat()
                            val marginPx = 60f
                            val availableW = (canvasW - marginPx * 2f).coerceAtLeast(1f)
                            val availableH = (canvasH - marginPx * 2f).coerceAtLeast(1f)
                            val baseScale = min(availableW / latestRoom.widthMm, availableH / latestRoom.depthMm) * latestZoomScale
                            val mmPerPixel = 1f / baseScale.coerceAtLeast(0.0001f)
                            val dxMm = dragAmount.x * mmPerPixel
                            val dzMm = dragAmount.y * mmPerPixel
                            if (activeDraggedRulerPin == 'A') {
                                rulerPinAMm = Offset(
                                    (rulerPinAMm.x + dxMm).coerceIn(0f, latestRoom.widthMm),
                                    (rulerPinAMm.y + dzMm).coerceIn(0f, latestRoom.depthMm)
                                )
                            } else if (activeDraggedRulerPin == 'B') {
                                rulerPinBMm = Offset(
                                    (rulerPinBMm.x + dxMm).coerceIn(0f, latestRoom.widthMm),
                                    (rulerPinBMm.y + dzMm).coerceIn(0f, latestRoom.depthMm)
                                )
                            }
                            return@detectDragGestures
                        }
                        if ((isDraggingFurniture || isResizingFurniture) && draggingElemId != null) {
                            val canvasW = size.width.toFloat()
                            val canvasH = size.height.toFloat()
                            val marginPx = 60f
                            val availableW = (canvasW - marginPx * 2f).coerceAtLeast(1f)
                            val availableH = (canvasH - marginPx * 2f).coerceAtLeast(1f)
                            val baseScale = min(availableW / latestRoom.widthMm, availableH / latestRoom.depthMm) * latestZoomScale

                            // Exact screen-pixel to world-millimeter conversion (cumulative from dragStartPos/dragStartDims)
                            val mmPerPixel = 1f / baseScale.coerceAtLeast(0.0001f)
                            subMmAccumX += dragAmount.x * mmPerPixel
                            subMmAccumZ += dragAmount.y * mmPerPixel

                            val stepMm = latestSnapStepMm.coerceAtLeast(1.0f)
                            val snappedTotalDeltaX = round(subMmAccumX / stepMm) * stepMm
                            val snappedTotalDeltaZ = round(subMmAccumZ / stepMm) * stepMm

                            if (isResizingFurniture) {
                                val targetElem = latestElements.find { it.id == draggingElemId }
                                val initialDims = dragStartDims ?: targetElem?.dimensions
                                if (targetElem != null && initialDims != null) {
                                    val isRot90 = targetElem.rotationY == 90f || targetElem.rotationY == 270f
                                    val minAllowedW = if (targetElem.type.isFillerStrip) 10f else 100f

                                    val screenHorizDelta = when (activeResizeHandle) {
                                        ResizeHandle2D.EAST_WIDTH, ResizeHandle2D.CORNER_SE -> snappedTotalDeltaX
                                        ResizeHandle2D.WEST_WIDTH -> -snappedTotalDeltaX
                                        else -> 0f
                                    }
                                    val screenVertDelta = when (activeResizeHandle) {
                                        ResizeHandle2D.SOUTH_DEPTH, ResizeHandle2D.CORNER_SE -> snappedTotalDeltaZ
                                        ResizeHandle2D.NORTH_DEPTH -> -snappedTotalDeltaZ
                                        else -> 0f
                                    }

                                    val deltaWidthMm = if (isRot90) screenVertDelta else screenHorizDelta
                                    val deltaDepthMm = if (isRot90) screenHorizDelta else screenVertDelta

                                    val newW = round((initialDims.x + deltaWidthMm).coerceIn(minAllowedW, 3600f) / stepMm) * stepMm
                                    val newD = round((initialDims.z + deltaDepthMm).coerceIn(100f, 2400f) / stepMm) * stepMm
                                    onResizeSelected(targetElem.id, newW, initialDims.y, newD)
                                }
                            } else {
                                val startPos = dragStartPos
                                val elemId = draggingElemId
                                if (onMoveElementToPosition != null && startPos != null && elemId != null) {
                                    val rawTarget = Vector3(
                                        x = startPos.x + snappedTotalDeltaX,
                                        y = startPos.y,
                                        z = startPos.z + snappedTotalDeltaZ
                                    )
                                    onMoveElementToPosition(elemId, rawTarget, stepMm)
                                } else if (abs(snappedTotalDeltaX) >= stepMm || abs(snappedTotalDeltaZ) >= stepMm) {
                                    subMmAccumX -= snappedTotalDeltaX
                                    subMmAccumZ -= snappedTotalDeltaZ
                                    onMoveSelected(snappedTotalDeltaX, 0f, snappedTotalDeltaZ)
                                }
                            }
                        } else {
                            // Pan the 2D canvas when dragging empty floor space
                            panOffset += dragAmount
                        }
                    }
                )
            }
            .pointerInput(state.isFurniturePlacementMode) {
                detectTapGestures { tapPos ->
                    val canvasW = size.width.toFloat()
                    val canvasH = size.height.toFloat()
                    val marginPx = 60f
                    val availableW = (canvasW - marginPx * 2f).coerceAtLeast(1f)
                    val availableH = (canvasH - marginPx * 2f).coerceAtLeast(1f)
                    val baseScale = min(availableW / latestRoom.widthMm, availableH / latestRoom.depthMm) * latestZoomScale
                    val originX = canvasW * 0.5f - (latestRoom.widthMm * 0.5f * baseScale) + latestPanOffset.x
                    val originY = canvasH * 0.5f - (latestRoom.depthMm * 0.5f * baseScale) + latestPanOffset.y

                    val worldXMm = round((tapPos.x - originX) / baseScale)
                    val worldZMm = round((tapPos.y - originY) / baseScale)

                    if (state.isFurniturePlacementMode) {
                        if (worldXMm in 0f..latestRoom.widthMm && worldZMm in 0f..latestRoom.depthMm) {
                            onPlaceFurnitureAt(worldXMm, worldZMm)
                        }
                    } else {
                        val tappedElem = latestElements.lastOrNull { elem ->
                            val aabb = elem.getAABB()
                            worldXMm in aabb.min.x..aabb.max.x && worldZMm in aabb.min.z..aabb.max.z
                        }
                        if (tappedElem != null) {
                            onSelectElement(tappedElem.id)
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            // Calculate auto-fit scale: room bounds to screen canvas
            val marginPx = 60f
            val availableW = canvasW - marginPx * 2f
            val availableH = canvasH - marginPx * 2f

            val baseScale = min(availableW / room.widthMm, availableH / room.depthMm) * zoomScale
            val originX = canvasW * 0.5f - (room.widthMm * 0.5f * baseScale) + panOffset.x
            val originY = canvasH * 0.5f - (room.depthMm * 0.5f * baseScale) + panOffset.y

            fun worldMmToScreen(xMm: Float, zMm: Float): Offset {
                return Offset(originX + xMm * baseScale, originY + zMm * baseScale)
            }

            // 1. Draw Multi-Resolution Architectural CAD Background Grid (500mm / 100mm / 50mm)
            val minorStepMm = if (zoomScale >= 2.2f) 50f else 100f
            var minorX = 0f
            while (minorX <= room.widthMm) {
                val topPt = worldMmToScreen(minorX, 0f)
                val botPt = worldMmToScreen(minorX, room.depthMm)
                drawLine(Color(0x0D94A3B8), topPt, botPt, strokeWidth = 0.8f)
                minorX += minorStepMm
            }
            var minorZ = 0f
            while (minorZ <= room.depthMm) {
                val leftPt = worldMmToScreen(0f, minorZ)
                val rightPt = worldMmToScreen(room.widthMm, minorZ)
                drawLine(Color(0x0D94A3B8), leftPt, rightPt, strokeWidth = 0.8f)
                minorZ += minorStepMm
            }

            val gridStepMm = 500f // 500mm major CAD grid
            var curX = 0f
            while (curX <= room.widthMm) {
                val topPt = worldMmToScreen(curX, 0f)
                val botPt = worldMmToScreen(curX, room.depthMm)
                drawLine(Color(0x2694A3B8), topPt, botPt, strokeWidth = 1.2f)
                curX += gridStepMm
            }
            var curZ = 0f
            while (curZ <= room.depthMm) {
                val leftPt = worldMmToScreen(0f, curZ)
                val rightPt = worldMmToScreen(room.widthMm, curZ)
                drawLine(Color(0x2694A3B8), leftPt, rightPt, strokeWidth = 1.2f)
                curZ += gridStepMm
            }

            // 2. Draw Floor Surface
            val floorTopLeft = worldMmToScreen(0f, 0f)
            val floorBottomRight = worldMmToScreen(room.widthMm, room.depthMm)
            drawRect(
                color = Color(room.floorMaterial.colorHex).copy(alpha = 0.25f),
                topLeft = floorTopLeft,
                size = Size(floorBottomRight.x - floorTopLeft.x, floorBottomRight.y - floorTopLeft.y)
            )

            // 3. Draw Room Walls with Thickness & Dynamic Openings
            val wallThickPx = room.wallThicknessMm * baseScale
            val wallPaintColor = Color(0xFF1E293B)

            // North Wall
            drawRect(
                color = wallPaintColor,
                topLeft = Offset(floorTopLeft.x - wallThickPx, floorTopLeft.y - wallThickPx),
                size = Size((room.widthMm + room.wallThicknessMm * 2) * baseScale, wallThickPx)
            )
            // South Wall
            drawRect(
                color = wallPaintColor,
                topLeft = Offset(floorTopLeft.x - wallThickPx, floorBottomRight.y),
                size = Size((room.widthMm + room.wallThicknessMm * 2) * baseScale, wallThickPx)
            )
            // West Wall
            drawRect(
                color = wallPaintColor,
                topLeft = Offset(floorTopLeft.x - wallThickPx, floorTopLeft.y),
                size = Size(wallThickPx, (room.depthMm) * baseScale)
            )
            // East Wall
            drawRect(
                color = wallPaintColor,
                topLeft = Offset(floorBottomRight.x, floorTopLeft.y),
                size = Size(wallThickPx, (room.depthMm) * baseScale)
            )

            // Draw Dynamic Wall Openings (Doors with Swing Arc, Windows with Double Line)
            room.walls.forEach { wall ->
                wall.openings.forEach { opening ->
                    when (wall.side) {
                        WallSide.NORTH -> {
                            val startPt = worldMmToScreen(opening.offsetMm, 0f)
                            val wPx = opening.widthMm * baseScale
                            drawRect(
                                color = Color(0xFF0F172A),
                                topLeft = Offset(startPt.x, startPt.y - wallThickPx),
                                size = Size(wPx, wallThickPx)
                            )
                            if (opening.type == OpeningType.DOOR) {
                                drawArc(
                                    color = Color(0xFFF59E0B),
                                    startAngle = 0f,
                                    sweepAngle = 90f,
                                    useCenter = true,
                                    topLeft = Offset(startPt.x, startPt.y),
                                    size = Size(wPx * 2f, wPx * 2f),
                                    style = Stroke(width = 1.5f)
                                )
                            } else {
                                drawLine(
                                    Color(0xFF38BDF8),
                                    Offset(startPt.x, startPt.y - wallThickPx * 0.5f),
                                    Offset(startPt.x + wPx, startPt.y - wallThickPx * 0.5f),
                                    strokeWidth = 2f
                                )
                            }
                        }
                        WallSide.SOUTH -> {
                            val startPt = worldMmToScreen(opening.offsetMm, room.depthMm)
                            val wPx = opening.widthMm * baseScale
                            drawRect(
                                color = Color(0xFF0F172A),
                                topLeft = Offset(startPt.x, startPt.y),
                                size = Size(wPx, wallThickPx)
                            )
                            if (opening.type == OpeningType.DOOR) {
                                drawArc(
                                    color = Color(0xFFF59E0B),
                                    startAngle = 180f,
                                    sweepAngle = 90f,
                                    useCenter = true,
                                    topLeft = Offset(startPt.x - wPx, startPt.y - wPx),
                                    size = Size(wPx * 2f, wPx * 2f),
                                    style = Stroke(width = 1.5f)
                                )
                            }
                        }
                        else -> { /* East / West openings rendered similarly */ }
                    }
                }
            }

            // 4. Draw Millimeter Dimension Lines along Outer Walls
            val dimColor = Color(0xFF94A3B8)
            val dimNorthY = floorTopLeft.y - wallThickPx - 20f
            drawLine(dimColor, Offset(floorTopLeft.x, dimNorthY), Offset(floorBottomRight.x, dimNorthY), strokeWidth = 1.5f)
            drawLine(dimColor, Offset(floorTopLeft.x, dimNorthY - 8f), Offset(floorTopLeft.x, dimNorthY + 8f), strokeWidth = 1.5f)
            drawLine(dimColor, Offset(floorBottomRight.x, dimNorthY - 8f), Offset(floorBottomRight.x, dimNorthY + 8f), strokeWidth = 1.5f)

            val textPaint = Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 28f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            drawContext.canvas.nativeCanvas.drawText(
                "${MeasurementUnitSystem.formatLength(room.widthMm, state.unitSystem)} (${room.layoutShape.titleAr})",
                (floorTopLeft.x + floorBottomRight.x) * 0.5f,
                dimNorthY - 12f,
                textPaint
            )

            // West dimension line
            val dimWestX = floorTopLeft.x - wallThickPx - 20f
            drawLine(dimColor, Offset(dimWestX, floorTopLeft.y), Offset(dimWestX, floorBottomRight.y), strokeWidth = 1.5f)
            drawLine(dimColor, Offset(dimWestX - 8f, floorTopLeft.y), Offset(dimWestX + 8f, floorTopLeft.y), strokeWidth = 1.5f)
            drawLine(dimColor, Offset(dimWestX - 8f, floorBottomRight.y), Offset(dimWestX + 8f, floorBottomRight.y), strokeWidth = 1.5f)

            drawContext.canvas.nativeCanvas.drawText(
                MeasurementUnitSystem.formatLength(room.depthMm, state.unitSystem),
                dimWestX - 16f,
                (floorTopLeft.y + floorBottomRight.y) * 0.5f,
                textPaint
            )

            // 5. Draw Kitchen Elements Footprint (Top-Down CAD Rectangles + Red Collision/Clearance Indicator)
            val activeCollision = state.collisionResult
            val criticalClearanceElementIds = state.validationReport.issues
                .filter {
                    it.code == com.example.core.geometry.ErgonomicRuleCode.INSUFFICIENT_DOOR_CLEARANCE ||
                        it.code == com.example.core.geometry.ErgonomicRuleCode.CORNER_DOOR_INTERFERENCE ||
                        it.code == com.example.core.geometry.ErgonomicRuleCode.SINK_TOO_CLOSE_TO_STOVE ||
                        it.code == com.example.core.geometry.ErgonomicRuleCode.ELEMENT_OVERLAP_COLLISION
                }
                .flatMap { it.affectedElementIds }
                .toSet() +
                state.validationReport.doorClearanceZones
                    .filter { it.isInsufficient }
                    .map { it.elementId }
                    .toSet()

            elements.forEach { elem ->
                val isSelected = elem.id in selectedIds
                val isDirectlyColliding = (isSelected && activeCollision?.hasCollision == true) ||
                    elem.id in (activeCollision?.collidingElementIds ?: emptyList()) ||
                    elem.id in criticalClearanceElementIds
                val affectedByIssue = state.validationReport.issues.any { elem.id in it.affectedElementIds }
                val corners = elem.getFloorCorners()
                val screenCorners = corners.map { worldMmToScreen(it.x, it.z) }

                val elemPath = Path().apply {
                    moveTo(screenCorners[0].x, screenCorners[0].y)
                    for (i in 1 until screenCorners.size) {
                        lineTo(screenCorners[i].x, screenCorners[i].y)
                    }
                    close()
                }

                val fillColor = if (isDirectlyColliding) {
                    Color(0x88EF4444) // Vivid crimson red fill when overlapping or violating ergonomic clearance
                } else {
                    Color(elem.material.colorHex).copy(alpha = if (isSelected) 0.95f else 0.85f)
                }
                drawPath(elemPath, color = fillColor)

                // Draw red diagonal hazard hatching lines + outer Red Bounding Box around colliding / clearance-violating cabinets
                if (isDirectlyColliding && screenCorners.size >= 4) {
                    val minX = screenCorners.minOf { it.x }
                    val maxX = screenCorners.maxOf { it.x }
                    val minY = screenCorners.minOf { it.y }
                    val maxY = screenCorners.maxOf { it.y }
                    val hatchStep = 14f
                    var hx = minX
                    while (hx <= maxX) {
                        drawLine(
                            color = Color(0xCCFCA5A5),
                            start = Offset(hx, minY),
                            end = Offset((hx - (maxY - minY) * 0.45f).coerceIn(minX, maxX), maxY),
                            strokeWidth = 2.0f
                        )
                        hx += hatchStep
                    }
                    // Outer Red Bounding Box (مربع أحمر حول أي خزانة تتقاطع أو تنتهك المسافة الدنيا)
                    val padPx = 6f
                    drawRect(
                        color = Color(0xFFEF4444),
                        topLeft = Offset(minX - padPx, minY - padPx),
                        size = Size((maxX - minX) + padPx * 2f, (maxY - minY) + padPx * 2f),
                        style = Stroke(
                            width = 3.2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f)
                        )
                    )
                }

                val strokeColor = when {
                    isDirectlyColliding -> Color(0xFFEF4444) // Bright Red collision / ergonomic clearance violation highlight
                    isSelected -> Color(0xFF06B6D4) // Bright Cyan selection
                    affectedByIssue -> Color(0xFFF59E0B) // Amber ergonomic warning highlight
                    else -> Color(0xFF334155)
                }
                drawPath(
                    elemPath,
                    color = strokeColor,
                    style = Stroke(width = if (isDirectlyColliding) 4.8f else if (isSelected || affectedByIssue) 3.5f else 1.5f)
                )

                // Front Edge Highlight (Indicator of cabinet opening direction)
                val frontP0 = screenCorners[2]
                val frontP1 = screenCorners[3]
                drawLine(if (isDirectlyColliding) Color(0xFFFCA5A5) else Color(0xFFF59E0B), frontP0, frontP1, strokeWidth = 3.2f)

                when (elem.type.category) {
                    ElementCategory.APPLIANCE -> {
                        val centerPt = worldMmToScreen(elem.position.x, elem.position.z)
                        drawCircle(Color(0x55000000), radius = 18f * baseScale * 0.5f, center = centerPt)
                    }
                    else -> {}
                }

                if (isSelected || isDirectlyColliding || showAllCabinetDimensions2D) {
                    val centerPt = worldMmToScreen(elem.position.x, elem.position.z)

                    val labelPaint = Paint().apply {
                        color = when {
                            isDirectlyColliding -> android.graphics.Color.rgb(254, 202, 202)
                            isSelected -> android.graphics.Color.rgb(165, 243, 252)
                            else -> android.graphics.Color.rgb(226, 232, 240)
                        }
                        textSize = if (isSelected || isDirectlyColliding) 22f else 17f
                        textAlign = Paint.Align.CENTER
                        isAntiAlias = true
                        isFakeBoldText = isSelected || isDirectlyColliding
                    }

                    val labelText = when {
                        isDirectlyColliding ->
                            "⛔ تصادم / انتهاك خلوص (${elem.dimensions.x.roundToInt()}×${elem.dimensions.z.roundToInt()} مم)"
                        isSelected ->
                            "${elem.name} (${elem.dimensions.x.roundToInt()}×${elem.dimensions.z.roundToInt()} مم)"
                        else ->
                            "${elem.dimensions.x.roundToInt()}×${elem.dimensions.z.roundToInt()}"
                    }

                    drawContext.canvas.nativeCanvas.drawText(
                        labelText,
                        centerPt.x,
                        centerPt.y + 6f,
                        labelPaint
                    )
                }
            }

            // 6. Draw Real-Time Ergonomic Work Triangle & Door Clearance Zones
            if (state.showErgonomicOverlay) {
                val report = state.validationReport

                report.doorClearanceZones.forEach { zone ->
                    val shouldRenderZone = zone.isInsufficient || zone.elementId in selectedIds
                    if (shouldRenderZone) {
                        val startPt = worldMmToScreen(zone.frontOriginMm.x, zone.frontOriginMm.z)
                        val displayDepthMm = if (zone.isInsufficient) {
                            zone.availableClearanceMm.coerceIn(180f, zone.recommendedWalkwayMm)
                        } else {
                            zone.requiredDoorSwingMm
                        }
                        val endWorld = zone.frontOriginMm + (zone.frontNormal * displayDepthMm)
                        val endPt = worldMmToScreen(endWorld.x, endWorld.z)

                        val zoneColor = when {
                            zone.isCriticalSwingBlocked -> Color(0xFFEF4444)
                            zone.isInsufficient -> Color(0xFFF59E0B)
                            else -> Color(0xFF10B981)
                        }

                        drawLine(
                            color = zoneColor.copy(alpha = 0.85f),
                            start = startPt,
                            end = endPt,
                            strokeWidth = 3f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                        )
                        drawCircle(color = zoneColor, radius = 5f, center = endPt)

                        // Draw 2D Door-Swing Clearance Arc on cabinet front
                        val arcRadiusPx = (displayDepthMm * baseScale).coerceAtLeast(14f)
                        val normalAngleDeg = Math.toDegrees(atan2(zone.frontNormal.z.toDouble(), zone.frontNormal.x.toDouble())).toFloat()
                        drawArc(
                            color = zoneColor.copy(alpha = 0.22f),
                            startAngle = normalAngleDeg - 45f,
                            sweepAngle = 90f,
                            useCenter = true,
                            topLeft = Offset(startPt.x - arcRadiusPx, startPt.y - arcRadiusPx),
                            size = Size(arcRadiusPx * 2f, arcRadiusPx * 2f)
                        )
                        drawArc(
                            color = zoneColor.copy(alpha = 0.75f),
                            startAngle = normalAngleDeg - 45f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(startPt.x - arcRadiusPx, startPt.y - arcRadiusPx),
                            size = Size(arcRadiusPx * 2f, arcRadiusPx * 2f),
                            style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f))
                        )

                        if (zone.isInsufficient) {
                            val midPt = Offset((startPt.x + endPt.x) * 0.5f, (startPt.y + endPt.y) * 0.5f)
                            val clearancePaint = Paint().apply {
                                color = if (zone.isCriticalSwingBlocked) {
                                    android.graphics.Color.rgb(248, 113, 113)
                                } else {
                                    android.graphics.Color.rgb(251, 191, 36)
                                }
                                textSize = 21f
                                textAlign = Paint.Align.CENTER
                                isAntiAlias = true
                                isFakeBoldText = true
                            }
                            drawContext.canvas.nativeCanvas.drawText(
                                "⚠ خلوص فتح الباب ${MeasurementUnitSystem.formatLength(zone.availableClearanceMm, state.unitSystem)}",
                                midPt.x,
                                midPt.y - 6f,
                                clearancePaint
                            )
                        }
                    }
                }

                report.workTriangle?.let { tri ->
                    val sinkPt = worldMmToScreen(tri.sinkPositionMm.x, tri.sinkPositionMm.z)
                    val stovePt = worldMmToScreen(tri.stovePositionMm.x, tri.stovePositionMm.z)
                    val sinkStoveColor = if (tri.isSinkStoveOptimal) Color(0xFF34D399) else Color(0xFFF87171)

                    drawLine(
                        color = sinkStoveColor,
                        start = sinkPt,
                        end = stovePt,
                        strokeWidth = 3.2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                    )

                    tri.fridgePositionMm?.let { fridgePos ->
                        val fridgePt = worldMmToScreen(fridgePos.x, fridgePos.z)
                        val legColor = if (tri.isTriangleOptimal) Color(0xFF38BDF8) else Color(0xFFFBBF24)
                        drawLine(
                            color = legColor.copy(alpha = 0.75f),
                            start = sinkPt,
                            end = fridgePt,
                            strokeWidth = 2.4f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                        )
                        drawLine(
                            color = legColor.copy(alpha = 0.75f),
                            start = stovePt,
                            end = fridgePt,
                            strokeWidth = 2.4f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                        )
                        drawCircle(color = legColor, radius = 6f, center = fridgePt)
                    }

                    drawCircle(color = sinkStoveColor, radius = 6.5f, center = sinkPt)
                    drawCircle(color = sinkStoveColor, radius = 6.5f, center = stovePt)

                    val midSinkStove = Offset((sinkPt.x + stovePt.x) * 0.5f, (sinkPt.y + stovePt.y) * 0.5f)
                    val triPaint = Paint().apply {
                        color = if (tri.isSinkStoveOptimal) {
                            android.graphics.Color.rgb(52, 211, 153)
                        } else {
                            android.graphics.Color.rgb(248, 113, 113)
                        }
                        textSize = 22f
                        textAlign = Paint.Align.CENTER
                        isAntiAlias = true
                        isFakeBoldText = true
                    }
                    val statusMark = if (tri.isSinkStoveOptimal) "✓" else "⚠"
                    drawContext.canvas.nativeCanvas.drawText(
                        "الحوض↔الموقد: ${(tri.sinkToStoveCenterMm / 10f).toInt()} سم $statusMark",
                        midSinkStove.x,
                        midSinkStove.y - 10f,
                        triPaint
                    )
                }
            }

            // 7. Draw Active 1mm Magnet Snap Guidelines & Red Collision/Clearance Vectors
            state.activeSnapGuideLines.forEach { guide ->
                val s = worldMmToScreen(guide.start.x, guide.start.z)
                val e = worldMmToScreen(guide.end.x, guide.end.z)
                val guideColor = when {
                    guide.isCollisionWarning -> Color(0xFFEF4444)
                    guide.isWallGuide -> Color(0xFF10B981)
                    else -> Color(0xFF06B6D4)
                }

                drawLine(
                    color = guideColor,
                    start = s,
                    end = e,
                    strokeWidth = if (guide.isCollisionWarning) 4.2f else 3f,
                    pathEffect = if (guide.isCollisionWarning) PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f) else null
                )
                drawCircle(color = guideColor, radius = if (guide.isCollisionWarning) 7.5f else 6f, center = s)
                drawCircle(color = guideColor, radius = if (guide.isCollisionWarning) 7.5f else 6f, center = e)
            }

            // Helper to draw a high-contrast CAD Dimension Pill Badge on the 2D Canvas
            fun drawCadDimensionPill(
                center: Offset,
                text: String,
                bgColor: Color = Color(0xEB0F172A),
                borderColor: Color = Color(0xFF38BDF8),
                textColorInt: Int = android.graphics.Color.WHITE,
                textSizePx: Float = 20f
            ) {
                val pillPaint = Paint().apply {
                    color = textColorInt
                    textSize = textSizePx
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                    isFakeBoldText = true
                }
                val textWidth = pillPaint.measureText(text)
                val padH = 14f
                val padV = 8f
                val pillW = textWidth + padH * 2f
                val pillH = textSizePx + padV * 2f
                val topLeft = Offset(center.x - pillW * 0.5f, center.y - pillH * 0.5f)

                drawRoundRect(
                    color = bgColor,
                    topLeft = topLeft,
                    size = Size(pillW, pillH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                )
                drawRoundRect(
                    color = borderColor,
                    topLeft = topLeft,
                    size = Size(pillW, pillH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
                    style = Stroke(width = 1.8f)
                )
                drawContext.canvas.nativeCanvas.drawText(
                    text,
                    center.x,
                    center.y + textSizePx * 0.34f,
                    pillPaint
                )
            }

            // 8. Draw Real-Time Dimension Labels, 4-Way Wall Clearances, Neighbor Gaps & Interactive 2D Resize Handles
            state.singleSelectedElement?.let { sel ->
                val aabb = sel.getAABB()
                val elemCenterScreen = worldMmToScreen(sel.position.x, sel.position.z)
                val boxTopLeft = worldMmToScreen(aabb.min.x, aabb.min.z)
                val boxBottomRight = worldMmToScreen(aabb.max.x, aabb.max.z)
                val midXWorld = (aabb.min.x + aabb.max.x) * 0.5f
                val midZWorld = (aabb.min.z + aabb.max.z) * 0.5f

                // 8.1: 4-Way Real-Time Wall Distance Lines & Pill Labels (West, East, North, South)
                val westGapMm = aabb.min.x.coerceAtLeast(0f).roundToInt()
                val eastGapMm = (room.widthMm - aabb.max.x).coerceAtLeast(0f).roundToInt()
                val northGapMm = aabb.min.z.coerceAtLeast(0f).roundToInt()
                val southGapMm = (room.depthMm - aabb.max.z).coerceAtLeast(0f).roundToInt()

                val wallLineColor = when {
                    isResizingFurniture -> Color(0xFFF59E0B)
                    isDraggingFurniture -> Color(0xFF22D3EE)
                    else -> Color(0xAA38BDF8)
                }

                // West Wall Clearance Line & Label
                val westWallScreen = worldMmToScreen(0f, midZWorld)
                val elemWestScreen = worldMmToScreen(aabb.min.x, midZWorld)
                drawLine(
                    color = wallLineColor,
                    start = westWallScreen,
                    end = elemWestScreen,
                    strokeWidth = 2.0f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                )
                if (westGapMm > 20) {
                    drawCadDimensionPill(
                        center = Offset((westWallScreen.x + elemWestScreen.x) * 0.5f, elemWestScreen.y - 18f),
                        text = "◀ غرب: $westGapMm مم (${String.format(java.util.Locale.US, "%.1f", westGapMm / 10f)} سم)",
                        borderColor = Color(0xFF38BDF8),
                        textColorInt = android.graphics.Color.rgb(125, 211, 252),
                        textSizePx = 18f
                    )
                }

                // East Wall Clearance Line & Label
                val eastWallScreen = worldMmToScreen(room.widthMm, midZWorld)
                val elemEastScreen = worldMmToScreen(aabb.max.x, midZWorld)
                drawLine(
                    color = wallLineColor,
                    start = elemEastScreen,
                    end = eastWallScreen,
                    strokeWidth = 2.0f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                )
                if (eastGapMm > 20) {
                    drawCadDimensionPill(
                        center = Offset((elemEastScreen.x + eastWallScreen.x) * 0.5f, elemEastScreen.y + 20f),
                        text = "شرق: $eastGapMm مم (${String.format(java.util.Locale.US, "%.1f", eastGapMm / 10f)} سم) ▶",
                        borderColor = Color(0xFF38BDF8),
                        textColorInt = android.graphics.Color.rgb(125, 211, 252),
                        textSizePx = 18f
                    )
                }

                // North Wall Clearance Line & Label
                val northWallScreen = worldMmToScreen(midXWorld, 0f)
                val elemNorthScreen = worldMmToScreen(midXWorld, aabb.min.z)
                drawLine(
                    color = wallLineColor,
                    start = northWallScreen,
                    end = elemNorthScreen,
                    strokeWidth = 2.0f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                )
                if (northGapMm > 20) {
                    drawCadDimensionPill(
                        center = Offset(elemNorthScreen.x, (northWallScreen.y + elemNorthScreen.y) * 0.5f),
                        text = "▲ شمال: $northGapMm مم",
                        borderColor = Color(0xFF38BDF8),
                        textColorInt = android.graphics.Color.rgb(125, 211, 252),
                        textSizePx = 18f
                    )
                }

                // South Wall Clearance Line & Label
                val southWallScreen = worldMmToScreen(midXWorld, room.depthMm)
                val elemSouthScreen = worldMmToScreen(midXWorld, aabb.max.z)
                drawLine(
                    color = wallLineColor,
                    start = elemSouthScreen,
                    end = southWallScreen,
                    strokeWidth = 2.0f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                )
                if (southGapMm > 20) {
                    drawCadDimensionPill(
                        center = Offset(elemSouthScreen.x, (elemSouthScreen.y + southWallScreen.y) * 0.5f),
                        text = "▼ جنوب: $southGapMm مم",
                        borderColor = Color(0xFF38BDF8),
                        textColorInt = android.graphics.Color.rgb(125, 211, 252),
                        textSizePx = 18f
                    )
                }

                // 8.2: Nearest Neighbor Cabinet Gap Measurement Labels (Left / Right / Front / Back)
                val otherElements = elements.filter { it.id != sel.id && it.layer == sel.layer }
                // Nearest neighbor to the West (left) with overlapping Z span
                val nearestWestNeighbor = otherElements
                    .map { it to it.getAABB() }
                    .filter { (_, ob) -> ob.max.x <= aabb.min.x + 2f && ob.max.z >= aabb.min.z - 250f && ob.min.z <= aabb.max.z + 250f }
                    .maxByOrNull { (_, ob) -> ob.max.x }
                if (nearestWestNeighbor != null) {
                    val nbBox = nearestWestNeighbor.second
                    val gapX = (aabb.min.x - nbBox.max.x).coerceAtLeast(0f).roundToInt()
                    if (gapX in 1..1500) {
                        val pStart = worldMmToScreen(nbBox.max.x, midZWorld)
                        val pEnd = worldMmToScreen(aabb.min.x, midZWorld)
                        drawLine(Color(0xFF10B981), pStart, pEnd, strokeWidth = 2.8f)
                        drawCircle(Color(0xFF10B981), radius = 4.5f, center = pStart)
                        drawCircle(Color(0xFF10B981), radius = 4.5f, center = pEnd)
                        drawCadDimensionPill(
                            center = Offset((pStart.x + pEnd.x) * 0.5f, pStart.y - 22f),
                            text = "↔ فجوة: $gapX مم",
                            borderColor = Color(0xFF10B981),
                            textColorInt = android.graphics.Color.rgb(110, 231, 183),
                            textSizePx = 18f
                        )
                    }
                }
                // Nearest neighbor to the East (right) with overlapping Z span
                val nearestEastNeighbor = otherElements
                    .map { it to it.getAABB() }
                    .filter { (_, ob) -> ob.min.x >= aabb.max.x - 2f && ob.max.z >= aabb.min.z - 250f && ob.min.z <= aabb.max.z + 250f }
                    .minByOrNull { (_, ob) -> ob.min.x }
                if (nearestEastNeighbor != null) {
                    val nbBox = nearestEastNeighbor.second
                    val gapX = (nbBox.min.x - aabb.max.x).coerceAtLeast(0f).roundToInt()
                    if (gapX in 1..1500) {
                        val pStart = worldMmToScreen(aabb.max.x, midZWorld)
                        val pEnd = worldMmToScreen(nbBox.min.x, midZWorld)
                        drawLine(Color(0xFF10B981), pStart, pEnd, strokeWidth = 2.8f)
                        drawCircle(Color(0xFF10B981), radius = 4.5f, center = pStart)
                        drawCircle(Color(0xFF10B981), radius = 4.5f, center = pEnd)
                        drawCadDimensionPill(
                            center = Offset((pStart.x + pEnd.x) * 0.5f, pStart.y - 22f),
                            text = "↔ فجوة: $gapX مم",
                            borderColor = Color(0xFF10B981),
                            textColorInt = android.graphics.Color.rgb(110, 231, 183),
                            textSizePx = 18f
                        )
                    }
                }

                // 8.3: Real-Time Cabinet Width (W) & Depth (D) Architectural Dimension Callouts
                val widthMm = sel.dimensions.x.roundToInt()
                val heightMm = sel.dimensions.y.roundToInt()
                val depthMm = sel.dimensions.z.roundToInt()
                val startDimsVal = dragStartDims
                val deltaWMm = if (isResizingFurniture && startDimsVal != null) (sel.dimensions.x - startDimsVal.x).roundToInt() else 0
                val deltaDMm = if (isResizingFurniture && startDimsVal != null) (sel.dimensions.z - startDimsVal.z).roundToInt() else 0
                val startPosVal = dragStartPos
                val deltaXMm = if (isDraggingFurniture && startPosVal != null) (sel.position.x - startPosVal.x).roundToInt() else 0
                val deltaZMm = if (isDraggingFurniture && startPosVal != null) (sel.position.z - startPosVal.z).roundToInt() else 0

                // Top Width Dimension Line + Witness Ticks + Pill Label
                val widthLineY = boxTopLeft.y - 24f
                val wLineColor = if (isResizingFurniture) Color(0xFFF59E0B) else Color(0xFF34D399)
                drawLine(wLineColor, Offset(boxTopLeft.x, widthLineY), Offset(boxBottomRight.x, widthLineY), strokeWidth = 2.4f)
                drawLine(wLineColor, Offset(boxTopLeft.x, widthLineY - 7f), Offset(boxTopLeft.x, boxTopLeft.y), strokeWidth = 1.8f)
                drawLine(wLineColor, Offset(boxBottomRight.x, widthLineY - 7f), Offset(boxBottomRight.x, boxTopLeft.y), strokeWidth = 1.8f)

                val deltaWTag = if (deltaWMm != 0) " (${if (deltaWMm > 0) "+$deltaWMm" else "$deltaWMm"} مم)" else ""
                drawCadDimensionPill(
                    center = Offset((boxTopLeft.x + boxBottomRight.x) * 0.5f, widthLineY - 18f),
                    text = "العرض W: $widthMm مم (${String.format(java.util.Locale.US, "%.1f", widthMm / 10f)} سم)$deltaWTag",
                    bgColor = if (isResizingFurniture) Color(0xF2451A03) else Color(0xF2064E3B),
                    borderColor = wLineColor,
                    textColorInt = if (isResizingFurniture) android.graphics.Color.rgb(253, 230, 138) else android.graphics.Color.rgb(167, 243, 208),
                    textSizePx = 20f
                )

                // Right Depth Dimension Line + Witness Ticks + Pill Label
                val depthLineX = boxBottomRight.x + 24f
                val dLineColor = if (isResizingFurniture) Color(0xFFF59E0B) else Color(0xFF38BDF8)
                drawLine(dLineColor, Offset(depthLineX, boxTopLeft.y), Offset(depthLineX, boxBottomRight.y), strokeWidth = 2.4f)
                drawLine(dLineColor, Offset(boxBottomRight.x, boxTopLeft.y), Offset(depthLineX + 7f, boxTopLeft.y), strokeWidth = 1.8f)
                drawLine(dLineColor, Offset(boxBottomRight.x, boxBottomRight.y), Offset(depthLineX + 7f, boxBottomRight.y), strokeWidth = 1.8f)

                val deltaDTag = if (deltaDMm != 0) " (${if (deltaDMm > 0) "+$deltaDMm" else "$deltaDMm"})" else ""
                drawCadDimensionPill(
                    center = Offset(depthLineX + 78f, (boxTopLeft.y + boxBottomRight.y) * 0.5f),
                    text = "العمق D: $depthMm مم (${String.format(java.util.Locale.US, "%.1f", depthMm / 10f)} سم)$deltaDTag",
                    bgColor = if (isResizingFurniture) Color(0xF2451A03) else Color(0xF20C4A6E),
                    borderColor = dLineColor,
                    textColorInt = if (isResizingFurniture) android.graphics.Color.rgb(253, 230, 138) else android.graphics.Color.rgb(186, 230, 253),
                    textSizePx = 19f
                )

                // Bottom Position & Height Live Readout Pill
                val moveDeltaTag = if (isDraggingFurniture && (deltaXMm != 0 || deltaZMm != 0)) {
                    " • ΔX:${if (deltaXMm >= 0) "+$deltaXMm" else "$deltaXMm"} ΔZ:${if (deltaZMm >= 0) "+$deltaZMm" else "$deltaZMm"} مم"
                } else ""
                drawCadDimensionPill(
                    center = Offset(elemCenterScreen.x, boxBottomRight.y + 32f),
                    text = "H: $heightMm مم | X: ${sel.position.x.roundToInt()} مم • Z: ${sel.position.z.roundToInt()} مم$moveDeltaTag",
                    bgColor = Color(0xF20F172A),
                    borderColor = if (isDraggingFurniture) Color(0xFF22D3EE) else Color(0xFF64748B),
                    textColorInt = android.graphics.Color.rgb(226, 232, 240),
                    textSizePx = 19f
                )

                // 8.4: Draw 5 Interactive 2D Resize Handles on Selected Element (East, West, South, North, SE Corner)
                if (!sel.isLocked) {
                    val handles = listOf(
                        ResizeHandle2D.EAST_WIDTH to elemEastScreen,
                        ResizeHandle2D.WEST_WIDTH to elemWestScreen,
                        ResizeHandle2D.SOUTH_DEPTH to elemSouthScreen,
                        ResizeHandle2D.NORTH_DEPTH to elemNorthScreen,
                        ResizeHandle2D.CORNER_SE to boxBottomRight
                    )
                    handles.forEach { (hType, pt) ->
                        val isHandleActive = activeResizeHandle == hType
                        val handleRadius = if (hType == ResizeHandle2D.CORNER_SE) 11f else 9f
                        val handleFill = when {
                            isHandleActive -> Color(0xFFF59E0B)
                            hType == ResizeHandle2D.CORNER_SE -> Color(0xFF10B981)
                            else -> Color(0xFF06B6D4)
                        }
                        drawCircle(Color(0xFF0F172A), radius = handleRadius + 3f, center = pt)
                        drawCircle(handleFill, radius = handleRadius, center = pt)
                        drawCircle(Color.White, radius = 3.5f, center = pt)
                    }
                }
            }

            // 8B. Draw Live 2D Snapped Cabinet Ghost Footprint + Real-Time Dimension Labels when dragging from catalog
            externalGhostWorldPos2D?.let { rawGhost ->
                val dragType = externalDragType ?: state.activePlacementType
                val ghostDims = dragType.defaultDimensions
                val tempCandidate = KitchenElement(
                    id = "ghost_preview_2d",
                    name = dragType.title,
                    type = dragType,
                    position = rawGhost,
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

                val gTopLeft = worldMmToScreen(ghostPos.x - hw, ghostPos.z - hd)
                val gBottomRight = worldMmToScreen(ghostPos.x + hw, ghostPos.z + hd)
                val gSize = Size(
                    width = (gBottomRight.x - gTopLeft.x).coerceAtLeast(4f),
                    height = (gBottomRight.y - gTopLeft.y).coerceAtLeast(4f)
                )
                val gFill = if (isGhostColliding) Color(0x66EF4444) else Color(0x5510B981)
                val gStroke = if (isGhostColliding) Color(0xFFEF4444) else Color(0xFF10B981)

                drawRect(color = gFill, topLeft = gTopLeft, size = gSize)
                drawRect(color = gStroke, topLeft = gTopLeft, size = gSize, style = Stroke(width = 3.5f))

                drawCadDimensionPill(
                    center = Offset((gTopLeft.x + gBottomRight.x) * 0.5f, gTopLeft.y - 22f),
                    text = "${dragType.title} • W:${ghostDims.x.toInt()}×D:${ghostDims.z.toInt()}×H:${ghostDims.y.toInt()} مم",
                    bgColor = if (isGhostColliding) Color(0xEE7F1D1D) else Color(0xEE064E3B),
                    borderColor = gStroke,
                    textColorInt = if (isGhostColliding) android.graphics.Color.rgb(254, 202, 202) else android.graphics.Color.rgb(167, 243, 208),
                    textSizePx = 20f
                )
            }

            // 8C. Draw Interactive Virtual Ruler (المسطرة الافتراضية الذكية) with Draggable Pins A & B
            if (isVirtualRulerActive) {
                val pA = worldMmToScreen(rulerPinAMm.x, rulerPinAMm.y)
                val pB = worldMmToScreen(rulerPinBMm.x, rulerPinBMm.y)
                val dxMm = abs(rulerPinBMm.x - rulerPinAMm.x)
                val dzMm = abs(rulerPinBMm.y - rulerPinAMm.y)
                val distMm = hypot(dxMm, dzMm)

                // Dashed orthogonal ΔX / ΔZ helper lines
                val cornerPt = Offset(pB.x, pA.y)
                drawLine(
                    color = Color(0x88FBBF24),
                    start = pA,
                    end = cornerPt,
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                )
                drawLine(
                    color = Color(0x88FBBF24),
                    start = cornerPt,
                    end = pB,
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                )

                // Main ruler line with graduation ticks
                drawLine(
                    color = Color(0xFFF59E0B),
                    start = pA,
                    end = pB,
                    strokeWidth = 4f
                )
                drawCircle(color = Color(0xFF0F172A), radius = 14f, center = pA)
                drawCircle(color = Color(0xFFF59E0B), radius = 11f, center = pA)
                drawCircle(color = Color(0xFF0F172A), radius = 14f, center = pB)
                drawCircle(color = Color(0xFF10B981), radius = 11f, center = pB)

                val pinPaint = Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 16f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                    isFakeBoldText = true
                }
                drawContext.canvas.nativeCanvas.drawText("A", pA.x, pA.y + 5f, pinPaint)
                drawContext.canvas.nativeCanvas.drawText("B", pB.x, pB.y + 5f, pinPaint)

                val midRuler = Offset((pA.x + pB.x) * 0.5f, (pA.y + pB.y) * 0.5f - 24f)
                val metricStr = MeasurementUnitSystem.formatLength(distMm, MeasurementUnitSystem.METRIC, preferCmInMetric = true)
                val imperialStr = MeasurementUnitSystem.formatLength(distMm, MeasurementUnitSystem.IMPERIAL)
                drawCadDimensionPill(
                    center = midRuler,
                    text = "📏 المسطرة: $metricStr ($imperialStr) | ΔX:${dxMm.roundToInt()} ΔZ:${dzMm.roundToInt()} مم",
                    bgColor = Color(0xF5451A03),
                    borderColor = Color(0xFFFBBF24),
                    textColorInt = android.graphics.Color.rgb(254, 243, 199),
                    textSizePx = 20f
                )
            }
        }

        // 9. Top-Start Floating 2D CAD 1mm Precision, Undo/Redo Command Stack & Layout Controls HUD
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 76.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 9A. 1mm Precision Snapping & Micro-Nudge Row
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xD90F172A))
                    .border(1.dp, Color(0x4038BDF8), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("floorplan_2d_snap_hud"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "دقة السحب 2D:",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                listOf(1f, 5f, 10f, 50f).forEach { step ->
                    val isSelected = snapGridStepMm == step
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B))
                            .clickable { snapGridStepMm = step }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                            .testTag("snap_step_${step.toInt()}mm")
                    ) {
                        Text(
                            text = "${step.toInt()} مم",
                            color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                state.singleSelectedElement?.let { sel ->
                    Text(
                        text = "| X:${sel.position.x.roundToInt()} Z:${sel.position.z.roundToInt()} مم",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    // 1mm micro-nudge controls for exact 1mm placement (each recorded as a Command)
                    listOf(
                        "◀1مم" to Pair(-1f, 0f),
                        "▶1مم" to Pair(1f, 0f),
                        "▲1مم" to Pair(0f, -1f),
                        "▼1مم" to Pair(0f, 1f)
                    ).forEach { (label, delta) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(6.dp))
                                .clickable {
                                    val oldPos = sel.position
                                    val oldRot = sel.rotationY
                                    val oldDims = sel.dimensions
                                    onMoveSelected(delta.first, 0f, delta.second)
                                    onCommitTransform(sel.id, oldPos, oldRot, oldDims)
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .testTag("nudge_2d_${delta.first.toInt()}_${delta.second.toInt()}")
                        ) {
                            Text(
                                text = label,
                                color = Color(0xFFE2E8F0),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 9B. 2D Command Pattern Undo/Redo & Quick Layout Modification Bar
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xE60F172A))
                    .border(1.dp, Color(0x4038BDF8), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("floorplan_2d_undo_redo_hud"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Undo Button in 2D Floor Plan
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (undoRedo.canUndo) Color(0xFF0369A1) else Color(0xFF1E293B))
                        .clickable(enabled = undoRedo.canUndo) { onUndo() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("floorplan_2d_undo_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Undo,
                            contentDescription = "تراجع 2D",
                            tint = if (undoRedo.canUndo) Color.White else Color(0xFF64748B),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (undoRedo.undoCount > 0) "تراجع (${undoRedo.undoCount})" else "تراجع",
                            color = if (undoRedo.canUndo) Color.White else Color(0xFF64748B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Redo Button in 2D Floor Plan
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (undoRedo.canRedo) Color(0xFF0369A1) else Color(0xFF1E293B))
                        .clickable(enabled = undoRedo.canRedo) { onRedo() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("floorplan_2d_redo_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Redo,
                            contentDescription = "إعادة 2D",
                            tint = if (undoRedo.canRedo) Color.White else Color(0xFF64748B),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (undoRedo.redoCount > 0) "إعادة (${undoRedo.redoCount})" else "إعادة",
                            color = if (undoRedo.canRedo) Color.White else Color(0xFF64748B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Command History Timeline Toggle Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (showCommandHistoryPanel) Color(0xFF0284C7) else Color(0xFF1E293B))
                        .border(1.dp, Color(0x4038BDF8), RoundedCornerShape(8.dp))
                        .clickable { showCommandHistoryPanel = !showCommandHistoryPanel }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("floorplan_2d_history_toggle_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "سجل الأوامر",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (undoRedo.lastActionTitle.isNotBlank()) {
                                "آخر أمر: ${undoRedo.lastActionTitle}"
                            } else {
                                "سجل الأوامر (Command Stack)"
                            },
                            color = Color(0xFFE2E8F0),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 175.dp)
                        )
                    }
                }

                // Quick 2D Room Layout Shape Switchers (Undoable via ModifyRoomLayoutCommand)
                RoomLayoutShape.entries.forEach { shape ->
                    val isCurrentShape = room.layoutShape == shape
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isCurrentShape) Color(0xFF0D9488) else Color(0xFF1E293B))
                            .clickable { onApplyRoomLayoutShape(shape) }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .testTag("floorplan_2d_shape_${shape.name.lowercase()}")
                    ) {
                        Text(
                            text = shape.titleAr.substringBefore(" "),
                            color = if (isCurrentShape) Color.White else Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = if (isCurrentShape) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                // Direct 2D Floor Plan DXF Export Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF065F46))
                        .border(1.dp, Color(0xFF10B981), RoundedCornerShape(6.dp))
                        .clickable { onExportFloorPlanDxf() }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .testTag("floorplan_2d_export_dxf_btn")
                ) {
                    Text(
                        text = "CAD DXF ⬇",
                        color = Color(0xFF6EE7B7),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Direct 1mm Automatic Wall Filler Tool Pill in 2D Floor Plan
                if (onOpenFillerTool != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF78350F))
                            .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(6.dp))
                            .clickable { onOpenFillerTool() }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                            .testTag("floorplan_2d_open_filler_btn")
                    ) {
                        Text(
                            text = "📏 فيلر الجدار 1مم",
                            color = Color(0xFFFDE68A),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Selected Element Quick 2D Actions (Rotate 90°, Duplicate, Delete — all undoable commands)
                if (selectedIds.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(6.dp))
                            .clickable { onRotateSelected(90f) }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .testTag("floorplan_2d_rotate_90_btn")
                    ) {
                        Text("↻ 90°", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0x3334D399), RoundedCornerShape(6.dp))
                            .clickable { onDuplicateSelected() }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .testTag("floorplan_2d_duplicate_btn")
                    ) {
                        Text("تكرار", color = Color(0xFF34D399), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0x33EF4444), RoundedCornerShape(6.dp))
                            .clickable { onDeleteSelected() }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .testTag("floorplan_2d_delete_btn")
                    ) {
                        Text("حذف", color = Color(0xFFF87171), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    if (state.singleSelectedElement != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, Color(0x33F59E0B), RoundedCornerShape(6.dp))
                                .clickable { onAlignSelectedToWall(WallSide.NORTH) }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .testTag("floorplan_2d_align_north_btn")
                        ) {
                            Text("محاذاة شمال", color = Color(0xFFFBBF24), fontSize = 10.sp)
                        }
                    }
                }

                // Toggle showing all cabinet dimensions vs active/selected only
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (showAllCabinetDimensions2D) Color(0xFF0284C7) else Color(0xFF1E293B))
                        .border(1.dp, Color(0x4038BDF8), RoundedCornerShape(6.dp))
                        .clickable { showAllCabinetDimensions2D = !showAllCabinetDimensions2D }
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                        .testTag("floorplan_2d_toggle_all_dims_btn")
                ) {
                    Text(
                        text = if (showAllCabinetDimensions2D) "📏 الأبعاد: الكل" else "📏 الأبعاد: المحدد",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Toggle Interactive Virtual Ruler (المسطرة الافتراضية الذكية)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isVirtualRulerActive) Color(0xFFD97706) else Color(0xFF1E293B))
                        .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(6.dp))
                        .clickable { isVirtualRulerActive = !isVirtualRulerActive }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .testTag("floorplan_2d_virtual_ruler_btn")
                ) {
                    Text(
                        text = if (isVirtualRulerActive) "📐 المسطرة الذكية: نشطة" else "📐 المسطرة الافتراضية",
                        color = if (isVirtualRulerActive) Color.White else Color(0xFFFDE68A),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 9B-1. Real-Time 2D Floor Plan Dimension Readout & Quick Resize Bar when Moving, Resizing, or Selecting a Cabinet
            state.singleSelectedElement?.let { sel ->
                val wMm = sel.dimensions.x.roundToInt()
                val dMm = sel.dimensions.z.roundToInt()
                val hMm = sel.dimensions.y.roundToInt()
                val xMm = sel.position.x.roundToInt()
                val zMm = sel.position.z.roundToInt()
                val startDims = dragStartDims
                val startPos = dragStartPos
                val dWMm = if ((isDraggingFurniture || isResizingFurniture) && startDims != null) (sel.dimensions.x - startDims.x).roundToInt() else 0
                val dDMm = if ((isDraggingFurniture || isResizingFurniture) && startDims != null) (sel.dimensions.z - startDims.z).roundToInt() else 0
                val dXMm = if ((isDraggingFurniture || isResizingFurniture) && startPos != null) (sel.position.x - startPos.x).roundToInt() else 0
                val dZMm = if ((isDraggingFurniture || isResizingFurniture) && startPos != null) (sel.position.z - startPos.z).roundToInt() else 0

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isResizingFurniture) Color(0xEE451A03)
                            else if (isDraggingFurniture) Color(0xEE064E3B)
                            else Color(0xE60F172A)
                        )
                        .border(
                            width = 1.2.dp,
                            color = when {
                                isResizingFurniture -> Color(0xFFF59E0B)
                                isDraggingFurniture -> Color(0xFF10B981)
                                else -> Color(0xFF38BDF8)
                            },
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("floorplan_realtime_dimension_hud"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = when {
                            isResizingFurniture -> "📐 تغيير الحجم الفوري:"
                            isDraggingFurniture -> "📏 تحريك فوري:"
                            else -> "📏 أبعاد الخزانة:"
                        },
                        color = when {
                            isResizingFurniture -> Color(0xFFFDE68A)
                            isDraggingFurniture -> Color(0xFF6EE7B7)
                            else -> Color(0xFF38BDF8)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "العرض W: $wMm مم (${String.format(java.util.Locale.US, "%.1f", wMm / 10f)} سم)" +
                                (if (dWMm != 0) " [${if (dWMm > 0) "+$dWMm" else "$dWMm"}]" else "") +
                                " | العمق D: $dMm مم (${String.format(java.util.Locale.US, "%.1f", dMm / 10f)} سم)" +
                                (if (dDMm != 0) " [${if (dDMm > 0) "+$dDMm" else "$dDMm"}]" else "") +
                                " | الارتفاع H: $hMm مم" +
                                " | الموضع: ($xMm, $zMm مم)" +
                                (if (dXMm != 0 || dZMm != 0) " [ΔX:$dXMm, ΔZ:$dZMm]" else ""),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Quick 2D Width/Depth Resize Pills (50mm = 5cm steps, recorded in Undo/Redo stack)
                    listOf(
                        "+5سم عرض" to Triple(50f, 0f, "resize_2d_w_plus"),
                        "-5سم عرض" to Triple(-50f, 0f, "resize_2d_w_minus"),
                        "+5سم عمق" to Triple(0f, 50f, "resize_2d_d_plus"),
                        "-5سم عمق" to Triple(0f, -50f, "resize_2d_d_minus")
                    ).forEach { (btnLabel, spec) ->
                        val (dw, dd, tag) = spec
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, Color(0x55F59E0B), RoundedCornerShape(6.dp))
                                .clickable {
                                    val oldPos = sel.position
                                    val oldRot = sel.rotationY
                                    val oldDims = sel.dimensions
                                    val newW = (sel.dimensions.x + dw).coerceIn(200f, 3600f)
                                    val newD = (sel.dimensions.z + dd).coerceIn(200f, 1800f)
                                    onResizeSelected(sel.id, newW, sel.dimensions.y, newD)
                                    onCommitTransform(sel.id, oldPos, oldRot, oldDims)
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag(tag)
                        ) {
                            Text(
                                text = btnLabel,
                                color = Color(0xFFFBBF24),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 9B-2. Visual Red Collision & Ergonomic Clearance Indicator Banner
            state.collisionResult?.takeIf { it.hasCollision }?.let { col ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xEE7F1D1D))
                        .border(1.5.dp, Color(0xFFEF4444), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("collision_indicator_banner"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFFEF4444))
                    )
                    Text(
                        text = col.message.ifBlank { "⛔ مؤشر التصادم: تداخل هندسي أو انتهاك للخلوص الإرجونومي القياسي" },
                        color = Color(0xFFFECACA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 9C. Expandable Command History Stack Panel (Undo & Redo Stacks with Multi-Step Revert)
            AnimatedVisibility(visible = showCommandHistoryPanel) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xF20F172A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x5538BDF8)),
                    modifier = Modifier
                        .width(340.dp)
                        .heightIn(max = 260.dp)
                        .testTag("floorplan_2d_history_panel")
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "سجل أوامر المخطط 2D (Undo: ${undoRedo.undoCount} | Redo: ${undoRedo.redoCount})",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "إغلاق ✕",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                modifier = Modifier.clickable { showCommandHistoryPanel = false }
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = Color(0xFF1E293B))
                        Spacer(modifier = Modifier.height(6.dp))

                        if (undoRedo.undoStackEntries.isEmpty() && undoRedo.redoStackEntries.isEmpty()) {
                            Text(
                                text = "لم يتم تسجيل تعديلات بعد. قم بوضع أو سحب الخزائن أو تعديل أبعاد الغرفة للتراجع/الإعادة.",
                                color = Color(0xFF64748B),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (undoRedo.redoStackEntries.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "الإعادات المتاحة (انقر للإعادة):",
                                            color = Color(0xFFF59E0B),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    items(
                                        items = undoRedo.redoStackEntries,
                                        key = { "redo_${it.stepsFromCurrent}_${it.title}" }
                                    ) { entry ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF1E293B).copy(alpha = 0.65f))
                                                .clickable { onRedoToStep(entry.stepsFromCurrent) }
                                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                                .testTag("history_redo_step_${entry.stepsFromCurrent}"),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "↪ ${entry.title}",
                                                    color = Color(0xFFFDE68A),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = entry.description,
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 9.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = "+${entry.stepsFromCurrent}",
                                                color = Color(0xFFF59E0B),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                if (undoRedo.undoStackEntries.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "التراجعات المتاحة (انقر للتراجع حتى الخطوة):",
                                            color = Color(0xFF34D399),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    items(
                                        items = undoRedo.undoStackEntries,
                                        key = { "undo_${it.stepsFromCurrent}_${it.title}" }
                                    ) { entry ->
                                        val badgeColor = when (entry.category) {
                                            CommandCategory.FURNITURE_PLACEMENT -> Color(0xFF34D399)
                                            CommandCategory.FLOORPLAN_TRANSFORM -> Color(0xFF38BDF8)
                                            CommandCategory.LAYOUT_MODIFICATION -> Color(0xFFA855F7)
                                            CommandCategory.ELEMENT_DELETION -> Color(0xFFF87171)
                                            CommandCategory.GROUPING -> Color(0xFFFBBF24)
                                            CommandCategory.MATERIAL_FINISH -> Color(0xFF2DD4BF)
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF1E293B))
                                                .clickable { onUndoToStep(entry.stepsFromCurrent) }
                                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                                .testTag("history_undo_step_${entry.stepsFromCurrent}"),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "↩ ${entry.title}",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = entry.description,
                                                    color = badgeColor,
                                                    fontSize = 9.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = "-${entry.stepsFromCurrent}",
                                                color = Color(0xFF38BDF8),
                                                fontSize = 10.sp,
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
    }
}
