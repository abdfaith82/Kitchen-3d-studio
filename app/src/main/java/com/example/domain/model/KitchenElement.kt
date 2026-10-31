package com.example.domain.model

import com.example.core.math.AABB
import com.example.core.math.OBB
import com.example.core.math.Vector3

/**
 * Core kitchen cabinet or appliance entity.
 * Position (x, y, z) defines the center of the base contact footprint in millimeters.
 * Includes dynamic vertical linking (`linkedBaseElementId`) and 3D back-panel hollowing (`hasBackPanel`).
 */
data class KitchenElement(
    val id: String,
    val name: String,
    val type: KitchenElementType,
    val layer: LayerType = LayerType.fromElementType(type),
    val position: Vector3, // X, Z are center on floor; Y is bottom elevation from floor
    val rotationY: Float = 0f, // Orientation in degrees (0, 90, 180, 270, etc.)
    val dimensions: Vector3 = type.defaultDimensions, // width (X), height (Y), depth (Z) in mm
    val material: MaterialFinish = defaultMaterialForType(type),
    val countertopMaterial: MaterialFinish? = defaultCountertopForType(type),
    val isLocked: Boolean = false,
    val isVisible: Boolean = true,
    val groupId: String? = null,
    val linkedBaseElementId: String? = null, // Links an upper WALL_CABINET to a BASE_CABINET below it
    val hasBackPanel: Boolean = true, // Interactive backing option: if false, 3D mesh is hollowed out and back panel cut-list is zeroed
    val backPanelMaterialType: String = "MDF ميلامين 8 مم",
    val backGrooveInsetMm: Float = 10f, // Router rabbet/groove overlap (نسبة التداخل للمجرى / الريشة)
    val topStretcherWidthMm: Float = 100f, // Upper structural stretcher rail width (سخات الميلامين)
    val customBoardThicknessMm: Float = 18f, // Manual override for melamine board thickness
    val customAluminumProfileMm: Float = 22f // Manual override for aluminum profile section size
) {
    /**
     * Computes the 3D Oriented Bounding Box for this element.
     */
    fun getOBB(): OBB {
        val center = Vector3(
            x = position.x,
            y = position.y + dimensions.y * 0.5f,
            z = position.z
        )
        return OBB(
            center = center,
            dimensions = dimensions,
            rotationYDegrees = rotationY
        )
    }

    /**
     * Computes the Axis-Aligned Bounding Box enclosing this element.
     */
    fun getAABB(): AABB = getOBB().toAABB()

    /**
     * Returns the 4 corner points on the 2D floor plane (X-Z).
     */
    fun getFloorCorners(): List<Vector3> = getOBB().getBottomCorners()

    companion object {
        fun defaultMaterialForType(type: KitchenElementType): MaterialFinish {
            return when {
                type.category == ElementCategory.BRIDGE_CABINET -> MaterialFinish.CHARCOAL_MATTE
                type == KitchenElementType.TALL_GLASS_VITRINE_LED ||
                        type == KitchenElementType.TALL_DOUBLE_VITRINE_LED ||
                        type == KitchenElementType.WALL_GLASS_BLACK_FRAME -> MaterialFinish.SMOKED_GLASS_ALU
                type.category == ElementCategory.APPLIANCE -> MaterialFinish.STAINLESS_STEEL
                else -> MaterialFinish.NATURAL_OAK
            }
        }

        fun defaultCountertopForType(type: KitchenElementType): MaterialFinish? {
            return when {
                type.category == ElementCategory.BASE_CABINET -> MaterialFinish.BLACK_GRANITE
                type == KitchenElementType.ISLAND_COUNTER ||
                        type == KitchenElementType.PENINSULA_LIT_NICHES ||
                        type == KitchenElementType.BREAKFAST_BAR -> MaterialFinish.BLACK_GRANITE
                else -> null
            }
        }
    }
}
