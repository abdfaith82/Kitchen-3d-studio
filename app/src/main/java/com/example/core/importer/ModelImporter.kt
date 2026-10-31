package com.example.core.importer

import com.example.core.math.Vector3
import com.example.domain.model.ElementCategory
import com.example.domain.model.KitchenElement
import com.example.domain.model.KitchenElementType
import com.example.domain.model.MaterialFinish
import java.io.InputStream
import java.util.UUID

/**
 * Unified entry point for importing 3D and CAD assets (OBJ, GLB, DXF) from device storage.
 * Enforces file size limits, formats validation, and error management.
 */
object ModelImporter {

    private const val MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024 // 50 Megabytes max to prevent mobile crash

    fun import3DModel(
        inputStream: InputStream,
        fileName: String,
        fileSizeBytes: Long
    ): ImportResult<ImportedMesh> {
        val startTime = System.currentTimeMillis()

        if (fileSizeBytes > MAX_FILE_SIZE_BYTES) {
            return ImportResult.Error(
                "حجم الملف كبير جداً (${fileSizeBytes / (1024 * 1024)} MB). الحد الأقصى المسموح به هو 50 MB لتجنب استنزاف ذاكرة الهاتف."
            )
        }

        val extension = fileName.substringAfterLast('.', "").lowercase()

        return try {
            val mesh = when (extension) {
                "obj" -> ObjParser.parse(inputStream, modelName = fileName.substringBeforeLast('.'))
                "glb", "gltf" -> GlbParser.parse(inputStream, modelName = fileName.substringBeforeLast('.'))
                else -> return ImportResult.Error("صيغة الملف غير مدعومة ($extension). الصيغ المدعومة هي: OBJ, GLB, glTF, DXF")
            }

            val duration = System.currentTimeMillis() - startTime
            val stats = ImportStats(
                fileName = fileName,
                format = extension.uppercase(),
                fileSizeBytes = fileSizeBytes,
                vertexCount = mesh.vertexCount,
                triangleCount = mesh.triangleCount,
                dimensionsMm = mesh.aabb.extents,
                loadDurationMs = duration
            )

            ImportResult.Success(mesh, stats)
        } catch (e: ModelImportException) {
            ImportResult.Error(e.message ?: "فشل في قراءة النموذج ثلاثي الأبعاد", e)
        } catch (e: OutOfMemoryError) {
            ImportResult.Error("نفدت ذاكرة الجهاز أثناء معالجة المجسم، يرجى اختيار ملف أصغر حجماً", e)
        } catch (e: Exception) {
            ImportResult.Error("حدث خطأ غير متوقع أثناء استيراد الملف: ${e.localizedMessage}", e)
        }
    }

    fun importDxf(
        inputStream: InputStream,
        fileName: String,
        fileSizeBytes: Long
    ): ImportResult<DxfDrawing> {
        val startTime = System.currentTimeMillis()

        if (fileSizeBytes > MAX_FILE_SIZE_BYTES) {
            return ImportResult.Error(
                "حجم ملف DXF كبير جداً (${fileSizeBytes / (1024 * 1024)} MB). الحد الأقصى هو 50 MB."
            )
        }

        return try {
            val drawing = DxfParser.parse(inputStream, fileName)
            val duration = System.currentTimeMillis() - startTime

            val stats = ImportStats(
                fileName = fileName,
                format = "DXF",
                fileSizeBytes = fileSizeBytes,
                vertexCount = drawing.lines.size * 2,
                triangleCount = 0,
                dimensionsMm = drawing.bounds.extents,
                loadDurationMs = duration
            )

            ImportResult.Success(drawing, stats)
        } catch (e: Exception) {
            ImportResult.Error("تعذر استيراد ملف DXF: ${e.localizedMessage}", e)
        }
    }

    /**
     * Converts an ImportedMesh into an interactive KitchenElement that can be transformed in the 3D scene.
     * Automatically normalizes meshes authored in meters (< 10 units), centimeters (10..150 units),
     * or oversized CAD units (> 2600 units) into realistic kitchen millimeter dimensions so the imported
     * 3D model is never clamped immovably against room walls.
     */
    fun createKitchenElementFromMesh(mesh: ImportedMesh, initialPosition: Vector3): KitchenElement {
        val extents = mesh.aabb.extents
        val maxAxis = maxOf(extents.x, maxOf(extents.y, extents.z))
        val unitScale = when {
            maxAxis <= 0.001f -> 1f
            maxAxis <= 10f -> 1000f // Mesh authored in meters -> convert to mm
            maxAxis <= 150f -> 10f  // Mesh authored in centimeters -> convert to mm
            maxAxis > 2400f -> 1200f / maxAxis // Oversized mesh -> normalize to 1200mm max span
            else -> 1f
        }

        val scaledX = extents.x * unitScale
        val scaledY = extents.y * unitScale
        val scaledZ = extents.z * unitScale

        val sanitizedDims = Vector3(
            x = (if (scaledX > 50f) scaledX else 600f).coerceIn(200f, 2200f),
            y = (if (scaledY > 50f) scaledY else 850f).coerceIn(200f, 2400f),
            z = (if (scaledZ > 50f) scaledZ else 600f).coerceIn(200f, 1600f)
        ).roundTo1mm()

        return KitchenElement(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            name = mesh.name.ifBlank { "مجسم مستورد 3D" },
            type = KitchenElementType.BASE_SINGLE_DOOR,
            position = initialPosition.roundTo1mm(),
            dimensions = sanitizedDims,
            material = MaterialFinish.STAINLESS_STEEL
        )
    }
}
