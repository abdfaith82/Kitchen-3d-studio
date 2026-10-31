package com.example.core.camera

import com.example.core.math.Vector3
import kotlin.math.*

/**
 * Immutable camera pose capturing complete spatial coordinates and orientation.
 */
data class CameraPose(
    val mode: CameraMode = CameraMode.ORBIT,
    val eyePosition: Vector3 = Vector3(2500f, 1600f, 2500f),
    val targetCenter: Vector3 = Vector3(2500f, 850f, 2500f),
    val yawDegrees: Float = 35f,
    val pitchDegrees: Float = 30f,
    val zoomFactor: Float = 1.0f,
    val panOffset: Vector3 = Vector3.ZERO,
    val eyeHeightMm: Float = 1600f
) {
    /**
     * Normalized forward vector pointing in the camera's line of sight.
     */
    fun forward(): Vector3 {
        return when (mode) {
            CameraMode.FIRST_PERSON_WALK -> {
                val yawRad = Math.toRadians(yawDegrees.toDouble()).toFloat()
                val pitchRad = Math.toRadians(pitchDegrees.toDouble()).toFloat()
                Vector3(
                    x = cos(pitchRad) * sin(yawRad),
                    y = sin(pitchRad),
                    z = cos(pitchRad) * cos(yawRad)
                ).normalized()
            }
            else -> {
                val diff = targetCenter + panOffset - eyePosition
                if (diff.lengthSquared() > 0.001f) diff.normalized() else Vector3(0f, 0f, 1f)
            }
        }
    }

    /**
     * Normalized horizontal forward vector (XZ floor plane) for walking.
     */
    fun walkForward(): Vector3 {
        val yawRad = Math.toRadians(yawDegrees.toDouble()).toFloat()
        return Vector3(sin(yawRad), 0f, cos(yawRad)).normalized()
    }

    /**
     * Normalized horizontal right strafe vector (XZ floor plane) for walking.
     */
    fun walkRight(): Vector3 {
        val yawRad = Math.toRadians(yawDegrees.toDouble()).toFloat()
        return Vector3(cos(yawRad), 0f, -sin(yawRad)).normalized()
    }

    /**
     * Right vector perpendicular to forward and world up.
     */
    fun right(): Vector3 {
        val fwd = forward()
        val worldUp = Vector3.UNIT_Y
        val r = fwd.cross(worldUp)
        return if (r.lengthSquared() > 0.001f) r.normalized() else Vector3.UNIT_X
    }

    /**
     * Up vector perpendicular to right and forward.
     */
    fun up(): Vector3 {
        return right().cross(forward()).normalized()
    }
}
