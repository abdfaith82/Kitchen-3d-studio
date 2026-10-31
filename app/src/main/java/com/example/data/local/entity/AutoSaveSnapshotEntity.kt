package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity storing periodic and debounced auto-save snapshots of the active [KitchenProject].
 *
 * Contains both indexed summary columns (room dimensions, furniture count, CRC32 checksum,
 * interval settings, and trigger reason) and the complete serialized JSON payload
 * (`serializedStateJson`) capturing room metadata, wall openings, furniture positions,
 * rotations, dimensions, materials, groups, and camera coordinates to guarantee zero data loss.
 */
@Entity(
    tableName = "project_autosave_snapshots",
    indices = [
        Index(value = ["projectId", "savedAtTimestamp"])
    ]
)
data class AutoSaveSnapshotEntity(
    @PrimaryKey
    val snapshotId: String,
    val projectId: String,
    val projectName: String,
    val serializedStateJson: String,
    val roomWidthMm: Float,
    val roomDepthMm: Float,
    val roomHeightMm: Float,
    val wallThicknessMm: Float = 150f,
    val furnitureCount: Int,
    val stateChecksumCrc32: Long,
    val payloadSizeBytes: Int,
    val intervalSeconds: Int,
    val triggerType: String, // "INTERVAL", "DEBOUNCED_EDIT", "MANUAL", "LIFECYCLE"
    val savedAtTimestamp: Long = System.currentTimeMillis()
)
