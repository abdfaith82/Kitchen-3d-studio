package com.example.core.geometry

import com.example.core.math.Vector3
import com.example.domain.model.KitchenElement
import com.example.domain.model.KitchenElementType
import com.example.domain.model.RoomStructure
import com.example.domain.repository.KitchenProject
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Severity levels for real-time ergonomic & architectural validation rules.
 */
enum class ValidationSeverity(
    val labelAr: String,
    val labelEn: String,
    val penaltyPoints: Int
) {
    ERROR("خطأ تصميمي حرج", "Critical Design Error", 25),
    WARNING("تنبيه إرجونومي", "Ergonomic Warning", 12),
    INFO("توصية تحسين", "Optimization Tip", 5)
}

/**
 * Standardized rule identifiers based on NKBA / architectural kitchen ergonomics.
 */
enum class ErgonomicRuleCode(
    val defaultTitleAr: String,
    val defaultTitleEn: String
) {
    SINK_TOO_FAR_FROM_STOVE(
        "الحوض بعيد جداً عن الموقد",
        "Sink too far from stove"
    ),
    SINK_TOO_CLOSE_TO_STOVE(
        "الحوض قريب جداً من الموقد بدون مساحة تحضير كافية",
        "Sink too close to stove (insufficient landing counter)"
    ),
    INSUFFICIENT_DOOR_CLEARANCE(
        "مسافة غير كافية لفتح أبواب الخزائن أو الممر",
        "Insufficient clearance for cabinet doors"
    ),
    CORNER_DOOR_INTERFERENCE(
        "تعارض فتح أبواب الخزائن المتعامدة في الزاوية",
        "Insufficient clearance for cabinet doors at corner"
    ),
    WORK_TRIANGLE_PERIMETER(
        "محيط مثلث العمل خارج النطاق الإرجونومي القياسي",
        "Work triangle perimeter out of ergonomic range"
    ),
    WORK_TRIANGLE_OBSTRUCTED(
        "تقاطع عائق مع مسار مثلث العمل",
        "Work triangle path obstructed"
    ),
    DISHWASHER_FAR_FROM_SINK(
        "غسالة الصحون بعيدة عن الحوض وتمديدات الصرف",
        "Dishwasher too far from sink"
    ),
    HOOD_CLEARANCE_HEIGHT(
        "ارتفاع الشفاط فوق الموقد غير قياسي",
        "Range hood clearance height mismatch"
    ),
    ELEMENT_OVERLAP_COLLISION(
        "تداخل هندسي بين الخزائن أو تجاوز لحدود الغرفة",
        "Cabinet overlap collision or room boundary breach"
    )
}

/**
 * Represents the projected front door-swing / walkway clearance zone of a cabinet or appliance.
 */
data class DoorClearanceZone(
    val elementId: String,
    val elementName: String,
    val frontOriginMm: Vector3,
    val frontNormal: Vector3,
    val widthMm: Float,
    val requiredDoorSwingMm: Float,
    val recommendedWalkwayMm: Float,
    val availableClearanceMm: Float,
    val isInsufficient: Boolean,
    val isCriticalSwingBlocked: Boolean,
    val blockingTargetName: String? = null
)

/**
 * Computed metrics for the Kitchen Work Triangle (Sink – Stove – Refrigerator).
 */
data class WorkTriangleMetrics(
    val sinkElementId: String,
    val stoveElementId: String,
    val fridgeElementId: String? = null,
    val sinkPositionMm: Vector3,
    val stovePositionMm: Vector3,
    val fridgePositionMm: Vector3? = null,
    val sinkToStoveCenterMm: Float,
    val sinkToStoveEdgeGapMm: Float,
    val sinkToFridgeCenterMm: Float? = null,
    val stoveToFridgeCenterMm: Float? = null,
    val totalPerimeterMm: Float? = null,
    val isSinkStoveOptimal: Boolean,
    val isTriangleOptimal: Boolean
)

/**
 * Single actionable design or ergonomic issue flagged in real time.
 */
data class ErgonomicIssue(
    val id: String,
    val code: ErgonomicRuleCode,
    val severity: ValidationSeverity,
    val titleAr: String,
    val titleEn: String,
    val descriptionAr: String,
    val descriptionEn: String,
    val recommendationAr: String,
    val measuredValueMm: Float,
    val standardRangeLabel: String,
    val affectedElementIds: List<String>,
    val markerPositionMm: Vector3
)

/**
 * Complete real-time ergonomic validation report for the active kitchen layout.
 */
data class ErgonomicValidationReport(
    val score: Int = 100,
    val issues: List<ErgonomicIssue> = emptyList(),
    val workTriangle: WorkTriangleMetrics? = null,
    val doorClearanceZones: List<DoorClearanceZone> = emptyList(),
    val errorCount: Int = 0,
    val warningCount: Int = 0,
    val infoCount: Int = 0,
    val isCompliant: Boolean = true
)

