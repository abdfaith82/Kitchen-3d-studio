package com.example.base

import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
abstract class FirestoreEmulatorTestBase {
    lateinit var db: FirebaseFirestore
    lateinit var auth: FirebaseAuth

    private val projectId = "demo-kitchen3d-studio"
    private lateinit var databaseId: String
    private val firestorePort = 8085
    private val authPort = 9099

    @Before
    fun setupEmulator() {
        ShadowLog.stream = System.out
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        databaseId = context.getString(R.string.firestore_database_id)

        FirebaseApp.clearInstancesForTest()
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setProjectId(projectId)
                .setApplicationId("1:1234567890:android:1234567890")
                .setApiKey("fake-api-key-for-emulator")
                .build()
            FirebaseApp.initializeApp(context, options)
        }

        db = FirebaseFirestore.getInstance(databaseId)
        auth = FirebaseAuth.getInstance()

        try {
            db.useEmulator("127.0.0.1", firestorePort)
            auth.useEmulator("127.0.0.1", authPort)
        } catch (_: IllegalStateException) {
            // Emulator already configured on singleton instance
        }

        val settings = FirebaseFirestoreSettings.Builder()
            .setHost("127.0.0.1:$firestorePort")
            .setSslEnabled(false)
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build()
        db.firestoreSettings = settings

        clearEmulatorData()
    }

    @After
    fun tearDownEmulator() {
        auth.signOut()
        clearEmulatorData()
        FirebaseApp.clearInstancesForTest()
    }

    protected suspend fun signInTestUser(
        email: String = "architect@kitchen3d.com",
        password: String = "Password123!"
    ): String = withContext(Dispatchers.IO) {
        val result = try {
            auth.createUserWithEmailAndPassword(email, password).await()
        } catch (_: Exception) {
            auth.signInWithEmailAndPassword(email, password).await()
        }
        result.user!!.uid
    }

    private fun clearEmulatorData() {
        val endpoints = listOf(
            "http://127.0.0.1:$firestorePort/emulator/v1/projects/$projectId/databases/$databaseId/documents",
            "http://127.0.0.1:$authPort/emulator/v1/projects/$projectId/accounts"
        )
        for (endpoint in endpoints) {
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = 2000
                readTimeout = 2000
            }
            try {
                val code = conn.responseCode
                check(code in 200..299) { "Failed to clear emulator at $endpoint (HTTP $code)" }
            } finally {
                conn.disconnect()
            }
        }
    }
}
