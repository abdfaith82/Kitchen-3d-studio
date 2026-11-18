package com.kitchen3d.studio.renderer

import android.content.Context
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.Filament
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.View

/**
 * Coordinates Google Filament engine life-cycle, PBR post-processing pipeline,
 * and per-frame rendering to achieve photorealistic kitchen interior visuals.
 */
class KitchenSceneRenderer(
    private val context: Context,
    private val surfaceView: SurfaceView
) : Choreographer.FrameCallback {

    init {
        Filament.init()
    }

    val engine: Engine = Engine.create()
    val renderer: Renderer = engine.createRenderer()
    val scene: Scene = engine.createScene()
    val view: View = engine.createView()
    val camera: Camera = engine.createCamera(engine.entityManager.create())

    private var swapChain: SwapChain? = null
    private var colorGrading: ColorGrading? = null

    val lightingManager = KitchenLightingManager(engine, scene)
    val cameraController = OrbitCameraController(camera)
    val geometryFactory = KitchenGeometryFactory(engine, scene)

    private val choreographer = Choreographer.getInstance()
    private var isRendering = false

    init {
        view.camera = camera
        view.scene = scene

        configureRealisticPbrView()
        lightingManager.setupLighting(enableWarmInterior = true)
        geometryFactory.buildKitchenEnvironment()
    }

    private fun configureRealisticPbrView() {
        // 1. High Quality Screen-Space Ambient Occlusion (SSAO)
        view.ambientOcclusionOptions = View.AmbientOcclusionOptions().apply {
            enabled = true
            radius = 0.35f
            bias = 0.005f
            power = 1.4f
            resolution = 0.75f
            intensity = 1.5f
            quality = View.QualityLevel.HIGH
            lowPassFilter = View.QualityLevel.HIGH
            upsampling = View.QualityLevel.HIGH
        }

        // 2. Realistic Bloom for glowing materials & light sources
        view.bloomOptions = View.BloomOptions().apply {
            enabled = true
            strength = 0.18f
            resolution = 512
            levels = 6
            threshold = true
        }

        // 3. Cinematic ACES Color Tone Mapping
        colorGrading = ColorGrading.Builder()
            .toneMapping(ColorGrading.ToneMapping.ACES)
            .exposure(0.0f)
            .contrast(1.08f)
            .saturation(1.04f)
            .vignette(0.2f)
            .build(engine)
        view.colorGrading = colorGrading

        // 4. Anti-Aliasing (FXAA & TAA)
        view.antiAliasing = View.AntiAliasing.FXAA
        view.temporalAntiAliasingOptions = View.TemporalAntiAliasingOptions().apply {
            enabled = true
            feedback = 0.88f
        }

        // 5. Dynamic resolution & shadows
        view.isShadowingEnabled = true
        view.renderQuality = View.RenderQuality().apply {
            hdrColorBuffer = View.QualityLevel.ULTRA
        }
    }

    fun onSurfaceAvailable(surface: Surface, width: Int, height: Int) {
        swapChain = engine.createSwapChain(surface)
        onSurfaceResized(width, height)
        startRendering()
    }

    fun onSurfaceResized(width: Int, height: Int) {
        view.viewport = com.google.android.filament.Viewport(0, 0, width, height)
        cameraController.updateAspectRatio(width.toFloat() / height.coerceAtLeast(1).toFloat())
    }

    fun onSurfaceDestroyed() {
        stopRendering()
        swapChain?.let {
            engine.destroySwapChain(it)
            swapChain = null
        }
    }

    fun startRendering() {
        if (!isRendering) {
            isRendering = true
            choreographer.postFrameCallback(this)
        }
    }

    fun stopRendering() {
        if (isRendering) {
            isRendering = false
            choreographer.removeFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRendering) return

        cameraController.update()

        val currentSwapChain = swapChain
        if (currentSwapChain != null && renderer.beginFrame(currentSwapChain, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
        }

        choreographer.postFrameCallback(this)
    }

    fun destroy() {
        stopRendering()
        lightingManager.destroy()
        geometryFactory.destroy()

        colorGrading?.let {
            engine.destroyColorGrading(it)
            colorGrading = null
        }

        swapChain?.let {
            engine.destroySwapChain(it)
            swapChain = null
        }

        engine.destroyCameraComponent(camera.entity)
        engine.entityManager.destroy(camera.entity)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyRenderer(renderer)
        engine.destroy()
    }
}
