package com.example.core.sketch

import androidx.compose.ui.geometry.Offset
import com.example.core.math.MathUtils
import com.example.core.math.Vector3
import com.example.domain.model.*
import java.util.UUID
import kotlin.math.*

enum class StrokeType(val title: String, val shortTitle: String) {
    WALL("جدار معماري (Wall)", "جدار"),
    BASE_CABINET("خزانة سفلية 3D (Base)", "خزانة سفلية"),
    WALL_CABINET("خزانة علوية 3D (Upper)", "خزانة علوية"),
    ISLAND("جزيرة وسطية 3D (Island)", "جزيرة"),
    DOOR("باب (Door)", "باب"),
    WINDOW("نافذة (Window)", "نافذة")
}

data class DrawnStroke(
    val id: String = UUID.randomUUID().toString().take(6),
    val points: List<Offset>,
    val type: StrokeType = StrokeType.WALL,
    val customLengthMmOverride: Float? = null,
    val snappedToStartVertex: Boolean = false,
    val snappedToEndVertex: Boolean = false
) {
    val start: Offset? get() = points.firstOrNull()
    val end: Offset? get() = points.lastOrNull()
    val lengthPx: Float
        get() {
            val s = start ?: return 0f
            val e = end ?: return 0f
            val dx = e.x - s.x
            val dy = e.y - s.y
            return sqrt(dx * dx + dy * dy)
        }

    val angleDegrees: Float
        get() {
            val s = start ?: return 0f
            val e = end ?: return 0f
            val deg = Math.toDegrees(atan2((e.y - s.y).toDouble(), (e.x - s.x).toDouble())).toFloat()
            return MathUtils.normalizeAngle(deg)
        }
}

data class SnappedStrokeResult(
    val start: Offset,
    val end: Offset,
    val snappedStartTarget: Offset?,
    val snappedEndTarget: Offset?,
    val alignedAxisCoordX: Float? = null,
    val alignedAxisCoordY: Float? = null,
    val angleDegrees: Float = 0f
)

data class Sketch3DConversionResult(
    val room: RoomStructure,
    val generatedElements: List<KitchenElement>,
    val wallSegmentsCount: Int,
    val isClosedLoop: Boolean
)

data class CalibrationMarker(
    val startPx: Offset,
    val endPx: Offset,
    val realDistanceMm: Float = 1000f // Default 1 meter calibration ruler
) {
    val pixelDistance: Float
        get() {
            val dx = endPx.x - startPx.x
            val dy = endPx.y - startPx.y
            return max(1f, sqrt(dx * dx + dy * dy))
        }

    val mmPerPixel: Float get() = realDistanceMm / pixelDistance
}

/**
 * Computational Sketch-to-3D engine that transforms freehand/guided strokes and calibrated
 * floorplan blueprints into real-world Vector3 walls, openings, and 3D kitchen cabinets.
 * Supports automatic magnetic endpoint snapping (جعل الخطوط لاصقة ببعضها بشكل تلقائي),
 * freehand multi-corner polyline straightening, and instant 3D conversion.
 */
object SketchTo3DEngine {

    private const val ORTHOGONAL_TOLERANCE_DEG = 18f
    private const val DIAGONAL_45_TOLERANCE_DEG = 8f
    const val DEFAULT_MAGNETIC_SNAP_RADIUS_PX = 52f

