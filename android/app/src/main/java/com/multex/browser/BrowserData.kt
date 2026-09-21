package com.multex.browser

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

/* ---------- Enums shared with the web build (lib/browser-data.ts) ---------- */

enum class Lang(val id: String, val label: String) {
    EN("en", "English"),
    ID("id", "Indonesia");

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: EN
    }
}

/** Pick the string for the active language. Keeps call sites short: tx(lang, "Hide", "Sembunyikan"). */
fun tx(lang: Lang, en: String, id: String): String = if (lang == Lang.ID) id else en

enum class ThemeId(val id: String, val label: String) {
    MIDNIGHT("midnight", "Midnight"),
    SAKURA("sakura", "Sakura");

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: MIDNIGHT
    }
}

enum class CompanionSize(val id: String, val label: String, val height: Dp) {
    SM("sm", "Small", 104.dp),
    MD("md", "Medium", 136.dp),
    LG("lg", "Large", 172.dp);

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: MD
    }
}

enum class PetId(val id: String, val label: String, val res: Int?) {
    NONE("none", "None", null),
    BUNNY("bunny", "Bunny", R.drawable.bunny),
    RABBIT("rabbit", "Rabbit", R.drawable.animal_rabbit),
    CAT("cat", "Cat", R.drawable.animal_cat),
    FOX("fox", "Fox", R.drawable.animal_fox),
    BEAR("bear", "Bear", R.drawable.animal_bear),
    PANDA("panda", "Panda", R.drawable.animal_panda),
    FROG("frog", "Frog", R.drawable.animal_frog);

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: NONE
    }
}

data class SearchEngine(val id: String, val label: String, val short: String, val host: String, val queryUrl: String)

val ENGINES = listOf(
    SearchEngine("google", "Google", "G", "google.com", "https://www.google.com/search?q="),
    SearchEngine("duckduckgo", "DuckDuckGo", "DDG", "duckduckgo.com", "https://duckduckgo.com/?q="),
    SearchEngine("brave", "Brave Search", "B", "search.brave.com", "https://search.brave.com/search?q="),
)

fun engineFrom(id: String?) = ENGINES.firstOrNull { it.id == id } ?: ENGINES.first()

enum class Overlay { NONE, TABS, SESSIONS, SETTINGS, MENU, SHORTCUT, SESSION_NAME }

/* ---------- Settings ---------- */

/** The remote AI service used only for Denia's free-form chat. */
enum class AiProvider(val id: String) {
    GEMINI("gemini"),
    OPENAI_COMPATIBLE("openai_compatible");

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: GEMINI
    }
}

data class Settings(
    val companionEnabled: Boolean = true,
    val companionSize: CompanionSize = CompanionSize.MD,
    val pet: PetId = PetId.NONE,
    val chatty: Boolean = true,
    val theme: ThemeId = ThemeId.MIDNIGHT,
    val searchEngine: SearchEngine = ENGINES.first(),
    val language: Lang = Lang.EN,
    val blockTrackers: Boolean = true,
    val httpsOnly: Boolean = true,
    /** Ask sites for their desktop version. */
    val desktopSite: Boolean = false,
    /** Google AI Studio key. When set, Denia answers free-form questions through Gemini. */
    val geminiKey: String = "",
    /** Which remote AI API Denia uses when a local command does not match. */
    val aiProvider: AiProvider = AiProvider.GEMINI,
    /** Root URL of an OpenAI-compatible API, normally ending in /v1. */
    val openAiBaseUrl: String = "",
    /** Optional for local OpenAI-compatible servers; cloud providers normally require it. */
    val openAiApiKey: String = "",
    /** Model identifier accepted by the configured OpenAI-compatible API. */
    val openAiModel: String = "",
)

/** Persists settings (including AI credentials) in app-private SharedPreferences. */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("multex.settings", Context.MODE_PRIVATE)

    fun load(): Settings = Settings(
        companionEnabled = prefs.getBoolean("companionEnabled", true),
        companionSize = CompanionSize.from(prefs.getString("companionSize", null)),
        pet = PetId.from(prefs.getString("pet", null)),
        chatty = prefs.getBoolean("chatty", true),
        theme = ThemeId.from(prefs.getString("theme", null)),
        searchEngine = engineFrom(prefs.getString("searchEngine", null)),
        language = Lang.from(prefs.getString("language", null)),
        blockTrackers = prefs.getBoolean("blockTrackers", true),
        httpsOnly = prefs.getBoolean("httpsOnly", true),
        desktopSite = prefs.getBoolean("desktopSite", false),
        geminiKey = prefs.getString("geminiKey", "") ?: "",
        aiProvider = AiProvider.from(prefs.getString("aiProvider", null)),
        openAiBaseUrl = prefs.getString("openAiBaseUrl", "") ?: "",
        openAiApiKey = prefs.getString("openAiApiKey", "") ?: "",
        openAiModel = prefs.getString("openAiModel", "") ?: "",
    )

    fun save(s: Settings) {
        prefs.edit()
            .putBoolean("companionEnabled", s.companionEnabled)
            .putString("companionSize", s.companionSize.id)
            .putString("pet", s.pet.id)
            .putBoolean("chatty", s.chatty)
            .putString("theme", s.theme.id)
            .putString("searchEngine", s.searchEngine.id)
            .putString("language", s.language.id)
            .putBoolean("blockTrackers", s.blockTrackers)
            .putBoolean("httpsOnly", s.httpsOnly)
            .putBoolean("desktopSite", s.desktopSite)
            .putString("geminiKey", s.geminiKey)
            .putString("aiProvider", s.aiProvider.id)
            .putString("openAiBaseUrl", s.openAiBaseUrl)
            .putString("openAiApiKey", s.openAiApiKey)
            .putString("openAiModel", s.openAiModel)
            .apply()
    }
}

