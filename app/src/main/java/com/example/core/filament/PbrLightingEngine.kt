package com.example.core.filament

import com.example.core.math.Vector3
import com.example.domain.model.ElementCategory
import com.example.domain.model.KitchenElement
import com.example.domain.model.KitchenElementType
import kotlin.math.*

/**
 * HDRI / IBL Environment lighting preset.
 */
enum class EnvironmentPreset(val titleAr: String, val titleEn: String, val defaultLux: Float) {
    VRAY_CORONA_GI("إضاءة شاملة في-راي وكورونا 8K", "V-Ray & Corona 8K Global Illumination", 3200f),
    STUDIO_NEUTRAL("استوديو محايد ناصع", "Clean Neutral Studio", 1200f),
    WARM_INTERIOR("مطبخ دافئ عصري", "Warm Cozy Interior", 850f),
    DAYLIGHT_WINDOW("ضوء شمس طبيعي من نافذة", "Bright Daylight Window", 2500f),
    LUXURY_NIGHT("أجواء ليلية مع إضاءة خفية", "Dramatic Night LED", 400f)
}

/**
 * 9 Spherical Harmonics coefficients for irradiance diffuse convolution (Bands 0, 1, 2).
 */
data class SphericalHarmonics(
    val l00: Vector3,
    val l1m1: Vector3,
    val l10: Vector3,
    val l11: Vector3,
    val l2m2: Vector3,
    val l2m1: Vector3,
    val l20: Vector3,
    val l21: Vector3,
    val l22: Vector3
) {
    /**
     * Evaluates irradiance at a given unit surface normal using Ramamoorthi's formula.
     */
    fun evaluateIrradiance(normal: Vector3): Vector3 {
        val x = normal.x
        val y = normal.y
        val z = normal.z

        // Constants from Ramamoorthi et al.
        val c1 = 0.429043f
        val c2 = 0.511664f
        val c3 = 0.743125f
        val c4 = 0.886227f
        val c5 = 0.247708f

        val result = l00 * c4 +
                (l10 * (2f * c2 * y) + l11 * (2f * c2 * x) + l1m1 * (2f * c2 * z)) +
                (l20 * (c3 * (3f * y * y - 1f)) +
                        l22 * (c1 * (x * x - z * z)) +
                        l2m2 * (2f * c1 * x * z) +
                        l21 * (2f * c1 * x * y) +
                        l2m1 * (2f * c1 * y * z))

        return Vector3(
            result.x.coerceAtLeast(0f),
            result.y.coerceAtLeast(0f),
            result.z.coerceAtLeast(0f)
        )
    }

    companion object {
        fun createPreset(preset: EnvironmentPreset): SphericalHarmonics {
            return when (preset) {
                EnvironmentPreset.VRAY_CORONA_GI -> SphericalHarmonics(
                    l00 = Vector3(0.92f, 0.86f, 0.78f),
                    l1m1 = Vector3(0.06f, 0.05f, 0.04f),
                    l10 = Vector3(0.32f, 0.28f, 0.22f),
                    l11 = Vector3(-0.08f, -0.07f, -0.05f),
                    l2m2 = Vector3(0.02f, 0.01f, 0.01f),
                    l2m1 = Vector3(0.05f, 0.04f, 0.03f),
                    l20 = Vector3(-0.12f, -0.10f, -0.08f),
                    l21 = Vector3(0.03f, 0.02f, 0.02f),
                    l22 = Vector3(0.06f, 0.05f, 0.04f)
                )
                EnvironmentPreset.STUDIO_NEUTRAL -> SphericalHarmonics(
                    l00 = Vector3(0.75f, 0.78f, 0.82f),
                    l1m1 = Vector3(0.04f, 0.05f, 0.06f),
                    l10 = Vector3(0.20f, 0.22f, 0.25f),
                    l11 = Vector3(-0.05f, -0.05f, -0.06f),
                    l2m2 = Vector3(0f, 0f, 0f),
                    l2m1 = Vector3(0.02f, 0.02f, 0.03f),
                    l20 = Vector3(-0.08f, -0.09f, -0.10f),
                    l21 = Vector3(0.01f, 0.01f, 0.02f),
                    l22 = Vector3(0.04f, 0.04f, 0.05f)
                )
                EnvironmentPreset.WARM_INTERIOR -> SphericalHarmonics(
                    l00 = Vector3(0.85f, 0.65f, 0.45f),
                    l1m1 = Vector3(0.08f, 0.06f, 0.04f),
                    l10 = Vector3(0.25f, 0.18f, 0.10f),
                    l11 = Vector3(-0.06f, -0.04f, -0.02f),
                    l2m2 = Vector3(0.01f, 0.01f, 0f),
                    l2m1 = Vector3(0.04f, 0.03f, 0.02f),
                    l20 = Vector3(-0.10f, -0.07f, -0.04f),
                    l21 = Vector3(0.02f, 0.01f, 0.01f),
                    l22 = Vector3(0.05f, 0.04f, 0.02f)
                )
                EnvironmentPreset.DAYLIGHT_WINDOW -> SphericalHarmonics(
                    l00 = Vector3(0.82f, 0.88f, 0.98f),
                    l1m1 = Vector3(-0.10f, -0.10f, -0.12f),
                    l10 = Vector3(0.35f, 0.38f, 0.42f),
                    l11 = Vector3(0.15f, 0.16f, 0.18f),
                    l2m2 = Vector3(-0.02f, -0.02f, -0.03f),
                    l2m1 = Vector3(0.05f, 0.06f, 0.07f),
                    l20 = Vector3(-0.15f, -0.16f, -0.18f),
                    l21 = Vector3(0.03f, 0.03f, 0.04f),
                    l22 = Vector3(0.06f, 0.07f, 0.08f)
                )
                EnvironmentPreset.LUXURY_NIGHT -> SphericalHarmonics(
                    l00 = Vector3(0.15f, 0.16f, 0.22f),
                    l1m1 = Vector3(0.01f, 0.01f, 0.02f),
                    l10 = Vector3(0.05f, 0.05f, 0.08f),
                    l11 = Vector3(-0.01f, -0.01f, -0.02f),
                    l2m2 = Vector3(0f, 0f, 0f),
                    l2m1 = Vector3(0.01f, 0.01f, 0.01f),
                    l20 = Vector3(-0.02f, -0.02f, -0.03f),
                    l21 = Vector3(0f, 0f, 0f),
                    l22 = Vector3(0.01f, 0.01f, 0.01f)
                )
            }
        }
    }
}