/**
 * Real-time Architectural & Ergonomic Validation Engine (NKBA / ISO Kitchen Standards).
 */
object ErgonomicValidationEngine {

    // NKBA / Architectural Ergonomic Constants (in mm)
    const val MAX_SINK_STOVE_DISTANCE_MM = 2700f
    const val CRITICAL_SINK_STOVE_DISTANCE_MM = 3200f
    const val MIN_SINK_STOVE_LANDING_GAP_MM = 400f

    const val MIN_WORK_TRIANGLE_PERIMETER_MM = 3400f
    const val MAX_WORK_TRIANGLE_PERIMETER_MM = 7900f

    const val MIN_CRITICAL_DOOR_SWING_MM = 650f
    const val MIN_APPLIANCE_DOOR_SWING_MM = 750f
    const val MIN_ERGONOMIC_WALKWAY_MM = 850f
    const val MIN_UPPER_CABINET_CLEARANCE_MM = 500f

    const val MAX_DISHWASHER_TO_SINK_GAP_MM = 1200f
    const val MIN_HOOD_ABOVE_COUNTER_MM = 600f
    const val MAX_HOOD_ABOVE_COUNTER_MM = 880f

    private val SINK_TYPES: Set<KitchenElementType> = setOf(
        KitchenElementType.BASE_SINK_UNIT,
        KitchenElementType.BASE_SINK_VENTED_100
    )

    private val PRIMARY_STOVE_TYPES: Set<KitchenElementType> = setOf(
        KitchenElementType.COOKTOP_INDUCTION,
        KitchenElementType.COOKTOP_GAS_90,
        KitchenElementType.BASE_UNDER_OVEN
    )

    private val SECONDARY_OVEN_TYPES: Set<KitchenElementType> = setOf(
        KitchenElementType.BUILTIN_OVEN,
        KitchenElementType.TALL_OVEN_TOWER
    )

    private val FRIDGE_TYPES: Set<KitchenElementType> = setOf(
        KitchenElementType.REFRIGERATOR_FROST,
        KitchenElementType.REFRIGERATOR_FRENCH_DOOR,
        KitchenElementType.TALL_FRIDGE_HOUSING
    )

    /**
     * Runs all real-time ergonomic and geometric checks on a [KitchenProject].
     */
    fun validateProject(project: KitchenProject): ErgonomicValidationReport {
        return validate(project.room, project.elements)
    }

    /**
     * Runs all real-time ergonomic and geometric checks on [room] and [elements].
     */
    fun validate(
        room: RoomStructure,
        elements: List<KitchenElement>
    ): ErgonomicValidationReport {
        val visibleElements = elements.filter { it.isVisible }
        if (visibleElements.isEmpty()) {
            return ErgonomicValidationReport()
        }

        val issues = mutableListOf<ErgonomicIssue>()

        // 1. Evaluate Sink-to-Stove distance & Kitchen Work Triangle
        val workTriangle = evaluateWorkTriangleAndSinkStove(visibleElements, issues)

        // 2. Evaluate Cabinet Door Swing & Walkway Clearances
        val doorClearanceZones = evaluateDoorClearances(room, visibleElements, issues)

        // 3. Evaluate Dishwasher-to-Sink Distance
        evaluateDishwasherProximity(visibleElements, issues)

        // 4. Evaluate Range Hood Mounting Height
        evaluateHoodClearance(visibleElements, issues)

        // 5. Evaluate Physical Collisions & Out-of-Bounds Placement
        evaluatePhysicalCollisions(room, visibleElements, issues)

        // Deduplicate & sort issues by severity (ERROR first, then WARNING, then INFO)
        val sortedIssues = issues.sortedBy { it.severity.ordinal }

        val errorCount = sortedIssues.count { it.severity == ValidationSeverity.ERROR }
        val warningCount = sortedIssues.count { it.severity == ValidationSeverity.WARNING }
        val infoCount = sortedIssues.count { it.severity == ValidationSeverity.INFO }

        val totalPenalty = sortedIssues.sumOf { it.severity.penaltyPoints }
        val score = (100 - totalPenalty).coerceIn(0, 100)

        return ErgonomicValidationReport(
            score = score,
            issues = sortedIssues,
            workTriangle = workTriangle,
            doorClearanceZones = doorClearanceZones,
            errorCount = errorCount,
            warningCount = warningCount,
            infoCount = infoCount,
            isCompliant = errorCount == 0 && warningCount == 0
        )
    }

