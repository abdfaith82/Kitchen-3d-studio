package com.example.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.example.core.math.Vector3
import com.example.data.local.entity.FurnitureAssetEntity
import com.example.data.local.entity.ProjectMetadataEntity
import com.example.data.local.entity.WallOpeningEntity
import com.example.domain.model.*
import com.example.domain.repository.KitchenProject

/**
 * Composite relational representation of a Project with its embedded metadata,
 * child furniture assets, and architectural wall openings.
 */
data class ProjectWithAssets(
    @Embedded
    val metadata: ProjectMetadataEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "projectId"
    )
    val furnitureAssets: List<FurnitureAssetEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "projectId"
    )
    val openings: List<WallOpeningEntity>
) {
    /**
     * Converts the database entities into a domain [KitchenProject].
     */
    fun toDomainProject(): KitchenProject {
        val openingsBySide = openings.groupBy { it.wallSide }

        val walls = listOf(
            Wall(
                id = "wall_north",
                side = WallSide.NORTH,
                lengthMm = metadata.roomWidthMm,
                heightMm = metadata.roomHeightMm,
                thicknessMm = metadata.wallThicknessMm,
                openings = openingsBySide[WallSide.NORTH.name]?.map { it.toDomainOpening() } ?: emptyList()
            ),
            Wall(
                id = "wall_east",
                side = WallSide.EAST,
                lengthMm = metadata.roomDepthMm,
                heightMm = metadata.roomHeightMm,
                thicknessMm = metadata.wallThicknessMm,
                openings = openingsBySide[WallSide.EAST.name]?.map { it.toDomainOpening() } ?: emptyList()
            ),
            Wall(
                id = "wall_south",
                side = WallSide.SOUTH,
                lengthMm = metadata.roomWidthMm,
                heightMm = metadata.roomHeightMm,
                thicknessMm = metadata.wallThicknessMm,
                openings = openingsBySide[WallSide.SOUTH.name]?.map { it.toDomainOpening() } ?: emptyList()
            ),
            Wall(
                id = "wall_west",
                side = WallSide.WEST,
                lengthMm = metadata.roomDepthMm,
                heightMm = metadata.roomHeightMm,
                thicknessMm = metadata.wallThicknessMm,
                openings = openingsBySide[WallSide.WEST.name]?.map { it.toDomainOpening() } ?: emptyList()
            )
        )

        val floorMaterial = MaterialFinish.ALL_FINISHES.find { it.id == metadata.floorMaterialId }
            ?: MaterialFinish.PARQUET_OAK_FLOOR
        val wallFinish = MaterialFinish.ALL_FINISHES.find { it.id == metadata.wallFinishId }
            ?: MaterialFinish.WHITE_HIGH_GLOSS

        val room = RoomStructure(
            widthMm = metadata.roomWidthMm,
            depthMm = metadata.roomDepthMm,
            heightMm = metadata.roomHeightMm,
            wallThicknessMm = metadata.wallThicknessMm,
            walls = walls,
            floorMaterial = floorMaterial,
            wallFinish = wallFinish
        )

        val elements = furnitureAssets.sortedBy { it.sortOrder }.map { asset ->
            val elementType = try {
                KitchenElementType.valueOf(asset.typeCode)
            } catch (e: Exception) {
                KitchenElementType.values().find { it.code == asset.typeCode }
                    ?: KitchenElementType.BASE_SINGLE_DOOR
            }

            val layer = try {
                LayerType.valueOf(asset.layer)
            } catch (e: Exception) {
                LayerType.fromElementType(elementType)
            }

            val material = MaterialFinish.ALL_FINISHES.find { it.id == asset.materialId }
                ?: MaterialFinish.NATURAL_OAK

            val countertopMat = asset.countertopMaterialId?.let { cId ->
                MaterialFinish.ALL_FINISHES.find { it.id == cId }
            }

            KitchenElement(
                id = asset.id,
                name = asset.name,
                type = elementType,
                position = Vector3(asset.posX, asset.posY, asset.posZ),
                rotationY = asset.rotationY,
                dimensions = Vector3(asset.dimWidthMm, asset.dimHeightMm, asset.dimDepthMm),
                material = material,
                countertopMaterial = countertopMat,
                isLocked = asset.isLocked,
                isVisible = asset.isVisible,
                groupId = asset.groupId,
                layer = layer
            )
        }

        val groups = elements.mapNotNull { it.groupId }.distinct().map { gId ->
            val groupElems = elements.filter { it.groupId == gId }
            KitchenGroup(
                id = gId,
                name = "مجموعة $gId",
                elementIds = groupElems.map { it.id }
            )
        }

        return KitchenProject(
            id = metadata.id,
            name = metadata.name,
            room = room,
            elements = elements,
            groups = groups,
            createdAt = metadata.createdAt
        )
    }

    companion object {
        fun fromDomain(
            project: KitchenProject,
            cameraState: SavedCameraState? = null
        ): Triple<ProjectMetadataEntity, List<FurnitureAssetEntity>, List<WallOpeningEntity>> {
            val metadata = ProjectMetadataEntity(
                id = project.id,
                name = project.name,
                createdAt = project.createdAt,
                updatedAt = System.currentTimeMillis(),
                isCurrentActive = true,
                roomWidthMm = project.room.widthMm,
                roomDepthMm = project.room.depthMm,
                roomHeightMm = project.room.heightMm,
                wallThicknessMm = project.room.wallThicknessMm,
                floorMaterialId = project.room.floorMaterial.id,
                wallFinishId = project.room.wallFinish.id,
                cameraMode = cameraState?.mode ?: "ORBIT",
                cameraEyeX = cameraState?.eyeX ?: 2500f,
                cameraEyeY = cameraState?.eyeY ?: 1600f,
                cameraEyeZ = cameraState?.eyeZ ?: 2500f,
                cameraTargetX = cameraState?.targetX ?: 2500f,
                cameraTargetY = cameraState?.targetY ?: 850f,
                cameraTargetZ = cameraState?.targetZ ?: 2500f,
                cameraYawDegrees = cameraState?.yaw ?: 35f,
                cameraPitchDegrees = cameraState?.pitch ?: 30f,
                cameraZoomFactor = cameraState?.zoom ?: 1.0f,
                cameraPanX = cameraState?.panX ?: 0f,
                cameraPanY = cameraState?.panY ?: 0f,
                walkEyeHeightMm = cameraState?.walkEyeHeightMm ?: 1600f,
                walkPosX = cameraState?.walkPosX ?: 2100f,
                walkPosY = cameraState?.walkPosY ?: 1600f,
                walkPosZ = cameraState?.walkPosZ ?: 1800f,
                walkLookYaw = cameraState?.walkLookYaw ?: 0f,
                walkLookPitch = cameraState?.walkLookPitch ?: 0f
            )

            val assets = project.elements.mapIndexed { index, elem ->
                FurnitureAssetEntity(
                    id = elem.id,
                    projectId = project.id,
                    name = elem.name,
                    typeCode = elem.type.name,
                    category = elem.type.category.name,
                    layer = elem.layer.name,
                    posX = elem.position.x,
                    posY = elem.position.y,
                    posZ = elem.position.z,
                    dimWidthMm = elem.dimensions.x,
                    dimHeightMm = elem.dimensions.y,
                    dimDepthMm = elem.dimensions.z,
                    rotationY = elem.rotationY,
                    materialId = elem.material.id,
                    countertopMaterialId = elem.countertopMaterial?.id,
                    isLocked = elem.isLocked,
                    isVisible = elem.isVisible,
                    groupId = elem.groupId,
                    sortOrder = index
                )
            }

            val openings = mutableListOf<WallOpeningEntity>()
            project.room.walls.forEach { wall ->
                wall.openings.forEach { op ->
                    openings.add(
                        WallOpeningEntity(
                            id = op.id,
                            projectId = project.id,
                            wallSide = wall.side.name,
                            openingType = op.type.name,
                            offsetMm = op.offsetMm,
                            widthMm = op.widthMm,
                            heightMm = op.heightMm,
                            sillHeightMm = op.sillHeightMm
                        )
                    )
                }
            }

            return Triple(metadata, assets, openings)
        }
    }
}

data class SavedCameraState(
    val mode: String = "ORBIT",
    val eyeX: Float = 2500f,
    val eyeY: Float = 1600f,
    val eyeZ: Float = 2500f,
    val targetX: Float = 2500f,
    val targetY: Float = 850f,
    val targetZ: Float = 2500f,
    val yaw: Float = 35f,
    val pitch: Float = 30f,
    val zoom: Float = 1.0f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val walkEyeHeightMm: Float = 1600f,
    val walkPosX: Float = 2100f,
    val walkPosY: Float = 1600f,
    val walkPosZ: Float = 1800f,
    val walkLookYaw: Float = 0f,
    val walkLookPitch: Float = 0f
)

private fun WallOpeningEntity.toDomainOpening(): WallOpening {
    val oType = try {
        OpeningType.valueOf(openingType)
    } catch (e: Exception) {
        OpeningType.WINDOW
    }
    return WallOpening(
        id = id,
        type = oType,
        offsetMm = offsetMm,
        widthMm = widthMm,
        heightMm = heightMm,
        sillHeightMm = sillHeightMm
    )
}
