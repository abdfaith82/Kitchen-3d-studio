package com.example.domain.model

import com.example.core.math.AABB
import com.example.core.math.Vector3

/**
 * Represents a group of kitchen elements grouped together for compound manipulation.
 */
data class KitchenGroup(
    val id: String,
    val name: String,
    val elementIds: List<String>,
    val isLocked: Boolean = false,
    val isVisible: Boolean = true
) {
    /**
     * Computes the aggregate bounding box for all member elements.
     */
    fun calculateCompoundAABB(elements: List<KitchenElement>): AABB {
        val memberBoxes = elements.filter { it.id in elementIds }.map { it.getAABB() }
        return AABB.merge(memberBoxes)
    }

    fun calculateCenter(elements: List<KitchenElement>): Vector3 {
        val compound = calculateCompoundAABB(elements)
        return compound.center
    }
}
