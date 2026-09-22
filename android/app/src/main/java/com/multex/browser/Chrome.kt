package com.multex.browser

/*
 * Ports of components/browser/chrome.tsx (AddressBar, BottomBar) and home-screen.tsx.
 * Sizes, radii, gaps and font sizes follow the Tailwind classes of the web build.
 */

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

/* ---------------------------------------------------------------------------------------------
 * Address pill (only shown on page tabs)
 * ------------------------------------------------------------------------------------------- */

@Composable
fun AddressBar(
    tab: Tab,
    blockTrackers: Boolean,
    lang: Lang,
    onHome: () -> Unit,
    onSubmit: (String) -> Unit,
    onReloadOrStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val loading = tab.progress < 100
    val animatedProgress by animateFloatAsState(
        targetValue = tab.progress / 100f,
        animationSpec = tween(180),
        label = "page-progress",
    )

    LaunchedEffect(editing) {
        if (editing) runCatching { focus.requestFocus() }
    }

    Column(modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().liquidGlass(999.dp).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoundIconButton(
                Icons.Filled.Home,
                contentDescription = tx(lang, "Go home", "Ke beranda"),
                onClick = onHome,
            )
            Icon(
                Icons.Filled.Lock,
                contentDescription = tx(lang, "Secure connection", "Koneksi aman"),
                tint = Palette.InkMuted,
                modifier = Modifier.size(14.dp),
            )
            Box(Modifier.weight(1f)) {
                if (editing) {
                    BasicTextField(
                        value = field,
                        onValueChange = { field = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Palette.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(Palette.Pink),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            onSubmit(field.text)
                            editing = false
                            focusManager.clearFocus()
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .onFocusChanged { state ->
                                // The first callback (before focus lands) must not close the editor.
                                if (state.isFocused) {
                                    hadFocus = true
                                } else if (hadFocus) {
                                    hadFocus = false
                                    editing = false
                                }
                            },
                    )
                } else {
                    Text(
                        tab.host,
                        color = Palette.Ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .press {
                                field = TextFieldValue(tab.url, TextRange(0, tab.url.length))
                                hadFocus = false
                                editing = true
                            },
                    )
                }
            }
            if (blockTrackers) {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(Palette.Sky.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = Palette.Sky, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${tab.blocked}", color = Palette.Sky, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            RoundIconButton(
                if (loading) Icons.Filled.Close else Icons.Filled.Refresh,
                contentDescription = if (loading) tx(lang, "Stop", "Berhenti") else tx(lang, "Reload", "Muat ulang"),
                onClick = onReloadOrStop,
            )
        }
        if (loading) {
            Box(
                Modifier
                    .padding(start = 24.dp, end = 24.dp, top = 4.dp)
                    .fillMaxWidth(animatedProgress.coerceIn(0.03f, 1f))
                    .height(2.dp)
                    .background(Palette.Pink, CircleShape),
            )
        }
    }
}

/* ---------------------------------------------------------------------------------------------
 * Floating glass dock: back, forward, + (accent), tabs, menu
 * ------------------------------------------------------------------------------------------- */

@Composable
fun BottomDock(
    lang: Lang,
    canBack: Boolean,
    canForward: Boolean,
    tabCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onNewTab: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .liquidGlass(shape)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(
            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = tx(lang, "Back", "Kembali"),
            onClick = onBack,
            size = 44.dp,
            iconSize = 24.dp,
            tint = if (canBack) Palette.Ink else Palette.Ink.copy(alpha = 0.3f),
            enabled = canBack,
        )
        RoundIconButton(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = tx(lang, "Forward", "Maju"),
            onClick = onForward,
            size = 44.dp,
            iconSize = 24.dp,
            tint = if (canForward) Palette.Ink else Palette.Ink.copy(alpha = 0.3f),
            enabled = canForward,
        )
        Box(
            Modifier
                .size(48.dp)
                .accent(CircleShape)
                .press(onClick = onNewTab),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = tx(lang, "New tab", "Tab baru"),
                tint = Palette.OnAccent,
                modifier = Modifier.size(24.dp),
            )
        }
        Box(
            Modifier.size(44.dp).clip(CircleShape).press(onClick = onTabs),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(24.dp).border(2.dp, Palette.Ink, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("$tabCount", color = Palette.Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            Modifier.size(44.dp).clip(CircleShape).press(onClick = onMenu),
            contentAlignment = Alignment.Center,
        ) {
            // lucide "ellipsis": three dots in a row
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) { Box(Modifier.size(4.5.dp).background(Palette.Ink, CircleShape)) }
            }
        }
    }
}

/* ---------------------------------------------------------------------------------------------
 * Home screen
 * ------------------------------------------------------------------------------------------- */

