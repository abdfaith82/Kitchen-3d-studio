package com.example.core.export

import com.example.core.cam.AluminumBarNestingOptimizer
import com.example.core.cam.AluminumNestingConfig
import com.example.core.cam.AluminumNestingResult
import com.example.core.cam.CutPart
import com.example.core.cam.CutPartCategory
import com.example.core.cam.CustomMaterialOverridePreset
import com.example.core.cam.DoorStyle
import com.example.core.cam.HandleStyle
import com.example.core.cam.NestingEngine
import com.example.core.cam.PackedSheet
import com.example.core.cam.ParametricCabinet
import com.example.core.geometry.ErgonomicValidationEngine
import com.example.core.geometry.ErgonomicValidationReport
import com.example.domain.model.ElementCategory
import com.example.domain.model.KitchenElement
import com.example.domain.model.KitchenElementType
import com.example.domain.model.LayerType
import com.example.domain.model.OpeningType
import com.example.domain.model.UtilityPointType
import com.example.domain.model.Wall
import com.example.domain.model.WallOpening
import com.example.domain.model.WallSide
import com.example.domain.model.WallUtilityPoint
import com.example.domain.repository.KitchenProject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Standard AutoCAD ASCII DXF (Release 12 / AC1009 & LT2000+ compliant) floor plan & CNC Cut-List serializer.
 * Serializes both:
 * 1. The complete 2D room floorplan (active structural walls, L/U/Rectangular topologies, out-of-square
 *    corner angles, doors with 90° swing arcs, windows with glazing lines, rotated base/wall/tall cabinets,
 *    appliances with burner/sink symbols, MEP utility points, ergonomic work triangle vectors, and dimensions).
 * 2. Complete CNC Cabinet Cut Lists (2D nested 2440x1220mm melamine sheets, 1:1 panel cut contours,
 *    back-panel router grooves [الريشة], 35mm hinge boring circles, edge-banding markers, grain direction
 *    vectors, cabinet panel schedule table, and 1D nested aluminum extrusion bars).
 * Compatible with AutoCAD, AlphaCAM, VCarve Pro, Aspire, WoodWOP, LibreCAD, and QCAD.
 */
object DxfExporter {

    data class DxfExportOptions(
        val includeWalls: Boolean = true,
        val includeOpenings: Boolean = true,
        val includeCabinets: Boolean = true,
        val includeAppliances: Boolean = true,
        val includeMepUtilities: Boolean = true,
        val includeWorkTriangle: Boolean = true,
        val includeDimensions: Boolean = true,
        val includeLabels: Boolean = true,
        val includeTitleBlock: Boolean = true,
        val includeReferenceGrid: Boolean = false,
        val useCentimeters: Boolean = false,
        val includeCabinetCutList: Boolean = true,
        val includeCncNestedSheets: Boolean = true,
        val includeAluminumBarCutList: Boolean = true,
        val includeRouterGroovesAndDrilling: Boolean = true,
        val standardAluminumBarLengthMm: Float = 6000f
    )

    data class DxfLayerDefinition(
        val name: String,
        val aciColor: Int,
        val lineType: String = "CONTINUOUS",
        val descriptionAr: String
    )

    data class DxfSerializationResult(
        val dxfContent: String,
        val totalEntitiesCount: Int,
        val layerEntityCounts: Map<String, Int>,
        val boundsMinX: Float,
        val boundsMinY: Float,
        val boundsMaxX: Float,
        val boundsMaxY: Float,
        val exportedWallsCount: Int,
        val exportedOpeningsCount: Int,
        val exportedCabinetsCount: Int,
        val exportedAppliancesCount: Int,
        val exportedMepPointsCount: Int,
        val exportedDimensionsCount: Int,
        val unitCode: Int,
        val exportedCncSheetsCount: Int = 0,
        val exportedCncCutPartsCount: Int = 0,
        val exportedAluminumBarsCount: Int = 0,
        val exportedAluminumCutPiecesCount: Int = 0
    ) {
        val fileSizeBytes: Int get() = dxfContent.toByteArray(StandardCharsets.UTF_8).size
    }

    val STANDARD_LAYERS: List<DxfLayerDefinition> = listOf(
        DxfLayerDefinition("0", 7, "CONTINUOUS", "الطبقة الافتراضية القياسية"),
        DxfLayerDefinition("WALLS", 1, "CONTINUOUS", "خطوط الجدران الإنشائية الداخلية والخارجية"),
        DxfLayerDefinition("OPENINGS", 2, "CONTINUOUS", "الأبواب مع قوس فتح الدرفة والنوافذ المزدوجة"),
        DxfLayerDefinition("CABINETS_BASE", 3, "CONTINUOUS", "مساقط الخزائن السفلية والجزر مع اتجاه الفتح"),
        DxfLayerDefinition("CABINETS_WALL", 4, "DASHED", "مساقط الخزائن العلوية والتاج (خطوط متقطعة)"),
        DxfLayerDefinition("CABINETS_TALL", 6, "CONTINUOUS", "الدواليب الطولية والفيترينات والأبراج"),
        DxfLayerDefinition("APPLIANCES", 30, "CONTINUOUS", "الأجهزة الكهربائية والعيون والأحواض"),
        DxfLayerDefinition("MEP_UTILITIES", 5, "CONTINUOUS", "نقاط الكهرباء (منطقة 60 سم) والسباكة والغاز والتهوية"),
        DxfLayerDefinition("WORK_TRIANGLE", 40, "DASHED", "مثلث العمل الإرجونومي ومسارات خلوص الأبواب"),
        DxfLayerDefinition("DIMENSIONS", 7, "CONTINUOUS", "خطوط الأبعاد الهندسية والمقاسات الخارجية والداخلية"),
        DxfLayerDefinition("TEXT_LABELS", 5, "CONTINUOUS", "تسميات الوحدات وجدول عنوان المخطط (Title Block)"),
        DxfLayerDefinition("CAD_GRID", 8, "CONTINUOUS", "شبكة المحاور الإنشائية المرجعية (500 مم)"),
        DxfLayerDefinition("CNC_SHEET_BORDER", 7, "CONTINUOUS", "حدود ألواح الميلامين الخام القياسية (2440×1220 مم) وهامش التنظيف"),
        DxfLayerDefinition("CNC_PANEL_CUT", 3, "CONTINUOUS", "مسارات قص ألواح الخزائن الخارجية لماكينات CNC Router"),
        DxfLayerDefinition("CNC_ROUTER_GROOVE", 1, "DASHED", "مجرى تفريز ظهر الخزانة (الريشة) وثقوب المفصلات 35 مم"),
        DxfLayerDefinition("CNC_EDGE_BANDING", 30, "CONTINUOUS", "علامات شريط الحواف PVC/ABS على أضلاع القطع"),
        DxfLayerDefinition("CNC_ALU_BARS", 4, "CONTINUOUS", "مخطط قص وتعشيق قضبان وبروفايلات الألمنيوم القياسية"),
        DxfLayerDefinition("CNC_PART_LABELS", 2, "CONTINUOUS", "تسميات قطع القص وأبعادها واتجاه العرق وجدول التفصيل")
    )

    /**
     * Dedicated function to export BOTH the 2D room floorplan AND the cabinet CNC cut lists
     * (2D nested melamine sheets, panel contours, router grooves, 35mm hinge holes, and 1D aluminum bars)
     * into a single structured DXF string ready for professional CNC manufacturing software.
     */
    fun exportFloorPlanAndCutListsToDxf(
        project: KitchenProject,
        cabinets: List<ParametricCabinet>? = null,
        materialPreset: CustomMaterialOverridePreset = CustomMaterialOverridePreset.DEFAULT_PRESETS.first(),
        options: DxfExportOptions = DxfExportOptions(
            includeCabinetCutList = true,
            includeCncNestedSheets = true,
            includeAluminumBarCutList = true,
            includeRouterGroovesAndDrilling = true
        ),
        hiddenLayers: Set<LayerType> = emptySet(),
        validationReport: ErgonomicValidationReport? = null,
        workshopName: String = "Kitchen3D Studio Engineering"
    ): String {
        return serializeFloorPlanWithMetadata(
            project = project,
            options = options.copy(includeCabinetCutList = true),
            hiddenLayers = hiddenLayers,
            validationReport = validationReport,
            workshopName = workshopName,
            explicitCabinets = cabinets,
            materialPreset = materialPreset
        ).dxfContent
    }

