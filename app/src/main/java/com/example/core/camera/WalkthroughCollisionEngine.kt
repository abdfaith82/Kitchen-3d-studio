package com.example.core.camera

import com.example.core.math.AABB
import com.example.core.math.MathUtils
import com.example.core.math.Vector3
import com.example.domain.model.KitchenElement
import com.example.domain.model.RoomStructure
import kotlin.math.*

/**
 * Result of collision resolution during walkthrough movement.
 */
data class WalkCollisionResult(
    val resolvedPosition: Vector3,
    val hasCollided: Boolean,
    val collidedObstacleName: String? = null,
    val penetrationDepthMm: Float = 0f,
    val slideNormal: Vector3 = Vector3.ZERO
)

/**
 * Robust 3D Walkthrough Collision Engine with sliding response.
 * Prevents camera from penetrating kitchen walls, cabinets, tall units, and islands.
 * Maintains fixed eye height (1600mm / 160cm) and models camera as a collision cylinder.
 */
object WalkthroughCollisionEngine {

    const val DEFAULT_EYE_HEIGHT_MM = 1600f // 160 cm
    const val DEFAULT_BODY_RADIUS_MM = 280f  // 28 cm body clearance
    const val MIN_WALL_MARGIN_MM = 50f

    /**
     * Resolves movement from currentPos to candidatePos against room walls and cabinets.
     * Uses iterative sliding collision response so the player slides smoothly along surfaces.
     */
    fun resolveMovement(
        currentPos: Vector3,
        candidatePos: Vector3,
        room: RoomStructure,
        elements: List<KitchenElement>,
        bodyRadiusMm: Float = DEFAULT_BODY_RADIUS_MM,
        eyeHeightMm: Float = DEFAULT_EYE_HEIGHT_MM,
        maxIterations: Int = 3
    ): WalkCollisionResult {
        // Enforce fixed eye-level height
        var resolved = Vector3(candidatePos.x, eyeHeightMm, candidatePos.z)
        var hasCollided = false
        var obstacleName: String? = null
        var maxPenetration = 0f
        var lastNormal = Vector3.ZERO

        // Obstacles to check: cabinets and elements that intersect human height (0mm to 1900mm)
        val solidObstacles = elements.filter { elem ->
            elem.isVisible && (elem.position.y < 1900f && (elem.position.y + elem.dimensions.y) > 100f)
        }

        for (iter in 0 until maxIterations) {
            var iterationCollided = false

            // 1. Room Boundary Walls Collision (X: [0, room.widthMm], Z: [0, room.depthMm])
            val minX = bodyRadiusMm + MIN_WALL_MARGIN_MM
            val maxX = max(minX, room.widthMm - bodyRadiusMm - MIN_WALL_MARGIN_MM)
            val minZ = bodyRadiusMm + MIN_WALL_MARGIN_MM
            val maxZ = max(minZ, room.depthMm - bodyRadiusMm - MIN_WALL_MARGIN_MM)

            if (resolved.x < minX) {
                val pen = minX - resolved.x
                resolved = resolved.copy(x = minX)
                hasCollided = true
                iterationCollided = true
                obstacleName = "الجدار الغربي (West Wall)"
                maxPenetration = max(maxPenetration, pen)
                lastNormal = Vector3.UNIT_X
            } else if (resolved.x > maxX) {
                val pen = resolved.x - maxX
                resolved = resolved.copy(x = maxX)
                hasCollided = true
                iterationCollided = true
                obstacleName = "الجدار الشرقي (East Wall)"
                maxPenetration = max(maxPenetration, pen)
                lastNormal = -Vector3.UNIT_X
            }

            if (resolved.z < minZ) {
                val pen = minZ - resolved.z
                resolved = resolved.copy(z = minZ)
                hasCollided = true
                iterationCollided = true
                obstacleName = "الجدار الشمالي (North Wall)"
                maxPenetration = max(maxPenetration, pen)
                lastNormal = Vector3.UNIT_Z
            } else if (resolved.z > maxZ) {
                val pen = resolved.z - maxZ
                resolved = resolved.copy(z = maxZ)
                hasCollided = true
                iterationCollided = true
                obstacleName = "الجدار الجنوبي (South Wall)"
                maxPenetration = max(maxPenetration, pen)
                lastNormal = -Vector3.UNIT_Z
            }

            // 2. Solid Kitchen Elements Collision (Cabinets, Countertops, Appliances)
            for (elem in solidObstacles) {
                val collision = testAndResolveObstacle(resolved, elem, bodyRadiusMm)
                if (collision != null) {
                    resolved = collision.first
                    hasCollided = true
                    iterationCollided = true
                    obstacleName = elem.name
                    maxPenetration = max(maxPenetration, collision.second)
                    lastNormal = collision.third
                }
            }

            if (!iterationCollided) break
        }

        return WalkCollisionResult(
            resolvedPosition = resolved,
            hasCollided = hasCollided,
            collidedObstacleName = obstacleName,
            penetrationDepthMm = maxPenetration,
            slideNormal = lastNormal
        )
    }

