package com.example.core.cam

import com.example.domain.model.MaterialFinish
import kotlin.math.ceil

enum class DoorStyle(val title: String, val description: String) {
    SLAB_MODERN("مودرن ناعم (Slab Modern)", "درفة مسطحة ملساء بحواف مستقيمة"),
    SHAKER_BEVEL("شيكر شطف (Shaker Beveled)", "إطار 45° غائر كلاسيكي معاصر"),
    CLASSIC_RAISED("كلاسيك إطار بارز (Classic Raised)", "بانيل بارز بحليات وتجاويف كلاسيكية"),
    ALUMINUM_GLASS("إطار ألمنيوم وزجاج (Aluminum Frame)", "بروفيل ألمنيوم نحيف مع واجهة زجاجية"),
    FLUTED_REEDED("مضلع رائع (Fluted / Ribbed)", "أخاديد خشبية رأسية حديثة وعصرية")
}

enum class HandleStyle(val title: String) {
    BAR_PULL("مقبض طولي ستانلس (Bar Pull)"),
    J_PULL_GOLA("مجرى ألمنيوم غولا (Gola / J-Pull)"),
    EDGE_FINGER_PULL("مقبض حافة علوية (Edge Grip)"),
    MINIMAL_KNOB("زرار دائري مودرن (Knob)"),
    PUSH_TO_OPEN("نظام لمس بدون مقابض (Push to Open)")
}

enum class PanelMaterial(val displayName: String, val thicknessMm: Float, val costPerSheet: Float) {
    MDF_18MM("MDF ميلامين 18 مم (جسم الخزانة)", 18f, 45.0f),
    MDF_16MM("MDF ميلامين 16 مم (أوروبي)", 16f, 41.0f),
    MDF_8MM("MDF ميلامين 8 مم (ظهر الخزانة)", 8f, 25.0f),
    MDF_6MM("MDF ميلامين 6 مم (ظهر مجرى)", 6f, 21.0f),
    CHIPBOARD_18MM("شيب بورد مقاوم للرطوبة 18 مم", 18f, 38.0f),
    PLYWOOD_18MM("خشب كونتر / بليود 18 مم", 18f, 65.0f),
    ALUMINUM_CLAD_4MM("كلادينج ألمنيوم 4 مم (ظهر مقاوم للماء)", 4f, 52.0f)
}

/**
 * Semantic category of each cut part for Section 4 detailed breakdown:
 * Melamine Sides, Melamine Bottoms & Shelves, Melamine Top Stretchers, Back Panel, Facade, or Plinth.
 */
enum class CutPartCategory(val titleAr: String) {
    MELAMINE_SIDE("جوانب الميلامين (الجنب الأيمن والأيسر)"),
    MELAMINE_BOTTOM_AND_SHELF("أرضيات الميلامين والرفوف الداخلية"),
    MELAMINE_TOP_STRETCHER("سخات الميلامين العلوية الرابطة (Top Stretchers)"),
    CABINET_BACK_PANEL("ظهر الخزانة (Cabinet Backing)"),
    DRAWER_BOX("صناديق الأدراج الداخلية"),
    FACADE_DOOR("الواجهات والضلف الخارجية"),
    TOE_KICK("الوزرة الأرضية (Toe-Kick)");

    val arabicTitle: String get() = titleAr
}

/**
 * Saved Custom Material & Deduction Preset (قالب تشكيلة خامات وخصومات مخصص)
 * created via the Manual Override UI.
 */
