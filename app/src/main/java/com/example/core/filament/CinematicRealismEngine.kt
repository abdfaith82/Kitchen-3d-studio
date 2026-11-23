package com.example.core.filament

import kotlin.math.*

/**
 * Cinematic realism engine — the "photoreal / filmic" layer on top of the
 * existing PBR + V-Ray/Corona mood presets in [OffscreenRenderer4K].
 *
 * What it adds (all pure math, no Android dependency, so it cannot break
 * the build):
 *  - 8 times of day with physically plausible sun position, colour
 *    temperature (Kelvin), illuminance and LED balance.
 *  - 7 film looks (ACES filmic, teal & orange, golden hour, nordic…).
 *  - Real cinema lenses (24 / 35 / 50 / 85 / 135 mm) with the true
 *    full-frame field of view, instead of an arbitrary "zoom" number.
 *  - Depth of field (aperture + focus distance + bokeh) and film grain.
 *  - Cinema aspect ratios (16:9, 2.39:1 CinemaScope, 4:5, 1:1, 9:16).
 *
 * The engine maps every preset back to the values the app already knows
 * how to apply: [EnvironmentPreset], sun azimuth/elevation, the artificial
 * light intensity in the ViewModel, and [CinematicPostProcessConfig].
 */

/** Time of day / lighting mood for a cinematic render. */
enum class CinematicTimeOfDay(
    val titleAr: String,
    val titleEn: String,
    val descriptionAr: String,
    val sunAzimuth: Float,
    val sunElevation: Float,
    val sunKelvin: Float,
    val sunLux: Float,
    /** 0..1.5 — maps to the ViewModel artificialLightIntensity. */
    val ledIntensity: Float,
    val exposureEv: Float,
    val bloom: Float,
    val vignette: Float,
    val filmGrain: Float,
    val envPreset: EnvironmentPreset,
    val filmLook: FilmLook,
    val letterbox: Boolean
) {
    GOLDEN_HOUR(
        titleAr = "الساعة الذهبية 🌅",
        titleEn = "Golden Hour",
        descriptionAr = "شمس منخفضة دافئة 3200K مع توهج LED وظلال طويلة ناعمة — أفضل لقطة للعرض على العميل",
        sunAzimuth = 148f, sunElevation = 18f, sunKelvin = 3200f, sunLux = 78000f,
        ledIntensity = 0.9f, exposureEv = -0.3f, bloom = 0.80f, vignette = 0.42f,
        filmGrain = 0.12f, envPreset = EnvironmentPreset.WARM_INTERIOR,
        filmLook = FilmLook.WARM_GOLDEN, letterbox = true
    ),
    BLUE_HOUR(
        titleAr = "الساعة الزرقاء 🌆",
        titleEn = "Blue Hour",
        descriptionAr = "سماء زرقاء باردة 7500K بعد الغروب مع إضاءة داخلية دافئة تبرز من النوافذ",
        sunAzimuth = 200f, sunElevation = 8f, sunKelvin = 7500f, sunLux = 12000f,
        ledIntensity = 1.3f, exposureEv = 0.3f, bloom = 0.95f, vignette = 0.50f,
        filmGrain = 0.15f, envPreset = EnvironmentPreset.LUXURY_NIGHT,
        filmLook = FilmLook.COOL_NORDIC, letterbox = true
    ),
    MIDDAY_SUN(
        titleAr = "شمس الظهيرة ☀️",
        titleEn = "Midday Sun",
        descriptionAr = "ضوء نهاري محايد 5600K بأعلى إضاءة — أوضح لقطة لمراجعة التفاصيل والخامات",
        sunAzimuth = 135f, sunElevation = 62f, sunKelvin = 5600f, sunLux = 110000f,
        ledIntensity = 0.4f, exposureEv = 0.0f, bloom = 0.45f, vignette = 0.22f,
        filmGrain = 0.05f, envPreset = EnvironmentPreset.DAYLIGHT_WINDOW,
        filmLook = FilmLook.ACES_FILMIC, letterbox = false
    ),
    OVERCAST_SOFT(
        titleAr = "يوم غائم ناعم ☁️",
        titleEn = "Soft Overcast",
        descriptionAr = "ضوء منتشر بلا ظلال قاسية 6500K — يظهر ألوان الخامات بأمانة تامة",
        sunAzimuth = 120f, sunElevation = 45f, sunKelvin = 6500f, sunLux = 45000f,
        ledIntensity = 0.7f, exposureEv = 0.2f, bloom = 0.50f, vignette = 0.25f,
        filmGrain = 0.06f, envPreset = EnvironmentPreset.STUDIO_NEUTRAL,
        filmLook = FilmLook.SOFT_PASTEL, letterbox = false
    ),
    SUNRISE(
        titleAr = "شروق الصباح 🌄",
        titleEn = "Sunrise",
        descriptionAr = "ضوء صباحي وردي-ذهبي 3800K بزاوية منخفضة من الشرق",
        sunAzimuth = 95f, sunElevation = 12f, sunKelvin = 3800f, sunLux = 60000f,
        ledIntensity = 0.8f, exposureEv = -0.2f, bloom = 0.70f, vignette = 0.38f,
        filmGrain = 0.10f, envPreset = EnvironmentPreset.WARM_INTERIOR,
        filmLook = FilmLook.WARM_GOLDEN, letterbox = false
    ),
    NIGHT_LUXURY(
        titleAr = "فخامة ليلية ✨",
        titleEn = "Luxury Night",
        descriptionAr = "ليل درامي 3000K بإضاءة مخفية قوية وانعكاسات رخام وزجاج مدخن",
        sunAzimuth = 160f, sunElevation = 10f, sunKelvin = 3000f, sunLux = 8000f,
        ledIntensity = 1.5f, exposureEv = 0.5f, bloom = 1.0f, vignette = 0.55f,
        filmGrain = 0.18f, envPreset = EnvironmentPreset.LUXURY_NIGHT,
        filmLook = FilmLook.DRAMATIC_NOIR, letterbox = true
    ),
    STUDIO_PRODUCT(
        titleAr = "استوديو منتج 📸",
        titleEn = "Studio Product",
        descriptionAr = "إضاءة استوديو محايدة 5500K بلا ظلال ملونة — لقطة كتالوج نظيفة",
        sunAzimuth = 90f, sunElevation = 50f, sunKelvin = 5500f, sunLux = 60000f,
        ledIntensity = 1.0f, exposureEv = 0.0f, bloom = 0.35f, vignette = 0.15f,
        filmGrain = 0.03f, envPreset = EnvironmentPreset.STUDIO_NEUTRAL,
        filmLook = FilmLook.ACES_FILMIC, letterbox = false
    ),
    CANDLELIT_DINNER(
        titleAr = "عشاء على الشموع 🕯️",
        titleEn = "Candlelit Dinner",
        descriptionAr = "دفء شمعي 2200K مع إضاءة خافتة وأجواء سينمائية حالمة",
        sunAzimuth = 180f, sunElevation = 6f, sunKelvin = 2200f, sunLux = 5000f,
        ledIntensity = 1.2f, exposureEv = 0.7f, bloom = 0.90f, vignette = 0.60f,
        filmGrain = 0.20f, envPreset = EnvironmentPreset.LUXURY_NIGHT,
        filmLook = FilmLook.VINTAGE_FILM, letterbox = true
    )
}

