package com.example.core.geometry

import kotlin.math.*

enum class CabinetMountingCategory {
    BASE_CABINET,
    WALL_UPPER_CABINET,
    TALL_PANTRY_CABINET,
    ISLAND_CABINET,
    COUNTERTOP_SURFACE,
    RANGE_HOOD
}

data class ElevationSpec(
    val basePlinthHeight: Float = 0.10f,
    val baseCabinetCarcassHeight: Float = 0.72f,
    val countertopThickness: Float = 0.04f,
    val backSplashClearanceHeight: Float = 0.60f,
    val upperWallCabinetHeight: Float = 0.72f,
    val minHoodClearanceGas: Float = 0.75f,
    val minHoodClearanceElectric: Float = 0.65f
) {
    val finishedCountertopElevation: Float
        get() = basePlinthHeight + baseCabinetCarcassHeight + countertopThickness

    val standardUpperCabinetBottomElevation: Float
        get() = finishedCountertopElevation + backSplashClearanceHeight

    val standardUpperCabinetTopElevation: Float
        get() = standardUpperCabinetBottomElevation + upperWallCabinetHeight
}

object ElevationAndSafetyEngine {

    val defaultSpec = ElevationSpec()

    fun calculateSnapElevation(category: CabinetMountingCategory, spec: ElevationSpec = defaultSpec): Float {
        return when (category) {
            CabinetMountingCategory.BASE_CABINET -> spec.basePlinthHeight
            CabinetMountingCategory.COUNTERTOP_SURFACE -> spec.basePlinthHeight + spec.baseCabinetCarcassHeight
            CabinetMountingCategory.WALL_UPPER_CABINET -> spec.standardUpperCabinetBottomElevation
            CabinetMountingCategory.TALL_PANTRY_CABINET -> spec.basePlinthHeight
            CabinetMountingCategory.ISLAND_CABINET -> spec.basePlinthHeight
            CabinetMountingCategory.RANGE_HOOD -> spec.finishedCountertopElevation + spec.minHoodClearanceGas
        }
    }

    fun checkHoodSafetyDistance(
        cooktopY: Float,
        hoodBottomY: Float,
        isGasBurner: Boolean = true,
        spec: ElevationSpec = defaultSpec
    ): ErgonomicDiagnostic? {
        val clearance = hoodBottomY - cooktopY
        val required = if (isGasBurner) spec.minHoodClearanceGas else spec.minHoodClearanceElectric

        return if (clearance < required) {
            ErgonomicDiagnostic(
                code = "SAFETY_HOOD_FIRE_HAZARD",
                title = "مسافة شفاط المطبخ غير مطابقة لشروط السلامة",
                description = "المسافة الرأسية بين الموقد والشفاط (${"%.2f".format(clearance)}m) أقل من الحد الأدنى للأمان (${"%.2f".format(required)}m). قد يؤدي ذلك لمخاطر الاشتعال وتلف فلاتر الشفاط.",
                severity = DiagnosticSeverity.ERROR,
                suggestedCorrection = "رفع الشفاط إلى ارتفاع لا يقل عن ${"%.2f".format(required)}m فوق سطح الموقد."
            )
        } else {
            null
        }
    }
}
