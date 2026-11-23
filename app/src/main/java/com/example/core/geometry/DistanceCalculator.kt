package com.example.core.geometry

import com.example.core.math.OBB
import com.example.core.math.Vector3
import com.example.domain.model.KitchenElement
import com.example.domain.model.RoomStructure
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class WallClearances(
    val distToNorthMm: Float,
    val distToSouthMm: Float,
    val distToWestMm: Float,
    val distToEastMm: Float
) {
    val minDistanceMm: Float
        get() = min(min(distToNorthMm, distToSouthMm), min(distToWestMm, distToEastMm))
}

data class CabinetDistanceInfo(
    val otherElementId: String,
    val distanceMm: Float,
    val deltaX: Float,
    val deltaZ: Float,
    val isAdjacent: Boolean
)

/**
 * Computational geometry distance calculator for CAD kitchen planning.
 * Computes exact 1mm clearances between cabinet edges and walls/adjacent units.
 */
object DistanceCalculator {

    /**
     * Calculates clear distances in millimeters from cabinet bounding edges to all 4 room walls.
     */
    fun calculateDistancesToWalls(element: KitchenElement, room: RoomStructure): WallClearances {
        val aabb = element.getAABB()
        val distNorth = aabb.min.z // Wall at z = 0
        val distSouth = room.depthMm - aabb.max.z // Wall at z = depth
        val distWest = aabb.min.x // Wall at x = 0
        val distEast = room.widthMm - aabb.max.x // Wall at x = width

        return WallClearances(
            distToNorthMm = max(0f, distNorth),
            distToSouthMm = max(0f, distSouth),
            distToWestMm = max(0f, distWest),
            distToEastMm = max(0f, distEast)
        )
    }

    /**
     * Computes the distance between two kitchen cabinets in the horizontal plane (X-Z).
     */
    fun calculateDistanceBetween(elemA: KitchenElement, elemB: KitchenElement): Float {
        val aabbA = elemA.getAABB()
        val aabbB = elemB.getAABB()
        return aabbA.distance2DTo(aabbB)
    }

    /**
     * Finds nearest neighbor cabinets and computes clearances.
     */
    fun findNearestCabinets(
        target: KitchenElement,
        allElements: List<KitchenElement>
    ): List<CabinetDistanceInfo> {
        val targetAABB = target.getAABB()

        return allElements
            .filter { it.id != target.id && it.isVisible }
            .map { other ->
                val otherAABB = other.getAABB()
                val dist = targetAABB.distance2DTo(otherAABB)
                val dx = other.position.x - target.position.x
                val dz = other.position.z - target.position.z
                CabinetDistanceInfo(
                    otherElementId = other.id,
                    distanceMm = dist,
                    deltaX = dx,
                    deltaZ = dz,
                    isAdjacent = dist <= 5f // within 5mm considered touching
                )
            }
            .sortedBy { it.distanceMm }
    }
}
