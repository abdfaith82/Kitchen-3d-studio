package com.example.core.cam

import kotlin.math.max

/**
 * Placed rectangular part positioned on a 2440 x 1220 mm raw sheet.
 */
data class NestedPartPlacement(
    val partName: String,
    val x: Float,
    val y: Float,
    val widthMm: Float,
    val lengthMm: Float,
    val isRotated: Boolean,
    val category: CutPartCategory = CutPartCategory.MELAMINE_SIDE
)

/**
 * A packed sheet with all its placed parts, kerf lines, and waste calculation.
 */
data class PackedSheet(
    val sheetIndex: Int,
    val rawWidthMm: Float = 2440f,
    val rawLengthMm: Float = 1220f,
    val material: PanelMaterial,
    val placements: List<NestedPartPlacement>,
    val usedAreaSqMm: Float,
    val totalAreaSqMm: Float = rawWidthMm * rawLengthMm
) {
    val utilizationPercentage: Float get() = (usedAreaSqMm / totalAreaSqMm) * 100f
    val wastePercentage: Float get() = (100f - utilizationPercentage).coerceAtLeast(0f)
    val wasteAreaSqM: Float get() = (totalAreaSqMm - usedAreaSqMm) / 1_000_000f
}

/**
 * 1D Aluminum Bar Stock cutting plan for a single 6000mm extrusion bar.
 */
data class PackedAluminumBar(
    val barIndex: Int,
    val standardBarLengthMm: Float = 6000f,
    val cuts: List<Pair<String, Float>>, // List of (ProfileName, CutLengthMm)
    val usedLengthMm: Float
) {
    val remainingWasteMm: Float get() = (standardBarLengthMm - usedLengthMm).coerceAtLeast(0f)
    val utilizationPercentage: Float get() = ((usedLengthMm / standardBarLengthMm.coerceAtLeast(1f)) * 100f).coerceIn(0f, 100f)
    val wastePercentage: Float get() = (100f - utilizationPercentage).coerceIn(0f, 100f)
}

/**
 * Comprehensive CAM Manufacturing and Cost Summary including Section 4 Melamine & Aluminum metrics.
 */
data class ManufacturingBOMReport(
    val totalCabinetsCount: Int,
    val packedWoodSheets: List<PackedSheet>,
    val packedAluminumBars: List<PackedAluminumBar>,
    val aluminumSummary: AluminumCalculationSummary,
    val melamineSidesParts: List<CutPart>,
    val melamineBottomsAndShelvesParts: List<CutPart>,
    val melamineStretchersParts: List<CutPart>,
    val backPanelParts: List<CutPart>,
    val totalRawSheetsCount: Int,
    val totalMdf18Sheets: Int,
    val totalMdf8Sheets: Int,
    val totalAluminumBarsCount: Int,
    val overallWoodUtilizationPct: Float,
    val overallWoodWastePct: Float,
    val totalEdgeBandingMeters: Float,
    val hardwareList: Map<String, Int>,
    val estimatedWoodCost: Float,
    val estimatedAluminumCost: Float,
    val estimatedEdgeBandingCost: Float,
    val estimatedHardwareCost: Float,
    val estimatedTotalCost: Float
)

/**
 * Industrial CAD/CAM 2D Guillotine Nesting & 1D Extrusion Bar optimizer.
 * Designed to minimize raw material waste and produce exact fabrication cutting plans.
 */
object NestingEngine {

    const val DEFAULT_SHEET_WIDTH_MM = 2440f
    const val DEFAULT_SHEET_LENGTH_MM = 1220f
    const val DEFAULT_SAW_KERF_MM = 3.2f // Circular saw blade kerf thickness
    const val DEFAULT_SHEET_TRIM_MM = 10.0f // Edge trim cut around factory raw sheet
    const val STANDARD_ALUMINUM_BAR_MM = 6000f // 6.0 meters extrusion bar

