package com.example.core.filament

import com.example.core.cam.DoorStyle
import com.example.core.cam.HandleStyle
import com.example.core.cam.PanelMaterial
import com.example.core.cam.ParametricCabinet
import com.example.core.math.AABB
import com.example.core.math.Vector3
import com.example.domain.model.ElementCategory
import com.example.domain.model.KitchenElement
import com.example.domain.model.KitchenElementType
import com.example.domain.model.MaterialFinish
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * High-level architectural style archetype for parametric cabinet mesh generation.
 */
enum class CabinetStyleArchetype(
    val id: String,
    val titleAr: String,
    val titleEn: String
) {
    MODERN_SLAB("modern", "مودرن مسطح وغولا (Modern Slab)", "Modern Minimalist Slab"),
    CLASSIC_SHAKER("classic", "كلاسيك شيكر وإطار بارز (Classic Shaker)", "Classic Shaker & Raised Panel"),
    GLASS_VITRINE("glass_door", "واجهات زجاج وألومنيوم LED (Glass-Door)", "Aluminum-Framed Glass Vitrine")
}

/**
 * Semantic role of each procedural box sub-mesh inside a parametric cabinet assembly.
 */
enum class CabinetMeshPartRole(val labelAr: String) {
    CARCASS_PANEL("ألواح جسم الخزانة (Carcass)"),
    TOE_KICK_PLINTH("الوزرة السفلية الغاطسة (Toe-Kick)"),
    SHELF_PANEL("الأرفف الداخلية (Shelves)"),
    DOOR_SLAB("لوح الدرفة أو الدرج (Door/Drawer Slab)"),
    CLASSIC_STILE_RAIL("قوائم وعوارض الإطار الكلاسيكي (Stiles & Rails)"),
    CLASSIC_CROWN_MOLDING("كورنيشة التاج والبانيل البارز (Crown & Raised Panel)"),
    GLASS_FRAME_MULLION("إطار ألومنيوم للزجاج (Aluminum Glass Frame)"),
    GLASS_PANE_TRANSLUCENT("لوح الزجاج العاكس/الشفاف (Glass Pane)"),
    LED_LIGHT_STRIP("شريط إضاءة LED دافئ 3000K (LED Strip)"),
    HANDLE_HARDWARE("مقابض ومجاري غولا (Hardware & Gola)"),
    COUNTERTOP_SLAB("سطح الرخام أو الجرانيت البارز (Countertop)")
}

/**
 * A single procedural 3D box component generated via [FilamentMeshGenerator.createBoxMesh].
 * Coordinates are in cabinet-local space (in millimeters) where (0, 0, 0) is the bottom-center of the cabinet footprint.
 */
data class CabinetSubMesh(
    val partId: String,
    val partNameAr: String,
    val role: CabinetMeshPartRole,
    val localCenter: Vector3,
    val dimensions: Vector3,
    val mesh: FilamentMesh,
    val opacity: Float = 1.0f,
    val emissiveHex: Long? = null
)

/**
 * Complete parametric cabinet 3D mesh assembly composed of procedural box sub-meshes.
 */
data class ParametricCabinetAssetMesh(
    val assetId: String,
    val titleAr: String,
    val titleEn: String,
    val archetype: CabinetStyleArchetype,
    val parametricSpec: ParametricCabinet,
    val subMeshes: List<CabinetSubMesh>,
    val boundingBox: AABB
) {
    val totalVertexCount: Int get() = subMeshes.sumOf { it.mesh.vertices.size }
    val totalTriangleCount: Int get() = subMeshes.sumOf { it.mesh.indices.size / 3 }
    val subMeshCount: Int get() = subMeshes.size

    /**
     * Merges all procedural box sub-meshes into a single combined [FilamentMesh] buffer.
     */
    fun mergeToCombinedFilamentMesh(): FilamentMesh {
        val mergedVertices = mutableListOf<FilamentVertex>()
        val mergedIndices = mutableListOf<Int>()

        for (sub in subMeshes) {
            val vertexOffset = mergedVertices.size
            mergedVertices.addAll(sub.mesh.vertices)
            for (idx in sub.mesh.indices) {
                mergedIndices.add(vertexOffset + idx)
            }
        }

        return FilamentMesh(
            vertices = mergedVertices,
            indices = mergedIndices,
            colorHex = parametricSpec.facadeMaterial.colorHex,
            roughness = parametricSpec.facadeMaterial.roughness,
            metallic = parametricSpec.facadeMaterial.metallic
        )
    }

    /**
     * Transforms all local procedural box sub-meshes into world-space [FilamentMesh] instances
     * given a floor/mounting position and Y-axis rotation in degrees.
     */
    fun transformToWorldSpace(
        worldPosition: Vector3,
        rotationYDegrees: Float = 0f
    ): List<FilamentMesh> {
        val rad = Math.toRadians(rotationYDegrees.toDouble()).toFloat()
        val cosR = cos(rad)
        val sinR = sin(rad)

        fun rotateVec(v: Vector3): Vector3 {
            return Vector3(
                x = v.x * cosR + v.z * sinR,
                y = v.y,
                z = -v.x * sinR + v.z * cosR
            )
        }

        return subMeshes.map { sub ->
            val transformedVertices = sub.mesh.vertices.map { vx ->
                FilamentVertex(
                    position = worldPosition + rotateVec(vx.position),
                    normal = rotateVec(vx.normal).normalized(),
                    u = vx.u,
                    v = vx.v
                )
            }
            sub.mesh.copy(vertices = transformedVertices)
        }
    }
}

/**
 * Pre-configured catalog preset entry in [CabinetAssetLibrary].
 */
data class CabinetLibraryPreset(
    val id: String,
    val titleAr: String,
    val titleEn: String,
    val archetype: CabinetStyleArchetype,
    val defaultCabinet: ParametricCabinet,
    val includeCountertop: Boolean = true,
    val countertopMaterial: MaterialFinish? = MaterialFinish.BLACK_GRANITE,
    val hasInternalLed: Boolean = false,
    val hasGlassMullions: Boolean = false,
    val hasClassicCrownMolding: Boolean = false
)

/**
 * Comprehensive procedural cabinet mesh library providing parametric 3D meshes across
 * three architectural families:
 * 1. **Modern (`MODERN_SLAB`)**: Handleless Gola channels, sleek slab doors, multi-drawer stacks, Aventos lift doors, and waterfall islands.
 * 2. **Classic (`CLASSIC_SHAKER`)**: 5-piece Shaker stile-and-rail frames, beveled center panels, raised panel moldings, crown cornices, and brass hardware.
 * 3. **Glass-Door (`GLASS_VITRINE`)**: Anodized black/bronze aluminum frames, smoked/clear glass panes, French mullion grids, glass shelves, and warm 3000K vertical/horizontal LED strips.
 *
 * Every sub-component is generated via [FilamentMeshGenerator.createBoxMesh] for 100% compatibility
 * with our procedural box generator and offline PBR/3D pipelines.
 */
object CabinetAssetLibrary {

    private const val CARCASS_WHITE_HEX = 0xFFF1F5F9L
    private const val TOE_KICK_BLACK_HEX = 0xFF111827L
    private const val ALU_ANODIZED_BLACK_HEX = 0xFF18181BL
    private const val SMOKED_GLASS_HEX = 0xFF1E293BL
    private const val WARM_LED_3000K_HEX = 0xFFFDE047L
    private const val STAINLESS_HARDWARE_HEX = 0xFFE2E8F0L
    private const val BRASS_HARDWARE_HEX = 0xFFD4AF37L

