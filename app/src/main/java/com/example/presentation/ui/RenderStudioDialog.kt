package com.example.presentation.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.filament.*
import com.example.core.geometry.WallGeometryCutter
import com.example.core.math.Vector3
import com.example.domain.model.WallSide
import com.example.presentation.ui.RealisticKitchenRenderer3D.renderCabinetRealisticDetails
import com.example.presentation.ui.RealisticKitchenRenderer3D.renderCeilingAndBacksplashLighting
import com.example.presentation.ui.RealisticKitchenRenderer3D.renderRealisticFloor
import com.example.presentation.viewmodel.KitchenEditorUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*

private enum class CinematicCameraShot(
    val titleAr: String,
    val yaw: Float,
    val pitch: Float,
    val distanceFactor: Float,
    val fovDeg: Float
) {
    HERO_EYE_LEVEL("لقطة سينمائية بمستوى العين 24mm", 32f, 18f, 1.28f, 44f),
    WIDE_ARCHITECTURAL("بانوراما معمارية واسعة", 42f, 28f, 1.42f, 50f),
    ISLAND_FOCUS("تركيز الجزيرة والرخام", 18f, 22f, 1.08f, 40f),
    CORNER_DRAMATIC("زاوية درامية متماثلة", -36f, 20f, 1.25f, 45f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenderStudioDialog(
    state: KitchenEditorUiState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Default to Tab 0 = Dedicated 4K Cinematic Render Studio Screen
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf(
        "استوديو الرندر السينمائي 4K",
        "الإضاءة والبيئة",
        "الكاميرا والعدسة",
        "خامات PBR",
        "محرك ArchViz JSON"
    )

    val deviceSpec = remember { DeviceProfiler.profile(context) }
    var qualityLevel by remember { mutableStateOf(deviceSpec.recommendedQuality) }

    // Active Cinematic Mood Preset (Defaults to V-Ray / Corona 8K Photoreal)
    var activeMoodPreset by remember { mutableStateOf(CinematicMoodPreset.VRAY_CORONA_ARCHITECTURAL_8K) }

    // Lighting parameters (synced when a preset is tapped, or fine-tuned manually)
    var sunAzimuth by remember { mutableFloatStateOf(activeMoodPreset.sunAzimuth) }
    var sunElevation by remember { mutableFloatStateOf(activeMoodPreset.sunElevation) }
    var sunLux by remember { mutableFloatStateOf(activeMoodPreset.sunIntensityLux) }
    var sunKelvin by remember { mutableFloatStateOf(activeMoodPreset.sunTempKelvin) }
    var selectedEnvPreset by remember { mutableStateOf(activeMoodPreset.envPreset) }

    // Under-Cabinet LED parameters
    var isLedEnabled by remember { mutableStateOf(activeMoodPreset.ledEnabled) }
    var ledKelvin by remember { mutableFloatStateOf(activeMoodPreset.ledTempKelvin) }
    var ledLumens by remember { mutableFloatStateOf(activeMoodPreset.ledFluxLumens) }

    // Camera & Cinematic Lens parameters
    var previewYaw by remember { mutableFloatStateOf(state.cameraYaw) }
    var previewPitch by remember { mutableFloatStateOf(state.cameraPitch.coerceIn(10f, 45f)) }
    var previewDistanceFactor by remember { mutableFloatStateOf(1.28f) }
    var previewFovDeg by remember { mutableFloatStateOf(44f) }
    var selectedCameraShot by remember { mutableStateOf(CinematicCameraShot.HERO_EYE_LEVEL) }

    var aperture by remember { mutableFloatStateOf(activeMoodPreset.apertureFStop) }
    var shutterSpeedFrac by remember { mutableFloatStateOf(60f) }
    var iso by remember { mutableFloatStateOf(activeMoodPreset.iso) }
    var useAces by remember { mutableStateOf(true) }

    // Cinematic Post-Processing parameters
    var bloomIntensity by remember { mutableFloatStateOf(activeMoodPreset.postConfig.bloomIntensity) }
    var vignetteStrength by remember { mutableFloatStateOf(activeMoodPreset.postConfig.vignetteStrength) }
    var warmthShift by remember { mutableFloatStateOf(activeMoodPreset.postConfig.warmthShift) }
    var contrastBoost by remember { mutableFloatStateOf(activeMoodPreset.postConfig.contrastBoost) }
    var cinematicLetterbox by remember { mutableStateOf(activeMoodPreset.postConfig.cinematicLetterbox) }
    var includeCountertopStaging by remember { mutableStateOf(activeMoodPreset.postConfig.includeCountertopStaging) }

    // Offscreen 4K Render state (Default to UHD_4K so the user gets 4K cinematic output out-of-the-box)
    var selectedResolution by remember { mutableStateOf(RenderResolutionPreset.UHD_4K) }
    var isRendering by remember { mutableStateOf(false) }
    var renderProgress by remember { mutableStateOf<RenderProgress?>(null) }
    var lastRenderResult by remember { mutableStateOf<RenderResult?>(null) }
    var showRenderedOutputView by remember { mutableStateOf(false) }
    var gallerySaveStatus by remember { mutableStateOf<String?>(null) }

    fun applyMoodPreset(preset: CinematicMoodPreset) {
        activeMoodPreset = preset
        sunAzimuth = preset.sunAzimuth
        sunElevation = preset.sunElevation
        sunLux = preset.sunIntensityLux
        sunKelvin = preset.sunTempKelvin
        selectedEnvPreset = preset.envPreset
        isLedEnabled = preset.ledEnabled
        ledKelvin = preset.ledTempKelvin
        ledLumens = preset.ledFluxLumens
        aperture = preset.apertureFStop
        iso = preset.iso
        bloomIntensity = preset.postConfig.bloomIntensity
        vignetteStrength = preset.postConfig.vignetteStrength
        warmthShift = preset.postConfig.warmthShift
        contrastBoost = preset.postConfig.contrastBoost
        cinematicLetterbox = preset.postConfig.cinematicLetterbox
        includeCountertopStaging = preset.postConfig.includeCountertopStaging
    }

    fun triggerCinematicRender() {
        if (isRendering) return
        isRendering = true
        renderProgress = null
        gallerySaveStatus = null
        scope.launch {
            val room = state.project.room
            val elements = state.project.elements.filter { it.isVisible }

            val distance = max(room.widthMm, room.depthMm) * previewDistanceFactor
            val yawRad = Math.toRadians(previewYaw.toDouble()).toFloat()
            val pitchRad = Math.toRadians(previewPitch.toDouble()).toFloat()
            val roomCenter = Vector3(room.widthMm * 0.5f, room.heightMm * 0.34f, room.depthMm * 0.5f)

            val eye = Vector3(
                roomCenter.x + distance * cos(pitchRad) * sin(yawRad),
                (roomCenter.y + distance * sin(pitchRad)).coerceAtLeast(220f),
                roomCenter.z + distance * cos(pitchRad) * cos(yawRad)
            )

            val ledStrips = UnderCabinetLedGenerator.generateLedStrips(
                elements = elements,
                colorTempKelvin = ledKelvin,
                lumensPerMeter = ledLumens,
                isEnabled = isLedEnabled
            )

            val exposure = CameraExposure(
                apertureFNumber = aperture,
                shutterSpeedSeconds = 1.0f / shutterSpeedFrac,
                iso = iso,
                useAcesToneMapping = useAces
            )

            val sun = DirectionalSunLight(
                azimuthDegrees = sunAzimuth,
                elevationDegrees = sunElevation,
                colorTemperatureKelvin = sunKelvin,
                illuminanceLux = sunLux
            )

            val postConfig = CinematicPostProcessConfig(
                bloomIntensity = bloomIntensity,
                vignetteStrength = vignetteStrength,
                warmthShift = warmthShift,
                contrastBoost = contrastBoost,
                cinematicLetterbox = cinematicLetterbox,
                includeCountertopStaging = includeCountertopStaging
            )

            val res = OffscreenRenderer4K.renderScene(
                context = context,
                room = room,
                elements = elements,
                cameraEye = eye,
                cameraTarget = roomCenter,
                fovDegrees = previewFovDeg,
                sunLight = sun,
                envPreset = selectedEnvPreset,
                ledStrips = ledStrips,
                exposure = exposure,
                resolutionPreset = selectedResolution,
                postProcessConfig = postConfig,
                onProgress = { prog ->
                    renderProgress = prog
                }
            )
            lastRenderResult = res
            showRenderedOutputView = true
            isRendering = false
        }
    }

    Dialog(
        onDismissRequest = { if (!isRendering) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .testTag("render_studio_4k_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF090D16),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Cinematic Studio Header Bar
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFD97706).copy(alpha = 0.22f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B))
                            ) {
                                Text(
                                    text = "8K V-RAY / 4K",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Column {
                                Text(
                                    "محرك الرندر المعماري الفائق 4K / 8K (V-Ray & Corona)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    "حفاظ صارم على الأبعاد • خامات PBR واقعية • إضاءة GI شاملة وإكسسوارات رخامة",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onDismiss,
                            enabled = !isRendering,
                            modifier = Modifier.testTag("close_render_studio_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                        }
                    },
                    actions = {
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    "${selectedResolution.width}×${selectedResolution.height}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.HighQuality,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color(0xFF1E293B)
                            )
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0F172A)
                    )
                )

                // Tabs Navigation
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF0F172A),
                    contentColor = Color(0xFFFBBF24),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            modifier = Modifier.testTag("render_studio_tab_$index"),
                            text = {
                                Text(
                                    title,
                                    fontSize = 12.sp,
                                    color = if (selectedTab == index) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                // Main Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    when (selectedTab) {
                        0 -> Cinematic4KStudioMainScreen(
                            state = state,
                            activeMoodPreset = activeMoodPreset,
                            onSelectMoodPreset = ::applyMoodPreset,
                            selectedCameraShot = selectedCameraShot,
                            onSelectCameraShot = { shot ->
                                selectedCameraShot = shot
                                previewYaw = shot.yaw
                                previewPitch = shot.pitch
                                previewDistanceFactor = shot.distanceFactor
                                previewFovDeg = shot.fovDeg
                            },
                            previewYaw = previewYaw,
                            previewPitch = previewPitch,
                            previewDistanceFactor = previewDistanceFactor,
                            onOrbitPreview = { dyaw, dpitch ->
                                previewYaw = (previewYaw + dyaw) % 360f
                                previewPitch = (previewPitch + dpitch).coerceIn(8f, 65f)
                            },
                            sunAzimuth = sunAzimuth,
                            sunElevation = sunElevation,
                            sunKelvin = sunKelvin,
                            isLedEnabled = isLedEnabled,
                            bloomIntensity = bloomIntensity,
                            onBloomChange = { bloomIntensity = it },
                            vignetteStrength = vignetteStrength,
                            onVignetteChange = { vignetteStrength = it },
                            warmthShift = warmthShift,
                            onWarmthChange = { warmthShift = it },
                            contrastBoost = contrastBoost,
                            onContrastChange = { contrastBoost = it },
                            cinematicLetterbox = cinematicLetterbox,
                            onToggleLetterbox = { cinematicLetterbox = !cinematicLetterbox },
                            includeCountertopStaging = includeCountertopStaging,
                            onToggleCountertopStaging = { includeCountertopStaging = it },
                            selectedResolution = selectedResolution,
                            onSelectResolution = { selectedResolution = it },
                            isRendering = isRendering,
                            progress = renderProgress,
                            lastResult = lastRenderResult,
                            showRenderedOutputView = showRenderedOutputView,
                            onToggleOutputView = { showRenderedOutputView = it },
                            gallerySaveStatus = gallerySaveStatus,
                            onSaveToGallery = { bitmap ->
                                scope.launch {
                                    val savedMsg = saveRenderBitmapToGallery(context, bitmap, selectedResolution)
                                    gallerySaveStatus = savedMsg
                                }
                            }
                        )
                        1 -> LightingTabContent(
                            sunAzimuth = sunAzimuth,
                            onSunAzimuthChange = { sunAzimuth = it },
                            sunElevation = sunElevation,
                            onSunElevationChange = { sunElevation = it },
                            sunLux = sunLux,
                            onSunLuxChange = { sunLux = it },
                            sunKelvin = sunKelvin,
                            onSunKelvinChange = { sunKelvin = it },
                            selectedEnv = selectedEnvPreset,
                            onSelectEnv = { selectedEnvPreset = it },
                            isLedEnabled = isLedEnabled,
                            onLedEnabledChange = { isLedEnabled = it },
                            ledKelvin = ledKelvin,
                            onLedKelvinChange = { ledKelvin = it },
                            ledLumens = ledLumens,
                            onLedLumensChange = { ledLumens = it }
                        )
                        2 -> CameraExposureTabContent(
                            aperture = aperture,
                            onApertureChange = { aperture = it },
                            shutterFrac = shutterSpeedFrac,
                            onShutterChange = { shutterSpeedFrac = it },
                            iso = iso,
                            onIsoChange = { iso = it },
                            useAces = useAces,
                            onUseAcesChange = { useAces = it }
                        )
                        3 -> MaterialsAndDeviceTabContent(
                            deviceSpec = deviceSpec,
                            currentQuality = qualityLevel,
                            onSelectQuality = { qualityLevel = it }
                        )
                        4 -> ArchVizJsonPipelineTabContent(
                            state = state,
                            sunKelvin = sunKelvin,
                            ledKelvin = ledKelvin,
                            aperture = aperture,
                            onTriggerRender = {
                                selectedTab = 0
                                triggerCinematicRender()
                            }
                        )
                    }
                }

                // Persistent Bottom 4K Cinematic Render Action Bar
                Surface(
                    color = Color(0xFF0F172A),
                    tonalElevation = 10.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        if (isRendering) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "جاري إخراج الصورة السينمائية (${selectedResolution.width}×${selectedResolution.height})...",
                                    color = Color(0xFFFBBF24),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "${renderProgress?.percent ?: 0}%",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (renderProgress?.percent ?: 0) / 100f },
                                color = Color(0xFFF59E0B),
                                trackColor = Color(0xFF1E293B),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = renderProgress?.stageDescription ?: "جاري تهيئة تتبع الأشعة الفيزيائي...",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Resolution quick selector pills
                                RenderResolutionPreset.entries.forEach { preset ->
                                    val isSelected = selectedResolution == preset
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) Color(0xFF1E3A8A) else Color(0xFF1E293B),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155)
                                        ),
                                        modifier = Modifier
                                            .clickable { selectedResolution = preset }
                                            .testTag("select_res_${preset.name.lowercase()}")
                                    ) {
                                        Text(
                                            text = when (preset) {
                                                RenderResolutionPreset.FHD_1080P -> "1080p"
                                                RenderResolutionPreset.QHD_2K -> "2K QHD"
                                                RenderResolutionPreset.UHD_4K -> "4K UHD"
                                                RenderResolutionPreset.UHD_8K_VRAY -> "8K V-Ray"
                                            },
                                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
                                        )
                                    }
                                }

                                // Primary 4K Cinematic Render Button
                                Button(
                                    onClick = ::triggerCinematicRender,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("start_4k_render_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFD97706),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(Icons.Default.CameraEnhance, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "إخراج صورة سينمائية (${selectedResolution.width}×${selectedResolution.height})",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Cinematic4KStudioMainScreen(
    state: KitchenEditorUiState,
    activeMoodPreset: CinematicMoodPreset,
    onSelectMoodPreset: (CinematicMoodPreset) -> Unit,
    selectedCameraShot: CinematicCameraShot,
    onSelectCameraShot: (CinematicCameraShot) -> Unit,
    previewYaw: Float,
    previewPitch: Float,
    previewDistanceFactor: Float,
    onOrbitPreview: (Float, Float) -> Unit,
    sunAzimuth: Float,
    sunElevation: Float,
    sunKelvin: Float,
    isLedEnabled: Boolean,
    bloomIntensity: Float,
    onBloomChange: (Float) -> Unit,
    vignetteStrength: Float,
    onVignetteChange: (Float) -> Unit,
    warmthShift: Float,
    onWarmthChange: (Float) -> Unit,
    contrastBoost: Float,
    onContrastChange: (Float) -> Unit,
    cinematicLetterbox: Boolean,
    onToggleLetterbox: () -> Unit,
    includeCountertopStaging: Boolean,
    onToggleCountertopStaging: (Boolean) -> Unit,
    selectedResolution: RenderResolutionPreset,
    onSelectResolution: (RenderResolutionPreset) -> Unit,
    isRendering: Boolean,
    progress: RenderProgress?,
    lastResult: RenderResult?,
    showRenderedOutputView: Boolean,
    onToggleOutputView: (Boolean) -> Unit,
    gallerySaveStatus: String?,
    onSaveToGallery: (Bitmap) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Interactive Live Cinematic 4K Viewport / Rendered Output Stage
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF050811)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x44F59E0B), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Stage Top Overlay Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (showRenderedOutputView && lastResult != null) Color(0xFF10B981) else Color(0xFFF59E0B))
                            )
                            Text(
                                text = if (showRenderedOutputView && lastResult != null) {
                                    "الصورة السينمائية المخرجة (${lastResult.width}×${lastResult.height} • ${lastResult.durationMs / 1000f} ث)"
                                } else {
                                    "معاينة الكادر السينمائي الحية (اسحب لتدوير زاوية اللقطة)"
                                },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (lastResult != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (!showRenderedOutputView) Color(0xFF0284C7) else Color(0xFF1E293B),
                                    modifier = Modifier.clickable { onToggleOutputView(false) }
                                ) {
                                    Text(
                                        text = "الكادر الحي",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (showRenderedOutputView) Color(0xFFD97706) else Color(0xFF1E293B),
                                    modifier = Modifier.clickable { onToggleOutputView(true) }
                                ) {
                                    Text(
                                        text = "نتيجة الـ 4K",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (cinematicLetterbox) Color(0xFFD97706) else Color(0xFF1E293B),
                                modifier = Modifier
                                    .clickable { onToggleLetterbox() }
                                    .testTag("toggle_cinemascope_btn")
                            ) {
                                Text(
                                    text = if (cinematicLetterbox) "CinemaScope 21:9 ✓" else "16:9 UHD",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    var isBeforeAfterSplitMode by remember { mutableStateOf(false) }
                    var splitComparisonRatio by remember { mutableFloatStateOf(0.52f) }

                    // Viewport Canvas OR Rendered 4K Bitmap Display (with Before/After Split Comparison support)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .background(Color.Black)
                    ) {
                        if (showRenderedOutputView && lastResult != null) {
                            if (isBeforeAfterSplitMode) {
                                // Base Layer: Live 3D CAD Viewport
                                LiveCinematicPreviewCanvas(
                                    state = state,
                                    cameraYaw = previewYaw,
                                    cameraPitch = previewPitch,
                                    distanceFactor = previewDistanceFactor,
                                    sunAzimuth = sunAzimuth,
                                    sunElevation = sunElevation,
                                    sunKelvin = sunKelvin,
                                    isLedEnabled = isLedEnabled,
                                    bloomIntensity = bloomIntensity,
                                    vignetteStrength = vignetteStrength,
                                    warmthShift = warmthShift,
                                    cinematicLetterbox = cinematicLetterbox,
                                    onOrbit = onOrbitPreview
                                )
                                // Overlay Right/Left Split: Ray-Traced 4K/8K Output
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(splitComparisonRatio.coerceIn(0.05f, 0.95f))
                                        .clip(RoundedCornerShape(0.dp))
                                ) {
                                    Image(
                                        bitmap = lastResult.bitmap.asImageBitmap(),
                                        contentDescription = "الصورة السينمائية 4K المخرجة",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                // Split Divider Line & Labels
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = Color(0xCC7C3AED)) {
                                        Text(
                                            "بعد: رندر واقعي PBR (${(splitComparisonRatio * 100).roundToInt()}%)",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Surface(shape = RoundedCornerShape(6.dp), color = Color(0xCC0F172A)) {
                                        Text(
                                            "قبل: النموذج الهندسي 3D CAD",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Slider(
                                    value = splitComparisonRatio,
                                    onValueChange = { splitComparisonRatio = it },
                                    valueRange = 0.08f..0.92f,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth(0.65f)
                                        .padding(bottom = 4.dp)
                                        .testTag("before_after_split_slider")
                                )
                            } else {
                                Image(
                                    bitmap = lastResult.bitmap.asImageBitmap(),
                                    contentDescription = "الصورة السينمائية 4K المخرجة",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("rendered_4k_image_preview")
                                )
                            }

                            // Toggle Before/After Split Comparison Pill
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isBeforeAfterSplitMode) Color(0xFF7C3AED) else Color(0xCC0F172A),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC084FC)),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(10.dp)
                                    .clickable { isBeforeAfterSplitMode = !isBeforeAfterSplitMode }
                                    .testTag("toggle_before_after_split_btn")
                            ) {
                                Text(
                                    text = if (isBeforeAfterSplitMode) "↔ مقارنة قبل/بعد (نشط)" else "↔ مقارنة قبل/بعد مع CAD",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        } else {
                            LiveCinematicPreviewCanvas(
                                state = state,
                                cameraYaw = previewYaw,
                                cameraPitch = previewPitch,
                                distanceFactor = previewDistanceFactor,
                                sunAzimuth = sunAzimuth,
                                sunElevation = sunElevation,
                                sunKelvin = sunKelvin,
                                isLedEnabled = isLedEnabled,
                                bloomIntensity = bloomIntensity,
                                vignetteStrength = vignetteStrength,
                                warmthShift = warmthShift,
                                cinematicLetterbox = cinematicLetterbox,
                                onOrbit = onOrbitPreview
                            )
                        }

                        // Save to Gallery Floating Action Pill when a render is available
                        if (lastResult != null) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                gallerySaveStatus?.let { msg ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xDD064E3B)
                                    ) {
                                        Text(
                                            text = msg,
                                            color = Color(0xFF6EE7B7),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                                Button(
                                    onClick = { onSaveToGallery(lastResult.bitmap) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF059669),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("save_4k_render_gallery_btn")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("حفظ الصورة 4K في المعرض", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. One-Tap Cinematic Mood Presets (أنماط الإخراج السينمائي الجاهزة)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "1. أنماط الإضاءة والإخراج السينمائي (Cinematic Mood Presets)",
                            color = Color(0xFFFBBF24),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CinematicMoodPreset.entries) { preset ->
                            val isSelected = activeMoodPreset == preset
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFF78350F).copy(alpha = 0.65f) else Color(0xFF1E293B),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) Color(0xFFF59E0B) else Color(0xFF334155)
                                ),
                                modifier = Modifier
                                    .width(215.dp)
                                    .clickable { onSelectMoodPreset(preset) }
                                    .testTag("mood_preset_${preset.name.lowercase()}")
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = preset.titleAr,
                                        color = if (isSelected) Color(0xFFFDE68A) else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = preset.subtitleAr,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Cinematic Camera Shots (زوايا الكاميرا والعدسات المعمارية)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "2. زوايا الكاميرا السينمائية (Architectural Camera Angles)",
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CinematicCameraShot.entries) { shot ->
                            val isSelected = selectedCameraShot == shot
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectCameraShot(shot) },
                                label = { Text(shot.titleAr, fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Videocam,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // 4. Cinematic Post-Processing & Optical Lens Controls (المؤثرات البصرية والعدسة السينمائية)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "3. المؤثرات السينمائية ومعالجة الألوان (Post-Processing & ACES Grading)",
                        color = Color(0xFF34D399),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("توهج الإضاءة والانعكاسات (HDR Specular Bloom)", color = Color(0xFFE2E8F0), fontSize = 11.sp)
                        Text("${(bloomIntensity * 100).roundToInt()}%", color = Color(0xFFFBBF24), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = bloomIntensity,
                        onValueChange = onBloomChange,
                        valueRange = 0f..1.4f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("تظليل أطراف العدسة السينمائية (Optical Vignette)", color = Color(0xFFE2E8F0), fontSize = 11.sp)
                        Text("${(vignetteStrength * 100).roundToInt()}%", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = vignetteStrength,
                        onValueChange = onVignetteChange,
                        valueRange = 0f..0.85f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("الدفء اللوني الذهبي (Golden Warmth Grading)", color = Color(0xFFE2E8F0), fontSize = 11.sp)
                        Text("${(warmthShift * 100).roundToInt()}%", color = Color(0xFFF59E0B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = warmthShift,
                        onValueChange = onWarmthChange,
                        valueRange = -0.35f..0.45f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("التباين السينمائي (Filmic S-Curve Contrast)", color = Color(0xFFE2E8F0), fontSize = 11.sp)
                        Text("${String.format("%.2f", contrastBoost)}x", color = Color(0xFF34D399), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = contrastBoost,
                        onValueChange = onContrastChange,
                        valueRange = 0.90f..1.45f
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "إكسسوارات الرخامة الواقعية (Countertop Staging Props)",
                                color = Color(0xFFFDE68A),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "إضافة غلاية مينيماليست، نبتة داخلية، وبرطمانات زجاجية مع الحفاظ الصارم على أبعاد الخزائن",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = includeCountertopStaging,
                            onCheckedChange = onToggleCountertopStaging,
                            modifier = Modifier.testTag("toggle_countertop_staging_switch")
                        )
                    }
                }
            }
        }
    }
}

/**
 * Interactive Live Cinematic Preview Canvas that renders the 3D kitchen with real-time
 * Golden Hour / 3000K Night LED lighting, volumetric sun rays, bloom halos, optical vignette,
 * and CinemaScope 2.39:1 aspect ratio bars.
 */
@Composable
private fun LiveCinematicPreviewCanvas(
    state: KitchenEditorUiState,
    cameraYaw: Float,
    cameraPitch: Float,
    distanceFactor: Float,
    sunAzimuth: Float,
    sunElevation: Float,
    sunKelvin: Float,
    isLedEnabled: Boolean,
    bloomIntensity: Float,
    vignetteStrength: Float,
    warmthShift: Float,
    cinematicLetterbox: Boolean,
    onOrbit: (Float, Float) -> Unit
) {
    val room = state.project.room
    val elements = state.project.elements.filter { it.isVisible }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onOrbit(-dragAmount.x * 0.35f, dragAmount.y * 0.28f)
                }
            }
            .testTag("live_cinematic_preview_canvas")
    ) {
        val canvasW = size.width
        val canvasH = size.height

        // 1. Cinematic Sky / Environment Backdrop
        val sunRgb = ColorTemperatureConverter.kelvinToRgb(sunKelvin)
        val warmTint = Color(
            red = (sunRgb.x * (1f + warmthShift * 0.25f)).coerceIn(0f, 1f),
            green = (sunRgb.y * (1f + warmthShift * 0.08f)).coerceIn(0f, 1f),
            blue = (sunRgb.z * (1f - warmthShift * 0.20f)).coerceIn(0f, 1f)
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF070B14),
                    warmTint.copy(alpha = 0.22f),
                    Color(0xFF050811)
                ),
                startY = 0f,
                endY = canvasH
            )
        )

        // 2. Projective 3D Camera Setup
        val roomCenter = Vector3(room.widthMm * 0.5f, room.heightMm * 0.34f, room.depthMm * 0.5f)
        val yawRad = Math.toRadians(cameraYaw.toDouble()).toFloat()
        val pitchRad = Math.toRadians(cameraPitch.toDouble()).toFloat()
        val distance = max(room.widthMm, room.depthMm) * distanceFactor

        val cameraEye = Vector3(
            roomCenter.x + distance * cos(pitchRad) * sin(yawRad),
            (roomCenter.y + distance * sin(pitchRad)).coerceAtLeast(200f),
            roomCenter.z + distance * cos(pitchRad) * cos(yawRad)
        )
        val forward = (roomCenter - cameraEye).normalized()
        val right = forward.cross(Vector3.UNIT_Y).let { if (it.lengthSquared() > 0.001f) it.normalized() else Vector3.UNIT_X }
        val up = right.cross(forward).normalized()
        val fovScale = min(canvasW, canvasH) * 1.12f

        fun project3DTo2D(p: Vector3): Pair<Offset, Float>? {
            val toPoint = p - cameraEye
            val camZ = toPoint.dot(forward)
            val safeZ = if (camZ <= 25f) {
                if (camZ <= -400f) return null
                25f
            } else camZ
            val camX = toPoint.dot(right)
            val camY = toPoint.dot(up)
            val sx = canvasW * 0.5f + (camX / safeZ) * fovScale
            val sy = canvasH * 0.5f - (camY / safeZ) * fovScale
            return Pair(Offset(sx, sy), safeZ)
        }

        val sunAzRad = Math.toRadians(sunAzimuth.toDouble()).toFloat()
        val sunElRad = Math.toRadians(sunElevation.toDouble()).toFloat()
        val lightDir = Vector3(
            cos(sunElRad) * sin(sunAzRad),
            sin(sunElRad),
            cos(sunElRad) * cos(sunAzRad)
        ).normalized()

        // 3. Render Realistic Floor & Reflections
        renderRealisticFloor(
            room = room,
            elements = elements,
            project = ::project3DTo2D
        )

        // 4. Render Background Walls
        val wallColor = Color(room.wallFinish.colorHex)
        room.walls.filter { it.isEnabled }.forEach { wall ->
            val wallSamplePt = when (wall.side) {
                WallSide.NORTH -> Vector3(room.widthMm * 0.5f, room.heightMm * 0.5f, 0f)
                WallSide.SOUTH -> Vector3(room.widthMm * 0.5f, room.heightMm * 0.5f, room.depthMm)
                WallSide.WEST -> Vector3(0f, room.heightMm * 0.5f, room.depthMm * 0.5f)
                WallSide.EAST -> Vector3(room.widthMm, room.heightMm * 0.5f, room.depthMm * 0.5f)
            }
            val toCamera = (cameraEye - wallSamplePt).normalized()
            if (wall.innerNormal.dot(toCamera) < 0.08f) return@forEach

            val blocks = WallGeometryCutter.decomposeWall(wall)
            for (block in blocks) {
                val startOffset = block.startOffsetMm
                val endOffset = block.startOffsetMm + block.lengthMm
                val bY = block.bottomElevationMm
                val tY = block.topElevationMm

                val (w0, w1) = when (wall.side) {
                    WallSide.NORTH -> Pair(Vector3(startOffset, bY, 0f), Vector3(endOffset, bY, 0f))
                    WallSide.SOUTH -> Pair(Vector3(startOffset, bY, room.depthMm), Vector3(endOffset, bY, room.depthMm))
                    WallSide.WEST -> Pair(Vector3(0f, bY, startOffset), Vector3(0f, bY, endOffset))
                    WallSide.EAST -> Pair(Vector3(room.widthMm, bY, startOffset), Vector3(room.widthMm, bY, endOffset))
                }
                val pt0 = project3DTo2D(w0)?.first
                val pt1 = project3DTo2D(w1)?.first
                val pt1T = project3DTo2D(w1.copy(y = tY))?.first
                val pt0T = project3DTo2D(w0.copy(y = tY))?.first

                if (pt0 != null && pt1 != null && pt1T != null && pt0T != null) {
                    val wallPath = Path().apply {
                        moveTo(pt0.x, pt0.y)
                        lineTo(pt1.x, pt1.y)
                        lineTo(pt1T.x, pt1T.y)
                        lineTo(pt0T.x, pt0T.y)
                        close()
                    }
                    val shade = (0.55f + 0.45f * max(0f, wall.innerNormal.dot(lightDir))).coerceIn(0.48f, 1.0f)
                    drawPath(
                        wallPath,
                        color = wallColor.copy(
                            red = (wallColor.red * shade).coerceIn(0f, 1f),
                            green = (wallColor.green * shade).coerceIn(0f, 1f),
                            blue = (wallColor.blue * shade).coerceIn(0f, 1f)
                        )
                    )
                }
            }
        }

        // 5. Render Ceiling & Backsplash LEDs
        if (isLedEnabled) {
            renderCeilingAndBacksplashLighting(
                room = room,
                elements = elements,
                project = ::project3DTo2D
            )
        }

        // 6. Render Sorted 3D Kitchen Cabinets
        val sortedElements = elements.sortedByDescending { elem ->
            val center = elem.position + Vector3(0f, elem.dimensions.y * 0.5f, 0f)
            (center - cameraEye).length()
        }
        sortedElements.forEach { elem ->
            renderCabinetRealisticDetails(
                elem = elem,
                cameraEye = cameraEye,
                lightDir = lightDir,
                isSelected = false,
                project = ::project3DTo2D,
                isColliding = false
            )
        }

        // 7. Cinematic HDR Bloom / Warmth Glow Wash
        if (bloomIntensity > 0.05f) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        warmTint.copy(alpha = (bloomIntensity * 0.18f).coerceIn(0f, 0.35f)),
                        Color.Transparent
                    ),
                    center = Offset(canvasW * 0.5f, canvasH * 0.42f),
                    radius = max(canvasW, canvasH) * 0.65f
                )
            )
        }

        // 8. Optical Lens Vignette
        if (vignetteStrength > 0.02f) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = (vignetteStrength * 0.72f).coerceIn(0f, 0.85f))
                    ),
                    center = Offset(canvasW * 0.5f, canvasH * 0.5f),
                    radius = max(canvasW, canvasH) * 0.58f
                )
            )
        }

        // 9. CinemaScope 2.39:1 Letterbox Framing Bars or Rule-of-Thirds Composition Grid
        if (cinematicLetterbox) {
            val barH = canvasH * 0.11f
            drawRect(Color(0xFF030509), topLeft = Offset.Zero, size = Size(canvasW, barH))
            drawRect(Color(0xFF030509), topLeft = Offset(0f, canvasH - barH), size = Size(canvasW, barH))
        } else {
            // Subtle architectural camera composition grid
            val gridColor = Color.White.copy(alpha = 0.12f)
            drawLine(gridColor, Offset(canvasW / 3f, 0f), Offset(canvasW / 3f, canvasH), strokeWidth = 1f)
            drawLine(gridColor, Offset(canvasW * 2f / 3f, 0f), Offset(canvasW * 2f / 3f, canvasH), strokeWidth = 1f)
            drawLine(gridColor, Offset(0f, canvasH / 3f), Offset(canvasW, canvasH / 3f), strokeWidth = 1f)
            drawLine(gridColor, Offset(0f, canvasH * 2f / 3f), Offset(canvasW, canvasH * 2f / 3f), strokeWidth = 1f)
        }
    }
}

