/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.graphics.PointF
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.gaurav.avnc.viewmodel.VncViewModel

/**
 * Handler for touch events. It detects various gestures and notifies [dispatcher].
 *
 * TODO: Reduce [PointF] garbage
 */
class TouchHandler(private val viewModel: VncViewModel, private val dispatcher: Dispatcher)
    : ScaleGestureDetector.OnScaleGestureListener, GestureDetector.SimpleOnGestureListener() {

    private val scaleDetector = ScaleGestureDetector(viewModel.getApplication(), this)
    private val gestureDetector = GestureDetector(viewModel.getApplication(), this)
    private val frameScroller = FrameScroller(viewModel) //Should it be in Dispatcher?
    private val dragDetector = DragDetector()
    private val dragEnabled = viewModel.pref.input.gesture.dragEnabled

    init {
        scaleDetector.isQuickScaleEnabled = false
    }

    //Extension to easily access touch position
    private fun MotionEvent.point() = PointF(x, y)

    /****************************************************************************************
     * Touch Event receivers
     ****************************************************************************************/

    fun onTouchEvent(event: MotionEvent): Boolean {
        if (dragEnabled)
            dragDetector.onTouchEvent(event)

        scaleDetector.onTouchEvent(event)
        return gestureDetector.onTouchEvent(event)
    }

    override fun onDown(e: MotionEvent): Boolean {
        frameScroller.stop()
        return true
    }

    /****************************************************************************************
     * Gestures
     ****************************************************************************************/

    override fun onScaleBegin(detector: ScaleGestureDetector) = true
    override fun onScaleEnd(detector: ScaleGestureDetector) {}
    override fun onScale(detector: ScaleGestureDetector): Boolean {
        dispatcher.onScale(detector.scaleFactor, detector.focusX, detector.focusY)
        return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        dispatcher.onTap(e.point())
        return true
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        dispatcher.onDoubleTap(e.point())
        return true
    }

    override fun onLongPress(e: MotionEvent) {
        viewModel.frameViewRef.get()?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        if (dragEnabled)
            dragDetector.onLongPress(e)
        else
            dispatcher.onLongPress(e.point())
    }

    override fun onFling(e1: MotionEvent, e2: MotionEvent, vX: Float, vY: Float): Boolean {
        frameScroller.fling(vX, vY)
        return true
    }

    override fun onScroll(e1: MotionEvent, e2: MotionEvent, dX: Float, dY: Float): Boolean {
        val startPoint = e1.point()
        val currentPoint = e2.point()

        when (e2.pointerCount) {
            1 -> dispatcher.onSwipe1(startPoint, currentPoint, -dX, -dY)
            2 -> dispatcher.onSwipe2(startPoint, currentPoint, -dX, -dY)
        }

        return true
    }

    /***************************************************************************************
     * Small utility class for handling drag gesture (Long Press followed by Swipe/Move)
     **************************************************************************************/

    private inner class DragDetector {
        private var longPressDetected = false
        private var isDragging = false
        private var startPoint = PointF()
        private var lastPoint = PointF()

        fun onLongPress(e: MotionEvent): Boolean {
            longPressDetected = true
            startPoint = e.point()
            lastPoint = startPoint
            return true
        }

        fun onTouchEvent(event: MotionEvent) {
            if (!longPressDetected)
                return

            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    isDragging = true

                    val cp = event.point()
                    dispatcher.onDrag(startPoint, cp, cp.x - lastPoint.x, cp.y - lastPoint.y)
                    lastPoint = cp
                }

                MotionEvent.ACTION_UP -> {
                    if (isDragging)
                        dispatcher.onDragEnd(event.point())
                    else
                        dispatcher.onLongPress(event.point())

                    longPressDetected = false
                    isDragging = false
                }

                MotionEvent.ACTION_POINTER_DOWN,
                MotionEvent.ACTION_CANCEL -> {
                    if (isDragging)
                        dispatcher.onDragEnd(event.point())

                    longPressDetected = false
                    isDragging = false
                }
            }
        }
    }
}