    /**
     * Primary function that serializes the current 2D floor plan (and optional CNC cut lists) into a DXF string.
     */
    fun serializeFloorPlanToDxf(
        project: KitchenProject,
        options: DxfExportOptions = DxfExportOptions(),
        hiddenLayers: Set<LayerType> = emptySet(),
        validationReport: ErgonomicValidationReport? = null,
        workshopName: String = "Kitchen3D Studio Engineering"
    ): String {
        return serializeFloorPlanWithMetadata(
            project = project,
            options = options,
            hiddenLayers = hiddenLayers,
            validationReport = validationReport,
            workshopName = workshopName
        ).dxfContent
    }

    /**
     * Generates standard ASCII DXF content from a [KitchenProject] (backward-compatible entrypoint).
     */
    fun generateDxf(
        project: KitchenProject,
        options: DxfExportOptions = DxfExportOptions()
    ): String {
        return serializeFloorPlanToDxf(project = project, options = options)
    }

    /**
     * Extracts [ParametricCabinet] fabrication models from a [KitchenProject]'s visible cabinet elements.
     */
    fun extractParametricCabinetsFromProject(
        project: KitchenProject,
        preset: CustomMaterialOverridePreset = CustomMaterialOverridePreset.DEFAULT_PRESETS.first(),
        hiddenLayers: Set<LayerType> = emptySet()
    ): List<ParametricCabinet> {
        val cabinetElements = project.elements.filter {
            it.isVisible &&
                it.layer !in hiddenLayers &&
                (it.type.category == ElementCategory.BASE_CABINET ||
                    it.type.category == ElementCategory.WALL_CABINET ||
                    it.type.category == ElementCategory.BRIDGE_CABINET ||
                    it.type.category == ElementCategory.TALL_UNIT ||
                    it.type.category == ElementCategory.ISLAND_AND_SURFACE)
        }
        if (cabinetElements.isEmpty()) {
            return listOf(
                ParametricCabinet(
                    id = "cab_default_60",
                    name = "خزانة سفلية قياسية 60 سم",
                    widthMm = 600f,
                    heightMm = 850f,
                    depthMm = 600f,
                    panelThicknessMm = preset.melamineThicknessMm,
                    backPanelThicknessMm = preset.backPanelThicknessMm,
                    hasBackPanel = preset.hasBackPanel,
                    backPanelMaterialName = preset.backPanelMaterialName,
                    backGrooveInsetMm = preset.backGrooveInsetMm,
                    topStretcherWidthMm = preset.topStretcherWidthMm,
                    aluminumProfileSectionSizeMm = preset.aluminumProfileSectionSizeMm,
                    aluminumProfileThicknessMm = preset.aluminumProfileThicknessMm,
                    manualWidthDeductionMm = preset.customWidthDeductionMm,
                    aluminumWastePercentage = preset.aluminumWastePercentage
                )
            )
        }
        return cabinetElements.map { elem ->
            val isDrawerUnit = elem.name.contains("أدراج") || elem.type == KitchenElementType.BASE_3_DRAWERS
            ParametricCabinet(
                id = elem.id,
                name = elem.name,
                widthMm = elem.dimensions.x,
                heightMm = elem.dimensions.y,
                depthMm = elem.dimensions.z,
                panelThicknessMm = elem.customBoardThicknessMm,
                backPanelThicknessMm = preset.backPanelThicknessMm,
                numberOfShelves = if (isDrawerUnit) 0 else 1,
                numberOfDrawers = if (isDrawerUnit) 3 else 0,
                doorStyle = DoorStyle.SLAB_MODERN,
                handleStyle = HandleStyle.BAR_PULL,
                hasBackPanel = elem.hasBackPanel,
                backPanelMaterialName = elem.backPanelMaterialType,
                backGrooveInsetMm = elem.backGrooveInsetMm,
                topStretcherWidthMm = elem.topStretcherWidthMm,
                aluminumProfileSectionSizeMm = elem.customAluminumProfileMm,
                aluminumProfileThicknessMm = preset.aluminumProfileThicknessMm,
                manualWidthDeductionMm = preset.customWidthDeductionMm,
                aluminumWastePercentage = preset.aluminumWastePercentage
            )
        }
    }

