package com.example

import com.example.core.manufacturing.AluminumBarNestingOptimizer
import com.example.core.manufacturing.CutPiece
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AluminumBarNestingOptimizerTest {

    @Test
    fun linearNesting_respectsStandardBarLengthAndSawKerf() {
        val stockLength = 6000.0
        val kerf = 4.0

        val cuts = listOf(
            CutPiece("C1", 2400.0, "Cabinet Door Rail Left"),
            CutPiece("C2", 2400.0, "Cabinet Door Rail Right"),
            CutPiece("C3", 1100.0, "Top Gola Profile"),
            CutPiece("C4", 1500.0, "Island Trim"),
            CutPiece("C5", 1500.0, "Island Base Trim")
        )

        val result = AluminumBarNestingOptimizer.optimizeLinearCuts(
            requestedCuts = cuts,
            stockBarLengthMm = stockLength,
            sawKerfMm = kerf
        )

        assertEquals("عدد القطع الإجمالي يجب أن يطابق المدخلات", 5, result.totalCutsCount)
        assertTrue("يجب ألا يتجاوز أي لوح الطول القياسي", result.bars.all { it.remainingLengthMm >= 0.0 })
        assertTrue("كفاءة التقطيع يجب أن تتجاوز 70%", result.overallEfficiencyPercentage > 70.0)

        for (bar in result.bars) {
            val totalPieceLength = bar.cuts.sumOf { it.lengthMm }
            val kerfSum = if (bar.cuts.size > 1) (bar.cuts.size - 1) * kerf else 0.0
            val calculatedRemaining = stockLength - (totalPieceLength + kerfSum)
            assertEquals(calculatedRemaining, bar.remainingLengthMm, 0.001)
        }
    }
}
