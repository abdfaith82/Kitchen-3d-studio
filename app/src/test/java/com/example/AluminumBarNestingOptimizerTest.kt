package com.example

import com.example.core.cam.*
import org.junit.Assert.*
import org.junit.Test

class AluminumBarNestingOptimizerTest {

    @Test
    fun `best fit decreasing packs individual cut pieces optimally into standard aluminum bar`() {
        val cuts = listOf(
            CabinetryAluminumCutInput(
                id = "c1",
                profileName = "قائم عمودي 2100",
                cabinetName = "برج فرن #1",
                lengthMm = 2100f,
                quantity = 2,
                profileTypeCode = "ALU_POST_V",
                orientation = AluminumBarOrientation.VERTICAL
            ),
            CabinetryAluminumCutInput(
                id = "c2",
                profileName = "عارضة أفقية 1700",
                cabinetName = "خزانة علوية #2",
                lengthMm = 1700f,
                quantity = 2,
                profileTypeCode = "ALU_CROSS_H",
                orientation = AluminumBarOrientation.HORIZONTAL
            )
        )

        val config = AluminumNestingConfig(
            primaryBarLengthMm = 6000f,
            sawKerfMm = 4.0f,
            endTrimMm = 10.0f,
            strategy = AluminumNestingStrategy.BEST_FIT_DECREASING,
            groupByProfileType = true
        )

        val result = AluminumBarNestingOptimizer.optimizeCutList(cuts, config)

        // 2x 2100 + 1x 1700 = 5900mm (+ kerfs + 10mm trim = 5922mm <= 6000mm) on Bar #1
        // Remaining 1x 1700mm on Bar #2
        assertEquals(2, result.totalBarsCount)
        assertEquals(4, result.totalIndividualCutPiecesCount)
        assertEquals(7600f, result.totalNetCutLengthMm, 0.01f)
        assertEquals(12000f, result.totalStockLengthMm, 0.01f)

        val firstBar = result.bars[0]
        assertEquals(3, firstBar.cutPieces.size)
        assertEquals(5900f, firstBar.netCutLengthMm, 0.01f)
        assertTrue(firstBar.utilizationPercentage > 98.0f)
        assertTrue(firstBar.wastePercentage < 2.0f)

        // Verify exact start/end coordinates on the bar
        assertEquals(10f, firstBar.cutPieces[0].startOffsetMm, 0.01f)
        assertEquals(2110f, firstBar.cutPieces[0].endOffsetMm, 0.01f)
        assertEquals(2114f, firstBar.cutPieces[1].startOffsetMm, 0.01f)
        assertEquals(4214f, firstBar.cutPieces[1].endOffsetMm, 0.01f)
    }

    @Test
    fun `multi stock optimizer chooses optimal standard bar length to minimize waste percentage`() {
        val cuts = listOf(
            CabinetryAluminumCutInput(
                id = "gola_1",
                profileName = "مقبض غولا L",
                cabinetName = "وحدة سفلية",
                lengthMm = 1400f,
                quantity = 3,
                profileTypeCode = "GOLA_L",
                orientation = AluminumBarOrientation.HORIZONTAL
            )
        )
        // 3 * 1400 = 4200mm. Between 4500mm and 6000mm standard bars, 4500mm yields much lower waste!
        val config = AluminumNestingConfig(
            primaryBarLengthMm = 6000f,
            availableStandardBarLengthsMm = listOf(6000f, 5800f, 4500f),
            sawKerfMm = 4.0f,
            endTrimMm = 10.0f,
            strategy = AluminumNestingStrategy.MULTI_STOCK_OPTIMAL,
            groupByProfileType = true
        )

        val result = AluminumBarNestingOptimizer.optimizeCutList(cuts, config)
        assertEquals(1, result.totalBarsCount)
        assertEquals(4500f, result.bars.first().stockLengthMm, 0.01f)
        assertEquals(3, result.bars.first().cutPieces.size)
        assertTrue(result.overallUtilizationPercentage > 93.0f)
        assertTrue(result.overallWastePercentage < 7.0f)
    }

    @Test
    fun `extracts cabinetry cuts from ParametricCabinet and generates workshop report`() {
        val cabinets = listOf(
            ParametricCabinet(
                id = "cab_1",
                name = "خزانة سفلية 80 سم",
                widthMm = 800f,
                heightMm = 850f,
                depthMm = 600f,
                doorStyle = DoorStyle.ALUMINUM_GLASS,
                handleStyle = HandleStyle.J_PULL_GOLA,
                includeStructuralAluminumFrame = true
            )
        )

        val extracted = AluminumBarNestingOptimizer.extractCabinetryCutInputs(cabinets)
        assertTrue(extracted.isNotEmpty())
        assertTrue(extracted.all { it.cabinetName == "خزانة سفلية 80 سم" })

        val result = AluminumBarNestingOptimizer.optimizeCutList(
            cutInputs = extracted,
            config = AluminumNestingConfig(primaryBarLengthMm = 5800f)
        )
        assertTrue(result.totalBarsCount >= 1)
        assertTrue(result.totalIndividualCutPiecesCount >= 12)

        val reportText = AluminumBarNestingOptimizer.generateWorkshopCutListReport(result)
        assertTrue(reportText.contains("5800 مم"))
        assertTrue(reportText.contains("نسبة الهدر الكلية"))
        assertTrue(reportText.contains("قطعة #1"))
    }
}
