package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.math.Vector3
import com.example.data.local.KitchenDatabase
import com.example.data.local.dao.KitchenProjectDao
import com.example.data.local.entity.FurnitureAssetEntity
import com.example.data.local.entity.ProjectMetadataEntity
import com.example.data.local.entity.WallOpeningEntity
import com.example.data.local.relation.ProjectWithAssets
import com.example.data.local.relation.SavedCameraState
import com.example.domain.model.*
import com.example.domain.repository.KitchenProject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomDatabaseSchemaTest {

    private lateinit var db: KitchenDatabase
    private lateinit var dao: KitchenProjectDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KitchenDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.kitchenProjectDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testRoomDimensionsAndMetadataPersistence() = runBlocking {
        val metadata = ProjectMetadataEntity(
            id = "proj_001",
            name = "مطبخ أحلامي العصري",
            roomWidthMm = 5000f,
            roomDepthMm = 4000f,
            roomHeightMm = 2800f,
            wallThicknessMm = 200f,
            floorMaterialId = "floor_slate",
            wallFinishId = "charcoal_matte",
            cameraMode = "ORBIT",
            cameraYawDegrees = 45f,
            cameraPitchDegrees = 35f,
            cameraZoomFactor = 1.2f
        )

        dao.insertProjectMetadata(metadata)

        val retrieved = dao.getAllProjectMetadata().first()
        assertEquals(1, retrieved.size)
        val proj = retrieved.first()

        assertEquals("proj_001", proj.id)
        assertEquals("مطبخ أحلامي العصري", proj.name)
        assertEquals(5000f, proj.roomWidthMm, 0.01f)
        assertEquals(4000f, proj.roomDepthMm, 0.01f)
        assertEquals(2800f, proj.roomHeightMm, 0.01f)
        assertEquals(200f, proj.wallThicknessMm, 0.01f)
        assertEquals("floor_slate", proj.floorMaterialId)
        assertEquals("charcoal_matte", proj.wallFinishId)
    }

    @Test
    fun testFurnitureAssetsAndOpeningsRelationship() = runBlocking {
        val metadata = ProjectMetadataEntity(
            id = "proj_002",
            name = "مطبخ أوفلاين مع خزائن",
            roomWidthMm = 4200f,
            roomDepthMm = 3600f,
            roomHeightMm = 2700f
        )

        val assets = listOf(
            FurnitureAssetEntity(
                id = "elem_base_1",
                projectId = "proj_002",
                name = "خزانة أرضية 60 سم",
                typeCode = "BASE_SINGLE_DOOR",
                category = "BASE_CABINET",
                layer = "BASE_CABINETS",
                posX = 600f,
                posY = 0f,
                posZ = 600f,
                dimWidthMm = 600f,
                dimHeightMm = 850f,
                dimDepthMm = 600f,
                rotationY = 0f,
                materialId = "oak",
                sortOrder = 0
            ),
            FurnitureAssetEntity(
                id = "elem_wall_1",
                projectId = "proj_002",
                name = "خزانة علوية 80 سم",
                typeCode = "WALL_DOUBLE_DOOR",
                category = "WALL_CABINET",
                layer = "WALL_CABINETS",
                posX = 1200f,
                posY = 1450f,
                posZ = 350f,
                dimWidthMm = 800f,
                dimHeightMm = 720f,
                dimDepthMm = 350f,
                rotationY = 0f,
                materialId = "white_gloss",
                sortOrder = 1
            )
        )

        val openings = listOf(
            WallOpeningEntity(
                id = "win_1",
                projectId = "proj_002",
                wallSide = "NORTH",
                openingType = "WINDOW",
                offsetMm = 1500f,
                widthMm = 1200f,
                heightMm = 1000f,
                sillHeightMm = 1000f
            )
        )

        dao.saveFullProjectWithAssets(metadata, assets, openings)

        val fullProject = dao.getProjectWithAssets("proj_002").first()
        assertNotNull(fullProject)
        assertEquals("proj_002", fullProject!!.metadata.id)
        assertEquals(2, fullProject.furnitureAssets.size)
        assertEquals(1, fullProject.openings.size)

        // Convert to domain model
        val domainProject = fullProject.toDomainProject()
        assertEquals(4200f, domainProject.room.widthMm, 0.01f)
        assertEquals(3600f, domainProject.room.depthMm, 0.01f)
        assertEquals(2, domainProject.elements.size)
        assertEquals(4, domainProject.room.walls.size)
        assertEquals("BASE_SINGLE_DOOR", domainProject.elements[0].type.name)
    }

    @Test
    fun testSavedCameraCoordinatesUpdateAndRestoration() = runBlocking {
        val metadata = ProjectMetadataEntity(
            id = "proj_cam",
            name = "مشروع اختبار الكاميرا",
            cameraMode = "ORBIT",
            cameraYawDegrees = 20f,
            cameraPitchDegrees = 25f,
            cameraZoomFactor = 1.0f
        )
        dao.insertProjectMetadata(metadata)

        // Update camera coordinates (e.g. user entered walkthrough mode and rotated)
        dao.updateSavedCamera(
            projectId = "proj_cam",
            mode = "FIRST_PERSON_WALK",
            yaw = 180f,
            pitch = -5f,
            zoom = 1.5f,
            panX = 120f,
            panY = 80f,
            walkX = 2200f,
            walkY = 1600f,
            walkZ = 1900f,
            walkYaw = 180f,
            walkPitch = -5f
        )

        val retrieved = dao.getProjectWithAssets("proj_cam").first()
        assertNotNull(retrieved)
        val cam = retrieved!!.metadata
        assertEquals("FIRST_PERSON_WALK", cam.cameraMode)
        assertEquals(180f, cam.cameraYawDegrees, 0.01f)
        assertEquals(-5f, cam.cameraPitchDegrees, 0.01f)
        assertEquals(1.5f, cam.cameraZoomFactor, 0.01f)
        assertEquals(120f, cam.cameraPanX, 0.01f)
        assertEquals(80f, cam.cameraPanY, 0.01f)
        assertEquals(2200f, cam.walkPosX, 0.01f)
        assertEquals(1600f, cam.walkPosY, 0.01f)
        assertEquals(1900f, cam.walkPosZ, 0.01f)
        assertEquals(180f, cam.walkLookYaw, 0.01f)
        assertEquals(-5f, cam.walkLookPitch, 0.01f)
    }

    @Test
    fun testDomainProjectRoundTrip() {
        val room = RoomStructure(
            widthMm = 4500f,
            depthMm = 3800f,
            heightMm = 2900f,
            wallThicknessMm = 180f,
            floorMaterial = MaterialFinish.PARQUET_OAK_FLOOR,
            wallFinish = MaterialFinish.WHITE_HIGH_GLOSS,
            walls = listOf(
                Wall(
                    id = "w_n",
                    side = WallSide.NORTH,
                    lengthMm = 4500f,
                    heightMm = 2900f,
                    thicknessMm = 180f,
                    openings = listOf(
                        WallOpening("op_1", OpeningType.WINDOW, 1000f, 1200f, 1000f, 1100f)
                    )
                )
            )
        )

        val elements = listOf(
            KitchenElement(
                id = "elem_island",
                name = "جزيرة مطبخ وسطية",
                type = KitchenElementType.ISLAND_COUNTER,
                position = Vector3(2000f, 0f, 2000f),
                rotationY = 90f,
                dimensions = Vector3(1400f, 850f, 900f),
                material = MaterialFinish.CARRARA_MARBLE,
                countertopMaterial = MaterialFinish.BLACK_GRANITE
            )
        )

        val project = KitchenProject(
            id = "proj_domain_test",
            name = "مطبخ الرخام والجرانيت",
            room = room,
            elements = elements
        )

        val (meta, assets, openings) = ProjectWithAssets.fromDomain(
            project,
            SavedCameraState(mode = "TOP_DOWN", yaw = 0f, pitch = 89f, zoom = 1.0f)
        )

        assertEquals("proj_domain_test", meta.id)
        assertEquals(4500f, meta.roomWidthMm, 0.01f)
        assertEquals(3800f, meta.roomDepthMm, 0.01f)
        assertEquals("TOP_DOWN", meta.cameraMode)
        assertEquals(89f, meta.cameraPitchDegrees, 0.01f)

        assertEquals(1, assets.size)
        assertEquals("elem_island", assets[0].id)
        assertEquals("carrara", assets[0].materialId)
        assertEquals("black_granite", assets[0].countertopMaterialId)

        assertEquals(1, openings.size)
        assertEquals("WINDOW", openings[0].openingType)
    }

    @Test
    fun testRepositoryOfflineProjectSavingAndLoading() = runBlocking {
        val repository = com.example.data.repository.KitchenProjectRepositoryImpl(dao = dao)
        val initialProject = repository.getProject().first()

        // 1. Save project as new offline project
        val saved = repository.saveProjectAs("مطبخ فيلا الشاطئ", initialProject)
        assertNotNull(saved.id)
        assertEquals("مطبخ فيلا الشاطئ", saved.name)

        // 2. Query all saved projects from Room
        val allProjects = dao.getAllProjectMetadata().first()
        assertTrue(allProjects.any { it.name == "مطبخ فيلا الشاطئ" })

        // 3. Create a blank project
        val blank = repository.createNewProject("مطبخ الملحق الصغير")
        assertEquals("مطبخ الملحق الصغير", blank.name)
        assertTrue(blank.elements.isEmpty())

        // 4. Load the first saved project back by ID
        repository.loadProjectById(saved.id)
        val loadedProject = repository.getProject().first()
        assertEquals(saved.id, loadedProject.id)
        assertEquals("مطبخ فيلا الشاطئ", loadedProject.name)

        // 5. Test JSON Export of project data
        val bom = repository.generateBillOfMaterials(loadedProject)
        val jsonOutput = com.example.core.export.JsonProjectExporter.serializeToJsonWithMetadata(
            project = loadedProject,
            options = com.example.core.export.JsonProjectExporter.JsonExportOptions(
                exportMode = com.example.core.export.JsonProjectExporter.JsonExportMode.FULL_PROJECT_AND_CAM
            ),
            cabinets = emptyList(),
            bom = bom
        ).jsonContent
        assertNotNull(jsonOutput)
        assertTrue(jsonOutput.contains("مطبخ فيلا الشاطئ"))
        assertTrue(jsonOutput.contains("room"))

        // 6. Test JSON Import parses back successfully
        val imported = com.example.core.export.JsonProjectExporter.importProjectOrElementsFromJson(
            jsonString = jsonOutput,
            fallbackProject = loadedProject
        )
        assertEquals(loadedProject.name, imported.name)
        assertEquals(loadedProject.elements.size, imported.elements.size)

        // 7. Delete project by ID
        repository.deleteProjectById(blank.id)
        val afterDelete = dao.getAllProjectMetadata().first()
        assertFalse(afterDelete.any { it.id == blank.id })
        repository.close()
    }
}

