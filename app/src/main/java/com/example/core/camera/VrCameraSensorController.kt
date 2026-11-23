package com.example.core.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.*

/**
 * Navigation sub-modes for Camera-Sensor VR Walkthrough:
 * - FULL_OPTICAL_PAN_TILT_WALK: Camera optical flow drives Pan (Yaw), Tilt (Pitch), and Forward/Backward Walk.
 * - OPTICAL_WALK_PLUS_GYRO_LOOK: Camera optical flow drives Forward/Backward/Strafe walking while Gyro/Touch drives Look.
 */
enum class VrCameraNavSubMode(val titleAr: String) {
    FULL_OPTICAL_PAN_TILT_WALK("كامل (التفات + إمالة + مشي بالكاميرا)"),
    OPTICAL_WALK_PLUS_GYRO_LOOK("مشي بصري بالكاميرا + التفات حر")
}

/**
 * Real-time telemetry emitted by the Camera Sensor Optical-Flow & Visual Odometry analyzer.
 */
data class CameraSensorOpticalFlowTelemetry(
    val panDeltaDeg: Float = 0f,
    val tiltDeltaDeg: Float = 0f,
    val walkForwardDeltaM: Float = 0f,
    val strafeDeltaM: Float = 0f,
    val flowVectorX: Float = 0f,
    val flowVectorY: Float = 0f,
    val radialDivergence: Float = 0f,
    val averageLuminance: Float = 128f,
    val trackedPatchesCount: Int = 0,
    val fps: Int = 30,
    val isCameraStreamActive: Boolean = false
)

/**
 * Pure, deterministic Optical Flow & Looming Visual Odometry calculator on downsampled luminance grids.
 *
 * Uses 2D Block-Matching Sum of Absolute Differences (SAD) across consecutive camera frames:
 * - Global translation `(meanDx, meanDy)` -> Camera Pan (horizontal rotation) & Tilt (vertical pitch).
 * - Radial divergence from frame center `dot((dx - meanDx, dy - meanDy), radialUnitVec)` ->
 *   Forward/Backward "Walk" velocity (positive divergence = objects expanding = walking forward;
 *   negative divergence = objects contracting = walking backward).
 */
object VrCameraOpticalFlowEngine {

    const val GRID_W = 24
    const val GRID_H = 18
    private const val SEARCH_RADIUS = 2

