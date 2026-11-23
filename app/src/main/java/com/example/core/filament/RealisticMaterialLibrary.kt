package com.example.core.filament

import com.example.core.math.Vector3

/**
 * Extended realistic PBR material library.
 *
 * The base library in [PbrMaterial] already covers marble, steel, chrome,
 * oak, walnut and glass. This file adds the 12 finishes clients ask for
 * most in real kitchen showrooms — brushed brass, matte black metal,
 * smoked oak, white quartz, terrazzo, fluted walnut, sage lacquer,
 * champagne mirror, micro-cement, emerald marble, brushed copper and
 * smoked bronze glass — all with physically plausible roughness /
 * metallic / clearcoat / transmission values (Cook-Torrance GGX).
 *
 * Nothing here edits the original presets: [ALL_REALISTIC_MATERIALS]
 * is the original list plus these new ones.
 */
enum class RealisticMaterialCategory(val titleAr: String, val titleEn: String) {
    WOOD("خشب", "Wood"),
    STONE("حجر ورخام", "Stone"),
    METAL("معدن", "Metal"),
    GLASS("زجاج", "Glass"),
    LACQUER("لاكيه مطفي/لامع", "Lacquer"),
    CONCRETE("خرسانة وتيرازو", "Concrete & Terrazzo")
}

data class RealisticMaterialEntry(
    val material: PbrMaterial,
    val category: RealisticMaterialCategory,
    val bestUseAr: String
)

object RealisticMaterialLibrary {

    val BRUSHED_BRASS_GOLD = PbrMaterial(
        id = "pbr_brushed_brass_gold",
        nameAr = "نحاس ذهبي مفرّش",
        nameEn = "Brushed Brass Gold",
        baseColor = Vector3(0.83f, 0.65f, 0.25f),
        roughness = 0.30f, metallic = 1.0f, reflectance = 0.5f,
        anisotropy = 0.60f
    )

    val MATTE_BLACK_METAL = PbrMaterial(
        id = "pbr_matte_black_metal",
        nameAr = "معدن أسود مطفي",
        nameEn = "Matte Black Metal",
        baseColor = Vector3(0.08f, 0.08f, 0.09f),
        roughness = 0.55f, metallic = 0.90f, reflectance = 0.5f
    )

    val SMOKED_OAK = PbrMaterial(
        id = "pbr_smoked_oak",
        nameAr = "بلوط مدخّن دافئ",
        nameEn = "Smoked Oak",
        baseColor = Vector3(0.45f, 0.32f, 0.20f),
        roughness = 0.60f, metallic = 0.0f, reflectance = 0.40f,
        bumpDepth = 0.55f
    )

    val WHITE_QUARTZ = PbrMaterial(
        id = "pbr_white_quartz",
        nameAr = "كوارتز أبيض مصقول",
        nameEn = "Polished White Quartz",
        baseColor = Vector3(0.96f, 0.96f, 0.95f),
        roughness = 0.06f, metallic = 0.0f, reflectance = 0.62f,
        clearcoat = 1.0f, clearcoatRoughness = 0.03f, ior = 1.55f
    )

    val ITALIAN_TERRAZZO = PbrMaterial(
        id = "pbr_italian_terrazzo",
        nameAr = "تيرازو إيطالي",
        nameEn = "Italian Terrazzo",
        baseColor = Vector3(0.82f, 0.78f, 0.70f),
        roughness = 0.30f, metallic = 0.0f, reflectance = 0.45f,
        bumpDepth = 0.35f
    )

    val FLUTED_WALNUT = PbrMaterial(
        id = "pbr_fluted_walnut",
        nameAr = "جوز مضلّع فلوتد",
        nameEn = "Fluted Walnut",
        baseColor = Vector3(0.30f, 0.20f, 0.12f),
        roughness = 0.50f, metallic = 0.0f, reflectance = 0.42f,
        anisotropy = 0.30f, bumpDepth = 0.80f
    )

    val SAGE_LACQUER = PbrMaterial(
        id = "pbr_sage_lacquer",
        nameAr = "لاكيه أخضر مريمية",
        nameEn = "Sage Green Lacquer",
        baseColor = Vector3(0.42f, 0.55f, 0.47f),
        roughness = 0.25f, metallic = 0.0f, reflectance = 0.55f,
        clearcoat = 0.60f, clearcoatRoughness = 0.12f
    )

    val CHAMPAGNE_MIRROR = PbrMaterial(
        id = "pbr_champagne_mirror",
        nameAr = "مرآة شامبين",
        nameEn = "Champagne Mirror",
        baseColor = Vector3(0.90f, 0.82f, 0.65f),
        roughness = 0.05f, metallic = 1.0f, reflectance = 0.5f
    )

