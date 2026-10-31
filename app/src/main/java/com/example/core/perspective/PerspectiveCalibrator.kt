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
    val imageHeightPx: Float
) {
    /**
     * Projects a 3D coordinate (X, Y, Z) in real millimeters into 2D image coordinates (u, v).
     */
    fun project3DToImage(worldPoint: Vector3): Offset? {
        val relX = worldPoint.x - cameraPosition.x
        val relY = worldPoint.y - cameraPosition.y
        val relZ = worldPoint.z - cameraPosition.z

        val pitchRad = Math.toRadians(pitchDegrees.toDouble()).toFloat()
        val cosP = cos(pitchRad)
        val sinP = sin(pitchRad)

        // Rotate by camera pitch
        val camY = relY * cosP - relZ * sinP
        val camZ = relY * sinP + relZ * cosP
        val camX = relX

        if (camZ <= 50f) return null // behind camera

        val screenX = imageWidthPx * 0.5f + (camX / camZ) * focalLengthPx
        val screenY = horizonY - (camY / camZ) * focalLengthPx

        return Offset(screenX, screenY)
    }

    /**
     * Unprojects an image screen touch (u, v) onto the floor plane (Y = targetElevationMm).
     * Computes the real-world (X, Z) in millimeters so user can place/drag cabinets on the photo!
     */
    fun unprojectImageToFloorPlane(screenPoint: Offset, targetElevationMm: Float = 0f): Vector3? {
        val pitchRad = Math.toRadians(pitchDegrees.toDouble()).toFloat()
        val cosP = cos(pitchRad)
        val sinP = sin(pitchRad)

        val normX = (screenPoint.x - imageWidthPx * 0.5f) / focalLengthPx
        val normY = (horizonY - screenPoint.y) / focalLengthPx

        // Ray direction in camera space: (normX, normY, 1.0)
        // Transform ray to world space
        val dirX = normX
        val dirY = normY * cosP + 1.0f * sinP
        val dirZ = -normY * sinP + 1.0f * cosP

        if (abs(dirY) < 1e-4f) return null // parallel to floor

        val t = (targetElevationMm - cameraPosition.y) / dirY
        if (t <= 0f) return null // points away from floor

        val worldX = cameraPosition.x + dirX * t
        val worldZ = cameraPosition.z + dirZ * t

        return Vector3(worldX, targetElevationMm, worldZ)
    }
}

/**
 * Computational photogrammetry engine for calibrating camera perspective from a single kitchen photo.
 */
object PerspectiveCalibrator {

    /**
     * Calibrates perspective from 4 interactive floor quad pins placed over the photo.
     */
    fun calibrateFromFloorQuad(
        quad: FloorCalibrationQuad,
        imageWidth: Float,
        imageHeight: Float
    ): CalibratedCamera {
        // 1. Calculate Vanishing Point (intersection of left line and right line)
        val leftLineP1 = quad.frontLeft
        val leftLineP2 = quad.backLeft
        val rightLineP1 = quad.frontRight
        val rightLineP2 = quad.backRight

        val vpZ = lineIntersection(leftLineP1, leftLineP2, rightLineP1, rightLineP2)

        // Estimated Horizon Line elevation in image pixels
        val horizonY = vpZ?.y?.coerceIn(imageHeight * 0.1f, imageHeight * 0.65f) ?: (imageHeight * 0.4f)

        // 2. Camera Field of View & Focal Length estimation
        val defaultVerticalFov = 58.0f // typical modern mobile wide camera
        val fovRad = Math.toRadians(defaultVerticalFov.toDouble()).toFloat()
        val focalLengthPx = (imageHeight * 0.5f) / tan(fovRad * 0.5f)

        // 3. Compute Pitch Angle from Horizon Line position relative to image center
        val deltaY = (imageHeight * 0.5f) - horizonY
        val pitchRad = atan2(deltaY.toDouble(), focalLengthPx.toDouble()).toFloat()
        val pitchDegrees = Math.toDegrees(pitchRad.toDouble()).toFloat()

        // 4. Compute Camera Elevation & Distance from real floor quad dimensions
        val frontWidthPx = abs(quad.frontRight.x - quad.frontLeft.x)
        val estimatedDistToFrontMm = (quad.floorWidthMm * focalLengthPx) / max(10f, frontWidthPx)
        val cameraHeightMm = max(900f, estimatedDistToFrontMm * sin(pitchRad).coerceAtLeast(0.2f))

        val cameraPos = Vector3(
            x = quad.floorWidthMm * 0.5f,
            y = cameraHeightMm,
            z = -estimatedDistToFrontMm * cos(pitchRad)
        )

        return CalibratedCamera(
            cameraPosition = cameraPos,
            pitchDegrees = pitchDegrees.coerceIn(5f, 60f),
            yawDegrees = 0f,
            focalLengthPx = focalLengthPx,
            horizonY = horizonY,
            imageWidthPx = imageWidth,
            imageHeightPx = imageHeight
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