data class CustomMaterialOverridePreset(
    val id: String,
    val nameAr: String,
    val melamineThicknessMm: Float = 18f,
    val aluminumProfileThicknessMm: Float = 1.8f,
    val aluminumProfileSectionSizeMm: Float = 45f,
    val aluminumWastePercentage: Float = 8f,
    val topStretcherWidthMm: Float = 100f,
    val hasBackPanel: Boolean = true,
    val backPanelMaterialName: String = "MDF ميلامين 8 مم",
    val backPanelThicknessMm: Float = 8f,
    val backGrooveInsetMm: Float = 10f, // نسبة التداخل للمجرى (الريشة)
    val customWidthDeductionMm: Float = 36f // خصم سمك اللوح يدوياً (2 × 18 مم افتراضياً)
) {
    val name: String get() = nameAr
    val backPanelMaterialType: String get() = backPanelMaterialName
    val aluminumProfileWidthMm: Float get() = aluminumProfileSectionSizeMm
    val manualBoardWidthDeductionMm: Float get() = customWidthDeductionMm

    companion object {
        val DEFAULT_PRESETS = listOf(
            CustomMaterialOverridePreset(
                id = "preset_std_18",
                nameAr = "قياسي ميلامين 18مم + ألمنيوم 45مم (بظهر 8مم)",
                melamineThicknessMm = 18f,
                aluminumProfileThicknessMm = 1.8f,
                aluminumProfileSectionSizeMm = 45f,
                aluminumWastePercentage = 8f,
                topStretcherWidthMm = 100f,
                hasBackPanel = true,
                backPanelMaterialName = "MDF ميلامين 8 مم",
                backPanelThicknessMm = 8f,
                backGrooveInsetMm = 10f,
                customWidthDeductionMm = 36f
            ),
            CustomMaterialOverridePreset(
                id = "preset_euro_16",
                nameAr = "أوروبي ميلامين 16مم + سخات 80مم (بظهر 6مم ريشة 8مم)",
                melamineThicknessMm = 16f,
                aluminumProfileThicknessMm = 1.6f,
                aluminumProfileSectionSizeMm = 40f,
                aluminumWastePercentage = 7f,
                topStretcherWidthMm = 80f,
                hasBackPanel = true,
                backPanelMaterialName = "MDF ميلامين 6 مم",
                backPanelThicknessMm = 6f,
                backGrooveInsetMm = 8f,
                customWidthDeductionMm = 32f
            ),
            CustomMaterialOverridePreset(
                id = "preset_sink_backless",
                nameAr = "وحدة مجلى/خدمات بدون ظهر (تفريغ 3D + كلادينج)",
                melamineThicknessMm = 18f,
                aluminumProfileThicknessMm = 2.0f,
                aluminumProfileSectionSizeMm = 50f,
                aluminumWastePercentage = 10f,
                topStretcherWidthMm = 120f,
                hasBackPanel = false,
                backPanelMaterialName = "بدون ظهر (مفرغ)",
                backPanelThicknessMm = 0f,
                backGrooveInsetMm = 0f,
                customWidthDeductionMm = 36f
            )
        )
    }
}

/**
 * Individual rectangular cut part of a cabinet carcass or facade.
 */
data class CutPart(
    val partName: String,
    val widthMm: Float,
    val lengthMm: Float,
    val thicknessMm: Float,
    val quantity: Int = 1,
    val material: PanelMaterial = PanelMaterial.MDF_18MM,
    val edgeBandingSides: Int = 2, // Number of edges receiving PVC/ABS edge tape (0..4)
    val isGrainDirectionMatters: Boolean = true,
    val category: CutPartCategory = CutPartCategory.MELAMINE_SIDE,
    val formulaNoteAr: String = "",
    val cabinetName: String = ""
) {
    val areaSqMm: Float get() = widthMm * lengthMm * quantity
    val areaSqM: Float get() = areaSqMm / 1_000_000f
    val perimeterMm: Float get() = (widthMm + lengthMm) * 2f
    val edgeBandingMeters: Float get() = (perimeterMm * (edgeBandingSides / 4f) * quantity) / 1000f
}

/**
 * Orientation of an aluminum extrusion profile bar on the cabinet structure.
 */
enum class AluminumBarOrientation(val labelAr: String) {
    HORIZONTAL("قضيب أفقي (Horizontal)"),
    VERTICAL("قضيب عمودي (Vertical)")
}

/**
 * Aluminum extrusion cut requirement (structural frame bars, Gola profile, glass door frame).
 */