    /**
     * Returns the comprehensive collection of 20 ready-to-use parametric cabinet presets
     * spanning Modern, Classic Shaker/Raised, and Glass-Door Vitrine styles.
     */
    fun getPresetCatalog(): List<CabinetLibraryPreset> = listOf(
        // ==========================================
        // 1. MODERN COLLECTION (مودرن مسطح وغولا)
        // ==========================================
        CabinetLibraryPreset(
            id = "modern_base_gola_60",
            titleAr = "خزانة سفلية مودرن غولا (60 سم)",
            titleEn = "Modern Gola Base 60cm",
            archetype = CabinetStyleArchetype.MODERN_SLAB,
            defaultCabinet = ParametricCabinet(
                id = "modern_base_gola_60",
                name = "خزانة سفلية مودرن غولا (60 سم)",
                widthMm = 600f,
                heightMm = 850f,
                depthMm = 600f,
                numberOfShelves = 1,
                numberOfDrawers = 0,
                doorStyle = DoorStyle.SLAB_MODERN,
                handleStyle = HandleStyle.J_PULL_GOLA,
                facadeMaterial = MaterialFinish.CHAMPAGNE_GLOSS
            ),
            includeCountertop = true,
            countertopMaterial = MaterialFinish.BLACK_GRANITE
        ),
        CabinetLibraryPreset(
            id = "modern_base_double_90",
            titleAr = "خزانة سفلية مودرن درفتين (90 سم)",
            titleEn = "Modern Double-Door Base 90cm",
            archetype = CabinetStyleArchetype.MODERN_SLAB,
            defaultCabinet = ParametricCabinet(
                id = "modern_base_double_90",
                name = "خزانة سفلية مودرن درفتين (90 سم)",
                widthMm = 900f,
                heightMm = 850f,
                depthMm = 600f,
                numberOfShelves = 1,
                numberOfDrawers = 0,
                doorStyle = DoorStyle.SLAB_MODERN,
                handleStyle = HandleStyle.BAR_PULL,
                facadeMaterial = MaterialFinish.NATURAL_OAK
            ),
            includeCountertop = true,
            countertopMaterial = MaterialFinish.BLACK_GRANITE
        ),
        CabinetLibraryPreset(
            id = "modern_drawers_3_60",
            titleAr = "وحدة 3 أدراج سحب مودرن (60 سم)",
            titleEn = "Modern 3-Drawer Stack 60cm",
            archetype = CabinetStyleArchetype.MODERN_SLAB,
            defaultCabinet = ParametricCabinet(
                id = "modern_drawers_3_60",
                name = "وحدة 3 أدراج سحب مودرن (60 سم)",
                widthMm = 600f,
                heightMm = 850f,
                depthMm = 600f,
                numberOfShelves = 0,
                numberOfDrawers = 3,
                doorStyle = DoorStyle.SLAB_MODERN,
                handleStyle = HandleStyle.J_PULL_GOLA,
                facadeMaterial = MaterialFinish.CAPPUCCINO_MATTE
            ),
            includeCountertop = true,
            countertopMaterial = MaterialFinish.BLACK_GRANITE
        ),
        CabinetLibraryPreset(
            id = "modern_drawers_4_60",
            titleAr = "وحدة 4 أدراج للملاعق والتخزين (60 سم)",
            titleEn = "Modern 4-Drawer Cutlery Unit 60cm",
            archetype = CabinetStyleArchetype.MODERN_SLAB,
            defaultCabinet = ParametricCabinet(
                id = "modern_drawers_4_60",
                name = "وحدة 4 أدراج للملاعق والتخزين (60 سم)",
                widthMm = 600f,
                heightMm = 850f,
                depthMm = 600f,
                numberOfShelves = 0,
                numberOfDrawers = 4,
                doorStyle = DoorStyle.SLAB_MODERN,
                handleStyle = HandleStyle.BAR_PULL,
                facadeMaterial = MaterialFinish.NATURAL_OAK
            ),
            includeCountertop = true,
            countertopMaterial = MaterialFinish.BLACK_GRANITE
        ),
        CabinetLibraryPreset(
            id = "modern_fluted_island_120",
            titleAr = "وحدة جزيرة مضلعة حديثة (Fluted 120 سم)",
            titleEn = "Modern Fluted Island Base 120cm",
            archetype = CabinetStyleArchetype.MODERN_SLAB,
            defaultCabinet = ParametricCabinet(
                id = "modern_fluted_island_120",
                name = "وحدة جزيرة مضلعة حديثة (120 سم)",
                widthMm = 1200f,
                heightMm = 880f,
                depthMm = 800f,
                numberOfShelves = 1,
                numberOfDrawers = 2,
                doorStyle = DoorStyle.FLUTED_REEDED,
                handleStyle = HandleStyle.J_PULL_GOLA,
                facadeMaterial = MaterialFinish.WALNUT_DARK
            ),
            includeCountertop = true,
            countertopMaterial = MaterialFinish.CALACATTA_GOLD
        ),
        CabinetLibraryPreset(
            id = "modern_wall_aventos_90",
            titleAr = "خزانة علوية قلاب Aventos مع LED (90 سم)",
            titleEn = "Modern Lift-Up Wall Unit 90cm",
            archetype = CabinetStyleArchetype.MODERN_SLAB,
            defaultCabinet = ParametricCabinet(
                id = "modern_wall_aventos_90",
                name = "خزانة علوية قلاب Aventos (90 سم)",
                widthMm = 900f,
                heightMm = 720f,
                depthMm = 350f,
                toeKickHeightMm = 0f,
                numberOfShelves = 1,
                numberOfDrawers = 2, // 2 horizontal flip panels
                doorStyle = DoorStyle.SLAB_MODERN,
                handleStyle = HandleStyle.PUSH_TO_OPEN,
                facadeMaterial = MaterialFinish.CHARCOAL_MATTE
            ),
            includeCountertop = false,
            countertopMaterial = null,
            hasInternalLed = true
        ),
        CabinetLibraryPreset(
            id = "modern_bridge_crown_120",
            titleAr = "خزانة تاج علوية عميقة للسقف (120 سم)",
            titleEn = "Modern Deep Top Bridge 120cm",
            archetype = CabinetStyleArchetype.MODERN_SLAB,
            defaultCabinet = ParametricCabinet(
                id = "modern_bridge_crown_120",
                name = "خزانة تاج علوية عميقة للسقف (120 سم)",
                widthMm = 1200f,
                heightMm = 500f,
                depthMm = 580f,
                toeKickHeightMm = 0f,
                numberOfShelves = 0,
                numberOfDrawers = 0,
                doorStyle = DoorStyle.SLAB_MODERN,
                handleStyle = HandleStyle.PUSH_TO_OPEN,
                facadeMaterial = MaterialFinish.CHARCOAL_MATTE
            ),
            includeCountertop = false,
            countertopMaterial = null
        ),
        CabinetLibraryPreset(
            id = "modern_tall_pantry_60",
            titleAr = "دولاب مؤونة عمودي مودرن (60 سم)",
            titleEn = "Modern Tall Pantry 60cm",
            archetype = CabinetStyleArchetype.MODERN_SLAB,
            defaultCabinet = ParametricCabinet(
                id = "modern_tall_pantry_60",
                name = "دولاب مؤونة عمودي مودرن (60 سم)",
                widthMm = 600f,
                heightMm = 2170f,
                depthMm = 600f,
                toeKickHeightMm = 100f,
                numberOfShelves = 4,
                numberOfDrawers = 0,
                doorStyle = DoorStyle.SLAB_MODERN,
                handleStyle = HandleStyle.BAR_PULL,
                facadeMaterial = MaterialFinish.CHAMPAGNE_GLOSS
            ),
            includeCountertop = false,
            countertopMaterial = null
        ),

        // ==========================================
        // 2. CLASSIC COLLECTION (كلاسيك شيكر وبارز)
        // ==========================================
        CabinetLibraryPreset(
            id = "classic_shaker_base_60",
            titleAr = "خزانة سفلية شيكر كلاسيك (60 سم)",
            titleEn = "Classic Shaker Base 60cm",
            archetype = CabinetStyleArchetype.CLASSIC_SHAKER,
            defaultCabinet = ParametricCabinet(
                id = "classic_shaker_base_60",
                name = "خزانة سفلية شيكر كلاسيك (60 سم)",
                widthMm = 600f,
                heightMm = 850f,
                depthMm = 600f,
                numberOfShelves = 1,
                numberOfDrawers = 0,
                doorStyle = DoorStyle.SHAKER_BEVEL,
                handleStyle = HandleStyle.BAR_PULL,
                facadeMaterial = MaterialFinish.SAGE_GREEN
            ),
            includeCountertop = true,
            countertopMaterial = MaterialFinish.CARRARA_MARBLE
        ),
        CabinetLibraryPreset(
            id = "classic_shaker_double_90",
            titleAr = "خزانة سفلية شيكر درفتين (90 سم)",
            titleEn = "Classic Shaker Double Base 90cm",
            archetype = CabinetStyleArchetype.CLASSIC_SHAKER,
            defaultCabinet = ParametricCabinet(
                id = "classic_shaker_double_90",
                name = "خزانة سفلية شيكر درفتين (90 سم)",
                widthMm = 900f,
                heightMm = 850f,
                depthMm = 600f,
                numberOfShelves = 1,
                numberOfDrawers = 0,
                doorStyle = DoorStyle.SHAKER_BEVEL,
                handleStyle = HandleStyle.MINIMAL_KNOB,
                facadeMaterial = MaterialFinish.NAVY_BLUE
            ),
            includeCountertop = true,
            countertopMaterial = MaterialFinish.CALACATTA_GOLD
        ),
        CabinetLibraryPreset(
            id = "classic_raised_drawers_80",
            titleAr = "وحدة أدراج كلاسيك بانيل بارز (80 سم)",
            titleEn = "Classic Raised-Panel 3-Drawers 80cm",
            archetype = CabinetStyleArchetype.CLASSIC_SHAKER,
            defaultCabinet = ParametricCabinet(
                id = "classic_raised_drawers_80",
                name = "وحدة أدراج كلاسيك بانيل بارز (80 سم)",
                widthMm = 800f,
                heightMm = 850f,
                depthMm = 600f,
                numberOfShelves = 0,
                numberOfDrawers = 3,
                doorStyle = DoorStyle.CLASSIC_RAISED,
                handleStyle = HandleStyle.BAR_PULL,
                facadeMaterial = MaterialFinish.ASH_WOOD
            ),
            includeCountertop = true,
            countertopMaterial = MaterialFinish.CARRARA_MARBLE
        ),
        CabinetLibraryPreset(
            id = "classic_wall_crown_90",
            titleAr = "خزانة علوية كلاسيك بكورنيشة تاج (90 سم)",
            titleEn = "Classic Crown Wall Cabinet 90cm",
            archetype = CabinetStyleArchetype.CLASSIC_SHAKER,
            defaultCabinet = ParametricCabinet(
                id = "classic_wall_crown_90",
                name = "خزانة علوية كلاسيك بكورنيشة تاج (90 سم)",
                widthMm = 900f,
                heightMm = 760f,
                depthMm = 360f,
                toeKickHeightMm = 0f,
                numberOfShelves = 2,
                numberOfDrawers = 0,
                doorStyle = DoorStyle.CLASSIC_RAISED,
                handleStyle = HandleStyle.MINIMAL_KNOB,
                facadeMaterial = MaterialFinish.WHITE_HIGH_GLOSS
            ),
            includeCountertop = false,
            countertopMaterial = null,
            hasClassicCrownMolding = true
        ),
        CabinetLibraryPreset(
            id = "classic_tall_larder_60",
            titleAr = "دولاب مؤونة كلاسيك بتاج علوي (60 سم)",
            titleEn = "Classic Tall Larder with Crown 60cm",
            archetype = CabinetStyleArchetype.CLASSIC_SHAKER,
            defaultCabinet = ParametricCabinet(
                id = "classic_tall_larder_60",
                name = "دولاب مؤونة كلاسيك بتاج علوي (60 سم)",
                widthMm = 600f,
                heightMm = 2200f,
                depthMm = 600f,
                toeKickHeightMm = 100f,
                numberOfShelves = 4,
                numberOfDrawers = 0,
                doorStyle = DoorStyle.CLASSIC_RAISED,
                handleStyle = HandleStyle.BAR_PULL,
                facadeMaterial = MaterialFinish.SAGE_GREEN
            ),
            includeCountertop = false,
            countertopMaterial = null,
            hasClassicCrownMolding = true
        ),

        // ==========================================
        // 3. GLASS-DOOR COLLECTION (زجاج وفيترينة LED)
        // ==========================================
        CabinetLibraryPreset(
            id = "glass_wall_alu_80",
            titleAr = "خزانة علوية زجاج بإطار ألومنيوم أسود و LED (80 سم)",
            titleEn = "Black-Framed Glass Wall Cabinet 80cm",
            archetype = CabinetStyleArchetype.GLASS_VITRINE,
            defaultCabinet = ParametricCabinet(
                id = "glass_wall_alu_80",
                name = "خزانة علوية زجاج بإطار ألومنيوم أسود و LED (80 سم)",
                widthMm = 800f,
                heightMm = 720f,
                depthMm = 350f,
                toeKickHeightMm = 0f,
                numberOfShelves = 2,
                numberOfDrawers = 0,
                doorStyle = DoorStyle.ALUMINUM_GLASS,
                handleStyle = HandleStyle.EDGE_FINGER_PULL,
                facadeMaterial = MaterialFinish.SMOKED_GLASS_ALU
            ),
            includeCountertop = false,
            countertopMaterial = null,
            hasInternalLed = true
        ),
        CabinetLibraryPreset(
            id = "glass_wall_mullion_90",
            titleAr = "خزانة علوية زجاج مقسم كلاسيك (Mullion 90 سم)",
            titleEn = "French Mullion Glass Wall Cabinet 90cm",
            archetype = CabinetStyleArchetype.GLASS_VITRINE,
            defaultCabinet = ParametricCabinet(
                id = "glass_wall_mullion_90",
                name = "خزانة علوية زجاج مقسم (90 سم)",
                widthMm = 900f,
                heightMm = 750f,
                depthMm = 350f,
                toeKickHeightMm = 0f,
                numberOfShelves = 2,
                numberOfDrawers = 0,
                doorStyle = DoorStyle.ALUMINUM_GLASS,
                handleStyle = HandleStyle.MINIMAL_KNOB,
                facadeMaterial = MaterialFinish.SMOKED_GLASS_ALU
            ),
            includeCountertop = false,
            countertopMaterial = null,
            hasInternalLed = true,
            hasGlassMullions = true
        ),
        CabinetLibraryPreset(
            id = "glass_bridge_led_90",
            titleAr = "خزانة تاج علوية زجاجية مضاءة LED (90 سم)",
            titleEn = "Illuminated Glass Bridge Cabinet 90cm",
            archetype = CabinetStyleArchetype.GLASS_VITRINE,
            defaultCabinet = ParametricCabinet(
                id = "glass_bridge_led_90",
                name = "خزانة تاج علوية زجاجية مضاءة LED (90 سم)",
                widthMm = 900f,
                heightMm = 500f,
                depthMm = 580f,
                toeKickHeightMm = 0f,
                numberOfShelves = 1,
                numberOfDrawers = 0,
                doorStyle = DoorStyle.ALUMINUM_GLASS,
                handleStyle = HandleStyle.PUSH_TO_OPEN,
                facadeMaterial = MaterialFinish.SMOKED_GLASS_ALU
            ),
            includeCountertop = false,
            countertopMaterial = null,
            hasInternalLed = true
        ),
        CabinetLibraryPreset(
            id = "glass_tall_vitrine_60",
            titleAr = "فيترينة عرض طولية زجاج أسود وشريط LED (60 سم)",
            titleEn = "Tall Smoked Glass Vitrine with LED 60cm",
            archetype = CabinetStyleArchetype.GLASS_VITRINE,
            defaultCabinet = ParametricCabinet(
                id = "glass_tall_vitrine_60",
                name = "فيترينة عرض طولية زجاج أسود وشريط LED (60 سم)",
                widthMm = 600f,
                heightMm = 2400f,
                depthMm = 580f,
                toeKickHeightMm = 80f,
                numberOfShelves = 4,
                numberOfDrawers = 0,
                doorStyle = DoorStyle.ALUMINUM_GLASS,
                handleStyle = HandleStyle.BAR_PULL,
                facadeMaterial = MaterialFinish.SMOKED_GLASS_ALU
            ),
            includeCountertop = false,
            countertopMaterial = null,
            hasInternalLed = true
        ),
        CabinetLibraryPreset(
            id = "glass_tall_double_vitrine_90",
            titleAr = "فيترينة عرض مزدوجة زجاج فحمي وإضاءة LED (90 سم)",
            titleEn = "Double-Door Tall Glass Vitrine with LED 90cm",
            archetype = CabinetStyleArchetype.GLASS_VITRINE,
            defaultCabinet = ParametricCabinet(
                id = "glass_tall_double_vitrine_90",
                name = "فيترينة عرض مزدوجة زجاج فحمي وإضاءة LED (90 سم)",
                widthMm = 900f,
                heightMm = 2400f,
                depthMm = 580f,
                toeKickHeightMm = 80f,
                numberOfShelves = 4,
                numberOfDrawers = 0,
                doorStyle = DoorStyle.ALUMINUM_GLASS,
                handleStyle = HandleStyle.BAR_PULL,
                facadeMaterial = MaterialFinish.SMOKED_GLASS_ALU
            ),
            includeCountertop = false,
            countertopMaterial = null,
            hasInternalLed = true
        ),
        CabinetLibraryPreset(
            id = "glass_appliance_garage_60",
            titleAr = "ركن قهوة وجراج أجهزة بواجهة زجاج و LED (60 سم)",
            titleEn = "Glass-Front Coffee & Appliance Garage 60cm",
            archetype = CabinetStyleArchetype.GLASS_VITRINE,
            defaultCabinet = ParametricCabinet(
                id = "glass_appliance_garage_60",
                name = "ركن قهوة وجراج أجهزة بواجهة زجاج و LED (60 سم)",
                widthMm = 600f,
                heightMm = 600f,
                depthMm = 380f,
                toeKickHeightMm = 0f,
                numberOfShelves = 1,
                numberOfDrawers = 0,
                doorStyle = DoorStyle.ALUMINUM_GLASS,
                handleStyle = HandleStyle.EDGE_FINGER_PULL,
                facadeMaterial = MaterialFinish.SMOKED_GLASS_ALU
            ),
            includeCountertop = false,
            countertopMaterial = null,
            hasInternalLed = true
        )
    )

