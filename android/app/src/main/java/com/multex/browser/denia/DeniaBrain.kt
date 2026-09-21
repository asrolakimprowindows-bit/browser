package com.multex.browser.denia

import com.multex.browser.Lang
import com.multex.browser.SearchEngine
import com.multex.browser.tx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/*
 * Denia's offline brain: bilingual strings, local command matching, and the "which site is X?"
 * lookup. Everything here runs on-device with no network or API key. Free-form questions go to
 * OpenRouter (see OpenRouterClient) when the user has added a key in Settings.
 *
 * NEW: PendingActionType/ConversationContext + confirmation detection so that when Denia asks
 * "want me to search / open in new tab?", the user's short "yes / buka / iya" answer executes
 * the stored pending action instead of being sent as a raw search query.
 */

/** Same action list as lib/denia-commands.ts. RELOAD and GO_BACK are Android-only, local commands. */
enum class DeniaAction(val wire: String) {
    NONE("none"),
    HIDE_DENIA("hide_denia"),
    SHOW_DENIA("show_denia"),
    NEW_TAB("new_tab"),
    OPEN_TABS("open_tabs"),
    OPEN_SETTINGS("open_settings"),
    OPEN_SESSIONS("open_sessions"),
    SAVE_SESSION("save_session"),
    DIRECT_MODE("direct_mode"),
    THEME_SAKURA("theme_sakura"),
    THEME_MIDNIGHT("theme_midnight"),
    GO_HOME("go_home"),
    OPEN_URL("open_url"),
    RELOAD("reload"),
    GO_BACK("go_back");

    companion object {
        /** Actions the AI provider is allowed to pick (identical to DENIA_ACTIONS on the web). */
        val FOR_AI = listOf(
            NONE, HIDE_DENIA, SHOW_DENIA, NEW_TAB, OPEN_TABS, OPEN_SETTINGS, OPEN_SESSIONS,
            SAVE_SESSION, DIRECT_MODE, THEME_SAKURA, THEME_MIDNIGHT, GO_HOME, OPEN_URL,
        )

        fun fromWire(id: String?): DeniaAction = entries.firstOrNull { it.wire == id } ?: NONE
    }
}

/** The kind of pending action Denia is holding while she waits for the user's yes/no. */
enum class PendingActionType {
    /** Denia already knows the URL (site was in her notes). Just open it. */
    OPEN_URL,
    /** Denia does NOT know the URL yet: on confirm, search the web for the official site,
     *  pick the top real result, and open it in a new tab. */
    SEARCH_AND_OPEN_OFFICIAL,
}

/** A URL Denia wants to open, but only after the user confirms with Yes. */
data class PendingOpen(
    /** For OPEN_URL: the concrete URL to open. For SEARCH_AND_OPEN_OFFICIAL: fallback URL (search page). */
    val url: String,
    val label: String,
    val type: PendingActionType = PendingActionType.OPEN_URL,
    /** The plain-language target ("NVIDIA", "Apple official website"). Used for the web search. */
    val target: String = label,
)

data class DeniaReply(
    val text: String,
    val mood: Mood = Mood.HAPPY,
    val action: DeniaAction = DeniaAction.NONE,
    val pending: PendingOpen? = null,
)

private class Rule(pattern: String, val build: (Lang) -> DeniaReply) {
    val regex = Regex(pattern, RegexOption.IGNORE_CASE)
}

