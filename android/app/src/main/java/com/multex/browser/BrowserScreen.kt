package com.multex.browser

/*
 * Port of components/browser/browser-shell.tsx.
 *
 * Layering matches the web build's z-order:
 *   content + dock  <  sheets (z-50)  <  Denia (z-60)  <  chat bar (z-65)  <  fullscreen orb
 * Page content starts strictly BELOW the address bar (no more bleed-through above it);
 * the bottom dock still floats over the page like mobile Safari.
 * In fullscreen mode both bars shrink/collapse into a single draggable orb (Immersive.kt).
 */

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    // Fullscreen only applies to real pages; the home screen keeps its normal chrome.
    val immersiveActive = model.immersiveMode && active.kind == TabKind.PAGE

    // Last registered = highest priority.
    BackHandler(enabled = active.kind == TabKind.PAGE) { model.back() }
    BackHandler(enabled = model.shortcutEditMode && active.kind == TabKind.HOME) { model.shortcutEditMode = false }
    BackHandler(enabled = model.directMode) { model.directMode = false }
    BackHandler(enabled = model.overlay != Overlay.NONE) { model.overlay = Overlay.NONE }
    BackHandler(enabled = model.chatOpen) { model.chatOpen = false }

    Box(Modifier.fillMaxSize().screenBackground()) {

        // ---- content + browser chrome ----
        Box(Modifier.fillMaxSize().imePadding()) {
            Column(Modifier.fillMaxSize()) {
                // The address bar owns the top of the layout, so page content can no longer
                // show through or above it. Entering fullscreen makes it shrink away (absorbed).
                AnimatedVisibility(
                    visible = active.kind == TabKind.PAGE && !immersiveActive,
                    enter = fadeIn(tween(180)) + slideInVertically(tween(260)) { -it / 2 },
                    exit = fadeOut(tween(210)) + scaleOut(tween(240), targetScale = 0.4f) +
                        slideOutVertically(tween(260)) { it / 2 },
                    modifier = Modifier.statusBarsPadding(),
                ) {
                    AddressBar(
                        tab = active,
                        blockTrackers = settings.blockTrackers,
                        lang = lang,
                        onHome = model::goHome,
                        onSubmit = model::navigate,
                        onReloadOrStop = model::reloadOrStop,
                    )
                }

                // Page / home content: on page tabs this is everything below the address bar.
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    key(active.id, active.kind) {
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

            // Bottom dock: floats over the page, and shrinks away in fullscreen mode.
            AnimatedVisibility(
                visible = !imeVisible && !immersiveActive,
                enter = fadeIn(tween(180)) + slideInVertically(tween(280)) { it / 2 },
                exit = fadeOut(tween(210)) + scaleOut(tween(240), targetScale = 0.4f) +
                    slideOutVertically(tween(260)) { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            ) {
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
                )
            }
        }

        // ---- sheets (full screen, so the scrim also covers the system bars) ----
        when (model.overlay) {
            Overlay.NONE -> Unit
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
                immersive = model.immersiveMode,
                onChat = model::openChat,
                onSessions = { model.openOverlay(Overlay.SESSIONS) },
                onSaveSession = model::saveSession,
                onDirect = model::startDirect,
                onToggleCompanion = {
                    model.updateSettings { it.copy(companionEnabled = !it.companionEnabled) }
                    model.overlay = Overlay.NONE
                },
                onToggleImmersive = model::toggleImmersive,
                onSettings = { model.openOverlay(Overlay.SETTINGS) },
                onClose = { model.overlay = Overlay.NONE },
            )
        }

        // ---- Denia + chat: above the sheets, like z-60 / z-65 in the web build ----
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
                    onTap = model::openChat,
                )
            }
            if (model.chatOpen) {
                DeniaChatBar(
                    lang = lang,
                    engine = settings.searchEngine,
                    aiProvider = settings.aiProvider,
                    geminiKey = settings.geminiKey,
                    openAiBaseUrl = settings.openAiBaseUrl,
                    openAiApiKey = settings.openAiApiKey,
                    openAiModel = settings.openAiModel,
                    context = model.chatContext,
                    onReply = model::handleDeniaReply,
                    onOpenUrl = { target -> model.openInNewTab(target.url, target.label) },
                    onClose = { model.chatOpen = false },
                    modifier = Modifier.align(Alignment.BottomCenter),
                    bottomPadding = if (imeVisible) 8.dp else 64.dp,
                )
            }
        }

        // ---- fullscreen orb: floats above everything ----
        if (immersiveActive) {
            Box(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                ImmersiveOrb(
                    lang = lang,
                    tab = active,
                    canForward = active.canGoForward,
                    tabCount = model.tabs.size,
                    onBack = model::back,
                    onForward = model::forward,
                    onSubmit = model::navigate,
                    onReloadOrStop = model::reloadOrStop,
                    onTabs = { model.openOverlay(Overlay.TABS) },
                    onExit = { model.immersiveMode = false },
                )
            }
        }
    }
}
