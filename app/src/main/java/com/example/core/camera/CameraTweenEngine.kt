package com.example.core.camera

import com.example.core.math.Vector3
import com.example.domain.model.KitchenElement
import com.example.domain.model.RoomStructure
import kotlin.math.*

/**
 * Mathematical Easing functions and cinematic interpolation engine for 3D camera navigation.
 */
object CameraTweenEngine {

    /**
     * Standard Cubic Ease-In-Out curve: Slow start, fast middle, smooth deceleration.
     */
    fun easeInOutCubic(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        return if (clamped < 0.5f) {
            4f * clamped * clamped * clamped
        } else {
            1f - (-2f * clamped + 2f).pow(3) / 2f
        }
    }

    /**
     * Quartic Ease-In-Out: Even smoother cinematic ramp.
     */
    fun easeInOutQuart(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        return if (clamped < 0.5f) {
            8f * clamped * clamped * clamped * clamped
        } else {
            1f - (-2f * clamped + 2f).pow(4) / 2f
        }
    }

    /**
     * Sine Ease-In-Out: Gentle organic motion.
     */
    fun easeInOutSine(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        return -((cos(Math.PI * clamped).toFloat() - 1f) / 2f)
    }

    /**
     * Shortest-path angular interpolation in degrees.
     * Prevents unnatural 360-degree spin loops when wrapping around 0°/360°.
     */
    fun lerpAngleDegrees(from: Float, to: Float, t: Float): Float {
        val diff = ((to - from + 180f) % 360f + 360f) % 360f - 180f
        val result = from + diff * t
        return (result % 360f + 360f) % 360f
    }

    /**
     * Scalar linear interpolation.
     */
    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /**
     * Interpolates smoothly between two camera poses using the specified easing function.
     */
    fun interpolatePose(
        start: CameraPose,
        target: CameraPose,
        progress: Float,
        easing: (Float) -> Float = ::easeInOutCubic
    ): CameraPose {
        val t = easing(progress.coerceIn(0f, 1f))

        return CameraPose(
            mode = if (progress >= 0.5f) target.mode else start.mode,
            eyePosition = start.eyePosition.lerp(target.eyePosition, t),
            targetCenter = start.targetCenter.lerp(target.targetCenter, t),
            yawDegrees = lerpAngleDegrees(start.yawDegrees, target.yawDegrees, t),
            pitchDegrees = lerp(start.pitchDegrees, target.pitchDegrees, t),
            zoomFactor = lerp(start.zoomFactor, target.zoomFactor, t),
            panOffset = start.panOffset.lerp(target.panOffset, t),
            eyeHeightMm = lerp(start.eyeHeightMm, target.eyeHeightMm, t)
        )
    }

    /**
     * Calculates the target camera pose for a given navigation preset.
     */
    fun computePresetPose(
        preset: CameraNavigationPreset,
        room: RoomStructure,
        selectedElement: KitchenElement? = null,
        currentPose: CameraPose = CameraPose()
    ): CameraPose {
        val roomCenter = Vector3(room.widthMm * 0.5f, room.heightMm * 0.35f, room.depthMm * 0.5f)

        return when (preset) {
            CameraNavigationPreset.PERSPECTIVE -> CameraPose(
                mode = CameraMode.ORBIT,
                targetCenter = roomCenter,
                yawDegrees = 35f,
                pitchDegrees = 30f,
                zoomFactor = 1.0f,
                panOffset = Vector3.ZERO
            )
            CameraNavigationPreset.ISOMETRIC -> CameraPose(
                mode = CameraMode.ORBIT,
                targetCenter = roomCenter,
                yawDegrees = 45f,
                pitchDegrees = 35.264f,
                zoomFactor = 1.1f,
                panOffset = Vector3.ZERO
            )
            CameraNavigationPreset.TOP_VIEW -> CameraPose(
                mode = CameraMode.TOP_DOWN,
                targetCenter = Vector3(room.widthMm * 0.5f, 0f, room.depthMm * 0.5f),
                yawDegrees = 0f,
                pitchDegrees = 89.5f,
                zoomFactor = 1.0f,
                panOffset = Vector3.ZERO
            )
            CameraNavigationPreset.FRONT_ELEVATION -> CameraPose(
                mode = CameraMode.ORBIT,
                targetCenter = Vector3(room.widthMm * 0.5f, 1000f, room.depthMm * 0.5f),
                yawDegrees = 0f,
                pitchDegrees = 5f,
                zoomFactor = 1.2f,
                panOffset = Vector3.ZERO
            )
            CameraNavigationPreset.SIDE_ELEVATION -> CameraPose(
                mode = CameraMode.ORBIT,
                targetCenter = Vector3(room.widthMm * 0.5f, 1000f, room.depthMm * 0.5f),
                yawDegrees = 90f,
                pitchDegrees = 5f,
                zoomFactor = 1.2f,
                panOffset = Vector3.ZERO
            )
            CameraNavigationPreset.FIRST_PERSON -> {
                val spawnPos = WalkthroughCollisionEngine.getSafeWalkSpawnPosition(room, emptyList())
                CameraPose(
                    mode = CameraMode.FIRST_PERSON_WALK,
                    eyePosition = spawnPos,
                    yawDegrees = 0f,
                    pitchDegrees = 0f,
                    eyeHeightMm = WalkthroughCollisionEngine.DEFAULT_EYE_HEIGHT_MM
                )
            }
            CameraNavigationPreset.FOCUS_SELECTION -> {
                if (selectedElement != null) {
                    val elemCenter = selectedElement.position + selectedElement.dimensions * 0.5f
                    val maxDim = max(selectedElement.dimensions.x, max(selectedElement.dimensions.y, selectedElement.dimensions.z))
                    val targetZoom = (1200f / max(300f, maxDim)).coerceIn(1.0f, 2.5f)
                    CameraPose(
                        mode = CameraMode.ORBIT,
                        targetCenter = elemCenter,
                        yawDegrees = currentPose.yawDegrees,
                        pitchDegrees = 25f,
                        zoomFactor = targetZoom,
                        panOffset = Vector3.ZERO
                    )
                } else {
                    computePresetPose(CameraNavigationPreset.PERSPECTIVE, room, null, currentPose)
                }
            }
        }
    }
}
