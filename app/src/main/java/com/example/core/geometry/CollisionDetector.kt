package com.example.core.geometry

import kotlin.math.*

data class OrientedBoundingBox(
    val center: Vector3,
    val halfExtents: Vector3,
    val rotationYDegrees: Float
) {
    val axes: Array<Vector3>
        get() {
            val rad = Math.toRadians(rotationYDegrees.toDouble())
            val cosA = cos(rad).toFloat()
            val sinA = sin(rad).toFloat()
            val uX = Vector3(cosA, 0f, -sinA)
            val uY = Vector3(0f, 1f, 0f)
            val uZ = Vector3(sinA, 0f, cosA)
            return arrayOf(uX, uY, uZ)
        }

    fun getCorners(): List<Vector3> {
        val ax = axes
        val uX = ax[0] * halfExtents.x
        val uY = ax[1] * halfExtents.y
        val uZ = ax[2] * halfExtents.z

        return listOf(
            center + uX + uY + uZ,
            center + uX + uY - uZ,
            center + uX - uY + uZ,
            center + uX - uY - uZ,
            center - uX + uY + uZ,
            center - uX + uY - uZ,
            center - uX - uY + uZ,
            center - uX - uY - uZ
        )
    }

    fun toAABB(): BoundingBox3D {
        val corners = getCorners()
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY

        for (c in corners) {
            if (c.x < minX) minX = c.x
            if (c.y < minY) minY = c.y
            if (c.z < minZ) minZ = c.z
            if (c.x > maxX) maxX = c.x
            if (c.y > maxY) maxY = c.y
            if (c.z > maxZ) maxZ = c.z
        }
        return BoundingBox3D(Vector3(minX, minY, minZ), Vector3(maxX, maxY, maxZ))
    }
}

data class CollisionResult(
    val hasCollision: Boolean,
    val overlapDepth: Float = 0f,
    val separationVector: Vector3 = Vector3(0f, 0f, 0f),
    val collidingEntityId: String? = null
)

object CollisionDetector {

    fun testOBBOBB(box1: OrientedBoundingBox, box2: OrientedBoundingBox): CollisionResult {
        val axes1 = box1.axes
        val axes2 = box2.axes
        val testAxes = mutableListOf<Vector3>()

        testAxes.add(axes1[0])
        testAxes.add(axes1[1])
        testAxes.add(axes1[2])

        testAxes.add(axes2[0])
        testAxes.add(axes2[1])
        testAxes.add(axes2[2])

        for (a in axes1) {
            for (b in axes2) {
                val crossAxis = a.cross(b)
                if (crossAxis.lengthSquared() > 0.001f) {
                    testAxes.add(crossAxis.normalized())
                }
            }
        }

        var minOverlap = Float.POSITIVE_INFINITY
        var smallestAxis = Vector3(0f, 1f, 0f)

        val translation = box2.center - box1.center

        for (axis in testAxes) {
            val normAxis = axis.normalized()
            val r1 = projectRadius(box1, normAxis)
            val r2 = projectRadius(box2, normAxis)
            val distance = abs(translation.dot(normAxis))

            val overlap = (r1 + r2) - distance
            if (overlap <= 0.001f) {
                return CollisionResult(hasCollision = false)
            }

            if (overlap < minOverlap) {
                minOverlap = overlap
                smallestAxis = if (translation.dot(normAxis) < 0f) normAxis * -1f else normAxis
            }
        }

        return CollisionResult(
            hasCollision = true,
            overlapDepth = minOverlap,
            separationVector = smallestAxis * minOverlap
        )
    }

    private fun projectRadius(box: OrientedBoundingBox, axis: Vector3): Float {
        val ax = box.axes
        return (box.halfExtents.x * abs(axis.dot(ax[0])) +
                box.halfExtents.y * abs(axis.dot(ax[1])) +
                box.halfExtents.z * abs(axis.dot(ax[2])))
    }

    fun checkWallCollision(box: OrientedBoundingBox, roomBounds: BoundingBox3D): Boolean {
        val corners = box.getCorners()
        for (c in corners) {
            if (c.x < roomBounds.min.x || c.x > roomBounds.max.x ||
                c.y < roomBounds.min.y || c.y > roomBounds.max.y ||
                c.z < roomBounds.min.z || c.z > roomBounds.max.z
            ) {
                return true
            }
        }
        return false
    }

    fun resolveSnapping(
        activeBox: OrientedBoundingBox,
        targetBox: OrientedBoundingBox,
        snapThresholdMm: Float = 40f
    ): Vector3? {
        val thresholdMeters = snapThresholdMm / 1000f
        val diff = targetBox.center - activeBox.center
        val ax = targetBox.axes[0]
        val az = targetBox.axes[2]

        val projectedX = diff.dot(ax)
        val targetSpacingX = activeBox.halfExtents.x + targetBox.halfExtents.x
        val gapX = abs(abs(projectedX) - targetSpacingX)

        if (gapX <= thresholdMeters) {
            val sign = if (projectedX >= 0) 1f else -1f
            val idealCenter = targetBox.center - (ax * (targetSpacingX * sign))
            return Vector3(idealCenter.x, activeBox.center.y, idealCenter.z)
        }

        val projectedZ = diff.dot(az)
        val targetSpacingZ = activeBox.halfExtents.z + targetBox.halfExtents.z
        val gapZ = abs(abs(projectedZ) - targetSpacingZ)

        if (gapZ <= thresholdMeters) {
            val sign = if (projectedZ >= 0) 1f else -1f
            val idealCenter = targetBox.center - (az * (targetSpacingZ * sign))
            return Vector3(idealCenter.x, activeBox.center.y, idealCenter.z)
        }

        return null
    }
}