    /**
     * Simplifies and snaps a stroke into an orthogonal (horizontal or vertical) or 45-degree CAD line.
     */
    fun snapToOrthogonal(start: Offset, end: Offset): Offset {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val normalizedAngle = MathUtils.normalizeAngle(angleDeg)
        val length = sqrt(dx * dx + dy * dy)

        // Snap to nearest 90-degree axis (or 45-degree architectural chamfer)
        val snappedAngle = when {
            normalizedAngle in (360f - ORTHOGONAL_TOLERANCE_DEG)..360f || normalizedAngle in 0f..ORTHOGONAL_TOLERANCE_DEG -> 0f
            normalizedAngle in (90f - ORTHOGONAL_TOLERANCE_DEG)..(90f + ORTHOGONAL_TOLERANCE_DEG) -> 90f
            normalizedAngle in (180f - ORTHOGONAL_TOLERANCE_DEG)..(180f + ORTHOGONAL_TOLERANCE_DEG) -> 180f
            normalizedAngle in (270f - ORTHOGONAL_TOLERANCE_DEG)..(270f + ORTHOGONAL_TOLERANCE_DEG) -> 270f
            normalizedAngle in (45f - DIAGONAL_45_TOLERANCE_DEG)..(45f + DIAGONAL_45_TOLERANCE_DEG) -> 45f
            normalizedAngle in (135f - DIAGONAL_45_TOLERANCE_DEG)..(135f + DIAGONAL_45_TOLERANCE_DEG) -> 135f
            normalizedAngle in (225f - DIAGONAL_45_TOLERANCE_DEG)..(225f + DIAGONAL_45_TOLERANCE_DEG) -> 225f
            normalizedAngle in (315f - DIAGONAL_45_TOLERANCE_DEG)..(315f + DIAGONAL_45_TOLERANCE_DEG) -> 315f
            else -> normalizedAngle
        }

        val rad = Math.toRadians(snappedAngle.toDouble())
        return Offset(
            (start.x + length * cos(rad)).toFloat(),
            (start.y + length * sin(rad)).toFloat()
        )
    }

    /**
     * Collects all existing endpoints from completed strokes for magnetic vertex snapping.
     */
    fun collectExistingVertices(strokes: List<DrawnStroke>): List<Offset> {
        val vertices = mutableListOf<Offset>()
        strokes.forEach { stroke ->
            stroke.start?.let { vertices.add(it) }
            stroke.end?.let { vertices.add(it) }
        }
        return vertices
    }

    /**
     * Finds the closest existing stroke endpoint or point on a wall segment within [snapRadiusPx].
     */
    fun findNearestMagneticPoint(
        point: Offset,
        existingStrokes: List<DrawnStroke>,
        snapRadiusPx: Float = DEFAULT_MAGNETIC_SNAP_RADIUS_PX
    ): Offset? {
        val vertices = collectExistingVertices(existingStrokes)
        var bestVertex: Offset? = null
        var minDist = snapRadiusPx

        for (v in vertices) {
            val d = (v - point).getDistance()
            if (d <= minDist) {
                minDist = d
                bestVertex = v
            }
        }
        if (bestVertex != null) return bestVertex

        // Also check projection onto existing wall segments so cabinets/doors/walls glue flush to wall lines
        for (stroke in existingStrokes) {
            val a = stroke.start ?: continue
            val b = stroke.end ?: continue
            val proj = projectPointOntoSegment(point, a, b)
            val d = (proj - point).getDistance()
            if (d <= snapRadiusPx * 0.75f) {
                minDist = d
                bestVertex = proj
            }
        }
        return bestVertex
    }

