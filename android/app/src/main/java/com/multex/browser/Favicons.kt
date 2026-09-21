package com.multex.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections

/**
 * Site icons for the shortcut tiles. Fetched once from Google's favicon service, then kept in
 * memory and in the app cache folder. If a site has no icon (or there is no network) the tile
 * simply shows its letter.
 */
object Favicons {
    private val memory = LruCache<String, Bitmap>(80)
    private val misses: MutableSet<String> = Collections.synchronizedSet(HashSet())

    suspend fun load(context: Context, host: String): Bitmap? = withContext(Dispatchers.IO) {
        val key = host.lowercase().filter { it.isLetterOrDigit() || it == '.' || it == '-' }
        if (key.isEmpty() || !key.contains('.')) return@withContext null
        memory.get(key)?.let { return@withContext it }

        val file = File(context.cacheDir, "fav_$key.png")
        if (file.exists()) {
            BitmapFactory.decodeFile(file.path)?.let {
                memory.put(key, it)
                return@withContext it
            }
        }
        if (key in misses) return@withContext null

        try {
            val conn = URL("https://www.google.com/s2/favicons?domain=$key&sz=128").openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                file.writeBytes(bytes)
                memory.put(key, bitmap)
            } else {
                misses.add(key)
            }
            bitmap
        } catch (e: Exception) {
            misses.add(key)
            null
        }
    }
}

@Composable
fun rememberFavicon(host: String): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val state = produceState<ImageBitmap?>(initialValue = null, host) {
        value = Favicons.load(context, host)?.asImageBitmap()
    }
    return state.value
}
