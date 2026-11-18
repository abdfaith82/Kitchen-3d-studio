package com.example.core.geometry

import kotlin.math.*

enum class FillerOrientation {
    VERTICAL,
    HORIZONTAL_CORNER
}

data class CabinetFillerPiece(
    val id: String,
    val position: Vector3,
    val width: Float,
    val height: Float,
    val depth: Float,
    val rotationY: Float,
    val orientation: FillerOrientation,
    val reasonDescription: String
)

object AutomaticFillerEngine {

    const val MINIMUM_WALL_CLEARANCE_FOR_HANDLES = 0.050f
    const val CORNER_DEAD_SPACE_FILLER_WIDTH = 0.070f

    fun computeWallSideFiller(
        cabinetBox: OrientedBoundingBox,
        wallNormal: Vector3,
        distanceToWall: Float,
        cabinetHeight: Float,
        cabinetDepth: Float
    ): CabinetFillerPiece? {
        if (distanceToWall in 0.005f..0.150f) {
            val fillerThickness = distanceToWall
            val offsetDir = wallNormal * -1f
            val fillerPos = cabinetBox.center + (offsetDir * (cabinetBox.halfExtents.x + fillerThickness * 0.5f))

            return CabinetFillerPiece(
                id = "filler_wall_${System.currentTimeMillis()}",
                position = fillerPos,
                width = fillerThickness,
                height = cabinetHeight,
                depth = cabinetDepth,
                rotationY = cabinetBox.rotationYDegrees,
                orientation = FillerOrientation.VERTICAL,
                reasonDescription = "سدادة جانبية (Filler Strip) لإغلاق الفراغ ومنع احتكاك المقابض بالجدار عند الفتح"
            )
        }
        return null
    }

    fun computeCornerFillers(
        cornerBoxA: OrientedBoundingBox,
        cornerBoxB: OrientedBoundingBox,
        depth: Float = 0.60f,
        height: Float = 0.82f
    ): List<CabinetFillerPiece> {
        val fillers = mutableListOf<CabinetFillerPiece>()
        val angleDiff = abs(cornerBoxA.rotationYDegrees - cornerBoxB.rotationYDegrees) % 360f

        val isOrthogonal = abs(angleDiff - 90f) < 5f || abs(angleDiff - 270f) < 5f
        if (isOrthogonal) {
            val cornerCenter = (cornerBoxA.center + cornerBoxB.center) * 0.5f

            fillers.add(
                CabinetFillerPiece(
                    id = "filler_corner_a_${System.currentTimeMillis()}",
                    position = cornerCenter,
                    width = CORNER_DEAD_SPACE_FILLER_WIDTH,
                    height = height,
                    depth = depth,
                    rotationY = cornerBoxA.rotationYDegrees,
                    orientation = FillerOrientation.HORIZONTAL_CORNER,
                    reasonDescription = "فاصل زاوية 90 درجة لمنع تصادم أبواب وأدراج الخزانتين المتقاطعتين"
                )
            )
        }
        return fillers
    }
}
