package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity storing project metadata, room dimensions, materials, and saved camera coordinates.
 * Guarantees complete offline state persistence across app sessions and reboots.
 */
@Entity(tableName = "project_metadata")
data class ProjectMetadataEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isCurrentActive: Boolean = true,

    // Room Dimensions & Architectural parameters (mm)
    val roomWidthMm: Float = 4200f,
    val roomDepthMm: Float = 3600f,
    val roomHeightMm: Float = 2700f,
    val wallThicknessMm: Float = 150f,
    val floorMaterialId: String = "floor_parquet",
    val wallFinishId: String = "white_gloss",

    // Saved Camera Coordinates & Navigation State
    val cameraMode: String = "ORBIT", // ORBIT, FIRST_PERSON_WALK, TOP_DOWN
    val cameraEyeX: Float = 2500f,
    val cameraEyeY: Float = 1600f,
    val cameraEyeZ: Float = 2500f,
    val cameraTargetX: Float = 2500f,
    val cameraTargetY: Float = 850f,
    val cameraTargetZ: Float = 2500f,
    val cameraYawDegrees: Float = 35f,
    val cameraPitchDegrees: Float = 30f,
    val cameraZoomFactor: Float = 1.0f,
    val cameraPanX: Float = 0f,
    val cameraPanY: Float = 0f,

    // First-Person Walkthrough persistent position & orientation
    val walkEyeHeightMm: Float = 1600f,
    val walkPosX: Float = 2100f,
    val walkPosY: Float = 1600f,
    val walkPosZ: Float = 1800f,
    val walkLookYaw: Float = 0f,
    val walkLookPitch: Float = 0f
)
