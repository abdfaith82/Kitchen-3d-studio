package com.example

import com.example.core.cam.DoorStyle
import com.example.core.cam.HandleStyle
import com.example.core.cam.NestingEngine
import com.example.core.cam.PanelMaterial
import com.example.core.cam.ParametricCabinet
import com.example.core.export.DxfExporter
import com.example.core.math.Vector3
import com.example.core.packaging.RoomProjectPackager
import com.example.domain.model.KitchenElement
import com.example.domain.model.KitchenElementType
import com.example.domain.model.KitchenGroup
import com.example.domain.model.MaterialFinish
import com.example.domain.model.OpeningType
import com.example.domain.model.RoomStructure
import com.example.domain.model.Wall
import com.example.domain.model.WallOpening
import com.example.domain.model.WallSide
import com.example.domain.repository.KitchenProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExportAndPackagingTest {

    private fun createSampleProject(): KitchenProject {
        val walls = listOf(
            Wall(
                id = "wall_north",
                side = WallSide.NORTH,
                lengthMm = 4200f,
                heightMm = 2700f,
                thicknessMm = 150f,
                openings = listOf(
                    WallOpening("win_1", OpeningType.WINDOW, 1500f, 1200f, 1100f, 1000f)
                )
            ),
            Wall("wall_east", WallSide.EAST, 3600f, 2700f, 150f, openings = emptyList()),
            Wall(
                id = "wall_south",
                side = WallSide.SOUTH,
                lengthMm = 4200f,
                heightMm = 2700f,
                thicknessMm = 150f,
                openings = listOf(
                    WallOpening("door_1", OpeningType.DOOR, 600f, 900f, 2100f, 0f)
                )
            ),
            Wall("wall_west", WallSide.WEST, 3600f, 2700f, 150f, openings = emptyList())
        )

        val room = RoomStructure(
            widthMm = 4200f,
            depthMm = 3600f,
            heightMm = 2700f,
            wallThicknessMm = 150f,
            walls = walls
        )

        val elements = listOf(
            KitchenElement(
                id = "base_sink_1",
                name = "خزانة حوض 80 سم",
                type = KitchenElementType.BASE_SINK_UNIT,
                position = Vector3(1000f, 0f, 600f),
                rotationY = 0f,
                dimensions = Vector3(800f, 850f, 600f),
                material = MaterialFinish.NATURAL_OAK
            ),
            KitchenElement(
                id = "base_drawer_1",
                name = "خزانة أدراج 60 سم",
                type = KitchenElementType.BASE_3_DRAWERS,
                position = Vector3(1800f, 0f, 600f),
                rotationY = 0f,
                dimensions = Vector3(600f, 850f, 600f),
                material = MaterialFinish.WALNUT_DARK
            ),
            KitchenElement(
                id = "wall_cab_1",
                name = "خزانة علوية 80 سم",
                type = KitchenElementType.WALL_DOUBLE_DOOR,
                position = Vector3(1000f, 1500f, 600f),
                rotationY = 0f,
                dimensions = Vector3(800f, 700f, 350f),
                material = MaterialFinish.WHITE_HIGH_GLOSS
            )
        )

        val groups = listOf(
            KitchenGroup(id = "grp_1", name = "طقم الغسيل", elementIds = listOf("base_sink_1", "wall_cab_1"))
        )

        return KitchenProject(
            id = "proj_test_101",
            name = "مطبخ مودرن تجريبي",
            room = room,
            elements = elements,
            groups = groups
        )
    }

    @Test
    fun testDxfExporter_StructureAndLayers() {
        val project = createSampleProject()
        val dxfOutput = DxfExporter.generateDxf(project)

        // 1. Verify standard DXF ASCII sections
        assertTrue("DXF must contain HEADER section", dxfOutput.contains("SECTION\n2\nHEADER"))
        assertTrue("DXF must specify AutoCAD version", dxfOutput.contains("\$ACADVER\n1\nAC1009"))
        assertTrue("DXF must specify mm units", dxfOutput.contains("\$INSUNITS\n70\n4"))
        assertTrue("DXF must contain TABLES section", dxfOutput.contains("SECTION\n2\nTABLES"))

        // 2. Verify CAD Layers
        assertTrue("DXF must contain WALLS layer", dxfOutput.contains("LAYER\n2\nWALLS"))
        assertTrue("DXF must contain OPENINGS layer", dxfOutput.contains("LAYER\n2\nOPENINGS"))
        assertTrue("DXF must contain CABINETS_BASE layer", dxfOutput.contains("LAYER\n2\nCABINETS_BASE"))
        assertTrue("DXF must contain CABINETS_WALL layer", dxfOutput.contains("LAYER\n2\nCABINETS_WALL"))
        assertTrue("DXF must contain DIMENSIONS layer", dxfOutput.contains("LAYER\n2\nDIMENSIONS"))

        // 3. Verify ENTITIES section & Geometry
        assertTrue("DXF must contain ENTITIES section", dxfOutput.contains("SECTION\n2\nENTITIES"))
        assertTrue("DXF must contain POLYLINE or LINE entities", dxfOutput.contains("0\nPOLYLINE") || dxfOutput.contains("0\nLINE"))
        assertTrue("DXF must contain room dimension measurement", dxfOutput.contains("4200 mm"))
        assertTrue("DXF must terminate with EOF", dxfOutput.endsWith("0\nEOF\n"))
    }

    @Test
    fun testSerializeFloorPlanToDxf_FullCadEntitiesAndRoundTripParse() {
        val baseProject = createSampleProject()
        val enrichedElements = baseProject.elements + listOf(
            KitchenElement(
                id = "cooktop_1",
                name = "مسطح غاز 90 سم",
                type = KitchenElementType.COOKTOP_GAS_90,
                position = Vector3(2600f, 850f, 600f),
                rotationY = 0f,
                dimensions = Vector3(900f, 60f, 520f),
                material = MaterialFinish.STAINLESS_STEEL
            ),
            KitchenElement(
                id = "fridge_1",
                name = "ثلاجة بابين",
                type = KitchenElementType.REFRIGERATOR_FROST,
                position = Vector3(3600f, 0f, 600f),
                rotationY = 0f,
                dimensions = Vector3(750f, 1850f, 700f),
                material = MaterialFinish.STAINLESS_STEEL
            )
        )
        val project = baseProject.copy(
            room = RoomStructure.createDefaultRectangular(),
            elements = enrichedElements
        )

        val result = DxfExporter.serializeFloorPlanWithMetadata(
            project = project,
            options = DxfExporter.DxfExportOptions(
                includeWalls = true,
                includeOpenings = true,
                includeCabinets = true,
                includeAppliances = true,
                includeMepUtilities = true,
                includeWorkTriangle = true,
                includeDimensions = true,
                includeLabels = true,
                includeTitleBlock = true
            )
        )

        val dxf = result.dxfContent
        assertTrue("Must contain LTYPE table with DASHED and CENTER", dxf.contains("TABLE\n2\nLTYPE") && dxf.contains("DASHED"))
        assertTrue("Must contain EXTMIN and EXTMAX header bounds", dxf.contains("\$EXTMIN") && dxf.contains("\$EXTMAX"))
        assertTrue("Must contain ARC entity for door swing", dxf.contains("0\nARC\n8\nOPENINGS"))
        assertTrue("Must contain CIRCLE entities for sink/cooktop/MEP", dxf.contains("0\nCIRCLE"))
        assertTrue("Must contain MEP_UTILITIES layer entities", (result.layerEntityCounts["MEP_UTILITIES"] ?: 0) > 0)
        assertTrue("Must contain WORK_TRIANGLE layer entities", (result.layerEntityCounts["WORK_TRIANGLE"] ?: 0) > 0)
        assertTrue("Must export all 4 walls", result.exportedWallsCount == 4)
        assertTrue("Must export openings", result.exportedOpeningsCount >= 2)
        assertTrue("Must export cabinets and appliances", result.exportedCabinetsCount == 3 && result.exportedAppliancesCount == 2)

        // Verify round-trip compatibility with DxfParser
        val parsed = com.example.core.importer.DxfParser.parse(
            inputStream = ByteArrayInputStream(dxf.toByteArray(Charsets.UTF_8)),
            fileName = "test_floorplan.dxf",
            autoScaleToMillimeters = false
        )
        assertTrue("Parsed DXF must contain polylines from R12 POLYLINE", parsed.polylines.isNotEmpty())
        assertTrue("Parsed DXF must contain lines", parsed.lines.isNotEmpty())
        assertTrue("Parsed DXF layers must include WALLS and CABINETS_BASE", parsed.layers.contains("WALLS") && parsed.layers.contains("CABINETS_BASE"))
    }

    @Test
    fun testParametricCabinet_CutListGeneration() {
        val cabinet = ParametricCabinet(
            id = "cab_1",
            name = "خزانة درفة واحدة 60 سم",
            widthMm = 600f,
            heightMm = 850f,
            depthMm = 600f,
            panelThicknessMm = 18f,
            backPanelThicknessMm = 8f,
            toeKickHeightMm = 100f,
            numberOfShelves = 1,
            numberOfDrawers = 0,
            doorStyle = DoorStyle.SLAB_MODERN,
            handleStyle = HandleStyle.BAR_PULL
        )

        val cutParts = cabinet.generateCarcassCutParts()
        assertTrue("Cabinet cut list must contain multiple carcass panels", cutParts.size >= 5)

        // Verify side gables
        val gables = cutParts.find { it.partName.contains("جنب") }
        assertNotNull("Should contain side panels", gables)
        assertEquals("Side panel depth should be 600mm", 600f, gables!!.widthMm, 0.1f)
        assertEquals("Side panel height should be 750mm (850 - 100 toe kick)", 750f, gables.lengthMm, 0.1f)
        assertEquals("Should have 2 side panels", 2, gables.quantity)

        // Verify back panel
        val backPanel = cutParts.find { it.partName.contains("ظهر") }
        assertNotNull("Should contain back panel", backPanel)
        assertEquals("Back panel material should be 8mm MDF", PanelMaterial.MDF_8MM, backPanel!!.material)

        // Verify door facade
        val door = cutParts.find { it.partName.contains("درفة") }
        assertNotNull("Should contain door facade", door)
        assertEquals("Door edge banding should be 4 sides", 4, door!!.edgeBandingSides)
    }

    @Test
    fun testNestingEngine_GuillotinePackAndReport() {
        val cabinets = listOf(
            ParametricCabinet(id = "c1", name = "خزانة 60 سم", widthMm = 600f, heightMm = 850f, depthMm = 600f),
            ParametricCabinet(id = "c2", name = "خزانة أدراج 80 سم", widthMm = 800f, heightMm = 850f, depthMm = 600f, numberOfDrawers = 3)
        )

        val report = NestingEngine.calculateCompleteReport(cabinets)
        assertEquals("Should report 2 cabinets", 2, report.totalCabinetsCount)
        assertTrue("Should calculate required 18mm MDF raw sheets", report.totalMdf18Sheets > 0)
        assertTrue("Overall wood utilization should be positive", report.overallWoodUtilizationPct > 0f)
        assertTrue("Wood waste percentage should be realistic", report.overallWoodWastePct in 0f..100f)
        assertTrue("Total edge banding should be calculated", report.totalEdgeBandingMeters > 0f)
        assertTrue("Total estimated cost should be greater than zero", report.estimatedTotalCost > 0f)
        assertTrue("Hardware list must include hinges or drawer slides", report.hardwareList.isNotEmpty())
    }

    @Test
    fun testRoomPackage_PackagingAndUnpackingRoundTrip() {
        val originalProject = createSampleProject()

        // 1. Pack into .room bytes
        val tempDir = File(System.getProperty("java.io.tmpdir"), "room_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        val roomFile = File(tempDir, "test_kitchen.room")

        val exportedFile = RoomProjectPackager.exportProjectToRoomFile(
            outputFile = roomFile,
            project = originalProject,
            thumbnailBitmap = null,
            enableEncryption = true
        )

        assertTrue("Exported .room file must exist", exportedFile.exists())
        assertTrue("Exported file must have non-zero size", exportedFile.length() > 0)

        // 2. Unpack from .room file
        val unpacked = RoomProjectPackager.importFromFile(exportedFile)

        // 3. Verify Manifest
        val manifest = unpacked.manifest
        assertEquals("ROOM_CAD_V1", manifest.format)
        assertEquals(originalProject.name, manifest.projectName)
        assertEquals(originalProject.elements.size, manifest.elementCount)
        assertEquals(originalProject.room.widthMm, manifest.roomWidthMm, 0.1f)
        assertEquals(originalProject.room.depthMm, manifest.roomDepthMm, 0.1f)
        assertTrue("Manifest should indicate encrypted archive", manifest.isEncrypted)
        assertTrue("Manifest checksum should be non-zero", manifest.checksum != 0L)

        // 4. Verify Project Content Integrity
        val restoredProject = unpacked.project
        assertEquals(originalProject.id, restoredProject.id)
        assertEquals(originalProject.name, restoredProject.name)
        assertEquals(originalProject.room.widthMm, restoredProject.room.widthMm, 0.1f)
        assertEquals(originalProject.room.depthMm, restoredProject.room.depthMm, 0.1f)
        assertEquals(originalProject.room.walls.size, restoredProject.room.walls.size)
        assertEquals(originalProject.elements.size, restoredProject.elements.size)

        // Verify elements details
        val origElem = originalProject.elements.first()
        val restElem = restoredProject.elements.first()
        assertEquals(origElem.id, restElem.id)
        assertEquals(origElem.name, restElem.name)
        assertEquals(origElem.type, restElem.type)
        assertEquals(origElem.dimensions.x, restElem.dimensions.x, 0.1f)
        assertEquals(origElem.dimensions.y, restElem.dimensions.y, 0.1f)
        assertEquals(origElem.material, restElem.material)

        // Verify groups
        assertEquals(originalProject.groups.size, restoredProject.groups.size)
        assertEquals(originalProject.groups.first().name, restoredProject.groups.first().name)

        // Clean up
        exportedFile.delete()
        tempDir.deleteRecursively()
    }

    @Test
    fun testExportFloorPlanAndCutListsToDxf_CncSheetsGroovesAndAluminumBars() {
        val project = createSampleProject()
        val result = DxfExporter.serializeFloorPlanWithMetadata(
            project = project,
            options = DxfExporter.DxfExportOptions(
                includeWalls = true,
                includeCabinets = true,
                includeCabinetCutList = true,
                includeCncNestedSheets = true,
                includeAluminumBarCutList = true,
                includeRouterGroovesAndDrilling = true,
                standardAluminumBarLengthMm = 6000f
            )
        )

        val dxf = result.dxfContent
        assertTrue("Must export at least 1 CNC nested sheet", result.exportedCncSheetsCount > 0)
        assertTrue("Must export multiple CNC cut panels", result.exportedCncCutPartsCount >= 10)
        assertTrue("Must export at least 1 aluminum bar", result.exportedAluminumBarsCount > 0)
        assertTrue("Must export aluminum profile cut pieces", result.exportedAluminumCutPiecesCount > 0)

        // Verify CNC layers exist and have entities
        assertTrue("CNC_SHEET_BORDER layer must have entities", (result.layerEntityCounts["CNC_SHEET_BORDER"] ?: 0) > 0)
        assertTrue("CNC_PANEL_CUT layer must have entities", (result.layerEntityCounts["CNC_PANEL_CUT"] ?: 0) > 0)
        assertTrue("CNC_ROUTER_GROOVE layer must have entities", (result.layerEntityCounts["CNC_ROUTER_GROOVE"] ?: 0) > 0)
        assertTrue("CNC_EDGE_BANDING layer must have entities", (result.layerEntityCounts["CNC_EDGE_BANDING"] ?: 0) > 0)
        assertTrue("CNC_ALU_BARS layer must have entities", (result.layerEntityCounts["CNC_ALU_BARS"] ?: 0) > 0)
        assertTrue("CNC_PART_LABELS layer must have entities", (result.layerEntityCounts["CNC_PART_LABELS"] ?: 0) > 0)

        // Verify direct helper exportFloorPlanAndCutListsToDxf
        val directDxf = DxfExporter.exportFloorPlanAndCutListsToDxf(project = project)
        assertTrue("Direct CNC DXF must include CNC_PANEL_CUT layer", directDxf.contains("CNC_PANEL_CUT"))
        assertTrue("Direct CNC DXF must include CNC_ALU_BARS layer", directDxf.contains("CNC_ALU_BARS"))
        assertTrue("Direct CNC DXF must terminate with EOF", directDxf.endsWith("0\nEOF\n"))
    }

    @Test
    fun testSmartSnapper_AutoWallRotationAndFlushLocking() {
        val project = createSampleProject()
        val room = project.room
        val westCab = KitchenElement(
            id = "cab_west_test",
            name = "خزانة سفلية 60 سم",
            type = KitchenElementType.BASE_SINGLE_DOOR,
            position = Vector3(310f, 0f, 1500f), // Near West wall (x=0, depth=600 -> halfD=300)
            rotationY = 0f,
            dimensions = Vector3(600f, 850f, 600f),
            material = MaterialFinish.NATURAL_OAK
        )

        val snapWest = com.example.core.geometry.SmartSnapper.snap(
            target = westCab,
            existingElements = emptyList(),
            room = room,
            snapThresholdMm = 80f,
            autoAlignRotationToWall = true
        )

        assertTrue("Cabinet near West wall must snap flush to wall", snapWest.isSnappedToWall)
        assertEquals("Cabinet near West wall must auto-rotate to 90 deg", 90f, snapWest.rotationY, 0.1f)
        assertEquals("Cabinet center X must lock flush at half-depth (300mm)", 300f, snapWest.position.x, 1.0f)
    }

    @Test
    fun testSmartSnapper_CollisionAndErgonomicClearanceIndicator() {
        val project = createSampleProject()
        val room = project.room

        // 1. Place a base cabinet on the North wall at X=1200, Z=300
        val northCabinet = KitchenElement(
            id = "cab_north_anchor",
            name = "خزانة سفلية 80 سم",
            type = KitchenElementType.BASE_DOUBLE_DOOR,
            position = Vector3(1200f, 0f, 300f),
            rotationY = 0f,
            dimensions = Vector3(800f, 850f, 600f),
            material = MaterialFinish.NATURAL_OAK
        )

        // 2. Test physical overlap collision indicator when another cabinet is placed overlapping it
        val overlappingCabinet = KitchenElement(
            id = "cab_overlapping",
            name = "خزانة متداخلة",
            type = KitchenElementType.BASE_SINGLE_DOOR,
            position = Vector3(1250f, 0f, 300f),
            rotationY = 0f,
            dimensions = Vector3(600f, 850f, 600f),
            material = MaterialFinish.NATURAL_OAK
        )

        val overlapSnap = com.example.core.geometry.SmartSnapper.snap(
            target = overlappingCabinet,
            existingElements = listOf(northCabinet),
            room = room,
            snapThresholdMm = 40f
        )

        assertTrue("Overlapping cabinet must trigger red collision/violation indicator", overlapSnap.hasCollisionOrViolation)
        assertTrue("Overlapping cabinet must flag physical overlap", overlapSnap.isPhysicalOverlap)
        assertTrue("Conflicting element IDs must include northCabinet", "cab_north_anchor" in overlapSnap.conflictingElementIds)
        assertTrue("Red collision guideline must be emitted", overlapSnap.guideLines.any { it.isCollisionWarning })

        // 3. Test ergonomic door-clearance violation when an island is placed only 450mm in front of northCabinet (requires >= 850mm walkway / 650mm swing)
        val blockingIsland = KitchenElement(
            id = "island_too_close",
            name = "جزيرة قريبة جداً",
            type = KitchenElementType.BASE_DOUBLE_DOOR,
            position = Vector3(1200f, 0f, 1300f), // front of northCabinet is Z=600, front of island is Z=1000 -> only 400mm clearance!
            rotationY = 0f,
            dimensions = Vector3(1200f, 850f, 600f),
            material = MaterialFinish.NATURAL_OAK
        )

        val clearanceSnap = com.example.core.geometry.SmartSnapper.snap(
            target = northCabinet,
            existingElements = listOf(northCabinet, blockingIsland),
            room = room,
            snapThresholdMm = 40f
        )

        assertTrue("Blocked door swing (< 850mm) must trigger collision/ergonomic indicator", clearanceSnap.hasCollisionOrViolation)
        assertTrue("Must flag ergonomic clearance violation", clearanceSnap.isErgonomicClearanceViolation)

        val colWithErg = com.example.core.geometry.CollisionDetector.checkCollision(
            candidate = northCabinet,
            existingElements = listOf(northCabinet, blockingIsland),
            room = room,
            includeErgonomicClearance = true
        )
        assertTrue("CollisionDetector with includeErgonomicClearance must flag violation", colWithErg.hasCollision)
        assertTrue("CollisionDetector must mark isErgonomicClearanceViolation", colWithErg.isErgonomicClearanceViolation)
    }

    @Test
    fun testPreventCabinetOverlap_MovementAndPlacement() {
        val project = createSampleProject()
        val room = project.room

        // Anchor cabinet at X=1200, Z=300 (width 800 -> X spans 800..1600)
        val anchorCabinet = KitchenElement(
            id = "cab_anchor_1",
            name = "خزانة سفلية 80 سم",
            type = KitchenElementType.BASE_DOUBLE_DOOR,
            position = Vector3(1200f, 0f, 300f),
            rotationY = 0f,
            dimensions = Vector3(800f, 850f, 600f),
            material = MaterialFinish.NATURAL_OAK
        )

        // Moving cabinet starts at X=1950 (width 600 -> X spans 1650..2250) and is dragged into X=1450 (overlapping anchorCabinet)
        val originalMoving = KitchenElement(
            id = "cab_moving_1",
            name = "خزانة متحركة 60 سم",
            type = KitchenElementType.BASE_SINGLE_DOOR,
            position = Vector3(1950f, 0f, 300f),
            rotationY = 0f,
            dimensions = Vector3(600f, 850f, 600f),
            material = MaterialFinish.NATURAL_OAK
        )
        val overlappingTarget = originalMoving.copy(position = Vector3(1450f, 0f, 300f))

        // 1. Verify SmartSnapper with preventOverlap = true stops cabinet flush at X = 1900 (1600 + 300)
        val preventedSnap = com.example.core.geometry.SmartSnapper.snap(
            target = overlappingTarget,
            existingElements = listOf(anchorCabinet),
            room = room,
            snapThresholdMm = 80f,
            preventOverlap = true
        )
        assertTrue("Overlap must be marked as prevented", preventedSnap.wasOverlapPrevented)
        assertTrue("Resolved snap must have no physical overlap", !preventedSnap.isPhysicalOverlap)
        assertEquals("Cabinet must lock flush at right edge of anchor (1600 + 300 = 1900mm)", 1900f, preventedSnap.position.x, 1.5f)

        // 2. Verify findNearestFreePosition relocates a cabinet dropped directly on top of anchorCabinet
        val droppedOnTop = originalMoving.copy(position = Vector3(1200f, 0f, 300f))
        val freeSlotElem = com.example.core.geometry.CollisionDetector.findNearestFreePosition(
            candidate = droppedOnTop,
            existingElements = listOf(anchorCabinet),
            room = room
        )
        assertTrue(
            "findNearestFreePosition must produce a non-overlapping placement",
            !com.example.core.geometry.CollisionDetector.hasPhysicalOverlap(freeSlotElem, listOf(anchorCabinet), room)
        )

        // 3. Verify resolveAllSceneOverlaps separates multiple overlapping cabinets
        val thirdOverlapping = originalMoving.copy(id = "cab_dup_overlap", position = Vector3(1210f, 0f, 300f))
        val resolvedScene = com.example.core.geometry.CollisionDetector.resolveAllSceneOverlaps(
            elements = listOf(anchorCabinet, droppedOnTop, thirdOverlapping),
            room = room
        )
        for (elem in resolvedScene) {
            assertTrue(
                "No element in resolvedScene should physically overlap another",
                !com.example.core.geometry.CollisionDetector.hasPhysicalOverlap(elem, resolvedScene, room)
            )
        }
    }

    @Test
    fun testJsonProjectExporter_ModesAndRoundTripImport() {
        val project = createSampleProject()

        // 1. Test Full Project + Elements + CAM JSON export
        val fullResult = com.example.core.export.JsonProjectExporter.serializeToJsonWithMetadata(
            project = project,
            options = com.example.core.export.JsonProjectExporter.JsonExportOptions(
                exportMode = com.example.core.export.JsonProjectExporter.JsonExportMode.FULL_PROJECT_AND_CAM,
                includeRoomAndMep = true,
                includeSceneElements = true,
                includeCatalogDefinitions = true,
                includeParametricCutList = true,
                includeAluminumAndSheetNesting = true
            )
        )
        assertTrue("JSON content must not be empty", fullResult.jsonContent.isNotEmpty())
        assertEquals("Should export 3 elements", 3, fullResult.exportedElementsCount)
        assertEquals("Should export 4 walls", 4, fullResult.exportedWallsCount)
        assertTrue("Should export cut parts", fullResult.exportedCutPartsCount > 0)
        assertTrue("Should export aluminum bars", fullResult.exportedAluminumBarsCount > 0)
        assertTrue("Should export melamine sheets", fullResult.exportedMelamineSheetsCount > 0)
        assertTrue("Should export catalog definitions", fullResult.exportedCatalogTypesCount > 20)
        assertTrue("JSON must contain boundingBoxAABB and floorCorners2D", fullResult.jsonContent.contains("\"boundingBoxAABB\"") && fullResult.jsonContent.contains("\"floorCorners2D\""))

        // 2. Test Round-Trip JSON Import of Full Project
        val reimportedProject = com.example.core.export.JsonProjectExporter.importProjectOrElementsFromJson(
            jsonString = fullResult.jsonContent,
            fallbackProject = project.copy(elements = emptyList())
        )
        assertEquals("Reimported project name must match", project.name, reimportedProject.name)
        assertEquals("Reimported elements count must match", 3, reimportedProject.elements.size)
        assertEquals("First element ID must match", "base_sink_1", reimportedProject.elements[0].id)

        // 3. Test Elements-Only JSON Export & Import
        val elementsOnlyResult = com.example.core.export.JsonProjectExporter.serializeToJsonWithMetadata(
            project = project,
            options = com.example.core.export.JsonProjectExporter.JsonExportOptions(
                exportMode = com.example.core.export.JsonProjectExporter.JsonExportMode.ELEMENTS_ONLY
            )
        )
        assertEquals("Elements-only mode must export 3 elements", 3, elementsOnlyResult.exportedElementsCount)
        assertEquals("Elements-only mode must export 0 walls", 0, elementsOnlyResult.exportedWallsCount)

        val reimportedFromElements = com.example.core.export.JsonProjectExporter.importProjectOrElementsFromJson(
            jsonString = elementsOnlyResult.jsonContent,
            fallbackProject = project.copy(elements = emptyList())
        )
        assertEquals("Reimported from elements-only JSON must restore 3 elements", 3, reimportedFromElements.elements.size)
    }
}
