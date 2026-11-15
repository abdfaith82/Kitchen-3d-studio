package com.example.core.geometry

import com.example.domain.model.MaterialFinish
import com.example.domain.model.OpeningType
import com.example.domain.model.RoomLayoutShape
import com.example.domain.model.RoomStructure
import com.example.domain.model.Wall
import com.example.domain.model.WallOpening
import com.example.domain.model.WallSide
import com.example.domain.model.WallUtilityPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Represents a wall segment drawn interactively on the 2D CAD RoomBuilder grid (in millimeters).
 */
data class DrawnGridWallSegment(
    val id: String,
    val startXMm: Float,
    val startZMm: Float,
    val endXMm: Float,
    val endZMm: Float,
    val thicknessMm: Float = 150f,
    val heightMm: Float = 2700f,
    val openings: List<WallOpening> = emptyList()
) {
    val lengthMm: Float
        get() = hypot((endXMm - startXMm).toDouble(), (endZMm - startZMm).toDouble()).toFloat()

    val angleDegrees: Float
        get() {
            val raw = Math.toDegrees(atan2((endZMm - startZMm).toDouble(), (endXMm - startXMm).toDouble())).toFloat()
            return ((raw % 360f) + 360f) % 360f
        }
}

data class GridPointMm(val xMm: Float, val zMm: Float)

/**
 * Pure geometric engine powering RoomBuilderScreen:
 * - Snaps coordinates to configurable CAD grid steps (100mm, 250mm, 500mm)
 * - Supports orthogonal & 45-degree angle snapping while drawing walls
 * - Converts user-drawn wall segments & room dimensions into a valid RoomStructure
 */
object RoomBuilderGridEngine {

    const val MIN_ROOM_SPAN_MM = 1500f
    const val MAX_ROOM_SPAN_MM = 12000f

    fun snapValue(valueMm: Float, gridStepMm: Float): Float {
        if (gridStepMm <= 0f) return valueMm
        return (valueMm / gridStepMm).roundToInt() * gridStepMm
    }

    fun snapPointToGrid(xMm: Float, zMm: Float, gridStepMm: Float): GridPointMm {
        return GridPointMm(
            xMm = snapValue(xMm, gridStepMm).coerceIn(0f, MAX_ROOM_SPAN_MM),
            zMm = snapValue(zMm, gridStepMm).coerceIn(0f, MAX_ROOM_SPAN_MM)
        )
    }

    /**
     * Snaps the end coordinate of a wall being drawn to the grid and, when angle snapping is enabled,
     * aligns nearly-horizontal, nearly-vertical, or 45-degree diagonals cleanly.
     */
    fun applyOrthogonalSnap(
        startXMm: Float,
        startZMm: Float,
        rawEndXMm: Float,
        rawEndZMm: Float,
        gridStepMm: Float,
        enableAngleSnap: Boolean = true
    ): GridPointMm {
        val snappedRaw = snapPointToGrid(rawEndXMm, rawEndZMm, gridStepMm)
        if (!enableAngleSnap) return snappedRaw

        val dx = snappedRaw.xMm - startXMm
        val dz = snappedRaw.zMm - startZMm
        val len = hypot(dx.toDouble(), dz.toDouble()).toFloat()
        if (len < gridStepMm * 0.5f) return snappedRaw

        val angleRad = atan2(dz.toDouble(), dx.toDouble())
        val angleDeg = Math.toDegrees(angleRad)
        val snapAngleDeg = (angleDeg / 45.0).roundToInt() * 45.0
        val diffDeg = abs(angleDeg - snapAngleDeg)

        if (diffDeg <= 14.0) {
            val snappedRad = snapAngleDeg * PI / 180.0
            val projX = snapValue((startXMm + len * kotlin.math.cos(snappedRad)).toFloat(), gridStepMm)
                .coerceIn(0f, MAX_ROOM_SPAN_MM)
            val projZ = snapValue((startZMm + len * kotlin.math.sin(snappedRad)).toFloat(), gridStepMm)
                .coerceIn(0f, MAX_ROOM_SPAN_MM)
            return GridPointMm(projX, projZ)
        }
        return snappedRaw
    }

    /**
     * Generates clean grid-aligned wall segments matching standard architectural presets.
     */
    fun createPresetSegments(
        shape: RoomLayoutShape,
        widthMm: Float,
        depthMm: Float,
        heightMm: Float,
        thicknessMm: Float,
        originXMm: Float = 500f,
        originZMm: Float = 500f
    ): List<DrawnGridWallSegment> {
        val w = widthMm.coerceIn(MIN_ROOM_SPAN_MM, MAX_ROOM_SPAN_MM)
        val d = depthMm.coerceIn(MIN_ROOM_SPAN_MM, MAX_ROOM_SPAN_MM)
        val x0 = originXMm
        val z0 = originZMm
        val x1 = x0 + w
        val z1 = z0 + d

        val north = DrawnGridWallSegment("seg_north", x0, z0, x1, z0, thicknessMm, heightMm)
        val west = DrawnGridWallSegment("seg_west", x0, z0, x0, z1, thicknessMm, heightMm)
        val east = DrawnGridWallSegment("seg_east", x1, z0, x1, z1, thicknessMm, heightMm)
        val south = DrawnGridWallSegment("seg_south", x0, z1, x1, z1, thicknessMm, heightMm)

        return when (shape) {
            RoomLayoutShape.RECTANGULAR -> listOf(north, east, south, west)
            RoomLayoutShape.L_SHAPE -> listOf(north, west)
            RoomLayoutShape.U_SHAPE -> listOf(north, west, east)
        }
    }