    private fun projectPointOntoSegment(p: Offset, a: Offset, b: Offset): Offset {
        val ab = b - a
        val lenSq = ab.x * ab.x + ab.y * ab.y
        if (lenSq < 1e-4f) return a
        val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / lenSq).coerceIn(0f, 1f)
        return Offset(a.x + ab.x * t, a.y + ab.y * t)
    }

    /**
     * Performs full magnetic endpoint snapping + orthogonal axis alignment + parallel vertex alignment
     * so drawn lines automatically stick together with zero gaps (جعل الخطوط لاصقة ببعضها بشكل تلقائي).
     */
    fun snapStrokeMagnetically(
        rawStart: Offset,
        rawEnd: Offset,
        existingStrokes: List<DrawnStroke>,
        snapRadiusPx: Float = DEFAULT_MAGNETIC_SNAP_RADIUS_PX,
        autoStickToLastEndpoint: Boolean = true
    ): SnappedStrokeResult {
        val vertices = collectExistingVertices(existingStrokes)

        // 1. Snap start point to nearest existing vertex, or if autoStickToLastEndpoint is active and
        // the user starts within 110px of the last drawn stroke's end, glue directly to it.
        var snappedStartTarget = findNearestMagneticPoint(rawStart, existingStrokes, snapRadiusPx)
        if (snappedStartTarget == null && autoStickToLastEndpoint) {
            val lastWallEnd = existingStrokes.lastOrNull()?.end
            if (lastWallEnd != null && (lastWallEnd - rawStart).getDistance() <= snapRadiusPx * 2.1f) {
                snappedStartTarget = lastWallEnd
            }
        }
        val effectiveStart = snappedStartTarget ?: rawStart

        // 2. Orthogonal/45° snap from the effective start
        var candidateEnd = snapToOrthogonal(effectiveStart, rawEnd)

        // 3. Check if rawEnd or candidateEnd is near an existing vertex (e.g. closing the room loop!)
        var snappedEndTarget: Offset? = null
        var minEndDist = snapRadiusPx
        for (v in vertices) {
            if ((v - effectiveStart).getDistance() < 10f) continue
            val dRaw = (v - rawEnd).getDistance()
            val dCand = (v - candidateEnd).getDistance()
            val bestD = min(dRaw, dCand)
            if (bestD <= minEndDist) {
                minEndDist = bestD
                snappedEndTarget = v
            }
        }

        var alignedX: Float? = null
        var alignedY: Float? = null

        if (snappedEndTarget != null) {
            candidateEnd = snappedEndTarget
        } else {
            // 4. Axis alignment with other vertices (so opposite walls automatically have identical length!)
            for (v in vertices) {
                if (abs(candidateEnd.x - v.x) <= snapRadiusPx * 0.6f) {
                    candidateEnd = candidateEnd.copy(x = v.x)
                    alignedX = v.x
                    break
                }
            }
            for (v in vertices) {
                if (abs(candidateEnd.y - v.y) <= snapRadiusPx * 0.6f) {
                    candidateEnd = candidateEnd.copy(y = v.y)
                    alignedY = v.y
                    break
                }
            }
        }

        val dx = candidateEnd.x - effectiveStart.x
        val dy = candidateEnd.y - effectiveStart.y
        val angle = MathUtils.normalizeAngle(Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat())

        return SnappedStrokeResult(
            start = effectiveStart,
            end = candidateEnd,
            snappedStartTarget = snappedStartTarget,
            snappedEndTarget = snappedEndTarget,
            alignedAxisCoordX = alignedX,
            alignedAxisCoordY = alignedY,
            angleDegrees = angle
        )
    }

    /**
     * Converts a continuous freehand stroke (e.g., an L-shape, U-shape, or full room rectangle drawn
     * in a single finger drag without lifting) into a chain of connected, orthogonal, magnetically-snapped
     * segments.
     */
    fun extractConnectedSegmentsFromFreehand(
        rawPoints: List<Offset>,
        strokeType: StrokeType,
        existingStrokes: List<DrawnStroke>,
        snapRadiusPx: Float = DEFAULT_MAGNETIC_SNAP_RADIUS_PX,
        autoStickToLastEndpoint: Boolean = true
    ): List<DrawnStroke> {
        if (rawPoints.size < 2) return emptyList()

        // Simplify polyline using Ramer-Douglas-Peucker with 22px tolerance to find intentional corners
        val simplified = simplifyPolylineRdp(rawPoints, epsilon = 24f)
        if (simplified.size <= 2) {
            val snapped = snapStrokeMagnetically(
                rawStart = rawPoints.first(),
                rawEnd = rawPoints.last(),
                existingStrokes = existingStrokes,
                snapRadiusPx = snapRadiusPx,
                autoStickToLastEndpoint = autoStickToLastEndpoint
            )
            if ((snapped.end - snapped.start).getDistance() < 16f) return emptyList()
            return listOf(
                DrawnStroke(
                    points = listOf(snapped.start, snapped.end),
                    type = strokeType,
                    snappedToStartVertex = snapped.snappedStartTarget != null,
                    snappedToEndVertex = snapped.snappedEndTarget != null
                )
            )
        }

        // Build chained segments where each segment's start is strictly glued to the previous segment's end
        val result = mutableListOf<DrawnStroke>()
        var workingStrokes = existingStrokes.toMutableList()
        var currentStart = simplified.first()

        for (i in 1 until simplified.size) {
            val rawTarget = simplified[i]
            if ((rawTarget - currentStart).getDistance() < 22f) continue

            val snapped = snapStrokeMagnetically(
                rawStart = currentStart,
                rawEnd = rawTarget,
                existingStrokes = workingStrokes,
                snapRadiusPx = snapRadiusPx,
                autoStickToLastEndpoint = true
            )
            if ((snapped.end - snapped.start).getDistance() >= 18f) {
                val seg = DrawnStroke(
                    points = listOf(snapped.start, snapped.end),
                    type = strokeType,
                    snappedToStartVertex = snapped.snappedStartTarget != null || i > 1,
                    snappedToEndVertex = snapped.snappedEndTarget != null
                )
                result.add(seg)
                workingStrokes.add(seg)
                currentStart = snapped.end
            }
        }

        return result
    }

    private fun simplifyPolylineRdp(points: List<Offset>, epsilon: Float): List<Offset> {
        if (points.size < 3) return points
        var maxDist = 0f
        var index = 0
        val start = points.first()
        val end = points.last()

        for (i in 1 until points.lastIndex) {
            val d = perpendicularDistance(points[i], start, end)
            if (d > maxDist) {
                index = i
                maxDist = d
            }
        }

        return if (maxDist > epsilon) {
            val left = simplifyPolylineRdp(points.subList(0, index + 1), epsilon)
            val right = simplifyPolylineRdp(points.subList(index, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(start, end)
        }
    }

    private fun perpendicularDistance(pt: Offset, lineStart: Offset, lineEnd: Offset): Float {
        val dx = lineEnd.x - lineStart.x
        val dy = lineEnd.y - lineStart.y
        val mag = hypot(dx, dy)
        if (mag < 1e-4f) return (pt - lineStart).getDistance()
        return abs(dy * pt.x - dx * pt.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x) / mag
    }

    /**
     * Converts a collection of sketch strokes into a calibrated 3D RoomStructure with dynamic openings.
     */
    fun convertStrokesToRoom(
        strokes: List<DrawnStroke>,
        calibration: CalibrationMarker?,
        canvasWidthPx: Float,
        canvasHeightPx: Float,
        ceilingHeightMm: Float = 2700f
    ): RoomStructure {
        return convertStrokesToScene(
            strokes = strokes,
            calibration = calibration,
            canvasWidthPx = canvasWidthPx,
            canvasHeightPx = canvasHeightPx,
            ceilingHeightMm = ceilingHeightMm
        ).room
    }

    /**
     * Full real-time conversion from 2D hand-drawn strokes into both a 3D [RoomStructure] (with
     * automatic L-shape / U-shape / Rectangular wall detection & doors/windows) AND 3D [KitchenElement]
     * cabinets drawn directly on the sketch canvas.
     */
    fun convertStrokesToScene(
        strokes: List<DrawnStroke>,
        calibration: CalibrationMarker?,
        canvasWidthPx: Float,
        canvasHeightPx: Float,
        ceilingHeightMm: Float = 2700f,
        autoPopulateCabinetsOnWalls: Boolean = false
    ): Sketch3DConversionResult {
        val safeW = max(300f, canvasWidthPx)
        val scale = calibration?.mmPerPixel ?: (4200f / (safeW * 0.68f))

        val wallStrokes = strokes.filter { it.type == StrokeType.WALL && it.lengthPx > 18f }
        val openingStrokes = strokes.filter { it.type == StrokeType.DOOR || it.type == StrokeType.WINDOW }
        val cabinetStrokes = strokes.filter {
            (it.type == StrokeType.BASE_CABINET || it.type == StrokeType.WALL_CABINET || it.type == StrokeType.ISLAND) &&
                    it.lengthPx > 15f
        }

        // Reference bounding box from wall strokes (or all strokes if user only sketched cabinets)
        val referenceStrokes = wallStrokes.ifEmpty { strokes.filter { it.lengthPx > 15f } }
        if (referenceStrokes.isEmpty()) {
            return Sketch3DConversionResult(
                room = RoomStructure(4000f, 3500f, ceilingHeightMm),
                generatedElements = emptyList(),
                wallSegmentsCount = 0,
                isClosedLoop = false
            )
        }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        referenceStrokes.forEach { stroke ->
            stroke.points.forEach { pt ->
                minX = min(minX, pt.x)
                minY = min(minY, pt.y)
                maxX = max(maxX, pt.x)
                maxY = max(maxY, pt.y)
            }
        }

        val spanX = max(120f, maxX - minX)
        val spanY = max(120f, maxY - minY)

        // If user overrides length on primary horizontal/vertical wall, respect it
        val horizOverride = wallStrokes.firstOrNull {
            val a = it.angleDegrees
            (a <= 25f || a >= 335f || a in 155f..205f) && it.customLengthMmOverride != null
        }?.customLengthMmOverride
        val vertOverride = wallStrokes.firstOrNull {
            val a = it.angleDegrees
            (a in 65f..115f || a in 245f..295f) && it.customLengthMmOverride != null
        }?.customLengthMmOverride

        val widthMm = (horizOverride ?: (spanX * scale)).coerceIn(2200f, 12000f).let { MathUtils.roundToMillimeter(it) }
        val depthMm = (vertOverride ?: (spanY * scale)).coerceIn(2000f, 12000f).let { MathUtils.roundToMillimeter(it) }

        // Detect which boundary walls were drawn (so 1-wall, L-shape, U-shape, or full 4-wall room works naturally)
        var hasNorth = false
        var hasSouth = false
        var hasWest = false
        var hasEast = false

        wallStrokes.forEach { ws ->
            val s = ws.start ?: return@forEach
            val e = ws.end ?: return@forEach
            val midX = (s.x + e.x) * 0.5f
            val midY = (s.y + e.y) * 0.5f
            val dx = abs(e.x - s.x)
            val dy = abs(e.y - s.y)
            if (dx >= dy) {
                if (midY <= minY + spanY * 0.45f) hasNorth = true else hasSouth = true
            } else {
                if (midX <= minX + spanX * 0.45f) hasWest = true else hasEast = true
            }
        }

        val enabledSidesCount = listOf(hasNorth, hasSouth, hasWest, hasEast).count { it }
        if (enabledSidesCount == 0) {
            hasNorth = true; hasSouth = true; hasWest = true; hasEast = true
        } else if (enabledSidesCount == 1) {
            // Keep North + West minimum L-backdrop if only 1 line drawn so 3D view looks architectural
            hasNorth = true
            hasWest = true
        }

        val layoutShape = when {
            enabledSidesCount == 2 -> RoomLayoutShape.L_SHAPE
            enabledSidesCount == 3 -> RoomLayoutShape.U_SHAPE
            else -> RoomLayoutShape.RECTANGULAR
        }

        val northWall = Wall(
            id = "wall_north_${UUID.randomUUID().toString().take(4)}",
            side = WallSide.NORTH,
            lengthMm = widthMm,
            heightMm = ceilingHeightMm,
            thicknessMm = 150f,
            isEnabled = hasNorth,
            openings = emptyList()
        )
        val eastWall = Wall(
            id = "wall_east_${UUID.randomUUID().toString().take(4)}",
            side = WallSide.EAST,
            lengthMm = depthMm,
            heightMm = ceilingHeightMm,
            thicknessMm = 150f,
            isEnabled = hasEast,
            openings = emptyList()
        )
        val southWall = Wall(
            id = "wall_south_${UUID.randomUUID().toString().take(4)}",
            side = WallSide.SOUTH,
            lengthMm = widthMm,
            heightMm = ceilingHeightMm,
            thicknessMm = 150f,
            isEnabled = hasSouth,
            openings = emptyList()
        )
        val westWall = Wall(
            id = "wall_west_${UUID.randomUUID().toString().take(4)}",
            side = WallSide.WEST,
            lengthMm = depthMm,
            heightMm = ceilingHeightMm,
            thicknessMm = 150f,
            isEnabled = hasWest,
            openings = emptyList()
        )

        val walls = mutableListOf(northWall, eastWall, southWall, westWall)

        // Map drawn doors and windows to closest wall
        openingStrokes.forEach { opStroke ->
            val centerPx = opStroke.start?.let { s ->
                opStroke.end?.let { e ->
                    Offset((s.x + e.x) * 0.5f, (s.y + e.y) * 0.5f)
                }
            } ?: return@forEach

            val relX = ((centerPx.x - minX) / spanX) * widthMm
            val relZ = ((centerPx.y - minY) / spanY) * depthMm
            val opWidthMm = (opStroke.customLengthMmOverride ?: (opStroke.lengthPx * scale)).coerceIn(700f, 2200f)

            val type = if (opStroke.type == StrokeType.DOOR) OpeningType.DOOR else OpeningType.WINDOW
            val heightMm = if (type == OpeningType.DOOR) 2100f else 1200f
            val sillMm = if (type == OpeningType.DOOR) 0f else 900f

            val distN = abs(relZ)
            val distS = abs(depthMm - relZ)
            val distW = abs(relX)
            val distE = abs(widthMm - relX)
            val minDist = min(min(distN, distS), min(distW, distE))

            val newOpening = WallOpening(
                id = "op_${UUID.randomUUID().toString().take(6)}",
                type = type,
                offsetMm = when (minDist) {
                    distN, distS -> (relX - opWidthMm * 0.5f).coerceIn(150f, (widthMm - opWidthMm - 150f).coerceAtLeast(150f))
                    else -> (relZ - opWidthMm * 0.5f).coerceIn(150f, (depthMm - opWidthMm - 150f).coerceAtLeast(150f))
                },
                widthMm = opWidthMm,
                heightMm = heightMm,
                sillHeightMm = sillMm
            )

            when (minDist) {
                distN -> walls[0] = walls[0].copy(isEnabled = true, openings = walls[0].openings + newOpening)
                distE -> walls[1] = walls[1].copy(isEnabled = true, openings = walls[1].openings + newOpening)
                distS -> walls[2] = walls[2].copy(isEnabled = true, openings = walls[2].openings + newOpening)
                distW -> walls[3] = walls[3].copy(isEnabled = true, openings = walls[3].openings + newOpening)
            }
        }

        // Convert drawn cabinet strokes into 3D KitchenElement objects
        val generatedCabinets = mutableListOf<KitchenElement>()
        cabinetStrokes.forEachIndexed { idx, cabStroke ->
            val s = cabStroke.start ?: return@forEachIndexed
            val e = cabStroke.end ?: return@forEachIndexed
            val centerPx = Offset((s.x + e.x) * 0.5f, (s.y + e.y) * 0.5f)
            val isHorizontal = abs(e.x - s.x) >= abs(e.y - s.y)

            val drawnLengthMm = (cabStroke.customLengthMmOverride ?: (cabStroke.lengthPx * scale))
                .coerceIn(400f, 3600f)

            val normX = ((centerPx.x - minX) / spanX).coerceIn(0.08f, 0.92f)
            val normZ = ((centerPx.y - minY) / spanY).coerceIn(0.08f, 0.92f)

            when (cabStroke.type) {
                StrokeType.BASE_CABINET -> {
                    // Break long base cabinet strokes into modular 600..900mm 3D units or a custom unit
                    val depthCab = 600f
                    val heightCab = 850f
                    val rotY = if (isHorizontal) 0f else 90f
                    val posX = if (isHorizontal) (normX * widthMm).coerceIn(drawnLengthMm * 0.5f, widthMm - drawnLengthMm * 0.5f)
                    else if (normX < 0.5f) depthCab * 0.5f else widthMm - depthCab * 0.5f
                    val posZ = if (!isHorizontal) (normZ * depthMm).coerceIn(drawnLengthMm * 0.5f, depthMm - drawnLengthMm * 0.5f)
                    else if (normZ < 0.5f) depthCab * 0.5f else depthMm - depthCab * 0.5f

                    val elemType = when {
                        idx == 0 && drawnLengthMm >= 750f -> KitchenElementType.BASE_SINK_UNIT
                        idx == 1 -> KitchenElementType.BASE_3_DRAWERS
                        drawnLengthMm >= 750f -> KitchenElementType.BASE_DOUBLE_DOOR
                        else -> KitchenElementType.BASE_SINGLE_DOOR
                    }
                    generatedCabinets.add(
                        KitchenElement(
                            id = "sketch_base_${cabStroke.id}",
                            name = "${elemType.title} (سكتش ${(drawnLengthMm / 10f).toInt()} سم)",
                            type = elemType,
                            position = Vector3(MathUtils.roundToMillimeter(posX), 0f, MathUtils.roundToMillimeter(posZ)),
                            rotationY = rotY,
                            dimensions = Vector3(MathUtils.roundToMillimeter(drawnLengthMm), heightCab, depthCab),
                            material = MaterialFinish.NATURAL_OAK,
                            countertopMaterial = MaterialFinish.CALACATTA_GOLD
                        )
                    )
                }

                StrokeType.WALL_CABINET -> {
                    val depthCab = 350f
                    val heightCab = 750f
                    val elevY = 1450f // 850mm base + 600mm ergonomic backsplash gap
                    val rotY = if (isHorizontal) 0f else 90f
                    val posX = if (isHorizontal) (normX * widthMm).coerceIn(drawnLengthMm * 0.5f, widthMm - drawnLengthMm * 0.5f)
                    else if (normX < 0.5f) depthCab * 0.5f else widthMm - depthCab * 0.5f
                    val posZ = if (!isHorizontal) (normZ * depthMm).coerceIn(drawnLengthMm * 0.5f, depthMm - drawnLengthMm * 0.5f)
                    else if (normZ < 0.5f) depthCab * 0.5f else depthMm - depthCab * 0.5f

                    generatedCabinets.add(
                        KitchenElement(
                            id = "sketch_wall_${cabStroke.id}",
                            name = "خزانة علوية زجاج/ميلامين (سكتش ${(drawnLengthMm / 10f).toInt()} سم)",
                            type = KitchenElementType.WALL_GLASS_DOOR,
                            position = Vector3(MathUtils.roundToMillimeter(posX), elevY, MathUtils.roundToMillimeter(posZ)),
                            rotationY = rotY,
                            dimensions = Vector3(MathUtils.roundToMillimeter(drawnLengthMm), heightCab, depthCab),
                            material = MaterialFinish.CHAMPAGNE_GLOSS
                        )
                    )
                }

                StrokeType.ISLAND -> {
                    val depthIsland = 900f
                    val heightIsland = 850f
                    val rotY = if (isHorizontal) 0f else 90f
                    val posX = (normX * widthMm).coerceIn(800f, widthMm - 800f)
                    val posZ = (normZ * depthMm).coerceIn(800f, depthMm - 800f)
                    generatedCabinets.add(
                        KitchenElement(
                            id = "sketch_island_${cabStroke.id}",
                            name = "جزيرة وسطية رخام (سكتش ${(drawnLengthMm / 10f).toInt()} سم)",
                            type = KitchenElementType.ISLAND_COUNTER,
                            position = Vector3(MathUtils.roundToMillimeter(posX), 0f, MathUtils.roundToMillimeter(posZ)),
                            rotationY = rotY,
                            dimensions = Vector3(MathUtils.roundToMillimeter(drawnLengthMm), heightIsland, depthIsland),
                            material = MaterialFinish.CHARCOAL_MATTE,
                            countertopMaterial = MaterialFinish.CARRARA_MARBLE
                        )
                    )
                }

                else -> {}
            }
        }

        // Optional: if user toggled "توزيع خزائن 3D تلقائياً على الجدران المرسومة" and hasn't drawn manual cabinet strokes
        if (autoPopulateCabinetsOnWalls && generatedCabinets.isEmpty() && wallStrokes.isNotEmpty()) {
            // Populate North wall with modular base & upper cabinets
            var cursorX = 450f
            var unitIndex = 0
            while (cursorX + 450f <= widthMm - 200f) {
                val bType = when (unitIndex % 3) {
                    0 -> KitchenElementType.BASE_SINK_UNIT
                    1 -> KitchenElementType.BASE_3_DRAWERS
                    else -> KitchenElementType.BASE_DOUBLE_DOOR
                }
                generatedCabinets.add(
                    KitchenElement(
                        id = "auto_base_$unitIndex",
                        name = bType.title,
                        type = bType,
                        position = Vector3(cursorX, 0f, 300f),
                        dimensions = Vector3(800f, 850f, 600f),
                        material = MaterialFinish.NATURAL_OAK,
                        countertopMaterial = MaterialFinish.CALACATTA_GOLD
                    )
                )
                generatedCabinets.add(
                    KitchenElement(
                        id = "auto_wall_$unitIndex",
                        name = "خزانة علوية 80 سم",
                        type = KitchenElementType.WALL_DOUBLE_DOOR,
                        position = Vector3(cursorX, 1450f, 175f),
                        dimensions = Vector3(800f, 750f, 350f),
                        material = MaterialFinish.CHAMPAGNE_GLOSS
                    )
                )
                cursorX += 820f
                unitIndex++
            }
        }

        val isClosed = wallStrokes.size >= 3 && wallStrokes.first().start != null && wallStrokes.last().end != null &&
                (wallStrokes.first().start!! - wallStrokes.last().end!!).getDistance() <= DEFAULT_MAGNETIC_SNAP_RADIUS_PX

        val room = RoomStructure(
            widthMm = widthMm,
            depthMm = depthMm,
            heightMm = ceilingHeightMm,
            wallThicknessMm = 150f,
            walls = walls,
            layoutShape = layoutShape
        )

        return Sketch3DConversionResult(
            room = room,
            generatedElements = generatedCabinets,
            wallSegmentsCount = wallStrokes.size,
            isClosedLoop = isClosed
        )
    }
}
