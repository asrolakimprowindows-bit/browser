package com.multex.browser

/*
 * Ports of the Tabs / Sessions / Menu sheets from components/browser/overlays.tsx.
 * They are drawn by OverlaySheet (Primitives.kt): dim scrim + glass-strong bottom sheet.
 */

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ------------------------------------------------------------------ Tabs */

@Composable
fun BoxScope.TabsOverlay(
    lang: Lang,
    tabs: List<Tab>,
    activeId: String,
    onSelect: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onSaveSession: () -> Unit,
    onClose: () -> Unit,
) {
    OverlaySheet(
        title = tx(lang, "Tabs", "Tab"),
        subtitle = tx(lang, "${tabs.size} open", "${tabs.size} terbuka"),
        onClose = onClose,
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(tx(lang, "Save session", "Simpan sesi"), Icons.Filled.Bookmark) { onSaveSession() }
                PillButton(tx(lang, "New tab", "Tab baru"), Icons.Filled.Add, accent = true) { onNewTab() }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tabs.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { t ->
                        Box(Modifier.weight(1f)) {
                            TabCard(t, active = t.id == activeId, lang = lang, onSelect = { onSelect(t.id) }, onClose = { onCloseTab(t.id) })
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TabCard(tab: Tab, active: Boolean, lang: Lang, onSelect: () -> Unit, onClose: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val isHome = tab.kind == TabKind.HOME
    Box(
        Modifier
            .fillMaxWidth()
            .glass(shape)
            .then(if (active) Modifier.border(2.dp, Palette.Pink, shape) else Modifier)
            .press(onClick = onSelect),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(
                        if (isHome) Brush.linearGradient(listOf(Palette.Lavender, Palette.Pink))
                        else Brush.linearGradient(listOf(tab.tint, tab.tint.copy(alpha = 0.2f))),
                    ),
            )
            Row(
                Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LetterTile(if (isHome) "M" else tab.title, tab.tint, TileSize.SM)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isHome) tx(lang, "New tab", "Tab baru") else tab.title,
                        color = Palette.Ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        tab.host.ifEmpty { tx(lang, "Home", "Beranda") },
                        color = Palette.InkMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .press(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = tx(lang, "Close ${tab.title}", "Tutup ${tab.title}"),
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/* ------------------------------------------------------------------ Sessions */

@Composable
fun BoxScope.SessionsOverlay(
    lang: Lang,
    sessions: List<Session>,
    activeId: String,
    isolationAvailable: Boolean,
    onOpen: (Session) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNewSession: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    OverlaySheet(
        title = tx(lang, "Sessions", "Sesi"),
        subtitle = tx(
            lang,
            "Each session keeps its own tabs. A new session starts with clean cookies.",
            "Tiap sesi punya tab sendiri. Sesi baru mulai dengan cookie bersih.",
        ),
        onClose = onClose,
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(tx(lang, "New session", "Sesi baru"), Icons.Filled.Add) { onNewSession() }
                PillButton(tx(lang, "Save current tabs", "Simpan tab ini"), Icons.Filled.Bookmark, accent = true) { onSave() }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!isolationAvailable) {
                Text(
                    tx(
                        lang,
                        "This phone's System WebView is too old for separate cookies. Update \"Android System WebView\" to enable it.",
                        "System WebView di HP ini terlalu lama buat cookie terpisah. Update \"Android System WebView\" dulu.",
                    ),
                    color = Color(0xFFD98A00),
                    fontSize = 12.sp,
                )
            }
            sessions.forEach { s ->
                SessionCard(
                    s,
                    lang,
                    active = s.id == activeId,
                    onOpen = { onOpen(s) },
                    onRename = { onRename(s.id) },
                    onDelete = { onDelete(s.id) },
                )
            }
        }
    }
}

@Composable
private fun SessionCard(
    s: Session,
    lang: Lang,
    active: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().glass(16.dp).padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(s.name, color = Palette.Ink, fontFamily = Fonts.Display, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    tx(lang, "${s.tabs.size} tabs", "${s.tabs.size} tab") + " · " + relativeTime(lang, s.savedAt),
                    color = Palette.InkMuted,
                    fontSize = 12.sp,
                )
            }
            RoundIconButton(
                Icons.Filled.Edit,
                contentDescription = tx(lang, "Rename ${s.name}", "Ubah nama ${s.name}"),
                onClick = onRename,
            )
            if (!active) {
                RoundIconButton(
                    Icons.Filled.Delete,
                    contentDescription = tx(lang, "Delete ${s.name}", "Hapus ${s.name}"),
                    onClick = onDelete,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row {
                s.tabs.filter { it.kind == TabKind.PAGE }.take(5).forEachIndexed { i, t ->
                    LetterTile(
                        t.title,
                        t.tint,
                        modifier = Modifier
                            .offset(x = (-6 * i).dp)
                            .border(2.dp, Palette.GlassStrong, RoundedCornerShape(12.dp)),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            val tone = if (active) Palette.Sky else Palette.Pink
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(tone.copy(alpha = 0.2f))
                    .then(if (active) Modifier else Modifier.press(onClick = onOpen))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!active) Icon(Icons.Filled.Restore, contentDescription = null, tint = tone, modifier = Modifier.size(14.dp))
                Text(
                    if (active) tx(lang, "Active", "Aktif") else tx(lang, "Restore", "Buka"),
                    color = tone,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ Session name editor */

@Composable
fun BoxScope.SessionNameOverlay(
    lang: Lang,
    initialName: String,
    onSave: (String) -> Unit,
    onClose: () -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }

    OverlaySheet(
        title = tx(lang, "Rename session", "Ubah nama sesi"),
        subtitle = tx(lang, "Give this group of tabs a memorable name", "Kasih nama yang gampang diingat buat kumpulan tab ini"),
        onClose = onClose,
        footer = {
            PillButtonFull(tx(lang, "Save name", "Simpan nama"), Icons.Filled.Check, accent = true) { onSave(name) }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassField(
                value = name,
                onChange = { name = it },
                placeholder = tx(lang, "Session name", "Nama sesi"),
                imeAction = ImeAction.Done,
                onDone = { onSave(name) },
            )
            Text(
                tx(lang, "Up to 48 characters", "Maksimal 48 karakter"),
                color = Palette.InkMuted,
                fontSize = 11.sp,
            )
        }
    }
}

/* ------------------------------------------------------------------ Menu */

private data class MenuItem(val icon: ImageVector, val label: String, val hint: String, val onClick: () -> Unit)

@Composable
fun BoxScope.MenuOverlay(
    lang: Lang,
    companionEnabled: Boolean,
    immersive: Boolean,
    onChat: () -> Unit,
    onSessions: () -> Unit,
    onSaveSession: () -> Unit,
    onDirect: () -> Unit,
    onToggleCompanion: () -> Unit,
    onToggleImmersive: () -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val items = listOf(
        MenuItem(
            if (immersive) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
            if (immersive) tx(lang, "Exit fullscreen", "Keluar layar penuh") else tx(lang, "Fullscreen mode", "Mode layar penuh"),
            tx(lang, "Fold the bars into a floating orb", "Lipat semua bar jadi satu bola melayang"),
            onToggleImmersive,
        ),
        MenuItem(Icons.Filled.ChatBubble, tx(lang, "Talk to Denia", "Ngobrol sama Denia"), tx(lang, "Ask anything or give a command", "Tanya apa aja atau kasih perintah"), onChat),
        MenuItem(Icons.Filled.Layers, tx(lang, "Sessions", "Sesi"), tx(lang, "Separate tabs and cookies", "Tab dan cookie terpisah"), onSessions),
        MenuItem(Icons.Filled.Bookmark, tx(lang, "Save tabs as session", "Simpan tab jadi sesi"), tx(lang, "Keep this set for later", "Simpan set ini buat nanti"), onSaveSession),
        MenuItem(Icons.Filled.GpsFixed, tx(lang, "Direct Denia", "Arahkan Denia"), tx(lang, "Tap a spot, she runs there", "Ketuk satu titik, dia lari ke sana"), onDirect),
        MenuItem(
            Icons.Filled.AutoAwesome,
            if (companionEnabled) tx(lang, "Hide Denia", "Sembunyikan Denia") else tx(lang, "Show Denia", "Tampilkan Denia"),
            tx(lang, "Toggle the companion", "Nyalakan / matikan Denia"),
            onToggleCompanion,
        ),
        MenuItem(LucideSettings2, tx(lang, "Settings", "Pengaturan"), tx(lang, "Theme, search, privacy", "Tema, pencarian, privasi"), onSettings),
    )
    OverlaySheet(title = tx(lang, "Menu", "Menu"), onClose = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .press(onClick = item.onClick)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Lavender.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(item.icon, contentDescription = null, tint = Palette.Lavender, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text(item.label, color = Palette.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(item.hint, color = Palette.InkMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ Shortcut editor */

@Composable
fun BoxScope.ShortcutEditorOverlay(
    lang: Lang,
    initial: Shortcut?,
    onSave: (title: String, address: String, tint: Color) -> Unit,
    onDelete: (() -> Unit)?,
    onClose: () -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var address by remember { mutableStateOf(initial?.host ?: "") }
    var tint by remember { mutableStateOf(initial?.tint ?: SHORTCUT_TINTS.first()) }

    OverlaySheet(
        title = if (initial == null) tx(lang, "New shortcut", "Pintasan baru") else tx(lang, "Edit shortcut", "Ubah pintasan"),
        subtitle = tx(lang, "Shown on your home screen", "Muncul di beranda"),
        onClose = onClose,
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onDelete != null) PillButton(tx(lang, "Delete", "Hapus"), Icons.Filled.Delete) { onDelete() }
                PillButton(tx(lang, "Save", "Simpan"), Icons.Filled.Check, accent = true) { onSave(title, address, tint) }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ShortcutIcon(title.ifBlank { address }, address, tint, size = 72.dp)
            }
            GlassField(
                value = title,
                onChange = { title = it },
                placeholder = tx(lang, "Name (e.g. YouTube)", "Nama (mis. YouTube)"),
            )
            GlassField(
                value = address,
                onChange = { address = it },
                placeholder = tx(lang, "Address (e.g. youtube.com)", "Alamat (mis. youtube.com)"),
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
                onDone = { onSave(title, address, tint) },
            )
            Text(
                tx(lang, "Color", "Warna").uppercase(),
                color = Palette.InkMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SHORTCUT_TINTS.forEach { c ->
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(c, lerp(c, Color.White, 0.4f))))
                            .then(if (c == tint) Modifier.border(3.dp, Palette.Ink, CircleShape) else Modifier)
                            .press { tint = c },
                    )
                }
            }
        }
    }
}
