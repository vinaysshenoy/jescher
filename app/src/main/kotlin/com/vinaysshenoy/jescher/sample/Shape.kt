package com.vinaysshenoy.jescher.sample

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.support.annotation.ColorInt
import com.vinaysshenoy.jescher.Movable
import com.vinaysshenoy.jescher.Scalable

interface Shape : Movable, Scalable {

	fun draw(canvas: Canvas)

	fun contains(
		pX: Float,
		pY: Float
	): Boolean
}

class Rectangle(
	@ColorInt val color: Int,
	val width: Float,
	val height: Float
) : Shape {

	private val bounds = RectF(0F, 0F, width, height)
	private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

	init {
		paint.color = color
		paint.style = Paint.Style.FILL
	}

	override fun draw(canvas: Canvas) {
		canvas.drawRect(bounds, paint)
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