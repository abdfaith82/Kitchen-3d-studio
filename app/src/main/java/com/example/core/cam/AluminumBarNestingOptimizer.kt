package com.example.core.cam

import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Nesting Algorithm strategy for 1D linear aluminum extrusion bar stock cutting.
 */
enum class AluminumNestingStrategy(
    val titleAr: String,
    val subtitleAr: String
) {
    BEST_FIT_DECREASING(
        titleAr = "أفضل تطابق تنازلي (Best-Fit Decreasing - BFD)",
        subtitleAr = "يرتب القطع تنازلياً ويضع كل قطعة في القضيب الذي يترك أقل فضلة متبقية لتقليل الهدر لأقصى درجة"
    ),
    FIRST_FIT_DECREASING(
        titleAr = "أول تطابق تنازلي (First-Fit Decreasing - FFD)",
        subtitleAr = "يرتب القطع تنازلياً ويضع كل قطعة في أول قضيب متاح يتسع لها لسرعة التجهيز في الورشة"
    ),
    MULTI_STOCK_OPTIMAL(
        titleAr = "التعشيق الذكي متعدد الأطوال (Multi-Bar Stock Optimizer)",
        subtitleAr = "يختار تلقائياً طول القضيب القياسي الأنسب من الأطوال المتوفرة لديك لتحقيق أقل نسبة هدر"
    )
}

/**
 * User-defined or cabinet-derived aluminum profile cut requirement with cabinet source tracking.
 */
data class CabinetryAluminumCutInput(
    val id: String,
    val profileName: String,
    val cabinetName: String = "خزانة المطبخ",
    val lengthMm: Float,
    val quantity: Int = 1,
    val profileTypeCode: String = "ALU_FRAME",
    val orientation: AluminumBarOrientation = AluminumBarOrientation.HORIZONTAL,
    val sectionSizeMm: Float = 45f,
    val thicknessMm: Float = 1.8f,
    val isCustomUserAdded: Boolean = false
) {
    val totalLinearMeters: Float
        get() = (lengthMm * quantity) / 1000f
}

/**
 * Configuration parameters for the 1D Aluminum Bar Nesting Algorithm.
 */
data class AluminumNestingConfig(
    val primaryBarLengthMm: Float = 6000f,
    val availableStandardBarLengthsMm: List<Float> = listOf(6000f),
    val sawKerfMm: Float = 4.0f,
    val endTrimMm: Float = 10.0f,
    val minReusableOffcutMm: Float = 250f,
    val strategy: AluminumNestingStrategy = AluminumNestingStrategy.BEST_FIT_DECREASING,
    val groupByProfileType: Boolean = true
) {
    val sanitizedBarLengthsMm: List<Float>
        get() = (availableStandardBarLengthsMm + primaryBarLengthMm)
            .map { it.coerceIn(500f, 12000f) }
            .distinct()
            .sortedDescending()
}

/**
 * A single individual cut piece positioned along a nested standard aluminum bar.
 */
data class PlacedAluminumCutPiece(
    val pieceNumberOnBar: Int,
    val globalPieceNumber: Int,
    val sourceId: String,
    val profileName: String,
    val cabinetName: String,
    val profileTypeCode: String,
    val orientation: AluminumBarOrientation,
    val lengthMm: Float,
    val startOffsetMm: Float,
    val endOffsetMm: Float,
    val kerfAfterMm: Float,
    val stockBarLengthMm: Float
) {
    val lengthCm: Float
        get() = lengthMm / 10f

    val barProportion: Float
        get() = (lengthMm / stockBarLengthMm.coerceAtLeast(1f)).coerceIn(0f, 1f)
}

/**
 * An optimized standard aluminum bar containing its placed cut pieces, kerf losses, and waste metrics.
 */
