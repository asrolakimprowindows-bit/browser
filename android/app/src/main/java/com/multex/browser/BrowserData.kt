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
    ID("id", "Indonesia"),
    JV("jv", "Jawa"),
    SU("su", "Sunda"),
    MS("ms", "Melayu"),
    TL("tl", "Filipino"),
    TH("th", "ไทย"),
    VI("vi", "Tiếng Việt"),
    JA("ja", "日本語"),
    KO("ko", "한국어"),
    ZH("zh", "中文"),
    HI("hi", "हिन्दी"),
    AR("ar", "العربية"),
    ES("es", "Español"),
    PT("pt", "Português"),
    FR("fr", "Français"),
    DE("de", "Deutsch"),
    IT("it", "Italiano"),
    NL("nl", "Nederlands"),
    PL("pl", "Polski"),
    TR("tr", "Türkçe"),
    RU("ru", "Русский");

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: EN
    }
}

/**
 * Core UI strings machine-translated for the non-EN/ID languages. EN and ID are always
 * provided at the call site via tx(lang, en, id); everything else falls back to this
 * table, then to English when a string is not covered yet.
 */
private val L10N: Map<String, Map<Lang, String>> = mapOf(
    "Settings" to mapOf(Lang.JA to "設定", Lang.ZH to "设置", Lang.KO to "설정", Lang.ES to "Ajustes", Lang.FR to "Paramètres", Lang.DE to "Einstellungen", Lang.PT to "Configurações", Lang.RU to "Настройки"),
    "Tabs" to mapOf(Lang.JA to "タブ", Lang.ZH to "标签页", Lang.KO to "탭", Lang.ES to "Pestañas", Lang.FR to "Onglets", Lang.DE to "Tabs", Lang.PT to "Abas", Lang.RU to "Вкладки"),
    "New tab" to mapOf(Lang.JA to "新しいタブ", Lang.ZH to "新标签页", Lang.KO to "새 탭", Lang.ES to "Nueva pestaña", Lang.FR to "Nouvel onglet", Lang.DE to "Neuer Tab", Lang.PT to "Nova aba", Lang.RU to "Новая вкладка"),
    "History" to mapOf(Lang.JA to "履歴", Lang.ZH to "历史记录", Lang.KO to "방문 기록", Lang.ES to "Historial", Lang.FR to "Historique", Lang.DE to "Verlauf", Lang.PT to "Histórico", Lang.RU to "История"),
    "Downloads" to mapOf(Lang.JA to "ダウンロード", Lang.ZH to "下载", Lang.KO to "다운로드", Lang.ES to "Descargas", Lang.FR to "Téléchargements", Lang.DE to "Downloads", Lang.PT to "Downloads", Lang.RU to "Загрузки"),
    "Find in page" to mapOf(Lang.JA to "ページ内検索", Lang.ZH to "在页面中查找", Lang.KO to "페이지에서 찾기", Lang.ES to "Buscar en la página", Lang.FR to "Rechercher dans la page", Lang.DE to "Auf Seite suchen", Lang.PT to "Localizar na página", Lang.RU to "Найти на странице"),
    "Translate" to mapOf(Lang.JA to "翻訳", Lang.ZH to "翻译", Lang.KO to "번역", Lang.ES to "Traducir", Lang.FR to "Traduire", Lang.DE to "Übersetzen", Lang.PT to "Traduzir", Lang.RU to "Перевести"),
    "Theme" to mapOf(Lang.JA to "テーマ", Lang.ZH to "主题", Lang.KO to "테마", Lang.ES to "Tema", Lang.FR to "Thème", Lang.DE to "Design", Lang.PT to "Tema", Lang.RU to "Тема"),
    "Language" to mapOf(Lang.JA to "言語", Lang.ZH to "语言", Lang.KO to "언어", Lang.ES to "Idioma", Lang.FR to "Langue", Lang.DE to "Sprache", Lang.PT to "Idioma", Lang.RU to "Язык"),
    "Close" to mapOf(Lang.JA to "閉じる", Lang.ZH to "关闭", Lang.KO to "닫기", Lang.ES to "Cerrar", Lang.FR to "Fermer", Lang.DE to "Schließen", Lang.PT to "Fechar", Lang.RU to "Закрыть"),
    "Save" to mapOf(Lang.JA to "保存", Lang.ZH to "保存", Lang.KO to "저장", Lang.ES to "Guardar", Lang.FR to "Enregistrer", Lang.DE to "Speichern", Lang.PT to "Salvar", Lang.RU to "Сохранить"),
    "Delete" to mapOf(Lang.JA to "削除", Lang.ZH to "删除", Lang.KO to "삭제", Lang.ES to "Eliminar", Lang.FR to "Supprimer", Lang.DE to "Löschen", Lang.PT to "Excluir", Lang.RU to "Удалить"),
    "Cancel" to mapOf(Lang.JA to "キャンセル", Lang.ZH to "取消", Lang.KO to "취소", Lang.ES to "Cancelar", Lang.FR to "Annuler", Lang.DE to "Abbrechen", Lang.PT to "Cancelar", Lang.RU to "Отмена"),
    "Incognito" to mapOf(Lang.JA to "シークレット", Lang.ZH to "无痕", Lang.KO to "시크릿", Lang.ES to "Incógnito", Lang.FR to "Navigation privée", Lang.DE to "Inkognito", Lang.PT to "Anônimo", Lang.RU to "Инкогнито"),
    "Extensions" to mapOf(Lang.JA to "拡張機能", Lang.ZH to "扩展", Lang.KO to "확장 프로그램", Lang.ES to "Extensiones", Lang.FR to "Extensions", Lang.DE to "Erweiterungen", Lang.PT to "Extensões", Lang.RU to "Расширения"),
    "Profile" to mapOf(Lang.JA to "プロフィール", Lang.ZH to "个人资料", Lang.KO to "프로필", Lang.ES to "Perfil", Lang.FR to "Profil", Lang.DE to "Profil", Lang.PT to "Perfil", Lang.RU to "Профиль"),
    "Good morning." to mapOf(Lang.JA to "おはよう。", Lang.ZH to "早上好。", Lang.KO to "좋은 아침.", Lang.ES to "Buenos días.", Lang.FR to "Bonjour.", Lang.DE to "Guten Morgen.", Lang.PT to "Bom dia.", Lang.RU to "Доброе утро."),
    "Good afternoon." to mapOf(Lang.JA to "こんにちは。", Lang.ZH to "下午好。", Lang.KO to "좋은 오후.", Lang.ES to "Buenas tardes.", Lang.FR to "Bon après-midi.", Lang.DE to "Guten Tag.", Lang.PT to "Boa tarde.", Lang.RU to "Добрый день."),
    "Good evening." to mapOf(Lang.JA to "こんばんは。", Lang.ZH to "晚上好。", Lang.KO to "좋은 저녁.", Lang.ES to "Buenas noches.", Lang.FR to "Bonsoir.", Lang.DE to "Guten Abend.", Lang.PT to "Boa noite.", Lang.RU to "Добрый вечер."),
    "Where are we headed today?" to mapOf(Lang.JA to "今日はどこへ行く？", Lang.ZH to "今天去哪儿？", Lang.KO to "오늘은 어디로 갈까?", Lang.ES to "¿A dónde vamos hoy?", Lang.FR to "Où allons-nous aujourd'hui ?", Lang.DE to "Wohin geht es heute?", Lang.PT to "Para onde vamos hoje?", Lang.RU to "Куда отправимся сегодня?"),
    "Where are we headed tonight?" to mapOf(Lang.JA to "今夜はどこへ行く？", Lang.ZH to "今晚去哪儿？", Lang.KO to "오늘 밤은 어디로 갈까?", Lang.ES to "¿A dónde vamos esta noche?", Lang.FR to "Où allons-nous ce soir ?", Lang.DE to "Wohin geht es heute Abend?", Lang.PT to "Para onde vamos hoje à noite?", Lang.RU to "Куда отправимся сегодня вечером?"),
    "Search" to mapOf(Lang.JA to "検索", Lang.ZH to "搜索", Lang.KO to "검색", Lang.ES to "Buscar", Lang.FR to "Rechercher", Lang.DE to "Suchen", Lang.PT to "Pesquisar", Lang.RU to "Поиск"),
    "Menu" to mapOf(Lang.JA to "メニュー", Lang.ZH to "菜单", Lang.KO to "메뉴", Lang.ES to "Menú", Lang.FR to "Menu", Lang.DE to "Menü", Lang.PT to "Menu", Lang.RU to "Меню"),
    "Home" to mapOf(Lang.JA to "ホーム", Lang.ZH to "主页", Lang.KO to "홈", Lang.ES to "Inicio", Lang.FR to "Accueil", Lang.DE to "Startseite", Lang.PT to "Início", Lang.RU to "Главная"),
    "Back" to mapOf(Lang.JA to "戻る", Lang.ZH to "后退", Lang.KO to "뒤로", Lang.ES to "Atrás", Lang.FR to "Retour", Lang.DE to "Zurück", Lang.PT to "Voltar", Lang.RU to "Назад"),
    "Forward" to mapOf(Lang.JA to "進む", Lang.ZH to "前进", Lang.KO to "앞으로", Lang.ES to "Adelante", Lang.FR to "Avancer", Lang.DE to "Vorwärts", Lang.PT to "Avançar", Lang.RU to "Вперёд"),
    "Reload" to mapOf(Lang.JA to "再読み込み", Lang.ZH to "重新加载", Lang.KO to "새로고침", Lang.ES to "Recargar", Lang.FR to "Actualiser", Lang.DE to "Neu laden", Lang.PT to "Recarregar", Lang.RU to "Обновить"),
    "Open app" to mapOf(Lang.JA to "アプリで開く", Lang.ZH to "打开应用", Lang.KO to "앱에서 열기", Lang.ES to "Abrir app", Lang.FR to "Ouvrir l'appli", Lang.DE to "App öffnen", Lang.PT to "Abrir app", Lang.RU to "Открыть приложение"),
    "Continue in browser" to mapOf(Lang.JA to "ブラウザで続ける", Lang.ZH to "在浏览器中继续", Lang.KO to "브라우저에서 계속", Lang.ES to "Continuar en el navegador", Lang.FR to "Continuer dans le navigateur", Lang.DE to "Im Browser fortfahren", Lang.PT to "Continuar no navegador", Lang.RU to "Продолжить в браузере"),
)

