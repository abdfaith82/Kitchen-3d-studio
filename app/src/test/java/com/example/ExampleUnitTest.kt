package com.example

import com.example.core.geometry.CollisionDetector
import com.example.core.geometry.OrientedBoundingBox
import com.example.core.geometry.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ExampleUnitTest {

    @Test
    fun vector3_mathOperations_areAccurate() {
        val v1 = Vector3(1f, 2f, 3f)
        val v2 = Vector3(4f, 5f, 6f)

        val sum = v1 + v2
        assertEquals(5f, sum.x, 0.0001f)
        assertEquals(7f, sum.y, 0.0001f)
        assertEquals(9f, sum.z, 0.0001f)

        val dot = v1.dot(v2)
        assertEquals(32f, dot, 0.0001f)

        val cross = v1.cross(v2)
        assertEquals(-3f, cross.x, 0.0001f)
        assertEquals(6f, cross.y, 0.0001f)
        assertEquals(-3f, cross.z, 0.0001f)
    }

    @Test
    fun obbCollision_detectsRealCollisionsAndClearance() {
        val boxA = OrientedBoundingBox(
            center = Vector3(0f, 0.5f, 0f),
            halfExtents = Vector3(0.30f, 0.40f, 0.30f),
            rotationYDegrees = 0f
        )

        // Box B overlaps box A
        val boxB = OrientedBoundingBox(
            center = Vector3(0.40f, 0.5f, 0f),
            halfExtents = Vector3(0.30f, 0.40f, 0.30f),
            rotationYDegrees = 0f
        )
        val resultOverlap = CollisionDetector.testOBBOBB(boxA, boxB)
        assertTrue("يجب أن يكشف النظام عن التداخل", resultOverlap.hasCollision)
        assertTrue(resultOverlap.overlapDepth > 0.10f)

        // Box C is completely separated with clear margin
        val boxC = OrientedBoundingBox(
            center = Vector3(2.0f, 0.5f, 0f),
            halfExtents = Vector3(0.30f, 0.40f, 0.30f),
            rotationYDegrees = 0f
        )
        val resultSeparated = CollisionDetector.testOBBOBB(boxA, boxC)
        assertFalse("يجب ألا يكون هناك تصادم مع مسافة 2 متر", resultSeparated.hasCollision)
    }
}
