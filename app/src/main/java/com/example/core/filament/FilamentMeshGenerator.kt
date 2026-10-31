package com.example.core.filament

import com.example.core.geometry.WallGeometryCutter
import com.example.core.math.Matrix4
import com.example.core.math.Vector3
import com.example.domain.model.KitchenElement
import com.example.domain.model.RoomStructure
import com.example.domain.model.Wall

/**
 * Filament vertex structure holding Position (XYZ), Normal (XYZ), and Color/UV.
 */
data class FilamentVertex(
    val position: Vector3,
    val normal: Vector3,
    val u: Float = 0f,
    val v: Float = 0f
)

/**
 * Procedural 3D Mesh representation suitable for Filament VertexBuffer and IndexBuffer.
 */
data class FilamentMesh(
    val vertices: List<FilamentVertex>,
    val indices: List<Int>,
    val colorHex: Long,
    val roughness: Float,
    val metallic: Float
)

/**
 * Procedural mesh generator for architectural kitchen elements, parametric cabinets, and dynamic walls.
 */
object FilamentMeshGenerator {

    /**
     * Generates a 3D box mesh (e.g. for cabinet bodies, doors, countertops, wall blocks).
     */
    fun createBoxMesh(
        center: Vector3,
        dimensions: Vector3,
        colorHex: Long,
        roughness: Float = 0.5f,
        metallic: Float = 0.0f
    ): FilamentMesh {
        val hx = dimensions.x * 0.5f
        val hy = dimensions.y * 0.5f
        val hz = dimensions.z * 0.5f

        val vertices = mutableListOf<FilamentVertex>()
        val indices = mutableListOf<Int>()

        fun addFace(p0: Vector3, p1: Vector3, p2: Vector3, p3: Vector3, normal: Vector3) {
            val baseIdx = vertices.size
            vertices.add(FilamentVertex(p0, normal, 0f, 0f))
            vertices.add(FilamentVertex(p1, normal, 1f, 0f))
            vertices.add(FilamentVertex(p2, normal, 1f, 1f))
            vertices.add(FilamentVertex(p3, normal, 0f, 1f))

            indices.add(baseIdx)
            indices.add(baseIdx + 1)
            indices.add(baseIdx + 2)
            indices.add(baseIdx)
            indices.add(baseIdx + 2)
            indices.add(baseIdx + 3)
        }

        // Front Face (+Z)
        addFace(
            center + Vector3(-hx, -hy, hz),
            center + Vector3(hx, -hy, hz),
            center + Vector3(hx, hy, hz),
            center + Vector3(-hx, hy, hz),
            Vector3(0f, 0f, 1f)
        )

        // Back Face (-Z)
        addFace(
            center + Vector3(hx, -hy, -hz),
            center + Vector3(-hx, -hy, -hz),
            center + Vector3(-hx, hy, -hz),
            center + Vector3(hx, hy, -hz),
            Vector3(0f, 0f, -1f)
        )

        // Top Face (+Y)
        addFace(
            center + Vector3(-hx, hy, hz),
            center + Vector3(hx, hy, hz),
            center + Vector3(hx, hy, -hz),
            center + Vector3(-hx, hy, -hz),
            Vector3(0f, 1f, 0f)
        )

        // Bottom Face (-Y)
        addFace(
            center + Vector3(-hx, -hy, -hz),
            center + Vector3(hx, -hy, -hz),
            center + Vector3(hx, -hy, hz),
            center + Vector3(-hx, -hy, hz),
            Vector3(0f, -1f, 0f)
        )

        // Right Face (+X)
        addFace(
            center + Vector3(hx, -hy, hz),
            center + Vector3(hx, -hy, -hz),
            center + Vector3(hx, hy, -hz),
            center + Vector3(hx, hy, hz),
            Vector3(1f, 0f, 0f)
        )

        // Left Face (-X)
        addFace(
            center + Vector3(-hx, -hy, -hz),
            center + Vector3(-hx, -hy, hz),
            center + Vector3(-hx, hy, hz),
            center + Vector3(-hx, hy, -hz),
            Vector3(-1f, 0f, 0f)
        )

        return FilamentMesh(vertices, indices, colorHex, roughness, metallic)
    }

    /**
     * Generates dynamic wall meshes with Boolean cutout blocks for doors and windows.
     */
    fun createWallMeshes(wall: Wall, room: RoomStructure): List<FilamentMesh> {
        val blocks = WallGeometryCutter.decomposeWall(wall)
        val meshes = mutableListOf<FilamentMesh>()

        for (block in blocks) {
            val center = WallGeometryCutter.localBlockToWorldCenter(wall, block, room.widthMm, room.depthMm)
            val dims = when (wall.side) {
                com.example.domain.model.WallSide.NORTH, com.example.domain.model.WallSide.SOUTH ->
                    Vector3(block.lengthMm, block.heightMm, wall.thicknessMm)
                com.example.domain.model.WallSide.EAST, com.example.domain.model.WallSide.WEST ->
                    Vector3(wall.thicknessMm, block.heightMm, block.lengthMm)
            }

            meshes.add(
                createBoxMesh(
                    center = center,
                    dimensions = dims,
                    colorHex = room.wallFinish.colorHex,
                    roughness = room.wallFinish.roughness,
                    metallic = room.wallFinish.metallic
                )
            )
        }

        return meshes
    }

    /**
     * Generates floor slab mesh.
     */
    fun createFloorMesh(room: RoomStructure): FilamentMesh {
        val center = Vector3(room.widthMm * 0.5f, -10f, room.depthMm * 0.5f)
        val dims = Vector3(room.widthMm, 20f, room.depthMm)
        return createBoxMesh(
            center = center,
            dimensions = dims,
            colorHex = room.floorMaterial.colorHex,
            roughness = room.floorMaterial.roughness,
            metallic = room.floorMaterial.metallic
        )
    }

    /**
     * Generates world-space procedural box sub-meshes for a [KitchenElement] using [CabinetAssetLibrary].
     */
    fun createCabinetAssetMeshes(element: KitchenElement): List<FilamentMesh> {
        val assetMesh = CabinetAssetLibrary.fromKitchenElement(element)
        return assetMesh.transformToWorldSpace(
            worldPosition = element.position,
            rotationYDegrees = element.rotationY
        )
    }

    /**
     * Generates world-space procedural box sub-meshes for a [com.example.core.cam.ParametricCabinet]
     * using [CabinetAssetLibrary].
     */
    fun createParametricCabinetMeshes(
        cabinet: com.example.core.cam.ParametricCabinet,
        worldPosition: Vector3 = Vector3.ZERO,
        rotationYDegrees: Float = 0f,
        includeCountertop: Boolean = false
    ): List<FilamentMesh> {
        val assetMesh = CabinetAssetLibrary.generateCabinetAssetMesh(
            cabinet = cabinet,
            includeCountertop = includeCountertop
        )
        return assetMesh.transformToWorldSpace(
            worldPosition = worldPosition,
            rotationYDegrees = rotationYDegrees
        )
    }
}
