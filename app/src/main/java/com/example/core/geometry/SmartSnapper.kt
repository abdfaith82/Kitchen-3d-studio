package com.example.core.geometry

import com.example.core.math.MathUtils
import com.example.core.math.Vector3
import com.example.domain.model.ElementCategory
import com.example.domain.model.KitchenElement
import com.example.domain.model.RoomStructure
import kotlin.math.abs
import kotlin.math.min

data class SnapGuideLine(
    val start: Vector3,
    val end: Vector3,
    val labelMm: String,
    val isWallGuide: Boolean,
    val isCollisionWarning: Boolean = false
)

data class SnapResult(
    val position: Vector3,
    val rotationY: Float,
    val isSnappedToWall: Boolean,
    val isSnappedToNeighbor: Boolean,
    val guideLines: List<SnapGuideLine>,
    val hasCollisionOrViolation: Boolean = false,
    val isPhysicalOverlap: Boolean = false,
    val isErgonomicClearanceViolation: Boolean = false,
    val conflictingElementIds: List<String> = emptyList(),
    val collisionReasonAr: String? = null,
    val clearanceZones: List<DoorClearanceZone> = emptyList(),
    val nonOverlappingPosition: Vector3 = position,
    val wasOverlapPrevented: Boolean = false
)

/**
 * High-precision CAD Smart Snapping engine.
 * Applies magnetic snapping to walls (with automatic wall-facing rotation) and adjacent cabinets
 * with strict 1mm tolerance, room-boundary clamping, and real-time collision + ergonomic clearance detection.
 */
object SmartSnapper {

    const val DEFAULT_SNAP_THRESHOLD_MM = 80.0f // 80mm magnetic pull radius
    const val DEFAULT_GRID_STEP_MM = 1.0f // Strict 1mm grid quantization
    const val ROTATION_SNAP_THRESHOLD_DEG = 7.5f // 7.5 degree angular snap window

    /**
     * Quantizes a 3D coordinate vector to the specified millimeter grid step (defaults to 1mm).
     */
    fun quantizeToGridMm(position: Vector3, gridStepMm: Float = DEFAULT_GRID_STEP_MM): Vector3 {
        val step = gridStepMm.coerceAtLeast(1.0f)
        val qx = kotlin.math.round(position.x / step) * step
        val qy = kotlin.math.round(position.y / step) * step
        val qz = kotlin.math.round(position.z / step) * step
        return Vector3(qx, qy, qz).roundTo1mm()
    }