    /**
     * Evaluates Sink-to-Stove proximity ('sink too far from stove' / 'sink too close to stove')
     * and the 3-point Kitchen Work Triangle (Sink – Stove – Fridge).
     */
    private fun evaluateWorkTriangleAndSinkStove(
        elements: List<KitchenElement>,
        issues: MutableList<ErgonomicIssue>
    ): WorkTriangleMetrics? {
        val sinks = elements.filter { it.type in SINK_TYPES }
        val stoves = elements.filter { it.type in PRIMARY_STOVE_TYPES }
            .ifEmpty { elements.filter { it.type in SECONDARY_OVEN_TYPES } }
        val fridges = elements.filter { it.type in FRIDGE_TYPES }

        val sink = sinks.firstOrNull() ?: return null
        val stove = stoves.minByOrNull { horizontalCenterDistanceMm(sink, it) } ?: return null
        val fridge = fridges.minByOrNull { horizontalCenterDistanceMm(sink, it) }

        val sinkToStoveCenterMm = horizontalCenterDistanceMm(sink, stove)
        val sinkToStoveEdgeGapMm = horizontalEdgeGapMm(sink, stove)

        var isSinkStoveOptimal = true

        // Check 1A: Sink too far from stove (> 2700 mm)
        if (sinkToStoveCenterMm > MAX_SINK_STOVE_DISTANCE_MM) {
            isSinkStoveOptimal = false
            val isCritical = sinkToStoveCenterMm > CRITICAL_SINK_STOVE_DISTANCE_MM
            val severity = if (isCritical) ValidationSeverity.ERROR else ValidationSeverity.WARNING
            val distCm = (sinkToStoveCenterMm / 10f).roundToInt()
            val maxCm = (MAX_SINK_STOVE_DISTANCE_MM / 10f).roundToInt()

            issues.add(
                ErgonomicIssue(
                    id = "sink_far_stove_${sink.id}_${stove.id}",
                    code = ErgonomicRuleCode.SINK_TOO_FAR_FROM_STOVE,
                    severity = severity,
                    titleAr = "الحوض بعيد جداً عن الموقد ($distCm سم)",
                    titleEn = "Sink too far from stove (${sinkToStoveCenterMm.roundToInt()} mm)",
                    descriptionAr = "المسافة بين الحوض (${sink.name}) والموقد (${stove.name}) تبلغ ${sinkToStoveCenterMm.roundToInt()} ملم ($distCm سم)، وهي تتجاوز الحد الإرجونومي الأقصى ($maxCm سم) مما يرهق الحركة أثناء الطبخ وتصفية المياه.",
                    descriptionEn = "Sink too far from stove: distance between ${sink.name} and ${stove.name} is ${sinkToStoveCenterMm.roundToInt()} mm, exceeding the ${MAX_SINK_STOVE_DISTANCE_MM.roundToInt()} mm ergonomic standard.",
                    recommendationAr = "قرّب وحدة الحوض أو الموقد لتكون المسافة بينهما بين 120 سم و270 سم.",
                    measuredValueMm = sinkToStoveCenterMm,
                    standardRangeLabel = "1200 – 2700 mm",
                    affectedElementIds = listOf(sink.id, stove.id),
                    markerPositionMm = midpointFloor(sink.position, stove.position)
                )
            )
        } else if (sinkToStoveEdgeGapMm < MIN_SINK_STOVE_LANDING_GAP_MM) {
            // Check 1B: Sink too close to stove without counter prep landing zone (< 400 mm edge gap)
            isSinkStoveOptimal = false
            val gapCm = (sinkToStoveEdgeGapMm / 10f).roundToInt()
            issues.add(
                ErgonomicIssue(
                    id = "sink_close_stove_${sink.id}_${stove.id}",
                    code = ErgonomicRuleCode.SINK_TOO_CLOSE_TO_STOVE,
                    severity = ValidationSeverity.WARNING,
                    titleAr = "الحوض ملاصق للموقد بدون سطح تحضير كافٍ ($gapCm سم)",
                    titleEn = "Sink too close to stove (${sinkToStoveEdgeGapMm.roundToInt()} mm landing gap)",
                    descriptionAr = "المسافة الفاصلة بين حافة الحوض وحافة الموقد ${sinkToStoveEdgeGapMm.roundToInt()} ملم فقط. يُنصح بترك خزانة تحضير لا تقل عن 40 إلى 60 سم لفصل الماء عن الحرارة.",
                    descriptionEn = "Insufficient counter landing space (${sinkToStoveEdgeGapMm.roundToInt()} mm) between sink and stove; minimum 400–600 mm is recommended for safety and prep.",
                    recommendationAr = "أضف خزانة تحضير أو وحدة أدراج بعرض 60 سم على الأقل بين الحوض والموقد.",
                    measuredValueMm = sinkToStoveEdgeGapMm,
                    standardRangeLabel = "≥ 400 mm edge gap",
                    affectedElementIds = listOf(sink.id, stove.id),
                    markerPositionMm = midpointFloor(sink.position, stove.position)
                )
            )
        }

        var sinkToFridgeMm: Float? = null
        var stoveToFridgeMm: Float? = null
        var totalPerimeterMm: Float? = null
        var isTriangleOptimal = isSinkStoveOptimal

        if (fridge != null) {
            sinkToFridgeMm = horizontalCenterDistanceMm(sink, fridge)
            stoveToFridgeMm = horizontalCenterDistanceMm(stove, fridge)
            val perimeter = sinkToStoveCenterMm + sinkToFridgeMm + stoveToFridgeMm
            totalPerimeterMm = perimeter

            if (perimeter > MAX_WORK_TRIANGLE_PERIMETER_MM || perimeter < MIN_WORK_TRIANGLE_PERIMETER_MM) {
                isTriangleOptimal = false
                val perimMeters = String.format(java.util.Locale.US, "%.2f", perimeter / 1000f)
                val isTooLarge = perimeter > MAX_WORK_TRIANGLE_PERIMETER_MM
                issues.add(
                    ErgonomicIssue(
                        id = "work_triangle_perimeter_${sink.id}_${stove.id}_${fridge.id}",
                        code = ErgonomicRuleCode.WORK_TRIANGLE_PERIMETER,
                        severity = ValidationSeverity.INFO,
                        titleAr = if (isTooLarge) "محيط مثلث العمل كبير ($perimMeters م)" else "محيط مثلث العمل ضيق ($perimMeters م)",
                        titleEn = "Work triangle perimeter (${perimeter.roundToInt()} mm) outside 3400–7900 mm standard",
                        descriptionAr = "مجموع أضلاع مثلث العمل (الحوض – الموقد – الثلاجة) يبلغ ${perimeter.roundToInt()} ملم ($perimMeters متر). النطاق القياسي العالمي يتراوح بين 3.4 و 7.9 متر.",
                        descriptionEn = "Total Kitchen Work Triangle perimeter is ${perimeter.roundToInt()} mm. Optimal NKBA range is 3400 mm to 7900 mm.",
                        recommendationAr = "أعد توزيع مواقع الثلاجة والحوض والموقد لتقليل المسافات المهدرة.",
                        measuredValueMm = perimeter,
                        standardRangeLabel = "3400 – 7900 mm",
                        affectedElementIds = listOf(sink.id, stove.id, fridge.id),
                        markerPositionMm = Vector3(
                            (sink.position.x + stove.position.x + fridge.position.x) / 3f,
                            0f,
                            (sink.position.z + stove.position.z + fridge.position.z) / 3f
                        )
                    )
                )
            }
        }

        return WorkTriangleMetrics(
            sinkElementId = sink.id,
            stoveElementId = stove.id,
            fridgeElementId = fridge?.id,
            sinkPositionMm = sink.position,
            stovePositionMm = stove.position,
            fridgePositionMm = fridge?.position,
            sinkToStoveCenterMm = sinkToStoveCenterMm,
            sinkToStoveEdgeGapMm = sinkToStoveEdgeGapMm,
            sinkToFridgeCenterMm = sinkToFridgeMm,
            stoveToFridgeCenterMm = stoveToFridgeMm,
            totalPerimeterMm = totalPerimeterMm,
            isSinkStoveOptimal = isSinkStoveOptimal,
            isTriangleOptimal = isTriangleOptimal
        )
    }

