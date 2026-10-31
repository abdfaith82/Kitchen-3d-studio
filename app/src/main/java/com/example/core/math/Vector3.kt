package com.example.core.math

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-precision 3D Vector in millimeters.
 * Used for CAD positioning, dimensions, transformations, and distance checks.
 */
data class Vector3(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
) {
    operator fun plus(other: Vector3): Vector3 = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3): Vector3 = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float): Vector3 = Vector3(x * scalar, y * scalar, z * scalar)
    operator fun times(other: Vector3): Vector3 = Vector3(x * other.x, y * other.y, z * other.z)
    operator fun div(scalar: Float): Vector3 = if (scalar != 0f) Vector3(x / scalar, y / scalar, z / scalar) else ZERO
    operator fun unaryMinus(): Vector3 = Vector3(-x, -y, -z)

    fun dot(other: Vector3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vector3): Vector3 = Vector3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun lengthSquared(): Float = x * x + y * y + z * z
    fun length(): Float = sqrt(lengthSquared())

    fun normalized(): Vector3 {
        val len = length()
        return if (len > 1e-6f) this / len else ZERO
    }

    fun distanceTo(other: Vector3): Float = (this - other).length()

    fun distanceSquaredTo(other: Vector3): Float = (this - other).lengthSquared()

    fun lerp(target: Vector3, t: Float): Vector3 = Vector3(
        x + (target.x - x) * t,
        y + (target.y - y) * t,
        z + (target.z - z) * t
    )

    /**
     * Projects this vector onto a plane defined by its normal vector.
     */
    fun projectOntoPlane(planeNormal: Vector3): Vector3 {
        val normalNorm = planeNormal.normalized()
        val dist = this.dot(normalNorm)
        return this - (normalNorm * dist)
    }

    /**
     * Rotates vector around the vertical Y-axis by degrees.
     */
    fun rotateY(degrees: Float): Vector3 {
        val rad = Math.toRadians(degrees.toDouble()).toFloat()
        val cosA = cos(rad)
        val sinA = sin(rad)
        return Vector3(
            x = x * cosA + z * sinA,
            y = y,
            z = -x * sinA + z * cosA
        )
    }

    /**
     * Snaps vector components to 1 millimeter precision.
     */
    fun roundTo1mm(): Vector3 = Vector3(
        MathUtils.roundToMillimeter(x),
        MathUtils.roundToMillimeter(y),
        MathUtils.roundToMillimeter(z)
    )

    companion object {
        val ZERO = Vector3(0f, 0f, 0f)
        val ONE = Vector3(1f, 1f, 1f)
        val UNIT_X = Vector3(1f, 0f, 0f)
        val UNIT_Y = Vector3(0f, 1f, 0f)
        val UNIT_Z = Vector3(0f, 0f, 1f)
    }
}

operator fun Float.times(v: Vector3): Vector3 = v * this
