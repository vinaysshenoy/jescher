package com.vinaysshenoy.jescher.sample

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.btn_addRect
import kotlinx.android.synthetic.main.activity_main.cv_shapes

class MainActivity : AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		btn_addRect.setOnClickListener { cv_shapes.addRect() }
	}
}
