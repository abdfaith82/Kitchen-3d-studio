package com.example.core.geometry

import com.example.core.math.MathUtils
import com.example.core.math.Vector3
import com.example.domain.model.ElementCategory
import com.example.domain.model.KitchenElement
import com.example.domain.model.KitchenElementType
import com.example.domain.model.LayerType
import com.example.domain.model.MaterialFinish
import com.example.domain.model.RoomStructure
import com.example.domain.model.WallSide
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * High-precision 1mm CAD Automatic Cabinet Filler & Spacer Engine.
 * Given a user-defined wall length (in mm), analyzes existing cabinets along the selected wall
 * and fills the remaining gap with customizable filler strips (شرائح فيلر) or spacers (فواصل)
 * quantized strictly to the 1mm snapping geometry.
 */
object AutomaticFillerEngine {

    enum class FillerTargetTier(val titleAr: String, val subtitleAr: String) {
        BASE_ROW("الخزائن السفلية (Base)", "ملء فراغات الصف السفلي والأبراج"),
        UPPER_WALL_ROW("الخزائن العلوية (Wall)", "ملء فراغات الصف العلوي الجداري"),
        TOP_BRIDGE_ROW("خزائن التاج (Bridge)", "ملء فراغات خزائن التاج العلوية"),
        ALL_TIERS("جميع المستويات (سفلي + علوي)", "ملء الفراغات تلقائياً لكل صفوف الجدار")
    }

    enum class FillerDistributionStrategy(val titleAr: String, val descriptionAr: String) {
        SYMMETRIC_BOTH_ENDS(
            "فيلر متماثل بالطرفين (يمين + يسار)",
            "توسيط الخزائن متلاصقة (0 مم) ووضع شريحتي فيلر متساويتين في بداية ونهاية الجدار بدقة 1 مم"
        ),
        LEFT_START_WALL_END(
            "شريحة فيلر في بداية الجدار (اليسار/الشمال)",
            "رصّ الخزائن حتى نهاية الجدار ووضع كامل المساحة المتبقية كشريحة فيلر في البداية"
        ),
        RIGHT_END_WALL_END(
            "شريحة فيلر في نهاية الجدار (اليمين/الجنوب)",
            "رصّ الخزائن من نقطة الصفر (0 مم) ووضع كامل المساحة المتبقية كشريحة فيلر في النهاية"
        ),
        FILL_INTERNAL_GAPS(
            "سد الفراغات الحالية في أماكنها",
            "الحفاظ على أماكن الخزائن الحالية وسد أي فراغ بينها أو عند أطراف الجدار بشرائح فيلر مفصلة"
        ),
        EVEN_SPACERS_BETWEEN_CABINETS(
            "فواصل متساوية بين الخزائن (Spacers)",
            "توزيع المسافة المتبقية كفواصل متساوية بين الخزائن والأطراف بدقة 1 مم"
        )
    }

    data class FillerRequestConfig(
        val wallSide: WallSide = WallSide.NORTH,
        val userWallLengthMm: Float = 4000f,
        val targetTier: FillerTargetTier = FillerTargetTier.BASE_ROW,
        val strategy: FillerDistributionStrategy = FillerDistributionStrategy.SYMMETRIC_BOTH_ENDS,
        val minFillerWidthMm: Float = 10f,
        val maxSingleFillerWidthMm: Float = 600f,
        val splitWideGapsIntoModularStrips: Boolean = false,
        val flushWithCabinetFront: Boolean = true,
        val recessDepthOffsetMm: Float = 20f,
        val includeCountertopOnBaseFiller: Boolean = true,
        val customMaterial: MaterialFinish? = null,
        val alsoUpdateRoomWallLength: Boolean = false
    )

    data class WallSegmentPreviewItem(
        val id: String,
        val label: String,
        val startOffsetMm: Float,
        val endOffsetMm: Float,
        val widthMm: Float,
        val heightMm: Float,
        val depthMm: Float,
        val elevationY: Float,
        val isFillerStrip: Boolean,
        val colorHex: Long
    )

