package com.multex.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.multex.browser.denia.DeniaAction
import com.multex.browser.denia.DeniaChatBar
import com.multex.browser.denia.DeniaCompanion
import com.multex.browser.denia.DeniaPalette
import com.multex.browser.denia.DeniaReply
import com.multex.browser.denia.Lang
import com.multex.browser.denia.Line
import com.multex.browser.denia.Mood
import com.multex.browser.denia.ThemeId
import com.multex.browser.denia.glass
import com.multex.browser.denia.tx
import kotlin.math.abs

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    externalUrl: String? = null,
    onExternalUrlConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var isHome by rememberSaveable { mutableStateOf(true) }
    var currentUrl by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(100) }
    var canGoBack by remember { mutableStateOf(false) }

    var cue by remember { mutableStateOf<Line?>(null) }
    var scrollSignal by remember { mutableIntStateOf(0) }
    var directMode by remember { mutableStateOf(false) }

    var showSettings by remember { mutableStateOf(false) }
    var showChat by rememberSaveable { mutableStateOf(false) }
    var showDenia by rememberSaveable { mutableStateOf(true) }
    var chatty by rememberSaveable { mutableStateOf(true) }
    var petId by rememberSaveable { mutableStateOf("bunny") }
    var sizeId by rememberSaveable { mutableStateOf("md") }
    var engineId by rememberSaveable { mutableStateOf("google") }
    var langId by rememberSaveable { mutableStateOf(Lang.EN.id) }
    var themeId by rememberSaveable { mutableStateOf(ThemeId.MIDNIGHT.id) }

    val lang = Lang.entries.firstOrNull { it.id == langId } ?: Lang.EN
    val theme = ThemeId.entries.firstOrNull { it.id == themeId } ?: ThemeId.MIDNIGHT
    LaunchedEffect(theme) { DeniaPalette.applyTheme(theme) }

    val engine = ENGINES.firstOrNull { it.id == engineId } ?: ENGINES.first()
    val pet = PETS.firstOrNull { it.id == petId } ?: PETS.first()
    val size = SIZES.firstOrNull { it.id == sizeId } ?: SIZES[1]

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.mediaPlaybackRequiresUserGesture = true
            setBackgroundColor(android.graphics.Color.TRANSPARENT)

            var lastScrollY = 0
            setOnScrollChangeListener { _, _, y, _, _ ->
                if (abs(y - lastScrollY) > 320) {
                    lastScrollY = y
                    scrollSignal++
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    currentUrl = url
                    address = url
                    canGoBack = view.canGoBack()
                    cue = Line(tx(lang, "Opening ${hostOf(url)}~", "Membuka ${hostOf(url)}~"))
                }

                override fun onPageFinished(view: WebView, url: String) {
                    canGoBack = view.canGoBack()
                    progress = 100
                }
            }
        }
    }

    DisposableEffect(webView) {
        onDispose { webView.destroy() }
    }

    fun open(input: String) {
        val url = resolveInput(input, engine)
        if (url.isEmpty()) return
        focusManager.clearFocus()
        isHome = false
        webView.loadUrl(url)
    }

    fun goHome() {
        focusManager.clearFocus()
        isHome = true
        address = ""
        if (chatty) cue = Line(tx(lang, "Back home~", "Kembali ke beranda~"), Mood.NEUTRAL)
    }

    fun openInNewTab(url: String) {
        webView.clearHistory()
        open(url)
    }

    fun handleDenia(reply: DeniaReply) {
        when (reply.action) {
            DeniaAction.HIDE_DENIA -> { showChat = false; showDenia = false }
            DeniaAction.SHOW_DENIA -> showDenia = true
            DeniaAction.NEW_TAB -> { webView.clearHistory(); goHome() }
            DeniaAction.OPEN_SETTINGS -> { showChat = false; showSettings = true }
            DeniaAction.DIRECT_MODE -> { showChat = false; showDenia = true; directMode = true }
            DeniaAction.THEME_SAKURA -> themeId = ThemeId.SAKURA.id
            DeniaAction.THEME_MIDNIGHT -> themeId = ThemeId.MIDNIGHT.id
            DeniaAction.GO_HOME -> goHome()
            DeniaAction.RELOAD -> if (!isHome) webView.reload()
            DeniaAction.GO_BACK -> if (webView.canGoBack()) webView.goBack() else goHome()
            DeniaAction.OPEN_URL, DeniaAction.NONE -> Unit
        }
        if (reply.action != DeniaAction.HIDE_DENIA) {
            showDenia = true
            cue = Line(reply.text, reply.mood)
        }
    }

    LaunchedEffect(externalUrl) {
        externalUrl?.let {
            open(it)
            onExternalUrlConsumed()
        }
    }

    LaunchedEffect(Unit) {
        if (!isHome && currentUrl.isNotEmpty() && webView.url == null) webView.loadUrl(currentUrl)
    }

    BackHandler(enabled = showChat) { showChat = false }
    BackHandler(enabled = directMode) { directMode = false }
    BackHandler(enabled = !directMode && !isHome) {
        if (webView.canGoBack()) webView.goBack() else goHome()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(DeniaPalette.Midnight)
            .systemBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())

                if (isHome) {
                    HomeScreen(lang = lang, onOpen = { open(it) }, modifier = Modifier.fillMaxSize())
                }

                if (!isHome && progress < 100) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                        color = DeniaPalette.Pink,
                        trackColor = Color.Transparent,
                    )
                }
            }

            Toolbar(
                lang = lang,
                address = address,
                onAddressChange = { address = it },
                isHome = isHome,
                canGoBack = canGoBack,
                loading = !isHome && progress < 100,
                onBack = { if (webView.canGoBack()) webView.goBack() else goHome() },
                onHome = { goHome() },
                onGo = { open(address) },
                onReload = { webView.reload() },
                onStop = { webView.stopLoading(); progress = 100 },
                onSettings = { showSettings = true },
                onChat = { showChat = !showChat },
                chatOpen = showChat,
            )
        }

        if (showDenia) {
            DeniaCompanion(
                height = size.height,
                petRes = pet.res,
                chatty = chatty,
                cue = cue,
                scrollSignal = scrollSignal,
                directMode = directMode,
                onDirectModeChange = { directMode = it },
                lang = lang,
                onOpenChat = { showChat = true },
            )
        }

        // Drawn after Denia so her sprite never covers the No / Yes buttons.
        if (showChat) {
            DeniaChatBar(
                lang = lang,
                engine = engine,
                onReply = { handleDenia(it) },
                onOpenUrl = { target ->
                    showChat = false
                    openInNewTab(target.url)
                },
                onClose = { showChat = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .padding(bottom = 64.dp),
            )
        }

        if (showSettings) {
            SettingsSheet(
                showDenia = showDenia,
                onShowDeniaChange = { showDenia = it },
                chatty = chatty,
                onChattyChange = { chatty = it },
                petId = petId,
                onPetChange = { petId = it },
                sizeId = sizeId,
                onSizeChange = { sizeId = it },
                engineId = engineId,
                onEngineChange = { engineId = it },
                lang = lang,
                onLangChange = { langId = it.id },
                theme = theme,
                onThemeChange = { themeId = it.id },
                onDismiss = { showSettings = false },
            )
        }
    }
}

