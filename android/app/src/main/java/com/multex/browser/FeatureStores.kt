package com.multex.browser

/*
 * Stores and state for the feature pack: history, downloads log, userscript "extensions",
 * per-site permission rules, and the global motion dial.
 * Everything persists in app-private SharedPreferences as JSON (no new dependencies).
 */

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/* ---------- Global animation dial ---------- */

/** Read by composables to scale (or kill) every transition. Mirrors Settings.animLevel. */
object Motion {
    var level by mutableStateOf(AnimLevel.DEFAULT)
    /** Scale a base duration (ms) by the current level. OFF collapses to 1ms. */
    fun ms(base: Int): Int = when (level) {
        AnimLevel.OFF -> 1
        AnimLevel.DEFAULT -> base
        AnimLevel.FULL -> (base * 1.6f).toInt()
    }
    val enabled: Boolean get() = level != AnimLevel.OFF
}

/* ---------- Known native apps for the "open in app?" prompt ---------- */

/** host -> (package name to app label). Prompted only when the package is installed. */
val APP_LINK_TARGETS: Map<String, Pair<String, String>> = mapOf(
    "youtube.com" to ("com.google.android.youtube" to "YouTube"),
    "m.youtube.com" to ("com.google.android.youtube" to "YouTube"),
    "music.youtube.com" to ("com.google.android.apps.youtube.music" to "YouTube Music"),
    "discord.com" to ("com.discord" to "Discord"),
    "x.com" to ("com.twitter.android" to "X"),
    "twitter.com" to ("com.twitter.android" to "X"),
    "instagram.com" to ("com.instagram.android" to "Instagram"),
    "tiktok.com" to ("com.zhiliaoapp.musically" to "TikTok"),
    "reddit.com" to ("com.reddit.frontpage" to "Reddit"),
    "open.spotify.com" to ("com.spotify.music" to "Spotify"),
    "spotify.com" to ("com.spotify.music" to "Spotify"),
    "facebook.com" to ("com.facebook.katana" to "Facebook"),
    "t.me" to ("org.telegram.messenger" to "Telegram"),
)

/* ---------- Browsing history ---------- */

data class HistoryEntry(val url: String, val title: String, val host: String, val time: Long)

class HistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("multex.history", Context.MODE_PRIVATE)
    var revision by mutableIntStateOf(0)
        private set

    fun all(): List<HistoryEntry> {
        val raw = prefs.getString("items", "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                HistoryEntry(o.getString("url"), o.optString("title"), o.optString("host"), o.optLong("time"))
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun add(url: String, title: String) {
        if (!url.startsWith("http")) return
        if (url.contains("translate.google.com/translate")) return
        val list = all().toMutableList()
        if (list.lastOrNull()?.url == url) return
        list.add(HistoryEntry(url, title, hostOf(url), System.currentTimeMillis()))
        while (list.size > 500) list.removeAt(0)
        persist(list)
    }

    fun remove(entry: HistoryEntry) {
        persist(all().filterNot { it.url == entry.url && it.time == entry.time })
    }

    fun clear() = persist(emptyList())

    private fun persist(list: List<HistoryEntry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().put("url", e.url).put("title", e.title).put("host", e.host).put("time", e.time))
        }
        prefs.edit().putString("items", arr.toString()).apply()
        revision += 1
    }
}

/* ---------- Downloads log ---------- */

data class DownloadEntry(val id: String, val name: String, val url: String, val mime: String, val time: Long, val status: String)

class DownloadLogStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("multex.downloads", Context.MODE_PRIVATE)
    var revision by mutableIntStateOf(0)
        private set

    fun all(): List<DownloadEntry> {
        val raw = prefs.getString("items", "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DownloadEntry(
                    o.getString("id"), o.getString("name"), o.getString("url"),
                    o.optString("mime"), o.optLong("time"), o.optString("status", "queued"),
                )
            }.reversed()
        }.getOrDefault(emptyList())
    }

    fun add(e: DownloadEntry) {
        val arr = JSONArray(prefs.getString("items", "[]") ?: "[]")
        arr.put(
            JSONObject().put("id", e.id).put("name", e.name).put("url", e.url)
                .put("mime", e.mime).put("time", e.time).put("status", e.status),
        )
        while (arr.length() > 200) arr.remove(0)
        prefs.edit().putString("items", arr.toString()).apply()
        revision += 1
    }

    fun remove(id: String) {
        val kept = all().filterNot { it.id == id }.reversed()
        val arr = JSONArray()
        kept.forEach { e ->
            arr.put(
                JSONObject().put("id", e.id).put("name", e.name).put("url", e.url)
                    .put("mime", e.mime).put("time", e.time).put("status", e.status),
            )
        }
        prefs.edit().putString("items", arr.toString()).apply()
        revision += 1
    }

    fun clear() {
        prefs.edit().putString("items", "[]").apply()
        revision += 1
    }
}

/* ---------- Userscript "extensions" (Firefox add-ons style) ---------- */

/**
 * A real Firefox extension engine needs GeckoView; on a WebView browser the honest equivalent
 * is a userscript manager (Tampermonkey-style): JS snippets injected into matching pages.
 */
data class ExtScript(
    val id: String,
    val name: String,
    val hosts: List<String>,
    val enabled: Boolean,
    val code: String,
    val builtin: Boolean = false,
)