    fun computeOpticalFlowAndWalkDelta(
        prevGrid: FloatArray,
        currGrid: FloatArray,
        gridW: Int = GRID_W,
        gridH: Int = GRID_H,
        sensitivity: Float = 1.0f
    ): CameraSensorOpticalFlowTelemetry {
        if (prevGrid.size < gridW * gridH || currGrid.size < gridW * gridH) {
            return CameraSensorOpticalFlowTelemetry()
        }

        var sumLuminance = 0f
        for (i in 0 until (gridW * gridH)) {
            sumLuminance += currGrid[i]
        }
        val avgLum = sumLuminance / (gridW * gridH).toFloat()

        val patchDx = FloatArray(gridW * gridH)
        val patchDy = FloatArray(gridW * gridH)
        val patchValid = BooleanArray(gridW * gridH)

        var sumDx = 0f
        var sumDy = 0f
        var validCount = 0

        // Track interior blocks with local gradient contrast
        for (by in (SEARCH_RADIUS + 1) until (gridH - SEARCH_RADIUS - 1)) {
            for (bx in (SEARCH_RADIUS + 1) until (gridW - SEARCH_RADIUS - 1)) {
                val idx = by * gridW + bx
                val cVal = prevGrid[idx]
                val gradX = abs(prevGrid[idx + 1] - prevGrid[idx - 1])
                val gradY = abs(prevGrid[idx + gridW] - prevGrid[idx - gridW])
                val localContrast = gradX + gradY

                // Skip flat/textureless blocks
                if (localContrast < 6.0f && abs(currGrid[idx] - cVal) < 2.5f) {
                    continue
                }

                var bestDx = 0
                var bestDy = 0
                var bestSad = Float.MAX_VALUE

                for (sy in -SEARCH_RADIUS..SEARCH_RADIUS) {
                    for (sx in -SEARCH_RADIUS..SEARCH_RADIUS) {
                        var sad = 0f
                        for (ky in -1..1) {
                            val rPrev = (by + ky) * gridW
                            val rCurr = (by + sy + ky) * gridW
                            for (kx in -1..1) {
                                sad += abs(currGrid[rCurr + bx + sx + kx] - prevGrid[rPrev + bx + kx])
                            }
                        }
                        // Slight regularization toward zero displacement to prevent noise jumps
                        val penalty = (abs(sx) + abs(sy)) * 1.2f
                        val score = sad + penalty
                        if (score < bestSad) {
                            bestSad = score
                            bestDx = sx
                            bestDy = sy
                        }
                    }
                }

                patchDx[idx] = bestDx.toFloat()
                patchDy[idx] = bestDy.toFloat()
                patchValid[idx] = true
                sumDx += bestDx.toFloat()
                sumDy += bestDy.toFloat()
                validCount++
            }
        }

        if (validCount == 0) {
            return CameraSensorOpticalFlowTelemetry(
                averageLuminance = avgLum,
                trackedPatchesCount = 0,
                isCameraStreamActive = true
            )
        }

        val meanDx = sumDx / validCount.toFloat()
        val meanDy = sumDy / validCount.toFloat()

        // Compute radial divergence (looming expansion/contraction around optical center)
        val centerX = (gridW - 1) * 0.5f
        val centerY = (gridH - 1) * 0.5f
        var radialAccum = 0f
        var radialCount = 0

        for (by in (SEARCH_RADIUS + 1) until (gridH - SEARCH_RADIUS - 1)) {
            for (bx in (SEARCH_RADIUS + 1) until (gridW - SEARCH_RADIUS - 1)) {
                val idx = by * gridW + bx
                if (!patchValid[idx]) continue
                val rx = bx - centerX
                val ry = by - centerY
                val rLen = sqrt(rx * rx + ry * ry)
                if (rLen > 2.0f) {
                    val ux = rx / rLen
                    val uy = ry / rLen
                    val resX = patchDx[idx] - meanDx
                    val resY = patchDy[idx] - meanDy
                    radialAccum += (resX * ux + resY * uy)
                    radialCount++
                }
            }
        }

        val rawDivergence = if (radialCount > 0) (radialAccum / radialCount.toFloat()) else 0f

        val clampedSens = sensitivity.coerceIn(0.3f, 3.0f)
        // Map image-space block displacement to camera pan/tilt degrees and forward walk meters
        // Moving physical camera right causes scene features in image to shift left (-meanDx -> +pan)
        val rawPanDeg = (-meanDx * 2.4f * clampedSens).coerceIn(-9.0f, 9.0f)
        val rawTiltDeg = (meanDy * 1.9f * clampedSens).coerceIn(-6.5f, 6.5f)
        val rawWalkM = (rawDivergence * 0.14f * clampedSens).coerceIn(-0.22f, 0.28f)
        val rawStrafeM = (-meanDx * 0.045f * clampedSens).coerceIn(-0.14f, 0.14f)

        val filteredPan = if (abs(rawPanDeg) >= 0.08f) rawPanDeg else 0f
        val filteredTilt = if (abs(rawTiltDeg) >= 0.08f) rawTiltDeg else 0f
        val filteredWalk = if (abs(rawWalkM) >= 0.008f) rawWalkM else 0f
        val filteredStrafe = if (abs(rawStrafeM) >= 0.008f) rawStrafeM else 0f

        return CameraSensorOpticalFlowTelemetry(
            panDeltaDeg = filteredPan,
            tiltDeltaDeg = filteredTilt,
            walkForwardDeltaM = filteredWalk,
            strafeDeltaM = filteredStrafe,
            flowVectorX = (meanDx / SEARCH_RADIUS.toFloat()).coerceIn(-1f, 1f),
            flowVectorY = (meanDy / SEARCH_RADIUS.toFloat()).coerceIn(-1f, 1f),
            radialDivergence = (rawDivergence / SEARCH_RADIUS.toFloat()).coerceIn(-1f, 1f),
            averageLuminance = avgLum,
            trackedPatchesCount = validCount,
            fps = 30,
            isCameraStreamActive = true
        )
    }