@Composable
private fun Toolbar(
    lang: Lang,
    address: String,
    onAddressChange: (String) -> Unit,
    isHome: Boolean,
    canGoBack: Boolean,
    loading: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onGo: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onSettings: () -> Unit,
    onChat: () -> Unit,
    chatOpen: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(DeniaPalette.Midnight)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = canGoBack || !isHome) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = tx(lang, "Back", "Kembali"),
                tint = if (canGoBack || !isHome) DeniaPalette.Ink else DeniaPalette.InkMuted.copy(alpha = 0.4f),
            )
        }
        IconButton(onClick = onHome) {
            Icon(
                Icons.Filled.Home,
                contentDescription = tx(lang, "Home", "Beranda"),
                tint = if (isHome) DeniaPalette.Pink else DeniaPalette.Ink,
            )
        }

        Box(
            Modifier
                .weight(1f)
                .glass(999.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (address.isEmpty()) {
                Text(tx(lang, "Search or type a URL", "Cari atau ketik URL"), color = DeniaPalette.InkMuted, fontSize = 14.sp, maxLines = 1)
            }
            BasicTextField(
                value = address,
                onValueChange = onAddressChange,
                singleLine = true,
                textStyle = TextStyle(color = DeniaPalette.Ink, fontSize = 14.sp),
                cursorBrush = SolidColor(DeniaPalette.Pink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onGo() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        IconButton(onClick = if (loading) onStop else onReload, enabled = !isHome) {
            Icon(
                if (loading) Icons.Filled.Close else Icons.Filled.Refresh,
                contentDescription = if (loading) tx(lang, "Stop", "Berhenti") else tx(lang, "Reload", "Muat ulang"),
                tint = if (isHome) DeniaPalette.InkMuted.copy(alpha = 0.4f) else DeniaPalette.Ink,
            )
        }
        IconButton(onClick = onChat) {
            Icon(
                Icons.Filled.Face,
                contentDescription = tx(lang, "Talk to Denia", "Ngobrol sama Denia"),
                tint = if (chatOpen) DeniaPalette.Pink else DeniaPalette.Ink,
            )
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = tx(lang, "Settings", "Pengaturan"), tint = DeniaPalette.Ink)
        }
    }
}

@Composable
private fun HomeScreen(lang: Lang, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }

    Column(
        modifier
            .background(Brush.verticalGradient(listOf(DeniaPalette.MidnightSoft, DeniaPalette.Midnight)))
            // Empty pointer handler so taps on the home screen never fall through to the WebView underneath.
            .pointerInput(Unit) {}
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(56.dp))
        Text("Multex", color = DeniaPalette.Ink, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text(
            tx(lang, "Your glass browser, with Denia by your side.", "Browser kaca-mu, ditemani Denia."),
            color = DeniaPalette.InkMuted,
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(28.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .glass(999.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = DeniaPalette.InkMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(tx(lang, "Search or type a URL", "Cari atau ketik URL"), color = DeniaPalette.InkMuted, fontSize = 15.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = DeniaPalette.Ink, fontSize = 15.sp),
                    cursorBrush = SolidColor(DeniaPalette.Pink),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onOpen(query) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            tx(lang, "SHORTCUTS", "PINTASAN"),
            color = DeniaPalette.InkMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(SHORTCUTS, key = { it.host }) { shortcut ->
                Column(
                    Modifier.clickable { onOpen(shortcut.host) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .glass(18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            shortcut.title.first().toString(),
                            color = shortcut.tint,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        shortcut.title,
                        color = DeniaPalette.Ink,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
