package com.example.core.units

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Supports toggling between Metric (mm / cm / m / m²) and Imperial (in / ft / sq.ft)
 * across the 2D floorplan, 3D gizmos, Virtual Ruler, and Bill of Materials cost estimator.
 */
enum class MeasurementUnitSystem(
    val displayNameAr: String,
    val badgeLabelAr: String,
    val lengthSuffixAr: String,
    val linearRateLabelAr: String,
    val areaRateLabelAr: String
) {
    METRIC(
        displayNameAr = "النظام المتري (سم / مم / م²)",
        badgeLabelAr = "متري (سم/مم)",
        lengthSuffixAr = "سم",
        linearRateLabelAr = "$ / متر طولي",
        areaRateLabelAr = "$ / م²"
    ),
    IMPERIAL(
        displayNameAr = "النظام الإمبراطوري (بوصة / قدم / ft²)",
        badgeLabelAr = "إمبراطوري (in/ft)",
        lengthSuffixAr = "in",
        linearRateLabelAr = "$ / ft طولي",
        areaRateLabelAr = "$ / sq.ft"
    );

    fun toggle(): MeasurementUnitSystem = if (this == METRIC) IMPERIAL else METRIC

    companion object {
        const val MM_PER_INCH = 25.4f
        const val MM_PER_FOOT = 304.8f
        const val METERS_TO_FEET = 3.28084f
        const val SQ_METERS_TO_SQ_FEET = 10.7639f

        // Base manufacturing rates
        const val BASE_RATE_PER_LINEAR_METER_USD = 320f
        const val COUNTERTOP_RATE_PER_SQ_METER_USD = 185f
        const val UPPER_CABINET_UNIT_AVERAGE_USD = 145f

        /**
         * Formats a length in millimeters into either Metric ("80 سم" or "800 مم")
         * or Imperial ("31.5\"" or "2' 7.5\"").
         */
        fun formatLength(
            mm: Float,
            unitSystem: MeasurementUnitSystem,
            preferCmInMetric: Boolean = true
        ): String {
            return when (unitSystem) {
                METRIC -> {
                    if (preferCmInMetric) {
                        val cm = mm / 10f
                        if (cm % 1f == 0f) {
                            "${cm.roundToInt()} سم"
                        } else {
                            String.format(Locale.US, "%.1f سم", cm)
                        }
                    } else {
                        "${mm.roundToInt()} مم"
                    }
                }
                IMPERIAL -> {
                    val totalInches = mm / MM_PER_INCH
                    if (totalInches >= 36f) {
                        val feet = (totalInches / 12f).toInt()
                        val remInches = totalInches - feet * 12f
                        String.format(Locale.US, "%d' %.1f\"", feet, remInches)
                    } else {
                        String.format(Locale.US, "%.1f\"", totalInches)
                    }
                }
            }
        }

        /**
         * Formats W x H x D dimensions in millimeters according to the active unit system.
         */
        fun formatDimensions(
            widthMm: Float,
            heightMm: Float,
            depthMm: Float,
            unitSystem: MeasurementUnitSystem
        ): String {
            return when (unitSystem) {
                METRIC -> "${widthMm.roundToInt()} × ${heightMm.roundToInt()} × ${depthMm.roundToInt()} مم"
                IMPERIAL -> {
                    val wIn = widthMm / MM_PER_INCH
                    val hIn = heightMm / MM_PER_INCH
                    val dIn = depthMm / MM_PER_INCH
                    String.format(Locale.US, "%.1f\" × %.1f\" × %.1f\" in", wIn, hIn, dIn)
                }
            }
        }

        /**
         * Formats linear length (stored in meters) as either meters ("4.20 م.ط") or feet ("13.8 ft").
         */
        fun formatLinearLength(meters: Float, unitSystem: MeasurementUnitSystem): String {
            return when (unitSystem) {
                METRIC -> String.format(Locale.US, "%.2f م.ط", meters)
                IMPERIAL -> String.format(Locale.US, "%.2f ft", meters * METERS_TO_FEET)
            }
        }

        /**
         * Formats surface area (stored in m²) as either square meters ("2.50 م²") or square feet ("26.9 sq.ft").
         */
        fun formatArea(sqMeters: Float, unitSystem: MeasurementUnitSystem): String {
            return when (unitSystem) {
                METRIC -> String.format(Locale.US, "%.2f م²", sqMeters)
                IMPERIAL -> String.format(Locale.US, "%.2f sq.ft", sqMeters * SQ_METERS_TO_SQ_FEET)
            }
        }

        /**
         * Converts a linear rate ($/m) to the active unit system rate ($/m or $/ft).
         */
        fun formatLinearRate(ratePerMeterUsd: Float, unitSystem: MeasurementUnitSystem): String {
            return when (unitSystem) {
                METRIC -> String.format(Locale.US, "$%.0f / م.ط", ratePerMeterUsd)
                IMPERIAL -> String.format(Locale.US, "$%.1f / ft", ratePerMeterUsd / METERS_TO_FEET)
            }
        }

        /**
         * Converts an area rate ($/m²) to the active unit system rate ($/m² or $/sq.ft).
         */
        fun formatAreaRate(ratePerSqMeterUsd: Float, unitSystem: MeasurementUnitSystem): String {
            return when (unitSystem) {
                METRIC -> String.format(Locale.US, "$%.0f / م²", ratePerSqMeterUsd)
                IMPERIAL -> String.format(Locale.US, "$%.1f / sq.ft", ratePerSqMeterUsd / SQ_METERS_TO_SQ_FEET)
            }
        }
    }
}
