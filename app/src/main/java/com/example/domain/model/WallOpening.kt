package com.example.domain.model

enum class OpeningType(val title: String) {
    DOOR("باب غرفة / Door"),
    WINDOW("نافذة إضاءة / Window")
}

/**
 * Represents an opening (Door or Window) placed on a wall that dynamically cuts into the wall mesh.
 * All measurements in millimeters.
 */
data class WallOpening(
    val id: String,
    val type: OpeningType,
    val offsetMm: Float, // Distance from wall's start edge to opening's start edge
    val widthMm: Float, // Clear opening width (e.g. 900mm for door, 1200mm for window)
    val heightMm: Float, // Clear opening height (e.g. 2100mm for door, 1200mm for window)
    val sillHeightMm: Float = 0f // Sill height from floor (0 for doors, ~900mm for windows)
) {
    val endOffsetMm: Float get() = offsetMm + widthMm
    val lintelHeightMm: Float get() = sillHeightMm + heightMm
}
