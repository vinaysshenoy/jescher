package com.vinaysshenoy.jescher.sample

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import com.vinaysshenoy.jescher.Jescher
import java.util.Random

private const val MIN_DIMENSION_FACTOR = 0.15F
private const val MAX_DIMENSION_FACTOR = 0.4F

private val colorSet = listOf(
		Color.BLACK,
		Color.BLUE,
		Color.DKGRAY,
		Color.GREEN,
		Color.MAGENTA,
		Color.RED
)

class CustomView @JvmOverloads constructor(
	context: Context,
	attributeSet: AttributeSet? = null
) : View(context, attributeSet) {

	private val jescher = Jescher(this)
	private val drawRect = Rect()
	private val dimensionFactorRange = MAX_DIMENSION_FACTOR - MIN_DIMENSION_FACTOR
	private val dimensionFactorRandomizer = Random()

	private val randomDimensionFactor
		get() = MIN_DIMENSION_FACTOR + (dimensionFactorRange * dimensionFactorRandomizer.nextFloat())

	private val randomColor
		get() = colorSet.shuffled().first()

	var shapes = emptyList<Shape>()
		private set(value) {
			field = value
			invalidate()
		}

	init {
		if (isInEditMode) {
			shapes = listOf(Rectangle(Color.DKGRAY, 100F, 100F, 50F, 50F))
		}
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		jescher.apply(canvas)
		shapes.forEach { it.draw(canvas) }
	}

	fun addRect() {
		getDrawingRect(drawRect)
		val centerX = drawRect.centerX()
				.toFloat()
		val centerY = drawRect.centerY()
				.toFloat()

		val width = drawRect.width() * randomDimensionFactor
		val height = drawRect.height() * randomDimensionFactor

		shapes += Rectangle(
				color = randomColor,
				centerX = centerX,
				centerY = centerY,
				width = width,
				height = height
		)
	}
}