data class AluminumProfileCut(
    val profileName: String,
    val lengthMm: Float,
    val quantity: Int = 1,
    val profileTypeCode: String = "ALU_FRAME", // e.g. ALU_FRAME_H, ALU_FRAME_V, GOLA_L, GLASS_FRAME
    val orientation: AluminumBarOrientation = AluminumBarOrientation.HORIZONTAL,
    val profileThicknessMm: Float = 1.8f,
    val profileSectionSizeMm: Float = 45f
) {
    val totalLengthMeters: Float get() = (lengthMm * quantity) / 1000f
}

/**
 * Detailed Aluminum Profile summary for a cabinet or project including horizontal/vertical breakdown,
 * total linear meters, waste percentage, and standard 6m bar count.
 */
data class AluminumCalculationSummary(
    val cuts: List<AluminumProfileCut>,
    val horizontalTotalMeters: Float,
    val verticalTotalMeters: Float,
    val netLinearMeters: Float,
    val wastePercentage: Float,
    val grossLinearMetersWithWaste: Float,
    val standardBarLengthMeters: Float = 6.0f,
    val requiredStandardBarsCount: Int
) {
    val totalHorizontalMeters: Float get() = horizontalTotalMeters
    val totalVerticalMeters: Float get() = verticalTotalMeters
    val netTotalLinearMeters: Float get() = netLinearMeters
    val totalMetersWithWaste: Float get() = grossLinearMetersWithWaste
}

/**
 * Procedural Parametric Cabinet Model implementing Section 4 of the Blueprint Specification:
 * - Aluminum profiles calculation (horizontal & vertical bars, linear meters, standard bars with waste %)
 * - Melamine sides (Right/Left net dimensions from Depth & Height)
 * - Melamine bottoms & shelves (Internal net width & depth with manual board thickness deduction)
 * - Melamine top stretchers (سخات الميلامين العلوية الرابطة لهيكل الألمنيوم والميلامين)
 * - Interactive Cabinet Backing System (نعم يوجد ظهر / لا يوجد ظهر with router groove inset الريشة and 3D hollowing)
 * - Manual Override UI support for board thickness, aluminum profile size/thickness, and custom presets.
 */
