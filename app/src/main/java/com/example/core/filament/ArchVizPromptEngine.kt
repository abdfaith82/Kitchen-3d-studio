package com.example.core.filament

import com.example.domain.model.ElementCategory
import com.example.domain.model.FinishCategory
import com.example.domain.model.KitchenElement
import com.example.domain.model.KitchenElementType
import com.example.domain.model.MaterialFinish
import com.example.domain.model.OpeningType
import com.example.domain.model.RoomLayoutShape
import com.example.domain.repository.KitchenProject
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Photorealistic Architectural & Design Rendering Engine (Image-to-Realism AI Prompt & ControlNet Generator).
 * Translates standard 2D/3D CAD kitchen layouts into physically accurate photographic rendering parameters
 * while strictly preserving spatial geometry and proportions.
 */
object ArchVizPromptEngine {

    const val DEFAULT_NEGATIVE_PROMPT =
        "sketch, wireframe, cartoon, CGI, 3D model look, plastic texture, flat shading, oversaturated, distorted perspective, deformed lines, blurry, watermark, low quality"

    data class ControlNetSettings(
        val preprocessor: String = "canny_or_mlsd",
        val conditioningScale: Double = 0.95,
        val guidanceScale: Double = 7.5,
        val denoisingStrength: Double = 0.65
    )

    data class ArchVizRenderPayload(
        val positivePrompt: String,
        val negativePrompt: String = DEFAULT_NEGATIVE_PROMPT,
        val controlNetSettings: ControlNetSettings = ControlNetSettings(),
        val jsonString: String
    )

