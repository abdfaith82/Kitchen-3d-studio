package com.example.core.math

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Oriented Bounding Box (OBB) with Y-axis rotation.
 * Provides exact 3D and 2D collision detection using Separating Axis Theorem (SAT).
 */
data class OBB(
    val center: Vector3,
    val dimensions: Vector3, // width (X), height (Y), depth (Z) in mm
    val rotationYDegrees: Float
) {
    val halfExtents: Vector3 = dimensions * 0.5f

    // Local axes in world space
    val axisX: Vector3
    val axisY: Vector3 = Vector3.UNIT_Y
    val axisZ: Vector3

    init {
        val rad = Math.toRadians(rotationYDegrees.toDouble()).toFloat()
        val cosA = cos(rad)
        val sinA = sin(rad)
        axisX = Vector3(cosA, 0f, -sinA)
        axisZ = Vector3(sinA, 0f, cosA)
    }

    /**
     * Computes the 4 corners on the floor (Y = center.y - halfExtents.y)
     */
    fun getBottomCorners(): List<Vector3> {
        val baseY = center.y - halfExtents.y
        val hx = axisX * halfExtents.x
        val hz = axisZ * halfExtents.z
        val baseCenter = Vector3(center.x, baseY, center.z)

        return listOf(
            baseCenter - hx - hz,
            baseCenter + hx - hz,
            baseCenter + hx + hz,
            baseCenter - hx + hz
        )
    }

    /**
     * Computes all 8 world-space corners of this OBB
     */
    fun getCorners(): List<Vector3> {
        val hx = axisX * halfExtents.x
        val hy = axisY * halfExtents.y
        val hz = axisZ * halfExtents.z

        val corners = mutableListOf<Vector3>()
        for (sx in listOf(-1f, 1f)) {
            for (sy in listOf(-1f, 1f)) {
                for (sz in listOf(-1f, 1f)) {
                    corners.add(center + (hx * sx) + (hy * sy) + (hz * sz))
                }
            }
        }
        return corners
    }

    /**
     * Checks collision with another OBB using Separating Axis Theorem (SAT) on X-Z floor plane.
     * In kitchen cabinet layout, cabinets align along the floor.
     */
    fun intersects(other: OBB, marginMm: Float = 0f): Boolean {
        // Test height overlap first
        val topA = center.y + halfExtents.y
        val bottomA = center.y - halfExtents.y
        val topB = other.center.y + other.halfExtents.y
        val bottomB = other.center.y - other.halfExtents.y

        if (bottomA >= topB - marginMm || topA <= bottomB + marginMm) {
            return false
        }

        // 2D SAT axes: test axisX, axisZ of A, and axisX, axisZ of B
        val testAxes = listOf(this.axisX, this.axisZ, other.axisX, other.axisZ)
        val delta = other.center - this.center

        for (axis in testAxes) {
            val projectedDelta = abs(delta.dot(axis))
            val radiusA = abs(this.axisX.dot(axis)) * (this.halfExtents.x - marginMm) +
                    abs(this.axisZ.dot(axis)) * (this.halfExtents.z - marginMm)
            val radiusB = abs(other.axisX.dot(axis)) * (other.halfExtents.x - marginMm) +
                    abs(other.axisZ.dot(axis)) * (other.halfExtents.z - marginMm)

            if (projectedDelta >= radiusA + radiusB) {
                return false // Separating axis found, no collision!
            }
        }
        return true
    }

    /**
     * Computes the Axis-Aligned Bounding Box that encloses this OBB
     */
    fun toAABB(): AABB {
        val corners = getCorners()
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE

        for (c in corners) {
            minX = min(minX, c.x)
            minY = min(minY, c.y)
            minZ = min(minZ, c.z)
            maxX = max(maxX, c.x)
            maxY = max(maxY, c.y)
            maxZ = max(maxZ, c.z)
        }
        return AABB(Vector3(minX, minY, minZ), Vector3(maxX, maxY, maxZ))
    }
}
