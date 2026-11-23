package com.example.core.command

import com.example.domain.model.KitchenElement
import com.example.domain.model.KitchenGroup
import com.example.domain.model.RoomStructure
import com.example.domain.model.Wall
import kotlin.math.roundToInt

/**
 * Command for adding a kitchen element from the catalog or quick toolbar.
 */
class AddElementCommand(
    val element: KitchenElement,
    private val onAdd: (KitchenElement) -> Unit,
    private val onRemove: (String) -> Unit
) : Command {
    override val category: CommandCategory = CommandCategory.FURNITURE_PLACEMENT
    override val title: String = "إضافة ${element.name}"
    override val description: String =
        "الموقع: (${element.position.x.roundToInt()}, ${element.position.z.roundToInt()}) مم"

    override fun execute() {
        onAdd(element)
    }

    override fun undo() {
        onRemove(element.id)
    }
}

/**
 * Dedicated command for placing a furniture element directly onto the 2D Floor Plan canvas
 * at a 1mm-snapped coordinate, preserving and restoring the prior selection state on undo.
 */
class PlaceFurniture2DCommand(
    val element: KitchenElement,
    val previousSelectedIds: Set<String> = emptySet(),
    private val onPlace: (KitchenElement) -> Unit,
    private val onRevertPlacement: (elementId: String, previousSelection: Set<String>) -> Unit
) : Command {
    override val category: CommandCategory = CommandCategory.FURNITURE_PLACEMENT
    override val title: String = "وضع ${element.name} بالمخطط 2D"
    override val description: String =
        "X:${element.position.x.roundToInt()} مم، Z:${element.position.z.roundToInt()} مم (${element.dimensions.x.roundToInt()}×${element.dimensions.z.roundToInt()} مم)"

    override fun execute() {
        onPlace(element)
    }

    override fun undo() {
        onRevertPlacement(element.id, previousSelectedIds)
    }
}

/**
 * Command for deleting one or more elements and restoring them to their exact 1mm positions on undo.
 */
class DeleteElementsCommand(
    val elements: List<KitchenElement>,
    private val onRestore: (List<KitchenElement>) -> Unit,
    private val onDelete: (List<String>) -> Unit
) : Command {
    override val category: CommandCategory = CommandCategory.ELEMENT_DELETION
    override val title: String = if (elements.size == 1) {
        "حذف ${elements.first().name}"
    } else {
        "حذف عناصر (${elements.size})"
    }
    override val description: String = elements.joinToString("، ") {
        "${it.name} (${it.position.x.roundToInt()},${it.position.z.roundToInt()} مم)"
    }

    override fun execute() {
        onDelete(elements.map { it.id })
    }

    override fun undo() {
        onRestore(elements)
    }
}

/**
 * Command for grouping multiple selected elements.
 */
class GroupElementsCommand(
    val group: KitchenGroup,
    private val onGroup: (KitchenGroup) -> Unit,
    private val onUngroup: (String) -> Unit
) : Command {
    override val category: CommandCategory = CommandCategory.GROUPING
    override val title: String = "تجميع عناصر (${group.name})"
    override val description: String = "دمج ${group.elementIds.size} عناصر في مجموعة واحدة"

    override fun execute() {
        onGroup(group)
    }

    override fun undo() {
        onUngroup(group.id)
    }
}

/**
 * Command for ungrouping an existing group and restoring the group on undo.
 */
class UngroupElementsCommand(
    val groups: List<KitchenGroup>,
    private val onUngroupAll: (List<String>) -> Unit,
    private val onRestoreGroups: (List<KitchenGroup>) -> Unit
) : Command {
    override val category: CommandCategory = CommandCategory.GROUPING
    override val title: String = "فك تجميع (${groups.size})"
    override val description: String = groups.joinToString("، ") { it.name }

    override fun execute() {
        onUngroupAll(groups.map { it.id })
    }

    override fun undo() {
        onRestoreGroups(groups)
    }
}

/**
 * Command for modifying a single wall (openings, doors, windows, MEP utility points).
 */
class ModifyWallCommand(
    val oldWall: Wall,
    val newWall: Wall,
    private val onApply: (Wall) -> Unit,
    customTitle: String? = null,
    customDescription: String? = null
) : Command {
    override val category: CommandCategory = CommandCategory.LAYOUT_MODIFICATION
    override val title: String = customTitle ?: "تعديل جدار (${newWall.side.title})"
    override val description: String = customDescription
        ?: "فتحات: ${newWall.openings.size} | خدمات: ${newWall.utilityPoints.size} | طول: ${newWall.lengthMm.roundToInt()} مم"

    override fun execute() {
        onApply(newWall)
    }

    override fun undo() {
        onApply(oldWall)
    }
}

/**
 * Command encapsulating room structure & 2D floor plan layout modifications
 * (room dimensions, L-Shape / U-Shape / Rectangular layout shape, wall lengths, corner angles, wall thickness).
 */
class ModifyRoomLayoutCommand(
    val oldRoom: RoomStructure,
    val newRoom: RoomStructure,
    override val title: String = "تعديل تخطيط الغرفة 2D",
    override val description: String = "${newRoom.widthMm.roundToInt()}×${newRoom.depthMm.roundToInt()} مم (${newRoom.layoutShape.titleAr})",
    private val onApplyRoom: (RoomStructure) -> Unit
) : Command {
    override val category: CommandCategory = CommandCategory.LAYOUT_MODIFICATION

    override fun execute() {
        onApplyRoom(newRoom)
    }

    override fun undo() {
        onApplyRoom(oldRoom)
    }
}

/**
 * Command encapsulating multi-element 2D floor plan layout modifications
 * (e.g., multi-element 2D drag/nudge, ergonomic layout auto-fix, material/countertop batch updates).
 */
class BatchFloorPlanLayoutCommand(
    val beforeElements: List<KitchenElement>,
    val afterElements: List<KitchenElement>,
    val beforeSelection: Set<String> = emptySet(),
    val afterSelection: Set<String> = emptySet(),
    override val title: String,
    override val description: String,
    override val category: CommandCategory = CommandCategory.FLOORPLAN_TRANSFORM,
    private val onApplyElements: (elements: List<KitchenElement>, selectedIds: Set<String>) -> Unit
) : Command {

    override fun execute() {
        onApplyElements(afterElements, afterSelection)
    }

    override fun undo() {
        onApplyElements(beforeElements, beforeSelection)
    }
}