    /**
     * Executes 2D Guillotine Bin Packing for cut panels grouped by material.
     */
    fun packPanels(
        parts: List<CutPart>,
        sheetWidthMm: Float = DEFAULT_SHEET_WIDTH_MM,
        sheetLengthMm: Float = DEFAULT_SHEET_LENGTH_MM,
        bladeKerfMm: Float = DEFAULT_SAW_KERF_MM,
        sheetTrimMm: Float = DEFAULT_SHEET_TRIM_MM
    ): List<PackedSheet> {
        val packedSheets = mutableListOf<PackedSheet>()
        val groupedByMaterial = parts.groupBy { it.material }

        groupedByMaterial.forEach { (material, materialParts) ->
            data class PieceItem(
                val partName: String,
                val widthMm: Float,
                val lengthMm: Float,
                val canRotate: Boolean,
                val category: CutPartCategory
            )

            val itemsToPack = mutableListOf<PieceItem>()
            materialParts.forEach { part ->
                for (i in 0 until part.quantity) {
                    itemsToPack.add(
                        PieceItem(
                            partName = part.partName,
                            widthMm = part.widthMm,
                            lengthMm = part.lengthMm,
                            canRotate = !part.isGrainDirectionMatters,
                            category = part.category
                        )
                    )
                }
            }

            // Sort in descending order of area (Best-Fit Decreasing)
            itemsToPack.sortByDescending { it.widthMm * it.lengthMm }

            var currentSheetPlacements = mutableListOf<NestedPartPlacement>()
            var currentSheetUsedArea = 0f
            var currentX = sheetTrimMm
            var currentY = sheetTrimMm
            var currentLevelHeight = 0f
            val maxRight = sheetWidthMm - sheetTrimMm
            val maxBottom = sheetLengthMm - sheetTrimMm

            fun finalizeSheet() {
                if (currentSheetPlacements.isNotEmpty()) {
                    packedSheets.add(
                        PackedSheet(
                            sheetIndex = packedSheets.size + 1,
                            rawWidthMm = sheetWidthMm,
                            rawLengthMm = sheetLengthMm,
                            material = material,
                            placements = currentSheetPlacements.toList(),
                            usedAreaSqMm = currentSheetUsedArea
                        )
                    )
                    currentSheetPlacements = mutableListOf()
                    currentSheetUsedArea = 0f
                    currentX = sheetTrimMm
                    currentY = sheetTrimMm
                    currentLevelHeight = 0f
                }
            }

            for (item in itemsToPack) {
                val normalFits = (currentX + item.widthMm <= maxRight) &&
                        (currentY + max(currentLevelHeight, item.lengthMm) <= maxBottom)
                val rotatedFits = item.canRotate &&
                        (currentX + item.lengthMm <= maxRight) &&
                        (currentY + max(currentLevelHeight, item.widthMm) <= maxBottom)

                if (normalFits || rotatedFits) {
                    val useRotated = when {
                        !normalFits -> true
                        !rotatedFits -> false
                        else -> {
                            max(currentLevelHeight, item.widthMm) < max(currentLevelHeight, item.lengthMm)
                        }
                    }

                    val pW = if (useRotated) item.lengthMm else item.widthMm
                    val pL = if (useRotated) item.widthMm else item.lengthMm

                    currentSheetPlacements.add(
                        NestedPartPlacement(
                            partName = item.partName,
                            x = currentX,
                            y = currentY,
                            widthMm = pW,
                            lengthMm = pL,
                            isRotated = useRotated,
                            category = item.category
                        )
                    )
                    currentSheetUsedArea += (pW * pL)
                    currentX += pW + bladeKerfMm
                    currentLevelHeight = max(currentLevelHeight, pL)
                } else {
                    val nextY = currentY + currentLevelHeight + bladeKerfMm
                    val nextNormalFits = (sheetTrimMm + item.widthMm <= maxRight) &&
                            (nextY + item.lengthMm <= maxBottom)
                    val nextRotatedFits = item.canRotate &&
                            (sheetTrimMm + item.lengthMm <= maxRight) &&
                            (nextY + item.widthMm <= maxBottom)

                    if (nextNormalFits || nextRotatedFits) {
                        currentX = sheetTrimMm
                        currentY = nextY
                        currentLevelHeight = 0f

                        val useRotated = when {
                            !nextNormalFits -> true
                            !nextRotatedFits -> false
                            else -> item.widthMm < item.lengthMm
                        }

                        val pW = if (useRotated) item.lengthMm else item.widthMm
                        val pL = if (useRotated) item.widthMm else item.lengthMm

                        currentSheetPlacements.add(
                            NestedPartPlacement(
                                partName = item.partName,
                                x = currentX,
                                y = currentY,
                                widthMm = pW,
                                lengthMm = pL,
                                isRotated = useRotated,
                                category = item.category
                            )
                        )
                        currentSheetUsedArea += (pW * pL)
                        currentX += pW + bladeKerfMm
                        currentLevelHeight = pL
                    } else {
                        finalizeSheet()

                        val brandNewNormalFits = (sheetTrimMm + item.widthMm <= maxRight) &&
                                (sheetTrimMm + item.lengthMm <= maxBottom)
                        val brandNewRotatedFits = item.canRotate &&
                                (sheetTrimMm + item.lengthMm <= maxRight) &&
                                (sheetTrimMm + item.widthMm <= maxBottom)

                        val useRotated = when {
                            !brandNewNormalFits -> true
                            !brandNewRotatedFits -> false
                            else -> item.widthMm < item.lengthMm
                        }

                        val pW = if (useRotated) item.lengthMm else item.widthMm
                        val pL = if (useRotated) item.widthMm else item.lengthMm

                        currentSheetPlacements.add(
                            NestedPartPlacement(
                                partName = item.partName,
                                x = sheetTrimMm,
                                y = sheetTrimMm,
                                widthMm = pW,
                                lengthMm = pL,
                                isRotated = useRotated,
                                category = item.category
                            )
                        )
                        currentSheetUsedArea += (pW * pL)
                        currentX = sheetTrimMm + pW + bladeKerfMm
                        currentLevelHeight = pL
                    }
                }
            }

            finalizeSheet()
        }

        return packedSheets
    }