    /**
     * Evaluates front door-swing & walkway clearance in front of every cabinet and appliance
     * ('insufficient clearance for cabinet doors').
     */
    private fun evaluateDoorClearances(
        room: RoomStructure,
        elements: List<KitchenElement>,
        issues: MutableList<ErgonomicIssue>
    ): List<DoorClearanceZone> {
        val zones = mutableListOf<DoorClearanceZone>()
        val flaggedPairKeys = mutableSetOf<String>()

        for (elem in elements) {
            if (!hasFrontOpeningDoors(elem.type)) continue

            val obb = elem.getOBB()
            val frontNormal = obb.axisZ // Local +Z points out of the cabinet front doors
            val frontCenter = obb.center + (frontNormal * obb.halfExtents.z)

            val isElevatedWallUnit = elem.position.y >= 1100f
            val isApplianceOrTall = elem.type in FRIDGE_TYPES ||
                    elem.type == KitchenElementType.DISHWASHER_INTEGRATED ||
                    elem.type == KitchenElementType.WASHING_MACHINE_FRONT ||
                    elem.type == KitchenElementType.BUILTIN_OVEN ||
                    elem.type == KitchenElementType.BASE_UNDER_OVEN ||
                    elem.type == KitchenElementType.TALL_OVEN_TOWER

            val requiredDoorSwingMm = when {
                isElevatedWallUnit -> MIN_UPPER_CABINET_CLEARANCE_MM
                isApplianceOrTall -> MIN_APPLIANCE_DOOR_SWING_MM
                else -> MIN_CRITICAL_DOOR_SWING_MM
            }

            val recommendedWalkwayMm = if (isElevatedWallUnit) {
                MIN_UPPER_CABINET_CLEARANCE_MM
            } else {
                MIN_ERGONOMIC_WALKWAY_MM
            }

            // 1. Calculate distance from front face to the room's opposing wall along frontNormal
            var minAvailableMm = distanceToWallAlongRay(frontCenter, frontNormal, room)
            var blockingName: String? = "جدار الغرفة المقابل / Opposing Wall"

            // 2. Check all other elements that lie in the front door-opening corridor of `elem`
            val elemBottomY = obb.center.y - obb.halfExtents.y
            val elemTopY = obb.center.y + obb.halfExtents.y

            // Inset lateral corridor slightly so side-by-side cabinets never trigger false positives
            val corridorHalfWidth = max(100f, obb.halfExtents.x - 50f)

            for (other in elements) {
                if (other.id == elem.id) continue
                if (isCountertopInsertOn(other, elem)) continue
                if (other.type == KitchenElementType.CEILING_CHANDELIER_MODERN ||
                    other.type == KitchenElementType.CEILING_LINEAR_LED_PANEL ||
                    other.type == KitchenElementType.RANGE_HOOD_BOX
                ) continue
                // Elevated wall/bridge cabinets (>= 1350mm) do not block floor base cabinets
                if (elem.position.y < 500f && other.position.y >= 1350f) continue

                val otherOBB = other.getOBB()
                val otherBottomY = otherOBB.center.y - otherOBB.halfExtents.y
                val otherTopY = otherOBB.center.y + otherOBB.halfExtents.y

                // Require vertical overlap (> 120mm) in the main door-opening band
                val effectiveElemTopY = min(elemTopY, elemBottomY + 2050f)
                val verticalOverlap = min(effectiveElemTopY, otherTopY) - max(elemBottomY, otherBottomY)
                if (verticalOverlap <= 120f) continue

                // Project `other` floor corners into `elem`'s local frame (origin = obb.center)
                var otherMinLocalX = Float.MAX_VALUE
                var otherMaxLocalX = -Float.MAX_VALUE
                var otherMinLocalZ = Float.MAX_VALUE
                var otherMaxLocalZ = -Float.MAX_VALUE

                for (corner in otherOBB.getBottomCorners()) {
                    val rel = corner - obb.center
                    val lx = rel.dot(obb.axisX)
                    val lz = rel.dot(obb.axisZ)
                    otherMinLocalX = min(otherMinLocalX, lx)
                    otherMaxLocalX = max(otherMaxLocalX, lx)
                    otherMinLocalZ = min(otherMinLocalZ, lz)
                    otherMaxLocalZ = max(otherMaxLocalZ, lz)
                }

                // Must be in front of `elem`'s front face (with small tolerance for minor overlap)
                if (otherMaxLocalZ <= obb.halfExtents.z + 15f) continue

                // Lateral overlap with `elem`'s door swing corridor
                val lateralOverlap = min(otherMaxLocalX, corridorHalfWidth) - max(otherMinLocalX, -corridorHalfWidth)
                if (lateralOverlap <= 80f) continue

                // Special handling for perpendicular L-corners:
                // When an L-corner cabinet (BASE_CORNER_L or WALL_CORNER_L) connects to a perpendicular run,
                // one half of its width is the blind return wall connection.
                val dotNormals = obb.axisZ.dot(otherOBB.axisZ)
                val isPerpendicular = abs(dotNormals) < 0.25f
                if (isPerpendicular && (elem.type == KitchenElementType.BASE_CORNER_L ||
                            other.type == KitchenElementType.BASE_CORNER_L ||
                            elem.type == KitchenElementType.BASE_MAGIC_CORNER ||
                            other.type == KitchenElementType.BASE_MAGIC_CORNER ||
                            elem.type == KitchenElementType.WALL_CORNER_L ||
                            other.type == KitchenElementType.WALL_CORNER_L)
                ) {
                    val forwardGap = max(0f, otherMinLocalZ - obb.halfExtents.z)
                    if (forwardGap <= 350f) {
                        // Normal L-corner junction with a dedicated corner unit
                        continue
                    }
                }

                val forwardClearanceMm = max(0f, otherMinLocalZ - obb.halfExtents.z)
                if (forwardClearanceMm < minAvailableMm) {
                    minAvailableMm = forwardClearanceMm
                    blockingName = other.name
                }
            }

            val isCriticalSwingBlocked = minAvailableMm < requiredDoorSwingMm
            val isWalkwayNarrow = minAvailableMm < recommendedWalkwayMm
            val isInsufficient = isCriticalSwingBlocked || isWalkwayNarrow

            zones.add(
                DoorClearanceZone(
                    elementId = elem.id,
                    elementName = elem.name,
                    frontOriginMm = frontCenter,
                    frontNormal = frontNormal,
                    widthMm = elem.dimensions.x,
                    requiredDoorSwingMm = requiredDoorSwingMm,
                    recommendedWalkwayMm = recommendedWalkwayMm,
                    availableClearanceMm = minAvailableMm,
                    isInsufficient = isInsufficient,
                    isCriticalSwingBlocked = isCriticalSwingBlocked,
                    blockingTargetName = if (isInsufficient) blockingName else null
                )
            )

            if (isInsufficient) {
                val pairKey = "${elem.id}_${blockingName ?: "wall"}"
                if (flaggedPairKeys.add(pairKey)) {
                    val availCm = (minAvailableMm / 10f).roundToInt()
                    val reqMm = if (isCriticalSwingBlocked) requiredDoorSwingMm else recommendedWalkwayMm
                    val reqCm = (reqMm / 10f).roundToInt()
                    val severity = if (isCriticalSwingBlocked) {
                        ValidationSeverity.ERROR
                    } else {
                        ValidationSeverity.WARNING
                    }

                    val markerPos = frontCenter + (frontNormal * (minAvailableMm * 0.5f).coerceAtMost(450f))

                    issues.add(
                        ErgonomicIssue(
                            id = "door_clearance_${elem.id}",
                            code = ErgonomicRuleCode.INSUFFICIENT_DOOR_CLEARANCE,
                            severity = severity,
                            titleAr = "مسافة غير كافية لفتح باب «${elem.name}» ($availCm سم متاحة)",
                            titleEn = "Insufficient clearance for cabinet doors: ${elem.name} (${minAvailableMm.roundToInt()} mm)",
                            descriptionAr = "المسافة الفارغة أمام «${elem.name}» تبلغ ${minAvailableMm.roundToInt()} ملم ($availCm سم) فقط باتجاه (${blockingName ?: "العائق المقابل"})، بينما الحد الأدنى لفتح الأبواب والممر الإرجونومي هو $reqCm سم (${reqMm.roundToInt()} ملم).",
                            descriptionEn = "Insufficient clearance for cabinet doors in front of ${elem.name}: only ${minAvailableMm.roundToInt()} mm available before ${blockingName ?: "obstacle"} (minimum ${reqMm.roundToInt()} mm required).",
                            recommendationAr = "أبعد العائق المقابل أو انقل الخزانة لتوفير خلوص أمامي لا يقل عن $reqCm سم لفتح الضلف والأدراج بحرية.",
                            measuredValueMm = minAvailableMm,
                            standardRangeLabel = "≥ ${reqMm.roundToInt()} mm",
                            affectedElementIds = listOf(elem.id),
                            markerPositionMm = Vector3(markerPos.x, 0f, markerPos.z)
                        )
                    )
                }
            }
        }

        return zones
    }

