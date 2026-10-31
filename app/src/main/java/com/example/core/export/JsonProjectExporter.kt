package com.example.core.export

import com.example.core.cam.AluminumBarNestingOptimizer
import com.example.core.cam.AluminumNestingConfig
import com.example.core.cam.CustomMaterialOverridePreset
import com.example.core.cam.NestingEngine
import com.example.core.cam.ParametricCabinet
import com.example.core.math.Vector3
import com.example.domain.model.ElementCategory
import com.example.domain.model.KitchenElement
import com.example.domain.model.KitchenElementType
import com.example.domain.model.KitchenGroup
import com.example.domain.model.LayerType
import com.example.domain.model.MaterialFinish
import com.example.domain.model.OpeningType
import com.example.domain.model.RoomLayoutShape
import com.example.domain.model.RoomStructure
import com.example.domain.model.UtilityPointType
import com.example.domain.model.Wall
import com.example.domain.model.WallOpening
import com.example.domain.model.WallSide
import com.example.domain.model.WallUtilityPoint
import com.example.domain.repository.BillOfMaterials
import com.example.domain.repository.KitchenProject
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import com.example.core.filament.ArchVizPromptEngine
import java.util.Date
import java.util.Locale

/**
 * Comprehensive JSON Serializer, Exporter & Importer for Kitchen 3D Studio.
 * Supports exporting:
 * 1. Full Project + Room + MEP Utilities + 3D/2D Elements + CAM Cut-Lists + 1D/2D Nesting + BOM
 * 2. Scene Kitchen Elements Only (with 3D coordinates, 2D floor footprints, OBB/AABB, materials & backing)
 * 3. Standard Catalog Element Definitions + Scene Elements
 * 4. CAM Cut-Lists, 1D Aluminum Bar Nesting & 2D Melamine Sheet Nesting
 * 5. Image-to-Realism AI ControlNet & ArchViz Prompt Parameters JSON
 */
object JsonProjectExporter {

    enum class JsonExportMode(val titleAr: String, val subtitleAr: String, val fileSuffix: String) {
        FULL_PROJECT_AND_CAM(
            titleAr = "ملف المشروع والعناصر الكامل (Full Project + Elements + CAM)",
            subtitleAr = "يشمل الغرفة، الجدران، الخدمات MEP، جميع عناصر المطبخ، قوائم القص، وتعشيق الألمنيوم والميلامين",
            fileSuffix = "FULL_PROJECT"
        ),
        ELEMENTS_ONLY(
            titleAr = "عناصر وخزائن التصميم فقط (Scene Elements JSON)",
            subtitleAr = "يستخرج قائمة عناصر المطبخ الموزعة في التصميم مع الإحداثيات 3D/2D والمقاسات والخامات وخيارات الظهر",
            fileSuffix = "ELEMENTS"
        ),
        CATALOG_AND_ELEMENTS(
            titleAr = "كتالوج العناصر القياسية + عناصر التصميم (Catalog & Elements)",
            subtitleAr = "يستخرج مكتبة عناصر المطبخ القياسية بالكامل مع العناصر المستخدمة حالياً في مساحة العمل",
            fileSuffix = "CATALOG_ELEMENTS"
        ),
        CUT_LIST_AND_NESTING(
            titleAr = "قوائم القص والتعشيق الصناعي (CAM Cut-List & Nesting JSON)",
            subtitleAr = "يستخرج تفصيل قطع الخزائن، قضبان الألمنيوم القياسية، وألواح الميلامين المعشقة لماكينات CNC",
            fileSuffix = "CAM_CUTLIST"
        ),
        ARCHVIZ_CONTROLNET_PROMPT(
            titleAr = "معاملات الرندر الواقعي (Image-to-Realism ArchViz JSON)",
            subtitleAr = "يستخرج كود JSON القياسي لمحرك الرندر الفوتوغرافي (positive_prompt, negative_prompt, controlnet_settings)",
            fileSuffix = "ARCHVIZ_RENDER"
        )
    }

    data class JsonExportOptions(
        val exportMode: JsonExportMode = JsonExportMode.FULL_PROJECT_AND_CAM,
        val includeRoomAndMep: Boolean = true,
        val includeSceneElements: Boolean = true,
        val includeElementBoundingBoxes: Boolean = true,
        val includeCatalogDefinitions: Boolean = true,
        val includeParametricCutList: Boolean = true,
        val includeAluminumAndSheetNesting: Boolean = true,
        val includeBillOfMaterials: Boolean = true,
        val useCentimeters: Boolean = true,
        val prettyPrint: Boolean = true,
        val standardAluminumBarLengthMm: Float = 6000f
    )

    data class JsonSerializationResult(
        val jsonContent: String,
        val fileSizeBytes: Int,
        val exportedElementsCount: Int,
        val exportedWallsCount: Int,
        val exportedOpeningsCount: Int,
        val exportedMepPointsCount: Int,
        val exportedCutPartsCount: Int,
        val exportedAluminumBarsCount: Int,
        val exportedMelamineSheetsCount: Int,
        val exportedCatalogTypesCount: Int
    )

