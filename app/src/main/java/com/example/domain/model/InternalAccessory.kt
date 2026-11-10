package com.example.domain.model

/**
 * Smart Internal Kitchen Accessories Library:
 * Defines internal hardware & organizers that automatically link to compatible cabinet units
 * and update the Bill of Materials (BOM) and project cost calculations.
 */
enum class InternalAccessoryType(
    val id: String,
    val arabicName: String,
    val code: String,
    val descriptionAr: String,
    val dimensionsMm: String,
    val materialSpecAr: String,
    val unitPriceUsd: Float,
    val compatibleTypes: Set<KitchenElementType>
) {
    CUTLERY_DRAWER_ORGANIZER(
        id = "acc_cutlery_organizer",
        arabicName = "منظم أدراج وملاعق وسكاكين ألمنيوم تقسيم ذكي",
        code = "ACC-ORG-60",
        descriptionAr = "يربط تلقائياً بخزائن الأدراج (3 و 4 أدراج) لتقسيم الملاعق وأدوات المائدة",
        dimensionsMm = "520 × 480 × 55 مم",
        materialSpecAr = "ألمنيوم مؤكسد رمادي أنثراسايت + فواصل قابلة للتعديل",
        unitPriceUsd = 48f,
        compatibleTypes = setOf(
            KitchenElementType.BASE_3_DRAWERS,
            KitchenElementType.BASE_4_DRAWERS
        )
    ),
    CORNER_CAROUSEL_LAZY_SUSAN(
        id = "acc_corner_carousel",
        arabicName = "دوار خزانة زاوية 360° (Lazy Susan Carousel)",
        code = "ACC-CRN-270",
        descriptionAr = "يربط تلقائياً بالخزائن الركنية (زاوية L) لاستغلال المساحة العميقة بالكامل",
        dimensionsMm = "قطر 750 × ارتفاع 680 مم (رفين)",
        materialSpecAr = "ستانلس ستيل 304 كروم مع قاعدة محور هيدروليكي",
        unitPriceUsd = 135f,
        compatibleTypes = setOf(
            KitchenElementType.BASE_CORNER_L
        )
    ),
    MAGIC_CORNER_PULLOUT(
        id = "acc_magic_corner",
        arabicName = "سلال زاوية سحرية سحب مزدوج (Magic Corner)",
        code = "ACC-MGC-90",
        descriptionAr = "نظام سحب ذكي بـ 4 سلال يخرج بالكامل عند فتح باب خزانة الزاوية",
        dimensionsMm = "860 × 480 × 560 مم",
        materialSpecAr = "سلال معدن أنثراسايت بقاعدة مانعة للانزلاق + إغلاق ناعم Soft-Close",
        unitPriceUsd = 220f,
        compatibleTypes = setOf(
            KitchenElementType.BASE_CORNER_L
        )
    ),
    UNDER_SINK_TRASH_BIN(
        id = "acc_sink_trash_pullout",
        arabicName = "سلة مهملات سحب مدمجة مزدوجة للفرز تحت المجلى",
        code = "ACC-TRSH-2X20",
        descriptionAr = "ترتبط تلقائياً بوحدات حوض الغسيل وتفتح بسكك تلسكوبية ناعمة",
        dimensionsMm = "360 × 470 × 420 مم (2 × 20 لتر)",
        materialSpecAr = "بولي بروبيلين مضاد للبكتيريا + سكك Blum تلسكوبية",
        unitPriceUsd = 85f,
        compatibleTypes = setOf(
            KitchenElementType.BASE_SINK_UNIT,
            KitchenElementType.BASE_SINK_VENTED_100
        )
    ),
    TALL_PANTRY_PULLOUT_BASKET(
        id = "acc_tall_pantry_larder",
        arabicName = "طقم سلال دولاب مؤونة تلسكوبي 6 طوابق (Tall Larder)",
        code = "ACC-PNT-190",
        descriptionAr = "يرتبط تلقائياً بدولاب المؤونة الطويل لحمل المعلبات والتموين",
        dimensionsMm = "540 × 1850 × 500 مم",
        materialSpecAr = "ستانلس ستيل وزجاج مقسى مع مكبح هيدروليكي",
        unitPriceUsd = 290f,
        compatibleTypes = setOf(
            KitchenElementType.TALL_PANTRY
        )
    ),
    UPPER_STAINLESS_DISH_RACK(
        id = "acc_upper_dish_rack",
        arabicName = "صفاية أطباق وأكواب ستانلس مع صينية تقطير للخزائن العلوية",
        code = "ACC-DSH-80",
        descriptionAr = "ترتبط تلقائياً بالخزائن العلوية فوق منطقة المجلى لتجفيف الصحون",
        dimensionsMm = "764 × 280 × 140 مم",
        materialSpecAr = "ستانلس ستيل 304 مقاوم للصدأ + صينية مياه سفلية",
        unitPriceUsd = 55f,
        compatibleTypes = setOf(
            KitchenElementType.WALL_DOUBLE_DOOR,
            KitchenElementType.WALL_GLASS_DOOR,
            KitchenElementType.WALL_GLASS_BLACK_FRAME,
            KitchenElementType.WALL_DISH_RACK,
            KitchenElementType.WALL_LIFT_AVENTOS,
            KitchenElementType.WALL_TRIPLE_120
        )
    ),
    SPICE_BOTTLE_PULLOUT_RACK(
        id = "acc_spice_pullout",
        arabicName = "سلة سحب جانبية للزيوت والبهارات",
        code = "ACC-SPC-20",
        descriptionAr = "ترتبط بوحدات سحب التوابل والخزائن المفردة بجوار منطقة الطهي",
        dimensionsMm = "150 × 480 × 520 مم",
        materialSpecAr = "كروم مزدوج الطبقات مع فواصل زجاجات الزيت",
        unitPriceUsd = 62f,
        compatibleTypes = setOf(
            KitchenElementType.BASE_NARROW_30_SPICE,
            KitchenElementType.BASE_SINGLE_40,
            KitchenElementType.BASE_SINGLE_DOOR
        )
    ),
    LED_DRAWER_SENSOR_LIGHT(
        id = "acc_led_drawer_sensor",
        arabicName = "شريط إضاءة داخلي بحساس فتح الباب/الدرج (IR Sensor LED)",
        code = "ACC-LED-IR",
        descriptionAr = "يرتبط تلقائياً بخزائن الأدراج والفيترينات ليضيء فور فتح الدرج",
        dimensionsMm = "560 × 12 × 8 مم (3000K دافئ)",
        materialSpecAr = "بروفايل ألمنيوم مع حساس أشعة تحت حمراء",
        unitPriceUsd = 28f,
        compatibleTypes = setOf(
            KitchenElementType.BASE_3_DRAWERS,
            KitchenElementType.BASE_4_DRAWERS,
            KitchenElementType.TALL_GLASS_VITRINE_LED,
            KitchenElementType.TALL_DOUBLE_VITRINE_LED
        )
    );

    companion object {
        val DEFAULT_ENABLED_IDS: Set<String> = entries.map { it.id }.toSet() - setOf(MAGIC_CORNER_PULLOUT.id)
    }
}

