package com.example.core.command

import com.example.core.math.Vector3
import kotlin.math.roundToInt

/**
 * Command encapsulating 2D/3D Position, Rotation (Yaw), and Dimensions/Scale transformations
 * with 1mm precision.
 */
class TransformCommand(
    val elementId: String,
    val oldPosition: Vector3,
    val newPosition: Vector3,
    val oldRotationY: Float,
    val newRotationY: Float,
    val oldDimensions: Vector3,
    val newDimensions: Vector3,
    private val applyTransform: (elementId: String, pos: Vector3, rot: Float, dims: Vector3) -> Unit,
    val elementName: String = ""
) : Command {

    private val displayLabel: String = elementName.ifBlank { "العنصر" }

    override val category: CommandCategory = CommandCategory.FLOORPLAN_TRANSFORM

    override val title: String = when {
        oldDimensions != newDimensions -> "تغيير مقاس $displayLabel"
        oldRotationY != newRotationY && oldPosition == newPosition -> "تدوير $displayLabel (${newRotationY.roundToInt()}°)"
        else -> "تحريك $displayLabel"
    }

    override val description: String = buildString {
        append("X:${newPosition.x.roundToInt()} Z:${newPosition.z.roundToInt()} مم")
        if (oldRotationY != newRotationY) {
            append(" | زاوية: ${oldRotationY.roundToInt()}°←${newRotationY.roundToInt()}°")
        }
        if (oldDimensions != newDimensions) {
            append(" | ${newDimensions.x.roundToInt()}×${newDimensions.z.roundToInt()} مم")
        }
    }

    override fun execute() {
        applyTransform(elementId, newPosition, newRotationY, newDimensions)
    }

    override fun undo() {
        applyTransform(elementId, oldPosition, oldRotationY, oldDimensions)
    }
}
