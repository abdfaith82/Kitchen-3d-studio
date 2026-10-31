package com.example.core.command

/**
 * Categories of undoable 2D/3D editor commands for history filtering and visual badges.
 */
enum class CommandCategory(val labelAr: String) {
    FURNITURE_PLACEMENT("وضع خزانة"),
    FLOORPLAN_TRANSFORM("تحريك / تدوير 2D"),
    LAYOUT_MODIFICATION("تعديل مخطط الغرفة"),
    ELEMENT_DELETION("حذف عنصر"),
    GROUPING("تجميع العناصر"),
    MATERIAL_FINISH("تعديل الخامات")
}

/**
 * Command interface adhering to GoF Command Pattern for lossless Undo/Redo actions
 * across the 2D floor plan drawing component and 3D viewport.
 */
interface Command {
    val title: String
    val description: String
    val category: CommandCategory
        get() = CommandCategory.FLOORPLAN_TRANSFORM

    fun execute()
    fun undo()
    fun redo() {
        execute()
    }
}
