package com.vinaysshenoy.jescher.sample

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.support.annotation.ColorInt
import com.vinaysshenoy.jescher.Movable
import com.vinaysshenoy.jescher.Scalable

interface Shape : Movable, Scalable {

	fun draw(canvas: Canvas)

	fun selected(selected: Boolean)

	fun contains(
		pX: Float,
		pY: Float
	): Boolean
}

class Rectangle(
	@ColorInt val color: Int,
	centerX: Float,
	centerY: Float,
	width: Float,
	height: Float
) : Shape {

	private val bounds: RectF
	private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private var selected: Boolean = false

	init {
		paint.color = color
		selectedPaint.color = Color.argb(102, 0, 0, 0)
		paint.style = Paint.Style.FILL

		bounds = RectF()
		bounds.left = centerX - width / 2
		bounds.right = centerX + width / 2
		bounds.top = centerY - height / 2
		bounds.bottom = centerY + height / 2
	}

	override fun draw(canvas: Canvas) {
		canvas.drawRect(bounds, paint)
		if (selected) {
			canvas.drawRect(bounds, selectedPaint)
		}
	}

	override fun selected(selected: Boolean) {
		this.selected = selected
	}

	override fun moveBy(
		deltaX: Float,
		deltaY: Float
	) {
		bounds.offset(deltaX, deltaY)
	}

	override fun scaleBy(scaleAmount: Float) {
		val insetX = -(bounds.width() * scaleAmount) / 2
		val insetY = -(bounds.height() * scaleAmount) / 2

		bounds.inset(insetX, insetY)
	}

	override fun contains(
		pX: Float,
		pY: Float
	) = bounds.contains(pX, pY)
}