    /**
     * 1D Bar Stock Cutting Optimizer for aluminum profiles using Best-Fit Decreasing (BFD).
     */
    fun packAluminumProfiles(
        cuts: List<AluminumProfileCut>,
        barLengthMm: Float = STANDARD_ALUMINUM_BAR_MM,
        kerfMm: Float = 4.0f
    ): List<PackedAluminumBar> {
        val safeBarLen = barLengthMm.coerceAtLeast(500f)
        val flatCuts = mutableListOf<Pair<String, Float>>()
        cuts.forEach { cut ->
            for (i in 0 until cut.quantity) {
                flatCuts.add(Pair(cut.profileName, cut.lengthMm.coerceAtMost(safeBarLen - kerfMm)))
            }
        }

        // Best-Fit Decreasing (BFD): sort pieces in descending order of length
        flatCuts.sortByDescending { it.second }

        val bars = mutableListOf<PackedAluminumBar>()

        for (cut in flatCuts) {
            val cutLengthWithKerf = cut.second + kerfMm

            // Select the open bar with the minimum remaining capacity that still fits cutLengthWithKerf
            val bestBarIndex = bars.indices
                .filter { idx -> (safeBarLen - bars[idx].usedLengthMm) >= cutLengthWithKerf }
                .minByOrNull { idx -> (safeBarLen - bars[idx].usedLengthMm) - cutLengthWithKerf }
                ?: -1

            if (bestBarIndex != -1) {
                val existing = bars[bestBarIndex]
                val updatedCuts = existing.cuts + cut
                val updatedUsed = existing.usedLengthMm + cutLengthWithKerf
                bars[bestBarIndex] = existing.copy(cuts = updatedCuts, usedLengthMm = updatedUsed)
            } else {
                bars.add(
                    PackedAluminumBar(
                        barIndex = bars.size + 1,
                        standardBarLengthMm = safeBarLen,
                        cuts = listOf(cut),
                        usedLengthMm = cutLengthWithKerf
                    )
                )
            }
        }

        return bars
    }

