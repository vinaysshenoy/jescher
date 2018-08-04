package com.vinaysshenoy.jescher.sample

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import com.vinaysshenoy.jescher.Jescher

class CustomView @JvmOverloads constructor(
	context: Context,
	attributeSet: AttributeSet? = null
) : View(context, attributeSet) {

	private val jescher = Jescher(this)

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		jescher.apply(canvas)
	}
}