    /**
     * Tests and resolves collision between the camera cylinder (position & bodyRadius)
     * and a kitchen element. Applies sliding projection if penetrated.
     */
    private fun testAndResolveObstacle(
        cameraPos: Vector3,
        elem: KitchenElement,
        bodyRadius: Float
    ): Triple<Vector3, Float, Vector3>? {
        val halfW = elem.dimensions.x * 0.5f
        val halfD = elem.dimensions.z * 0.5f
        val centerX = elem.position.x + halfW
        val centerZ = elem.position.z + halfD
        val rotRad = Math.toRadians(-elem.rotationY.toDouble())
        val cosA = cos(rotRad).toFloat()
        val sinA = sin(rotRad).toFloat()

        // Transform camera position into element's local coordinate space
        val dx = cameraPos.x - centerX
        val dz = cameraPos.z - centerZ
        val localX = dx * cosA - dz * sinA
        val localZ = dx * sinA + dz * cosA

        // Closest point on the unrotated 2D rectangle in local space
        val clampedX = MathUtils.clamp(localX, -halfW, halfW)
        val clampedZ = MathUtils.clamp(localZ, -halfD, halfD)

        val distX = localX - clampedX
        val distZ = localZ - clampedZ
        val distSq = distX * distX + distZ * distZ

        // Check if camera center is within bodyRadius of the cabinet
        if (distSq < bodyRadius * bodyRadius) {
            val dist = sqrt(distSq)
            val penetration = bodyRadius - dist

            // Local normal vector pointing away from cabinet
            val (localNormX, localNormZ) = if (dist > 1e-4f) {
                Pair(distX / dist, distZ / dist)
            } else {
                // If camera center is completely inside cabinet, push along closest edge
                val overlapX = halfW - abs(localX)
                val overlapZ = halfD - abs(localZ)
                if (overlapX < overlapZ) {
                    Pair(if (localX >= 0) 1f else -1f, 0f)
                } else {
                    Pair(0f, if (localZ >= 0) 1f else -1f)
                }
            }

            // Move camera point outside cabinet boundary
            val correctedLocalX = clampedX + localNormX * (bodyRadius + 2f)
            val correctedLocalZ = clampedZ + localNormZ * (bodyRadius + 2f)

            // Transform back to world space
            val worldCos = cos(-rotRad).toFloat()
            val worldSin = sin(-rotRad).toFloat()
            val correctedWorldX = centerX + (correctedLocalX * worldCos - correctedLocalZ * worldSin)
            val correctedWorldZ = centerZ + (correctedLocalX * worldSin + correctedLocalZ * worldCos)

            val worldNormX = localNormX * worldCos - localNormZ * worldSin
            val worldNormZ = localNormX * worldSin + localNormZ * worldCos
            val worldNormal = Vector3(worldNormX, 0f, worldNormZ).normalized()

            return Triple(
                Vector3(correctedWorldX, cameraPos.y, correctedWorldZ),
                penetration,
                worldNormal
            )
        }

        return null
    }

    /**
     * Computes a safe initial walkthrough spawn position in the center of the kitchen room floor.
     */
    fun getSafeWalkSpawnPosition(room: RoomStructure, elements: List<KitchenElement>): Vector3 {
        val centerX = room.widthMm * 0.5f
        val centerZ = room.depthMm * 0.5f
        val initialCandidate = Vector3(centerX, DEFAULT_EYE_HEIGHT_MM, centerZ)
        return resolveMovement(initialCandidate, initialCandidate, room, elements).resolvedPosition
    }
}
