package com.example.core.math

import kotlin.math.cos
import kotlin.math.sin

/**
 * 4x4 Transformation Matrix for 3D graphics and Filament coordinate conversions.
 * Stored in row-major order: elements[row * 4 + col]
 */
class Matrix4(val data: FloatArray = FloatArray(16)) {

    init {
        if (data.all { it == 0f }) {
            setIdentity()
        }
    }

    fun setIdentity(): Matrix4 {
        for (i in 0 until 16) data[i] = 0f
        data[0] = 1f
        data[5] = 1f
        data[10] = 1f
        data[15] = 1f
        return this
    }

    fun get(row: Int, col: Int): Float = data[row * 4 + col]
    fun set(row: Int, col: Int, value: Float) {
        data[row * 4 + col] = value
    }

    operator fun times(other: Matrix4): Matrix4 {
        val result = Matrix4(FloatArray(16))
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) {
                    sum += this.get(row, k) * other.get(k, col)
                }
                result.set(row, col, sum)
            }
        }
        return result
    }

    /**
     * Transforms a 3D point (w = 1.0)
     */
    fun transformPoint(point: Vector3): Vector3 {
        val x = data[0] * point.x + data[1] * point.y + data[2] * point.z + data[3]
        val y = data[4] * point.x + data[5] * point.y + data[6] * point.z + data[7]
        val z = data[8] * point.x + data[9] * point.y + data[10] * point.z + data[11]
        val w = data[12] * point.x + data[13] * point.y + data[14] * point.z + data[15]
        return if (w != 0f && w != 1f) Vector3(x / w, y / w, z / w) else Vector3(x, y, z)
    }

    /**
     * Transforms a 3D direction vector (w = 0.0, ignores translation)
     */
    fun transformDirection(dir: Vector3): Vector3 {
        val x = data[0] * dir.x + data[1] * dir.y + data[2] * dir.z
        val y = data[4] * dir.x + data[5] * dir.y + data[6] * dir.z
        val z = data[8] * dir.x + data[9] * dir.y + data[10] * dir.z
        return Vector3(x, y, z)
    }

    companion object {
        fun identity(): Matrix4 = Matrix4()

        fun translation(tx: Float, ty: Float, tz: Float): Matrix4 {
            val m = Matrix4()
            m.set(0, 3, tx)
            m.set(1, 3, ty)
            m.set(2, 3, tz)
            return m
        }

        fun translation(v: Vector3): Matrix4 = translation(v.x, v.y, v.z)

        fun rotationY(degrees: Float): Matrix4 {
            val m = Matrix4()
            val rad = Math.toRadians(degrees.toDouble()).toFloat()
            val cosA = cos(rad)
            val sinA = sin(rad)
            m.set(0, 0, cosA)
            m.set(0, 2, sinA)
            m.set(2, 0, -sinA)
            m.set(2, 2, cosA)
            return m
        }

        fun scale(sx: Float, sy: Float, sz: Float): Matrix4 {
            val m = Matrix4()
            m.set(0, 0, sx)
            m.set(1, 1, sy)
            m.set(2, 2, sz)
            return m
        }

        fun scale(v: Vector3): Matrix4 = scale(v.x, v.y, v.z)

        /**
         * Composes TRS (Translation * Rotation * Scale) matrix
         */
        fun trs(translation: Vector3, rotationYDegrees: Float, scale: Vector3): Matrix4 {
            return translation(translation) * rotationY(rotationYDegrees) * scale(scale)
        }
    }
}
