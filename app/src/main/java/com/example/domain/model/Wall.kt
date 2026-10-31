package com.example.domain.model

import com.example.core.math.Vector3

enum class WallSide(val title: String, val rotationY: Float) {
    NORTH("الجدار الشمالي (North)", 0f),
    EAST("الجدار الشرقي (East)", 90f),
    SOUTH("الجدار الجنوبي (South)", 180f),
    WEST("الجدار الغربي (West)", 270f)
}

/**
 * Types of MEP utility points distributed manually on kitchen walls
 * (Plumbing, Gas, Ventilation, and Electrical sockets in the 60cm backsplash zone).
 */
enum class UtilityPointType(
    val titleAr: String,
    val codeSymbol: String,
    val defaultElevationMm: Float,
    val isIn60cmBacksplashZone: Boolean,
    val colorHex: Long
) {
    PLUMBING_DRAIN(
        titleAr = "نقطة سباكة وصرف (Plumbing & Drain)",
        codeSymbol = "W/D",
        defaultElevationMm = 500f,
        isIn60cmBacksplashZone = false,
        colorHex = 0xFF0284C7L
    ),
    GAS_OUTLET(
        titleAr = "مخرج غاز طبيعي (Gas Outlet)",
        codeSymbol = "GAS",
        defaultElevationMm = 750f,
        isIn60cmBacksplashZone = false,
        colorHex = 0xFFF59E0BL
    ),
    ELECTRICAL_SOCKET(
        titleAr = "مقبس كهرباء / فيش (منطقة الـ 60 سم)",
        codeSymbol = "ELEC-60",
        defaultElevationMm = 1100f, // Inside 850..1450mm (60cm backsplash zone)
        isIn60cmBacksplashZone = true,
        colorHex = 0xFF10B981L
    ),
    VENTILATION_DUCT(
        titleAr = "مخرج تهوية وشفاط (Ventilation Duct)",
        codeSymbol = "VENT",
        defaultElevationMm = 2150f,
        isIn60cmBacksplashZone = false,
        colorHex = 0xFFA855F7L
    );

    val arabicTitle: String get() = titleAr
    val codeBadge: String get() = codeSymbol
    val mustStayInBacksplash60cmZone: Boolean get() = isIn60cmBacksplashZone
}

/**
 * Represents a manually positioned MEP service point on a specific wall.
 * All coordinates are in millimeters (and displayed in centimeters in executive blueprints).
 */
data class WallUtilityPoint(
    val id: String,
    val type: UtilityPointType,
    val offsetFromWallStartMm: Float, // Horizontal distance along wall from start corner
    val elevationFromFloorMm: Float = type.defaultElevationMm, // Height from floor (850..1450mm for 60cm zone sockets)
    val label: String = type.titleAr
) {
    val offsetMm: Float get() = offsetFromWallStartMm
    val elevationMm: Float get() = elevationFromFloorMm
    val offsetCm: Float get() = offsetFromWallStartMm / 10f
    val elevationCm: Float get() = elevationFromFloorMm / 10f
    val isInBacksplash60cmZone: Boolean get() = type.isIn60cmBacksplashZone || (elevationFromFloorMm in 850f..1450f)
}

data class Wall(
    val id: String,
    val side: WallSide,
    val lengthMm: Float,
    val heightMm: Float,
    val thicknessMm: Float = 150f,
    val cornerAngleDegrees: Float = 90f, // Allows 75°..135° to handle out-of-square building defects (معالجة عيوب البناء)
    val isEnabled: Boolean = true, // Supports L-Shape, U-Shape, or Rectangular active walls
    val openings: List<WallOpening> = emptyList(),
    val utilityPoints: List<WallUtilityPoint> = emptyList()
) {
    val lengthCm: Float get() = lengthMm / 10f

    /**
     * Inner surface normal pointing toward the inside of the kitchen,
     * adjusted by any custom corner angle deviation from 90 degrees.
     */
    val innerNormal: Vector3
        get() = when (side) {
            WallSide.NORTH -> Vector3(0f, 0f, 1f)
            WallSide.SOUTH -> Vector3(0f, 0f, -1f)
            WallSide.WEST -> Vector3(1f, 0f, 0f)
            WallSide.EAST -> Vector3(-1f, 0f, 0f)
        }
}
