package com.example.core.filament

import com.example.core.math.Vector3
import kotlin.math.*

/**
 * PBR (Physically Based Rendering) Material definition complying with Google Filament's
 * Cook-Torrance GGX microfacet formulation, Clearcoat lobe, and Transmission/Refraction models.
 */
data class PbrMaterial(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val baseColor: Vector3, // Linear RGB [0..1]
    val roughness: Float, // Perceptual roughness [0.04..1.0]
    val metallic: Float, // Metallic [0.0 = Dielectric, 1.0 = Pure Metal]
    val reflectance: Float = 0.5f, // Dielectric F0 specular reflectance (0.5 = 4% for standard plastics/woods)
    val clearcoat: Float = 0.0f, // Clearcoat layer weight [0.0..1.0]
    val clearcoatRoughness: Float = 0.05f, // Clearcoat roughness
    val transmission: Float = 0.0f, // Optical transmission / transparency [0.0 = Opaque, 1.0 = Pure Glass]
    val ior: Float = 1.52f, // Index of Refraction (Glass = 1.52, Water = 1.33, Diamond = 2.42)
    val anisotropy: Float = 0.0f, // Directional brush reflection for metals [-1.0..1.0]
    val bumpDepth: Float = 0.0f, // Relief depth for carved wood / textured tiles [0.0..1.0]
    val emissiveColor: Vector3 = Vector3.ZERO, // Self-illumination (e.g. LED strip)
    val emissiveIntensity: Float = 0.0f // Luminous intensity factor
) {
    /**
     * Converts a 0xAARRGGBB hex color into linear RGB Vector3 [0..1].
     */
    companion object {
        fun hexToLinearRgb(colorHex: Long): Vector3 {
            val r = ((colorHex shr 16) and 0xFF) / 255.0f
            val g = ((colorHex shr 8) and 0xFF) / 255.0f
            val b = (colorHex and 0xFF) / 255.0f
            // Fast sRGB to linear conversion: approx gamma 2.2
            return Vector3(r.pow(2.2f), g.pow(2.2f), b.pow(2.2f))
        }

        fun linearRgbToHex(rgb: Vector3, alpha: Int = 255): Long {
            val r = (rgb.x.pow(1f / 2.2f).coerceIn(0f, 1f) * 255f).toInt()
            val g = (rgb.y.pow(1f / 2.2f).coerceIn(0f, 1f) * 255f).toInt()
            val b = (rgb.z.pow(1f / 2.2f).coerceIn(0f, 1f) * 255f).toInt()
            val a = alpha.coerceIn(0, 255)
            return ((a.toLong() and 0xFF) shl 24) or
                    ((r.toLong() and 0xFF) shl 16) or
                    ((g.toLong() and 0xFF) shl 8) or
                    (b.toLong() and 0xFF)
        }

        // =========================================================================
        // 1. ADVANCED PBR MATERIALS PRESETS
        // =========================================================================

        /**
         * 1. رخام إيطالي مصقول (Polished Calacatta / Carrara Marble)
         * High specular gloss, protective clearcoat, low dielectric roughness.
         */
        val POLISHED_CARRARA_MARBLE = PbrMaterial(
            id = "pbr_carrara_marble",
            nameAr = "رخام كرارا إيطالي مصقول",
            nameEn = "Polished Carrara Marble",
            baseColor = Vector3(0.92f, 0.93f, 0.95f),
            roughness = 0.08f,
            metallic = 0.0f,
            reflectance = 0.65f,
            clearcoat = 1.0f,
            clearcoatRoughness = 0.03f,
            transmission = 0.0f,
            ior = 1.56f,
            bumpDepth = 0.02f
        )

        /**
         * 2. رخام أسود ماركينا (Nero Marquina Black Marble)
         */
        val NERO_MARQUINA_MARBLE = PbrMaterial(
            id = "pbr_marquina_marble",
            nameAr = "رخام ماركينا أسود ملكي",
            nameEn = "Nero Marquina Marble",
            baseColor = Vector3(0.04f, 0.04f, 0.05f),
            roughness = 0.09f,
            metallic = 0.0f,
            reflectance = 0.60f,
            clearcoat = 1.0f,
            clearcoatRoughness = 0.04f,
            transmission = 0.0f,
            ior = 1.57f
        )

        /**
         * 3. ستانلس ستيل مفرش (Brushed Stainless Steel 304)
         * Pure metal, directional anisotropic highlights, tuned roughness.
         */
        val BRUSHED_STAINLESS_STEEL = PbrMaterial(
            id = "pbr_brushed_steel",
            nameAr = "ستانلس ستيل 304 مفرّش",
            nameEn = "Brushed Stainless Steel 304",
            baseColor = Vector3(0.85f, 0.86f, 0.88f),
            roughness = 0.22f,
            metallic = 1.0f,
            reflectance = 0.5f,
            anisotropy = 0.85f // Distinct linear brushed grain reflection
        )

        /**
         * 4. كروم لماع عاكس كالمراة (Mirror Polished Chrome)
         */
        val MIRROR_CHROME = PbrMaterial(
            id = "pbr_mirror_chrome",
            nameAr = "كروم لماع عاكس (مرآة)",
            nameEn = "Mirror Polished Chrome",
            baseColor = Vector3(0.95f, 0.95f, 0.97f),
            roughness = 0.02f,
            metallic = 1.0f,
            reflectance = 0.5f
        )

        /**
         * 5. خشب بلوط محفور طبيعي (Natural Carved Oak Relief)
         * Deep textured relief grooves, warm dielectric sheen, natural wood grain.
         */
        val CARVED_NATURAL_OAK = PbrMaterial(
            id = "pbr_carved_oak",
            nameAr = "خشب بلوط محفور طبيعي",
            nameEn = "Carved Natural Oak",
            baseColor = Vector3(0.68f, 0.48f, 0.28f),
            roughness = 0.62f,
            metallic = 0.0f,
            reflectance = 0.40f,
            bumpDepth = 0.85f // Pronounced carving depth
        )

        /**
         * 6. خشب جوز داكن مصقول مطفي (Dark Walnut Satin)
         */
        val DARK_WALNUT_SATIN = PbrMaterial(
            id = "pbr_dark_walnut",
            nameAr = "خشب جوز أمريكي داكن",
            nameEn = "Dark American Walnut",
            baseColor = Vector3(0.25f, 0.16f, 0.10f),
            roughness = 0.48f,
            metallic = 0.0f,
            reflectance = 0.45f,
            bumpDepth = 0.40f
        )

        /**
         * 7. زجاج شفاف بلوري مع انكسار (Ultra-Clear Float Glass)
         * High optical transmission, real IOR 1.52, pristine Fresnel reflection.
         */
        val CLEAR_FLOAT_GLASS = PbrMaterial(
            id = "pbr_clear_glass",
            nameAr = "زجاج شفاف مع انكسار فيزيائي",
            nameEn = "Ultra-Clear Float Glass",
            baseColor = Vector3(0.98f, 0.99f, 1.0f),
            roughness = 0.015f,
            metallic = 0.0f,
            reflectance = 0.50f,
            clearcoat = 1.0f,
            clearcoatRoughness = 0.02f,
            transmission = 0.95f, // 95% light transmission
            ior = 1.52f // Standard architectural soda-lime glass
        )

        /**
         * 8. زجاج مضلع ديكوري (Fluted / Reeded Glass)
         * High transmission with vertical grooved refraction distortions.
         */
        val FLUTED_REEDED_GLASS = PbrMaterial(
            id = "pbr_fluted_glass",
            nameAr = "زجاج مضلع فلوتد (Reeded Glass)",
            nameEn = "Fluted Reeded Glass",
            baseColor = Vector3(0.95f, 0.97f, 0.98f),
            roughness = 0.14f,
            metallic = 0.0f,
            reflectance = 0.55f,
            clearcoat = 0.8f,
            transmission = 0.88f,
            ior = 1.52f,
            bumpDepth = 0.70f // Vertical fluted ribs
        )

        /**
         * 9. زجاج مصنفر مطفي (Frosted Acid-Etched Glass)
         */
        val FROSTED_GLASS = PbrMaterial(
            id = "pbr_frosted_glass",
            nameAr = "زجاج مصنفر ضبابي (Frosted)",
            nameEn = "Frosted Acid-Etched Glass",
            baseColor = Vector3(0.90f, 0.92f, 0.94f),
            roughness = 0.45f,
            metallic = 0.0f,
            reflectance = 0.50f,
            transmission = 0.75f,
            ior = 1.50f
        )

        /**
         * 10. شريط إضاءة LED دافئ (Warm LED Strip Emitter)
         */
        val WARM_LED_EMITTER = PbrMaterial(
            id = "pbr_warm_led",
            nameAr = "شريط LED دافئ 3000K",
            nameEn = "Warm 3000K LED Strip",
            baseColor = Vector3(1.0f, 0.85f, 0.60f),
            roughness = 0.9f,
            metallic = 0.0f,
            emissiveColor = Vector3(1.0f, 0.82f, 0.55f),
            emissiveIntensity = 4.0f
        )

        val ALL_PBR_PRESETS = listOf(
            POLISHED_CARRARA_MARBLE,
            NERO_MARQUINA_MARBLE,
            BRUSHED_STAINLESS_STEEL,
            MIRROR_CHROME,
            CARVED_NATURAL_OAK,
            DARK_WALNUT_SATIN,
            CLEAR_FLOAT_GLASS,
            FLUTED_REEDED_GLASS,
            FROSTED_GLASS,
            WARM_LED_EMITTER
        )
    }
}