private val RULES = listOf(
    Rule("""\b(hide|sembunyi\w*|matiin|matikan|hilang\w*|umpetin|pergi)\b""") {
        DeniaReply(tx(it, "Okay~ I will hide for now. Call me when you need me!", "Oke~ Denia sembunyi dulu. Panggil kalau butuh ya!"), Mood.POUT, DeniaAction.HIDE_DENIA)
    },
    Rule("""\b(show|muncul\w*|nyalain|nyalakan|tampil\w*|balik|kembali|come back)\b""") {
        DeniaReply(tx(it, "Tadaa! Denia is back~", "Tadaa! Denia balik lagi~"), Mood.HAPPY, DeniaAction.SHOW_DENIA)
    },
    Rule("""\b(new tab|tab baru|buka tab|bikin tab)\b""") {
        DeniaReply(tx(it, "One fresh tab, coming right up!", "Satu tab baru, siap~"), Mood.HAPPY, DeniaAction.NEW_TAB)
    },
    Rule("""\b(setting\w*|pengaturan|setelan)\b""") {
        DeniaReply(tx(it, "Opening settings~ Dress me up nicely, okay?", "Buka pengaturan~ Dandanin aku yang cantik ya?"), Mood.HAPPY, DeniaAction.OPEN_SETTINGS)
    },
    Rule("""\b(save|simpan)\b.*\b(session|sesi|tabs?)\b|\b(session|sesi)\b.*\b(save|simpan)\b""") {
        DeniaReply(tx(it, "Saving your tabs as a session!", "Simpan tab-mu jadi sesi!"), Mood.HAPPY, DeniaAction.SAVE_SESSION)
    },
    Rule("""\b(sessions?|sesi)\b""") {
        DeniaReply(tx(it, "Here are your sessions~", "Ini sesi-sesimu~"), Mood.NEUTRAL, DeniaAction.OPEN_SESSIONS)
    },
    Rule("""\b(tabs|semua tab|list tab|daftar tab)\b""") {
        DeniaReply(tx(it, "Here are all your tabs!", "Ini semua tab-mu!"), Mood.NEUTRAL, DeniaAction.OPEN_TABS)
    },
    Rule("""\b(direct|sini|kesini|ke sini|follow|ikut)\b""") {
        DeniaReply(tx(it, "Tap anywhere and I will run there!", "Ketuk di mana saja, aku lari ke sana!"), Mood.HAPPY, DeniaAction.DIRECT_MODE)
    },
    Rule("""\bsakura\b|\b(pink|light|terang)\b.*\b(theme|tema|mode)\b|\b(theme|tema|mode)\b.*\b(pink|light|terang)\b""") {
        DeniaReply(tx(it, "Sakura mode on~ So pretty!", "Mode sakura nyala~ Cantik banget!"), Mood.HAPPY, DeniaAction.THEME_SAKURA)
    },
    Rule("""\bmidnight\b|\b(dark|gelap|malam)\b.*\b(theme|tema|mode)\b|\b(theme|tema|mode)\b.*\b(dark|gelap|malam)\b""") {
        DeniaReply(tx(it, "Midnight mode. Cozy and dark~", "Mode midnight. Gelap dan nyaman~"), Mood.NEUTRAL, DeniaAction.THEME_MIDNIGHT)
    },
    Rule("""\b(reload|refresh|muat ulang|segarkan)\b""") {
        DeniaReply(tx(it, "Refreshing the page~", "Muat ulang halamannya~"), Mood.HAPPY, DeniaAction.RELOAD)
    },
    Rule("""\b(go back|back|mundur|balik lagi)\b""") {
        DeniaReply(tx(it, "Going back one step~", "Mundur satu langkah~"), Mood.NEUTRAL, DeniaAction.GO_BACK)
    },
    Rule("""\b(home|beranda|balik ke awal)\b""") {
        DeniaReply(tx(it, "Taking you home~", "Antar pulang ke beranda~"), Mood.HAPPY, DeniaAction.GO_HOME)
    },
)

