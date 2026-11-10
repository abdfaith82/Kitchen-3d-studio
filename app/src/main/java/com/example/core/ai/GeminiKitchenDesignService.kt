package com.example.core.ai

import android.util.Log
import com.example.core.math.Vector3
import com.example.domain.model.KitchenElement
import com.example.domain.model.KitchenElementType
import com.example.domain.model.MaterialFinish
import com.example.domain.model.RoomLayoutShape
import com.example.domain.model.RoomStructure
import com.example.domain.repository.KitchenProject
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

private const val TAG = "GeminiKitchenDesigner"

/**
 * Style preferences selectable by the user for AI-generated kitchen layouts.
 */
enum class KitchenAiStylePreference(
    val id: String,
    val arabicTitle: String,
    val subtitleAr: String,
    val defaultCabinetMaterial: MaterialFinish,
    val defaultUpperMaterial: MaterialFinish,
    val defaultCountertop: MaterialFinish,
    val defaultFloor: MaterialFinish,
    val promptGuidance: String
) {
    ROYAL_OAK_AND_CHARCOAL(
        id = "ROYAL_OAK",
        arabicTitle = "ملكي خشب بلوط طبيعي ورمادي أنثراسايت",
        subtitleAr = "خزائن تاج مزدوجة للسقف مع فيترينة زجاج مدخن وإضاءة 3000K",
        defaultCabinetMaterial = MaterialFinish.NATURAL_OAK,
        defaultUpperMaterial = MaterialFinish.CHARCOAL_MATTE,
        defaultCountertop = MaterialFinish.BLACK_GRANITE,
        defaultFloor = MaterialFinish.MARBLE_WHITE_FLOOR,
        promptGuidance = "Warm Natural Oak base cabinets, Charcoal Matte crown/bridge upper cabinets, Smoked Glass vitrine with warm 3000K LED, Black Galaxy Granite countertop."
    ),
    CHAMPAGNE_LUXURY_ACRYLIC(
        id = "CHAMPAGNE_LUXURY",
        arabicTitle = "شامبين أكريليك فاخر وذهبي مصقول",
        subtitleAr = "واجهات لامعة شامبين وكابوتشينو مع بار إفطار مضاء",
        defaultCabinetMaterial = MaterialFinish.CHAMPAGNE_GLOSS,
        defaultUpperMaterial = MaterialFinish.CAPPUCCINO_MATTE,
        defaultCountertop = MaterialFinish.CARRARA_MARBLE,
        defaultFloor = MaterialFinish.MARBLE_WHITE_FLOOR,
        promptGuidance = "High-gloss Champagne Acrylic and Cappuccino Matte cabinets, Carrara Marble countertop, illuminated glass display units."
    ),
    MODERN_MINIMALIST_WHITE(
        id = "MINIMALIST_WHITE",
        arabicTitle = "مودرن مينيمال أبيض لامع ورخام كرارا",
        subtitleAr = "تصميم عصري مشرق بدون مقابض بارزة ومثلث عمل مثالي",
        defaultCabinetMaterial = MaterialFinish.WHITE_HIGH_GLOSS,
        defaultUpperMaterial = MaterialFinish.WHITE_HIGH_GLOSS,
        defaultCountertop = MaterialFinish.CARRARA_MARBLE,
        defaultFloor = MaterialFinish.PARQUET_OAK_FLOOR,
        promptGuidance = "Sleek Minimalist High-Gloss White cabinets, Carrara White Marble countertop, warm oak parquet floor, handleless Aventos lift wall units."
    ),
    INDUSTRIAL_WALNUT_DARK(
        id = "INDUSTRIAL_WALNUT",
        arabicTitle = "كلاسيك عصري جوز داكن وستانلس ستيل",
        subtitleAr = "فخامة خشب الجوز الأمريكي مع جزيرة عمل احترافية",
        defaultCabinetMaterial = MaterialFinish.WALNUT_DARK,
        defaultUpperMaterial = MaterialFinish.SMOKED_GLASS_ALU,
        defaultCountertop = MaterialFinish.BLACK_GRANITE,
        defaultFloor = MaterialFinish.GREY_SLATE_FLOOR,
        promptGuidance = "Rich Dark American Walnut wood cabinets, Smoked Glass aluminum frame wall cabinets, Black Granite countertops, professional chef layout."
    ),
    MEDITERRANEAN_SAGE_GREEN(
        id = "SAGE_GREEN",
        arabicTitle = "أخضر ميرمية هادئ (Sage Green) ورخام أبيض",
        subtitleAr = "طابع أوروبي دافئ مريح للعين مع إضاءة طبيعية",
        defaultCabinetMaterial = MaterialFinish.SAGE_GREEN,
        defaultUpperMaterial = MaterialFinish.WHITE_HIGH_GLOSS,
        defaultCountertop = MaterialFinish.CARRARA_MARBLE,
        defaultFloor = MaterialFinish.PARQUET_OAK_FLOOR,
        promptGuidance = "Soft Mediterranean Sage Green base units, crisp White upper units, Carrara Marble countertops, ergonomic work triangle."
    )
}

