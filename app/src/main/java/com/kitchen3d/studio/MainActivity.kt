package com.kitchen3d.studio

import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import androidx.appcompat.app.AppCompatActivity
import com.kitchen3d.studio.databinding.ActivityMainBinding
import com.kitchen3d.studio.renderer.KitchenSceneRenderer
import com.kitchen3d.studio.renderer.KitchenTouchGestureListener

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var sceneRenderer: KitchenSceneRenderer? = null
    private var isWarmLight = true

    private var surfaceAttached = false
    private var lastSurfaceWidth = -1
    private var lastSurfaceHeight = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val renderer = KitchenSceneRenderer(this, binding.filamentSurfaceView)
        sceneRenderer = renderer

        val touchListener = KitchenTouchGestureListener(this, renderer.cameraController)
        binding.filamentSurfaceView.setOnTouchListener(touchListener)

        binding.filamentSurfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                // surfaceChanged is always called right after surfaceCreated
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                if (surfaceAttached && width == lastSurfaceWidth && height == lastSurfaceHeight) {
                    return
                }
                if (surfaceAttached) {
                    renderer.onSurfaceDestroyed()
                }
                lastSurfaceWidth = width
                lastSurfaceHeight = height
                surfaceAttached = true
                renderer.onSurfaceAvailable(holder.surface, width, height)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                if (surfaceAttached) {
                    surfaceAttached = false
                    lastSurfaceWidth = -1
                    lastSurfaceHeight = -1
                    renderer.onSurfaceDestroyed()
                }
            }
        })

        setupControls(renderer)
    }

    private fun setupControls(renderer: KitchenSceneRenderer) {
        binding.sliderSunAngle.addOnChangeListener { _, value, _ ->
            renderer.lightingManager.updateSunDirection(
                azimuthDeg = value,
                elevationDeg = 48.0f
            )
        }

        binding.btnLightingMode.setOnClickListener {
            isWarmLight = !isWarmLight
            binding.btnLightingMode.text = if (isWarmLight) "Day / Warm" else "Day / Cool"
            try {
                renderer.lightingManager.destroy()
                renderer.lightingManager.setupLighting(enableWarmInterior = isWarmLight)
                renderer.lightingManager.updateSunDirection(
                    azimuthDeg = binding.sliderSunAngle.value,
                    elevationDeg = 48.0f
                )
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to switch lighting mode", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sceneRenderer?.startRendering()
    }

    override fun onPause() {
        super.onPause()
        sceneRenderer?.stopRendering()
    }

    override fun onDestroy() {
        super.onDestroy()
        sceneRenderer?.destroy()
        sceneRenderer = null
        surfaceAttached = false
    }
}
