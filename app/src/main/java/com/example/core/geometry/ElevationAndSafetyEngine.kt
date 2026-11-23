package com.example.core.geometry

import com.example.core.math.MathUtils
import com.example.core.math.Vector3
import com.example.domain.model.*
import kotlin.math.abs

/**
 * Result of applying the Section 2 Automatic Elevation & 60cm Lower Safety Limit rules.
 */
data class ElevationConstraintResult(
    val resolvedPosition: Vector3,
    val linkedBaseElementId: String?,
    val underlyingBaseTopElevationMm: Float,
    val minimumAllowedWallElevationMm: Float,
    val wasClampedByLowerSafetyLimit: Boolean,
    val safetyMessageAr: String? = null
)

/**
 * Result of checking Section 3 Architectural Window Safety Zones and 60cm Backsplash Utility Points.
 */
data class ArchitecturalSafetyZoneResult(
    val isBlockedByWindowSafetyZone: Boolean,
    val isApprovedSinkUnderWindow: Boolean,
    val blockedWindowId: String? = null,
    val coveredUtilityPoints: List<WallUtilityPoint> = emptyList(),
    val safetyNoticeAr: String? = null
)

/**
 * Engineering Placement, Automatic Elevation, Dynamic Base-to-Wall Linking,
 * and Architectural Window/Utility Safety Zone Engine.
 *
 * Implements Section 2 & Section 3 of the Kitchen Design Blueprint Specification:
 * 1. **Automatic Elevation Formula (معادلة الارتفاع التلقائي)**:
 *    `Wall Cabinet Bottom Y = (Underlying Base Cabinet Height from Floor + 600mm [60 cm] clear backsplash gap)`.
 * 2. **Lower Safety Limit Constraint (قيد حد الأمان السفلي)**:
 *    Prevents manually dragging an upper wall cabinet below the 60cm gap above the base cabinet,
 *    while preserving full freedom to pull it upward (`Y >= baseTopY + 600mm`).
 * 3. **Dynamic Vertical Linking (الارتباط الديناميكي)**:
 *    When a base cabinet's height or size is modified later, any upper wall cabinet linked or aligned
 *    above it automatically shifts vertically to maintain the constant 60cm clearance.
 * 4. **Window Safety Zones (مناطق أمان النوافذ والأبواب)**:
 *    Blocks placing solid upper wall cabinets (`WALL_CABINET`) over a window opening while explicitly
 *    allowing sink base cabinets (`BASE_SINK_UNIT`, `BASE_SINK_VENTED_100`) directly beneath the window.
 * 5. **60cm Backsplash Utility Points Protection (توزيع وحماية نقاط الخدمات والفيش في منطقة الـ 60 سم)**:
 *    Detects if any cabinet covers an electrical socket or gas/plumbing point.
 */
object ElevationAndSafetyEngine {

    /** Mandatory 60 cm (600 mm) clear backsplash gap between base cabinet top and wall cabinet bottom */
    const val MANDATORY_BACKSPLASH_GAP_MM = 600.0f

    /** Standard reference base cabinet height from floor when no base cabinet is directly underneath */
    const val DEFAULT_BASE_CABINET_HEIGHT_MM = 850.0f

    /**
     * Finds the base cabinet directly underneath (or horizontally aligned with) the given wall cabinet.
     */
    fun findUnderlyingBaseCabinet(
        wallCabinet: KitchenElement,
        existingElements: List<KitchenElement>
    ): KitchenElement? {
        // 1. Check explicit link first if still horizontally close
        if (wallCabinet.linkedBaseElementId != null) {
            val linked = existingElements.find {
                it.id == wallCabinet.linkedBaseElementId &&
                        it.type.category == ElementCategory.BASE_CABINET &&
                        it.isVisible
            }
            if (linked != null && isHorizontallyOverlapping(wallCabinet, linked)) {
                return linked
            }
        }

        // 2. Search all visible base cabinets overlapping horizontally in X-Z footprint
        val baseCabinets = existingElements.filter {
            it.id != wallCabinet.id &&
                    it.isVisible &&
                    it.type.category == ElementCategory.BASE_CABINET
        }

        return baseCabinets
            .filter { base -> isHorizontallyOverlapping(wallCabinet, base) }
            .maxByOrNull { base -> base.position.y + base.dimensions.y }
    }

