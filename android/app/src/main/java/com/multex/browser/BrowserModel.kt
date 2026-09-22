package com.multex.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.ViewGroup
import java.io.File
import java.io.FileOutputStream
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import java.net.URLEncoder
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

    private val historyStore = HistoryStore(activity)
    private val downloadStore = DownloadLogStore(activity)
    val extensionStore = ExtensionStore(activity)
    val sitePrefs = SitePrefsStore(activity)

    /** "Open in app?" prompt: url to app label. Non-null while the dialog is up. */
    var appLinkPrompt by mutableStateOf<Pair<String, String>?>(null)
        private set
    /** Pending WebView permission request (mic/camera/...) awaiting the user's answer. */
    var permissionPrompt by mutableStateOf<PendingPerm?>(null)
        private set
    /** Pending geolocation prompt: origin to callback. */
    var geoPrompt by mutableStateOf<Pair<String, GeolocationPermissions.Callback>?>(null)
        private set
    var findBarOpen by mutableStateOf(false)
        private set
    var findInfo by mutableStateOf("")
        private set
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var fileLauncher: androidx.activity.result.ActivityResultLauncher<Intent>? = null
    private var profileImageLauncher: androidx.activity.result.ActivityResultLauncher<String>? = null
    /** URLs the user chose to keep in the browser (skip the app prompt for these). */
    private val appLinkAllowed = HashSet<String>()

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
        Motion.level = settings.animLevel
        (activity as? ComponentActivity)?.let { act ->
            fileLauncher = act.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
                val cb = filePathCallback ?: return@registerForActivityResult
                filePathCallback = null
                cb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data))
            }
            profileImageLauncher = act.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri != null) saveProfileAvatar(uri)
            }
        }
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
        if (old.profileImagePath.isNotBlank() && old.profileImagePath != next.profileImagePath && next.profileImagePath.isBlank()) {
            runCatching { File(old.profileImagePath).delete() }
        }
        settings = next
        store.save(next)
        Motion.level = next.animLevel
        if (next.textZoom != old.textZoom) {
            webViews.values.forEach { it.settings.textZoom = next.textZoom }
        }
        if (next.desktopSite != old.desktopSite) {
            webViews.values.forEach { wv ->
                applyUa(wv)
                wv.reload()
            }
        }
    }

    /** Opens the system image picker for the profile avatar. */
    fun chooseProfileImage() {
        profileImageLauncher?.launch("image/*")
    }

    /** Removes the stored profile avatar and returns to the emoji avatar. */
    fun removeProfileImage() {
        val old = settings.profileImagePath
        if (old.isNotBlank()) runCatching { File(old).delete() }
        updateSettings { it.copy(profileImagePath = "") }
    }

    /** Center-crops the selected image into a small private square avatar file. */
    private fun saveProfileAvatar(uri: Uri) {
        val decoded = runCatching {
            activity.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return

        val side = minOf(decoded.width, decoded.height)
        val left = (decoded.width - side) / 2
        val top = (decoded.height - side) / 2
        val cropped = Bitmap.createBitmap(decoded, left, top, side, side)
        if (cropped != decoded) decoded.recycle()

        val target = File(activity.filesDir, "profile_avatar.jpg")
        runCatching {
            FileOutputStream(target).use { out ->
                cropped.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            cropped.recycle()
            val old = settings.profileImagePath
            if (old.isNotBlank() && old != target.absolutePath) runCatching { File(old).delete() }
            updateSettings { it.copy(profileImagePath = target.absolutePath) }
        }.onFailure {
            if (!cropped.isRecycled) cropped.recycle()
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
        closeFindBar()
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
        if (tabs.firstOrNull { it.id == id }?.incognito == true) {
            webViews[id]?.clearCache(true)
            webViews[id]?.clearHistory()
        }
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
        val next = makeTab(url).copy(id = a.id, incognito = a.incognito)
        updateTab(a.id) { next }
        webViews[a.id]?.loadUrl(url)
        overlay = Overlay.NONE
        nudge(tx(lang, "Opening ${next.host}~", "Membuka ${next.host}~"))
        save()
    }

    fun goHome() {
        val a = active
        destroyWebView(a.id)
        updateTab(a.id) { makeHomeTab(a.incognito).copy(id = a.id) }
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
            tabs = tabs.filter { !it.incognito }.map { it.parked() },
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
        // "cari X" lands as OPEN_URL with a label; run it as a search in the current tab instead.
        r.pending?.let { target -> navigate(target.url) }
        if (!settings.companionEnabled && r.action != DeniaAction.SHOW_DENIA) {
            updateSettings { it.copy(companionEnabled = true) }
        }
        nudge(r.text, r.mood, true)
    }

    // ------------------------------------------------------------------ Feature pack

    fun newIncognitoTab() {
        val t = makeHomeTab(true)
        tabs.add(t)
        activeId = t.id
        overlay = Overlay.NONE
        nudge(tx(lang, "Incognito tab! Nothing here is written to history~", "Tab penyamaran! Semua di sini bebas dari riwayat~"), Mood.HAPPY, true)
    }

    fun history(): List<HistoryEntry> = historyStore.all().asReversed()
    fun clearHistory() {
        historyStore.clear()
        nudge(tx(lang, "History wiped clean~", "Riwayat sudah bersih~"), Mood.HAPPY, true)
    }
    fun removeHistoryEntry(e: HistoryEntry) = historyStore.remove(e)

    val historyRevision: Int get() = historyStore.revision
    val downloadRevision: Int get() = downloadStore.revision

    fun downloads(): List<DownloadEntry> = downloadStore.all()
    fun removeDownload(id: String) = downloadStore.remove(id)
    fun clearDownloads() = downloadStore.clear()

    fun openFindBar() {
        if (active.kind != TabKind.PAGE) return
        findBarOpen = true
        findInfo = ""
        overlay = Overlay.NONE
    }

    fun closeFindBar() {
        if (!findBarOpen) return
        findBarOpen = false
        webViews[active.id]?.clearMatches()
    }

    fun findInPage(q: String) {
        if (q.isBlank()) {
            webViews[active.id]?.clearMatches()
            findInfo = ""
        } else {
            webViews[active.id]?.findAllAsync(q)
        }
    }

    fun findNext(forward: Boolean) {
        webViews[active.id]?.findNext(forward)
    }

    /** Wraps the current page in Google Translate (sl=auto, tl = app language). */
    fun translatePage() {
        val wv = webViews[active.id] ?: return
        val url = active.url
        if (!url.startsWith("http")) return
        overlay = Overlay.NONE
        wv.loadUrl(
            "https://translate.google.com/translate?sl=auto&tl=${settings.language.id}&u=" +
                URLEncoder.encode(url, "UTF-8"),
        )
        nudge(tx(lang, "Translating this page~", "Lagi diterjemahin~"), Mood.HAPPY, true)
    }

    /** Puts the whole activity into Picture-in-Picture (for videos). */
    fun enterPip() {
        overlay = Overlay.NONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && active.kind == TabKind.PAGE) {
            runCatching {
                activity.enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build(),
                )
            }
        }
    }

    fun resolveAppLink(openApp: Boolean) {
        val p = appLinkPrompt ?: return
        appLinkPrompt = null
        if (openApp) {
            runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.first))) }
        } else {
            appLinkAllowed.add(p.first)
            webViews[active.id]?.loadUrl(p.first)
        }
    }

    fun answerPermission(grant: Boolean) {
        val p = permissionPrompt ?: return
        permissionPrompt = null
        if (grant) p.request.grant(p.request.resources) else p.request.deny()
        var rule = sitePrefs.ruleFor(p.host)
        p.resources.forEach { r ->
            when (r) {
                PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                    rule = rule.copy(mic = if (grant) Perm.ALLOW else Perm.DENY)
                PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                    rule = rule.copy(cam = if (grant) Perm.ALLOW else Perm.DENY)
            }
        }
        sitePrefs.set(p.host, rule)
    }

    fun answerGeo(allow: Boolean) {
        val g = geoPrompt ?: return
        geoPrompt = null
        g.second.invoke(g.first, allow, false)
        val host = hostOf(g.first)
        sitePrefs.set(host, sitePrefs.ruleFor(host).copy(loc = if (allow) Perm.ALLOW else Perm.DENY))
    }

    /** Denia command "cari X" / "search for X" runs as a search in the current tab. */
    fun searchWithDenia(query: String) {
        navigate(query)
        chatOpen = false
        nudge(tx(lang, "Searching for $query~", "Nyari $query~"), Mood.HAPPY, true)
    }

    // ------------------------------------------------------------------ Downloads

    fun startDownload(url: String, contentDisposition: String?, mime: String?) {
        val name = URLUtil.guessFileName(url, contentDisposition, mime)
        if (settings.downloadMode == DownloadMode.EXTERNAL && tryExternalDownloader(url, name)) {
            return
        }
        runCatching {
            val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(url))
            if (mime != null) req.setMimeType(mime)
            CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("Cookie", it) }
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            dm.enqueue(req)
            downloadStore.add(DownloadEntry(uid(), name, url, mime ?: "", System.currentTimeMillis(), "queued"))
            nudge(tx(lang, "Downloading $name~", "Mengunduh $name~"), Mood.HAPPY, true)
        }.onFailure {
            nudge(tx(lang, "Couldn't start that download...", "Unduhannya gagal mulai..."), Mood.POUT, true)
        }
    }

    /** Hands the file to an installed external downloader (1DM, ADM), falling back to a generic VIEW. */
    private fun tryExternalDownloader(url: String, name: String): Boolean {
        val pm = activity.packageManager
        val packages = listOf(
            "idm.internet.download.manager",
            "idm.internet.download.manager.plus",
            "idm.internet.download.manager.adm.lite",
            "com.dv.adm",
        )
        for (pkg in packages) {
            if (runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess) {
                val ok = runCatching {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg))
                }.isSuccess
                if (ok) {
                    downloadStore.add(DownloadEntry(uid(), name, url, "", System.currentTimeMillis(), "external"))
                    notifyExternalDownload(name)
                    nudge(tx(lang, "Sent to the external downloader~", "Dikirim ke downloader eksternal~"), Mood.HAPPY, true)
                    return true
                }
            }
        }
        // No dedicated downloader found: let Android resolve anything that wants the file.
        val ok = runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isSuccess
        if (ok) {
            downloadStore.add(DownloadEntry(uid(), name, url, "", System.currentTimeMillis(), "external"))
            notifyExternalDownload(name)
        }
        return ok
    }

    private fun notifyExternalDownload(name: String) {
        if (!settings.notifications) return
        if (Build.VERSION.SDK_INT >= 33 &&
            activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(NOTIF_CHANNEL, "Downloads", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        nm.notify(
            (System.currentTimeMillis() % 100000).toInt(),
            NotificationCompat.Builder(activity, NOTIF_CHANNEL)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(tx(lang, "Sent to external downloader", "Dikirim ke downloader eksternal"))
                .setContentText(name)
                .setAutoCancel(true)
                .build(),
        )
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
        ws.textZoom = settings.textZoom
        if (mobileUa.isEmpty()) {
            // Look like regular Chrome (helps Google sign-in in a WebView; not guaranteed).
            mobileUa = ws.userAgentString.replace("; wv", "").replace("Version/4.0 ", "")
        }
        applyUa(wv)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

        wv.setDownloadListener { url, _, contentDisposition, mime, _ ->
            startDownload(url, contentDisposition, mime)
        }
        wv.setFindListener { activeMatch, count, done ->
            findInfo = if (done) {
                if (count == 0) "0/0" else "${activeMatch + 1}/$count"
            } else findInfo
        }

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

            // Upload: <input type=file> opens the system picker and the result goes back to the page.
            override fun onShowFileChooser(
                view: WebView?,
                filePath: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?,
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = filePath
                val intent = params?.createIntent()
                    ?: Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                return try {
                    fileLauncher?.launch(intent)
                    true
                } catch (e: Exception) {
                    filePathCallback = null
                    false
                }
            }

            // Site settings: mic/camera requests follow the saved per-host rule, otherwise ask.
            override fun onPermissionRequest(request: PermissionRequest?) {
                val req = request ?: return
                val host = hostOf(req.origin.toString())
                val rule = sitePrefs.ruleFor(host)
                var undecided = false
                var denied = false
                req.resources.forEach { r ->
                    val p = when (r) {
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> rule.mic
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> rule.cam
                        else -> Perm.ASK
                    }
                    if (p == Perm.DENY) denied = true else if (p == Perm.ASK) undecided = true
                }
                when {
                    denied -> req.deny()
                    undecided -> permissionPrompt = PendingPerm(req, host, req.resources.toList())
                    else -> req.grant(req.resources)
                }
            }

            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                if (origin == null || callback == null) return
                when (sitePrefs.ruleFor(hostOf(origin)).loc) {
                    Perm.ALLOW -> callback.invoke(origin, true, false)
                    Perm.DENY -> callback.invoke(origin, false, false)
                    Perm.ASK -> geoPrompt = origin to callback
                }
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val req = request ?: return false
                val uri = req.url ?: return false
                return when (uri.scheme) {
                    "about", "data", "blob", "javascript", null -> false
                    "https" -> {
                        val url = uri.toString()
                        // Offer the native app for well-known sites (YouTube, Discord, ...) when installed.
                        if (req.isForMainFrame && settings.appLinkPrompt && url !in appLinkAllowed) {
                            val target = APP_LINK_TARGETS[hostOf(url)]
                            if (target != null && activity.packageManager.getLaunchIntentForPackage(target.first) != null) {
                                appLinkPrompt = url to target.second
                                return true
                            }
                        }
                        false
                    }
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
                if (settings.adBlock && isAdHost(req.url?.host)) {
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
                if (url != null) {
                    val tab = tabs.firstOrNull { it.id == tabId }
                    if (tab != null && !tab.incognito && url.startsWith("http")) {
                        historyStore.add(url, tab.title.ifBlank { titleFor(hostOf(url)) })
                    }
                    extensionStore.enabledFor(hostOf(url)).forEach { script ->
                        view?.evaluateJavascript(script.code, null)
                    }
                }
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

private const val NOTIF_CHANNEL = "multex.downloads"

/** A WebView permission request paused until the user answers the prompt dialog. */
data class PendingPerm(val request: PermissionRequest, val host: String, val resources: List<String>)