data class OptimizedAluminumBar(
    val barIndex: Int,
    val stockLengthMm: Float,
    val endTrimMm: Float,
    val profileTypeCode: String,
    val profileCategoryLabel: String,
    val cutPieces: List<PlacedAluminumCutPiece>,
    val netCutLengthMm: Float,
    val totalKerfLossMm: Float
) {
    val usableLengthMm: Float
        get() = (stockLengthMm - endTrimMm).coerceAtLeast(1f)

    val usedLengthWithKerfAndTrimMm: Float
        get() = (netCutLengthMm + totalKerfLossMm + endTrimMm).coerceAtMost(stockLengthMm)

    /** Uncut tail remnant at the end of the bar (الفضلة المتبقية). */
    val remnantOffcutMm: Float
        get() = (stockLengthMm - usedLengthWithKerfAndTrimMm).coerceAtLeast(0f)

    /** Total non-product material on this bar (tail offcut + saw kerf + end trim). */
    val totalBarWasteMm: Float
        get() = (stockLengthMm - netCutLengthMm).coerceAtLeast(0f)

    /** Net material utilization efficiency percentage (الصافي المستغل / طول القضيب القياسي). */
    val utilizationPercentage: Float
        get() = ((netCutLengthMm / stockLengthMm.coerceAtLeast(1f)) * 100f).coerceIn(0f, 100f)

    /** Total bar waste percentage (100% - utilizationPercentage). */
    val wastePercentage: Float
        get() = (100f - utilizationPercentage).coerceIn(0f, 100f)

    /** Remnant offcut percentage alone (excluding saw kerf & end trim). */
    val remnantOffcutPercentage: Float
        get() = ((remnantOffcutMm / stockLengthMm.coerceAtLeast(1f)) * 100f).coerceIn(0f, 100f)

    fun isReusableOffcut(minReusableMm: Float = 250f): Boolean = remnantOffcutMm >= minReusableMm
}

/**
 * Summary breakdown per aluminum profile category (e.g., Vertical Structural Posts, Horizontal Rails, Gola, Glass Frame).
 */
data class AluminumProfileCategorySummary(
    val profileTypeCode: String,
    val categoryTitleAr: String,
    val barsCount: Int,
    val cutPiecesCount: Int,
    val totalStockMeters: Float,
    val totalNetCutMeters: Float,
    val totalWasteMeters: Float,
    val wastePercentage: Float,
    val utilizationPercentage: Float
)

/**
 * Complete result of the 1D Aluminum Bar Nesting Optimization Algorithm.
 */
data class AluminumNestingResult(
    val config: AluminumNestingConfig,
    val bars: List<OptimizedAluminumBar>,
    val categorySummaries: List<AluminumProfileCategorySummary>,
    val oversizedCutAdjustments: List<String>,
    val totalBarsCount: Int,
    val barCountsByLengthMm: Map<Int, Int>,
    val totalIndividualCutPiecesCount: Int,
    val totalStockLengthMm: Float,
    val totalNetCutLengthMm: Float,
    val totalKerfLossMm: Float,
    val totalEndTrimLossMm: Float,
    val totalRemnantOffcutMm: Float,
    val totalWasteLengthMm: Float,
    val reusableOffcutsCount: Int,
    val reusableOffcutsTotalMm: Float,
    val overallUtilizationPercentage: Float,
    val overallWastePercentage: Float,
    val naiveUnoptimizedBarsCount: Int,
    val savedBarsCount: Int
) {
    val totalStockMeters: Float get() = totalStockLengthMm / 1000f
    val totalNetCutMeters: Float get() = totalNetCutLengthMm / 1000f
    val totalWasteMeters: Float get() = totalWasteLengthMm / 1000f
    val totalRemnantOffcutMeters: Float get() = totalRemnantOffcutMm / 1000f
}

/**
 * Dedicated 1D Linear Stock Cutting / Nesting Optimizer for Kitchen Cabinetry Aluminum Profiles.
 * Supports configurable standard bar lengths (e.g., 6000mm, 5800mm, 6500mm, 4500mm, or custom),
 * saw blade kerf allowance, factory end trim, and Best-Fit / First-Fit / Multi-Stock algorithms.
 */