    /**
     * Checks if two elements overlap horizontally (on X-Z floor projection) enough to be vertically paired.
     */
    fun isHorizontallyOverlapping(upper: KitchenElement, lower: KitchenElement): Boolean {
        val uAABB = upper.getAABB()
        val lAABB = lower.getAABB()
        val overlapX = minOf(uAABB.max.x, lAABB.max.x) - maxOf(uAABB.min.x, lAABB.min.x)
        val overlapZ = minOf(uAABB.max.z, lAABB.max.z) - maxOf(uAABB.min.z, lAABB.min.z)
        // Wall cabinets are shallower (350mm) than base cabinets (600mm), so check X overlap + proximity in Z
        val centerDistX = abs(upper.position.x - lower.position.x)
        val centerDistZ = abs(upper.position.z - lower.position.z)
        val closeAlongWall = (overlapX > 60f && centerDistZ <= 450f) || (overlapZ > 60f && centerDistX <= 450f)
        return closeAlongWall
    }

    /**
     * Section 2.1: Automatic Elevation Formula (معادلة الارتفاع التلقائي):
     * Calculates the exact bottom point Y of an upper wall cabinet as:
     * `(Base Cabinet Top from Floor + 600mm [60 cm] clear space)`.
     */
    fun calculateAutomaticWallCabinetElevation(
        candidate: KitchenElement,
        existingElements: List<KitchenElement>
    ): ElevationConstraintResult {
        if (candidate.type.category != ElementCategory.WALL_CABINET) {
            return ElevationConstraintResult(
                resolvedPosition = candidate.position,
                linkedBaseElementId = candidate.linkedBaseElementId,
                underlyingBaseTopElevationMm = 0f,
                minimumAllowedWallElevationMm = 0f,
                wasClampedByLowerSafetyLimit = false
            )
        }

        val underlyingBase = findUnderlyingBaseCabinet(candidate, existingElements)
        // Reference base height: either the directly underlying base cabinet, or any base cabinet in the room, or 850mm
        val referenceBase = underlyingBase ?: existingElements
            .filter { it.type.category == ElementCategory.BASE_CABINET && it.isVisible }
            .maxByOrNull { it.position.y + it.dimensions.y }

        val baseTopFromFloorMm = if (referenceBase != null) {
            referenceBase.position.y + referenceBase.dimensions.y
        } else {
            DEFAULT_BASE_CABINET_HEIGHT_MM
        }

        val requiredBottomY = MathUtils.roundToMillimeter(baseTopFromFloorMm + MANDATORY_BACKSPLASH_GAP_MM)
        val resolvedPos = candidate.position.copy(y = requiredBottomY)

        return ElevationConstraintResult(
            resolvedPosition = resolvedPos,
            linkedBaseElementId = underlyingBase?.id ?: referenceBase?.id,
            underlyingBaseTopElevationMm = baseTopFromFloorMm,
            minimumAllowedWallElevationMm = requiredBottomY,
            wasClampedByLowerSafetyLimit = false,
            safetyMessageAr = "تم ضبط الارتفاع التلقائي للخزانة العلوية: ${(baseTopFromFloorMm / 10f).toInt()} سم (سفلية) + 60 سم (فراغ) = ${(requiredBottomY / 10f).toInt()} سم"
        )
    }