/**
 * Represents a matched internal accessory linked to specific cabinets in the current kitchen project.
 */
data class MatchedAccessoryItem(
    val accessory: InternalAccessoryType,
    val matchedCabinetIds: List<String>,
    val matchedCabinetNames: List<String>,
    val quantity: Int,
    val totalCostUsd: Float
)

object SmartAccessoryMatcher {
    /**
     * Automatically scans the kitchen elements in the project, finds compatible cabinets
     * for each enabled accessory, and returns the linked accessories list for BOM & cost updates.
     */
    fun matchAccessoriesForElements(
        elements: List<KitchenElement>,
        enabledAccessoryIds: Set<String> = InternalAccessoryType.DEFAULT_ENABLED_IDS
    ): List<MatchedAccessoryItem> {
        val results = mutableListOf<MatchedAccessoryItem>()

        for (accessory in InternalAccessoryType.entries) {
            if (accessory.id !in enabledAccessoryIds) continue

            val compatibleElements = elements.filter { it.type in accessory.compatibleTypes }
            if (compatibleElements.isNotEmpty()) {
                val qty = compatibleElements.size
                results.add(
                    MatchedAccessoryItem(
                        accessory = accessory,
                        matchedCabinetIds = compatibleElements.map { it.id },
                        matchedCabinetNames = compatibleElements.map { it.name },
                        quantity = qty,
                        totalCostUsd = qty * accessory.unitPriceUsd
                    )
                )
            }
        }
        return results
    }

    /**
     * Calculates the total cost in USD for a list of matched accessories.
     */
    fun calculateTotalAccessoriesCostUsd(matchedAccessories: List<MatchedAccessoryItem>): Double {
        return matchedAccessories.sumOf { it.totalCostUsd.toDouble() }
    }

    /**
     * Returns all compatible accessories for a single cabinet element.
     */
    fun getCompatibleAccessoriesForElement(element: KitchenElement): List<InternalAccessoryType> {
        return InternalAccessoryType.entries.filter { element.type in it.compatibleTypes }
    }
}
