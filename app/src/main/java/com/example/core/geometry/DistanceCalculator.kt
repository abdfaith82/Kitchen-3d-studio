package com.example.core.geometry

import kotlin.math.*

data class Vector3(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
) {
    operator fun plus(other: Vector3): Vector3 = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3): Vector3 = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float): Vector3 = Vector3(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float): Vector3 = Vector3(x / scalar, y / scalar, z / scalar)

    fun length(): Float = sqrt(x * x + y * y + z * z)
    fun lengthSquared(): Float = x * x + y * y + z * z

    fun normalized(): Vector3 {
        val len = length()
        return if (len > 0.00001f) this / len else Vector3(0f, 0f, 0f)
    }

    fun dot(other: Vector3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vector3): Vector3 = Vector3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun distanceTo(other: Vector3): Float = (this - other).length()

    fun distanceTo2D(other: Vector3): Float {
        val dx = x - other.x
        val dz = z - other.z
        return sqrt(dx * dx + dz * dz)
    }

    fun rotateY(degrees: Float): Vector3 {
        val rad = Math.toRadians(degrees.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        return Vector3(
            x = x * cosA + z * sinA,
            y = y,
            z = -x * sinA + z * cosA
        )
    }
}

data class Ray3D(
    val origin: Vector3,
    val direction: Vector3
)

data class BoundingBox3D(
    val min: Vector3,
    val max: Vector3
) {
    val center: Vector3
        get() = Vector3(
            (min.x + max.x) * 0.5f,
            (min.y + max.y) * 0.5f,
            (min.z + max.z) * 0.5f
        )

    val size: Vector3
        get() = Vector3(
            abs(max.x - min.x),
            abs(max.y - min.y),
            abs(max.z - min.z)
        )

    fun intersects(other: BoundingBox3D): Boolean {
        return (min.x <= other.max.x && max.x >= other.min.x) &&
                (min.y <= other.max.y && max.y >= other.min.y) &&
                (min.z <= other.max.z && max.z >= other.min.z)
    }

    fun contains(point: Vector3): Boolean {
        return point.x in min.x..max.x &&
                point.y in min.y..max.y &&
                point.z in min.z..max.z
    }

    fun expanded(margin: Float): BoundingBox3D {
        val delta = Vector3(margin, margin, margin)
        return BoundingBox3D(min - delta, max + delta)
    }
}

object DistanceCalculator {

    fun distancePointToSegment(point: Vector3, segStart: Vector3, segEnd: Vector3): Float {
        val segment = segEnd - segStart
        val segLengthSq = segment.lengthSquared()
        if (segLengthSq < 0.0001f) return point.distanceTo(segStart)

        val t = ((point - segStart).dot(segment) / segLengthSq).coerceIn(0f, 1f)
        val projection = segStart + (segment * t)
        return point.distanceTo(projection)
    }

    fun distancePointToPlane(point: Vector3, planePoint: Vector3, planeNormal: Vector3): Float {
        val n = planeNormal.normalized()
        return abs((point - planePoint).dot(n))
    }

    fun intersectRayAABB(ray: Ray3D, box: BoundingBox3D): Float? {
        var tMin = (box.min.x - ray.origin.x) / if (ray.direction.x != 0f) ray.direction.x else 0.00001f
        var tMax = (box.max.x - ray.origin.x) / if (ray.direction.x != 0f) ray.direction.x else 0.00001f
        if (tMin > tMax) {
            val tmp = tMin; tMin = tMax; tMax = tmp
        }

        var tyMin = (box.min.y - ray.origin.y) / if (ray.direction.y != 0f) ray.direction.y else 0.00001f
        var tyMax = (box.max.y - ray.origin.y) / if (ray.direction.y != 0f) ray.direction.y else 0.00001f
        if (tyMin > tyMax) {
            val tmp = tyMin; tyMin = tyMax; tyMax = tmp
        }

        if (tMin > tyMax || tyMin > tMax) return null

        if (tyMin > tMin) tMin = tyMin
        if (tyMax < tMax) tMax = tyMax

        var tzMin = (box.min.z - ray.origin.z) / if (ray.direction.z != 0f) ray.direction.z else 0.00001f
        var tzMax = (box.max.z - ray.origin.z) / if (ray.direction.z != 0f) ray.direction.z else 0.00001f
        if (tzMin > tzMax) {
            val tmp = tzMin; tzMin = tzMax; tzMax = tmp
        }

        if (tMin > tzMax || tzMin > tMax) return null

        if (tzMin > tMin) tMin = tzMin
        if (tzMax < tMax) tMax = tzMax

        return if (tMax < 0f) null else if (tMin >= 0f) tMin else tMax
    }

    fun distanceBetweenBoxes(boxA: BoundingBox3D, boxB: BoundingBox3D): Float {
        val dx = max(0f, max(boxA.min.x - boxB.max.x, boxB.min.x - boxA.max.x))
        val dy = max(0f, max(boxA.min.y - boxB.max.y, boxB.min.y - boxA.max.y))
        val dz = max(0f, max(boxA.min.z - boxB.max.z, boxB.min.z - boxA.max.z))
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun trianglePerimeter(p1: Vector3, p2: Vector3, p3: Vector3): Float {
        return p1.distanceTo2D(p2) + p2.distanceTo2D(p3) + p3.distanceTo2D(p1)
    }
}
