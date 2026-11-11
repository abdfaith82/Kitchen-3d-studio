package com.example.`data`.local

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.example.`data`.local.dao.KitchenProjectDao
import com.example.`data`.local.dao.KitchenProjectDao_Impl
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL", "RestrictedApi"])
public class KitchenDatabase_Impl : KitchenDatabase() {
  private val _kitchenProjectDao: Lazy<KitchenProjectDao> = lazy {
    KitchenProjectDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(2,
        "9c7c7ed38b68338a3116647a2080f0cc", "3171278989a02bada813647bd92cba15") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `project_metadata` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isCurrentActive` INTEGER NOT NULL, `roomWidthMm` REAL NOT NULL, `roomDepthMm` REAL NOT NULL, `roomHeightMm` REAL NOT NULL, `wallThicknessMm` REAL NOT NULL, `floorMaterialId` TEXT NOT NULL, `wallFinishId` TEXT NOT NULL, `cameraMode` TEXT NOT NULL, `cameraEyeX` REAL NOT NULL, `cameraEyeY` REAL NOT NULL, `cameraEyeZ` REAL NOT NULL, `cameraTargetX` REAL NOT NULL, `cameraTargetY` REAL NOT NULL, `cameraTargetZ` REAL NOT NULL, `cameraYawDegrees` REAL NOT NULL, `cameraPitchDegrees` REAL NOT NULL, `cameraZoomFactor` REAL NOT NULL, `cameraPanX` REAL NOT NULL, `cameraPanY` REAL NOT NULL, `walkEyeHeightMm` REAL NOT NULL, `walkPosX` REAL NOT NULL, `walkPosY` REAL NOT NULL, `walkPosZ` REAL NOT NULL, `walkLookYaw` REAL NOT NULL, `walkLookPitch` REAL NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `furniture_assets` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `name` TEXT NOT NULL, `typeCode` TEXT NOT NULL, `category` TEXT NOT NULL, `layer` TEXT NOT NULL, `posX` REAL NOT NULL, `posY` REAL NOT NULL, `posZ` REAL NOT NULL, `dimWidthMm` REAL NOT NULL, `dimHeightMm` REAL NOT NULL, `dimDepthMm` REAL NOT NULL, `rotationY` REAL NOT NULL, `materialId` TEXT NOT NULL, `countertopMaterialId` TEXT, `isLocked` INTEGER NOT NULL, `isVisible` INTEGER NOT NULL, `groupId` TEXT, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `project_metadata`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_furniture_assets_projectId` ON `furniture_assets` (`projectId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `wall_openings` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `wallSide` TEXT NOT NULL, `openingType` TEXT NOT NULL, `offsetMm` REAL NOT NULL, `widthMm` REAL NOT NULL, `heightMm` REAL NOT NULL, `sillHeightMm` REAL NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `project_metadata`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_wall_openings_projectId` ON `wall_openings` (`projectId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `project_autosave_snapshots` (`snapshotId` TEXT NOT NULL, `projectId` TEXT NOT NULL, `projectName` TEXT NOT NULL, `serializedStateJson` TEXT NOT NULL, `roomWidthMm` REAL NOT NULL, `roomDepthMm` REAL NOT NULL, `roomHeightMm` REAL NOT NULL, `wallThicknessMm` REAL NOT NULL, `furnitureCount` INTEGER NOT NULL, `stateChecksumCrc32` INTEGER NOT NULL, `payloadSizeBytes` INTEGER NOT NULL, `intervalSeconds` INTEGER NOT NULL, `triggerType` TEXT NOT NULL, `savedAtTimestamp` INTEGER NOT NULL, PRIMARY KEY(`snapshotId`))")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_project_autosave_snapshots_projectId_savedAtTimestamp` ON `project_autosave_snapshots` (`projectId`, `savedAtTimestamp`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '9c7c7ed38b68338a3116647a2080f0cc')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `project_metadata`")
        connection.execSQL("DROP TABLE IF EXISTS `furniture_assets`")
        connection.execSQL("DROP TABLE IF EXISTS `wall_openings`")
        connection.execSQL("DROP TABLE IF EXISTS `project_autosave_snapshots`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        connection.execSQL("PRAGMA foreign_keys = ON")
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection):
          RoomOpenDelegate.ValidationResult {
        val _columnsProjectMetadata: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsProjectMetadata.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("updatedAt", TableInfo.Column("updatedAt", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("isCurrentActive", TableInfo.Column("isCurrentActive",
            "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("roomWidthMm", TableInfo.Column("roomWidthMm", "REAL", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("roomDepthMm", TableInfo.Column("roomDepthMm", "REAL", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("roomHeightMm", TableInfo.Column("roomHeightMm", "REAL", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("wallThicknessMm", TableInfo.Column("wallThicknessMm", "REAL",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("floorMaterialId", TableInfo.Column("floorMaterialId", "TEXT",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("wallFinishId", TableInfo.Column("wallFinishId", "TEXT", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("cameraMode", TableInfo.Column("cameraMode", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("cameraEyeX", TableInfo.Column("cameraEyeX", "REAL", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("cameraEyeY", TableInfo.Column("cameraEyeY", "REAL", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("cameraEyeZ", TableInfo.Column("cameraEyeZ", "REAL", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("cameraTargetX", TableInfo.Column("cameraTargetX", "REAL", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("cameraTargetY", TableInfo.Column("cameraTargetY", "REAL", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("cameraTargetZ", TableInfo.Column("cameraTargetZ", "REAL", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("cameraYawDegrees", TableInfo.Column("cameraYawDegrees", "REAL",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("cameraPitchDegrees", TableInfo.Column("cameraPitchDegrees",
            "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("cameraZoomFactor", TableInfo.Column("cameraZoomFactor", "REAL",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("cameraPanX", TableInfo.Column("cameraPanX", "REAL", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("cameraPanY", TableInfo.Column("cameraPanY", "REAL", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("walkEyeHeightMm", TableInfo.Column("walkEyeHeightMm", "REAL",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("walkPosX", TableInfo.Column("walkPosX", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("walkPosY", TableInfo.Column("walkPosY", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("walkPosZ", TableInfo.Column("walkPosZ", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("walkLookYaw", TableInfo.Column("walkLookYaw", "REAL", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectMetadata.put("walkLookPitch", TableInfo.Column("walkLookPitch", "REAL", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysProjectMetadata: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesProjectMetadata: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoProjectMetadata: TableInfo = TableInfo("project_metadata", _columnsProjectMetadata,
            _foreignKeysProjectMetadata, _indicesProjectMetadata)
        val _existingProjectMetadata: TableInfo = read(connection, "project_metadata")
        if (!_infoProjectMetadata.equals(_existingProjectMetadata)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |project_metadata(com.example.data.local.entity.ProjectMetadataEntity).
              | Expected:
              |""".trimMargin() + _infoProjectMetadata + """
              |
              | Found:
              |""".trimMargin() + _existingProjectMetadata)
        }
        val _columnsFurnitureAssets: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsFurnitureAssets.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsFurnitureAssets.put("projectId", TableInfo.Column("projectId", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsFurnitureAssets.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsFurnitureAssets.put("typeCode", TableInfo.Column("typeCode", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsFurnitureAssets.put("category", TableInfo.Column("category", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsFurnitureAssets.put("layer", TableInfo.Column("layer", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsFurnitureAssets.put("posX", TableInfo.Column("posX", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsFurnitureAssets.put("posY", TableInfo.Column("posY", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsFurnitureAssets.put("posZ", TableInfo.Column("posZ", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsFurnitureAssets.put("dimWidthMm", TableInfo.Column("dimWidthMm", "REAL", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsFurnitureAssets.put("dimHeightMm", TableInfo.Column("dimHeightMm", "REAL", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsFurnitureAssets.put("dimDepthMm", TableInfo.Column("dimDepthMm", "REAL", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsFurnitureAssets.put("rotationY", TableInfo.Column("rotationY", "REAL", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsFurnitureAssets.put("materialId", TableInfo.Column("materialId", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsFurnitureAssets.put("countertopMaterialId", TableInfo.Column("countertopMaterialId",
            "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsFurnitureAssets.put("isLocked", TableInfo.Column("isLocked", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsFurnitureAssets.put("isVisible", TableInfo.Column("isVisible", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsFurnitureAssets.put("groupId", TableInfo.Column("groupId", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsFurnitureAssets.put("sortOrder", TableInfo.Column("sortOrder", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysFurnitureAssets: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysFurnitureAssets.add(TableInfo.ForeignKey("project_metadata", "CASCADE",
            "NO ACTION", listOf("projectId"), listOf("id")))
        val _indicesFurnitureAssets: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesFurnitureAssets.add(TableInfo.Index("index_furniture_assets_projectId", false,
            listOf("projectId"), listOf("ASC")))
        val _infoFurnitureAssets: TableInfo = TableInfo("furniture_assets", _columnsFurnitureAssets,
            _foreignKeysFurnitureAssets, _indicesFurnitureAssets)
        val _existingFurnitureAssets: TableInfo = read(connection, "furniture_assets")
        if (!_infoFurnitureAssets.equals(_existingFurnitureAssets)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |furniture_assets(com.example.data.local.entity.FurnitureAssetEntity).
              | Expected:
              |""".trimMargin() + _infoFurnitureAssets + """
              |
              | Found:
              |""".trimMargin() + _existingFurnitureAssets)
        }
        val _columnsWallOpenings: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsWallOpenings.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWallOpenings.put("projectId", TableInfo.Column("projectId", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWallOpenings.put("wallSide", TableInfo.Column("wallSide", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWallOpenings.put("openingType", TableInfo.Column("openingType", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWallOpenings.put("offsetMm", TableInfo.Column("offsetMm", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWallOpenings.put("widthMm", TableInfo.Column("widthMm", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWallOpenings.put("heightMm", TableInfo.Column("heightMm", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsWallOpenings.put("sillHeightMm", TableInfo.Column("sillHeightMm", "REAL", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysWallOpenings: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysWallOpenings.add(TableInfo.ForeignKey("project_metadata", "CASCADE",
            "NO ACTION", listOf("projectId"), listOf("id")))
        val _indicesWallOpenings: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesWallOpenings.add(TableInfo.Index("index_wall_openings_projectId", false,
            listOf("projectId"), listOf("ASC")))
        val _infoWallOpenings: TableInfo = TableInfo("wall_openings", _columnsWallOpenings,
            _foreignKeysWallOpenings, _indicesWallOpenings)
        val _existingWallOpenings: TableInfo = read(connection, "wall_openings")
        if (!_infoWallOpenings.equals(_existingWallOpenings)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |wall_openings(com.example.data.local.entity.WallOpeningEntity).
              | Expected:
              |""".trimMargin() + _infoWallOpenings + """
              |
              | Found:
              |""".trimMargin() + _existingWallOpenings)
        }
        val _columnsProjectAutosaveSnapshots: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsProjectAutosaveSnapshots.put("snapshotId", TableInfo.Column("snapshotId", "TEXT",
            true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectAutosaveSnapshots.put("projectId", TableInfo.Column("projectId", "TEXT",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectAutosaveSnapshots.put("projectName", TableInfo.Column("projectName", "TEXT",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectAutosaveSnapshots.put("serializedStateJson",
            TableInfo.Column("serializedStateJson", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectAutosaveSnapshots.put("roomWidthMm", TableInfo.Column("roomWidthMm", "REAL",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectAutosaveSnapshots.put("roomDepthMm", TableInfo.Column("roomDepthMm", "REAL",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectAutosaveSnapshots.put("roomHeightMm", TableInfo.Column("roomHeightMm",
            "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectAutosaveSnapshots.put("wallThicknessMm", TableInfo.Column("wallThicknessMm",
            "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectAutosaveSnapshots.put("furnitureCount", TableInfo.Column("furnitureCount",
            "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectAutosaveSnapshots.put("stateChecksumCrc32",
            TableInfo.Column("stateChecksumCrc32", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectAutosaveSnapshots.put("payloadSizeBytes",
            TableInfo.Column("payloadSizeBytes", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectAutosaveSnapshots.put("intervalSeconds", TableInfo.Column("intervalSeconds",
            "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectAutosaveSnapshots.put("triggerType", TableInfo.Column("triggerType", "TEXT",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjectAutosaveSnapshots.put("savedAtTimestamp",
            TableInfo.Column("savedAtTimestamp", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysProjectAutosaveSnapshots: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesProjectAutosaveSnapshots: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesProjectAutosaveSnapshots.add(TableInfo.Index("index_project_autosave_snapshots_projectId_savedAtTimestamp",
            false, listOf("projectId", "savedAtTimestamp"), listOf("ASC", "ASC")))
        val _infoProjectAutosaveSnapshots: TableInfo = TableInfo("project_autosave_snapshots",
            _columnsProjectAutosaveSnapshots, _foreignKeysProjectAutosaveSnapshots,
            _indicesProjectAutosaveSnapshots)
        val _existingProjectAutosaveSnapshots: TableInfo = read(connection,
            "project_autosave_snapshots")
        if (!_infoProjectAutosaveSnapshots.equals(_existingProjectAutosaveSnapshots)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |project_autosave_snapshots(com.example.data.local.entity.AutoSaveSnapshotEntity).
              | Expected:
              |""".trimMargin() + _infoProjectAutosaveSnapshots + """
              |
              | Found:
              |""".trimMargin() + _existingProjectAutosaveSnapshots)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "project_metadata",
        "furniture_assets", "wall_openings", "project_autosave_snapshots")
  }

  public override fun clearAllTables() {
    super.performClear(true, "project_metadata", "furniture_assets", "wall_openings",
        "project_autosave_snapshots")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(KitchenProjectDao::class, KitchenProjectDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override
      fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>):
      List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun kitchenProjectDao(): KitchenProjectDao = _kitchenProjectDao.value
}