    /**
     * Serializes the kitchen project, scene elements, catalog definitions, and CAM manufacturing data into a structured JSON string.
     */
    fun serializeToJsonWithMetadata(
        project: KitchenProject,
        options: JsonExportOptions = JsonExportOptions(),
        cabinets: List<ParametricCabinet> = emptyList(),
        bom: BillOfMaterials? = null,
        materialPreset: CustomMaterialOverridePreset = CustomMaterialOverridePreset.DEFAULT_PRESETS.first(),
        workshopName: String = "ورشة التصميم الهندسي للمطابخ الذكية"
    ): JsonSerializationResult {
        if (options.exportMode == JsonExportMode.ARCHVIZ_CONTROLNET_PROMPT) {
            val archVizPayload = ArchVizPromptEngine.generateRenderPayload(project = project)
            val rootObj = ArchVizPromptEngine.toJsonObject(
                positivePrompt = archVizPayload.positivePrompt,
                negativePrompt = archVizPayload.negativePrompt,
                controlNetSettings = archVizPayload.controlNetSettings
            )
            val formatted = if (options.prettyPrint) rootObj.toString(2) else rootObj.toString()
            val bytes = formatted.toByteArray(StandardCharsets.UTF_8).size
            return JsonSerializationResult(
                jsonContent = formatted,
                fileSizeBytes = bytes,
                exportedElementsCount = project.elements.count { it.isVisible },
                exportedWallsCount = project.room.walls.size,
                exportedOpeningsCount = project.room.walls.sumOf { it.openings.size },
                exportedMepPointsCount = project.room.walls.sumOf { it.utilityPoints.size },
                exportedCutPartsCount = 0,
                exportedAluminumBarsCount = 0,
                exportedMelamineSheetsCount = 0,
                exportedCatalogTypesCount = 0
            )
        }

        val root = JSONObject()
        val timeStampIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

        // 1. Header Metadata
        val metaObj = JSONObject().apply {
            put("schemaVersion", "KITCHEN_3D_STUDIO_JSON_V2")
            put("exportMode", options.exportMode.name)
            put("exportModeTitleAr", options.exportMode.titleAr)
            put("exportedAtIso", timeStampIso)
            put("workshopName", workshopName)
            put("projectId", project.id)
            put("projectName", project.name)
            put("createdAtTimestamp", project.createdAt)
            put("primaryUnit", if (options.useCentimeters) "cm (with mm exact)" else "mm")
        }
        root.put("metadata", metaObj)

        // Include Image-to-Realism ArchViz & ControlNet rendering parameters block
        val archViz = ArchVizPromptEngine.generateRenderPayload(project = project)
        root.put(
            "archviz_render_parameters",
            ArchVizPromptEngine.toJsonObject(
                positivePrompt = archViz.positivePrompt,
                negativePrompt = archViz.negativePrompt,
                controlNetSettings = archViz.controlNetSettings
            )
        )

        // Maintain top-level compatibility fields so standard project parsers can also read root directly
        root.put("id", project.id)
        root.put("name", project.name)
        root.put("createdAt", project.createdAt)

        var exportedWallsCount = 0
        var exportedOpeningsCount = 0
        var exportedMepPointsCount = 0
        var exportedElementsCount = 0
        var exportedCutPartsCount = 0
        var exportedAluminumBarsCount = 0
        var exportedMelamineSheetsCount = 0
        var exportedCatalogTypesCount = 0

        val shouldWriteRoom = when (options.exportMode) {
            JsonExportMode.FULL_PROJECT_AND_CAM -> options.includeRoomAndMep
            JsonExportMode.ELEMENTS_ONLY -> false
            JsonExportMode.CATALOG_AND_ELEMENTS -> false
            JsonExportMode.CUT_LIST_AND_NESTING -> false
            JsonExportMode.ARCHVIZ_CONTROLNET_PROMPT -> false
        }

        val shouldWriteElements = when (options.exportMode) {
            JsonExportMode.FULL_PROJECT_AND_CAM -> options.includeSceneElements
            JsonExportMode.ELEMENTS_ONLY -> true
            JsonExportMode.CATALOG_AND_ELEMENTS -> true
            JsonExportMode.CUT_LIST_AND_NESTING -> options.includeSceneElements
            JsonExportMode.ARCHVIZ_CONTROLNET_PROMPT -> false
        }

        val shouldWriteCatalog = when (options.exportMode) {
            JsonExportMode.FULL_PROJECT_AND_CAM -> options.includeCatalogDefinitions
            JsonExportMode.ELEMENTS_ONLY -> false
            JsonExportMode.CATALOG_AND_ELEMENTS -> true
            JsonExportMode.CUT_LIST_AND_NESTING -> false
            JsonExportMode.ARCHVIZ_CONTROLNET_PROMPT -> false
        }

        val shouldWriteCutList = when (options.exportMode) {
            JsonExportMode.FULL_PROJECT_AND_CAM -> options.includeParametricCutList
            JsonExportMode.ELEMENTS_ONLY -> false
            JsonExportMode.CATALOG_AND_ELEMENTS -> false
            JsonExportMode.CUT_LIST_AND_NESTING -> true
            JsonExportMode.ARCHVIZ_CONTROLNET_PROMPT -> false
        }

        val shouldWriteNesting = when (options.exportMode) {
            JsonExportMode.FULL_PROJECT_AND_CAM -> options.includeAluminumAndSheetNesting
            JsonExportMode.ELEMENTS_ONLY -> false
            JsonExportMode.CATALOG_AND_ELEMENTS -> false
            JsonExportMode.CUT_LIST_AND_NESTING -> true
            JsonExportMode.ARCHVIZ_CONTROLNET_PROMPT -> false
        }

        val shouldWriteBom = when (options.exportMode) {
            JsonExportMode.FULL_PROJECT_AND_CAM -> options.includeBillOfMaterials
            JsonExportMode.ELEMENTS_ONLY -> false
            JsonExportMode.CATALOG_AND_ELEMENTS -> false
            JsonExportMode.CUT_LIST_AND_NESTING -> options.includeBillOfMaterials
            JsonExportMode.ARCHVIZ_CONTROLNET_PROMPT -> false
        }

        // 2. Room Structure, Walls, Openings & MEP Utility Points
        if (shouldWriteRoom) {
            val room = project.room
            val roomObj = JSONObject().apply {
                put("widthMm", round2(room.widthMm))
                put("depthMm", round2(room.depthMm))
                put("heightMm", round2(room.heightMm))
                put("widthCm", round2(room.widthMm / 10f))
                put("depthCm", round2(room.depthMm / 10f))
                put("heightCm", round2(room.heightMm / 10f))
                put("wallThicknessMm", round2(room.wallThicknessMm))
                put("layoutShape", room.layoutShape.name)
                put("layoutShapeTitleAr", room.layoutShape.titleAr)
                put("floorMaterial", serializeMaterialFinish(room.floorMaterial))
                put("wallFinish", serializeMaterialFinish(room.wallFinish))
                put("floorMaterialId", room.floorMaterial.id)
                put("wallFinishId", room.wallFinish.id)
            }

            val wallsArr = JSONArray()
            room.walls.forEach { wall ->
                exportedWallsCount++
                val wallObj = JSONObject().apply {
                    put("id", wall.id)
                    put("side", wall.side.name)
                    put("sideTitleAr", wall.side.title)
                    put("lengthMm", round2(wall.lengthMm))
                    put("lengthCm", round2(wall.lengthCm))
                    put("heightMm", round2(wall.heightMm))
                    put("thicknessMm", round2(wall.thicknessMm))
                    put("cornerAngleDegrees", round2(wall.cornerAngleDegrees))
                    put("isEnabled", wall.isEnabled)
                }

                val openingsArr = JSONArray()
                wall.openings.forEach { op ->
                    exportedOpeningsCount++
                    openingsArr.put(
                        JSONObject().apply {
                            put("id", op.id)
                            put("type", op.type.name)
                            put("offsetMm", round2(op.offsetMm))
                            put("offsetCm", round2(op.offsetMm / 10f))
                            put("widthMm", round2(op.widthMm))
                            put("widthCm", round2(op.widthMm / 10f))
                            put("heightMm", round2(op.heightMm))
                            put("heightCm", round2(op.heightMm / 10f))
                            put("sillHeightMm", round2(op.sillHeightMm))
                            put("sillHeightCm", round2(op.sillHeightMm / 10f))
                        }
                    )
                }
                wallObj.put("openings", openingsArr)

                val utilitiesArr = JSONArray()
                wall.utilityPoints.forEach { util ->
                    exportedMepPointsCount++
                    utilitiesArr.put(
                        JSONObject().apply {
                            put("id", util.id)
                            put("type", util.type.name)
                            put("typeTitleAr", util.type.titleAr)
                            put("codeSymbol", util.type.codeSymbol)
                            put("label", util.label)
                            put("offsetFromWallStartMm", round2(util.offsetFromWallStartMm))
                            put("offsetFromWallStartCm", round2(util.offsetCm))
                            put("elevationFromFloorMm", round2(util.elevationFromFloorMm))
                            put("elevationFromFloorCm", round2(util.elevationCm))
                            put("isIn60cmBacksplashZone", util.isInBacksplash60cmZone)
                        }
                    )
                }
                wallObj.put("utilityPoints", utilitiesArr)
                wallsArr.put(wallObj)
            }
            roomObj.put("walls", wallsArr)
            root.put("room", roomObj)
        }

        // 3. Scene Kitchen Elements (Cabinets, Appliances, Islands, Lighting)
        if (shouldWriteElements) {
            val elementsArr = JSONArray()
            project.elements.forEach { elem ->
                exportedElementsCount++
                elementsArr.put(
                    serializeKitchenElement(
                        elem = elem,
                        includeBoundingBoxes = options.includeElementBoundingBoxes
                    )
                )
            }
            root.put("elements", elementsArr)

            // Element Groups
            val groupsArr = JSONArray()
            project.groups.forEach { grp ->
                val idsArr = JSONArray()
                grp.elementIds.forEach { idsArr.put(it) }
                groupsArr.put(
                    JSONObject().apply {
                        put("id", grp.id)
                        put("name", grp.name)
                        put("elementIds", idsArr)
                    }
                )
            }
            root.put("groups", groupsArr)
        }

        // 4. Standard Catalog Element Definitions & Materials
        if (shouldWriteCatalog) {
            val catalogArr = JSONArray()
            KitchenElementType.values().forEach { type ->
                exportedCatalogTypesCount++
                catalogArr.put(
                    JSONObject().apply {
                        put("type", type.name)
                        put("code", type.code)
                        put("titleAr", type.title)
                        put("category", type.category.name)
                        put("categoryDisplayName", type.category.displayName)
                        put("defaultWidthMm", round2(type.defaultDimensions.x))
                        put("defaultHeightMm", round2(type.defaultDimensions.y))
                        put("defaultDepthMm", round2(type.defaultDimensions.z))
                        put("defaultWidthCm", round2(type.defaultDimensions.x / 10f))
                        put("defaultHeightCm", round2(type.defaultDimensions.y / 10f))
                        put("defaultDepthCm", round2(type.defaultDimensions.z / 10f))
                        put("defaultElevationFromFloorMm", round2(type.defaultElevationY))
                        put("defaultElevationFromFloorCm", round2(type.defaultElevationY / 10f))
                    }
                )
            }
            root.put("catalogDefinitions", catalogArr)

            val materialsArr = JSONArray()
            MaterialFinish.ALL_FINISHES.forEach { mat ->
                materialsArr.put(serializeMaterialFinish(mat))
            }
            root.put("availableMaterialFinishes", materialsArr)
        }

        // 5. Parametric Cabinet Cut-Lists (Melamine Panels, Stretchers, Backing, Router Grooves, Aluminum Profiles)
        val effectiveCabinets = cabinets.ifEmpty {
            deriveParametricCabinetsFromElements(project.elements, materialPreset)
        }

        if (shouldWriteCutList) {
            val presetObj = JSONObject().apply {
                put("id", materialPreset.id)
                put("nameAr", materialPreset.nameAr)
                put("melamineThicknessMm", round2(materialPreset.melamineThicknessMm))
                put("aluminumProfileThicknessMm", round2(materialPreset.aluminumProfileThicknessMm))
                put("aluminumProfileSectionSizeMm", round2(materialPreset.aluminumProfileSectionSizeMm))
                put("aluminumWastePercentage", round2(materialPreset.aluminumWastePercentage))
                put("topStretcherWidthMm", round2(materialPreset.topStretcherWidthMm))
                put("hasBackPanel", materialPreset.hasBackPanel)
                put("backPanelMaterialName", materialPreset.backPanelMaterialName)
                put("backPanelThicknessMm", round2(materialPreset.backPanelThicknessMm))
                put("backGrooveInsetMm", round2(materialPreset.backGrooveInsetMm))
                put("customWidthDeductionMm", round2(materialPreset.customWidthDeductionMm))
            }
            root.put("activeMaterialPreset", presetObj)

            val cabinetsCutArr = JSONArray()
            effectiveCabinets.forEach { cab ->
                val parts = cab.generateCarcassCutParts()
                val aluSummary = cab.calculateAluminumSummary()
                val partsArr = JSONArray()
                parts.forEach { part ->
                    exportedCutPartsCount += part.quantity
                    partsArr.put(
                        JSONObject().apply {
                            put("partName", part.partName)
                            put("category", part.category.name)
                            put("categoryTitleAr", part.category.titleAr)
                            put("widthMm", round2(part.widthMm))
                            put("lengthMm", round2(part.lengthMm))
                            put("widthCm", round2(part.widthMm / 10f))
                            put("lengthCm", round2(part.lengthMm / 10f))
                            put("thicknessMm", round2(part.thicknessMm))
                            put("quantity", part.quantity)
                            put("material", part.material.name)
                            put("materialDisplayName", part.material.displayName)
                            put("edgeBandingSides", part.edgeBandingSides)
                            put("edgeBandingMeters", round2(part.edgeBandingMeters))
                            put("areaSqM", round2(part.areaSqM))
                            put("formulaNoteAr", part.formulaNoteAr)
                        }
                    )
                }

                val aluCutsArr = JSONArray()
                aluSummary.cuts.forEach { aluCut ->
                    aluCutsArr.put(
                        JSONObject().apply {
                            put("profileName", aluCut.profileName)
                            put("profileTypeCode", aluCut.profileTypeCode)
                            put("orientation", aluCut.orientation.name)
                            put("orientationLabelAr", aluCut.orientation.labelAr)
                            put("lengthMm", round2(aluCut.lengthMm))
                            put("lengthCm", round2(aluCut.lengthMm / 10f))
                            put("quantity", aluCut.quantity)
                            put("totalLengthMeters", round2(aluCut.totalLengthMeters))
                            put("profileSectionSizeMm", round2(aluCut.profileSectionSizeMm))
                            put("profileThicknessMm", round2(aluCut.profileThicknessMm))
                        }
                    )
                }

                cabinetsCutArr.put(
                    JSONObject().apply {
                        put("cabinetId", cab.id)
                        put("cabinetName", cab.name)
                        put("widthMm", round2(cab.widthMm))
                        put("heightMm", round2(cab.heightMm))
                        put("depthMm", round2(cab.depthMm))
                        put("internalWidthMm", round2(cab.internalWidthMm))
                        put("internalHeightMm", round2(cab.internalHeightMm))
                        put("internalDepthMm", round2(cab.internalDepthMm))
                        put("panelThicknessMm", round2(cab.panelThicknessMm))
                        put("hasBackPanel", cab.hasBackPanel)
                        put("backPanelMaterialName", cab.backPanelMaterialName)
                        put("backGrooveInsetMm", round2(cab.backGrooveInsetMm))
                        put("netBackPanelWidthMm", round2(cab.netBackPanelWidthMm))
                        put("netBackPanelHeightMm", round2(cab.netBackPanelHeightMm))
                        put("topStretcherWidthMm", round2(cab.topStretcherWidthMm))
                        put("melamineCutParts", partsArr)
                        put("aluminumProfileCuts", aluCutsArr)
                        put("aluminumNetLinearMeters", round2(aluSummary.netLinearMeters))
                        put("aluminumGrossMetersWithWaste", round2(aluSummary.grossLinearMetersWithWaste))
                    }
                )
            }
            root.put("parametricCabinetCutLists", cabinetsCutArr)
        }

        // 6. 1D Aluminum Bar Nesting & 2D Melamine Sheet Guillotine Nesting
        if (shouldWriteNesting) {
            val allParts = effectiveCabinets.flatMap { cab ->
                cab.generateCarcassCutParts().map { it.copy(cabinetName = cab.name) }
            }
            val packedSheets = NestingEngine.packPanels(allParts)
            exportedMelamineSheetsCount = packedSheets.size

            val sheetsArr = JSONArray()
            packedSheets.forEach { sheet ->
                val placementsArr = JSONArray()
                sheet.placements.forEach { pl ->
                    placementsArr.put(
                        JSONObject().apply {
                            put("partName", pl.partName)
                            put("category", pl.category.name)
                            put("xMm", round2(pl.x))
                            put("yMm", round2(pl.y))
                            put("widthMm", round2(pl.widthMm))
                            put("lengthMm", round2(pl.lengthMm))
                            put("isRotated", pl.isRotated)
                        }
                    )
                }
                sheetsArr.put(
                    JSONObject().apply {
                        put("sheetIndex", sheet.sheetIndex)
                        put("rawWidthMm", round2(sheet.rawWidthMm))
                        put("rawLengthMm", round2(sheet.rawLengthMm))
                        put("material", sheet.material.name)
                        put("materialDisplayName", sheet.material.displayName)
                        put("utilizationPercentage", round2(sheet.utilizationPercentage))
                        put("wastePercentage", round2(sheet.wastePercentage))
                        put("wasteAreaSqM", round2(sheet.wasteAreaSqM))
                        put("placements", placementsArr)
                    }
                )
            }

            val aluInputs = AluminumBarNestingOptimizer.extractCabinetryCutInputs(effectiveCabinets)
            val aluNestingResult = AluminumBarNestingOptimizer.optimizeCutList(
                cutInputs = aluInputs,
                config = AluminumNestingConfig(primaryBarLengthMm = options.standardAluminumBarLengthMm)
            )
            exportedAluminumBarsCount = aluNestingResult.totalBarsCount

            val aluBarsArr = JSONArray()
            aluNestingResult.bars.forEach { bar ->
                val piecesArr = JSONArray()
                bar.cutPieces.forEach { pc ->
                    piecesArr.put(
                        JSONObject().apply {
                            put("pieceNumberOnBar", pc.pieceNumberOnBar)
                            put("globalPieceNumber", pc.globalPieceNumber)
                            put("profileName", pc.profileName)
                            put("cabinetName", pc.cabinetName)
                            put("profileTypeCode", pc.profileTypeCode)
                            put("orientation", pc.orientation.name)
                            put("lengthMm", round2(pc.lengthMm))
                            put("lengthCm", round2(pc.lengthCm))
                            put("startOffsetMm", round2(pc.startOffsetMm))
                            put("endOffsetMm", round2(pc.endOffsetMm))
                            put("kerfAfterMm", round2(pc.kerfAfterMm))
                        }
                    )
                }
                aluBarsArr.put(
                    JSONObject().apply {
                        put("barIndex", bar.barIndex)
                        put("stockLengthMm", round2(bar.stockLengthMm))
                        put("profileTypeCode", bar.profileTypeCode)
                        put("profileCategoryLabel", bar.profileCategoryLabel)
                        put("netCutLengthMm", round2(bar.netCutLengthMm))
                        put("totalKerfLossMm", round2(bar.totalKerfLossMm))
                        put("remnantOffcutMm", round2(bar.remnantOffcutMm))
                        put("totalBarWasteMm", round2(bar.totalBarWasteMm))
                        put("utilizationPercentage", round2(bar.utilizationPercentage))
                        put("wastePercentage", round2(100f - bar.utilizationPercentage))
                        put("cutPieces", piecesArr)
                    }
                )
            }

            val nestingObj = JSONObject().apply {
                put("melamineSheetsCount", packedSheets.size)
                put("melamineSheets", sheetsArr)
                put("aluminumStandardBarLengthMm", round2(options.standardAluminumBarLengthMm))
                put("aluminumTotalBarsRequired", aluNestingResult.totalBarsCount)
                put("aluminumOverallUtilizationPct", round2(aluNestingResult.overallUtilizationPercentage))
                put("aluminumOverallWastePct", round2(aluNestingResult.overallWastePercentage))
                put("aluminumTotalNetCutMeters", round2(aluNestingResult.totalNetCutLengthMm / 1000f))
                put("aluminumTotalWasteMeters", round2(aluNestingResult.totalWasteLengthMm / 1000f))
                put("aluminumBars", aluBarsArr)
            }
            root.put("camNesting", nestingObj)
        }

        // 7. Bill of Materials (BOM)
        if (shouldWriteBom && bom != null) {
            val itemsArr = JSONArray()
            bom.items.forEach { item ->
                itemsArr.put(
                    JSONObject().apply {
                        put("name", item.name)
                        put("typeCode", item.typeCode)
                        put("categoryName", item.categoryName)
                        put("quantity", item.quantity)
                        put("dimensionsMm", item.dimensionsMm)
                        put("finishName", item.finishName)
                    }
                )
            }
            root.put(
                "billOfMaterials",
                JSONObject().apply {
                    put("projectName", bom.projectName)
                    put("totalCabinetsCount", bom.totalCabinetsCount)
                    put("totalLinearMeters", round2(bom.totalLinearMeters))
                    put("countertopAreaSquareMeters", round2(bom.countertopAreaSquareMeters))
                    put("generatedDate", bom.generatedDate)
                    put("items", itemsArr)
                }
            )
        }

        val formattedJson = if (options.prettyPrint) root.toString(2) else root.toString()
        val byteSize = formattedJson.toByteArray(StandardCharsets.UTF_8).size

        return JsonSerializationResult(
            jsonContent = formattedJson,
            fileSizeBytes = byteSize,
            exportedElementsCount = exportedElementsCount,
            exportedWallsCount = exportedWallsCount,
            exportedOpeningsCount = exportedOpeningsCount,
            exportedMepPointsCount = exportedMepPointsCount,
            exportedCutPartsCount = exportedCutPartsCount,
            exportedAluminumBarsCount = exportedAluminumBarsCount,
            exportedMelamineSheetsCount = exportedMelamineSheetsCount,
            exportedCatalogTypesCount = exportedCatalogTypesCount
        )
    }