/** Known official sites, keyed by the aliases people actually type. */
private val SITES: List<Pair<List<String>, Pair<String, String>>> = listOf(
    listOf("github", "git hub") to ("GitHub" to "https://github.com"),
    listOf("youtube", "yt") to ("YouTube" to "https://www.youtube.com"),
    listOf("google") to ("Google" to "https://www.google.com"),
    listOf("gmail") to ("Gmail" to "https://mail.google.com"),
    listOf("facebook", "fb") to ("Facebook" to "https://www.facebook.com"),
    listOf("instagram", "ig") to ("Instagram" to "https://www.instagram.com"),
    listOf("twitter", "x.com") to ("X (Twitter)" to "https://x.com"),
    listOf("tiktok") to ("TikTok" to "https://www.tiktok.com"),
    listOf("reddit") to ("Reddit" to "https://www.reddit.com"),
    listOf("wikipedia", "wiki") to ("Wikipedia" to "https://www.wikipedia.org"),
    listOf("pixiv") to ("Pixiv" to "https://www.pixiv.net"),
    listOf("discord") to ("Discord" to "https://discord.com"),
    listOf("spotify") to ("Spotify" to "https://open.spotify.com"),
    listOf("netflix") to ("Netflix" to "https://www.netflix.com"),
    listOf("amazon") to ("Amazon" to "https://www.amazon.com"),
    listOf("shopee") to ("Shopee" to "https://shopee.co.id"),
    listOf("tokopedia", "tokped") to ("Tokopedia" to "https://www.tokopedia.com"),
    listOf("lazada") to ("Lazada" to "https://www.lazada.co.id"),
    listOf("bukalapak") to ("Bukalapak" to "https://www.bukalapak.com"),
    listOf("whatsapp", "wa") to ("WhatsApp" to "https://web.whatsapp.com"),
    listOf("telegram") to ("Telegram" to "https://web.telegram.org"),
    listOf("twitch") to ("Twitch" to "https://www.twitch.tv"),
    listOf("steam") to ("Steam" to "https://store.steampowered.com"),
    listOf("epic games", "epic") to ("Epic Games" to "https://store.epicgames.com"),
    listOf("roblox") to ("Roblox" to "https://www.roblox.com"),
    listOf("minecraft") to ("Minecraft" to "https://www.minecraft.net"),
    listOf("stack overflow", "stackoverflow") to ("Stack Overflow" to "https://stackoverflow.com"),
    listOf("chatgpt", "openai") to ("ChatGPT" to "https://chatgpt.com"),
    listOf("gemini") to ("Gemini" to "https://gemini.google.com"),
    listOf("claude", "anthropic") to ("Claude" to "https://claude.ai"),
    listOf("vercel") to ("Vercel" to "https://vercel.com"),
    listOf("v0") to ("v0" to "https://v0.app"),
    listOf("nextjs", "next.js", "next js") to ("Next.js" to "https://nextjs.org"),
    listOf("react") to ("React" to "https://react.dev"),
    listOf("kotlin") to ("Kotlin" to "https://kotlinlang.org"),
    listOf("android developer", "android dev", "android studio") to ("Android Developers" to "https://developer.android.com"),
    listOf("apple") to ("Apple" to "https://www.apple.com"),
    listOf("microsoft") to ("Microsoft" to "https://www.microsoft.com"),
    listOf("linkedin") to ("LinkedIn" to "https://www.linkedin.com"),
    listOf("pinterest") to ("Pinterest" to "https://www.pinterest.com"),
    listOf("tumblr") to ("Tumblr" to "https://www.tumblr.com"),
    listOf("quora") to ("Quora" to "https://www.quora.com"),
    listOf("medium") to ("Medium" to "https://medium.com"),
    listOf("notion") to ("Notion" to "https://www.notion.so"),
    listOf("figma") to ("Figma" to "https://www.figma.com"),
    listOf("canva") to ("Canva" to "https://www.canva.com"),
    listOf("bing") to ("Bing" to "https://www.bing.com"),
    listOf("duckduckgo", "ddg") to ("DuckDuckGo" to "https://duckduckgo.com"),
    listOf("brave") to ("Brave" to "https://brave.com"),
    listOf("firefox", "mozilla") to ("Firefox" to "https://www.mozilla.org/firefox"),
    listOf("chrome") to ("Google Chrome" to "https://www.google.com/chrome"),
    listOf("bilibili") to ("Bilibili" to "https://www.bilibili.com"),
    listOf("crunchyroll") to ("Crunchyroll" to "https://www.crunchyroll.com"),
    listOf("myanimelist", "mal") to ("MyAnimeList" to "https://myanimelist.net"),
    listOf("anilist") to ("AniList" to "https://anilist.co"),
    listOf("mangadex") to ("MangaDex" to "https://mangadex.org"),
    listOf("deviantart") to ("DeviantArt" to "https://www.deviantart.com"),
    listOf("artstation") to ("ArtStation" to "https://www.artstation.com"),
    listOf("behance") to ("Behance" to "https://www.behance.net"),
    listOf("dribbble") to ("Dribbble" to "https://dribbble.com"),
    listOf("unsplash") to ("Unsplash" to "https://unsplash.com"),
    listOf("soundcloud") to ("SoundCloud" to "https://soundcloud.com"),
    listOf("kaskus") to ("Kaskus" to "https://www.kaskus.co.id"),
    listOf("detik") to ("Detik" to "https://www.detik.com"),
    listOf("kompas") to ("Kompas" to "https://www.kompas.com"),
    listOf("tribun") to ("Tribunnews" to "https://www.tribunnews.com"),
    listOf("cnn") to ("CNN" to "https://edition.cnn.com"),
    listOf("bbc") to ("BBC" to "https://www.bbc.com"),
    listOf("gojek") to ("Gojek" to "https://www.gojek.com"),
    listOf("grab") to ("Grab" to "https://www.grab.com"),
    listOf("traveloka") to ("Traveloka" to "https://www.traveloka.com"),
    listOf("tiket.com", "tiket") to ("tiket.com" to "https://www.tiket.com"),
    listOf("zoom") to ("Zoom" to "https://zoom.us"),
    listOf("dropbox") to ("Dropbox" to "https://www.dropbox.com"),
    listOf("google drive", "gdrive", "drive") to ("Google Drive" to "https://drive.google.com"),
    listOf("google maps", "maps") to ("Google Maps" to "https://maps.google.com"),
    listOf("google translate", "translate") to ("Google Translate" to "https://translate.google.com"),
    listOf("playstore", "play store") to ("Google Play" to "https://play.google.com"),
    listOf("app store", "appstore") to ("App Store" to "https://apps.apple.com"),
    listOf("hoyoverse", "genshin", "genshin impact") to ("Genshin Impact" to "https://genshin.hoyoverse.com"),
    listOf("honkai", "star rail") to ("Honkai: Star Rail" to "https://hsr.hoyoverse.com"),
    listOf("mobile legends", "mlbb") to ("Mobile Legends" to "https://m.mobilelegends.com"),
    listOf("valorant") to ("Valorant" to "https://playvalorant.com"),
    listOf("nvidia") to ("NVIDIA" to "https://www.nvidia.com"),
    listOf("amd") to ("AMD" to "https://www.amd.com"),
    listOf("intel") to ("Intel" to "https://www.intel.com"),
)