    data class WallTierFillerAnalysis(
        val tier: FillerTargetTier,
        val wallSide: WallSide,
        val userWallLengthMm: Float,
        val cabinetCount: Int,
        val totalCabinetsWidthMm: Float,
        val remainingGapMm: Float,
        val generatedFillersCount: Int,
        val generatedFillerWidthsMm: List<Float>,
        val segments: List<WallSegmentPreviewItem>,
        val isOverfilled: Boolean,
        val statusMessageAr: String
    )

    data class FillerLayoutResult(
        val updatedElements: List<KitchenElement>,
        val addedFillers: List<KitchenElement>,
        val removedOldFillersCount: Int,
        val repositionedCabinetIds: Set<String>,
        val tierAnalyses: List<WallTierFillerAnalysis>,
        val totalGapFilledMm: Float,
        val summaryAr: String
    )

    /**
     * Checks if an element is a previously generated or catalog filler strip.
     */
    fun isFillerElement(element: KitchenElement): Boolean {
        return element.type.isFillerStrip ||
            element.id.startsWith("filler_") ||
            element.name.contains("فيلر") ||
            element.name.contains("فاصل")
    }

    /**
     * Determines whether an element is mounted along the given [wallSide] within a generous proximity window.
     */
    fun isElementOnWall(
        element: KitchenElement,
        wallSide: WallSide,
        room: RoomStructure,
        maxWallDistanceMm: Float = 360f
    ): Boolean {
        if (!element.isVisible) return false
        if (element.type.category == ElementCategory.ISLAND_AND_SURFACE) return false

        val aabb = element.getAABB()
        return when (wallSide) {
            WallSide.NORTH -> aabb.min.z <= maxWallDistanceMm
            WallSide.SOUTH -> (room.depthMm - aabb.max.z) <= maxWallDistanceMm
            WallSide.WEST -> aabb.min.x <= maxWallDistanceMm
            WallSide.EAST -> (room.widthMm - aabb.max.x) <= maxWallDistanceMm
        }
    }

    /**
     * Determines if an element belongs to a specific vertical tier.
     */
    fun matchesSingleTier(element: KitchenElement, singleTier: FillerTargetTier): Boolean {
        return when (singleTier) {
            FillerTargetTier.BASE_ROW -> {
                element.type.category == ElementCategory.BASE_CABINET ||
                    element.type.category == ElementCategory.TALL_UNIT ||
                    (element.type.category == ElementCategory.APPLIANCE && element.position.y < 400f)
            }
            FillerTargetTier.UPPER_WALL_ROW -> {
                element.type.category == ElementCategory.WALL_CABINET ||
                    (element.type.category == ElementCategory.APPLIANCE && element.position.y in 1100f..1950f)
            }
            FillerTargetTier.TOP_BRIDGE_ROW -> {
                element.type.category == ElementCategory.BRIDGE_CABINET ||
                    element.position.y >= 1950f
            }
            FillerTargetTier.ALL_TIERS -> true
        }
    }