@Composable
private fun SectionHeading(text: String) {
    Text(
        text.uppercase(),
        color = Palette.InkMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
fun HomeScreen(
    lang: Lang,
    tabs: List<Tab>,
    engine: SearchEngine,
    shortcuts: List<Shortcut>,
    shortcutEditMode: Boolean,
    onToggleShortcutEdit: () -> Unit,
    onEditShortcut: (Int) -> Unit,
    onAddShortcut: () -> Unit,
    onDeleteShortcut: (Int) -> Unit,
    onResetShortcuts: () -> Unit,
    onNavigate: (String) -> Unit,
    onSwitchTab: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    profileEmoji: String,
    profileImagePath: String,
    onDirect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val recent = tabs.filter { it.kind == TabKind.PAGE }.takeLast(6).reversed()
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when {
        hour < 11 -> tx(lang, "Good morning.", "Selamat pagi.")
        hour < 15 -> tx(lang, "Good afternoon.", "Selamat siang.")
        hour < 18 -> tx(lang, "Good evening.", "Selamat sore.")
        else -> tx(lang, "Good evening.", "Selamat malam.")
    }
    val subline = if (hour < 18) tx(lang, "Where are we headed today?", "Mau ke mana hari ini?")
    else tx(lang, "Where are we headed tonight?", "Mau ke mana malam ini?")

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 144.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        // header
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Multex", color = Palette.Ink, fontFamily = Fonts.Display, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    tx(lang, "with Denia", "bersama Denia"),
                    color = Palette.Pink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            ProfileAvatarButton(
                emoji = profileEmoji,
                imagePath = profileImagePath,
                contentDescription = tx(lang, "Open profile", "Buka profil"),
                onClick = onOpenProfile,
            )
        }

        // greeting
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                greeting,
                color = Palette.Ink,
                fontFamily = Fonts.Display,
                fontSize = 34.sp,
                lineHeight = 37.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(subline, color = Palette.InkMuted, fontSize = 16.sp)
        }

        // search
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().glass(16.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = Palette.InkMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        tx(lang, "Search ${engine.label} or type a URL", "Cari di ${engine.label} atau ketik URL"),
                        color = Palette.InkMuted,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Palette.Ink, fontSize = 15.sp),
                    cursorBrush = SolidColor(Palette.Pink),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        onNavigate(query)
                        query = ""
                    }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                engine.short,
                color = Palette.InkMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Palette.Ink.copy(alpha = 0.1f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        // shortcuts (tap to open, long-press or "Edit" to rearrange)
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tx(lang, "Shortcuts", "Pintasan").uppercase(),
                    color = Palette.InkMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.weight(1f),
                )
                if (shortcutEditMode) {
                    HeaderChip(tx(lang, "Reset", "Reset"), Palette.InkMuted, onResetShortcuts)
                    Spacer(Modifier.width(8.dp))
                    HeaderChip(tx(lang, "Done", "Selesai"), Palette.Pink, onToggleShortcutEdit)
                } else {
                    HeaderChip(tx(lang, "Edit", "Ubah"), Palette.Pink, onToggleShortcutEdit)
                }
            }
            val cells = shortcuts.size + if (shortcutEditMode) 1 else 0
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                (0 until cells).chunked(4).forEach { rowCells ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowCells.forEach { i ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                if (i < shortcuts.size) {
                                    ShortcutCell(
                                        s = shortcuts[i],
                                        index = i,
                                        editing = shortcutEditMode,
                                        onOpen = { shortcuts.getOrNull(i)?.let { onNavigate(it.host) } },
                                        onEdit = { onEditShortcut(i) },
                                        onLongPress = onToggleShortcutEdit,
                                        onDelete = { onDeleteShortcut(i) },
                                    )
                                } else {
                                    AddShortcutCell(lang, onAddShortcut)
                                }
                            }
                        }
                        repeat(4 - rowCells.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        // jump back in
        if (recent.isNotEmpty()) {
            Column {
                Box(Modifier.padding(horizontal = 20.dp)) { SectionHeading(tx(lang, "Jump back in", "Lanjutkan")) }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    recent.forEach { t ->
                        Row(
                            Modifier.width(160.dp).glass(16.dp).press { onSwitchTab(t.id) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            LetterTile(t.title, t.tint)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    t.title,
                                    color = Palette.Ink,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    t.host,
                                    color = Palette.InkMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        // meet Denia
        Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().glass(24.dp).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(
                    painterResource(R.drawable.face_happy),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(2.dp, Palette.Pink.copy(alpha = 0.7f), CircleShape),
                )
                Column {
                    Text(
                        tx(lang, "Meet Denia", "Kenalan sama Denia"),
                        color = Palette.Ink,
                        fontFamily = Fonts.Display,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(tx(lang, "Your browsing companion", "Teman browsing-mu"), color = Palette.InkMuted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Icons.Filled.TouchApp to tx(lang, "Tap Denia to hear what she has to say.", "Ketuk Denia buat dengar dia ngomong."),
                    Icons.Filled.OpenWith to tx(lang, "Drag her anywhere when she is in the way.", "Seret dia ke mana aja kalau menghalangi."),
                    Icons.Filled.GpsFixed to tx(lang, "Double-tap her, then tap a spot: she runs there.", "Ketuk dua kali, lalu ketuk satu titik: dia lari ke sana."),
                ).forEach { (icon, text) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Palette.Lavender.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(icon, contentDescription = null, tint = Palette.Lavender, modifier = Modifier.size(14.dp))
                        }
                        Text(text, color = Palette.Ink.copy(alpha = 0.9f), fontSize = 13.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .accent(RoundedCornerShape(16.dp))
                    .press(onClick = onDirect)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.GpsFixed, contentDescription = null, tint = Palette.OnAccent, modifier = Modifier.size(16.dp))
                Text(tx(lang, "Direct Denia", "Arahkan Denia"), color = Palette.OnAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/* ---------------------------------------------------------------------------------------------
 * Shortcut tiles
 * ------------------------------------------------------------------------------------------- */

@Composable
private fun HeaderChip(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .press(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/**
 * A shortcut icon: colored glossy tile with the site's real icon on a white badge.
 * While the icon loads (or if the site has none) the badge shows the first letter.
 */
@Composable
fun ShortcutIcon(title: String, address: String, tint: Color, size: Dp = 58.dp) {
    val host = remember(address) { normalizeHost(address) }
    val favicon = rememberFavicon(host)
    val shape = RoundedCornerShape(size * 0.31f)
    Box(
        Modifier
            .size(size)
            .shadow(10.dp, shape, ambientColor = tint, spotColor = tint)
            .clip(shape)
            .background(Brush.linearGradient(listOf(tint, lerp(tint, Color.White, 0.4f))))
            .border(1.dp, Color.White.copy(alpha = 0.45f), shape),
        contentAlignment = Alignment.Center,
    ) {
        // soft highlight on the top half, like glass
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.32f), 0.6f to Color.Transparent)),
        )
        val badge = size * 0.6f
        val badgeShape = RoundedCornerShape(badge * 0.32f)
        Box(
            Modifier.size(badge).shadow(3.dp, badgeShape).clip(badgeShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            if (favicon != null) {
                Image(bitmap = favicon, contentDescription = null, modifier = Modifier.size(badge * 0.66f))
            } else {
                Text(
                    title.take(1).uppercase(),
                    color = Palette.TileInk,
                    fontFamily = Fonts.Display,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.3f).sp,
                )
            }
        }
    }
}

@Composable
private fun ShortcutCell(
    s: Shortcut,
    index: Int,
    editing: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
) {
    val open by rememberUpdatedState(onOpen)
    val edit by rememberUpdatedState(onEdit)
    val longPress by rememberUpdatedState(onLongPress)

    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 500f),
        label = "tile",
    )
    // In edit mode the tiles wiggle a little, like app icons do.
    val angle: Float = if (editing) {
        val transition = rememberInfiniteTransition(label = "wiggle")
        val a by transition.animateFloat(
            initialValue = -2.2f,
            targetValue = 2.2f,
            animationSpec = infiniteRepeatable(tween(120 + (index % 4) * 25, easing = LinearEasing), RepeatMode.Reverse),
            label = "angle",
        )
        a
    } else {
        0f
    }

    Column(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = angle
            }
            .pointerInput(editing) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { if (editing) edit() else open() },
                    onLongPress = { if (!editing) longPress() },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            ShortcutIcon(s.title, s.host, s.tint)
            if (editing) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-6).dp, y = (-6).dp)
                        .size(22.dp)
                        .shadow(3.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color(0xFFFF5C7A))
                        .press(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Delete ${s.title}", tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
        Text(
            s.title,
            color = Palette.InkMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AddShortcutCell(lang: Lang, onClick: () -> Unit) {
    val ink = Palette.InkMuted
    Column(
        Modifier.press(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(58.dp)
                .drawBehind {
                    drawRoundRect(
                        color = ink.copy(alpha = 0.6f),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = tx(lang, "Add shortcut", "Tambah pintasan"), tint = ink, modifier = Modifier.size(24.dp))
        }
        Text(tx(lang, "Add", "Tambah"), color = ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Glass text field used by the shortcut editor. */
@Composable
fun GlassField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    Box(modifier.fillMaxWidth().glass(16.dp).padding(horizontal = 14.dp, vertical = 12.dp)) {
        if (value.isEmpty()) Text(placeholder, color = Palette.InkMuted, fontSize = 14.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Palette.Ink, fontSize = 14.sp),
            cursorBrush = SolidColor(Palette.Pink),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