// Question shapes like "denia website github official yang mana?" / "situs resmi shopee?" / "where is the github site".
private val SITE_QUESTION = Regex(
    """\b(website|web|situs|site|link|url|halaman|homepage|official|resmi|buka|open|cari|search|carikan|cariin|find|goto|go to)\b""",
    RegexOption.IGNORE_CASE,
)
private val FILLER = Regex(
    """\b(denia|website|web|situs|site|link|url|halaman|homepage|official|resmi|officialnya|resminya|yang|mana|dimana|di mana|itu|apa|apaan|dong|sih|nya|tolong|please|coba|the|is|what|which|where|of|for|buka|open|cari|search|carikan|cariin|find|me|kan|ya|deh|aja|ga|gak|nggak|dulu)\b""",
    RegexOption.IGNORE_CASE,
)

private fun extractSiteName(text: String): String =
    text.replace(Regex("""[?!.,:;"']"""), " ")
        .replace(FILLER, " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

fun lookupSite(text: String, lang: Lang, engine: SearchEngine): DeniaReply? {
    if (!SITE_QUESTION.containsMatchIn(text)) return null
    val name = extractSiteName(text)
    if (name.isEmpty()) return null
    val needle = name.lowercase()

    val hit = SITES.firstOrNull { (aliases, _) -> aliases.any { alias -> needle == alias || needle.contains(alias) } }
    if (hit != null) {
        val (label, url) = hit.second
        val host = url.removePrefix("https://").removePrefix("www.")
        return DeniaReply(
            text = tx(
                lang,
                "Found it! The official $label site is $host. Open it in a new tab?",
                "Ketemu! Situs resmi $label itu $host. Buka di tab baru?",
            ),
            mood = Mood.HAPPY,
            action = DeniaAction.OPEN_URL,
            pending = PendingOpen(url, label, PendingActionType.OPEN_URL, target = label),
        )
    }

    // Fallback URL if the runtime web search fails: the configured engine's search page.
    val fallback = engine.queryUrl + URLEncoder.encode("$name official site", "UTF-8")
    return DeniaReply(
        text = tx(
            lang,
            "Hmm, $name is not in my notes. Want me to search for the official site in a new tab?",
            "Hmm, $name nggak ada di catatanku. Mau aku cari situs resminya di tab baru?",
        ),
        mood = Mood.NEUTRAL,
        action = DeniaAction.OPEN_URL,
        pending = PendingOpen(fallback, name, PendingActionType.SEARCH_AND_OPEN_OFFICIAL, target = name),
    )
}

/** Local-only match. Site questions first (they contain "?"), then short commands. Null means "ask the AI". */
fun matchLocal(message: String, lang: Lang, engine: SearchEngine): DeniaReply? {
    val t = message.trim()
    lookupSite(t, lang, engine)?.let { return it }
    if (t.length <= 60 && !t.contains('?')) {
        RULES.firstOrNull { it.regex.containsMatchIn(t) }?.let { return it.build(lang) }
    }
    return null
}

/** Shown when the message is free-form and there is no OpenRouter key to answer it. */
fun offlineReply(lang: Lang): DeniaReply = DeniaReply(
    tx(
        lang,
        "My AI brain is not connected yet. Add an OpenRouter key in Settings (openrouter.ai/keys)~ Until then I can still open tabs, change theme, and find official sites.",
        "Otak AI-ku belum tersambung. Isi key OpenRouter di Pengaturan (openrouter.ai/keys)~ Sementara itu aku masih bisa buka tab, ganti tema, dan cari situs resmi.",
    ),
    Mood.NEUTRAL,
)

fun deniaSuggestions(lang: Lang): List<String> =
    if (lang == Lang.ID) listOf("Sembunyikan Denia", "Tab baru", "Tema sakura", "Website GitHub yang mana?")
    else listOf("Hide Denia", "New tab", "Sakura theme", "Which site is GitHub?")

/* ============================================================================
 * Confirmation detection: turns "yes / ya / buka / iya buka / ok, open it" into
 * a Boolean instead of shipping it to Google as a search query.
 * ============================================================================ */

private val YES_WORDS = Regex(
    """^\s*(y|yes|yes,?\s*open|yes,?\s*please|yeah|yep|yup|sure|ok|okay|okey|oke|buka|iya|ya|yaudah|ayo|boleh|silakan|silahkan|lanjut|jalan|go|do it|open it|open,?\s*please|pls|please|of course)[\s.!,]*$""",
    RegexOption.IGNORE_CASE,
)
private val NO_WORDS = Regex(
    """^\s*(n|no|nope|nah|jangan|tidak|gak|nggak|ngga|engga|batal|cancel|stop|skip|nope,?\s*thanks|no thanks|nvm|never mind)[\s.!,]*$""",
    RegexOption.IGNORE_CASE,
)

fun isYesAnswer(text: String): Boolean = YES_WORDS.matches(text.trim())
fun isNoAnswer(text: String): Boolean = NO_WORDS.matches(text.trim())

/* ============================================================================
 * Runtime web search — Denia uses the USER'S internet (no API key needed) to
 * find the top real result and open it in a new tab. DuckDuckGo HTML endpoint
 * returns plain HTML that is trivial to parse, and does not require a key.
 *
 * Callers should invoke this from a coroutine (Dispatchers.IO already applied).
 * Never called from the UI thread.
 * ============================================================================ */
private val DDG_HTML_ENDPOINT = "https://html.duckduckgo.com/html/?q="
private val UA =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

// Result href on the DDG HTML page looks like:
//   /l/?uddg=<url-encoded-real-url>&rut=...
// or occasionally already an absolute URL.
private val DDG_HREF = Regex("""<a[^>]+class="[^"]*result__a[^"]*"[^>]+href="([^"]+)""", RegexOption.IGNORE_CASE)

/** Best-effort blocklist: skip trackers / redirectors / ad hosts, prefer the real destination. */
private val SEARCH_SKIP_HOSTS = listOf(
    "duckduckgo.com", "google.com/aclk", "bing.com/aclick",
    "youtube.com/redirect",
)

data class WebSearchHit(val url: String, val host: String)

/** Returns the best organic hit for `query`, or null on failure. Runs on Dispatchers.IO. */
suspend fun deniaWebSearch(query: String): WebSearchHit? = withContext(Dispatchers.IO) {
    val q = query.trim().ifBlank { return@withContext null }
    val encoded = URLEncoder.encode(q, "UTF-8")
    var conn: HttpURLConnection? = null
    try {
        conn = URL(DDG_HTML_ENDPOINT + encoded).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 8_000
        conn.readTimeout = 12_000
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml")
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9,id;q=0.8")
        val code = conn.responseCode
        if (code !in 200..299) return@withContext null
        val html = conn.inputStream.bufferedReader().use { it.readText() }

        for (match in DDG_HREF.findAll(html)) {
            val raw = match.groupValues[1]
            val target = decodeDdgHref(raw) ?: continue
            if (SEARCH_SKIP_HOSTS.any { target.contains(it, ignoreCase = true) }) continue
            val host = runCatching { java.net.URI(target).host?.removePrefix("www.") }.getOrNull()
                ?: continue
            if (host.isBlank()) continue
            return@withContext WebSearchHit(target, host)
        }
        null
    } catch (_: Exception) {
        null
    } finally {
        conn?.disconnect()
    }
}

/** DDG wraps result URLs in `/l/?uddg=<encoded>&...`. Unwrap when possible, else pass through. */
private fun decodeDdgHref(href: String): String? {
    if (href.isBlank()) return null
    val cleaned = when {
        href.startsWith("//") -> "https:$href"
        else -> href
    }
    // Grab uddg param if present.
    val uddg = Regex("""[?&]uddg=([^&]+)""").find(cleaned)?.groupValues?.getOrNull(1)
    val resolved = if (uddg != null) {
        runCatching { java.net.URLDecoder.decode(uddg, "UTF-8") }.getOrNull() ?: return null
    } else {
        cleaned
    }
    // Only accept absolute http(s) URLs.
    return if (resolved.startsWith("http://") || resolved.startsWith("https://")) resolved else null
}
