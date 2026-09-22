package com.multex.browser

/*
 * Port of components/browser/browser-shell.tsx.
 *
 * Layering matches the web build's z-order:
 *   content + dock  <  sheets (z-50)  <  Denia (z-60)  <  chat bar (z-65)  <  fullscreen orb
 * Page content starts strictly BELOW the address bar (no more bleed-through above it);
 * the bottom dock still floats over the page like mobile Safari.
 *
 * Fullscreen (immersive) runs through the FsState machine (see Immersive.kt):
 * the real chrome is hidden the moment ENTERING starts while exact ghost copies fly
 * into the orb; the Android system bars are hidden only AFTER the absorption ends,
 * and restored BEFORE the release animation plays on the way out.
 */

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.multex.browser.denia.DeniaChatBar
import com.multex.browser.denia.DeniaCompanion

/** Applies the saved theme to [Palette] before any child reads it, then draws the browser. */
@Composable
fun MultexRoot(model: BrowserModel) {
    val theme = model.settings.theme
    remember(theme) {
        Palette.apply(theme)
        theme
    }
    MultexTheme { BrowserScreen(model) }
}

@Composable
fun BrowserScreen(model: BrowserModel) {
    val settings = model.settings
    val lang = settings.language
    val active = model.active
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val fs = model.fsState
    // Fullscreen can also be entered from Home; in that case only the dock is absorbed.
    val fullscreen = fs != FsState.NORMAL
    val immersiveTab = if (active.kind == TabKind.PAGE) {
        active
    } else {
        Tab(
            id = "immersive-home",
            kind = TabKind.PAGE,
            title = "Multex",
            host = "Home",
            url = "",
            tint = Palette.Pink,
        )
    }
    // The real chrome only exists in NORMAL and EXITING (where it is the animation target).
    val chromeVisible = fs == FsState.NORMAL || fs == FsState.EXITING

    // ---- System bars: hidden for the whole fullscreen session, restored on the way out ----
    val view = LocalView.current
    DisposableEffect(fs) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (controller != null) {
            when (fs) {
                // Wait for the absorption to finish before hiding: no mid-animation pop.
                FsState.ORB, FsState.MENU_OPEN -> {
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                }
                // Show the bars first, THEN the chrome flies back out of the orb.
                FsState.EXITING -> controller.show(WindowInsetsCompat.Type.systemBars())
                else -> Unit
            }
        }
        onDispose { }
    }

    // Last registered = highest priority.
    BackHandler(enabled = active.kind == TabKind.PAGE && fs == FsState.NORMAL) { model.back() }
    BackHandler(enabled = fs == FsState.ORB || fs == FsState.MENU_OPEN) { model.exitImmersive() }
    BackHandler(enabled = model.shortcutEditMode && active.kind == TabKind.HOME) { model.shortcutEditMode = false }
    BackHandler(enabled = model.findBarOpen) { model.closeFindBar() }
    BackHandler(enabled = model.appLinkPrompt != null) { model.resolveAppLink(false) }
    BackHandler(enabled = model.permissionPrompt != null) { model.answerPermission(false) }
    BackHandler(enabled = model.geoPrompt != null) { model.answerGeo(false) }
    BackHandler(enabled = model.directMode) { model.directMode = false }
    BackHandler(enabled = model.overlay != Overlay.NONE) { model.overlay = Overlay.NONE }
    BackHandler(enabled = model.chatOpen) { model.closeChatAnimated() }

    Box(Modifier.fillMaxSize().screenBackground()) {

        // ---- content + browser chrome ----
        Box(Modifier.fillMaxSize().imePadding()) {
            Column(Modifier.fillMaxSize()) {
                // The address bar owns the top of the layout, so page content can no longer
                // show through or above it. It vanishes the instant ENTERING begins; the
                // ghost copy inside ImmersiveOrb carries the absorption animation instead.
                if (active.kind == TabKind.PAGE && chromeVisible) {
                    AddressBar(
                        tab = active,
                        blockTrackers = settings.blockTrackers,
                        lang = lang,
                        onHome = model::goHome,
                        onSubmit = model::navigate,
                        onReloadOrStop = model::reloadOrStop,
                        modifier = Modifier.statusBarsPadding(),
                    )
                }

                // Page / home content: on page tabs this is everything below the address bar.
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    key(active.id, active.kind) {
                        val tabEntrance = remember { Animatable(0f) }
                        LaunchedEffect(Unit) {
                            tabEntrance.snapTo(if (Motion.level == AnimLevel.OFF) 1f else 0f)
                            tabEntrance.animateTo(
                                1f,
                                tween(
                                    Motion.ms(260),
                                    easing = FastOutSlowInEasing,
                                ),
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = tabEntrance.value
                                    translationX = with(density) { (1f - tabEntrance.value) * 14.dp.toPx() }
                                },
                        ) {
                        if (active.kind == TabKind.HOME) {
                            HomeScreen(
                                lang = lang,
                                tabs = model.tabs,
                                engine = settings.searchEngine,
                                shortcuts = model.shortcuts,
                                shortcutEditMode = model.shortcutEditMode,
                                onToggleShortcutEdit = { model.shortcutEditMode = !model.shortcutEditMode },
                                onEditShortcut = model::openShortcutEditor,
                                onAddShortcut = { model.openShortcutEditor(-1) },
                                onDeleteShortcut = model::deleteShortcut,
                                onResetShortcuts = model::resetShortcuts,
                                onNavigate = model::navigate,
                                onSwitchTab = model::select,
                                onOpenSettings = { model.openOverlay(Overlay.SETTINGS) },
                                onOpenProfile = { model.openOverlay(Overlay.PROFILE) },
                                profileEmoji = settings.profileEmoji,
                                profileImagePath = settings.profileImagePath,
                                onDirect = model::startDirect,
                                modifier = Modifier.fillMaxSize().systemBarsPadding(),
                            )
                        } else {
                            AndroidView(
                                factory = { model.webViewFor(active.id) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        }
                    }
                }
            }

            // Bottom dock: floats over the page, absorbed into the orb in fullscreen mode.
            if (!imeVisible && chromeVisible) {
                BottomDock(
                    lang = lang,
                    canBack = active.kind == TabKind.PAGE,
                    canForward = active.canGoForward,
                    tabCount = model.tabs.size,
                    onBack = model::back,
                    onForward = model::forward,
                    onNewTab = model::newTab,
                    onTabs = { model.openOverlay(Overlay.TABS) },
                    onMenu = { model.openOverlay(Overlay.MENU) },
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                )
            }
        }

        // ---- sheets (full screen, so the scrim also covers the system bars) ----
        when (model.overlay) {
            Overlay.NONE -> Unit
            Overlay.HISTORY -> HistoryOverlay(
                lang = lang,
                entries = remember(model.historyRevision) { model.history() },
                onOpen = { url -> model.openInNewTab(url) },
                onRemove = model::removeHistoryEntry,
                onClear = model::clearHistory,
                onClose = { model.overlay = Overlay.NONE },
            )
            Overlay.DOWNLOADS -> DownloadsOverlay(
                lang = lang,
                items = remember(model.downloadRevision) { model.downloads() },
                onRedownload = { url -> model.startDownload(url, null, null) },
                onRemove = model::removeDownload,
                onClear = model::clearDownloads,
                onClose = { model.overlay = Overlay.NONE },
            )
            Overlay.EXTENSIONS -> ExtensionsOverlay(
                lang = lang,
                scripts = model.extensionStore.all(),
                onToggle = model.extensionStore::toggle,
                onRemove = model.extensionStore::remove,
                onAdd = model.extensionStore::add,
                onClose = { model.overlay = Overlay.NONE },
            )
            Overlay.SITE_SETTINGS -> SiteSettingsOverlay(
                lang = lang,
                rules = model.sitePrefs.all(),
                currentHost = if (active.kind == TabKind.PAGE) active.host else null,
                onSet = model.sitePrefs::set,
                onRemove = model.sitePrefs::remove,
                onClose = { model.overlay = Overlay.NONE },
            )
            Overlay.PROFILE -> ProfileOverlay(
                lang = lang,
                settings = settings,
                tabCount = model.tabs.size,
                sessionCount = model.sessions.size,
                historyCount = model.history().size,
                onChange = model::updateSettings,
                onChooseImage = model::chooseProfileImage,
                onRemoveImage = model::removeProfileImage,
                onOpenSettings = { model.openOverlay(Overlay.SETTINGS) },
                onClearCookies = model::clearActiveCookies,
                onClose = { model.overlay = Overlay.NONE },
            )
            Overlay.TABS -> TabsOverlay(
                lang = lang,
                tabs = model.tabs,
                activeId = model.activeId,
                onSelect = model::select,
                onCloseTab = model::closeTab,
                onNewTab = model::newTab,
                onSaveSession = model::saveSession,
                onClose = { model.overlay = Overlay.NONE },
            )
            Overlay.SESSIONS -> {
                // The active session's stored tabs are only refreshed when parked, so show the live ones.
                val shown = model.sessions.map { if (it.id == model.activeSessionId) it.copy(tabs = model.tabs.toList()) else it }
                SessionsOverlay(
                    lang = lang,
                    sessions = shown,
                    activeId = model.activeSessionId,
                    isolationAvailable = model.isolationAvailable,
                    onOpen = model::openSession,
                    onRename = model::openSessionRename,
                    onDelete = model::deleteSession,
                    onNewSession = model::newSession,
                    onSave = model::saveSession,
                    onClose = { model.overlay = Overlay.NONE },
                )
            }
            Overlay.SESSION_NAME -> {
                val session = model.sessions.firstOrNull { it.id == model.editingSessionId }
                if (session != null) {
                    SessionNameOverlay(
                        lang = lang,
                        initialName = session.name,
                        onSave = { name -> model.renameSession(session.id, name) },
                        onClose = model::cancelSessionRename,
                    )
                }
            }
            Overlay.SETTINGS -> SettingsOverlay(
                lang = lang,
                settings = settings,
                activeSessionName = model.activeSession.name,
                onChange = model::updateSettings,
                onDirect = model::startDirect,
                onClearCookies = model::clearActiveCookies,
                onClose = { model.overlay = Overlay.NONE },
            )
            Overlay.SHORTCUT -> {
                val index = model.editingShortcut
                val initial = model.shortcuts.getOrNull(index)
                ShortcutEditorOverlay(
                    lang = lang,
                    initial = initial,
                    onSave = { t, a, c -> model.saveShortcut(index, t, a, c) },
                    onDelete = if (initial != null) ({ model.deleteShortcut(index) }) else null,
                    onClose = { model.overlay = Overlay.NONE },
                )
            }
            Overlay.MENU -> MenuOverlay(
                lang = lang,
                companionEnabled = settings.companionEnabled,
                immersive = fs != FsState.NORMAL,
                desktopSite = settings.desktopSite,
                onChat = model::openChat,
                onSessions = { model.openOverlay(Overlay.SESSIONS) },
                onSaveSession = model::saveSession,
                onDirect = model::startDirect,
                onToggleCompanion = {
                    model.updateSettings { it.copy(companionEnabled = !it.companionEnabled) }
                    model.overlay = Overlay.NONE
                },
                onToggleImmersive = model::toggleImmersive,
                onToggleDesktop = {
                    model.updateSettings { it.copy(desktopSite = !it.desktopSite) }
                    model.overlay = Overlay.NONE
                },
                onSettings = { model.openOverlay(Overlay.SETTINGS) },
                onHistory = { model.openOverlay(Overlay.HISTORY) },
                onDownloads = { model.openOverlay(Overlay.DOWNLOADS) },
                onExtensions = { model.openOverlay(Overlay.EXTENSIONS) },
                onSiteSettings = { model.openOverlay(Overlay.SITE_SETTINGS) },
                onProfile = { model.openOverlay(Overlay.PROFILE) },
                onFindInPage = model::openFindBar,
                onTranslate = model::translatePage,
                onPip = model::enterPip,
                onIncognito = model::newIncognitoTab,
                onClose = { model.overlay = Overlay.NONE },
            )
        }

        // ---- feature-pack dialogs (top of the sheets, below Denia) ----
        model.appLinkPrompt?.let { (url, app) ->
            AppLinkSheet(lang = lang, url = url, appName = app, onPick = model::resolveAppLink)
        }
        model.permissionPrompt?.let { p ->
            PermissionSheet(lang = lang, host = p.host, resources = p.resources, onAnswer = model::answerPermission)
        }
        model.geoPrompt?.let { (origin, _) ->
            GeoSheet(lang = lang, origin = origin, onAnswer = model::answerGeo)
        }

        // ---- find-in-page bar: floats just above the bottom dock ----
        if (model.findBarOpen && active.kind == TabKind.PAGE && fs == FsState.NORMAL) {
            Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp)) {
                FindBar(
                    lang = lang,
                    info = model.findInfo,
                    onQuery = model::findInPage,
                    onNext = { model.findNext(true) },
                    onPrev = { model.findNext(false) },
                    onClose = model::closeFindBar,
                )
            }
        }

        // ---- Denia + chat: above the sheets, like z-60 / z-65 in the web build ----
        // systemBarsPadding keeps Denia and her chat clear of the status bar and the
        // navigation bar (and of the floating bottom dock), imePadding lifts the input
        // above the keyboard whenever the user explicitly focuses it.
        Box(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            if (settings.companionEnabled) {
                DeniaCompanion(
                    height = settings.companionSize.height,
                    petRes = settings.pet.res,
                    chatty = settings.chatty,
                    cue = model.cue,
                    scrollSignal = model.scrollTick,
                    directMode = model.directMode,
                    onDirectModeChange = { model.directMode = it },
                    lang = lang,
                    onAnchorChange = { model.deniaAnchor = it },
                    onTap = model::openChat,
                )
            }
            if (model.chatOpen) {
                DeniaChatBar(
                    lang = lang,
                    engine = settings.searchEngine,
                    aiProvider = settings.aiProvider,
                    openRouterKey = settings.openRouterKey,
                    openRouterModel = settings.openRouterModel,
                    openAiBaseUrl = settings.openAiBaseUrl,
                    openAiApiKey = settings.openAiApiKey,
                    openAiModel = settings.openAiModel,
                    context = model.chatContext,
                    anchor = model.deniaAnchor,
                    closeSignal = model.chatCloseSignal,
                    onReply = model::handleDeniaReply,
                    onOpenUrl = { target -> model.openInNewTab(target.url, target.label) },
                    onClose = { model.chatOpen = false },
                    modifier = Modifier.align(Alignment.BottomCenter),
                    bottomPadding = if (imeVisible) 8.dp else 96.dp,
                )
            }
        }

        // ---- fullscreen orb: floats above everything ----
        if (fullscreen) {
            Box(Modifier.fillMaxSize().imePadding()) {
                ImmersiveOrb(
                    lang = lang,
                    tab = immersiveTab,
                    showAddress = active.kind == TabKind.PAGE,
                    blockTrackers = settings.blockTrackers,
                    canForward = active.kind == TabKind.PAGE && active.canGoForward,
                    tabCount = model.tabs.size,
                    fs = fs,
                    detail = settings.orbDetail,
                    onAbsorbed = { model.applyFsState(FsState.ORB) },
                    onRestored = { model.applyFsState(FsState.NORMAL) },
                    onMenuOpenChange = { open ->
                        if (open) model.applyFsState(FsState.MENU_OPEN) else model.applyFsState(FsState.ORB)
                    },
                    onBack = model::back,
                    onForward = model::forward,
                    onSubmit = model::navigate,
                    onReloadOrStop = model::reloadOrStop,
                    onTabs = { model.openOverlay(Overlay.TABS) },
                    onExit = model::exitImmersive,
                )
            }
        }
    }
}