/** Filmic colour grade applied on top of the render. */
enum class FilmLook(
    val titleAr: String,
    val titleEn: String,
    val saturation: Float,
    val contrast: Float,
    val warmthShift: Float,
    val tealOrangeStrength: Float
) {
    ACES_FILMIC("سينمائي ACES", "ACES Filmic", 1.00f, 1.15f, 0.05f, 0.00f),
    TEAL_ORANGE("برتقالي-تركوازي 🎞️", "Teal & Orange", 1.08f, 1.22f, 0.08f, 0.85f),
    WARM_GOLDEN("ذهبي دافئ", "Warm Golden", 1.05f, 1.12f, 0.22f, 0.10f),
    COOL_NORDIC("شمالي بارد", "Cool Nordic", 0.95f, 1.08f, -0.18f, 0.35f),
    SOFT_PASTEL("باستيل ناعم", "Soft Pastel", 0.90f, 0.95f, 0.05f, 0.00f),
    DRAMATIC_NOIR("درامي داكن", "Dramatic Noir", 0.85f, 1.35f, -0.05f, 0.15f),
    VINTAGE_FILM("فيلم قديم", "Vintage Film", 0.92f, 1.05f, 0.15f, 0.00f)
}

/**
 * Real cinema lens. Field of view is the true full-frame value:
 * horizontal FOV = 2 * atan(sensorWidth / (2 * focal)), sensor = 36 mm.
 */
