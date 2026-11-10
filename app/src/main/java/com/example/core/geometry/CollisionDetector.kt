package com.example.core.geometry

import com.example.core.math.Vector3
import com.example.domain.model.ElementCategory
import com.example.domain.model.KitchenElement
import com.example.domain.model.KitchenElementType
import com.example.domain.model.RoomStructure
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class CollisionResult(
    val hasCollision: Boolean,
    val collidingElementIds: List<String> = emptyList(),
    val isOutOfBounds: Boolean = false,
    val isBlockedByWindowSafetyZone: Boolean = false,
    val isErgonomicClearanceViolation: Boolean = false,
    val isPhysicalOverlap: Boolean = false,
    val wasOverlapPrevented: Boolean = false,
    val coveredUtilityCount: Int = 0,
    val message: String = ""
)

data class NonOverlapResolution(
    val resolvedElement: KitchenElement,
    val wasOverlapPrevented: Boolean,
    val blockedByElementIds: List<String> = emptyList(),
    val messageAr: String? = null
)

/**
 * High-speed 3D/2D collision detection and anti-overlap resolution engine using Separating Axis Theorem (SAT),
 * Architectural Window & 60cm Backsplash Utility Safety Zones, and Ergonomic Clearance rules.
 * Strictly prevents overlapping cabinets by resolving movements to flush 0mm contact boundaries or nearest free slots.
 */
object CollisionDetector {

    private val COUNTERTOP_INSERT_TYPES = setOf(
        KitchenElementType.COOKTOP_INDUCTION,
        KitchenElementType.COOKTOP_GAS_90
    )

    private val CEILING_FIXTURE_TYPES = setOf(
        KitchenElementType.CEILING_CHANDELIER_MODERN,
        KitchenElementType.CEILING_LINEAR_LED_PANEL
    )

    /**
     * Returns true when one element is a countertop cooktop insert resting on top of a base/island cabinet.
     */
    fun isCountertopInsertPair(a: KitchenElement, b: KitchenElement): Boolean {
        val aInsert = a.type in COUNTERTOP_INSERT_TYPES
        val bInsert = b.type in COUNTERTOP_INSERT_TYPES
        if (!aInsert && !bInsert) return false
        val insert = if (aInsert) a else b
        val base = if (aInsert) b else a
        val baseTopY = base.position.y + base.dimensions.y
        return abs(insert.position.y - baseTopY) <= 85f
    }

    /**
     * Returns true when one element is a ceiling light/chandelier and the other is a cabinet/appliance.
     * Ceiling fixtures hang in the center of the room ceiling and should only collide with other ceiling fixtures.
     */
    fun isNonCollidingCeilingPair(a: KitchenElement, b: KitchenElement): Boolean {
        val aCeiling = a.type in CEILING_FIXTURE_TYPES
        val bCeiling = b.type in CEILING_FIXTURE_TYPES
        return aCeiling xor bCeiling
    }

    /**
     * Computes effective X/Z footprint extents taking Y rotation (90°/270°) into account.
     */
    fun getEffectiveFootprint(element: KitchenElement): Pair<Float, Float> {
        val normRot = ((element.rotationY % 360f) + 360f) % 360f
        val isRot90or270 = abs(normRot - 90f) < 15f || abs(normRot - 270f) < 15f
        val effW = if (isRot90or270) element.dimensions.z else element.dimensions.x
        val effD = if (isRot90or270) element.dimensions.x else element.dimensions.z
        return effW to effD
    }

    /**
     * Clamps an element's position so its footprint stays strictly inside the room walls and ceiling.
     */
    fun clampInsideRoomBounds(element: KitchenElement, room: RoomStructure): KitchenElement {
        val (effW, effD) = getEffectiveFootprint(element)
        val halfW = effW * 0.5f
        val halfD = effD * 0.5f
        val maxX = (room.widthMm - halfW).coerceAtLeast(halfW)
        val maxZ = (room.depthMm - halfD).coerceAtLeast(halfD)
        val maxY = (room.heightMm - element.dimensions.y).coerceAtLeast(0f)

        val clampedPos = Vector3(
            x = element.position.x.coerceIn(halfW, maxX),
            y = element.position.y.coerceIn(0f, maxY),
            z = element.position.z.coerceIn(halfD, maxZ)
        ).roundTo1mm()

        return if (clampedPos == element.position) element else element.copy(position = clampedPos)
    }

