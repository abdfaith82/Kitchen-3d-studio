package com.example.core.perspective

import androidx.compose.ui.geometry.Offset
import com.example.core.math.Vector3
import kotlin.math.*

/**
 * 4-point Floor Calibration Quad on the uploaded image.
 * Defines the visible rectangular floor boundaries in image pixel coordinates.
 */
data class FloorCalibrationQuad(
    val frontLeft: Offset,
    val frontRight: Offset,
    val backRight: Offset,
    val backLeft: Offset,
    val floorWidthMm: Float = 3000f,
    val floorDepthMm: Float = 3000f
)

/**
 * Calibrated Camera Parameters derived from the uploaded room photo.
 */
data class CalibratedCamera(
    val cameraPosition: Vector3,
    val pitchDegrees: Float,
    val yawDegrees: Float,
    val focalLengthPx: Float,
    val horizonY: Float,
    val imageWidthPx: Float,
    val imageHeightPx: Float,
    val forward: Vector3 = Vector3(0f, -0.22f, -0.975f).normalized(),
    val right: Vector3 = Vector3.UNIT_X,
    val up: Vector3 = Vector3(0f, 0.975f, -0.22f).normalized()
) {
    /**
     * Projects a 3D coordinate (X, Y, Z) in real millimeters into 2D image coordinates (u, v) + depth.
     * In the kitchen coordinate system:
     *   - X ∈ [0..floorWidthMm] (left to right)
     *   - Y ∈ [0..heightMm] (floor to ceiling)
     *   - Z ∈ [0..floorDepthMm] (North back wall at Z=0, front at Z=floorDepthMm)
     */
    fun project3DWithDepth(worldPoint: Vector3): Pair<Offset, Float>? {
        val toPoint = worldPoint - cameraPosition
        val camZ = toPoint.dot(forward)
        val safeZ = if (camZ <= 40f) {
            if (camZ <= -350f) return null
            40f
        } else camZ

        val camX = toPoint.dot(right)
        val camY = toPoint.dot(up)

        val screenX = imageWidthPx * 0.5f + (camX / safeZ) * focalLengthPx
        val screenY = imageHeightPx * 0.5f - (camY / safeZ) * focalLengthPx
        return Pair(Offset(screenX, screenY), safeZ)
    }

    /**
     * Projects a 3D coordinate (X, Y, Z) in real millimeters into 2D image coordinates (u, v).
     */
    fun project3DToImage(worldPoint: Vector3): Offset? {
        return project3DWithDepth(worldPoint)?.first
    }

    /**
     * Unprojects an image screen touch (u, v) onto the horizontal plane (Y = targetElevationMm).
     * Computes the real-world (X, Z) in millimeters so user can place/drag cabinets on the photo/camera!
     */
    fun unprojectImageToFloorPlane(screenPoint: Offset, targetElevationMm: Float = 0f): Vector3? {
        if (imageWidthPx <= 1f || imageHeightPx <= 1f || focalLengthPx <= 1f) return null
        val nx = (screenPoint.x - imageWidthPx * 0.5f) / focalLengthPx
        val ny = (imageHeightPx * 0.5f - screenPoint.y) / focalLengthPx
        val rayDir = (forward + (right * nx) + (up * ny)).normalized()

        if (abs(rayDir.y) >= 0.01f) {
            val t = (targetElevationMm - cameraPosition.y) / rayDir.y
            if (t in 40f..35000f) {
                val hit = cameraPosition + (rayDir * t)
                return Vector3(hit.x, targetElevationMm, hit.z)
            }
        }
        return null
    }
}

/**
 * Computational photogrammetry engine for calibrating camera perspective from a single kitchen photo or live camera feed.
 */
object PerspectiveCalibrator {

