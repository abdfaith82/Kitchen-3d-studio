package com.example.core.geometry

import kotlin.math.*

enum class WallOrientation {
    NORTH, SOUTH, EAST, WEST
}

data class WallSegment(
    val id: String,
    val start: Vector3,
    val end: Vector3,
    val height: Float = 2.80f,
    val thickness: Float = 0.20f
) {
    val length: Float
        get() = start.distanceTo2D(end)

    val normal: Vector3
        get() {
            val dir = (end - start).normalized()
            return Vector3(-dir.z, 0f, dir.x).normalized()
        }

    val angleDegrees: Float
        get() {
            val dir = end - start
            return Math.toDegrees(atan2(dir.z.toDouble(), dir.x.toDouble())).toFloat()
        }
}

data class KitchenRoom(
    val width: Float = 4.0f,
    val length: Float = 5.0f,
    val height: Float = 2.8f,
    val walls: List<WallSegment> = emptyList()
) {
    val boundingBox: BoundingBox3D
        get() = BoundingBox3D(
            min = Vector3(-width / 2f, 0f, -length / 2f),
            max = Vector3(width / 2f, height, length / 2f)
        )
}

object RoomBuilderGridEngine {

    const val MODULAR_GRID_FINE = 0.015f
    const val MODULAR_GRID_MEDIUM = 0.050f
    const val MODULAR_GRID_STANDARD = 0.100f
    const val CABINET_GRID_STEP = 0.150f

    fun createRectangularRoom(width: Float, length: Float, height: Float = 2.8f): KitchenRoom {
        val halfW = width * 0.5f
        val halfL = length * 0.5f

        val p1 = Vector3(-halfW, 0f, -halfL)
        val p2 = Vector3(halfW, 0f, -halfL)
        val p3 = Vector3(halfW, 0f, halfL)
        val p4 = Vector3(-halfW, 0f, halfL)

        val walls = listOf(
            WallSegment("wall_north", p1, p2, height),
            WallSegment("wall_east", p2, p3, height),
            WallSegment("wall_south", p3, p4, height),
            WallSegment("wall_west", p4, p1, height)
        )

        return KitchenRoom(width = width, length = length, height = height, walls = walls)
    }

    fun snapToGrid(position: Vector3, gridSize: Float = MODULAR_GRID_FINE): Vector3 {
        return Vector3(
            x = (round(position.x / gridSize) * gridSize),
            y = (round(position.y / gridSize) * gridSize),
            z = (round(position.z / gridSize) * gridSize)
        )
    }

    fun snapAngleTo90(angleDegrees: Float, snapTolerance: Float = 15f): Float {
        val normalized = ((angleDegrees % 360f) + 360f) % 360f
        val nearest90 = round(normalized / 90f) * 90f
        val diff = abs(normalized - nearest90)
        return if (diff <= snapTolerance || (360f - diff) <= snapTolerance) {
            (nearest90 % 360f)
        } else {
            normalized
        }
    }

    fun snapToNearestWall(
        boxCenter: Vector3,
        boxDepth: Float,
        room: KitchenRoom,
        wallSnappingDistanceThreshold: Float = 0.40f
    ): Pair<Vector3, Float>? {
        var closestWall: WallSegment? = null
        var minDistance = Float.POSITIVE_INFINITY
        var projectedPointOnWall = Vector3()

        for (wall in room.walls) {
            val dist = DistanceCalculator.distancePointToSegment(boxCenter, wall.start, wall.end)
            if (dist < minDistance) {
                minDistance = dist
                closestWall = wall
                val seg = wall.end - wall.start
                val t = ((boxCenter - wall.start).dot(seg) / seg.lengthSquared()).coerceIn(0.1f, 0.9f)
                projectedPointOnWall = wall.start + (seg * t)
            }
        }

        if (closestWall != null && minDistance <= wallSnappingDistanceThreshold) {
            val halfDepth = boxDepth * 0.5f
            val snappedPos = projectedPointOnWall + (closestWall.normal * (halfDepth + closestWall.thickness * 0.5f))
            val finalPos = Vector3(snappedPos.x, boxCenter.y, snappedPos.z)
            val wallRot = closestWall.angleDegrees
            return Pair(finalPos, wallRot)
        }

        return null
    }
}
