package com.vinaysshenoy.jescher

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener

class Jescher @JvmOverloads constructor(
	private val view: View,
	private val findCurrentMovable: (PointF) -> Movable? = { _ -> null },
	private val findCurrentScalable: (PointF) -> Scalable? = { _ -> null },
	private val onMoveCancel: (Movable) -> Unit = {},
	private val onMoveFinished: (Movable) -> Unit = {},
		// TODO: Register actions for onScale,onMove,on
	private val forwardTouchEvents: View.OnTouchListener = OnTouchListener { _, _ -> false }
) {

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

	private val curTouchPoint = PointF()
	private val prevTouchPoint = PointF()

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
		view.setOnTouchListener { _, event -> handleSingleFingerTouch(event) }
	}

	private fun handleSingleFingerTouch(event: MotionEvent): Boolean {
		curTouchPoint.set(event.x, event.y)

		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> onSinglePointerDown()
			MotionEvent.ACTION_CANCEL -> onSinglePointerCancel()
			MotionEvent.ACTION_UP -> onSinglePointerUp()
			MotionEvent.ACTION_MOVE -> onSinglePointerMove()
		}

		forwardTouchEvents.onTouch(view, event)
		view.invalidate()
		return true
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