    /**
     * Fast check for physical cabinet-to-cabinet overlap, out-of-bounds, or window safety zone breach.
     */
    fun getPhysicalCollidingIds(
        candidate: KitchenElement,
        existingElements: List<KitchenElement>,
        toleranceMm: Float = 1.5f
    ): List<String> {
        val candidateOBB = candidate.getOBB()
        val aabb = candidateOBB.toAABB()
        val collidingIds = mutableListOf<String>()

        for (other in existingElements) {
            if (other.id == candidate.id || !other.isVisible) continue
            if (isCountertopInsertPair(candidate, other)) continue
            if (isNonCollidingCeilingPair(candidate, other)) continue

            val otherAABB = other.getAABB()
            if (!aabb.intersects(otherAABB)) continue

            val otherOBB = other.getOBB()
            if (candidateOBB.intersects(otherOBB, marginMm = toleranceMm)) {
                collidingIds.add(other.id)
            }
        }
        return collidingIds
    }

    fun hasPhysicalOverlap(
        candidate: KitchenElement,
        existingElements: List<KitchenElement>,
        room: RoomStructure,
        toleranceMm: Float = 1.0f
    ): Boolean {
        val candidateOBB = candidate.getOBB()
        val aabb = candidateOBB.toAABB()

        val isOutOfBounds = aabb.min.x < -toleranceMm ||
            aabb.max.x > room.widthMm + toleranceMm ||
            aabb.min.z < -toleranceMm ||
            aabb.max.z > room.depthMm + toleranceMm ||
            aabb.min.y < -toleranceMm ||
            aabb.max.y > room.heightMm + toleranceMm
        if (isOutOfBounds) return true

        val safetyZone = ElevationAndSafetyEngine.evaluateWindowAndUtilitySafetyZones(candidate, room)
        if (safetyZone.isBlockedByWindowSafetyZone) return true

        return getPhysicalCollidingIds(candidate, existingElements, toleranceMm).isNotEmpty()
    }

