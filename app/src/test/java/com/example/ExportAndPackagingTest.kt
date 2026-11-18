package com.example

import com.example.core.command.CabinetEntityModel
import com.example.core.geometry.Vector3
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportAndPackagingTest {

    @Test
    fun cabinetEntity_transformsAndOBBAreConsistent() {
        val entity = CabinetEntityModel(
            id = "cab_base_sink_90",
            name = "وحدة حوض 90سم",
            modelAssetPath = "models/cabinets/base_sink_90.glb",
            position = Vector3(1.20f, 0.10f, 0.60f),
            rotationY = 90f,
            width = 0.90f,
            height = 0.82f,
            depth = 0.60f,
            materialId = "granite_black_galaxy"
        )

        val obb = entity.toOBB()
        assertEqualsFloat(1.20f, obb.center.x)
        assertEqualsFloat(0.10f + 0.41f, obb.center.y)
        assertEqualsFloat(0.60f, obb.center.z)
        assertEqualsFloat(90f, obb.rotationYDegrees)
        assertEqualsFloat(0.45f, obb.halfExtents.x)
        assertEqualsFloat(0.41f, obb.halfExtents.y)
        assertEqualsFloat(0.30f, obb.halfExtents.z)

        val copy = entity.copyEntity()
        assertNotNull(copy)
        assertTrue(copy.id == entity.id)
    }

    private fun assertEqualsFloat(expected: Float, actual: Float) {
        org.junit.Assert.assertEquals(expected, actual, 0.001f)
    }
}