/**
 * Directional Sunlight simulating natural daylight or sun through windows.
 */
data class DirectionalSunLight(
    val azimuthDegrees: Float = 45f, // 0..360 (compass direction)
    val elevationDegrees: Float = 40f, // 5..85 (altitude above horizon)
    val colorTemperatureKelvin: Float = 5500f, // 2700K (Golden Hour) to 6500K (Noon Daylight)
    val illuminanceLux: Float = 45000f, // Daylight illuminance in lux (scaled for interior windows)
    val castsShadows: Boolean = true,
    val shadowBias: Float = 0.002f,
    val shadowNormalBias: Float = 0.005f
) {
    /**
     * Unit vector pointing towards the light source in world space.
     */
    val directionToLight: Vector3
        get() {
            val azRad = Math.toRadians(azimuthDegrees.toDouble()).toFloat()
            val elRad = Math.toRadians(elevationDegrees.toDouble()).toFloat()
            val x = cos(elRad) * sin(azRad)
            val y = sin(elRad)
            val z = cos(elRad) * cos(azRad)
            return Vector3(x, y, z).normalized()
        }

    /**
     * Linear RGB color calculated from blackbody Planckian color temperature.
     */
    val linearRgbColor: Vector3
        get() = ColorTemperatureConverter.kelvinToRgb(colorTemperatureKelvin)
}

/**
 * Individual Spot/Point Light emitter for under-cabinet LED strips or downlights.
 */
data class LedSpotLight(
    val id: String,
    val positionMm: Vector3, // World coordinates in mm
    val direction: Vector3 = Vector3(0f, -1f, 0f), // Default points straight down onto countertop
    val luminousFluxLumens: Float = 650f, // Luminous flux
    val colorTemperatureKelvin: Float = 3000f, // 3000K warm white, 4000K natural, 6000K cool
    val innerConeAngleDeg: Float = 45f,
    val outerConeAngleDeg: Float = 75f,
    val radiusRangeMm: Float = 1400f // Distance attenuation limit
) {
    val linearRgbColor: Vector3
        get() = ColorTemperatureConverter.kelvinToRgb(colorTemperatureKelvin)

    /**
     * Computes distance and angular attenuation for a point on a surface.
     */
    fun computeAttenuation(surfacePoint: Vector3, surfaceNormal: Vector3): Float {
        val toLight = positionMm - surfacePoint
        val distMm = toLight.length()
        if (distMm > radiusRangeMm || distMm < 1f) return 0.0f

        val lightDir = toLight.normalized()

        // 1. Lambertian cosine
        val nDotL = surfaceNormal.dot(lightDir).coerceAtLeast(0f)
        if (nDotL <= 0.001f) return 0.0f

        // 2. Physical inverse-square falloff with windowing
        val distMeters = distMm / 1000f
        val distanceFalloff = 1.0f / (distMeters * distMeters + 0.01f)
        val window = (1.0f - (distMm / radiusRangeMm).pow(4)).coerceIn(0f, 1f).pow(2)

        // 3. Spotlight cone angular falloff
        val spotDir = -direction.normalized()
        val cosAngle = lightDir.dot(spotDir)
        val cosInner = cos(Math.toRadians(innerConeAngleDeg.toDouble()).toFloat())
        val cosOuter = cos(Math.toRadians(outerConeAngleDeg.toDouble()).toFloat())
        val spotFactor = ((cosAngle - cosOuter) / (cosInner - cosOuter).coerceAtLeast(1e-4f)).coerceIn(0f, 1f)

        return distanceFalloff * window * spotFactor * nDotL
    }
}

