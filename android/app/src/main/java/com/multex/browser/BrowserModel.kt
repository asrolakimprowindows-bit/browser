package com.multex.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.webkit.WebViewFeature
import com.multex.browser.denia.Cue
import com.multex.browser.denia.DeniaAction
import com.multex.browser.denia.DeniaReply
import com.multex.browser.denia.Mood
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import kotlin.math.abs

private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

/**
 * Separate cookie jars per session, using WebView profiles (androidx.webkit multi-profile).
 * Called through reflection so the app still builds and runs (without isolation) if the
 * installed androidx.webkit or System WebView is too old to have the API.
 */
object ProfileSupport {
    private const val STORE = "androidx.webkit.ProfileStore"
    private const val PROFILE = "androidx.webkit.Profile"
    private const val COMPAT = "androidx.webkit.WebViewCompat"

    val available: Boolean by lazy {
        WebViewFeature.isFeatureSupported("MULTI_PROFILE") &&
            runCatching { Class.forName(STORE); Class.forName(COMPAT) }.isSuccess
    }

    private fun store(): Any? = Class.forName(STORE).getMethod("getInstance").invoke(null)

    /** Must run before any other call on [webView] (loadUrl, settings that load, ...). */
    fun attach(webView: WebView, profileName: String): Boolean {
        if (!available) return false
        return runCatching {
            Class.forName(STORE).getMethod("getOrCreateProfile", String::class.java).invoke(store(), profileName)
            Class.forName(COMPAT)
                .getMethod("setProfile", WebView::class.java, String::class.java)
                .invoke(null, webView, profileName)
            true
        }.getOrDefault(false)
    }

    fun delete(profileName: String) {
        if (!available) return
        runCatching { Class.forName(STORE).getMethod("deleteProfile", String::class.java).invoke(store(), profileName) }
    }

    fun clearCookies(profileName: String) {
        if (!available) {
            CookieManager.getInstance().removeAllCookies(null)
            return
        }
        runCatching {
            val profile = Class.forName(STORE).getMethod("getProfile", String::class.java).invoke(store(), profileName)
            if (profile != null) {
                val cm = Class.forName(PROFILE).getMethod("getCookieManager").invoke(profile) as CookieManager
                cm.removeAllCookies(null)
                cm.flush()
            }
        }
    }
}

/**
 * Everything the web preview keeps in BrowserShell's useState hooks, plus the real WebViews.
 * Tabs belong to the active session; other sessions are parked (their tabs are kept as data).
 */
class BrowserModel(private val activity: Activity) {

    private val prefs = activity.getSharedPreferences("multex.browser", Context.MODE_PRIVATE)
    private val store = SettingsStore(activity)
    private val handler = Handler(Looper.getMainLooper())

    var settings by mutableStateOf(store.load())
        private set

    val tabs = mutableStateListOf<Tab>()

    /** Home screen shortcuts. Editable: add, edit, delete, reset. */
    val shortcuts = mutableStateListOf<Shortcut>()
    var shortcutEditMode by mutableStateOf(false)
    /** Index being edited in the shortcut sheet, or -1 when adding a new one. */
    var editingShortcut by mutableIntStateOf(-1)
    var activeId by mutableStateOf("")
    val sessions = mutableStateListOf<Session>()
    var activeSessionId by mutableStateOf("")
    var editingSessionId by mutableStateOf("")
        private set

    var overlay by mutableStateOf(Overlay.NONE)
    var directMode by mutableStateOf(false)
    var chatOpen by mutableStateOf(false)
    /** Denia's live on-screen center (root pixels). The chat panel pivots out of this point. */
    var deniaAnchor by mutableStateOf<Offset?>(null)
    /** Bump to ask the chat bar to play its collapse-into-Denia animation, then close. */
    var chatCloseSignal by mutableIntStateOf(0)
        private set
    /**
     * Fullscreen state machine (Immersive.kt): the chrome bars are absorbed into a single
     * floating orb and released back out of it. Transitions (ENTERING / EXITING) ignore
     * further fullscreen input, so double taps or spam can never corrupt the state.
     */
    var fsState by mutableStateOf(FsState.NORMAL)
        private set

    var cue by mutableStateOf<Cue?>(null)
        private set
    var scrollTick by mutableIntStateOf(0)
        private set