data class ParametricCabinet(
    val id: String,
    val name: String,
    val widthMm: Float = 600f,
    val heightMm: Float = 850f,
    val depthMm: Float = 600f,
    val panelThicknessMm: Float = 18f, // سمك لوح الميلامين (قابل للتعديل يدوياً)
    val backPanelThicknessMm: Float = 8f,
    val toeKickHeightMm: Float = 100f, // Plinth / legs height
    val numberOfShelves: Int = 1,
    val numberOfDrawers: Int = 0,
    val doorStyle: DoorStyle = DoorStyle.SLAB_MODERN,
    val handleStyle: HandleStyle = HandleStyle.BAR_PULL,
    val carcassMaterial: PanelMaterial = PanelMaterial.MDF_18MM,
    val facadeMaterial: MaterialFinish = MaterialFinish.NATURAL_OAK,
    // Section 4 Engineering Additions:
    val hasBackPanel: Boolean = true, // نظام التحكم بالظهر (نعم يوجد ظهر / لا يوجد ظهر)
    val backPanelMaterialName: String = "MDF ميلامين 8 مم",
    val backGrooveInsetMm: Float = 10f, // نسبة التداخل للمجرى (الريشة) من كل طرف
    val CustomBackWidthMm: Float? = null, // حقل إدخال يدوي لمقاس الظهر (اختياري)
    val CustomBackHeightMm: Float? = null,
    val topStretcherWidthMm: Float = 100f, // مقاس وعرض سخات الميلامين العلوية الرابطة
    val topStretcherCount: Int = 2,
    val manualWidthDeductionMm: Float? = null, // خصم سمك اللوح يدوياً (إذا ترك فارغاً يخصم 2 × سمك اللوح)
    val aluminumProfileThicknessMm: Float = 1.8f, // سمك بروفايل الألمنيوم يدوياً
    val aluminumProfileSectionSizeMm: Float = 45f, // حجم مقطع بروفايل الألمنيوم يدوياً (مم)
    val aluminumWastePercentage: Float = 8f, // نسبة الهدر في قضبان الألمنيوم (%)
    val includeStructuralAluminumFrame: Boolean = true // حساب قضبان هيكل الألمنيوم الأفقية والعمودية للخزانة
) {
    /** Effective width deduction (خصم سمك اللوح يدوياً أو تلقائياً 2 × سمك اللوح) */
    val effectiveWidthDeductionMm: Float
        get() = manualWidthDeductionMm ?: (panelThicknessMm * 2f)

    val boardThicknessMm: Float get() = panelThicknessMm
    val backPanelMaterialType: String get() = backPanelMaterialName
    val aluminumProfileWidthMm: Float get() = aluminumProfileSectionSizeMm

    /** Net Carcass Height excluding plinth/toe-kick */
    val carcassHeightMm: Float
        get() = (heightMm - toeKickHeightMm).coerceAtLeast(150f)

    /** Internal usable cabinet dimensions (Joinery deductions) */
    val internalWidthMm: Float
        get() = (widthMm - effectiveWidthDeductionMm).coerceAtLeast(80f)

    val internalHeightMm: Float
        get() = (carcassHeightMm - (panelThicknessMm * 2f)).coerceAtLeast(80f)

    val internalDepthMm: Float
        get() = (depthMm - (if (hasBackPanel) backPanelThicknessMm else 0f) - 20f).coerceAtLeast(80f)

    /** Net Back Panel Width & Height including router groove inset (نسبة التداخل للمجرى / الريشة) */
    val netBackPanelWidthMm: Float
        get() = if (!hasBackPanel) 0f else (CustomBackWidthMm ?: (internalWidthMm + backGrooveInsetMm * 2f))

    val netBackPanelHeightMm: Float
        get() = if (!hasBackPanel) 0f else (CustomBackHeightMm ?: (internalHeightMm + backGrooveInsetMm * 2f))

    /**
     * Decomposes the parametric cabinet into exact Melamine panels & Cut-List parts:
     * 1. جوانب الميلامين (Left & Right Sides)
     * 2. أرضيات الميلامين والرفوف (Bottom Deck & Internal Shelves with manual thickness deduction)
     * 3. سخات الميلامين (Upper Structural Top Stretchers)
     * 4. ظهر الخزانة (Interactive Back Panel — zeroed out immediately when hasBackPanel == false)
     * 5. الأدراج والواجهات والوزرة
     */
    fun generateCarcassCutParts(): List<CutPart> {
        val parts = mutableListOf<CutPart>()
        val carcassH = carcassHeightMm

        // 1. جوانب الميلامين (Left & Right Side Gables): Net Depth × Carcass Height
        parts.add(
            CutPart(
                partName = "جنب خزانة ميلامين (الأيمن والأيسر)",
                widthMm = depthMm,
                lengthMm = carcassH,
                thicknessMm = panelThicknessMm,
                quantity = 2,
                material = carcassMaterial,
                edgeBandingSides = 1,
                category = CutPartCategory.MELAMINE_SIDE,
                formulaNoteAr = "العمق الصافي (${depthMm.toInt()} مم) × ارتفاع الجنب (${carcassH.toInt()} مم)"
            )
        )

        // 2. أرضيات الميلامين (Bottom Deck): Net Internal Width (after manual thickness deduction) × Depth
        parts.add(
            CutPart(
                partName = "أرضية ميلامين (قاعدة الخزانة الصافية)",
                widthMm = internalWidthMm,
                lengthMm = depthMm,
                thicknessMm = panelThicknessMm,
                quantity = 1,
                material = carcassMaterial,
                edgeBandingSides = 1,
                category = CutPartCategory.MELAMINE_BOTTOM_AND_SHELF,
                formulaNoteAr = "العرض الداخلي (${widthMm.toInt()} - خصم ${effectiveWidthDeductionMm.toInt()} = ${internalWidthMm.toInt()} مم) × العمق (${depthMm.toInt()} مم)"
            )
        )

        // 3. سخات الميلامين (Top Stretchers linking Aluminum & Melamine carcass)
        if (topStretcherCount > 0 && topStretcherWidthMm > 0f) {
            parts.add(
                CutPart(
                    partName = "سخات ميلامين علوية رابطة (Top Stretchers)",
                    widthMm = topStretcherWidthMm,
                    lengthMm = internalWidthMm,
                    thicknessMm = panelThicknessMm,
                    quantity = topStretcherCount,
                    material = carcassMaterial,
                    edgeBandingSides = 1,
                    category = CutPartCategory.MELAMINE_TOP_STRETCHER,
                    formulaNoteAr = "عرض السخة (${topStretcherWidthMm.toInt()} مم) × العرض الداخلي الصافي (${internalWidthMm.toInt()} مم) × عدد $topStretcherCount"
                )
            )
        }

        // 4. نظام التحكم بالظهر (Cabinet Backing Options):
        // Only added when hasBackPanel == true; when disabled, back panel calculations are zeroed out immediately.
        if (hasBackPanel && backPanelThicknessMm > 0f) {
            val backMat = when {
                backPanelThicknessMm <= 5f -> PanelMaterial.ALUMINUM_CLAD_4MM
                backPanelThicknessMm <= 6.5f -> PanelMaterial.MDF_6MM
                backPanelThicknessMm >= 15f -> PanelMaterial.MDF_16MM
                else -> PanelMaterial.MDF_8MM
            }
            parts.add(
                CutPart(
                    partName = "ظهر الخزانة ($backPanelMaterialName - ريشة ${backGrooveInsetMm.toInt()}مم)",
                    widthMm = netBackPanelWidthMm,
                    lengthMm = netBackPanelHeightMm,
                    thicknessMm = backPanelThicknessMm,
                    quantity = 1,
                    material = backMat,
                    edgeBandingSides = 0,
                    category = CutPartCategory.CABINET_BACK_PANEL,
                    formulaNoteAr = "الداخلي + (2 × تداخل الريشة ${backGrooveInsetMm.toInt()} مم) = ${netBackPanelWidthMm.toInt()} × ${netBackPanelHeightMm.toInt()} مم"
                )
            )
        }

        // 5. الرفوف الداخلية (Melamine Adjustable Shelves)
        if (numberOfShelves > 0 && numberOfDrawers == 0) {
            val shelfClearance = 2f
            val netShelfWidth = (internalWidthMm - shelfClearance).coerceAtLeast(60f)
            val netShelfDepth = (internalDepthMm - 10f).coerceAtLeast(60f)
            parts.add(
                CutPart(
                    partName = "رف داخلي ميلامين صافي",
                    widthMm = netShelfWidth,
                    lengthMm = netShelfDepth,
                    thicknessMm = panelThicknessMm,
                    quantity = numberOfShelves,
                    material = carcassMaterial,
                    edgeBandingSides = 1,
                    category = CutPartCategory.MELAMINE_BOTTOM_AND_SHELF,
                    formulaNoteAr = "العرض الداخلي الصافي (${netShelfWidth.toInt()} مم) × العمق الداخلي (${netShelfDepth.toInt()} مم)"
                )
            )
        }

        // 6. Drawers Boxes Breakdown (أدراج السحب الداخلية)
        if (numberOfDrawers > 0) {
            val drawerSlideDeduction = 25f
            val drawerWidth = internalWidthMm - drawerSlideDeduction
            val drawerDepth = (depthMm - 50f).coerceAtLeast(300f)
            val drawerHeight = (internalHeightMm / numberOfDrawers) - 35f

            parts.add(
                CutPart(
                    partName = "جنب درج داخلي",
                    widthMm = drawerDepth,
                    lengthMm = drawerHeight,
                    thicknessMm = panelThicknessMm,
                    quantity = numberOfDrawers * 2,
                    material = carcassMaterial,
                    edgeBandingSides = 1,
                    category = CutPartCategory.DRAWER_BOX
                )
            )

            parts.add(
                CutPart(
                    partName = "ظهر ومقدمة صندوق الدرج",
                    widthMm = (drawerWidth - (panelThicknessMm * 2f)).coerceAtLeast(80f),
                    lengthMm = drawerHeight,
                    thicknessMm = panelThicknessMm,
                    quantity = numberOfDrawers * 2,
                    material = carcassMaterial,
                    edgeBandingSides = 1,
                    category = CutPartCategory.DRAWER_BOX
                )
            )

            parts.add(
                CutPart(
                    partName = "قاع الدرج (MDF 8 مم)",
                    widthMm = drawerWidth - 5f,
                    lengthMm = drawerDepth - 5f,
                    thicknessMm = 8f,
                    quantity = numberOfDrawers,
                    material = PanelMaterial.MDF_8MM,
                    edgeBandingSides = 0,
                    category = CutPartCategory.DRAWER_BOX
                )
            )
        }

        // 7. Doors or Drawer Front Facades (الواجهات والأبواب الخارجية)
        val doorRevealGap = 3f
        val facadeH = carcassH - (doorRevealGap * 2f)

        if (numberOfDrawers > 0) {
            val drawerFrontH = (facadeH / numberOfDrawers) - doorRevealGap
            parts.add(
                CutPart(
                    partName = "وجه درج خارجي (Facade)",
                    widthMm = widthMm - (doorRevealGap * 2f),
                    lengthMm = drawerFrontH,
                    thicknessMm = panelThicknessMm,
                    quantity = numberOfDrawers,
                    material = carcassMaterial,
                    edgeBandingSides = 4,
                    category = CutPartCategory.FACADE_DOOR
                )
            )
        } else {
            if (widthMm > 650f) {
                val halfWidth = (widthMm * 0.5f) - doorRevealGap
                parts.add(
                    CutPart(
                        partName = "درفة باب خارجية (مزدوج)",
                        widthMm = halfWidth,
                        lengthMm = facadeH,
                        thicknessMm = panelThicknessMm,
                        quantity = 2,
                        material = carcassMaterial,
                        edgeBandingSides = 4,
                        category = CutPartCategory.FACADE_DOOR
                    )
                )
            } else {
                parts.add(
                    CutPart(
                        partName = "درفة باب خارجية (مفرد)",
                        widthMm = widthMm - (doorRevealGap * 2f),
                        lengthMm = facadeH,
                        thicknessMm = panelThicknessMm,
                        quantity = 1,
                        material = carcassMaterial,
                        edgeBandingSides = 4,
                        category = CutPartCategory.FACADE_DOOR
                    )
                )
            }
        }

        // 8. Plinth / Toe Kick Board (وزرة أسفل الخزانة)
        if (toeKickHeightMm > 0f) {
            parts.add(
                CutPart(
                    partName = "وزرة أرضية (Toe Kick Plinth)",
                    widthMm = widthMm,
                    lengthMm = toeKickHeightMm,
                    thicknessMm = panelThicknessMm,
                    quantity = 1,
                    material = carcassMaterial,
                    edgeBandingSides = 1,
                    category = CutPartCategory.TOE_KICK
                )
            )
        }

        return parts.map { it.copy(cabinetName = name) }
    }

    /**
     * Section 4.1: Aluminum Profiles Calculation (حساب بروفايلات الألمنيوم):
     * Treats dimensions as linear bars cut geometrically, calculating horizontal and vertical
     * bars for each cabinet (structural frame rails, Gola handle profiles, and glass door frames).
     */
    fun generateAluminumCuts(): List<AluminumProfileCut> {
        val cuts = mutableListOf<AluminumProfileCut>()
        val carcassH = carcassHeightMm

        // 1. Structural Aluminum Frame Bars (القضبان الأفقية والعمودية لهيكل الخزانة)
        if (includeStructuralAluminumFrame) {
            // 4 Vertical Corner Posts (قوائم عمودية)
            cuts.add(
                AluminumProfileCut(
                    profileName = "قائم ألمنيوم عمودي هيكل (${aluminumProfileSectionSizeMm.toInt()}مم × ${aluminumProfileThicknessMm}مم)",
                    lengthMm = carcassH,
                    quantity = 4,
                    profileTypeCode = "ALU_POST_V",
                    orientation = AluminumBarOrientation.VERTICAL,
                    profileThicknessMm = aluminumProfileThicknessMm,
                    profileSectionSizeMm = aluminumProfileSectionSizeMm
                )
            )
            // 4 Horizontal Front/Back Crossbars (عوارض أفقية عرضية)
            cuts.add(
                AluminumProfileCut(
                    profileName = "عارضة ألمنيوم أفقية أمامية/خلفية (${aluminumProfileSectionSizeMm.toInt()}مم)",
                    lengthMm = widthMm,
                    quantity = 4,
                    profileTypeCode = "ALU_CROSS_H",
                    orientation = AluminumBarOrientation.HORIZONTAL,
                    profileThicknessMm = aluminumProfileThicknessMm,
                    profileSectionSizeMm = aluminumProfileSectionSizeMm
                )
            )
            // 4 Horizontal Depth Side Bars (عوارض أفقية جانبية للعمق)
            cuts.add(
                AluminumProfileCut(
                    profileName = "عارضة ألمنيوم أفقية للعمق (${aluminumProfileSectionSizeMm.toInt()}مم)",
                    lengthMm = depthMm,
                    quantity = 4,
                    profileTypeCode = "ALU_DEPTH_H",
                    orientation = AluminumBarOrientation.HORIZONTAL,
                    profileThicknessMm = aluminumProfileThicknessMm,
                    profileSectionSizeMm = aluminumProfileSectionSizeMm
                )
            )
        }

        // 2. Gola Handleless Profiles
        if (handleStyle == HandleStyle.J_PULL_GOLA) {
            cuts.add(
                AluminumProfileCut(
                    profileName = "بروفيل ألمنيوم غولا علوي أفقي (Gola L-Profile)",
                    lengthMm = widthMm,
                    quantity = 1,
                    profileTypeCode = "GOLA_L",
                    orientation = AluminumBarOrientation.HORIZONTAL,
                    profileThicknessMm = aluminumProfileThicknessMm,
                    profileSectionSizeMm = aluminumProfileSectionSizeMm
                )
            )
            if (numberOfDrawers > 1) {
                cuts.add(
                    AluminumProfileCut(
                        profileName = "بروفيل ألمنيوم غولا وسطي أفقي (Gola C-Profile)",
                        lengthMm = widthMm,
                        quantity = numberOfDrawers - 1,
                        profileTypeCode = "GOLA_C",
                        orientation = AluminumBarOrientation.HORIZONTAL,
                        profileThicknessMm = aluminumProfileThicknessMm,
                        profileSectionSizeMm = aluminumProfileSectionSizeMm
                    )
                )
            }
        }

        // 3. Aluminum Glass Door Frame Profiles
        if (doorStyle == DoorStyle.ALUMINUM_GLASS) {
            val doorH = carcassH - 6f
            val doorW = if (widthMm > 650f) (widthMm * 0.5f) - 6f else widthMm - 6f
            val count = if (widthMm > 650f) 2 else 1

            cuts.add(
                AluminumProfileCut(
                    profileName = "بروفيل إطار زجاج عمودي (Vertical Glass Frame)",
                    lengthMm = doorH,
                    quantity = count * 2,
                    profileTypeCode = "GLASS_FRAME_V",
                    orientation = AluminumBarOrientation.VERTICAL,
                    profileThicknessMm = aluminumProfileThicknessMm,
                    profileSectionSizeMm = aluminumProfileSectionSizeMm
                )
            )
            cuts.add(
                AluminumProfileCut(
                    profileName = "بروفيل إطار زجاج أفقي (Horizontal Glass Frame)",
                    lengthMm = doorW,
                    quantity = count * 2,
                    profileTypeCode = "GLASS_FRAME_H",
                    orientation = AluminumBarOrientation.HORIZONTAL,
                    profileThicknessMm = aluminumProfileThicknessMm,
                    profileSectionSizeMm = aluminumProfileSectionSizeMm
                )
            )
        }

        return cuts
    }

    /**
     * Computes the complete Aluminum Profile summary for this cabinet (or across multiple cabinets via companion).
     */
    fun calculateAluminumSummary(standardBarLengthMeters: Float = 6.0f): AluminumCalculationSummary {
        return calculateAluminumSummaryForCabinets(
            cabinets = listOf(this),
            wastePercentageOverride = aluminumWastePercentage,
            standardBarLengthMeters = standardBarLengthMeters
        )
    }

    /**
     * Hardware inventory calculation (Hinges, drawer slides, shelf pins, handles).
     */
    fun calculateHardwareRequirements(): Map<String, Int> {
        val hardware = mutableMapOf<String, Int>()

        if (numberOfDrawers > 0) {
            hardware["أطقم مجاري أدراج تلسكوبية Soft-Close (${(depthMm - 50f).toInt()} مم)"] = numberOfDrawers
            if (handleStyle != HandleStyle.J_PULL_GOLA && handleStyle != HandleStyle.PUSH_TO_OPEN) {
                hardware["مقابض أدراج"] = numberOfDrawers
            }
            if (handleStyle == HandleStyle.PUSH_TO_OPEN) {
                hardware["مكبس فتح باللمس (Push Latch)"] = numberOfDrawers
            }
        } else {
            val doorCount = if (widthMm > 650f) 2 else 1
            val carcassH = carcassHeightMm
            val hingesPerDoor = when {
                carcassH <= 1000f -> 2
                carcassH <= 1600f -> 3
                carcassH <= 2000f -> 4
                else -> 5
            }
            hardware["مفصلات هيدروليك Soft-Close 110°"] = doorCount * hingesPerDoor

            if (handleStyle != HandleStyle.J_PULL_GOLA && handleStyle != HandleStyle.PUSH_TO_OPEN) {
                hardware["مقابض أبواب"] = doorCount
            }
            if (handleStyle == HandleStyle.PUSH_TO_OPEN) {
                hardware["مكبس فتح باللمس (Push Latch)"] = doorCount
            }

            if (numberOfShelves > 0) {
                hardware["حوامل أرفف نيكل (Shelf Support Pins)"] = numberOfShelves * 4
            }
        }

        if (toeKickHeightMm > 0f) {
            hardware["أرجل بلاستيك إيطالي قابلة للتعديل ${toeKickHeightMm.toInt()} مم"] = 4
        }

        return hardware
    }

    companion object {
        fun calculateAluminumSummaryForCabinets(
            cabinets: List<ParametricCabinet>,
            wastePercentageOverride: Float? = null,
            standardBarLengthMeters: Float = 6.0f
        ): AluminumCalculationSummary {
            val allCuts = cabinets.flatMap { it.generateAluminumCuts() }
            val horizMeters = allCuts
                .filter { it.orientation == AluminumBarOrientation.HORIZONTAL }
                .sumOf { it.totalLengthMeters.toDouble() }
                .toFloat()
            val vertMeters = allCuts
                .filter { it.orientation == AluminumBarOrientation.VERTICAL }
                .sumOf { it.totalLengthMeters.toDouble() }
                .toFloat()
            val netMeters = horizMeters + vertMeters
            val avgWastePct = wastePercentageOverride
                ?: cabinets.map { it.aluminumWastePercentage }.average().toFloat().takeIf { !it.isNaN() }
                ?: 8f
            val grossMeters = netMeters * (1f + (avgWastePct.coerceAtLeast(0f) / 100f))
            val barCount = if (grossMeters <= 0f) 0 else ceil(grossMeters / standardBarLengthMeters.coerceAtLeast(1f)).toInt()

            return AluminumCalculationSummary(
                cuts = allCuts,
                horizontalTotalMeters = horizMeters,
                verticalTotalMeters = vertMeters,
                netLinearMeters = netMeters,
                wastePercentage = avgWastePct,
                grossLinearMetersWithWaste = grossMeters,
                standardBarLengthMeters = standardBarLengthMeters,
                requiredStandardBarsCount = barCount
            )
        }
    }
}
