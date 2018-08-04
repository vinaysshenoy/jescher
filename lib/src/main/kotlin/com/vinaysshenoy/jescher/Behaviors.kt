package com.vinaysshenoy.jescher

interface Movable {

	fun moveBy(deltaX: Float, deltaY: Float)
}

interface Scalable {

	fun scaleBy(scaleAmount: Float)
}