    private val webViews = HashMap<String, WebView>()
    private var cueSeq = 0
    private var mobileUa = ""

    val isolationAvailable: Boolean get() = ProfileSupport.available
    val active: Tab get() = tabs.firstOrNull { it.id == activeId } ?: tabs.first()
    val activeSession: Session get() = sessions.firstOrNull { it.id == activeSessionId } ?: sessions.first()
    private val lang: Lang get() = settings.language

    init {
        restore()
    }

    // ------------------------------------------------------------------ Denia cues

    fun nudge(text: String, mood: Mood = Mood.HAPPY, force: Boolean = false) {
        cueSeq += 1
        cue = Cue(cueSeq, text, mood, force)
    }

    // ------------------------------------------------------------------ Settings

    fun updateSettings(transform: (Settings) -> Settings) {
        val old = settings
        val next = transform(old)
        settings = next
        store.save(next)
        if (next.desktopSite != old.desktopSite) {
            webViews.values.forEach { wv ->
                applyUa(wv)
                wv.reload()
            }
        }
    }

    private fun applyUa(wv: WebView) {
        wv.settings.userAgentString = if (settings.desktopSite) DESKTOP_UA else mobileUa
    }

    /** Wipes cookies + cache of the cookie jar the active session uses. */
    fun clearActiveCookies() {
        ProfileSupport.clearCookies(profileName())
        webViews.values.forEach { it.clearCache(true) }
        nudge(tx(lang, "Cookies cleared for this session~", "Cookie sesi ini sudah dihapus~"), Mood.HAPPY, true)
    }

    private fun profileName(): String = profileNameFor(activeSession.profile)

    // ------------------------------------------------------------------ Tabs

    private fun updateTab(id: String, block: (Tab) -> Tab) {
        val i = tabs.indexOfFirst { it.id == id }
        if (i >= 0) tabs[i] = block(tabs[i])
    }

    fun select(id: String) {
        activeId = id
        overlay = Overlay.NONE
    }

    fun newTab() {
        val t = makeHomeTab()
        tabs.add(t)
        activeId = t.id
        overlay = Overlay.NONE
        if (tabs.size >= 6) {
            nudge(
                tx(lang, "So many tabs! Want me to save them as a session?", "Banyak banget tab! Mau kusimpan jadi sesi?"),
                Mood.POUT,
                true,
            )
        }
        save()
    }

    fun closeTab(id: String) {
        destroyWebView(id)
        tabs.removeAll { it.id == id }
        if (tabs.isEmpty()) {
            val home = makeHomeTab()
            tabs.add(home)
            activeId = home.id
        } else if (id == activeId) {
            activeId = tabs.last().id
        }
        save()
    }

    fun openInNewTab(url: String, label: String? = null) {
        val t = makeTab(url, label)
        tabs.add(t)
        activeId = t.id
        overlay = Overlay.NONE
        chatOpen = false
        val name = label ?: t.host
        nudge(tx(lang, "Opening $name in a new tab~", "Buka $name di tab baru~"), Mood.HAPPY, true)
        save()
    }

    /** A link opened from another app (http/https VIEW intent). */
    fun openExternal(url: String) {
        if (active.kind == TabKind.HOME) navigate(url) else openInNewTab(url)
    }

    // ------------------------------------------------------------------ Navigation

    fun navigate(input: String) {
        val url = resolveInput(input, settings.searchEngine, settings.httpsOnly)
        if (url.isEmpty()) return
        val a = active
        val next = makeTab(url).copy(id = a.id)
        updateTab(a.id) { next }
        webViews[a.id]?.loadUrl(url)
        overlay = Overlay.NONE
        nudge(tx(lang, "Opening ${next.host}~", "Membuka ${next.host}~"))
        save()
    }

    fun goHome() {
        val a = active
        destroyWebView(a.id)
        updateTab(a.id) { makeHomeTab().copy(id = a.id) }
        nudge(tx(lang, "Back home~", "Kembali ke beranda~"), Mood.NEUTRAL)
        save()
    }

    fun back() {
        val wv = webViews[active.id]
        if (wv != null && wv.canGoBack()) wv.goBack()
        else if (active.kind == TabKind.PAGE) goHome()
    }

