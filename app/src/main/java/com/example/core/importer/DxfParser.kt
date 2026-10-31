package com.example.core.importer

import com.example.core.math.AABB
import com.example.core.math.Vector3
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.max
import kotlin.math.min

data class DxfLine(
    val start: Vector3,
    val end: Vector3,
    val layer: String
)

data class DxfPolyline(
    val points: List<Vector3>,
    val isClosed: Boolean,
    val layer: String
)

data class DxfDrawing(
    val fileName: String,
    val lines: List<DxfLine>,
    val polylines: List<DxfPolyline>,
    val bounds: AABB,
    val layers: Set<String>
) {
    val totalElementsCount: Int get() = lines.size + polylines.size
}

/**
 * Robust ASCII DXF (Drawing Exchange Format) parser for AutoCAD 2D/3D floorplans.
 * Supports both R12 POLYLINE (VERTEX / SEQEND) and R14/2000+ LWPOLYLINE and LINE entities.
 */
object DxfParser {

    private const val MAX_DXF_ENTITIES = 50_000

    fun parse(
        inputStream: InputStream,
        fileName: String = "Drawing.dxf",
        autoScaleToMillimeters: Boolean = true
    ): DxfDrawing {
        val reader = BufferedReader(InputStreamReader(inputStream), 32 * 1024)
        val lines = mutableListOf<DxfLine>()
        val polylines = mutableListOf<DxfPolyline>()
        val layers = mutableSetOf<String>()

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE

        fun recordPoint(x: Float, y: Float, z: Float) {
            minX = min(minX, x)
            minY = min(minY, y)
            minZ = min(minZ, z)
            maxX = max(maxX, x)
            maxY = max(maxY, y)
            maxZ = max(maxZ, z)
        }

        try {
            var insideEntities = false
            var currentEntity: String? = null

            // LINE attributes
            var lineLayer = "0"
            var lineX0 = 0f; var lineY0 = 0f; var lineZ0 = 0f
            var lineX1 = 0f; var lineY1 = 0f; var lineZ1 = 0f

            // LWPOLYLINE / R12 POLYLINE attributes
            var polyLayer = "0"
            var polyClosed = false
            var insideR12Polyline = false
            val currentPolyPoints = mutableListOf<Vector3>()
            var nextIsY = false
            var curX = 0f
            var vertexX = 0f
            var vertexY = 0f
            var vertexZ = 0f
            var hasVertexCoords = false

            while (true) {
                val codeLine = reader.readLine() ?: break
                val valueLine = reader.readLine() ?: break

                val code = codeLine.trim().toIntOrNull() ?: continue
                val value = valueLine.trim()

                if (code == 2 && value == "ENTITIES") {
                    insideEntities = true
                    continue
                }

                if (code == 0) {
                    // Flush previous VERTEX if inside R12 POLYLINE
                    if (currentEntity == "VERTEX" && insideR12Polyline && hasVertexCoords) {
                        recordPoint(vertexX, vertexY, vertexZ)
                        currentPolyPoints.add(Vector3(vertexX, vertexZ, vertexY))
                        hasVertexCoords = false
                    }

                    // Flush previous entity if any
                    when (currentEntity) {
                        "LINE" -> {
                            recordPoint(lineX0, lineY0, lineZ0)
                            recordPoint(lineX1, lineY1, lineZ1)
                            lines.add(
                                DxfLine(
                                    start = Vector3(lineX0, lineZ0, lineY0), // map 2D CAD (X, Y) to 3D Floor (X, Z)
                                    end = Vector3(lineX1, lineZ1, lineY1),
                                    layer = lineLayer
                                )
                            )
                            layers.add(lineLayer)
                        }
                        "LWPOLYLINE" -> {
                            if (currentPolyPoints.isNotEmpty()) {
                                polylines.add(
                                    DxfPolyline(
                                        points = currentPolyPoints.toList(),
                                        isClosed = polyClosed,
                                        layer = polyLayer
                                    )
                                )
                                layers.add(polyLayer)
                            }
                        }
                    }

                    if (value == "SEQEND" && insideR12Polyline) {
                        if (currentPolyPoints.isNotEmpty()) {
                            polylines.add(
                                DxfPolyline(
                                    points = currentPolyPoints.toList(),
                                    isClosed = polyClosed,
                                    layer = polyLayer
                                )
                            )
                            layers.add(polyLayer)
                        }
                        insideR12Polyline = false
                        currentPolyPoints.clear()
                        currentEntity = null
                        continue
                    }

                    if (value == "VERTEX" && insideR12Polyline) {
                        currentEntity = "VERTEX"
                        vertexX = 0f
                        vertexY = 0f
                        vertexZ = 0f
                        hasVertexCoords = false
                        continue
                    }

                    if (value == "ENDSEC") {
                        insideEntities = false
                        insideR12Polyline = false
                        currentEntity = null
                        continue
                    }

                    if (value == "SECTION") {
                        currentEntity = null
                        continue
                    }

                    if (insideEntities) {
                        currentEntity = value
                        lineLayer = "0"
                        if (value == "POLYLINE") {
                            insideR12Polyline = true
                            polyLayer = "0"
                            polyClosed = false
                            currentPolyPoints.clear()
                        } else if (value == "LWPOLYLINE") {
                            insideR12Polyline = false
                            polyLayer = "0"
                            polyClosed = false
                            currentPolyPoints.clear()
                        }

                        if (lines.size + polylines.size > MAX_DXF_ENTITIES) {
                            throw ModelImportException("ملف DXF يحتوي على عناصر تفوق الحد المسموح ($MAX_DXF_ENTITIES عنصر)")
                        }
                    } else {
                        currentEntity = null
                    }
                    continue
                }

                if (!insideEntities) continue

                when (currentEntity) {
                    "LINE" -> {
                        when (code) {
                            8 -> lineLayer = value
                            10 -> lineX0 = value.toFloatOrNull() ?: 0f
                            20 -> lineY0 = value.toFloatOrNull() ?: 0f
                            30 -> lineZ0 = value.toFloatOrNull() ?: 0f
                            11 -> lineX1 = value.toFloatOrNull() ?: 0f
                            21 -> lineY1 = value.toFloatOrNull() ?: 0f
                            31 -> lineZ1 = value.toFloatOrNull() ?: 0f
                        }
                    }
                    "POLYLINE" -> {
                        when (code) {
                            8 -> polyLayer = value
                            70 -> polyClosed = (value.toIntOrNull() ?: 0) and 1 == 1
                        }
                    }
                    "VERTEX" -> {
                        when (code) {
                            10 -> {
                                vertexX = value.toFloatOrNull() ?: 0f
                                hasVertexCoords = true
                            }
                            20 -> {
                                vertexY = value.toFloatOrNull() ?: 0f
                                hasVertexCoords = true
                            }
                            30 -> {
                                vertexZ = value.toFloatOrNull() ?: 0f
                            }
                        }
                    }
                    "LWPOLYLINE" -> {
                        when (code) {
                            8 -> polyLayer = value
                            70 -> polyClosed = (value.toIntOrNull() ?: 0) and 1 == 1
                            10 -> {
                                curX = value.toFloatOrNull() ?: 0f
                                nextIsY = true
                            }
                            20 -> {
                                if (nextIsY) {
                                    val curY = value.toFloatOrNull() ?: 0f
                                    recordPoint(curX, curY, 0f)
                                    currentPolyPoints.add(Vector3(curX, 0f, curY))
                                    nextIsY = false
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            reader.close()
        }

        if (lines.isEmpty() && polylines.isEmpty()) {
            throw ModelImportException("لم يتم العثور على خطوط هندسية داخل قسم ENTITIES في ملف DXF")
        }

        var bounds = AABB(Vector3(minX, 0f, minY), Vector3(maxX, 0f, maxY))

        // Unit scaling: if DXF was drawn in meters (e.g. room width = 4.0), convert to mm
        val maxExtent = max(bounds.extents.x, bounds.extents.z)
        if (autoScaleToMillimeters && maxExtent in 0.05f..30f) {
            val scaleFactor = 1000f
            val scaledLines = lines.map {
                DxfLine(
                    start = Vector3(it.start.x * scaleFactor, it.start.y, it.start.z * scaleFactor),
                    end = Vector3(it.end.x * scaleFactor, it.end.y, it.end.z * scaleFactor),
                    layer = it.layer
                )
            }
            val scaledPolylines = polylines.map { poly ->
                DxfPolyline(
                    points = poly.points.map { Vector3(it.x * scaleFactor, it.y, it.z * scaleFactor) },
                    isClosed = poly.isClosed,
                    layer = poly.layer
                )
            }
            bounds = AABB(
                Vector3(bounds.min.x * scaleFactor, 0f, bounds.min.z * scaleFactor),
                Vector3(bounds.max.x * scaleFactor, 0f, bounds.max.z * scaleFactor)
            )
            return DxfDrawing(fileName, scaledLines, scaledPolylines, bounds, layers)
        }

        return DxfDrawing(fileName, lines, polylines, bounds, layers)
    }
}