    /**
     * Serializes the 2D floor plan and CNC cabinet cut lists into a structured DXF format and returns
     * rich metadata (entity counts per layer, bounding extents, and component breakdown).
     */
    fun serializeFloorPlanWithMetadata(
        project: KitchenProject,
        options: DxfExportOptions = DxfExportOptions(),
        hiddenLayers: Set<LayerType> = emptySet(),
        validationReport: ErgonomicValidationReport? = null,
        workshopName: String = "Kitchen3D Studio Engineering",
        explicitCabinets: List<ParametricCabinet>? = null,
        materialPreset: CustomMaterialOverridePreset = CustomMaterialOverridePreset.DEFAULT_PRESETS.first()
    ): DxfSerializationResult {
        val unitScale = if (options.useCentimeters) 0.1f else 1.0f
        val unitSuffix = if (options.useCentimeters) "cm" else "mm"
        val insUnitsCode = if (options.useCentimeters) 5 else 4 // 4 = Millimeters, 5 = Centimeters

        val entitiesBuilder = StringBuilder(32_768)
        val layerCounts = mutableMapOf<String, Int>()
        var totalEntities = 0

        var minX = 0f
        var minY = 0f
        var maxX = project.room.widthMm * unitScale
        var maxY = project.room.depthMm * unitScale

        fun recordPoint(x: Float, y: Float) {
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }

        fun recordEntity(layer: String) {
            totalEntities++
            layerCounts[layer] = (layerCounts[layer] ?: 0) + 1
        }

        fun emitLine(
            layer: String,
            x1Mm: Float,
            y1Mm: Float,
            x2Mm: Float,
            y2Mm: Float,
            lineType: String? = null
        ) {
            val x1 = x1Mm * unitScale
            val y1 = y1Mm * unitScale
            val x2 = x2Mm * unitScale
            val y2 = y2Mm * unitScale
            recordPoint(x1, y1)
            recordPoint(x2, y2)
            recordEntity(layer)

            entitiesBuilder.append("0\nLINE\n8\n$layer\n")
            if (lineType != null) {
                entitiesBuilder.append("6\n$lineType\n")
            }
            entitiesBuilder.append(String.format(Locale.US, "10\n%.2f\n20\n%.2f\n30\n0.0\n", x1, y1))
            entitiesBuilder.append(String.format(Locale.US, "11\n%.2f\n21\n%.2f\n31\n0.0\n", x2, y2))
        }

        fun emitPolyline(
            layer: String,
            pointsMm: List<Pair<Float, Float>>,
            isClosed: Boolean = true,
            lineType: String? = null
        ) {
            if (pointsMm.isEmpty()) return
            recordEntity(layer)
            entitiesBuilder.append("0\nPOLYLINE\n8\n$layer\n")
            if (lineType != null) {
                entitiesBuilder.append("6\n$lineType\n")
            }
            entitiesBuilder.append("66\n1\n70\n${if (isClosed) 1 else 0}\n")
            pointsMm.forEach { (pxMm, pyMm) ->
                val px = pxMm * unitScale
                val py = pyMm * unitScale
                recordPoint(px, py)
                entitiesBuilder.append("0\nVERTEX\n8\n$layer\n")
                entitiesBuilder.append(String.format(Locale.US, "10\n%.2f\n20\n%.2f\n30\n0.0\n", px, py))
            }
            entitiesBuilder.append("0\nSEQEND\n")
        }

        fun emitCircle(
            layer: String,
            centerXMm: Float,
            centerYMm: Float,
            radiusMm: Float
        ) {
            val cx = centerXMm * unitScale
            val cy = centerYMm * unitScale
            val r = radiusMm * unitScale
            recordPoint(cx - r, cy - r)
            recordPoint(cx + r, cy + r)
            recordEntity(layer)

            entitiesBuilder.append("0\nCIRCLE\n8\n$layer\n")
            entitiesBuilder.append(String.format(Locale.US, "10\n%.2f\n20\n%.2f\n30\n0.0\n", cx, cy))
            entitiesBuilder.append(String.format(Locale.US, "40\n%.2f\n", r))
        }

        fun emitArc(
            layer: String,
            centerXMm: Float,
            centerYMm: Float,
            radiusMm: Float,
            startAngleDeg: Float,
            endAngleDeg: Float
        ) {
            val cx = centerXMm * unitScale
            val cy = centerYMm * unitScale
            val r = radiusMm * unitScale
            recordPoint(cx - r, cy - r)
            recordPoint(cx + r, cy + r)
            recordEntity(layer)

            entitiesBuilder.append("0\nARC\n8\n$layer\n")
            entitiesBuilder.append(String.format(Locale.US, "10\n%.2f\n20\n%.2f\n30\n0.0\n", cx, cy))
            entitiesBuilder.append(String.format(Locale.US, "40\n%.2f\n", r))
            entitiesBuilder.append(String.format(Locale.US, "50\n%.2f\n51\n%.2f\n", startAngleDeg, endAngleDeg))
        }

        fun emitText(
            layer: String,
            text: String,
            xMm: Float,
            yMm: Float,
            heightMm: Float,
            rotationDeg: Float = 0f
        ) {
            val cleanText = text.replace("\n", " ").trim()
            if (cleanText.isEmpty()) return
            val x = xMm * unitScale
            val y = yMm * unitScale
            val h = (heightMm * unitScale).coerceAtLeast(1.0f)
            recordPoint(x, y)
            recordPoint(x + cleanText.length * h * 0.5f, y + h)
            recordEntity(layer)

            entitiesBuilder.append("0\nTEXT\n8\n$layer\n")
            entitiesBuilder.append(String.format(Locale.US, "10\n%.2f\n20\n%.2f\n30\n0.0\n", x, y))
            entitiesBuilder.append(String.format(Locale.US, "40\n%.2f\n", h))
            entitiesBuilder.append("1\n$cleanText\n")
            if (rotationDeg != 0f) {
                entitiesBuilder.append(String.format(Locale.US, "50\n%.2f\n", rotationDeg))
            }
        }

        val room = project.room
        val roomW = room.widthMm
        val roomD = room.depthMm
        val wallThick = room.wallThicknessMm

        var exportedWallsCount = 0
        var exportedOpeningsCount = 0
        var exportedCabinetsCount = 0
        var exportedAppliancesCount = 0
        var exportedMepPointsCount = 0
        var exportedDimensionsCount = 0
        var exportedCncSheetsCount = 0
        var exportedCncCutPartsCount = 0
        var exportedAluminumBarsCount = 0
        var exportedAluminumCutPiecesCount = 0

        // 0. Optional 500mm CAD Reference Grid
        if (options.includeReferenceGrid) {
            val step = 500f
            var gx = step
            while (gx < roomW) {
                emitLine("CAD_GRID", gx, 0f, gx, roomD)
                gx += step
            }
            var gy = step
            while (gy < roomD) {
                emitLine("CAD_GRID", 0f, gy, roomW, gy)
                gy += step
            }
        }

        // 1. Export Structural Room Walls (Supporting Rectangular, L-Shape, U-Shape & Out-of-Square Angles)
        if (options.includeWalls && LayerType.STRUCTURE !in hiddenLayers) {
            val activeWalls = room.walls.filter { it.isEnabled }
            if (activeWalls.size == 4 || activeWalls.isEmpty()) {
                // Full closed outer and inner wall contours
                emitPolyline(
                    layer = "WALLS",
                    pointsMm = listOf(
                        Pair(-wallThick, -wallThick),
                        Pair(roomW + wallThick, -wallThick),
                        Pair(roomW + wallThick, roomD + wallThick),
                        Pair(-wallThick, roomD + wallThick)
                    ),
                    isClosed = true
                )
                emitPolyline(
                    layer = "WALLS",
                    pointsMm = listOf(
                        Pair(0f, 0f),
                        Pair(roomW, 0f),
                        Pair(roomW, roomD),
                        Pair(0f, roomD)
                    ),
                    isClosed = true
                )
                exportedWallsCount = 4
            } else {
                // Partial active walls (L-Shape or U-Shape) + inner floor boundary
                emitPolyline(
                    layer = "WALLS",
                    pointsMm = listOf(
                        Pair(0f, 0f),
                        Pair(roomW, 0f),
                        Pair(roomW, roomD),
                        Pair(0f, roomD)
                    ),
                    isClosed = true,
                    lineType = "DASHED"
                )
                activeWalls.forEach { wall ->
                    val t = wall.thicknessMm
                    val wallPts = when (wall.side) {
                        WallSide.NORTH -> listOf(
                            Pair(-t, roomD),
                            Pair(wall.lengthMm + t, roomD),
                            Pair(wall.lengthMm + t, roomD + t),
                            Pair(-t, roomD + t)
                        )
                        WallSide.SOUTH -> listOf(
                            Pair(-t, -t),
                            Pair(wall.lengthMm + t, -t),
                            Pair(wall.lengthMm + t, 0f),
                            Pair(-t, 0f)
                        )
                        WallSide.WEST -> listOf(
                            Pair(-t, 0f),
                            Pair(0f, 0f),
                            Pair(0f, wall.lengthMm),
                            Pair(-t, wall.lengthMm)
                        )
                        WallSide.EAST -> listOf(
                            Pair(roomW, 0f),
                            Pair(roomW + t, 0f),
                            Pair(roomW + t, wall.lengthMm),
                            Pair(roomW, wall.lengthMm)
                        )
                    }
                    emitPolyline("WALLS", wallPts, isClosed = true)
                    exportedWallsCount++
                }
            }

            // Annotate non-orthogonal corner angle adjustments if present
            room.walls.filter { it.isEnabled && it.cornerAngleDegrees != 90f }.forEach { wall ->
                val cornerPt = when (wall.side) {
                    WallSide.NORTH -> Pair(60f, roomD - 80f)
                    WallSide.SOUTH -> Pair(60f, 60f)
                    WallSide.WEST -> Pair(60f, roomD * 0.5f)
                    WallSide.EAST -> Pair(roomW - 280f, roomD * 0.5f)
                }
                emitText(
                    layer = "WALLS",
                    text = "${wall.side.name} Angle: ${String.format(Locale.US, "%.1f", wall.cornerAngleDegrees)} deg",
                    xMm = cornerPt.first,
                    yMm = cornerPt.second,
                    heightMm = 45f
                )
            }
        }

        // 2. Export Architectural Openings (Doors with 90° Swing Arcs & Windows with Glazing Panes)
        if (options.includeOpenings && LayerType.STRUCTURE !in hiddenLayers) {
            room.walls.filter { it.isEnabled }.forEach { wall ->
                wall.openings.forEach { op ->
                    exportOpeningEntity(
                        wall = wall,
                        opening = op,
                        roomW = roomW,
                        roomD = roomD,
                        wallThick = wallThick,
                        emitLine = ::emitLine,
                        emitArc = ::emitArc,
                        emitText = ::emitText,
                        includeLabels = options.includeLabels
                    )
                    exportedOpeningsCount++
                }
            }
        }

        // 3. Export MEP Utility Points (Plumbing, Gas, 60cm Backsplash Electrical Sockets, Ventilation)
        if (options.includeMepUtilities) {
            room.walls.filter { it.isEnabled }.forEach { wall ->
                wall.utilityPoints.forEach { utilPt ->
                    exportMepUtilityPoint(
                        wall = wall,
                        point = utilPt,
                        roomW = roomW,
                        roomD = roomD,
                        wallThick = wallThick,
                        emitCircle = ::emitCircle,
                        emitLine = ::emitLine,
                        emitText = ::emitText,
                        includeLabels = options.includeLabels
                    )
                    exportedMepPointsCount++
                }
            }
        }

        // 4. Export Kitchen Cabinets, Islands, and Appliances
        val visibleElements = project.elements.filter { it.isVisible && it.layer !in hiddenLayers }
        visibleElements.forEach { elem ->
            val isAppliance = elem.type.category == ElementCategory.APPLIANCE
            if ((isAppliance && options.includeAppliances) || (!isAppliance && options.includeCabinets)) {
                exportFloorPlanElement(
                    elem = elem,
                    includeLabels = options.includeLabels,
                    unitScale = unitScale,
                    unitSuffix = unitSuffix,
                    emitPolyline = ::emitPolyline,
                    emitLine = ::emitLine,
                    emitCircle = ::emitCircle,
                    emitText = ::emitText
                )
                if (isAppliance) {
                    exportedAppliancesCount++
                } else {
                    exportedCabinetsCount++
                }
            }
        }

        // 5. Export Ergonomic Work Triangle & Door Swing Clearance Vectors
        if (options.includeWorkTriangle) {
            val report = validationReport ?: ErgonomicValidationEngine.validateProject(project)
            report.workTriangle?.let { tri ->
                val layer = "WORK_TRIANGLE"
                val sx = tri.sinkPositionMm.x
                val sy = tri.sinkPositionMm.z
                val stx = tri.stovePositionMm.x
                val sty = tri.stovePositionMm.z

                emitLine(layer, sx, sy, stx, sty, lineType = "DASHED")
                emitCircle(layer, sx, sy, 45f)
                emitCircle(layer, stx, sty, 45f)

                if (options.includeLabels) {
                    val distVal = (tri.sinkToStoveCenterMm * unitScale).roundToInt()
                    emitText(
                        layer = layer,
                        text = "Sink-Stove: $distVal $unitSuffix",
                        xMm = (sx + stx) * 0.5f - 120f,
                        yMm = (sy + sty) * 0.5f + 40f,
                        heightMm = 48f
                    )
                }

                tri.fridgePositionMm?.let { fridgePos ->
                    val fx = fridgePos.x
                    val fy = fridgePos.z
                    emitLine(layer, sx, sy, fx, fy, lineType = "DASHED")
                    emitLine(layer, stx, sty, fx, fy, lineType = "DASHED")
                    emitCircle(layer, fx, fy, 45f)
                }
            }

            report.doorClearanceZones.forEach { zone ->
                val depthMm = zone.requiredDoorSwingMm.coerceAtLeast(300f)
                val x1 = zone.frontOriginMm.x
                val y1 = zone.frontOriginMm.z
                val x2 = x1 + zone.frontNormal.x * depthMm
                val y2 = y1 + zone.frontNormal.z * depthMm
                emitLine("WORK_TRIANGLE", x1, y1, x2, y2, lineType = "DASHED")
            }
        }

        // 6. Export Architectural Linear Dimensions
        if (options.includeDimensions) {
            exportRoomDimensions(
                roomW = roomW,
                roomD = roomD,
                wallThick = wallThick,
                unitScale = unitScale,
                unitSuffix = unitSuffix,
                emitLine = ::emitLine,
                emitText = ::emitText
            )
            exportedDimensionsCount += 2

            // Individual cabinet clearance / span dimensions along North & West walls
            visibleElements.forEach { elem ->
                val aabb = elem.getAABB()
                val wVal = (elem.dimensions.x * unitScale).roundToInt()
                val dVal = (elem.dimensions.z * unitScale).roundToInt()
                emitText(
                    layer = "DIMENSIONS",
                    text = "${wVal}x${dVal} $unitSuffix",
                    xMm = aabb.min.x + 25f,
                    yMm = aabb.min.z + 25f,
                    heightMm = 38f
                )
                exportedDimensionsCount++
            }
        }

        // 7. Export Architectural Title Block & Project Metadata
        if (options.includeLabels && options.includeTitleBlock) {
            val titleTopY = -wallThick - 420f
            val titleBottomY = titleTopY - 380f
            val titleWidth = max(roomW, 3200f)

            emitPolyline(
                layer = "TEXT_LABELS",
                pointsMm = listOf(
                    Pair(0f, titleBottomY),
                    Pair(titleWidth, titleBottomY),
                    Pair(titleWidth, titleTopY),
                    Pair(0f, titleTopY)
                ),
                isClosed = true
            )
            emitLine("TEXT_LABELS", 0f, titleTopY - 140f, titleWidth, titleTopY - 140f)

            emitText(
                layer = "TEXT_LABELS",
                text = "${project.name} - 2D CAD FLOORPLAN & CNC CAM PACKAGE",
                xMm = 60f,
                yMm = titleTopY - 105f,
                heightMm = 95f
            )
            val areaSqM = String.format(Locale.US, "%.2f", (roomW * roomD) / 1_000_000f)
            val roomWDisplay = (roomW * unitScale).roundToInt()
            val roomDDisplay = (roomD * unitScale).roundToInt()
            emitText(
                layer = "TEXT_LABELS",
                text = "Room: $roomWDisplay x $roomDDisplay $unitSuffix ($areaSqM m2) | Layout: ${room.layoutShape.name} | Units: ${visibleElements.size}",
                xMm = 60f,
                yMm = titleTopY - 235f,
                heightMm = 58f
            )
            emitText(
                layer = "TEXT_LABELS",
                text = "Scale 1:1 (All dimensions in $unitSuffix) - Workshop: $workshopName - Exported Offline",
                xMm = 60f,
                yMm = titleTopY - 330f,
                heightMm = 52f
            )
        } else if (options.includeLabels) {
            emitText(
                layer = "TEXT_LABELS",
                text = "${project.name} - 2D CAD FLOORPLAN",
                xMm = 0f,
                yMm = -wallThick - 300f,
                heightMm = 120f
            )
            emitText(
                layer = "TEXT_LABELS",
                text = "Scale 1:1 (All dimensions in $unitSuffix) - Exported Offline",
                xMm = 0f,
                yMm = -wallThick - 450f,
                heightMm = 70f
            )
        }

        // 8. Export CNC Cabinet Cut Lists (2D Nested Melamine Sheets + Panel Contours + Router Grooves + 1D Aluminum Bars)
        if (options.includeCabinetCutList) {
            val parametricCabinets = explicitCabinets
                ?: extractParametricCabinetsFromProject(project, materialPreset, hiddenLayers)

            val allCutParts: List<CutPart> = parametricCabinets.flatMap { cab ->
                cab.generateCarcassCutParts()
            }

            val cncZoneOriginX = roomW + wallThick + 1500f
            var currentCncCursorY = roomD

            // CNC Header Banner
            emitText(
                layer = "CNC_PART_LABELS",
                text = "CNC MANUFACTURING CUT LISTS & 2D NESTING SHEETS (1:1 SCALE)",
                xMm = cncZoneOriginX,
                yMm = currentCncCursorY + 260f,
                heightMm = 110f
            )
            emitText(
                layer = "CNC_PART_LABELS",
                text = "Cabinets: ${parametricCabinets.size} | Total Parts: ${allCutParts.sumOf { it.quantity }} | Board Thickness: ${materialPreset.melamineThicknessMm}mm | Back Groove: ${materialPreset.backGrooveInsetMm}mm",
                xMm = cncZoneOriginX,
                yMm = currentCncCursorY + 120f,
                heightMm = 58f
            )

            // 8A. 2D Nested Melamine Sheets (2440 x 1220 mm) with G-Code Router Contours
            if (options.includeCncNestedSheets && allCutParts.isNotEmpty()) {
                val packedSheets: List<PackedSheet> = NestingEngine.packPanels(allCutParts)
                val sheetsPerRow = 2
                val sheetSpacingX = 360f
                val sheetSpacingY = 480f

                packedSheets.forEachIndexed { idx, sheet ->
                    val col = idx % sheetsPerRow
                    val row = idx / sheetsPerRow
                    val sheetOriginX = cncZoneOriginX + col * (sheet.rawWidthMm + sheetSpacingX)
                    val sheetOriginY = currentCncCursorY - (row + 1) * sheet.rawLengthMm - row * sheetSpacingY

                    // Outer Raw Sheet Border (2440 x 1220 mm)
                    emitPolyline(
                        layer = "CNC_SHEET_BORDER",
                        pointsMm = listOf(
                            Pair(sheetOriginX, sheetOriginY),
                            Pair(sheetOriginX + sheet.rawWidthMm, sheetOriginY),
                            Pair(sheetOriginX + sheet.rawWidthMm, sheetOriginY + sheet.rawLengthMm),
                            Pair(sheetOriginX, sheetOriginY + sheet.rawLengthMm)
                        ),
                        isClosed = true
                    )

                    // 10mm Factory Trim Margin Line
                    val trim = NestingEngine.DEFAULT_SHEET_TRIM_MM
                    emitPolyline(
                        layer = "CNC_SHEET_BORDER",
                        pointsMm = listOf(
                            Pair(sheetOriginX + trim, sheetOriginY + trim),
                            Pair(sheetOriginX + sheet.rawWidthMm - trim, sheetOriginY + trim),
                            Pair(sheetOriginX + sheet.rawWidthMm - trim, sheetOriginY + sheet.rawLengthMm - trim),
                            Pair(sheetOriginX + trim, sheetOriginY + sheet.rawLengthMm - trim)
                        ),
                        isClosed = true,
                        lineType = "DASHED"
                    )

                    // Sheet Title & Utilization Label
                    val utilStr = String.format(Locale.US, "%.1f", sheet.utilizationPercentage)
                    val wasteStr = String.format(Locale.US, "%.1f", sheet.wastePercentage)
                    emitText(
                        layer = "CNC_PART_LABELS",
                        text = "SHEET #${sheet.sheetIndex} [${sheet.material.name}] ${(sheet.rawWidthMm * unitScale).roundToInt()}x${(sheet.rawLengthMm * unitScale).roundToInt()} $unitSuffix | Used: $utilStr% | Waste: $wasteStr%",
                        xMm = sheetOriginX,
                        yMm = sheetOriginY + sheet.rawLengthMm + 45f,
                        heightMm = 48f
                    )
                    exportedCncSheetsCount++

                    // Placed Panels inside the Sheet
                    sheet.placements.forEachIndexed { pIdx, placement ->
                        val px = sheetOriginX + placement.x
                        val py = sheetOriginY + placement.y
                        val pw = placement.widthMm
                        val pl = placement.lengthMm

                        // Outer CNC Router Cut Contour
                        emitPolyline(
                            layer = "CNC_PANEL_CUT",
                            pointsMm = listOf(
                                Pair(px, py),
                                Pair(px + pw, py),
                                Pair(px + pw, py + pl),
                                Pair(px, py + pl)
                            ),
                            isClosed = true
                        )
                        exportedCncCutPartsCount++

                        // Optional CNC Router Grooves (مجرى الريشة للظهر) & 35mm Hinge Boring Circles
                        if (options.includeRouterGroovesAndDrilling) {
                            val grooveInset = materialPreset.backGrooveInsetMm.coerceAtLeast(8f)
                            val grooveWidth = materialPreset.backPanelThicknessMm.coerceIn(4f, 10f)
                            when (placement.category) {
                                CutPartCategory.MELAMINE_SIDE,
                                CutPartCategory.MELAMINE_BOTTOM_AND_SHELF -> {
                                    if (materialPreset.hasBackPanel && pw > 80f && pl > 80f) {
                                        // Back-panel router dado groove along rear edge
                                        val gy1 = py + pl - grooveInset - grooveWidth
                                        val gy2 = py + pl - grooveInset
                                        emitPolyline(
                                            layer = "CNC_ROUTER_GROOVE",
                                            pointsMm = listOf(
                                                Pair(px, gy1),
                                                Pair(px + pw, gy1),
                                                Pair(px + pw, gy2),
                                                Pair(px, gy2)
                                            ),
                                            isClosed = true,
                                            lineType = "DASHED"
                                        )
                                    }
                                    // System 32mm shelf-pin drilling holes on side panels
                                    if (placement.category == CutPartCategory.MELAMINE_SIDE && pw > 250f && pl > 250f) {
                                        val midY = py + pl * 0.5f
                                        emitCircle("CNC_ROUTER_GROOVE", px + 37f, midY - 32f, 2.5f)
                                        emitCircle("CNC_ROUTER_GROOVE", px + 37f, midY, 2.5f)
                                        emitCircle("CNC_ROUTER_GROOVE", px + 37f, midY + 32f, 2.5f)
                                        emitCircle("CNC_ROUTER_GROOVE", px + pw - 37f, midY - 32f, 2.5f)
                                        emitCircle("CNC_ROUTER_GROOVE", px + pw - 37f, midY, 2.5f)
                                        emitCircle("CNC_ROUTER_GROOVE", px + pw - 37f, midY + 32f, 2.5f)
                                    }
                                }
                                CutPartCategory.FACADE_DOOR -> {
                                    // Standard 35mm Cup Hinge Boring Circles (100mm from top/bottom, 22.5mm inset)
                                    if (pw >= 250f && pl >= 280f) {
                                        val hingeX = px + 22.5f
                                        emitCircle("CNC_ROUTER_GROOVE", hingeX, py + 100f, 17.5f)
                                        emitCircle("CNC_ROUTER_GROOVE", hingeX, py + pl - 100f, 17.5f)
                                    }
                                }
                                else -> {}
                            }
                        }

                        // Edge-Banding Indicator Line on front edge (CNC_EDGE_BANDING)
                        if (placement.category != CutPartCategory.CABINET_BACK_PANEL && pw > 60f && pl > 40f) {
                            emitLine(
                                layer = "CNC_EDGE_BANDING",
                                x1Mm = px + 12f,
                                y1Mm = py + 6f,
                                x2Mm = px + pw - 12f,
                                y2Mm = py + 6f
                            )
                        }

                        // Grain Direction Arrow & Part Dimension Label
                        if (pw >= 90f && pl >= 55f) {
                            val cx = px + pw * 0.5f
                            val cy = py + pl * 0.5f
                            val arrowHalf = min(pw, pl) * 0.18f
                            if (!placement.isRotated) {
                                emitLine("CNC_PART_LABELS", cx - arrowHalf, cy - 12f, cx + arrowHalf, cy - 12f)
                            } else {
                                emitLine("CNC_PART_LABELS", cx, cy - arrowHalf, cx, cy + arrowHalf)
                            }

                            val wDisp = (pw * unitScale).roundToInt()
                            val lDisp = (pl * unitScale).roundToInt()
                            val textH = min(28f, min(pw, pl) * 0.16f).coerceAtLeast(14f)
                            emitText(
                                layer = "CNC_PART_LABELS",
                                text = "P${pIdx + 1}: ${placement.partName.take(24)} (${wDisp}x${lDisp}$unitSuffix)",
                                xMm = px + 14f,
                                yMm = cy + 6f,
                                heightMm = textH
                            )
                        }
                    }
                }

                val totalSheetRows = (packedSheets.size + sheetsPerRow - 1) / sheetsPerRow
                currentCncCursorY -= totalSheetRows * (NestingEngine.DEFAULT_SHEET_LENGTH_MM + sheetSpacingY) + 250f
            }

            // 8B. Export 1D Nested Aluminum Profile Bars (قضبان الألمنيوم القياسية 6000 مم)
            if (options.includeAluminumBarCutList) {
                val aluCutInputs = AluminumBarNestingOptimizer.extractCabinetryCutInputs(parametricCabinets)
                val aluResult: AluminumNestingResult = AluminumBarNestingOptimizer.optimizeCutList(
                    cutInputs = aluCutInputs,
                    config = AluminumNestingConfig(
                        primaryBarLengthMm = options.standardAluminumBarLengthMm
                    )
                )

                emitText(
                    layer = "CNC_PART_LABELS",
                    text = "1D ALUMINUM EXTRUSION BAR CUT LIST (Bar Stock: ${(options.standardAluminumBarLengthMm * unitScale).roundToInt()} $unitSuffix | Bars: ${aluResult.totalBarsCount} | Waste: ${String.format(Locale.US, "%.1f", aluResult.overallWastePercentage)}%)",
                    xMm = cncZoneOriginX,
                    yMm = currentCncCursorY,
                    heightMm = 75f
                )
                currentCncCursorY -= 160f

                val barVisualHeightMm = 90f
                val barRowGapMm = 170f

                aluResult.bars.forEach { bar ->
                    val barY = currentCncCursorY
                    // Full Standard Bar Stock Outline
                    emitPolyline(
                        layer = "CNC_ALU_BARS",
                        pointsMm = listOf(
                            Pair(cncZoneOriginX, barY),
                            Pair(cncZoneOriginX + bar.stockLengthMm, barY),
                            Pair(cncZoneOriginX + bar.stockLengthMm, barY + barVisualHeightMm),
                            Pair(cncZoneOriginX, barY + barVisualHeightMm)
                        ),
                        isClosed = true
                    )
                    exportedAluminumBarsCount++

                    emitText(
                        layer = "CNC_PART_LABELS",
                        text = "ALU BAR #${bar.barIndex} [${bar.profileTypeCode}] Stock=${(bar.stockLengthMm * unitScale).roundToInt()}$unitSuffix | Used=${(bar.netCutLengthMm * unitScale).roundToInt()}$unitSuffix | Rem=${(bar.remnantOffcutMm * unitScale).roundToInt()}$unitSuffix (${String.format(Locale.US, "%.1f", bar.wastePercentage)}% waste)",
                        xMm = cncZoneOriginX,
                        yMm = barY + barVisualHeightMm + 18f,
                        heightMm = 36f
                    )

                    // Individual Cut Pieces along the Aluminum Bar
                    bar.cutPieces.forEach { piece ->
                        val px1 = cncZoneOriginX + piece.startOffsetMm
                        val px2 = cncZoneOriginX + piece.endOffsetMm
                        emitPolyline(
                            layer = "CNC_PANEL_CUT",
                            pointsMm = listOf(
                                Pair(px1, barY + 6f),
                                Pair(px2, barY + 6f),
                                Pair(px2, barY + barVisualHeightMm - 6f),
                                Pair(px1, barY + barVisualHeightMm - 6f)
                            ),
                            isClosed = true
                        )
                        exportedAluminumCutPiecesCount++

                        val pieceLenDisp = (piece.lengthMm * unitScale).roundToInt()
                        if (piece.lengthMm >= 180f) {
                            emitText(
                                layer = "CNC_PART_LABELS",
                                text = "#${piece.pieceNumberOnBar} ${pieceLenDisp}$unitSuffix",
                                xMm = px1 + 12f,
                                yMm = barY + 32f,
                                heightMm = 26f
                            )
                        }
                    }

                    currentCncCursorY -= barRowGapMm
                }

                currentCncCursorY -= 160f
            }

            // 8C. Tabular Cabinet Cut-List Schedule inside the DXF Drawing
            if (allCutParts.isNotEmpty()) {
                val scheduleWidthMm = 3200f
                val rowHeightMm = 70f
                val headerY = currentCncCursorY

                emitText(
                    layer = "CNC_PART_LABELS",
                    text = "CABINET PANEL CUT-LIST SCHEDULE (CNC BOM TABLE)",
                    xMm = cncZoneOriginX,
                    yMm = headerY + 30f,
                    heightMm = 68f
                )

                val tableTopY = headerY
                val displayedParts = allCutParts.take(40)
                val tableBottomY = tableTopY - (displayedParts.size + 1) * rowHeightMm

                emitPolyline(
                    layer = "CNC_SHEET_BORDER",
                    pointsMm = listOf(
                        Pair(cncZoneOriginX, tableBottomY),
                        Pair(cncZoneOriginX + scheduleWidthMm, tableBottomY),
                        Pair(cncZoneOriginX + scheduleWidthMm, tableTopY),
                        Pair(cncZoneOriginX, tableTopY)
                    ),
                    isClosed = true
                )

                emitLine("CNC_SHEET_BORDER", cncZoneOriginX, tableTopY - rowHeightMm, cncZoneOriginX + scheduleWidthMm, tableTopY - rowHeightMm)
                emitText(
                    layer = "CNC_PART_LABELS",
                    text = "NO | CABINET / PART NAME | CATEGORY | MATERIAL | WIDTH x LENGTH x THICK ($unitSuffix) | QTY | EDGE BANDING",
                    xMm = cncZoneOriginX + 25f,
                    yMm = tableTopY - rowHeightMm + 20f,
                    heightMm = 32f
                )

                displayedParts.forEachIndexed { idx, part ->
                    val rowTop = tableTopY - (idx + 1) * rowHeightMm
                    val rowBottom = rowTop - rowHeightMm
                    emitLine("CNC_SHEET_BORDER", cncZoneOriginX, rowBottom, cncZoneOriginX + scheduleWidthMm, rowBottom)

                    val wDisp = (part.widthMm * unitScale).roundToInt()
                    val lDisp = (part.lengthMm * unitScale).roundToInt()
                    val tDisp = String.format(Locale.US, "%.1f", part.thicknessMm * unitScale)
                    emitText(
                        layer = "CNC_PART_LABELS",
                        text = "${idx + 1} | ${part.cabinetName.ifBlank { "Cabinet" }} - ${part.partName} | ${part.category.name} | ${part.material.name} | ${wDisp} x ${lDisp} x $tDisp $unitSuffix | Qty: ${part.quantity} | Edges: ${part.edgeBandingSides}",
                        xMm = cncZoneOriginX + 25f,
                        yMm = rowBottom + 20f,
                        heightMm = 28f
                    )
                }
            }
        }

        // Assemble Complete DXF Document
        val fullDxf = StringBuilder(entitiesBuilder.length + 2048)

        // 1. HEADER SECTION
        fullDxf.append("0\nSECTION\n2\nHEADER\n")
        fullDxf.append("9\n\$ACADVER\n1\nAC1009\n")
        fullDxf.append("9\n\$INSBASE\n10\n0.0\n20\n0.0\n30\n0.0\n")
        fullDxf.append(String.format(Locale.US, "9\n\$EXTMIN\n10\n%.2f\n20\n%.2f\n30\n0.0\n", minX, minY))
        fullDxf.append(String.format(Locale.US, "9\n\$EXTMAX\n10\n%.2f\n20\n%.2f\n30\n0.0\n", maxX, maxY))
        fullDxf.append(String.format(Locale.US, "9\n\$LIMMIN\n10\n%.2f\n20\n%.2f\n", minX, minY))
        fullDxf.append(String.format(Locale.US, "9\n\$LIMMAX\n10\n%.2f\n20\n%.2f\n", maxX, maxY))
        fullDxf.append("9\n\$INSUNITS\n70\n$insUnitsCode\n")
        fullDxf.append("9\n\$MEASUREMENT\n70\n1\n")
        fullDxf.append("9\n\$LUNITS\n70\n2\n")
        fullDxf.append("9\n\$LUPREC\n70\n2\n")
        fullDxf.append("0\nENDSEC\n")

        // 2. TABLES SECTION (LTYPE + LAYER)
        fullDxf.append("0\nSECTION\n2\nTABLES\n")

        // 2A. Line Types Table (CONTINUOUS, DASHED, CENTER)
        fullDxf.append("0\nTABLE\n2\nLTYPE\n70\n3\n")
        fullDxf.append("0\nLTYPE\n2\nCONTINUOUS\n70\n0\n3\nSolid line\n72\n65\n73\n0\n40\n0.0\n")
        fullDxf.append("0\nLTYPE\n2\nDASHED\n70\n0\n3\nDashed __ __ __\n72\n65\n73\n2\n40\n150.0\n49\n100.0\n49\n-50.0\n")
        fullDxf.append("0\nLTYPE\n2\nCENTER\n70\n0\n3\nCenter ____ _ ____\n72\n65\n73\n4\n40\n200.0\n49\n120.0\n49\n-30.0\n49\n20.0\n49\n-30.0\n")
        fullDxf.append("0\nENDTAB\n")

        // 2B. Layers Table
        fullDxf.append("0\nTABLE\n2\nLAYER\n70\n${STANDARD_LAYERS.size}\n")
        STANDARD_LAYERS.forEach { layerDef ->
            appendLayer(fullDxf, layerDef.name, layerDef.aciColor, layerDef.lineType)
        }
        fullDxf.append("0\nENDTAB\n")
        fullDxf.append("0\nENDSEC\n")

        // 3. BLOCKS SECTION
        fullDxf.append("0\nSECTION\n2\nBLOCKS\n0\nENDSEC\n")

        // 4. ENTITIES SECTION
        fullDxf.append("0\nSECTION\n2\nENTITIES\n")
        fullDxf.append(entitiesBuilder)
        fullDxf.append("0\nENDSEC\n")
        fullDxf.append("0\nEOF\n")

        return DxfSerializationResult(
            dxfContent = fullDxf.toString(),
            totalEntitiesCount = totalEntities,
            layerEntityCounts = layerCounts.toMap(),
            boundsMinX = minX,
            boundsMinY = minY,
            boundsMaxX = maxX,
            boundsMaxY = maxY,
            exportedWallsCount = exportedWallsCount,
            exportedOpeningsCount = exportedOpeningsCount,
            exportedCabinetsCount = exportedCabinetsCount,
            exportedAppliancesCount = exportedAppliancesCount,
            exportedMepPointsCount = exportedMepPointsCount,
            exportedDimensionsCount = exportedDimensionsCount,
            unitCode = insUnitsCode,
            exportedCncSheetsCount = exportedCncSheetsCount,
            exportedCncCutPartsCount = exportedCncCutPartsCount,
            exportedAluminumBarsCount = exportedAluminumBarsCount,
            exportedAluminumCutPiecesCount = exportedAluminumCutPiecesCount
        )
    }

