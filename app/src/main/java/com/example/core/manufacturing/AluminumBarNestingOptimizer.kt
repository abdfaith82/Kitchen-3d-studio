package com.example.core.manufacturing

data class CutPiece(
    val id: String,
    val lengthMm: Double,
    val label: String,
    val angleLeftDeg: Double = 90.0,
    val angleRightDeg: Double = 90.0
)

data class NestingStockBar(
    val barIndex: Int,
    val totalStockLengthMm: Double,
    val cuts: MutableList<CutPiece> = mutableListOf(),
    var remainingLengthMm: Double = totalStockLengthMm
) {
    val usedLengthMm: Double
        get() = totalStockLengthMm - remainingLengthMm

    val wastePercentage: Double
        get() = if (totalStockLengthMm > 0.0) (remainingLengthMm / totalStockLengthMm) * 100.0 else 0.0
}

data class NestingResult(
    val standardBarLengthMm: Double,
    val kerfLossMm: Double,
    val bars: List<NestingStockBar>,
    val totalStockBarsUsed: Int,
    val totalCutsCount: Int,
    val totalMaterialLengthUsedMm: Double,
    val totalScrapWasteMm: Double,
    val overallEfficiencyPercentage: Double
)

object AluminumBarNestingOptimizer {

    const val DEFAULT_STOCK_BAR_LENGTH_MM = 6000.0
    const val DEFAULT_SAW_KERF_MM = 4.0

    fun optimizeLinearCuts(
        requestedCuts: List<CutPiece>,
        stockBarLengthMm: Double = DEFAULT_STOCK_BAR_LENGTH_MM,
        sawKerfMm: Double = DEFAULT_SAW_KERF_MM
    ): NestingResult {
        if (requestedCuts.isEmpty()) {
            return NestingResult(
                standardBarLengthMm = stockBarLengthMm,
                kerfLossMm = sawKerfMm,
                bars = emptyList(),
                totalStockBarsUsed = 0,
                totalCutsCount = 0,
                totalMaterialLengthUsedMm = 0.0,
                totalScrapWasteMm = 0.0,
                overallEfficiencyPercentage = 100.0
            )
        }

        // Sort descending by length (Best Fit Decreasing / First Fit Decreasing algorithm)
        val sortedCuts = requestedCuts.sortedByDescending { it.lengthMm }
        val bars = mutableListOf<NestingStockBar>()

        for (piece in sortedCuts) {
            var placed = false
            var bestBar: NestingStockBar? = null
            var minRemainingAfterFit = Double.MAX_VALUE

            for (bar in bars) {
                val neededSpace = piece.lengthMm + (if (bar.cuts.isNotEmpty()) sawKerfMm else 0.0)
                if (bar.remainingLengthMm >= neededSpace) {
                    val potentialRemaining = bar.remainingLengthMm - neededSpace
                    if (potentialRemaining < minRemainingAfterFit) {
                        minRemainingAfterFit = potentialRemaining
                        bestBar = bar
                    }
                }
            }

            if (bestBar != null) {
                val neededSpace = piece.lengthMm + (if (bestBar.cuts.isNotEmpty()) sawKerfMm else 0.0)
                bestBar.cuts.add(piece)
                bestBar.remainingLengthMm -= neededSpace
                placed = true
            }

            if (!placed) {
                val newBar = NestingStockBar(
                    barIndex = bars.size + 1,
                    totalStockLengthMm = stockBarLengthMm,
                    remainingLengthMm = stockBarLengthMm - piece.lengthMm
                )
                newBar.cuts.add(piece)
                bars.add(newBar)
            }
        }

        val totalStockBars = bars.size
        val totalPurchasedLength = totalStockBars * stockBarLengthMm
        val totalNetCutLength = requestedCuts.sumOf { it.lengthMm }
        val totalScrap = totalPurchasedLength - totalNetCutLength
        val efficiency = if (totalPurchasedLength > 0.0) (totalNetCutLength / totalPurchasedLength) * 100.0 else 0.0

        return NestingResult(
            standardBarLengthMm = stockBarLengthMm,
            kerfLossMm = sawKerfMm,
            bars = bars,
            totalStockBarsUsed = totalStockBars,
            totalCutsCount = requestedCuts.size,
            totalMaterialLengthUsedMm = totalPurchasedLength,
            totalScrapWasteMm = totalScrap,
            overallEfficiencyPercentage = efficiency
        )
    }
}
