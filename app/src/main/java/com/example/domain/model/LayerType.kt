package com.example.domain.model

enum class LayerType(val title: String, val zIndex: Int) {
    STRUCTURE("الهيكل والجدران (Walls & Openings)", 0),
    BASE_CABINETS("الخزائن السفلية (Base Cabinets)", 10),
    COUNTERTOPS("أسطح العمل والرخام (Countertops)", 20),
    WALL_CABINETS("الخزائن العلوية والتاج (Wall & Bridge Cabinets)", 30),
    TALL_UNITS("الدواليب والفيترينة والأبراج (Tall & Vitrines)", 40),
    APPLIANCES("الأجهزة الكهربائية (Appliances)", 50),
    ISLANDS("الجزر والبار والإضاءة (Islands & Lighting)", 60);

    companion object {
        fun fromElementType(type: KitchenElementType): LayerType {
            return when (type.category) {
                ElementCategory.BASE_CABINET -> BASE_CABINETS
                ElementCategory.WALL_CABINET, ElementCategory.BRIDGE_CABINET -> WALL_CABINETS
                ElementCategory.TALL_UNIT -> TALL_UNITS
                ElementCategory.APPLIANCE -> APPLIANCES
                ElementCategory.ISLAND_AND_SURFACE -> ISLANDS
            }
        }
    }
}
