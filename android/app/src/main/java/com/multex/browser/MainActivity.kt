package com.multex.browser

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private lateinit var model: BrowserModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // On API < 30 the androidx WindowInsetsControllerCompat.hide() calls below cannot work
        // by themselves, so mirror them with the legacy sticky-immersive flag. From API 30 up,
        // WindowInsetsControllerCompat.hide(systemBars()) (BrowserScreen) is the real mechanism:
        // it makes BOTH system bars truly disappear (not just transparent) until a swipe.
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
        model = BrowserModel(this)
        // Needed on API 33+ for download-status notifications (ignored silently when denied).
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7)
        }
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