/**
 * Camera physical exposure and film response.
 */
data class CameraExposure(
    val apertureFNumber: Float = 4.0f, // f/4.0
    val shutterSpeedSeconds: Float = 1.0f / 60.0f, // 1/60 sec
    val iso: Float = 100f, // ISO 100
    val evCompensation: Float = 0.0f, // EV +/- compensation
    val useAcesToneMapping: Boolean = true
) {
    /**
     * Calculates the EV100 exposure value:
     * EV100 = log2(N^2 / t) - log2(ISO / 100)
     */
    val ev100: Float
        get() {
            val ev = (log2(apertureFNumber * apertureFNumber / shutterSpeedSeconds) - log2(iso / 100.0f))
            return ev + evCompensation
        }

    /**
     * Filament photometric exposure multiplier:
     * scale = 1.0 / (1.2 * 2^EV100)
     */
    val exposureMultiplier: Float
        get() = 1.0f / (1.2f * 2.0f.pow(ev100)).coerceAtLeast(1e-6f)

    /**
     * ACES (Academy Color Encoding System) filmic tone mapping curve.
     */
    fun toneMapAces(color: Vector3): Vector3 {
        if (!useAcesToneMapping) return color

        fun acesLobe(x: Float): Float {
            val a = 2.51f
            val b = 0.03f
            val c = 2.43f
            val d = 0.59f
            val e = 0.14f
            return ((x * (a * x + b)) / (x * (c * x + d) + e)).coerceIn(0f, 1f)
        }

        return Vector3(
            acesLobe(color.x),
            acesLobe(color.y),
            acesLobe(color.z)
        )
    }
}

/**
 * Automatic generator for under-cabinet LED light strips.
 * Analyzes kitchen scene elements, finds upper/wall cabinets, and generates
 * physically accurate LED emitters along the bottom panel pointing down to illuminate countertops.
 */
object UnderCabinetLedGenerator {

