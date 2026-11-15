package com.example

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.core.ai.configureAppCheck
import com.example.presentation.ui.KitchenStudioScreen
import com.example.presentation.viewmodel.KitchenEditorViewModel
import com.example.ui.theme.MyApplicationTheme
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

fun Context.findActivityOrNull(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

fun observeAuthState(auth: FirebaseAuth): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    auth.addAuthStateListener(listener)
    awaitClose { auth.removeAuthStateListener(listener) }
}

private const val GOOGLE_WEB_CLIENT_ID_FALLBACK =
    "52319811209-14vjtdf5s2av8u7nuno9mhv71313u01i.apps.googleusercontent.com"

@SuppressLint("DiscouragedApi")
fun resolveDefaultWebClientId(context: Context): String {
    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    if (resId != 0) {
        val value = runCatching { context.getString(resId) }.getOrDefault("").trim()
        if (value.isNotEmpty() && !value.startsWith("REPLACE_WITH")) return value
    }
    return GOOGLE_WEB_CLIENT_ID_FALLBACK
}

fun ensureFirebaseAuth(context: Context): FirebaseAuth? {
    return try {
        val appContext = context.applicationContext
        if (com.google.firebase.FirebaseApp.getApps(appContext).isEmpty()) {
            com.google.firebase.FirebaseApp.initializeApp(appContext)
        }
        FirebaseAuth.getInstance()
    } catch (e: Exception) {
        Log.w("Auth", "FirebaseAuth unavailable: ${e.message}")
        null
    }
}

suspend fun attemptAutoSignIn(activityContext: Context, auth: FirebaseAuth): Result<FirebaseUser?> {
    if (auth.currentUser != null) return Result.success(auth.currentUser)
    return try {
        val credentialManager = CredentialManager.create(activityContext)
        val webClientId = resolveDefaultWebClientId(activityContext)
        if (webClientId.isBlank()) return Result.success(null)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(true)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(activityContext, request)
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            Result.success(authResult.user)
        } else {
            Result.success(null)
        }
    } catch (_: NoCredentialException) {
        Result.success(null)
    } catch (_: GetCredentialCancellationException) {
        Result.success(null)
    } catch (e: GetCredentialException) {
        Log.w("Auth", "Auto sign-in skipped: ${e.message}")
        Result.success(null)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

suspend fun signInWithGoogleButton(activityContext: Context, auth: FirebaseAuth): Result<FirebaseUser> {
    return try {
        val credentialManager = CredentialManager.create(activityContext)
        val webClientId = resolveDefaultWebClientId(activityContext)
        if (webClientId.isBlank()) {
            return Result.failure(IllegalStateException("معرّف عميل Google غير مهيأ — يرجى التحقق من إعدادات التطبيق"))
        }

        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        val result = credentialManager.getCredential(activityContext, request)
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val signedInUser = authResult.user
                ?: return Result.failure(IllegalStateException("تعذر استرداد بيانات المستخدم من Firebase"))
            Result.success(signedInUser)
        } else {
            Result.failure(IllegalStateException("نوع بيانات اعتماد غير مدعوم"))
        }
    } catch (e: GetCredentialCancellationException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

suspend fun signOutUser(context: Context, auth: FirebaseAuth) {
    auth.signOut()
    try {
        CredentialManager.create(context.applicationContext)
            .clearCredentialState(ClearCredentialStateRequest())
    } catch (e: Exception) {
        Log.w("Auth", "Failed to clear credential state", e)
    }
}

class MainActivity : ComponentActivity() {

    private val editorViewModel: KitchenEditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    KitchenAppRoot(viewModel = editorViewModel)
                }
            }
        }
    }
}

