package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity representing a single 3D kitchen element / furniture asset placed in the room.
 */
@Entity(
    tableName = "furniture_assets",
    foreignKeys = [
        ForeignKey(
            entity = ProjectMetadataEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class FurnitureAssetEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val name: String,
    val typeCode: String,
    val category: String,
    val layer: String,
    val posX: Float,
    val posY: Float,
    val posZ: Float,
    val dimWidthMm: Float,
    val dimHeightMm: Float,
    val dimDepthMm: Float,
    val rotationY: Float,
    val materialId: String,
    val countertopMaterialId: String? = null,
    val isLocked: Boolean = false,
    val isVisible: Boolean = true,
    val groupId: String? = null,
    val sortOrder: Int = 0
)