/** Pick the string for the active language. Keeps call sites short: tx(lang, "Hide", "Sembunyikan"). */
fun tx(lang: Lang, en: String, id: String): String = when (lang) {
    Lang.ID -> id
    Lang.EN -> en
    else -> L10N[en]?.get(lang) ?: en
}

enum class ThemeId(val id: String, val label: String) {
    MIDNIGHT("midnight", "Midnight"),
    SAKURA("sakura", "Sakura"),
    LIQUID("liquid", "Liquid Glass");

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
    SearchEngine("bing", "Bing", "Bi", "bing.com", "https://www.bing.com/search?q="),
    SearchEngine("yahoo", "Yahoo", "Y", "search.yahoo.com", "https://search.yahoo.com/search?p="),
    SearchEngine("ecosia", "Ecosia", "E", "ecosia.org", "https://www.ecosia.org/search?q="),
    SearchEngine("startpage", "Startpage", "SP", "startpage.com", "https://www.startpage.com/sp/search?query="),
)

fun engineFrom(id: String?) = ENGINES.firstOrNull { it.id == id } ?: ENGINES.first()

enum class Overlay {
    NONE, TABS, SESSIONS, SETTINGS, MENU, SHORTCUT, SESSION_NAME,
    HISTORY, DOWNLOADS, EXTENSIONS, PROFILE, SITE_SETTINGS
}

