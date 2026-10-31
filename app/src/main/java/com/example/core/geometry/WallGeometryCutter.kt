package com.example.core.geometry

import com.example.core.math.Vector3
import com.example.domain.model.OpeningType
import com.example.domain.model.Wall
import com.example.domain.model.WallOpening
import com.example.domain.model.WallSide
import kotlin.math.max
import kotlin.math.min

/**
 * Solid block geometry resulting from cutting a wall by openings (Boolean/CSG decomposition).
 */
data class WallSolidBlock(
    val startOffsetMm: Float,
    val lengthMm: Float,
    val bottomElevationMm: Float,
    val topElevationMm: Float,
    val thicknessMm: Float
) {
    val heightMm: Float get() = topElevationMm - bottomElevationMm
}

/**
 * Computational geometry engine that dynamically slices wall geometry around doors and windows.
 */
object WallGeometryCutter {

    /**
     * Slices a wall into a set of non-overlapping solid volumetric blocks.
     */
    fun decomposeWall(wall: Wall): List<WallSolidBlock> {
        val blocks = mutableListOf<WallSolidBlock>()
        val wallLength = wall.lengthMm
        val wallHeight = wall.heightMm
        val thickness = wall.thicknessMm

        // Sort openings by their position along the wall
        val sortedOpenings = wall.openings
            .filter { it.offsetMm < wallLength && it.widthMm > 0f }
            .sortedBy { it.offsetMm }

        if (sortedOpenings.isEmpty()) {
            // Uncut solid wall
            blocks.add(
                WallSolidBlock(
                    startOffsetMm = 0f,
                    lengthMm = wallLength,
                    bottomElevationMm = 0f,
                    topElevationMm = wallHeight,
                    thicknessMm = thickness
                )
            )
            return blocks
        }

        var currentOffset = 0f

        for (opening in sortedOpenings) {
            val openingStart = max(0f, opening.offsetMm)
            val openingEnd = min(wallLength, opening.offsetMm + opening.widthMm)
            val openingWidth = openingEnd - openingStart

            if (openingStart > currentOffset) {
                // 1. Full height solid wall before this opening
                blocks.add(
                    WallSolidBlock(
                        startOffsetMm = currentOffset,
                        lengthMm = openingStart - currentOffset,
                        bottomElevationMm = 0f,
                        topElevationMm = wallHeight,
                        thicknessMm = thickness
                    )
                )
            }

            if (openingWidth > 0f) {
                // 2. Below sill block (for windows with sillHeight > 0)
                if (opening.sillHeightMm > 0f) {
                    blocks.add(
                        WallSolidBlock(
                            startOffsetMm = openingStart,
                            lengthMm = openingWidth,
                            bottomElevationMm = 0f,
                            topElevationMm = min(wallHeight, opening.sillHeightMm),
                            thicknessMm = thickness
                        )
                    )
                }

                // 3. Above lintel / header block (above door or window)
                val lintelBottom = opening.sillHeightMm + opening.heightMm
                if (lintelBottom < wallHeight) {
                    blocks.add(
                        WallSolidBlock(
                            startOffsetMm = openingStart,
                            lengthMm = openingWidth,
                            bottomElevationMm = lintelBottom,
                            topElevationMm = wallHeight,
                            thicknessMm = thickness
                        )
                    )
                }
            }

            currentOffset = max(currentOffset, openingEnd)
        }

        // 4. Trailing solid wall block after the last opening
        if (currentOffset < wallLength) {
            blocks.add(
                WallSolidBlock(
                    startOffsetMm = currentOffset,
                    lengthMm = wallLength - currentOffset,
                    bottomElevationMm = 0f,
                    topElevationMm = wallHeight,
                    thicknessMm = thickness
                )
            )
        }

        return blocks
    }

    /**
     * Converts a wall block's local 1D offset into a 3D world coordinate.
     */
    fun localBlockToWorldCenter(
        wall: Wall,
        block: WallSolidBlock,
        roomWidth: Float,
        roomDepth: Float
    ): Vector3 {
        val halfLen = block.lengthMm * 0.5f
        val centerOffset = block.startOffsetMm + halfLen
        val centerY = block.bottomElevationMm + block.heightMm * 0.5f

        return when (wall.side) {
            WallSide.NORTH -> Vector3(centerOffset, centerY, -wall.thicknessMm * 0.5f)
            WallSide.SOUTH -> Vector3(centerOffset, centerY, roomDepth + wall.thicknessMm * 0.5f)
            WallSide.WEST -> Vector3(-wall.thicknessMm * 0.5f, centerY, centerOffset)
            WallSide.EAST -> Vector3(roomWidth + wall.thicknessMm * 0.5f, centerY, centerOffset)
        }
    }
}
