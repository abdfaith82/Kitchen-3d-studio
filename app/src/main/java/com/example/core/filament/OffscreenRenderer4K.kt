package com.example.core.filament

import android.content.Context
import android.graphics.Bitmap
import com.example.core.geometry.WallGeometryCutter
import com.example.core.math.AABB
import com.example.core.math.Vector3
import com.example.domain.model.ElementCategory
import com.example.domain.model.KitchenElement
import com.example.domain.model.KitchenElementType
import com.example.domain.model.RoomStructure
import com.example.domain.model.WallSide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.*

/**
 * Resolution Presets for Offline Rendering.
 */
enum class RenderResolutionPreset(
    val titleAr: String,
    val titleEn: String,
    val width: Int,
    val height: Int
) {
    FHD_1080P("عالية الدقة 1080p (FHD)", "1080p Full HD", 1920, 1080),
    QHD_2K("فائقة الدقة 2K (QHD)", "2K Quad HD", 2560, 1440),
    UHD_4K("دقة سينمائية 4K (UHD)", "4K Ultra HD", 3840, 2160),
    UHD_8K_VRAY("دقة معمارية فائقة 8K (V-Ray / Corona)", "8K V-Ray / Corona Ultra", 7680, 4320)
}

/**
 * Render progress notification for UI updates.
 */
data class RenderProgress(
    val percent: Int, // 0..100
    val currentTile: Int,
    val totalTiles: Int,
    val timeElapsedMs: Long,
    val estimatedTimeRemainingMs: Long,
    val stageDescription: String
)

/**
 * Cinematic Post-Processing & Lens Effects configuration for 4K Photorealistic Output.
 */
data class CinematicPostProcessConfig(
    val bloomIntensity: Float = 0.68f,     // 0.0..1.5 (HDR Specular & LED Glow Halo)
    val vignetteStrength: Float = 0.36f,   // 0.0..1.0 (Optical 24mm f/1.4 Lens Vignette)
    val warmthShift: Float = 0.14f,        // -0.5..0.5 (Golden Hour / 3000K Warmth Shift)
    val contrastBoost: Float = 1.14f,      // 0.8..1.5 (Filmic S-Curve Contrast)
    val cinematicLetterbox: Boolean = false, // 2.39:1 CinemaScope Aspect Ratio Framing Bars
    val includeCountertopStaging: Boolean = true // Subtle Kettle, Indoor Plant & Glass Jars staging
)

/**
 * Ready-made Cinematic Architectural Mood Presets for one-tap 4K/8K V-Ray & Corona Studio configuration.
 */
enum class CinematicMoodPreset(
    val titleAr: String,
    val subtitleAr: String,
    val sunElevation: Float,
    val sunAzimuth: Float,
    val sunIntensityLux: Float,
    val sunTempKelvin: Float,
    val envPreset: EnvironmentPreset,
    val ledEnabled: Boolean,
    val ledFluxLumens: Float,
    val ledTempKelvin: Float,
    val apertureFStop: Float,
    val iso: Float,
    val postConfig: CinematicPostProcessConfig
) {
    VRAY_CORONA_ARCHITECTURAL_8K(
        titleAr = "في-راي / كورونا 8K (V-Ray & Corona Photoreal)",
        subtitleAr = "إضاءة شاملة GI + رخام كلكتا وخشب طبيعي + إكسسوارات الرخامة الواقعية",
        sunElevation = 36f,
        sunAzimuth = 138f,
        sunIntensityLux = 95000f,
        sunTempKelvin = 5100f,
        envPreset = EnvironmentPreset.VRAY_CORONA_GI,
        ledEnabled = true,
        ledFluxLumens = 1150f,
        ledTempKelvin = 3000f,
        apertureFStop = 5.6f,
        iso = 125f,
        postConfig = CinematicPostProcessConfig(
            bloomIntensity = 0.64f,
            vignetteStrength = 0.28f,
            warmthShift = 0.14f,
            contrastBoost = 1.16f,
            cinematicLetterbox = false,
            includeCountertopStaging = true
        )
    ),
    GOLDEN_HOUR_CINEMATIC(
        titleAr = "غروب ذهبي سينمائي (Golden Hour 4K)",
        subtitleAr = "أشعة شمس دافئة بزاوية منخفضة مع توهج LED وظلال ناعمة",
        sunElevation = 24f,
        sunAzimuth = 148f,
        sunIntensityLux = 92000f,
        sunTempKelvin = 4300f,
        envPreset = EnvironmentPreset.WARM_INTERIOR,
        ledEnabled = true,
        ledFluxLumens = 980f,
        ledTempKelvin = 3000f,
        apertureFStop = 5.6f,
        iso = 160f,
        postConfig = CinematicPostProcessConfig(
            bloomIntensity = 0.78f,
            vignetteStrength = 0.40f,
            warmthShift = 0.22f,
            contrastBoost = 1.18f,
            cinematicLetterbox = false
        )
    ),
    LUXURY_NIGHT_LED_3000K(
        titleAr = "فخامة ليلية 3000K (Luxury Night LED)",
        subtitleAr = "إضاءة مخفية دافئة وانعكاسات رخام وزجاج مدخن درامية",
        sunElevation = 12f,
        sunAzimuth = 160f,
        sunIntensityLux = 18000f,
        sunTempKelvin = 3600f,
        envPreset = EnvironmentPreset.LUXURY_NIGHT,
        ledEnabled = true,
        ledFluxLumens = 1450f,
        ledTempKelvin = 2850f,
        apertureFStop = 4.0f,
        iso = 250f,
        postConfig = CinematicPostProcessConfig(
            bloomIntensity = 0.95f,
            vignetteStrength = 0.48f,
            warmthShift = 0.18f,
            contrastBoost = 1.24f,
            cinematicLetterbox = true
        )
    ),
    EDITORIAL_DAYLIGHT_MAGAZINE(
        titleAr = "إضاءة مجلات معمارية (Editorial Daylight)",
        subtitleAr = "توازن لوني نقي يبرز عروق الرخام وتفاصيل الخشب الطبيعي",
        sunElevation = 52f,
        sunAzimuth = 130f,
        sunIntensityLux = 98000f,
        sunTempKelvin = 5600f,
        envPreset = EnvironmentPreset.DAYLIGHT_WINDOW,
        ledEnabled = true,
        ledFluxLumens = 750f,
        ledTempKelvin = 3200f,
        apertureFStop = 8.0f,
        iso = 100f,
        postConfig = CinematicPostProcessConfig(
            bloomIntensity = 0.52f,
            vignetteStrength = 0.26f,
            warmthShift = 0.06f,
            contrastBoost = 1.12f,
            cinematicLetterbox = false
        )
    ),
    MOODY_CHIAROSCURO_CINEMA(
        titleAr = "دراما سينمائية (Moody Chiaroscuro)",
        subtitleAr = "تباين سينمائي عميق مع شريط CinemaScope وتوهج سبوتات السقف",
        sunElevation = 19f,
        sunAzimuth = 115f,
        sunIntensityLux = 54000f,
        sunTempKelvin = 4100f,
        envPreset = EnvironmentPreset.WARM_INTERIOR,
        ledEnabled = true,
        ledFluxLumens = 1250f,
        ledTempKelvin = 2900f,
        apertureFStop = 4.5f,
        iso = 200f,
        postConfig = CinematicPostProcessConfig(
            bloomIntensity = 0.88f,
            vignetteStrength = 0.52f,
            warmthShift = 0.15f,
            contrastBoost = 1.28f,
            cinematicLetterbox = true
        )
    )
}