    /**
     * Section 2.2: Lower Safety Limit Constraint (قيد حد الأمان السفلي):
     * When manually moving a wall cabinet vertically, prevents it from descending below the 60cm gap
     * above the base cabinet, while allowing full freedom to move it higher (`Y >= baseTopY + 600mm`).
     */
    fun enforceWallCabinetLowerSafetyLimit(
        candidate: KitchenElement,
        existingElements: List<KitchenElement>,
        roomHeightMm: Float
    ): ElevationConstraintResult {
        if (candidate.type.category != ElementCategory.WALL_CABINET) {
            val clampedFloorY = candidate.position.y.coerceIn(0f, (roomHeightMm - candidate.dimensions.y).coerceAtLeast(0f))
            return ElevationConstraintResult(
                resolvedPosition = candidate.position.copy(y = clampedFloorY),
                linkedBaseElementId = candidate.linkedBaseElementId,
                underlyingBaseTopElevationMm = 0f,
                minimumAllowedWallElevationMm = 0f,
                wasClampedByLowerSafetyLimit = false
            )
        }

        val underlyingBase = findUnderlyingBaseCabinet(candidate, existingElements)
        val referenceBase = underlyingBase ?: existingElements
            .filter { it.type.category == ElementCategory.BASE_CABINET && it.isVisible }
            .maxByOrNull { it.position.y + it.dimensions.y }

        val baseTopFromFloorMm = if (referenceBase != null) {
            referenceBase.position.y + referenceBase.dimensions.y
        } else {
            DEFAULT_BASE_CABINET_HEIGHT_MM
        }

        val minSafeY = MathUtils.roundToMillimeter(baseTopFromFloorMm + MANDATORY_BACKSPLASH_GAP_MM)
        val maxCeilingY = (roomHeightMm - candidate.dimensions.y).coerceAtLeast(minSafeY)

        val requestedY = candidate.position.y
        val wasClamped = requestedY < minSafeY - 0.5f
        val clampedY = MathUtils.roundToMillimeter(requestedY.coerceIn(minSafeY, maxCeilingY))

        return ElevationConstraintResult(
            resolvedPosition = candidate.position.copy(y = clampedY),
            linkedBaseElementId = underlyingBase?.id ?: candidate.linkedBaseElementId ?: referenceBase?.id,
            underlyingBaseTopElevationMm = baseTopFromFloorMm,
            minimumAllowedWallElevationMm = minSafeY,
            wasClampedByLowerSafetyLimit = wasClamped,
            safetyMessageAr = if (wasClamped) {
                "قيد حد الأمان السفلي: يمنع نزول الخزانة العلوية لأقل من 60 سم فوق السفلية (الحد الأدنى ${(minSafeY / 10f).toInt()} سم)"
            } else null
        )
    }

    /**
     * Section 2.3: Dynamic Vertical Linking (الارتباط الديناميكي):
     * Whenever a base cabinet's height or dimensions are modified, all upper wall cabinets
     * linked to it or horizontally overlapping above it automatically shift vertically to preserve
     * the constant 60cm (600mm) clear backsplash gap (or higher if they were already pulled higher,
     * shifting by the exact delta or snapping to `newBaseTopY + 600mm`).
     */
    fun propagateDynamicBaseHeightToWallCabinets(
        modifiedBaseId: String,
        oldBaseTopY: Float,
        elements: List<KitchenElement>,
        roomHeightMm: Float
    ): List<KitchenElement> {
        val updatedBase = elements.find { it.id == modifiedBaseId } ?: return elements
        if (updatedBase.type.category != ElementCategory.BASE_CABINET) return elements

        val newBaseTopY = updatedBase.position.y + updatedBase.dimensions.y
        val deltaHeight = newBaseTopY - oldBaseTopY
        val targetMinWallY = MathUtils.roundToMillimeter(newBaseTopY + MANDATORY_BACKSPLASH_GAP_MM)

        return elements.map { elem ->
            if (elem.type.category == ElementCategory.WALL_CABINET) {
                val isLinkedToThisBase = elem.linkedBaseElementId == modifiedBaseId ||
                        isHorizontallyOverlapping(elem, updatedBase)
                if (isLinkedToThisBase) {
                    val maxCeilingY = (roomHeightMm - elem.dimensions.y).coerceAtLeast(targetMinWallY)
                    // Maintain the 60cm constant gap (or shift proportionally if raised above 60cm, never below 60cm)
                    val previousGap = elem.position.y - oldBaseTopY
                    val preservedGap = if (abs(previousGap - MANDATORY_BACKSPLASH_GAP_MM) <= 15f) {
                        MANDATORY_BACKSPLASH_GAP_MM
                    } else {
                        maxOf(MANDATORY_BACKSPLASH_GAP_MM, previousGap)
                    }
                    val newWallY = MathUtils.roundToMillimeter((newBaseTopY + preservedGap).coerceIn(targetMinWallY, maxCeilingY))
                    elem.copy(
                        position = elem.position.copy(y = newWallY),
                        linkedBaseElementId = modifiedBaseId
                    )
                } else {
                    elem
                }
            } else {
                elem
            }
        }
    }