    fun forward() {
        val wv = webViews[active.id] ?: return
        if (wv.canGoForward()) wv.goForward()
    }

    fun reloadOrStop() {
        val wv = webViews[active.id] ?: return
        if (active.progress < 100) {
            wv.stopLoading()
            updateTab(active.id) { it.copy(progress = 100) }
        } else {
            wv.reload()
        }
    }

    // ------------------------------------------------------------------ Sessions

    private fun Tab.parked(): Tab = copy(progress = 100, canGoBack = false, canGoForward = false, blocked = 0)

    /** Writes the live tabs back into the active session's entry. */
    private fun parkActive(touch: Boolean) {
        val i = sessions.indexOfFirst { it.id == activeSessionId }
        if (i < 0) return
        val s = sessions[i]
        sessions[i] = s.copy(
            tabs = tabs.map { it.parked() },
            savedAt = if (touch) System.currentTimeMillis() else s.savedAt,
        )
    }

    private fun sessionName(): String = tx(lang, "Session", "Sesi") + " ${sessions.size}"

    /** "Save current tabs": snapshot the open pages. The snapshot keeps using the same cookie jar. */
    fun saveSession() {
        val pages = tabs.filter { it.kind == TabKind.PAGE }
        if (pages.isEmpty()) {
            nudge(tx(lang, "There is nothing to save yet~", "Belum ada yang bisa disimpan~"), Mood.POUT, true)
            return
        }
        val s = Session(
            id = uid(),
            name = sessionName(),
            savedAt = System.currentTimeMillis(),
            tabs = pages.map { it.parked().copy(id = uid()) },
            profile = activeSession.profile,
        )
        sessions.add(0, s)
        overlay = Overlay.SESSIONS
        nudge(
            tx(lang, "Saved ${pages.size} tabs as ${s.name}!", "${pages.size} tab disimpan jadi ${s.name}!"),
            Mood.HAPPY,
            true,
        )
        save()
    }

    /** "Restore": switch to a session. Its tabs come back, running in that session's cookie jar. */
    fun openSession(target: Session) {
        if (target.id == activeSessionId) {
            overlay = Overlay.NONE
            return
        }
        parkActive(touch = true)
        destroyAllWebViews()
        val fresh = sessions.firstOrNull { it.id == target.id } ?: return
        activeSessionId = fresh.id
        tabs.clear()
        tabs.addAll(fresh.tabs.ifEmpty { listOf(makeHomeTab()) }.map { it.parked() })
        activeId = tabs.first().id
        overlay = Overlay.NONE
        nudge(tx(lang, "Restored ${fresh.name}~", "${fresh.name} dipulihkan~"), Mood.HAPPY, true)
        save()
    }

    /** A brand new session: empty tabs and its own clean cookie jar (like a second phone). */
    fun newSession() {
        parkActive(touch = true)
        destroyAllWebViews()
        val id = uid()
        val s = Session(
            id = id,
            name = sessionName(),
            savedAt = System.currentTimeMillis(),
            tabs = listOf(makeHomeTab()),
            profile = id,
        )
        sessions.add(0, s)
        activeSessionId = s.id
        tabs.clear()
        tabs.addAll(s.tabs)
        activeId = tabs.first().id
        overlay = Overlay.NONE
        nudge(tx(lang, "Fresh session, fresh cookies!", "Sesi baru, cookie baru!"), Mood.HAPPY, true)
        save()
    }

    fun deleteSession(id: String) {
        if (id == activeSessionId) return
        val s = sessions.firstOrNull { it.id == id } ?: return
        sessions.remove(s)
        if (sessions.none { it.profile == s.profile }) ProfileSupport.delete(profileNameFor(s.profile))
        save()
    }

    fun openSessionRename(id: String) {
        if (sessions.none { it.id == id }) return
        editingSessionId = id
        overlay = Overlay.SESSION_NAME
    }

    fun renameSession(id: String, rawName: String) {
        val name = rawName.trim().take(48)
        if (name.isEmpty()) {
            nudge(tx(lang, "A session needs a name~", "Sesi butuh nama dulu~"), Mood.POUT, true)
            return
        }
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        sessions[i] = sessions[i].copy(name = name)
        editingSessionId = ""
        overlay = Overlay.NONE
        save()
        nudge(tx(lang, "Session renamed~", "Nama sesi sudah diubah~"), Mood.HAPPY)
    }

