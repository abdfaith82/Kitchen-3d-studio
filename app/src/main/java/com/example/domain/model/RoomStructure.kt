package com.example.domain.model

/**
 * Kitchen room layout topology (L-Shape, U-Shape, or Full Rectangular).
 */
enum class RoomLayoutShape(val titleAr: String, val descriptionAr: String) {
    RECTANGULAR("مستطيل كامل (4 جدران)", "غرفة مغلقة بأربعة جدران مستقلة"),
    L_SHAPE("شكل حرف L (جداران نشطان)", "تصميم مفتوح على جدارين متجاورين (شمالي + غربي)"),
    U_SHAPE("شكل حرف U (3 جدران)", "تصميم على ثلاثة جدران (شمالي + غربي + شرقي)");

    val arabicTitle: String get() = titleAr
}

/**
 * Room architectural structure encompassing individual wall lengths, corner angles,
 * window/door safety zones, manual MEP utility points, and surface finishes.
 * All spatial dimensions are in millimeters (10 mm = 1 cm).
 */
data class RoomStructure(
    val widthMm: Float = 4200f, // 4.2 meters width (X)
    val depthMm: Float = 3600f, // 3.6 meters depth (Z)
    val heightMm: Float = 2700f, // 2.7 meters ceiling height (Y)
    val wallThicknessMm: Float = 150f,
    val layoutShape: RoomLayoutShape = RoomLayoutShape.RECTANGULAR,
    val walls: List<Wall> = createDefaultWalls(4200f, 3600f, 2700f, 150f),
    val floorMaterial: MaterialFinish = MaterialFinish.PARQUET_OAK_FLOOR,
    val wallFinish: MaterialFinish = MaterialFinish.WHITE_HIGH_GLOSS
) {
    companion object {
        fun createDefaultRectangular(): RoomStructure = RoomStructure()

        fun createDefaultWalls(
            width: Float,
            depth: Float,
            height: Float,
            thickness: Float
        ): List<Wall> {
            return listOf(
                Wall(
                    id = "wall_north",
                    side = WallSide.NORTH,
                    lengthMm = width,
                    heightMm = height,
                    thicknessMm = thickness,
                    cornerAngleDegrees = 90f,
                    isEnabled = true,
                    openings = listOf(
                        WallOpening(
                            id = "win_north_1",
                            type = OpeningType.WINDOW,
                            offsetMm = 1500f,
                            widthMm = 1200f,
                            heightMm = 1100f,
                            sillHeightMm = 1000f
                        )
                    ),
                    utilityPoints = listOf(
                        WallUtilityPoint(
                            id = "util_plumb_north",
                            type = UtilityPointType.PLUMBING_DRAIN,
                            offsetFromWallStartMm = 2100f,
                            elevationFromFloorMm = 500f,
                            label = "صرف وتغذية المجلى تحت الشباك"
                        ),
                        WallUtilityPoint(
                            id = "util_elec_north_1",
                            type = UtilityPointType.ELECTRICAL_SOCKET,
                            offsetFromWallStartMm = 950f,
                            elevationFromFloorMm = 1100f,
                            label = "فيش كهرباء منطقة الـ 60 سم (1)"
                        ),
                        WallUtilityPoint(
                            id = "util_elec_north_2",
                            type = UtilityPointType.ELECTRICAL_SOCKET,
                            offsetFromWallStartMm = 3150f,
                            elevationFromFloorMm = 1100f,
                            label = "فيش كهرباء منطقة الـ 60 سم (2)"
                        ),
                        WallUtilityPoint(
                            id = "util_gas_north",
                            type = UtilityPointType.GAS_OUTLET,
                            offsetFromWallStartMm = 3400f,
                            elevationFromFloorMm = 750f,
                            label = "مخرج غاز البوتاجاز"
                        ),
                        WallUtilityPoint(
                            id = "util_vent_north",
                            type = UtilityPointType.VENTILATION_DUCT,
                            offsetFromWallStartMm = 3400f,
                            elevationFromFloorMm = 2200f,
                            label = "مدخنة شفاط التهوية"
                        )
                    )
                ),
                Wall(
                    id = "wall_east",
                    side = WallSide.EAST,
                    lengthMm = depth,
                    heightMm = height,
                    thicknessMm = thickness,
                    cornerAngleDegrees = 90f,
                    isEnabled = true,
                    openings = emptyList(),
                    utilityPoints = listOf(
                        WallUtilityPoint(
                            id = "util_elec_east_1",
                            type = UtilityPointType.ELECTRICAL_SOCKET,
                            offsetFromWallStartMm = 1400f,
                            elevationFromFloorMm = 1150f,
                            label = "فيش كهرباء منطقة الـ 60 سم (شرقي)"
                        )
                    )
                ),
                Wall(
                    id = "wall_south",
                    side = WallSide.SOUTH,
                    lengthMm = width,
                    heightMm = height,
                    thicknessMm = thickness,
                    cornerAngleDegrees = 90f,
                    isEnabled = true,
                    openings = listOf(
                        WallOpening(
                            id = "door_south_1",
                            type = OpeningType.DOOR,
                            offsetMm = 600f,
                            widthMm = 900f,
                            heightMm = 2100f,
                            sillHeightMm = 0f
                        )
                    ),
                    utilityPoints = emptyList()
                ),
                Wall(
                    id = "wall_west",
                    side = WallSide.WEST,
                    lengthMm = depth,
                    heightMm = height,
                    thicknessMm = thickness,
                    cornerAngleDegrees = 90f,
                    isEnabled = true,
                    openings = emptyList(),
                    utilityPoints = listOf(
                        WallUtilityPoint(
                            id = "util_elec_west_1",
                            type = UtilityPointType.ELECTRICAL_SOCKET,
                            offsetFromWallStartMm = 1200f,
                            elevationFromFloorMm = 1100f,
                            label = "فيش فرن وثلاجة (منطقة 60 سم)"
                        )
                    )
                )
            )
        }
    }

    fun updateDimensions(newWidth: Float, newDepth: Float, newHeight: Float): RoomStructure {
        val updatedWalls = walls.map { wall ->
            val newLength = when (wall.side) {
                WallSide.NORTH, WallSide.SOUTH -> newWidth
                WallSide.EAST, WallSide.WEST -> newDepth
            }
            val validOpenings = wall.openings.filter { it.offsetMm + it.widthMm <= newLength }
            val validUtilities = wall.utilityPoints.filter { it.offsetFromWallStartMm <= newLength }
            wall.copy(
                lengthMm = newLength,
                heightMm = newHeight,
                openings = validOpenings,
                utilityPoints = validUtilities
            )
        }
        return copy(
            widthMm = newWidth,
            depthMm = newDepth,
            heightMm = newHeight,
            walls = updatedWalls
        )
    }

    /**
     * Updates an individual wall's length, corner angle, enabled state, openings, or utilities
     * to support custom L-Shape, U-Shape, and out-of-square construction corrections.
     */
    fun updateIndividualWall(
        wallId: String,
        newLengthMm: Float? = null,
        newCornerAngleDeg: Float? = null,
        isEnabled: Boolean? = null
    ): RoomStructure {
        val updatedWalls = walls.map { wall ->
            if (wall.id == wallId) {
                val len = (newLengthMm ?: wall.lengthMm).coerceIn(1000f, 10000f)
                val angle = (newCornerAngleDeg ?: wall.cornerAngleDegrees).coerceIn(70f, 135f)
                val enabled = isEnabled ?: wall.isEnabled
                wall.copy(
                    lengthMm = len,
                    cornerAngleDegrees = angle,
                    isEnabled = enabled,
                    openings = wall.openings.filter { it.offsetMm + it.widthMm <= len },
                    utilityPoints = wall.utilityPoints.filter { it.offsetFromWallStartMm <= len }
                )
            } else {
                wall
            }
        }
        val northLen = updatedWalls.find { it.side == WallSide.NORTH }?.lengthMm ?: widthMm
        val southLen = updatedWalls.find { it.side == WallSide.SOUTH }?.lengthMm ?: widthMm
        val eastLen = updatedWalls.find { it.side == WallSide.EAST }?.lengthMm ?: depthMm
        val westLen = updatedWalls.find { it.side == WallSide.WEST }?.lengthMm ?: depthMm
        return copy(
            widthMm = maxOf(northLen, southLen),
            depthMm = maxOf(eastLen, westLen),
            walls = updatedWalls
        )
    }

    fun applyLayoutShape(shape: RoomLayoutShape): RoomStructure {
        val updatedWalls = walls.map { wall ->
            val enabled = when (shape) {
                RoomLayoutShape.RECTANGULAR -> true
                RoomLayoutShape.L_SHAPE -> wall.side == WallSide.NORTH || wall.side == WallSide.WEST
                RoomLayoutShape.U_SHAPE -> wall.side == WallSide.NORTH || wall.side == WallSide.WEST || wall.side == WallSide.EAST
            }
            wall.copy(isEnabled = enabled)
        }
        return copy(layoutShape = shape, walls = updatedWalls)
    }

    fun updateWall(updatedWall: Wall): RoomStructure {
        return copy(walls = walls.map { if (it.id == updatedWall.id) updatedWall else it })
    }
}
