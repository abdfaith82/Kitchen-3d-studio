package com.kitchen3d.studio.renderer

import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Scene
import kotlin.math.cos
import kotlin.math.sin

/**
 * Manages realistic PBR lighting setup for interior architectural kitchen scenes.
 * Combines directional sun rays, ambient spherical harmonics, and interior spotlights.
 */
class KitchenLightingManager(
    private val engine: Engine,
    private val scene: Scene
) {
    private val entityManager = EntityManager.get()

    private var sunEntity: Int = 0
    private var indirectLight: IndirectLight? = null
    private val spotLightEntities = mutableListOf<Int>()

    fun setupLighting(enableWarmInterior: Boolean = true) {
        setupIndirectLight()
        setupDirectionalSunLight()
        setupCeilingSpotLights(enableWarmInterior)
        setupUnderCabinetAccentLights()
    }

    private fun setupIndirectLight() {
        // 3-band Spherical Harmonics approximating clean daylight bouncing in a modern room
        val shHarmonics = floatArrayOf(
            0.82f, 0.85f, 0.90f,
            0.12f, 0.14f, 0.18f,
            -0.08f, -0.06f, -0.04f,
            0.05f, 0.05f, 0.06f,
            -0.04f, -0.04f, -0.03f,
            0.02f, 0.02f, 0.02f,
            -0.01f, -0.01f, -0.01f,
            0.03f, 0.03f, 0.03f,
            -0.02f, -0.02f, -0.01f
        )

        indirectLight = IndirectLight.Builder()
            .radiance(3, shHarmonics)
            .intensity(28000.0f)
            .build(engine)

        scene.indirectLight = indirectLight
    }

    private fun setupDirectionalSunLight() {
        sunEntity = entityManager.create()

        val shadowOptions = LightManager.ShadowOptions().apply {
            mapSize = 2048
            shadowCascades = 3
            cascadeSplitPositions = floatArrayOf(0.15f, 0.45f)
            constantBias = 0.001f
            normalBias = 1.0f
            shadowBulge = 0.02f
        }

        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(0.98f, 0.95f, 0.88f) // Warm natural sunlight
            .intensity(100000.0f)        // 100k Lux natural direct sun
            .direction(-0.6f, -1.0f, -0.7f)
            .castShadows(true)
            .shadowOptions(shadowOptions)
            .build(engine, sunEntity)

        scene.addEntity(sunEntity)
    }

    private fun setupCeilingSpotLights(warmToned: Boolean) {
        val (r, g, b) = if (warmToned) {
            Triple(1.0f, 0.92f, 0.78f) // 3000K warm halogen/LED
        } else {
            Triple(0.95f, 0.97f, 1.0f) // 4500K neutral white
        }

        // Add 3 downlights aligned with kitchen counter and island
        val positions = listOf(
            floatArrayOf(-1.0f, 2.85f, 0.0f),
            floatArrayOf(0.0f, 2.85f, 0.0f),
            floatArrayOf(1.0f, 2.85f, 0.0f)
        )

        for (pos in positions) {
            val entity = entityManager.create()
            val shadowOptions = LightManager.ShadowOptions().apply {
                mapSize = 1024
                constantBias = 0.002f
                normalBias = 1.2f
            }

            LightManager.Builder(LightManager.Type.FOCUSED_SPOT)
                .color(r, g, b)
                .intensity(14000.0f) // 14,000 Candela recessed downlight
                .position(pos[0], pos[1], pos[2])
                .direction(0.0f, -1.0f, 0.0f)
                .spotLightCone(Math.toRadians(25.0).toFloat(), Math.toRadians(45.0).toFloat())
                .falloff(4.5f)
                .castShadows(true)
                .shadowOptions(shadowOptions)
                .build(engine, entity)

            scene.addEntity(entity)
            spotLightEntities.add(entity)
        }
    }

    private fun setupUnderCabinetAccentLights() {
        // Strip lights along the bottom of wall-mounted cabinets
        val stripPositions = listOf(
            floatArrayOf(-1.2f, 1.45f, -1.35f),
            floatArrayOf(-0.4f, 1.45f, -1.35f),
            floatArrayOf(0.4f, 1.45f, -1.35f),
            floatArrayOf(1.2f, 1.45f, -1.35f)
        )

        for (pos in stripPositions) {
            val entity = entityManager.create()

            LightManager.Builder(LightManager.Type.POINT)
                .color(1.0f, 0.88f, 0.68f) // Soft warm 2700K LED glow
                .intensity(3200.0f)
                .position(pos[0], pos[1], pos[2])
                .falloff(1.2f)
                .castShadows(false)
                .build(engine, entity)

            scene.addEntity(entity)
            spotLightEntities.add(entity)
        }
    }

    fun updateSunDirection(azimuthDeg: Float, elevationDeg: Float) {
        val radA = Math.toRadians(azimuthDeg.toDouble())
        val radE = Math.toRadians(elevationDeg.toDouble())

        val dx = (cos(radE) * sin(radA)).toFloat()
        val dy = (-sin(radE)).toFloat()
        val dz = (cos(radE) * cos(radA)).toFloat()

        val lightManager = engine.lightManager
        val instance = lightManager.getInstance(sunEntity)
        if (instance != 0) {
            lightManager.setDirection(instance, dx, dy, dz)
        }
    }

    fun destroy() {
        if (sunEntity != 0) {
            scene.removeEntity(sunEntity)
            engine.destroyEntity(sunEntity)
            entityManager.destroy(sunEntity)
            sunEntity = 0
        }

        spotLightEntities.forEach { entity ->
            scene.removeEntity(entity)
            engine.destroyEntity(entity)
            entityManager.destroy(entity)
        }
        spotLightEntities.clear()

        indirectLight?.let {
            scene.indirectLight = null
            engine.destroyIndirectLight(it)
            indirectLight = null
        }
    }
}
