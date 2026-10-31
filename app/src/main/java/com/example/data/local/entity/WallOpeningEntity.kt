package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity representing architectural wall openings (windows, doors, pass-throughs).
 */
@Entity(
    tableName = "wall_openings",
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
data class WallOpeningEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val wallSide: String, // NORTH, EAST, SOUTH, WEST
    val openingType: String, // WINDOW, DOOR, PASS_THROUGH
    val offsetMm: Float,
    val widthMm: Float,
    val heightMm: Float,
    val sillHeightMm: Float
)