/**
 * UI State for Gemini AI Kitchen Design generation.
 */
sealed interface AiDesignUiState {
    data object Idle : AiDesignUiState
    data object Loading : AiDesignUiState
    data class Success(
        val proposalTitle: String,
        val architecturalSummaryAr: String,
        val ergonomicNotesAr: String,
        val lightingRecommendationAr: String,
        val suggestedAccessoriesAr: List<String>,
        val generatedProject: KitchenProject
    ) : AiDesignUiState
    data class Error(val message: String) : AiDesignUiState
}

/**
 * Service that uses Firebase AI Logic (`Firebase.ai.generativeModel("gemini-3.8-flash")`)
 * with structured JSON output (`Schema.obj`) to generate complete 3D kitchen layouts
 * tailored to the user's room dimensions and style preferences.
 */
class GeminiKitchenDesignService {

    private val kitchenDesignSchema by lazy {
        val unitItemSchema = Schema.obj(
            properties = mapOf(
                "nameAr" to Schema.string(description = "Arabic name of the kitchen unit"),
                "typeCode" to Schema.enumeration(
                    values = KitchenElementType.entries.map { it.name },
                    description = "Exact enum name from KitchenElementType"
                ),
                "xMm" to Schema.integer(description = "Center X position in millimeters within [300, widthMm - 300]"),
                "yMm" to Schema.integer(description = "Bottom Y elevation in millimeters (0 for base/tall, 850 for countertop appliances, 1450 for wall cabinets, 2170 for bridge crown cabinets)"),
                "zMm" to Schema.integer(description = "Center Z position in millimeters within [175, depthMm - 300]"),
                "rotationYDeg" to Schema.integer(description = "Rotation around Y axis in degrees: 0 (North wall), 90 (West wall), 180 (South wall), 270 (East wall)"),
                "widthMm" to Schema.integer(description = "Unit width in mm (e.g. 600, 800, 900, 1200)"),
                "heightMm" to Schema.integer(description = "Unit height in mm (e.g. 850 for base, 720 for wall, 2170 for tall)"),
                "depthMm" to Schema.integer(description = "Unit depth in mm (e.g. 600 for base/tall, 350 for wall)"),
                "materialName" to Schema.enumeration(
                    values = MaterialFinish.ALL_FINISHES.map { it.id },
                    description = "MaterialFinish id for cabinet body/front"
                ),
                "countertopMaterialName" to Schema.enumeration(
                    values = listOf(
                        MaterialFinish.CARRARA_MARBLE.id,
                        MaterialFinish.BLACK_GRANITE.id,
                        MaterialFinish.CALACATTA_GOLD.id
                    ),
                    description = "Countertop finish id for base cabinets"
                )
            )
        )

        Schema.obj(
            properties = mapOf(
                "proposalTitleAr" to Schema.string(description = "Attractive Arabic title for the proposed kitchen design"),
                "architecturalSummaryAr" to Schema.string(description = "Detailed Arabic explanation of the spatial zoning and architectural flow"),
                "ergonomicNotesAr" to Schema.string(description = "Arabic notes on Work Triangle (Sink, Cooktop, Refrigerator) distances and door clearance compliance"),
                "lightingRecommendationAr" to Schema.string(description = "Arabic recommendation for natural daylight and 3000K under-cabinet LED placement"),
                "suggestedAccessoriesAr" to Schema.array(
                    items = Schema.string(),
                    description = "List of 3 to 5 smart internal kitchen accessories in Arabic recommended for this layout"
                ),
                "floorMaterialName" to Schema.enumeration(
                    values = listOf(
                        MaterialFinish.MARBLE_WHITE_FLOOR.id,
                        MaterialFinish.PARQUET_OAK_FLOOR.id,
                        MaterialFinish.GREY_SLATE_FLOOR.id
                    ),
                    description = "Recommended floor material finish"
                ),
                "units" to Schema.array(
                    items = unitItemSchema,
                    description = "Complete list of 8 to 14 kitchen units (base cabinets, sink, drawers, cooktop, oven tower, refrigerator, upper wall cabinets, bridge cabinets, and island if room >= 4000mm)"
                )
            )
        )
    }