    /**
     * Computes the full automatic filler layout and 1mm-snapped preview for [config].
     */
    fun computeFillerLayout(
        elements: List<KitchenElement>,
        room: RoomStructure,
        config: FillerRequestConfig
    ): FillerLayoutResult {
        val targetWallLengthMm = MathUtils.roundToMillimeter(config.userWallLengthMm.coerceIn(400f, 15000f))
        val minStripMm = MathUtils.roundToMillimeter(config.minFillerWidthMm.coerceIn(1f, 300f))
        val maxSingleStripMm = MathUtils.roundToMillimeter(config.maxSingleFillerWidthMm.coerceIn(50f, 2400f))

        val tiersToProcess = if (config.targetTier == FillerTargetTier.ALL_TIERS) {
            listOf(FillerTargetTier.BASE_ROW, FillerTargetTier.UPPER_WALL_ROW, FillerTargetTier.TOP_BRIDGE_ROW)
        } else {
            listOf(config.targetTier)
        }

        // Remove any previous filler strips on the same wall and target tiers so re-applying is clean and idempotent
        val removedOldFillers = elements.filter { elem ->
            isFillerElement(elem) &&
                isElementOnWall(elem, config.wallSide, room) &&
                tiersToProcess.any { tier -> matchesSingleTier(elem, tier) }
        }
        val removedIds = removedOldFillers.map { it.id }.toSet()

        val workingElements = elements
            .filter { it.id !in removedIds }
            .toMutableList()

        val allAddedFillers = mutableListOf<KitchenElement>()
        val repositionedIds = mutableSetOf<String>()
        val tierAnalyses = mutableListOf<WallTierFillerAnalysis>()

        for (tier in tiersToProcess) {
            val wallCabinetsInTier = workingElements.filter { elem ->
                !elem.isLocked &&
                    isElementOnWall(elem, config.wallSide, room) &&
                    matchesSingleTier(elem, tier)
            }

            if (wallCabinetsInTier.isEmpty() && config.targetTier == FillerTargetTier.ALL_TIERS && tier == FillerTargetTier.TOP_BRIDGE_ROW) {
                continue
            }

            val tierResult = computeSingleTierFillers(
                tier = tier,
                wallCabinets = wallCabinetsInTier,
                room = room,
                targetWallLengthMm = targetWallLengthMm,
                minStripMm = minStripMm,
                maxSingleStripMm = maxSingleStripMm,
                config = config
            )

            // Update repositioned cabinets in workingElements
            tierResult.repositionedCabinets.forEach { updatedCab ->
                val idx = workingElements.indexOfFirst { it.id == updatedCab.id }
                if (idx != -1) {
                    if (workingElements[idx].position != updatedCab.position ||
                        workingElements[idx].rotationY != updatedCab.rotationY
                    ) {
                        repositionedIds.add(updatedCab.id)
                    }
                    workingElements[idx] = updatedCab
                }
            }

            allAddedFillers.addAll(tierResult.generatedFillers)
            tierAnalyses.add(tierResult.analysis)
        }

        val finalElements = workingElements + allAddedFillers
        val totalGapFilled = allAddedFillers.sumOf { it.dimensions.x.toDouble() }.toFloat()

        val summaryAr = if (allAddedFillers.isNotEmpty()) {
            val widthsText = allAddedFillers.joinToString(" + ") { "${it.dimensions.x.roundToInt()} مم" }
            "✅ تم توليد ${allAddedFillers.size} شريحة فيلر/فاصل ($widthsText) لتغطية طول الجدار (${targetWallLengthMm.roundToInt()} مم) بدقة 1 مم"
        } else {
            val primaryAnalysis = tierAnalyses.firstOrNull()
            primaryAnalysis?.statusMessageAr ?: "لا توجد فراغات متبقية تتطلب شرائح فيلر على هذا الجدار"
        }

        return FillerLayoutResult(
            updatedElements = finalElements,
            addedFillers = allAddedFillers,
            removedOldFillersCount = removedOldFillers.size,
            repositionedCabinetIds = repositionedIds,
            tierAnalyses = tierAnalyses,
            totalGapFilledMm = MathUtils.roundToMillimeter(totalGapFilled),
            summaryAr = summaryAr
        )
    }

    private data class SingleTierComputation(
        val repositionedCabinets: List<KitchenElement>,
        val generatedFillers: List<KitchenElement>,
        val analysis: WallTierFillerAnalysis
    )

    private data class CabinetSpanAlongWall(
        val element: KitchenElement,
        val startMm: Float,
        val endMm: Float,
        val spanWidthMm: Float,
        val depthFromWallMm: Float
    )