    /**
     * Prevents physical cabinet overlap during interactive 2D/3D dragging or numeric/rotational transforms.
     * - If `candidate` is collision-free, returns it as-is.
     * - If `candidate` overlaps one or more existing cabinets, resolves to the nearest flush (0mm contact)
     *   position against the obstacle(s), or slides along the unobstructed axis / binary-searches the exact
     *   1mm contact boundary along the movement vector so cabinets NEVER penetrate each other.
     */
    fun resolveNonOverlappingMovement(
        original: KitchenElement,
        candidate: KitchenElement,
        existingElements: List<KitchenElement>,
        room: RoomStructure,
        toleranceMm: Float = 1.0f
    ): NonOverlapResolution {
        val clampedCandidate = clampInsideRoomBounds(candidate, room)
        val initialCollidingIds = getPhysicalCollidingIds(clampedCandidate, existingElements, toleranceMm)
        val safetyZone = ElevationAndSafetyEngine.evaluateWindowAndUtilitySafetyZones(clampedCandidate, room)

        if (initialCollidingIds.isEmpty() && !safetyZone.isBlockedByWindowSafetyZone) {
            return NonOverlapResolution(
                resolvedElement = clampedCandidate,
                wasOverlapPrevented = false
            )
        }

        val (candEffW, candEffD) = getEffectiveFootprint(clampedCandidate)
        val halfW = candEffW * 0.5f
        val halfD = candEffD * 0.5f

        // 1. Collect all flush (0mm contact) candidate positions around the colliding obstacle(s)
        val flushCandidates = mutableListOf<Vector3>()
        val collidingElements = existingElements.filter { it.id in initialCollidingIds }

        for (obs in collidingElements) {
            val obsAabb = obs.getAABB()
            val rightX = obsAabb.max.x + halfW
            val leftX = obsAabb.min.x - halfW
            val frontZ = obsAabb.max.z + halfD
            val backZ = obsAabb.min.z - halfD

            // Flush along X (keeping candidate Z or matching obstacle Z)
            flushCandidates.add(Vector3(rightX, clampedCandidate.position.y, clampedCandidate.position.z))
            flushCandidates.add(Vector3(leftX, clampedCandidate.position.y, clampedCandidate.position.z))
            flushCandidates.add(Vector3(rightX, clampedCandidate.position.y, obs.position.z))
            flushCandidates.add(Vector3(leftX, clampedCandidate.position.y, obs.position.z))

            // Flush along Z (keeping candidate X or matching obstacle X)
            flushCandidates.add(Vector3(clampedCandidate.position.x, clampedCandidate.position.y, frontZ))
            flushCandidates.add(Vector3(clampedCandidate.position.x, clampedCandidate.position.y, backZ))
            flushCandidates.add(Vector3(obs.position.x, clampedCandidate.position.y, frontZ))
            flushCandidates.add(Vector3(obs.position.x, clampedCandidate.position.y, backZ))

            // Vertical flush for upper/bridge cabinets
            if (clampedCandidate.type.category == ElementCategory.WALL_CABINET ||
                clampedCandidate.type.category == ElementCategory.BRIDGE_CABINET
            ) {
                val aboveY = obsAabb.max.y
                val belowY = obsAabb.min.y - clampedCandidate.dimensions.y
                if (aboveY + clampedCandidate.dimensions.y <= room.heightMm) {
                    flushCandidates.add(Vector3(clampedCandidate.position.x, aboveY, clampedCandidate.position.z))
                }
                if (belowY >= 1450f) {
                    flushCandidates.add(Vector3(clampedCandidate.position.x, belowY, clampedCandidate.position.z))
                }
            }
        }

        val maxNaturalSnapJumpMm = max(candEffW, candEffD) * 1.45f
        val validFlush = flushCandidates
            .map { pos -> clampInsideRoomBounds(clampedCandidate.copy(position = pos.roundTo1mm()), room) }
            .filter { testElem ->
                val distToTarget = hypot(
                    testElem.position.x - clampedCandidate.position.x,
                    testElem.position.z - clampedCandidate.position.z
                )
                distToTarget <= maxNaturalSnapJumpMm &&
                    !hasPhysicalOverlap(testElem, existingElements, room, toleranceMm)
            }
            .minByOrNull { testElem ->
                // Prefer closest to user's target cursor position, with tie-breaker toward original position
                val distTarget = (testElem.position - clampedCandidate.position).length()
                val distOrig = (testElem.position - original.position).length()
                distTarget + distOrig * 0.18f
            }

        val blockedNames = collidingElements.joinToString("، ") { "«${it.name}»" }
        val preventMsg = if (blockedNames.isNotBlank()) {
            "🛡️ منع تداخل الخزائن: تم إيقاف التداخل وتثبيت الخزانة ملاصقة لـ $blockedNames (0 مم)"
        } else {
            "🛡️ منع تداخل الخزائن: تم ضبط موقع الخزانة لمنع التداخل الهندسي"
        }

        if (validFlush != null) {
            return NonOverlapResolution(
                resolvedElement = validFlush,
                wasOverlapPrevented = true,
                blockedByElementIds = initialCollidingIds,
                messageAr = preventMsg
            )
        }

        // 2. Try axis-separated sliding from `original` (X-only or Z-only) + Binary Contact Search
        val origWithNewRotDims = clampInsideRoomBounds(
            clampedCandidate.copy(position = original.position),
            room
        )
        if (!hasPhysicalOverlap(origWithNewRotDims, existingElements, room, toleranceMm)) {
            // Binary search along segment original.position -> clampedCandidate.position
            var low = 0f
            var high = 1f
            var bestPos = origWithNewRotDims.position
            for (step in 0 until 12) {
                val mid = (low + high) * 0.5f
                val interpPos = (origWithNewRotDims.position + (clampedCandidate.position - origWithNewRotDims.position) * mid).roundTo1mm()
                val testElem = clampInsideRoomBounds(clampedCandidate.copy(position = interpPos), room)
                if (!hasPhysicalOverlap(testElem, existingElements, room, toleranceMm)) {
                    bestPos = testElem.position
                    low = mid
                } else {
                    high = mid
                }
            }

            // Also check X-only and Z-only slides to see if sliding along the obstacle face gets closer to target
            val slideX = clampInsideRoomBounds(
                clampedCandidate.copy(
                    position = Vector3(clampedCandidate.position.x, clampedCandidate.position.y, original.position.z).roundTo1mm()
                ),
                room
            )
            val slideZ = clampInsideRoomBounds(
                clampedCandidate.copy(
                    position = Vector3(original.position.x, clampedCandidate.position.y, clampedCandidate.position.z).roundTo1mm()
                ),
                room
            )

            val slideCandidates = buildList {
                add(clampInsideRoomBounds(clampedCandidate.copy(position = bestPos), room))
                if (!hasPhysicalOverlap(slideX, existingElements, room, toleranceMm)) add(slideX)
                if (!hasPhysicalOverlap(slideZ, existingElements, room, toleranceMm)) add(slideZ)
            }

            val bestSlide = slideCandidates.minByOrNull { (it.position - clampedCandidate.position).length() }
                ?: origWithNewRotDims

            return NonOverlapResolution(
                resolvedElement = bestSlide,
                wasOverlapPrevented = true,
                blockedByElementIds = initialCollidingIds,
                messageAr = preventMsg
            )
        }

        // 3. Fallback if `original` itself was overlapping (e.g., after resizing/rotating in-place):
        // relocate to the nearest valid non-overlapping slot in the room.
        val nearestFree = findNearestFreePosition(
            candidate = clampedCandidate,
            existingElements = existingElements,
            room = room,
            toleranceMm = toleranceMm
        )
        return NonOverlapResolution(
            resolvedElement = nearestFree,
            wasOverlapPrevented = true,
            blockedByElementIds = initialCollidingIds,
            messageAr = preventMsg
        )
    }