    /**
     * Downsamples an ImageProxy Y-plane ByteBuffer into a [GRID_W] x [GRID_H] luminance array.
     */
    fun extractLuminanceGrid(image: ImageProxy, outGrid: FloatArray) {
        val plane = image.planes.firstOrNull() ?: return
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0) return

        val stepX = (width.toFloat() / GRID_W).coerceAtLeast(1f)
        val stepY = (height.toFloat() / GRID_H).coerceAtLeast(1f)
        val limit = buffer.limit()

        for (gy in 0 until GRID_H) {
            val srcY = ((gy + 0.5f) * stepY).toInt().coerceIn(0, height - 1)
            val rowOffset = srcY * rowStride
            for (gx in 0 until GRID_W) {
                val srcX = ((gx + 0.5f) * stepX).toInt().coerceIn(0, width - 1)
                val byteIndex = rowOffset + srcX * pixelStride
                val lum = if (byteIndex in 0 until limit) {
                    (buffer.get(byteIndex).toInt() and 0xFF).toFloat()
                } else {
                    128f
                }
                outGrid[gy * GRID_W + gx] = lum
            }
        }
    }
}

/**
 * Interactive VR Camera-Sensor Navigation HUD & Real-Time Optical-Flow Walkthrough Controller.
 *
 * Utilizes the device's camera sensor (via CameraX `ImageAnalysis` + `PreviewView`) fused with
 * linear acceleration / step cadence so users can pan, tilt, and physically 'walk' through their
 * rendered 3D kitchen design in real-time.
 */
