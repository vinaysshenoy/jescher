package com.vinaysshenoy.jescher

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.PointF
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

	private val isScaleBounded
		get() = minScale != 0F

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
			sourceRect.set(
					left.toFloat(),
					top.toFloat(),
					right.toFloat(),
					bottom.toFloat()
			)
		}
		view.setOnTouchListener { _, event ->

			scaleGestureDetector.onTouchEvent(event)
			event.takeIf { it.pointerCount == 1 }
					?.let { handleSingleFingerTouch(it) }
			event.takeIf { it.pointerCount != 1 }
					?.let {
						// Cancel moving if something get selected in multi touch gesture
						currentMovable = null
					}
			forwardTouchEvents.onTouch(view, event)
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
		mapTransformationsOnSource()
		mapPointFromTransformedToSource(curTouchPoint.x, curTouchPoint.y, sourcePoint)
		currentMovable = findCurrentMovable(sourcePoint)
		prevTouchPoint.set(curTouchPoint)
	}

	private fun onSinglePointerCancel() {
		currentMovable?.let {
			onMoveCancel(it)
			currentMovable = null
		}
	}

	private fun onSinglePointerUp() {
		currentMovable?.let {
			onMoveFinished(it)
			currentMovable = null
		}
	}

	private fun onSinglePointerMove() {
		val deltaX = curTouchPoint.x - prevTouchPoint.x
		val deltaY = curTouchPoint.y - prevTouchPoint.y

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
		Timber.tag(LOG_TAG)
				.d("Scale: $scale")

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
			Timber.tag(LOG_TAG)
					.d("Prev Scale: $prevScale")

			tempMatrix.apply {
				set(transformMatrix)
				preScale(scale, scale, scaleFocus.x, scaleFocus.y)
				getValues(tempMatrixValues)
			}

			val finalScale = tempMatrixValues[matrixScaleX]
			Timber.tag(LOG_TAG)
					.d("Final Scale: $finalScale")
			val actualScaleToSet = when {
				finalScale > maxScale -> 1f + maxScale - prevScale
				finalScale < minScale -> 1f + prevScale - minScale
				else -> scale
			}

			if ((finalScale - maxScale).absoluteValue <= 0.1f || (finalScale - minScale).absoluteValue <= 0.1f) return

			Timber.tag(LOG_TAG)
					.d("Actual Scale To Set: $actualScaleToSet")
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

//		scaleFocus.x = scaleFocusX
//		scaleFocus.y = scaleFocusY

		return true
	}

	override fun onScaleEnd(detector: ScaleGestureDetector) {
		// We don't do anything here (yet!)
	}

	override fun onScale(detector: ScaleGestureDetector): Boolean {
		clampedScale(detector.scaleFactor)
		Timber.tag(LOG_TAG)
				.d("Current Scale: $scaleFactor")
		view.invalidate()
		return true
	}

	fun reset() {
		transformMatrix.reset()
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