    /**
     * Evaluates proximity between Dishwasher and Sink (recommended <= 1200mm edge-to-edge).
     */
    private fun evaluateDishwasherProximity(
        elements: List<KitchenElement>,
        issues: MutableList<ErgonomicIssue>
    ) {
        val dishwashers = elements.filter { it.type == KitchenElementType.DISHWASHER_INTEGRATED }
        val sinks = elements.filter { it.type in SINK_TYPES }
        if (dishwashers.isEmpty() || sinks.isEmpty()) return

        for (dw in dishwashers) {
            val nearestSink = sinks.minByOrNull { horizontalEdgeGapMm(dw, it) } ?: continue
            val edgeGapMm = horizontalEdgeGapMm(dw, nearestSink)
            if (edgeGapMm > MAX_DISHWASHER_TO_SINK_GAP_MM) {
                val gapCm = (edgeGapMm / 10f).roundToInt()
                issues.add(
                    ErgonomicIssue(
                        id = "dishwasher_sink_${dw.id}_${nearestSink.id}",
                        code = ErgonomicRuleCode.DISHWASHER_FAR_FROM_SINK,
                        severity = ValidationSeverity.WARNING,
                        titleAr = "غسالة الصحون بعيدة عن الحوض ($gapCm سم)",
                        titleEn = "Dishwasher too far from sink (${edgeGapMm.roundToInt()} mm)",
                        descriptionAr = "تبعد غسالة الصحون (${dw.name}) مسافة ${edgeGapMm.roundToInt()} ملم عن الحوض (${nearestSink.name})، مما يصعّب شطف الأطباق وتمديد خراطيم الصرف والمياه.",
                        descriptionEn = "Dishwasher is ${edgeGapMm.roundToInt()} mm away from the nearest sink (recommended ≤ 1200 mm for plumbing and ergonomic loading).",
                        recommendationAr = "ضع غسالة الصحون مجاورة مباشرة لوحدة الحوض (مسافة أقل من 120 سم).",
                        measuredValueMm = edgeGapMm,
                        standardRangeLabel = "≤ 1200 mm",
                        affectedElementIds = listOf(dw.id, nearestSink.id),
                        markerPositionMm = midpointFloor(dw.position, nearestSink.position)
                    )
                )
            }
        }
    }