    /**
     * Filter presets by architectural style archetype (Modern, Classic, Glass-Door).
     */
    fun getPresetsByArchetype(archetype: CabinetStyleArchetype): List<CabinetLibraryPreset> {
        return getPresetCatalog().filter { it.archetype == archetype }
    }

    /**
     * Generates a complete [ParametricCabinetAssetMesh] from a [CabinetLibraryPreset].
     */
    fun generateFromPreset(preset: CabinetLibraryPreset): ParametricCabinetAssetMesh {
        return generateCabinetAssetMesh(
            cabinet = preset.defaultCabinet,
            includeCountertop = preset.includeCountertop,
            countertopMaterial = preset.countertopMaterial,
            hasInternalLed = preset.hasInternalLed || preset.archetype == CabinetStyleArchetype.GLASS_VITRINE,
            hasGlassMullions = preset.hasGlassMullions,
            hasClassicCrownMolding = preset.hasClassicCrownMolding
        )
    }

    /**
     * Converts any [KitchenElement] from the scene into a detailed [ParametricCabinetAssetMesh].
     */
    fun fromKitchenElement(element: KitchenElement): ParametricCabinetAssetMesh {
        val isWallOrBridge = element.type.category == ElementCategory.WALL_CABINET ||
                element.type.category == ElementCategory.BRIDGE_CABINET ||
                element.position.y >= 800f

        val isGlass = element.type == KitchenElementType.TALL_GLASS_VITRINE_LED ||
                element.type == KitchenElementType.TALL_DOUBLE_VITRINE_LED ||
                element.type == KitchenElementType.WALL_GLASS_DOOR ||
                element.type == KitchenElementType.WALL_GLASS_BLACK_FRAME ||
                element.type == KitchenElementType.BRIDGE_GLASS_LED_90

        val drawerCount = when (element.type) {
            KitchenElementType.BASE_2_DEEP_DRAWERS -> 2
            KitchenElementType.BASE_3_DRAWERS, KitchenElementType.BASE_3_DRAWERS_90 -> 3
            KitchenElementType.BASE_4_DRAWERS -> 4
            KitchenElementType.WALL_LIFT_AVENTOS -> 2
            else -> 0
        }

        val shelfCount = when {
            drawerCount > 0 -> 0
            element.dimensions.y >= 1800f -> 4
            element.dimensions.y >= 700f -> 2
            else -> 1
        }

        val doorStyle = when {
            isGlass -> DoorStyle.ALUMINUM_GLASS
            element.material.id.contains("sage") || element.material.id.contains("navy") -> DoorStyle.SHAKER_BEVEL
            else -> DoorStyle.SLAB_MODERN
        }

        val handleStyle = when {
            element.type.category == ElementCategory.BRIDGE_CABINET -> HandleStyle.PUSH_TO_OPEN
            element.material.id.contains("champagne") || element.material.id.contains("gloss") -> HandleStyle.J_PULL_GOLA
            else -> HandleStyle.BAR_PULL
        }

        val parametric = ParametricCabinet(
            id = element.id,
            name = element.name,
            widthMm = element.dimensions.x,
            heightMm = element.dimensions.y,
            depthMm = element.dimensions.z,
            toeKickHeightMm = if (isWallOrBridge) 0f else 100f,
            numberOfShelves = shelfCount,
            numberOfDrawers = drawerCount,
            doorStyle = doorStyle,
            handleStyle = handleStyle,
            facadeMaterial = element.material
        )

        return generateCabinetAssetMesh(
            cabinet = parametric,
            includeCountertop = element.countertopMaterial != null,
            countertopMaterial = element.countertopMaterial,
            hasInternalLed = isGlass || element.type.category == ElementCategory.WALL_CABINET
        )
    }

