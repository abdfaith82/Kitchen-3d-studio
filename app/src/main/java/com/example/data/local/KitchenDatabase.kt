package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.KitchenProjectDao
import com.example.data.local.entity.AutoSaveSnapshotEntity
import com.example.data.local.entity.FurnitureAssetEntity
import com.example.data.local.entity.ProjectMetadataEntity
import com.example.data.local.entity.WallOpeningEntity

@Database(
    entities = [
        ProjectMetadataEntity::class,
        FurnitureAssetEntity::class,
        WallOpeningEntity::class,
        AutoSaveSnapshotEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class KitchenDatabase : RoomDatabase() {

    abstract fun kitchenProjectDao(): KitchenProjectDao

    companion object {
        @Volatile
        private var INSTANCE: KitchenDatabase? = null

        fun getInstance(context: Context): KitchenDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KitchenDatabase::class.java,
                    "kitchen_studio_offline.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
