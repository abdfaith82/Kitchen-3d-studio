package com.example.core.importer

import com.example.core.math.AABB
import com.example.core.math.Vector3

/**
 * Statistics and metadata for an imported 3D/CAD asset.
 */
data class ImportStats(
    val fileName: String,
    val format: String,
    val fileSizeBytes: Long,
    val vertexCount: Int,
    val triangleCount: Int,
    val dimensionsMm: Vector3,
    val loadDurationMs: Long
)

/**
 * Standardized in-memory 3D mesh ready for rendering in the engine.
 */
data class ImportedMesh(
    val name: String,
    val vertices: List<Vector3>,
    val normals: List<Vector3>,
    val indices: List<Int>,
    val aabb: AABB,
    val defaultColorHex: Long = 0xFF94A3B8 // Muted metallic slate
) {
    val vertexCount: Int get() = vertices.size
    val triangleCount: Int get() = indices.size / 3
}

sealed class ImportResult<out T> {
    data class Success<T>(val data: T, val stats: ImportStats) : ImportResult<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : ImportResult<Nothing>()
}

class ModelImportException(message: String, cause: Throwable? = null) : Exception(message, cause)