    private val generativeModel by lazy {
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = kitchenDesignSchema
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = config
        )
    }

    suspend fun generateKitchenDesign(
        roomWidthMm: Float,
        roomDepthMm: Float,
        roomHeightMm: Float,
        layoutShape: RoomLayoutShape,
        stylePreference: KitchenAiStylePreference,
        includeIsland: Boolean,
        customNotes: String
    ): AiDesignUiState {
        val safeW = roomWidthMm.coerceIn(2400f, 8000f).toInt()
        val safeD = roomDepthMm.coerceIn(2200f, 8000f).toInt()
        val safeH = roomHeightMm.coerceIn(2400f, 3600f).toInt()

        val prompt = buildString {
            appendLine("أنت مهندس معماري خبير في تصميم المطابخ ثلاثية الأبعاد (CAD Kitchen Architect).")
            appendLine("قم باقتراح تصميم مطبخ كامل ومتكامل هندسياً بدون أي تداخل بين الوحدات بناءً على المعطيات التالية:")
            appendLine("- عرض الغرفة (X): $safeW مم")
            appendLine("- عمق الغرفة (Z): $safeD مم")
            appendLine("- ارتفاع السقف (Y): $safeH مم")
            appendLine("- شكل التوزيع المطلوب: ${layoutShape.titleAr} (${layoutShape.name})")
            appendLine("- النمط المعماري المفضل: ${stylePreference.arabicTitle} - ${stylePreference.promptGuidance}")
            appendLine("- تضمين جزيرة وسطية أو بار: ${if (includeIsland && safeW >= 3800 && safeD >= 3400) "نعم (ضعها في منتصف الغرفة مع ترك ممر حركة لا يقل عن 1000 مم)" else "لا"}")
            if (customNotes.isNotBlank()) {
                appendLine("- ملاحظات إضافية من العميل: $customNotes")
            }
            appendLine()
            appendLine("القواعد الهندسية الإلزامية لتوزيع الوحدات داخل نظام الإحداثيات (X من 0 إلى $safeW، Z من 0 إلى $safeD):")
            appendLine("1. الجدار الشمالي الخلفي (North Wall): zMm = 300 للخزائن السفلية والطويلة، و zMm = 175 للخزائن العلوية، و rotationYDeg = 0. رتب الوحدات على محور X جنبًا إلى جنب بدون تداخل (مثلاً X = 700, 1400, 2100, 2750, 3350...).")
            appendLine("2. إذا كان الشكل L_SHAPE أو U_SHAPE: ضع خزائن على الجدار الغربي (West Wall) عند xMm = 300 و rotationYDeg = 90، وزعها على محور Z (مثلاً zMm = 1350, 2200...).")
            appendLine("3. إذا كان الشكل U_SHAPE: أضف خزائن على الجدار الشرقي (East Wall) عند xMm = ${safeW - 300} و rotationYDeg = 270.")
            appendLine("4. يجب أن يتضمن التصميم مثلث عمل متكامل: حوض غسيل (BASE_SINK_UNIT أو BASE_SINK_VENTED_100)، خزانة أدراج (BASE_4_DRAWERS أو BASE_3_DRAWERS)، منطقة طهي (BASE_UNDER_OVEN + COOKTOP_INDUCTION + RANGE_HOOD_BOX)، برج فرن أو مؤونة (TALL_OVEN_TOWER أو TALL_PANTRY)، وثلاجة (REFRIGERATOR_FROST)، بالإضافة إلى خزائن علوية (WALL_GLASS_BLACK_FRAME / WALL_LIFT_AVENTOS) وإضاءة سقف (CEILING_CHANDELIER_MODERN أو CEILING_LINEAR_LED_PANEL).")
            appendLine("5. اترك مسافة أمامية كافية لا تقل عن 900 مم أمام جميع الأبواب والأدراج لضمان عدم تعارض فتح الأبواب مع معايير التركيب الهندسية.")
        }

        return try {
            val response = withTimeout(60_000L) {
                generativeModel.generateContent(prompt)
            }
            val jsonString = response.text
            if (jsonString.isNullOrBlank()) {
                return AiDesignUiState.Error("أرجع نموذج Gemini استجابة فارغة. يرجى المحاولة مرة أخرى.")
            }

            parseStructuredKitchenResponse(
                jsonString = jsonString,
                roomWidthMm = safeW.toFloat(),
                roomDepthMm = safeD.toFloat(),
                roomHeightMm = safeH.toFloat(),
                layoutShape = layoutShape,
                stylePreference = stylePreference
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini kitchen design generation timed out", e)
            AiDesignUiState.Error("انتهت مهلة توليد التصميم عبر الذكاء الاصطناعي (60 ثانية). يرجى المحاولة مجدداً.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini kitchen design generation failed", e)
            AiDesignUiState.Error(
                e.localizedMessage ?: "تعذر الاتصال بخدمة Gemini AI لتوليد تصميم المطبخ."
            )
        }
    }

    private fun parseStructuredKitchenResponse(
        jsonString: String,
        roomWidthMm: Float,
        roomDepthMm: Float,
        roomHeightMm: Float,
        layoutShape: RoomLayoutShape,
        stylePreference: KitchenAiStylePreference
    ): AiDesignUiState {
        val json = JSONObject(jsonString)
        val proposalTitle = json.optString("proposalTitleAr", "تصميم مطبخ ذكي - ${stylePreference.arabicTitle}")
        val architecturalSummary = json.optString("architecturalSummaryAr", "")
        val ergonomicNotes = json.optString("ergonomicNotesAr", "")
        val lightingRecommendation = json.optString("lightingRecommendationAr", "")

        val accessoriesArray = json.optJSONArray("suggestedAccessoriesAr")
        val suggestedAccessories = mutableListOf<String>()
        if (accessoriesArray != null) {
            for (i in 0 until accessoriesArray.length()) {
                suggestedAccessories.add(accessoriesArray.optString(i))
            }
        }

        val floorMatName = json.optString("floorMaterialName", stylePreference.defaultFloor.id)
        val floorMaterial = MaterialFinish.ALL_FINISHES.find { it.id == floorMatName || it.name == floorMatName }
            ?: stylePreference.defaultFloor

        val unitsArray = json.optJSONArray("units")
        val parsedElements = mutableListOf<KitchenElement>()
        if (unitsArray != null) {
            for (i in 0 until unitsArray.length()) {
                val unitObj = unitsArray.optJSONObject(i) ?: continue
                val typeCode = unitObj.optString("typeCode", KitchenElementType.BASE_DOUBLE_DOOR.name)
                val elementType = KitchenElementType.entries.find { it.name == typeCode }
                    ?: KitchenElementType.BASE_DOUBLE_DOOR

                val width = unitObj.optInt("widthMm", elementType.defaultDimensions.x.toInt())
                    .toFloat().coerceIn(200f, 2000f)
                val height = unitObj.optInt("heightMm", elementType.defaultDimensions.y.toInt())
                    .toFloat().coerceIn(45f, 2750f)
                val depth = unitObj.optInt("depthMm", elementType.defaultDimensions.z.toInt())
                    .toFloat().coerceIn(150f, 1200f)

                val halfW = width / 2f
                val halfD = depth / 2f
                val x = unitObj.optInt("xMm", (roomWidthMm / 2f).toInt())
                    .toFloat().coerceIn(halfW + 20f, (roomWidthMm - halfW - 20f).coerceAtLeast(halfW + 20f))
                val y = unitObj.optInt("yMm", elementType.defaultElevationY.toInt())
                    .toFloat().coerceIn(0f, (roomHeightMm - height).coerceAtLeast(0f))
                val z = unitObj.optInt("zMm", (depth / 2f).toInt())
                    .toFloat().coerceIn(halfD + 20f, (roomDepthMm - halfD - 20f).coerceAtLeast(halfD + 20f))

                val rotRaw = unitObj.optInt("rotationYDeg", 0)
                val rotY = (((rotRaw / 90) * 90) % 360 + 360) % 360

                val matName = unitObj.optString("materialName", stylePreference.defaultCabinetMaterial.id)
                val material = MaterialFinish.ALL_FINISHES.find { it.id == matName || it.name == matName }
                    ?: stylePreference.defaultCabinetMaterial

                val counterName = unitObj.optString("countertopMaterialName", stylePreference.defaultCountertop.id)
                val countertop = MaterialFinish.ALL_FINISHES.find { it.id == counterName || it.name == counterName }
                    ?: stylePreference.defaultCountertop

                val nameAr = unitObj.optString("nameAr", elementType.title).ifBlank { elementType.title }

                parsedElements.add(
                    KitchenElement(
                        id = "ai_${UUID.randomUUID().toString().take(8)}",
                        name = nameAr,
                        type = elementType,
                        position = Vector3(x, y, z),
                        rotationY = rotY.toFloat(),
                        dimensions = Vector3(width, height, depth),
                        material = material,
                        countertopMaterial = countertop
                    )
                )
            }
        }

        val room = RoomStructure(
            widthMm = roomWidthMm,
            depthMm = roomDepthMm,
            heightMm = roomHeightMm,
            wallThicknessMm = 150f,
            layoutShape = layoutShape,
            walls = RoomStructure.createDefaultWalls(roomWidthMm, roomDepthMm, roomHeightMm, 150f),
            floorMaterial = floorMaterial,
            wallFinish = MaterialFinish.WHITE_HIGH_GLOSS
        ).applyLayoutShape(layoutShape)

        val generatedProject = KitchenProject(
            id = "ai_project_${UUID.randomUUID().toString().take(8)}",
            name = proposalTitle,
            room = room,
            elements = parsedElements
        )

        return AiDesignUiState.Success(
            proposalTitle = proposalTitle,
            architecturalSummaryAr = architecturalSummary,
            ergonomicNotesAr = ergonomicNotes,
            lightingRecommendationAr = lightingRecommendation,
            suggestedAccessoriesAr = suggestedAccessories,
            generatedProject = generatedProject
        )
    }
}