    /**
     * Calibrates perspective from 4 interactive floor quad pins placed over the photo/camera.
     */
    fun calibrateFromFloorQuad(
        quad: FloorCalibrationQuad,
        imageWidth: Float,
        imageHeight: Float
    ): CalibratedCamera {
        val safeW = imageWidth.coerceAtLeast(100f)
        val safeH = imageHeight.coerceAtLeast(100f)

        // 1. Calculate Vanishing Point (intersection of left floor edge and right floor edge)
        val vpZ = lineIntersection(quad.frontLeft, quad.backLeft, quad.frontRight, quad.backRight)

        // Estimated Horizon Line elevation in image pixels
        val horizonY = vpZ?.y?.coerceIn(safeH * 0.15f, safeH * 0.62f) ?: (safeH * 0.38f)

        // 2. Camera Field of View & Focal Length estimation (natural architectural lens)
        val focalLengthPx = min(safeW, safeH) * 0.98f

        // 3. Compute Pitch Angle from Horizon Line position relative to image center
        val deltaY = (safeH * 0.5f) - horizonY
        val pitchRad = atan2(deltaY.toDouble(), focalLengthPx.toDouble()).toFloat()
        val pitchDegrees = Math.toDegrees(pitchRad.toDouble()).toFloat().coerceIn(8f, 38f)

        // 4. Compute Yaw Offset from Vanishing Point X relative to image center
        val vpX = vpZ?.x?.coerceIn(safeW * 0.2f, safeW * 0.8f) ?: (safeW * 0.5f)
        val yawRad = atan2(((vpX - safeW * 0.5f) * 0.35f).toDouble(), focalLengthPx.toDouble()).toFloat()
        val yawDegrees = Math.toDegrees(yawRad.toDouble()).toFloat().coerceIn(-25f, 25f)

        // 5. Place camera in front of the kitchen looking towards the North Wall (Z = 0)
        // so L-Shape, Wall, Tall, and Island cabinets appear centered and properly proportioned on the wall & floor!
        val roomW = quad.floorWidthMm.coerceAtLeast(2400f)
        val roomD = quad.floorDepthMm.coerceAtLeast(2400f)

        val quadSpanRatio = (abs(quad.frontRight.x - quad.frontLeft.x) / safeW).coerceIn(0.35f, 0.95f)
        val zoomFactor = (quadSpanRatio / 0.68f).coerceIn(0.65f, 1.35f)
        val distanceMm = (max(roomW, roomD) * 1.28f) / zoomFactor

        val target = Vector3(roomW * 0.5f, 1050f, roomD * 0.32f)
        val pitchAngleRad = Math.toRadians(pitchDegrees.toDouble()).toFloat()
        val cameraPos = Vector3(
            x = target.x - distanceMm * sin(yawRad) * 0.5f,
            y = (target.y + distanceMm * sin(pitchAngleRad) * 0.65f).coerceIn(1200f, 2500f),
            z = target.z + distanceMm * cos(pitchAngleRad)
        )

        val forward = (target - cameraPos).normalized()
        val worldUp = Vector3.UNIT_Y
        val r = forward.cross(worldUp)
        val right = if (r.lengthSquared() > 0.001f) r.normalized() else Vector3.UNIT_X
        val up = right.cross(forward).normalized()

        return CalibratedCamera(
            cameraPosition = cameraPos,
            pitchDegrees = pitchDegrees,
            yawDegrees = yawDegrees,
            focalLengthPx = focalLengthPx,
            horizonY = horizonY,
            imageWidthPx = safeW,
            imageHeightPx = safeH,
            forward = forward,
            right = right,
            up = up
        )
    }

    private fun lineIntersection(p1: Offset, p2: Offset, p3: Offset, p4: Offset): Offset? {
        val d = (p1.x - p2.x) * (p3.y - p4.y) - (p1.y - p2.y) * (p3.x - p4.x)
        if (abs(d) < 1e-4f) return null

        val xi = ((p3.x - p4.x) * (p1.x * p2.y - p1.y * p2.x) - (p1.x - p2.x) * (p3.x * p4.y - p3.y * p4.x)) / d
        val yi = ((p3.y - p4.y) * (p1.x * p2.y - p1.y * p2.x) - (p1.y - p2.y) * (p3.x * p4.y - p3.y * p4.x)) / d
        return Offset(xi, yi)
    }
}