/** Detail level of the fullscreen orb's black-hole / laser effects (see Immersive.kt). */
enum class OrbDetail(val id: String, val label: String) {
    POWERSAVE("powersave", "Power save · 30fps"),
    BALANCE("balance", "Balance · 60fps"),
    MAX("max", "Max");

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: BALANCE
    }
}

/** How files are downloaded: straight into the Downloads folder, or handed to an external downloader (1DM, ADM, ...). */
enum class DownloadMode(val id: String, val label: String) {
    INTERNAL("internal", "Downloads folder"),
    EXTERNAL("external", "External downloader");

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: INTERNAL
    }
}

/** Global animation dial: OFF kills motion, DEFAULT is balanced, FULL is maximum flair. */
enum class AnimLevel(val id: String, val label: String) {
    OFF("off", "Off"),
    DEFAULT("default", "Default"),
    FULL("full", "Full");

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Typography used by the local profile card/name. */
enum class ProfileFont(val id: String, val label: String) {
    NUNITO("nunito", "Nunito"),
    FREDOKA("fredoka", "Fredoka"),
    SERIF("serif", "Serif"),
    MONO("mono", "Mono");

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: NUNITO
    }
}

/* ---------- Settings ---------- */

/** The remote AI service used only for Denia's free-form chat. */
enum class AiProvider(val id: String) {
    OPENROUTER("openrouter"),
    OPENAI_COMPATIBLE("openai_compatible");

