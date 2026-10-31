package com.example.core.math

import kotlin.math.max
import kotlin.math.min

/**
 * Axis-Aligned Bounding Box (AABB) in 3D millimeters.
 */
data class AABB(
    val min: Vector3,
    val max: Vector3
) {
    val center: Vector3 get() = (min + max) * 0.5f
    val extents: Vector3 get() = max - min
    val halfExtents: Vector3 get() = extents * 0.5f

    fun contains(point: Vector3): Boolean {
        return point.x in min.x..max.x &&
                point.y in min.y..max.y &&
                point.z in min.z..max.z
    }

    fun intersects(other: AABB): Boolean {
        return min.x <= other.max.x && max.x >= other.min.x &&
                min.y <= other.max.y && max.y >= other.min.y &&
                min.z <= other.max.z && max.z >= other.min.z
    }

    /**
     * Calculates signed or unsigned distance in 2D floor plane (X-Z)
     */
    fun distance2DTo(other: AABB): Float {
        val dx = max(0f, max(min.x - other.max.x, other.min.x - max.x))
        val dz = max(0f, max(min.z - other.max.z, other.min.z - max.z))
        return kotlin.math.sqrt(dx * dx + dz * dz)
    }

    /**
     * Returns closest point on this AABB to an external point
     */
    fun closestPoint(point: Vector3): Vector3 {
        return Vector3(
            x = MathUtils.clamp(point.x, min.x, max.x),
            y = MathUtils.clamp(point.y, min.y, max.y),
            z = MathUtils.clamp(point.z, min.z, max.z)
        )
    }

    fun expandBy(marginMm: Float): AABB {
        val margin = Vector3(marginMm, marginMm, marginMm)
        return AABB(min - margin, max + margin)
    }

    companion object {
        fun fromCenterAndExtents(center: Vector3, extents: Vector3): AABB {
            val half = extents * 0.5f
            return AABB(center - half, center + half)
        }

        fun merge(boxes: List<AABB>): AABB {
            if (boxes.isEmpty()) return AABB(Vector3.ZERO, Vector3.ZERO)
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var minZ = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            var maxZ = -Float.MAX_VALUE

            for (box in boxes) {
                minX = min(minX, box.min.x)
                minY = min(minY, box.min.y)
                minZ = min(minZ, box.min.z)
                maxX = max(maxX, box.max.x)
                maxY = max(maxY, box.max.y)
                maxZ = max(maxZ, box.max.z)
            }
            return AABB(Vector3(minX, minY, minZ), Vector3(maxX, maxY, maxZ))
        }
    }
}
