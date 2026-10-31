package com.example.domain.model

enum class FinishCategory(val title: String) {
    WOOD("أخشاب طبيعية وقشرة / Wood"),
    STONE("رخام وجرانيت وكوارتز / Stone"),
    SOLID_COLOR("أكريليك ومطفي حديث / Acrylic & Matte"),
    METAL("معادن وزجاج مؤطر / Metal & Glass"),
    FLOOR_TILES("بورسلان وسيراميك / Flooring")
}

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

        // Modern Solid & High-Gloss Acrylic colors (inspired by luxury reference kitchens)
        val WHITE_HIGH_GLOSS = MaterialFinish("white_gloss", "أبيض أكريليك لامع (High Gloss)", FinishCategory.SOLID_COLOR, 0xFFF8FAFC, 0.08f, 0.05f)
        val CHAMPAGNE_GLOSS = MaterialFinish("champagne_gloss", "شامبين أكريليك مرآتي (Champagne Gloss)", FinishCategory.SOLID_COLOR, 0xFFE6DEC8, 0.08f, 0.06f)
        val CAPPUCCINO_MATTE = MaterialFinish("cappuccino_matte", "بيج كابتشينو حريري (Cappuccino Silk)", FinishCategory.SOLID_COLOR, 0xFFD5C7B4, 0.32f, 0.02f)
        val CHARCOAL_MATTE = MaterialFinish("charcoal_matte", "رمادي فحمي مطفي للتاج (Charcoal Matte)", FinishCategory.SOLID_COLOR, 0xFF282C34, 0.80f, 0.04f)
        val ANTHRACITE_BLACK = MaterialFinish("anthracite_black", "أسود أنثراسيت مطفي (Anthracite Black)", FinishCategory.SOLID_COLOR, 0xFF1A1D24, 0.75f, 0.05f)
        val SAGE_GREEN = MaterialFinish("sage_green", "أخضر مريمية هادئ (Sage Matte)", FinishCategory.SOLID_COLOR, 0xFF6B8E78, 0.70f, 0.0f)
        val NAVY_BLUE = MaterialFinish("navy_blue", "أزرق ملكي (Midnight Navy)", FinishCategory.SOLID_COLOR, 0xFF1E293B, 0.65f, 0.0f)

        // Stone / Countertops
        val CARRARA_MARBLE = MaterialFinish("carrara", "رخام كرارا أبيض بعروق (Carrara)", FinishCategory.STONE, 0xFFEFF3F8, 0.12f, 0.08f)
        val BLACK_GRANITE = MaterialFinish("black_granite", "جرانيت جالاكسي أسود لامع (Black Galaxy)", FinishCategory.STONE, 0xFF141518, 0.10f, 0.18f)
        val CALACATTA_GOLD = MaterialFinish("calacatta_gold", "كوارتز كالاكاتا جولد (Calacatta Gold)", FinishCategory.STONE, 0xFFF5F2EB, 0.10f, 0.08f)
        val TERRAZZO = MaterialFinish("terrazzo", "تيرازو إيطالي عصري (Terrazzo)", FinishCategory.STONE, 0xFFD8D2C2, 0.35f, 0.05f)

        // Metal & Smoked Glass
        val STAINLESS_STEEL = MaterialFinish("stainless", "ستانلس ستيل مصقول (Brushed Inox)", FinishCategory.METAL, 0xFF94A3B8, 0.22f, 0.88f)
        val BRUSHED_BRASS = MaterialFinish("brass", "نحاس ذهبي مفرش (Brushed Brass)", FinishCategory.METAL, 0xFFD4AF37, 0.28f, 0.85f)
        val SMOKED_GLASS_ALU = MaterialFinish("smoked_glass", "زجاج فحمي بإطار ألومنيوم أسود (Smoked Vitrine)", FinishCategory.METAL, 0xFF1E252D, 0.05f, 0.35f)

        // Flooring
        val PARQUET_OAK_FLOOR = MaterialFinish("floor_parquet", "باركيه خشب سنديان دافئ", FinishCategory.FLOOR_TILES, 0xFFB9935A, 0.40f, 0.0f)
        val GREY_SLATE_FLOOR = MaterialFinish("floor_slate", "بورسلان رمادي فحمي مطفي", FinishCategory.FLOOR_TILES, 0xFF3F4E58, 0.50f, 0.0f)
        val MARBLE_WHITE_FLOOR = MaterialFinish("floor_marble", "بورسلان رخامي كالاكاتا لامع", FinishCategory.FLOOR_TILES, 0xFFF4F1EA, 0.08f, 0.05f)
        val BEIGE_TRAVERTINE_FLOOR = MaterialFinish("floor_travertine", "بورسلان بيج دافئ لامع (Warm Cream Tile)", FinishCategory.FLOOR_TILES, 0xFFE5DAC9, 0.12f, 0.03f)

        val ALL_FINISHES = listOf(
            NATURAL_OAK, WALNUT_DARK, ASH_WOOD, SMOKED_ELM,
            WHITE_HIGH_GLOSS, CHAMPAGNE_GLOSS, CAPPUCCINO_MATTE, CHARCOAL_MATTE, ANTHRACITE_BLACK, SAGE_GREEN, NAVY_BLUE,
            CARRARA_MARBLE, BLACK_GRANITE, CALACATTA_GOLD, TERRAZZO,
            STAINLESS_STEEL, BRUSHED_BRASS, SMOKED_GLASS_ALU,
            MARBLE_WHITE_FLOOR, BEIGE_TRAVERTINE_FLOOR, PARQUET_OAK_FLOOR, GREY_SLATE_FLOOR
        )
    }
}