/**
 * Render execution result.
 */
data class RenderResult(
    val bitmap: Bitmap,
    val savedFile: File?,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val resolutionPreset: RenderResolutionPreset
)

/**
 * Dedicated 100% Offline 4K Physically Based Offscreen Renderer.
 * Runs in background threads using Kotlin Coroutines without blocking the Android UI thread.
 * Generates photorealistic architectural renders with Cook-Torrance GGX, Marble Clearcoat,
 * Glass Refraction, Sunlight Shadows, Under-Cabinet LED glow, HDR Specular Bloom, and ACES Filmic Grading.
 */
object OffscreenRenderer4K {

    // Internal ray representation for 3D scene traversal
    private data class Ray(val origin: Vector3, val direction: Vector3)

    private data class RayHit(
        val distance: Float,
        val hitPoint: Vector3,
        val normal: Vector3,
        val material: PbrMaterial
    )

    private data class BoxCollider(
        val aabb: AABB,
        val material: PbrMaterial
    )

    /**
     * Executes the offscreen render pass asynchronously in a background coroutine.
     */
    suspend fun renderScene(
        context: Context?,
        room: RoomStructure,
        elements: List<KitchenElement>,
        cameraEye: Vector3,
        cameraTarget: Vector3,
        fovDegrees: Float = 45f,
        sunLight: DirectionalSunLight = DirectionalSunLight(),
        envPreset: EnvironmentPreset = EnvironmentPreset.WARM_INTERIOR,
        ledStrips: List<LedSpotLight> = emptyList(),
        exposure: CameraExposure = CameraExposure(),
        resolutionPreset: RenderResolutionPreset = RenderResolutionPreset.FHD_1080P,
        postProcessConfig: CinematicPostProcessConfig = CinematicPostProcessConfig(),
        onProgress: (RenderProgress) -> Unit = {}
    ): RenderResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val maxHeapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L)
        // Memory-safe internal buffer resolution so 8K V-Ray preset never triggers OOM on constrained mobile heaps
        val useSafe4KBufferFor8K = resolutionPreset == RenderResolutionPreset.UHD_8K_VRAY && maxHeapMb < 1024L
        val width = if (useSafe4KBufferFor8K) 3840 else resolutionPreset.width
        val height = if (useSafe4KBufferFor8K) 2160 else resolutionPreset.height

        val envSH = SphericalHarmonics.createPreset(envPreset)

        // 1. Compile 3D Scene Geometry into Spatial Bounding Boxes (Strictly preserving structure + optional countertop staging)
        val colliders = buildSceneColliders(room, elements, postProcessConfig.includeCountertopStaging)

        // 2. Camera Basis Vectors
        val forward = (cameraTarget - cameraEye).normalized()
        val worldUp = Vector3.UNIT_Y
        val right = forward.cross(worldUp).normalized()
        val up = right.cross(forward).normalized()

        val fovRad = Math.toRadians(fovDegrees.toDouble()).toFloat()
        val halfHeight = tan(fovRad * 0.5f)
        val halfWidth = halfHeight * (width.toFloat() / height.toFloat())

        // 3. Tile-based Multi-threaded Rendering into Linear HDR Buffer
        val tileSize = 64
        val tilesX = (width + tileSize - 1) / tileSize
        val tilesY = (height + tileSize - 1) / tileSize
        val totalTiles = tilesX * tilesY

        val hdrR = FloatArray(width * height)
        val hdrG = FloatArray(width * height)
        val hdrB = FloatArray(width * height)
        val pixels = IntArray(width * height)
        var completedTiles = 0

        for (ty in 0 until tilesY) {
            for (tx in 0 until tilesX) {
                coroutineContext.ensureActive() // Cancellation check

                val startX = tx * tileSize
                val endX = min(startX + tileSize, width)
                val startY = ty * tileSize
                val endY = min(startY + tileSize, height)

                for (y in startY until endY) {
                    val normY = 1.0f - 2.0f * (y + 0.5f) / height.toFloat()
                    for (x in startX until endX) {
                        val normX = 2.0f * (x + 0.5f) / width.toFloat() - 1.0f

                        val rayDir = (forward + right * (normX * halfWidth) + up * (normY * halfHeight)).normalized()
                        val primaryRay = Ray(cameraEye, rayDir)

                        val pixelColor = traceRay(
                            ray = primaryRay,
                            colliders = colliders,
                            sunLight = sunLight,
                            ledStrips = ledStrips,
                            envSH = envSH,
                            exposure = exposure,
                            bounce = 0
                        )

                        val idx = y * width + x
                        hdrR[idx] = pixelColor.x
                        hdrG[idx] = pixelColor.y
                        hdrB[idx] = pixelColor.z
                    }
                }

                completedTiles++
                val elapsed = System.currentTimeMillis() - startTime
                val percent = ((completedTiles.toFloat() / totalTiles.toFloat()) * 92f).toInt()
                val estimatedRemaining = if (completedTiles > 0) {
                    ((elapsed.toFloat() / completedTiles.toFloat()) * (totalTiles - completedTiles)).toLong()
                } else 0L

                onProgress(
                    RenderProgress(
                        percent = percent,
                        currentTile = completedTiles,
                        totalTiles = totalTiles,
                        timeElapsedMs = elapsed,
                        estimatedTimeRemainingMs = estimatedRemaining,
                        stageDescription = "تتبع الأشعة الفيزيائي PBR: البلاطة $completedTiles من $totalTiles (${resolutionPreset.width}×${resolutionPreset.height})"
                    )
                )
            }
        }

        // 3B. Cinematic Post-Processing Pass (HDR Specular Bloom, Warmth Shift, Filmic Contrast, Optical Vignette)
        onProgress(
            RenderProgress(
                percent = 96,
                currentTile = totalTiles,
                totalTiles = totalTiles,
                timeElapsedMs = System.currentTimeMillis() - startTime,
                estimatedTimeRemainingMs = 120L,
                stageDescription = "المعالجة السينمائية النهائية (HDR Bloom + ACES Filmic + Optical Vignette)..."
            )
        )

        val letterboxBarHeight = if (postProcessConfig.cinematicLetterbox) (height * 0.095f).toInt() else 0
        val bloomStrength = postProcessConfig.bloomIntensity.coerceIn(0f, 1.5f)
        val vignetteStrength = postProcessConfig.vignetteStrength.coerceIn(0f, 1.0f)
        val warmth = postProcessConfig.warmthShift.coerceIn(-0.5f, 0.5f)
        val contrast = postProcessConfig.contrastBoost.coerceIn(0.8f, 1.5f)

        for (y in 0 until height) {
            if (postProcessConfig.cinematicLetterbox && (y < letterboxBarHeight || y >= height - letterboxBarHeight)) {
                val rowOffset = y * width
                for (x in 0 until width) {
                    pixels[rowOffset + x] = 0xFF05070B.toInt()
                }
                continue
            }

            val ny = (y.toFloat() / height.toFloat()) * 2f - 1f
            val rowOffset = y * width
            for (x in 0 until width) {
                val idx = rowOffset + x
                var r = hdrR[idx]
                var g = hdrG[idx]
                var b = hdrB[idx]

                // Fast neighborhood HDR Bloom accumulation from bright specular/LED emitters
                if (bloomStrength > 0.02f) {
                    var bloomR = 0f
                    var bloomG = 0f
                    var bloomB = 0f
                    val step = 6
                    var samples = 0
                    val y0 = (y - step).coerceAtLeast(0)
                    val y1 = (y + step).coerceAtMost(height - 1)
                    val x0 = (x - step).coerceAtLeast(0)
                    val x1 = (x + step).coerceAtMost(width - 1)

                    val sIdx1 = y0 * width + x
                    val sIdx2 = y1 * width + x
                    val sIdx3 = y * width + x0
                    val sIdx4 = y * width + x1
                    val sIdx5 = y0 * width + x0
                    val sIdx6 = y1 * width + x1

                    val neighborIndices = intArrayOf(sIdx1, sIdx2, sIdx3, sIdx4, sIdx5, sIdx6)
                    for (nIdx in neighborIndices) {
                        val nr = hdrR[nIdx]
                        val ng = hdrG[nIdx]
                        val nb = hdrB[nIdx]
                        val lum = nr * 0.2126f + ng * 0.7152f + nb * 0.0722f
                        if (lum > 0.85f) {
                            val excess = (lum - 0.85f).coerceAtMost(3.5f)
                            bloomR += nr * excess
                            bloomG += ng * excess
                            bloomB += nb * excess
                        }
                        samples++
                    }
                    if (samples > 0) {
                        val factor = (bloomStrength * 0.14f) / samples.toFloat()
                        r += bloomR * factor
                        g += bloomG * factor
                        b += bloomB * factor
                    }
                }

                // Cinematic Color Temperature Warmth Shift
                if (abs(warmth) > 0.005f) {
                    r *= (1f + warmth * 0.28f)
                    g *= (1f + warmth * 0.08f)
                    b *= (1f - warmth * 0.22f).coerceAtLeast(0.4f)
                }

                // ACES Filmic Tone Mapping
                val toneMapped = exposure.toneMapAces(Vector3(r, g, b))

                // Filmic Contrast S-Curve around mid-grey (0.5)
                var cr = ((toneMapped.x - 0.5f) * contrast + 0.5f).coerceIn(0f, 1f)
                var cg = ((toneMapped.y - 0.5f) * contrast + 0.5f).coerceIn(0f, 1f)
                var cb = ((toneMapped.z - 0.5f) * contrast + 0.5f).coerceIn(0f, 1f)

                // Optical Lens Vignette
                if (vignetteStrength > 0.01f) {
                    val nx = (x.toFloat() / width.toFloat()) * 2f - 1f
                    val radialSq = (nx * nx * 0.75f + ny * ny * 0.75f)
                    val vig = (1f - radialSq * vignetteStrength * 0.55f).coerceIn(0.28f, 1f)
                    cr *= vig
                    cg *= vig
                    cb *= vig
                }

                val hex = PbrMaterial.linearRgbToHex(Vector3(cr, cg, cb))
                pixels[idx] = hex.toInt()
            }
        }

        // 4. Create Bitmap on Android Graphic thread
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)

        // 5. Save to local storage cache if context available
        var savedFile: File? = null
        if (context != null) {
            try {
                val outputDir = File(context.cacheDir, "renders")
                if (!outputDir.exists()) outputDir.mkdirs()
                val file = File(outputDir, "kitchen_render_${System.currentTimeMillis()}_${resolutionPreset.name}.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                savedFile = file
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val totalTime = System.currentTimeMillis() - startTime
        RenderResult(
            bitmap = bitmap,
            savedFile = savedFile,
            width = width,
            height = height,
            durationMs = totalTime,
            resolutionPreset = resolutionPreset
        )
    }

    /**
     * Evaluates procedural 3D surface detail (Calacatta marble veining, natural wood grain fibers,
     * and 600mm porcelain tile grout seams) at the exact 3D hit point.
     */
    private fun evaluateProceduralSurfaceAlbedo(hit: RayHit): Vector3 {
        val p = hit.hitPoint
        val id = hit.material.id.lowercase()
        var albedo = hit.material.baseColor

        // 1. 600x600mm Floor Porcelain Tile Grout Seams & Calacatta Marble Veins
        if (abs(hit.normal.y) > 0.9f && p.y in -5f..5f) {
            val gx = abs(p.x % 600f)
            val gz = abs(p.z % 600f)
            if (gx < 3.0f || gx > 597.0f || gz < 3.0f || gz > 597.0f) {
                return albedo * 0.42f // Recessed dark grout line
            }
            val wave = sin((p.x * 0.0065f) + (p.z * 0.0052f) + sin(p.x * 0.014f) * 1.6f)
            val veinMask = (1f - abs(wave)).pow(10f)
            albedo = albedo * (1f - veinMask * 0.22f) + Vector3(0.76f, 0.62f, 0.36f) * (veinMask * 0.22f)
        }

        // 2. Calacatta / Carrara Marble Countertops & Backsplashes
        if (id.contains("carrara") || id.contains("calacatta") || id.contains("marble")) {
            val phase = (p.x * 0.009f) + (p.z * 0.008f) + (p.y * 0.007f) +
                    sin(p.x * 0.021f + p.z * 0.017f) * 1.4f
            val primaryVein = (1f - abs(sin(phase))).pow(12f)
            val secondaryVein = (1f - abs(cos(phase * 1.7f))).pow(16f)
            val fineVein = (1f - abs(sin(phase * 2.9f + cos(p.x * 0.03f)))).pow(20f)
            val goldTint = Vector3(0.76f, 0.60f, 0.34f)
            val greyTint = Vector3(0.45f, 0.48f, 0.52f)
            albedo = albedo * (1f - primaryVein * 0.30f) + goldTint * (primaryVein * 0.30f)
            albedo = albedo * (1f - secondaryVein * 0.22f) + greyTint * (secondaryVein * 0.22f)
            albedo = albedo * (1f - fineVein * 0.14f) + goldTint * (fineVein * 0.14f)
        }

        // 2B. Polished Black Galaxy Granite Countertops (Subtle Gold/Silver Mineral Flecks)
        if (id.contains("granite")) {
            val fleckNoise = sin(p.x * 0.18f) * cos(p.z * 0.18f) * sin((p.x + p.z) * 0.11f)
            if (fleckNoise > 0.72f) {
                val fleckIntensity = ((fleckNoise - 0.72f) / 0.28f).coerceIn(0f, 1f)
                albedo = albedo + Vector3(0.55f, 0.44f, 0.26f) * (fleckIntensity * 0.35f)
            }
        }

        // 3. Natural Wood Grain Fibers & Cathedral Grain Arches (Oak / Walnut)
        if (id.contains("oak") || id.contains("walnut") || id.contains("wood")) {
            val ring = sin((p.x * 0.045f) + sin(p.y * 0.012f) * 2.4f + cos(p.z * 0.035f))
            val microFiber = cos(p.x * 0.22f + p.y * 0.04f) * 0.05f
            val grainFactor = (0.90f + 0.14f * ring + microFiber).coerceIn(0.72f, 1.12f)
            albedo = Vector3(
                (albedo.x * grainFactor).coerceIn(0f, 1f),
                (albedo.y * grainFactor).coerceIn(0f, 1f),
                (albedo.z * grainFactor).coerceIn(0f, 1f)
            )
        }

        // 4. Brushed Stainless Steel Anisotropic Micro-Grain (Appliances & Kettle)
        if (id.contains("stainless") || id.contains("chrome") || id.contains("kettle")) {
            val brush = sin(p.y * 0.65f + p.x * 0.15f) * 0.04f
            albedo = Vector3(
                (albedo.x + brush).coerceIn(0f, 1f),
                (albedo.y + brush).coerceIn(0f, 1f),
                (albedo.z + brush).coerceIn(0f, 1f)
            )
        }

        // 5. Organic Botanical Indoor Plant Foliage Variation
        if (id.contains("plant_foliage")) {
            val leafVar = sin(p.x * 0.09f) * cos(p.y * 0.09f) * 0.08f
            albedo = Vector3(
                (albedo.x + leafVar * 0.5f).coerceIn(0f, 1f),
                (albedo.y + leafVar).coerceIn(0f, 1f),
                (albedo.z + leafVar * 0.4f).coerceIn(0f, 1f)
            )
        }

        return albedo
    }

    /**
     * Traces a physical light ray through the scene, evaluating PBR Cook-Torrance,
     * procedural marble/wood textures, shadows, clearcoat, and dielectric transmission.
     */
    private fun traceRay(
        ray: Ray,
        colliders: List<BoxCollider>,
        sunLight: DirectionalSunLight,
        ledStrips: List<LedSpotLight>,
        envSH: SphericalHarmonics,
        exposure: CameraExposure,
        bounce: Int
    ): Vector3 {
        val hit = intersectScene(ray, colliders) ?: run {
            // Environment background / sky radiance
            val dirY = ray.direction.y.coerceIn(0f, 1f)
            val skyColor = Vector3(0.5f, 0.7f, 1.0f) * dirY + Vector3(0.9f, 0.95f, 1.0f) * (1f - dirY)
            return skyColor * (0.2f * exposure.exposureMultiplier)
        }

        val mat = hit.material
        val surfaceAlbedo = evaluateProceduralSurfaceAlbedo(hit)
        val n = hit.normal
        val v = -ray.direction
        val nDotV = n.dot(v).coerceAtLeast(0.001f)

        var finalRgb = Vector3.ZERO

        // Add self-emissive light (LED strip emitters)
        if (mat.emissiveIntensity > 0f) {
            finalRgb = finalRgb + mat.emissiveColor * mat.emissiveIntensity
        }

        // 1. Direct Sunlight
        val lSun = sunLight.directionToLight
        val nDotLSun = n.dot(lSun)
        if (nDotLSun > 0.001f) {
            // Cast shadow ray
            val shadowRay = Ray(hit.hitPoint + n * 2.0f, lSun)
            val shadowHit = intersectScene(shadowRay, colliders, maxDist = 15000f)
            val inShadow = shadowHit != null && shadowHit.material.transmission < 0.5f

            if (!inShadow) {
                val h = (v + lSun).normalized()
                val nDotH = n.dot(h).coerceAtLeast(0f)
                val vDotH = v.dot(h).coerceAtLeast(0f)

                // Cook-Torrance Specular BRDF
                val f0 = PbrShadingMath.computeF0(surfaceAlbedo, mat.metallic, mat.reflectance)
                val d = PbrShadingMath.distributionGgx(nDotH, mat.roughness)
                val g = PbrShadingMath.visibilitySmithGgx(nDotV, nDotLSun, mat.roughness)
                val f = PbrShadingMath.fresnelSchlick(vDotH, f0)

                val specular = f * (d * g)

                // Diffuse component (Lambertian with energy conservation)
                val kd = (Vector3.ONE - f) * (1.0f - mat.metallic)
                val diffuse = kd * (surfaceAlbedo * (1.0f / Math.PI.toFloat()))

                // Marble Clearcoat Lobe (Secondary specular reflection)
                var clearcoatSpecular = Vector3.ZERO
                if (mat.clearcoat > 0f) {
                    val dCc = PbrShadingMath.distributionGgx(nDotH, mat.clearcoatRoughness)
                    val gCc = PbrShadingMath.visibilitySmithGgx(nDotV, nDotLSun, mat.clearcoatRoughness)
                    val fCc = PbrShadingMath.fresnelSchlick(vDotH, Vector3(0.04f, 0.04f, 0.04f))
                    clearcoatSpecular = fCc * (dCc * gCc * mat.clearcoat * 0.25f)
                }

                val sunColor = sunLight.linearRgbColor * (sunLight.illuminanceLux / 10000f)
                finalRgb = finalRgb + (diffuse + specular + clearcoatSpecular) * sunColor * nDotLSun
            }
        }

        // 2. Under-Cabinet & In-Cabinet LED Spot Lights
        ledStrips.forEach { led ->
            val atten = led.computeAttenuation(hit.hitPoint, n)
            if (atten > 0.001f) {
                val toLed = (led.positionMm - hit.hitPoint).normalized()
                val h = (v + toLed).normalized()
                val nDotH = n.dot(h).coerceAtLeast(0f)
                val vDotH = v.dot(h).coerceAtLeast(0f)

                val f0 = PbrShadingMath.computeF0(surfaceAlbedo, mat.metallic, mat.reflectance)
                val d = PbrShadingMath.distributionGgx(nDotH, mat.roughness)
                val g = PbrShadingMath.visibilitySmithGgx(nDotV, n.dot(toLed).coerceAtLeast(0.001f), mat.roughness)
                val f = PbrShadingMath.fresnelSchlick(vDotH, f0)

                val specular = f * (d * g)
                val kd = (Vector3.ONE - f) * (1.0f - mat.metallic)
                val diffuse = kd * (surfaceAlbedo * (1.0f / Math.PI.toFloat()))

                val ledColor = led.linearRgbColor * (led.luminousFluxLumens / 600f)
                finalRgb = finalRgb + (diffuse + specular) * ledColor * atten
            }
        }

        // 3. V-Ray / Corona Global Illumination (GI) & Indirect Ambient Irradiance from Spherical Harmonics
        val irradiance = envSH.evaluateIrradiance(n)
        val floorAo = if (hit.hitPoint.y < 140f && abs(n.y) < 0.5f) {
            (0.62f + 0.38f * (hit.hitPoint.y / 140f)).coerceIn(0.62f, 1.0f)
        } else 1.0f
        // Warm indirect GI bounce from floor and wood surfaces onto walls, countertops, and upper cabinets
        val giWarmBounce = Vector3(1.04f, 0.96f, 0.86f) * (0.10f * (1.0f - n.y.coerceIn(-1f, 1f) * 0.35f))
        val ambientDiffuse = surfaceAlbedo * (irradiance + giWarmBounce) * (1.0f - mat.metallic) * (0.38f * floorAo)
        finalRgb = finalRgb + ambientDiffuse

        // 4. Optical Refraction / Transmission for Glass Cabinets, Vitrines & Countertop Glass Jars
        if (mat.transmission > 0f && bounce < 2) {
            val eta = 1.0f / mat.ior
            val refrDir = PbrShadingMath.refract(ray.direction, n, eta)
            if (refrDir != null) {
                val refrRay = Ray(hit.hitPoint + refrDir * 2.0f, refrDir)
                val refrColor = traceRay(
                    ray = refrRay,
                    colliders = colliders,
                    sunLight = sunLight,
                    ledStrips = ledStrips,
                    envSH = envSH,
                    exposure = exposure,
                    bounce = bounce + 1
                )
                // Add subtle Fresnel glass surface reflection on tinted glass cabinets & jars
                val glassFresnel = (1.0f - nDotV).pow(4f) * 0.28f
                val glassReflColor = Vector3(0.95f, 0.97f, 1.0f) * glassFresnel
                finalRgb = finalRgb * (1f - mat.transmission) + refrColor * mat.transmission + glassReflColor
            }
        } else if ((mat.roughness <= 0.22f || mat.clearcoat >= 0.6f) && bounce < 1) {
            // 5. Specular Mirror & Clearcoat Reflection Bounce on Glossy Floors, Veined Marble Countertops, Acrylic & Stainless Appliances
            val reflDir = (ray.direction - n * (2.0f * ray.direction.dot(n))).normalized()
            val reflRay = Ray(hit.hitPoint + n * 2.5f, reflDir)
            val reflColor = traceRay(
                ray = reflRay,
                colliders = colliders,
                sunLight = sunLight,
                ledStrips = ledStrips,
                envSH = envSH,
                exposure = exposure,
                bounce = bounce + 1
            )
            val fresnelWeight = (0.14f + (1f - mat.roughness) * 0.20f + mat.clearcoat * 0.08f).coerceIn(0f, 0.38f)
            finalRgb = finalRgb * (1f - fresnelWeight) + reflColor * fresnelWeight
        }

        // Scale by camera photometric exposure factor
        return finalRgb * (exposure.exposureMultiplier * 1.5f)
    }

    /**
     * Ray-Scene intersection test against all box colliders.
     */
    private fun intersectScene(
        ray: Ray,
        colliders: List<BoxCollider>,
        maxDist: Float = Float.MAX_VALUE
    ): RayHit? {
        var closestDist = maxDist
        var closestHit: RayHit? = null

        for (collider in colliders) {
            val dist = intersectAabb(ray, collider.aabb)
            if (dist != null && dist in 0.1f..closestDist) {
                closestDist = dist
                val hitPoint = ray.origin + ray.direction * dist
                val normal = collider.aabb.getNormalAt(hitPoint)
                closestHit = RayHit(
                    distance = dist,
                    hitPoint = hitPoint,
                    normal = normal,
                    material = collider.material
                )
            }
        }

        return closestHit
    }

    /**
     * Slab method for fast Ray-AABB intersection.
     */
    private fun intersectAabb(ray: Ray, aabb: AABB): Float? {
        val invDx = if (abs(ray.direction.x) > 1e-6f) 1.0f / ray.direction.x else 1e6f
        val invDy = if (abs(ray.direction.y) > 1e-6f) 1.0f / ray.direction.y else 1e6f
        val invDz = if (abs(ray.direction.z) > 1e-6f) 1.0f / ray.direction.z else 1e6f

        val t1 = (aabb.min.x - ray.origin.x) * invDx
        val t2 = (aabb.max.x - ray.origin.x) * invDx
        val t3 = (aabb.min.y - ray.origin.y) * invDy
        val t4 = (aabb.max.y - ray.origin.y) * invDy
        val t5 = (aabb.min.z - ray.origin.z) * invDz
        val t6 = (aabb.max.z - ray.origin.z) * invDz

        val tmin = max(max(min(t1, t2), min(t3, t4)), min(t5, t6))
        val tmax = min(min(max(t1, t2), max(t3, t4)), max(t5, t6))

        if (tmax < 0 || tmin > tmax) return null
        return if (tmin > 0) tmin else tmax
    }

    private fun AABB.getNormalAt(point: Vector3): Vector3 {
        val eps = 2.0f
        return when {
            abs(point.x - min.x) < eps -> Vector3(-1f, 0f, 0f)
            abs(point.x - max.x) < eps -> Vector3(1f, 0f, 0f)
            abs(point.y - min.y) < eps -> Vector3(0f, -1f, 0f)
            abs(point.y - max.y) < eps -> Vector3(0f, 1f, 0f)
            abs(point.z - min.z) < eps -> Vector3(0f, 0f, -1f)
            abs(point.z - max.z) < eps -> Vector3(0f, 0f, 1f)
            else -> Vector3.UNIT_Y
        }
    }

    /**
     * Converts room structure and kitchen elements into physical PBR colliders.
     * Strictly preserves the exact structural layout, cabinet proportions, and appliance placements.
     */
    private fun buildSceneColliders(
        room: RoomStructure,
        elements: List<KitchenElement>,
        includeStagingProps: Boolean = true
    ): List<BoxCollider> {
        val colliders = mutableListOf<BoxCollider>()

        // 1. Room Floor (Polished Calacatta / Porcelain with Clearcoat reflection)
        colliders.add(
            BoxCollider(
                aabb = AABB(
                    min = Vector3(0f, -20f, 0f),
                    max = Vector3(room.widthMm, 0f, room.depthMm)
                ),
                material = PbrMaterial(
                    id = room.floorMaterial.id,
                    nameAr = room.floorMaterial.name,
                    nameEn = room.floorMaterial.id,
                    baseColor = PbrMaterial.hexToLinearRgb(room.floorMaterial.colorHex),
                    roughness = room.floorMaterial.roughness.coerceAtMost(0.14f),
                    metallic = room.floorMaterial.metallic,
                    clearcoat = 0.88f,
                    clearcoatRoughness = 0.05f
                )
            )
        )

        // 2. Room Ceiling + Emissive Ceiling Recessed Spotlights (for realistic floor & countertop reflections)
        colliders.add(
            BoxCollider(
                aabb = AABB(
                    min = Vector3(0f, room.heightMm, 0f),
                    max = Vector3(room.widthMm, room.heightMm + 20f, room.depthMm)
                ),
                material = PbrMaterial(
                    id = "ceiling",
                    nameAr = "سقف جبس بورد أبيض",
                    nameEn = "White Gypsum Ceiling",
                    baseColor = Vector3(0.95f, 0.95f, 0.96f),
                    roughness = 0.78f,
                    metallic = 0.0f
                )
            )
        )

        val ceilingSpotlightEmitterMat = PbrMaterial(
            id = "ceiling_spotlight_emitter",
            nameAr = "سبوت سقف معماري",
            nameEn = "Ceiling Recessed Spotlight",
            baseColor = Vector3(1.0f, 0.96f, 0.84f),
            roughness = 0.05f,
            metallic = 0.0f,
            emissiveColor = Vector3(1.0f, 0.92f, 0.72f),
            emissiveIntensity = 4.2f
        )
        val spotCoords = listOf(
            Vector3(room.widthMm * 0.25f, room.heightMm - 6f, 420f),
            Vector3(room.widthMm * 0.50f, room.heightMm - 6f, 420f),
            Vector3(room.widthMm * 0.75f, room.heightMm - 6f, 420f),
            Vector3(420f, room.heightMm - 6f, room.depthMm * 0.45f),
            Vector3(room.widthMm * 0.50f, room.heightMm - 6f, room.depthMm * 0.50f)
        )
        spotCoords.forEach { sp ->
            colliders.add(
                BoxCollider(
                    aabb = AABB(
                        min = Vector3(sp.x - 45f, sp.y - 4f, sp.z - 45f),
                        max = Vector3(sp.x + 45f, sp.y + 2f, sp.z + 45f)
                    ),
                    material = ceilingSpotlightEmitterMat
                )
            )
        }

        // 3. Walls with dynamic cutouts + Veined Calacatta Marble Backsplash Cladding (y = 850..1450mm)
        room.walls.forEach { wall ->
            val solidBlocks = WallGeometryCutter.decomposeWall(wall)
            solidBlocks.forEach { block ->
                val centerPos = WallGeometryCutter.localBlockToWorldCenter(
                    wall = wall,
                    block = block,
                    roomWidth = room.widthMm,
                    roomDepth = room.depthMm
                )
                val dims = when (wall.side) {
                    WallSide.NORTH, WallSide.SOUTH -> Vector3(block.lengthMm, block.heightMm, wall.thicknessMm)
                    WallSide.WEST, WallSide.EAST -> Vector3(wall.thicknessMm, block.heightMm, block.lengthMm)
                }
                val half = dims * 0.5f
                colliders.add(
                    BoxCollider(
                        aabb = AABB(min = centerPos - half, max = centerPos + half),
                        material = PbrMaterial(
                            id = "wall_${wall.id}",
                            nameAr = "جدار ناصع",
                            nameEn = "Off-White Wall",
                            baseColor = PbrMaterial.hexToLinearRgb(room.wallFinish.colorHex),
                            roughness = 0.68f,
                            metallic = 0.0f
                        )
                    )
                )
            }
        }

        // Calacatta Veined Marble Backsplash Slab on North & West walls (between base countertops and upper cabinets)
        colliders.add(
            BoxCollider(
                aabb = AABB(
                    min = Vector3(20f, 850f, 2f),
                    max = Vector3(room.widthMm - 20f, 1450f, 14f)
                ),
                material = PbrMaterial.POLISHED_CARRARA_MARBLE
            )
        )
        colliders.add(
            BoxCollider(
                aabb = AABB(
                    min = Vector3(2f, 850f, 20f),
                    max = Vector3(14f, 1450f, room.depthMm - 20f)
                ),
                material = PbrMaterial.POLISHED_CARRARA_MARBLE
            )
        )

        // 4. Kitchen Elements (Toe-kicks, Carcasses, Countertops, Smoked Glass Doors, Emissive LED Strips, Staging Props)
        val blackPlinthMat = PbrMaterial(
            id = "toe_kick_black",
            nameAr = "وزرة سفلية سوداء",
            nameEn = "Black Toe Kick",
            baseColor = Vector3(0.05f, 0.06f, 0.08f),
            roughness = 0.75f,
            metallic = 0.1f
        )
        val warmLedEmitterMat = PbrMaterial(
            id = "warm_led_emitter",
            nameAr = "شريط ليد دافئ 3000K",
            nameEn = "Warm 3000K LED Strip",
            baseColor = Vector3(1.0f, 0.88f, 0.55f),
            roughness = 0.1f,
            metallic = 0.0f,
            emissiveColor = Vector3(1.0f, 0.82f, 0.42f),
            emissiveIntensity = 3.4f
        )
        val kettleMatteBlackMat = PbrMaterial(
            id = "kettle_stainless_black",
            nameAr = "غلاية قهوة مينيماليست",
            nameEn = "Minimalist Matte Black & Steel Kettle",
            baseColor = Vector3(0.08f, 0.10f, 0.13f),
            roughness = 0.18f,
            metallic = 0.75f,
            clearcoat = 0.6f
        )
        val ceramicPotMat = PbrMaterial(
            id = "ceramic_pot_white",
            nameAr = "أصيص سيراميك أبيض",
            nameEn = "Matte Ceramic Planter Pot",
            baseColor = Vector3(0.92f, 0.93f, 0.94f),
            roughness = 0.32f,
            metallic = 0.0f
        )
        val plantFoliageMat = PbrMaterial(
            id = "plant_foliage_green",
            nameAr = "نبتة داخلية طبيعية",
            nameEn = "Botanical Indoor Plant Foliage",
            baseColor = Vector3(0.08f, 0.42f, 0.24f),
            roughness = 0.45f,
            metallic = 0.0f
        )

        elements.filter { it.isVisible }.forEach { el ->
            val obb = el.getOBB()
            val aabb = obb.toAABB()

            val pbrMat = when (el.material.id) {
                "carrara", "calacatta_gold" -> PbrMaterial.POLISHED_CARRARA_MARBLE
                "stainless" -> PbrMaterial.BRUSHED_STAINLESS_STEEL
                "brass" -> PbrMaterial.MIRROR_CHROME
                "oak" -> PbrMaterial.CARVED_NATURAL_OAK
                "walnut" -> PbrMaterial.DARK_WALNUT_SATIN
                else -> PbrMaterial(
                    id = el.material.id,
                    nameAr = el.material.name,
                    nameEn = el.material.id,
                    baseColor = PbrMaterial.hexToLinearRgb(el.material.colorHex),
                    roughness = el.material.roughness,
                    metallic = el.material.metallic,
                    clearcoat = if (el.material.roughness <= 0.18f) 0.92f else 0f
                )
            }

            if (el.position.y <= 20f && el.type.category != ElementCategory.APPLIANCE) {
                // Recessed 95mm black toe-kick plinth at bottom + cabinet body above 95mm
                val toeAabb = AABB(
                    min = Vector3(aabb.min.x + 25f, aabb.min.y, aabb.min.z + 25f),
                    max = Vector3(aabb.max.x - 25f, (aabb.min.y + 95f).coerceAtMost(aabb.max.y), aabb.max.z - 25f)
                )
                val bodyAabb = AABB(
                    min = Vector3(aabb.min.x, (aabb.min.y + 95f).coerceAtMost(aabb.max.y), aabb.min.z),
                    max = aabb.max
                )
                colliders.add(BoxCollider(aabb = toeAabb, material = blackPlinthMat))
                colliders.add(BoxCollider(aabb = bodyAabb, material = pbrMat))
            } else {
                colliders.add(BoxCollider(aabb = aabb, material = pbrMat))
            }

            // Countertop slab (if present) + Tasteful 3D Countertop Staging Props
            el.countertopMaterial?.let { topMat ->
                val topMaxY = aabb.max.y + 38f
                val topAabb = AABB(
                    min = Vector3(aabb.min.x - 18f, aabb.max.y, aabb.min.z - 18f),
                    max = Vector3(aabb.max.x + 18f, topMaxY, aabb.max.z + 22f)
                )
                val topPbr = if (topMat.id == "black_granite") {
                    PbrMaterial(
                        id = "black_granite_pbr",
                        nameAr = "جرانيت جالاكسي أسود لامع",
                        nameEn = "Polished Black Galaxy Granite",
                        baseColor = Vector3(0.03f, 0.035f, 0.04f),
                        roughness = 0.08f,
                        metallic = 0.15f,
                        clearcoat = 1.0f,
                        clearcoatRoughness = 0.04f
                    )
                } else {
                    PbrMaterial.POLISHED_CARRARA_MARBLE
                }
                colliders.add(BoxCollider(aabb = topAabb, material = topPbr))

                // Subtle 3D Countertop Staging Props (Minimalist Kettle, Small Indoor Plant, Glass Canister Jars)
                if (includeStagingProps &&
                    el.type != KitchenElementType.COOKTOP_INDUCTION &&
                    el.type != KitchenElementType.COOKTOP_GAS_90 &&
                    el.type != KitchenElementType.BASE_UNDER_OVEN &&
                    el.dimensions.x >= 450f
                ) {
                    val cx = (aabb.min.x + aabb.max.x) * 0.5f
                    val cz = (aabb.min.z + aabb.max.z) * 0.5f
                    val isIsland = el.type == KitchenElementType.ISLAND_COUNTER ||
                            el.type == KitchenElementType.PENINSULA_LIT_NICHES ||
                            el.type == KitchenElementType.BREAKFAST_BAR
                    val isSink = el.type == KitchenElementType.BASE_SINK_UNIT ||
                            el.type == KitchenElementType.BASE_SINK_VENTED_100
                    val variant = abs(el.id.hashCode() + (el.position.x.toInt() / 100)) % 3

                    if (isIsland) {
                        // Small Indoor Plant in Ceramic Pot + Minimalist Kettle on Island
                        val px = cx - 180f
                        colliders.add(
                            BoxCollider(
                                aabb = AABB(Vector3(px - 55f, topMaxY, cz - 55f), Vector3(px + 55f, topMaxY + 110f, cz + 55f)),
                                material = ceramicPotMat
                            )
                        )
                        colliders.add(
                            BoxCollider(
                                aabb = AABB(Vector3(px - 75f, topMaxY + 110f, cz - 75f), Vector3(px + 75f, topMaxY + 220f, cz + 75f)),
                                material = plantFoliageMat
                            )
                        )
                        val kx = cx + 180f
                        colliders.add(
                            BoxCollider(
                                aabb = AABB(Vector3(kx - 60f, topMaxY, cz - 60f), Vector3(kx + 60f, topMaxY + 170f, cz + 60f)),
                                material = kettleMatteBlackMat
                            )
                        )
                    } else if (!isSink && el.type.category == ElementCategory.BASE_CABINET) {
                        when (variant) {
                            0 -> {
                                // Minimalist Pour-Over Kettle
                                colliders.add(
                                    BoxCollider(
                                        aabb = AABB(
                                            Vector3(cx + 40f, topMaxY, cz - 55f),
                                            Vector3(cx + 150f, topMaxY + 165f, cz + 55f)
                                        ),
                                        material = kettleMatteBlackMat
                                    )
                                )
                            }
                            1 -> {
                                // Pair of Glass Canister Jars with Wooden Lids
                                for (j in 0..1) {
                                    val jx = cx - 90f + j * 115f
                                    val jh = if (j == 0) 145f else 115f
                                    colliders.add(
                                        BoxCollider(
                                            aabb = AABB(
                                                Vector3(jx - 38f, topMaxY, cz - 80f),
                                                Vector3(jx + 38f, topMaxY + jh, cz - 4f)
                                            ),
                                            material = PbrMaterial.CLEAR_FLOAT_GLASS
                                        )
                                    )
                                    colliders.add(
                                        BoxCollider(
                                            aabb = AABB(
                                                Vector3(jx - 40f, topMaxY + jh, cz - 82f),
                                                Vector3(jx + 40f, topMaxY + jh + 18f, cz - 2f)
                                            ),
                                            material = PbrMaterial.CARVED_NATURAL_OAK
                                        )
                                    )
                                }
                            }
                            else -> {
                                // Small Indoor Botanical Plant in Ceramic Pot
                                colliders.add(
                                    BoxCollider(
                                        aabb = AABB(
                                            Vector3(cx - 110f, topMaxY, cz - 75f),
                                            Vector3(cx - 10f, topMaxY + 105f, cz + 25f)
                                        ),
                                        material = ceramicPotMat
                                    )
                                )
                                colliders.add(
                                    BoxCollider(
                                        aabb = AABB(
                                            Vector3(cx - 125f, topMaxY + 105f, cz - 90f),
                                            Vector3(cx + 5f, topMaxY + 205f, cz + 40f)
                                        ),
                                        material = plantFoliageMat
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Glass door & emissive vertical LED strips for Vitrines and Glass Wall/Bridge Cabinets
            if (el.name.contains("زجاج") || el.name.contains("فيترينة") || el.name.contains("نيش")) {
                val glassAabb = AABB(
                    min = Vector3(aabb.min.x + 18f, aabb.min.y + 18f, aabb.max.z + 1f),
                    max = Vector3(aabb.max.x - 18f, aabb.max.y - 18f, aabb.max.z + 10f)
                )
                colliders.add(BoxCollider(aabb = glassAabb, material = PbrMaterial.CLEAR_FLOAT_GLASS))

                // Glowing vertical LED strip inside vitrine
                val ledStripAabb = AABB(
                    min = Vector3(aabb.min.x + 28f, aabb.min.y + 30f, aabb.max.z - 12f),
                    max = Vector3(aabb.min.x + 48f, aabb.max.y - 30f, aabb.max.z - 2f)
                )
                colliders.add(BoxCollider(aabb = ledStripAabb, material = warmLedEmitterMat))
            }

            // Glowing Under-Cabinet LED bar for Wall Cabinets
            if (el.type.category == ElementCategory.WALL_CABINET) {
                val underLedAabb = AABB(
                    min = Vector3(aabb.min.x + 20f, aabb.min.y - 8f, aabb.min.z + 40f),
                    max = Vector3(aabb.max.x - 20f, aabb.min.y - 1f, aabb.max.z - 40f)
                )
                colliders.add(BoxCollider(aabb = underLedAabb, material = warmLedEmitterMat))
            }
        }

        return colliders
    }
}
