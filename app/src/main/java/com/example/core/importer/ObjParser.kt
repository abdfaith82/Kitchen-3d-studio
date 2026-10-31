package com.example.core.importer

import com.example.core.math.AABB
import com.example.core.math.Vector3
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance, streaming Wavefront (.OBJ) parser.
 * Handles vertices, normals, triangulated and polygon faces with memory safety guards.
 */
object ObjParser {

    private const val MAX_ALLOWED_VERTICES = 250_000 // Guard against OutOfMemory on mobile

    fun parse(
        inputStream: InputStream,
        modelName: String = "ImportedModel",
        autoScaleToMillimeters: Boolean = true
    ): ImportedMesh {
        val rawPositions = mutableListOf<Vector3>()
        val rawNormals = mutableListOf<Vector3>()

        val finalVertices = mutableListOf<Vector3>()
        val finalNormals = mutableListOf<Vector3>()
        val finalIndices = mutableListOf<Int>()

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE

        val reader = BufferedReader(InputStreamReader(inputStream), 32 * 1024)

        try {
            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine

                val parts = trimmed.split("\\s+".toRegex())
                when (parts[0]) {
                    "v" -> {
                        if (parts.size >= 4) {
                            val x = parts[1].toFloatOrNull() ?: 0f
                            val y = parts[2].toFloatOrNull() ?: 0f
                            val z = parts[3].toFloatOrNull() ?: 0f

                            minX = min(minX, x)
                            minY = min(minY, y)
                            minZ = min(minZ, z)
                            maxX = max(maxX, x)
                            maxY = max(maxY, y)
                            maxZ = max(maxZ, z)

                            rawPositions.add(Vector3(x, y, z))

                            if (rawPositions.size > MAX_ALLOWED_VERTICES) {
                                throw ModelImportException(
                                    "الملف يحتوي على أكثر من $MAX_ALLOWED_VERTICES نقطة وهو ضخم جداً لذاكرة الهاتف / Model exceeds vertex limit"
                                )
                            }
                        }
                    }
                    "vn" -> {
                        if (parts.size >= 4) {
                            val nx = parts[1].toFloatOrNull() ?: 0f
                            val ny = parts[2].toFloatOrNull() ?: 0f
                            val nz = parts[3].toFloatOrNull() ?: 0f
                            rawNormals.add(Vector3(nx, ny, nz).normalized())
                        }
                    }
                    "f" -> {
                        // Face vertices can be formatted as: v, v/vt, v//vn, or v/vt/vn
                        if (parts.size >= 4) {
                            val faceIndices = mutableListOf<Int>()

                            for (i in 1 until parts.size) {
                                val token = parts[i]
                                val subParts = token.split("/")
                                val vIdx = subParts[0].toIntOrNull() ?: continue
                                // 1-based indexing in OBJ, negative numbers are relative
                                val actualVIdx = if (vIdx > 0) vIdx - 1 else rawPositions.size + vIdx

                                if (actualVIdx in rawPositions.indices) {
                                    val v = rawPositions[actualVIdx]
                                    var n = Vector3.UNIT_Y

                                    if (subParts.size >= 3 && subParts[2].isNotEmpty()) {
                                        val vnIdx = subParts[2].toIntOrNull() ?: 0
                                        val actualVnIdx = if (vnIdx > 0) vnIdx - 1 else rawNormals.size + vnIdx
                                        if (actualVnIdx in rawNormals.indices) {
                                            n = rawNormals[actualVnIdx]
                                        }
                                    }

                                    val newIdx = finalVertices.size
                                    finalVertices.add(v)
                                    finalNormals.add(n)
                                    faceIndices.add(newIdx)
                                }
                            }

                            // Triangulate n-gon face using fan triangulation
                            if (faceIndices.size >= 3) {
                                for (i in 1 until faceIndices.size - 1) {
                                    finalIndices.add(faceIndices[0])
                                    finalIndices.add(faceIndices[i])
                                    finalIndices.add(faceIndices[i + 1])
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: OutOfMemoryError) {
            throw ModelImportException("نفدت الذاكرة أثناء معالجة ملف OBJ، يرجى استخدام نموذج بأبعاد أخف", e)
        } finally {
            reader.close()
        }

        if (finalVertices.isEmpty()) {
            throw ModelImportException("ملف OBJ فارغ أو لا يحتوي على هندسة ثلاثية أبعاد صالحة")
        }

        // Compute Bounding Box
        var aabb = AABB(Vector3(minX, minY, minZ), Vector3(maxX, maxY, maxZ))

        // Automatic Unit Detection (e.g. if model max dimension is < 20, it is likely in meters; convert to millimeters)
        var scaledVertices: List<Vector3> = finalVertices
        val maxExtent = max(max(aabb.extents.x, aabb.extents.y), aabb.extents.z)

        if (autoScaleToMillimeters && maxExtent in 0.05f..25f) {
            val scaleFactor = 1000f // meters to mm
            scaledVertices = finalVertices.map { it * scaleFactor }
            aabb = AABB(aabb.min * scaleFactor, aabb.max * scaleFactor)
        }

        return ImportedMesh(
            name = modelName,
            vertices = scaledVertices,
            normals = finalNormals,
            indices = finalIndices,
            aabb = aabb
        )
    }
}