@Composable
fun KitchenAppRoot(viewModel: KitchenEditorViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var auth by remember { mutableStateOf(ensureFirebaseAuth(context)) }
    var isOfflineGuestMode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    val currentUser by remember(auth) {
        auth?.let { observeAuthState(it) } ?: kotlinx.coroutines.flow.flowOf(null)
    }.collectAsState(initial = auth?.currentUser)

    var isCheckingSilentAuth by remember { mutableStateOf(auth?.currentUser == null) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    var isSigningIn by remember { mutableStateOf(false) }

    LaunchedEffect(auth) {
        val activeAuth = auth ?: ensureFirebaseAuth(context).also { auth = it }
        val activity = context.findActivityOrNull()
        if (activeAuth != null && activeAuth.currentUser == null && activity != null) {
            try {
                attemptAutoSignIn(activity, activeAuth)
            } catch (_: Exception) {
            } finally {
                isCheckingSilentAuth = false
            }
        } else {
            isCheckingSilentAuth = false
        }
    }

    LaunchedEffect(currentUser?.uid) {
        if (currentUser != null) {
            isOfflineGuestMode = false
        }
        viewModel.bindAuthenticatedUser(
            uid = currentUser?.uid,
            displayName = currentUser?.displayName,
            email = currentUser?.email
        )
    }

    val user = currentUser
    if (user != null || isOfflineGuestMode) {
        key(user?.uid ?: "guest_offline_studio") {
            KitchenStudioScreen(
                viewModel = viewModel,
                isGuestMode = (user == null),
                onSignOut = {
                    if (user != null) {
                        scope.launch {
                            auth?.let { signOutUser(context, it) }
                        }
                    } else {
                        isOfflineGuestMode = false
                    }
                }
            )
        }
    } else {
        GoogleAuthGateScreen(
            isLoading = isCheckingSilentAuth || isSigningIn,
            isFirebaseAvailable = auth != null,
            errorMessage = authErrorMessage,
            onSignInClick = {
                val activeAuth = auth ?: ensureFirebaseAuth(context).also { auth = it }
                if (activeAuth == null) {
                    authErrorMessage = "تعذر تهيئة خدمة المصادقة السحابية في هذا الإصدار؛ يمكنك المتابعة مباشرة عبر \"الدخول بدون حساب (الوضع المحلي)\" بكامل أدوات التصميم 3D و CAD."
                    return@GoogleAuthGateScreen
                }
                val activity = context.findActivityOrNull()
                if (activity == null) {
                    authErrorMessage = "تعذر تحديد نافذة النشاط الحالية لتسجيل الدخول."
                    return@GoogleAuthGateScreen
                }
                scope.launch {
                    isSigningIn = true
                    authErrorMessage = null
                    val res = signInWithGoogleButton(activity, activeAuth)
                    isSigningIn = false
                    res.onFailure { err ->
                        if (err !is GetCredentialCancellationException) {
                            authErrorMessage = err.localizedMessage
                                ?: "تعذر تسجيل الدخول عبر Google. يمكنك تجربة الدخول بدون حساب أو التحقق من الاتصال."
                        }
                    }
                }
            },
            onContinueWithoutAccount = {
                authErrorMessage = null
                isOfflineGuestMode = true
            }
        )
    }
}

@Composable
fun GoogleAuthGateScreen(
    isLoading: Boolean,
    isFirebaseAvailable: Boolean = true,
    errorMessage: String?,
    onSignInClick: () -> Unit,
    onContinueWithoutAccount: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF020617),
                        Color(0xFF0F172A),
                        Color(0xFF1E293B)
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.95f)),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .testTag("google_auth_gate_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0284C7).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Architecture,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "مصمم المطابخ الاحترافي 3D & CAD",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 21.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "سجّل الدخول بحساب Google لحفظ تصاميم المطابخ ومكوناتها سحابياً في Firestore، واسترجاعها وتعديلها مع حساب التكلفة التلقائي والرسوم البيانية.",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Feature highlights
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AuthFeatureRow(
                        icon = Icons.Default.CloudDone,
                        tint = Color(0xFF38BDF8),
                        text = "حفظ واسترجاع تصاميم المطابخ ومكوناتها عبر حساب Google في Firestore"
                    )
                    AuthFeatureRow(
                        icon = Icons.Default.PieChart,
                        tint = Color(0xFF10B981),
                        text = "حساب التكلفة الإجمالية تلقائياً ورسوم بيانية لتوزيع المواد والعمالة والإكسسوارات"
                    )
                    AuthFeatureRow(
                        icon = Icons.Default.ViewInAr,
                        tint = Color(0xFFF59E0B),
                        text = "سحب وإفلات على شبكة 2D ومعاينة 3D واقعية مع تنبيهات التصادم باللون الأحمر"
                    )
                }

                if (!isFirebaseAvailable && errorMessage == null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF78350F).copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "تنبيه: المزامنة السحابية غير مفعلة في هذه الحزمة أو بدون اتصال — يمكنك الدخول فوراً في الوضع المحلي بدون حساب واستخدام كافة أدوات 3D و CAD وتصدير PDF.",
                            color = Color(0xFFFDE68A),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF7F1D1D).copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage,
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onSignInClick,
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("google_sign_in_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "جاري التحقق من حساب Google...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "تسجيل الدخول باستخدام حساب Google",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onContinueWithoutAccount,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, Color(0xFF10B981)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF064E3B).copy(alpha = 0.35f),
                        contentColor = Color(0xFF6EE7B7)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("continue_without_account_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Architecture,
                        contentDescription = null,
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "الدخول بدون حساب (الوضع المحلي)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF6EE7B7)
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthFeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(text = text, color = Color(0xFFE2E8F0), fontSize = 12.sp)
    }
}