    /**
     * Evaluates vertical clearance between Range Hood and Cooktop/Oven counter.
     */
    private fun evaluateHoodClearance(
        elements: List<KitchenElement>,
        issues: MutableList<ErgonomicIssue>
    ) {
        val hoods = elements.filter { it.type == KitchenElementType.RANGE_HOOD_BOX }
        val stoves = elements.filter { it.type in PRIMARY_STOVE_TYPES }
        if (hoods.isEmpty() || stoves.isEmpty()) return

        for (hood in hoods) {
            val stove = stoves.minByOrNull { horizontalCenterDistanceMm(hood, it) } ?: continue
            val horizDist = horizontalCenterDistanceMm(hood, stove)
            if (horizDist <= 900f) {
                val counterTopY = if (stove.position.y > 400f) {
                    stove.position.y
                } else {
                    stove.position.y + stove.dimensions.y
                }
                val verticalGapMm = hood.position.y - counterTopY
                if (verticalGapMm < MIN_HOOD_ABOVE_COUNTER_MM || verticalGapMm > MAX_HOOD_ABOVE_COUNTER_MM) {
                    val gapCm = (verticalGapMm / 10f).roundToInt()
                    issues.add(
                        ErgonomicIssue(
                            id = "hood_height_${hood.id}_${stove.id}",
                            code = ErgonomicRuleCode.HOOD_CLEARANCE_HEIGHT,
                            severity = ValidationSeverity.INFO,
                            titleAr = "ارتفاع الشفاط عن سطح الموقد غير قياسي ($gapCm سم)",
                            titleEn = "Range hood vertical clearance (${verticalGapMm.roundToInt()} mm) outside 600–880 mm",
                            descriptionAr = "المسافة الرأسية بين سطح الموقد وأسفل الشفاط تبلغ ${verticalGapMm.roundToInt()} ملم ($gapCm سم). الارتفاع القياسي للشفاط يتراوح بين 65 و 85 سم فوق سطح الطهي.",
                            descriptionEn = "Vertical distance between cooking surface and range hood is ${verticalGapMm.roundToInt()} mm (recommended 600–880 mm).",
                            recommendationAr = "اضبط ارتفاع الشفاط ليكون على ارتفاع 150 إلى 170 سم من الأرضية (65–85 سم فوق الرخامة).",
                            measuredValueMm = verticalGapMm,
                            standardRangeLabel = "600 – 880 mm",
                            affectedElementIds = listOf(hood.id, stove.id),
                            markerPositionMm = Vector3(hood.position.x, 0f, hood.position.z)
                        )
                    )
                }
            }
        }
    }

