package com.example

import com.example.core.command.CommandHistory
import com.example.core.command.TransformCommand
import com.example.core.geometry.CollisionDetector
import com.example.core.geometry.DistanceCalculator
import com.example.core.geometry.SmartSnapper
import com.example.core.geometry.WallGeometryCutter
import com.example.core.math.Vector3
import com.example.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class KitchenStudioEngineTest {

    @Test
    fun testTransformCommand_UndoRedo() {
        val history = CommandHistory()
        var currentPos = Vector3(100f, 0f, 100f)
        val initialPos = currentPos
        val targetPos = Vector3(500f, 0f, 300f)

        val cmd = TransformCommand(
            elementId = "elem_1",
            oldPosition = initialPos,
            newPosition = targetPos,
            oldRotationY = 0f,
            newRotationY = 90f,
            oldDimensions = Vector3(600f, 850f, 600f),
            newDimensions = Vector3(600f, 850f, 600f),
            applyTransform = { _, pos, _, _ -> currentPos = pos }
        )

        history.execute(cmd)
        assertEquals(targetPos, currentPos)
        assertTrue(history.state.value.canUndo)

        // Undo
        history.undo()
        assertEquals(initialPos, currentPos)
        assertTrue(history.state.value.canRedo)

        // Redo
        history.redo()
        assertEquals(targetPos, currentPos)
    }

    @Test
    fun testWallDynamicOpenings_Decomposition() {
        val wall = Wall(
            id = "wall_north",
            side = WallSide.NORTH,
            lengthMm = 4000f,
            heightMm = 2700f,
            thicknessMm = 150f,
            openings = listOf(
                WallOpening(
                    id = "win_1",
                    type = OpeningType.WINDOW,
                    offsetMm = 1000f,
                    widthMm = 1200f,
                    heightMm = 1100f,
                    sillHeightMm = 900f
                )
            )
        )

        val blocks = WallGeometryCutter.decomposeWall(wall)
        // Expected blocks:
        // 1. Solid left wall (offset 0 to 1000, height 2700)
        // 2. Below sill block (offset 1000 to 2200, height 900)
        // 3. Above lintel block (offset 1000 to 2200, bottom 2000, top 2700)
        // 4. Solid right wall (offset 2200 to 4000, height 2700)
        assertEquals(4, blocks.size)

        val leftBlock = blocks[0]
        assertEquals(0f, leftBlock.startOffsetMm, 0.001f)
        assertEquals(1000f, leftBlock.lengthMm, 0.001f)
        assertEquals(2700f, leftBlock.heightMm, 0.001f)

        val rightBlock = blocks[3]
        assertEquals(2200f, rightBlock.startOffsetMm, 0.001f)
        assertEquals(1800f, rightBlock.lengthMm, 0.001f)
    }

    @Test
    fun testSmartSnapping_WallMagneticPull() {
        val room = RoomStructure(widthMm = 4000f, depthMm = 3000f, heightMm = 2600f)
        // Cabinet near North wall (depth = 600, center.z = halfDepth = 300 when flush)
        // If center.z is 340 (distance to wall = 40mm <= 80mm threshold), it should snap flush to z = 300!
        val nearNorthCabinet = KitchenElement(
            id = "cab_1",
            name = "خزانة",
            type = KitchenElementType.BASE_SINGLE_DOOR,
            position = Vector3(1000f, 0f, 340f),
            dimensions = Vector3(600f, 850f, 600f)
        )

        val snapResult = SmartSnapper.snap(
            target = nearNorthCabinet,
            existingElements = emptyList(),
            room = room,
            snapThresholdMm = 80f
        )

        assertTrue(snapResult.isSnappedToWall)
        assertEquals(300f, snapResult.position.z, 1.0f) // 1mm precision flush alignment!
    }

    @Test
    fun testCollisionDetector_SAT_Overlap() {
        val room = RoomStructure(widthMm = 4000f, depthMm = 3000f, heightMm = 2600f)
        val elem1 = KitchenElement(
            id = "cab_1",
            name = "خزانة 1",
            type = KitchenElementType.BASE_SINGLE_DOOR,
            position = Vector3(1000f, 0f, 300f),
            dimensions = Vector3(600f, 850f, 600f)
        )
        // elem2 overlaps elem1 directly
        val elem2 = KitchenElement(
            id = "cab_2",
            name = "خزانة 2",
            type = KitchenElementType.BASE_SINGLE_DOOR,
            position = Vector3(1100f, 0f, 300f),
            dimensions = Vector3(600f, 850f, 600f)
        )

        val collision = CollisionDetector.checkCollision(elem2, listOf(elem1), room)
        assertTrue(collision.hasCollision)
        assertTrue(collision.collidingElementIds.contains("cab_1"))
    }

    @Test
    fun testDistanceCalculator_Clearances() {
        val room = RoomStructure(widthMm = 4000f, depthMm = 3000f, heightMm = 2600f)
        val elem = KitchenElement(
            id = "cab_1",
            name = "خزانة",
            type = KitchenElementType.BASE_SINGLE_DOOR,
            position = Vector3(1000f, 0f, 300f),
            dimensions = Vector3(600f, 850f, 600f)
        )

        val clearances = DistanceCalculator.calculateDistancesToWalls(elem, room)
        // Flush to North wall (dist = 0)
        assertEquals(0f, clearances.distToNorthMm, 0.001f)
        // West clearance: center 1000 - halfWidth 300 = 700mm
        assertEquals(700f, clearances.distToWestMm, 0.001f)
    }

    @Test
    fun testKitchenGroup_CompoundAABB() {
        val elem1 = KitchenElement(
            id = "cab_1",
            name = "خزانة 1",
            type = KitchenElementType.BASE_SINGLE_DOOR,
            position = Vector3(1000f, 0f, 300f),
            dimensions = Vector3(600f, 850f, 600f)
        )
        val elem2 = KitchenElement(
            id = "cab_2",
            name = "خزانة 2",
            type = KitchenElementType.BASE_SINGLE_DOOR,
            position = Vector3(1600f, 0f, 300f),
            dimensions = Vector3(600f, 850f, 600f)
        )

        val group = KitchenGroup(
            id = "group_1",
            name = "مجموعة",
            elementIds = listOf("cab_1", "cab_2")
        )

        val compoundBox = group.calculateCompoundAABB(listOf(elem1, elem2))
        // Extents: width should be (1600+300) - (1000-300) = 1900 - 700 = 1200mm
        assertEquals(1200f, compoundBox.extents.x, 0.001f)
    }

    @Test
    fun testObjParser_TriangulationAndScaling() {
        // Mock simple 3D triangle in meters
        val objContent = """
            # Test Wavefront OBJ
            v 0.0 0.0 0.0
            v 0.6 0.0 0.0
            v 0.6 0.85 0.0
            v 0.0 0.85 0.0
            vn 0.0 0.0 1.0
            f 1//1 2//1 3//1 4//1
        """.trimIndent()

        val stream = objContent.byteInputStream()
        val mesh = com.example.core.importer.ObjParser.parse(stream, "TestCabinet", autoScaleToMillimeters = true)

        // Quad 1-2-3-4 triangulated into 2 triangles -> 6 indices
        assertEquals(6, mesh.indices.size)
        assertEquals(2, mesh.triangleCount)
        // Auto-scaled from meters to mm: 0.6m -> 600mm, 0.85m -> 850mm
        assertEquals(600f, mesh.aabb.extents.x, 1.0f)
        assertEquals(850f, mesh.aabb.extents.y, 1.0f)
    }

    @Test
    fun testDxfParser_LinesAndBounds() {
        val dxfContent = """
            0
            SECTION
            2
            ENTITIES
            0
            LINE
            8
            WALLS
            10
            0.0
            20
            0.0
            30
            0.0
            11
            4000.0
            21
            0.0
            31
            0.0
            0
            ENDSEC
            0
            EOF
        """.trimIndent()

        val stream = dxfContent.byteInputStream()
        val drawing = com.example.core.importer.DxfParser.parse(stream, "Floorplan.dxf", autoScaleToMillimeters = false)

        assertEquals(1, drawing.lines.size)
        assertEquals("WALLS", drawing.lines[0].layer)
        assertEquals(4000f, drawing.bounds.extents.x, 0.001f)
    }

    @Test
    fun testSketchTo3D_OrthogonalSnap() {
        // Draw slightly slanted line from (100, 100) to (495, 105) (nearly horizontal ~0.7°)
        val start = androidx.compose.ui.geometry.Offset(100f, 100f)
        val rawEnd = androidx.compose.ui.geometry.Offset(495f, 105f)

        val snappedEnd = com.example.core.sketch.SketchTo3DEngine.snapToOrthogonal(start, rawEnd)
        // Snapped to exact horizontal (Y = 100)
        assertEquals(100f, snappedEnd.y, 0.001f)
        assertTrue(snappedEnd.x > 490f)
    }

    @Test
    fun testPerspectiveCalibrator_HorizonAndCamera() {
        val quad = com.example.core.perspective.FloorCalibrationQuad(
            frontLeft = androidx.compose.ui.geometry.Offset(100f, 800f),
            frontRight = androidx.compose.ui.geometry.Offset(900f, 800f),
            backRight = androidx.compose.ui.geometry.Offset(700f, 400f),
            backLeft = androidx.compose.ui.geometry.Offset(300f, 400f)
        )

        val cam = com.example.core.perspective.PerspectiveCalibrator.calibrateFromFloorQuad(
            quad = quad,
            imageWidth = 1000f,
            imageHeight = 1000f
        )

        assertTrue(cam.cameraPosition.y > 500f) // Camera is elevated above floor
        assertTrue(cam.pitchDegrees > 0f) // Looking downward at floor
        // Test projection of point on floor
        val projected = cam.project3DToImage(com.example.core.math.Vector3(1500f, 0f, 1500f))
        assertNotNull(projected)
    }

    @Test
    fun testParametricCabinet_CarcassDecomposition() {
        val cabinet = com.example.core.cam.ParametricCabinet(
            id = "cab_1",
            name = "خزانة اختبار 60 سم",
            widthMm = 600f,
            heightMm = 850f,
            depthMm = 600f,
            panelThicknessMm = 18f,
            numberOfShelves = 1,
            numberOfDrawers = 0
        )

        // Internal width must be: 600 - (18 * 2) = 564 mm
        assertEquals(564f, cabinet.internalWidthMm, 0.001f)
        // Internal height: 850 - 100(plinth) - 36(top/bottom) = 714 mm
        assertEquals(714f, cabinet.internalHeightMm, 0.001f)

        val cutParts = cabinet.generateCarcassCutParts()
        // Must contain side gables, bottom deck, top stretchers, back panel, shelf, door, toe kick
        assertTrue(cutParts.size >= 6)

        val sideGable = cutParts.find { it.partName.contains("جنب خزانة") }
        assertNotNull(sideGable)
        assertEquals(750f, sideGable!!.lengthMm, 0.001f) // 850 - 100mm plinth
        assertEquals(600f, sideGable.widthMm, 0.001f) // depth
        assertEquals(2, sideGable.quantity)

        val shelf = cutParts.find { it.partName.contains("رف داخلي") }
        assertNotNull(shelf)
        // Shelf width = 564 - 2mm clearance = 562 mm
        assertEquals(562f, shelf!!.widthMm, 0.001f)
    }

    @Test
    fun testNestingEngine_2DGuillotinePacking() {
        // 4 cabinet side panels: 750mm x 600mm each
        val parts = listOf(
            com.example.core.cam.CutPart(
                partName = "جنب خزانة",
                widthMm = 600f,
                lengthMm = 750f,
                thicknessMm = 18f,
                quantity = 4,
                material = com.example.core.cam.PanelMaterial.MDF_18MM
            )
        )

        val packedSheets = com.example.core.cam.NestingEngine.packPanels(
            parts = parts,
            sheetWidthMm = 2440f,
            sheetLengthMm = 1220f,
            bladeKerfMm = 3.2f,
            sheetTrimMm = 10f
        )

        // All 4 panels (each 750x600) easily fit on one 2440x1220mm sheet:
        // (4 panels in a row: 4 * 600 = 2400 <= 2420, height 750 <= 1200)
        assertEquals(1, packedSheets.size)
        val sheet = packedSheets[0]
        assertEquals(4, sheet.placements.size)

        // Area math verification:
        val expectedUsedArea = 4f * (600f * 750f) // 1,800,000 sq mm
        assertEquals(expectedUsedArea, sheet.usedAreaSqMm, 0.001f)
        val rawArea = 2440f * 1220f // 2,976,800 sq mm
        val expectedUtilization = (expectedUsedArea / rawArea) * 100f
        assertEquals(expectedUtilization, sheet.utilizationPercentage, 0.1f)
        val expectedWaste = 100f - expectedUtilization
        assertEquals(expectedWaste, sheet.wastePercentage, 0.1f)
    }

    @Test
    fun testNestingEngine_1DAluminumBarOptimizer() {
        val cuts = listOf(
            com.example.core.cam.AluminumProfileCut("غولا 1", 1200f, quantity = 3), // 3600 mm
            com.example.core.cam.AluminumProfileCut("غولا 2", 900f, quantity = 2)   // 1800 mm
            // Total = 5400 mm + kerf (4 * 5 = 20) = 5420 mm <= 6000 mm bar!
        )

        val packedBars = com.example.core.cam.NestingEngine.packAluminumProfiles(
            cuts = cuts,
            barLengthMm = 6000f,
            kerfMm = 4.0f
        )

        // Fits on a single 6-meter bar
        assertEquals(1, packedBars.size)
        val bar = packedBars[0]
        assertEquals(5, bar.cuts.size)
        assertTrue(bar.usedLengthMm <= 6000f)
        assertTrue(bar.remainingWasteMm > 0f)
    }

    @Test
    fun testPbrMaterials_CookTorranceMathAndRefraction() {
        val marble = com.example.core.filament.PbrMaterial.POLISHED_CARRARA_MARBLE
        assertEquals(0.08f, marble.roughness, 0.001f)
        assertEquals(1.0f, marble.clearcoat, 0.001f)
        assertEquals(0.0f, marble.metallic, 0.001f)

        // F0 computation for dielectric
        val f0Dielectric = com.example.core.filament.PbrShadingMath.computeF0(
            baseColor = marble.baseColor,
            metallic = marble.metallic,
            reflectance = marble.reflectance
        )
        // 0.16 * 0.65^2 = 0.0676
        assertEquals(0.0676f, f0Dielectric.x, 0.005f)

        // GGX distribution
        val d = com.example.core.filament.PbrShadingMath.distributionGgx(nDotH = 1.0f, roughness = 0.5f)
        assertTrue(d > 0f)

        // Snell's Law refraction for glass (IOR = 1.52)
        val incident = com.example.core.math.Vector3(0f, -1f, 1f).normalized()
        val normal = com.example.core.math.Vector3(0f, 1f, 0f)
        val refracted = com.example.core.filament.PbrShadingMath.refract(incident, normal, eta = 1.0f / 1.52f)
        assertNotNull(refracted)
        assertTrue(refracted!!.y < 0f) // Continues downward into medium
    }

    @Test
    fun testDeviceProfiler_QualityTierAssignment() {
        // High quality settings check
        val highSettings = com.example.core.filament.DeviceProfiler.getSettings(com.example.core.filament.QualityLevel.HIGH)
        assertTrue(highSettings.enableCookTorranceGGX)
        assertTrue(highSettings.enableClearcoatLobe)
        assertTrue(highSettings.enableTransmissionRefraction)
        assertEquals(2048, highSettings.shadowMapResolution)

        // Low quality settings check
        val lowSettings = com.example.core.filament.DeviceProfiler.getSettings(com.example.core.filament.QualityLevel.LOW)
        assertFalse(lowSettings.enableCookTorranceGGX)
        assertFalse(lowSettings.enableTransmissionRefraction)
        assertEquals(512, lowSettings.shadowMapResolution)
    }

    @Test
    fun testLightingEngine_SunLightAndLedStrips() {
        val sun = com.example.core.filament.DirectionalSunLight(
            azimuthDegrees = 90f, // East
            elevationDegrees = 45f,
            colorTemperatureKelvin = 5500f,
            illuminanceLux = 50000f
        )
        val dir = sun.directionToLight
        assertTrue(dir.x > 0f) // East positive X
        assertTrue(dir.y > 0f) // Above horizon positive Y

        val kelvinColor = com.example.core.filament.ColorTemperatureConverter.kelvinToRgb(3000f)
        // Warm 3000K should have more red than blue
        assertTrue(kelvinColor.x > kelvinColor.z)

        // Under-Cabinet LED generator
        val wallCabinet = com.example.domain.model.KitchenElement(
            id = "cab_upper_1",
            name = "خزانة علوية",
            type = com.example.domain.model.KitchenElementType.WALL_DOUBLE_DOOR,
            position = com.example.core.math.Vector3(1000f, 1500f, 600f), // elevated
            dimensions = com.example.core.math.Vector3(900f, 750f, 350f)
        )
        val ledStrips = com.example.core.filament.UnderCabinetLedGenerator.generateLedStrips(
            elements = listOf(wallCabinet),
            colorTempKelvin = 3200f
        )
        assertTrue(ledStrips.isNotEmpty())
        assertEquals(3200f, ledStrips[0].colorTemperatureKelvin, 0.01f)
        // Direction points downward onto countertop
        assertEquals(-1.0f, ledStrips[0].direction.y, 0.01f)
    }

    @Test
    fun testCameraExposure_Ev100AndAcesToneMapping() {
        val exposure = com.example.core.filament.CameraExposure(
            apertureFNumber = 4.0f,
            shutterSpeedSeconds = 1.0f / 125.0f,
            iso = 100f,
            useAcesToneMapping = true
        )
        // EV100 = log2(16 / (1/125)) - log2(1) = log2(2000) approx 10.965
        assertEquals(10.965f, exposure.ev100, 0.1f)
        assertTrue(exposure.exposureMultiplier > 0f)

        // ACES Tone mapping maps HDR values into [0..1]
        val hdrColor = com.example.core.math.Vector3(2.5f, 5.0f, 10.0f)
        val ldrColor = exposure.toneMapAces(hdrColor)
        assertTrue(ldrColor.x in 0f..1f)
        assertTrue(ldrColor.y in 0f..1f)
        assertTrue(ldrColor.z in 0f..1f)
    }

    // ==========================================
    // CAMERA NAVIGATION & UX TESTS
    // ==========================================

    @Test
    fun testCameraInertia_DecayAndOrbit() {
        val controller = com.example.core.camera.CameraNavigationController()
        controller.currentPose = com.example.core.camera.CameraPose(
            yawDegrees = 30f,
            pitchDegrees = 30f
        )

        // 1-finger drag adds momentum
        controller.onOrbitDrag(deltaX = 20f, deltaY = 10f)
        val yawAfterDrag = controller.currentPose.yawDegrees
        val pitchAfterDrag = controller.currentPose.pitchDegrees

        // Ticking inertia should continue rotating smoothly
        val hadMotion = controller.tickInertia()
        assertTrue(hadMotion)
        assertNotEquals(yawAfterDrag, controller.currentPose.yawDegrees)

        // After sufficient ticks, inertia velocity decays to zero
        for (i in 0..80) {
            controller.tickInertia()
        }
        val finalHadMotion = controller.tickInertia()
        assertFalse(finalHadMotion)
    }

    @Test
    fun testWalkthroughCollision_WallBoundariesAndEyeHeight() {
        val room = RoomStructure(widthMm = 4000f, depthMm = 3000f, heightMm = 2800f)
        val elements = emptyList<KitchenElement>()

        // 1. Candidate position trying to penetrate west wall (x = -500mm)
        val candidatePos = Vector3(x = -500f, y = 1600f, z = 1500f)
        val result = com.example.core.camera.WalkthroughCollisionEngine.resolveMovement(
            currentPos = Vector3(1000f, 1600f, 1500f),
            candidatePos = candidatePos,
            room = room,
            elements = elements,
            bodyRadiusMm = 280f,
            eyeHeightMm = 1600f
        )

        assertTrue(result.hasCollided)
        assertEquals(1600f, result.resolvedPosition.y, 0.01f) // Fixed eye level
        val minAllowedX = 280f + com.example.core.camera.WalkthroughCollisionEngine.MIN_WALL_MARGIN_MM
        assertEquals(minAllowedX, result.resolvedPosition.x, 0.01f)
    }

    @Test
    fun testWalkthroughCollision_CabinetObstacleSliding() {
        val room = RoomStructure(widthMm = 5000f, depthMm = 5000f, heightMm = 2800f)
        // Cabinet at (x = 2000, z = 2000, width = 1000, depth = 600, height = 850)
        val cabinet = KitchenElement(
            id = "test_cab",
            name = "خزانة أرضية 100سم",
            type = KitchenElementType.BASE_DOUBLE_DOOR,
            position = Vector3(2000f, 0f, 2000f),
            dimensions = Vector3(1000f, 850f, 600f)
        )

        // Try walking directly into the center of the cabinet from the front (z = 2100)
        val candidateInside = Vector3(x = 2500f, y = 1600f, z = 2300f)
        val result = com.example.core.camera.WalkthroughCollisionEngine.resolveMovement(
            currentPos = Vector3(2500f, 1600f, 3500f),
            candidatePos = candidateInside,
            room = room,
            elements = listOf(cabinet),
            bodyRadiusMm = 280f,
            eyeHeightMm = 1600f
        )

        assertTrue(result.hasCollided)
        assertEquals(cabinet.name, result.collidedObstacleName)
        // Position must be pushed outside the cabinet
        val resolvedZ = result.resolvedPosition.z
        assertTrue("Camera must be pushed outside cabinet: $resolvedZ", resolvedZ > 2600f || resolvedZ < 2000f)
        assertEquals(1600f, result.resolvedPosition.y, 0.01f)
    }

    @Test
    fun testCameraTweening_EasingAndShortestAngleLerp() {
        // Cubic Ease-In-Out
        assertEquals(0f, com.example.core.camera.CameraTweenEngine.easeInOutCubic(0f), 0.001f)
        assertEquals(0.5f, com.example.core.camera.CameraTweenEngine.easeInOutCubic(0.5f), 0.001f)
        assertEquals(1f, com.example.core.camera.CameraTweenEngine.easeInOutCubic(1f), 0.001f)

        // Shortest arc angle lerp: from 350° to 10° halfway should be 0° (360°/0°)
        val midAngle = com.example.core.camera.CameraTweenEngine.lerpAngleDegrees(350f, 10f, 0.5f)
        assertEquals(0f, midAngle, 0.01f)

        // Preset Pose Calculation for Top View
        val room = RoomStructure(widthMm = 4000f, depthMm = 3000f, heightMm = 2800f)
        val topPose = com.example.core.camera.CameraTweenEngine.computePresetPose(
            preset = com.example.core.camera.CameraNavigationPreset.TOP_VIEW,
            room = room
        )
        assertEquals(com.example.core.camera.CameraMode.TOP_DOWN, topPose.mode)
        assertEquals(89.5f, topPose.pitchDegrees, 0.1f)
        assertEquals(0f, topPose.yawDegrees, 0.1f)
    }

    @Test
    fun testCabinetAssetLibrary_ModernClassicAndGlassDoorProceduralMeshes() {
        val allPresets = com.example.core.filament.CabinetAssetLibrary.getPresetCatalog()
        assertTrue("Expected at least 18 comprehensive presets", allPresets.size >= 18)

        val modernPresets = com.example.core.filament.CabinetAssetLibrary.getPresetsByArchetype(
            com.example.core.filament.CabinetStyleArchetype.MODERN_SLAB
        )
        val classicPresets = com.example.core.filament.CabinetAssetLibrary.getPresetsByArchetype(
            com.example.core.filament.CabinetStyleArchetype.CLASSIC_SHAKER
        )
        val glassPresets = com.example.core.filament.CabinetAssetLibrary.getPresetsByArchetype(
            com.example.core.filament.CabinetStyleArchetype.GLASS_VITRINE
        )

        assertTrue(modernPresets.isNotEmpty())
        assertTrue(classicPresets.isNotEmpty())
        assertTrue(glassPresets.isNotEmpty())

        // 1. Verify Modern Slab + Gola mesh generation
        val modernMesh = com.example.core.filament.CabinetAssetLibrary.generateFromPreset(modernPresets.first())
        assertTrue(modernMesh.subMeshCount >= 8)
        assertEquals(modernMesh.subMeshCount * 24, modernMesh.totalVertexCount)
        assertEquals(modernMesh.subMeshCount * 12, modernMesh.totalTriangleCount)
        assertTrue(modernMesh.subMeshes.any { it.role == com.example.core.filament.CabinetMeshPartRole.COUNTERTOP_SLAB })

        // 2. Verify Classic Shaker / Raised Panel stile & rail decomposition
        val classicMesh = com.example.core.filament.CabinetAssetLibrary.generateFromPreset(classicPresets.first())
        assertTrue(classicMesh.subMeshes.any { it.role == com.example.core.filament.CabinetMeshPartRole.CLASSIC_STILE_RAIL })
        assertTrue(classicMesh.subMeshes.any { it.role == com.example.core.filament.CabinetMeshPartRole.DOOR_SLAB })

        // 3. Verify Glass-Door Vitrine with aluminum frame, translucent glass pane, and warm 3000K LED strips
        val glassMesh = com.example.core.filament.CabinetAssetLibrary.generateFromPreset(glassPresets.first())
        assertTrue(glassMesh.subMeshes.any { it.role == com.example.core.filament.CabinetMeshPartRole.GLASS_FRAME_MULLION })
        assertTrue(glassMesh.subMeshes.any { it.role == com.example.core.filament.CabinetMeshPartRole.GLASS_PANE_TRANSLUCENT })
        assertTrue(glassMesh.subMeshes.any { it.role == com.example.core.filament.CabinetMeshPartRole.LED_LIGHT_STRIP })

        // 4. Verify combined buffer merge and world-space transformation
        val combined = glassMesh.mergeToCombinedFilamentMesh()
        assertEquals(glassMesh.totalVertexCount, combined.vertices.size)
        assertEquals(glassMesh.totalTriangleCount * 3, combined.indices.size)

        val worldMeshes = glassMesh.transformToWorldSpace(Vector3(1200f, 1450f, 300f), rotationYDegrees = 90f)
        assertEquals(glassMesh.subMeshCount, worldMeshes.size)
    }

    @Test
    fun testErgonomicValidationEngine_SinkTooFarFromStove() {
        val room = RoomStructure(widthMm = 5000f, depthMm = 4000f, heightMm = 2800f)

        val sink = KitchenElement(
            id = "sink_1",
            name = "حوض مطبخ 90 سم",
            type = KitchenElementType.BASE_SINK_UNIT,
            position = Vector3(600f, 0f, 300f),
            dimensions = Vector3(900f, 850f, 600f),
            rotationY = 0f
        )

        // Stove placed 3600 mm away from sink (> 2700 mm ergonomic limit)
        val distantStove = KitchenElement(
            id = "stove_1",
            name = "وحدة فرن وموقد",
            type = KitchenElementType.BASE_UNDER_OVEN,
            position = Vector3(4200f, 0f, 300f),
            dimensions = Vector3(600f, 850f, 600f),
            rotationY = 0f
        )

        val badReport = com.example.core.geometry.ErgonomicValidationEngine.validate(
            room = room,
            elements = listOf(sink, distantStove)
        )

        val sinkFarIssue = badReport.issues.find {
            it.code == com.example.core.geometry.ErgonomicRuleCode.SINK_TOO_FAR_FROM_STOVE
        }
        assertNotNull("Expected SINK_TOO_FAR_FROM_STOVE issue when distance > 2700mm", sinkFarIssue)
        assertTrue(sinkFarIssue!!.titleEn.contains("Sink too far from stove", ignoreCase = true))
        assertEquals(3600f, sinkFarIssue.measuredValueMm, 1.0f)

        // Move stove to ergonomic distance (1600 mm center-to-center)
        val optimalStove = distantStove.copy(position = Vector3(2200f, 0f, 300f))
        val goodReport = com.example.core.geometry.ErgonomicValidationEngine.validate(
            room = room,
            elements = listOf(sink, optimalStove)
        )
        assertFalse(
            goodReport.issues.any {
                it.code == com.example.core.geometry.ErgonomicRuleCode.SINK_TOO_FAR_FROM_STOVE
            }
        )
        assertTrue(goodReport.workTriangle?.isSinkStoveOptimal == true)
    }

    @Test
    fun testErgonomicValidationEngine_InsufficientClearanceForCabinetDoors() {
        val room = RoomStructure(widthMm = 4000f, depthMm = 3500f, heightMm = 2800f)

        // Base cabinet on North wall facing +Z (front face at z = 600 mm)
        val baseCabinet = KitchenElement(
            id = "cab_north",
            name = "خزانة سفلية 80 سم",
            type = KitchenElementType.BASE_DOUBLE_DOOR,
            position = Vector3(1500f, 0f, 300f),
            dimensions = Vector3(800f, 850f, 600f),
            rotationY = 0f
        )

        // Island placed too close in front of the cabinet doors:
        // Island center z = 1450, depth = 900 -> near face at z = 1000 mm.
        // Available clearance = 1000 - 600 = 400 mm (< 650 mm critical door swing & < 850 mm walkway)
        val tightIsland = KitchenElement(
            id = "island_tight",
            name = "جزيرة وسطية",
            type = KitchenElementType.ISLAND_COUNTER,
            position = Vector3(1500f, 0f, 1450f),
            dimensions = Vector3(1400f, 850f, 900f),
            rotationY = 180f
        )

        val report = com.example.core.geometry.ErgonomicValidationEngine.validate(
            room = room,
            elements = listOf(baseCabinet, tightIsland)
        )

        val clearanceIssue = report.issues.find {
            it.code == com.example.core.geometry.ErgonomicRuleCode.INSUFFICIENT_DOOR_CLEARANCE &&
                    "cab_north" in it.affectedElementIds
        }
        assertNotNull("Expected INSUFFICIENT_DOOR_CLEARANCE issue", clearanceIssue)
        assertTrue(
            clearanceIssue!!.titleEn.contains("Insufficient clearance for cabinet doors", ignoreCase = true)
        )
        assertEquals(400f, clearanceIssue.measuredValueMm, 1.0f)

        // Move island back so near face is at z = 1650 mm (clearance = 1050 mm >= 850 mm)
        val spaciousIsland = tightIsland.copy(position = Vector3(1500f, 0f, 2100f))
        val fixedReport = com.example.core.geometry.ErgonomicValidationEngine.validate(
            room = room,
            elements = listOf(baseCabinet, spaciousIsland)
        )
        assertFalse(
            fixedReport.issues.any {
                it.code == com.example.core.geometry.ErgonomicRuleCode.INSUFFICIENT_DOOR_CLEARANCE &&
                        "cab_north" in it.affectedElementIds
            }
        )
    }

    @Test
    fun testFloorPlan2DCommandPattern_PlaceFurnitureAndModifyLayoutUndoRedo() {
        val history = CommandHistory()
        var currentRoom = RoomStructure(widthMm = 4000f, depthMm = 3000f, heightMm = 2800f)
        val elements = mutableListOf<KitchenElement>()
        var selectedIds = emptySet<String>()

        // 1. Place furniture on 2D floor plan at (1200, 300) mm
        val sinkCabinet = KitchenElement(
            id = "cab_sink_2d",
            name = "خزانة حوض 90 سم",
            type = KitchenElementType.BASE_SINK_UNIT,
            position = Vector3(1200f, 0f, 300f),
            dimensions = Vector3(900f, 850f, 600f)
        )
        val placeCmd = com.example.core.command.PlaceFurniture2DCommand(
            element = sinkCabinet,
            previousSelectedIds = selectedIds,
            onPlace = { elem ->
                elements.add(elem)
                selectedIds = setOf(elem.id)
            },
            onRevertPlacement = { id, prevSel ->
                elements.removeAll { it.id == id }
                selectedIds = prevSel
            }
        )
        history.execute(placeCmd)
        assertEquals(1, elements.size)
        assertEquals(setOf("cab_sink_2d"), selectedIds)
        assertEquals(1, history.state.value.undoCount)

        // 2. Drag / transform furniture on 2D floor plan to (1850, 300) mm
        val dragCmd = TransformCommand(
            elementId = "cab_sink_2d",
            oldPosition = Vector3(1200f, 0f, 300f),
            newPosition = Vector3(1850f, 0f, 300f),
            oldRotationY = 0f,
            newRotationY = 90f,
            oldDimensions = sinkCabinet.dimensions,
            newDimensions = sinkCabinet.dimensions,
            applyTransform = { id, pos, rot, dims ->
                val idx = elements.indexOfFirst { it.id == id }
                if (idx >= 0) {
                    elements[idx] = elements[idx].copy(position = pos, rotationY = rot, dimensions = dims)
                }
            },
            elementName = sinkCabinet.name
        )
        history.execute(dragCmd)
        assertEquals(1850f, elements.first().position.x, 0.001f)
        assertEquals(90f, elements.first().rotationY, 0.001f)

        // 3. Modify 2D Room Layout dimensions and shape (4000x3000 -> 4500x3500 L-Shape)
        val newRoom = currentRoom
            .updateDimensions(4500f, 3500f, 2800f)
            .applyLayoutShape(RoomLayoutShape.L_SHAPE)
        val layoutCmd = com.example.core.command.ModifyRoomLayoutCommand(
            oldRoom = currentRoom,
            newRoom = newRoom,
            onApplyRoom = { updated -> currentRoom = updated }
        )
        history.execute(layoutCmd)
        assertEquals(4500f, currentRoom.widthMm, 0.001f)
        assertEquals(RoomLayoutShape.L_SHAPE, currentRoom.layoutShape)
        assertEquals(3, history.state.value.undoCount)

        // 4. Multi-step Undo: revert 2 steps (layout modification + 2D drag) in one call
        val undoneSteps = history.undoSteps(2)
        assertEquals(2, undoneSteps)
        assertEquals(4000f, currentRoom.widthMm, 0.001f)
        assertEquals(1200f, elements.first().position.x, 0.001f)
        assertEquals(0f, elements.first().rotationY, 0.001f)
        assertEquals(1, history.state.value.undoCount)
        assertEquals(2, history.state.value.redoCount)

        // 5. Undo final step (furniture placement)
        assertTrue(history.undo())
        assertTrue(elements.isEmpty())
        assertTrue(selectedIds.isEmpty())
        assertFalse(history.state.value.canUndo)
        assertEquals(3, history.state.value.redoCount)

        // 6. Multi-step Redo all 3 steps back
        val redoneSteps = history.redoSteps(3)
        assertEquals(3, redoneSteps)
        assertEquals(1, elements.size)
        assertEquals(1850f, elements.first().position.x, 0.001f)
        assertEquals(4500f, currentRoom.widthMm, 0.001f)
        assertEquals(RoomLayoutShape.L_SHAPE, currentRoom.layoutShape)
    }

    // =========================================================================
    // AUTOMATIC CABINET FILLER & SPACER ENGINE (1MM SNAPPING GEOMETRY) TESTS
    // =========================================================================

    @Test
    fun testAutomaticFillerEngine_SymmetricBothEnds_1mmPrecision() {
        val room = RoomStructure(widthMm = 3743f, depthMm = 3000f, heightMm = 2800f)
        // Place 4 base cabinets along NORTH wall: 900 + 800 + 900 + 900 = 3500mm total
        // Remaining gap on 3743mm wall = 243mm -> Symmetric fillers: 121mm on left, 122mm on right (sum = 243mm!)
        val c1 = KitchenElement(
            id = "cab_1",
            name = "خزانة 1",
            type = KitchenElementType.BASE_DOUBLE_DOOR,
            position = Vector3(500f, 0f, 300f),
            dimensions = Vector3(900f, 850f, 600f)
        )
        val c2 = KitchenElement(
            id = "cab_2",
            name = "خزانة 2",
            type = KitchenElementType.BASE_DOUBLE_DOOR,
            position = Vector3(1400f, 0f, 300f),
            dimensions = Vector3(800f, 850f, 600f)
        )
        val c3 = KitchenElement(
            id = "cab_3",
            name = "خزانة 3",
            type = KitchenElementType.BASE_SINK_UNIT,
            position = Vector3(2300f, 0f, 300f),
            dimensions = Vector3(900f, 850f, 600f)
        )
        val c4 = KitchenElement(
            id = "cab_4",
            name = "خزانة 4",
            type = KitchenElementType.BASE_3_DRAWERS_90,
            position = Vector3(3200f, 0f, 300f),
            dimensions = Vector3(900f, 850f, 600f)
        )

        val config = com.example.core.geometry.AutomaticFillerEngine.FillerRequestConfig(
            wallSide = WallSide.NORTH,
            userWallLengthMm = 3743f,
            targetTier = com.example.core.geometry.AutomaticFillerEngine.FillerTargetTier.BASE_ROW,
            strategy = com.example.core.geometry.AutomaticFillerEngine.FillerDistributionStrategy.SYMMETRIC_BOTH_ENDS,
            minFillerWidthMm = 10f,
            maxSingleFillerWidthMm = 250f,
            splitWideGapsIntoModularStrips = true
        )

        val result = com.example.core.geometry.AutomaticFillerEngine.computeFillerLayout(
            elements = listOf(c1, c2, c3, c4),
            room = room,
            config = config
        )

        val baseAnalysis = result.tierAnalyses.first()
        assertEquals(3743f, baseAnalysis.userWallLengthMm, 0.001f)
        assertEquals(3500f, baseAnalysis.totalCabinetsWidthMm, 0.001f)
        assertEquals(243f, baseAnalysis.remainingGapMm, 0.001f)
        assertEquals(243f, result.totalGapFilledMm, 0.001f)
        assertEquals(2, result.addedFillers.size)

        // Verify 1mm snapping of both generated filler strips
        val leftFiller = result.addedFillers.first()
        val rightFiller = result.addedFillers.last()
        assertEquals(243f, leftFiller.dimensions.x + rightFiller.dimensions.x, 0.001f)
        assertTrue(leftFiller.dimensions.x == 121f || leftFiller.dimensions.x == 122f)
        assertTrue(rightFiller.dimensions.x == 121f || rightFiller.dimensions.x == 122f)
    }

    @Test
    fun testAutomaticFillerEngine_EvenSpacersBetweenCabinets_1mmPrecision() {
        val room = RoomStructure(widthMm = 3000f, depthMm = 3000f, heightMm = 2800f)
        // 3 base cabinets of 900mm each = 2700mm on a user-defined 2855mm wall -> 155mm gap
        // With 3 cabinets, there are 4 spacer slots (left wall, between 1-2, between 2-3, right wall)
        // 155mm / 4 = 38.75mm -> integer 1mm distribution summing to 155mm
        val c1 = KitchenElement(
            id = "cab_a",
            name = "خزانة A",
            type = KitchenElementType.BASE_DOUBLE_DOOR,
            position = Vector3(450f, 0f, 300f),
            dimensions = Vector3(900f, 850f, 600f)
        )
        val c2 = KitchenElement(
            id = "cab_b",
            name = "خزانة B",
            type = KitchenElementType.BASE_DOUBLE_DOOR,
            position = Vector3(1350f, 0f, 300f),
            dimensions = Vector3(900f, 850f, 600f)
        )
        val c3 = KitchenElement(
            id = "cab_c",
            name = "خزانة C",
            type = KitchenElementType.BASE_DOUBLE_DOOR,
            position = Vector3(2250f, 0f, 300f),
            dimensions = Vector3(900f, 850f, 600f)
        )

        val config = com.example.core.geometry.AutomaticFillerEngine.FillerRequestConfig(
            wallSide = WallSide.NORTH,
            userWallLengthMm = 2855f,
            targetTier = com.example.core.geometry.AutomaticFillerEngine.FillerTargetTier.BASE_ROW,
            strategy = com.example.core.geometry.AutomaticFillerEngine.FillerDistributionStrategy.EVEN_SPACERS_BETWEEN_CABINETS,
            minFillerWidthMm = 10f,
            maxSingleFillerWidthMm = 200f
        )

        val result = com.example.core.geometry.AutomaticFillerEngine.computeFillerLayout(
            elements = listOf(c1, c2, c3),
            room = room,
            config = config
        )

        val baseAnalysis = result.tierAnalyses.first()
        assertEquals(2855f, baseAnalysis.userWallLengthMm, 0.001f)
        assertEquals(2700f, baseAnalysis.totalCabinetsWidthMm, 0.001f)
        assertEquals(155f, result.totalGapFilledMm, 0.001f)
        assertEquals(4, result.addedFillers.size)
        val sumWidths = result.addedFillers.sumOf { it.dimensions.x.toDouble() }.toFloat()
        assertEquals(155f, sumWidths, 0.001f)
    }

    @Test
    fun testModelImporter_NormalizesMeterAndCmModelsForSmoothMovement() {
        // Create a raw mesh authored in meters (0.8m x 0.85m x 0.6m) without prior scaling
        val objInMeters = """
            v 0.0 0.0 0.0
            v 0.8 0.0 0.0
            v 0.8 0.85 0.6
            v 0.0 0.85 0.6
            f 1 2 3 4
        """.trimIndent()

        val rawMesh = com.example.core.importer.ObjParser.parse(
            inputStream = objInMeters.byteInputStream(),
            modelName = "ImportedMeterCabinet",
            autoScaleToMillimeters = false
        )
        assertEquals(0.8f, rawMesh.aabb.extents.x, 0.01f)

        val element = com.example.core.importer.ModelImporter.createKitchenElementFromMesh(
            mesh = rawMesh,
            initialPosition = Vector3(1500f, 0f, 300f)
        )

        // Must be normalized to millimeters (800mm x 850mm x 600mm) so it never gets stuck or oversized
        assertEquals(800f, element.dimensions.x, 1.0f)
        assertEquals(850f, element.dimensions.y, 1.0f)
        assertEquals(600f, element.dimensions.z, 1.0f)
    }

    @Test
    fun testCameraObjectScanner_ImportViaCameraTo3DAndJson() {
        val scanAnalysis = com.example.core.importer.CameraObjectScanner.analyzeCapturedBitmap(
            bitmap = null,
            presetCategory = com.example.core.importer.CameraObjectScanner.CameraImportPresetCategory.WALL_CABINET,
            customNameHint = "خزانة علوية ممسوحة بالكاميرا"
        )

        assertEquals("خزانة علوية ممسوحة بالكاميرا", scanAnalysis.element.name)
        assertEquals(KitchenElementType.WALL_GLASS_BLACK_FRAME, scanAnalysis.element.type)
        assertEquals(720f, scanAnalysis.element.dimensions.y, 0.001f)
        assertEquals(350f, scanAnalysis.element.dimensions.z, 0.001f)
        assertEquals(8, scanAnalysis.generatedMesh.vertexCount)
        assertEquals(12, scanAnalysis.generatedMesh.triangleCount)

        // Verify conversion of the camera-scanned 3D mesh into a movable KitchenElement
        val convertedFromMesh = com.example.core.importer.ModelImporter.createKitchenElementFromMesh(
            mesh = scanAnalysis.generatedMesh,
            initialPosition = scanAnalysis.element.position
        )
        assertEquals(scanAnalysis.element.dimensions.x, convertedFromMesh.dimensions.x, 1.0f)
        assertEquals(720f, convertedFromMesh.dimensions.y, 1.0f)
        assertEquals(350f, convertedFromMesh.dimensions.z, 1.0f)
    }
}