enum class CinematicLens(
    val focalMm: Float,
    val titleAr: String,
    val titleEn: String,
    val useCaseAr: String
) {
    FOCAL_24(24f, "عدسة 24mm واسعة", "24mm Wide", "لقطة معمارية واسعة تظهر الغرفة كاملة"),
    FOCAL_35(35f, "عدسة 35mm", "35mm", "منظور طبيعي قريب من عين الإنسان"),
    FOCAL_50(50f, "عدسة 50mm", "50mm", "العدسة القياسية — تفاصيل بلا تشويه"),
    FOCAL_85(85f, "عدسة 85mm بورتريه", "85mm Portrait", "تركيز على الجزيرة والرخام مع عزل خلفية"),
    FOCAL_135(135f, "عدسة 135mm مقرّبة", "135mm Tele", "تفاصيل دقيقة: مقابض، عروق رخام، إكسسوارات");

    /** True horizontal field of view in degrees (36 mm full-frame sensor). */
    val horizontalFovDeg: Float
        get() = Math.toDegrees(2.0 * atan(18.0 / focalMm.toDouble())).toFloat()

    /** True vertical field of view for a 16:9 frame (24 mm sensor height). */
    val verticalFovDeg: Float
        get() = Math.toDegrees(2.0 * atan(12.0 / focalMm.toDouble())).toFloat()

    /** Zoom factor that reproduces this lens from the app's 50 mm default view. */
    val zoomFactor: Float
        get() = (50f / focalMm).coerceIn(0.35f, 4.0f)
}

/** Cinema / social aspect ratios for the exported render. */
enum class RenderAspectRatio(
    val titleAr: String,
    val label: String,
    val widthPart: Float,
    val heightPart: Float
) {
    WIDESCREEN_16_9("عريض 16:9", "16:9", 16f, 9f),
    CINEMASCOPE_2_39_1("سينما سكوب 2.39:1 🎬", "2.39:1", 2.39f, 1f),
    PORTRAIT_4_5("عمودي 4:5 إنستغرام", "4:5", 4f, 5f),
    SQUARE_1_1("مربع 1:1", "1:1", 1f, 1f),
    STORY_9_16("ستوري 9:16", "9:16", 9f, 16f),
    PHOTO_3_2("صورة 3:2", "3:2", 3f, 2f);

    val ratio: Float get() = widthPart / heightPart
    val isLetterboxed: Boolean get() = this == CINEMASCOPE_2_39_1
}

/** Depth of field: aperture + focus distance + bokeh strength. */
data class DepthOfFieldConfig(
    val apertureFStop: Float = 5.6f,
    val focusDistanceMm: Float = 2400f,
    val bokehStrength: Float = 0.5f
) {
    /** f/2.8 or faster = shallow, cinematic background separation. */
    val isShallow: Boolean get() = apertureFStop <= 2.8f

    /** Approximate background blur amount 0..1 for the preview overlay. */
    fun blurAmount(): Float {
        val aperturePart = ((8f - apertureFStop.coerceIn(1.4f, 16f)) / 6.6f).coerceIn(0f, 1f)
        val focusPart = (focusDistanceMm / 6000f).coerceIn(0f, 1f)
        return (aperturePart * 0.7f + focusPart * 0.3f).coerceIn(0f, 1f) * bokehStrength.coerceIn(0f, 1f)
    }
}

