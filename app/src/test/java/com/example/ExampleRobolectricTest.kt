package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.KitchenProjectRepositoryImpl
import com.example.presentation.viewmodel.KitchenEditorViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Kitchen3D", appName)
    }

    @Test
    fun `floating toolbar mode toggles update editor state`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = KitchenEditorViewModel(
            application = app,
            repository = KitchenProjectRepositoryImpl(dao = null)
        )

        // Initial states
        assertFalse(viewModel.uiState.value.isWallCreationMode)
        assertFalse(viewModel.uiState.value.isFurniturePlacementMode)
        assertTrue(viewModel.uiState.value.isSmartSnappingEnabled)

        // Toggle Wall Creation ON
        viewModel.toggleWallCreationMode()
        assertTrue(viewModel.uiState.value.isWallCreationMode)
        assertFalse(viewModel.uiState.value.isFurniturePlacementMode)

        // Toggle Furniture Placement ON (automatically turns off Wall Creation)
        viewModel.toggleFurniturePlacementMode()
        assertFalse(viewModel.uiState.value.isWallCreationMode)
        assertTrue(viewModel.uiState.value.isFurniturePlacementMode)

        // Toggle Dimension Snapping OFF and back ON
        viewModel.toggleSmartSnapping()
        assertFalse(viewModel.uiState.value.isSmartSnappingEnabled)
        viewModel.toggleSmartSnapping()
        assertTrue(viewModel.uiState.value.isSmartSnappingEnabled)
    }

    @Test
    fun `autoSaveManager serializes room metadata and furniture positions into Room database`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.local.KitchenDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        try {
            val dao = db.kitchenProjectDao()
            val repository = KitchenProjectRepositoryImpl(dao = dao)
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
            val autoSaveManager = com.example.data.local.autosave.ProjectAutoSaveManager(
                dao = dao,
                scope = scope,
                ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
                initialIntervalSeconds = 10,
                repository = repository
            )

            // Modify project state: custom room dimensions + custom furniture position
            val baseTemplate = KitchenProjectRepositoryImpl.createLShapedTemplate()
            val movedElements = baseTemplate.elements.mapIndexed { index, elem ->
                if (index == 0) {
                    elem.copy(
                        position = com.example.core.math.Vector3(777f, 0f, 1888f),
                        rotationY = 180f
                    )
                } else elem
            }
            val modifiedProject = baseTemplate.copy(
                name = "مطبخ محفوظ تلقائياً",
                room = baseTemplate.room.copy(widthMm = 4950f, depthMm = 3950f, heightMm = 2850f),
                elements = movedElements
            )
            val cameraState = com.example.data.local.relation.SavedCameraState(
                mode = "ORBIT",
                yaw = 62f,
                pitch = 28f,
                zoom = 1.25f
            )

            // Trigger immediate auto-save into Room
            val savedSnapshot = autoSaveManager.saveNow(
                project = modifiedProject,
                cameraState = cameraState,
                trigger = com.example.data.local.autosave.AutoSaveTrigger.INTERVAL,
                createCheckpoint = true
            )
            org.junit.Assert.assertNotNull(savedSnapshot)

            // Verify snapshot is persisted in Room table `project_autosave_snapshots`
            val latestFromDb = dao.getLatestAutoSaveSnapshot(modifiedProject.id)
            org.junit.Assert.assertNotNull(latestFromDb)
            assertEquals("مطبخ محفوظ تلقائياً", latestFromDb!!.projectName)
            assertEquals(4950f, latestFromDb.roomWidthMm, 0.1f)
            assertEquals(3950f, latestFromDb.roomDepthMm, 0.1f)
            assertEquals(2850f, latestFromDb.roomHeightMm, 0.1f)
            assertEquals(movedElements.size, latestFromDb.furnitureCount)

            // Verify deserialization restores exact room metadata, furniture coordinates, and camera state
            val (restoredProject, restoredCam) = com.example.data.local.autosave.ProjectAutoSaveManager.deserializeSnapshot(latestFromDb)
            assertEquals(4950f, restoredProject.room.widthMm, 0.1f)
            assertEquals(3950f, restoredProject.room.depthMm, 0.1f)
            assertEquals(777f, restoredProject.elements.first().position.x, 0.1f)
            assertEquals(1888f, restoredProject.elements.first().position.z, 0.1f)
            assertEquals(180f, restoredProject.elements.first().rotationY, 0.1f)
            org.junit.Assert.assertNotNull(restoredCam)
            assertEquals(62f, restoredCam!!.yaw, 0.1f)
            assertEquals(1.25f, restoredCam.zoom, 0.01f)
        } finally {
            db.close()
        }
    }

    @Test
    fun `blueprint spec sections 2, 3, 4 and 6 rules work accurately`() {
        // Section 2: Automatic Elevation (Base 850mm + 600mm gap = 1450mm), Lower Safety Limit & Dynamic Linking
        val baseCab = com.example.domain.model.KitchenElement(
            id = "base_1",
            name = "خزانة سفلية",
            type = com.example.domain.model.KitchenElementType.BASE_DOUBLE_DOOR,
            position = com.example.core.math.Vector3(1200f, 0f, 300f),
            dimensions = com.example.core.math.Vector3(800f, 850f, 600f)
        )
        val rawWallCandidate = com.example.domain.model.KitchenElement(
            id = "wall_1",
            name = "خزانة علوية",
            type = com.example.domain.model.KitchenElementType.WALL_DOUBLE_DOOR,
            position = com.example.core.math.Vector3(1200f, 1000f, 180f),
            dimensions = com.example.core.math.Vector3(800f, 750f, 350f)
        )
        val autoElev = com.example.core.geometry.ElevationAndSafetyEngine.calculateAutomaticWallCabinetElevation(
            candidate = rawWallCandidate,
            existingElements = listOf(baseCab)
        )
        assertEquals(1450f, autoElev.resolvedPosition.y, 0.1f) // 85cm + 60cm = 145cm (1450mm)
        assertEquals("base_1", autoElev.linkedBaseElementId)

        val wallCab = rawWallCandidate.copy(
            position = autoElev.resolvedPosition,
            linkedBaseElementId = autoElev.linkedBaseElementId
        )

        // Attempt to drag wall cabinet down to Y=1200mm (below 1450mm safety limit) -> must clamp to 1450mm
        val clampedDownRes = com.example.core.geometry.ElevationAndSafetyEngine.enforceWallCabinetLowerSafetyLimit(
            candidate = wallCab.copy(position = com.example.core.math.Vector3(1200f, 1200f, 180f)),
            existingElements = listOf(baseCab, wallCab),
            roomHeightMm = 2700f
        )
        assertTrue(clampedDownRes.wasClampedByLowerSafetyLimit)
        assertEquals(1450f, clampedDownRes.resolvedPosition.y, 0.1f)

        // Attempt to pull wall cabinet upward to Y=1650mm -> full freedom upward
        val pulledUpRes = com.example.core.geometry.ElevationAndSafetyEngine.enforceWallCabinetLowerSafetyLimit(
            candidate = wallCab.copy(position = com.example.core.math.Vector3(1200f, 1650f, 180f)),
            existingElements = listOf(baseCab, wallCab),
            roomHeightMm = 2700f
        )
        assertFalse(pulledUpRes.wasClampedByLowerSafetyLimit)
        assertEquals(1650f, pulledUpRes.resolvedPosition.y, 0.1f)

        // Dynamic Linking: Increase base cabinet height from 850mm to 920mm -> wall cabinet shifts to 920 + 600 = 1520mm
        val tallerBase = baseCab.copy(dimensions = com.example.core.math.Vector3(800f, 920f, 600f))
        val propagated = com.example.core.geometry.ElevationAndSafetyEngine.propagateDynamicBaseHeightToWallCabinets(
            modifiedBaseId = "base_1",
            oldBaseTopY = 850f,
            elements = listOf(tallerBase, wallCab),
            roomHeightMm = 2700f
        )
        val shiftedWall = propagated.first { it.id == "wall_1" }
        assertEquals(1520f, shiftedWall.position.y, 0.1f)

        // Section 3: Window Safety Zone blocks upper solid cabinet over window, but allows sink base unit underneath
        val room = com.example.domain.model.RoomStructure.createDefaultRectangular() // North wall has window at 1500..2700mm
        val wallOverWindow = wallCab.copy(position = com.example.core.math.Vector3(1900f, 1450f, 180f))
        val windowCheckWall = com.example.core.geometry.ElevationAndSafetyEngine.evaluateWindowAndUtilitySafetyZones(wallOverWindow, room)
        assertTrue(windowCheckWall.isBlockedByWindowSafetyZone)

        val sinkUnderWindow = com.example.domain.model.KitchenElement(
            id = "sink_1",
            name = "وحدة مجلى",
            type = com.example.domain.model.KitchenElementType.BASE_SINK_UNIT,
            position = com.example.core.math.Vector3(1900f, 0f, 300f),
            dimensions = com.example.core.math.Vector3(900f, 850f, 600f)
        )
        val windowCheckSink = com.example.core.geometry.ElevationAndSafetyEngine.evaluateWindowAndUtilitySafetyZones(sinkUnderWindow, room)
        assertFalse(windowCheckSink.isBlockedByWindowSafetyZone)
        assertTrue(windowCheckSink.isApprovedSinkUnderWindow)

        // Section 4: Back panel toggle zeroes out back cut part & hollows 3D mesh; Top Stretchers included
        val cabWithBack = com.example.core.cam.ParametricCabinet(
            id = "c1",
            name = "خزانة بظهر",
            widthMm = 800f,
            heightMm = 850f,
            depthMm = 600f,
            hasBackPanel = true
        )
        val cabWithoutBack = cabWithBack.copy(hasBackPanel = false)
        assertTrue(cabWithBack.generateCarcassCutParts().any { it.category == com.example.core.cam.CutPartCategory.CABINET_BACK_PANEL })
        assertTrue(cabWithBack.generateCarcassCutParts().any { it.category == com.example.core.cam.CutPartCategory.MELAMINE_TOP_STRETCHER })
        assertFalse(cabWithoutBack.generateCarcassCutParts().any { it.category == com.example.core.cam.CutPartCategory.CABINET_BACK_PANEL })

        val meshWithBack = com.example.core.filament.CabinetAssetLibrary.generateCabinetAssetMesh(cabWithBack)
        val meshWithoutBack = com.example.core.filament.CabinetAssetLibrary.generateCabinetAssetMesh(cabWithoutBack)
        assertTrue(meshWithoutBack.subMeshCount < meshWithBack.subMeshCount)

        // Section 6: Render High-Res Print-Ready 2D Blueprint in cm
        val project = KitchenProjectRepositoryImpl.createLShapedTemplate()
        val blueprintBmp = com.example.core.export.PdfBlueprintExporter.renderHighResBlueprintBitmap(
            project = project,
            widthPx = 595,
            heightPx = 842
        )
        assertEquals(595, blueprintBmp.width)
        assertEquals(842, blueprintBmp.height)
    }

    @Test
    fun `collapsible panels, aluminum-melamine calculator, and magnetic sketch-to-3D work accurately`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = KitchenEditorViewModel(
            application = app,
            repository = KitchenProjectRepositoryImpl(dao = null)
        )

        // 1. Control panels start collapsed so they don't cover the 3D design
        assertTrue(viewModel.uiState.value.isControlPanelsCollapsed)
        assertFalse(viewModel.uiState.value.isCollapsibleSidebarOpen)
        assertFalse(viewModel.uiState.value.isCleanCanvasMode)

        viewModel.toggleControlPanelsCollapsed()
        assertFalse(viewModel.uiState.value.isControlPanelsCollapsed)

        viewModel.toggleCleanCanvasMode()
        assertTrue(viewModel.uiState.value.isCleanCanvasMode)
        assertTrue(viewModel.uiState.value.isControlPanelsCollapsed)

        // 2. Aluminum Profiles & Melamine Panels Calculation for custom dimensions
        val customCab = com.example.core.cam.ParametricCabinet(
            id = "calc_cab",
            name = "خزانة ألمنيوم وميلامين 90سم",
            widthMm = 900f,
            heightMm = 850f,
            depthMm = 600f,
            toeKickHeightMm = 100f,
            panelThicknessMm = 18f,
            numberOfShelves = 2,
            aluminumProfileSectionSizeMm = 40f,
            aluminumProfileThicknessMm = 2.0f,
            hasBackPanel = true
        )
        val aluParts = customCab.generateAluminumCuts()
        val aluSummary = customCab.calculateAluminumSummary()
        val melParts = customCab.generateCarcassCutParts()
        assertTrue(aluParts.isNotEmpty())
        assertTrue(aluSummary.grossLinearMetersWithWaste > 0f)
        assertTrue(melParts.any { it.category == com.example.core.cam.CutPartCategory.MELAMINE_SIDE })
        assertTrue(melParts.any { it.category == com.example.core.cam.CutPartCategory.MELAMINE_BOTTOM_AND_SHELF })

        // 3. Magnetic Sketch-to-3D auto-snapping & instant 3D scene conversion
        val snap1 = com.example.core.sketch.SketchTo3DEngine.snapStrokeMagnetically(
            rawStart = androidx.compose.ui.geometry.Offset(100f, 100f),
            rawEnd = androidx.compose.ui.geometry.Offset(400f, 105f),
            existingStrokes = emptyList()
        )
        val stroke1 = com.example.core.sketch.DrawnStroke(
            points = listOf(snap1.start, snap1.end),
            type = com.example.core.sketch.StrokeType.WALL
        )
        // Second stroke starts near the end of stroke1 (within magnetic radius) -> snaps to exact endpoint (400f, 100f)
        val snap2 = com.example.core.sketch.SketchTo3DEngine.snapStrokeMagnetically(
            rawStart = androidx.compose.ui.geometry.Offset(412f, 108f),
            rawEnd = androidx.compose.ui.geometry.Offset(415f, 350f),
            existingStrokes = listOf(stroke1)
        )
        assertEquals(snap1.end.x, snap2.start.x, 0.01f)
        assertEquals(snap1.end.y, snap2.start.y, 0.01f)
        val stroke2 = com.example.core.sketch.DrawnStroke(
            points = listOf(snap2.start, snap2.end),
            type = com.example.core.sketch.StrokeType.WALL
        )

        val snapCab = com.example.core.sketch.SketchTo3DEngine.snapStrokeMagnetically(
            rawStart = androidx.compose.ui.geometry.Offset(120f, 102f),
            rawEnd = androidx.compose.ui.geometry.Offset(280f, 102f),
            existingStrokes = listOf(stroke1, stroke2)
        )
        val cabStroke = com.example.core.sketch.DrawnStroke(
            points = listOf(snapCab.start, snapCab.end),
            type = com.example.core.sketch.StrokeType.BASE_CABINET
        )
        val scene = com.example.core.sketch.SketchTo3DEngine.convertStrokesToScene(
            strokes = listOf(stroke1, stroke2, cabStroke),
            calibration = null,
            canvasWidthPx = 800f,
            canvasHeightPx = 600f
        )
        assertTrue(scene.room.widthMm >= 1800f)
        assertEquals(1, scene.generatedElements.size)
    }
}
