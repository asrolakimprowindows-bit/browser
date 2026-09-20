package com.multex.browser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private var incomingUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingUrl = intent?.dataString
        setContent {
            MultexTheme {
                BrowserScreen(
                    externalUrl = incomingUrl,
                    onExternalUrlConsumed = { incomingUrl = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incomingUrl = intent.dataString
    }
}