    /**
     * Calculates complete factory manufacturing report, Section 4 breakdown, and pricing.
     */
    fun calculateCompleteReport(cabinets: List<ParametricCabinet>): ManufacturingBOMReport {
        val allCutParts = cabinets.flatMap { it.generateCarcassCutParts() }
        val allAluminumCuts = cabinets.flatMap { it.generateAluminumCuts() }
        val aluSummary = ParametricCabinet.calculateAluminumSummaryForCabinets(cabinets)

        val packedSheets = packPanels(allCutParts)
        val packedAluminum = packAluminumProfiles(allAluminumCuts)

        val mdf18Count = packedSheets.count {
            it.material == PanelMaterial.MDF_18MM ||
                    it.material == PanelMaterial.MDF_16MM ||
                    it.material == PanelMaterial.CHIPBOARD_18MM ||
                    it.material == PanelMaterial.PLYWOOD_18MM
        }
        val mdf8Count = packedSheets.count {
            it.material == PanelMaterial.MDF_8MM ||
                    it.material == PanelMaterial.MDF_6MM ||
                    it.material == PanelMaterial.ALUMINUM_CLAD_4MM
        }

        val totalUsedWoodArea = packedSheets.sumOf { it.usedAreaSqMm.toDouble() }.toFloat()
        val totalRawWoodArea = packedSheets.sumOf { it.totalAreaSqMm.toDouble() }.toFloat()

        val overallWoodUtilization = if (totalRawWoodArea > 0f) (totalUsedWoodArea / totalRawWoodArea) * 100f else 0f
        val overallWoodWaste = (100f - overallWoodUtilization).coerceAtLeast(0f)

        val totalEdgeTapeMeters = allCutParts.sumOf { it.edgeBandingMeters.toDouble() }.toFloat()

        val aggregateHardware = mutableMapOf<String, Int>()
        cabinets.forEach { cab ->
            val hw = cab.calculateHardwareRequirements()
            hw.forEach { (item, qty) ->
                aggregateHardware[item] = (aggregateHardware[item] ?: 0) + qty
            }
        }

        val totalAluBars = max(packedAluminum.size, aluSummary.requiredStandardBarsCount)
        val woodCost = (mdf18Count * 45.0f) + (mdf8Count * 25.0f)
        val aluminumCost = totalAluBars * 28.0f
        val edgeBandingCost = totalEdgeTapeMeters * 0.65f
        val hardwareCost = (aggregateHardware.values.sum() * 3.5f)
        val totalCost = woodCost + aluminumCost + edgeBandingCost + hardwareCost

        return ManufacturingBOMReport(
            totalCabinetsCount = cabinets.size,
            packedWoodSheets = packedSheets,
            packedAluminumBars = packedAluminum,
            aluminumSummary = aluSummary,
            melamineSidesParts = allCutParts.filter { it.category == CutPartCategory.MELAMINE_SIDE },
            melamineBottomsAndShelvesParts = allCutParts.filter { it.category == CutPartCategory.MELAMINE_BOTTOM_AND_SHELF },
            melamineStretchersParts = allCutParts.filter { it.category == CutPartCategory.MELAMINE_TOP_STRETCHER },
            backPanelParts = allCutParts.filter { it.category == CutPartCategory.CABINET_BACK_PANEL },
            totalRawSheetsCount = packedSheets.size,
            totalMdf18Sheets = mdf18Count,
            totalMdf8Sheets = mdf8Count,
            totalAluminumBarsCount = totalAluBars,
            overallWoodUtilizationPct = overallWoodUtilization,
            overallWoodWastePct = overallWoodWaste,
            totalEdgeBandingMeters = totalEdgeTapeMeters,
            hardwareList = aggregateHardware,
            estimatedWoodCost = woodCost,
            estimatedAluminumCost = aluminumCost,
            estimatedEdgeBandingCost = edgeBandingCost,
            estimatedHardwareCost = hardwareCost,
            estimatedTotalCost = totalCost
        )
    }
}