    /**
     * Section 3.2 & 3.3: Architectural Window Safety Zones & 60cm Backsplash MEP Utility Points Check.
     * - Prevents mounting solid upper wall cabinets (`WALL_CABINET`) across any window opening.
     * - Explicitly allows placing a sink base cabinet (`BASE_SINK_UNIT`, `BASE_SINK_VENTED_100`) beneath the window.
     * - Flags if any solid cabinet covers an electrical socket (`ELECTRICAL_SOCKET`) or gas/plumbing point.
     */
    fun evaluateWindowAndUtilitySafetyZones(
        candidate: KitchenElement,
        room: RoomStructure
    ): ArchitecturalSafetyZoneResult {
        val aabb = candidate.getAABB()
        val wallProximityThresholdMm = 450f

        var blockedByWindow = false
        var approvedSinkUnderWindow = false
        var blockedWindowId: String? = null
        val coveredUtilities = mutableListOf<WallUtilityPoint>()

        val isSinkUnit = candidate.type == KitchenElementType.BASE_SINK_UNIT ||
                candidate.type == KitchenElementType.BASE_SINK_VENTED_100
        val isSolidWallCabinet = candidate.type.category == ElementCategory.WALL_CABINET ||
                candidate.type.category == ElementCategory.TALL_UNIT

        for (wall in room.walls) {
            if (!wall.isEnabled) continue

            // Check if element is against this wall
            val isNearWall = when (wall.side) {
                WallSide.NORTH -> aabb.min.z <= wallProximityThresholdMm
                WallSide.SOUTH -> (room.depthMm - aabb.max.z) <= wallProximityThresholdMm
                WallSide.WEST -> aabb.min.x <= wallProximityThresholdMm
                WallSide.EAST -> (room.widthMm - aabb.max.x) <= wallProximityThresholdMm
            }
            if (!isNearWall) continue

            // Element's horizontal span along this wall
            val (elemStartAlongWall, elemEndAlongWall) = when (wall.side) {
                WallSide.NORTH, WallSide.SOUTH -> Pair(aabb.min.x, aabb.max.x)
                WallSide.WEST, WallSide.EAST -> Pair(aabb.min.z, aabb.max.z)
            }
            val elemBottomY = aabb.min.y
            val elemTopY = aabb.max.y

            // 1. Check Window Openings Safety Zones
            for (opening in wall.openings) {
                if (opening.type == OpeningType.WINDOW) {
                    val horizOverlap = minOf(elemEndAlongWall, opening.endOffsetMm) -
                            maxOf(elemStartAlongWall, opening.offsetMm)

                    if (horizOverlap > 25f) {
                        val vertOverlap = minOf(elemTopY, opening.lintelHeightMm) -
                                maxOf(elemBottomY, opening.sillHeightMm)

                        if (isSolidWallCabinet && vertOverlap > 15f) {
                            blockedByWindow = true
                            blockedWindowId = opening.id
                        } else if (isSinkUnit && elemBottomY <= 50f) {
                            approvedSinkUnderWindow = true
                        }
                    }
                }
            }

            // 2. Check Manual Utility Points (Electrical sockets in 60cm zone, Gas, Plumbing)
            for (util in wall.utilityPoints) {
                val coversHoriz = util.offsetFromWallStartMm in (elemStartAlongWall + 10f)..(elemEndAlongWall - 10f)
                val coversVert = util.elevationFromFloorMm in (elemBottomY + 10f)..(elemTopY - 10f)
                if (coversHoriz && coversVert) {
                    // Note: Plumbing drain is expected inside a Sink Base Cabinet, so only flag if NOT a sink unit
                    val isValidSinkOverPlumbing = isSinkUnit && util.type == UtilityPointType.PLUMBING_DRAIN
                    if (!isValidSinkOverPlumbing) {
                        coveredUtilities.add(util)
                    }
                }
            }
        }

        val notice = when {
            blockedByWindow -> "منطقة أمان النافذة: يمنع تركيب خزانة علوية مصمتة فوق الشباك (يسمح بوضع المجلى أسفله فقط)"
            coveredUtilities.isNotEmpty() -> {
                val names = coveredUtilities.joinToString("، ") { it.label }
                "تحذير نقاط الخدمات: الخزانة تغطي ($names) في الحائط!"
            }
            approvedSinkUnderWindow -> "تموضع هندسي مثالي: وحدة المجلى أسفل النافذة مباشرة ضمن منطقة الأمان المسموحة"
            else -> null
        }

        return ArchitecturalSafetyZoneResult(
            isBlockedByWindowSafetyZone = blockedByWindow,
            isApprovedSinkUnderWindow = approvedSinkUnderWindow,
            blockedWindowId = blockedWindowId,
            coveredUtilityPoints = coveredUtilities,
            safetyNoticeAr = notice
        )
    }
}