    /**
     * Finds the nearest guaranteed non-overlapping position for `candidate` inside `room`.
     * Prioritizes:
     * 1. The candidate's current position if already collision-free.
     * 2. Flush side-by-side positions next to existing cabinets along the same wall/elevation band.
     * 3. Wall perimeter slots (North, West, East, South walls with automatic inward-facing rotation).
     * 4. Room interior grid slots.
     */
    fun findNearestFreePosition(
        candidate: KitchenElement,
        existingElements: List<KitchenElement>,
        room: RoomStructure,
        toleranceMm: Float = 1.0f
    ): KitchenElement {
        val clamped = clampInsideRoomBounds(candidate, room)
        if (!hasPhysicalOverlap(clamped, existingElements, room, toleranceMm)) {
            return clamped
        }

        val isIsland = candidate.type.category == ElementCategory.ISLAND_AND_SURFACE
        val sameBandElements = existingElements.filter { other ->
            other.id != candidate.id &&
                other.isVisible &&
                !isCountertopInsertPair(candidate, other) &&
                abs(other.position.y - candidate.position.y) < 550f
        }

        val candidateSlots = mutableListOf<KitchenElement>()

        fun addSlotIfInside(posX: Float, posY: Float, posZ: Float, rotY: Float) {
            val test = clampInsideRoomBounds(
                clamped.copy(
                    position = Vector3(posX, posY, posZ).roundTo1mm(),
                    rotationY = rotY
                ),
                room
            )
            candidateSlots.add(test)
        }

        // 1. If island/peninsula, try central room positions first
        if (isIsland) {
            val (effW, effD) = getEffectiveFootprint(clamped)
            val halfW = effW * 0.5f
            val halfD = effD * 0.5f
            for (zFactor in listOf(0.58f, 0.50f, 0.66f, 0.45f, 0.72f)) {
                for (xFactor in listOf(0.50f, 0.42f, 0.58f, 0.35f, 0.65f)) {
                    val cx = (room.widthMm * xFactor).coerceIn(halfW, room.widthMm - halfW)
                    val cz = (room.depthMm * zFactor).coerceIn(halfD, room.depthMm - halfD)
                    addSlotIfInside(cx, clamped.position.y, cz, clamped.rotationY)
                }
            }
        }

        // 2. Try flush adjacent positions next to existing cabinets on the same elevation band
        for (other in sameBandElements) {
            val otherAabb = other.getAABB()
            val (candWSameRot, candDSameRot) = getEffectiveFootprint(clamped.copy(rotationY = other.rotationY))
            val hw = candWSameRot * 0.5f
            val hd = candDSameRot * 0.5f

            // Right & Left of `other` (matching `other`'s rotation & Z)
            addSlotIfInside(otherAabb.max.x + hw, clamped.position.y, other.position.z, other.rotationY)
            addSlotIfInside(otherAabb.min.x - hw, clamped.position.y, other.position.z, other.rotationY)

            // Front (+Z) & Back (-Z) of `other` (matching `other`'s rotation & X)
            addSlotIfInside(other.position.x, clamped.position.y, otherAabb.max.z + hd, other.rotationY)
            addSlotIfInside(other.position.x, clamped.position.y, otherAabb.min.z - hd, other.rotationY)
        }

        // 3. Scan along the 4 walls (North, West, East, South) at 50mm intervals
        if (!isIsland) {
            val baseW = clamped.dimensions.x
            val baseD = clamped.dimensions.z
            val halfW0 = baseW * 0.5f
            val halfD0 = baseD * 0.5f

            // North Wall (Z = halfD0, rot = 0°)
            var x = halfW0
            while (x <= room.widthMm - halfW0 + 1f) {
                addSlotIfInside(x, clamped.position.y, halfD0, 0f)
                x += 50f
            }

            // West Wall (X = halfD0, rot = 90° -> effW = baseD, effD = baseW)
            var zWest = halfW0
            while (zWest <= room.depthMm - halfW0 + 1f) {
                addSlotIfInside(halfD0, clamped.position.y, zWest, 90f)
                zWest += 50f
            }

            // East Wall (X = room.widthMm - halfD0, rot = 270°)
            var zEast = halfW0
            while (zEast <= room.depthMm - halfW0 + 1f) {
                addSlotIfInside(room.widthMm - halfD0, clamped.position.y, zEast, 270f)
                zEast += 50f
            }

            // South Wall (Z = room.depthMm - halfD0, rot = 180°)
            var xSouth = halfW0
            while (xSouth <= room.widthMm - halfW0 + 1f) {
                addSlotIfInside(xSouth, clamped.position.y, room.depthMm - halfD0, 180f)
                xSouth += 50f
            }
        }

        // 4. Scan interior room grid at 100mm intervals as universal fallback
        val (effW, effD) = getEffectiveFootprint(clamped)
        val halfW = effW * 0.5f
        val halfD = effD * 0.5f
        var gz = halfD
        while (gz <= room.depthMm - halfD + 1f) {
            var gx = halfW
            while (gx <= room.widthMm - halfW + 1f) {
                addSlotIfInside(gx, clamped.position.y, gz, clamped.rotationY)
                gx += 100f
            }
            gz += 100f
        }

        // Find the valid collision-free slot closest to `clamped.position` (with bonus for keeping same rotation)
        val bestFree = candidateSlots
            .firstOrNull { !hasPhysicalOverlap(it, existingElements, room, toleranceMm) }
            ?.let {
                candidateSlots
                    .filter { slot -> !hasPhysicalOverlap(slot, existingElements, room, toleranceMm) }
                    .minByOrNull { slot ->
                        val dist = hypot(slot.position.x - clamped.position.x, slot.position.z - clamped.position.z)
                        val rotPenalty = if (abs(slot.rotationY - clamped.rotationY) < 1f) 0f else 180f
                        dist + rotPenalty
                    }
            }

        return bestFree ?: clamped
    }

