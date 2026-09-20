package com.multex.browser.denia

import com.multex.browser.SearchEngine
import java.net.URLEncoder

/*
 * Denia's offline brain: bilingual strings, local command matching, and the "which site is X?"
 * lookup. Everything here runs on-device with no network or API key.
 */

enum class Lang(val id: String, val label: String) {
    EN("en", "English"),
    ID("id", "Indonesia"),
}

/** Pick the string for the active language. Keeps call sites short: tx(lang, "Hide", "Sembunyikan"). */
fun tx(lang: Lang, en: String, id: String): String = if (lang == Lang.ID) id else en

enum class DeniaAction {
    NONE, HIDE_DENIA, SHOW_DENIA, NEW_TAB, OPEN_SETTINGS, DIRECT_MODE,
    THEME_SAKURA, THEME_MIDNIGHT, GO_HOME, RELOAD, GO_BACK, OPEN_URL,
}

/** A URL Denia wants to open, but only after the user confirms with Yes. */
data class PendingOpen(val url: String, val label: String)

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
)

// Question shapes like "denia website github official yang mana?" / "situs resmi shopee?" / "where is the github site".
private val SITE_QUESTION = Regex(
    """\b(website|web|situs|site|link|url|halaman|homepage|official|resmi|buka|open|cari|search)\b""",
    RegexOption.IGNORE_CASE,
)
private val FILLER = Regex(
    """\b(denia|website|web|situs|site|link|url|halaman|homepage|official|resmi|officialnya|resminya|yang|mana|dimana|di mana|itu|apa|apaan|dong|sih|nya|tolong|please|coba|the|is|what|which|where|of|for|buka|open|cari|search|carikan|find|me|kan|ya|deh|aja)\b""",
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
            pending = PendingOpen(url, label),
        )
    }

    val query = URLEncoder.encode("$name official site", "UTF-8")
    return DeniaReply(
        text = tx(
            lang,
            "Hmm, $name is not in my notes. Want me to search for the official site in a new tab?",
            "Hmm, $name nggak ada di catatanku. Mau aku cari situs resminya di tab baru?",
        ),
        mood = Mood.NEUTRAL,
        action = DeniaAction.OPEN_URL,
        pending = PendingOpen(engine.queryUrl + query, name),
    )
}

/** Local-only reply. Site questions first (they contain "?"), then short commands, then a friendly fallback. */
fun deniaReply(message: String, lang: Lang, engine: SearchEngine): DeniaReply {
    val t = message.trim()
    lookupSite(t, lang, engine)?.let { return it }
    if (t.length <= 60 && !t.contains('?')) {
        RULES.firstOrNull { it.regex.containsMatchIn(t) }?.let { return it.build(lang) }
    }
    return DeniaReply(
        tx(
            lang,
            "I can hide, show, change theme, open settings, or find official sites for you~ Try \"which site is GitHub?\"",
            "Aku bisa sembunyi, muncul, ganti tema, buka pengaturan, atau cari situs resmi~ Coba \"website github yang mana?\"",
        ),
        Mood.NEUTRAL,
    )
}

fun deniaSuggestions(lang: Lang): List<String> =
    if (lang == Lang.ID) listOf("Sembunyikan Denia", "Tema sakura", "Website GitHub yang mana?", "Pengaturan")
    else listOf("Hide Denia", "Sakura theme", "Which site is GitHub?", "Settings")
