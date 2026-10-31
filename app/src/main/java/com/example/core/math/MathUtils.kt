package com.example.core.math

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Computational geometry utilities for CAD precision and angle conversions.
 */
object MathUtils {
    const val EPSILON = 0.001f // 1 micrometer/millimeter tolerance

    fun isNearlyEqual(a: Float, b: Float, tolerance: Float = EPSILON): Boolean {
        return abs(a - b) <= tolerance
    }

    fun isNearlyZero(value: Float, tolerance: Float = EPSILON): Boolean {
        return abs(value) <= tolerance
    }

    fun roundToMillimeter(value: Float): Float {
        return value.roundToInt().toFloat()
    }

    fun clamp(value: Float, min: Float, max: Float): Float {
        return when {
            value < min -> min
            value > max -> max
            else -> value
        }
    }

    fun normalizeAngle(degrees: Float): Float {
        var angle = degrees % 360f
        if (angle < 0f) angle += 360f
        return angle
    }

    fun snapAngleToStep(degrees: Float, step: Float = 15f): Float {
        val normalized = normalizeAngle(degrees)
        return (normalized / step).roundToInt() * step
    }
}
