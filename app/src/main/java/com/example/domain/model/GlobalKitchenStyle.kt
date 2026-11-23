package com.example.domain.model

/**
 * Global kitchen design styles — the "world features" layer.
 *
 * Ten real, named design languages used by showrooms worldwide, each one
 * mapped to finishes the app already owns ([MaterialFinish]) so applying a
 * style is a one-tap, undoable change of wall + floor (plus a cabinet /
 * countertop / hardware recommendation shown in the dialog).
 *
 * [WorldKitchenStandards] holds the global ergonomic numbers every style
 * shares: 90 cm countertop height, 60 cm base depth, 120 cm walkway,
 * and the classic work-triangle range (3.6 – 6.6 m).
 */
enum class GlobalKitchenStyle(
    val titleAr: String,
    val titleEn: String,
    val regionAr: String,
    val regionEn: String,
    val descriptionAr: String,
    val wallFinish: MaterialFinish,
    val floorMaterial: MaterialFinish,
    val cabinetFinish: MaterialFinish,
    val countertopFinish: MaterialFinish,
    val hardwareAr: String,
    val lightingKelvin: Float,
    val layoutShape: RoomLayoutShape
) {
    SCANDINAVIAN(
        titleAr = "إسكندنافي دافئ 🇸🇪",
        titleEn = "Scandinavian Warm",
        regionAr = "الدول الإسكندنافية", regionEn = "Nordic",
        descriptionAr = "خشب فاتح، أبيض عاجي، وضوء نهاري — مطبخ هادئ ومشرق",
        wallFinish = MaterialFinish.WALL_WARM_IVORY,
        floorMaterial = MaterialFinish.PARQUET_OAK_FLOOR,
        cabinetFinish = MaterialFinish.ASH_WOOD,
        countertopFinish = MaterialFinish.CARRARA_MARBLE,
        hardwareAr = "مقابض خشبية فاتحة مخفية",
        lightingKelvin = 4000f,
        layoutShape = RoomLayoutShape.RECTANGULAR
    ),
    ITALIAN_LUXURY(
        titleAr = "إيطالي فاخر 🇮🇹",
        titleEn = "Italian Luxury",
        regionAr = "إيطاليا — ميلانو", regionEn = "Italy — Milan",
        descriptionAr = "جوز داكن مع كالاكاتا جولد ورخام أبيض — فخامة ميلانو الكلاسيكية",
        wallFinish = MaterialFinish.WALL_GREIGE_CASHMERE,
        floorMaterial = MaterialFinish.MARBLE_WHITE_FLOOR,
        cabinetFinish = MaterialFinish.WALNUT_DARK,
        countertopFinish = MaterialFinish.CALACATTA_GOLD,
        hardwareAr = "نحاس ذهبي مفرّش",
        lightingKelvin = 3000f,
        layoutShape = RoomLayoutShape.U_SHAPE
    ),
    JAPANESE_MINIMAL(
        titleAr = "ياباني بسيط 🇯🇵",
        titleEn = "Japanese Minimal",
        regionAr = "اليابان", regionEn = "Japan",
        descriptionAr = "خطوط نظيفة، خرسانة ناعمة وخشب مدخن — هدوء الزن",
        wallFinish = MaterialFinish.WALL_PEARL_GREY,
        floorMaterial = MaterialFinish.CONCRETE_LOFT_FLOOR,
        cabinetFinish = MaterialFinish.SMOKED_ELM,
        countertopFinish = MaterialFinish.TERRAZZO,
        hardwareAr = "مقابض مخفية بدون بروز",
        lightingKelvin = 4000f,
        layoutShape = RoomLayoutShape.L_SHAPE
    ),
    AMERICAN_FARMHOUSE(
        titleAr = "ريفي أمريكي 🇺🇸",
        titleEn = "American Farmhouse",
        regionAr = "الولايات المتحدة", regionEn = "USA",
        descriptionAr = "بلوط طبيعي وباركيه دافئ مع جزيرة كبيرة — دفء البيت الريفي",
        wallFinish = MaterialFinish.WALL_WARM_IVORY,
        floorMaterial = MaterialFinish.PARQUET_OAK_FLOOR,
        cabinetFinish = MaterialFinish.NATURAL_OAK,
        countertopFinish = MaterialFinish.CARRARA_MARBLE,
        hardwareAr = "حديد أسود مطفي",
        lightingKelvin = 3500f,
        layoutShape = RoomLayoutShape.U_SHAPE
    ),
    FRENCH_PROVINCIAL(
        titleAr = "فرنسي بروفنسال 🇫🇷",
        titleEn = "French Provincial",
        regionAr = "فرنسا — بروفانس", regionEn = "France — Provence",
        descriptionAr = "شامبين لامع مع ترافرتين بيج وتفاصيل كلاسيكية رومانسية",
        wallFinish = MaterialFinish.WALL_SOFT_TAUPE,
        floorMaterial = MaterialFinish.BEIGE_TRAVERTINE_FLOOR,
        cabinetFinish = MaterialFinish.CHAMPAGNE_GLOSS,
        countertopFinish = MaterialFinish.CARRARA_MARBLE,
        hardwareAr = "نحاس عتيق",
        lightingKelvin = 3000f,
        layoutShape = RoomLayoutShape.RECTANGULAR
    ),
    MEDITERRANEAN(
        titleAr = "متوسطي دافئ 🇬🇷",
        titleEn = "Mediterranean Warm",
        regionAr = "البحر المتوسط", regionEn = "Mediterranean",
        descriptionAr = "تيراكوتا طيني مع تيرازو إيطالي — شمس المتوسط في مطبخك",
        wallFinish = MaterialFinish.WALL_TERRACOTTA_CLAY,
        floorMaterial = MaterialFinish.TERRAZZO,
        cabinetFinish = MaterialFinish.CAPPUCCINO_MATTE,
        countertopFinish = MaterialFinish.CALACATTA_GOLD,
        hardwareAr = "حديد مطروق داكن",
        lightingKelvin = 3500f,
        layoutShape = RoomLayoutShape.L_SHAPE
    ),
    INDUSTRIAL_LOFT(
        titleAr = "صناعي لوفت 🏭",
        titleEn = "Industrial Loft",
        regionAr = "نيويورك — لوفت", regionEn = "New York Loft",
        descriptionAr = "فحمي مطفي مع خرسانة وجرانيت أسود — طابع المصنع العصري",
        wallFinish = MaterialFinish.WALL_PEARL_GREY,
        floorMaterial = MaterialFinish.CONCRETE_LOFT_FLOOR,
        cabinetFinish = MaterialFinish.CHARCOAL_MATTE,
        countertopFinish = MaterialFinish.BLACK_GRANITE,
        hardwareAr = "ستانلس ستيل أسود",
        lightingKelvin = 4000f,
        layoutShape = RoomLayoutShape.RECTANGULAR
    ),
    MODERN_GULF(
        titleAr = "خليجي عصري 🇦🇪",
        titleEn = "Modern Gulf",
        regionAr = "الخليج العربي", regionEn = "Arabian Gulf",
        descriptionAr = "جوز داكن مع إمبرادور بني وذهبي — فخامة المجالس الحديثة",
        wallFinish = MaterialFinish.WALL_GREIGE_CASHMERE,
        floorMaterial = MaterialFinish.DARK_EMPERADOR_FLOOR,
        cabinetFinish = MaterialFinish.WALNUT_DARK,
        countertopFinish = MaterialFinish.CALACATTA_GOLD,
        hardwareAr = "ذهبي لامع",
        lightingKelvin = 3000f,
        layoutShape = RoomLayoutShape.U_SHAPE
    ),
    JAPANDI(
        titleAr = "جاباندي (ياباني + إسكندنافي) 🇯🇵🇸🇪",
        titleEn = "Japandi",
        regionAr = "اليابان + الشمال", regionEn = "Japan + Nordic",
        descriptionAr = "أخف وزناً من الياباني وأدفأ من الإسكندنافي — شيفرون عسلي وتيرازو",
        wallFinish = MaterialFinish.WALL_WARM_IVORY,
        floorMaterial = MaterialFinish.HERRINGBONE_OAK_FLOOR,
        cabinetFinish = MaterialFinish.ASH_WOOD,
        countertopFinish = MaterialFinish.TERRAZZO,
        hardwareAr = "خشب داكن مخفي",
        lightingKelvin = 3500f,
        layoutShape = RoomLayoutShape.L_SHAPE
    ),
    MID_CENTURY(
        titleAr = "منتصف القرن 🎨",
        titleEn = "Mid-Century Modern",
        regionAr = "أمريكا — الخمسينات", regionEn = "USA — 1950s",
        descriptionAr = "مريمية زيتونية مع دردار مدخن — كلاسيكية الخمسينات العصرية",
        wallFinish = MaterialFinish.WALL_OLIVE_SAGE,
        floorMaterial = MaterialFinish.PARQUET_OAK_FLOOR,
        cabinetFinish = MaterialFinish.SMOKED_ELM,
        countertopFinish = MaterialFinish.BLACK_GRANITE,
        hardwareAr = "نحاس مصقول",
        lightingKelvin = 3000f,
        layoutShape = RoomLayoutShape.RECTANGULAR
    );

    /** Applies the style's wall + floor to a room (undoable via the ViewModel). */
    fun applyTo(room: RoomStructure): RoomStructure =
        room.copy(wallFinish = wallFinish, floorMaterial = floorMaterial)

    fun recommendationAr(): String =
        "خزائن: ${cabinetFinish.name} • سطح: ${countertopFinish.name} • مقابض: $hardwareAr • إضاءة: ${lightingKelvin.toInt()}K • التخطيط المقترح: ${layoutShape.titleAr}"
}

/** Global ergonomic standards shared by every kitchen style. */
object WorldKitchenStandards {
    const val COUNTERTOP_HEIGHT_MM: Float = 900f
    const val BASE_CABINET_DEPTH_MM: Float = 600f
    const val UPPER_CABINET_DEPTH_MM: Float = 350f
    const val MIN_WALKWAY_MM: Float = 1200f
    const val WORK_TRIANGLE_MIN_MM: Float = 3600f
    const val WORK_TRIANGLE_MAX_MM: Float = 6600f
    const val EYE_HEIGHT_MM: Float = 1600f
    const val MIN_CLEARANCE_IN_FRONT_OF_OVEN_MM: Float = 1000f

    /** Classic work triangle (sink–stove–fridge): 3.6 m … 6.6 m total. */
    fun isWorkTriangleOk(sinkToStoveMm: Float, stoveToFridgeMm: Float, fridgeToSinkMm: Float): Boolean {
        val total = sinkToStoveMm + stoveToFridgeMm + fridgeToSinkMm
        return total in WORK_TRIANGLE_MIN_MM..WORK_TRIANGLE_MAX_MM
    }

    fun isWalkwayOk(walkwayMm: Float): Boolean = walkwayMm >= MIN_WALKWAY_MM
}
