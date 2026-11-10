package com.example.data.repository

import com.example.core.math.Vector3
import com.example.domain.model.*
import com.example.domain.repository.BillOfMaterials
import com.example.domain.repository.BillOfMaterialsItem
import com.example.domain.repository.KitchenProject
import com.example.domain.repository.KitchenProjectRepository
import com.example.data.local.dao.KitchenProjectDao
import com.example.data.local.relation.ProjectWithAssets
import com.example.data.local.relation.SavedCameraState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.Closeable
import java.text.SimpleDateFormat
import java.util.*

class KitchenProjectRepositoryImpl(
    private val dao: KitchenProjectDao? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : KitchenProjectRepository, Closeable {

    private val initialTemplate = createLShapedTemplate()

    private val templates by lazy {
        listOf(
            initialTemplate,
            createLuxuryDoubleTierVitrineTemplate(),
            createIslandTemplate(),
            createUShapedTemplate(),
            createLinearTemplate()
        )
    }

    private val _currentProject = MutableStateFlow(initialTemplate)

    init {
        if (dao != null) {
            scope.launch {
                try {
                    dao.getActiveProjectWithAssets().collect { dbProject ->
                        if (dbProject != null) {
                            val domainProj = dbProject.toDomainProject()
                            if (_currentProject.value != domainProj) {
                                _currentProject.value = domainProj
                            }
                        } else {
                            // Bootstrap database with initial L-Shaped template
                            val (meta, assets, openings) = ProjectWithAssets.fromDomain(initialTemplate)
                            dao.saveFullProjectWithAssets(meta, assets, openings)
                        }
                    }
                } catch (e: Throwable) {
                    // Handled gracefully when database connection is closed or coroutine cancelled
                }
            }
        }
    }

    override fun close() {
        scope.cancel()
    }

    override fun getProject(): Flow<KitchenProject> = _currentProject.asStateFlow()

    override suspend fun updateProject(project: KitchenProject) {
        val changed = _currentProject.value != project
        _currentProject.value = project
        if (dao != null && changed) {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                val (meta, assets, openings) = ProjectWithAssets.fromDomain(project)
                dao.saveFullProjectWithAssets(meta, assets, openings)
            }
        }
    }

    override fun getSampleTemplates(): List<KitchenProject> = templates

    override suspend fun loadTemplate(templateId: String) {
        templates.find { it.id == templateId }?.let { template ->
            _currentProject.value = template
            dao?.let {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val (meta, assets, openings) = ProjectWithAssets.fromDomain(template)
                    it.saveFullProjectWithAssets(meta, assets, openings)
                }
            }
        }
    }

    override suspend fun saveCameraState(projectId: String, cameraState: SavedCameraState) {
        dao?.updateSavedCamera(
            projectId = projectId,
            mode = cameraState.mode,
            yaw = cameraState.yaw,
            pitch = cameraState.pitch,
            zoom = cameraState.zoom,
            panX = cameraState.panX,
            panY = cameraState.panY,
            walkX = cameraState.walkPosX,
            walkY = cameraState.walkPosY,
            walkZ = cameraState.walkPosZ,
            walkYaw = cameraState.walkLookYaw,
            walkPitch = cameraState.walkLookPitch
        )
    }

    override fun getSavedCameraState(projectId: String): Flow<SavedCameraState?> {
        return dao?.getProjectWithAssets(projectId)?.map { projectWithAssets ->
            projectWithAssets?.metadata?.let { meta ->
                SavedCameraState(
                    mode = meta.cameraMode,
                    eyeX = meta.cameraEyeX,
                    eyeY = meta.cameraEyeY,
                    eyeZ = meta.cameraEyeZ,
                    targetX = meta.cameraTargetX,
                    targetY = meta.cameraTargetY,
                    targetZ = meta.cameraTargetZ,
                    yaw = meta.cameraYawDegrees,
                    pitch = meta.cameraPitchDegrees,
                    zoom = meta.cameraZoomFactor,
                    panX = meta.cameraPanX,
                    panY = meta.cameraPanY,
                    walkEyeHeightMm = meta.walkEyeHeightMm,
                    walkPosX = meta.walkPosX,
                    walkPosY = meta.walkPosY,
                    walkPosZ = meta.walkPosZ,
                    walkLookYaw = meta.walkLookYaw,
                    walkLookPitch = meta.walkLookPitch
                )
            }
        } ?: kotlinx.coroutines.flow.flowOf(null)
    }

    override suspend fun saveAutoSaveSnapshot(
        project: KitchenProject,
        cameraState: SavedCameraState?,
        snapshot: com.example.data.local.entity.AutoSaveSnapshotEntity
    ) {
        _currentProject.value = project
        dao?.let {
            val (meta, assets, openings) = ProjectWithAssets.fromDomain(project, cameraState)
            it.saveAutoSaveEntryWithRelationalSync(
                metadata = meta,
                assets = assets,
                openings = openings,
                snapshot = snapshot
            )
        }
    }

    override fun observeAutoSaveSnapshots(
        limit: Int
    ): Flow<List<com.example.data.local.entity.AutoSaveSnapshotEntity>> {
        return dao?.observeRecentAutoSaveSnapshots(limit) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    override suspend fun getLatestAutoSaveSnapshot(
        projectId: String
    ): com.example.data.local.entity.AutoSaveSnapshotEntity? {
        return dao?.getLatestAutoSaveSnapshot(projectId) ?: dao?.getMostRecentAutoSaveSnapshot()
    }

    override fun getAllSavedProjects(): Flow<List<com.example.data.local.entity.ProjectMetadataEntity>> {
        return dao?.getAllProjectMetadata() ?: flowOf(emptyList())
    }

    override suspend fun loadProjectById(projectId: String) {
        if (dao != null) {
            dao.deactivateOtherProjects(projectId)
            dao.setActiveProject(projectId)
            val p = dao.getProjectWithAssets(projectId).firstOrNull()
            if (p != null) {
                _currentProject.value = p.toDomainProject()
            }
        }
    }

    override suspend fun saveProjectAs(name: String, project: KitchenProject): KitchenProject {
        val newId = UUID.randomUUID().toString()
        val safeName = name.trim().ifEmpty { "مشروع مطبخ ${SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date())}" }
        val updatedProject = project.copy(
            id = newId,
            name = safeName,
            createdAt = System.currentTimeMillis()
        )
        _currentProject.value = updatedProject
        if (dao != null) {
            dao.deactivateOtherProjects(newId)
            val (meta, assets, openings) = ProjectWithAssets.fromDomain(updatedProject)
            val activeMeta = meta.copy(isCurrentActive = true, updatedAt = System.currentTimeMillis())
            dao.saveFullProjectWithAssets(activeMeta, assets, openings)
        }
        return updatedProject
    }

    override suspend fun createNewProject(name: String, room: RoomStructure): KitchenProject {
        val safeName = name.trim().ifEmpty { "تصميم جديد ${SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date())}" }
        val newProject = KitchenProject(
            id = UUID.randomUUID().toString(),
            name = safeName,
            room = room,
            elements = emptyList(),
            groups = emptyList(),
            createdAt = System.currentTimeMillis()
        )
        _currentProject.value = newProject
        if (dao != null) {
            dao.deactivateOtherProjects(newProject.id)
            val (meta, assets, openings) = ProjectWithAssets.fromDomain(newProject)
            val activeMeta = meta.copy(isCurrentActive = true, updatedAt = System.currentTimeMillis())
            dao.saveFullProjectWithAssets(activeMeta, assets, openings)
        }
        return newProject
    }

    override suspend fun deleteProjectById(projectId: String) {
        if (dao != null) {
            dao.deleteProject(projectId)
            if (_currentProject.value.id == projectId) {
                val remaining = dao.getAllProjectMetadata().firstOrNull()?.filter { it.id != projectId }
                if (!remaining.isNullOrEmpty()) {
                    loadProjectById(remaining[0].id)
                } else {
                    loadTemplate(templates[0].id)
                }
            }
        }
    }

    override suspend fun getProjectById(projectId: String): KitchenProject? {
        return dao?.getProjectWithAssets(projectId)?.firstOrNull()?.toDomainProject()
    }

    override fun generateBillOfMaterials(
        project: KitchenProject,
        enabledAccessoryIds: Set<String>
    ): BillOfMaterials {
        val grouped = project.elements.groupBy { it.type.code to it.material.name }
        val items = grouped.map { (_, list) ->
            val first = list.first()
            BillOfMaterialsItem(
                name = first.name,
                typeCode = first.type.code,
                quantity = list.size,
                dimensionsMm = "${first.dimensions.x.toInt()} × ${first.dimensions.y.toInt()} × ${first.dimensions.z.toInt()} مم",
                finishName = first.material.name,
                categoryName = first.type.category.displayName,
                widthMm = first.dimensions.x,
                heightMm = first.dimensions.y,
                depthMm = first.dimensions.z
            )
        }.sortedBy { it.categoryName }

        val baseElements = project.elements.filter { it.type.category == ElementCategory.BASE_CABINET }
        val upperAndTallElements = project.elements.filter {
            it.type.category == ElementCategory.WALL_CABINET || it.type.category == ElementCategory.TALL_UNIT
        }
        val totalLinearMm = baseElements.sumOf { it.dimensions.x.toDouble() }.toFloat()
        val totalLinearM = totalLinearMm / 1000f

        val countertopAreaSqM = baseElements.sumOf { (it.dimensions.x * it.dimensions.z).toDouble() }.toFloat() / 1_000_000f

        val matchedAccessories = SmartAccessoryMatcher.matchAccessoriesForElements(
            elements = project.elements,
            enabledAccessoryIds = enabledAccessoryIds
        )
        val totalAccessoriesCount = matchedAccessories.sumOf { it.quantity }
        val accessoriesCostUsd = matchedAccessories.sumOf { it.totalCostUsd.toDouble() }.toFloat()
        val cabinetsCostUsd = (totalLinearM * com.example.core.units.MeasurementUnitSystem.BASE_RATE_PER_LINEAR_METER_USD) +
            (upperAndTallElements.size * com.example.core.units.MeasurementUnitSystem.UPPER_CABINET_UNIT_AVERAGE_USD)
        val countertopCostUsd = countertopAreaSqM * com.example.core.units.MeasurementUnitSystem.COUNTERTOP_RATE_PER_SQ_METER_USD
        val totalEstimatedCostUsd = cabinetsCostUsd + countertopCostUsd + accessoriesCostUsd

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        return BillOfMaterials(
            projectName = project.name,
            items = items,
            totalCabinetsCount = project.elements.size,
            totalLinearMeters = totalLinearM,
            countertopAreaSquareMeters = countertopAreaSqM,
            generatedDate = dateFormat.format(Date()),
            matchedAccessories = matchedAccessories,
            totalAccessoriesCount = totalAccessoriesCount,
            cabinetsEstimatedCostUsd = cabinetsCostUsd,
            countertopEstimatedCostUsd = countertopCostUsd,
            accessoriesEstimatedCostUsd = accessoriesCostUsd,
            totalEstimatedProjectCostUsd = totalEstimatedCostUsd
        )
    }

    companion object {
        fun createLShapedTemplate(): KitchenProject {
            val room = RoomStructure(
                widthMm = 4400f,
                depthMm = 3800f,
                heightMm = 2700f,
                wallThicknessMm = 150f,
                floorMaterial = MaterialFinish.MARBLE_WHITE_FLOOR,
                wallFinish = MaterialFinish.WHITE_HIGH_GLOSS
            )

            val elements = listOf(
                // 1. West Entrance: Tall Illuminated 4-Niche Display Column (as in photo 3)
                KitchenElement(
                    id = "niche_col_1",
                    name = "عمود ديكور بـ 4 فتحات نيش مضاءة",
                    type = KitchenElementType.TALL_NICHE_DISPLAY_COLUMN,
                    position = Vector3(300f, 0f, 2250f),
                    rotationY = 90f,
                    dimensions = Vector3(450f, 2170f, 580f),
                    material = MaterialFinish.NATURAL_OAK
                ),
                // West Wall Base Cabinet (90cm double door)
                KitchenElement(
                    id = "base_west_1",
                    name = "خزانة درفتين غربية",
                    type = KitchenElementType.BASE_DOUBLE_DOOR,
                    position = Vector3(300f, 0f, 1450f),
                    rotationY = 90f,
                    dimensions = Vector3(900f, 850f, 600f),
                    material = MaterialFinish.NATURAL_OAK,
                    countertopMaterial = MaterialFinish.BLACK_GRANITE
                ),

                // 2. North Wall Base Units: L-Corner + Sink + 4-Drawers + Under-Oven + Cooktop + Oven Tower + Glass Vitrine
                KitchenElement(
                    id = "base_corner_1",
                    name = "خزانة ركنية زاوية",
                    type = KitchenElementType.BASE_CORNER_L,
                    position = Vector3(450f, 0f, 450f),
                    rotationY = 0f,
                    dimensions = Vector3(900f, 850f, 900f),
                    material = MaterialFinish.NATURAL_OAK,
                    countertopMaterial = MaterialFinish.BLACK_GRANITE
                ),
                KitchenElement(
                    id = "base_sink_1",
                    name = "وحدة حوض الغسيل بالخلاط",
                    type = KitchenElementType.BASE_SINK_UNIT,
                    position = Vector3(1300f, 0f, 300f),
                    rotationY = 0f,
                    dimensions = Vector3(800f, 850f, 600f),
                    material = MaterialFinish.NATURAL_OAK,
                    countertopMaterial = MaterialFinish.BLACK_GRANITE
                ),
                KitchenElement(
                    id = "base_4drawers_1",
                    name = "خزانة 4 أدراج للملاعق والتخزين",
                    type = KitchenElementType.BASE_4_DRAWERS,
                    position = Vector3(2000f, 0f, 300f),
                    rotationY = 0f,
                    dimensions = Vector3(600f, 850f, 600f),
                    material = MaterialFinish.NATURAL_OAK,
                    countertopMaterial = MaterialFinish.BLACK_GRANITE
                ),
                KitchenElement(
                    id = "base_cooktop_unit",
                    name = "خزانة فرن سفلي بفتحة تهوية",
                    type = KitchenElementType.BASE_UNDER_OVEN,
                    position = Vector3(2600f, 0f, 300f),
                    rotationY = 0f,
                    dimensions = Vector3(600f, 850f, 600f),
                    material = MaterialFinish.NATURAL_OAK,
                    countertopMaterial = MaterialFinish.BLACK_GRANITE
                ),
                KitchenElement(
                    id = "cooktop_1",
                    name = "مسطح طهي سيراميك حثي",
                    type = KitchenElementType.COOKTOP_INDUCTION,
                    position = Vector3(2600f, 850f, 300f),
                    rotationY = 0f,
                    dimensions = Vector3(600f, 55f, 520f),
                    material = MaterialFinish.BLACK_GRANITE
                ),
                KitchenElement(
                    id = "tall_oven_tower_1",
                    name = "برج فرن وميكروويف بفتحات تهوية",
                    type = KitchenElementType.TALL_OVEN_TOWER,
                    position = Vector3(3200f, 0f, 300f),
                    rotationY = 0f,
                    dimensions = Vector3(600f, 2170f, 600f),
                    material = MaterialFinish.NATURAL_OAK
                ),
                KitchenElement(
                    id = "tall_vitrine_1",
                    name = "فيترينة زجاج أسود مضاءة بشريط LED",
                    type = KitchenElementType.TALL_GLASS_VITRINE_LED,
                    position = Vector3(3850f, 0f, 300f),
                    rotationY = 0f,
                    dimensions = Vector3(650f, 2670f, 580f),
                    material = MaterialFinish.SMOKED_GLASS_ALU
                ),

                // 3. Standard Wall Cabinets at 1450mm with Warm 3000K Under-Cabinet LEDs
                KitchenElement(
                    id = "wall_sink_1",
                    name = "خزانة علوية واجهة زجاج وإضاءة LED",
                    type = KitchenElementType.WALL_GLASS_BLACK_FRAME,
                    position = Vector3(1300f, 1450f, 175f),
                    rotationY = 0f,
                    dimensions = Vector3(800f, 720f, 350f),
                    material = MaterialFinish.SMOKED_GLASS_ALU
                ),
                KitchenElement(
                    id = "wall_aventos_1",
                    name = "خزانة علوية قلاب مزدوج Aventos",
                    type = KitchenElementType.WALL_LIFT_AVENTOS,
                    position = Vector3(2000f, 1450f, 175f),
                    rotationY = 0f,
                    dimensions = Vector3(600f, 720f, 350f),
                    material = MaterialFinish.CHARCOAL_MATTE
                ),
                KitchenElement(
                    id = "hood_1",
                    name = "شفاط مطبخ تلسكوبي أسود",
                    type = KitchenElementType.RANGE_HOOD_BOX,
                    position = Vector3(2600f, 1520f, 250f),
                    rotationY = 0f,
                    dimensions = Vector3(600f, 400f, 500f),
                    material = MaterialFinish.CHARCOAL_MATTE
                ),

                // 4. Second-Tier Deep Top Bridge / Crown Cabinets up to 2670mm Ceiling (as in photos 1, 2, 3)
                KitchenElement(
                    id = "bridge_north_1",
                    name = "خزانة تاج علوية عميقة للسقف 1",
                    type = KitchenElementType.BRIDGE_TRIPLE_120,
                    position = Vector3(1100f, 2170f, 290f),
                    rotationY = 0f,
                    dimensions = Vector3(1200f, 500f, 580f),
                    material = MaterialFinish.CHARCOAL_MATTE
                ),
                KitchenElement(
                    id = "bridge_north_2",
                    name = "خزانة تاج علوية عميقة للسقف 2",
                    type = KitchenElementType.BRIDGE_TRIPLE_120,
                    position = Vector3(2300f, 2170f, 290f),
                    rotationY = 0f,
                    dimensions = Vector3(1200f, 500f, 580f),
                    material = MaterialFinish.CHARCOAL_MATTE
                ),
                KitchenElement(
                    id = "bridge_over_tower",
                    name = "خزانة تاج فوق برج الفرن",
                    type = KitchenElementType.BRIDGE_SINGLE_60,
                    position = Vector3(3200f, 2170f, 300f),
                    rotationY = 0f,
                    dimensions = Vector3(600f, 500f, 600f),
                    material = MaterialFinish.CHARCOAL_MATTE
                ),

                // 5. Central Pendant Chandelier
                KitchenElement(
                    id = "ceiling_chandelier_1",
                    name = "نجفة سقف ثلاثية مودرن",
                    type = KitchenElementType.CEILING_CHANDELIER_MODERN,
                    position = Vector3(2200f, 2120f, 1900f),
                    rotationY = 0f,
                    dimensions = Vector3(600f, 550f, 600f),
                    material = MaterialFinish.BRUSHED_BRASS
                )
            )

            return KitchenProject(
                id = "template_l_shape",
                name = "مطبخ ملكي مزدوج للتاج مع فيترينة ونيش LED",
                room = room,
                elements = elements
            )
        }

        fun createLuxuryDoubleTierVitrineTemplate(): KitchenProject {
            val room = RoomStructure(
                widthMm = 4600f,
                depthMm = 4000f,
                heightMm = 2700f,
                wallThicknessMm = 150f,
                floorMaterial = MaterialFinish.MARBLE_WHITE_FLOOR,
                wallFinish = MaterialFinish.CHAMPAGNE_GLOSS
            )

            val elements = listOf(
                // North Wall: Champagne Gloss Base Units + Black Galaxy Granite Countertop
                KitchenElement(
                    id = "lux_tower_oven",
                    name = "برج فرن وميكروويف مدمج بفتحات تهوية",
                    type = KitchenElementType.TALL_OVEN_TOWER,
                    position = Vector3(600f, 0f, 300f),
                    rotationY = 0f,
                    dimensions = Vector3(600f, 2170f, 600f),
                    material = MaterialFinish.CAPPUCCINO_MATTE
                ),
                KitchenElement(
                    id = "lux_base_drawers",
                    name = "خزانة 4 أدراج شامبين أكريليك",
                    type = KitchenElementType.BASE_4_DRAWERS,
                    position = Vector3(1200f, 0f, 300f),
                    rotationY = 0f,
                    dimensions = Vector3(600f, 850f, 600f),
                    material = MaterialFinish.CHAMPAGNE_GLOSS,
                    countertopMaterial = MaterialFinish.BLACK_GRANITE
                ),
                KitchenElement(
                    id = "lux_base_sink",
                    name = "خزانة حوض مزدوج بفتحات تهوية",
                    type = KitchenElementType.BASE_SINK_VENTED_100,
                    position = Vector3(2000f, 0f, 300f),
                    rotationY = 0f,
                    dimensions = Vector3(1000f, 850f, 600f),
                    material = MaterialFinish.CHAMPAGNE_GLOSS,
                    countertopMaterial = MaterialFinish.BLACK_GRANITE
                ),
                KitchenElement(
                    id = "lux_base_double",
                    name = "خزانة سفلية 90 سم",
                    type = KitchenElementType.BASE_DOUBLE_DOOR,
                    position = Vector3(2950f, 0f, 300f),
                    rotationY = 0f,
                    dimensions = Vector3(900f, 850f, 600f),
                    material = MaterialFinish.CHAMPAGNE_GLOSS,
                    countertopMaterial = MaterialFinish.BLACK_GRANITE
                ),
                KitchenElement(
                    id = "lux_vitrine_right",
                    name = "فيترينة عرض مزدوجة زجاج أسود و LED",
                    type = KitchenElementType.TALL_DOUBLE_VITRINE_LED,
                    position = Vector3(3850f, 0f, 300f),
                    rotationY = 0f,
                    dimensions = Vector3(900f, 2670f, 580f),
                    material = MaterialFinish.SMOKED_GLASS_ALU
                ),
                // Upper Wall + Glass Bridge Units with Warm LED
                KitchenElement(
                    id = "lux_wall_triple",
                    name = "خزانة علوية 3 درف مع شريط LED",
                    type = KitchenElementType.WALL_TRIPLE_120,
                    position = Vector3(1500f, 1450f, 175f),
                    rotationY = 0f,
                    dimensions = Vector3(1200f, 720f, 350f),
                    material = MaterialFinish.CHAMPAGNE_GLOSS
                ),
                KitchenElement(
                    id = "lux_bridge_glass_1",
                    name = "خزانة تاج زجاجية مضاءة LED 1",
                    type = KitchenElementType.BRIDGE_GLASS_LED_90,
                    position = Vector3(1350f, 2170f, 290f),
                    rotationY = 0f,
                    dimensions = Vector3(900f, 500f, 580f),
                    material = MaterialFinish.SMOKED_GLASS_ALU
                ),
                KitchenElement(
                    id = "lux_bridge_glass_2",
                    name = "خزانة تاج زجاجية مضاءة LED 2",
                    type = KitchenElementType.BRIDGE_GLASS_LED_90,
                    position = Vector3(2250f, 2170f, 290f),
                    rotationY = 0f,
                    dimensions = Vector3(900f, 500f, 580f),
                    material = MaterialFinish.SMOKED_GLASS_ALU
                ),
                // Peninsula Breakfast Bar with Illuminated Recessed Niches (as in photo 4)
                KitchenElement(
                    id = "lux_peninsula_lit",
                    name = "بار إفطار بفتحات نيش خشبية مضاءة LED",
                    type = KitchenElementType.PENINSULA_LIT_NICHES,
                    position = Vector3(1800f, 0f, 2450f),
                    rotationY = 0f,
                    dimensions = Vector3(1600f, 920f, 600f),
                    material = MaterialFinish.CHAMPAGNE_GLOSS,
                    countertopMaterial = MaterialFinish.BLACK_GRANITE
                ),
                // Geometric Ceiling Linear LED Panel
                KitchenElement(
                    id = "lux_ceiling_led",
                    name = "إضاءة سقف خطية هندسية LED",
                    type = KitchenElementType.CEILING_LINEAR_LED_PANEL,
                    position = Vector3(2300f, 2640f, 1950f),
                    rotationY = 0f,
                    dimensions = Vector3(1800f, 45f, 900f),
                    material = MaterialFinish.WHITE_HIGH_GLOSS
                )
            )

            return KitchenProject(
                id = "template_luxury_vitrine",
                name = "مطبخ شامبين أكريليك مع بار مضاء وفيترينة LED",
                room = room,
                elements = elements
            )
        }

        fun createIslandTemplate(): KitchenProject {
            val room = RoomStructure(
                widthMm = 4800f,
                depthMm = 4000f,
                heightMm = 2800f,
                floorMaterial = MaterialFinish.GREY_SLATE_FLOOR,
                wallFinish = MaterialFinish.CHARCOAL_MATTE
            )

            val elements = listOf(
                // Island in the center
                KitchenElement(
                    id = "island_center_1",
                    name = "جزيرة المطبخ الرئيسية",
                    type = KitchenElementType.ISLAND_COUNTER,
                    position = Vector3(2400f, 0f, 2000f),
                    rotationY = 0f,
                    dimensions = Vector3(1600f, 880f, 900f),
                    material = MaterialFinish.CHARCOAL_MATTE,
                    countertopMaterial = MaterialFinish.CARRARA_MARBLE
                ),
                KitchenElement(
                    id = "island_cooktop",
                    name = "مسطح الجزيرة الحثي",
                    type = KitchenElementType.COOKTOP_INDUCTION,
                    position = Vector3(2400f, 880f, 2000f),
                    rotationY = 0f,
                    dimensions = Vector3(600f, 60f, 520f),
                    material = MaterialFinish.BLACK_GRANITE
                ),
                // Back tall wall (North)
                KitchenElement(
                    id = "tall_pantry_1",
                    name = "برج مؤونة طويل",
                    type = KitchenElementType.TALL_PANTRY,
                    position = Vector3(1500f, 0f, 300f),
                    rotationY = 0f,
                    dimensions = Vector3(600f, 2150f, 600f),
                    material = MaterialFinish.WALNUT_DARK
                ),
                KitchenElement(
                    id = "tall_oven_1",
                    name = "برج الفرن والميكروويف",
                    type = KitchenElementType.TALL_OVEN_TOWER,
                    position = Vector3(2100f, 0f, 300f),
                    rotationY = 0f,
                    dimensions = Vector3(600f, 2150f, 600f),
                    material = MaterialFinish.WALNUT_DARK
                ),
                KitchenElement(
                    id = "tall_fridge_1",
                    name = "ثلاجة ستانلس مزدوجة",
                    type = KitchenElementType.REFRIGERATOR_FROST,
                    position = Vector3(2750f, 0f, 350f),
                    rotationY = 0f,
                    dimensions = Vector3(750f, 1850f, 700f),
                    material = MaterialFinish.STAINLESS_STEEL
                )
            )

            return KitchenProject(
                id = "template_island",
                name = "مطبخ الجزيرة الفاخر (Chef's Island)",
                room = room,
                elements = elements
            )
        }

        fun createUShapedTemplate(): KitchenProject {
            val room = RoomStructure(
                widthMm = 3800f,
                depthMm = 3400f,
                heightMm = 2600f,
                floorMaterial = MaterialFinish.PARQUET_OAK_FLOOR,
                wallFinish = MaterialFinish.WHITE_HIGH_GLOSS
            )

            val elements = listOf(
                // North Wall: Left drawers (x=1050, w=700 -> 700..1400), Central Sink (x=1850, w=900 -> 1400..2300), Right Base (x=2650, w=700 -> 2300..3000)
                KitchenElement(
                    id = "u_base_north_left",
                    name = "خزانة أدراج شمالية",
                    type = KitchenElementType.BASE_3_DRAWERS,
                    position = Vector3(1050f, 0f, 300f),
                    rotationY = 0f,
                    dimensions = Vector3(700f, 850f, 600f),
                    material = MaterialFinish.SAGE_GREEN,
                    countertopMaterial = MaterialFinish.CARRARA_MARBLE
                ),
                KitchenElement(
                    id = "u_sink",
                    name = "حوض غسيل مركزي",
                    type = KitchenElementType.BASE_SINK_UNIT,
                    position = Vector3(1850f, 0f, 300f),
                    rotationY = 0f,
                    dimensions = Vector3(900f, 850f, 600f),
                    material = MaterialFinish.SAGE_GREEN,
                    countertopMaterial = MaterialFinish.CARRARA_MARBLE
                ),
                KitchenElement(
                    id = "u_base_north_right",
                    name = "خزانة تحضير شمالية",
                    type = KitchenElementType.BASE_DOUBLE_DOOR,
                    position = Vector3(2650f, 0f, 300f),
                    rotationY = 0f,
                    dimensions = Vector3(700f, 850f, 600f),
                    material = MaterialFinish.SAGE_GREEN,
                    countertopMaterial = MaterialFinish.CARRARA_MARBLE
                ),
                // West Wall (X = 300, rotY = 90 -> Z spans 1050..1950)
                KitchenElement(
                    id = "u_base_west",
                    name = "خزانة غربية",
                    type = KitchenElementType.BASE_DOUBLE_DOOR,
                    position = Vector3(300f, 0f, 1500f),
                    rotationY = 90f,
                    dimensions = Vector3(900f, 850f, 600f),
                    material = MaterialFinish.SAGE_GREEN,
                    countertopMaterial = MaterialFinish.CARRARA_MARBLE
                ),
                // East Wall (X = 3500, rotY = 270 -> Z spans 1050..1950)
                KitchenElement(
                    id = "u_base_east",
                    name = "خزانة شرقية مع مسطح",
                    type = KitchenElementType.BASE_DOUBLE_DOOR,
                    position = Vector3(3500f, 0f, 1500f),
                    rotationY = 270f,
                    dimensions = Vector3(900f, 850f, 600f),
                    material = MaterialFinish.SAGE_GREEN,
                    countertopMaterial = MaterialFinish.CARRARA_MARBLE
                ),
                // Upper Wall Cabinets on North Wall
                KitchenElement(
                    id = "u_wall_north_1",
                    name = "خزانة علوية زجاجية",
                    type = KitchenElementType.WALL_GLASS_BLACK_FRAME,
                    position = Vector3(1850f, 1450f, 175f),
                    rotationY = 0f,
                    dimensions = Vector3(900f, 720f, 350f),
                    material = MaterialFinish.SMOKED_GLASS_ALU
                )
            )

            return KitchenProject(
                id = "template_u_shape",
                name = "مطبخ متكامل شكل U (Ergonomic U-Shape)",
                room = room,
                elements = elements
            )
        }

        fun createLinearTemplate(): KitchenProject {
            val room = RoomStructure(
                widthMm = 3600f,
                depthMm = 2600f,
                heightMm = 2600f,
                floorMaterial = MaterialFinish.MARBLE_WHITE_FLOOR,
                wallFinish = MaterialFinish.WHITE_HIGH_GLOSS
            )

            val elements = listOf(
                KitchenElement(
                    id = "lin_sink",
                    name = "حوض الغسيل",
                    type = KitchenElementType.BASE_SINK_UNIT,
                    position = Vector3(800f, 0f, 300f),
                    rotationY = 0f,
                    dimensions = Vector3(800f, 850f, 600f),
                    material = MaterialFinish.NAVY_BLUE,
                    countertopMaterial = MaterialFinish.CARRARA_MARBLE
                ),
                KitchenElement(
                    id = "lin_drawers",
                    name = "خزانة أدراج",
                    type = KitchenElementType.BASE_3_DRAWERS,
                    position = Vector3(1500f, 0f, 300f),
                    rotationY = 0f,
                    dimensions = Vector3(600f, 850f, 600f),
                    material = MaterialFinish.NAVY_BLUE,
                    countertopMaterial = MaterialFinish.CARRARA_MARBLE
                ),
                KitchenElement(
                    id = "lin_under_oven",
                    name = "خزانة فرن سفلي",
                    type = KitchenElementType.BASE_UNDER_OVEN,
                    position = Vector3(2100f, 0f, 300f),
                    rotationY = 0f,
                    dimensions = Vector3(600f, 850f, 600f),
                    material = MaterialFinish.NAVY_BLUE,
                    countertopMaterial = MaterialFinish.CARRARA_MARBLE
                ),
                KitchenElement(
                    id = "lin_cooktop",
                    name = "مسطح الطهي",
                    type = KitchenElementType.COOKTOP_INDUCTION,
                    position = Vector3(2100f, 850f, 300f),
                    rotationY = 0f,
                    dimensions = Vector3(600f, 60f, 520f),
                    material = MaterialFinish.BLACK_GRANITE
                ),
                KitchenElement(
                    id = "lin_fridge",
                    name = "ثلاجة ستانلس",
                    type = KitchenElementType.REFRIGERATOR_FROST,
                    position = Vector3(2800f, 0f, 350f),
                    rotationY = 0f,
                    dimensions = Vector3(700f, 1850f, 700f),
                    material = MaterialFinish.STAINLESS_STEEL
                )
            )

            return KitchenProject(
                id = "template_linear",
                name = "مطبخ خطي للشقق (Linear Studio)",
                room = room,
                elements = elements
            )
        }
    }
}