    /**
     * Resolves any physical overlaps across an entire list of scene elements sequentially.
     */
    fun resolveAllSceneOverlaps(
        elements: List<KitchenElement>,
        room: RoomStructure,
        toleranceMm: Float = 1.0f
    ): List<KitchenElement> {
        val placed = mutableListOf<KitchenElement>()
        for (elem in elements) {
            val resolved = if (hasPhysicalOverlap(elem, placed, room, toleranceMm)) {
                findNearestFreePosition(elem, placed, room, toleranceMm)
            } else {
                clampInsideRoomBounds(elem, room)
            }
            placed.add(resolved)
        }
        return placed
    }

    /**
     * Checks if a candidate element collides with existing room elements, breaches wall limits,
     * violates window safety zones (blocking solid wall cabinets over windows), covers 60cm zone sockets,
     * or optionally violates standard ergonomic clearance rules (door swing, walkway clearance, corner door interference, sink-stove spacing).
     */
    fun checkCollision(
        candidate: KitchenElement,
        existingElements: List<KitchenElement>,
        room: RoomStructure,
        toleranceMm: Float = 1.0f, // 1mm contact tolerance allowed for touching cabinets
        includeErgonomicClearance: Boolean = false
    ): CollisionResult {
        val candidateOBB = candidate.getOBB()
        val aabb = candidateOBB.toAABB()

        // 1. Room boundary check
        val isOutOfBounds = aabb.min.x < -toleranceMm ||
                aabb.max.x > room.widthMm + toleranceMm ||
                aabb.min.z < -toleranceMm ||
                aabb.max.z > room.depthMm + toleranceMm ||
                aabb.max.y > room.heightMm + toleranceMm

        if (isOutOfBounds) {
            return CollisionResult(
                hasCollision = true,
                isOutOfBounds = true,
                isPhysicalOverlap = true,
                message = "⛔ تجاوز لحدود الغرفة والجدران / Out of room bounds"
            )
        }

        // 2. Window Safety Zone & 60cm Backsplash MEP Utility Check (Section 3)
        val safetyZone = ElevationAndSafetyEngine.evaluateWindowAndUtilitySafetyZones(candidate, room)
        if (safetyZone.isBlockedByWindowSafetyZone) {
            return CollisionResult(
                hasCollision = true,
                isBlockedByWindowSafetyZone = true,
                isPhysicalOverlap = true,
                message = safetyZone.safetyNoticeAr ?: "⛔ منطقة أمان النافذة: يمنع تركيب خزانة علوية مصمتة فوق الشباك"
            )
        }

        // 3. Inter-cabinet physical overlap check via SAT
        val collidingIds = getPhysicalCollidingIds(candidate, existingElements, toleranceMm)

        if (collidingIds.isNotEmpty()) {
            return CollisionResult(
                hasCollision = true,
                collidingElementIds = collidingIds,
                isOutOfBounds = false,
                isPhysicalOverlap = true,
                message = "⛔ تداخل مباشر مع خزانة أخرى (${collidingIds.size}) / Collision detected"
            )
        }

        // 4. Optional Ergonomic Door-Swing Clearance Check (only critical door-swing blockages < 650mm at matching elevation)
        if (includeErgonomicClearance) {
            val sceneWithCandidate = existingElements.filter { it.id != candidate.id } + candidate
            val ergReport = ErgonomicValidationEngine.validate(room, sceneWithCandidate)
            val criticalZones = ergReport.doorClearanceZones.filter { zone ->
                zone.isCriticalSwingBlocked && zone.elementId == candidate.id
            }

            if (criticalZones.isNotEmpty()) {
                val z = criticalZones.first()
                val msg = "⛔ انتهاك خلوص فتح الباب: المسافة المتاحة أمام الخزانة ${(z.availableClearanceMm / 10f).toInt()} سم فقط (الحد الأدنى لفتح الضلفة 65 سم)"
                return CollisionResult(
                    hasCollision = true,
                    collidingElementIds = emptyList(),
                    isErgonomicClearanceViolation = true,
                    message = msg
                )
            }
        }

        if (safetyZone.coveredUtilityPoints.isNotEmpty()) {
            return CollisionResult(
                hasCollision = false,
                coveredUtilityCount = safetyZone.coveredUtilityPoints.size,
                message = safetyZone.safetyNoticeAr ?: "⚠️ تنبيه: الخزانة تغطي مقبس كهرباء (فيش) أو نقطة خدمات في منطقة الـ 60 سم"
            )
        }

        return CollisionResult(
            hasCollision = false,
            collidingElementIds = emptyList(),
            isOutOfBounds = false,
            message = safetyZone.safetyNoticeAr ?: "خالي من التداخل / Clear"
        )
    }
}

