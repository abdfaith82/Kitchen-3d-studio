package com.example

import com.example.core.geometry.ElevationAndSafetyEngine
import com.example.core.geometry.ErgonomicValidationEngine
import com.example.core.geometry.KitchenWorkTriangle
import com.example.core.geometry.Vector3
import com.example.core.texture.PbrMaterialProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealisticPbrAndErgonomicTest {

    @Test
    fun pbrProfileSettings_matchPhotorealisticPhysicalAttributes() {
        val granite = PbrMaterialProcessor.PresetProfiles.blackGalaxyGranite
        assertEquals(0.12f, granite.roughnessBase, 0.001f)
        assertEquals(0.85f, granite.metallicSpecksMask, 0.001f)
        assertEquals(0.95f, granite.clearCoatStrength, 0.001f)
        assertTrue("الجرانيت يتطلب إزالة انعكاسات النوافذ المسبقة", granite.removePreBakedHighlights)

        val marble = PbrMaterialProcessor.PresetProfiles.carraraWhiteMarble
        assertEquals(0.18f, marble.roughnessBase, 0.001f)
        assertEquals(0.80f, marble.clearCoatStrength, 0.001f)
        assertFalse(marble.removePreBakedHighlights)

        val oakWood = PbrMaterialProcessor.PresetProfiles.naturalOakWood
        assertEquals(0.55f, oakWood.roughnessBase, 0.001f)
        assertEquals(0.0f, oakWood.clearCoatStrength, 0.001f)
        assertTrue("خشب البلوط يتطلب إزالة التعتيم الجانبي", oakWood.removeVignette)
        assertTrue(oakWood.makeSeamlessHorizontal)
    }

    @Test
    fun kitchenWorkTriangle_validatesGoldenErgonomics() {
        // مثلث عمل مثالي: حوض (0,0), موقد (2, 0), ثلاجة (1, 2)
        val optimalTriangle = KitchenWorkTriangle(
            sinkCenter = Vector3(0f, 0f, 0f),
            cooktopCenter = Vector3(2.0f, 0f, 0f),
            fridgeCenter = Vector3(1.0f, 0f, 2.0f)
        )

        val diagnostic = ErgonomicValidationEngine.validateWorkTriangle(optimalTriangle)
        assertNull("المثلث المتوازن تماماً يجب ألا يُصدر تحذيرات أو أخطاء", diagnostic)

        // مثلث بعيد جداً ومخالف لمعايير الراحة (> 8.0 متر)
        val excessiveTriangle = KitchenWorkTriangle(
            sinkCenter = Vector3(0f, 0f, 0f),
            cooktopCenter = Vector3(4.5f, 0f, 0f),
            fridgeCenter = Vector3(2.0f, 0f, 5.0f)
        )
        val warningDiagnostic = ErgonomicValidationEngine.validateWorkTriangle(excessiveTriangle)
        assertNotNull(warningDiagnostic)
        assertEquals("ERGONOMIC_WORK_TRIANGLE_EXCESSIVE", warningDiagnostic?.code)
    }

    @Test
    fun rangeHoodSafety_alertsOnFireHazardDistance() {
        val cooktopY = 0.90f
        val dangerousHoodY = 1.35f // 45 سم فقط - خطر حريق
        val safeHoodY = 1.70f      // 80 سم - آمن تماماً

        val dangerResult = ElevationAndSafetyEngine.checkHoodSafetyDistance(
            cooktopY = cooktopY,
            hoodBottomY = dangerousHoodY,
            isGasBurner = true
        )
        assertNotNull("يجب إطلاق تنبيه سلامة عند تدني ارتفاع الشفاط", dangerResult)
        assertEquals("SAFETY_HOOD_FIRE_HAZARD", dangerResult?.code)

        val safeResult = ElevationAndSafetyEngine.checkHoodSafetyDistance(
            cooktopY = cooktopY,
            hoodBottomY = safeHoodY,
            isGasBurner = true
        )
        assertNull("المسافة الآمنة لا تنتج أي أخطار", safeResult)
    }
}