    /**
     * Saves serialized 2D floor plan and CNC cut-list DXF content directly into a target [File] and returns the metadata result.
     */
    fun exportToFile(
        file: File,
        project: KitchenProject,
        options: DxfExportOptions = DxfExportOptions(),
        hiddenLayers: Set<LayerType> = emptySet(),
        validationReport: ErgonomicValidationReport? = null,
        workshopName: String = "Kitchen3D Studio Engineering",
        explicitCabinets: List<ParametricCabinet>? = null,
        materialPreset: CustomMaterialOverridePreset = CustomMaterialOverridePreset.DEFAULT_PRESETS.first()
    ): DxfSerializationResult {
        val result = serializeFloorPlanWithMetadata(
            project = project,
            options = options,
            hiddenLayers = hiddenLayers,
            validationReport = validationReport,
            workshopName = workshopName,
            explicitCabinets = explicitCabinets,
            materialPreset = materialPreset
        )
        FileOutputStream(file).use { fos ->
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                writer.write(result.dxfContent)
            }
        }
        return result
    }

    private fun appendLayer(
        sb: StringBuilder,
        layerName: String,
        colorNumber: Int,
        lineType: String = "CONTINUOUS"
    ) {
        sb.append("0\nLAYER\n2\n$layerName\n70\n0\n62\n$colorNumber\n6\n$lineType\n")
    }

    private fun exportFloorPlanElement(
        elem: KitchenElement,
        includeLabels: Boolean,
        unitScale: Float,
        unitSuffix: String,
        emitPolyline: (String, List<Pair<Float, Float>>, Boolean, String?) -> Unit,
        emitLine: (String, Float, Float, Float, Float, String?) -> Unit,
        emitCircle: (String, Float, Float, Float) -> Unit,
        emitText: (String, String, Float, Float, Float, Float) -> Unit
    ) {
        val corners = elem.getFloorCorners()
        if (corners.size < 4) return

        val layer = when (elem.type.category) {
            ElementCategory.WALL_CABINET, ElementCategory.BRIDGE_CABINET -> "CABINETS_WALL"
            ElementCategory.TALL_UNIT -> "CABINETS_TALL"
            ElementCategory.APPLIANCE -> "APPLIANCES"
            else -> "CABINETS_BASE"
        }

        val lineType = if (layer == "CABINETS_WALL") "DASHED" else null
        val pts = corners.map { Pair(it.x, it.z) }
        emitPolyline(layer, pts, true, lineType)

        // Front edge direction tick mark (pts[2]..pts[3] is the front face)
        val frontMidX = (pts[2].first + pts[3].first) * 0.5f
        val frontMidY = (pts[2].second + pts[3].second) * 0.5f
        val centerX = (pts[0].first + pts[2].first) * 0.5f
        val centerY = (pts[0].second + pts[2].second) * 0.5f
        val dirX = frontMidX - centerX
        val dirY = frontMidY - centerY
        val dirLen = kotlin.math.hypot(dirX, dirY).coerceAtLeast(1f)
        val normX = dirX / dirLen
        val normY = dirY / dirLen
        emitLine(layer, frontMidX, frontMidY, frontMidX + normX * 45f, frontMidY + normY * 45f, null)

        when (elem.type.category) {
            ElementCategory.BASE_CABINET, ElementCategory.ISLAND_AND_SURFACE -> {
                // Standard architectural diagonal cross for base unit
                emitLine(layer, pts[0].first, pts[0].second, pts[2].first, pts[2].second, null)

                // Detailed CAD symbols for sink units
                if (elem.type == KitchenElementType.BASE_SINK_UNIT || elem.type == KitchenElementType.BASE_SINK_VENTED_100) {
                    val bowlHalfW = (elem.dimensions.x * 0.32f).coerceAtLeast(120f)
                    val bowlHalfD = (elem.dimensions.z * 0.28f).coerceAtLeast(100f)
                    val bowlPts = listOf(
                        Pair(centerX - bowlHalfW, centerY - bowlHalfD),
                        Pair(centerX + bowlHalfW, centerY - bowlHalfD),
                        Pair(centerX + bowlHalfW, centerY + bowlHalfD),
                        Pair(centerX - bowlHalfW, centerY + bowlHalfD)
                    )
                    emitPolyline(layer, bowlPts, true, null)
                    emitCircle(layer, centerX, centerY, 35f) // Sink drain hole
                }
            }
            ElementCategory.TALL_UNIT -> {
                // Full-height tall unit double X cross
                emitLine(layer, pts[0].first, pts[0].second, pts[2].first, pts[2].second, null)
                emitLine(layer, pts[1].first, pts[1].second, pts[3].first, pts[3].second, null)
            }
            ElementCategory.APPLIANCE -> {
                // Cooktop burner rings
                if (elem.type == KitchenElementType.COOKTOP_INDUCTION || elem.type == KitchenElementType.COOKTOP_GAS_90) {
                    val dx = elem.dimensions.x * 0.22f
                    val dz = elem.dimensions.z * 0.22f
                    emitCircle(layer, centerX - dx, centerY - dz, 65f)
                    emitCircle(layer, centerX + dx, centerY - dz, 65f)
                    emitCircle(layer, centerX - dx, centerY + dz, 55f)
                    emitCircle(layer, centerX + dx, centerY + dz, 55f)
                    if (elem.type == KitchenElementType.COOKTOP_GAS_90) {
                        emitCircle(layer, centerX, centerY, 80f) // Center wok burner
                    }
                } else {
                    emitCircle(layer, centerX, centerY, min(elem.dimensions.x, elem.dimensions.z) * 0.18f)
                }
            }
            else -> {}
        }

        if (includeLabels) {
            val dimLabel = (elem.dimensions.x * unitScale).roundToInt()
            emitText(
                "TEXT_LABELS",
                "${elem.name} (${dimLabel}$unitSuffix)",
                centerX - (elem.dimensions.x * 0.35f),
                centerY,
                50f,
                0f
            )
        }
    }

    private fun exportOpeningEntity(
        wall: Wall,
        opening: WallOpening,
        roomW: Float,
        roomD: Float,
        wallThick: Float,
        emitLine: (String, Float, Float, Float, Float, String?) -> Unit,
        emitArc: (String, Float, Float, Float, Float, Float) -> Unit,
        emitText: (String, String, Float, Float, Float, Float) -> Unit,
        includeLabels: Boolean
    ) {
        val layer = "OPENINGS"
        val offsetMm = opening.offsetMm
        val widthMm = opening.widthMm
        val type = opening.type

        when (wall.side) {
            WallSide.NORTH -> { // Top wall along X at Y = roomD
                val x1 = offsetMm
                val x2 = offsetMm + widthMm
                val y1 = roomD
                val y2 = roomD + wallThick

                emitLine(layer, x1, y1, x1, y2, null)
                emitLine(layer, x2, y1, x2, y2, null)
                if (type == OpeningType.DOOR) {
                    emitLine(layer, x1, y1, x1, y1 - widthMm, null)
                    emitArc(layer, x1, y1, widthMm, 270f, 360f)
                } else {
                    val midY1 = y1 + wallThick * 0.35f
                    val midY2 = y1 + wallThick * 0.65f
                    emitLine(layer, x1, midY1, x2, midY1, null)
                    emitLine(layer, x1, midY2, x2, midY2, null)
                }
                if (includeLabels) {
                    emitText("TEXT_LABELS", "${type.name} ${widthMm.toInt()}mm", x1 + 20f, y2 + 30f, 45f, 0f)
                }
            }
            WallSide.SOUTH -> { // Bottom wall along X at Y = 0
                val x1 = offsetMm
                val x2 = offsetMm + widthMm
                val y1 = 0f
                val y2 = -wallThick

                emitLine(layer, x1, y1, x1, y2, null)
                emitLine(layer, x2, y1, x2, y2, null)
                if (type == OpeningType.DOOR) {
                    emitLine(layer, x1, y1, x1, y1 + widthMm, null)
                    emitArc(layer, x1, y1, widthMm, 0f, 90f)
                } else {
                    val midY1 = y1 - wallThick * 0.35f
                    val midY2 = y1 - wallThick * 0.65f
                    emitLine(layer, x1, midY1, x2, midY1, null)
                    emitLine(layer, x1, midY2, x2, midY2, null)
                }
                if (includeLabels) {
                    emitText("TEXT_LABELS", "${type.name} ${widthMm.toInt()}mm", x1 + 20f, y2 - 70f, 45f, 0f)
                }
            }
            WallSide.WEST -> { // Left wall along Y at X = 0
                val y1 = offsetMm
                val y2 = offsetMm + widthMm
                val x1 = 0f
                val x2 = -wallThick

                emitLine(layer, x1, y1, x2, y1, null)
                emitLine(layer, x1, y2, x2, y2, null)
                if (type == OpeningType.DOOR) {
                    emitLine(layer, x1, y1, x1 + widthMm, y1, null)
                    emitArc(layer, x1, y1, widthMm, 0f, 90f)
                } else {
                    val midX1 = x1 - wallThick * 0.35f
                    val midX2 = x1 - wallThick * 0.65f
                    emitLine(layer, midX1, y1, midX1, y2, null)
                    emitLine(layer, midX2, y1, midX2, y2, null)
                }
                if (includeLabels) {
                    emitText("TEXT_LABELS", "${type.name} ${widthMm.toInt()}mm", x2 - 90f, y1 + 20f, 45f, 90f)
                }
            }
            WallSide.EAST -> { // Right wall along Y at X = roomW
                val y1 = offsetMm
                val y2 = offsetMm + widthMm
                val x1 = roomW
                val x2 = roomW + wallThick

                emitLine(layer, x1, y1, x2, y1, null)
                emitLine(layer, x1, y2, x2, y2, null)
                if (type == OpeningType.DOOR) {
                    emitLine(layer, x1, y1, x1 - widthMm, y1, null)
                    emitArc(layer, x1, y1, widthMm, 90f, 180f)
                } else {
                    val midX1 = x1 + wallThick * 0.35f
                    val midX2 = x1 + wallThick * 0.65f
                    emitLine(layer, midX1, y1, midX1, y2, null)
                    emitLine(layer, midX2, y1, midX2, y2, null)
                }
                if (includeLabels) {
                    emitText("TEXT_LABELS", "${type.name} ${widthMm.toInt()}mm", x2 + 40f, y1 + 20f, 45f, 90f)
                }
            }
        }
    }

    private fun exportMepUtilityPoint(
        wall: Wall,
        point: WallUtilityPoint,
        roomW: Float,
        roomD: Float,
        wallThick: Float,
        emitCircle: (String, Float, Float, Float) -> Unit,
        emitLine: (String, Float, Float, Float, Float, String?) -> Unit,
        emitText: (String, String, Float, Float, Float, Float) -> Unit,
        includeLabels: Boolean
    ) {
        val layer = "MEP_UTILITIES"
        val (ux, uy) = when (wall.side) {
            WallSide.NORTH -> Pair(point.offsetFromWallStartMm, roomD + wallThick * 0.5f)
            WallSide.SOUTH -> Pair(point.offsetFromWallStartMm, -wallThick * 0.5f)
            WallSide.WEST -> Pair(-wallThick * 0.5f, point.offsetFromWallStartMm)
            WallSide.EAST -> Pair(roomW + wallThick * 0.5f, point.offsetFromWallStartMm)
        }

        val radius = 40f
        emitCircle(layer, ux, uy, radius)
        emitLine(layer, ux - radius, uy, ux + radius, uy, null)
        emitLine(layer, ux, uy - radius, ux, uy + radius, null)

        if (includeLabels) {
            val tag = "${point.type.codeSymbol} (H:${point.elevationFromFloorMm.toInt()}mm)"
            emitText(layer, tag, ux - 60f, uy + radius + 20f, 38f, 0f)
        }
    }

    private fun exportRoomDimensions(
        roomW: Float,
        roomD: Float,
        wallThick: Float,
        unitScale: Float,
        unitSuffix: String,
        emitLine: (String, Float, Float, Float, Float, String?) -> Unit,
        emitText: (String, String, Float, Float, Float, Float) -> Unit
    ) {
        val layer = "DIMENSIONS"
        val offset = wallThick + 250f
        val tick = 60f

        val wVal = (roomW * unitScale).roundToInt()
        val dVal = (roomD * unitScale).roundToInt()

        // 1. South Width Dimension (Bottom)
        val yDimSouth = -offset
        emitLine(layer, 0f, 0f, 0f, yDimSouth - tick, null)
        emitLine(layer, roomW, 0f, roomW, yDimSouth - tick, null)
        emitLine(layer, 0f, yDimSouth, roomW, yDimSouth, null)
        emitLine(layer, -tick, yDimSouth - tick, tick, yDimSouth + tick, null)
        emitLine(layer, roomW - tick, yDimSouth - tick, roomW + tick, yDimSouth + tick, null)
        emitText(
            layer,
            "$wVal $unitSuffix",
            (roomW * 0.5f) - 150f,
            yDimSouth + 30f,
            80f,
            0f
        )

        // 2. West Depth Dimension (Left)
        val xDimWest = -offset
        emitLine(layer, 0f, 0f, xDimWest - tick, 0f, null)
        emitLine(layer, 0f, roomD, xDimWest - tick, roomD, null)
        emitLine(layer, xDimWest, 0f, xDimWest, roomD, null)
        emitLine(layer, xDimWest - tick, -tick, xDimWest + tick, tick, null)
        emitLine(layer, xDimWest - tick, roomD - tick, xDimWest + tick, roomD + tick, null)
        emitText(
            layer,
            "$dVal $unitSuffix",
            xDimWest - 180f,
            (roomD * 0.5f) - 100f,
            80f,
            90f
        )
    }
}
