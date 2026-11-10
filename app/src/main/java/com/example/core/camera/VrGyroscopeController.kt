package com.example.core.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

/**
 * Real-Time Hardware Gyroscope & Rotation Vector Controller for 360° VR Kitchen Walkthrough.
 *
 * Uses Android's [SensorManager] (`TYPE_GAME_ROTATION_VECTOR`, `TYPE_ROTATION_VECTOR`, or `TYPE_GYROSCOPE`)
 * to track the device's physical orientation and apply smooth 360-degree yaw and pitch deltas
 * to the 3D First-Person Walkthrough camera.
 * Also supports an optional continuous auto-walk forward loop when the user enables VR Hands-Free Walk.
 */
@Composable
fun rememberVrGyroscopeEffect(
    enabled: Boolean,
    autoWalkEnabled: Boolean,
    onLookDelta: (dYawDeg: Float, dPitchDeg: Float) -> Unit,
    onWalkStep: (forwardDeltaM: Float, strafeDeltaM: Float) -> Unit
) {
    val context = LocalContext.current
    val currentOnLookDelta by rememberUpdatedState(onLookDelta)
    val currentOnWalkStep by rememberUpdatedState(onWalkStep)

    // Continuous auto-walk forward loop when VR Auto-Walk is active
    LaunchedEffect(enabled, autoWalkEnabled) {
        if (enabled && autoWalkEnabled) {
            while (isActive) {
                currentOnWalkStep(0.035f, 0f)
                delay(32L) // ~30 FPS smooth walking
            }
        }
    }

    DisposableEffect(enabled, context) {
        if (!enabled) {
            return@DisposableEffect onDispose { }
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        var lastYawDeg: Float? = null
        var lastPitchDeg: Float? = null
        var lastGyroTimestampNs: Long = 0L

        val rotationMatrix = FloatArray(9)
        val remappedMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return

                when (event.sensor.type) {
                    Sensor.TYPE_GAME_ROTATION_VECTOR,
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        // Remap coordinate system for upright/landscape handheld viewing
                        SensorManager.remapCoordinateSystem(
                            rotationMatrix,
                            SensorManager.AXIS_X,
                            SensorManager.AXIS_Z,
                            remappedMatrix
                        )
                        SensorManager.getOrientation(remappedMatrix, orientationAngles)

                        val azimuthDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                        val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()

                        val prevYaw = lastYawDeg
                        val prevPitch = lastPitchDeg

                        if (prevYaw != null && prevPitch != null) {
                            var dYaw = azimuthDeg - prevYaw
                            // Wrap around [-180, 180]
                            if (dYaw > 180f) dYaw -= 360f
                            if (dYaw < -180f) dYaw += 360f

                            val dPitch = -(pitchDeg - prevPitch)

                            // Filter out tiny sensor jitter (< 0.08 deg)
                            if (abs(dYaw) > 0.08f || abs(dPitch) > 0.08f) {
                                currentOnLookDelta(
                                    dYaw.coerceIn(-12f, 12f),
                                    dPitch.coerceIn(-8f, 8f)
                                )
                            }
                        }
                        lastYawDeg = azimuthDeg
                        lastPitchDeg = pitchDeg
                    }

                    Sensor.TYPE_GYROSCOPE -> {
                        if (lastGyroTimestampNs != 0L) {
                            val dtSec = (event.timestamp - lastGyroTimestampNs) * 1e-9f
                            if (dtSec in 0.001f..0.25f) {
                                // Gyro values are rad/s around X (pitch), Y (yaw in portrait), Z
                                val pitchRateDeg = Math.toDegrees(event.values[0].toDouble()).toFloat()
                                val yawRateDeg = -Math.toDegrees(event.values[1].toDouble()).toFloat()
                                val dYaw = yawRateDeg * dtSec
                                val dPitch = pitchRateDeg * dtSec
                                if (abs(dYaw) > 0.05f || abs(dPitch) > 0.05f) {
                                    currentOnLookDelta(
                                        dYaw.coerceIn(-12f, 12f),
                                        dPitch.coerceIn(-8f, 8f)
                                    )
                                }
                            }
                        }
                        lastGyroTimestampNs = event.timestamp
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val targetSensor = rotationSensor ?: gyroSensor
        if (targetSensor != null) {
            sensorManager?.registerListener(
                listener,
                targetSensor,
                SensorManager.SENSOR_DELAY_GAME
            )
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }
}