object AluminumBarNestingOptimizer {

    val STANDARD_PRESET_BAR_LENGTHS_MM = listOf(6000f, 5800f, 6500f, 4500f, 3000f)

    fun profileCategoryTitleAr(profileTypeCode: String): String = when (profileTypeCode) {
        "ALU_POST_V" -> "قوائم ألمنيوم هيكلية عمودية (Vertical Structural Posts)"
        "ALU_CROSS_H" -> "عوارض ألمنيوم أفقية أمامية/خلفية (Horizontal Crossbars)"
        "ALU_DEPTH_H" -> "عوارض ألمنيوم جانبية للعمق (Depth Side Rails)"
        "GOLA_L" -> "مقابض مخفية غولا علوي (Gola L-Profile)"
        "GOLA_C" -> "مقابض مخفية غولا وسطي (Gola C-Profile)"
        "GLASS_FRAME_V", "GLASS_FRAME_H" -> "قطاعات إطارات درف الزجاج (Aluminum Glass Door Frames)"
        "PLINTH_ALU" -> "وزرات ألمنيوم سفلية مقاومة للماء (Aluminum Plinth)"
        "CUSTOM_PROFILE" -> "قطاعات ألمنيوم مخصصة إضافية (Custom Profiles)"
        else -> "قطاعات وبروفايلات ألمنيوم هيكلية ($profileTypeCode)"
    }

    /**
     * Extracts all required aluminum profile cuts from a list of [ParametricCabinet] instances,
     * preserving the source cabinet name for every cut piece.
     */
    fun extractCabinetryCutInputs(cabinets: List<ParametricCabinet>): List<CabinetryAluminumCutInput> {
        val inputs = mutableListOf<CabinetryAluminumCutInput>()
        cabinets.forEachIndexed { cabIdx, cabinet ->
            val rawCuts = cabinet.generateAluminumCuts()
            rawCuts.forEachIndexed { cutIdx, cut ->
                if (cut.quantity > 0 && cut.lengthMm > 0f) {
                    inputs.add(
                        CabinetryAluminumCutInput(
                            id = "${cabinet.id}_cut_${cabIdx}_$cutIdx",
                            profileName = cut.profileName,
                            cabinetName = cabinet.name,
                            lengthMm = cut.lengthMm,
                            quantity = cut.quantity,
                            profileTypeCode = cut.profileTypeCode,
                            orientation = cut.orientation,
                            sectionSizeMm = cut.profileSectionSizeMm,
                            thicknessMm = cut.profileThicknessMm,
                            isCustomUserAdded = false
                        )
                    )
                }
            }
        }
        return inputs
    }