private val BUILTIN_SCRIPTS = listOf(
    ExtScript(
        id = "builtin-reader",
        name = "Reader boost",
        hosts = listOf("*"),
        enabled = false,
        builtin = true,
        code = "(function(){document.querySelectorAll('p').forEach(function(p){p.style.lineHeight='1.7';p.style.maxWidth='72ch'})})();",
    ),
    ExtScript(
        id = "builtin-cookie",
        name = "Cookie banner hider",
        hosts = listOf("*"),
        enabled = false,
        builtin = true,
        code = "(function(){var k=['cookie','consent','gdpr'];document.querySelectorAll('div,section,aside').forEach(function(e){var id=((e.id||'')+' '+(typeof e.className==='string'?e.className:'')).toLowerCase();for(var i=0;i<k.length;i++){if(id.indexOf(k[i])>=0){var r=e.getBoundingClientRect();if(r.height<window.innerHeight*0.6&&r.width>window.innerWidth*0.5){e.style.display='none'}break}}})})();",
    ),
    ExtScript(
        id = "builtin-noshorts",
        name = "No YouTube Shorts",
        hosts = listOf("youtube.com"),
        enabled = false,
        builtin = true,
        code = "(function(){var h=function(){document.querySelectorAll('ytd-reel-shelf-renderer,ytd-rich-shelf-renderer').forEach(function(e){e.style.display='none'})};h();try{new MutationObserver(h).observe(document.body,{childList:true,subtree:true})}catch(e){}})();",
    ),
)

class ExtensionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("multex.extensions", Context.MODE_PRIVATE)
    var revision by mutableIntStateOf(0)
        private set

    private fun loadUser(): List<ExtScript> = runCatching {
        val arr = JSONArray(prefs.getString("user", "[]") ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val hostsArr = o.getJSONArray("hosts")
            ExtScript(
                id = o.getString("id"),
                name = o.getString("name"),
                hosts = (0 until hostsArr.length()).map { hostsArr.getString(it) },
                enabled = o.optBoolean("enabled", true),
                code = o.getString("code"),
            )
        }
    }.getOrDefault(emptyList())

    fun all(): List<ExtScript> {
        // Built-ins start disabled; the enabledBuiltins set holds the ones the user turned on.
        val enabled = enabledBuiltins()
        return BUILTIN_SCRIPTS.map { it.copy(enabled = it.id in enabled) } + loadUser()
    }

    private fun enabledBuiltins(): Set<String> = prefs.getStringSet("enabledBuiltins", emptySet()) ?: emptySet()

    fun toggle(id: String) {
        val script = all().firstOrNull { it.id == id } ?: return
        if (script.builtin) {
            val enabled = enabledBuiltins().toMutableSet()
            if (script.enabled) enabled.remove(id) else enabled.add(id)
            prefs.edit().putStringSet("enabledBuiltins", enabled).apply()
        } else {
            persistUser(loadUser().map { if (it.id == id) it.copy(enabled = !it.enabled) else it })
            return
        }
        revision += 1
    }

    fun add(name: String, hostsCsv: String, code: String) {
        val hosts = hostsCsv.split(',', ' ', '\n').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            .ifEmpty { listOf("*") }
        persistUser(loadUser() + ExtScript(uid(), name.ifBlank { "My script" }, hosts, true, code))
    }

    fun remove(id: String) = persistUser(loadUser().filterNot { it.id == id })

    private fun persistUser(list: List<ExtScript>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject().put("id", s.id).put("name", s.name)
                    .put("hosts", JSONArray(s.hosts)).put("enabled", s.enabled).put("code", s.code),
            )
        }
        prefs.edit().putString("user", arr.toString()).apply()
        revision += 1
    }

    /** Enabled scripts whose host list covers [host] ("*" matches everything, otherwise suffix match). */
    fun enabledFor(host: String): List<ExtScript> = all().filter { s ->
        s.enabled && s.hosts.any { h -> h == "*" || host == h || host.endsWith(".$h") }
    }
}

/* ---------- Per-site permission rules (site settings) ---------- */

enum class Perm { ASK, ALLOW, DENY }

data class SiteRule(
    val mic: Perm = Perm.ASK,
    val cam: Perm = Perm.ASK,
    val loc: Perm = Perm.ASK,
)

class SitePrefsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("multex.siteprefs", Context.MODE_PRIVATE)
    var revision by mutableIntStateOf(0)
        private set

    fun ruleFor(host: String): SiteRule {
        val raw = prefs.getString(host, null) ?: return SiteRule()
        return runCatching {
            val o = JSONObject(raw)
            SiteRule(
                mic = Perm.valueOf(o.optString("mic", "ASK")),
                cam = Perm.valueOf(o.optString("cam", "ASK")),
                loc = Perm.valueOf(o.optString("loc", "ASK")),
            )
        }.getOrDefault(SiteRule())
    }

    fun set(host: String, rule: SiteRule) {
        prefs.edit().putString(
            host,
            JSONObject().put("mic", rule.mic.name).put("cam", rule.cam.name).put("loc", rule.loc.name).toString(),
        ).apply()
        revision += 1
    }

    fun remove(host: String) {
        prefs.edit().remove(host).apply()
        revision += 1
    }

    fun all(): Map<String, SiteRule> =
        prefs.all.keys.sorted().associateWith { ruleFor(it) }
}