    private fun computeSingleTierFillers(
        tier: FillerTargetTier,
        wallCabinets: List<KitchenElement>,
        room: RoomStructure,
        targetWallLengthMm: Float,
        minStripMm: Float,
        maxSingleStripMm: Float,
        config: FillerRequestConfig
    ): SingleTierComputation {
        val isHorizontalWall = config.wallSide == WallSide.NORTH || config.wallSide == WallSide.SOUTH
        val wallAlignedRotation = when (config.wallSide) {
            WallSide.NORTH -> 0f
            WallSide.SOUTH -> 180f
            WallSide.WEST -> 90f
            WallSide.EAST -> 270f
        }

        // Sort cabinets along the wall axis (X for North/South, Z for West/East)
        val sortedSpans = wallCabinets.map { elem ->
            val aabb = elem.getAABB()
            val (effW, effD) = CollisionDetector.getEffectiveFootprint(elem)
            val startCoord = if (isHorizontalWall) aabb.min.x else aabb.min.z
            val endCoord = if (isHorizontalWall) aabb.max.x else aabb.max.z
            val spanW = MathUtils.roundToMillimeter(if (isHorizontalWall) effW else effD)
            val depthD = MathUtils.roundToMillimeter(if (isHorizontalWall) effD else effW)
            CabinetSpanAlongWall(
                element = elem,
                startMm = MathUtils.roundToMillimeter(startCoord),
                endMm = MathUtils.roundToMillimeter(endCoord),
                spanWidthMm = spanW,
                depthFromWallMm = depthD
            )
        }.sortedBy { it.startMm }

        val totalCabinetsWidthMm = MathUtils.roundToMillimeter(
            sortedSpans.sumOf { it.spanWidthMm.toDouble() }.toFloat()
        )
        val rawRemainingGapMm = MathUtils.roundToMillimeter(targetWallLengthMm - totalCabinetsWidthMm)

        // Determine reference height, depth, elevation, and material for generated filler strips in this tier
        val referenceCab = sortedSpans.firstOrNull()?.element
        val defaultFillerType = when (tier) {
            FillerTargetTier.BASE_ROW -> KitchenElementType.BASE_FILLER_STRIP
            FillerTargetTier.UPPER_WALL_ROW -> KitchenElementType.WALL_FILLER_STRIP
            FillerTargetTier.TOP_BRIDGE_ROW -> KitchenElementType.WALL_FILLER_STRIP
            FillerTargetTier.ALL_TIERS -> KitchenElementType.BASE_FILLER_STRIP
        }

        val fillerHeightMm = MathUtils.roundToMillimeter(
            referenceCab?.dimensions?.y ?: when (tier) {
                FillerTargetTier.BASE_ROW -> 850f
                FillerTargetTier.UPPER_WALL_ROW -> 720f
                FillerTargetTier.TOP_BRIDGE_ROW -> 500f
                FillerTargetTier.ALL_TIERS -> 850f
            }
        )

        val baseCabinetDepthMm = referenceCab?.let { cab ->
            val (effW, effD) = CollisionDetector.getEffectiveFootprint(cab)
            if (isHorizontalWall) effD else effW
        } ?: when (tier) {
            FillerTargetTier.BASE_ROW -> 600f
            FillerTargetTier.UPPER_WALL_ROW -> 350f
            FillerTargetTier.TOP_BRIDGE_ROW -> 580f
            FillerTargetTier.ALL_TIERS -> 600f
        }

        val fillerDepthMm = MathUtils.roundToMillimeter(
            if (config.flushWithCabinetFront) {
                baseCabinetDepthMm
            } else {
                (baseCabinetDepthMm - config.recessDepthOffsetMm).coerceAtLeast(100f)
            }
        )

        val fillerElevationY = MathUtils.roundToMillimeter(
            referenceCab?.position?.y ?: when (tier) {
                FillerTargetTier.BASE_ROW -> 0f
                FillerTargetTier.UPPER_WALL_ROW -> 1450f
                FillerTargetTier.TOP_BRIDGE_ROW -> 2170f
                FillerTargetTier.ALL_TIERS -> 0f
            }
        )

        val fillerMaterial = config.customMaterial
            ?: referenceCab?.material
            ?: MaterialFinish.WHITE_HIGH_GLOSS

        val fillerCountertop = if (tier == FillerTargetTier.BASE_ROW && config.includeCountertopOnBaseFiller) {
            referenceCab?.countertopMaterial ?: MaterialFinish.CARRARA_MARBLE
        } else null

        // Helper to place a cabinet at [startOffsetMm..startOffsetMm + spanW] flush against [config.wallSide]
        fun placeCabinetAtOffset(span: CabinetSpanAlongWall, newStartOffsetMm: Float): KitchenElement {
            val snappedStart = MathUtils.roundToMillimeter(newStartOffsetMm)
            val halfSpan = span.spanWidthMm * 0.5f
            val halfDepth = span.depthFromWallMm * 0.5f
            val centerAlongWall = MathUtils.roundToMillimeter(snappedStart + halfSpan)

            val newPos = when (config.wallSide) {
                WallSide.NORTH -> Vector3(centerAlongWall, span.element.position.y, halfDepth)
                WallSide.SOUTH -> Vector3(centerAlongWall, span.element.position.y, room.depthMm - halfDepth)
                WallSide.WEST -> Vector3(halfDepth, span.element.position.y, centerAlongWall)
                WallSide.EAST -> Vector3(room.widthMm - halfDepth, span.element.position.y, centerAlongWall)
            }.roundTo1mm()

            return span.element.copy(position = newPos)
        }

        // Helper to create one or more filler strips spanning [gapStartMm..gapEndMm]
        val generatedFillers = mutableListOf<KitchenElement>()
        val previewSegments = mutableListOf<WallSegmentPreviewItem>()

        fun addFillerSpan(gapStartMm: Float, gapWidthMm: Float, labelPrefix: String) {
            val roundedGap = MathUtils.roundToMillimeter(gapWidthMm)
            if (roundedGap < minStripMm) return

            // Split into modular pieces if requested and gap exceeds maxSingleStripMm
            val stripWidths = mutableListOf<Float>()
            if (config.splitWideGapsIntoModularStrips && roundedGap > maxSingleStripMm) {
                val count = kotlin.math.ceil(roundedGap / maxSingleStripMm).toInt().coerceAtLeast(1)
                val baseW = (roundedGap.roundToInt() / count).toFloat()
                var remainder = roundedGap.roundToInt() - (baseW.roundToInt() * count)
                for (i in 0 until count) {
                    val extra = if (remainder > 0) {
                        remainder -= 1
                        1f
                    } else 0f
                    stripWidths.add(baseW + extra)
                }
            } else {
                stripWidths.add(roundedGap)
            }

            var cursorMm = MathUtils.roundToMillimeter(gapStartMm)
            stripWidths.forEachIndexed { idx, wMm ->
                val stripW = MathUtils.roundToMillimeter(wMm)
                val halfW = stripW * 0.5f
                val halfD = fillerDepthMm * 0.5f
                val centerAlongWall = MathUtils.roundToMillimeter(cursorMm + halfW)

                val pos = when (config.wallSide) {
                    WallSide.NORTH -> Vector3(centerAlongWall, fillerElevationY, halfD)
                    WallSide.SOUTH -> Vector3(centerAlongWall, fillerElevationY, room.depthMm - halfD)
                    WallSide.WEST -> Vector3(halfD, fillerElevationY, centerAlongWall)
                    WallSide.EAST -> Vector3(room.widthMm - halfD, fillerElevationY, centerAlongWall)
                }.roundTo1mm()

                val stripIndexLabel = if (stripWidths.size > 1) " #${idx + 1}" else ""
                val fillerName = "$labelPrefix (${stripW.roundToInt()} مم)$stripIndexLabel"
                val fillerElem = KitchenElement(
                    id = "filler_${UUID.randomUUID().toString().take(8)}",
                    name = fillerName,
                    type = defaultFillerType,
                    layer = if (tier == FillerTargetTier.UPPER_WALL_ROW || tier == FillerTargetTier.TOP_BRIDGE_ROW) {
                        LayerType.WALL_CABINETS
                    } else {
                        LayerType.BASE_CABINETS
                    },
                    position = pos,
                    rotationY = wallAlignedRotation,
                    dimensions = Vector3(stripW, fillerHeightMm, fillerDepthMm).roundTo1mm(),
                    material = fillerMaterial,
                    countertopMaterial = fillerCountertop,
                    hasBackPanel = true
                )
                generatedFillers.add(fillerElem)
                previewSegments.add(
                    WallSegmentPreviewItem(
                        id = fillerElem.id,
                        label = "فيلر ${stripW.roundToInt()}مم",
                        startOffsetMm = cursorMm,
                        endOffsetMm = MathUtils.roundToMillimeter(cursorMm + stripW),
                        widthMm = stripW,
                        heightMm = fillerHeightMm,
                        depthMm = fillerDepthMm,
                        elevationY = fillerElevationY,
                        isFillerStrip = true,
                        colorHex = 0xFFF59E0B
                    )
                )
                cursorMm = MathUtils.roundToMillimeter(cursorMm + stripW)
            }
        }

        val repositionedCabinets = mutableListOf<KitchenElement>()

        if (rawRemainingGapMm < 0f && config.strategy != FillerDistributionStrategy.FILL_INTERNAL_GAPS) {
            // Overfilled wall: keep cabinets as-is and report overfill
            sortedSpans.forEach { span ->
                repositionedCabinets.add(span.element)
                previewSegments.add(
                    WallSegmentPreviewItem(
                        id = span.element.id,
                        label = "${span.element.name} (${span.spanWidthMm.roundToInt()}مم)",
                        startOffsetMm = span.startMm,
                        endOffsetMm = span.endMm,
                        widthMm = span.spanWidthMm,
                        heightMm = span.element.dimensions.y,
                        depthMm = span.depthFromWallMm,
                        elevationY = span.element.position.y,
                        isFillerStrip = false,
                        colorHex = span.element.material.colorHex
                    )
                )
            }
            val overMm = abs(rawRemainingGapMm).roundToInt()
            return SingleTierComputation(
                repositionedCabinets = repositionedCabinets,
                generatedFillers = emptyList(),
                analysis = WallTierFillerAnalysis(
                    tier = tier,
                    wallSide = config.wallSide,
                    userWallLengthMm = targetWallLengthMm,
                    cabinetCount = sortedSpans.size,
                    totalCabinetsWidthMm = totalCabinetsWidthMm,
                    remainingGapMm = rawRemainingGapMm,
                    generatedFillersCount = 0,
                    generatedFillerWidthsMm = emptyList(),
                    segments = previewSegments.sortedBy { it.startOffsetMm },
                    isOverfilled = true,
                    statusMessageAr = "⚠️ مجموع عرض الخزائن (${totalCabinetsWidthMm.roundToInt()} مم) يتجاوز طول الجدار المحدد (${targetWallLengthMm.roundToInt()} مم) بمقدار $overMm مم"
                )
            )
        }

        if (sortedSpans.isEmpty()) {
            // No cabinets on this wall/tier yet: generate a single or modular filler only if user wants to see the wall span
            val msg = "لا توجد خزائن حالية في «${tier.titleAr}» على ${config.wallSide.title}"
            return SingleTierComputation(
                repositionedCabinets = emptyList(),
                generatedFillers = emptyList(),
                analysis = WallTierFillerAnalysis(
                    tier = tier,
                    wallSide = config.wallSide,
                    userWallLengthMm = targetWallLengthMm,
                    cabinetCount = 0,
                    totalCabinetsWidthMm = 0f,
                    remainingGapMm = targetWallLengthMm,
                    generatedFillersCount = 0,
                    generatedFillerWidthsMm = emptyList(),
                    segments = emptyList(),
                    isOverfilled = false,
                    statusMessageAr = msg
                )
            )
        }

        when (config.strategy) {
            FillerDistributionStrategy.SYMMETRIC_BOTH_ENDS -> {
                val totalGapInt = rawRemainingGapMm.roundToInt().coerceAtLeast(0)
                val leftGapMm = (totalGapInt / 2).toFloat()
                val rightGapMm = (totalGapInt - leftGapMm.roundToInt()).toFloat()

                var cursorMm = 0f
                if (leftGapMm >= minStripMm) {
                    addFillerSpan(0f, leftGapMm, "شريحة فيلر يمين/بداية")
                    cursorMm = leftGapMm
                } else {
                    cursorMm = 0f
                }

                sortedSpans.forEach { span ->
                    val placed = placeCabinetAtOffset(span, cursorMm)
                    repositionedCabinets.add(placed)
                    previewSegments.add(
                        WallSegmentPreviewItem(
                            id = placed.id,
                            label = "${placed.name} (${span.spanWidthMm.roundToInt()}مم)",
                            startOffsetMm = cursorMm,
                            endOffsetMm = cursorMm + span.spanWidthMm,
                            widthMm = span.spanWidthMm,
                            heightMm = placed.dimensions.y,
                            depthMm = span.depthFromWallMm,
                            elevationY = placed.position.y,
                            isFillerStrip = false,
                            colorHex = placed.material.colorHex
                        )
                    )
                    cursorMm = MathUtils.roundToMillimeter(cursorMm + span.spanWidthMm)
                }

                val remainingEndMm = MathUtils.roundToMillimeter(targetWallLengthMm - cursorMm)
                if (remainingEndMm >= minStripMm) {
                    addFillerSpan(cursorMm, remainingEndMm, "شريحة فيلر يسار/نهاية")
                } else if (rightGapMm >= minStripMm) {
                    addFillerSpan(cursorMm, rightGapMm, "شريحة فيلر يسار/نهاية")
                }
            }

            FillerDistributionStrategy.LEFT_START_WALL_END -> {
                var cursorMm = 0f
                if (rawRemainingGapMm >= minStripMm) {
                    addFillerSpan(0f, rawRemainingGapMm, "شريحة فيلر بداية الجدار")
                    cursorMm = rawRemainingGapMm
                }
                sortedSpans.forEach { span ->
                    val placed = placeCabinetAtOffset(span, cursorMm)
                    repositionedCabinets.add(placed)
                    previewSegments.add(
                        WallSegmentPreviewItem(
                            id = placed.id,
                            label = "${placed.name} (${span.spanWidthMm.roundToInt()}مم)",
                            startOffsetMm = cursorMm,
                            endOffsetMm = cursorMm + span.spanWidthMm,
                            widthMm = span.spanWidthMm,
                            heightMm = placed.dimensions.y,
                            depthMm = span.depthFromWallMm,
                            elevationY = placed.position.y,
                            isFillerStrip = false,
                            colorHex = placed.material.colorHex
                        )
                    )
                    cursorMm = MathUtils.roundToMillimeter(cursorMm + span.spanWidthMm)
                }
            }

            FillerDistributionStrategy.RIGHT_END_WALL_END -> {
                var cursorMm = 0f
                sortedSpans.forEach { span ->
                    val placed = placeCabinetAtOffset(span, cursorMm)
                    repositionedCabinets.add(placed)
                    previewSegments.add(
                        WallSegmentPreviewItem(
                            id = placed.id,
                            label = "${placed.name} (${span.spanWidthMm.roundToInt()}مم)",
                            startOffsetMm = cursorMm,
                            endOffsetMm = cursorMm + span.spanWidthMm,
                            widthMm = span.spanWidthMm,
                            heightMm = placed.dimensions.y,
                            depthMm = span.depthFromWallMm,
                            elevationY = placed.position.y,
                            isFillerStrip = false,
                            colorHex = placed.material.colorHex
                        )
                    )
                    cursorMm = MathUtils.roundToMillimeter(cursorMm + span.spanWidthMm)
                }
                val endGapMm = MathUtils.roundToMillimeter(targetWallLengthMm - cursorMm)
                if (endGapMm >= minStripMm) {
                    addFillerSpan(cursorMm, endGapMm, "شريحة فيلر نهاية الجدار")
                }
            }

            FillerDistributionStrategy.FILL_INTERNAL_GAPS -> {
                var cursorMm = 0f
                sortedSpans.forEachIndexed { idx, span ->
                    val clampedStart = max(cursorMm, span.startMm.coerceAtLeast(0f))
                    val gapBefore = MathUtils.roundToMillimeter(clampedStart - cursorMm)
                    if (gapBefore >= minStripMm) {
                        val label = if (idx == 0) "شريحة فيلر طرفية" else "فاصل فيلر داخلي"
                        addFillerSpan(cursorMm, gapBefore, label)
                        cursorMm = MathUtils.roundToMillimeter(cursorMm + gapBefore)
                    } else if (gapBefore > 0f) {
                        // Snap cabinet flush by closing sub-minimum gap
                        cursorMm = clampedStart
                    }

                    val placed = placeCabinetAtOffset(span, cursorMm)
                    repositionedCabinets.add(placed)
                    previewSegments.add(
                        WallSegmentPreviewItem(
                            id = placed.id,
                            label = "${placed.name} (${span.spanWidthMm.roundToInt()}مم)",
                            startOffsetMm = cursorMm,
                            endOffsetMm = cursorMm + span.spanWidthMm,
                            widthMm = span.spanWidthMm,
                            heightMm = placed.dimensions.y,
                            depthMm = span.depthFromWallMm,
                            elevationY = placed.position.y,
                            isFillerStrip = false,
                            colorHex = placed.material.colorHex
                        )
                    )
                    cursorMm = MathUtils.roundToMillimeter(cursorMm + span.spanWidthMm)
                }

                val trailingGap = MathUtils.roundToMillimeter(targetWallLengthMm - cursorMm)
                if (trailingGap >= minStripMm) {
                    addFillerSpan(cursorMm, trailingGap, "شريحة فيلر نهاية الجدار")
                }
            }

            FillerDistributionStrategy.EVEN_SPACERS_BETWEEN_CABINETS -> {
                val slotsCount = sortedSpans.size + 1
                val totalGapInt = rawRemainingGapMm.roundToInt().coerceAtLeast(0)
                val baseSlotInt = totalGapInt / slotsCount
                var remainderInt = totalGapInt - (baseSlotInt * slotsCount)

                var cursorMm = 0f
                for (slotIndex in 0 until slotsCount) {
                    val extraMm = if (remainderInt > 0) {
                        remainderInt -= 1
                        1f
                    } else 0f
                    val slotWidthMm = (baseSlotInt + extraMm.roundToInt()).toFloat()

                    if (slotWidthMm >= minStripMm) {
                        val label = when (slotIndex) {
                            0 -> "فاصل طرفي أول"
                            slotsCount - 1 -> "فاصل طرفي أخير"
                            else -> "فاصل بيني #$slotIndex"
                        }
                        addFillerSpan(cursorMm, slotWidthMm, label)
                        cursorMm = MathUtils.roundToMillimeter(cursorMm + slotWidthMm)
                    }

                    if (slotIndex < sortedSpans.size) {
                        val span = sortedSpans[slotIndex]
                        val placed = placeCabinetAtOffset(span, cursorMm)
                        repositionedCabinets.add(placed)
                        previewSegments.add(
                            WallSegmentPreviewItem(
                                id = placed.id,
                                label = "${placed.name} (${span.spanWidthMm.roundToInt()}مم)",
                                startOffsetMm = cursorMm,
                                endOffsetMm = cursorMm + span.spanWidthMm,
                                widthMm = span.spanWidthMm,
                                heightMm = placed.dimensions.y,
                                depthMm = span.depthFromWallMm,
                                elevationY = placed.position.y,
                                isFillerStrip = false,
                                colorHex = placed.material.colorHex
                            )
                        )
                        cursorMm = MathUtils.roundToMillimeter(cursorMm + span.spanWidthMm)
                    }
                }
            }
        }

        val generatedWidths = generatedFillers.map { it.dimensions.x }
        val statusMsg = when {
            generatedFillers.isNotEmpty() -> {
                val stripsDesc = generatedWidths.joinToString(" + ") { "${it.roundToInt()} مم" }
                "تم توليد ${generatedFillers.size} شريحة فيلر ($stripsDesc) لسد فراغ ${rawRemainingGapMm.roundToInt()} مم بدقة 1 مم"
            }
            rawRemainingGapMm == 0f -> {
                "الخزائن تغطي طول الجدار بالكامل (${targetWallLengthMm.roundToInt()} مم) بدون أي فراغ متبقٍ (0 مم)"
            }
            else -> {
                "الفراغ المتبقي (${rawRemainingGapMm.roundToInt()} مم) أقل من الحد الأدنى لعرض الفيلر (${minStripMm.roundToInt()} مم)"
            }
        }

        return SingleTierComputation(
            repositionedCabinets = repositionedCabinets,
            generatedFillers = generatedFillers,
            analysis = WallTierFillerAnalysis(
                tier = tier,
                wallSide = config.wallSide,
                userWallLengthMm = targetWallLengthMm,
                cabinetCount = sortedSpans.size,
                totalCabinetsWidthMm = totalCabinetsWidthMm,
                remainingGapMm = rawRemainingGapMm,
                generatedFillersCount = generatedFillers.size,
                generatedFillerWidthsMm = generatedWidths,
                segments = previewSegments.sortedBy { it.startOffsetMm },
                isOverfilled = false,
                statusMessageAr = statusMsg
            )
        )
    }
}
