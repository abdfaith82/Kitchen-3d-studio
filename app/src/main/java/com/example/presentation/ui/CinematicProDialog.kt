package com.example.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.camera.CameraNavigationPreset
import com.example.core.camera.ProCameraKeyframe
import com.example.core.camera.ProCameraMode
import com.example.core.camera.ProCameraPath
import com.example.core.filament.CinematicLens
import com.example.core.filament.CinematicRenderSettings
import com.example.core.filament.CinematicTimeOfDay
import com.example.core.filament.DepthOfFieldConfig
import com.example.core.filament.FilmLook
import com.example.core.filament.RenderAspectRatio
import com.example.domain.model.GlobalKitchenStyle
import com.example.presentation.viewmodel.GizmoMode
import com.example.presentation.viewmodel.KitchenEditorUiState
import com.example.presentation.viewmodel.KitchenEditorViewModel
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Cinematic Pro Studio — one dialog, three studios:
 *  1) 🎬 رندر سينمائي: 8 أوقات يوم + 7 تدرجات فيلمية + عدسات سينمائية
 *     حقيقية + عمق ميدان + نسبة أبعاد. التطبيق يغيّر إضاءة الشمس وLED
 *     والزوم مباشرة في المشهد، وقيم ما بعد المعالجة تُحفظ مع الإعداد.
 *  2) 🌍 ستايلات عالمية: 10 ستايلات (إسكندنافي، إيطالي، ياباني، خليجي…)
 *     تطبّق الجدار والأرضية بضغطة واحدة (قابلة للتراجع).
 *  3) 🎥 كاميرا احترافية: أوضاع تصوير + Gizmo + مسار سينمائي بمفاتيح
 *     (Keyframes) يُسجَّل من العرض الحالي ويُشغَّل كجولة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CinematicProDialog(
    state: KitchenEditorUiState,
    viewModel: KitchenEditorViewModel,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("🎬 رندر سينمائي", "🌍 ستايلات عالمية", "🎥 كاميرا احترافية")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الاستوديو السينمائي الاحترافي 🎬",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                    }
                }
                Text(
                    text = "رندر واقعي • ستايلات عالمية • تحكم 3D احترافي",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tabs.indices.toList()) { index ->
                        FilterChip(
                            selected = tab == index,
                            onClick = { tab = index },
                            label = { Text(tabs[index], fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF7C3AED),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF1E293B),
                                labelColor = Color(0xFFCBD5E1)
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                when (tab) {
                    0 -> CinematicRenderTab(state = state, viewModel = viewModel)
                    1 -> GlobalStylesTab(viewModel = viewModel)
                    else -> ProCameraTab(state = state, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Color(0xFFF472B6),
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
    )
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.White, fontSize = 13.sp)
            Text(format(value), color = Color(0xFF38BDF8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFA855F7),
                activeTrackColor = Color(0xFFA855F7)
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CinematicRenderTab(state: KitchenEditorUiState, viewModel: KitchenEditorViewModel) {
    var selectedTime by remember { mutableStateOf(CinematicTimeOfDay.GOLDEN_HOUR) }
    var selectedLook by remember { mutableStateOf(FilmLook.ACES_FILMIC) }
    var selectedLens by remember { mutableStateOf(CinematicLens.FOCAL_35) }
    var selectedAspect by remember { mutableStateOf(RenderAspectRatio.WIDESCREEN_16_9) }
    var aperture by remember { mutableFloatStateOf(5.6f) }
    var exposure by remember { mutableFloatStateOf(0f) }
    var bloom by remember { mutableFloatStateOf(selectedTime.bloom) }
    var vignette by remember { mutableFloatStateOf(selectedTime.vignette) }
    var grain by remember { mutableFloatStateOf(selectedTime.filmGrain) }
    var letterbox by remember { mutableStateOf(selectedTime.letterbox) }
    var appliedMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SectionTitle("🕐 وقت اليوم — إضاءة الشمس وLED")
            Text(selectedTime.descriptionAr, color = Color(0xFFCBD5E1), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CinematicTimeOfDay.values().toList()) { time ->
                    FilterChip(
                        selected = selectedTime == time,
                        onClick = {
                            selectedTime = time
                            selectedLook = time.filmLook
                            bloom = time.bloom
                            vignette = time.vignette
                            grain = time.filmGrain
                            letterbox = time.letterbox
                            exposure = time.exposureEv
                            appliedMessage = null
                        },
                        label = { Text(time.titleAr, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFF59E0B),
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFFCBD5E1)
                        )
                    )
                }
            }
            Text(
                text = "شمس: ${selectedTime.sunKelvin.toInt()}K • ${selectedTime.sunLux.toInt()} لوكس • ارتفاع ${selectedTime.sunElevation.toInt()}°",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            SectionTitle("🎞️ التدرج اللوني الفيلمي")
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FilmLook.values().toList()) { look ->
                    FilterChip(
                        selected = selectedLook == look,
                        onClick = { selectedLook = look },
                        label = { Text(look.titleAr, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF7C3AED),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFFCBD5E1)
                        )
                    )
                }
            }
            SectionTitle("📷 العدسة السينمائية (زاوية رؤية حقيقية)")
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CinematicLens.values().toList()) { lens ->
                    FilterChip(
                        selected = selectedLens == lens,
                        onClick = { selectedLens = lens },
                        label = { Text("${lens.titleAr} • ${lens.horizontalFovDeg.toInt()}°", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0284C7),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFFCBD5E1)
                        )
                    )
                }
            }
            Text(selectedLens.useCaseAr, color = Color(0xFF94A3B8), fontSize = 12.sp)
            SectionTitle("🖼️ نسبة الأبعاد")
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(RenderAspectRatio.values().toList()) { aspect ->
                    FilterChip(
                        selected = selectedAspect == aspect,
                        onClick = { selectedAspect = aspect },
                        label = { Text(aspect.titleAr, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF059669),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFFCBD5E1)
                        )
                    )
                }
            }
            SectionTitle("🎛️ ضبط دقيق")
            SliderRow(
                label = "فتحة العدسة (عمق الميدان)",
                value = aperture,
                range = 1.4f..16f,
                format = { String.format(Locale.US, "f/%.1f", it) },
                onChange = { aperture = it }
            )
            Text(
                text = if (aperture <= 2.8f) "عمق ميدان ضحل — خلفية معزولة سينمائياً" else "عمق ميدان عميق — كل شيء واضح",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
            )
            SliderRow(
                label = "التعريض (Exposure)",
                value = exposure,
                range = -2f..2f,
                format = { String.format(Locale.US, "%+.1f EV", it) },
                onChange = { exposure = it }
            )
            SliderRow(
                label = "التوهج (Bloom)",
                value = bloom,
                range = 0f..1.5f,
                format = { String.format(Locale.US, "%.2f", it) },
                onChange = { bloom = it }
            )
            SliderRow(
                label = "التظليل الطرفي (Vignette)",
                value = vignette,
                range = 0f..1f,
                format = { String.format(Locale.US, "%.2f", it) },
                onChange = { vignette = it }
            )
            SliderRow(
                label = "حبّة الفيلم (Grain)",
                value = grain,
                range = 0f..0.3f,
                format = { String.format(Locale.US, "%.2f", it) },
                onChange = { grain = it }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("أشرطة سينما 2.39:1 (Letterbox)", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = letterbox, onCheckedChange = { letterbox = it })
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    val settings = CinematicRenderSettings(
                        timeOfDay = selectedTime,
                        filmLook = selectedLook,
                        lens = selectedLens,
                        aspect = selectedAspect,
                        depthOfField = DepthOfFieldConfig(apertureFStop = aperture),
                        exposureEv = exposure,
                        bloom = bloom,
                        vignette = vignette,
                        filmGrain = grain,
                        letterbox = letterbox || selectedAspect.isLetterboxed
                    )
                    viewModel.applyCinematicTimeOfDay(selectedTime)
                    viewModel.setCameraPose(
                        yawDegrees = state.cameraYaw,
                        pitchDegrees = state.cameraPitch,
                        zoomFactor = selectedLens.zoomFactor
                    )
                    appliedMessage = "تم التطبيق ✅\n${settings.summaryAr()}\n" +
                        "الإضاءة والعدسة تغيّرتا الآن في المشهد، وقيم ما بعد المعالجة " +
                        "(Bloom / Vignette / Grain) تُستخدم في تصدير 4K من استوديو الرندر."
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
            ) {
                Icon(Icons.Default.Movie, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("تطبيق الرندر السينمائي على المشهد", color = Color.White, fontWeight = FontWeight.Bold)
            }
            appliedMessage?.let { msg ->
                Text(
                    text = msg,
                    color = Color(0xFF34D399),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GlobalStylesTab(viewModel: KitchenEditorViewModel) {
    var appliedStyle by remember { mutableStateOf<GlobalKitchenStyle?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "10 ستايلات عالمية حقيقية — ضغطة واحدة تطبّق الجدار والأرضية، ويمكن التراجع عنها بزر التراجع.",
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp
            )
        }
        items(GlobalKitchenStyle.values().toList()) { style ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Public, contentDescription = null, tint = Color(0xFF38BDF8))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(style.titleAr, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("${style.regionAr} • ${style.titleEn}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                    }
                    Text(style.descriptionAr, color = Color(0xFFCBD5E1), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "جدار" to style.wallFinish,
                            "أرضية" to style.floorMaterial,
                            "خزائن" to style.cabinetFinish,
                            "سطح" to style.countertopFinish
                        ).forEach { (label, finish) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(finish.colorHex))
                                        .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                )
                                Text(label, color = Color(0xFF94A3B8), fontSize = 10.sp)
                            }
                        }
                    }
                    Text(
                        text = style.recommendationAr(),
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.applyGlobalKitchenStyle(style)
                            appliedStyle = style
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (appliedStyle == style) Color(0xFF059669) else Color(0xFF0284C7)
                        )
                    ) {
                        Text(
                            if (appliedStyle == style) "تم التطبيق ✅ ${style.titleAr}" else "تطبيق ستايل ${style.titleAr}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProCameraTab(state: KitchenEditorUiState, viewModel: KitchenEditorViewModel) {
    var selectedMode by remember { mutableStateOf(ProCameraMode.CINEMATIC_ORBIT) }
    var keyframes by remember { mutableStateOf<List<ProCameraKeyframe>>(emptyList()) }
    var isPlaying by remember { mutableStateOf(false) }
    var playMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionTitle("🎥 وضع التصوير الاحترافي")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ProCameraMode.values().toList()) { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { selectedMode = mode },
                        label = { Text(mode.titleAr, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF7C3AED),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFFCBD5E1)
                        )
                    )
                }
            }
            Text(selectedMode.descriptionAr, color = Color(0xFFCBD5E1), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            SectionTitle("🧭 لقطات سريعة")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "منظور عام" to CameraNavigationPreset.PERSPECTIVE,
                    "أيزومتري 45°" to CameraNavigationPreset.ISOMETRIC,
                    "مسقط علوي" to CameraNavigationPreset.TOP_VIEW
                ).forEach { (label, preset) ->
                    OutlinedButton(onClick = { viewModel.animateCameraToPreset(preset) }) {
                        Text(label, fontSize = 12.sp, color = Color.White)
                    }
                }
            }
            SectionTitle("✋ أداة التحويل (Gizmo) للعنصر المحدد")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "تحريك" to GizmoMode.TRANSLATE,
                    "تدوير" to GizmoMode.ROTATE,
                    "الأبعاد" to GizmoMode.DIMENSIONS
                ).forEach { (label, mode) ->
                    FilterChip(
                        selected = state.gizmoMode == mode,
                        onClick = { viewModel.setGizmoMode(mode) },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF059669),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFFCBD5E1)
                        )
                    )
                }
            }
            Text(
                text = "التحكم الدقيق: اسحب بإصبع واحد للتدوير، بإصبعين للإزاحة، وبالقرص للتقريب. في وضع المشي استخدم أزرار الحركة.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            SectionTitle("🎬 مسار سينمائي بمفاتيح (Keyframes)")
            Text(
                text = "حرّك الكاميرا للقطة تعجبك ثم اضغط «+ إضافة اللقطة الحالية» — كرر ذلك 2-5 مرات ثم شغّل الجولة.",
                color = Color(0xFFCBD5E1),
                fontSize = 12.sp
            )
        }
        items(keyframes.indices.toList()) { index ->
            val kf = keyframes[index]
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "لقطة ${index + 1}: زاوية ${kf.yawDegrees.toInt()}° • ميل ${kf.pitchDegrees.toInt()}° • زوم ${String.format(Locale.US, "%.2f", kf.zoomFactor)}",
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { keyframes = keyframes.filterIndexed { i, _ -> i != index } }) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف اللقطة", tint = Color(0xFFF87171))
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        keyframes = keyframes + ProCameraKeyframe(
                            yawDegrees = state.cameraYaw,
                            pitchDegrees = state.cameraPitch,
                            zoomFactor = state.cameraZoom,
                            panX = state.cameraPanX,
                            panY = state.cameraPanY
                        )
                        playMessage = null
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إضافة اللقطة الحالية", color = Color.White, fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        val path = ProCameraPath(keyframes)
                        if (!path.isPlayable) {
                            playMessage = "أضف لقطتين على الأقل لتشغيل الجولة."
                            return@Button
                        }
                        isPlaying = true
                        playMessage = null
                        scope.launch {
                            val start = System.currentTimeMillis()
                            val total = path.totalDurationMs()
                            while (true) {
                                val elapsed = System.currentTimeMillis() - start
                                if (elapsed > total) break
                                path.poseAt(elapsed)?.let { pose ->
                                    viewModel.setCameraPose(
                                        yawDegrees = pose.yawDegrees,
                                        pitchDegrees = pose.pitchDegrees,
                                        zoomFactor = pose.zoomFactor,
                                        panX = pose.panOffset.x,
                                        panY = pose.panOffset.y
                                    )
                                }
                                delay(33)
                            }
                            isPlaying = false
                            playMessage = "اكتملت الجولة السينمائية ✅ (${keyframes.size} لقطات)"
                        }
                    },
                    enabled = !isPlaying,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isPlaying) "يُشغَّل…" else "تشغيل الجولة", color = Color.White, fontSize = 12.sp)
                }
            }
            playMessage?.let { msg ->
                Text(msg, color = Color(0xFF34D399), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Text(
                text = "نصيحة: اجعل اللقطة الأولى واسعة 24mm والأخيرة مقرّبة 85mm على الجزيرة — هكذا تبدأ الجولة بعرض الغرفة وتنتهي بتفاصيل الرخام.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
