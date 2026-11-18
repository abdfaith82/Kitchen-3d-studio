package com.kitchen3d.studio.renderer

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/**
 * Handles single-touch rotation, dual-touch pinch-to-zoom, and two-finger panning
 * for navigating the realistic 3D kitchen space.
 */
class KitchenTouchGestureListener(
    context: Context,
    private val cameraController: OrbitCameraController
) : View.OnTouchListener {

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                cameraController.onZoom(detector.scaleFactor)
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (e2.pointerCount == 2) {
                    cameraController.onPan(distanceX, distanceY)
                    return true
                }
                return false
            }
        }
    )

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                isDragging = false
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // Re-anchor on the remaining finger to avoid a sudden camera jump
                val liftedIndex = event.actionIndex
                val remainingIndex = if (liftedIndex == 0) 1 else 0
                if (remainingIndex < event.pointerCount) {
                    lastTouchX = event.getX(remainingIndex)
                    lastTouchY = event.getY(remainingIndex)
                }
                isDragging = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging && event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    val deltaX = event.x - lastTouchX
                    val deltaY = event.y - lastTouchY
                    cameraController.onRotate(deltaX, deltaY)
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
            }

            MotionEvent.ACTION_UP -> {
                isDragging = false
                v.performClick()
            }

            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
        return true
    }
}