    /**
     * Snaps an element position and rotation against room walls, adjacent cabinets, and a 1mm grid,
     * and evaluates physical overlap + standard ergonomic clearance rules for the visual collision indicator.
     * When [preventOverlap] is true, any physical overlap is automatically resolved to a flush 0mm contact slot.
     */
    fun snap(
        target: KitchenElement,
        existingElements: List<KitchenElement>,
        room: RoomStructure,
        snapThresholdMm: Float = DEFAULT_SNAP_THRESHOLD_MM,
        gridStepMm: Float = DEFAULT_GRID_STEP_MM,
        autoAlignRotationToWall: Boolean = true,
        preventOverlap: Boolean = false
    ): SnapResult {
        var snappedPos = quantizeToGridMm(target.position, gridStepMm)
        var snappedRot = target.rotationY
        var snappedToWall = false
        var snappedToNeighbor = false
        val guideLines = mutableListOf<SnapGuideLine>()

        // 1. Angular Snapping (Snap to 0, 90, 180, 270 degrees)
        val normalizedRot = MathUtils.normalizeAngle(snappedRot)
        val cardinalAngles = listOf(0f, 90f, 180f, 270f, 360f)
        for (cardinal in cardinalAngles) {
            if (abs(normalizedRot - cardinal) <= ROTATION_SNAP_THRESHOLD_DEG) {
                snappedRot = cardinal % 360f
                break
            }
        }

        // Auto-rotate cabinet to face into the room when dragged close to a wall
        if (autoAlignRotationToWall && target.type.category != ElementCategory.ISLAND_AND_SURFACE) {
            val baseHalfD = target.dimensions.z * 0.5f
            val dNorth = abs(snappedPos.z - baseHalfD)
            val dSouth = abs(room.depthMm - (snappedPos.z + baseHalfD))
            val dWest = abs(snappedPos.x - baseHalfD)
            val dEast = abs(room.widthMm - (snappedPos.x + baseHalfD))
            val minWallDist = min(min(dNorth, dSouth), min(dWest, dEast))

            if (minWallDist <= snapThresholdMm * 1.35f) {
                snappedRot = when (minWallDist) {
                    dNorth -> 0f
                    dSouth -> 180f
                    dWest -> 90f
                    else -> 270f
                }
            }
        }

        // Compute effective footprint dimensions based on rotation
        val isRotated90or270 = snappedRot == 90f || snappedRot == 270f
        val effectiveWidth = if (isRotated90or270) target.dimensions.z else target.dimensions.x
        val effectiveDepth = if (isRotated90or270) target.dimensions.x else target.dimensions.z
        val halfW = effectiveWidth * 0.5f
        val halfD = effectiveDepth * 0.5f

        // Clamp inside room boundaries first so elements never drift outside the walls
        val maxRoomX = (room.widthMm - halfW).coerceAtLeast(halfW)
        val maxRoomZ = (room.depthMm - halfD).coerceAtLeast(halfD)
        snappedPos = snappedPos.copy(
            x = snappedPos.x.coerceIn(halfW, maxRoomX),
            z = snappedPos.z.coerceIn(halfD, maxRoomZ)
        )

        // 2. Wall Snapping (Snap flush with 1mm precision)
        // North Wall (Z = 0)
        val distToNorth = (snappedPos.z - halfD)
        if (abs(distToNorth) <= snapThresholdMm) {
            snappedPos = snappedPos.copy(z = halfD)
            snappedToWall = true
            guideLines.add(
                SnapGuideLine(
                    start = Vector3(snappedPos.x, snappedPos.y, 0f),
                    end = Vector3(snappedPos.x, snappedPos.y, halfD),
                    labelMm = "0 mm (تثبيت جدار شمالي)",
                    isWallGuide = true
                )
            )
        }

        // South Wall (Z = room.depthMm)
        val distToSouth = room.depthMm - (snappedPos.z + halfD)
        if (abs(distToSouth) <= snapThresholdMm) {
            snappedPos = snappedPos.copy(z = room.depthMm - halfD)
            snappedToWall = true
            guideLines.add(
                SnapGuideLine(
                    start = Vector3(snappedPos.x, snappedPos.y, room.depthMm - halfD),
                    end = Vector3(snappedPos.x, snappedPos.y, room.depthMm),
                    labelMm = "0 mm (تثبيت جدار جنوبي)",
                    isWallGuide = true
                )
            )
        }

        // West Wall (X = 0)
        val distToWest = (snappedPos.x - halfW)
        if (abs(distToWest) <= snapThresholdMm) {
            snappedPos = snappedPos.copy(x = halfW)
            snappedToWall = true
            guideLines.add(
                SnapGuideLine(
                    start = Vector3(0f, snappedPos.y, snappedPos.z),
                    end = Vector3(halfW, snappedPos.y, snappedPos.z),
                    labelMm = "0 mm (تثبيت جدار غربي)",
                    isWallGuide = true
                )
            )
        }

        // East Wall (X = room.widthMm)
        val distToEast = room.widthMm - (snappedPos.x + halfW)
        if (abs(distToEast) <= snapThresholdMm) {
            snappedPos = snappedPos.copy(x = room.widthMm - halfW)
            snappedToWall = true
            guideLines.add(
                SnapGuideLine(
                    start = Vector3(room.widthMm - halfW, snappedPos.y, snappedPos.z),
                    end = Vector3(room.widthMm, snappedPos.y, snappedPos.z),
                    labelMm = "0 mm (تثبيت جدار شرقي)",
                    isWallGuide = true
                )
            )
        }

        // 3. Adjacent Cabinet Snapping (Side-by-side seamless magnetic connection)
        val sameLevelElements = existingElements.filter { other ->
            other.id != target.id &&
                other.isVisible &&
                abs(other.position.y - target.position.y) < 350f
        }

        for (other in sameLevelElements) {
            val otherIsRot = other.rotationY == 90f || other.rotationY == 270f
            val otherEffW = if (otherIsRot) other.dimensions.z else other.dimensions.x
            val otherEffD = if (otherIsRot) other.dimensions.x else other.dimensions.z
            val otherHalfW = otherEffW * 0.5f
            val otherHalfD = otherEffD * 0.5f

            // Snap along X-axis (side-to-side alignment along North/South walls)
            val zOverlap = abs(snappedPos.z - other.position.z) < (halfD + otherHalfD) * 0.9f
            if (zOverlap) {
                // Snap to Right of 'other'
                val snapRightX = other.position.x + otherHalfW + halfW
                if (abs(snappedPos.x - snapRightX) <= snapThresholdMm * 1.25f) {
                    snappedPos = snappedPos.copy(x = snapRightX, z = if (snappedToWall) snappedPos.z else other.position.z)
                    snappedToNeighbor = true
                    guideLines.add(
                        SnapGuideLine(
                            start = other.position,
                            end = snappedPos,
                            labelMm = "تثبيت ملاصق (0 mm)",
                            isWallGuide = false
                        )
                    )
                }

                // Snap to Left of 'other'
                val snapLeftX = other.position.x - otherHalfW - halfW
                if (abs(snappedPos.x - snapLeftX) <= snapThresholdMm * 1.25f) {
                    snappedPos = snappedPos.copy(x = snapLeftX, z = if (snappedToWall) snappedPos.z else other.position.z)
                    snappedToNeighbor = true
                    guideLines.add(
                        SnapGuideLine(
                            start = other.position,
                            end = snappedPos,
                            labelMm = "تثبيت ملاصق (0 mm)",
                            isWallGuide = false
                        )
                    )
                }
            }

            // Snap along Z-axis (side-to-side along West/East walls)
            val xOverlap = abs(snappedPos.x - other.position.x) < (halfW + otherHalfW) * 0.9f
            if (xOverlap) {
                // Snap to South (+Z) of 'other'
                val snapFrontZ = other.position.z + otherHalfD + halfD
                if (abs(snappedPos.z - snapFrontZ) <= snapThresholdMm * 1.25f) {
                    snappedPos = snappedPos.copy(z = snapFrontZ, x = if (snappedToWall) snappedPos.x else other.position.x)
                    snappedToNeighbor = true
                    guideLines.add(
                        SnapGuideLine(
                            start = other.position,
                            end = snappedPos,
                            labelMm = "تثبيت ملاصق (0 mm)",
                            isWallGuide = false
                        )
                    )
                }

                // Snap to North (-Z) of 'other'
                val snapBackZ = other.position.z - otherHalfD - halfD
                if (abs(snappedPos.z - snapBackZ) <= snapThresholdMm * 1.25f) {
                    snappedPos = snappedPos.copy(z = snapBackZ, x = if (snappedToWall) snappedPos.x else other.position.x)
                    snappedToNeighbor = true
                    guideLines.add(
                        SnapGuideLine(
                            start = other.position,
                            end = snappedPos,
                            labelMm = "تثبيت ملاصق (0 mm)",
                            isWallGuide = false
                        )
                    )
                }
            }
        }

        // Final room boundary clamp & 1mm quantization
        val finalPos = snappedPos.copy(
            x = snappedPos.x.coerceIn(halfW, maxRoomX),
            z = snappedPos.z.coerceIn(halfD, maxRoomZ)
        ).roundTo1mm()

        // 4. Real-Time Collision, Anti-Overlap Resolution & Ergonomic Clearance Evaluation
        val snappedTarget = target.copy(position = finalPos, rotationY = snappedRot)
        val nonOverlapRes = CollisionDetector.resolveNonOverlappingMovement(
            original = target,
            candidate = snappedTarget,
            existingElements = existingElements,
            room = room,
            toleranceMm = 1.0f
        )

        val resolvedTarget = if (preventOverlap && nonOverlapRes.wasOverlapPrevented) {
            snappedToNeighbor = true
            nonOverlapRes.blockedByElementIds.forEach { blockId ->
                existingElements.find { it.id == blockId }?.let { blockElem ->
                    guideLines.add(
                        SnapGuideLine(
                            start = nonOverlapRes.resolvedElement.position + Vector3(0f, nonOverlapRes.resolvedElement.dimensions.y * 0.5f, 0f),
                            end = blockElem.position + Vector3(0f, blockElem.dimensions.y * 0.5f, 0f),
                            labelMm = "🛡️ منع تداخل (0 mm)",
                            isWallGuide = false,
                            isCollisionWarning = false
                        )
                    )
                }
            }
            nonOverlapRes.resolvedElement
        } else {
            snappedTarget
        }

        val collisionCheck = CollisionDetector.checkCollision(
            candidate = resolvedTarget,
            existingElements = existingElements,
            room = room,
            toleranceMm = 2.0f
        )

        val sceneWithTarget = existingElements.filter { it.id != target.id } + resolvedTarget
        val ergReport = ErgonomicValidationEngine.validate(room, sceneWithTarget)
        val clearanceIssues = ergReport.issues.filter { issue ->
            resolvedTarget.id in issue.affectedElementIds &&
                (issue.code == ErgonomicRuleCode.INSUFFICIENT_DOOR_CLEARANCE ||
                    issue.code == ErgonomicRuleCode.CORNER_DOOR_INTERFERENCE ||
                    issue.code == ErgonomicRuleCode.SINK_TOO_CLOSE_TO_STOVE ||
                    issue.code == ErgonomicRuleCode.ELEMENT_OVERLAP_COLLISION)
        }
        val violatedZones = ergReport.doorClearanceZones.filter { zone ->
            zone.isInsufficient && zone.elementId == resolvedTarget.id
        }

        val hasErgonomicViolation = clearanceIssues.isNotEmpty() || violatedZones.isNotEmpty()
        val conflictingIds = (
            collisionCheck.collidingElementIds +
                clearanceIssues.flatMap { it.affectedElementIds } +
                violatedZones.map { it.elementId }
            )
            .filter { it != resolvedTarget.id }
            .distinct()

        // Emit red collision warning guidelines between the target and any conflicting element
        conflictingIds.forEach { conflictId ->
            existingElements.find { it.id == conflictId }?.let { conflictElem ->
                guideLines.add(
                    SnapGuideLine(
                        start = resolvedTarget.position + Vector3(0f, resolvedTarget.dimensions.y * 0.5f, 0f),
                        end = conflictElem.position + Vector3(0f, conflictElem.dimensions.y * 0.5f, 0f),
                        labelMm = if (collisionCheck.hasCollision) "⛔ تداخل مباشر" else "⚠ انتهاك خلوص",
                        isWallGuide = false,
                        isCollisionWarning = true
                    )
                )
            }
        }

        val collisionReason = when {
            collisionCheck.hasCollision && collisionCheck.message.isNotBlank() -> collisionCheck.message
            nonOverlapRes.wasOverlapPrevented && nonOverlapRes.messageAr != null -> nonOverlapRes.messageAr
            clearanceIssues.isNotEmpty() -> clearanceIssues.first().titleAr + ": " + clearanceIssues.first().descriptionAr
            violatedZones.isNotEmpty() -> {
                val z = violatedZones.first()
                "انتهاك خلوص فتح الباب/الممر (${(z.availableClearanceMm / 10f).toInt()} سم متاح من أصل ${(z.recommendedWalkwayMm / 10f).toInt()} سم قياسي)"
            }
            else -> null
        }

        return SnapResult(
            position = resolvedTarget.position,
            rotationY = resolvedTarget.rotationY,
            isSnappedToWall = snappedToWall,
            isSnappedToNeighbor = snappedToNeighbor,
            guideLines = guideLines,
            hasCollisionOrViolation = collisionCheck.hasCollision || hasErgonomicViolation,
            isPhysicalOverlap = collisionCheck.hasCollision,
            isErgonomicClearanceViolation = hasErgonomicViolation,
            conflictingElementIds = conflictingIds,
            collisionReasonAr = collisionReason,
            clearanceZones = violatedZones,
            nonOverlappingPosition = nonOverlapRes.resolvedElement.position,
            wasOverlapPrevented = nonOverlapRes.wasOverlapPrevented
        )
    }
}
