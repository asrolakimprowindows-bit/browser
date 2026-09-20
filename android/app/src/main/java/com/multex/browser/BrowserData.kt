package com.multex.browser

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.net.URI
import java.net.URLEncoder

data class Shortcut(val title: String, val host: String, val tint: Color)

val SHORTCUTS = listOf(
    Shortcut("YouTube", "youtube.com", Color(0xFFFF6B8A)),
    Shortcut("Pixiv", "pixiv.net", Color(0xFF5FA8FF)),
    Shortcut("GitHub", "github.com", Color(0xFFC9C4FF)),
    Shortcut("Reddit", "reddit.com", Color(0xFFFF9A6B)),
    Shortcut("X", "x.com", Color(0xFF9FD8FF)),
    Shortcut("Wikipedia", "wikipedia.org", Color(0xFFECE9FF)),
    Shortcut("Spotify", "spotify.com", Color(0xFF6CF5A8)),
    Shortcut("Discord", "discord.com", Color(0xFFA3AEFF)),
)

data class Pet(val id: String, val label: String, val res: Int?)

val PETS = listOf(
    Pet("none", "None", null),
    Pet("bunny", "Bunny", R.drawable.bunny),
    Pet("rabbit", "Rabbit", R.drawable.animal_rabbit),
    Pet("cat", "Cat", R.drawable.animal_cat),
    Pet("fox", "Fox", R.drawable.animal_fox),
    Pet("bear", "Bear", R.drawable.animal_bear),
    Pet("panda", "Panda", R.drawable.animal_panda),
    Pet("frog", "Frog", R.drawable.animal_frog),
)

data class CompanionSize(val id: String, val label: String, val height: Dp)

val SIZES = listOf(
    CompanionSize("sm", "Small", 104.dp),
    CompanionSize("md", "Medium", 136.dp),
    CompanionSize("lg", "Large", 172.dp),
)

data class SearchEngine(val id: String, val label: String, val queryUrl: String)

val ENGINES = listOf(
    SearchEngine("google", "Google", "https://www.google.com/search?q="),
    SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q="),
    SearchEngine("brave", "Brave Search", "https://search.brave.com/search?q="),
)

fun resolveInput(input: String, engine: SearchEngine): String {
    val q = input.trim()
    if (q.isEmpty()) return ""
    if (q.startsWith("http://") || q.startsWith("https://")) return q
    val looksLikeHost = !q.contains(' ') && q.contains('.')
    return if (looksLikeHost) "https://$q" else engine.queryUrl + URLEncoder.encode(q, "UTF-8")
}

fun hostOf(url: String): String =
    runCatching { URI(url).host?.removePrefix("www.") }.getOrNull() ?: url
