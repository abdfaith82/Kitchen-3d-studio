package com.example.core.geometry

import kotlin.math.*

enum class ApplianceFunctionType {
    SINK,
    STOVE_OR_COOKTOP,
    REFRIGERATOR,
    DISHWASHER,
    OVEN,
    MICROWAVE,
    GENERAL_CABINET
}

data class FunctionalApplianceNode(
    val id: String,
    val type: ApplianceFunctionType,
    val position: Vector3,
    val boundingBox: BoundingBox3D
)

enum class ErgonomicIssueSeverity {
    INFO,
    WARNING,
    CRITICAL
}

data class ErgonomicDiagnostic(
    val code: String,
    val title: String,
    val description: String,
    val severity: ErgonomicIssueSeverity,
    val affectedApplianceIds: List<String> = emptyList()
)

object ErgonomicValidationEngine {

    const val WORK_TRIANGLE_MIN_PERIMETER = 4.0f
    const val WORK_TRIANGLE_MAX_PERIMETER = 7.9f
    const val WORK_TRIANGLE_LEG_MIN = 1.2f
    const val WORK_TRIANGLE_LEG_MAX = 2.7f
    const val MIN_WALKWAY_CLEARANCE = 0.90f
    const val RECOMMENDED_WALKWAY_CLEARANCE = 1.05f
    const val MIN_SINK_TO_COOKTOP_DISTANCE = 0.60f
    const val MIN_SAFETY_CLEARANCE_STOVE_TO_TALL = 0.30f

    fun validateWorkTriangle(nodes: List<FunctionalApplianceNode>): List<ErgonomicDiagnostic> {
        val diagnostics = mutableListOf<ErgonomicDiagnostic>()

        val sink = nodes.firstOrNull { it.type == ApplianceFunctionType.SINK }
        val cooktop = nodes.firstOrNull { it.type == ApplianceFunctionType.STOVE_OR_COOKTOP }
        val fridge = nodes.firstOrNull { it.type == ApplianceFunctionType.REFRIGERATOR }

        if (sink == null || cooktop == null || fridge == null) {
            val missing = mutableListOf<String>()
            if (sink == null) missing.add("حوض الغسيل (Sink)")
            if (cooktop == null) missing.add("الموقد / الفرن (Cooktop)")
            if (fridge == null) missing.add("الثلاجة (Refrigerator)")

            diagnostics.add(
                ErgonomicDiagnostic(
                    code = "ERG_TRIANGLE_INCOMPLETE",
                    title = "مثلث الحركة الذهبي غير مكتمل",
                    description = "المطبخ يفتقر إلى أحد أركان مثلث العمل الأساسية: ${missing.joinToString(", ")}.",
                    severity = ErgonomicIssueSeverity.INFO
                )
            )
            return diagnostics
        }

        val dSinkToCooktop = sink.position.distanceTo2D(cooktop.position)
        val dCooktopToFridge = cooktop.position.distanceTo2D(fridge.position)
        val dFridgeToSink = fridge.position.distanceTo2D(sink.position)

        val totalPerimeter = dSinkToCooktop + dCooktopToFridge + dFridgeToSink

        if (totalPerimeter < WORK_TRIANGLE_MIN_PERIMETER) {
            diagnostics.add(
                ErgonomicDiagnostic(
                    code = "ERG_TRIANGLE_TOO_TIGHT",
                    title = "محيط مثلث العمل ضيق جداً",
                    description = "محيط الحركة (${"%.2f".format(totalPerimeter)}m) أقل من الحد الأدنى الموصى به (${WORK_TRIANGLE_MIN_PERIMETER}m)، مما يسبب ازدحاماً وصعوبة في التحضير.",
                    severity = ErgonomicIssueSeverity.WARNING,
                    affectedApplianceIds = listOf(sink.id, cooktop.id, fridge.id)
                )
            )
        } else if (totalPerimeter > WORK_TRIANGLE_MAX_PERIMETER) {
            diagnostics.add(
                ErgonomicDiagnostic(
                    code = "ERG_TRIANGLE_TOO_LARGE",
                    title = "محيط مثلث العمل متباعد ومرهق",
                    description = "محيط الحركة (${"%.2f".format(totalPerimeter)}m) يتجاوز الحد الأرغونومي (${WORK_TRIANGLE_MAX_PERIMETER}m)، مما يتطلب خطوات مشي إضافية ويزيد الإجهاد أثناء الطهي.",
                    severity = ErgonomicIssueSeverity.WARNING,
                    affectedApplianceIds = listOf(sink.id, cooktop.id, fridge.id)
                )
            )
        }

        if (dSinkToCooktop < MIN_SINK_TO_COOKTOP_DISTANCE) {
            diagnostics.add(
                ErgonomicDiagnostic(
                    code = "ERG_SINK_COOKTOP_COLLISION",
                    title = "المسافة بين الحوض والموقد صغيرة جداً",
                    description = "المسافة الحالية (${"%.2f".format(dSinkToCooktop)}m) أقل من 60 سم. يجب توفير مساحة تقطيع وعزل كافية بين مصدر الماء ومصدر النار لأسباب السلامة.",
                    severity = ErgonomicIssueSeverity.CRITICAL,
                    affectedApplianceIds = listOf(sink.id, cooktop.id)
                )
            )
        }

        val dishwasher = nodes.firstOrNull { it.type == ApplianceFunctionType.DISHWASHER }
        if (dishwasher != null) {
            val dSinkToDw = sink.position.distanceTo2D(dishwasher.position)
            if (dSinkToDw > 1.20f) {
                diagnostics.add(
                    ErgonomicDiagnostic(
                        code = "ERG_DISHWASHER_TOO_FAR",
                        title = "غسالة الصحون بعيدة عن الحوض",
                        description = "المسافة (${"%.2f".format(dSinkToDw)}m) تتجاوز المعتاد (أقل من 90 سم)، مما يصعّب شطف الأطباق وتمديدات السباكة المباشرة.",
                        severity = ErgonomicIssueSeverity.WARNING,
                        affectedApplianceIds = listOf(sink.id, dishwasher.id)
                    )
                )
            }
        }

        return diagnostics
    }

    fun validateWalkwayClearance(boxes: List<BoundingBox3D>): List<ErgonomicDiagnostic> {
        val diagnostics = mutableListOf<ErgonomicDiagnostic>()
        for (i in boxes.indices) {
            for (j in i + 1 until boxes.size) {
                val b1 = boxes[i]
                val b2 = boxes[j]
                val dist = DistanceCalculator.distanceBetweenBoxes(b1, b2)
                if (dist in 0.05f until MIN_WALKWAY_CLEARANCE) {
                    diagnostics.add(
                        ErgonomicDiagnostic(
                            code = "ERG_TIGHT_PASSAGEWAY",
                            title = "ممر حركة ضيق بين الخزائن",
                            description = "المسافة بين الوحدات تبلغ ${"%.2f".format(dist)}m وهي أقل من الحد الأدنى للممر (0.90m). قد يؤدي ذلك لصعوبة فتح الأدراج أو المرور المريح.",
                            severity = ErgonomicIssueSeverity.CRITICAL
                        )
                    )
                }
            }
        }
        return diagnostics
    }
}