    val MICROCEMENT_CONCRETE = PbrMaterial(
        id = "pbr_microcement",
        nameAr = "مايكروسمنت خرساني",
        nameEn = "Microcement Concrete",
        baseColor = Vector3(0.58f, 0.60f, 0.62f),
        roughness = 0.70f, metallic = 0.0f, reflectance = 0.35f,
        bumpDepth = 0.25f
    )

    val EMERALD_MARBLE = PbrMaterial(
        id = "pbr_emerald_marble",
        nameAr = "رخام أخضر زمردي",
        nameEn = "Emerald Green Marble",
        baseColor = Vector3(0.10f, 0.25f, 0.20f),
        roughness = 0.10f, metallic = 0.0f, reflectance = 0.60f,
        clearcoat = 1.0f, clearcoatRoughness = 0.04f, ior = 1.57f
    )

    val BRUSHED_COPPER = PbrMaterial(
        id = "pbr_brushed_copper",
        nameAr = "نحاس أحمر مفرّش",
        nameEn = "Brushed Copper",
        baseColor = Vector3(0.72f, 0.40f, 0.25f),
        roughness = 0.28f, metallic = 1.0f, reflectance = 0.5f,
        anisotropy = 0.70f
    )

    val SMOKED_BRONZE_GLASS = PbrMaterial(
        id = "pbr_smoked_bronze_glass",
        nameAr = "زجاج برونزي مدخّن",
        nameEn = "Smoked Bronze Glass",
        baseColor = Vector3(0.45f, 0.32f, 0.20f),
        roughness = 0.08f, metallic = 0.0f, reflectance = 0.55f,
        clearcoat = 0.9f, transmission = 0.70f, ior = 1.52f
    )

    val NEW_REALISTIC_MATERIALS: List<PbrMaterial> = listOf(
        BRUSHED_BRASS_GOLD, MATTE_BLACK_METAL, SMOKED_OAK, WHITE_QUARTZ,
        ITALIAN_TERRAZZO, FLUTED_WALNUT, SAGE_LACQUER, CHAMPAGNE_MIRROR,
        MICROCEMENT_CONCRETE, EMERALD_MARBLE, BRUSHED_COPPER, SMOKED_BRONZE_GLASS
    )

    /** Original 10 presets + the 12 new ones = 22 realistic PBR finishes. */
    val ALL_REALISTIC_MATERIALS: List<PbrMaterial> =
        PbrMaterial.ALL_PBR_PRESETS + NEW_REALISTIC_MATERIALS

    val REALISTIC_CATALOG: List<RealisticMaterialEntry> = listOf(
        RealisticMaterialEntry(BRUSHED_BRASS_GOLD, RealisticMaterialCategory.METAL, "مقابض وإطارات فيترينة"),
        RealisticMaterialEntry(MATTE_BLACK_METAL, RealisticMaterialCategory.METAL, "مقابض وشفاط وأرجل جزيرة"),
        RealisticMaterialEntry(SMOKED_OAK, RealisticMaterialCategory.WOOD, "خزائن سفلية وجزيرة"),
        RealisticMaterialEntry(WHITE_QUARTZ, RealisticMaterialCategory.STONE, "سطح عمل (كونترتوب)"),
        RealisticMaterialEntry(ITALIAN_TERRAZZO, RealisticMaterialCategory.CONCRETE, "سطح عمل وأرضية"),
        RealisticMaterialEntry(FLUTED_WALNUT, RealisticMaterialCategory.WOOD, "واجهات مضلعة فاخرة"),
        RealisticMaterialEntry(SAGE_LACQUER, RealisticMaterialCategory.LACQUER, "خزائن علوية ملونة"),
        RealisticMaterialEntry(CHAMPAGNE_MIRROR, RealisticMaterialCategory.METAL, "ألواح زخرفية ومرايا"),
        RealisticMaterialEntry(MICROCEMENT_CONCRETE, RealisticMaterialCategory.CONCRETE, "جدران وأرضية لوفت"),
        RealisticMaterialEntry(EMERALD_MARBLE, RealisticMaterialCategory.STONE, "جزيرة رخامية فاخرة"),
        RealisticMaterialEntry(BRUSHED_COPPER, RealisticMaterialCategory.METAL, "صنابير وإكسسوارات"),
        RealisticMaterialEntry(SMOKED_BRONZE_GLASS, RealisticMaterialCategory.GLASS, "فيترينة زجاجية مدخنة")
    )

    fun findById(id: String): PbrMaterial? =
        ALL_REALISTIC_MATERIALS.firstOrNull { it.id == id }

    fun materialsForCategory(category: RealisticMaterialCategory): List<PbrMaterial> =
        REALISTIC_CATALOG.filter { it.category == category }.map { it.material }
}