    companion object {
        // The retired "gemini" provider id migrates to OpenRouter on load.
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: OPENROUTER
    }
}

/** Where to create an OpenRouter key (linked from Settings). */
const val OPENROUTER_KEY_URL = "https://openrouter.ai/keys"

/**
 * Default OpenRouter model for Denia's free-form chat. This is the single source of truth;
 * change it here, not in call sites. Pick any model id from https://openrouter.ai/models.
 */
const val OPENROUTER_DEFAULT_MODEL = "openai/gpt-4o-mini"

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
    /** OpenRouter key (openrouter.ai/keys). When set, Denia answers free-form questions through OpenRouter. */
    val openRouterKey: String = "",
    /** OpenRouter model id; falls back to [OPENROUTER_DEFAULT_MODEL] when blank. */
    val openRouterModel: String = "",
    /** Which remote AI API Denia uses when a local command does not match. */
    val aiProvider: AiProvider = AiProvider.OPENROUTER,
    /** Root URL of an OpenAI-compatible API, normally ending in /v1. */
    val openAiBaseUrl: String = "",
    /** Optional for local OpenAI-compatible servers; cloud providers normally require it. */
    val openAiApiKey: String = "",
    /** Model identifier accepted by the configured OpenAI-compatible API. */
    val openAiModel: String = "",
    /** Fullscreen orb animation detail: 30fps powersave / 60fps balance / max (device refresh rate). */
    val orbDetail: OrbDetail = OrbDetail.BALANCE,
    /** Block ad-serving hosts on top of trackers. */
    val adBlock: Boolean = false,
    /** Where downloads go: the public Downloads folder or an external downloader app. */
    val downloadMode: DownloadMode = DownloadMode.INTERNAL,
    /** Global animation budget for every transition in the app. */
    val animLevel: AnimLevel = AnimLevel.DEFAULT,
    /** Accessibility: page text scaling, applied as WebView textZoom (percent). */
    val textZoom: Int = 100,
    /** System notifications (download progress, etc.). */
    val notifications: Boolean = true,
    /** Ask before leaving the browser for a native app (YouTube, Discord, ...). */
    val appLinkPrompt: Boolean = true,
    /** Shown on the profile card on the home screen. */
    val profileName: String = "Multex User",
    val profileEmoji: String = "🦊",
    /** Private app-file path to the user's cropped avatar. */
    val profileImagePath: String = "",
    /** Font used for the profile display name. */
    val profileFont: ProfileFont = ProfileFont.NUNITO,
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
        openRouterKey = prefs.getString("openRouterKey", "") ?: "",
        openRouterModel = prefs.getString("openRouterModel", "") ?: "",
        aiProvider = AiProvider.from(prefs.getString("aiProvider", null)),
        openAiBaseUrl = prefs.getString("openAiBaseUrl", "") ?: "",
        openAiApiKey = prefs.getString("openAiApiKey", "") ?: "",
        openAiModel = prefs.getString("openAiModel", "") ?: "",
        orbDetail = OrbDetail.from(prefs.getString("orbDetail", null)),
        adBlock = prefs.getBoolean("adBlock", false),
        downloadMode = DownloadMode.from(prefs.getString("downloadMode", null)),
        animLevel = AnimLevel.from(prefs.getString("animLevel", null)),
        textZoom = prefs.getInt("textZoom", 100),
        notifications = prefs.getBoolean("notifications", true),
        appLinkPrompt = prefs.getBoolean("appLinkPrompt", true),
        profileName = prefs.getString("profileName", "Multex User") ?: "Multex User",
        profileEmoji = prefs.getString("profileEmoji", "🦊") ?: "🦊",
        profileImagePath = prefs.getString("profileImagePath", "") ?: "",
        profileFont = ProfileFont.from(prefs.getString("profileFont", null)),
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
            .putString("openRouterKey", s.openRouterKey)
            .putString("openRouterModel", s.openRouterModel)
            .putString("aiProvider", s.aiProvider.id)
            .putString("openAiBaseUrl", s.openAiBaseUrl)
            .putString("openAiApiKey", s.openAiApiKey)
            .putString("openAiModel", s.openAiModel)
            .putString("orbDetail", s.orbDetail.id)
            .putBoolean("adBlock", s.adBlock)
            .putString("downloadMode", s.downloadMode.id)
            .putString("animLevel", s.animLevel.id)
            .putInt("textZoom", s.textZoom)
            .putBoolean("notifications", s.notifications)
            .putBoolean("appLinkPrompt", s.appLinkPrompt)
            .putString("profileName", s.profileName)
            .putString("profileEmoji", s.profileEmoji)
            .putString("profileImagePath", s.profileImagePath)
            .putString("profileFont", s.profileFont.id)
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
    /** Incognito tabs are never written to history and their cache is wiped on close. */
    val incognito: Boolean = false,
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