/**
 * PBR Shading Mathematics according to the Cook-Torrance Microfacet Model.
 */
object PbrShadingMath {

    private const val PI = Math.PI.toFloat()

    /**
     * Normal Distribution Function (GGX / Trowbridge-Reitz)
     * D(h) = alpha^2 / (pi * ((n.h)^2 * (alpha^2 - 1) + 1)^2)
     */
    fun distributionGgx(nDotH: Float, roughness: Float): Float {
        val alpha = roughness * roughness
        val alpha2 = alpha * alpha
        val nDotH2 = nDotH * nDotH
        val denom = nDotH2 * (alpha2 - 1.0f) + 1.0f
        return alpha2 / (PI * denom * denom).coerceAtLeast(1e-5f)
    }

    /**
     * Geometric Shadowing/Masking Function (Smith Joint GGX)
     * V(v, l) = G(v, l) / (4 * (n.v) * (n.l))
     */
    fun visibilitySmithGgx(nDotV: Float, nDotL: Float, roughness: Float): Float {
        val alpha = roughness * roughness
        val alpha2 = alpha * alpha
        val gV = nDotL * sqrt(nDotV * nDotV * (1f - alpha2) + alpha2)
        val gL = nDotV * sqrt(nDotL * nDotL * (1f - alpha2) + alpha2)
        return 0.5f / (gV + gL).coerceAtLeast(1e-5f)
    }