    /**
     * Executes the 4-stage Processing Pipeline on a [KitchenProject] and returns the structured JSON payload:
     * 1. Structural Lock (Geometry & Layout)
     * 2. Material Mapping (PBR Texturing)
     * 3. Lighting & Optics Calculation
     * 4. Camera & Lens Simulation
     */
    fun generateRenderPayload(
        project: KitchenProject,
        focalLengthMm: Int = 24,
        apertureFStop: Float = 8.0f,
        sunKelvin: Float = 5400f,
        ledKelvin: Float = 3000f,
        conditioningScale: Double = 0.95,
        guidanceScale: Double = 7.5,
        denoisingStrength: Double = 0.65,
        preprocessor: String = "canny_or_mlsd"
    ): ArchVizRenderPayload {
        val room = project.room
        val visibleElements = project.elements.filter { it.isVisible }

        // 1. Structural Lock (Geometry & Layout)
        val roomWidthM = String.format(Locale.US, "%.2f", room.widthMm / 1000f)
        val roomDepthM = String.format(Locale.US, "%.2f", room.depthMm / 1000f)
        val roomHeightM = String.format(Locale.US, "%.2f", room.heightMm / 1000f)

        val layoutDesc = when (room.layoutShape) {
            RoomLayoutShape.L_SHAPE -> "L-shaped architectural kitchen layout"
            RoomLayoutShape.U_SHAPE -> "U-shaped wrap-around architectural kitchen layout"
            RoomLayoutShape.RECTANGULAR -> "linear and perimeter architectural kitchen layout"
        }

        val baseUnitsCount = visibleElements.count { it.type.category == ElementCategory.BASE_CABINET }
        val wallUnitsCount = visibleElements.count { it.type.category == ElementCategory.WALL_CABINET }
        val bridgeUnitsCount = visibleElements.count { it.type.category == ElementCategory.BRIDGE_CABINET }
        val tallUnitsCount = visibleElements.count { it.type.category == ElementCategory.TALL_UNIT }
        val islandsCount = visibleElements.count { it.type.category == ElementCategory.ISLAND_AND_SURFACE }
        val appliances = visibleElements.filter {
            it.type.category == ElementCategory.APPLIANCE ||
                    it.type == KitchenElementType.BASE_SINK_UNIT ||
                    it.type == KitchenElementType.BASE_SINK_VENTED_100
        }

        val windowsCount = room.walls.flatMap { it.openings }.count { it.type == OpeningType.WINDOW }
        val doorsCount = room.walls.flatMap { it.openings }.count { it.type == OpeningType.DOOR }

        val structuralLockSegment = buildString {
            append("Ultra-luxury modern interior architectural photograph of a $layoutDesc ")
            append("with strict structural lock preserving exact spatial geometry and proportions ")
            append("(${roomWidthM}m x ${roomDepthM}m room footprint, ${roomHeightM}m ceiling height), ")
            append("featuring $baseUnitsCount precision-aligned base cabinets with recessed 100mm toe-kick plinths, ")
            append("$wallUnitsCount upper wall cabinets mounted at exact 60cm backsplash clearance")
            if (bridgeUnitsCount > 0) append(", $bridgeUnitsCount flush ceiling-height bridge crown cabinets")
            if (tallUnitsCount > 0) append(", $tallUnitsCount floor-to-ceiling tall pantry and illuminated glass vitrine columns")
            if (islandsCount > 0) append(", central architectural kitchen island with waterfall countertop edge")
            if (windowsCount > 0) append(", $windowsCount architectural daylight window opening(s)")
            if (doorsCount > 0) append(", $doorsCount doorway passage(s)")
        }

        // 2. Material Mapping (PBR Texturing)
        val primaryCabinetFinish = visibleElements.firstOrNull {
            it.type.category == ElementCategory.BASE_CABINET || it.type.category == ElementCategory.WALL_CABINET
        }?.material ?: MaterialFinish.NATURAL_OAK

        val secondaryUpperFinish = visibleElements.firstOrNull {
            it.type.category == ElementCategory.WALL_CABINET || it.type.category == ElementCategory.BRIDGE_CABINET
        }?.material ?: MaterialFinish.WHITE_HIGH_GLOSS

        val primaryCountertopFinish = visibleElements.firstNotNullOfOrNull { it.countertopMaterial }
            ?: MaterialFinish.CALACATTA_GOLD

        val pbrCabinetDesc = mapFinishToPbrDescription(primaryCabinetFinish)
        val pbrUpperDesc = mapFinishToPbrDescription(secondaryUpperFinish)
        val pbrCountertopDesc = mapCountertopToPbrDescription(primaryCountertopFinish)
        val pbrFloorDesc = mapFloorToPbrDescription(room.floorMaterial)

        val applianceDesc = if (appliances.isNotEmpty()) {
            "integrated PVD brushed stainless steel and reflective black tempered glass appliances (undermount sink with gooseneck mixer faucet, induction cooktop, built-in oven, and range hood) with accurate anisotropic metal reflections"
        } else {
            "sleek handleless Gola anodized aluminum profile channels and precision mitered joinery"
        }

        val pbrMaterialSegment = buildString {
            append("Upgraded Physically Based Rendering (PBR) materials: ")
            append("base cabinetry crafted in $pbrCabinetDesc, ")
            append("upper cabinetry in $pbrUpperDesc, ")
            append("continuous 60cm backsplash and countertops in $pbrCountertopDesc, ")
            append("tinted smoked reflective glass vitrine doors with slim matte black anodized aluminum frames, ")
            append("$applianceDesc, ")
            append("and flooring in $pbrFloorDesc. ")
            append("Tasteful minimalist countertop staging including a matte black gooseneck kettle, a small ceramic indoor botanical planter, and artisan glass storage jars")
        }

        // 3. Lighting & Optics Calculation
        val lightingSegment = buildString {
            append("Physically accurate lighting and optics: ")
            append("Ray-Traced Global Illumination (GI) with multi-bounce indirect light, ")
            append("deep contact Ambient Occlusion (AO) in cabinet reveals and ceiling corners, ")
            if (windowsCount > 0) {
                append("soft natural directional sunlight (${sunKelvin.roundToInt()}K) streaming through the window openings with realistic shadow falloff across the floor and worktop, ")
            } else {
                append("balanced architectural studio daylight (${sunKelvin.roundToInt()}K) with soft natural shadow falloff, ")
            }
            append("warm recessed ${ledKelvin.roundToInt()}K linear LED strip illumination under all upper cabinets and inside vertical glass vitrines casting realistic specular grazing highlights on the veined stone backsplash, ")
            append("and recessed architectural ceiling downlights producing subtle Fresnel reflections on the floor and countertops")
        }

        // 4. Camera & Lens Simulation
        val fStopFormatted = String.format(Locale.US, "f/%.1f", apertureFStop).replace(".0", "")
        val cameraSegment = buildString {
            append("Captured on a full-frame DSLR camera with a ${focalLengthMm}mm architectural tilt-shift lens, ")
            append("$fStopFormatted aperture for deep depth of field, perfectly vertical two-point perspective lines, ")
            append("crisp edge-to-edge optical focus, ACES filmic tone mapping, ")
            append("photorealistic, raw photo, 8k resolution, ArchViz, Unreal Engine 5, V-Ray render, high surface imperfections")
        }

        val fullPositivePrompt = "$structuralLockSegment. $pbrMaterialSegment. $lightingSegment. $cameraSegment"

        val controlNet = ControlNetSettings(
            preprocessor = preprocessor,
            conditioningScale = conditioningScale,
            guidanceScale = guidanceScale,
            denoisingStrength = denoisingStrength
        )

        val rootJson = toJsonObject(
            positivePrompt = fullPositivePrompt,
            negativePrompt = DEFAULT_NEGATIVE_PROMPT,
            controlNetSettings = controlNet
        )

        return ArchVizRenderPayload(
            positivePrompt = fullPositivePrompt,
            negativePrompt = DEFAULT_NEGATIVE_PROMPT,
            controlNetSettings = controlNet,
            jsonString = rootJson.toString(2)
        )
    }

