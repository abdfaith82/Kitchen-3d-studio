package com.example.domain.repository

import com.example.domain.model.KitchenElement
import com.example.domain.model.KitchenGroup
import com.example.domain.model.RoomStructure
import kotlinx.coroutines.flow.Flow

data class BillOfMaterialsItem(
    val name: String,
    val typeCode: String,
    val quantity: Int,
    val dimensionsMm: String,
    val finishName: String,
    val categoryName: String
)

data class BillOfMaterials(
    val projectName: String,
    val items: List<BillOfMaterialsItem>,
    val totalCabinetsCount: Int,
    val totalLinearMeters: Float, // in meters
    val countertopAreaSquareMeters: Float,
    val generatedDate: String
)

data class KitchenProject(
    val id: String,
    val name: String,
    val room: RoomStructure,
    val elements: List<KitchenElement>,
    val groups: List<KitchenGroup> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

interface KitchenProjectRepository {
    fun getProject(): Flow<KitchenProject>
    suspend fun updateProject(project: KitchenProject)
    fun getSampleTemplates(): List<KitchenProject>
    suspend fun loadTemplate(templateId: String)
    fun generateBillOfMaterials(project: KitchenProject): BillOfMaterials
    suspend fun saveCameraState(projectId: String, cameraState: com.example.data.local.relation.SavedCameraState) {}
    fun getSavedCameraState(projectId: String): Flow<com.example.data.local.relation.SavedCameraState?> = kotlinx.coroutines.flow.flowOf(null)
    suspend fun saveAutoSaveSnapshot(
        project: KitchenProject,
        cameraState: com.example.data.local.relation.SavedCameraState?,
        snapshot: com.example.data.local.entity.AutoSaveSnapshotEntity
    ) {}
    fun observeAutoSaveSnapshots(limit: Int = 12): Flow<List<com.example.data.local.entity.AutoSaveSnapshotEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
    suspend fun getLatestAutoSaveSnapshot(projectId: String): com.example.data.local.entity.AutoSaveSnapshotEntity? = null
    fun getAllSavedProjects(): Flow<List<com.example.data.local.entity.ProjectMetadataEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
    suspend fun loadProjectById(projectId: String) {}
    suspend fun saveProjectAs(name: String, project: KitchenProject): KitchenProject = project
    suspend fun createNewProject(name: String, room: RoomStructure = RoomStructure()): KitchenProject = KitchenProject(id = java.util.UUID.randomUUID().toString(), name = name, room = room, elements = emptyList())
    suspend fun deleteProjectById(projectId: String) {}
    suspend fun getProjectById(projectId: String): KitchenProject? = null
}
