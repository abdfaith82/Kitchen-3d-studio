package com.example.core.command

import com.example.core.geometry.CollisionDetector
import com.example.core.geometry.OrientedBoundingBox
import com.example.core.geometry.Vector3

class TransformCommand(
    private val targetEntity: CabinetEntityModel,
    private val oldPosition: Vector3,
    private val oldRotationY: Float,
    private val newPosition: Vector3,
    private val newRotationY: Float,
    private val allEntities: List<CabinetEntityModel>,
    private val preventCollisions: Boolean = true,
    private val onTransformApplied: (CabinetEntityModel) -> Unit
) : EditorCommand {

    override val description: String = "تحريك / تدوير وحدة ${targetEntity.name}"

    override fun execute(): Boolean {
        if (targetEntity.isLocked) return false

        if (preventCollisions) {
            val tentativeOBB = OrientedBoundingBox(
                center = Vector3(newPosition.x, newPosition.y + targetEntity.height * 0.5f, newPosition.z),
                halfExtents = Vector3(targetEntity.width * 0.5f, targetEntity.height * 0.5f, targetEntity.depth * 0.5f),
                rotationYDegrees = newRotationY
            )

            for (other in allEntities) {
                if (other.id == targetEntity.id) continue
                val otherOBB = other.toOBB()
                val collision = CollisionDetector.testOBBOBB(tentativeOBB, otherOBB)
                if (collision.hasCollision && collision.overlapDepth > 0.015f) {
                    return false
                }
            }
        }

        targetEntity.position = newPosition
        targetEntity.rotationY = newRotationY
        onTransformApplied(targetEntity)
        return true
    }

    override fun undo(): Boolean {
        targetEntity.position = oldPosition
        targetEntity.rotationY = oldRotationY
        onTransformApplied(targetEntity)
        return true
    }
}

class ResizeCabinetCommand(
    private val targetEntity: CabinetEntityModel,
    private val oldWidth: Float,
    private val oldHeight: Float,
    private val oldDepth: Float,
    private val newWidth: Float,
    private val newHeight: Float,
    private val newDepth: Float,
    private val onResized: (CabinetEntityModel) -> Unit
) : EditorCommand {

    override val description: String = "تعديل أبعاد وحدة ${targetEntity.name}"

    override fun execute(): Boolean {
        if (targetEntity.isLocked) return false
        targetEntity.width = newWidth
        targetEntity.height = newHeight
        targetEntity.depth = newDepth
        onResized(targetEntity)
        return true
    }

    override fun undo(): Boolean {
        targetEntity.width = oldWidth
        targetEntity.height = oldHeight
        targetEntity.depth = oldDepth
        onResized(targetEntity)
        return true
    }
}