@Composable
fun VrCameraSensorNavigationOverlay(
    enabled: Boolean,
    sensitivity: Float,
    subMode: VrCameraNavSubMode,
    onSensitivityChange: (Float) -> Unit,
    onSubModeChange: (VrCameraNavSubMode) -> Unit,
    onLookDelta: (dYawDeg: Float, dPitchDeg: Float) -> Unit,
    onWalkStep: (forwardDeltaM: Float, strafeDeltaM: Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!enabled) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnLookDelta by rememberUpdatedState(onLookDelta)
    val currentOnWalkStep by rememberUpdatedState(onWalkStep)
    val currentSensitivity by rememberUpdatedState(sensitivity)
    val currentSubMode by rememberUpdatedState(subMode)

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(enabled) {
        if (enabled && !hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var telemetry by remember { mutableStateOf(CameraSensorOpticalFlowTelemetry()) }
    var isPipMinimized by remember { mutableStateOf(false) }

    // Smoothed exponential moving average for fluid 60fps camera-sensor pan/tilt/walk
    var smoothPan by remember { mutableFloatStateOf(0f) }
    var smoothTilt by remember { mutableFloatStateOf(0f) }
    var smoothWalk by remember { mutableFloatStateOf(0f) }

    // Supplemental IMU linear acceleration / step bobbing fusion so walking forward with the phone
    // feels responsive even when pointing at low-contrast surfaces
    DisposableEffect(enabled, context) {
        if (!enabled) return@DisposableEffect onDispose { }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val linearAccelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        var lastAccelTimestampNs = 0L
        var forwardMomentum = 0f

        val imuListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                when (event.sensor.type) {
                    Sensor.TYPE_STEP_DETECTOR -> {
                        // Physical step detected while holding phone -> advance 0.28m forward in 3D kitchen
                        currentOnWalkStep(0.26f * currentSensitivity, 0f)
                    }
                    Sensor.TYPE_LINEAR_ACCELERATION -> {
                        if (lastAccelTimestampNs != 0L) {
                            val dt = (event.timestamp - lastAccelTimestampNs) * 1e-9f
                            if (dt in 0.005f..0.2f) {
                                val az = -event.values[2] // Forward axis along phone camera optical axis
                                if (abs(az) > 0.45f) {
                                    forwardMomentum = (forwardMomentum * 0.8f + az * dt * 0.09f).coerceIn(-0.08f, 0.12f)
                                    if (abs(forwardMomentum) > 0.01f) {
                                        currentOnWalkStep(forwardMomentum * currentSensitivity, 0f)
                                    }
                                } else {
                                    forwardMomentum *= 0.7f
                                }
                            }
                        }
                        lastAccelTimestampNs = event.timestamp
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (linearAccelSensor != null) {
            sensorManager?.registerListener(imuListener, linearAccelSensor, SensorManager.SENSOR_DELAY_GAME)
        }
        if (stepSensor != null) {
            sensorManager?.registerListener(imuListener, stepSensor, SensorManager.SENSOR_DELAY_FASTEST)
        }

        onDispose {
            sensorManager?.unregisterListener(imuListener)
        }
    }

    // CameraX ImageAnalysis Executor
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.94f),
        border = BorderStroke(1.5.dp, Color(0xFF10B981)),
        modifier = modifier
            .width(if (isPipMinimized) 250.dp else 330.dp)
            .testTag("vr_camera_sensor_pip_hud")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (hasCameraPermission) Color(0xFF10B981) else Color(0xFFF59E0B))
                    )
                    Column {
                        Text(
                            text = "📷 ملاحة VR بحساس الكاميرا (حي)",
                            color = Color(0xFF34D399),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "حرك الهاتف للالتفات والإمالة والمشي داخل المطبخ 3D",
                            color = Color(0xFF94A3B8),
                            fontSize = 9.sp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { isPipMinimized = !isPipMinimized },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            if (isPipMinimized) Icons.Default.OpenInFull else Icons.Default.Minimize,
                            contentDescription = "تصغير/تكبير",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(22.dp)
                            .testTag("close_vr_camera_sensor_btn")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "إغلاق ملاحة الكاميرا",
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            if (!hasCameraPermission) {
                // Permission Request Card + Manual Optical Flow Simulation Joypad fallback
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "يتطلب وضع ملاحة VR البصرية إذن الوصول إلى حساس الكاميرا لتتبع الحركة والتجول الفوري.",
                        color = Color(0xFFFDE68A),
                        fontSize = 10.sp
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("grant_vr_camera_permission_btn")
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تفعيل حساس الكاميرا الآن", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else if (!isPipMinimized) {
                // Live CameraX Preview + Optical Flow Vector Overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(118.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black)
                        .border(1.dp, Color(0xFF059669), RoundedCornerShape(10.dp))
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            }

                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                runCatching {
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }

                                    val prevGrid = FloatArray(VrCameraOpticalFlowEngine.GRID_W * VrCameraOpticalFlowEngine.GRID_H)
                                    val currGrid = FloatArray(VrCameraOpticalFlowEngine.GRID_W * VrCameraOpticalFlowEngine.GRID_H)
                                    var hasPrevFrame = false
                                    var lastFrameTimeMs = System.currentTimeMillis()

                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                        .also { analysis ->
                                            analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                                try {
                                                    VrCameraOpticalFlowEngine.extractLuminanceGrid(imageProxy, currGrid)
                                                    val nowMs = System.currentTimeMillis()
                                                    val dtMs = (nowMs - lastFrameTimeMs).coerceAtLeast(1L)
                                                    lastFrameTimeMs = nowMs

                                                    if (hasPrevFrame) {
                                                        val flow = VrCameraOpticalFlowEngine.computeOpticalFlowAndWalkDelta(
                                                            prevGrid = prevGrid,
                                                            currGrid = currGrid,
                                                            sensitivity = currentSensitivity
                                                        ).copy(
                                                            fps = (1000L / dtMs).toInt().coerceIn(5, 60)
                                                        )

                                                        // Copy currGrid -> prevGrid for next frame
                                                        System.arraycopy(currGrid, 0, prevGrid, 0, currGrid.size)

                                                        // Exponential smoothing
                                                        smoothPan = smoothPan * 0.55f + flow.panDeltaDeg * 0.45f
                                                        smoothTilt = smoothTilt * 0.55f + flow.tiltDeltaDeg * 0.45f
                                                        smoothWalk = smoothWalk * 0.60f + flow.walkForwardDeltaM * 0.40f

                                                        previewView.post {
                                                            telemetry = flow
                                                            when (currentSubMode) {
                                                                VrCameraNavSubMode.FULL_OPTICAL_PAN_TILT_WALK -> {
                                                                    if (abs(smoothPan) > 0.05f || abs(smoothTilt) > 0.05f) {
                                                                        currentOnLookDelta(smoothPan, smoothTilt)
                                                                    }
                                                                    if (abs(smoothWalk) > 0.006f) {
                                                                        currentOnWalkStep(smoothWalk, 0f)
                                                                    }
                                                                }
                                                                VrCameraNavSubMode.OPTICAL_WALK_PLUS_GYRO_LOOK -> {
                                                                    if (abs(smoothWalk) > 0.005f || abs(flow.strafeDeltaM) > 0.005f) {
                                                                        currentOnWalkStep(smoothWalk, flow.strafeDeltaM)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        System.arraycopy(currGrid, 0, prevGrid, 0, currGrid.size)
                                                        hasPrevFrame = true
                                                    }
                                                } catch (_: Exception) {
                                                } finally {
                                                    imageProxy.close()
                                                }
                                            }
                                        }

                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageAnalysis
                                    )
                                }
                            }, ContextCompat.getMainExecutor(ctx))

                            previewView
                        }
                    )

                    // Real-time Optical Flow & Walk Radar HUD Canvas Overlay
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width * 0.5f
                        val cy = size.height * 0.5f

                        // Reticle rings
                        drawCircle(
                            color = Color(0xFF10B981).copy(alpha = 0.45f),
                            radius = 28f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.5f)
                        )

                        // Looming / Walk Ring (expands green when walking forward, amber when stepping back)
                        val walkRadius = (28f + telemetry.radialDivergence * 24f).coerceIn(10f, 52f)
                        drawCircle(
                            color = if (telemetry.walkForwardDeltaM >= 0f) Color(0xFF34D399) else Color(0xFFF59E0B),
                            radius = walkRadius,
                            center = Offset(cx, cy),
                            style = Stroke(width = 2.5f)
                        )

                        // Pan/Tilt Optical Flow Vector Line
                        val vecEnd = Offset(
                            x = cx - telemetry.flowVectorX * (size.width * 0.35f),
                            y = cy + telemetry.flowVectorY * (size.height * 0.35f)
                        )
                        drawLine(
                            color = Color(0xFF38BDF8),
                            start = Offset(cx, cy),
                            end = vecEnd,
                            strokeWidth = 3.5f
                        )
                        drawCircle(
                            color = Color(0xFF38BDF8),
                            radius = 5f,
                            center = vecEnd
                        )
                    }

                    // Live Optical Flow Telemetry Badges inside PiP
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Color(0xAA0F172A))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "التفات: ${String.format(Locale.US, "%+.1f°", telemetry.panDeltaDeg)} | إمالة: ${String.format(Locale.US, "%+.1f°", telemetry.tiltDeltaDeg)}",
                                color = Color(0xFF38BDF8),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "مشي: ${String.format(Locale.US, "%+.2f م", telemetry.walkForwardDeltaM)} (${telemetry.trackedPatchesCount} نقطة)",
                                color = Color(0xFF34D399),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Sub-mode Switcher Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                VrCameraNavSubMode.entries.forEach { modeItem ->
                    val selected = subMode == modeItem
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) Color(0xFF059669) else Color(0xFF1E293B),
                        border = BorderStroke(1.dp, if (selected) Color(0xFF34D399) else Color(0xFF334155)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSubModeChange(modeItem) }
                            .testTag("vr_cam_submode_${modeItem.name}")
                    ) {
                        Text(
                            text = modeItem.titleAr,
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Sensitivity Slider & Quick Walk Step Triggers
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "الحساسية ${String.format(Locale.US, "%.1fx", sensitivity)}",
                    color = Color(0xFFCBD5E1),
                    fontSize = 9.sp
                )
                Slider(
                    value = sensitivity,
                    onValueChange = onSensitivityChange,
                    valueRange = 0.4f..2.5f,
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp)
                        .testTag("vr_camera_sensitivity_slider")
                )
                // Quick Walk Step Buttons (useful for testing on stationary desk)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF0284C7),
                    modifier = Modifier
                        .clickable { onWalkStep(0.35f * sensitivity, 0f) }
                        .testTag("vr_cam_step_forward_btn")
                ) {
                    Text(
                        text = "⬆️ خطوة للأمام",
                        color = Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF334155),
                    modifier = Modifier
                        .clickable { onWalkStep(-0.30f * sensitivity, 0f) }
                        .testTag("vr_cam_step_back_btn")
                ) {
                    Text(
                        text = "⬇️ للخلف",
                        color = Color.White,
                        fontSize = 8.5.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