    /**
     * Computes room bounding metrics and maps the drawn CAD grid walls into a RoomStructure
     * ready for the 2D/3D studio workspace.
     */
    fun buildRoomStructure(
        segments: List<DrawnGridWallSegment>,
        fallbackWidthMm: Float,
        fallbackDepthMm: Float,
        ceilingHeightMm: Float,
        wallThicknessMm: Float,
        floorMaterial: MaterialFinish = MaterialFinish.PARQUET_OAK_FLOOR,
        wallFinish: MaterialFinish = MaterialFinish.WHITE_HIGH_GLOSS,
        existingWalls: List<Wall> = emptyList()
    ): RoomStructure {
        val validSegments = segments.filter { it.lengthMm >= 250f }
        val safeHeight = ceilingHeightMm.coerceIn(2000f, 4500f)
        val safeThickness = wallThicknessMm.coerceIn(80f, 350f)

        if (validSegments.isEmpty()) {
            val safeW = fallbackWidthMm.coerceIn(MIN_ROOM_SPAN_MM, MAX_ROOM_SPAN_MM)
            val safeD = fallbackDepthMm.coerceIn(MIN_ROOM_SPAN_MM, MAX_ROOM_SPAN_MM)
            return RoomStructure(
                widthMm = safeW,
                depthMm = safeD,
                heightMm = safeHeight,
                wallThicknessMm = safeThickness,
                layoutShape = RoomLayoutShape.RECTANGULAR,
                walls = RoomStructure.createDefaultWalls(safeW, safeD, safeHeight, safeThickness),
                floorMaterial = floorMaterial,
                wallFinish = wallFinish
            )
        }

        val minX = validSegments.minOf { min(it.startXMm, it.endXMm) }
        val maxX = validSegments.maxOf { max(it.startXMm, it.endXMm) }
        val minZ = validSegments.minOf { min(it.startZMm, it.endZMm) }
        val maxZ = validSegments.maxOf { max(it.startZMm, it.endZMm) }

        val computedW = max(fallbackWidthMm.coerceIn(MIN_ROOM_SPAN_MM, MAX_ROOM_SPAN_MM), (maxX - minX))
            .coerceIn(MIN_ROOM_SPAN_MM, MAX_ROOM_SPAN_MM)
        val computedD = max(fallbackDepthMm.coerceIn(MIN_ROOM_SPAN_MM, MAX_ROOM_SPAN_MM), (maxZ - minZ))
            .coerceIn(MIN_ROOM_SPAN_MM, MAX_ROOM_SPAN_MM)

        val midX = (minX + maxX) * 0.5f
        val midZ = (minZ + maxZ) * 0.5f

        var hasNorth = false
        var hasSouth = false
        var hasWest = false
        var hasEast = false

        val openingsBySide = mutableMapOf<WallSide, MutableList<WallOpening>>()

        validSegments.forEach { seg ->
            val dx = abs(seg.endXMm - seg.startXMm)
            val dz = abs(seg.endZMm - seg.startZMm)
            val segMidX = (seg.startXMm + seg.endXMm) * 0.5f
            val segMidZ = (seg.startZMm + seg.endZMm) * 0.5f

            val assignedSide = if (dx >= dz) {
                if (segMidZ <= midZ) {
                    hasNorth = true
                    WallSide.NORTH
                } else {
                    hasSouth = true
                    WallSide.SOUTH
                }
            } else {
                if (segMidX <= midX) {
                    hasWest = true
                    WallSide.WEST
                } else {
                    hasEast = true
                    WallSide.EAST
                }
            }

            if (seg.openings.isNotEmpty()) {
                val targetList = openingsBySide.getOrPut(assignedSide) { mutableListOf() }
                targetList.addAll(seg.openings)
            }
        }

        // Guarantee at least one primary wall is enabled
        if (!hasNorth && !hasSouth && !hasWest && !hasEast) {
            hasNorth = true
        }

        val inferredShape = when {
            hasNorth && hasWest && hasEast && !hasSouth -> RoomLayoutShape.U_SHAPE
            (hasNorth && hasWest && !hasEast && !hasSouth) -> RoomLayoutShape.L_SHAPE
            else -> RoomLayoutShape.RECTANGULAR
        }

        val existingBySide = existingWalls.associateBy { it.side }

        fun buildWall(side: WallSide, enabled: Boolean, len: Float): Wall {
            val prev = existingBySide[side]
            val userOpenings = openingsBySide[side].orEmpty().filter { it.offsetMm + it.widthMm <= len }
            val finalOpenings = if (userOpenings.isNotEmpty()) {
                userOpenings
            } else {
                prev?.openings?.filter { it.offsetMm + it.widthMm <= len }.orEmpty()
            }
            val finalUtilities: List<WallUtilityPoint> = prev?.utilityPoints?.filter {
                it.offsetFromWallStartMm <= len
            }.orEmpty()
            return Wall(
                id = prev?.id ?: "wall_${side.name.lowercase()}",
                side = side,
                lengthMm = len,
                heightMm = safeHeight,
                thicknessMm = safeThickness,
                cornerAngleDegrees = prev?.cornerAngleDegrees ?: 90f,
                isEnabled = enabled,
                openings = finalOpenings,
                utilityPoints = finalUtilities
            )
        }

        val walls = listOf(
            buildWall(WallSide.NORTH, hasNorth, computedW),
            buildWall(WallSide.EAST, hasEast, computedD),
            buildWall(WallSide.SOUTH, hasSouth, computedW),
            buildWall(WallSide.WEST, hasWest, computedD)
        )

        return RoomStructure(
            widthMm = computedW,
            depthMm = computedD,
            heightMm = safeHeight,
            wallThicknessMm = safeThickness,
            layoutShape = inferredShape,
            walls = walls,
            floorMaterial = floorMaterial,
            wallFinish = wallFinish
        )
    }
}