    /**
     * Writes the serialized JSON output directly to a target file on disk.
     */
    fun exportToFile(
        file: File,
        project: KitchenProject,
        options: JsonExportOptions = JsonExportOptions(),
        cabinets: List<ParametricCabinet> = emptyList(),
        bom: BillOfMaterials? = null,
        materialPreset: CustomMaterialOverridePreset = CustomMaterialOverridePreset.DEFAULT_PRESETS.first(),
        workshopName: String = "ورشة التصميم الهندسي للمطابخ الذكية"
    ): JsonSerializationResult {
        file.parentFile?.mkdirs()
        val result = serializeToJsonWithMetadata(
            project = project,
            options = options,
            cabinets = cabinets,
            bom = bom,
            materialPreset = materialPreset,
            workshopName = workshopName
        )
        file.writeText(result.jsonContent, StandardCharsets.UTF_8)
        return result
    }

    /**
     * Serializes a single [KitchenElement] into a comprehensive JSONObject including 3D coordinates,
     * dimensions in mm & cm, materials, backing config, and optional 2D/3D bounding geometry.
     */
    fun serializeKitchenElement(
        elem: KitchenElement,
        includeBoundingBoxes: Boolean = true
    ): JSONObject {
        val elemObj = JSONObject()
        elemObj.put("id", elem.id)
        elemObj.put("name", elem.name)
        elemObj.put("type", elem.type.name)
        elemObj.put("typeCode", elem.type.code)
        elemObj.put("typeTitleAr", elem.type.title)
        elemObj.put("category", elem.type.category.name)
        elemObj.put("categoryDisplayName", elem.type.category.displayName)
        elemObj.put("layer", elem.layer.name)
        elemObj.put("rotationY", round2(elem.rotationY))
        elemObj.put("material", elem.material.id)
        elemObj.put("materialDetails", serializeMaterialFinish(elem.material))
        if (elem.countertopMaterial != null) {
            elemObj.put("countertopMaterial", elem.countertopMaterial.id)
            elemObj.put("countertopMaterialDetails", serializeMaterialFinish(elem.countertopMaterial))
        }
        elemObj.put("isLocked", elem.isLocked)
        elemObj.put("isVisible", elem.isVisible)
        if (elem.groupId != null) elemObj.put("groupId", elem.groupId)
        if (elem.linkedBaseElementId != null) elemObj.put("linkedBaseElementId", elem.linkedBaseElementId)

        // Manufacturing & Backing Properties
        elemObj.put("hasBackPanel", elem.hasBackPanel)
        elemObj.put("backPanelMaterialType", elem.backPanelMaterialType)
        elemObj.put("backGrooveInsetMm", round2(elem.backGrooveInsetMm))
        elemObj.put("topStretcherWidthMm", round2(elem.topStretcherWidthMm))
        elemObj.put("customBoardThicknessMm", round2(elem.customBoardThicknessMm))
        elemObj.put("customAluminumProfileMm", round2(elem.customAluminumProfileMm))

        // Position (mm & cm)
        elemObj.put(
            "position",
            JSONObject().apply {
                put("x", round2(elem.position.x))
                put("y", round2(elem.position.y))
                put("z", round2(elem.position.z))
                put("xCm", round2(elem.position.x / 10f))
                put("yCm", round2(elem.position.y / 10f))
                put("zCm", round2(elem.position.z / 10f))
            }
        )

        // Dimensions (mm & cm)
        elemObj.put(
            "dimensions",
            JSONObject().apply {
                put("x", round2(elem.dimensions.x))
                put("y", round2(elem.dimensions.y))
                put("z", round2(elem.dimensions.z))
                put("widthMm", round2(elem.dimensions.x))
                put("heightMm", round2(elem.dimensions.y))
                put("depthMm", round2(elem.dimensions.z))
                put("widthCm", round2(elem.dimensions.x / 10f))
                put("heightCm", round2(elem.dimensions.y / 10f))
                put("depthCm", round2(elem.dimensions.z / 10f))
            }
        )

        if (includeBoundingBoxes) {
            val aabb = elem.getAABB()
            elemObj.put(
                "boundingBoxAABB",
                JSONObject().apply {
                    put("minX", round2(aabb.min.x))
                    put("minY", round2(aabb.min.y))
                    put("minZ", round2(aabb.min.z))
                    put("maxX", round2(aabb.max.x))
                    put("maxY", round2(aabb.max.y))
                    put("maxZ", round2(aabb.max.z))
                }
            )
            val cornersArr = JSONArray()
            elem.getFloorCorners().forEach { corner ->
                cornersArr.put(
                    JSONObject().apply {
                        put("xMm", round2(corner.x))
                        put("zMm", round2(corner.z))
                    }
                )
            }
            elemObj.put("floorCorners2D", cornersArr)
        }

        return elemObj
    }