    /**
     * Runs the 1D Nesting Optimization Algorithm on the given cabinetry aluminum profile cuts
     * and standard bar lengths configuration.
     */
    fun optimizeCutList(
        cutInputs: List<CabinetryAluminumCutInput>,
        config: AluminumNestingConfig = AluminumNestingConfig()
    ): AluminumNestingResult {
        val kerfMm = config.sawKerfMm.coerceIn(0f, 25f)
        val endTrimMm = config.endTrimMm.coerceIn(0f, 150f)
        val candidateLengths = if (config.strategy == AluminumNestingStrategy.MULTI_STOCK_OPTIMAL) {
            config.sanitizedBarLengthsMm
        } else {
            listOf(config.primaryBarLengthMm.coerceIn(500f, 12000f))
        }
        val maxStockLengthMm = candidateLengths.maxOrNull() ?: 6000f
        val maxUsableSingleBarMm = (maxStockLengthMm - endTrimMm).coerceAtLeast(100f)

        data class AtomicCutItem(
            val sourceId: String,
            val profileName: String,
            val cabinetName: String,
            val profileTypeCode: String,
            val orientation: AluminumBarOrientation,
            val lengthMm: Float
        )

        val oversizedNotes = mutableListOf<String>()
        val expandedItems = mutableListOf<AtomicCutItem>()

        cutInputs.forEach { input ->
            val validQty = input.quantity.coerceAtLeast(0)
            val rawLength = input.lengthMm.coerceAtLeast(10f)
            repeat(validQty) {
                if (rawLength <= maxUsableSingleBarMm) {
                    expandedItems.add(
                        AtomicCutItem(
                            sourceId = input.id,
                            profileName = input.profileName,
                            cabinetName = input.cabinetName,
                            profileTypeCode = input.profileTypeCode,
                            orientation = input.orientation,
                            lengthMm = rawLength
                        )
                    )
                } else {
                    // Automatically split oversized profile requests across multiple stock bars with splice joint note
                    var remainingToSplit = rawLength
                    var partIdx = 1
                    while (remainingToSplit > 0f) {
                        val segmentLen = remainingToSplit.coerceAtMost(maxUsableSingleBarMm - kerfMm)
                        expandedItems.add(
                            AtomicCutItem(
                                sourceId = "${input.id}_seg$partIdx",
                                profileName = "${input.profileName} (جزء $partIdx)",
                                cabinetName = input.cabinetName,
                                profileTypeCode = input.profileTypeCode,
                                orientation = input.orientation,
                                lengthMm = segmentLen
                            )
                        )
                        remainingToSplit -= segmentLen
                        partIdx++
                    }
                    oversizedNotes.add(
                        "تم تقسيم القطعة الطويلة (${rawLength.roundToInt()} مم - ${input.cabinetName}) تلقائياً لتجاوزها الطول الصافي للقضيب القياسي (${maxUsableSingleBarMm.roundToInt()} مم)."
                    )
                }
            }
        }

        if (expandedItems.isEmpty()) {
            return AluminumNestingResult(
                config = config,
                bars = emptyList(),
                categorySummaries = emptyList(),
                oversizedCutAdjustments = oversizedNotes,
                totalBarsCount = 0,
                barCountsByLengthMm = emptyMap(),
                totalIndividualCutPiecesCount = 0,
                totalStockLengthMm = 0f,
                totalNetCutLengthMm = 0f,
                totalKerfLossMm = 0f,
                totalEndTrimLossMm = 0f,
                totalRemnantOffcutMm = 0f,
                totalWasteLengthMm = 0f,
                reusableOffcutsCount = 0,
                reusableOffcutsTotalMm = 0f,
                overallUtilizationPercentage = 0f,
                overallWastePercentage = 0f,
                naiveUnoptimizedBarsCount = 0,
                savedBarsCount = 0
            )
        }

        // Naive unoptimized sequential bar count (for comparison metric)
        val naiveBarsCount = calculateNaiveSequentialBarCount(
            itemsLengths = expandedItems.map { it.lengthMm },
            stockLengthMm = config.primaryBarLengthMm.coerceIn(500f, 12000f),
            kerfMm = kerfMm,
            endTrimMm = endTrimMm
        )

        // Group by profile type if enabled (e.g., Gola vs Structural vs Glass Frame)
        val groupedItems: Map<String, List<AtomicCutItem>> = if (config.groupByProfileType) {
            expandedItems.groupBy { normalizeGroupKey(it.profileTypeCode) }
        } else {
            mapOf("ALL_PROFILES" to expandedItems)
        }

        class MutableOpenBar(
            val stockLengthMm: Float,
            val endTrimMm: Float,
            val groupKey: String,
            val categoryLabel: String
        ) {
            val usableLengthMm: Float = (stockLengthMm - endTrimMm).coerceAtLeast(1f)
            val items = mutableListOf<AtomicCutItem>()
            var netCutSumMm: Float = 0f
            var kerfSumMm: Float = 0f

            val currentOccupiedOnUsableMm: Float
                get() = netCutSumMm + kerfSumMm

            val remainingUsableMm: Float
                get() = (usableLengthMm - currentOccupiedOnUsableMm).coerceAtLeast(0f)

            fun requiredSpaceFor(cutLengthMm: Float): Float {
                // Every cut on the bar consumes saw kerf unless it fills the exact end of the bar within kerf tolerance
                return if (cutLengthMm <= remainingUsableMm && (cutLengthMm + kerfMm) > remainingUsableMm) {
                    cutLengthMm // Last cut on the bar doesn't need an extra kerf if it fits exactly
                } else {
                    cutLengthMm + kerfMm
                }
            }

            fun canFit(cutLengthMm: Float): Boolean {
                return cutLengthMm <= remainingUsableMm
            }

            fun addCut(item: AtomicCutItem) {
                val appliedKerf = if ((item.lengthMm + kerfMm) <= remainingUsableMm) {
                    kerfMm
                } else {
                    (remainingUsableMm - item.lengthMm).coerceAtLeast(0f)
                }
                items.add(item)
                netCutSumMm += item.lengthMm
                kerfSumMm += appliedKerf
            }
        }

        val allOpenBars = mutableListOf<MutableOpenBar>()

        groupedItems.forEach { (groupKey, groupCuts) ->
            // Sort descending by cut length (core requirement of FFD / BFD 1D Bin Packing)
            val sortedCuts = groupCuts.sortedByDescending { it.lengthMm }
            val groupOpenBars = mutableListOf<MutableOpenBar>()
            val categoryLabel = if (groupKey == "ALL_PROFILES") {
                "كافة قطاعات الألمنيوم المدمجة (Combined Profiles)"
            } else {
                profileCategoryTitleAr(groupKey)
            }

            sortedCuts.forEachIndexed { idx, cutItem ->
                val targetBar: MutableOpenBar? = when (config.strategy) {
                    AluminumNestingStrategy.FIRST_FIT_DECREASING -> {
                        groupOpenBars.firstOrNull { it.canFit(cutItem.lengthMm) }
                    }
                    AluminumNestingStrategy.BEST_FIT_DECREASING,
                    AluminumNestingStrategy.MULTI_STOCK_OPTIMAL -> {
                        // Pick the open bar that leaves the smallest non-negative residual after placing this cut
                        groupOpenBars
                            .filter { it.canFit(cutItem.lengthMm) }
                            .minByOrNull { it.remainingUsableMm - it.requiredSpaceFor(cutItem.lengthMm) }
                    }
                }

                if (targetBar != null) {
                    targetBar.addCut(cutItem)
                } else {
                    // Choose the best standard bar length to open
                    val chosenStockLength = selectOptimalNewBarLength(
                        currentPieceMm = cutItem.lengthMm,
                        remainingPiecesMm = sortedCuts.drop(idx + 1).map { it.lengthMm },
                        candidateStockLengthsMm = candidateLengths,
                        kerfMm = kerfMm,
                        endTrimMm = endTrimMm
                    )
                    val newBar = MutableOpenBar(
                        stockLengthMm = chosenStockLength,
                        endTrimMm = endTrimMm,
                        groupKey = groupKey,
                        categoryLabel = categoryLabel
                    )
                    newBar.addCut(cutItem)
                    groupOpenBars.add(newBar)
                }
            }

            allOpenBars.addAll(groupOpenBars)
        }

        // Convert MutableOpenBars into immutable OptimizedAluminumBar instances with exact piece start/end offsets
        var runningGlobalPieceIndex = 1
        val finalizedBars = allOpenBars.mapIndexed { barIdx, openBar ->
            var cursorMm = openBar.endTrimMm
            var accumulatedKerf = 0f
            val placedPieces = openBar.items.mapIndexed { pieceIdx, atomic ->
                val startMm = cursorMm
                val endMm = startMm + atomic.lengthMm
                val remainingAfterEnd = (openBar.stockLengthMm - endMm).coerceAtLeast(0f)
                val appliedKerf = if (remainingAfterEnd >= kerfMm) kerfMm else remainingAfterEnd
                cursorMm = endMm + appliedKerf
                accumulatedKerf += appliedKerf

                PlacedAluminumCutPiece(
                    pieceNumberOnBar = pieceIdx + 1,
                    globalPieceNumber = runningGlobalPieceIndex++,
                    sourceId = atomic.sourceId,
                    profileName = atomic.profileName,
                    cabinetName = atomic.cabinetName,
                    profileTypeCode = atomic.profileTypeCode,
                    orientation = atomic.orientation,
                    lengthMm = atomic.lengthMm,
                    startOffsetMm = startMm,
                    endOffsetMm = endMm,
                    kerfAfterMm = appliedKerf,
                    stockBarLengthMm = openBar.stockLengthMm
                )
            }

            OptimizedAluminumBar(
                barIndex = barIdx + 1,
                stockLengthMm = openBar.stockLengthMm,
                endTrimMm = openBar.endTrimMm,
                profileTypeCode = openBar.groupKey,
                profileCategoryLabel = openBar.categoryLabel,
                cutPieces = placedPieces,
                netCutLengthMm = placedPieces.sumOf { it.lengthMm.toDouble() }.toFloat(),
                totalKerfLossMm = accumulatedKerf
            )
        }

        val totalStockMm = finalizedBars.sumOf { it.stockLengthMm.toDouble() }.toFloat()
        val totalNetCutMm = finalizedBars.sumOf { it.netCutLengthMm.toDouble() }.toFloat()
        val totalKerfMm = finalizedBars.sumOf { it.totalKerfLossMm.toDouble() }.toFloat()
        val totalTrimMm = finalizedBars.sumOf { it.endTrimMm.toDouble() }.toFloat()
        val totalRemnantMm = finalizedBars.sumOf { it.remnantOffcutMm.toDouble() }.toFloat()
        val totalWasteMm = (totalStockMm - totalNetCutMm).coerceAtLeast(0f)

        val overallUtilizationPct = if (totalStockMm > 0f) {
            ((totalNetCutMm / totalStockMm) * 100f).coerceIn(0f, 100f)
        } else 0f
        val overallWastePct = if (totalStockMm > 0f) {
            (100f - overallUtilizationPct).coerceIn(0f, 100f)
        } else 0f

        val reusableBars = finalizedBars.filter { it.isReusableOffcut(config.minReusableOffcutMm) }
        val barCountsByLength = finalizedBars
            .groupBy { it.stockLengthMm.roundToInt() }
            .mapValues { it.value.size }

        val categorySummaries = finalizedBars
            .groupBy { it.profileTypeCode }
            .map { (typeCode, catBars) ->
                val catStockMm = catBars.sumOf { it.stockLengthMm.toDouble() }.toFloat()
                val catNetMm = catBars.sumOf { it.netCutLengthMm.toDouble() }.toFloat()
                val catWasteMm = (catStockMm - catNetMm).coerceAtLeast(0f)
                val catUtilPct = if (catStockMm > 0f) (catNetMm / catStockMm) * 100f else 0f
                AluminumProfileCategorySummary(
                    profileTypeCode = typeCode,
                    categoryTitleAr = catBars.first().profileCategoryLabel,
                    barsCount = catBars.size,
                    cutPiecesCount = catBars.sumOf { it.cutPieces.size },
                    totalStockMeters = catStockMm / 1000f,
                    totalNetCutMeters = catNetMm / 1000f,
                    totalWasteMeters = catWasteMm / 1000f,
                    wastePercentage = (100f - catUtilPct).coerceIn(0f, 100f),
                    utilizationPercentage = catUtilPct.coerceIn(0f, 100f)
                )
            }

        return AluminumNestingResult(
            config = config,
            bars = finalizedBars,
            categorySummaries = categorySummaries,
            oversizedCutAdjustments = oversizedNotes.distinct(),
            totalBarsCount = finalizedBars.size,
            barCountsByLengthMm = barCountsByLength,
            totalIndividualCutPiecesCount = finalizedBars.sumOf { it.cutPieces.size },
            totalStockLengthMm = totalStockMm,
            totalNetCutLengthMm = totalNetCutMm,
            totalKerfLossMm = totalKerfMm,
            totalEndTrimLossMm = totalTrimMm,
            totalRemnantOffcutMm = totalRemnantMm,
            totalWasteLengthMm = totalWasteMm,
            reusableOffcutsCount = reusableBars.size,
            reusableOffcutsTotalMm = reusableBars.sumOf { it.remnantOffcutMm.toDouble() }.toFloat(),
            overallUtilizationPercentage = overallUtilizationPct,
            overallWastePercentage = overallWastePct,
            naiveUnoptimizedBarsCount = max(naiveBarsCount, finalizedBars.size),
            savedBarsCount = (naiveBarsCount - finalizedBars.size).coerceAtLeast(0)
        )
    }