    fun toJsonObject(
        positivePrompt: String,
        negativePrompt: String = DEFAULT_NEGATIVE_PROMPT,
        controlNetSettings: ControlNetSettings = ControlNetSettings()
    ): JSONObject {
        return JSONObject().apply {
            put("positive_prompt", positivePrompt)
            put("negative_prompt", negativePrompt)
            put(
                "controlnet_settings",
                JSONObject().apply {
                    put("preprocessor", controlNetSettings.preprocessor)
                    put("conditioning_scale", controlNetSettings.conditioningScale)
                    put("guidance_scale", controlNetSettings.guidanceScale)
                    put("denoising_strength", controlNetSettings.denoisingStrength)
                }
            )
        }
    }

    private fun mapFinishToPbrDescription(finish: MaterialFinish): String {
        return when (finish.id) {
            "oak" -> "natural quarter-sawn European oak wood with visible fine open-pore grain and satin matte polyurethane varnish"
            "walnut" -> "rich American dark walnut wood veneer with natural book-matched grain and low-sheen oil finish"
            "ash" -> "Nordic light ash wood with tactile vertical grain and ultra-matte clear coat"
            "smoked_elm" -> "warm smoked elm timber with deep organic wood grain texture and subtle tactile pores"
            "white_gloss" -> "seamless high-gloss architectural white acrylic lacquer with crisp mirror-like specular reflections"
            "champagne_gloss" -> "luxury champagne metallic high-gloss acrylic with soft mirror reflections"
            "cappuccino_matte" -> "warm cappuccino silk ultra-matte Fenix laminate with soft-touch anti-fingerprint micro-texture"
            "charcoal_matte" -> "architectural charcoal matte lacquer with fine micro-roughness and zero plastic sheen"
            "anthracite_black" -> "deep anthracite matte black velvet finish with subtle edge bevel highlights"
            "sage_green" -> "organic muted sage green ultra-matte lacquer with fine architectural texture"
            "navy_blue" -> "deep midnight navy matte architectural cabinetry finish with brushed brass accents"
            "stainless" -> "brushed 304 architectural stainless steel with fine linear grain"
            "smoked_glass" -> "tinted dark smoked reflective tempered glass framed in matte black anodized aluminum"
            else -> when (finish.category) {
                FinishCategory.WOOD -> "natural fine-grain hardwood veneer with matte architectural varnish"
                FinishCategory.STONE -> "honed natural stone slab with subtle mineral veining"
                FinishCategory.METAL -> "brushed anodized architectural metal with realistic anisotropic reflections"
                else -> "sleek ultra-matte architectural lacquer with realistic micro-surface imperfections"
            }
        }
    }

    private fun mapCountertopToPbrDescription(finish: MaterialFinish): String {
        return when (finish.id) {
            "carrara" -> "honed Italian Bianca Carrara marble slab with delicate feathery grey veining and realistic stone specular response"
            "black_granite" -> "polished Nero Assoluto and Black Galaxy natural granite slab with subtle crystalline mineral flecks and crisp surface reflections"
            "calacatta_gold" -> "honed Calacatta Gold marble with dramatic warm taupe and grey veining across a luminous white stone matrix"
            "terrazzo" -> "artisan Italian architectural terrazzo stone with embedded marble aggregates and satin honed polish"
            else -> "honed Calacatta marble with subtle grey veining and bevel-cut 38mm waterfall edges"
        }
    }

    private fun mapFloorToPbrDescription(finish: MaterialFinish): String {
        return when (finish.id) {
            "floor_parquet" -> "wide-plank natural European oak herringbone parquet flooring with subtle bevel joints and satin wax reflections"
            "floor_slate" -> "large-format matte dark grey architectural slate porcelain tiles with fine rectified grout lines"
            "floor_marble" -> "large-format polished Calacatta porcelain slabs with soft specular floor reflections"
            "floor_travertine" -> "warm honed cream travertine stone floor tiles with natural mineral texture and soft sheen"
            else -> "large-format architectural porcelain flooring with subtle natural specular reflections"
        }
    }
}
