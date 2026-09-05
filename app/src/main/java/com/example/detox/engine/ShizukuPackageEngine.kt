package com.example.detox

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val textView = TextView(this).apply {
            text = "Aegis Detox Initialized\nShizuku Engine Ready"
            textSize = 20f
            setPadding(32, 32, 32, 32)
        }
        
        setContentView(textView)
    }
}