package com.example.core.camera

import com.example.core.math.Vector3
import com.example.domain.model.KitchenElement
import com.example.domain.model.RoomStructure
import kotlin.math.*

/**
 * Controller managing gestures, inertia, walking collision, and pose transitions.
 */
class CameraNavigationController(
    var currentPose: CameraPose = CameraPose()
) {
    // Dynamic inertia momentum tracking
    private var orbitVelocityYaw: Float = 0f
    private var orbitVelocityPitch: Float = 0f
    private var panVelocityX: Float = 0f
    private var panVelocityY: Float = 0f
    private var zoomVelocity: Float = 0f

    // Configuration parameters
    var orbitSensitivity: Float = 0.35f
    var panSensitivity: Float = 1.2f
    var walkSpeedMmPerSec: Float = 1600f // 1.6 m/s comfortable walking speed
    var lookSensitivity: Float = 0.25f
    var inertiaDecay: Float = 0.90f
    var minVelocityThreshold: Float = 0.01f

    /**
     * Translates 1-finger drag into Orbit rotation with momentum tracking.
     */
    fun onOrbitDrag(deltaX: Float, deltaY: Float) {
        val dyaw = -deltaX * orbitSensitivity
        val dpitch = deltaY * orbitSensitivity

        orbitVelocityYaw = dyaw
        orbitVelocityPitch = dpitch

        applyOrbitDelta(dyaw, dpitch)
    }

    /**
     * Translates 2-finger drag into Pan translation with momentum tracking.
     */
    fun onPanDrag(deltaX: Float, deltaY: Float) {
        val right = currentPose.right()
        val up = currentPose.up()
        val panDelta = (right * (-deltaX * panSensitivity)) + (up * (deltaY * panSensitivity))

        panVelocityX = deltaX * panSensitivity
        panVelocityY = deltaY * panSensitivity

        currentPose = currentPose.copy(
            panOffset = currentPose.panOffset + panDelta
        )
    }

    /**
     * Translates pinch gesture into smooth zoom.
     */
    fun onPinchZoom(zoomMultiplier: Float) {
        val newZoom = (currentPose.zoomFactor * zoomMultiplier).coerceIn(0.35f, 4.0f)
        zoomVelocity = newZoom - currentPose.zoomFactor
        currentPose = currentPose.copy(zoomFactor = newZoom)
    }

    /**
     * Applies inertia decay step (called per frame or tick).
     * Returns true if there is still active motion.
     */
    fun tickInertia(): Boolean {
        var hasActiveMotion = false

        if (abs(orbitVelocityYaw) > minVelocityThreshold || abs(orbitVelocityPitch) > minVelocityThreshold) {
            applyOrbitDelta(orbitVelocityYaw, orbitVelocityPitch)
            orbitVelocityYaw *= inertiaDecay
            orbitVelocityPitch *= inertiaDecay
            hasActiveMotion = true
        } else {
            orbitVelocityYaw = 0f
            orbitVelocityPitch = 0f
        }

        return hasActiveMotion
    }

    /**
     * Handles first-person walking movement along camera forward/right directions,
     * resolved through the collision engine against walls and cabinets.
     */
    fun onWalkMove(
        forwardInput: Float,
        strafeInput: Float,
        deltaTimeSec: Float,
        room: RoomStructure,
        elements: List<KitchenElement>,
        isSprinting: Boolean = false
    ): WalkCollisionResult {
        val speedMultiplier = if (isSprinting) 2.0f else 1.0f
        val distance = walkSpeedMmPerSec * speedMultiplier * deltaTimeSec

        val fwdVec = currentPose.walkForward() * (forwardInput * distance)
        val rightVec = currentPose.walkRight() * (strafeInput * distance)
        val movementVector = fwdVec + rightVec

        val candidatePos = currentPose.eyePosition + movementVector

        val collisionResult = WalkthroughCollisionEngine.resolveMovement(
            currentPos = currentPose.eyePosition,
            candidatePos = candidatePos,
            room = room,
            elements = elements,
            eyeHeightMm = currentPose.eyeHeightMm
        )

        currentPose = currentPose.copy(
            eyePosition = collisionResult.resolvedPosition
        )

        return collisionResult
    }

    /**
     * Handles look rotation during first-person walkthrough.
     */
    fun onWalkLook(deltaX: Float, deltaY: Float) {
        val newYaw = (currentPose.yawDegrees + deltaX * lookSensitivity) % 360f
        val newPitch = (currentPose.pitchDegrees - deltaY * lookSensitivity).coerceIn(-75f, 75f)

        currentPose = currentPose.copy(
            yawDegrees = (newYaw + 360f) % 360f,
            pitchDegrees = newPitch
        )
    }

    /**
     * Clamps and applies orbit rotation deltas.
     */
    private fun applyOrbitDelta(dyaw: Float, dpitch: Float) {
        val newYaw = (currentPose.yawDegrees + dyaw) % 360f
        val maxPitch = if (currentPose.mode == CameraMode.TOP_DOWN) 89.8f else 88.0f
        val minPitch = if (currentPose.mode == CameraMode.TOP_DOWN) 75.0f else 5.0f
        val newPitch = (currentPose.pitchDegrees + dpitch).coerceIn(minPitch, maxPitch)

        currentPose = currentPose.copy(
            yawDegrees = (newYaw + 360f) % 360f,
            pitchDegrees = newPitch
        )
    }

    fun stopInertia() {
        orbitVelocityYaw = 0f
        orbitVelocityPitch = 0f
        panVelocityX = 0f
        panVelocityY = 0f
        zoomVelocity = 0f
    }
}
