package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.AutoSaveSnapshotEntity
import com.example.data.local.entity.FurnitureAssetEntity
import com.example.data.local.entity.ProjectMetadataEntity
import com.example.data.local.entity.WallOpeningEntity
import com.example.data.local.relation.ProjectWithAssets
import kotlinx.coroutines.flow.Flow

@Dao
interface KitchenProjectDao {

    @Transaction
    @Query("SELECT * FROM project_metadata WHERE id = :projectId")
    fun getProjectWithAssets(projectId: String): Flow<ProjectWithAssets?>

    @Transaction
    @Query("SELECT * FROM project_metadata WHERE isCurrentActive = 1 ORDER BY updatedAt DESC LIMIT 1")
    fun getActiveProjectWithAssets(): Flow<ProjectWithAssets?>

    @Query("SELECT * FROM project_metadata ORDER BY updatedAt DESC")
    fun getAllProjectMetadata(): Flow<List<ProjectMetadataEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjectMetadata(metadata: ProjectMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFurnitureAssets(assets: List<FurnitureAssetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallOpenings(openings: List<WallOpeningEntity>)

    @Query("DELETE FROM furniture_assets WHERE projectId = :projectId")
    suspend fun deleteFurnitureAssets(projectId: String)

    @Query("DELETE FROM wall_openings WHERE projectId = :projectId")
    suspend fun deleteWallOpenings(projectId: String)

    @Query("DELETE FROM project_metadata WHERE id = :projectId")
    suspend fun deleteProject(projectId: String)

    // ==========================================
    // AUTO-SAVE SNAPSHOTS QUERIES & TRANSACTIONS
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAutoSaveSnapshot(snapshot: AutoSaveSnapshotEntity)

    @Query("SELECT * FROM project_autosave_snapshots WHERE projectId = :projectId ORDER BY savedAtTimestamp DESC")
    fun observeAutoSaveSnapshots(projectId: String): Flow<List<AutoSaveSnapshotEntity>>

    @Query("SELECT * FROM project_autosave_snapshots ORDER BY savedAtTimestamp DESC LIMIT :limit")
    fun observeRecentAutoSaveSnapshots(limit: Int = 12): Flow<List<AutoSaveSnapshotEntity>>

    @Query("SELECT * FROM project_autosave_snapshots WHERE projectId = :projectId ORDER BY savedAtTimestamp DESC LIMIT 1")
    suspend fun getLatestAutoSaveSnapshot(projectId: String): AutoSaveSnapshotEntity?

    @Query("SELECT * FROM project_autosave_snapshots ORDER BY savedAtTimestamp DESC LIMIT 1")
    suspend fun getMostRecentAutoSaveSnapshot(): AutoSaveSnapshotEntity?

    @Query("""
        DELETE FROM project_autosave_snapshots 
        WHERE projectId = :projectId 
          AND snapshotId NOT IN (
              SELECT snapshotId FROM project_autosave_snapshots 
              WHERE projectId = :projectId 
              ORDER BY savedAtTimestamp DESC 
              LIMIT :maxSnapshots
          )
    """)
    suspend fun pruneOldSnapshots(projectId: String, maxSnapshots: Int = 12)

    @Query("""
        UPDATE project_metadata 
        SET cameraMode = :mode, 
            cameraYawDegrees = :yaw, 
            cameraPitchDegrees = :pitch, 
            cameraZoomFactor = :zoom, 
            cameraPanX = :panX, 
            cameraPanY = :panY, 
            walkPosX = :walkX, 
            walkPosY = :walkY, 
            walkPosZ = :walkZ, 
            walkLookYaw = :walkYaw, 
            walkLookPitch = :walkPitch, 
            updatedAt = :updatedAt 
        WHERE id = :projectId
    """)
    suspend fun updateSavedCamera(
        projectId: String,
        mode: String,
        yaw: Float,
        pitch: Float,
        zoom: Float,
        panX: Float,
        panY: Float,
        walkX: Float,
        walkY: Float,
        walkZ: Float,
        walkYaw: Float,
        walkPitch: Float,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE project_metadata SET isCurrentActive = 0 WHERE id != :projectId")
    suspend fun deactivateOtherProjects(projectId: String)

    @Query("UPDATE project_metadata SET isCurrentActive = 1 WHERE id = :projectId")
    suspend fun setActiveProject(projectId: String)

    @Transaction
    suspend fun saveFullProjectWithAssets(
        metadata: ProjectMetadataEntity,
        assets: List<FurnitureAssetEntity>,
        openings: List<WallOpeningEntity>
    ) {
        insertProjectMetadata(metadata)
        deleteFurnitureAssets(metadata.id)
        if (assets.isNotEmpty()) {
            insertFurnitureAssets(assets)
        }
        deleteWallOpenings(metadata.id)
        if (openings.isNotEmpty()) {
            insertWallOpenings(openings)
        }
        deactivateOtherProjects(metadata.id)
    }

    /**
     * Atomically writes both the serialized [AutoSaveSnapshotEntity] and the normalized
     * relational Room tables ([ProjectMetadataEntity], [FurnitureAssetEntity], [WallOpeningEntity]),
     * pruning older checkpoints beyond [maxHistoryCount].
     */
    @Transaction
    suspend fun saveAutoSaveEntryWithRelationalSync(
        metadata: ProjectMetadataEntity,
        assets: List<FurnitureAssetEntity>,
        openings: List<WallOpeningEntity>,
        snapshot: AutoSaveSnapshotEntity,
        maxHistoryCount: Int = 12
    ) {
        saveFullProjectWithAssets(metadata, assets, openings)
        upsertAutoSaveSnapshot(snapshot)
        pruneOldSnapshots(metadata.id, maxHistoryCount)
    }
}
