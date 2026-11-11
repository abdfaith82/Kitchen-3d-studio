package com.example.`data`.local.dao

import androidx.collection.ArrayMap
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.appendPlaceholders
import androidx.room.util.getColumnIndex
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performInTransactionSuspending
import androidx.room.util.performSuspending
import androidx.room.util.recursiveFetchArrayMap
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.example.`data`.local.entity.AutoSaveSnapshotEntity
import com.example.`data`.local.entity.FurnitureAssetEntity
import com.example.`data`.local.entity.ProjectMetadataEntity
import com.example.`data`.local.entity.WallOpeningEntity
import com.example.`data`.local.relation.ProjectWithAssets
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlin.text.StringBuilder
import kotlinx.coroutines.flow.Flow

@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL", "RestrictedApi"])
public class KitchenProjectDao_Impl(
  __db: RoomDatabase,
) : KitchenProjectDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfProjectMetadataEntity: EntityInsertAdapter<ProjectMetadataEntity>

  private val __insertAdapterOfFurnitureAssetEntity: EntityInsertAdapter<FurnitureAssetEntity>

  private val __insertAdapterOfWallOpeningEntity: EntityInsertAdapter<WallOpeningEntity>

  private val __insertAdapterOfAutoSaveSnapshotEntity: EntityInsertAdapter<AutoSaveSnapshotEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfProjectMetadataEntity = object :
        EntityInsertAdapter<ProjectMetadataEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `project_metadata` (`id`,`name`,`createdAt`,`updatedAt`,`isCurrentActive`,`roomWidthMm`,`roomDepthMm`,`roomHeightMm`,`wallThicknessMm`,`floorMaterialId`,`wallFinishId`,`cameraMode`,`cameraEyeX`,`cameraEyeY`,`cameraEyeZ`,`cameraTargetX`,`cameraTargetY`,`cameraTargetZ`,`cameraYawDegrees`,`cameraPitchDegrees`,`cameraZoomFactor`,`cameraPanX`,`cameraPanY`,`walkEyeHeightMm`,`walkPosX`,`walkPosY`,`walkPosZ`,`walkLookYaw`,`walkLookPitch`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ProjectMetadataEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindLong(3, entity.createdAt)
        statement.bindLong(4, entity.updatedAt)
        val _tmp: Int = if (entity.isCurrentActive) 1 else 0
        statement.bindLong(5, _tmp.toLong())
        statement.bindDouble(6, entity.roomWidthMm.toDouble())
        statement.bindDouble(7, entity.roomDepthMm.toDouble())
        statement.bindDouble(8, entity.roomHeightMm.toDouble())
        statement.bindDouble(9, entity.wallThicknessMm.toDouble())
        statement.bindText(10, entity.floorMaterialId)
        statement.bindText(11, entity.wallFinishId)
        statement.bindText(12, entity.cameraMode)
        statement.bindDouble(13, entity.cameraEyeX.toDouble())
        statement.bindDouble(14, entity.cameraEyeY.toDouble())
        statement.bindDouble(15, entity.cameraEyeZ.toDouble())
        statement.bindDouble(16, entity.cameraTargetX.toDouble())
        statement.bindDouble(17, entity.cameraTargetY.toDouble())
        statement.bindDouble(18, entity.cameraTargetZ.toDouble())
        statement.bindDouble(19, entity.cameraYawDegrees.toDouble())
        statement.bindDouble(20, entity.cameraPitchDegrees.toDouble())
        statement.bindDouble(21, entity.cameraZoomFactor.toDouble())
        statement.bindDouble(22, entity.cameraPanX.toDouble())
        statement.bindDouble(23, entity.cameraPanY.toDouble())
        statement.bindDouble(24, entity.walkEyeHeightMm.toDouble())
        statement.bindDouble(25, entity.walkPosX.toDouble())
        statement.bindDouble(26, entity.walkPosY.toDouble())
        statement.bindDouble(27, entity.walkPosZ.toDouble())
        statement.bindDouble(28, entity.walkLookYaw.toDouble())
        statement.bindDouble(29, entity.walkLookPitch.toDouble())
      }
    }
    this.__insertAdapterOfFurnitureAssetEntity = object :
        EntityInsertAdapter<FurnitureAssetEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `furniture_assets` (`id`,`projectId`,`name`,`typeCode`,`category`,`layer`,`posX`,`posY`,`posZ`,`dimWidthMm`,`dimHeightMm`,`dimDepthMm`,`rotationY`,`materialId`,`countertopMaterialId`,`isLocked`,`isVisible`,`groupId`,`sortOrder`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: FurnitureAssetEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.projectId)
        statement.bindText(3, entity.name)
        statement.bindText(4, entity.typeCode)
        statement.bindText(5, entity.category)
        statement.bindText(6, entity.layer)
        statement.bindDouble(7, entity.posX.toDouble())
        statement.bindDouble(8, entity.posY.toDouble())
        statement.bindDouble(9, entity.posZ.toDouble())
        statement.bindDouble(10, entity.dimWidthMm.toDouble())
        statement.bindDouble(11, entity.dimHeightMm.toDouble())
        statement.bindDouble(12, entity.dimDepthMm.toDouble())
        statement.bindDouble(13, entity.rotationY.toDouble())
        statement.bindText(14, entity.materialId)
        val _tmpCountertopMaterialId: String? = entity.countertopMaterialId
        if (_tmpCountertopMaterialId == null) {
          statement.bindNull(15)
        } else {
          statement.bindText(15, _tmpCountertopMaterialId)
        }
        val _tmp: Int = if (entity.isLocked) 1 else 0
        statement.bindLong(16, _tmp.toLong())
        val _tmp_1: Int = if (entity.isVisible) 1 else 0
        statement.bindLong(17, _tmp_1.toLong())
        val _tmpGroupId: String? = entity.groupId
        if (_tmpGroupId == null) {
          statement.bindNull(18)
        } else {
          statement.bindText(18, _tmpGroupId)
        }
        statement.bindLong(19, entity.sortOrder.toLong())
      }
    }
    this.__insertAdapterOfWallOpeningEntity = object : EntityInsertAdapter<WallOpeningEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `wall_openings` (`id`,`projectId`,`wallSide`,`openingType`,`offsetMm`,`widthMm`,`heightMm`,`sillHeightMm`) VALUES (?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: WallOpeningEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.projectId)
        statement.bindText(3, entity.wallSide)
        statement.bindText(4, entity.openingType)
        statement.bindDouble(5, entity.offsetMm.toDouble())
        statement.bindDouble(6, entity.widthMm.toDouble())
        statement.bindDouble(7, entity.heightMm.toDouble())
        statement.bindDouble(8, entity.sillHeightMm.toDouble())
      }
    }
    this.__insertAdapterOfAutoSaveSnapshotEntity = object :
        EntityInsertAdapter<AutoSaveSnapshotEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `project_autosave_snapshots` (`snapshotId`,`projectId`,`projectName`,`serializedStateJson`,`roomWidthMm`,`roomDepthMm`,`roomHeightMm`,`wallThicknessMm`,`furnitureCount`,`stateChecksumCrc32`,`payloadSizeBytes`,`intervalSeconds`,`triggerType`,`savedAtTimestamp`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: AutoSaveSnapshotEntity) {
        statement.bindText(1, entity.snapshotId)
        statement.bindText(2, entity.projectId)
        statement.bindText(3, entity.projectName)
        statement.bindText(4, entity.serializedStateJson)
        statement.bindDouble(5, entity.roomWidthMm.toDouble())
        statement.bindDouble(6, entity.roomDepthMm.toDouble())
        statement.bindDouble(7, entity.roomHeightMm.toDouble())
        statement.bindDouble(8, entity.wallThicknessMm.toDouble())
        statement.bindLong(9, entity.furnitureCount.toLong())
        statement.bindLong(10, entity.stateChecksumCrc32)
        statement.bindLong(11, entity.payloadSizeBytes.toLong())
        statement.bindLong(12, entity.intervalSeconds.toLong())
        statement.bindText(13, entity.triggerType)
        statement.bindLong(14, entity.savedAtTimestamp)
      }
    }
  }

  public override suspend fun insertProjectMetadata(metadata: ProjectMetadataEntity): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfProjectMetadataEntity.insert(_connection, metadata)
  }

  public override suspend fun insertFurnitureAssets(assets: List<FurnitureAssetEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfFurnitureAssetEntity.insert(_connection, assets)
  }

  public override suspend fun insertWallOpenings(openings: List<WallOpeningEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfWallOpeningEntity.insert(_connection, openings)
  }

  public override suspend fun upsertAutoSaveSnapshot(snapshot: AutoSaveSnapshotEntity): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfAutoSaveSnapshotEntity.insert(_connection, snapshot)
  }

  public override suspend fun saveFullProjectWithAssets(
    metadata: ProjectMetadataEntity,
    assets: List<FurnitureAssetEntity>,
    openings: List<WallOpeningEntity>,
  ): Unit = performInTransactionSuspending(__db) {
    super@KitchenProjectDao_Impl.saveFullProjectWithAssets(metadata, assets, openings)
  }

  public override suspend fun saveAutoSaveEntryWithRelationalSync(
    metadata: ProjectMetadataEntity,
    assets: List<FurnitureAssetEntity>,
    openings: List<WallOpeningEntity>,
    snapshot: AutoSaveSnapshotEntity,
    maxHistoryCount: Int,
  ): Unit = performInTransactionSuspending(__db) {
    super@KitchenProjectDao_Impl.saveAutoSaveEntryWithRelationalSync(metadata, assets, openings,
        snapshot, maxHistoryCount)
  }

  public override fun getProjectWithAssets(projectId: String): Flow<ProjectWithAssets?> {
    val _sql: String = "SELECT * FROM project_metadata WHERE id = ?"
    return createFlow(__db, true, arrayOf("furniture_assets", "wall_openings", "project_metadata"))
        { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, projectId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _columnIndexOfIsCurrentActive: Int = getColumnIndexOrThrow(_stmt, "isCurrentActive")
        val _columnIndexOfRoomWidthMm: Int = getColumnIndexOrThrow(_stmt, "roomWidthMm")
        val _columnIndexOfRoomDepthMm: Int = getColumnIndexOrThrow(_stmt, "roomDepthMm")
        val _columnIndexOfRoomHeightMm: Int = getColumnIndexOrThrow(_stmt, "roomHeightMm")
        val _columnIndexOfWallThicknessMm: Int = getColumnIndexOrThrow(_stmt, "wallThicknessMm")
        val _columnIndexOfFloorMaterialId: Int = getColumnIndexOrThrow(_stmt, "floorMaterialId")
        val _columnIndexOfWallFinishId: Int = getColumnIndexOrThrow(_stmt, "wallFinishId")
        val _columnIndexOfCameraMode: Int = getColumnIndexOrThrow(_stmt, "cameraMode")
        val _columnIndexOfCameraEyeX: Int = getColumnIndexOrThrow(_stmt, "cameraEyeX")
        val _columnIndexOfCameraEyeY: Int = getColumnIndexOrThrow(_stmt, "cameraEyeY")
        val _columnIndexOfCameraEyeZ: Int = getColumnIndexOrThrow(_stmt, "cameraEyeZ")
        val _columnIndexOfCameraTargetX: Int = getColumnIndexOrThrow(_stmt, "cameraTargetX")
        val _columnIndexOfCameraTargetY: Int = getColumnIndexOrThrow(_stmt, "cameraTargetY")
        val _columnIndexOfCameraTargetZ: Int = getColumnIndexOrThrow(_stmt, "cameraTargetZ")
        val _columnIndexOfCameraYawDegrees: Int = getColumnIndexOrThrow(_stmt, "cameraYawDegrees")
        val _columnIndexOfCameraPitchDegrees: Int = getColumnIndexOrThrow(_stmt,
            "cameraPitchDegrees")
        val _columnIndexOfCameraZoomFactor: Int = getColumnIndexOrThrow(_stmt, "cameraZoomFactor")
        val _columnIndexOfCameraPanX: Int = getColumnIndexOrThrow(_stmt, "cameraPanX")
        val _columnIndexOfCameraPanY: Int = getColumnIndexOrThrow(_stmt, "cameraPanY")
        val _columnIndexOfWalkEyeHeightMm: Int = getColumnIndexOrThrow(_stmt, "walkEyeHeightMm")
        val _columnIndexOfWalkPosX: Int = getColumnIndexOrThrow(_stmt, "walkPosX")
        val _columnIndexOfWalkPosY: Int = getColumnIndexOrThrow(_stmt, "walkPosY")
        val _columnIndexOfWalkPosZ: Int = getColumnIndexOrThrow(_stmt, "walkPosZ")
        val _columnIndexOfWalkLookYaw: Int = getColumnIndexOrThrow(_stmt, "walkLookYaw")
        val _columnIndexOfWalkLookPitch: Int = getColumnIndexOrThrow(_stmt, "walkLookPitch")
        val _collectionFurnitureAssets: ArrayMap<String, MutableList<FurnitureAssetEntity>> =
            ArrayMap<String, MutableList<FurnitureAssetEntity>>()
        val _collectionOpenings: ArrayMap<String, MutableList<WallOpeningEntity>> =
            ArrayMap<String, MutableList<WallOpeningEntity>>()
        while (_stmt.step()) {
          val _tmpKey: String
          _tmpKey = _stmt.getText(_columnIndexOfId)
          if (!_collectionFurnitureAssets.containsKey(_tmpKey)) {
            _collectionFurnitureAssets.put(_tmpKey, mutableListOf())
          }
          val _tmpKey_1: String
          _tmpKey_1 = _stmt.getText(_columnIndexOfId)
          if (!_collectionOpenings.containsKey(_tmpKey_1)) {
            _collectionOpenings.put(_tmpKey_1, mutableListOf())
          }
        }
        _stmt.reset()
        __fetchRelationshipfurnitureAssetsAscomExampleDataLocalEntityFurnitureAssetEntity(_connection,
            _collectionFurnitureAssets)
        __fetchRelationshipwallOpeningsAscomExampleDataLocalEntityWallOpeningEntity(_connection,
            _collectionOpenings)
        val _result: ProjectWithAssets?
        if (_stmt.step()) {
          val _tmpMetadata: ProjectMetadataEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpIsCurrentActive: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsCurrentActive).toInt()
          _tmpIsCurrentActive = _tmp != 0
          val _tmpRoomWidthMm: Float
          _tmpRoomWidthMm = _stmt.getDouble(_columnIndexOfRoomWidthMm).toFloat()
          val _tmpRoomDepthMm: Float
          _tmpRoomDepthMm = _stmt.getDouble(_columnIndexOfRoomDepthMm).toFloat()
          val _tmpRoomHeightMm: Float
          _tmpRoomHeightMm = _stmt.getDouble(_columnIndexOfRoomHeightMm).toFloat()
          val _tmpWallThicknessMm: Float
          _tmpWallThicknessMm = _stmt.getDouble(_columnIndexOfWallThicknessMm).toFloat()
          val _tmpFloorMaterialId: String
          _tmpFloorMaterialId = _stmt.getText(_columnIndexOfFloorMaterialId)
          val _tmpWallFinishId: String
          _tmpWallFinishId = _stmt.getText(_columnIndexOfWallFinishId)
          val _tmpCameraMode: String
          _tmpCameraMode = _stmt.getText(_columnIndexOfCameraMode)
          val _tmpCameraEyeX: Float
          _tmpCameraEyeX = _stmt.getDouble(_columnIndexOfCameraEyeX).toFloat()
          val _tmpCameraEyeY: Float
          _tmpCameraEyeY = _stmt.getDouble(_columnIndexOfCameraEyeY).toFloat()
          val _tmpCameraEyeZ: Float
          _tmpCameraEyeZ = _stmt.getDouble(_columnIndexOfCameraEyeZ).toFloat()
          val _tmpCameraTargetX: Float
          _tmpCameraTargetX = _stmt.getDouble(_columnIndexOfCameraTargetX).toFloat()
          val _tmpCameraTargetY: Float
          _tmpCameraTargetY = _stmt.getDouble(_columnIndexOfCameraTargetY).toFloat()
          val _tmpCameraTargetZ: Float
          _tmpCameraTargetZ = _stmt.getDouble(_columnIndexOfCameraTargetZ).toFloat()
          val _tmpCameraYawDegrees: Float
          _tmpCameraYawDegrees = _stmt.getDouble(_columnIndexOfCameraYawDegrees).toFloat()
          val _tmpCameraPitchDegrees: Float
          _tmpCameraPitchDegrees = _stmt.getDouble(_columnIndexOfCameraPitchDegrees).toFloat()
          val _tmpCameraZoomFactor: Float
          _tmpCameraZoomFactor = _stmt.getDouble(_columnIndexOfCameraZoomFactor).toFloat()
          val _tmpCameraPanX: Float
          _tmpCameraPanX = _stmt.getDouble(_columnIndexOfCameraPanX).toFloat()
          val _tmpCameraPanY: Float
          _tmpCameraPanY = _stmt.getDouble(_columnIndexOfCameraPanY).toFloat()
          val _tmpWalkEyeHeightMm: Float
          _tmpWalkEyeHeightMm = _stmt.getDouble(_columnIndexOfWalkEyeHeightMm).toFloat()
          val _tmpWalkPosX: Float
          _tmpWalkPosX = _stmt.getDouble(_columnIndexOfWalkPosX).toFloat()
          val _tmpWalkPosY: Float
          _tmpWalkPosY = _stmt.getDouble(_columnIndexOfWalkPosY).toFloat()
          val _tmpWalkPosZ: Float
          _tmpWalkPosZ = _stmt.getDouble(_columnIndexOfWalkPosZ).toFloat()
          val _tmpWalkLookYaw: Float
          _tmpWalkLookYaw = _stmt.getDouble(_columnIndexOfWalkLookYaw).toFloat()
          val _tmpWalkLookPitch: Float
          _tmpWalkLookPitch = _stmt.getDouble(_columnIndexOfWalkLookPitch).toFloat()
          _tmpMetadata =
              ProjectMetadataEntity(_tmpId,_tmpName,_tmpCreatedAt,_tmpUpdatedAt,_tmpIsCurrentActive,_tmpRoomWidthMm,_tmpRoomDepthMm,_tmpRoomHeightMm,_tmpWallThicknessMm,_tmpFloorMaterialId,_tmpWallFinishId,_tmpCameraMode,_tmpCameraEyeX,_tmpCameraEyeY,_tmpCameraEyeZ,_tmpCameraTargetX,_tmpCameraTargetY,_tmpCameraTargetZ,_tmpCameraYawDegrees,_tmpCameraPitchDegrees,_tmpCameraZoomFactor,_tmpCameraPanX,_tmpCameraPanY,_tmpWalkEyeHeightMm,_tmpWalkPosX,_tmpWalkPosY,_tmpWalkPosZ,_tmpWalkLookYaw,_tmpWalkLookPitch)
          val _tmpFurnitureAssetsCollection: MutableList<FurnitureAssetEntity>
          val _tmpKey_2: String
          _tmpKey_2 = _stmt.getText(_columnIndexOfId)
          _tmpFurnitureAssetsCollection = _collectionFurnitureAssets.getValue(_tmpKey_2)
          val _tmpOpeningsCollection: MutableList<WallOpeningEntity>
          val _tmpKey_3: String
          _tmpKey_3 = _stmt.getText(_columnIndexOfId)
          _tmpOpeningsCollection = _collectionOpenings.getValue(_tmpKey_3)
          _result =
              ProjectWithAssets(_tmpMetadata,_tmpFurnitureAssetsCollection,_tmpOpeningsCollection)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getActiveProjectWithAssets(): Flow<ProjectWithAssets?> {
    val _sql: String =
        "SELECT * FROM project_metadata WHERE isCurrentActive = 1 ORDER BY updatedAt DESC LIMIT 1"
    return createFlow(__db, true, arrayOf("furniture_assets", "wall_openings", "project_metadata"))
        { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _columnIndexOfIsCurrentActive: Int = getColumnIndexOrThrow(_stmt, "isCurrentActive")
        val _columnIndexOfRoomWidthMm: Int = getColumnIndexOrThrow(_stmt, "roomWidthMm")
        val _columnIndexOfRoomDepthMm: Int = getColumnIndexOrThrow(_stmt, "roomDepthMm")
        val _columnIndexOfRoomHeightMm: Int = getColumnIndexOrThrow(_stmt, "roomHeightMm")
        val _columnIndexOfWallThicknessMm: Int = getColumnIndexOrThrow(_stmt, "wallThicknessMm")
        val _columnIndexOfFloorMaterialId: Int = getColumnIndexOrThrow(_stmt, "floorMaterialId")
        val _columnIndexOfWallFinishId: Int = getColumnIndexOrThrow(_stmt, "wallFinishId")
        val _columnIndexOfCameraMode: Int = getColumnIndexOrThrow(_stmt, "cameraMode")
        val _columnIndexOfCameraEyeX: Int = getColumnIndexOrThrow(_stmt, "cameraEyeX")
        val _columnIndexOfCameraEyeY: Int = getColumnIndexOrThrow(_stmt, "cameraEyeY")
        val _columnIndexOfCameraEyeZ: Int = getColumnIndexOrThrow(_stmt, "cameraEyeZ")
        val _columnIndexOfCameraTargetX: Int = getColumnIndexOrThrow(_stmt, "cameraTargetX")
        val _columnIndexOfCameraTargetY: Int = getColumnIndexOrThrow(_stmt, "cameraTargetY")
        val _columnIndexOfCameraTargetZ: Int = getColumnIndexOrThrow(_stmt, "cameraTargetZ")
        val _columnIndexOfCameraYawDegrees: Int = getColumnIndexOrThrow(_stmt, "cameraYawDegrees")
        val _columnIndexOfCameraPitchDegrees: Int = getColumnIndexOrThrow(_stmt,
            "cameraPitchDegrees")
        val _columnIndexOfCameraZoomFactor: Int = getColumnIndexOrThrow(_stmt, "cameraZoomFactor")
        val _columnIndexOfCameraPanX: Int = getColumnIndexOrThrow(_stmt, "cameraPanX")
        val _columnIndexOfCameraPanY: Int = getColumnIndexOrThrow(_stmt, "cameraPanY")
        val _columnIndexOfWalkEyeHeightMm: Int = getColumnIndexOrThrow(_stmt, "walkEyeHeightMm")
        val _columnIndexOfWalkPosX: Int = getColumnIndexOrThrow(_stmt, "walkPosX")
        val _columnIndexOfWalkPosY: Int = getColumnIndexOrThrow(_stmt, "walkPosY")
        val _columnIndexOfWalkPosZ: Int = getColumnIndexOrThrow(_stmt, "walkPosZ")
        val _columnIndexOfWalkLookYaw: Int = getColumnIndexOrThrow(_stmt, "walkLookYaw")
        val _columnIndexOfWalkLookPitch: Int = getColumnIndexOrThrow(_stmt, "walkLookPitch")
        val _collectionFurnitureAssets: ArrayMap<String, MutableList<FurnitureAssetEntity>> =
            ArrayMap<String, MutableList<FurnitureAssetEntity>>()
        val _collectionOpenings: ArrayMap<String, MutableList<WallOpeningEntity>> =
            ArrayMap<String, MutableList<WallOpeningEntity>>()
        while (_stmt.step()) {
          val _tmpKey: String
          _tmpKey = _stmt.getText(_columnIndexOfId)
          if (!_collectionFurnitureAssets.containsKey(_tmpKey)) {
            _collectionFurnitureAssets.put(_tmpKey, mutableListOf())
          }
          val _tmpKey_1: String
          _tmpKey_1 = _stmt.getText(_columnIndexOfId)
          if (!_collectionOpenings.containsKey(_tmpKey_1)) {
            _collectionOpenings.put(_tmpKey_1, mutableListOf())
          }
        }
        _stmt.reset()
        __fetchRelationshipfurnitureAssetsAscomExampleDataLocalEntityFurnitureAssetEntity(_connection,
            _collectionFurnitureAssets)
        __fetchRelationshipwallOpeningsAscomExampleDataLocalEntityWallOpeningEntity(_connection,
            _collectionOpenings)
        val _result: ProjectWithAssets?
        if (_stmt.step()) {
          val _tmpMetadata: ProjectMetadataEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpIsCurrentActive: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsCurrentActive).toInt()
          _tmpIsCurrentActive = _tmp != 0
          val _tmpRoomWidthMm: Float
          _tmpRoomWidthMm = _stmt.getDouble(_columnIndexOfRoomWidthMm).toFloat()
          val _tmpRoomDepthMm: Float
          _tmpRoomDepthMm = _stmt.getDouble(_columnIndexOfRoomDepthMm).toFloat()
          val _tmpRoomHeightMm: Float
          _tmpRoomHeightMm = _stmt.getDouble(_columnIndexOfRoomHeightMm).toFloat()
          val _tmpWallThicknessMm: Float
          _tmpWallThicknessMm = _stmt.getDouble(_columnIndexOfWallThicknessMm).toFloat()
          val _tmpFloorMaterialId: String
          _tmpFloorMaterialId = _stmt.getText(_columnIndexOfFloorMaterialId)
          val _tmpWallFinishId: String
          _tmpWallFinishId = _stmt.getText(_columnIndexOfWallFinishId)
          val _tmpCameraMode: String
          _tmpCameraMode = _stmt.getText(_columnIndexOfCameraMode)
          val _tmpCameraEyeX: Float
          _tmpCameraEyeX = _stmt.getDouble(_columnIndexOfCameraEyeX).toFloat()
          val _tmpCameraEyeY: Float
          _tmpCameraEyeY = _stmt.getDouble(_columnIndexOfCameraEyeY).toFloat()
          val _tmpCameraEyeZ: Float
          _tmpCameraEyeZ = _stmt.getDouble(_columnIndexOfCameraEyeZ).toFloat()
          val _tmpCameraTargetX: Float
          _tmpCameraTargetX = _stmt.getDouble(_columnIndexOfCameraTargetX).toFloat()
          val _tmpCameraTargetY: Float
          _tmpCameraTargetY = _stmt.getDouble(_columnIndexOfCameraTargetY).toFloat()
          val _tmpCameraTargetZ: Float
          _tmpCameraTargetZ = _stmt.getDouble(_columnIndexOfCameraTargetZ).toFloat()
          val _tmpCameraYawDegrees: Float
          _tmpCameraYawDegrees = _stmt.getDouble(_columnIndexOfCameraYawDegrees).toFloat()
          val _tmpCameraPitchDegrees: Float
          _tmpCameraPitchDegrees = _stmt.getDouble(_columnIndexOfCameraPitchDegrees).toFloat()
          val _tmpCameraZoomFactor: Float
          _tmpCameraZoomFactor = _stmt.getDouble(_columnIndexOfCameraZoomFactor).toFloat()
          val _tmpCameraPanX: Float
          _tmpCameraPanX = _stmt.getDouble(_columnIndexOfCameraPanX).toFloat()
          val _tmpCameraPanY: Float
          _tmpCameraPanY = _stmt.getDouble(_columnIndexOfCameraPanY).toFloat()
          val _tmpWalkEyeHeightMm: Float
          _tmpWalkEyeHeightMm = _stmt.getDouble(_columnIndexOfWalkEyeHeightMm).toFloat()
          val _tmpWalkPosX: Float
          _tmpWalkPosX = _stmt.getDouble(_columnIndexOfWalkPosX).toFloat()
          val _tmpWalkPosY: Float
          _tmpWalkPosY = _stmt.getDouble(_columnIndexOfWalkPosY).toFloat()
          val _tmpWalkPosZ: Float
          _tmpWalkPosZ = _stmt.getDouble(_columnIndexOfWalkPosZ).toFloat()
          val _tmpWalkLookYaw: Float
          _tmpWalkLookYaw = _stmt.getDouble(_columnIndexOfWalkLookYaw).toFloat()
          val _tmpWalkLookPitch: Float
          _tmpWalkLookPitch = _stmt.getDouble(_columnIndexOfWalkLookPitch).toFloat()
          _tmpMetadata =
              ProjectMetadataEntity(_tmpId,_tmpName,_tmpCreatedAt,_tmpUpdatedAt,_tmpIsCurrentActive,_tmpRoomWidthMm,_tmpRoomDepthMm,_tmpRoomHeightMm,_tmpWallThicknessMm,_tmpFloorMaterialId,_tmpWallFinishId,_tmpCameraMode,_tmpCameraEyeX,_tmpCameraEyeY,_tmpCameraEyeZ,_tmpCameraTargetX,_tmpCameraTargetY,_tmpCameraTargetZ,_tmpCameraYawDegrees,_tmpCameraPitchDegrees,_tmpCameraZoomFactor,_tmpCameraPanX,_tmpCameraPanY,_tmpWalkEyeHeightMm,_tmpWalkPosX,_tmpWalkPosY,_tmpWalkPosZ,_tmpWalkLookYaw,_tmpWalkLookPitch)
          val _tmpFurnitureAssetsCollection: MutableList<FurnitureAssetEntity>
          val _tmpKey_2: String
          _tmpKey_2 = _stmt.getText(_columnIndexOfId)
          _tmpFurnitureAssetsCollection = _collectionFurnitureAssets.getValue(_tmpKey_2)
          val _tmpOpeningsCollection: MutableList<WallOpeningEntity>
          val _tmpKey_3: String
          _tmpKey_3 = _stmt.getText(_columnIndexOfId)
          _tmpOpeningsCollection = _collectionOpenings.getValue(_tmpKey_3)
          _result =
              ProjectWithAssets(_tmpMetadata,_tmpFurnitureAssetsCollection,_tmpOpeningsCollection)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getAllProjectMetadata(): Flow<List<ProjectMetadataEntity>> {
    val _sql: String = "SELECT * FROM project_metadata ORDER BY updatedAt DESC"
    return createFlow(__db, false, arrayOf("project_metadata")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _columnIndexOfIsCurrentActive: Int = getColumnIndexOrThrow(_stmt, "isCurrentActive")
        val _columnIndexOfRoomWidthMm: Int = getColumnIndexOrThrow(_stmt, "roomWidthMm")
        val _columnIndexOfRoomDepthMm: Int = getColumnIndexOrThrow(_stmt, "roomDepthMm")
        val _columnIndexOfRoomHeightMm: Int = getColumnIndexOrThrow(_stmt, "roomHeightMm")
        val _columnIndexOfWallThicknessMm: Int = getColumnIndexOrThrow(_stmt, "wallThicknessMm")
        val _columnIndexOfFloorMaterialId: Int = getColumnIndexOrThrow(_stmt, "floorMaterialId")
        val _columnIndexOfWallFinishId: Int = getColumnIndexOrThrow(_stmt, "wallFinishId")
        val _columnIndexOfCameraMode: Int = getColumnIndexOrThrow(_stmt, "cameraMode")
        val _columnIndexOfCameraEyeX: Int = getColumnIndexOrThrow(_stmt, "cameraEyeX")
        val _columnIndexOfCameraEyeY: Int = getColumnIndexOrThrow(_stmt, "cameraEyeY")
        val _columnIndexOfCameraEyeZ: Int = getColumnIndexOrThrow(_stmt, "cameraEyeZ")
        val _columnIndexOfCameraTargetX: Int = getColumnIndexOrThrow(_stmt, "cameraTargetX")
        val _columnIndexOfCameraTargetY: Int = getColumnIndexOrThrow(_stmt, "cameraTargetY")
        val _columnIndexOfCameraTargetZ: Int = getColumnIndexOrThrow(_stmt, "cameraTargetZ")
        val _columnIndexOfCameraYawDegrees: Int = getColumnIndexOrThrow(_stmt, "cameraYawDegrees")
        val _columnIndexOfCameraPitchDegrees: Int = getColumnIndexOrThrow(_stmt,
            "cameraPitchDegrees")
        val _columnIndexOfCameraZoomFactor: Int = getColumnIndexOrThrow(_stmt, "cameraZoomFactor")
        val _columnIndexOfCameraPanX: Int = getColumnIndexOrThrow(_stmt, "cameraPanX")
        val _columnIndexOfCameraPanY: Int = getColumnIndexOrThrow(_stmt, "cameraPanY")
        val _columnIndexOfWalkEyeHeightMm: Int = getColumnIndexOrThrow(_stmt, "walkEyeHeightMm")
        val _columnIndexOfWalkPosX: Int = getColumnIndexOrThrow(_stmt, "walkPosX")
        val _columnIndexOfWalkPosY: Int = getColumnIndexOrThrow(_stmt, "walkPosY")
        val _columnIndexOfWalkPosZ: Int = getColumnIndexOrThrow(_stmt, "walkPosZ")
        val _columnIndexOfWalkLookYaw: Int = getColumnIndexOrThrow(_stmt, "walkLookYaw")
        val _columnIndexOfWalkLookPitch: Int = getColumnIndexOrThrow(_stmt, "walkLookPitch")
        val _result: MutableList<ProjectMetadataEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ProjectMetadataEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpIsCurrentActive: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsCurrentActive).toInt()
          _tmpIsCurrentActive = _tmp != 0
          val _tmpRoomWidthMm: Float
          _tmpRoomWidthMm = _stmt.getDouble(_columnIndexOfRoomWidthMm).toFloat()
          val _tmpRoomDepthMm: Float
          _tmpRoomDepthMm = _stmt.getDouble(_columnIndexOfRoomDepthMm).toFloat()
          val _tmpRoomHeightMm: Float
          _tmpRoomHeightMm = _stmt.getDouble(_columnIndexOfRoomHeightMm).toFloat()
          val _tmpWallThicknessMm: Float
          _tmpWallThicknessMm = _stmt.getDouble(_columnIndexOfWallThicknessMm).toFloat()
          val _tmpFloorMaterialId: String
          _tmpFloorMaterialId = _stmt.getText(_columnIndexOfFloorMaterialId)
          val _tmpWallFinishId: String
          _tmpWallFinishId = _stmt.getText(_columnIndexOfWallFinishId)
          val _tmpCameraMode: String
          _tmpCameraMode = _stmt.getText(_columnIndexOfCameraMode)
          val _tmpCameraEyeX: Float
          _tmpCameraEyeX = _stmt.getDouble(_columnIndexOfCameraEyeX).toFloat()
          val _tmpCameraEyeY: Float
          _tmpCameraEyeY = _stmt.getDouble(_columnIndexOfCameraEyeY).toFloat()
          val _tmpCameraEyeZ: Float
          _tmpCameraEyeZ = _stmt.getDouble(_columnIndexOfCameraEyeZ).toFloat()
          val _tmpCameraTargetX: Float
          _tmpCameraTargetX = _stmt.getDouble(_columnIndexOfCameraTargetX).toFloat()
          val _tmpCameraTargetY: Float
          _tmpCameraTargetY = _stmt.getDouble(_columnIndexOfCameraTargetY).toFloat()
          val _tmpCameraTargetZ: Float
          _tmpCameraTargetZ = _stmt.getDouble(_columnIndexOfCameraTargetZ).toFloat()
          val _tmpCameraYawDegrees: Float
          _tmpCameraYawDegrees = _stmt.getDouble(_columnIndexOfCameraYawDegrees).toFloat()
          val _tmpCameraPitchDegrees: Float
          _tmpCameraPitchDegrees = _stmt.getDouble(_columnIndexOfCameraPitchDegrees).toFloat()
          val _tmpCameraZoomFactor: Float
          _tmpCameraZoomFactor = _stmt.getDouble(_columnIndexOfCameraZoomFactor).toFloat()
          val _tmpCameraPanX: Float
          _tmpCameraPanX = _stmt.getDouble(_columnIndexOfCameraPanX).toFloat()
          val _tmpCameraPanY: Float
          _tmpCameraPanY = _stmt.getDouble(_columnIndexOfCameraPanY).toFloat()
          val _tmpWalkEyeHeightMm: Float
          _tmpWalkEyeHeightMm = _stmt.getDouble(_columnIndexOfWalkEyeHeightMm).toFloat()
          val _tmpWalkPosX: Float
          _tmpWalkPosX = _stmt.getDouble(_columnIndexOfWalkPosX).toFloat()
          val _tmpWalkPosY: Float
          _tmpWalkPosY = _stmt.getDouble(_columnIndexOfWalkPosY).toFloat()
          val _tmpWalkPosZ: Float
          _tmpWalkPosZ = _stmt.getDouble(_columnIndexOfWalkPosZ).toFloat()
          val _tmpWalkLookYaw: Float
          _tmpWalkLookYaw = _stmt.getDouble(_columnIndexOfWalkLookYaw).toFloat()
          val _tmpWalkLookPitch: Float
          _tmpWalkLookPitch = _stmt.getDouble(_columnIndexOfWalkLookPitch).toFloat()
          _item =
              ProjectMetadataEntity(_tmpId,_tmpName,_tmpCreatedAt,_tmpUpdatedAt,_tmpIsCurrentActive,_tmpRoomWidthMm,_tmpRoomDepthMm,_tmpRoomHeightMm,_tmpWallThicknessMm,_tmpFloorMaterialId,_tmpWallFinishId,_tmpCameraMode,_tmpCameraEyeX,_tmpCameraEyeY,_tmpCameraEyeZ,_tmpCameraTargetX,_tmpCameraTargetY,_tmpCameraTargetZ,_tmpCameraYawDegrees,_tmpCameraPitchDegrees,_tmpCameraZoomFactor,_tmpCameraPanX,_tmpCameraPanY,_tmpWalkEyeHeightMm,_tmpWalkPosX,_tmpWalkPosY,_tmpWalkPosZ,_tmpWalkLookYaw,_tmpWalkLookPitch)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeAutoSaveSnapshots(projectId: String):
      Flow<List<AutoSaveSnapshotEntity>> {
    val _sql: String =
        "SELECT * FROM project_autosave_snapshots WHERE projectId = ? ORDER BY savedAtTimestamp DESC"
    return createFlow(__db, false, arrayOf("project_autosave_snapshots")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, projectId)
        val _columnIndexOfSnapshotId: Int = getColumnIndexOrThrow(_stmt, "snapshotId")
        val _columnIndexOfProjectId: Int = getColumnIndexOrThrow(_stmt, "projectId")
        val _columnIndexOfProjectName: Int = getColumnIndexOrThrow(_stmt, "projectName")
        val _columnIndexOfSerializedStateJson: Int = getColumnIndexOrThrow(_stmt,
            "serializedStateJson")
        val _columnIndexOfRoomWidthMm: Int = getColumnIndexOrThrow(_stmt, "roomWidthMm")
        val _columnIndexOfRoomDepthMm: Int = getColumnIndexOrThrow(_stmt, "roomDepthMm")
        val _columnIndexOfRoomHeightMm: Int = getColumnIndexOrThrow(_stmt, "roomHeightMm")
        val _columnIndexOfWallThicknessMm: Int = getColumnIndexOrThrow(_stmt, "wallThicknessMm")
        val _columnIndexOfFurnitureCount: Int = getColumnIndexOrThrow(_stmt, "furnitureCount")
        val _columnIndexOfStateChecksumCrc32: Int = getColumnIndexOrThrow(_stmt,
            "stateChecksumCrc32")
        val _columnIndexOfPayloadSizeBytes: Int = getColumnIndexOrThrow(_stmt, "payloadSizeBytes")
        val _columnIndexOfIntervalSeconds: Int = getColumnIndexOrThrow(_stmt, "intervalSeconds")
        val _columnIndexOfTriggerType: Int = getColumnIndexOrThrow(_stmt, "triggerType")
        val _columnIndexOfSavedAtTimestamp: Int = getColumnIndexOrThrow(_stmt, "savedAtTimestamp")
        val _result: MutableList<AutoSaveSnapshotEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: AutoSaveSnapshotEntity
          val _tmpSnapshotId: String
          _tmpSnapshotId = _stmt.getText(_columnIndexOfSnapshotId)
          val _tmpProjectId: String
          _tmpProjectId = _stmt.getText(_columnIndexOfProjectId)
          val _tmpProjectName: String
          _tmpProjectName = _stmt.getText(_columnIndexOfProjectName)
          val _tmpSerializedStateJson: String
          _tmpSerializedStateJson = _stmt.getText(_columnIndexOfSerializedStateJson)
          val _tmpRoomWidthMm: Float
          _tmpRoomWidthMm = _stmt.getDouble(_columnIndexOfRoomWidthMm).toFloat()
          val _tmpRoomDepthMm: Float
          _tmpRoomDepthMm = _stmt.getDouble(_columnIndexOfRoomDepthMm).toFloat()
          val _tmpRoomHeightMm: Float
          _tmpRoomHeightMm = _stmt.getDouble(_columnIndexOfRoomHeightMm).toFloat()
          val _tmpWallThicknessMm: Float
          _tmpWallThicknessMm = _stmt.getDouble(_columnIndexOfWallThicknessMm).toFloat()
          val _tmpFurnitureCount: Int
          _tmpFurnitureCount = _stmt.getLong(_columnIndexOfFurnitureCount).toInt()
          val _tmpStateChecksumCrc32: Long
          _tmpStateChecksumCrc32 = _stmt.getLong(_columnIndexOfStateChecksumCrc32)
          val _tmpPayloadSizeBytes: Int
          _tmpPayloadSizeBytes = _stmt.getLong(_columnIndexOfPayloadSizeBytes).toInt()
          val _tmpIntervalSeconds: Int
          _tmpIntervalSeconds = _stmt.getLong(_columnIndexOfIntervalSeconds).toInt()
          val _tmpTriggerType: String
          _tmpTriggerType = _stmt.getText(_columnIndexOfTriggerType)
          val _tmpSavedAtTimestamp: Long
          _tmpSavedAtTimestamp = _stmt.getLong(_columnIndexOfSavedAtTimestamp)
          _item =
              AutoSaveSnapshotEntity(_tmpSnapshotId,_tmpProjectId,_tmpProjectName,_tmpSerializedStateJson,_tmpRoomWidthMm,_tmpRoomDepthMm,_tmpRoomHeightMm,_tmpWallThicknessMm,_tmpFurnitureCount,_tmpStateChecksumCrc32,_tmpPayloadSizeBytes,_tmpIntervalSeconds,_tmpTriggerType,_tmpSavedAtTimestamp)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeRecentAutoSaveSnapshots(limit: Int):
      Flow<List<AutoSaveSnapshotEntity>> {
    val _sql: String =
        "SELECT * FROM project_autosave_snapshots ORDER BY savedAtTimestamp DESC LIMIT ?"
    return createFlow(__db, false, arrayOf("project_autosave_snapshots")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, limit.toLong())
        val _columnIndexOfSnapshotId: Int = getColumnIndexOrThrow(_stmt, "snapshotId")
        val _columnIndexOfProjectId: Int = getColumnIndexOrThrow(_stmt, "projectId")
        val _columnIndexOfProjectName: Int = getColumnIndexOrThrow(_stmt, "projectName")
        val _columnIndexOfSerializedStateJson: Int = getColumnIndexOrThrow(_stmt,
            "serializedStateJson")
        val _columnIndexOfRoomWidthMm: Int = getColumnIndexOrThrow(_stmt, "roomWidthMm")
        val _columnIndexOfRoomDepthMm: Int = getColumnIndexOrThrow(_stmt, "roomDepthMm")
        val _columnIndexOfRoomHeightMm: Int = getColumnIndexOrThrow(_stmt, "roomHeightMm")
        val _columnIndexOfWallThicknessMm: Int = getColumnIndexOrThrow(_stmt, "wallThicknessMm")
        val _columnIndexOfFurnitureCount: Int = getColumnIndexOrThrow(_stmt, "furnitureCount")
        val _columnIndexOfStateChecksumCrc32: Int = getColumnIndexOrThrow(_stmt,
            "stateChecksumCrc32")
        val _columnIndexOfPayloadSizeBytes: Int = getColumnIndexOrThrow(_stmt, "payloadSizeBytes")
        val _columnIndexOfIntervalSeconds: Int = getColumnIndexOrThrow(_stmt, "intervalSeconds")
        val _columnIndexOfTriggerType: Int = getColumnIndexOrThrow(_stmt, "triggerType")
        val _columnIndexOfSavedAtTimestamp: Int = getColumnIndexOrThrow(_stmt, "savedAtTimestamp")
        val _result: MutableList<AutoSaveSnapshotEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: AutoSaveSnapshotEntity
          val _tmpSnapshotId: String
          _tmpSnapshotId = _stmt.getText(_columnIndexOfSnapshotId)
          val _tmpProjectId: String
          _tmpProjectId = _stmt.getText(_columnIndexOfProjectId)
          val _tmpProjectName: String
          _tmpProjectName = _stmt.getText(_columnIndexOfProjectName)
          val _tmpSerializedStateJson: String
          _tmpSerializedStateJson = _stmt.getText(_columnIndexOfSerializedStateJson)
          val _tmpRoomWidthMm: Float
          _tmpRoomWidthMm = _stmt.getDouble(_columnIndexOfRoomWidthMm).toFloat()
          val _tmpRoomDepthMm: Float
          _tmpRoomDepthMm = _stmt.getDouble(_columnIndexOfRoomDepthMm).toFloat()
          val _tmpRoomHeightMm: Float
          _tmpRoomHeightMm = _stmt.getDouble(_columnIndexOfRoomHeightMm).toFloat()
          val _tmpWallThicknessMm: Float
          _tmpWallThicknessMm = _stmt.getDouble(_columnIndexOfWallThicknessMm).toFloat()
          val _tmpFurnitureCount: Int
          _tmpFurnitureCount = _stmt.getLong(_columnIndexOfFurnitureCount).toInt()
          val _tmpStateChecksumCrc32: Long
          _tmpStateChecksumCrc32 = _stmt.getLong(_columnIndexOfStateChecksumCrc32)
          val _tmpPayloadSizeBytes: Int
          _tmpPayloadSizeBytes = _stmt.getLong(_columnIndexOfPayloadSizeBytes).toInt()
          val _tmpIntervalSeconds: Int
          _tmpIntervalSeconds = _stmt.getLong(_columnIndexOfIntervalSeconds).toInt()
          val _tmpTriggerType: String
          _tmpTriggerType = _stmt.getText(_columnIndexOfTriggerType)
          val _tmpSavedAtTimestamp: Long
          _tmpSavedAtTimestamp = _stmt.getLong(_columnIndexOfSavedAtTimestamp)
          _item =
              AutoSaveSnapshotEntity(_tmpSnapshotId,_tmpProjectId,_tmpProjectName,_tmpSerializedStateJson,_tmpRoomWidthMm,_tmpRoomDepthMm,_tmpRoomHeightMm,_tmpWallThicknessMm,_tmpFurnitureCount,_tmpStateChecksumCrc32,_tmpPayloadSizeBytes,_tmpIntervalSeconds,_tmpTriggerType,_tmpSavedAtTimestamp)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getLatestAutoSaveSnapshot(projectId: String):
      AutoSaveSnapshotEntity? {
    val _sql: String =
        "SELECT * FROM project_autosave_snapshots WHERE projectId = ? ORDER BY savedAtTimestamp DESC LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, projectId)
        val _columnIndexOfSnapshotId: Int = getColumnIndexOrThrow(_stmt, "snapshotId")
        val _columnIndexOfProjectId: Int = getColumnIndexOrThrow(_stmt, "projectId")
        val _columnIndexOfProjectName: Int = getColumnIndexOrThrow(_stmt, "projectName")
        val _columnIndexOfSerializedStateJson: Int = getColumnIndexOrThrow(_stmt,
            "serializedStateJson")
        val _columnIndexOfRoomWidthMm: Int = getColumnIndexOrThrow(_stmt, "roomWidthMm")
        val _columnIndexOfRoomDepthMm: Int = getColumnIndexOrThrow(_stmt, "roomDepthMm")
        val _columnIndexOfRoomHeightMm: Int = getColumnIndexOrThrow(_stmt, "roomHeightMm")
        val _columnIndexOfWallThicknessMm: Int = getColumnIndexOrThrow(_stmt, "wallThicknessMm")
        val _columnIndexOfFurnitureCount: Int = getColumnIndexOrThrow(_stmt, "furnitureCount")
        val _columnIndexOfStateChecksumCrc32: Int = getColumnIndexOrThrow(_stmt,
            "stateChecksumCrc32")
        val _columnIndexOfPayloadSizeBytes: Int = getColumnIndexOrThrow(_stmt, "payloadSizeBytes")
        val _columnIndexOfIntervalSeconds: Int = getColumnIndexOrThrow(_stmt, "intervalSeconds")
        val _columnIndexOfTriggerType: Int = getColumnIndexOrThrow(_stmt, "triggerType")
        val _columnIndexOfSavedAtTimestamp: Int = getColumnIndexOrThrow(_stmt, "savedAtTimestamp")
        val _result: AutoSaveSnapshotEntity?
        if (_stmt.step()) {
          val _tmpSnapshotId: String
          _tmpSnapshotId = _stmt.getText(_columnIndexOfSnapshotId)
          val _tmpProjectId: String
          _tmpProjectId = _stmt.getText(_columnIndexOfProjectId)
          val _tmpProjectName: String
          _tmpProjectName = _stmt.getText(_columnIndexOfProjectName)
          val _tmpSerializedStateJson: String
          _tmpSerializedStateJson = _stmt.getText(_columnIndexOfSerializedStateJson)
          val _tmpRoomWidthMm: Float
          _tmpRoomWidthMm = _stmt.getDouble(_columnIndexOfRoomWidthMm).toFloat()
          val _tmpRoomDepthMm: Float
          _tmpRoomDepthMm = _stmt.getDouble(_columnIndexOfRoomDepthMm).toFloat()
          val _tmpRoomHeightMm: Float
          _tmpRoomHeightMm = _stmt.getDouble(_columnIndexOfRoomHeightMm).toFloat()
          val _tmpWallThicknessMm: Float
          _tmpWallThicknessMm = _stmt.getDouble(_columnIndexOfWallThicknessMm).toFloat()
          val _tmpFurnitureCount: Int
          _tmpFurnitureCount = _stmt.getLong(_columnIndexOfFurnitureCount).toInt()
          val _tmpStateChecksumCrc32: Long
          _tmpStateChecksumCrc32 = _stmt.getLong(_columnIndexOfStateChecksumCrc32)
          val _tmpPayloadSizeBytes: Int
          _tmpPayloadSizeBytes = _stmt.getLong(_columnIndexOfPayloadSizeBytes).toInt()
          val _tmpIntervalSeconds: Int
          _tmpIntervalSeconds = _stmt.getLong(_columnIndexOfIntervalSeconds).toInt()
          val _tmpTriggerType: String
          _tmpTriggerType = _stmt.getText(_columnIndexOfTriggerType)
          val _tmpSavedAtTimestamp: Long
          _tmpSavedAtTimestamp = _stmt.getLong(_columnIndexOfSavedAtTimestamp)
          _result =
              AutoSaveSnapshotEntity(_tmpSnapshotId,_tmpProjectId,_tmpProjectName,_tmpSerializedStateJson,_tmpRoomWidthMm,_tmpRoomDepthMm,_tmpRoomHeightMm,_tmpWallThicknessMm,_tmpFurnitureCount,_tmpStateChecksumCrc32,_tmpPayloadSizeBytes,_tmpIntervalSeconds,_tmpTriggerType,_tmpSavedAtTimestamp)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getMostRecentAutoSaveSnapshot(): AutoSaveSnapshotEntity? {
    val _sql: String =
        "SELECT * FROM project_autosave_snapshots ORDER BY savedAtTimestamp DESC LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfSnapshotId: Int = getColumnIndexOrThrow(_stmt, "snapshotId")
        val _columnIndexOfProjectId: Int = getColumnIndexOrThrow(_stmt, "projectId")
        val _columnIndexOfProjectName: Int = getColumnIndexOrThrow(_stmt, "projectName")
        val _columnIndexOfSerializedStateJson: Int = getColumnIndexOrThrow(_stmt,
            "serializedStateJson")
        val _columnIndexOfRoomWidthMm: Int = getColumnIndexOrThrow(_stmt, "roomWidthMm")
        val _columnIndexOfRoomDepthMm: Int = getColumnIndexOrThrow(_stmt, "roomDepthMm")
        val _columnIndexOfRoomHeightMm: Int = getColumnIndexOrThrow(_stmt, "roomHeightMm")
        val _columnIndexOfWallThicknessMm: Int = getColumnIndexOrThrow(_stmt, "wallThicknessMm")
        val _columnIndexOfFurnitureCount: Int = getColumnIndexOrThrow(_stmt, "furnitureCount")
        val _columnIndexOfStateChecksumCrc32: Int = getColumnIndexOrThrow(_stmt,
            "stateChecksumCrc32")
        val _columnIndexOfPayloadSizeBytes: Int = getColumnIndexOrThrow(_stmt, "payloadSizeBytes")
        val _columnIndexOfIntervalSeconds: Int = getColumnIndexOrThrow(_stmt, "intervalSeconds")
        val _columnIndexOfTriggerType: Int = getColumnIndexOrThrow(_stmt, "triggerType")
        val _columnIndexOfSavedAtTimestamp: Int = getColumnIndexOrThrow(_stmt, "savedAtTimestamp")
        val _result: AutoSaveSnapshotEntity?
        if (_stmt.step()) {
          val _tmpSnapshotId: String
          _tmpSnapshotId = _stmt.getText(_columnIndexOfSnapshotId)
          val _tmpProjectId: String
          _tmpProjectId = _stmt.getText(_columnIndexOfProjectId)
          val _tmpProjectName: String
          _tmpProjectName = _stmt.getText(_columnIndexOfProjectName)
          val _tmpSerializedStateJson: String
          _tmpSerializedStateJson = _stmt.getText(_columnIndexOfSerializedStateJson)
          val _tmpRoomWidthMm: Float
          _tmpRoomWidthMm = _stmt.getDouble(_columnIndexOfRoomWidthMm).toFloat()
          val _tmpRoomDepthMm: Float
          _tmpRoomDepthMm = _stmt.getDouble(_columnIndexOfRoomDepthMm).toFloat()
          val _tmpRoomHeightMm: Float
          _tmpRoomHeightMm = _stmt.getDouble(_columnIndexOfRoomHeightMm).toFloat()
          val _tmpWallThicknessMm: Float
          _tmpWallThicknessMm = _stmt.getDouble(_columnIndexOfWallThicknessMm).toFloat()
          val _tmpFurnitureCount: Int
          _tmpFurnitureCount = _stmt.getLong(_columnIndexOfFurnitureCount).toInt()
          val _tmpStateChecksumCrc32: Long
          _tmpStateChecksumCrc32 = _stmt.getLong(_columnIndexOfStateChecksumCrc32)
          val _tmpPayloadSizeBytes: Int
          _tmpPayloadSizeBytes = _stmt.getLong(_columnIndexOfPayloadSizeBytes).toInt()
          val _tmpIntervalSeconds: Int
          _tmpIntervalSeconds = _stmt.getLong(_columnIndexOfIntervalSeconds).toInt()
          val _tmpTriggerType: String
          _tmpTriggerType = _stmt.getText(_columnIndexOfTriggerType)
          val _tmpSavedAtTimestamp: Long
          _tmpSavedAtTimestamp = _stmt.getLong(_columnIndexOfSavedAtTimestamp)
          _result =
              AutoSaveSnapshotEntity(_tmpSnapshotId,_tmpProjectId,_tmpProjectName,_tmpSerializedStateJson,_tmpRoomWidthMm,_tmpRoomDepthMm,_tmpRoomHeightMm,_tmpWallThicknessMm,_tmpFurnitureCount,_tmpStateChecksumCrc32,_tmpPayloadSizeBytes,_tmpIntervalSeconds,_tmpTriggerType,_tmpSavedAtTimestamp)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteFurnitureAssets(projectId: String) {
    val _sql: String = "DELETE FROM furniture_assets WHERE projectId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, projectId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteWallOpenings(projectId: String) {
    val _sql: String = "DELETE FROM wall_openings WHERE projectId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, projectId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteProject(projectId: String) {
    val _sql: String = "DELETE FROM project_metadata WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, projectId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun pruneOldSnapshots(projectId: String, maxSnapshots: Int) {
    val _sql: String = """
        |
        |        DELETE FROM project_autosave_snapshots 
        |        WHERE projectId = ? 
        |          AND snapshotId NOT IN (
        |              SELECT snapshotId FROM project_autosave_snapshots 
        |              WHERE projectId = ? 
        |              ORDER BY savedAtTimestamp DESC 
        |              LIMIT ?
        |          )
        |    
        """.trimMargin()
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, projectId)
        _argIndex = 2
        _stmt.bindText(_argIndex, projectId)
        _argIndex = 3
        _stmt.bindLong(_argIndex, maxSnapshots.toLong())
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateSavedCamera(
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
    updatedAt: Long,
  ) {
    val _sql: String = """
        |
        |        UPDATE project_metadata 
        |        SET cameraMode = ?, 
        |            cameraYawDegrees = ?, 
        |            cameraPitchDegrees = ?, 
        |            cameraZoomFactor = ?, 
        |            cameraPanX = ?, 
        |            cameraPanY = ?, 
        |            walkPosX = ?, 
        |            walkPosY = ?, 
        |            walkPosZ = ?, 
        |            walkLookYaw = ?, 
        |            walkLookPitch = ?, 
        |            updatedAt = ? 
        |        WHERE id = ?
        |    
        """.trimMargin()
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, mode)
        _argIndex = 2
        _stmt.bindDouble(_argIndex, yaw.toDouble())
        _argIndex = 3
        _stmt.bindDouble(_argIndex, pitch.toDouble())
        _argIndex = 4
        _stmt.bindDouble(_argIndex, zoom.toDouble())
        _argIndex = 5
        _stmt.bindDouble(_argIndex, panX.toDouble())
        _argIndex = 6
        _stmt.bindDouble(_argIndex, panY.toDouble())
        _argIndex = 7
        _stmt.bindDouble(_argIndex, walkX.toDouble())
        _argIndex = 8
        _stmt.bindDouble(_argIndex, walkY.toDouble())
        _argIndex = 9
        _stmt.bindDouble(_argIndex, walkZ.toDouble())
        _argIndex = 10
        _stmt.bindDouble(_argIndex, walkYaw.toDouble())
        _argIndex = 11
        _stmt.bindDouble(_argIndex, walkPitch.toDouble())
        _argIndex = 12
        _stmt.bindLong(_argIndex, updatedAt)
        _argIndex = 13
        _stmt.bindText(_argIndex, projectId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deactivateOtherProjects(projectId: String) {
    val _sql: String = "UPDATE project_metadata SET isCurrentActive = 0 WHERE id != ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, projectId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun setActiveProject(projectId: String) {
    val _sql: String = "UPDATE project_metadata SET isCurrentActive = 1 WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, projectId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  private
      fun __fetchRelationshipfurnitureAssetsAscomExampleDataLocalEntityFurnitureAssetEntity(_connection: SQLiteConnection,
      _map: ArrayMap<String, MutableList<FurnitureAssetEntity>>) {
    val __mapKeySet: Set<String> = _map.keys
    if (__mapKeySet.isEmpty()) {
      return
    }
    if (_map.size > 999) {
      recursiveFetchArrayMap(_map, true) { _tmpMap ->
        __fetchRelationshipfurnitureAssetsAscomExampleDataLocalEntityFurnitureAssetEntity(_connection,
            _tmpMap)
      }
      return
    }
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("SELECT `id`,`projectId`,`name`,`typeCode`,`category`,`layer`,`posX`,`posY`,`posZ`,`dimWidthMm`,`dimHeightMm`,`dimDepthMm`,`rotationY`,`materialId`,`countertopMaterialId`,`isLocked`,`isVisible`,`groupId`,`sortOrder` FROM `furniture_assets` WHERE `projectId` IN (")
    val _inputSize: Int = __mapKeySet.size
    appendPlaceholders(_stringBuilder, _inputSize)
    _stringBuilder.append(")")
    val _sql: String = _stringBuilder.toString()
    val _stmt: SQLiteStatement = _connection.prepare(_sql)
    var _argIndex: Int = 1
    for (_item: String in __mapKeySet) {
      _stmt.bindText(_argIndex, _item)
      _argIndex++
    }
    try {
      val _itemKeyIndex: Int = getColumnIndex(_stmt, "projectId")
      if (_itemKeyIndex == -1) {
        return
      }
      val _columnIndexOfId: Int = 0
      val _columnIndexOfProjectId: Int = 1
      val _columnIndexOfName: Int = 2
      val _columnIndexOfTypeCode: Int = 3
      val _columnIndexOfCategory: Int = 4
      val _columnIndexOfLayer: Int = 5
      val _columnIndexOfPosX: Int = 6
      val _columnIndexOfPosY: Int = 7
      val _columnIndexOfPosZ: Int = 8
      val _columnIndexOfDimWidthMm: Int = 9
      val _columnIndexOfDimHeightMm: Int = 10
      val _columnIndexOfDimDepthMm: Int = 11
      val _columnIndexOfRotationY: Int = 12
      val _columnIndexOfMaterialId: Int = 13
      val _columnIndexOfCountertopMaterialId: Int = 14
      val _columnIndexOfIsLocked: Int = 15
      val _columnIndexOfIsVisible: Int = 16
      val _columnIndexOfGroupId: Int = 17
      val _columnIndexOfSortOrder: Int = 18
      while (_stmt.step()) {
        val _tmpKey: String
        _tmpKey = _stmt.getText(_itemKeyIndex)
        val _tmpRelation: MutableList<FurnitureAssetEntity>? = _map.get(_tmpKey)
        if (_tmpRelation != null) {
          val _item_1: FurnitureAssetEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpProjectId: String
          _tmpProjectId = _stmt.getText(_columnIndexOfProjectId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpTypeCode: String
          _tmpTypeCode = _stmt.getText(_columnIndexOfTypeCode)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpLayer: String
          _tmpLayer = _stmt.getText(_columnIndexOfLayer)
          val _tmpPosX: Float
          _tmpPosX = _stmt.getDouble(_columnIndexOfPosX).toFloat()
          val _tmpPosY: Float
          _tmpPosY = _stmt.getDouble(_columnIndexOfPosY).toFloat()
          val _tmpPosZ: Float
          _tmpPosZ = _stmt.getDouble(_columnIndexOfPosZ).toFloat()
          val _tmpDimWidthMm: Float
          _tmpDimWidthMm = _stmt.getDouble(_columnIndexOfDimWidthMm).toFloat()
          val _tmpDimHeightMm: Float
          _tmpDimHeightMm = _stmt.getDouble(_columnIndexOfDimHeightMm).toFloat()
          val _tmpDimDepthMm: Float
          _tmpDimDepthMm = _stmt.getDouble(_columnIndexOfDimDepthMm).toFloat()
          val _tmpRotationY: Float
          _tmpRotationY = _stmt.getDouble(_columnIndexOfRotationY).toFloat()
          val _tmpMaterialId: String
          _tmpMaterialId = _stmt.getText(_columnIndexOfMaterialId)
          val _tmpCountertopMaterialId: String?
          if (_stmt.isNull(_columnIndexOfCountertopMaterialId)) {
            _tmpCountertopMaterialId = null
          } else {
            _tmpCountertopMaterialId = _stmt.getText(_columnIndexOfCountertopMaterialId)
          }
          val _tmpIsLocked: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsLocked).toInt()
          _tmpIsLocked = _tmp != 0
          val _tmpIsVisible: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsVisible).toInt()
          _tmpIsVisible = _tmp_1 != 0
          val _tmpGroupId: String?
          if (_stmt.isNull(_columnIndexOfGroupId)) {
            _tmpGroupId = null
          } else {
            _tmpGroupId = _stmt.getText(_columnIndexOfGroupId)
          }
          val _tmpSortOrder: Int
          _tmpSortOrder = _stmt.getLong(_columnIndexOfSortOrder).toInt()
          _item_1 =
              FurnitureAssetEntity(_tmpId,_tmpProjectId,_tmpName,_tmpTypeCode,_tmpCategory,_tmpLayer,_tmpPosX,_tmpPosY,_tmpPosZ,_tmpDimWidthMm,_tmpDimHeightMm,_tmpDimDepthMm,_tmpRotationY,_tmpMaterialId,_tmpCountertopMaterialId,_tmpIsLocked,_tmpIsVisible,_tmpGroupId,_tmpSortOrder)
          _tmpRelation.add(_item_1)
        }
      }
    } finally {
      _stmt.close()
    }
  }

  private
      fun __fetchRelationshipwallOpeningsAscomExampleDataLocalEntityWallOpeningEntity(_connection: SQLiteConnection,
      _map: ArrayMap<String, MutableList<WallOpeningEntity>>) {
    val __mapKeySet: Set<String> = _map.keys
    if (__mapKeySet.isEmpty()) {
      return
    }
    if (_map.size > 999) {
      recursiveFetchArrayMap(_map, true) { _tmpMap ->
        __fetchRelationshipwallOpeningsAscomExampleDataLocalEntityWallOpeningEntity(_connection,
            _tmpMap)
      }
      return
    }
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("SELECT `id`,`projectId`,`wallSide`,`openingType`,`offsetMm`,`widthMm`,`heightMm`,`sillHeightMm` FROM `wall_openings` WHERE `projectId` IN (")
    val _inputSize: Int = __mapKeySet.size
    appendPlaceholders(_stringBuilder, _inputSize)
    _stringBuilder.append(")")
    val _sql: String = _stringBuilder.toString()
    val _stmt: SQLiteStatement = _connection.prepare(_sql)
    var _argIndex: Int = 1
    for (_item: String in __mapKeySet) {
      _stmt.bindText(_argIndex, _item)
      _argIndex++
    }
    try {
      val _itemKeyIndex: Int = getColumnIndex(_stmt, "projectId")
      if (_itemKeyIndex == -1) {
        return
      }
      val _columnIndexOfId: Int = 0
      val _columnIndexOfProjectId: Int = 1
      val _columnIndexOfWallSide: Int = 2
      val _columnIndexOfOpeningType: Int = 3
      val _columnIndexOfOffsetMm: Int = 4
      val _columnIndexOfWidthMm: Int = 5
      val _columnIndexOfHeightMm: Int = 6
      val _columnIndexOfSillHeightMm: Int = 7
      while (_stmt.step()) {
        val _tmpKey: String
        _tmpKey = _stmt.getText(_itemKeyIndex)
        val _tmpRelation: MutableList<WallOpeningEntity>? = _map.get(_tmpKey)
        if (_tmpRelation != null) {
          val _item_1: WallOpeningEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpProjectId: String
          _tmpProjectId = _stmt.getText(_columnIndexOfProjectId)
          val _tmpWallSide: String
          _tmpWallSide = _stmt.getText(_columnIndexOfWallSide)
          val _tmpOpeningType: String
          _tmpOpeningType = _stmt.getText(_columnIndexOfOpeningType)
          val _tmpOffsetMm: Float
          _tmpOffsetMm = _stmt.getDouble(_columnIndexOfOffsetMm).toFloat()
          val _tmpWidthMm: Float
          _tmpWidthMm = _stmt.getDouble(_columnIndexOfWidthMm).toFloat()
          val _tmpHeightMm: Float
          _tmpHeightMm = _stmt.getDouble(_columnIndexOfHeightMm).toFloat()
          val _tmpSillHeightMm: Float
          _tmpSillHeightMm = _stmt.getDouble(_columnIndexOfSillHeightMm).toFloat()
          _item_1 =
              WallOpeningEntity(_tmpId,_tmpProjectId,_tmpWallSide,_tmpOpeningType,_tmpOffsetMm,_tmpWidthMm,_tmpHeightMm,_tmpSillHeightMm)
          _tmpRelation.add(_item_1)
        }
      }
    } finally {
      _stmt.close()
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