    /**
     * Checks physical cabinet overlaps and room boundary breaches using [CollisionDetector].
     */
    private fun evaluatePhysicalCollisions(
        room: RoomStructure,
        elements: List<KitchenElement>,
        issues: MutableList<ErgonomicIssue>
    ) {
        val reportedCollisions = mutableSetOf<String>()

        for (elem in elements) {
            val result = CollisionDetector.checkCollision(elem, elements, room, toleranceMm = 5.0f)
            if (result.isOutOfBounds) {
                issues.add(
                    ErgonomicIssue(
                        id = "oob_${elem.id}",
                        code = ErgonomicRuleCode.ELEMENT_OVERLAP_COLLISION,
                        severity = ValidationSeverity.ERROR,
                        titleAr = "تجاوز «${elem.name}» لحدود جدران الغرفة",
                        titleEn = "${elem.name} exceeds room wall boundaries",
                        descriptionAr = "الوحدة «${elem.name}» تتجاوز أبعاد الغرفة (${room.widthMm.roundToInt()}×${room.depthMm.roundToInt()} ملم).",
                        descriptionEn = "${elem.name} extends outside the room boundaries.",
                        recommendationAr = "اسحب الوحدة للداخل لتكون ضمن حدود الجدران الأربعة.",
                        measuredValueMm = 0f,
                        standardRangeLabel = "Within Room Bounds",
                        affectedElementIds = listOf(elem.id),
                        markerPositionMm = Vector3(elem.position.x, 0f, elem.position.z)
                    )
                )
            }

            for (otherId in result.collidingElementIds) {
                val other = elements.find { it.id == otherId } ?: continue
                if (isCountertopInsertOn(elem, other) || isCountertopInsertOn(other, elem)) continue

                val key = if (elem.id < otherId) "${elem.id}_$otherId" else "${otherId}_${elem.id}"
                if (reportedCollisions.add(key)) {
                    issues.add(
                        ErgonomicIssue(
                            id = "collision_$key",
                            code = ErgonomicRuleCode.ELEMENT_OVERLAP_COLLISION,
                            severity = ValidationSeverity.ERROR,
                            titleAr = "تداخل هندسي بين «${elem.name}» و«${other.name}»",
                            titleEn = "Physical collision between ${elem.name} and ${other.name}",
                            descriptionAr = "يوجد تقاطع في الحيز الهندسي بين «${elem.name}» و«${other.name}» يمنع التركيب الواقعي.",
                            descriptionEn = "Bounding boxes of ${elem.name} and ${other.name} overlap.",
                            recommendationAr = "حرّك إحدى الوحدتين بمحاذاة الأخرى دون تداخل.",
                            measuredValueMm = 0f,
                            standardRangeLabel = "0 mm overlap",
                            affectedElementIds = listOf(elem.id, other.id),
                            markerPositionMm = midpointFloor(elem.position, other.position)
                        )
                    )
                }
            }
        }
    }

