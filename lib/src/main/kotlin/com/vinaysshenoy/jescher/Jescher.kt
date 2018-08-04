package com.vinaysshenoy.jescher

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.PointF
import android.view.View
import android.view.View.OnTouchListener

class Jescher @JvmOverloads constructor(
	private val view: View,
	private val currentMovable: (PointF) -> Movable? = { _ -> null },
	private val currentScalable: (PointF) -> Scalable? = { _ -> null },
	// TODO: Register actions for onScale and onMove
	private val interceptTouch: View.OnTouchListener = OnTouchListener { _, _ -> false }
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

	fun apply(canvas: Canvas) {
		canvas.concat(transformMatrix)
	}

}