    fun cancelSessionRename() {
        editingSessionId = ""
        overlay = Overlay.NONE
    }

    // ------------------------------------------------------------------ Shortcuts

    fun openShortcutEditor(index: Int) {
        editingShortcut = index
        overlay = Overlay.SHORTCUT
    }

    fun saveShortcut(index: Int, title: String, address: String, tint: Color) {
        val addr = address.trim()
        if (addr.isEmpty()) {
            nudge(tx(lang, "I need an address for that~", "Butuh alamatnya dulu~"), Mood.POUT, true)
            return
        }
        val name = title.trim().ifEmpty { titleFor(normalizeHost(addr)) }
        val item = Shortcut(name, addr, tint)
        if (index in shortcuts.indices) shortcuts[index] = item else shortcuts.add(item)
        overlay = Overlay.NONE
        saveShortcuts()
        nudge(tx(lang, "Shortcut saved~", "Pintasan disimpan~"))
    }

    fun deleteShortcut(index: Int) {
        if (index !in shortcuts.indices) return
        shortcuts.removeAt(index)
        if (overlay == Overlay.SHORTCUT) overlay = Overlay.NONE
        saveShortcuts()
    }

    fun resetShortcuts() {
        shortcuts.clear()
        shortcuts.addAll(SHORTCUTS)
        saveShortcuts()
        nudge(tx(lang, "Shortcuts back to default~", "Pintasan dikembalikan~"))
    }

    private fun saveShortcuts() {
        val arr = JSONArray()
        shortcuts.forEach { sc ->
            arr.put(JSONObject().put("title", sc.title).put("address", sc.host).put("tint", sc.tint.toArgb()))
        }
        prefs.edit().putString("shortcuts", arr.toString()).apply()
    }