    /**
     * Serializes a single [KitchenElement] to a formatted JSON string for real-time object inspection & editing.
     */
    fun serializeSingleElementToJsonString(
        elem: KitchenElement,
        includeBoundingBoxes: Boolean = false,
        prettyPrint: Boolean = true
    ): String {
        val obj = serializeKitchenElement(elem, includeBoundingBoxes)
        return if (prettyPrint) obj.toString(2) else obj.toString()
    }

    /**
     * Serializes a list of [KitchenElement] objects into a JSON array string.
     */
    fun serializeElementsListToJsonString(
        elements: List<KitchenElement>,
        includeBoundingBoxes: Boolean = false,
        prettyPrint: Boolean = true
    ): String {
        val arr = JSONArray()
        elements.forEach { elem ->
            arr.put(serializeKitchenElement(elem, includeBoundingBoxes))
        }
        return if (prettyPrint) arr.toString(2) else arr.toString()
    }

    /**
     * Parses a JSON string representing a single kitchen object `{...}`, an array of objects `[...]`,
     * or a project/wrapper object `{"elements": [...]}` into a list of [KitchenElement] instances.
     */
    fun parseElementsFromJsonString(jsonString: String): List<KitchenElement> {
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("كود الـ JSON فارغ")
        }
        if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            return parseElementsArray(arr)
        }
        val root = JSONObject(trimmed)
        if (root.has("elements")) {
            return parseElementsArray(root.getJSONArray("elements"))
        }
        return listOf(parseSingleElement(root, 0))
    }

    /**
     * Parses either a full project JSON object, an elements-only JSON object (`{"elements": [...]}`),
     * or a raw JSON array of elements (`[...]`) into an updated [KitchenProject].
     */
    fun importProjectOrElementsFromJson(
        jsonString: String,
        fallbackProject: KitchenProject
    ): KitchenProject {
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("ملف الـ JSON فارغ")
        }

        if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            val parsedElements = parseElementsArray(arr)
            if (parsedElements.isEmpty()) {
                throw IllegalArgumentException("لم يتم العثور على عناصر صالحة داخل مصفوفة الـ JSON")
            }
            return fallbackProject.copy(elements = parsedElements)
        }

        val root = JSONObject(trimmed)
        val hasRoom = root.has("room")
        val hasElements = root.has("elements")

        if (!hasRoom && !hasElements) {
            // Check if it's a single element JSON object
            if (root.has("type") || root.has("position") || root.has("dimensions") || root.has("name")) {
                val singleElem = parseSingleElement(root)
                val existingIdx = fallbackProject.elements.indexOfFirst { it.id == singleElem.id }
                val updatedList = if (existingIdx >= 0) {
                    fallbackProject.elements.toMutableList().apply { set(existingIdx, singleElem) }
                } else {
                    fallbackProject.elements + singleElem
                }
                return fallbackProject.copy(elements = updatedList)
            }
            throw IllegalArgumentException("ملف JSON لا يحتوي على بيانات غرفة (room) أو عناصر مطبخ (elements)")
        }

        val id = root.optString("id", root.optJSONObject("metadata")?.optString("projectId") ?: fallbackProject.id)
        val name = root.optString("name", root.optJSONObject("metadata")?.optString("projectName") ?: fallbackProject.name)
        val createdAt = root.optLong("createdAt", fallbackProject.createdAt)

        val room = if (hasRoom) {
            parseRoomStructure(root.getJSONObject("room"), fallbackProject.room)
        } else {
            fallbackProject.room
        }

        val elements = if (hasElements) {
            parseElementsArray(root.getJSONArray("elements"))
        } else {
            fallbackProject.elements
        }

        val groups = if (root.has("groups")) {
            val grpsArr = root.getJSONArray("groups")
            val list = mutableListOf<KitchenGroup>()
            for (i in 0 until grpsArr.length()) {
                val gObj = grpsArr.getJSONObject(i)
                val idsArr = gObj.optJSONArray("elementIds") ?: JSONArray()
                val ids = mutableListOf<String>()
                for (k in 0 until idsArr.length()) {
                    ids.add(idsArr.getString(k))
                }
                list.add(
                    KitchenGroup(
                        id = gObj.optString("id", "grp_$i"),
                        name = gObj.optString("name", "مجموعة $i"),
                        elementIds = ids
                    )
                )
            }
            list
        } else {
            fallbackProject.groups
        }

        return KitchenProject(
            id = id,
            name = name,
            room = room,
            elements = elements,
            groups = groups,
            createdAt = createdAt
        )
    }

    private fun parseRoomStructure(roomObj: JSONObject, fallbackRoom: RoomStructure): RoomStructure {
        val rWidth = roomObj.optDouble("widthMm", fallbackRoom.widthMm.toDouble()).toFloat()
        val rDepth = roomObj.optDouble("depthMm", fallbackRoom.depthMm.toDouble()).toFloat()
        val rHeight = roomObj.optDouble("heightMm", fallbackRoom.heightMm.toDouble()).toFloat()
        val rThick = roomObj.optDouble("wallThicknessMm", fallbackRoom.wallThicknessMm.toDouble()).toFloat()

        val layoutShape = try {
            RoomLayoutShape.valueOf(roomObj.optString("layoutShape", fallbackRoom.layoutShape.name))
        } catch (_: Exception) {
            fallbackRoom.layoutShape
        }

        val floorMatKey = roomObj.optString(
            "floorMaterialId",
            roomObj.optJSONObject("floorMaterial")?.optString("id") ?: roomObj.optString("floorMaterial")
        )
        val floorMat = MaterialFinish.ALL_FINISHES.find { it.id == floorMatKey || it.name == floorMatKey }
            ?: fallbackRoom.floorMaterial

        val wallFinishKey = roomObj.optString(
            "wallFinishId",
            roomObj.optJSONObject("wallFinish")?.optString("id") ?: roomObj.optString("wallFinish")
        )
        val wallFinish = MaterialFinish.ALL_FINISHES.find { it.id == wallFinishKey || it.name == wallFinishKey }
            ?: fallbackRoom.wallFinish

        val walls = if (roomObj.has("walls")) {
            val wallsArr = roomObj.getJSONArray("walls")
            val parsedWalls = mutableListOf<Wall>()
            for (i in 0 until wallsArr.length()) {
                val wObj = wallsArr.getJSONObject(i)
                val side = try {
                    WallSide.valueOf(wObj.getString("side"))
                } catch (_: Exception) {
                    WallSide.values()[i % WallSide.values().size]
                }
                val ops = mutableListOf<WallOpening>()
                val opsArr = wObj.optJSONArray("openings") ?: JSONArray()
                for (j in 0 until opsArr.length()) {
                    val oObj = opsArr.getJSONObject(j)
                    val opType = try {
                        OpeningType.valueOf(oObj.getString("type"))
                    } catch (_: Exception) {
                        OpeningType.WINDOW
                    }
                    ops.add(
                        WallOpening(
                            id = oObj.optString("id", "op_${i}_$j"),
                            type = opType,
                            offsetMm = oObj.optDouble("offsetMm", 600.0).toFloat(),
                            widthMm = oObj.optDouble("widthMm", 900.0).toFloat(),
                            heightMm = oObj.optDouble("heightMm", 1200.0).toFloat(),
                            sillHeightMm = oObj.optDouble("sillHeightMm", 0.0).toFloat()
                        )
                    )
                }

                val utils = mutableListOf<WallUtilityPoint>()
                val utilsArr = wObj.optJSONArray("utilityPoints") ?: JSONArray()
                for (j in 0 until utilsArr.length()) {
                    val uObj = utilsArr.getJSONObject(j)
                    val uType = try {
                        UtilityPointType.valueOf(uObj.getString("type"))
                    } catch (_: Exception) {
                        UtilityPointType.ELECTRICAL_SOCKET
                    }
                    utils.add(
                        WallUtilityPoint(
                            id = uObj.optString("id", "util_${i}_$j"),
                            type = uType,
                            offsetFromWallStartMm = uObj.optDouble("offsetFromWallStartMm", 1000.0).toFloat(),
                            elevationFromFloorMm = uObj.optDouble("elevationFromFloorMm", uType.defaultElevationMm.toDouble()).toFloat(),
                            label = uObj.optString("label", uType.titleAr)
                        )
                    )
                }

                parsedWalls.add(
                    Wall(
                        id = wObj.optString("id", "wall_${side.name.lowercase(Locale.US)}"),
                        side = side,
                        lengthMm = wObj.optDouble("lengthMm", rWidth.toDouble()).toFloat(),
                        heightMm = wObj.optDouble("heightMm", rHeight.toDouble()).toFloat(),
                        thicknessMm = wObj.optDouble("thicknessMm", rThick.toDouble()).toFloat(),
                        cornerAngleDegrees = wObj.optDouble("cornerAngleDegrees", 90.0).toFloat(),
                        isEnabled = wObj.optBoolean("isEnabled", true),
                        openings = ops,
                        utilityPoints = utils
                    )
                )
            }
            parsedWalls
        } else {
            fallbackRoom.walls
        }

        return RoomStructure(
            widthMm = rWidth,
            depthMm = rDepth,
            heightMm = rHeight,
            wallThicknessMm = rThick,
            layoutShape = layoutShape,
            walls = walls,
            floorMaterial = floorMat,
            wallFinish = wallFinish
        )
    }

    private fun parseElementsArray(elemsArr: JSONArray): List<KitchenElement> {
        val list = mutableListOf<KitchenElement>()
        for (i in 0 until elemsArr.length()) {
            val eObj = elemsArr.optJSONObject(i) ?: continue
            list.add(parseSingleElement(eObj, i))
        }
        return list
    }

    private fun parseSingleElement(eObj: JSONObject, index: Int = 0): KitchenElement {
        val typeStr = eObj.optString("type", "")
        val codeStr = eObj.optString("typeCode", "")
        val type = KitchenElementType.values().find {
            it.name.equals(typeStr, ignoreCase = true) || it.code.equals(codeStr, ignoreCase = true)
        } ?: KitchenElementType.BASE_SINGLE_DOOR

        val layer = try {
            LayerType.valueOf(eObj.optString("layer", ""))
        } catch (_: Exception) {
            LayerType.fromElementType(type)
        }

        val pObj = eObj.optJSONObject("position")
        val pos = if (pObj != null) {
            Vector3(
                pObj.optDouble("x", 1000.0).toFloat(),
                pObj.optDouble("y", type.defaultElevationY.toDouble()).toFloat(),
                pObj.optDouble("z", 1000.0).toFloat()
            )
        } else {
            Vector3(1000f, type.defaultElevationY, 1000f)
        }

        val dObj = eObj.optJSONObject("dimensions")
        val dims = if (dObj != null) {
            val w = dObj.optDouble("x", dObj.optDouble("widthMm", type.defaultDimensions.x.toDouble())).toFloat()
            val h = dObj.optDouble("y", dObj.optDouble("heightMm", type.defaultDimensions.y.toDouble())).toFloat()
            val d = dObj.optDouble("z", dObj.optDouble("depthMm", type.defaultDimensions.z.toDouble())).toFloat()
            Vector3(w, h, d)
        } else {
            type.defaultDimensions
        }

        val matStr = eObj.optString("material", "")
        val mat = MaterialFinish.ALL_FINISHES.find { it.id == matStr || it.name == matStr }
            ?: KitchenElement.defaultMaterialForType(type)

        val ctMat = if (eObj.has("countertopMaterial")) {
            val ctStr = eObj.optString("countertopMaterial", "")
            MaterialFinish.ALL_FINISHES.find { it.id == ctStr || it.name == ctStr }
        } else {
            KitchenElement.defaultCountertopForType(type)
        }

        return KitchenElement(
            id = eObj.optString("id", "elem_json_$index"),
            name = eObj.optString("name", type.title),
            type = type,
            layer = layer,
            position = pos,
            rotationY = eObj.optDouble("rotationY", 0.0).toFloat(),
            dimensions = dims,
            material = mat,
            countertopMaterial = ctMat,
            isLocked = eObj.optBoolean("isLocked", false),
            isVisible = eObj.optBoolean("isVisible", true),
            groupId = eObj.optString("groupId", "").takeIf { it.isNotBlank() },
            linkedBaseElementId = eObj.optString("linkedBaseElementId", "").takeIf { it.isNotBlank() },
            hasBackPanel = eObj.optBoolean("hasBackPanel", true),
            backPanelMaterialType = eObj.optString("backPanelMaterialType", "MDF ميلامين 8 مم"),
            backGrooveInsetMm = eObj.optDouble("backGrooveInsetMm", 10.0).toFloat(),
            topStretcherWidthMm = eObj.optDouble("topStretcherWidthMm", 100.0).toFloat(),
            customBoardThicknessMm = eObj.optDouble("customBoardThicknessMm", 18.0).toFloat(),
            customAluminumProfileMm = eObj.optDouble("customAluminumProfileMm", 45.0).toFloat()
        )
    }

    private fun deriveParametricCabinetsFromElements(
        elements: List<KitchenElement>,
        preset: CustomMaterialOverridePreset
    ): List<ParametricCabinet> {
        val cabinetElems = elements.filter {
            it.type.category == ElementCategory.BASE_CABINET ||
                    it.type.category == ElementCategory.WALL_CABINET ||
                    it.type.category == ElementCategory.TALL_UNIT ||
                    it.type.category == ElementCategory.BRIDGE_CABINET
        }
        return cabinetElems.map { elem ->
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

    private fun serializeMaterialFinish(finish: MaterialFinish): JSONObject {
        return JSONObject().apply {
            put("id", finish.id)
            put("name", finish.name)
            put("category", finish.category.name)
            put("colorHex", String.format(Locale.US, "#%06X", (0xFFFFFF and finish.colorHex.toInt())))
            put("roughness", round2(finish.roughness))
            put("metallic", round2(finish.metallic))
        }
    }

    private fun round2(value: Float): Double {
        return kotlin.math.round(value * 100.0) / 100.0
    }
}
