package com.example.presentation.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.camera.CameraMode
import com.example.core.camera.CameraNavigationPreset
import com.example.presentation.viewmodel.KitchenEditorUiState
import kotlin.math.*

/**
 * On-screen 3D Camera Navigation Controls:
 * - Floating Cinematic Camera Presets Bar (Orbit, Walk, Top, Iso, Front, Focus)
 * - Virtual Thumbstick for First-Person Walkthrough
 * - Eye-level height HUD and collision prevention feedback
 */
@Composable
fun CameraNavigationOverlay(
    state: KitchenEditorUiState,
    onPresetSelected: (CameraNavigationPreset) -> Unit,
    onWalkMovement: (Float, Float) -> Unit,
    onToggleSprint: () -> Unit,
    onExitWalk: () -> Unit,
    onResetPan: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.isCleanCanvasMode) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // 1. Floating Camera Presets Bar (positioned cleanly below the compact top pill)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 42.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xCC0F172A))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(20.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            PresetChip(
                label = "مدار 3D",
                icon = Icons.Default.RotateRight,
                isSelected = state.cameraMode == CameraMode.ORBIT && state.activeCameraPreset == null,
                onClick = { onPresetSelected(CameraNavigationPreset.PERSPECTIVE) },
                tag = "preset_orbit"
            )

            PresetChip(
                label = "مشي 160سم",
                icon = Icons.Default.DirectionsWalk,
                isSelected = state.cameraMode == CameraMode.FIRST_PERSON_WALK,
                badgeColor = Color(0xFF10B981),
                onClick = { onPresetSelected(CameraNavigationPreset.FIRST_PERSON) },
                tag = "preset_walk"
            )

            PresetChip(
                label = "علوي",
                icon = Icons.Default.GridOn,
                isSelected = state.cameraMode == CameraMode.TOP_DOWN,
                onClick = { onPresetSelected(CameraNavigationPreset.TOP_VIEW) },
                tag = "preset_top"
            )

            PresetChip(
                label = "أيزومتري",
                icon = Icons.Default.ViewInAr,
                isSelected = state.activeCameraPreset == CameraNavigationPreset.ISOMETRIC,
                onClick = { onPresetSelected(CameraNavigationPreset.ISOMETRIC) },
                tag = "preset_iso"
            )

            if (state.selectedElementIds.isNotEmpty()) {
                PresetChip(
                    label = "تركيز",
                    icon = Icons.Default.CenterFocusStrong,
                    isSelected = state.activeCameraPreset == CameraNavigationPreset.FOCUS_SELECTION,
                    badgeColor = Color(0xFFF59E0B),
                    onClick = { onPresetSelected(CameraNavigationPreset.FOCUS_SELECTION) },
                    tag = "preset_focus"
                )
            }

            if (state.cameraPanX != 0f || state.cameraPanY != 0f) {
                IconButton(
                    onClick = onResetPan,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("btn_reset_pan")
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "إعادة ضبط الإزاحة",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // 2. First-Person Walkthrough HUD & Virtual Controls
        AnimatedVisibility(
            visible = state.cameraMode == CameraMode.FIRST_PERSON_WALK,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Eye-level & Collision Status Banner
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        color = Color(0xCC0F172A),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            Text("ارتفاع العين: 160 سم", color = Color(0xFFE2E8F0), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    state.walkCollisionFeedback?.let { feedback ->
                        Surface(
                            color = Color(0xE67F1D1D),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
                                Text(feedback, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Virtual Thumbstick on Bottom-Left for Walking
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 24.dp)
                ) {
                    VirtualJoystick(
                        onMove = onWalkMovement,
                        modifier = Modifier.testTag("virtual_joystick")
                    )
                }

                // Walk Controls on Bottom-Right (Sprint Toggle, Exit)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    // Sprint Toggle
                    FilledTonalIconButton(
                        onClick = onToggleSprint,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (state.isWalkSprinting) Color(0xFFF59E0B) else Color(0xCC1E293B),
                            contentColor = if (state.isWalkSprinting) Color.Black else Color.White
                        ),
                        modifier = Modifier.testTag("btn_toggle_sprint")
                    ) {
                        Icon(
                            Icons.Default.FlashOn,
                            contentDescription = "ركض سريع 2x",
                            tint = if (state.isWalkSprinting) Color.Black else Color(0xFFF59E0B)
                        )
                    }

                    // Exit Walk Mode Button
                    FilledIconButton(
                        onClick = onExitWalk,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xCCEF4444),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("btn_exit_walk")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "خروج من وضع المشي")
                    }
                }

                // Look Around Prompt (Subtle hint in top-right)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp),
                    color = Color(0x990F172A),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "اسحب الشاشة للالتفات 360°",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    badgeColor: Color? = null,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) (badgeColor ?: Color(0xFF0284C7)) else Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else Color(0xFF94A3B8),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/**
 * Interactive touch thumbstick emitting normalized direction values (forward [-1, 1], strafe [-1, 1]).
 */
@Composable
fun VirtualJoystick(
    onMove: (forward: Float, strafe: Float) -> Unit,
    modifier: Modifier = Modifier,
    radiusDp: Float = 60f
) {
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    val maxRadiusPx = radiusDp * 2.5f

    Box(
        modifier = modifier
            .size((radiusDp * 2).dp)
            .clip(CircleShape)
            .background(Color(0x800F172A))
            .border(2.dp, Color(0x6638BDF8), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val center = Offset(size.width * 0.5f, size.height * 0.5f)
                        val delta = offset - center
                        val dist = delta.getDistance()
                        val clamped = if (dist > maxRadiusPx) delta * (maxRadiusPx / dist) else delta
                        thumbOffset = clamped

                        // Normalize: Up is forward (-Y is +forward), Right is +strafe
                        val forward = -clamped.y / maxRadiusPx
                        val strafe = clamped.x / maxRadiusPx
                        onMove(forward, strafe)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = thumbOffset + dragAmount
                        val dist = newOffset.getDistance()
                        val clamped = if (dist > maxRadiusPx) newOffset * (maxRadiusPx / dist) else newOffset
                        thumbOffset = clamped

                        val forward = -clamped.y / maxRadiusPx
                        val strafe = clamped.x / maxRadiusPx
                        onMove(forward, strafe)
                    },
                    onDragEnd = {
                        thumbOffset = Offset.Zero
                        onMove(0f, 0f)
                    },
                    onDragCancel = {
                        thumbOffset = Offset.Zero
                        onMove(0f, 0f)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Inner Thumb Pad
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.x.roundToInt(), thumbOffset.y.roundToInt()) }
                .size(46.dp)
                .clip(CircleShape)
                .background(Color(0xEE0284C7))
                .border(2.dp, Color(0xFF38BDF8), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.ControlCamera,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
