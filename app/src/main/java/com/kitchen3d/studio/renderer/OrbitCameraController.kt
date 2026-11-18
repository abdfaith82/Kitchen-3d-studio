package com.kitchen3d.studio.renderer

import com.google.android.filament.Camera
import kotlin.math.cos
import kotlin.math.sin

/**
 * Touch-enabled orbit camera controller tailored for interior kitchen inspection.
 * Provides smooth yaw, pitch, zoom, and panning without gimbal locks.
 */
class OrbitCameraController(private val camera: Camera) {

    // Target look-at point (center of the kitchen)
    var targetX = 0.0f
    var targetY = 1.0f
    var targetZ = 0.0f

    // Spherical coordinates
    private var yawDeg = 45.0f
    private var pitchDeg = 25.0f
    private var distance = 4.2f

    private var aspectRatio = 16.0f / 9.0f
    private val fovDegrees = 58.0

    init {
        updateCameraTransform()
    }

    fun updateAspectRatio(ratio: Float) {
        aspectRatio = ratio
        camera.setProjection(
            fovDegrees,
            aspectRatio.toDouble(),
            0.1,
            50.0,
            Camera.Fov.VERTICAL
        )
    }

    fun onRotate(deltaX: Float, deltaY: Float) {
        yawDeg = (yawDeg + deltaX * 0.35f) % 360f
        pitchDeg = (pitchDeg - deltaY * 0.35f).coerceIn(-10.0f, 80.0f)
        updateCameraTransform()
    }

    fun onZoom(scaleFactor: Float) {
        distance = (distance / scaleFactor).coerceIn(1.2f, 8.5f)
        updateCameraTransform()
    }

    fun onPan(deltaX: Float, deltaY: Float) {
        val yawRad = Math.toRadians(yawDeg.toDouble())
        val rightX = cos(yawRad).toFloat()
        val rightZ = -sin(yawRad).toFloat()

        val speed = 0.0035f * distance
        targetX += (-rightX * deltaX) * speed
        targetZ += (-rightZ * deltaX) * speed
        targetY += (deltaY * speed).coerceIn(-0.5f, 3.0f)

        updateCameraTransform()
    }

    fun update() {
        // Continuous damping or animation hooks can be applied here
    }

    private fun updateCameraTransform() {
        val yawRad = Math.toRadians(yawDeg.toDouble())
        val pitchRad = Math.toRadians(pitchDeg.toDouble())

        val eyeX = targetX + (distance * cos(pitchRad) * sin(yawRad)).toFloat()
        val eyeY = targetY + (distance * sin(pitchRad)).toFloat()
        val eyeZ = targetZ + (distance * cos(pitchRad) * cos(yawRad)).toFloat()

        camera.lookAt(
            eyeX.toDouble(), eyeY.toDouble(), eyeZ.toDouble(),
            targetX.toDouble(), targetY.toDouble(), targetZ.toDouble(),
            0.0, 1.0, 0.0
        )
    }
}
