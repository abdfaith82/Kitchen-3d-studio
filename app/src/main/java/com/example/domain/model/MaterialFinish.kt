package com.example.domain.model

enum class FinishCategory(val title: String) {
    WOOD("أخشاب طبيعية وقشرة / Wood"),
    STONE("رخام وجرانيت وكوارتز / Stone"),
    SOLID_COLOR("أكريليك ومطفي حديث / Acrylic & Matte"),
    METAL("معادن وزجاج مؤطر / Metal & Glass"),
    FLOOR_TILES("بورسلان وسيراميك وأرضيات / Flooring")
}

data class WallFloorHarmonyPreset(
    val id: String,
    val titleAr: String,
    val subtitleAr: String,
    val wallFinish: MaterialFinish,
    val floorMaterial: MaterialFinish,
    val recommendedCabinetFinish: MaterialFinish
)

data class MaterialFinish(
    val id: String,
    val name: String,
    val category: FinishCategory,
    val colorHex: Long,
    val roughness: Float = 0.5f,
    val metallic: Float = 0.0f
) {
    companion object {
        // Wood finishes
        val NATURAL_OAK = MaterialFinish("oak", "بلوط طبيعي معرق (Natural Oak)", FinishCategory.WOOD, 0xFFC8A165, 0.55f, 0.0f)
        val WALNUT_DARK = MaterialFinish("walnut", "جوز أمريكي داكن (Dark Walnut)", FinishCategory.WOOD, 0xFF4A3525, 0.50f, 0.0f)
        val ASH_WOOD = MaterialFinish("ash", "خشب الدردار الفاتح (Nordic Ash)", FinishCategory.WOOD, 0xFFDECDB4, 0.60f, 0.0f)
        val SMOKED_ELM = MaterialFinish("smoked_elm", "خشب دردار مدخن دافئ (Smoked Elm)", FinishCategory.WOOD, 0xFF8C6D53, 0.55f, 0.0f)

        // Modern Solid, Acrylic & Architectural Wall Paint colors
        val WHITE_HIGH_GLOSS = MaterialFinish("white_gloss", "أبيض أكريليك ناصع (Pure White)", FinishCategory.SOLID_COLOR, 0xFFF8FAFC, 0.08f, 0.05f)
        val CHAMPAGNE_GLOSS = MaterialFinish("champagne_gloss", "شامبين أكريليك مرآتي (Champagne Gloss)", FinishCategory.SOLID_COLOR, 0xFFE6DEC8, 0.08f, 0.06f)
        val CAPPUCCINO_MATTE = MaterialFinish("cappuccino_matte", "بيج كابتشينو حريري (Cappuccino Silk)", FinishCategory.SOLID_COLOR, 0xFFD5C7B4, 0.32f, 0.02f)
        val CHARCOAL_MATTE = MaterialFinish("charcoal_matte", "رمادي فحمي مطفي (Charcoal Matte)", FinishCategory.SOLID_COLOR, 0xFF282C34, 0.80f, 0.04f)
        val ANTHRACITE_BLACK = MaterialFinish("anthracite_black", "أسود أنثراسيت مطفي (Anthracite Black)", FinishCategory.SOLID_COLOR, 0xFF1A1D24, 0.75f, 0.05f)
        val SAGE_GREEN = MaterialFinish("sage_green", "أخضر مريمية هادئ (Sage Matte)", FinishCategory.SOLID_COLOR, 0xFF6B8E78, 0.70f, 0.0f)
        val NAVY_BLUE = MaterialFinish("navy_blue", "أزرق ملكي (Midnight Navy)", FinishCategory.SOLID_COLOR, 0xFF1E293B, 0.65f, 0.0f)

        // Dedicated Architectural Wall Paint Shades (ألوان دهانات الحوائط المعمارية)
        val WALL_WARM_IVORY = MaterialFinish("wall_warm_ivory", "دهان عاجي دافئ (Warm Ivory)", FinishCategory.SOLID_COLOR, 0xFFF6F1E6, 0.65f, 0.0f)
        val WALL_GREIGE_CASHMERE = MaterialFinish("wall_greige", "دهان جريج كشمير (Greige Cashmere)", FinishCategory.SOLID_COLOR, 0xFFD8D0C5, 0.68f, 0.0f)
        val WALL_SOFT_TAUPE = MaterialFinish("wall_soft_taupe", "دهان توب رملي ناعم (Soft Taupe)", FinishCategory.SOLID_COLOR, 0xFFC2B5A5, 0.70f, 0.0f)
        val WALL_PEARL_GREY = MaterialFinish("wall_pearl_grey", "دهان رمادي لؤلؤي فاتح (Pearl Grey)", FinishCategory.SOLID_COLOR, 0xFFE2E8F0, 0.62f, 0.0f)
        val WALL_TERRACOTTA_CLAY = MaterialFinish("wall_terracotta", "دهان تيراكوتا طيني دافئ (Terracotta)", FinishCategory.SOLID_COLOR, 0xFFB86B52, 0.74f, 0.0f)
        val WALL_OLIVE_SAGE = MaterialFinish("wall_olive_sage", "دهان زيتوني مريمية (Olive Sage)", FinishCategory.SOLID_COLOR, 0xFF7D8C78, 0.72f, 0.0f)

        // Stone / Countertops
        val CARRARA_MARBLE = MaterialFinish("carrara", "رخام كرارا أبيض بعروق (Carrara)", FinishCategory.STONE, 0xFFEFF3F8, 0.12f, 0.08f)
        val BLACK_GRANITE = MaterialFinish("black_granite", "جرانيت جالاكسي أسود لامع (Black Galaxy)", FinishCategory.STONE, 0xFF141518, 0.10f, 0.18f)
        val CALACATTA_GOLD = MaterialFinish("calacatta_gold", "كوارتز كالاكاتا جولد (Calacatta Gold)", FinishCategory.STONE, 0xFFF5F2EB, 0.10f, 0.08f)
        val TERRAZZO = MaterialFinish("terrazzo", "تيرازو إيطالي عصري (Terrazzo)", FinishCategory.STONE, 0xFFD8D2C2, 0.35f, 0.05f)

        // Metal & Smoked Glass
        val STAINLESS_STEEL = MaterialFinish("stainless", "ستانلس ستيل مصقول (Brushed Inox)", FinishCategory.METAL, 0xFF94A3B8, 0.22f, 0.88f)
        val BRUSHED_BRASS = MaterialFinish("brass", "نحاس ذهبي مفرش (Brushed Brass)", FinishCategory.METAL, 0xFFD4AF37, 0.28f, 0.85f)
        val SMOKED_GLASS_ALU = MaterialFinish("smoked_glass", "زجاج فحمي بإطار ألومنيوم أسود (Smoked Vitrine)", FinishCategory.METAL, 0xFF1E252D, 0.05f, 0.35f)

        // Flooring (ألوان وخامات الأرضيات)
        val PARQUET_OAK_FLOOR = MaterialFinish("floor_parquet", "باركيه خشب سنديان دافئ", FinishCategory.FLOOR_TILES, 0xFFB9935A, 0.40f, 0.0f)
        val HERRINGBONE_OAK_FLOOR = MaterialFinish("floor_herringbone", "باركيه شيفرون عسلي فاخر (Herringbone)", FinishCategory.FLOOR_TILES, 0xFFA47B48, 0.38f, 0.0f)
        val GREY_SLATE_FLOOR = MaterialFinish("floor_slate", "بورسلان رمادي فحمي مطفي", FinishCategory.FLOOR_TILES, 0xFF3F4E58, 0.50f, 0.0f)
        val MARBLE_WHITE_FLOOR = MaterialFinish("floor_marble", "بورسلان رخامي كالاكاتا لامع", FinishCategory.FLOOR_TILES, 0xFFF4F1EA, 0.08f, 0.05f)
        val BEIGE_TRAVERTINE_FLOOR = MaterialFinish("floor_travertine", "بورسلان بيج ترافرتين دافئ (Warm Cream)", FinishCategory.FLOOR_TILES, 0xFFE5DAC9, 0.12f, 0.03f)
        val CONCRETE_LOFT_FLOOR = MaterialFinish("floor_concrete", "بورسلان خرساني مودرن (Microcement Loft)", FinishCategory.FLOOR_TILES, 0xFF949A9E, 0.55f, 0.02f)
        val DARK_EMPERADOR_FLOOR = MaterialFinish("floor_emperador", "رخام إمبرادور بني داكن لامع", FinishCategory.FLOOR_TILES, 0xFF3D2E26, 0.10f, 0.06f)

        val WALL_PALETTE = listOf(
            WHITE_HIGH_GLOSS,
            WALL_WARM_IVORY,
            WALL_GREIGE_CASHMERE,
            WALL_SOFT_TAUPE,
            WALL_PEARL_GREY,
            CAPPUCCINO_MATTE,
            WALL_OLIVE_SAGE,
            SAGE_GREEN,
            WALL_TERRACOTTA_CLAY,
            NAVY_BLUE,
            CHARCOAL_MATTE,
            CARRARA_MARBLE,
            CALACATTA_GOLD
        )

        val FLOOR_PALETTE = listOf(
            PARQUET_OAK_FLOOR,
            HERRINGBONE_OAK_FLOOR,
            MARBLE_WHITE_FLOOR,
            BEIGE_TRAVERTINE_FLOOR,
            GREY_SLATE_FLOOR,
            CONCRETE_LOFT_FLOOR,
            DARK_EMPERADOR_FLOOR,
            TERRAZZO,
            BLACK_GRANITE
        )

        val HARMONY_PRESETS = listOf(
            WallFloorHarmonyPreset(
                id = "scandi_warm",
                titleAr = "تناغم إسكندنافي دافئ",
                subtitleAr = "حائط عاجي + أرضية باركيه سنديان",
                wallFinish = WALL_WARM_IVORY,
                floorMaterial = PARQUET_OAK_FLOOR,
                recommendedCabinetFinish = NATURAL_OAK
            ),
            WallFloorHarmonyPreset(
                id = "royal_marble",
                titleAr = "فخامة الكالاكاتا والكشمير",
                subtitleAr = "حائط جريج كشمير + بورسلان رخامي أبيض",
                wallFinish = WALL_GREIGE_CASHMERE,
                floorMaterial = MARBLE_WHITE_FLOOR,
                recommendedCabinetFinish = WALNUT_DARK
            ),
            WallFloorHarmonyPreset(
                id = "modern_industrial",
                titleAr = "لوفت عصري رمادي",
                subtitleAr = "حائط لؤلؤي + أرضية بورسلان فحمي",
                wallFinish = WALL_PEARL_GREY,
                floorMaterial = GREY_SLATE_FLOOR,
                recommendedCabinetFinish = WHITE_HIGH_GLOSS
            ),
            WallFloorHarmonyPreset(
                id = "botanical_sage",
                titleAr = "طبيعة المريمية والبيج",
                subtitleAr = "حائط أخضر زيتوني + أرضية ترافرتين بيج",
                wallFinish = WALL_OLIVE_SAGE,
                floorMaterial = BEIGE_TRAVERTINE_FLOOR,
                recommendedCabinetFinish = ASH_WOOD
            ),
            WallFloorHarmonyPreset(
                id = "mediterranean_clay",
                titleAr = "دفء التيراكوتا المتوسطي",
                subtitleAr = "حائط تيراكوتا دافئ + أرضية تيرازو إيطالي",
                wallFinish = WALL_TERRACOTTA_CLAY,
                floorMaterial = TERRAZZO,
                recommendedCabinetFinish = CHAMPAGNE_GLOSS
            ),
            WallFloorHarmonyPreset(
                id = "executive_dark",
                titleAr = "كونتراست ملكي داكن",
                subtitleAr = "حائط توب رملي + رخام إمبرادور بني",
                wallFinish = WALL_SOFT_TAUPE,
                floorMaterial = DARK_EMPERADOR_FLOOR,
                recommendedCabinetFinish = CAPPUCCINO_MATTE
            )
        )

        val ALL_FINISHES = listOf(
            NATURAL_OAK, WALNUT_DARK, ASH_WOOD, SMOKED_ELM,
            WHITE_HIGH_GLOSS, CHAMPAGNE_GLOSS, CAPPUCCINO_MATTE, CHARCOAL_MATTE, ANTHRACITE_BLACK, SAGE_GREEN, NAVY_BLUE,
            WALL_WARM_IVORY, WALL_GREIGE_CASHMERE, WALL_SOFT_TAUPE, WALL_PEARL_GREY, WALL_TERRACOTTA_CLAY, WALL_OLIVE_SAGE,
            CARRARA_MARBLE, BLACK_GRANITE, CALACATTA_GOLD, TERRAZZO,
            STAINLESS_STEEL, BRUSHED_BRASS, SMOKED_GLASS_ALU,
            MARBLE_WHITE_FLOOR, BEIGE_TRAVERTINE_FLOOR, PARQUET_OAK_FLOOR, HERRINGBONE_OAK_FLOOR,
            GREY_SLATE_FLOOR, CONCRETE_LOFT_FLOOR, DARK_EMPERADOR_FLOOR
        )
    }
}