    private fun restoreShortcuts() {
        val loaded = try {
            val raw = prefs.getString("shortcuts", null)
            if (raw == null) null else {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Shortcut(o.getString("title"), o.getString("address"), Color(o.optInt("tint", HOME_TINT.toArgb())))
                }
            }
        } catch (e: Exception) {
            null
        }
        shortcuts.clear()
        shortcuts.addAll(loaded ?: SHORTCUTS)
    }

    // ------------------------------------------------------------------ Denia

    fun startDirect() {
        overlay = Overlay.NONE
        if (!settings.companionEnabled) updateSettings { it.copy(companionEnabled = true) }
        directMode = true
        nudge(tx(lang, "Tap anywhere and I will run there!", "Ketuk di mana saja, aku lari ke sana!"), Mood.HAPPY, true)
    }

    fun openChat() {
        overlay = Overlay.NONE
        chatOpen = true
    }

    /** Closes the chat bar through its reverse (absorb-back-into-Denia) animation. */
    fun closeChatAnimated() {
        chatCloseSignal += 1
    }

    /** Enter fullscreen (start the absorption) or leave it (release the chrome from the orb). */
    fun toggleImmersive() {
        when (fsState) {
            FsState.NORMAL -> {
                overlay = Overlay.NONE
                chatOpen = false
                fsState = FsState.ENTERING
                nudge(
                    tx(lang, "Fullscreen! Tap the orb for controls, hold it to bring the bars back~", "Layar penuh! Ketuk bolanya buat kontrol, tahan buat balikin bar-nya~"),
                    Mood.HAPPY,
                    true,
                )
            }
            FsState.ORB, FsState.MENU_OPEN -> exitImmersive()
            FsState.ENTERING, FsState.EXITING -> Unit // mid-transition: ignore, state stays consistent
        }
    }

    /** Releases the chrome back out of the orb. Only valid from a settled fullscreen state. */
    fun exitImmersive() {
        if (fsState == FsState.ORB || fsState == FsState.MENU_OPEN) fsState = FsState.EXITING
    }

    /** Animation-completion callbacks from ImmersiveOrb; invalid transitions are dropped. */
    fun applyFsState(next: FsState) {
        val valid = when (fsState) {
            FsState.ENTERING -> next == FsState.ORB
            FsState.EXITING -> next == FsState.NORMAL
            FsState.ORB -> next == FsState.MENU_OPEN
            FsState.MENU_OPEN -> next == FsState.ORB
            FsState.NORMAL -> false
        }
        if (valid) fsState = next
    }

    fun openOverlay(o: Overlay) {
        overlay = o
        if (o == Overlay.SETTINGS) nudge(tx(lang, "Ooh, dressing me up?", "Ooh, mau dandanin aku?"))
    }

    val chatContext: String
        get() = "${tabs.size} tabs open, active tab: " +
            (if (active.kind == TabKind.HOME) "home screen" else active.host) +
            ", theme: ${settings.theme.id}, Denia visible: ${settings.companionEnabled}, ${sessions.size} sessions"

    fun handleDeniaReply(r: DeniaReply) {
        when (r.action) {
            DeniaAction.HIDE_DENIA -> {
                nudge(r.text, r.mood, true)
                handler.postDelayed({
                    updateSettings { it.copy(companionEnabled = false) }
                    chatOpen = false
                }, 1400)
                return
            }
            DeniaAction.SHOW_DENIA -> updateSettings { it.copy(companionEnabled = true) }
            DeniaAction.NEW_TAB -> newTab()
            DeniaAction.OPEN_TABS -> { overlay = Overlay.TABS; chatOpen = false }
            DeniaAction.OPEN_SETTINGS -> { overlay = Overlay.SETTINGS; chatOpen = false }
            DeniaAction.OPEN_SESSIONS -> { overlay = Overlay.SESSIONS; chatOpen = false }
            DeniaAction.SAVE_SESSION -> {
                saveSession()
                chatOpen = false
                return
            }
            DeniaAction.DIRECT_MODE -> {
                startDirect()
                chatOpen = false
                return
            }
            DeniaAction.THEME_SAKURA -> updateSettings { it.copy(theme = ThemeId.SAKURA) }
            DeniaAction.THEME_MIDNIGHT -> updateSettings { it.copy(theme = ThemeId.MIDNIGHT) }
            DeniaAction.GO_HOME -> goHome()
            DeniaAction.RELOAD -> webViews[active.id]?.reload()
            DeniaAction.GO_BACK -> back()
            DeniaAction.OPEN_URL, DeniaAction.NONE -> Unit
        }
        if (!settings.companionEnabled && r.action != DeniaAction.SHOW_DENIA) {
            updateSettings { it.copy(companionEnabled = true) }
        }
        nudge(r.text, r.mood, true)
    }

    // ------------------------------------------------------------------ WebViews

    @SuppressLint("SetJavaScriptEnabled")
    fun webViewFor(tabId: String): WebView {
        webViews[tabId]?.let { existing ->
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }

        val wv = WebView(activity)

        // Must come first: gives this WebView its own cookie jar (the session's profile).
        ProfileSupport.attach(wv, profileName())

        val ws = wv.settings
        ws.javaScriptEnabled = true
        ws.domStorageEnabled = true
        ws.loadWithOverviewMode = true
        ws.useWideViewPort = true
        ws.setSupportZoom(true)
        ws.builtInZoomControls = true
        ws.displayZoomControls = false
        ws.mediaPlaybackRequiresUserGesture = true
        if (mobileUa.isEmpty()) {
            // Look like regular Chrome (helps Google sign-in in a WebView; not guaranteed).
            mobileUa = ws.userAgentString.replace("; wv", "").replace("Version/4.0 ", "")
        }
        applyUa(wv)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

        var lastScrollY = 0
        wv.setOnScrollChangeListener { _, _, y, _, _ ->
            if (abs(y - lastScrollY) > 320) {
                lastScrollY = y
                scrollTick += 1
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                updateTab(tabId) { it.copy(progress = newProgress) }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) updateTab(tabId) { it.copy(title = title) }
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val req = request ?: return false
                val uri = req.url ?: return false
                return when (uri.scheme) {
                    "https", "about", "data", "blob", "javascript", null -> false
                    "http" -> {
                        if (settings.httpsOnly && req.isForMainFrame) {
                            view?.loadUrl(uri.toString().replaceFirst("http://", "https://"))
                            true
                        } else {
                            false
                        }
                    }
                    else -> {
                        try {
                            activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        } catch (e: Exception) {
                            // no app can open it
                        }
                        true
                    }
                }
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val req = request ?: return null
                if (settings.blockTrackers && isTracker(req.url?.host)) {
                    handler.post { updateTab(tabId) { it.copy(blocked = it.blocked + 1) } }
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (url == null) return
                val host = hostOf(url)
                updateTab(tabId) { t ->
                    t.copy(
                        url = url,
                        host = host,
                        tint = tintFor(host),
                        title = if (t.host == host && t.title.isNotBlank()) t.title else titleFor(host),
                        blocked = 0,
                    )
                }
                sync(view, tabId)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                updateTab(tabId) { it.copy(progress = 100) }
                sync(view, tabId)
                save()
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                sync(view, tabId)
            }
        }

        webViews[tabId] = wv
        val url = tabs.firstOrNull { it.id == tabId }?.url.orEmpty()
        if (url.isNotBlank()) wv.loadUrl(url)
        return wv
    }

    private fun sync(v: WebView?, tabId: String) {
        if (v == null) return
        updateTab(tabId) { it.copy(canGoBack = v.canGoBack(), canGoForward = v.canGoForward()) }
    }

    private fun destroyWebView(tabId: String) {
        val wv = webViews.remove(tabId) ?: return
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.stopLoading()
        wv.destroy()
    }

    private fun destroyAllWebViews() {
        webViews.keys.toList().forEach { destroyWebView(it) }
    }

    // ------------------------------------------------------------------ Lifecycle

    fun onPause() {
        save()
        webViews.values.forEach { it.onPause() }
    }

    fun onResume() {
        webViews.values.forEach { it.onResume() }
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        destroyAllWebViews()
    }

    // ------------------------------------------------------------------ Persistence

    private fun tabsToJson(list: List<Tab>): JSONArray {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("kind", t.kind.name)
                    .put("title", t.title)
                    .put("host", t.host)
                    .put("url", t.url)
                    .put("tint", t.tint.toArgb()),
            )
        }
        return arr
    }

    private fun tabsFromJson(arr: JSONArray): List<Tab> = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val url = o.optString("url")
        val isPage = o.optString("kind") == TabKind.PAGE.name && url.isNotBlank()
        Tab(
            id = o.getString("id"),
            kind = if (isPage) TabKind.PAGE else TabKind.HOME,
            title = if (isPage) o.optString("title") else "New tab",
            host = if (isPage) o.optString("host") else "",
            url = if (isPage) url else "",
            tint = if (isPage) Color(o.optInt("tint", HOME_TINT.toArgb())) else HOME_TINT,
        )
    }

    fun save() {
        parkActive(touch = false)
        val arr = JSONArray()
        sessions.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("savedAt", s.savedAt)
                    .put("profile", s.profile)
                    .put("tabs", tabsToJson(s.tabs)),
            )
        }
        prefs.edit()
            .putString("sessions", arr.toString())
            .putString("activeSession", activeSessionId)
            .putString("activeTab", activeId)
            .apply()
    }

    private fun restore() {
        try {
            val raw = prefs.getString("sessions", null)
            if (raw != null) {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.getString("id")
                    sessions.add(
                        Session(
                            id = id,
                            name = o.getString("name"),
                            savedAt = o.optLong("savedAt", System.currentTimeMillis()),
                            tabs = tabsFromJson(o.getJSONArray("tabs")),
                            profile = o.optString("profile", id).ifBlank { id },
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            sessions.clear()
        }

        if (sessions.isEmpty()) {
            sessions.add(
                Session(
                    id = "main",
                    name = "Main",
                    savedAt = System.currentTimeMillis(),
                    tabs = listOf(makeHomeTab()),
                    profile = "main",
                ),
            )
        }

        val savedActive = prefs.getString("activeSession", null)
        activeSessionId = sessions.firstOrNull { it.id == savedActive }?.id ?: sessions.first().id
        val current = sessions.first { it.id == activeSessionId }
        tabs.addAll(current.tabs.ifEmpty { listOf(makeHomeTab()) }.map { it.parked() })

        val savedTab = prefs.getString("activeTab", null)
        activeId = tabs.firstOrNull { it.id == savedTab }?.id ?: tabs.first().id

        restoreShortcuts()
    }
}
