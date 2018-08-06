package com.vinaysshenoy.jescher

internal fun Float.clamp(
	min: Float,
	max: Float
) = when {
	this < min -> min
	this > max -> max
	else -> this
}