    /**
     * Automatically extracts LED strips from upper wall cabinets, glass vitrines,
     * illuminated niche columns, peninsulas, and ceiling linear/chandelier units.
     */
    fun generateLedStrips(
        elements: List<KitchenElement>,
        colorTempKelvin: Float = 3200f, // Cozy 3200K warm LED glow
        lumensPerMeter: Float = 950f,
        isEnabled: Boolean = true
    ): List<LedSpotLight> {
        if (!isEnabled) return emptyList()

        val lights = mutableListOf<LedSpotLight>()

        elements.filter { it.isVisible }.forEach { cabinet ->
            val w = cabinet.dimensions.x
            val h = cabinet.dimensions.y
            val rad = Math.toRadians(cabinet.rotationY.toDouble()).toFloat()

            when {
                // 1. Upper Wall Cabinets -> Downward warm LED wash onto backsplash & countertop
                cabinet.type.category == ElementCategory.WALL_CABINET -> {
                    val bottomY = cabinet.position.y - 10f
                    val emitterSpacingMm = 300f
                    val emitterCount = max(1, (w / emitterSpacingMm).toInt())
                    val fluxPerCluster = (w / 1000f * lumensPerMeter) / emitterCount

                    for (i in 0 until emitterCount) {
                        val offsetX = if (emitterCount == 1) 0f else -w * 0.5f + (w / (emitterCount + 1)) * (i + 1)
                        val rotX = offsetX * cos(rad)
                        val rotZ = offsetX * sin(rad)
                        lights.add(
                            LedSpotLight(
                                id = "led_wall_${cabinet.id}_$i",
                                positionMm = Vector3(cabinet.position.x + rotX, bottomY, cabinet.position.z + rotZ),
                                direction = Vector3(0f, -1f, 0f),
                                luminousFluxLumens = fluxPerCluster,
                                colorTemperatureKelvin = colorTempKelvin,
                                innerConeAngleDeg = 55f,
                                outerConeAngleDeg = 88f,
                                radiusRangeMm = 1350f
                            )
                        )
                    }
                }

                // 2. Glass Vitrines, Bridge Glass LED & Niche Display Columns -> Internal warm LED emitters
                cabinet.type == KitchenElementType.TALL_GLASS_VITRINE_LED ||
                        cabinet.type == KitchenElementType.TALL_DOUBLE_VITRINE_LED ||
                        cabinet.type == KitchenElementType.TALL_NICHE_DISPLAY_COLUMN ||
                        cabinet.type == KitchenElementType.BRIDGE_GLASS_LED_90 ||
                        cabinet.type == KitchenElementType.PENINSULA_LIT_NICHES -> {
                    val verticalCount = if (h > 1400f) 3 else 1
                    for (v in 1..verticalCount) {
                        val lightY = cabinet.position.y + (h / (verticalCount + 1)) * v
                        lights.add(
                            LedSpotLight(
                                id = "led_vitrine_${cabinet.id}_$v",
                                positionMm = Vector3(cabinet.position.x, lightY, cabinet.position.z),
                                direction = Vector3(0f, -0.5f, 0.86f).normalized(),
                                luminousFluxLumens = lumensPerMeter * 0.85f,
                                colorTemperatureKelvin = 3000f,
                                innerConeAngleDeg = 65f,
                                outerConeAngleDeg = 110f,
                                radiusRangeMm = 1600f
                            )
                        )
                    }
                }

                // 3. Ceiling Chandelier & Ceiling Linear LED Panel -> Room-wide architectural downlight
                cabinet.type == KitchenElementType.CEILING_CHANDELIER_MODERN ||
                        cabinet.type == KitchenElementType.CEILING_LINEAR_LED_PANEL -> {
                    lights.add(
                        LedSpotLight(
                            id = "led_ceiling_${cabinet.id}",
                            positionMm = Vector3(cabinet.position.x, cabinet.position.y - 30f, cabinet.position.z),
                            direction = Vector3(0f, -1f, 0f),
                            luminousFluxLumens = lumensPerMeter * 1.8f,
                            colorTemperatureKelvin = 3800f,
                            innerConeAngleDeg = 70f,
                            outerConeAngleDeg = 120f,
                            radiusRangeMm = 3200f
                        )
                    )
                }

                // 4. Base Cabinets & Islands -> Overhead Ceiling Recessed Spotlight + Subtle Toe-Kick LED
                cabinet.type.category == ElementCategory.BASE_CABINET ||
                        cabinet.type == KitchenElementType.ISLAND_COUNTER ||
                        cabinet.type == KitchenElementType.PENINSULA_LIT_NICHES ||
                        cabinet.type == KitchenElementType.BREAKFAST_BAR -> {
                    // Overhead ceiling recessed spotlight focused onto countertop & floor for specular reflections
                    lights.add(
                        LedSpotLight(
                            id = "led_ceiling_spot_${cabinet.id}",
                            positionMm = Vector3(cabinet.position.x, 2650f, cabinet.position.z + 120f),
                            direction = Vector3(0f, -1f, 0f),
                            luminousFluxLumens = lumensPerMeter * 0.95f,
                            colorTemperatureKelvin = 3600f,
                            innerConeAngleDeg = 42f,
                            outerConeAngleDeg = 78f,
                            radiusRangeMm = 2800f
                        )
                    )
                }
            }
        }

        return lights
    }
}

/**
 * Accurate conversion from Kelvin Color Temperature to Linear RGB (Planckian blackbody radiator).
 */
object ColorTemperatureConverter {

    fun kelvinToRgb(kelvin: Float): Vector3 {
        val temp = (kelvin / 100f).coerceIn(10f, 400f)

        // Red
        val r = if (temp <= 66f) {
            255f
        } else {
            val rCalc = 329.698727446f * (temp - 60f).pow(-0.1332047592f)
            rCalc.coerceIn(0f, 255f)
        }

        // Green
        val g = if (temp <= 66f) {
            val gCalc = 99.4708025861f * ln(temp) - 161.1195681661f
            gCalc.coerceIn(0f, 255f)
        } else {
            val gCalc = 288.1221695283f * (temp - 60f).pow(-0.0755148492f)
            gCalc.coerceIn(0f, 255f)
        }

        // Blue
        val b = if (temp >= 66f) {
            255f
        } else if (temp <= 19f) {
            0f
        } else {
            val bCalc = 138.5177312231f * ln(temp - 10f) - 305.0447927307f
            bCalc.coerceIn(0f, 255f)
        }

        // Convert to linear [0..1]
        val linearR = (r / 255f).pow(2.2f)
        val linearG = (g / 255f).pow(2.2f)
        val linearB = (b / 255f).pow(2.2f)

        return Vector3(linearR, linearG, linearB)
    }
}