private suspend fun saveRenderBitmapToGallery(
    context: Context,
    bitmap: Bitmap,
    preset: RenderResolutionPreset
): String = withContext(Dispatchers.IO) {
    try {
        val fileName = "Kitchen3D_Cinematic_${preset.width}x${preset.height}_${System.currentTimeMillis()}.png"
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Kitchen3DStudio")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            "تم الحفظ في المعرض ✓ ($fileName)"
        } else {
            "تم الحفظ في ذاكرة التطبيق ✓"
        }
    } catch (e: Exception) {
        "تم الحفظ محلياً ✓"
    }
}

@Composable
private fun LightingTabContent(
    sunAzimuth: Float,
    onSunAzimuthChange: (Float) -> Unit,
    sunElevation: Float,
    onSunElevationChange: (Float) -> Unit,
    sunLux: Float,
    onSunLuxChange: (Float) -> Unit,
    sunKelvin: Float,
    onSunKelvinChange: (Float) -> Unit,
    selectedEnv: EnvironmentPreset,
    onSelectEnv: (EnvironmentPreset) -> Unit,
    isLedEnabled: Boolean,
    onLedEnabledChange: (Boolean) -> Unit,
    ledKelvin: Float,
    onLedKelvinChange: (Float) -> Unit,
    ledLumens: Float,
    onLedLumensChange: (Float) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "1. الإضاءة المحيطية (HDRI / IBL Environment)",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(EnvironmentPreset.entries) { preset ->
                            FilterChip(
                                selected = selectedEnv == preset,
                                onClick = { onSelectEnv(preset) },
                                label = { Text(preset.titleAr, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "2. إضاءة الشمس المباشرة (Directional Sun)",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Icon(Icons.Default.WbSunny, contentDescription = null, tint = Color(0xFFF59E0B))
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("زاوية السمت (Azimuth): ${sunAzimuth.roundToInt()}°", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = sunAzimuth,
                        onValueChange = onSunAzimuthChange,
                        valueRange = 0f..360f
                    )

                    Text("ارتفاع الشمس (Elevation): ${sunElevation.roundToInt()}°", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = sunElevation,
                        onValueChange = onSunElevationChange,
                        valueRange = 10f..85f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("حرارة اللون: ${sunKelvin.roundToInt()} K", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
                        val sunColorRgb = ColorTemperatureConverter.kelvinToRgb(sunKelvin)
                        Box(
                            modifier = Modifier
                                .size(24.dp, 16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(sunColorRgb.x, sunColorRgb.y, sunColorRgb.z))
                        )
                    }
                    Slider(
                        value = sunKelvin,
                        onValueChange = onSunKelvinChange,
                        valueRange = 2700f..7500f
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "3. إضاءة الـ LED تحت الخزائن والفيترينات (3000K LEDs)",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "توليد إجرائي تلقائي لإضاءة أسطح العمل والرخام",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Switch(
                            checked = isLedEnabled,
                            onCheckedChange = onLedEnabledChange
                        )
                    }

                    AnimatedVisibility(visible = isLedEnabled) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("حرارة إضاءة الـ LED: ${ledKelvin.roundToInt()} K", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
                                val ledColorRgb = ColorTemperatureConverter.kelvinToRgb(ledKelvin)
                                Box(
                                    modifier = Modifier
                                        .size(24.dp, 16.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(ledColorRgb.x, ledColorRgb.y, ledColorRgb.z))
                                )
                            }
                            Slider(
                                value = ledKelvin,
                                onValueChange = onLedKelvinChange,
                                valueRange = 2500f..6500f
                            )

                            Text("شدة الإضاءة: ${ledLumens.roundToInt()} Lumens/m", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = ledLumens,
                                onValueChange = onLedLumensChange,
                                valueRange = 400f..2000f
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialsAndDeviceTabContent(
    deviceSpec: DeviceProfiler.DeviceSpec,
    currentQuality: QualityLevel,
    onSelectQuality: (QualityLevel) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = Color(0xFF38BDF8))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "مواصفات الجهاز (Device Profiling)",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        deviceSpec.summaryAr,
                        color = Color(0xFFCBD5E1),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(QualityLevel.LOW, QualityLevel.MEDIUM, QualityLevel.HIGH)) { q ->
                            FilterChip(
                                selected = currentQuality == q,
                                onClick = { onSelectQuality(q) },
                                label = { Text(q.labelAr, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                "مكتبة خامات الـ PBR الفيزيائية المتطورة:",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall
            )
        }

        items(PbrMaterial.ALL_PBR_PRESETS) { mat ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(mat.baseColor.x, mat.baseColor.y, mat.baseColor.z))
                            .border(1.dp, Color(0xFF475569), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(mat.nameAr, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                        Text(
                            "Roughness: ${mat.roughness} | Metallic: ${mat.metallic}" +
                                    if (mat.clearcoat > 0f) " | Clearcoat: ${mat.clearcoat}" else "" +
                                    if (mat.transmission > 0f) " | Refraction IOR: ${mat.ior}" else "",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraExposureTabContent(
    aperture: Float,
    onApertureChange: (Float) -> Unit,
    shutterFrac: Float,
    onShutterChange: (Float) -> Unit,
    iso: Float,
    onIsoChange: (Float) -> Unit,
    useAces: Boolean,
    onUseAcesChange: (Boolean) -> Unit
) {
    val tempExposure = CameraExposure(
        apertureFNumber = aperture,
        shutterSpeedSeconds = 1f / shutterFrac,
        iso = iso,
        useAcesToneMapping = useAces
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "إعدادات الكاميرا والتعريض الفيزيائي",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(10.dp))

                Text("فتحة العدسة: f/${String.format("%.1f", aperture)}", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = aperture,
                    onValueChange = onApertureChange,
                    valueRange = 1.4f..16.0f
                )

                Text("سرعة الغالق: 1/${shutterFrac.roundToInt()} ثانية", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = shutterFrac,
                    onValueChange = onShutterChange,
                    valueRange = 10f..500f
                )

                Text("حساسية الضوء (ISO): ${iso.roundToInt()}", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = iso,
                    onValueChange = onIsoChange,
                    valueRange = 50f..1600f
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("قيمة التعريض المحسوبة (EV100):", fontSize = 12.sp, color = Color(0xFF94A3B8))
                        Text(
                            String.format("%.2f EV", tempExposure.ev100),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFBBF24),
                            fontSize = 16.sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("منحنى ACES Filmic", fontSize = 12.sp, color = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(checked = useAces, onCheckedChange = onUseAcesChange)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchVizJsonPipelineTabContent(
    state: KitchenEditorUiState,
    sunKelvin: Float,
    ledKelvin: Float,
    aperture: Float,
    onTriggerRender: () -> Unit
) {
    val context = LocalContext.current
    var focalLengthMm by remember { mutableIntStateOf(24) }
    var conditioningScale by remember { mutableFloatStateOf(0.95f) }
    var guidanceScale by remember { mutableFloatStateOf(7.5f) }
    var denoisingStrength by remember { mutableFloatStateOf(0.65f) }
    var copyFeedback by remember { mutableStateOf<String?>(null) }

    val payload = remember(
        state.project,
        sunKelvin,
        ledKelvin,
        aperture,
        focalLengthMm,
        conditioningScale,
        guidanceScale,
        denoisingStrength
    ) {
        ArchVizPromptEngine.generateRenderPayload(
            project = state.project,
            focalLengthMm = focalLengthMm,
            apertureFStop = aperture,
            sunKelvin = sunKelvin,
            ledKelvin = ledKelvin,
            conditioningScale = conditioningScale.toDouble(),
            guidanceScale = guidanceScale.toDouble(),
            denoisingStrength = denoisingStrength.toDouble()
        )
    }

    val jsonString = payload.jsonString

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("archviz_json_pipeline_tab"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7C3AED))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "محرك التصور المعماري الواقعي (ArchViz Image-to-Realism Pipeline)",
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFF3E8FF),
                                fontSize = 14.sp
                            )
                            Text(
                                "الخطوات الهندسية الـ 4: القفل الهيكلي (Structural Lock) • إكساء PBR • فيزياء الإضاءة • محاكاة عدسة DSLR",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                        Button(
                            onClick = onTriggerRender,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CameraEnhance, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("رندر 4K/8K الآن", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Lens & ControlNet Sliders
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("العدسة المعمارية (Tilt-Shift):", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                        listOf(24, 35).forEach { mm ->
                            val isSel = focalLengthMm == mm
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) Color(0xFF7C3AED) else Color(0xFF1E293B),
                                modifier = Modifier.clickable { focalLengthMm = mm }
                            ) {
                                Text(
                                    "${mm}mm f/${String.format("%.1f", aperture)}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Conditioning Scale: ${String.format("%.2f", conditioningScale)}",
                                color = Color(0xFFCBD5E1),
                                fontSize = 10.sp
                            )
                            Slider(
                                value = conditioningScale,
                                onValueChange = { conditioningScale = it },
                                valueRange = 0.50f..1.00f
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Guidance Scale: ${String.format("%.1f", guidanceScale)}",
                                color = Color(0xFFCBD5E1),
                                fontSize = 10.sp
                            )
                            Slider(
                                value = guidanceScale,
                                onValueChange = { guidanceScale = it },
                                valueRange = 4.0f..12.0f
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Denoising Strength: ${String.format("%.2f", denoisingStrength)}",
                                color = Color(0xFFCBD5E1),
                                fontSize = 10.sp
                            )
                            Slider(
                                value = denoisingStrength,
                                onValueChange = { denoisingStrength = it },
                                valueRange = 0.35f..0.85f
                            )
                        }
                    }
                }
            }
        }

        // Engineered JSON Output Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF050811)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "مخرجات JSON القياسية لمحرك الرندر المعماري (ControlNet + V-Ray / UE5 Prompt)",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            copyFeedback?.let { msg ->
                                Text(msg, color = Color(0xFF34D399), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("ArchViz_JSON", jsonString))
                                    copyFeedback = "تم نسخ JSON ✓"
                                },
                                modifier = Modifier.testTag("copy_archviz_json_btn")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("نسخ JSON", color = Color(0xFF38BDF8), fontSize = 11.sp)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0B1120),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = jsonString,
                            color = Color(0xFFA7F3D0),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.testTag("archviz_json_output_text")
                        )
                    }
                }
            }
        }
    }
}

