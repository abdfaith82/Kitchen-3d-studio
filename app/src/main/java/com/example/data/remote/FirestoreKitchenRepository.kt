package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.R
import com.example.domain.model.ElementCategory
import com.example.domain.model.KitchenElement
import com.example.domain.model.KitchenElementType
import com.example.domain.model.MaterialFinish
import com.example.domain.repository.KitchenProject
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    LIST,
    GET,
    WRITE
}

fun handleFirestoreError(error: Exception, operationType: OperationType, path: String?) {
    val auth = try {
        FirebaseAuth.getInstance()
    } catch (_: Exception) {
        null
    }
    val currentUser = auth?.currentUser
    val providerDataJson = JSONArray().apply {
        currentUser?.providerData?.forEach { userInfo ->
            put(
                JSONObject().apply {
                    put("providerId", userInfo.providerId)
                    put("displayName", userInfo.displayName)
                    put("email", userInfo.email)
                    put("photoUrl", userInfo.photoUrl?.toString())
                }
            )
        }
    }
    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("isAnonymous", currentUser?.isAnonymous)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", providerDataJson)
    }
    val errInfo = JSONObject().apply {
        put("error", error.message ?: error.toString())
        put("operationType", operationType.name.lowercase())
        put("path", path)
        put("authInfo", authInfoJson)
    }.toString()

    Log.e("FirestoreError", errInfo, error)
    throw RuntimeException(errInfo, error)
}

/**
 * Firestore document model representing a saved 2D/3D Kitchen Design under
 * `/users/{userId}/kitchen_designs/{designId}`.
 */
