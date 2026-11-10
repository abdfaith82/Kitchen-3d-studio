package com.example.domain.model

import com.example.core.math.Vector3

enum class ElementCategory(val displayName: String) {
    BASE_CABINET("خزائن سفلية وكورنرات / Base & Corners"),
    WALL_CABINET("خزائن علوية وكورنرات / Wall & Corners"),
    BRIDGE_CABINET("خزائن تاج للسقف / Top Bridge"),
    TALL_UNIT("دولاب وفيترينة وأبراج / Tall & Vitrine"),
    APPLIANCE("أجهزة كهربائية / Appliances"),
    ISLAND_AND_SURFACE("جزر، بار وإضاءة / Islands & Lighting")
}

enum class KitchenElementType(
    val code: String,
    val title: String,
    val category: ElementCategory,
    val defaultDimensions: Vector3, // width, height, depth in mm
    val defaultElevationY: Float // standard mounting height from floor in mm
) {
    // 1. Base Cabinets & Lower Corner Cabinets (850mm standard height with 100mm toe-kick & 38mm countertop, 600mm depth)
    BASE_BOTTLE_RACK_15("base_bottle_15", "وحدة سحب زجاجات وزيوت نحيفة (15 سم)", ElementCategory.BASE_CABINET, Vector3(150f, 850f, 600f), 0f),
    BASE_NARROW_30_SPICE("base_spice_30", "وحدة سحب توابل وزيت (30 سم)", ElementCategory.BASE_CABINET, Vector3(300f, 850f, 600f), 0f),
    BASE_SINGLE_40("base_single_40", "خزانة سفلية مفردة (40 سم)", ElementCategory.BASE_CABINET, Vector3(400f, 850f, 600f), 0f),
    BASE_SINGLE_DOOR("base_single", "خزانة باب مفرد قياسية (60 سم)", ElementCategory.BASE_CABINET, Vector3(600f, 850f, 600f), 0f),
    BASE_DOUBLE_80("base_double_80", "خزانة سفلية درفتين (80 سم)", ElementCategory.BASE_CABINET, Vector3(800f, 850f, 600f), 0f),
    BASE_DOUBLE_DOOR("base_double", "خزانة سفلية درفتين (90 سم)", ElementCategory.BASE_CABINET, Vector3(900f, 850f, 600f), 0f),
    BASE_WIDE_DOUBLE_120("base_double_120", "خزانة سفلية عريضة درفتين (120 سم)", ElementCategory.BASE_CABINET, Vector3(1200f, 850f, 600f), 0f),
    BASE_2_DEEP_DRAWERS("base_2_drawers", "خزانة درجين عميقين للأواني (80 سم)", ElementCategory.BASE_CABINET, Vector3(800f, 850f, 600f), 0f),
    BASE_3_DRAWERS("base_drawers", "خزانة 3 أدراج سحب ناعمة (60 سم)", ElementCategory.BASE_CABINET, Vector3(600f, 850f, 600f), 0f),
    BASE_3_DRAWERS_90("base_3_drawers_90", "خزانة 3 أدراج عريضة (90 سم)", ElementCategory.BASE_CABINET, Vector3(900f, 850f, 600f), 0f),
    BASE_4_DRAWERS("base_4_drawers", "خزانة 4 أدراج للملاعق والتخزين (60 سم)", ElementCategory.BASE_CABINET, Vector3(600f, 850f, 600f), 0f),
    BASE_MICROWAVE_DRAWER_60("base_micro_drawer_60", "خزانة سفلية بميكروويف مدمج ودرج عميق (60 سم)", ElementCategory.BASE_CABINET, Vector3(600f, 850f, 600f), 0f),
    BASE_SINK_UNIT("base_sink", "خزانة حوض غسيل مع خلاط (80 سم)", ElementCategory.BASE_CABINET, Vector3(800f, 850f, 600f), 0f),
    BASE_SINK_VENTED_100("base_sink_vented", "خزانة حوض مزدوج بفتحات تهوية (100 سم)", ElementCategory.BASE_CABINET, Vector3(1000f, 850f, 600f), 0f),
    BASE_UNDER_OVEN("base_under_oven", "خزانة فرن سفلي مدمج بفتحة تهوية (60 سم)", ElementCategory.BASE_CABINET, Vector3(600f, 850f, 600f), 0f),
    // Lower Corner Cabinets (خزائن الكورنرات السفلية المتعددة)
    BASE_CORNER_L("base_corner", "خزانة كورنر سفلي زاوية L-Corner (90×90 سم)", ElementCategory.BASE_CABINET, Vector3(900f, 850f, 900f), 0f),
    BASE_MAGIC_CORNER("base_magic_corner", "خزانة كورنر سفلي سحرية Magic Corner (105×60 سم)", ElementCategory.BASE_CABINET, Vector3(1050f, 850f, 600f), 0f),
    BASE_CORNER_BLIND_100("base_corner_blind_100", "خزانة كورنر سفلي عمياء Blind Corner (100×60 سم)", ElementCategory.BASE_CABINET, Vector3(1000f, 850f, 600f), 0f),
    BASE_CORNER_DIAGONAL_90("base_corner_diag_90", "خزانة كورنر سفلي مشطوفة 45° (90×90 سم)", ElementCategory.BASE_CABINET, Vector3(900f, 850f, 900f), 0f),
    BASE_CORNER_CAROUSEL_95("base_corner_carousel_95", "خزانة كورنر سفلي بصينية دوارة 360° (95×95 سم)", ElementCategory.BASE_CABINET, Vector3(950f, 850f, 950f), 0f),
    BASE_CORNER_SINK_105("base_corner_sink_105", "خزانة كورنر سفلي بحوض غسيل ركني (105×105 سم)", ElementCategory.BASE_CABINET, Vector3(1050f, 850f, 1050f), 0f),
    BASE_TRASH_PULLOUT("base_trash", "وحدة سلة مهملات مدمجة سحب (45 سم)", ElementCategory.BASE_CABINET, Vector3(450f, 850f, 600f), 0f),
    BASE_FILLER_STRIP("base_filler_strip", "شريحة فيلر/فاصل سفلي مخصص (1 مم)", ElementCategory.BASE_CABINET, Vector3(50f, 850f, 600f), 0f),

    // 2. Wall Cabinets & Upper Corner Cabinets (Mounted at 1450mm elevation with warm 3000K under-cabinet LED strip)
    WALL_SINGLE_40("wall_single_40", "خزانة علوية مفردة (40 سم)", ElementCategory.WALL_CABINET, Vector3(400f, 720f, 350f), 1450f),
    WALL_SINGLE_DOOR("wall_single", "خزانة علوية مفردة (60 سم)", ElementCategory.WALL_CABINET, Vector3(600f, 720f, 350f), 1450f),
    WALL_DOUBLE_80("wall_double_80", "خزانة علوية درفتين (80 سم)", ElementCategory.WALL_CABINET, Vector3(800f, 720f, 350f), 1450f),
    WALL_DOUBLE_DOOR("wall_double", "خزانة علوية درفتين (90 سم)", ElementCategory.WALL_CABINET, Vector3(900f, 720f, 350f), 1450f),
    WALL_TRIPLE_120("wall_triple_120", "خزانة علوية 3 درف مع شريط LED (120 سم)", ElementCategory.WALL_CABINET, Vector3(1200f, 720f, 350f), 1450f),
    WALL_LIFT_AVENTOS("wall_aventos", "خزانة علوية قلاب مزدوج Aventos (90 سم)", ElementCategory.WALL_CABINET, Vector3(900f, 720f, 350f), 1450f),
    WALL_HORIZONTAL_BI_FOLD_100("wall_bifold_100", "خزانة علوية قلاب هيدروليكي زجاج وخشب (100 سم)", ElementCategory.WALL_CABINET, Vector3(1000f, 720f, 350f), 1450f),
    WALL_GLASS_DOOR("wall_glass", "خزانة علوية واجهة زجاج وإضاءة LED (80 سم)", ElementCategory.WALL_CABINET, Vector3(800f, 720f, 350f), 1450f),
    WALL_GLASS_BLACK_FRAME("wall_glass_black", "خزانة علوية زجاج فحمي بإطار أسود (90 سم)", ElementCategory.WALL_CABINET, Vector3(900f, 720f, 350f), 1450f),
    WALL_DISH_RACK("wall_dish_rack", "خزانة صفاية أطباق استيل فوق الحوض (80 سم)", ElementCategory.WALL_CABINET, Vector3(800f, 720f, 350f), 1450f),
    WALL_PLATE_DISPLAY_RACK_90("wall_plate_rack_90", "خزانة علوية عرض أطباق وكؤوس مضاءة (90 سم)", ElementCategory.WALL_CABINET, Vector3(900f, 720f, 350f), 1450f),
    // Upper Corner Cabinets (خزائن الكورنرات العلوية المتعددة)
    WALL_CORNER_L("wall_corner_l", "خزانة كورنر علوي زاوية L-Corner (65×65 سم)", ElementCategory.WALL_CABINET, Vector3(650f, 720f, 650f), 1450f),
    WALL_CORNER_DIAGONAL_65("wall_corner_diag_65", "خزانة كورنر علوي مشطوفة 45° بواجهة زجاج (65×65 سم)", ElementCategory.WALL_CABINET, Vector3(650f, 720f, 650f), 1450f),
    WALL_CORNER_BLIND_80("wall_corner_blind_80", "خزانة كورنر علوي عمياء Blind Corner (80×35 سم)", ElementCategory.WALL_CABINET, Vector3(800f, 720f, 350f), 1450f),
    WALL_CORNER_OPEN_SHELVES("wall_corner_open_60", "خزانة كورنر علوي أرفف مفتوحة مضاءة LED (60×60 سم)", ElementCategory.WALL_CABINET, Vector3(600f, 720f, 600f), 1450f),
    WALL_OPEN_SHELF("wall_shelf", "رف جداري مفتوح بسبوت LED (80 سم)", ElementCategory.WALL_CABINET, Vector3(800f, 280f, 320f), 1500f),
    WALL_APPLIANCE_GARAGE("wall_garage", "وحدة ركن القهوة وجراج الأجهزة (60 سم)", ElementCategory.WALL_CABINET, Vector3(600f, 600f, 380f), 850f),
    WALL_FILLER_STRIP("wall_filler_strip", "شريحة فيلر/فاصل علوي مخصص (1 مم)", ElementCategory.WALL_CABINET, Vector3(50f, 720f, 350f), 1450f),

    // 3. Top Bridge / Mezzanine Crown Cabinets (Mounted at 2170mm up to 2700mm ceiling, 580mm deep)
    BRIDGE_SINGLE_60("bridge_60", "خزانة تاج علوية عميقة للسقف (60 سم)", ElementCategory.BRIDGE_CABINET, Vector3(600f, 500f, 580f), 2170f),
    BRIDGE_DOUBLE_80("bridge_80", "خزانة تاج علوية عميقة درفتين (80 سم)", ElementCategory.BRIDGE_CABINET, Vector3(800f, 500f, 580f), 2170f),
    BRIDGE_DOUBLE_90("bridge_90", "خزانة تاج علوية عميقة درفتين (90 سم)", ElementCategory.BRIDGE_CABINET, Vector3(900f, 500f, 580f), 2170f),
    BRIDGE_TRIPLE_120("bridge_120", "خزانة تاج علوية عميقة 3 درف (120 سم)", ElementCategory.BRIDGE_CABINET, Vector3(1200f, 500f, 580f), 2170f),
    BRIDGE_GLASS_LED_90("bridge_glass_led", "خزانة تاج زجاجية مضاءة LED (90 سم)", ElementCategory.BRIDGE_CABINET, Vector3(900f, 500f, 580f), 2170f),
    BRIDGE_CORNER_L_90("bridge_corner_l_90", "خزانة تاج كورنر علوي زاوية للسقف (90×90 سم)", ElementCategory.BRIDGE_CABINET, Vector3(900f, 500f, 900f), 2170f),
    BRIDGE_OVER_FRIDGE("bridge_fridge", "خزانة جسر عميقة فوق الثلاجة (85 سم)", ElementCategory.BRIDGE_CABINET, Vector3(850f, 450f, 650f), 1900f),

    // 4. Tall Units, Glass Vitrines & Illuminated Niche Columns (Floor to 2170mm / 2670mm)
    TALL_PANTRY("tall_pantry", "دولاب مؤونة وتخزين عمودي (60 سم)", ElementCategory.TALL_UNIT, Vector3(600f, 2170f, 600f), 0f),
    TALL_PULLOUT_LARDER("tall_larder", "دولاب سحب سلال كروم للمؤونة (45 سم)", ElementCategory.TALL_UNIT, Vector3(450f, 2170f, 600f), 0f),
    TALL_BROOM_UTILITY_50("tall_broom_50", "دولاب تخزين أدوات النظافة والمؤونة (50 سم)", ElementCategory.TALL_UNIT, Vector3(500f, 2170f, 600f), 0f),
    TALL_OVEN_TOWER("tall_oven", "برج فرن وميكروويف مدمج بفتحات تهوية (60 سم)", ElementCategory.TALL_UNIT, Vector3(600f, 2170f, 600f), 0f),
    TALL_COFFEE_STATION_90("tall_coffee_station_90", "دولاب ركن القهوة والأجهزة الفاخر (90 سم)", ElementCategory.TALL_UNIT, Vector3(900f, 2170f, 600f), 0f),
    TALL_GLASS_VITRINE_LED("tall_vitrine_led", "فيترينة زجاج أسود مضاءة بشريط LED طولي (60 سم)", ElementCategory.TALL_UNIT, Vector3(600f, 2400f, 580f), 0f),
    TALL_DOUBLE_VITRINE_LED("tall_double_vitrine", "فيترينة عرض مزدوجة زجاج فحمي وإضاءة LED (90 سم)", ElementCategory.TALL_UNIT, Vector3(900f, 2400f, 580f), 0f),
    TALL_NICHE_DISPLAY_COLUMN("tall_niche_column", "عمود ديكور بـ 4 فتحات نيش مضاءة سبوت (45 سم)", ElementCategory.TALL_UNIT, Vector3(450f, 2400f, 580f), 0f),
    TALL_FRIDGE_HOUSING("tall_fridge", "إطار دولاب ثلاجة مدمجة بالكامل (85 سم)", ElementCategory.TALL_UNIT, Vector3(850f, 2170f, 650f), 0f),
    TALL_FILLER_STRIP("tall_filler_strip", "شريحة فيلر/فاصل عمودي كامل (1 مم)", ElementCategory.TALL_UNIT, Vector3(50f, 2170f, 600f), 0f),

    // 5. Appliances
    REFRIGERATOR_FROST("app_fridge", "ثلاجة ستانلس ستيل بابين (75 سم)", ElementCategory.APPLIANCE, Vector3(750f, 1850f, 700f), 0f),
    REFRIGERATOR_FRENCH_DOOR("app_fridge_french", "ثلاجة 4 أبواب ديجيتال عريضة (90 سم)", ElementCategory.APPLIANCE, Vector3(900f, 1880f, 720f), 0f),
    COOKTOP_INDUCTION("app_cooktop", "مسطح طهي سيراميك حثي 4 عيون (60 سم)", ElementCategory.APPLIANCE, Vector3(600f, 55f, 520f), 850f),
    COOKTOP_GAS_90("app_cooktop_90", "مسطح غاز إيطالي 5 شعلات (90 سم)", ElementCategory.APPLIANCE, Vector3(900f, 60f, 520f), 850f),
    BUILTIN_OVEN("app_oven", "فرن بيلت إن زجاج أسود وديجيتال (60 سم)", ElementCategory.APPLIANCE, Vector3(600f, 595f, 570f), 200f),
    BUILTIN_MICROWAVE("app_microwave", "ميكروويف بيلت إن مدمج (60 سم)", ElementCategory.APPLIANCE, Vector3(600f, 380f, 450f), 1400f),
    DISHWASHER_INTEGRATED("app_dishwasher", "غسالة صحون ديجيتال (60 سم)", ElementCategory.APPLIANCE, Vector3(600f, 840f, 580f), 0f),
    WASHING_MACHINE_FRONT("app_washer", "غسالة ملابس أوتوماتيك أمامية (60 سم)", ElementCategory.APPLIANCE, Vector3(600f, 840f, 580f), 0f),
    RANGE_HOOD_BOX("app_hood", "شفاط مطبخ هرمي/تلسكوبي أسود (90 سم)", ElementCategory.APPLIANCE, Vector3(900f, 420f, 500f), 1620f),

    // 6. Islands, Illuminated Peninsulas & Architectural Ceiling Lights
    ISLAND_COUNTER("island_center", "جزيرة مطبخ وسطية برخام وأدراج (160 سم)", ElementCategory.ISLAND_AND_SURFACE, Vector3(1600f, 860f, 900f), 0f),
    PENINSULA_LIT_NICHES("peninsula_lit", "بار إفطار بفتحات نيش خشبية مضاءة LED (160 سم)", ElementCategory.ISLAND_AND_SURFACE, Vector3(1600f, 920f, 600f), 0f),
    BREAKFAST_BAR("breakfast_bar", "بار إفطار مرتفع بسطح رخامي (130 سم)", ElementCategory.ISLAND_AND_SURFACE, Vector3(1300f, 950f, 550f), 0f),
    CEILING_CHANDELIER_MODERN("ceiling_chandelier", "نجفة سقف ثلاثية/كريستال معلقة (60 سم)", ElementCategory.ISLAND_AND_SURFACE, Vector3(600f, 550f, 600f), 2100f),
    CEILING_LINEAR_LED_PANEL("ceiling_linear_led", "إضاءة سقف خطية هندسية LED (180 سم)", ElementCategory.ISLAND_AND_SURFACE, Vector3(1800f, 45f, 900f), 2650f);

    val isFillerStrip: Boolean
        get() = this == BASE_FILLER_STRIP || this == WALL_FILLER_STRIP || this == TALL_FILLER_STRIP

    val isCornerUnit: Boolean
        get() = code.contains("corner")
}
