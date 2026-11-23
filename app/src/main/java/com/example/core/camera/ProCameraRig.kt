package com.example.core.camera

import com.example.core.math.Vector3
import kotlin.math.*

/**
 * Professional camera rig — the "more 3D control" layer.
 *
 * The app already has [CameraMode] (orbit / walk / top-down) and tweened
 * presets. This file adds, without touching those types (so no existing
 * `when` expression breaks):
 *  - 5 pro shooting modes: turntable, dolly, crane, free-fly, cinematic orbit.
 *  - A keyframe camera path: record the current view as a keyframe, then
 *    play a smooth cinematic tour through all keyframes.
 *  - A 4-mode transform gizmo (move / rotate / scale / universal).
 *  - True lens FOV math shared with the cinematic engine.
 *
 * Playback is driven by the dialog: it samples [ProCameraPath.poseAt]
 * every frame and applies it with `ViewModel.setCameraPose`, so the rig
 * itself stays pure math with no Android dependency.
 */
enum class ProCameraMode(val titleAr: String, val titleEn: String, val descriptionAr: String) {
    TURNTABLE(
        "دوران العرض Turntable",
        "Turntable",
        "يدور المطبخ حول نفسه ببطء — مثالي لعرض التصميم على العميل"
    ),
    DOLLY(
        "دولي Dolly",
        "Dolly",
        "تقريب وابتعاد سينمائي على سكة مستقيمة نحو الجزيرة أو الرخام"
    ),
    CRANE(
        "رافعة Crane",
        "Crane",
        "صعود وهبوط عمودي من الأعلى — لقطة افتتاحية درامية"
    ),
    FREE_FLY(
        "طيران حر Free-Fly",
        "Free-Fly",
        "تحليق حر في كل الاتجاهات بلا قيود — استكشاف سريع للغرفة"
    ),
    CINEMATIC_ORBIT(
        "مدار سينمائي",
        "Cinematic Orbit",
        "مدار بطيء بزاوية منخفضة وعمق ميدان ضحل — لقطة فيلم معماري"
    )
}

/** Transform gizmo mode for the selected element (move / rotate / scale). */
enum class ProGizmoMode(val titleAr: String, val titleEn: String) {
    MOVE("تحريك", "Move"),
    ROTATE("تدوير", "Rotate"),
    SCALE("تكبير/تصغير", "Scale"),
    UNIVERSAL("شامل", "Universal")
}

/** One recorded camera view inside a cinematic path. */
data class ProCameraKeyframe(
    val yawDegrees: Float,
    val pitchDegrees: Float,
    val zoomFactor: Float,
    val panX: Float = 0f,
    val panY: Float = 0f,
    /** How long to hold this keyframe before moving to the next (ms). */
    val holdMs: Long = 800L
)

/**
 * A cinematic camera path through [keyframes].
 * Between two keyframes the camera eases in-out over [transitionMs].
 */
data class ProCameraPath(
    val keyframes: List<ProCameraKeyframe>,
    val transitionMs: Long = 1200L
) {
    val isPlayable: Boolean get() = keyframes.size >= 2

    fun totalDurationMs(): Long {
        if (keyframes.size < 2) return 0L
        return (keyframes.size - 1) * transitionMs + keyframes.drop(1).sumOf { it.holdMs }
    }

    /**
     * Interpolated pose at [elapsedMs] since playback started, or null when
     * the path is not playable / finished. Yaw uses the shortest angular
     * path so the camera never spins the long way round.
     */
    fun poseAt(elapsedMs: Long): CameraPose? {
        if (!isPlayable) return null
        var remaining = elapsedMs
        for (i in 0 until keyframes.size - 1) {
            val from = keyframes[i]
            val to = keyframes[i + 1]
            if (remaining < transitionMs) {
                val t = ProCameraRig.easeInOutCubic(remaining.toFloat() / transitionMs.toFloat())
                return CameraPose(
                    mode = CameraMode.ORBIT,
                    yawDegrees = ProCameraRig.lerpAngle(from.yawDegrees, to.yawDegrees, t),
                    pitchDegrees = from.pitchDegrees + (to.pitchDegrees - from.pitchDegrees) * t,
                    zoomFactor = from.zoomFactor + (to.zoomFactor - from.zoomFactor) * t,
                    panOffset = Vector3(
                        from.panX + (to.panX - from.panX) * t,
                        from.panY + (to.panY - from.panY) * t,
                        0f
                    )
                )
            }
            remaining -= transitionMs
            if (remaining < to.holdMs) {
                return CameraPose(
                    mode = CameraMode.ORBIT,
                    yawDegrees = to.yawDegrees,
                    pitchDegrees = to.pitchDegrees,
                    zoomFactor = to.zoomFactor,
                    panOffset = Vector3(to.panX, to.panY, 0f)
                )
            }
            remaining -= to.holdMs
        }
        val last = keyframes.last()
        return CameraPose(
            mode = CameraMode.ORBIT,
            yawDegrees = last.yawDegrees,
            pitchDegrees = last.pitchDegrees,
            zoomFactor = last.zoomFactor,
            panOffset = Vector3(last.panX, last.panY, 0f)
        )
    }
}

object ProCameraRig {

    /** Cubic ease-in-out, the standard cinematic easing curve. */
    fun easeInOutCubic(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
    }

    /** Shortest-path angular lerp (never spins the long way round). */
    fun lerpAngle(fromDeg: Float, toDeg: Float, t: Float): Float {
        var delta = (toDeg - fromDeg) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        val result = (fromDeg + delta * t) % 360f
        return if (result < 0f) result + 360f else result
    }

    /** Turntable yaw after [elapsedSec] at [speedDegPerSec] (default 12°/s). */
    fun turntableYaw(startYawDeg: Float, elapsedSec: Float, speedDegPerSec: Float = 12f): Float {
        val result = (startYawDeg + elapsedSec * speedDegPerSec) % 360f
        return if (result < 0f) result + 360f else result
    }

    /** Dolly: zoom factor for moving [dollyMm] closer (positive) or away. */
    fun dollyZoomFactor(currentZoom: Float, dollyMm: Float, baseDistanceMm: Float = 4000f): Float {
        val factor = (baseDistanceMm / (baseDistanceMm - dollyMm).coerceIn(800f, 8000f))
        return (currentZoom * factor).coerceIn(0.35f, 4.0f)
    }

    /** Crane: pitch for a vertical [liftMm] move (up = higher pitch). */
    fun cranePitch(currentPitchDeg: Float, liftMm: Float): Float =
        (currentPitchDeg + liftMm / 60f).coerceIn(5f, 89.8f)

    /** True horizontal FOV for a focal length on a 36 mm full-frame sensor. */
    fun fovForFocalLengthMm(focalMm: Float): Float =
        Math.toDegrees(2.0 * atan(18.0 / focalMm.coerceIn(14f, 200f).toDouble())).toFloat()

    /** Suggested pro mode for a given design task. */
    fun suggestedModeFor(taskAr: String): ProCameraMode = when {
        taskAr.contains("عميل") || taskAr.contains("عرض") -> ProCameraMode.TURNTABLE
        taskAr.contains("جزيرة") || taskAr.contains("رخام") -> ProCameraMode.DOLLY
        taskAr.contains("افتتاح") || taskAr.contains("عام") -> ProCameraMode.CRANE
        else -> ProCameraMode.CINEMATIC_ORBIT
    }
}