    /**
     * Fresnel-Schlick approximation with dielectric F0
     * F(v, h) = F0 + (1 - F0) * (1 - v.h)^5
     */
    fun fresnelSchlick(vDotH: Float, f0: Vector3): Vector3 {
        val f = (1.0f - vDotH.coerceIn(0f, 1f)).pow(5.0f)
        return Vector3(
            f0.x + (1.0f - f0.x) * f,
            f0.y + (1.0f - f0.y) * f,
            f0.z + (1.0f - f0.z) * f
        )
    }

    /**
     * Computes base reflectivity F0 for materials.
     * For dielectrics: F0 = 0.16 * reflectance^2 * (1 - metallic)
     * For metals: F0 = baseColor * metallic
     */
    fun computeF0(baseColor: Vector3, metallic: Float, reflectance: Float): Vector3 {
        val dielectricF0 = 0.16f * (reflectance * reflectance)
        return Vector3(
            lerp(dielectricF0, baseColor.x, metallic),
            lerp(dielectricF0, baseColor.y, metallic),
            lerp(dielectricF0, baseColor.z, metallic)
        )
    }

    /**
     * Snell's Law Refraction Vector calculation.
     * Given incident ray `i`, normal `n`, and ratio of IORs `eta = n1 / n2`.
     * Returns refracted ray direction or null if total internal reflection occurs.
     */
    fun refract(incident: Vector3, normal: Vector3, eta: Float): Vector3? {
        val nDotI = incident.dot(normal)
        val k = 1.0f - eta * eta * (1.0f - nDotI * nDotI)
        if (k < 0.0f) return null // Total internal reflection
        return (incident * eta - normal * (eta * nDotI + sqrt(k))).normalized()
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