fun makeTab(url: String, title: String? = null, incognito: Boolean = false): Tab {
    val host = hostOf(url)
    return Tab(
        id = uid(),
        kind = TabKind.PAGE,
        title = title ?: titleFor(host),
        host = host,
        url = url,
        tint = tintFor(host),
        progress = 0,
        incognito = incognito,
    )
}

fun makeHomeTab(incognito: Boolean = false): Tab = Tab(uid(), TabKind.HOME, "New tab", "", "", HOME_TINT, incognito = incognito)

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

/** Ad-serving hosts blocked when "Ad blocker" is on (separate toggle from tracker blocking). */
val AD_HOSTS = listOf(
    "ads.google.com", "pagead2.googlesyndication.com", "adservice.google.co.id", "ads.yahoo.com",
    "advertising.com", "adcolony.com", "admob.com", "ads.yieldmo.com", "adform.net",
    "adroll.com", "adsrvr.org", "amazon-adsystem.com", "applovin.com", "appnexus.com",
    "bidr.io", "bidswitch.net", "casalemedia.com", "contextweb.com", "exosrv.com",
    "flashtalking.com", "gumgum.com", "inmobi.com", "indexww.com", "ironsrc.com",
    "lijit.com", "media.net", "mgid.com", "outbrainimg.com", "popads.net", "popcash.net",
    "propellerads.com", "revcontent.com", "rtk.io", "sharethrough.com", "smartadserver.com",
    "spotxchange.com", "taboola-cdn.com", "teads.tv", "triplelift.com", "unityads.unity3d.com",
    "vungle.com", "yieldlab.net", "yandex.ru/ads", "zedo.com", "zergnet.com", "adcash.com",
)

fun isAdHost(host: String?): Boolean {
    if (host.isNullOrEmpty()) return false
    val h = host.lowercase()
    return AD_HOSTS.any { h == it || h.endsWith(".$it") }
}