    /**
     * Core procedural mesh builder: decomposes a [ParametricCabinet] into a list of
     * precision 3D box sub-meshes using [FilamentMeshGenerator.createBoxMesh].
     *
     * Supports:
     * - **Modern (`SLAB_MODERN`, `FLUTED_REEDED`)**: Crisp slab doors/drawers, recessed Gola channels or vertical flutes, bar handles.
     * - **Classic (`SHAKER_BEVEL`, `CLASSIC_RAISED`)**: 5-piece Shaker stile & rail frames, recessed/raised center panels, optional top crown molding.
     * - **Glass-Door (`ALUMINUM_GLASS`)**: Mitered black aluminum perimeter frames, translucent smoked glass panes, optional French mullions, glass shelves, and warm 3000K vertical/under-cabinet LED emitter strips.
     */
    fun generateCabinetAssetMesh(
        cabinet: ParametricCabinet,
        includeCountertop: Boolean = false,
        countertopMaterial: MaterialFinish? = MaterialFinish.BLACK_GRANITE,
        hasInternalLed: Boolean = cabinet.doorStyle == DoorStyle.ALUMINUM_GLASS,
        hasGlassMullions: Boolean = false,
        hasClassicCrownMolding: Boolean = false
    ): ParametricCabinetAssetMesh {
        val subMeshes = mutableListOf<CabinetSubMesh>()

        val w = cabinet.widthMm
        val h = cabinet.heightMm
        val d = cabinet.depthMm
        val t = cabinet.panelThicknessMm
        val backT = cabinet.backPanelThicknessMm
        val toeH = cabinet.toeKickHeightMm.coerceIn(0f, h * 0.25f)
        val carcassH = (h - toeH).coerceAtLeast(100f)
        val doorT = 18f

        val archetype = when (cabinet.doorStyle) {
            DoorStyle.ALUMINUM_GLASS -> CabinetStyleArchetype.GLASS_VITRINE
            DoorStyle.SHAKER_BEVEL, DoorStyle.CLASSIC_RAISED -> CabinetStyleArchetype.CLASSIC_SHAKER
            else -> CabinetStyleArchetype.MODERN_SLAB
        }

        // Carcass body occupies Z from [-d/2] to [d/2 - doorT]
        val carcassDepth = (d - doorT).coerceAtLeast(150f)
        val carcassCenterZ = -doorT * 0.5f
        val carcassBottomY = toeH
        val carcassTopY = toeH + carcassH

        val carcassColorHex = if (archetype == CabinetStyleArchetype.GLASS_VITRINE) {
            0xFF1E252DL // Dark anthracite interior for luxury glass vitrines
        } else {
            CARCASS_WHITE_HEX
        }

        fun addBoxSubMesh(
            id: String,
            nameAr: String,
            role: CabinetMeshPartRole,
            center: Vector3,
            dims: Vector3,
            colorHex: Long,
            roughness: Float = 0.5f,
            metallic: Float = 0.0f,
            opacity: Float = 1.0f,
            emissiveHex: Long? = null
        ) {
            val safeDims = Vector3(
                dims.x.coerceAtLeast(2f),
                dims.y.coerceAtLeast(2f),
                dims.z.coerceAtLeast(2f)
            )
            val boxMesh = FilamentMeshGenerator.createBoxMesh(
                center = center,
                dimensions = safeDims,
                colorHex = colorHex,
                roughness = roughness,
                metallic = metallic
            )
            subMeshes.add(
                CabinetSubMesh(
                    partId = id,
                    partNameAr = nameAr,
                    role = role,
                    localCenter = center,
                    dimensions = safeDims,
                    mesh = boxMesh,
                    opacity = opacity,
                    emissiveHex = emissiveHex
                )
            )
        }

        // ====================================================
        // 1. RECESSED TOE-KICK PLINTH (الوزرة السفلية الغاطسة)
        // ====================================================
        if (toeH > 0f) {
            val plinthRecess = 45f
            val plinthDepth = (carcassDepth - plinthRecess).coerceAtLeast(100f)
            addBoxSubMesh(
                id = "${cabinet.id}_toe_kick",
                nameAr = "وزرة سفلية سوداء غاطسة",
                role = CabinetMeshPartRole.TOE_KICK_PLINTH,
                center = Vector3(0f, toeH * 0.5f, -plinthRecess * 0.5f - doorT * 0.5f),
                dims = Vector3(w - 4f, toeH, plinthDepth),
                colorHex = TOE_KICK_BLACK_HEX,
                roughness = 0.75f,
                metallic = 0.08f
            )
        }

        // ====================================================
        // 2. CARCASS PANELS (الجوانب، القاعدة، السقف، الظهر)
        // ====================================================
        val internalW = (w - t * 2f).coerceAtLeast(60f)

        // Left Gable (-X)
        addBoxSubMesh(
            id = "${cabinet.id}_gable_left",
            nameAr = "جنب خزانة أيسر",
            role = CabinetMeshPartRole.CARCASS_PANEL,
            center = Vector3(-w * 0.5f + t * 0.5f, carcassBottomY + carcassH * 0.5f, carcassCenterZ),
            dims = Vector3(t, carcassH, carcassDepth),
            colorHex = cabinet.facadeMaterial.colorHex,
            roughness = cabinet.facadeMaterial.roughness,
            metallic = cabinet.facadeMaterial.metallic
        )

        // Right Gable (+X)
        addBoxSubMesh(
            id = "${cabinet.id}_gable_right",
            nameAr = "جنب خزانة أيمن",
            role = CabinetMeshPartRole.CARCASS_PANEL,
            center = Vector3(w * 0.5f - t * 0.5f, carcassBottomY + carcassH * 0.5f, carcassCenterZ),
            dims = Vector3(t, carcassH, carcassDepth),
            colorHex = cabinet.facadeMaterial.colorHex,
            roughness = cabinet.facadeMaterial.roughness,
            metallic = cabinet.facadeMaterial.metallic
        )

        // Bottom Deck
        addBoxSubMesh(
            id = "${cabinet.id}_bottom_deck",
            nameAr = "قاعدة الخزانة السفلية",
            role = CabinetMeshPartRole.CARCASS_PANEL,
            center = Vector3(0f, carcassBottomY + t * 0.5f, carcassCenterZ),
            dims = Vector3(internalW, t, carcassDepth),
            colorHex = carcassColorHex,
            roughness = 0.6f
        )

        // Top Stretchers / Top Deck (سخات الميلامين العلوية الرابطة أو السقف)
        val stretcherW = cabinet.topStretcherWidthMm.coerceIn(50f, carcassDepth * 0.45f)
        if (!cabinet.hasBackPanel || cabinet.topStretcherCount >= 2) {
            // Front Top Stretcher (سخة أمامية)
            addBoxSubMesh(
                id = "${cabinet.id}_top_stretcher_front",
                nameAr = "سخة ميلامين علوية أمامية (${stretcherW.toInt()} مم)",
                role = CabinetMeshPartRole.CARCASS_PANEL,
                center = Vector3(0f, carcassTopY - t * 0.5f, carcassCenterZ + carcassDepth * 0.5f - stretcherW * 0.5f),
                dims = Vector3(internalW, t, stretcherW),
                colorHex = carcassColorHex,
                roughness = 0.6f
            )
            // Rear Top Stretcher (سخة خلفية)
            addBoxSubMesh(
                id = "${cabinet.id}_top_stretcher_rear",
                nameAr = "سخة ميلامين علوية خلفية (${stretcherW.toInt()} مم)",
                role = CabinetMeshPartRole.CARCASS_PANEL,
                center = Vector3(0f, carcassTopY - t * 0.5f, carcassCenterZ - carcassDepth * 0.5f + stretcherW * 0.5f),
                dims = Vector3(internalW, t, stretcherW),
                colorHex = carcassColorHex,
                roughness = 0.6f
            )
        } else {
            addBoxSubMesh(
                id = "${cabinet.id}_top_deck",
                nameAr = "سقف الخزانة العلوي",
                role = CabinetMeshPartRole.CARCASS_PANEL,
                center = Vector3(0f, carcassTopY - t * 0.5f, carcassCenterZ),
                dims = Vector3(internalW, t, carcassDepth),
                colorHex = carcassColorHex,
                roughness = 0.6f
            )
        }

        // Back Panel (-Z): Only generated when hasBackPanel == true; hollowed out immediately when false
        if (cabinet.hasBackPanel && backT > 0f) {
            val grooveOverlap = cabinet.backGrooveInsetMm * 2f
            addBoxSubMesh(
                id = "${cabinet.id}_back_panel",
                nameAr = "ظهر الخزانة الخلفي (تداخل ريشة ${cabinet.backGrooveInsetMm.toInt()} مم)",
                role = CabinetMeshPartRole.CARCASS_PANEL,
                center = Vector3(0f, carcassBottomY + carcassH * 0.5f, -d * 0.5f + backT * 0.5f + cabinet.backGrooveInsetMm),
                dims = Vector3(internalW + grooveOverlap, (carcassH - t * 2f) + grooveOverlap, backT),
                colorHex = carcassColorHex,
                roughness = 0.7f
            )
        }

        // ====================================================
        // 3. INTERNAL SHELVES (أرفف خشبية أو زجاجية)
        // ====================================================
        if (cabinet.numberOfShelves > 0 && cabinet.numberOfDrawers == 0) {
            val usableH = carcassH - t * 2f
            val shelfStep = usableH / (cabinet.numberOfShelves + 1)
            val isGlassShelf = archetype == CabinetStyleArchetype.GLASS_VITRINE
            val shelfThickness = if (isGlassShelf) 8f else t
            val shelfDepth = (carcassDepth - backT - 25f).coerceAtLeast(80f)

            for (s in 1..cabinet.numberOfShelves) {
                val shelfY = carcassBottomY + t + shelfStep * s
                addBoxSubMesh(
                    id = "${cabinet.id}_shelf_$s",
                    nameAr = if (isGlassShelf) "رف زجاجي داخلي $s" else "رف داخلي متحرك $s",
                    role = CabinetMeshPartRole.SHELF_PANEL,
                    center = Vector3(0f, shelfY, carcassCenterZ),
                    dims = Vector3(internalW - 4f, shelfThickness, shelfDepth),
                    colorHex = if (isGlassShelf) 0xFF93C5FDL else carcassColorHex,
                    roughness = if (isGlassShelf) 0.05f else 0.55f,
                    metallic = if (isGlassShelf) 0.1f else 0.0f,
                    opacity = if (isGlassShelf) 0.45f else 1.0f
                )
            }
        }

        // ====================================================
        // 4. FRONT DOORS / DRAWERS BY ARCHETYPE
        //    (Modern Slab, Classic Shaker/Raised, Glass Vitrine)
        // ====================================================
        val revealGap = 3f
        val frontZ = d * 0.5f - doorT * 0.5f

        if (cabinet.numberOfDrawers > 0) {
            // Drawer stack (or horizontal flip panels)
            val totalFacadeH = carcassH - revealGap * 2f
            val singleDrawerH = (totalFacadeH / cabinet.numberOfDrawers) - revealGap
            val drawerW = w - revealGap * 2f

            for (dr in 0 until cabinet.numberOfDrawers) {
                val centerY = carcassBottomY + revealGap + singleDrawerH * 0.5f + dr * (singleDrawerH + revealGap)
                buildDoorOrDrawerPanelMeshes(
                    panelIdPrefix = "${cabinet.id}_drawer_${dr + 1}",
                    panelLabelAr = "واجهة درج ${dr + 1}",
                    centerX = 0f,
                    centerY = centerY,
                    frontZ = frontZ,
                    panelW = drawerW,
                    panelH = singleDrawerH,
                    doorT = doorT,
                    cabinet = cabinet,
                    hasGlassMullions = hasGlassMullions,
                    isHorizontalHandle = true,
                    hingeLeft = true,
                    addBox = ::addBoxSubMesh
                )
            }
        } else {
            // 1, 2, or 3 vertical doors based on cabinet width
            val doorCount = when {
                w >= 1100f -> 3
                w > 650f -> 2
                else -> 1
            }
            val doorH = carcassH - revealGap * 2f
            val singleDoorW = ((w - revealGap * (doorCount + 1)) / doorCount).coerceAtLeast(80f)
            val doorCenterY = carcassBottomY + carcassH * 0.5f

            for (idx in 0 until doorCount) {
                val centerX = -w * 0.5f + revealGap + singleDoorW * 0.5f + idx * (singleDoorW + revealGap)
                val hingeLeft = idx == 0
                buildDoorOrDrawerPanelMeshes(
                    panelIdPrefix = "${cabinet.id}_door_${idx + 1}",
                    panelLabelAr = "درفة باب ${idx + 1}",
                    centerX = centerX,
                    centerY = doorCenterY,
                    frontZ = frontZ,
                    panelW = singleDoorW,
                    panelH = doorH,
                    doorT = doorT,
                    cabinet = cabinet,
                    hasGlassMullions = hasGlassMullions,
                    isHorizontalHandle = false,
                    hingeLeft = hingeLeft,
                    addBox = ::addBoxSubMesh
                )
            }
        }

        // ====================================================
        // 5. CLASSIC CROWN MOLDING (كورنيشة التاج الكلاسيكية)
        // ====================================================
        if (hasClassicCrownMolding || cabinet.doorStyle == DoorStyle.CLASSIC_RAISED) {
            val crownH = 36f
            val crownOverhang = 24f
            addBoxSubMesh(
                id = "${cabinet.id}_classic_crown",
                nameAr = "حلية كورنيشة تاج كلاسيكية",
                role = CabinetMeshPartRole.CLASSIC_CROWN_MOLDING,
                center = Vector3(0f, carcassTopY + crownH * 0.5f, crownOverhang * 0.25f),
                dims = Vector3(w + crownOverhang * 2f, crownH, d + crownOverhang),
                colorHex = cabinet.facadeMaterial.colorHex,
                roughness = cabinet.facadeMaterial.roughness,
                metallic = cabinet.facadeMaterial.metallic
            )
        }

        // ====================================================
        // 6. WARM 3000K LED LIGHT STRIPS (شرائط إضاءة LED)
        // ====================================================
        if (hasInternalLed) {
            if (archetype == CabinetStyleArchetype.GLASS_VITRINE) {
                // Left & Right vertical warm LED strips inside the vitrine frame
                val ledH = (carcassH - t * 2f - 20f).coerceAtLeast(80f)
                val ledZ = frontZ - doorT - 14f
                addBoxSubMesh(
                    id = "${cabinet.id}_led_vertical_left",
                    nameAr = "شريط LED رأسي أيسر 3000K",
                    role = CabinetMeshPartRole.LED_LIGHT_STRIP,
                    center = Vector3(-w * 0.5f + t + 10f, carcassBottomY + carcassH * 0.5f, ledZ),
                    dims = Vector3(10f, ledH, 8f),
                    colorHex = WARM_LED_3000K_HEX,
                    roughness = 0.05f,
                    emissiveHex = WARM_LED_3000K_HEX
                )
                addBoxSubMesh(
                    id = "${cabinet.id}_led_vertical_right",
                    nameAr = "شريط LED رأسي أيمن 3000K",
                    role = CabinetMeshPartRole.LED_LIGHT_STRIP,
                    center = Vector3(w * 0.5f - t - 10f, carcassBottomY + carcassH * 0.5f, ledZ),
                    dims = Vector3(10f, ledH, 8f),
                    colorHex = WARM_LED_3000K_HEX,
                    roughness = 0.05f,
                    emissiveHex = WARM_LED_3000K_HEX
                )
            } else {
                // Horizontal under-cabinet warm LED strip recessed in bottom deck
                addBoxSubMesh(
                    id = "${cabinet.id}_led_under_strip",
                    nameAr = "شريط إضاءة LED سفلي دافئ 3000K",
                    role = CabinetMeshPartRole.LED_LIGHT_STRIP,
                    center = Vector3(0f, carcassBottomY - 3f, d * 0.2f),
                    dims = Vector3(w - 40f, 6f, 14f),
                    colorHex = WARM_LED_3000K_HEX,
                    roughness = 0.05f,
                    emissiveHex = WARM_LED_3000K_HEX
                )
            }
        }

        // ====================================================
        // 7. COUNTERTOP SLAB & BACKSPLASH UPSTAND (الرخامة)
        // ====================================================
        if (includeCountertop && countertopMaterial != null) {
            val topThickness = 38f
            val frontOverhang = 25f
            val sideOverhang = 12f
            addBoxSubMesh(
                id = "${cabinet.id}_countertop_slab",
                nameAr = "سطح رخام / جرانيت بارز (38 مم)",
                role = CabinetMeshPartRole.COUNTERTOP_SLAB,
                center = Vector3(0f, carcassTopY + topThickness * 0.5f, frontOverhang * 0.5f),
                dims = Vector3(w + sideOverhang * 2f, topThickness, d + frontOverhang),
                colorHex = countertopMaterial.colorHex,
                roughness = countertopMaterial.roughness,
                metallic = countertopMaterial.metallic
            )
            // Rear 50mm marble backsplash upstand lip
            addBoxSubMesh(
                id = "${cabinet.id}_countertop_upstand",
                nameAr = "وزرة رخام خلفية (Backsplash Lip)",
                role = CabinetMeshPartRole.COUNTERTOP_SLAB,
                center = Vector3(0f, carcassTopY + topThickness + 25f, -d * 0.5f + 10f),
                dims = Vector3(w + sideOverhang * 2f, 50f, 18f),
                colorHex = countertopMaterial.colorHex,
                roughness = countertopMaterial.roughness,
                metallic = countertopMaterial.metallic
            )
        }

        // Compute overall AABB in local space
        val totalHeight = carcassTopY + if (includeCountertop) 88f else if (hasClassicCrownMolding) 36f else 0f
        val halfW = (w * 0.5f) + 24f
        val halfD = (d * 0.5f) + 30f
        val boundingBox = AABB(
            min = Vector3(-halfW, 0f, -halfD),
            max = Vector3(halfW, totalHeight, halfD)
        )

        return ParametricCabinetAssetMesh(
            assetId = cabinet.id,
            titleAr = cabinet.name,
            titleEn = "${archetype.titleEn} (${w.toInt()}×${h.toInt()}×${d.toInt()}mm)",
            archetype = archetype,
            parametricSpec = cabinet,
            subMeshes = subMeshes,
            boundingBox = boundingBox
        )
    }

