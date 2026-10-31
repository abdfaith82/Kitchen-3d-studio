package com.example.core.importer

import com.example.core.math.AABB
import com.example.core.math.Vector3
import org.json.JSONObject
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * Binary glTF (GLB 2.0) parser.
 * Reads GLB header, extracts JSON scene manifest and binary geometry buffers directly on-device.
 */
object GlbParser {

    private const val GLB_MAGIC = 0x46546C67 // "glTF"
    private const val CHUNK_TYPE_JSON = 0x4E4F534A // "JSON"
    private const val CHUNK_TYPE_BIN = 0x004E4942  // "BIN\0"

    fun parse(
        inputStream: InputStream,
        modelName: String = "GlbModel"
    ): ImportedMesh {
        val bytes = inputStream.readBytes()
        if (bytes.size < 20) {
            throw ModelImportException("حجم ملف GLB صغير جداً أو تالف / Corrupt GLB header")
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Read 12-byte header
        val magic = buffer.int
        if (magic != GLB_MAGIC) {
            throw ModelImportException("صيغة الملف غير متوافقة مع GLB ثنائي (Magic mismatch: expected glTF)")
        }

        val version = buffer.int
        if (version != 2) {
            throw ModelImportException("إصدار GLB غير مدعوم (يدعم إصدار glTF 2.0 فقط)")
        }

        val totalLength = buffer.int
        if (totalLength > bytes.size) {
            throw ModelImportException("الملف غير مكتمل أو تم قطع تحميله / Incomplete binary stream")
        }

        var jsonString: String? = null
        var binBytes: ByteArray? = null

        // Parse chunks
        while (buffer.hasRemaining()) {
            if (buffer.remaining() < 8) break
            val chunkLength = buffer.int
            val chunkType = buffer.int

            if (chunkLength < 0 || chunkLength > buffer.remaining()) break

            val chunkData = ByteArray(chunkLength)
            buffer.get(chunkData)

            when (chunkType) {
                CHUNK_TYPE_JSON -> {
                    jsonString = String(chunkData, Charsets.UTF_8)
                }
                CHUNK_TYPE_BIN -> {
                    binBytes = chunkData
                }
            }
        }

        if (jsonString == null || binBytes == null) {
            throw ModelImportException("الملف لا يحتوي على كتل JSON أو بيانات الهندسة الثنائية (BIN)")
        }

        return extractMeshFromJsonAndBin(jsonString, binBytes, modelName)
    }

    private fun extractMeshFromJsonAndBin(
        jsonString: String,
        binBytes: ByteArray,
        modelName: String
    ): ImportedMesh {
        val root = JSONObject(jsonString)
        val binBuffer = ByteBuffer.wrap(binBytes).order(ByteOrder.LITTLE_ENDIAN)

        val accessors = root.optJSONArray("accessors") ?: throw ModelImportException("لا توجد مصفوفات accessors في ملف glTF")
        val bufferViews = root.optJSONArray("bufferViews") ?: throw ModelImportException("لا توجد bufferViews في ملف glTF")
        val meshes = root.optJSONArray("meshes") ?: throw ModelImportException("لا يحتوي الملف على أي Meshes ثلاثية الأبعاد")

        val firstMesh = meshes.getJSONObject(0)
        val primitives = firstMesh.getJSONArray("primitives")
        val firstPrimitive = primitives.getJSONObject(0)

        val attributes = firstPrimitive.getJSONObject("attributes")
        val posAccessorIdx = attributes.getInt("POSITION")
        val indicesAccessorIdx = firstPrimitive.optInt("indices", -1)

        // 1. Extract Vertex Positions
        val posAccessor = accessors.getJSONObject(posAccessorIdx)
        val posBufferViewIdx = posAccessor.getInt("bufferView")
        val posCount = posAccessor.getInt("count")
        val posByteOffsetInAccessor = posAccessor.optInt("byteOffset", 0)

        val posBufferView = bufferViews.getJSONObject(posBufferViewIdx)
        val posByteOffsetInView = posBufferView.optInt("byteOffset", 0)
        val posTotalOffset = posByteOffsetInView + posByteOffsetInAccessor

        val vertices = mutableListOf<Vector3>()
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE

        binBuffer.position(posTotalOffset)
        for (i in 0 until posCount) {
            val x = binBuffer.float
            val y = binBuffer.float
            val z = binBuffer.float

            minX = min(minX, x)
            minY = min(minY, y)
            minZ = min(minZ, z)
            maxX = max(maxX, x)
            maxY = max(maxY, y)
            maxZ = max(maxZ, z)

            vertices.add(Vector3(x, y, z))
        }

        // 2. Extract Indices
        val indices = mutableListOf<Int>()
        if (indicesAccessorIdx >= 0) {
            val idxAccessor = accessors.getJSONObject(indicesAccessorIdx)
            val idxBufferViewIdx = idxAccessor.getInt("bufferView")
            val idxCount = idxAccessor.getInt("count")
            val idxComponentType = idxAccessor.getInt("componentType")
            val idxByteOffsetInAcc = idxAccessor.optInt("byteOffset", 0)

            val idxBufferView = bufferViews.getJSONObject(idxBufferViewIdx)
            val idxTotalOffset = idxBufferView.optInt("byteOffset", 0) + idxByteOffsetInAcc

            binBuffer.position(idxTotalOffset)
            when (idxComponentType) {
                5123 -> { // UNSIGNED_SHORT
                    for (i in 0 until idxCount) {
                        indices.add(binBuffer.short.toInt() and 0xFFFF)
                    }
                }
                5125 -> { // UNSIGNED_INT
                    for (i in 0 until idxCount) {
                        indices.add(binBuffer.int)
                    }
                }
                5121 -> { // UNSIGNED_BYTE
                    for (i in 0 until idxCount) {
                        indices.add(binBuffer.get().toInt() and 0xFF)
                    }
                }
            }
        } else {
            // Unindexed primitives
            for (i in 0 until vertices.size) {
                indices.add(i)
            }
        }

        var aabb = AABB(Vector3(minX, minY, minZ), Vector3(maxX, maxY, maxZ))

        // Scale to mm if modeled in meters (standard in glTF)
        val maxDim = max(max(aabb.extents.x, aabb.extents.y), aabb.extents.z)
        var finalVertices: List<Vector3> = vertices
        if (maxDim in 0.05f..30f) {
            val scaleMm = 1000f
            finalVertices = vertices.map { it * scaleMm }
            aabb = AABB(aabb.min * scaleMm, aabb.max * scaleMm)
        }

        // Generate simple face normals
        val normals = MutableList(finalVertices.size) { Vector3.UNIT_Y }

        return ImportedMesh(
            name = modelName,
            vertices = finalVertices,
            normals = normals,
            indices = indices,
            aabb = aabb
        )
    }
}