    /**
     * Determines whether an element type has front-opening doors, drawers, or pull-outs
     * that require front swing and user access clearance.
     */
    fun hasFrontOpeningDoors(type: KitchenElementType): Boolean {
        return when (type) {
            KitchenElementType.COOKTOP_INDUCTION,
            KitchenElementType.COOKTOP_GAS_90,
            KitchenElementType.RANGE_HOOD_BOX,
            KitchenElementType.WALL_OPEN_SHELF,
            KitchenElementType.TALL_NICHE_DISPLAY_COLUMN,
            KitchenElementType.CEILING_CHANDELIER_MODERN,
            KitchenElementType.CEILING_LINEAR_LED_PANEL -> false
            else -> true
        }
    }

    private fun isCountertopInsertOn(a: KitchenElement, b: KitchenElement): Boolean {
        val insertTypes = setOf(
            KitchenElementType.COOKTOP_INDUCTION,
            KitchenElementType.COOKTOP_GAS_90
        )
        return a.type in insertTypes || b.type in insertTypes
    }

    private fun horizontalCenterDistanceMm(a: KitchenElement, b: KitchenElement): Float {
        val dx = a.position.x - b.position.x
        val dz = a.position.z - b.position.z
        return hypot(dx, dz)
    }

    private fun horizontalEdgeGapMm(a: KitchenElement, b: KitchenElement): Float {
        val aabbA = a.getAABB()
        val aabbB = b.getAABB()
        val gapX = max(0f, max(aabbA.min.x - aabbB.max.x, aabbB.min.x - aabbA.max.x))
        val gapZ = max(0f, max(aabbA.min.z - aabbB.max.z, aabbB.min.z - aabbA.max.z))
        return hypot(gapX, gapZ)
    }

    private fun midpointFloor(a: Vector3, b: Vector3): Vector3 {
        return Vector3((a.x + b.x) * 0.5f, 0f, (a.z + b.z) * 0.5f)
    }

    /**
     * Computes the distance from [origin] along unit horizontal vector [dir] to the inner walls of [room].
     */
    private fun distanceToWallAlongRay(origin: Vector3, dir: Vector3, room: RoomStructure): Float {
        var minT = Float.MAX_VALUE
        if (dir.x > 1e-4f) {
            val tEast = (room.widthMm - origin.x) / dir.x
            if (tEast >= -50f) minT = min(minT, max(0f, tEast))
        } else if (dir.x < -1e-4f) {
            val tWest = (0f - origin.x) / dir.x
            if (tWest >= -50f) minT = min(minT, max(0f, tWest))
        }

        if (dir.z > 1e-4f) {
            val tSouth = (room.depthMm - origin.z) / dir.z
            if (tSouth >= -50f) minT = min(minT, max(0f, tSouth))
        } else if (dir.z < -1e-4f) {
            val tNorth = (0f - origin.z) / dir.z
            if (tNorth >= -50f) minT = min(minT, max(0f, tNorth))
        }

        return if (minT == Float.MAX_VALUE) max(room.widthMm, room.depthMm) else minT
    }
}