/* ---------- Tabs & sessions ---------- */

enum class TabKind { HOME, PAGE }

data class Tab(
    val id: String,
    val kind: TabKind,
    val title: String,
    val host: String,
    val url: String,
    val tint: Color,
    val progress: Int = 100,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val blocked: Int = 0,
)

/**
 * A session is a named set of tabs. [profile] is the cookie jar (WebView profile) its tabs run in:
 * "Save current tabs" keeps the same jar, while "New session" starts a clean one.
 */
data class Session(
    val id: String,
    val name: String,
    val savedAt: Long,
    val tabs: List<Tab>,
    val profile: String = id,
)

/** WebView profile names only allow letters and digits, so everything else is stripped. */
fun profileNameFor(profile: String): String = "s" + profile.filter { it.isLetterOrDigit() }

fun relativeTime(lang: Lang, savedAt: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - savedAt) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1 -> tx(lang, "Just now", "Baru saja")
        minutes < 60 -> tx(lang, "$minutes min ago", "$minutes mnt lalu")
        minutes < 60 * 24 -> tx(lang, "${minutes / 60} h ago", "${minutes / 60} jam lalu")
        minutes < 60 * 48 -> tx(lang, "Yesterday", "Kemarin")
        else -> tx(lang, "${minutes / (60 * 24)} days ago", "${minutes / (60 * 24)} hari lalu")
    }
}

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

/** Colors offered in the shortcut editor. */
val SHORTCUT_TINTS = listOf(
    Color(0xFFFF6B8A), Color(0xFFF79AC8), Color(0xFFFF9A6B), Color(0xFFFFC178),
    Color(0xFF6CF5A8), Color(0xFF7FE0C9), Color(0xFF9FD8FF), Color(0xFF5FA8FF),
    Color(0xFFA3AEFF), Color(0xFFB9A9FF), Color(0xFFC9C4FF), Color(0xFFECE9FF),
)

private val SITE_INFO = SHORTCUTS.associateBy { it.host }

private val TINTS = listOf(
    Color(0xFFF79AC8), Color(0xFFB9A9FF), Color(0xFF86A6FF), Color(0xFF7FE0C9), Color(0xFFFFC178),
)

val HOME_TINT = Color(0xFFB9A9FF)

private var seq = 0L
fun uid(): String = "t-${System.currentTimeMillis().toString(36)}-${(seq++).toString(36)}"

fun normalizeHost(input: String): String =
    input.trim()
        .replace(Regex("^https?://", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^www\\.", RegexOption.IGNORE_CASE), "")
        .split('/', '?', '#')[0]
        .lowercase()

fun hostOf(url: String): String =
    runCatching { URI(url).host?.removePrefix("www.") }.getOrNull() ?: normalizeHost(url)

fun tintFor(host: String): Color {
    SITE_INFO[host]?.let { return it.tint }
    val hash = host.sumOf { it.code }
    return TINTS[hash % TINTS.size]
}

fun titleFor(host: String): String =
    SITE_INFO[host]?.title ?: host.substringBefore('.').replaceFirstChar { it.titlecase(Locale.ROOT) }

fun makeTab(url: String, title: String? = null): Tab {
    val host = hostOf(url)
    return Tab(
        id = uid(),
        kind = TabKind.PAGE,
        title = title ?: titleFor(host),
        host = host,
        url = url,
        tint = tintFor(host),
        progress = 0,
    )
}

fun makeHomeTab(): Tab = Tab(uid(), TabKind.HOME, "New tab", "", "", HOME_TINT)

/** Turns typed text into a URL: full URLs pass through, host-like input gets https://, everything else is a search. */
fun resolveInput(input: String, engine: SearchEngine, httpsOnly: Boolean = true): String {
    val q = input.trim()
    if (q.isEmpty()) return ""
    if (q.startsWith("http://") && httpsOnly) return "https://" + q.removePrefix("http://")
    if (q.startsWith("http://") || q.startsWith("https://")) return q
    val looksLikeHost = !q.contains(' ') && Regex("^[\\w-]+(\\.[\\w-]+)+").containsMatchIn(q)
    return if (looksLikeHost) "https://$q" else engine.queryUrl + URLEncoder.encode(q, "UTF-8")
}

/** Hosts blocked when "Block trackers" is on. The address pill shield shows how many were stopped. */
val TRACKER_HOSTS = listOf(
    "google-analytics.com", "googletagmanager.com", "doubleclick.net", "googlesyndication.com",
    "googleadservices.com", "adservice.google.com", "facebook.net", "connect.facebook.net",
    "scorecardresearch.com", "quantserve.com", "hotjar.com", "mixpanel.com", "segment.io",
    "segment.com", "amplitude.com", "criteo.com", "criteo.net", "taboola.com", "outbrain.com",
    "adnxs.com", "rubiconproject.com", "pubmatic.com", "openx.net", "moatads.com", "chartbeat.com",
    "newrelic.com", "nr-data.net", "bugsnag.com", "sentry.io", "branch.io", "adjust.com", "appsflyer.com",
)

fun isTracker(host: String?): Boolean {
    if (host.isNullOrEmpty()) return false
    val h = host.lowercase()
    return TRACKER_HOSTS.any { h == it || h.endsWith(".$it") }
}
