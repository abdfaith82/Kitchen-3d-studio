package com.example.data.remote

import android.os.Looper
import com.example.base.FirestoreEmulatorTestBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.robolectric.Shadows

class FirestoreKitchenRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private lateinit var repository: FirestoreKitchenRepository

    @Before
    fun setUpRepository() {
        repository = FirestoreKitchenRepository(db)
    }

    private fun <T> runWithMainLooperIdle(block: suspend CoroutineScope.() -> T): T {
        val deferred = CoroutineScope(Dispatchers.IO).async(block = block)
        val shadowLooper = Shadows.shadowOf(Looper.getMainLooper())
        while (!deferred.isCompleted) {
            shadowLooper.idle()
            Thread.sleep(15)
        }
        shadowLooper.idle()
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        return deferred.getCompleted()
    }

    @Test
    fun createAndReadKitchenDesign_whenAuthenticatedAsOwner_succeeds() = runWithMainLooperIdle {
        val uid = signInTestUser("owner@kitchen3d.com")
        val design = FirestoreKitchenDesign(
            userId = uid,
            title = "مطبخ البلوط الأوروبي والرخام",
            roomWidthCm = 420.0,
            roomDepthCm = 360.0,
            roomHeightCm = 280.0,
            elementCount = 7,
            totalCostUsd = 5240.0,
            materialsCostUsd = 3500.0,
            laborCostUsd = 1040.0,
            accessoriesCostUsd = 700.0,
            primaryMaterialId = "NATURAL_OAK",
            countertopMaterialId = "CARRARA_MARBLE",
            projectJson = "{\"name\":\"مطبخ البلوط الأوروبي والرخام\"}"
        )

        repository.createKitchenDesign(uid, "design_oak_1", design)
        val fetched = repository.getKitchenDesign(uid, "design_oak_1")
        assertNotNull(fetched)
        assertEquals("design_oak_1", fetched?.id)
        assertEquals("مطبخ البلوط الأوروبي والرخام", fetched?.title)
        assertEquals(5240.0, fetched?.totalCostUsd ?: 0.0, 0.01)

        // Update allowed fields
        repository.updateKitchenDesign(
            uid,
            "design_oak_1",
            mapOf(
                "title" to "مطبخ البلوط الأوروبي المطور",
                "totalCostUsd" to 5600.0
            )
        )
        val updated = repository.getKitchenDesign(uid, "design_oak_1")
        assertEquals("مطبخ البلوط الأوروبي المطور", updated?.title)
        assertEquals(5600.0, updated?.totalCostUsd ?: 0.0, 0.01)

        // Delete
        repository.deleteKitchenDesign(uid, "design_oak_1")
        val afterDelete = repository.getKitchenDesign(uid, "design_oak_1")
        assertNull(afterDelete)
    }

    @Test
    fun createAndReadKitchenComponent_whenAuthenticatedAsOwner_succeeds() = runWithMainLooperIdle {
        val uid = signInTestUser("designer@kitchen3d.com")
        val component = FirestoreKitchenComponent(
            userId = uid,
            designId = "design_oak_1",
            nameAr = "وحدة أدراج ذكية 80 سم",
            category = "DRAWERS",
            typeName = "DRAWER_UNIT_3",
            widthCm = 80.0,
            depthCm = 60.0,
            heightCm = 85.0,
            elevationCm = 0.0,
            posXCm = 140.0,
            posYCm = 0.0,
            rotationDeg = 0.0,
            materialId = "NATURAL_OAK",
            unitPriceUsd = 360.0
        )

        repository.createKitchenComponent(uid, "comp_drawer_80", component)
        val loaded = repository.getKitchenComponent(uid, "comp_drawer_80")
        assertNotNull(loaded)
        assertEquals("comp_drawer_80", loaded?.id)
        assertEquals("وحدة أدراج ذكية 80 سم", loaded?.nameAr)
        assertEquals(360.0, loaded?.unitPriceUsd ?: 0.0, 0.01)
    }

    @Test
    fun createKitchenDesign_whenUnauthenticatedOrWrongOwner_throwsPermissionDenied() = runWithMainLooperIdle {
        auth.signOut()
        val design = FirestoreKitchenDesign(
            userId = "unauthed_user",
            title = "مطبخ غير مصرح",
            projectJson = "{}"
        )
        try {
            repository.createKitchenDesign("unauthed_user", "design_denied", design)
            fail("Expected PERMISSION_DENIED for unauthenticated user")
        } catch (e: RuntimeException) {
            assertTrue(e.message?.contains("PERMISSION_DENIED") == true)
        }

        // Now sign in as user A and try writing to user B's collection
        val uidA = signInTestUser("userA@kitchen3d.com")
        try {
            repository.createKitchenDesign("userB_uid", "design_cross_user", design.copy(userId = uidA))
            fail("Expected PERMISSION_DENIED for cross-user write")
        } catch (e: RuntimeException) {
            assertTrue(e.message?.contains("PERMISSION_DENIED") == true)
        }
    }
}