@IgnoreExtraProperties
data class FirestoreKitchenDesign(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val roomWidthCm: Double = 400.0,
    val roomDepthCm: Double = 350.0,
    val roomHeightCm: Double = 280.0,
    val elementCount: Int = 0,
    val totalCostUsd: Double = 0.0,
    val materialsCostUsd: Double = 0.0,
    val laborCostUsd: Double = 0.0,
    val accessoriesCostUsd: Double = 0.0,
    val primaryMaterialId: String = "NATURAL_OAK",
    val countertopMaterialId: String = "CARRARA_MARBLE",
    val projectJson: String = "{}",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Firestore document model representing an individual Kitchen Component/Unit under
 * `/users/{userId}/kitchen_components/{componentId}` with size, category, 2D/3D coordinates, and price.
 */
@IgnoreExtraProperties
data class FirestoreKitchenComponent(
    @DocumentId val id: String = "",
    val userId: String = "",
    val designId: String = "catalog_custom",
    val nameAr: String = "",
    val category: String = "CABINETS",
    val typeName: String = "BASE_SINGLE_DOOR",
    val widthCm: Double = 60.0,
    val depthCm: Double = 60.0,
    val heightCm: Double = 85.0,
    val elevationCm: Double = 0.0,
    val posXCm: Double = 0.0,
    val posYCm: Double = 0.0,
    val rotationDeg: Double = 0.0,
    val materialId: String = "NATURAL_OAK",
    val unitPriceUsd: Double = 180.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Structured cost distribution breakdown for visual charts (Materials, Labor, Accessories, Total).
 */
data class ProjectCostDistribution(
    val cabinetsAndBoardsCostUsd: Double,
    val countertopsCostUsd: Double,
    val aluminumAndGlassCostUsd: Double,
    val materialsTotalUsd: Double,
    val accessoriesCostUsd: Double,
    val laborCostUsd: Double,
    val totalCostUsd: Double,
    val unitCostsByElementId: Map<String, Double>
)

object KitchenCostCalculator {
    /**
     * Automatically calculates the price of a single kitchen component in USD based on its
     * 3D volume/surface area (in mm converted to m²), material finish, and hardware complexity.
     */
    fun calculateUnitPriceForType(type: KitchenElementType): Double {
        val wCm = type.defaultDimensions.x / 10.0
        val hCm = type.defaultDimensions.y / 10.0
        val dCm = type.defaultDimensions.z / 10.0
        val surfaceAreaSqm = (2.0 * (wCm * hCm) + 2.0 * (dCm * hCm) + 2.0 * (wCm * dCm)) / 10000.0
        val baseRatePerSqm = when (type.category) {
            ElementCategory.BASE_CABINET -> 92.0
            ElementCategory.WALL_CABINET -> 84.0
            ElementCategory.BRIDGE_CABINET -> 88.0
            ElementCategory.TALL_UNIT -> 108.0
            ElementCategory.ISLAND_AND_SURFACE -> 125.0
            ElementCategory.APPLIANCE -> 170.0
            else -> 55.0
        }
        val bonus = when {
            type.code.contains("drawers") -> 65.0
            type.isCornerUnit -> 85.0
            type.category == ElementCategory.TALL_UNIT -> 95.0
            else -> 25.0
        }
        val raw = (surfaceAreaSqm * baseRatePerSqm) + bonus
        return ((raw * 10.0).roundToInt() / 10.0).coerceAtLeast(40.0)
    }

    fun calculateElementPriceUsd(element: KitchenElement): Double {
        val wCm = element.dimensions.x / 10.0
        val hCm = element.dimensions.y / 10.0
        val dCm = element.dimensions.z / 10.0
        val surfaceAreaSqm = (2.0 * (wCm * hCm) + 2.0 * (dCm * hCm) + 2.0 * (wCm * dCm)) / 10000.0

        val baseRatePerSqm = when (element.type.category) {
            ElementCategory.BASE_CABINET -> 92.0
            ElementCategory.WALL_CABINET -> 84.0
            ElementCategory.BRIDGE_CABINET -> 88.0
            ElementCategory.TALL_UNIT -> 108.0
            ElementCategory.ISLAND_AND_SURFACE -> 125.0
            ElementCategory.APPLIANCE -> 170.0
            else -> 55.0
        }

        val materialMultiplier = when (element.material.category) {
            com.example.domain.model.FinishCategory.STONE -> 1.35
            com.example.domain.model.FinishCategory.METAL -> 1.25
            com.example.domain.model.FinishCategory.WOOD -> 1.15
            else -> 1.05
        }

        val hardwareBonus = when {
            element.type.code.contains("drawers") -> 65.0
            element.type.isCornerUnit -> 85.0
            element.type.category == ElementCategory.TALL_UNIT -> 95.0
            element.customDivisionsCount > 0 -> element.customDivisionsCount * 22.0
            else -> 25.0
        }

        val countertopBonus = if (element.countertopMaterial != null ||
            element.type.category == ElementCategory.BASE_CABINET ||
            element.type.category == ElementCategory.ISLAND_AND_SURFACE
        ) {
            val topAreaSqm = (wCm * dCm) / 10000.0
            topAreaSqm * 145.0
        } else {
            0.0
        }

        val rawPrice = (surfaceAreaSqm * baseRatePerSqm * materialMultiplier) + hardwareBonus + countertopBonus
        return ((rawPrice * 10.0).roundToInt() / 10.0).coerceAtLeast(35.0)
    }

    /**
     * Automatically calculates the full project cost distribution (Materials, Labor, Accessories)
     * from the selected units in the 2D/3D workspace.
     */
    fun calculateProjectCostDistribution(
        project: KitchenProject,
        accessoriesCostUsd: Double
    ): ProjectCostDistribution {
        val unitMap = mutableMapOf<String, Double>()
        var cabinetsAndBoards = 0.0
        var countertops = 0.0
        var aluminumAndGlass = 0.0

        project.elements.forEach { el ->
            val unitPrice = calculateElementPriceUsd(el)
            unitMap[el.id] = unitPrice

            val wCm = el.dimensions.x / 10.0
            val dCm = el.dimensions.z / 10.0
            val cTopCost = if (el.countertopMaterial != null ||
                el.type.category == ElementCategory.BASE_CABINET ||
                el.type.category == ElementCategory.ISLAND_AND_SURFACE
            ) {
                ((wCm * dCm) / 10000.0) * 145.0
            } else {
                0.0
            }
            val alumCost = unitPrice * 0.16
            val boardCost = (unitPrice - cTopCost - alumCost).coerceAtLeast(20.0)

            cabinetsAndBoards += boardCost
            countertops += cTopCost
            aluminumAndGlass += alumCost
        }

        val materialsTotal = cabinetsAndBoards + countertops + aluminumAndGlass
        val safeAccessories = accessoriesCostUsd.coerceAtLeast(
            project.elements.count {
                it.type.category != ElementCategory.APPLIANCE
            } * 38.0
        )
        // Workshop fabrication + CNC cutting + on-site installation labor (22% of materials + $45 per unit)
        val laborCost = (materialsTotal * 0.22) + (project.elements.size * 45.0)
        val grandTotal = materialsTotal + safeAccessories + laborCost

        return ProjectCostDistribution(
            cabinetsAndBoardsCostUsd = (cabinetsAndBoards * 10.0).roundToInt() / 10.0,
            countertopsCostUsd = (countertops * 10.0).roundToInt() / 10.0,
            aluminumAndGlassCostUsd = (aluminumAndGlass * 10.0).roundToInt() / 10.0,
            materialsTotalUsd = (materialsTotal * 10.0).roundToInt() / 10.0,
            accessoriesCostUsd = (safeAccessories * 10.0).roundToInt() / 10.0,
            laborCostUsd = (laborCost * 10.0).roundToInt() / 10.0,
            totalCostUsd = (grandTotal * 10.0).roundToInt() / 10.0,
            unitCostsByElementId = unitMap
        )
    }
}

class FirestoreKitchenRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
    )

    private fun designsCollection(userId: String) =
        db.collection("users").document(userId).collection("kitchen_designs")

    private fun componentsCollection(userId: String) =
        db.collection("users").document(userId).collection("kitchen_components")

    fun observeUserDesigns(userId: String): Flow<List<FirestoreKitchenDesign>> = callbackFlow {
        val path = "users/$userId/kitchen_designs"
        val registration: ListenerRegistration = designsCollection(userId)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    try {
                        handleFirestoreError(error, OperationType.LIST, path)
                    } catch (e: Exception) {
                        close(e)
                    }
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val items = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(FirestoreKitchenDesign::class.java)?.copy(id = doc.id)
                    }
                    trySend(items)
                }
            }
        awaitClose { registration.remove() }
    }

    fun observeUserComponents(userId: String): Flow<List<FirestoreKitchenComponent>> = callbackFlow {
        val path = "users/$userId/kitchen_components"
        val registration: ListenerRegistration = componentsCollection(userId)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    try {
                        handleFirestoreError(error, OperationType.LIST, path)
                    } catch (e: Exception) {
                        close(e)
                    }
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val items = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(FirestoreKitchenComponent::class.java)?.copy(id = doc.id)
                    }
                    trySend(items)
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun createKitchenDesign(userId: String, designId: String, design: FirestoreKitchenDesign) {
        val docRef = designsCollection(userId).document(designId)
        try {
            docRef.set(design.copy(userId = userId)).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
        }
    }

    suspend fun updateKitchenDesign(
        userId: String,
        designId: String,
        updates: Map<String, Any>
    ) {
        val docRef = designsCollection(userId).document(designId)
        try {
            val payload = updates.toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.update(payload).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
        }
    }

    suspend fun getKitchenDesign(userId: String, designId: String): FirestoreKitchenDesign? {
        val docRef = designsCollection(userId).document(designId)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                snap.toObject(FirestoreKitchenDesign::class.java)?.copy(id = snap.id)
            } else {
                null
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            null
        }
    }

    suspend fun deleteKitchenDesign(userId: String, designId: String) {
        val docRef = designsCollection(userId).document(designId)
        try {
            docRef.delete().await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
        }
    }

    suspend fun createKitchenComponent(
        userId: String,
        componentId: String,
        component: FirestoreKitchenComponent
    ) {
        val docRef = componentsCollection(userId).document(componentId)
        try {
            docRef.set(component.copy(userId = userId)).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
        }
    }

    suspend fun updateKitchenComponent(
        userId: String,
        componentId: String,
        updates: Map<String, Any>
    ) {
        val docRef = componentsCollection(userId).document(componentId)
        try {
            val payload = updates.toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.update(payload).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
        }
    }

    suspend fun getKitchenComponent(userId: String, componentId: String): FirestoreKitchenComponent? {
        val docRef = componentsCollection(userId).document(componentId)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                snap.toObject(FirestoreKitchenComponent::class.java)?.copy(id = snap.id)
            } else {
                null
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            null
        }
    }

    suspend fun deleteKitchenComponent(userId: String, componentId: String) {
        val docRef = componentsCollection(userId).document(componentId)
        try {
            docRef.delete().await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
        }
    }
}