/** One complete cinematic render setup — preset + fine tuning. */
data class CinematicRenderSettings(
    val timeOfDay: CinematicTimeOfDay = CinematicTimeOfDay.GOLDEN_HOUR,
    val filmLook: FilmLook = FilmLook.ACES_FILMIC,
    val lens: CinematicLens = CinematicLens.FOCAL_35,
    val aspect: RenderAspectRatio = RenderAspectRatio.WIDESCREEN_16_9,
    val depthOfField: DepthOfFieldConfig = DepthOfFieldConfig(),
    val exposureEv: Float = 0f,
    val bloom: Float = 0.65f,
    val vignette: Float = 0.35f,
    val filmGrain: Float = 0.10f,
    val letterbox: Boolean = false
) {
    /** Maps this setup onto the existing post-process config the renderer uses. */
    fun toPostProcessConfig(): CinematicPostProcessConfig = CinematicPostProcessConfig(
        bloomIntensity = bloom.coerceIn(0f, 1.5f),
        vignetteStrength = vignette.coerceIn(0f, 1f),
        warmthShift = filmLook.warmthShift,
        contrastBoost = filmLook.contrast,
        cinematicLetterbox = letterbox || aspect.isLetterboxed,
        includeCountertopStaging = true
    )

    fun summaryAr(): String {
        val dof = if (depthOfField.isShallow) "عمق ميدان ضحل f/${depthOfField.apertureFStop}" else "عمق ميدان عميق f/${depthOfField.apertureFStop}"
        return "${timeOfDay.titleAr} • ${filmLook.titleAr} • ${lens.titleAr} • ${aspect.label} • $dof"
    }

    companion object {
        fun fromTimeOfDay(time: CinematicTimeOfDay): CinematicRenderSettings =
            CinematicRenderSettings(
                timeOfDay = time,
                filmLook = time.filmLook,
                exposureEv = time.exposureEv,
                bloom = time.bloom,
                vignette = time.vignette,
                filmGrain = time.filmGrain,
                letterbox = time.letterbox
            )

        fun default(): CinematicRenderSettings = fromTimeOfDay(CinematicTimeOfDay.GOLDEN_HOUR)
    }
}

/** Pure-math helpers for the cinematic engine. */
object CinematicRealismEngine {

    /** Exposure value (EV) to linear light multiplier: 2^EV. */
    fun exposureEvToMultiplier(ev: Float): Float = 2f.pow(ev)

    /** Recommended aperture for a lens: wide lenses stay sharp, tele lenses go shallow. */
    fun recommendedApertureFor(lens: CinematicLens): Float = when (lens) {
        CinematicLens.FOCAL_24 -> 8.0f
        CinematicLens.FOCAL_35 -> 5.6f
        CinematicLens.FOCAL_50 -> 4.0f
        CinematicLens.FOCAL_85 -> 2.8f
        CinematicLens.FOCAL_135 -> 2.0f
    }

    /** Warmth shift (-0.5..0.5) implied by a colour temperature in Kelvin. */
    fun kelvinToWarmthShift(kelvin: Float): Float =
        ((5500f - kelvin.coerceIn(2000f, 8000f)) / 5500f * 0.45f).coerceIn(-0.5f, 0.5f)

    /** True horizontal FOV for any focal length on a full-frame sensor. */
    fun fovForFocalLengthMm(focalMm: Float): Float =
        Math.toDegrees(2.0 * atan(18.0 / focalMm.coerceIn(14f, 200f).toDouble())).toFloat()

    /** Best time of day for the room's main window direction (azimuth 0..360). */
    fun bestTimeForWindowAzimuth(windowAzimuthDeg: Float): CinematicTimeOfDay {
        val az = ((windowAzimuthDeg % 360f) + 360f) % 360f
        return when {
            az in 60f..120f -> CinematicTimeOfDay.SUNRISE
            az in 120f..200f -> CinematicTimeOfDay.GOLDEN_HOUR
            az in 200f..260f -> CinematicTimeOfDay.BLUE_HOUR
            else -> CinematicTimeOfDay.MIDDAY_SUN
        }
    }
}
