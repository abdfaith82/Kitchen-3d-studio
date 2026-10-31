package com.example.core.filament

import com.example.core.math.Matrix4
import com.example.core.math.Vector3
import com.example.domain.model.KitchenElement
import com.example.domain.model.RoomStructure

/**
 * Clean architectural adapter managing Google Filament scene graph, lighting, camera, and entity transforms.
 * Designed to integrate with Filament Engine / SurfaceView or Compose graphics runtime.
 */
class FilamentBridge {

    data class FilamentEntity(
        val id: String,
        val transform: Matrix4,
        val meshes: List<FilamentMesh>,
        val isVisible: Boolean = true
    )

    private val entities = mutableMapOf<String, FilamentEntity>()
    var cameraEye: Vector3 = Vector3(2100f, 2500f, 5500f)
    var cameraTarget: Vector3 = Vector3(2100f, 800f, 1800f)
    var fieldOfView: Float = 45f // in degrees

    /**
     * Updates an element's Filament transform matrix with 1mm precision.
     */
    fun updateElementTransform(element: KitchenElement) {
        val translation = Vector3(
            element.position.x,
            element.position.y + element.dimensions.y * 0.5f,
            element.position.z
        )
        val matrix = Matrix4.trs(translation, element.rotationY, Vector3.ONE)

        // Generate procedural meshes for cabinet carcass and countertop
        val meshes = mutableListOf<FilamentMesh>()

        // 1. Cabinet body
        meshes.add(
            FilamentMeshGenerator.createBoxMesh(
                center = Vector3.ZERO,
                dimensions = element.dimensions,
                colorHex = element.material.colorHex,
                roughness = element.material.roughness,
                metallic = element.material.metallic
            )
        )

        // 2. Countertop slab if present
        element.countertopMaterial?.let { topMat ->
            val topDims = Vector3(element.dimensions.x + 20f, 30f, element.dimensions.z + 20f)
            val topCenter = Vector3(0f, element.dimensions.y * 0.5f + 15f, 0f)
            meshes.add(
                FilamentMeshGenerator.createBoxMesh(
                    center = topCenter,
                    dimensions = topDims,
                    colorHex = topMat.colorHex,
                    roughness = topMat.roughness,
                    metallic = topMat.metallic
                )
            )
        }

        // 3. Handle / Accent
        val handleDims = Vector3(140f, 15f, 25f)
        val handleCenter = Vector3(0f, element.dimensions.y * 0.25f, element.dimensions.z * 0.5f + 15f)
        meshes.add(
            FilamentMeshGenerator.createBoxMesh(
                center = handleCenter,
                dimensions = handleDims,
                colorHex = 0xFFCBD5E1, // Brushed steel
                roughness = 0.2f,
                metallic = 0.8f
            )
        )

        entities[element.id] = FilamentEntity(
            id = element.id,
            transform = matrix,
            meshes = meshes,
            isVisible = element.isVisible
        )
    }

    fun removeEntity(id: String) {
        entities.remove(id)
    }

    fun clear() {
        entities.clear()
    }

    fun getAllEntities(): List<FilamentEntity> = entities.values.toList()
}