    private fun normalizeGroupKey(profileTypeCode: String): String = when {
        profileTypeCode.startsWith("GLASS_FRAME") -> "GLASS_FRAME_V"
        profileTypeCode.startsWith("GOLA_L") -> "GOLA_L"
        profileTypeCode.startsWith("GOLA_C") -> "GOLA_C"
        profileTypeCode.startsWith("ALU_POST") ||
            profileTypeCode.startsWith("ALU_CROSS") ||
            profileTypeCode.startsWith("ALU_DEPTH") ||
            profileTypeCode == "ALU_FRAME" -> "ALU_POST_V"
        else -> profileTypeCode
    }

    /**
     * Selects the best stock bar length from [candidateStockLengthsMm] by simulating greedy lookahead
     * packing of [currentPieceMm] + [remainingPiecesMm] and picking the length with the lowest waste %.
     */
    private fun selectOptimalNewBarLength(
        currentPieceMm: Float,
        remainingPiecesMm: List<Float>,
        candidateStockLengthsMm: List<Float>,
        kerfMm: Float,
        endTrimMm: Float
    ): Float {
        if (candidateStockLengthsMm.size <= 1) {
            return candidateStockLengthsMm.firstOrNull() ?: 6000f
        }

        val validCandidates = candidateStockLengthsMm.filter { (it - endTrimMm) >= currentPieceMm }
        if (validCandidates.isEmpty()) {
            return candidateStockLengthsMm.maxOrNull() ?: 6000f
        }

        var bestLength = validCandidates.first()
        var bestWasteRatio = Float.MAX_VALUE

        for (stockLen in validCandidates) {
            val usable = stockLen - endTrimMm
            var usedNet = currentPieceMm
            var usedWithKerf = currentPieceMm + kerfMm

            for (nextLen in remainingPiecesMm) {
                val needed = if ((usedWithKerf + nextLen + kerfMm) <= usable) {
                    nextLen + kerfMm
                } else if ((usedWithKerf + nextLen) <= usable) {
                    nextLen
                } else {
                    Float.MAX_VALUE
                }
                if (usedWithKerf + needed <= usable) {
                    usedNet += nextLen
                    usedWithKerf += needed
                }
            }

            val wasteRatio = (stockLen - usedNet) / stockLen
            if (wasteRatio < bestWasteRatio) {
                bestWasteRatio = wasteRatio
                bestLength = stockLen
            }
        }

        return bestLength
    }