    /**
     * Generates the procedural box sub-meshes for a single door or drawer front according
     * to its [DoorStyle] (Modern Slab, Fluted Reeded, Classic Shaker, Classic Raised, or Aluminum Glass)
     * and [HandleStyle].
     */
    private fun buildDoorOrDrawerPanelMeshes(
        panelIdPrefix: String,
        panelLabelAr: String,
        centerX: Float,
        centerY: Float,
        frontZ: Float,
        panelW: Float,
        panelH: Float,
        doorT: Float,
        cabinet: ParametricCabinet,
        hasGlassMullions: Boolean,
        isHorizontalHandle: Boolean,
        hingeLeft: Boolean,
        addBox: (
            id: String,
            nameAr: String,
            role: CabinetMeshPartRole,
            center: Vector3,
            dims: Vector3,
            colorHex: Long,
            roughness: Float,
            metallic: Float,
            opacity: Float,
            emissiveHex: Long?
        ) -> Unit
    ) {
        val mat = cabinet.facadeMaterial

        when (cabinet.doorStyle) {
            // ----------------------------------------------------
            // A. MODERN SLAB & FLUTED REEDED DOORS
            // ----------------------------------------------------
            DoorStyle.SLAB_MODERN, DoorStyle.FLUTED_REEDED -> {
                addBox(
                    "${panelIdPrefix}_slab",
                    "$panelLabelAr (مودرن مسطح)",
                    CabinetMeshPartRole.DOOR_SLAB,
                    Vector3(centerX, centerY, frontZ),
                    Vector3(panelW, panelH, doorT),
                    mat.colorHex,
                    mat.roughness,
                    mat.metallic,
                    1.0f,
                    null
                )

                // If Fluted / Reeded: generate vertical 3D wood ribs across the front face
                if (cabinet.doorStyle == DoorStyle.FLUTED_REEDED) {
                    val ribPitch = 36f
                    val ribW = 18f
                    val ribCount = max(2, (panelW / ribPitch).toInt())
                    val step = panelW / (ribCount + 1)
                    for (r in 1..ribCount) {
                        val rx = centerX - panelW * 0.5f + step * r
                        addBox(
                            "${panelIdPrefix}_flute_$r",
                            "شريحة تضليع رأسي $r",
                            CabinetMeshPartRole.CLASSIC_STILE_RAIL,
                            Vector3(rx, centerY, frontZ + doorT * 0.5f + 3f),
                            Vector3(ribW, panelH - 4f, 6f),
                            mat.colorHex,
                            mat.roughness,
                            mat.metallic,
                            1.0f,
                            null
                        )
                    }
                }
            }

            // ----------------------------------------------------
            // B. CLASSIC SHAKER & CLASSIC RAISED-PANEL DOORS
            // ----------------------------------------------------
            DoorStyle.SHAKER_BEVEL, DoorStyle.CLASSIC_RAISED -> {
                val frameWidth = (panelW * 0.14f).coerceIn(42f, 68f)
                val innerW = (panelW - frameWidth * 2f).coerceAtLeast(30f)
                val innerH = (panelH - frameWidth * 2f).coerceAtLeast(30f)
                val recessedT = (doorT * 0.55f).coerceAtLeast(9f)

                // 1. Recessed Center Panel
                addBox(
                    "${panelIdPrefix}_center_panel",
                    "$panelLabelAr (لوح وسطي غائر)",
                    CabinetMeshPartRole.DOOR_SLAB,
                    Vector3(centerX, centerY, frontZ - (doorT - recessedT) * 0.5f),
                    Vector3(innerW + 6f, innerH + 6f, recessedT),
                    mat.colorHex,
                    mat.roughness,
                    mat.metallic,
                    1.0f,
                    null
                )

                // 2. Left & Right Vertical Stiles (القوائم الرأسية)
                addBox(
                    "${panelIdPrefix}_stile_left",
                    "قائم إطار شيكر أيسر",
                    CabinetMeshPartRole.CLASSIC_STILE_RAIL,
                    Vector3(centerX - panelW * 0.5f + frameWidth * 0.5f, centerY, frontZ),
                    Vector3(frameWidth, panelH, doorT),
                    mat.colorHex,
                    mat.roughness,
                    mat.metallic,
                    1.0f,
                    null
                )
                addBox(
                    "${panelIdPrefix}_stile_right",
                    "قائم إطار شيكر أيمن",
                    CabinetMeshPartRole.CLASSIC_STILE_RAIL,
                    Vector3(centerX + panelW * 0.5f - frameWidth * 0.5f, centerY, frontZ),
                    Vector3(frameWidth, panelH, doorT),
                    mat.colorHex,
                    mat.roughness,
                    mat.metallic,
                    1.0f,
                    null
                )

                // 3. Top & Bottom Horizontal Rails (العوارض الأفقية)
                addBox(
                    "${panelIdPrefix}_rail_top",
                    "عارضة إطار شيكر علوية",
                    CabinetMeshPartRole.CLASSIC_STILE_RAIL,
                    Vector3(centerX, centerY + panelH * 0.5f - frameWidth * 0.5f, frontZ),
                    Vector3(innerW, frameWidth, doorT),
                    mat.colorHex,
                    mat.roughness,
                    mat.metallic,
                    1.0f,
                    null
                )
                addBox(
                    "${panelIdPrefix}_rail_bottom",
                    "عارضة إطار شيكر سفلية",
                    CabinetMeshPartRole.CLASSIC_STILE_RAIL,
                    Vector3(centerX, centerY - panelH * 0.5f + frameWidth * 0.5f, frontZ),
                    Vector3(innerW, frameWidth, doorT),
                    mat.colorHex,
                    mat.roughness,
                    mat.metallic,
                    1.0f,
                    null
                )

                // 4. Raised Center Bevel Panel for CLASSIC_RAISED
                if (cabinet.doorStyle == DoorStyle.CLASSIC_RAISED && innerW > 50f && innerH > 50f) {
                    addBox(
                        "${panelIdPrefix}_raised_boss",
                        "بانيل كلاسيك بارز وسطي",
                        CabinetMeshPartRole.CLASSIC_CROWN_MOLDING,
                        Vector3(centerX, centerY, frontZ + 2f),
                        Vector3(innerW - 24f, innerH - 24f, doorT),
                        mat.colorHex,
                        mat.roughness,
                        mat.metallic,
                        1.0f,
                        null
                    )
                }
            }

            // ----------------------------------------------------
            // C. ALUMINUM-FRAMED GLASS VITRINE DOORS
            // ----------------------------------------------------
            DoorStyle.ALUMINUM_GLASS -> {
                val aluProfileW = 38f
                val innerGlassW = (panelW - aluProfileW * 2f).coerceAtLeast(40f)
                val innerGlassH = (panelH - aluProfileW * 2f).coerceAtLeast(40f)

                // 1. Translucent Smoked/Float Glass Pane (6mm thick)
                addBox(
                    "${panelIdPrefix}_glass_pane",
                    "لوح زجاج فحمي عاكس (6 مم)",
                    CabinetMeshPartRole.GLASS_PANE_TRANSLUCENT,
                    Vector3(centerX, centerY, frontZ),
                    Vector3(innerGlassW + 8f, innerGlassH + 8f, 6f),
                    SMOKED_GLASS_HEX,
                    0.04f,
                    0.25f,
                    0.38f,
                    null
                )

                // 2. 4 Mitered Anodized Black Aluminum Frame Profiles
                addBox(
                    "${panelIdPrefix}_alu_left",
                    "قائم ألومنيوم زجاج أيسر",
                    CabinetMeshPartRole.GLASS_FRAME_MULLION,
                    Vector3(centerX - panelW * 0.5f + aluProfileW * 0.5f, centerY, frontZ),
                    Vector3(aluProfileW, panelH, doorT),
                    ALU_ANODIZED_BLACK_HEX,
                    0.22f,
                    0.85f,
                    1.0f,
                    null
                )
                addBox(
                    "${panelIdPrefix}_alu_right",
                    "قائم ألومنيوم زجاج أيمن",
                    CabinetMeshPartRole.GLASS_FRAME_MULLION,
                    Vector3(centerX + panelW * 0.5f - aluProfileW * 0.5f, centerY, frontZ),
                    Vector3(aluProfileW, panelH, doorT),
                    ALU_ANODIZED_BLACK_HEX,
                    0.22f,
                    0.85f,
                    1.0f,
                    null
                )
                addBox(
                    "${panelIdPrefix}_alu_top",
                    "عارضة ألومنيوم زجاج علوية",
                    CabinetMeshPartRole.GLASS_FRAME_MULLION,
                    Vector3(centerX, centerY + panelH * 0.5f - aluProfileW * 0.5f, frontZ),
                    Vector3(innerGlassW, aluProfileW, doorT),
                    ALU_ANODIZED_BLACK_HEX,
                    0.22f,
                    0.85f,
                    1.0f,
                    null
                )
                addBox(
                    "${panelIdPrefix}_alu_bottom",
                    "عارضة ألومنيوم زجاج سفلية",
                    CabinetMeshPartRole.GLASS_FRAME_MULLION,
                    Vector3(centerX, centerY - panelH * 0.5f + aluProfileW * 0.5f, frontZ),
                    Vector3(innerGlassW, aluProfileW, doorT),
                    ALU_ANODIZED_BLACK_HEX,
                    0.22f,
                    0.85f,
                    1.0f,
                    null
                )

                // 3. Optional French Mullion Grid Bars (تقسيمات الزجاج الداخلية)
                if (hasGlassMullions) {
                    addBox(
                        "${panelIdPrefix}_mullion_v",
                        "قائم تقسيم زجاج وسطي",
                        CabinetMeshPartRole.GLASS_FRAME_MULLION,
                        Vector3(centerX, centerY, frontZ + 2f),
                        Vector3(14f, innerGlassH, 10f),
                        ALU_ANODIZED_BLACK_HEX,
                        0.25f,
                        0.85f,
                        1.0f,
                        null
                    )
                    addBox(
                        "${panelIdPrefix}_mullion_h",
                        "عارضة تقسيم زجاج وسطية",
                        CabinetMeshPartRole.GLASS_FRAME_MULLION,
                        Vector3(centerX, centerY, frontZ + 2f),
                        Vector3(innerGlassW, 14f, 10f),
                        ALU_ANODIZED_BLACK_HEX,
                        0.25f,
                        0.85f,
                        1.0f,
                        null
                    )
                }
            }
        }

        // ----------------------------------------------------
        // D. HANDLE HARDWARE OR GOLA PROFILE CHANNEL
        // ----------------------------------------------------
        val hardwareColorHex = if (cabinet.doorStyle == DoorStyle.CLASSIC_RAISED || cabinet.doorStyle == DoorStyle.SHAKER_BEVEL) {
            BRASS_HARDWARE_HEX
        } else if (cabinet.doorStyle == DoorStyle.ALUMINUM_GLASS) {
            ALU_ANODIZED_BLACK_HEX
        } else {
            STAINLESS_HARDWARE_HEX
        }

        when (cabinet.handleStyle) {
            HandleStyle.BAR_PULL -> {
                val handleLen = (if (isHorizontalHandle) panelW * 0.32f else panelH * 0.24f).coerceIn(96f, 220f)
                val hx = if (isHorizontalHandle) {
                    centerX
                } else {
                    if (hingeLeft) centerX + panelW * 0.5f - 42f else centerX - panelW * 0.5f + 42f
                }
                val hy = if (isHorizontalHandle) centerY else centerY + panelH * 0.18f
                val hDims = if (isHorizontalHandle) Vector3(handleLen, 12f, 22f) else Vector3(12f, handleLen, 22f)

                addBox(
                    "${panelIdPrefix}_handle_bar",
                    "مقبض طولي معماري",
                    CabinetMeshPartRole.HANDLE_HARDWARE,
                    Vector3(hx, hy, frontZ + doorT * 0.5f + 11f),
                    hDims,
                    hardwareColorHex,
                    0.25f,
                    0.85f,
                    1.0f,
                    null
                )
            }
            HandleStyle.MINIMAL_KNOB -> {
                val kx = if (isHorizontalHandle) centerX else if (hingeLeft) centerX + panelW * 0.5f - 42f else centerX - panelW * 0.5f + 42f
                val ky = if (isHorizontalHandle) centerY else centerY + panelH * 0.25f
                addBox(
                    "${panelIdPrefix}_handle_knob",
                    "مقبض دائري كلاسيك",
                    CabinetMeshPartRole.HANDLE_HARDWARE,
                    Vector3(kx, ky, frontZ + doorT * 0.5f + 12f),
                    Vector3(26f, 26f, 24f),
                    hardwareColorHex,
                    0.25f,
                    0.85f,
                    1.0f,
                    null
                )
            }
            HandleStyle.J_PULL_GOLA, HandleStyle.EDGE_FINGER_PULL -> {
                // Recessed top aluminum Gola / J-pull extrusion channel
                addBox(
                    "${panelIdPrefix}_gola_channel",
                    "مجرى ألومنيوم غولا مخفي",
                    CabinetMeshPartRole.HANDLE_HARDWARE,
                    Vector3(centerX, centerY + panelH * 0.5f - 14f, frontZ - 4f),
                    Vector3(panelW, 28f, doorT),
                    ALU_ANODIZED_BLACK_HEX,
                    0.30f,
                    0.80f,
                    1.0f,
                    null
                )
            }
            HandleStyle.PUSH_TO_OPEN -> {
                // Internal magnetic push-to-open latch behind door
                val lx = if (hingeLeft) centerX + panelW * 0.4f else centerX - panelW * 0.4f
                addBox(
                    "${panelIdPrefix}_push_latch",
                    "مكبس فتح باللمس Push-to-Open",
                    CabinetMeshPartRole.HANDLE_HARDWARE,
                    Vector3(lx, centerY + panelH * 0.35f, frontZ - doorT),
                    Vector3(16f, 16f, 45f),
                    STAINLESS_HARDWARE_HEX,
                    0.4f,
                    0.7f,
                    1.0f,
                    null
                )
            }
        }
    }
}
