package com.vinaysshenoy.jescher

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.OnScaleGestureListener
import android.view.View
import android.view.View.OnTouchListener
import timber.log.Timber
import kotlin.math.absoluteValue

private const val LOG_TAG = "Jescher"

class Jescher @JvmOverloads constructor(
	private val view: View,
	private val minScale: Float = 0F,
	private val maxScale: Float = 0F,
	private val findCurrentMovable: (PointF) -> Movable? = { _ -> null },
	private val findCurrentScalable: (PointF) -> Scalable? = { _ -> null },
	private val onMoveCancel: (Movable) -> Unit = {},
	private val onMoveFinished: (Movable) -> Unit = {},
		// TODO: Register actions for onScale
	private val forwardTouchEvents: View.OnTouchListener = OnTouchListener { _, _ -> false }
) : OnScaleGestureListener {

	/**
	 * Matrix that maintains the current accumulated transformations applied
	 * */
	private val transformMatrix = Matrix()

	/**
	 * Matrix that is used to test transformations on the current transform matrix
	 * before actually applying transformations.
	 *
	 * This is used in the case of clamped transformations, since we need to apply the
	 * transformations first to test whether the final transform needs to be changed or not
	 * */
	private val tempMatrix = Matrix()
	private val tempMatrixValues = FloatArray(9)

	private val curTouchPoint = PointF()
	private val prevTouchPoint = PointF()

	/**
	 * Holds the current scale focus point
	 * */
	private val scaleFocus = PointF()

	/**
	 * Holds the current scale factor
	 * */
	private var scaleFactor: Float = 1F

	private val scaleGestureDetector = ScaleGestureDetector(view.context, this)

	/**
	 * Used to hold the view area which is the target of transformation operations.
	 *
	 * Currently, set to the view's draw rect
	 * */
	private val sourceRect = RectF()

	/**
	 * Used to hold the point in the source rect to which the corresponding view touch points
	 * map if no transformations were done
	 **/
	private val sourcePoint = PointF()

	/**
	 *  Used to maintain the transformed bounds to which the source rect corresponds
	 *  after applying transformations
	 **/
	private val mappedRect = RectF()

	private var currentMovable: Movable? = null

	/**
	 * [MotionEvent.ACTION_CANCEL] is no longer triggered if the user gesture exits the view bounds.
	 *
	 * See this [SO post](https://stackoverflow.com/questions/19417608/motionevent-action-cancel-is-not-triggered-during-ontouch)
	 *
	 * This is used to workaround that by manually checking the user has left the view bounds
	 * */
	private var isCurrentActionCancelled = false

	/**
	 * The android event framework gives action move and up events immediately after finishing
	 * a scale (2-finger) gesture. This causes a "jump" in panning immediately at the end of a scale
	 * gesture, which is bad.
	 *
	 * This flag is turned on for a short duration immediately after the scale ends, and for as
	 * long as it's on, the motion events will get swallowed
	 **/
	private var scaleGestureJustFinished = false

	private val isScaleBounded
		get() = minScale != 0F

	private val isCurrentTouchPointInsideViewBounds
		get() = sourceRect.contains(curTouchPoint.x, curTouchPoint.y)

	/**
	 * Matrix that is provided to external callers
	 *
	 * When this property is accessed, it copies the values from the internal transform matrix
	 * because those values should not be modified from external classes
	 * */
	val matrix = Matrix()
		get() {
			field.set(transformMatrix)
			return field
		}

	init {
		view.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
			val rect = Rect()
			view.getDrawingRect(rect)
			sourceRect.set(rect)
		}
		view.setOnTouchListener { _, event ->

			if (!scaleGestureJustFinished) {
				scaleGestureDetector.onTouchEvent(event)
				event.takeIf { it.pointerCount == 1 }
						?.let { handleSingleFingerTouch(it) }
				event.takeIf { it.pointerCount != 1 }
						?.let { _ ->
							currentMovable?.let { onMoveCancel(it) }
							// Cancel moving if something get selected in multi touch gesture
							currentMovable = null
						}
				forwardTouchEvents.onTouch(view, event)
			}
			return@setOnTouchListener true
		}

		if (minScale < 0) throw IllegalArgumentException("Min Scale [$minScale] must be >= 0")
		if (maxScale < 0) throw IllegalArgumentException("Max Scale [$maxScale] must be >= 0")
		if (maxScale > 0 && minScale == 0F) throw IllegalArgumentException(
				"Must set Min Scale to a value > 0 and < max scale if max scale is set"
		)
		if (minScale > maxScale) throw IllegalArgumentException(
				"Min Scale [$minScale] must be <= Max Scale [$maxScale]"
		)
	}

	private fun handleSingleFingerTouch(event: MotionEvent) {
		curTouchPoint.set(event.x, event.y)

		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> onSinglePointerDown()
			MotionEvent.ACTION_CANCEL -> onSinglePointerCancel()
			MotionEvent.ACTION_UP -> onSinglePointerUp()
			MotionEvent.ACTION_MOVE -> onSinglePointerMove()
		}

		view.invalidate()
	}

	private fun onSinglePointerDown() {
		if (isCurrentTouchPointInsideViewBounds) {
			isCurrentActionCancelled = false
		} else {
			return
		}

		mapTransformationsOnSource()
		mapPointFromTransformedToSource(curTouchPoint.x, curTouchPoint.y, sourcePoint)
		currentMovable = findCurrentMovable(sourcePoint)
		prevTouchPoint.set(curTouchPoint)
	}

	private fun onSinglePointerCancel() {
		isCurrentActionCancelled = true
		currentMovable?.let {
			onMoveCancel(it)
			currentMovable = null
		}
	}

	private fun onSinglePointerUp() {
		Timber.tag(LOG_TAG)
				.d("Up")
		if (isCurrentActionCancelled) return
		currentMovable?.let {
			onMoveFinished(it)
			currentMovable = null
		}
	}

	private fun onSinglePointerMove() {
		// Workaround for the fact that the framework no longer triggers ACTION_CANCEL whenever the pointer leaves the view
		if (isCurrentActionCancelled) {
			return
		} else {
			if (isCurrentTouchPointInsideViewBounds.not()) {
				onSinglePointerCancel()
				return
			}
		}

		val deltaX = (curTouchPoint.x - prevTouchPoint.x) / scaleFactor
		val deltaY = (curTouchPoint.y - prevTouchPoint.y) / scaleFactor

		currentMovable?.moveBy(deltaX, deltaY)
		if (currentMovable == null) {
			transformMatrix.preTranslate(deltaX, deltaY)
		}
		prevTouchPoint.set(curTouchPoint)
	}

	private fun mapPointFromTransformedToSource(
		pX: Float,
		pY: Float,
		holder: PointF
	) {
		holder.x = sourceRect.left + sourceRect.width() * ((pX - mappedRect.left) / mappedRect.width())
		holder.y = sourceRect.top + sourceRect.height() * ((pY - mappedRect.top) / mappedRect.height())
	}

	private fun mapTransformationsOnSource() {
		transformMatrix.mapRect(mappedRect, sourceRect)
	}

	private fun clampedScale(scale: Float) {
		if (scale == 1f) return

		// This works because we scale both X and Y uniformly
		val matrixScaleX = Matrix.MSCALE_X

		if (!isScaleBounded) {
			transformMatrix.apply {
				preScale(scale, scale, scaleFocus.x, scaleFocus.y)
				getValues(tempMatrixValues)
				scaleFactor = tempMatrixValues[matrixScaleX]
			}
		} else {
			transformMatrix.getValues(tempMatrixValues)
			val prevScale = tempMatrixValues[matrixScaleX]

			tempMatrix.apply {
				set(transformMatrix)
				preScale(scale, scale, scaleFocus.x, scaleFocus.y)
				getValues(tempMatrixValues)
			}

			val finalScale = tempMatrixValues[matrixScaleX]
			val actualScaleToSet = when {
				finalScale > maxScale -> 1f + maxScale - prevScale
				finalScale < minScale -> 1f + prevScale - minScale
				else -> scale
			}

			if ((finalScale - maxScale).absoluteValue <= 0.1f || (finalScale - minScale).absoluteValue <= 0.1f) return

			transformMatrix.apply {
				preScale(actualScaleToSet, actualScaleToSet, scaleFocus.x, scaleFocus.y)
				getValues(tempMatrixValues)
				scaleFactor = tempMatrixValues[matrixScaleX]
			}
		}
	}

	override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
		val scaleFocusX = detector.focusX
		val scaleFocusY = detector.focusY

		mapTransformationsOnSource()
		mapPointFromTransformedToSource(scaleFocusX, scaleFocusY, scaleFocus)

		return true
	}

	override fun onScaleEnd(detector: ScaleGestureDetector) {
		scaleGestureJustFinished = true
		view.postDelayed({ scaleGestureJustFinished = false }, 100L)
	}

	override fun onScale(detector: ScaleGestureDetector): Boolean {
		clampedScale(detector.scaleFactor)
		view.invalidate()
		return true
	}

	fun reset() {
		transformMatrix.reset()
		scaleFactor = 1F
		view.invalidate()
	}

	fun apply(canvas: Canvas) {
		canvas.concat(transformMatrix)
	}

	fun convertViewTouchPointToActual(
		pX: Float,
		pY: Float
	): PointF {
		mapTransformationsOnSource()
		mapPointFromTransformedToSource(pX, pY, sourcePoint)
		return sourcePoint
	}

}