    private fun calculateNaiveSequentialBarCount(
        itemsLengths: List<Float>,
        stockLengthMm: Float,
        kerfMm: Float,
        endTrimMm: Float
    ): Int {
        if (itemsLengths.isEmpty()) return 0
        val usable = (stockLengthMm - endTrimMm).coerceAtLeast(100f)
        var bars = 1
        var used = 0f
        for (len in itemsLengths) {
            val safeLen = len.coerceAtMost(usable)
            if (used + safeLen <= usable) {
                used += safeLen + kerfMm
            } else {
                bars++
                used = safeLen + kerfMm
            }
        }
        return bars
    }

    /**
     * Generates a formatted workshop cut-list report suitable for clipboard copy, WhatsApp/Email sharing, or CNC operator printouts.
     */
    fun generateWorkshopCutListReport(result: AluminumNestingResult): String {
        val sb = StringBuilder()
        sb.appendLine("====================================================")
        sb.appendLine("كشف القص والتعشيق الهندسي لبروفايلات الألمنيوم (1D Nesting)")
        sb.appendLine("====================================================")
        sb.appendLine("خوارزمية التعشيق: ${result.config.strategy.titleAr}")
        val barLengthsStr = result.config.sanitizedBarLengthsMm.joinToString(" / ") { "${it.roundToInt()} مم (${String.format(Locale.US, "%.2f", it / 1000f)} م)" }
        sb.appendLine("أطوال القضبان القياسية المعتمدة: $barLengthsStr")
        sb.appendLine("سُمك شفرة المنشار (Kerf): ${String.format(Locale.US, "%.1f", result.config.sawKerfMm)} مم | تهذيب الأطراف: ${result.config.endTrimMm.roundToInt()} مم")
        sb.appendLine("----------------------------------------------------")
        sb.appendLine("إجمالي القضبان القياسية المطلوبة: ${result.totalBarsCount} قضيب")
        sb.appendLine("إجمالي عدد القطع المقصوصة: ${result.totalIndividualCutPiecesCount} قطعة")
        sb.appendLine("إجمالي الطول الخام للقضبان: ${String.format(Locale.US, "%.2f", result.totalStockMeters)} م.ط (${result.totalStockLengthMm.roundToInt()} مم)")
        sb.appendLine("إجمالي الطول الصافي للقطع: ${String.format(Locale.US, "%.2f", result.totalNetCutMeters)} م.ط (${result.totalNetCutLengthMm.roundToInt()} مم)")
        sb.appendLine("نسبة الاستغلال الكلية (Yield): ${String.format(Locale.US, "%.1f", result.overallUtilizationPercentage)}%")
        sb.appendLine("نسبة الهدر الكلية (Waste): ${String.format(Locale.US, "%.1f", result.overallWastePercentage)}% (${result.totalWasteLengthMm.roundToInt()} مم)")
        sb.appendLine("  - فضلات متبقية (Offcuts): ${result.totalRemnantOffcutMm.roundToInt()} مم")
        sb.appendLine("  - فاقد المنشار والتهذيب (Kerf + Trim): ${(result.totalKerfLossMm + result.totalEndTrimLossMm).roundToInt()} مم")
        sb.appendLine("----------------------------------------------------")

        result.bars.forEach { bar ->
            sb.appendLine(
                "قضيب #${bar.barIndex} [طول ${bar.stockLengthMm.roundToInt()} مم] - ${bar.profileCategoryLabel} | " +
                    "استغلال: ${String.format(Locale.US, "%.1f", bar.utilizationPercentage)}% | " +
                    "هدر: ${String.format(Locale.US, "%.1f", bar.wastePercentage)}% (فضلة: ${bar.remnantOffcutMm.roundToInt()} مم)"
            )
            bar.cutPieces.forEach { piece ->
                sb.appendLine(
                    "   • قطعة #${piece.pieceNumberOnBar}: ${piece.lengthMm.roundToInt()} مم (${String.format(Locale.US, "%.1f", piece.lengthCm)} سم) " +
                        "| [${piece.startOffsetMm.roundToInt()} ← ${piece.endOffsetMm.roundToInt()} مم] " +
                        "| ${piece.cabinetName} - ${piece.profileName}"
                )
            }
            sb.appendLine()
        }
        return sb.toString()
    }
}
