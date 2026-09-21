package com.multex.browser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private lateinit var model: BrowserModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = BrowserModel(this)
        // A link opened from another app (this app is registered for http/https).
        intent?.dataString?.let { model.openExternal(it) }
        setContent { MultexRoot(model) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let { model.openExternal(it) }
    }

    override fun onPause() {
        super.onPause()
        model.onPause()
    }

    override fun onResume() {
        super.onResume()
        model.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        model.destroy()
    }
}
