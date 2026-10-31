package com.example.core.filament

import android.app.ActivityManager
import android.content.Context
import kotlin.math.roundToInt

/**
 * Graphics Quality Preset dynamically determined by device profiling.
 */
enum class QualityLevel(val labelAr: String, val labelEn: String) {
    LOW("أداء أساسي (Low)", "Basic Performance"),
    MEDIUM("متوسط متوازن (Medium)", "Balanced Medium"),
    HIGH("فائق الواقعية (High)", "Ultra Realistic High"),
    ULTRA_4K("رندر أوفلاين فائق (4K Ultra)", "4K Offline Raytracer")
}

/**
 * Concrete technical rendering pipeline settings for a quality tier.
 */
data class QualitySettings(
    val level: QualityLevel,
    val resolutionScale: Float, // 0.75f, 1.0f
    val enableCookTorranceGGX: Boolean,
    val enableClearcoatLobe: Boolean,
    val enableTransmissionRefraction: Boolean,
    val enableAnisotropicReflections: Boolean,
    val shadowMapResolution: Int, // 512, 1024, 2048, 4096
    val maxActivePointLights: Int, // 2, 8, 24
    val enableAmbientOcclusion: Boolean,
    val enableIblSphericalHarmonics: Boolean,
    val msaaSamples: Int // 1, 2, 4
)

/**
 * Device hardware profiler inspects available memory, processor cores, and low-RAM flags
 * to select the optimal PBR rendering tier automatically without manual user intervention.
 */
object DeviceProfiler {

    data class DeviceSpec(
        val totalRamGb: Float,
        val availableRamGb: Float,
        val cpuCores: Int,
        val isLowRamDevice: Boolean,
        val recommendedQuality: QualityLevel,
        val summaryAr: String
    )

    /**
     * Inspects device hardware using Android ActivityManager and Runtime.
     */
    fun profile(context: Context? = null): DeviceSpec {
        val cores = Runtime.getRuntime().availableProcessors()
        var totalRamGb = 4.0f // safe default
        var availRamGb = 2.0f
        var isLowRam = false

        if (context != null) {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (actManager != null) {
                val memInfo = ActivityManager.MemoryInfo()
                actManager.getMemoryInfo(memInfo)
                totalRamGb = (memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)).toFloat()
                availRamGb = (memInfo.availMem / (1024.0 * 1024.0 * 1024.0)).toFloat()
                isLowRam = actManager.isLowRamDevice
            }
        }

        // Profile logic:
        // High: >= 7.0 GB RAM and >= 8 CPU cores
        // Medium: >= 3.5 GB RAM and >= 4 CPU cores
        // Low: < 3.5 GB RAM or low-ram device or <= 4 cores
        val recommendedLevel = when {
            isLowRam || totalRamGb < 3.2f || cores <= 4 -> QualityLevel.LOW
            totalRamGb >= 7.0f && cores >= 8 -> QualityLevel.HIGH
            else -> QualityLevel.MEDIUM
        }

        val summaryAr = when (recommendedLevel) {
            QualityLevel.HIGH -> "جهاز بمواصفات عالية (${cores} أنوية، ${String.format("%.1f", totalRamGb)} GB رام) - تفعيل كامل خامات PBR وانكسار الزجاج والظلال فائقة الدقة."
            QualityLevel.MEDIUM -> "جهاز بمواصفات متوسطة متوازنة (${cores} أنوية، ${String.format("%.1f", totalRamGb)} GB رام) - تفعيل تظليل GGX والإضاءة المحيطية IBL."
            QualityLevel.LOW -> "جهاز بنمط توفير الموارد (${cores} أنوية، ${String.format("%.1f", totalRamGb)} GB رام) - تظليل سريع لضمان سلاسة الحركة 60 FPS."
            QualityLevel.ULTRA_4K -> "نمط الرندر الخلفي المنفصل بدقة 4K."
        }

        return DeviceSpec(
            totalRamGb = totalRamGb,
            availableRamGb = availRamGb,
            cpuCores = cores,
            isLowRamDevice = isLowRam,
            recommendedQuality = recommendedLevel,
            summaryAr = summaryAr
        )
    }

    /**
     * Maps a QualityLevel to concrete graphics pipeline flags.
     */
    fun getSettings(level: QualityLevel): QualitySettings {
        return when (level) {
            QualityLevel.LOW -> QualitySettings(
                level = QualityLevel.LOW,
                resolutionScale = 0.8f,
                enableCookTorranceGGX = false, // Fast Blinn-Phong microfacet approx
                enableClearcoatLobe = false,
                enableTransmissionRefraction = false,
                enableAnisotropicReflections = false,
                shadowMapResolution = 512,
                maxActivePointLights = 2,
                enableAmbientOcclusion = false,
                enableIblSphericalHarmonics = false,
                msaaSamples = 1
            )
            QualityLevel.MEDIUM -> QualitySettings(
                level = QualityLevel.MEDIUM,
                resolutionScale = 1.0f,
                enableCookTorranceGGX = true,
                enableClearcoatLobe = true,
                enableTransmissionRefraction = false,
                enableAnisotropicReflections = false,
                shadowMapResolution = 1024,
                maxActivePointLights = 8,
                enableAmbientOcclusion = true,
                enableIblSphericalHarmonics = true,
                msaaSamples = 2
            )
            QualityLevel.HIGH -> QualitySettings(
                level = QualityLevel.HIGH,
                resolutionScale = 1.0f,
                enableCookTorranceGGX = true,
                enableClearcoatLobe = true,
                enableTransmissionRefraction = true,
                enableAnisotropicReflections = true,
                shadowMapResolution = 2048,
                maxActivePointLights = 24,
                enableAmbientOcclusion = true,
                enableIblSphericalHarmonics = true,
                msaaSamples = 4
            )
            QualityLevel.ULTRA_4K -> QualitySettings(
                level = QualityLevel.ULTRA_4K,
                resolutionScale = 1.0f,
                enableCookTorranceGGX = true,
                enableClearcoatLobe = true,
                enableTransmissionRefraction = true,
                enableAnisotropicReflections = true,
                shadowMapResolution = 4096,
                maxActivePointLights = 64,
                enableAmbientOcclusion = true,
                enableIblSphericalHarmonics = true,
                msaaSamples = 4
            )
        }
    }
}
